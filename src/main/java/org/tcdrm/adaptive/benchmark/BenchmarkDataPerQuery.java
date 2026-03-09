package org.tcdrm.adaptive.benchmark;

import java.util.List;

/**
 * Benchmark data per individual query (not cumulative)
 * Used to generate charts similar to the TCDRM article
 */
public record BenchmarkDataPerQuery(
    String queryId,
    List<Integer> queryNumbers,        // Query number (1 to N)
    List<Double> timePerQueryMs,       // Time per individual query
    List<Double> costPerQuery,         // Cost per individual query
    List<Double> cumulativeCost,       // Cumulative cost over time
    List<Integer> replicaCount         // Number of replicas at each query
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
    
    // Alias pour compatibilité avec AllModelsMetricsPlotter
    public List<Integer> replicaCounts() {
        return replicaCount;
    }
    
    // Calculer les violations SLA cumulatives (TSLA = 200ms selon article)
    public List<Double> cumulativeSLAViolations() {
        List<Double> violations = new java.util.ArrayList<>();
        double cumulative = 0.0;
        double SLA_THRESHOLD = 0.200; // 200ms en secondes
        
        for (double timeMs : timePerQueryMs) {
            if (timeMs > SLA_THRESHOLD) {
                cumulative += 1.0;
            }
            violations.add(cumulative);
        }
        return violations;
    }
}
