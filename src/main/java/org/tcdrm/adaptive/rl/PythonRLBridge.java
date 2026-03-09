package org.tcdrm.adaptive.rl;

/**
 * Interface for the Python RL bridge via Py4J.
 * 
 * Actions: 0=NOOP, 1=REPLICATE, 2=DELETE
 * State: [latency, budget, replicas, normalizedPopularity, cost,
 *         tSlaViolation, cSlaViolation, queryProgress]
 */
public interface PythonRLBridge {
    
    /** Select action using Q-Learning with TCDRM-ADAPTIVE strategy. */
    int selectActionQLearning(double[] state);
    
    /** Select action using DQN with TCDRM-ADAPTIVE strategy. */
    int selectActionDQN(double[] state);
    
    /** Check if Q-Learning model is loaded. */
    boolean isQLearningReady();
    
    /** Check if DQN model is loaded. */
    boolean isDQNReady();
    
    /** Return info about loaded models. */
    String getModelInfo();
    
    /** Reset internal counters between benchmark runs (e.g., simple → complex). */
    void resetCounters();
}
