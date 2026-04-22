package org.tcdrm.adaptive.simulation;

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
    private String execRegion;
    
    // État de la simulation
    private int currentReplicaCount;
    private double currentBudget;
    private int queryCount;
    
    // Compteurs pour popularité (lecture/écriture)
    
    // EMA (Exponential Moving Average) pour popularité - feature d'état pour RL
    private double emaPopularity;
    // Popularity strategy (TinyLFU)
    private String popularityStrategy = "EMA"; // EMA | TINYLFU | EMA_TINYLFU
    private TinyLFU tinyLfu;
    private double tinyTauHi = 0.6;
    private double tinyTauLo = 0.3;
    private int tinyWidth = 2048;
    private int tinyDepth = 4;
    private int tinyAging = 200;

    public TcdrmSimulation(long seed, boolean complex) {
        this.infrastructure = new MultiCloudInfrastructure();
        this.rnd = new Random(seed);
        this.complex = complex;
        this.execProvider = "Google";
        // Runtime-configurable (default EU)
        this.execRegion = org.tcdrm.adaptive.core.RuntimeConfig.getExecRegion();
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        this.emaPopularity = 0.0;
        
        // Créer les fragments distribués sur les providers
        this.fragments = createDistributedFragments();
        this.workloadSets = buildLegacyWorkloadSets(seed);

        // Popularity strategy from RuntimeConfig
        String strat = org.tcdrm.adaptive.core.RuntimeConfig.getPopularityStrategy();
        Integer w = org.tcdrm.adaptive.core.RuntimeConfig.getTinyWidth();
        Integer d = org.tcdrm.adaptive.core.RuntimeConfig.getTinyDepth();
        Integer a = org.tcdrm.adaptive.core.RuntimeConfig.getTinyAging();
        Double hi = org.tcdrm.adaptive.core.RuntimeConfig.getTinyTauHi();
        Double lo = org.tcdrm.adaptive.core.RuntimeConfig.getTinyTauLo();
        configurePopularity(strat, w, d, a, hi, lo);
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

    // Legacy-like workload: predefined fragment subsets per query
    private List<int[]> workloadSets;
    private List<int[]> buildLegacyWorkloadSets(long seed) {
        int n = TcdrmConstants.MAX_QUERIES;
        if (complex) {
            return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.generateComplex(fragments, n, seed);
        } else {
            return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.generateSimple(fragments, n, seed);
        }
    }

    private List<DataFragment> selectFragmentsForQuery(int qIdx) {
        if (workloadSets != null && qIdx < workloadSets.size()) {
            int[] idx = workloadSets.get(qIdx);
            return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.select(fragments, idx);
        }
        return fragments; // fallback: all relations
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
        // Sélection de la région d'exécution par requête  
        refreshExecRegionPerQuery();
        // Simuler lecture/écriture (workload OLAP: 90% lectures, 10% écritures)
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        incrementPopularityForQuery();
        
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
     * Exécute une requête TCDRM avec stratégie de réplication à seuil fixe (baseline).
     */
    public QueryResult executeTcdrmQuery() {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;
        // Sélection de la région d'exécution par requête  
        refreshExecRegionPerQuery();
        
        // Simuler lecture/écriture
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        // TCDRM static: réplication basée sur seuil fixe P_SLA
        if (queryCount >= TcdrmConstants.POPULARITY_THRESHOLD && currentReplicaCount < maxReplicas) {
            creationCost = createNextReplica();
        }
        
        // Incrémenter le compteur de warm-up pour tous les fragments répliqués
        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);
        
        // Exécuter la requête
        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        incrementPopularityForQuery();
        
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
     * L'agent RL apprend via la reward function quand répliquer.
     * 
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     * @return résultat de la requête
     */
    public QueryResult executeRLQuery(int action) {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;
        // Sélection de la région d'exécution par requête  
        refreshExecRegionPerQuery();
        
        // Simuler lecture/écriture
        boolean isRead = rnd.nextDouble() < TcdrmConstants.READ_WRITE_RATIO;
        
        // Mettre à jour la popularité EMA
        updateEmaPopularity(isRead);
        
        // Appliquer l'action RL avec garde popularité/P_SLA et hystérésis anti-oscillation
        boolean replicateAllowed = (queryCount >= TcdrmConstants.POPULARITY_THRESHOLD)
            || (emaPopularity >= TcdrmConstants.EMA_REPLICATION_THRESHOLD);
        if (action == 1 && currentReplicaCount < maxReplicas && replicateAllowed) {
            creationCost = createNextReplica();
            currentBudget -= creationCost;
        } else if (action == 2 && currentReplicaCount > 0) {
            // Ne pas supprimer si la popularité reste au-dessus d'un seuil
            if (emaPopularity < TcdrmConstants.EMA_DELETE_THRESHOLD) {
                // Supprimer uniquement si la durée de vie minimale est atteinte
                int idx = findDeletableReplicaIndex();
                if (idx >= 0) {
                    deleteReplicaAtIndex(idx);
                }
                // sinon: NOOP (évite les oscillations)
            }
        }
        
        // Incrémenter le compteur de warm-up
        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);
        
        // Exécuter la requête
        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, execProvider, execRegion, rnd);
        incrementPopularityForQuery();
        
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
        if (isTinyEnabled()) {
            int bestIdx = -1;
            double bestScore = -1.0;
            for (int i = 0; i < fragments.size(); i++) {
                DataFragment f = fragments.get(i);
                if (!f.hasReplica() && !f.hasRecreateCooldown()) {
                    double sc = tinyLfu.estimate(f.getId());
                    if (sc > bestScore) { bestScore = sc; bestIdx = i; }
                }
            }
            if (bestIdx >= 0 && bestScore >= tinyTauHi) {
                double cost = fragments.get(bestIdx).createReplica(execProvider, execRegion);
                currentReplicaCount++;
                return cost;
            }
            return 0.0;
        }
        for (DataFragment fragment : fragments) {
            if (!fragment.hasReplica() && !fragment.hasRecreateCooldown()) {
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

    /** Supprime un réplica pour le fragment donné par son index. */
    private void deleteReplicaAtIndex(int index) {
        if (index >= 0 && index < fragments.size() && fragments.get(index).hasReplica()) {
            DataFragment f = fragments.get(index);
            f.deleteReplica();
            f.startRecreateCooldown(TcdrmConstants.REPLICA_RECREATE_COOLDOWN_QUERIES);
            currentReplicaCount--;
        }
    }

    /**
     * Trouve un réplica supprimable: requiert une durée de vie minimale
     * (MIN_REPLICA_LIFETIME_QUERIES). Retourne l'index ou -1 si aucun.
     */
    private int findDeletableReplicaIndex() {
        for (int i = fragments.size() - 1; i >= 0; i--) {
            DataFragment f = fragments.get(i);
            if (f.hasReplica() && f.getQueriesSinceReplication() >= TcdrmConstants.MIN_REPLICA_LIFETIME_QUERIES) {
                if (!isTinyEnabled()) return i;
                if (tinyLfu.estimate(f.getId()) < tinyTauLo) return i;
            }
        }
        return -1;
    }

    private void incrementPopularityForQuery() {
        if (!isTinyEnabled()) return;
        int nRel = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        for (int i = 0; i < nRel && i < fragments.size(); i++) {
            DataFragment f = fragments.get(i);
            tinyLfu.increment(f.getId());
            f.incrementQueryCount(); // also ticks cooldown
        }
    }

    private boolean isTinyEnabled() {
        return tinyLfu != null && ("TINYLFU".equalsIgnoreCase(popularityStrategy) || "EMA_TINYLFU".equalsIgnoreCase(popularityStrategy));
    }

    public void configurePopularity(String strategy, Integer width, Integer depth, Integer agingPeriod, Double tauHi, Double tauLo) {
        if (strategy != null) this.popularityStrategy = strategy;
        if (width != null) this.tinyWidth = Math.max(256, width);
        if (depth != null) this.tinyDepth = Math.max(2, depth);
        if (agingPeriod != null) this.tinyAging = Math.max(32, agingPeriod);
        if (tauHi != null) this.tinyTauHi = Math.max(0.0, Math.min(1.0, tauHi));
        if (tauLo != null) this.tinyTauLo = Math.max(0.0, Math.min(1.0, tauLo));
        if ("TINYLFU".equalsIgnoreCase(popularityStrategy) || "EMA_TINYLFU".equalsIgnoreCase(popularityStrategy)) {
            if (this.tinyLfu == null) this.tinyLfu = new TinyLFU(tinyWidth, tinyDepth, tinyAging);
        }
    }

    /**
     * Met à jour la popularité EMA après chaque accès.
     * Cette popularité est une FEATURE D'ÉTAT pour les agents RL.
     * Les agents apprennent via la reward function quand répliquer.
     */
    private void updateEmaPopularity(boolean isRead) {
        // Score d'accès: lectures favorisées (réplication bénéfique)
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
        
        // Décroissance pour éviter la saturation
        emaPopularity *= TcdrmConstants.DECAY_PER_QUERY;
        
        // Borner entre 0 et 1
        emaPopularity = Math.max(0.0, Math.min(1.0, emaPopularity));
    }
    
    /**
     * Construit l'état pour les modèles RL.
     * [latency, budget, replicas, normalizedPopularity, cost, tSlaViolation, cSlaViolation,
     *  queryProgress, pSlaProgress]
     *
     * {@code pSlaProgress} = min(1, queryIndex / P_SLA) aligné avec l'article (P_SLA = 200 requêtes
     * avant zone de décision TCDRM). Utilisé uniquement par les agents RL ; NoRep/TCDRM inchangés.
     *
     * La popularité EMA reste une feature d'observation ; l'agent RL apprend via la récompense.
     */
    public double[] buildRLState(double lastLatency, double lastCost) {
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        
        // Popularité EMA (0-1) - feature d'observation pour l'agent
        double normalizedPopularity = emaPopularity / TcdrmConstants.EMA_REPLICATION_THRESHOLD;
        normalizedPopularity = Math.min(1.5, normalizedPopularity);

        double pSlaProgress = Math.min(1.0,
            queryCount / (double) Math.max(1, TcdrmConstants.POPULARITY_THRESHOLD));
        
        return new double[] {
            lastLatency / tSla,                                    // Normalized latency
            currentBudget / TcdrmConstants.INITIAL_BUDGET,         // Normalized budget
            (double) currentReplicaCount / maxReplicas,            // Normalized replicas
            normalizedPopularity,                                  // Popularity EMA (0-1.5)
            lastCost / cSla,                                       // Normalized cost
            lastLatency > tSla ? 1.0 : 0.0,                       // T_SLA violation
            lastCost > cSla ? 1.0 : 0.0,                          // C_SLA violation
            (double) queryCount / TcdrmConstants.MAX_QUERIES,      // Query progress
            pSlaProgress                                           // Index requête vs P_SLA (paper)
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
        this.emaPopularity = 0.0;
        fragments.forEach(DataFragment::deleteReplica);
    }
    
    /**
     * Retourne la popularité EMA actuelle (pour debug/monitoring).
     */
    public double getEmaPopularity() {
        return emaPopularity;
    }

    /**
     * Met à jour la région d'exécution pour LA requête courante.
     * Si RuntimeConfig.execRegion == "RANDOM", choisit uniformément parmi {EU, US, AS}.
     * Sinon, utilise la région configurée.
     */
    private void refreshExecRegionPerQuery() {
        String cfg = org.tcdrm.adaptive.core.RuntimeConfig.getExecRegion();
        if (cfg != null && cfg.equalsIgnoreCase("RANDOM")) {
            String[] regions = MultiCloudInfrastructure.REGIONS;
            if (regions != null && regions.length > 0) {
                this.execRegion = regions[rnd.nextInt(regions.length)];
            } else {
                this.execRegion = "EU"; // repli sûr
            }
        } else {
            this.execRegion = cfg != null ? cfg : "EU";
        }
    }

    
}
