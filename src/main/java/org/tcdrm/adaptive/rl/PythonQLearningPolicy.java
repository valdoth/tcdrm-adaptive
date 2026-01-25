package org.tcdrm.adaptive.rl;

/**
 * Politique de réplication basée sur Q-Learning Python (via Py4J)
 */
public class PythonQLearningPolicy {
    
    private PythonQLearningAgent pythonAgent;
    private int totalActions = 0;
    
    /**
     * Constructeur avec l'agent Python connecté via Py4J
     */
    public PythonQLearningPolicy(PythonQLearningAgent pythonAgent) {
        this.pythonAgent = pythonAgent;
    }
    
    /**
     * Choisit une action en utilisant le modèle Python
     */
    public TcdrmAction chooseAction(TcdrmState state) {
        if (pythonAgent == null || !pythonAgent.isModelLoaded()) {
            System.err.println("⚠️  Agent Python non chargé. Utilisation de DO_NOTHING.");
            return TcdrmAction.DO_NOTHING;
        }
        
        try {
            // Convertir l'état en array pour Python
            int[] stateArray = new int[]{
                state.getBudgetLevel().ordinal(),
                state.getLatencyLevel().ordinal(),
                state.getPopularityLevel().ordinal(),
                state.getReplicaCount()
            };
            
            // Appeler l'agent Python
            int actionIndex = pythonAgent.chooseAction(stateArray);
            totalActions++;
            
            return TcdrmAction.fromValue(actionIndex);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'appel Python: " + e.getMessage());
            return TcdrmAction.DO_NOTHING;
        }
    }
    
    /**
     * Obtient la Q-value pour un état et une action
     */
    public double getQValue(TcdrmState state, TcdrmAction action) {
        if (pythonAgent == null || !pythonAgent.isModelLoaded()) {
            return 0.0;
        }
        
        try {
            int[] stateArray = new int[]{
                state.getBudgetLevel().ordinal(),
                state.getLatencyLevel().ordinal(),
                state.getPopularityLevel().ordinal(),
                state.getReplicaCount()
            };
            
            return pythonAgent.getQValue(stateArray, action.getValue());
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    public boolean isModelLoaded() {
        return pythonAgent != null && pythonAgent.isModelLoaded();
    }
    
    public int getTotalActions() {
        return totalActions;
    }
    
    public void resetStatistics() {
        totalActions = 0;
    }
    
    @Override
    public String toString() {
        return "PythonQLearningPolicy(modelLoaded=" + isModelLoaded() + ", actions=" + totalActions + ")";
    }
}
