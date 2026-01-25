package org.tcdrm.adaptive.rl;

import java.util.List;

/**
 * Entry point pour Py4J - exposé à Python
 * Python enregistre sa Q-table ici, Java l'utilise pour les décisions
 */
public class PythonQLearningAgent {
    
    private double[][] qTable;  // Q-table [états][actions]
    private boolean modelLoaded = false;
    private String modelInfo = "";
    private int nStates = 108;  // 3*3*3*4
    private int nActions = 3;   // CREATE, DELETE, DO_NOTHING
    
    /**
     * Appelé par Python pour enregistrer la Q-table
     * @param qTableList Q-table sous forme de liste de listes
     * @param info Informations sur le modèle
     */
    public void registerQTable(List<List<Double>> qTableList, String info) {
        try {
            // Convertir List<List<Double>> en double[][]
            int rows = qTableList.size();
            int cols = qTableList.get(0).size();
            
            this.qTable = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                List<Double> row = qTableList.get(i);
                for (int j = 0; j < cols; j++) {
                    this.qTable[i][j] = row.get(j);
                }
            }
            
            this.modelInfo = info;
            this.modelLoaded = true;
            System.out.println("✅ Q-Table Python enregistrée: " + info);
            System.out.println("   Dimensions: " + rows + " états × " + cols + " actions");
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'enregistrement de la Q-table: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Choisit une action en utilisant la Q-table (politique greedy)
     * @param state État sous forme de tableau [budget, latency, popularity, replicas]
     * @return Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
     */
    public int chooseAction(int[] state) {
        if (!modelLoaded || qTable == null) {
            System.err.println("⚠️  Q-table non chargée, retour action par défaut (DO_NOTHING)");
            return 2; // DO_NOTHING
        }
        
        try {
            // Convertir l'état en index
            int stateIndex = stateToIndex(state);
            
            // Politique greedy: choisir l'action avec la plus haute Q-value
            int bestAction = 0;
            double bestQValue = qTable[stateIndex][0];
            
            for (int a = 1; a < nActions; a++) {
                if (qTable[stateIndex][a] > bestQValue) {
                    bestQValue = qTable[stateIndex][a];
                    bestAction = a;
                }
            }
            
            return bestAction;
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du choix d'action: " + e.getMessage());
            return 2; // DO_NOTHING par défaut
        }
    }
    
    /**
     * Obtient la Q-value pour un état et une action
     * @param state État
     * @param action Action
     * @return Q-value
     */
    public double getQValue(int[] state, int action) {
        if (!modelLoaded || qTable == null) {
            return 0.0;
        }
        
        try {
            int stateIndex = stateToIndex(state);
            return qTable[stateIndex][action];
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Convertit un état en index de la Q-table
     * État: [budget, latency, popularity, replicas]
     * Index = budget * 36 + latency * 12 + popularity * 4 + replicas
     */
    private int stateToIndex(int[] state) {
        int budget = state[0];
        int latency = state[1];
        int popularity = state[2];
        int replicas = state[3];
        
        return budget * 36 + latency * 12 + popularity * 4 + replicas;
    }
    
    /**
     * Retourne les informations sur le modèle
     */
    public String getModelInfo() {
        return modelLoaded ? modelInfo : "Aucun modèle chargé";
    }
    
    /**
     * Vérifie si un modèle est chargé
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }
    
    // ========== Méthodes pour exécution d'épisodes complets ==========
    
    /**
     * Réinitialise un épisode dans l'environnement Python
     * Cette méthode est appelée par Python via callback
     */
    public void resetEpisode(double dataGb, Long seed) {
        // Cette méthode sera implémentée côté Python
        // Java appelle Python via callback pour reset l'environnement
        System.out.println("   Reset épisode: dataGb=" + dataGb + ", seed=" + seed);
    }
    
    /**
     * Obtient l'état actuel depuis Python
     * Retourne: [budget_ratio, latency, access_count_norm, replica_count, ...]
     */
    public double[] getCurrentState() {
        // Cette méthode sera implémentée côté Python
        // Pour l'instant, retourne un état par défaut
        return new double[]{0.5, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    }
    
    /**
     * Sélectionne une action en utilisant la Q-table entraînée
     * @param state État continu depuis l'environnement
     * @return Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
     */
    public int selectAction(double[] state) {
        if (!modelLoaded || qTable == null) {
            System.err.println("⚠️  Q-table non chargée, retour action par défaut (DO_NOTHING)");
            return 2; // DO_NOTHING
        }
        
        try {
            // Discrétiser l'état continu en index discret
            int stateIndex = discretizeState(state);
            
            // Politique greedy: choisir l'action avec la plus haute Q-value
            int bestAction = 0;
            double bestQValue = qTable[stateIndex][0];
            
            for (int a = 1; a < nActions; a++) {
                if (qTable[stateIndex][a] > bestQValue) {
                    bestQValue = qTable[stateIndex][a];
                    bestAction = a;
                }
            }
            
            return bestAction;
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du choix d'action: " + e.getMessage());
            return 2; // DO_NOTHING par défaut
        }
    }
    
    /**
     * Exécute un step dans l'environnement Python
     * @param action Action à exécuter
     * @return [latency, cost, replicas, reward, done]
     */
    public double[] executeStep(int action) {
        // Cette méthode sera implémentée côté Python
        // Pour l'instant, retourne des valeurs par défaut
        return new double[]{100.0, 0.5, 1.0, 0.0, 0.0};
    }
    
    /**
     * Discrétise un état continu en index discret
     * État continu: [budget_ratio, latency, access_count_norm, replica_count, ...]
     * 
     * Discrétisation:
     * - Budget: LOW (0-0.33), MEDIUM (0.33-0.66), HIGH (0.66-1.0)
     * - Latency: LOW (<100ms), MEDIUM (100-200ms), HIGH (>200ms)
     * - Popularity: LOW (<150), MEDIUM (150-250), HIGH (>250)
     * - Replicas: 0, 1, 2, 3
     * 
     * Index = budget * 36 + latency * 12 + popularity * 4 + replicas
     */
    private int discretizeState(double[] observation) {
        double budget_ratio = observation[0];
        double latency = observation[1];
        double access_count_norm = observation[2];
        int replica_count = (int) observation[3];
        
        // Discrétiser budget (3 niveaux)
        int budget_level;
        if (budget_ratio < 0.33) {
            budget_level = 0;  // LOW
        } else if (budget_ratio < 0.66) {
            budget_level = 1;  // MEDIUM
        } else {
            budget_level = 2;  // HIGH
        }
        
        // Discrétiser latency (3 niveaux)
        int latency_level;
        if (latency < 100.0) {
            latency_level = 0;  // LOW
        } else if (latency < 200.0) {
            latency_level = 1;  // MEDIUM
        } else {
            latency_level = 2;  // HIGH
        }
        
        // Discrétiser popularity (3 niveaux)
        // access_count_norm est entre 0 et 1, on le convertit en nombre d'accès
        double access_count = access_count_norm * 1000.0;
        int popularity_level;
        if (access_count < 150.0) {
            popularity_level = 0;  // LOW
        } else if (access_count < 250.0) {
            popularity_level = 1;  // MEDIUM
        } else {
            popularity_level = 2;  // HIGH
        }
        
        // Limiter replica_count à [0, 3]
        replica_count = Math.max(0, Math.min(3, replica_count));
        
        // Calculer l'index
        return budget_level * 36 + latency_level * 12 + popularity_level * 4 + replica_count;
    }
}
