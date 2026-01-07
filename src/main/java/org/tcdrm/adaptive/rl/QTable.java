package org.tcdrm.adaptive.rl;

import java.util.Arrays;
import java.util.Random;

/**
 * Q-Table pour l'algorithme Q-Learning
 * Stocke les valeurs Q(s, a) pour chaque paire (état, action)
 */
public class QTable {
    
    private final double[][] qValues;
    private final int numStates;
    private final int numActions;
    private final Random random;
    
    public QTable(int numStates, int numActions) {
        this.numStates = numStates;
        this.numActions = numActions;
        this.qValues = new double[numStates][numActions];
        this.random = new Random();
        
        // Initialiser toutes les valeurs Q à 0
        for (int s = 0; s < numStates; s++) {
            Arrays.fill(qValues[s], 0.0);
        }
    }
    
    /**
     * Obtient la valeur Q pour une paire (état, action)
     */
    public double get(int state, int action) {
        validateIndices(state, action);
        return qValues[state][action];
    }
    
    /**
     * Met à jour la valeur Q pour une paire (état, action)
     */
    public void set(int state, int action, double value) {
        validateIndices(state, action);
        qValues[state][action] = value;
    }
    
    /**
     * Obtient la meilleure action pour un état donné (exploitation)
     * Retourne l'action avec la plus haute valeur Q
     */
    public int getBestAction(int state) {
        if (state < 0 || state >= numStates) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
        
        int bestAction = 0;
        double bestValue = qValues[state][0];
        
        for (int a = 1; a < numActions; a++) {
            if (qValues[state][a] > bestValue) {
                bestValue = qValues[state][a];
                bestAction = a;
            }
        }
        
        return bestAction;
    }
    
    /**
     * Obtient la valeur Q maximale pour un état donné
     */
    public double getMaxQ(int state) {
        if (state < 0 || state >= numStates) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
        
        double maxQ = qValues[state][0];
        for (int a = 1; a < numActions; a++) {
            maxQ = Math.max(maxQ, qValues[state][a]);
        }
        
        return maxQ;
    }
    
    /**
     * Choisit une action en utilisant la stratégie epsilon-greedy
     * 
     * @param state État actuel
     * @param epsilon Probabilité d'exploration (0.0 = toujours exploitation, 1.0 = toujours exploration)
     * @return Action choisie
     */
    public int chooseAction(int state, double epsilon) {
        if (random.nextDouble() < epsilon) {
            // Exploration: action aléatoire
            return random.nextInt(numActions);
        } else {
            // Exploitation: meilleure action connue
            return getBestAction(state);
        }
    }
    
    /**
     * Met à jour la Q-table en utilisant la formule de Q-Learning
     * Q(s, a) ← Q(s, a) + α[r + γ max Q(s', a') - Q(s, a)]
     * 
     * @param state État actuel
     * @param action Action prise
     * @param reward Récompense reçue
     * @param nextState État suivant
     * @param alpha Taux d'apprentissage
     * @param gamma Facteur de discount
     */
    public void update(int state, int action, double reward, int nextState, 
                      double alpha, double gamma) {
        validateIndices(state, action);
        
        double currentQ = qValues[state][action];
        double maxNextQ = getMaxQ(nextState);
        
        // Formule de Q-Learning
        double newQ = currentQ + alpha * (reward + gamma * maxNextQ - currentQ);
        
        qValues[state][action] = newQ;
    }
    
    /**
     * Affiche les statistiques de la Q-table
     */
    public void printStatistics() {
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int nonZeroCount = 0;
        
        for (int s = 0; s < numStates; s++) {
            for (int a = 0; a < numActions; a++) {
                double value = qValues[s][a];
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
                if (Math.abs(value) > 1e-10) {
                    nonZeroCount++;
                }
            }
        }
        
        int totalCells = numStates * numActions;
        double avg = sum / totalCells;
        
        System.out.println("=== Q-Table Statistics ===");
        System.out.println("States: " + numStates);
        System.out.println("Actions: " + numActions);
        System.out.println("Total cells: " + totalCells);
        System.out.println("Non-zero cells: " + nonZeroCount + " (" + 
                          String.format("%.1f%%", 100.0 * nonZeroCount / totalCells) + ")");
        System.out.println("Min Q-value: " + String.format("%.4f", min));
        System.out.println("Max Q-value: " + String.format("%.4f", max));
        System.out.println("Avg Q-value: " + String.format("%.4f", avg));
    }
    
    /**
     * Affiche les meilleures actions pour quelques états
     */
    public void printBestActions(int numStatesToShow) {
        System.out.println("\n=== Best Actions for Sample States ===");
        int step = Math.max(1, numStates / numStatesToShow);
        
        for (int s = 0; s < numStates; s += step) {
            TcdrmState state = TcdrmState.fromIndex(s);
            int bestAction = getBestAction(s);
            double bestValue = qValues[s][bestAction];
            
            System.out.println(String.format("State %d %s → Action: %s (Q=%.4f)",
                s, state, TcdrmAction.fromValue(bestAction), bestValue));
        }
    }
    
    private void validateIndices(int state, int action) {
        if (state < 0 || state >= numStates) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
        if (action < 0 || action >= numActions) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
    }
    
    public int getNumStates() {
        return numStates;
    }
    
    public int getNumActions() {
        return numActions;
    }
}
