package org.tcdrm.adaptive.benchmark;

import java.util.List;

/**
 * Benchmark data per individual query (not cumulative).
 * Used to generate all charts from TCDRM V1 article (Figs 2-8).
 * 
 * Aggregate fields (totalBw*, totalCpu*, totalReplica*) are populated
 * at the end of the simulation for bar-chart generation (Figs 5 & 8).
 */
public record BenchmarkDataPerQuery(
    String queryId,
    List<Integer> queryNumbers,        // Query number (0 to N-1)
    List<Double> timePerQueryMs,       // Response time per query (ms)
    List<Double> costPerQuery,         // BW cost per individual query ($)
    List<Double> cumulativeCost,       // Cumulative BW cost ($)
    List<Integer> replicaCount,        // Number of replicas at each query

    // === Aggregate metrics for bar charts (Figs 5 & 8) ===
    double totalBwInterProviderGb,     // Total GB transferred inter-provider
    double totalBwInterRegionGb,       // Total GB transferred inter-region
    double totalBwCost,                // Total bandwidth cost ($)
    double totalCpuCost,               // Total CPU cost ($)
    double totalReplicaCost            // Total replica creation cost ($)
) {
    /** Compact constructor for backward compatibility (aggregates = 0) */
    public BenchmarkDataPerQuery(String queryId, List<Integer> queryNumbers,
                                  List<Double> timePerQueryMs, List<Double> costPerQuery,
                                  List<Double> cumulativeCost, List<Integer> replicaCount) {
        this(queryId, queryNumbers, timePerQueryMs, costPerQuery, cumulativeCost, replicaCount,
             0.0, 0.0, 0.0, 0.0, 0.0);
    }

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
    
    /** Alias used by SingleModelMetricsPlotter. */
    public List<Integer> replicaCounts() {
        return replicaCount;
    }

    /** Total cost = BW + CPU + Replica (for Fig 8 stacked bar) */
    public double totalCost() {
        return totalBwCost + totalCpuCost + totalReplicaCost;
    }
    
    // Calculer les violations SLA cumulatives (TSLA = 200ms selon article)
    public List<Double> cumulativeSLAViolations() {
        List<Double> violations = new java.util.ArrayList<>();
        double cumulative = 0.0;
        double SLA_THRESHOLD = 200.0; // ms
        
        for (double timeMs : timePerQueryMs) {
            if (timeMs > SLA_THRESHOLD) {
                cumulative += 1.0;
            }
            violations.add(cumulative);
        }
        return violations;
    }
}
