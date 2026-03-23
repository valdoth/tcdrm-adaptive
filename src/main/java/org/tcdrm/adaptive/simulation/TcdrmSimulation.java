package org.tcdrm.adaptive.simulation;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.tcdrm.adaptive.cloudsim.DataFragment;
import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;
import org.tcdrm.adaptive.cloudsim.QueryCloudlet;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation TCDRM utilisant CloudSimPlus.
 * 
 * Gère l'exécution des requêtes avec différentes stratégies:
 * - NoRepLc: pas de réplication
 * - TCDRM: réplication à seuil fixe (P_SLA)
 * - RL: réplication adaptative via modèles Python
 */
public class TcdrmSimulation {

    private final MultiCloudInfrastructure infrastructure;
    private final List<DataFragment> fragments;
    private final Random rnd;
    private final boolean complex;
    
    // Provider/région d'exécution des requêtes
    private final String execProvider;
    private final String execRegion;
    
    // État de la simulation
    private int currentReplicaCount;
    private double currentBudget;
    private int queryCount;
    
    // Compteurs pour popularité (lecture/écriture)
    private int readCount;
    private int writeCount;
    
    // EMA (Exponential Moving Average) pour popularité avec décroissance
    // Basé sur Redis LFU et algorithmes de ranking avec time decay
    private double emaPopularity;

    public TcdrmSimulation(long seed, boolean complex) {
        this.infrastructure = new MultiCloudInfrastructure();
        this.rnd = new Random(seed);
        this.complex = complex;
        this.execProvider = "Google";
        this.execRegion = "US";
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        this.readCount = 0;
        this.writeCount = 0;
        this.emaPopularity = 0.0;
        
        // Créer les fragments distribués sur les providers
        this.fragments = createDistributedFragments();
    }

    /**
     * Crée les fragments de données distribués sur les providers.
     * Paper Section 4.2: chaque relation sur un provider différent.
     */
    private List<DataFragment> createDistributedFragments() {
        int nRelations = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        List<DataFragment> frags = new ArrayList<>();
        
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions = MultiCloudInfrastructure.REGIONS;
        
        for (int i = 0; i < nRelations; i++) {
            String provider = providers[i % providers.length];
            String region = regions[i % regions.length];
            frags.add(new DataFragment(i, "R" + i, provider, region));
        }
        
        return frags;
    }

    /**
     * Résultat d'une requête.
     */
    public record QueryResult(
        int queryNumber,
        double queryTimeMs,
        double bwCost,
        double cpuCost,
        double ioCost,
        double totalCost,
        double bwInterProviderGb,
        double bwInterRegionGb,
        int replicaCount
    ) {}

