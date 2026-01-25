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
}
