package org.tcdrm.adaptive.rl;

/**
 * Interface for the Python RL bridge via Py4J.
 * 
 * Supports both inference (pre-trained models) and online learning.
 * 
 * Actions: 0=NOOP, 1=REPLICATE, 2=DELETE
 * State: [latency, budget, replicas, normalizedPopularity, cost,
 *         tSlaViolation, cSlaViolation, queryProgress]
 */
public interface PythonRLBridge {
    
    /** Select action using Q-Learning (online learning). */
    int selectActionQLearning(double[] state);
    
    /** Select action using DQN (online learning). */
    int selectActionDQN(double[] state);
    
    /** 
     * Update Q-Learning agent with reward from last action.
     * Called after each query to enable online learning.
     * 
     * @param reward The reward signal (positive = good, negative = bad)
     * @param nextState The new state after action execution
     * @param done True if episode is finished
     */
    void updateQLearning(double reward, double[] nextState, boolean done);
    
    /**
     * Update DQN agent with reward from last action.
     * Called after each query to enable online learning.
     */
    void updateDQN(double reward, double[] nextState, boolean done);
    
    /** Check if Q-Learning model is ready. */
    boolean isQLearningReady();
    
    /** Check if DQN model is ready. */
    boolean isDQNReady();
    
    /** Return info about loaded models. */
    String getModelInfo();
    
    /** Reset internal counters between benchmark runs. */
    void resetCounters();
    
    /** Save learned models to disk. */
    void saveModels();
}
