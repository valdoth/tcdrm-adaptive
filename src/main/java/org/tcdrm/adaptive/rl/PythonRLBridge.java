package org.tcdrm.adaptive.rl;

import java.util.List;

/**
 * Interface pour le pont Python RL
 * Permet à Python d'exposer les méthodes nécessaires via Py4J
 */
public interface PythonRLBridge {
    
    /**
     * Réinitialise un épisode dans l'environnement Python
     * 
     * @param dataGb Taille des données en GB
     * @param seed Seed pour la reproductibilité
     */
    void resetEpisode(double dataGb, int seed);
    
    /**
     * Obtient l'état actuel de l'environnement
     * 
     * @return État actuel sous forme de liste de doubles
     */
    List<Double> getCurrentState();
    
    /**
     * Sélectionne une action basée sur l'état actuel
     * 
     * @param state État actuel
     * @return Action sélectionnée (0=CREATE, 1=DELETE, 2=DO_NOTHING)
     */
    int selectAction(List<Double> state);
    
    /**
     * Exécute un step dans l'environnement
     * 
     * @param action Action à exécuter
     * @return Résultat du step [latency, cost, replicas, reward, done]
     */
    List<Double> executeStep(int action);
    
    /**
     * Sélectionne une action avec le modèle Q-Learning
     * 
     * @param state État [latency, budget, replicas, popularity, cost]
     * @return Action (0=NOOP, 1=REPLICATE, 2=DELETE)
     */
    int selectActionQLearning(double[] state);
    
    /**
     * Sélectionne une action avec le modèle DQN
     * 
     * @param state État [latency, budget, replicas, popularity, cost, ...]
     * @return Action (0=NOOP, 1=REPLICATE, 2=DELETE)
     */
    int selectActionDQN(double[] state);
    
    /**
     * Vérifie si le modèle Q-Learning est chargé
     */
    boolean isQLearningReady();
    
    /**
     * Vérifie si le modèle DQN est chargé
     */
    boolean isDQNReady();
    
    /**
     * Retourne les informations sur les modèles chargés
     */
    String getModelInfo();
    
    /**
     * Reset internal counters between benchmark runs (e.g., simple → complex)
     */
    void resetCounters();
}
