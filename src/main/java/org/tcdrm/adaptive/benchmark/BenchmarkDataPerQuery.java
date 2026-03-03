package org.tcdrm.adaptive.benchmark;

import java.util.List;

/**
 * Benchmark data per individual query (not cumulative)
 * Used to generate charts similar to the TCDRM article
 */
public record BenchmarkDataPerQuery(
    String queryId,
    List<Integer> queryNumbers,        // Query number (1 to N)
    List<Double> timePerQueryMs,       // Time per individual query (Response time)
    List<Double> costPerQuery,         // Cost per individual query (Total cost)
    List<Double> cumulativeCost,       // Cumulative cost over time
    List<Integer> replicaCount,        // Number of replicas at each query
    
    // Nouveaux champs détaillés pour le dashboard per-query
    List<Double> bwInterProviderCost,  // BW inter-provider ($/query)
    List<Double> bwInterRegionCost,    // BW inter-region ($/query) 
    List<Double> bwTotalCost,          // BW total ($/query)
    List<Double> cpuCost,              // CPU ($/query)
    List<Double> ioCost,               // IO ($/query) - on utilise storageCost pour simplifier
    List<Double> execTimeMs            // Exec time (ms) - temps CPU seul
) {
    public double getTimeAtQuery(int queryNum) {
        int idx = queryNumbers.indexOf(queryNum);
        return idx >= 0 ? timePerQueryMs.get(idx) : 0.0;
    }

    public double getCostAtQuery(int queryNum) {
        int idx = queryNumbers.indexOf(queryNum);
        return idx >= 0 ? costPerQuery.get(idx) : 0.0;
    }

    public double getCumulativeCostAtQuery(int queryNum) {
        int idx = queryNumbers.indexOf(queryNum);
        return idx >= 0 ? cumulativeCost.get(idx) : 0.0;
    }
}
