package org.tcdrm.adaptive.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * NoRep Benchmark that tracks metrics per individual query
 * Always uses remote access (inter-provider)
 */
public class NoRepBenchmarkPerQuery {
    private static final int MAX_QUERIES = 1000;
    
    // Inter-provider (always remote)
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;
    private static final double COST_BW_INTER_PROVIDER = 0.01;   // Article: 0.01

    private static final double CPU_COST_PER_HOUR = 0.02;        // Article: 0.020
    private static final double PROCESSING_MIN_PER_GB = 0.5;
    
    private static final double JITTER_RATIO = 0.05;
    private static final double CPU_JITTER_RATIO = 0.05;
    
    // Variabilité de la latence inter-provider (congestion réseau)
    private static final double LATENCY_VARIATION_RATIO = 0.15;

    private final Random rnd;

    public NoRepBenchmarkPerQuery(long seed) {
        this.rnd = new Random(seed);
    }

    public BenchmarkDataPerQuery computeBenchmark(String queryId, List<Double> fragmentSizesGb) {
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCount = new ArrayList<>();

        double totalCost = 0.0;
        double dataGb = fragmentSizesGb.stream().mapToDouble(d -> d).sum();

        for (int q = 0; q < MAX_QUERIES; q++) {
            // Network parameters (always remote with variability)
            double bwGbps = BW_REMOTE_GBPS;
            // Latence variable pour simuler la congestion réseau inter-provider
            double latencyMs = LAT_REMOTE_MS * (1.0 + LATENCY_VARIATION_RATIO * (rnd.nextDouble() * 2 - 1));
            double costPerGb = COST_BW_INTER_PROVIDER;

            // Transfer time with jitter
            double transferMs = (dataGb * 8_000.0 / bwGbps) + latencyMs;
            transferMs *= (1.0 + JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Processing time with jitter
            double processingMin = dataGb * PROCESSING_MIN_PER_GB;
            processingMin *= (1.0 + CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Total time for this query
            double queryTimeMs = transferMs + processingMin * 60_000.0;
            
            // Costs for this query
            double transferCost = dataGb * COST_BW_INTER_PROVIDER;
            double cpuCost = (processingMin / 60.0) * CPU_COST_PER_HOUR;
            
            // Coût additionnel pour la gestion inter-provider (overhead)
            double overheadCost = transferCost * 0.05; // 5% overhead
            
            double queryCost = transferCost + cpuCost + overheadCost;
            totalCost += queryCost;

            queryNumbers.add(q);
            timePerQueryMs.add(queryTimeMs / 1000.0); // Convert to seconds
            costPerQuery.add(queryCost);
            cumulativeCost.add(totalCost);
            replicaCount.add(0);
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs, 
                                         costPerQuery, cumulativeCost, replicaCount);
    }
}
