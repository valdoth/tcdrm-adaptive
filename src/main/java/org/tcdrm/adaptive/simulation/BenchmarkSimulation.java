package org.tcdrm.adaptive.simulation;

import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;
import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.data.BenchmarkQueries;
import org.tcdrm.adaptive.data.Query;
import org.tcdrm.adaptive.data.Relation;
import org.tcdrm.adaptive.data.WorkloadGenerator;

import java.util.*;

/**
 * Simulation pour le benchmark utilisant des requêtes fixes R1 (simple) ou R2 (complexe).
 * 
 * Contrairement à TcdrmSimulation qui utilise des requêtes aléatoires pour l'entraînement,
 * cette classe répète la même requête (R1 ou R2) 1000 fois pour évaluer les performances
 * des stratégies de réplication de manière déterministe.
 */
public class BenchmarkSimulation {
    
    private final MultiCloudInfrastructure infrastructure;
    private final Random rnd;
    private final boolean complex;
    private final Query benchmarkQuery;
    private final List<Relation> relations;
    
    // État de la simulation
    private int currentReplicaCount;
    private double currentBudget;
    private int queryCount;
    
    // Tracking des réplicas par relation
    private final Map<String, Set<String>> replicasByRelation;  // relationId -> Set<datacenterName>
    
    // EMA pour popularité
    private double emaPopularity;
    
    public BenchmarkSimulation(long seed, boolean complex) {
        this.infrastructure = new MultiCloudInfrastructure();
        this.rnd = new Random(seed);
        this.complex = complex;
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        this.emaPopularity = 0.0;
        this.replicasByRelation = new HashMap<>();
        
        // Générer les relations
        WorkloadGenerator generator = new WorkloadGenerator(seed, infrastructure);
        this.relations = generator.getRelations();
        
        // Créer la requête benchmark (R1 ou R2)
        this.benchmarkQuery = complex ? 
            BenchmarkQueries.createR2Complex(0) : 
            BenchmarkQueries.createR1Simple(0);
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
     * Utilise toujours la requête benchmark (R1 ou R2).
     */
    public QueryResult executeNoRepQuery() {
        // Calculer latence et coût pour accéder aux relations sans réplication
        double totalLatency = 0.0;
        double totalBwCost = 0.0;
        double bwInterProvider = 0.0;
        double bwInterRegion = 0.0;
        
        String sourceDC = benchmarkQuery.getSourceDatacenter();
        
        for (String relationId : benchmarkQuery.getRelationIds()) {
            Relation relation = getRelation(relationId);
            if (relation == null) continue;
            
            String targetDC = relation.getHomeDatacenter();
            
            // Latence réseau
            double latency = infrastructure.getLatencyMs(sourceDC, targetDC);
            
            // Temps de transfert
            double transferTime = infrastructure.computeTransferTimeMs(
                relation.getSizeGb(), 
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1],
                rnd
            );
            
            totalLatency += latency + transferTime;
            
            // Coût bande passante
            double bwCost = infrastructure.getBandwidthCostPerGb(
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1]
            ) * relation.getSizeGb();
            
            totalBwCost += bwCost;
            
            // Tracking inter-provider/region
            if (!sourceDC.split("_")[0].equals(targetDC.split("_")[0])) {
                bwInterProvider += relation.getSizeGb();
            } else if (!sourceDC.split("_")[1].equals(targetDC.split("_")[1])) {
                bwInterRegion += relation.getSizeGb();
            }
        }
        
        // Coût CPU (simulation simple)
        double cpuCost = TcdrmConstants.CPU_COST_PER_10M_MI * 0.1;
        
        // Coût I/O
        double ioCost = TcdrmConstants.STORAGE_COST_PER_GB_PER_MONTH * 0.001;
        
        // Mettre à jour popularité
        updateEmaPopularity(benchmarkQuery.isRead());
        
