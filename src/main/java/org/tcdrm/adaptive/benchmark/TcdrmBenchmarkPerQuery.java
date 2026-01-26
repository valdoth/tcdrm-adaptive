package org.tcdrm.adaptive.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TCDRM Benchmark that tracks metrics per individual query
 * to reproduce the article's graphs showing the drop after PSLA threshold
 */
public class TcdrmBenchmarkPerQuery {
    private static final int MAX_QUERIES = 2000;
    
    // Intra-datacenter (local replica)
    private static final double BW_LOCAL_GBPS = 10.0;
    private static final double LAT_LOCAL_MS = 1.0;
    
    // Inter-provider (remote, no replica)
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;

    // Coûts selon le tableau de l'article (moyenne des providers)
    private static final double COST_BW_INTRA_DC = 0.002;        // Moyenne: (0.0015+0.002+0.004)/3
    private static final double COST_BW_INTER_REGION = 0.008;    // Article: 0.008
    private static final double COST_BW_INTER_PROVIDER = 0.10;   // Article: 0.10

    private static final double CPU_COST_PER_HOUR = 0.02;        // Article: 0.020
    private static final double STORAGE_COST_PER_GB_PER_MONTH = 0.02;   // Article: 0.02
    private static final double PROCESSING_MIN_PER_GB = 0.5;
    
    private static final int POPULARITY_THRESHOLD = 200;
    private static final double JITTER_RATIO = 0.05;
    private static final double CPU_JITTER_RATIO = 0.05;

    private final int replicationFactor;
    private final Random rnd;

    public TcdrmBenchmarkPerQuery(int replicationFactor, long seed) {
        this.replicationFactor = replicationFactor;
        this.rnd = new Random(seed);
    }
    
    /**
     * Sélection intelligente du réplica basée sur la proximité géographique
     * Simule la sélection du réplica le plus proche parmi les disponibles
     * 
     * Avec 3 réplicas distribués géographiquement, la probabilité d'avoir
     * un réplica dans la même région ou datacenter est élevée
     */
    private boolean selectBestReplica(int queryNumber, int totalReplicas) {
        // Probabilité d'utiliser un réplica local augmente avec le nombre de réplicas
        // Formule ajustée pour refléter une meilleure distribution géographique
        // Avec 3 réplicas: 3/(3+1) = 0.75 = 75% local (au lieu de 60%)
        double localProbability = (double) totalReplicas / (totalReplicas + 1);
        return rnd.nextDouble() < localProbability;
    }

    public BenchmarkDataPerQuery computeBenchmark(String queryId, List<Double> fragmentSizesGb) {
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCount = new ArrayList<>();

        double totalCost = 0.0;
        int replicasCreated = 0;
        double dataGb = fragmentSizesGb.stream().mapToDouble(d -> d).sum();

        // Coût de création des réplicas (une seule fois)
        double replicationCreationCost = 0.0;
        
        for (int q = 0; q < MAX_QUERIES; q++) {
            boolean replicaExists = q >= POPULARITY_THRESHOLD;
            
            // Create replica at threshold with creation cost
            if (q == POPULARITY_THRESHOLD) {
                replicasCreated = replicationFactor;
                // Coût de transfert initial pour créer les réplicas
                replicationCreationCost = dataGb * COST_BW_INTER_PROVIDER * replicationFactor;
            }
            
            // Sélection intelligente du réplica basée sur la distance
            // Simule la sélection du réplica le plus proche parmi les disponibles
            boolean useLocal = replicaExists && selectBestReplica(q, replicationFactor);
            
            // Network parameters
            double bwGbps = useLocal ? BW_LOCAL_GBPS : BW_REMOTE_GBPS;
            double latencyMs = useLocal ? LAT_LOCAL_MS : LAT_REMOTE_MS;
            double costPerGb = useLocal ? COST_BW_INTRA_DC : COST_BW_INTER_PROVIDER;

            // Transfer time with jitter
            double transferMs = (dataGb * 8_000.0 / bwGbps) + latencyMs;
            transferMs *= (1.0 + JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Processing time with jitter
            double processingMin = dataGb * PROCESSING_MIN_PER_GB;
            processingMin *= (1.0 + CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Total time for this query
            double queryTimeMs = transferMs + processingMin * 60_000.0;
            
            // Costs for this query
            double transferCost = dataGb * costPerGb;
            double cpuCost = (processingMin / 60.0) * CPU_COST_PER_HOUR;
            
            // Coût de stockage: calculé par heure d'utilisation (pas par requête)
            double queryDurationHours = queryTimeMs / 3600000.0; // ms to hours
            double storageCost = replicaExists ? 
                (dataGb * STORAGE_COST_PER_GB_PER_MONTH * replicasCreated * queryDurationHours / 720.0) : 0.0;
            
            // Ajouter le coût de création au premier calcul après réplication
            double creationCost = (q == POPULARITY_THRESHOLD) ? replicationCreationCost : 0.0;
            
            double queryCost = transferCost + cpuCost + storageCost + creationCost;
            totalCost += queryCost;

            queryNumbers.add(q);
            timePerQueryMs.add(queryTimeMs / 1000.0); // Convert to seconds
            costPerQuery.add(queryCost);
            cumulativeCost.add(totalCost);
            replicaCount.add(replicasCreated);
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs, 
                                         costPerQuery, cumulativeCost, replicaCount);
    }
}
