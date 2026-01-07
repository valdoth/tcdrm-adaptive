package org.tcdrm.adaptive.examples;

import org.tcdrm.adaptive.rl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Entraînement TCDRM-ADAPTIVE avec requêtes variées
 * Génère des requêtes de tailles différentes pour un apprentissage robuste
 * R1 et R2 sont utilisés uniquement pour la vérification finale
 */
public class TcdrmAdaptiveTrainingVaried {
    
    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   TCDRM-ADAPTIVE : Entraînement avec requêtes variées    ");
        System.out.println("==========================================================\n");
        
        // Générer 50,000 requêtes variées pour l'entraînement
        List<QueryConfig> trainingQueries = generateVariedQueries(50000);
        
        System.out.println(">>> Génération de " + trainingQueries.size() + " requêtes variées <<<");
        printQueryStatistics(trainingQueries);
        
        // Entraîner l'agent avec requêtes variées (2000 épisodes pour 50k requêtes)
        System.out.println("\n>>> Entraînement avec requêtes variées (2000 épisodes) <<<\n");
        QLearningAgent agent = trainWithVariedQueries(trainingQueries, 2000, 42L);
        
        // Vérification avec R1
        System.out.println("\n" + "=".repeat(60));
        System.out.println(">>> Vérification avec R1 (Requête Simple - 5.3 GB) <<<\n");
        verifyWithQuery("R1", 5.3, agent, 42L);
        
        // Vérification avec R2
        System.out.println("\n" + "=".repeat(60));
        System.out.println(">>> Vérification avec R2 (Requête Complexe - 11.9 GB) <<<\n");
        verifyWithQuery("R2", 11.9, agent, 43L);
        