        QueryResult result = new QueryResult(
            queryCount,
            totalLatency,
            totalBwCost,
            cpuCost,
            ioCost,
            totalBwCost + cpuCost + ioCost,
            bwInterProvider,
            bwInterRegion,
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
        
        // Mettre à jour popularité
        updateEmaPopularity(benchmarkQuery.isRead());
        
        // Créer un réplica si on dépasse le seuil de popularité
        if (queryCount >= TcdrmConstants.POPULARITY_THRESHOLD && currentReplicaCount < maxReplicas) {
            creationCost = createNextReplica();
        }
        
        // Calculer latence et coût en utilisant les réplicas disponibles
        double totalLatency = 0.0;
        double totalBwCost = 0.0;
        double bwInterProvider = 0.0;
        double bwInterRegion = 0.0;
        
        String sourceDC = benchmarkQuery.getSourceDatacenter();
        
        for (String relationId : benchmarkQuery.getRelationIds()) {
            Relation relation = getRelation(relationId);
            if (relation == null) continue;
            
            // Trouver le datacenter le plus proche (original ou réplica)
            String targetDC = findClosestDatacenter(relationId, sourceDC);
            
            // Latence réseau
            double latency = infrastructure.getLatencyMs(sourceDC, targetDC);
            
            // Temps de transfert
            double transferTime = infrastructure.computeTransferTimeMs(
                relation.getSizeGb(),
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1],
                rnd
            );
            
            totalLatency += latency + transferTime;
            
            // Coût bande passante
            double bwCost = infrastructure.getBandwidthCostPerGb(
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1]
            ) * relation.getSizeGb();
            
            totalBwCost += bwCost;
            
