package org.tcdrm.adaptive.cloudsim;

import org.tcdrm.adaptive.rl.TcdrmAction;
import org.tcdrm.adaptive.rl.TcdrmState;
import py4j.GatewayServer;

/**
 * Adaptateur pour utiliser un modèle Q-Learning Python dans CloudSim
 * Permet de charger un modèle entraîné et de l'utiliser pour prendre des décisions
 */
public class RLPolicyAdapter {
    
    private PythonQLearningAgent pythonAgent;
    private boolean connected;
    
    public RLPolicyAdapter() {
        this.connected = false;
    }
    
    /**
     * Connecte à l'agent Python via Py4J
     * L'agent Python doit être démarré avec le modèle chargé
     */
    public void connectToPythonAgent(String host, int port) {
        try {
            // Note: La connexion inverse - Python se connecte à Java
            System.out.println("En attente de connexion de l'agent Python...");
            System.out.println("Démarrez le script Python avec le modèle chargé.");
            this.connected = true;
        } catch (Exception e) {
            System.err.println("Erreur de connexion à l'agent Python: " + e.getMessage());
            this.connected = false;
        }
    }
    
    /**
     * Définit l'agent Python (appelé par Py4J)
     */
    public void setPythonAgent(PythonQLearningAgent agent) {
        this.pythonAgent = agent;
        this.connected = true;
        System.out.println("Agent Python connecté avec succès!");
    }
    
    /**
     * Obtient la meilleure action pour un état donné en utilisant le modèle Python
     */
    public TcdrmAction getBestAction(TcdrmState state) {
        if (!connected || pythonAgent == null) {
            System.err.println("Agent Python non connecté. Utilisation de DO_NOTHING par défaut.");
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
            
            // Appeler l'agent Python pour obtenir l'action
            int actionIndex = pythonAgent.chooseAction(stateArray);
            
            // Convertir l'index en action
            return TcdrmAction.fromValue(actionIndex);
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'appel à l'agent Python: " + e.getMessage());
            return TcdrmAction.DO_NOTHING;
        }
    }
    
    /**
     * Vérifie si l'agent est connecté
     */
    public boolean isConnected() {
        return connected && pythonAgent != null;
    }
    
    /**
     * Interface pour l'agent Python (implémentée côté Python)
     */
    public interface PythonQLearningAgent {
        /**
         * Choisit une action pour un état donné
         * @param state État sous forme [budget_level, latency_level, popularity_level, num_replicas]
         * @return Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
         */
        int chooseAction(int[] state);
        
        /**
         * Obtient la Q-value pour un état et une action
         */
        double getQValue(int[] state, int action);
        
        /**
         * Obtient des informations sur le modèle
         */
        String getModelInfo();
    }
}
