package org.tcdrm.adaptive.runner;

import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.rl.PythonRLBridge;

/**
 * Orchestrates benchmark execution for all models.
 */
public final class BenchmarkRunner {
    
    private BenchmarkRunner() {}
    
    /** Run NoRepLc benchmark */
    public static BenchmarkDataPerQuery runNoRep(long seed, boolean complex, String queryId) {
        NoRepBenchmarkPerQuery benchmark = new NoRepBenchmarkPerQuery(seed, complex);
        return benchmark.computeBenchmark(queryId);
    }
    
    /** Run TCDRM benchmark */
    public static BenchmarkDataPerQuery runTcdrm(long seed, boolean complex, String queryId) {
        TcdrmBenchmarkPerQuery benchmark = new TcdrmBenchmarkPerQuery(seed, complex);
        return benchmark.computeBenchmark(queryId);
    }
    
    /** Run RL benchmark (Q-Learning or DQN) */
    public static BenchmarkDataPerQuery runRL(PythonRLBridge bridge, String modelType, 
                                               String queryId, boolean complex, long seed) {
        System.out.println("  >>> " + modelType.toUpperCase() + " (" + (complex ? "complex" : "simple") + ")...");
        
        if ("qlearning".equals(modelType) && !bridge.isQLearningReady()) {
            throw new RuntimeException("Q-Learning model not loaded");
        }
        if ("dqn".equals(modelType) && !bridge.isDQNReady()) {
            throw new RuntimeException("DQN model not loaded");
        }
        
        RealRLBenchmark rlBench = new RealRLBenchmark(bridge, modelType, seed, complex);
        BenchmarkDataPerQuery result = rlBench.computeBenchmark(queryId);
        System.out.println("      " + modelType.toUpperCase() + " done");
        return result;
    }
    
    /** Log sample values for debugging */
    public static void logSampleValues(String label, BenchmarkDataPerQuery data) {
        int mid = Math.min(500, data.queryNumbers().size() - 1);
        int end = data.queryNumbers().size() - 1;
        System.out.printf("  %s: time[0]=%.1fms, time[%d]=%.1fms, cumulBW[%d]=$%.2f, replicas[%d]=%d%n",
            label, data.timePerQueryMs().get(0), mid, data.timePerQueryMs().get(mid),
            end, data.cumulativeCost().get(end), end, data.replicaCount().get(end));
    }
    
    /** Log bar chart values for debugging */
    public static void logBarValues(String label, BenchmarkDataPerQuery data) {
        System.out.printf("  %s: interProviderGB=%.1f, interRegionGB=%.1f, bwCost=$%.2f, cpuCost=$%.2f, replicaCost=$%.2f%n",
            label, data.totalBwInterProviderGb(), data.totalBwInterRegionGb(),
            data.totalBwCost(), data.totalCpuCost(), data.totalReplicaCost());
    }
}
