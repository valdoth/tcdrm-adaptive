package org.tcdrm.adaptive.rl;

/**
 * Interface for the Python RL bridge via Py4J.
 * 
 * Supports both inference (pre-trained models) and online learning.
 * 
 * Actions: 0=NOOP, 1=REPLICATE, 2=DELETE
 * State: [latency, budget, replicas, normalizedPopularity, cost,
 *         tSlaViolation, cSlaViolation, queryProgress, pSlaProgress]
 */
public interface PythonRLBridge {
    
    /**
     * Select action using Q-Learning (online learning).
     *
     * Les flags de validité viennent de l'environnement Java (mêmes règles que
     * TrainingEnvironment.getActionMask : éligibilité popularité + limites physiques)
     * pour que l'éval présente à l'agent EXACTEMENT le même masque d'actions que
     * l'entraînement — aucune divergence train/eval.
     */
    int selectActionQLearning(double[] state, boolean canReplicate, boolean canDelete);

    /** Select action using Rainbow DQN (online learning). Mêmes flags que ci-dessus. */
    int selectActionRainbow(double[] state, boolean canReplicate, boolean canDelete);

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
     * Update Rainbow DQN agent with reward from last action.
     * Called after each query to enable online learning.
     */
    void updateRainbow(double reward, double[] nextState, boolean done);

    /** Check if Q-Learning model is ready. */
    boolean isQLearningReady();

    /** Check if Rainbow DQN model is ready. */
    boolean isRainbowReady();
    
    /** Return info about loaded models. */
    String getModelInfo();
    
    /** Reset internal counters between benchmark runs. */
    void resetCounters();
    
    /** Save learned models to disk. */
    void saveModels();

    /** Optional: preferred execution region (e.g., US/EU/AS). */
    default String getPreferredExecRegion() { return null; }
}
