package org.tcdrm.adaptive.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TCDRM Benchmark that tracks metrics per individual query
 * to reproduce the article's graphs showing the drop after PSLA threshold
 */
public class TcdrmBenchmarkPerQuery {
    private static final int MAX_QUERIES = 5000;
    
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
    
    private static final double JITTER_RATIO = 0.05;
    private static final double CPU_JITTER_RATIO = 0.05;
    
    // Warm-up progressif des réplicas (Fig. 3: descente progressive)
    private static final int WARMUP_QUERIES = 600;  // Nombre de requêtes pour atteindre 100% d'efficacité
    
    // Seuil de popularité aléatoire pour chaque instance (au lieu de fixe)
    private static final int MIN_POPULARITY_THRESHOLD = 100;
    private static final int MAX_POPULARITY_THRESHOLD = 300;

    private final int replicationFactor;
    private final int popularityThreshold;  // Seuil aléatoire par instance
    private final Random rnd;

    public TcdrmBenchmarkPerQuery(int replicationFactor, long seed) {
        this.replicationFactor = replicationFactor;
        this.rnd = new Random(seed);
        // Générer un seuil de popularité aléatoire entre 100 et 300
        this.popularityThreshold = MIN_POPULARITY_THRESHOLD + rnd.nextInt(MAX_POPULARITY_THRESHOLD - MIN_POPULARITY_THRESHOLD + 1);
    }
    
    /**
     * Calcule l'efficacité du warm-up avec fonction sigmoid
     * Pour une descente progressive douce comme dans l'article (Fig. 3)
     */
    private double calculateWarmupEfficiency(int queriesSinceCreation) {
        if (queriesSinceCreation >= WARMUP_QUERIES) {
            return 1.0;  // 100% d'efficacité
        }
        // Progression linéaire: 0 → 1 sur WARMUP_QUERIES requêtes
        double x = (double) queriesSinceCreation / WARMUP_QUERIES;
        // Sigmoid: 1 / (1 + exp(-k*(x - 0.5)))
        // k=5 pour une transition très douce (descente progressive)
        return 1.0 / (1.0 + Math.exp(-5.0 * (x - 0.5)));
    }
    
    /**
     * Sélection intelligente du réplica basée sur la proximité géographique
     * et l'efficacité du warm-up progressif
     */
    private boolean selectBestReplica(int queryNumber, int totalReplicas, double warmupEfficiency) {
        // Probabilité de base d'utiliser un réplica local
        double baseProbability = (double) totalReplicas / (totalReplicas + 2);
        // Ajuster par l'efficacité du warm-up (descente progressive)
        double localProbability = baseProbability * warmupEfficiency;
        return rnd.nextDouble() < localProbability;
    }

    public BenchmarkDataPerQuery computeBenchmark(String queryId, List<Double> fragmentSizesGb) {
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCount = new ArrayList<>();

        List<Double> bwInterProviderCost = new ArrayList<>();
        List<Double> bwInterRegionCost = new ArrayList<>();
        List<Double> bwTotalCost = new ArrayList<>();
        List<Double> cpuCostList = new ArrayList<>();
        List<Double> ioCostList = new ArrayList<>();
        List<Double> execTimeList = new ArrayList<>();

        double totalCost = 0.0;
        int replicasCreated = 0;
        double dataGb = fragmentSizesGb.stream().mapToDouble(d -> d).sum();

        // Coût de création des réplicas (une seule fois)
        double replicationCreationCost = 0.0;
        int replicaCreationQuery = -1;  // Requête où les réplicas ont été créés
        
        for (int q = 0; q < MAX_QUERIES; q++) {
            boolean replicaExists = q >= popularityThreshold;
            
            // Create replica at threshold with creation cost
            if (q == popularityThreshold) {
                replicasCreated = replicationFactor;
                replicaCreationQuery = q;
                // Coût de transfert initial pour créer les réplicas
                replicationCreationCost = dataGb * COST_BW_INTER_PROVIDER * replicationFactor;
            }
            
            // Calculer l'efficacité du warm-up (descente progressive)
            double warmupEfficiency = 0.0;
            if (replicaExists && replicaCreationQuery >= 0) {
                int queriesSinceCreation = q - replicaCreationQuery;
                warmupEfficiency = calculateWarmupEfficiency(queriesSinceCreation);
            }
            
            // Sélection intelligente du réplica avec warm-up progressif
            boolean useLocal = replicaExists && selectBestReplica(q, replicationFactor, warmupEfficiency);
            
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
            double creationCost = (q == popularityThreshold) ? replicationCreationCost : 0.0;
            
            double queryCost = transferCost + cpuCost + storageCost + creationCost;
            totalCost += queryCost;

            queryNumbers.add(q);
            timePerQueryMs.add(queryTimeMs / 1000.0); // Convert to seconds
            costPerQuery.add(queryCost);
            cumulativeCost.add(totalCost);
            replicaCount.add(replicasCreated);
            
            // Détails pour le dashboard
            double interProvCost = useLocal ? 0.0 : transferCost;
            double interRegCost = useLocal ? transferCost : 0.0; // Dans l'article, local c'est intra-DC, on approxime inter-reg pour le graphe
            
            bwInterProviderCost.add(interProvCost + creationCost); // Ajouter coût de création
            bwInterRegionCost.add(interRegCost);
            bwTotalCost.add(transferCost + creationCost);
            cpuCostList.add(cpuCost);
            ioCostList.add(storageCost);
            execTimeList.add(processingMin * 60_000.0);
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs, 
                                         costPerQuery, cumulativeCost, replicaCount,
                                         bwInterProviderCost, bwInterRegionCost, bwTotalCost,
                                         cpuCostList, ioCostList, execTimeList);
    }
}