        System.out.println("\n==========================================================");
        System.out.println("   Entraînement et vérification terminés avec succès !    ");
        System.out.println("==========================================================");
    }
    
    /**
     * Génère des requêtes variées pour l'entraînement
     */
    private static List<QueryConfig> generateVariedQueries(int count) {
        List<QueryConfig> queries = new ArrayList<>();
        Random random = new Random(42);
        
        for (int i = 0; i < count; i++) {
            // Taille de données variée entre 1 GB et 20 GB
            double dataGb = 1.0 + random.nextDouble() * 19.0;
            
            // Arrondir à 1 décimale
            dataGb = Math.round(dataGb * 10.0) / 10.0;
            
            String queryId = "Q" + (i + 1);
            queries.add(new QueryConfig(queryId, dataGb));
        }
        
        return queries;
    }
    
    /**
     * Affiche les statistiques des requêtes générées
     */
    private static void printQueryStatistics(List<QueryConfig> queries) {
        double min = queries.stream().mapToDouble(q -> q.dataGb).min().orElse(0);
        double max = queries.stream().mapToDouble(q -> q.dataGb).max().orElse(0);
        double avg = queries.stream().mapToDouble(q -> q.dataGb).average().orElse(0);
        
        System.out.println("Statistiques des requêtes générées:");
        System.out.println("  Nombre: " + queries.size());
        System.out.println("  Taille min: " + String.format("%.1f GB", min));
        System.out.println("  Taille max: " + String.format("%.1f GB", max));
        System.out.println("  Taille moyenne: " + String.format("%.1f GB", avg));
        
        // Distribution par catégorie
        long small = queries.stream().filter(q -> q.dataGb < 5).count();
        long medium = queries.stream().filter(q -> q.dataGb >= 5 && q.dataGb < 10).count();
        long large = queries.stream().filter(q -> q.dataGb >= 10 && q.dataGb < 15).count();
        long xlarge = queries.stream().filter(q -> q.dataGb >= 15).count();
        
        System.out.println("\nDistribution:");
        System.out.println("  Petites (< 5 GB): " + small);
        System.out.println("  Moyennes (5-10 GB): " + medium);
        System.out.println("  Grandes (10-15 GB): " + large);
        System.out.println("  Très grandes (>= 15 GB): " + xlarge);
    }
    
    /**
     * Entraîne l'agent avec des requêtes variées
     */
    private static QLearningAgent trainWithVariedQueries(List<QueryConfig> queries, 
                                                         int numEpisodes, Long seed) {
        Random random = new Random(seed);
        
        // Créer un environnement avec taille moyenne pour initialisation
        double avgDataGb = queries.stream().mapToDouble(q -> q.dataGb).average().orElse(10.0);
        TcdrmEnvironment env = new TcdrmEnvironment(avgDataGb);
        
        // Créer l'agent
        QLearningAgent agent = new QLearningAgent(env, 0.1, 0.95, 1.0, 0.995, 0.01);
        
        System.out.println("=== Début de l'entraînement avec requêtes variées ===");
        System.out.println("Épisodes: " + numEpisodes);
        System.out.println("Requêtes d'entraînement: " + queries.size());
        System.out.println();
        
        double totalReward = 0.0;
        int episodeCount = 0;
        
        for (int episode = 0; episode < numEpisodes; episode++) {
            // Sélectionner une requête aléatoire
            QueryConfig query = queries.get(random.nextInt(queries.size()));
            
            // Créer environnement pour cette requête
            TcdrmEnvironment episodeEnv = new TcdrmEnvironment(query.dataGb);
            TcdrmState state = episodeEnv.reset(seed != null ? seed + episode : null);
            
            double episodeReward = 0.0;
            
            // Exécuter l'épisode
            while (true) {
                // Choisir action avec epsilon-greedy
                int stateIndex = state.toIndex();
                int actionIndex = agent.getQTable().chooseAction(stateIndex, agent.getEpsilon());
                TcdrmAction action = TcdrmAction.fromValue(actionIndex);
                
                // Exécuter l'action
                Environment.StepResult<TcdrmState> result = episodeEnv.step(action);
                
                // Mettre à jour Q-table
                int nextStateIndex = result.getNextState().toIndex();
                agent.getQTable().update(stateIndex, actionIndex, result.getReward(), 
                                        nextStateIndex, 0.1, 0.95);
                
                episodeReward += result.getReward();
                state = result.getNextState();
                
                if (result.isDone()) {
                    break;
                }
            }
            
            totalReward += episodeReward;
            episodeCount++;
            
            // Afficher progression (tous les 100 épisodes pour 10k requêtes)
            if ((episode + 1) % 100 == 0) {
                double avgReward = totalReward / episodeCount;
                System.out.println(String.format("Épisode %d/%d - Récompense moyenne: %.2f - Epsilon: %.4f - Requête: %s (%.1f GB)",
                    episode + 1, numEpisodes, avgReward, agent.getEpsilon(), query.queryId, query.dataGb));
                totalReward = 0.0;
                episodeCount = 0;
            }
        }
        
        System.out.println("\n=== Entraînement terminé ===");
        agent.getQTable().printStatistics();
        agent.getQTable().printBestActions(10);
        
        return agent;
    }
    
    /**
     * Vérifie la performance de l'agent avec une requête spécifique
     */
    private static void verifyWithQuery(String queryId, double dataGb, QLearningAgent agent, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        
        System.out.println("=== Évaluation sur " + queryId + " (" + dataGb + " GB) ===");
        
        // Évaluer sur 10 épisodes
        double totalReward = 0.0;
        double totalBudgetRemaining = 0.0;
        
        for (int i = 0; i < 10; i++) {
            TcdrmState state = env.reset(seed != null ? seed + i : null);
            double episodeReward = 0.0;
            
            while (true) {
                TcdrmAction action = agent.getBestAction(state);
                Environment.StepResult<TcdrmState> result = env.step(action);
                
                episodeReward += result.getReward();
                state = result.getNextState();
                
                if (result.isDone()) {
                    break;
                }
            }
            
            totalReward += episodeReward;
            totalBudgetRemaining += env.getCurrentBudget();
        }
        
        double avgReward = totalReward / 10;
        double avgBudget = totalBudgetRemaining / 10;
        
        System.out.println("Récompense moyenne (10 épisodes): " + String.format("%.2f", avgReward));
        System.out.println("Budget moyen restant: $" + String.format("%.2f", avgBudget));
        
        // Afficher exemples de décisions
        System.out.println("\n=== Exemples de décisions pour " + queryId + " ===");
        demonstrateLearnedPolicy(agent, dataGb);
    }
    
    private static void demonstrateLearnedPolicy(QLearningAgent agent, double dataGb) {
        TcdrmState[] testStates = {
            TcdrmState.fromContinuous(0.8, 150.0, 300, 0),
            TcdrmState.fromContinuous(0.2, 150.0, 300, 0),
            TcdrmState.fromContinuous(0.8, 50.0, 50, 1),
            TcdrmState.fromContinuous(0.5, 120.0, 200, 1),
            TcdrmState.fromContinuous(0.8, 150.0, 100, 3)
        };
        
        for (TcdrmState state : testStates) {
            TcdrmAction action = agent.getBestAction(state);
            System.out.println(String.format("%s → %s", state, action.getDescription()));
        }
    }
    
    /**
     * Classe pour stocker la configuration d'une requête
     */
    static class QueryConfig {
        final String queryId;
        final double dataGb;
        
        QueryConfig(String queryId, double dataGb) {
            this.queryId = queryId;
            this.dataGb = dataGb;
        }
    }
}