            // Tracking inter-provider/region
            if (!sourceDC.split("_")[0].equals(targetDC.split("_")[0])) {
                bwInterProvider += relation.getSizeGb();
            } else if (!sourceDC.split("_")[1].equals(targetDC.split("_")[1])) {
                bwInterRegion += relation.getSizeGb();
            }
        }
        
        // Coût CPU
        double cpuCost = TcdrmConstants.CPU_COST_PER_10M_MI * 0.1;
        
        // Coût I/O + maintenance
        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
        double ioCost = TcdrmConstants.STORAGE_COST_PER_GB_PER_MONTH * 0.001 + maintenanceCost;
        
        QueryResult result = new QueryResult(
            queryCount,
            totalLatency,
            totalBwCost + creationCost,
            cpuCost,
            ioCost,
            totalBwCost + creationCost + cpuCost + ioCost,
            bwInterProvider,
            bwInterRegion,
            currentReplicaCount
        );
        
        queryCount++;
        return result;
    }
    
    /**
     * Exécute une requête RL avec action adaptative.
     */
    public QueryResult executeRLQuery(int action) {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;
        
        // Mettre à jour popularité
        updateEmaPopularity(benchmarkQuery.isRead());
        
        // Appliquer l'action RL
        if (action == 1 && currentReplicaCount < maxReplicas) {
            // REPLICATE
            creationCost = createNextReplica();
        } else if (action == 2 && currentReplicaCount > 0) {
            // DELETE
            deleteLastReplica();
        }
        // action == 0 : NOOP
        
        // Calculer latence et coût
        double totalLatency = 0.0;
        double totalBwCost = 0.0;
        double bwInterProvider = 0.0;
        double bwInterRegion = 0.0;
        
        String sourceDC = benchmarkQuery.getSourceDatacenter();
        
        for (String relationId : benchmarkQuery.getRelationIds()) {
            Relation relation = getRelation(relationId);
            if (relation == null) continue;
            
            String targetDC = findClosestDatacenter(relationId, sourceDC);
            
            double latency = infrastructure.getLatencyMs(sourceDC, targetDC);
            double transferTime = infrastructure.computeTransferTimeMs(
                relation.getSizeGb(),
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1],
                rnd
            );
            
            totalLatency += latency + transferTime;
            
            double bwCost = infrastructure.getBandwidthCostPerGb(
                sourceDC.split("_")[0], sourceDC.split("_")[1],
                targetDC.split("_")[0], targetDC.split("_")[1]
            ) * relation.getSizeGb();
            
            totalBwCost += bwCost;
            
            if (!sourceDC.split("_")[0].equals(targetDC.split("_")[0])) {
                bwInterProvider += relation.getSizeGb();
            } else if (!sourceDC.split("_")[1].equals(targetDC.split("_")[1])) {
                bwInterRegion += relation.getSizeGb();
            }
        }
        
        double cpuCost = TcdrmConstants.CPU_COST_PER_10M_MI * 0.1;
        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
        double ioCost = TcdrmConstants.STORAGE_COST_PER_GB_PER_MONTH * 0.001 + maintenanceCost;
        
        QueryResult result = new QueryResult(
            queryCount,
            totalLatency,
            totalBwCost + creationCost,
            cpuCost,
            ioCost,
            totalBwCost + creationCost + cpuCost + ioCost,
            bwInterProvider,
            bwInterRegion,
            currentReplicaCount
        );
        
        queryCount++;
        return result;
    }
    
    /**
     * Trouve le datacenter le plus proche contenant la relation (original ou réplica).
     */
    private String findClosestDatacenter(String relationId, String sourceDC) {
        Relation relation = getRelation(relationId);
        if (relation == null) return sourceDC;
        
        Set<String> availableDCs = new HashSet<>();
        availableDCs.add(relation.getHomeDatacenter());
        
        if (replicasByRelation.containsKey(relationId)) {
            availableDCs.addAll(replicasByRelation.get(relationId));
        }
        
        // Trouver le DC avec la latence minimale
        String closestDC = relation.getHomeDatacenter();
        double minLatency = infrastructure.getLatencyMs(sourceDC, closestDC);
        
        for (String dc : availableDCs) {
            double latency = infrastructure.getLatencyMs(sourceDC, dc);
            if (latency < minLatency) {
                minLatency = latency;
                closestDC = dc;
            }
        }
        
        return closestDC;
    }
    
    /**
     * Crée un réplica pour la première relation de la requête benchmark.
     */
    private double createNextReplica() {
        if (benchmarkQuery.getRelationIds().isEmpty()) return 0.0;
        
        // Répliquer la première relation dans une région différente
        String relationId = benchmarkQuery.getRelationIds().get(0);
        Relation relation = getRelation(relationId);
        if (relation == null) return 0.0;
        
        // Choisir un datacenter cible (différent de l'original)
        List<String> dcNames = new ArrayList<>(infrastructure.getAllDatacenters().keySet());
        dcNames.remove(relation.getHomeDatacenter());
        
        if (dcNames.isEmpty()) return 0.0;
        
        String targetDC = dcNames.get(rnd.nextInt(dcNames.size()));
        
        // Ajouter le réplica
        replicasByRelation.putIfAbsent(relationId, new HashSet<>());
        replicasByRelation.get(relationId).add(targetDC);
        
        currentReplicaCount++;
        
        // Coût de création
        return TcdrmConstants.REPLICA_CREATION_COST;
    }
    
    /**
     * Supprime le dernier réplica créé.
     */
    private void deleteLastReplica() {
        if (currentReplicaCount == 0) return;
        
        // Trouver une relation avec des réplicas et en supprimer un
        for (Map.Entry<String, Set<String>> entry : replicasByRelation.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                String dcToRemove = entry.getValue().iterator().next();
                entry.getValue().remove(dcToRemove);
                currentReplicaCount--;
                return;
            }
        }
    }
    
    /**
     * Met à jour la popularité EMA.
     */
    private void updateEmaPopularity(boolean isRead) {
        double accessScore = isRead ? 
            TcdrmConstants.ACCESS_SCORE_BASE * TcdrmConstants.READ_BONUS_FACTOR :
            TcdrmConstants.ACCESS_SCORE_BASE;
        
        double normalizedScore = accessScore / (TcdrmConstants.ACCESS_SCORE_BASE * TcdrmConstants.READ_BONUS_FACTOR);
        double alpha = TcdrmConstants.EMA_ALPHA;
        emaPopularity = alpha * normalizedScore + (1.0 - alpha) * emaPopularity;
        emaPopularity *= TcdrmConstants.DECAY_PER_QUERY;
        emaPopularity = Math.max(0.0, Math.min(1.0, emaPopularity));
    }
    
    /**
     * Construit l'état pour les modèles RL.
     */
    public double[] buildRLState(double lastLatency, double lastCost) {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        
        double normalizedPopularity = emaPopularity / TcdrmConstants.EMA_REPLICATION_THRESHOLD;
        normalizedPopularity = Math.min(1.5, normalizedPopularity);
        
        return new double[] {
            lastLatency / tSla,
            currentBudget / TcdrmConstants.INITIAL_BUDGET,
            (double) currentReplicaCount / maxReplicas,
            normalizedPopularity,
            lastCost / cSla,
            lastLatency > tSla ? 1.0 : 0.0,
            lastCost > cSla ? 1.0 : 0.0,
            (double) queryCount / TcdrmConstants.MAX_QUERIES
        };
    }
    
    private Relation getRelation(String relationId) {
        return relations.stream()
            .filter(r -> r.getId().equals(relationId))
            .findFirst()
            .orElse(null);
    }
    
    public double getEmaPopularity() {
        return emaPopularity;
    }
}