    /**
     * Exécute une requête NoRepLc (pas de réplication).
     */
    public QueryResult executeNoRepQuery() {
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, fragments);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        
        // Simuler lecture/écriture (workload OLAP: 90% lectures, 10% écritures)
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        if (isRead) {
            readCount++;
        } else {
            writeCount++;
        }
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost(),
            query.getCpuCost(),
            query.getIoCost(),
            query.getTotalCost(),
            query.getBwInterProviderGb(),
            query.getBwInterRegionGb(),
            0
        );
        
        queryCount++;
        return result;
    }

    /**
     * Exécute une requête TCDRM avec stratégie de réplication à seuil fixe.
     */
    public QueryResult executeTcdrmQuery() {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;
        
        // Simuler lecture/écriture (workload OLAP: 90% lectures, 10% écritures)
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        if (isRead) {
            readCount++;
        } else {
            writeCount++;
        }
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        // Créer un réplica si on dépasse P_SLA et qu'on n'a pas atteint le max
        if (queryCount >= TcdrmConstants.POPULARITY_THRESHOLD && currentReplicaCount < maxReplicas) {
            creationCost = createNextReplica();
        }
        
        // Incrémenter le compteur de warm-up pour tous les fragments répliqués
        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);
        
        // Exécuter la requête
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, fragments);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        
        // Coût de maintenance des réplicas
        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
        
        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost() + creationCost,
            query.getCpuCost(),
            query.getIoCost() + maintenanceCost,
            query.getTotalCost() + creationCost + maintenanceCost,
            query.getBwInterProviderGb(),
            query.getBwInterRegionGb(),
            currentReplicaCount
        );
        
        queryCount++;
        return result;
    }

    /**
     * Exécute une requête avec une action RL spécifique.
     * 
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     * @return résultat de la requête
     */
    public QueryResult executeRLQuery(int action) {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;
        
        // Simuler lecture/écriture (workload OLAP: 90% lectures, 10% écritures)
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        if (isRead) {
            readCount++;
        } else {
            writeCount++;
        }
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        // Appliquer l'action RL
        if (action == 1 && currentReplicaCount < maxReplicas) {
            creationCost = createNextReplica();
            currentBudget -= creationCost;
        } else if (action == 2 && currentReplicaCount > 0) {
            deleteLastReplica();
        }
        
        // Incrémenter le compteur de warm-up
        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);
        
        // Exécuter la requête
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, fragments);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        
        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
        
        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost() + creationCost,
            query.getCpuCost(),
            query.getIoCost() + maintenanceCost,
            query.getTotalCost() + creationCost + maintenanceCost,
            query.getBwInterProviderGb(),
            query.getBwInterRegionGb(),
            currentReplicaCount
        );
        
        queryCount++;
        currentBudget -= query.getTotalCost() + maintenanceCost;
        
        return result;
    }

    /**
     * Crée un réplica pour le prochain fragment non répliqué.
     */
    private double createNextReplica() {
        for (DataFragment fragment : fragments) {
            if (!fragment.hasReplica()) {
                double cost = fragment.createReplica(execProvider, execRegion);
                currentReplicaCount++;
                return cost;
            }
        }
        return 0.0;
    }

    /**
     * Supprime le dernier réplica créé.
     */
    private void deleteLastReplica() {
        for (int i = fragments.size() - 1; i >= 0; i--) {
            if (fragments.get(i).hasReplica()) {
                fragments.get(i).deleteReplica();
                currentReplicaCount--;
                return;
            }
        }
    }

    /**
     * Met à jour la popularité EMA après chaque accès.
     * 
     * Formule EMA: popularity(t) = α × access_score + (1-α) × popularity(t-1)
     * 
     * Cette approche est basée sur:
     * - Redis LFU: compteur probabiliste avec décroissance temporelle
     * - Algorithmes de ranking (Reddit, HackerNews): score avec half-life
     * 
     * Avantages:
     * - Décroissance naturelle: la popularité diminue si les accès cessent
     * - Lissage: évite les pics soudains qui déclenchent une réplication prématurée
     * - Réaliste: modélise le comportement "hot/cold" des données
     * 
     * @param isRead true si l'accès est une lecture, false si écriture
     */
    private void updateEmaPopularity(boolean isRead) {
        // Score d'accès: lectures favorisées (réplication bénéfique)
        // Écritures pénalisées (coût de synchronisation)
        double accessScore;
        if (isRead) {
            accessScore = TcdrmConstants.ACCESS_SCORE_BASE * TcdrmConstants.READ_BONUS_FACTOR;
        } else {
            accessScore = TcdrmConstants.ACCESS_SCORE_BASE * TcdrmConstants.WRITE_PENALTY_FACTOR;
        }
        
        // Normaliser le score (0-1)
        double normalizedScore = accessScore / (TcdrmConstants.ACCESS_SCORE_BASE * TcdrmConstants.READ_BONUS_FACTOR);
        
        // Mise à jour EMA: popularity(t) = α × score + (1-α) × popularity(t-1)
        double alpha = TcdrmConstants.EMA_ALPHA;
        emaPopularity = alpha * normalizedScore + (1.0 - alpha) * emaPopularity;
        
        // Appliquer une légère décroissance supplémentaire pour éviter la saturation
        emaPopularity *= TcdrmConstants.DECAY_PER_QUERY;
        
        // Borner entre 0 et 1
        emaPopularity = Math.max(0.0, Math.min(1.0, emaPopularity));
    }
    
    /**
     * Construit l'état pour les modèles RL.
     * [latency, budget, replicas, normalizedPopularity, cost, tSlaViolation, cSlaViolation, queryProgress]
     * 
     * La popularité utilise maintenant EMA avec décroissance exponentielle:
     * - Croît progressivement avec les accès répétés
     * - Décroît naturellement sans accès
     * - Favorise les lectures (réplication bénéfique)
     * - Pénalise les écritures (coût de synchronisation)
     */
    public double[] buildRLState(double lastLatency, double lastCost) {
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        
        // Popularité normalisée par rapport au seuil de réplication
        // emaPopularity est déjà entre 0 et 1
        // On normalise par rapport au seuil: 1.0 = seuil atteint
        double normalizedPopularity = emaPopularity / TcdrmConstants.EMA_REPLICATION_THRESHOLD;
        normalizedPopularity = Math.min(1.5, normalizedPopularity); // Cap à 1.5 pour permettre "très populaire"
        
        return new double[] {
            lastLatency / tSla,                                    // Normalized latency
            currentBudget / TcdrmConstants.INITIAL_BUDGET,         // Normalized budget
            (double) currentReplicaCount / maxReplicas,            // Normalized replicas
            normalizedPopularity,                                  // Popularity EMA (0-1.5)
            lastCost / cSla,                                       // Normalized cost
            lastLatency > tSla ? 1.0 : 0.0,                       // T_SLA violation
            lastCost > cSla ? 1.0 : 0.0,                          // C_SLA violation
            (double) queryCount / TcdrmConstants.MAX_QUERIES       // Query progress
        };
    }

    // === Getters ===
    
    public MultiCloudInfrastructure getInfrastructure() { return infrastructure; }
    public List<DataFragment> getFragments() { return fragments; }
    public int getCurrentReplicaCount() { return currentReplicaCount; }
    public double getCurrentBudget() { return currentBudget; }
    public int getQueryCount() { return queryCount; }
    public boolean isComplex() { return complex; }
    
    public void reset() {
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        this.readCount = 0;
        this.writeCount = 0;
        this.emaPopularity = 0.0;
        fragments.forEach(DataFragment::deleteReplica);
    }
    
    /**
     * Retourne la popularité EMA actuelle (pour debug/monitoring).
     */
    public double getEmaPopularity() {
        return emaPopularity;
    }
}
