package org.tcdrm.adaptive.examples;

import org.tcdrm.adaptive.rl.*;

/**
 * Exemple d'entraînement de TCDRM-ADAPTIVE avec Q-Learning
 * Démontre l'apprentissage par renforcement pour la réplication adaptative
 */
public class TcdrmAdaptiveTraining {
    
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("   TCDRM-ADAPTIVE : Entraînement Q-Learning   ");
        System.out.println("===============================================\n");
        
        // Paramètres de simulation
        double dataGbR1 = 5.3;   // Requête simple (R1)
        double dataGbR2 = 11.9;  // Requête complexe (R2)
        
        // Entraîner pour R1
        System.out.println(">>> Entraînement pour R1 (Requête Simple - 5.3 GB) <<<\n");
        trainForQuery("R1", dataGbR1, 500, 42L);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Entraîner pour R2
        System.out.println(">>> Entraînement pour R2 (Requête Complexe - 11.9 GB) <<<\n");
        trainForQuery("R2", dataGbR2, 500, 42L);
        
        System.out.println("\n===============================================");
        System.out.println("   Entraînement terminé avec succès !         ");
        System.out.println("===============================================");
    }
    
    private static void trainForQuery(String queryId, double dataGb, int numEpisodes, Long seed) {
        // Créer l'environnement
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        
        // Créer l'agent Q-Learning avec hyperparamètres optimisés
        QLearningAgent agent = new QLearningAgent(
            env,
            0.1,    // alpha: taux d'apprentissage
            0.95,   // gamma: facteur de discount (importance du futur)
            1.0,    // epsilon: exploration initiale maximale
            0.995,  // epsilonDecay: décroissance lente pour bien explorer
            0.01    // epsilonMin: toujours un peu d'exploration
        );
        
        // Entraîner l'agent
        QLearningAgent.TrainingStats stats = agent.train(numEpisodes, seed);
        
        // Évaluer la politique apprise
        System.out.println("\n=== Évaluation de la politique apprise ===");
        double avgReward = agent.evaluate(10, seed);
        System.out.println("Récompense moyenne sur 10 épisodes: " + String.format("%.2f", avgReward));
        
        // Afficher quelques exemples de décisions
        System.out.println("\n=== Exemples de décisions apprises ===");
        demonstrateLearnedPolicy(agent);
    }
    
    private static void demonstrateLearnedPolicy(QLearningAgent agent) {
        // Tester différents scénarios
        TcdrmState[] testStates = {
            // Budget élevé, latence haute, popularité haute → Devrait créer réplica
            TcdrmState.fromContinuous(0.8, 150.0, 300, 0),
            
            // Budget faible, latence haute, popularité haute → Dilemme
            TcdrmState.fromContinuous(0.2, 150.0, 300, 0),
            
            // Budget élevé, latence basse, popularité basse → Ne rien faire
            TcdrmState.fromContinuous(0.8, 50.0, 50, 1),
            
            // Budget moyen, latence moyenne, popularité moyenne → Décision équilibrée
            TcdrmState.fromContinuous(0.5, 120.0, 200, 1),
            
            // Budget élevé, latence haute, trop de réplicas → Supprimer
            TcdrmState.fromContinuous(0.8, 150.0, 100, 3)
        };
        
        for (TcdrmState state : testStates) {
            TcdrmAction action = agent.getBestAction(state);
            System.out.println(String.format("%s → %s", state, action.getDescription()));
        }
    }
}
