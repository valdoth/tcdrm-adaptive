package org.tcdrm.adaptive.examples;

import org.tcdrm.adaptive.rl.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Entraînement TCDRM-ADAPTIVE avec Curriculum Learning
 * Entraînement progressif : Petites → Moyennes → Grandes requêtes
 * Utilise TcdrmEnvironmentV2 avec budget adaptatif et récompenses ajustées
 */
public class TcdrmAdaptiveCurriculumLearning {
    
    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   TCDRM-ADAPTIVE V2 : Curriculum Learning                ");
        System.out.println("==========================================================\n");
        
        // Phase 1 : Petites requêtes (1-5 GB) - 15000 requêtes, 800 épisodes
        System.out.println(">>> Phase 1 : Entraînement sur petites requêtes (1-5 GB) <<<\n");
        List<QueryConfig> smallQueries = generateQueriesInRange(1.0, 5.0, 15000, 42);
        QLearningAgent agent = trainPhase("Phase 1", smallQueries, 800, 42L, null);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Phase 2 : Moyennes requêtes (5-10 GB) - 20000 requêtes, 800 épisodes
        System.out.println(">>> Phase 2 : Entraînement sur moyennes requêtes (5-10 GB) <<<\n");
        List<QueryConfig> mediumQueries = generateQueriesInRange(5.0, 10.0, 20000, 43);
        agent = trainPhase("Phase 2", mediumQueries, 800, 43L, agent);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Phase 3 : Grandes requêtes (10-20 GB) - 15000 requêtes, 400 épisodes
        System.out.println(">>> Phase 3 : Entraînement sur grandes requêtes (10-20 GB) <<<\n");
        List<QueryConfig> largeQueries = generateQueriesInRange(10.0, 20.0, 15000, 44);
        agent = trainPhase("Phase 3", largeQueries, 400, 44L, agent);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println(">>> Validation finale sur R1 et R2 <<<\n");
        
        // Validation R1
        System.out.println("=== Validation R1 (5.3 GB) ===");
        validateQuery("R1", 5.3, agent, 45L);
        
        System.out.println();
        
        // Validation R2
        System.out.println("=== Validation R2 (11.9 GB) ===");
        validateQuery("R2", 11.9, agent, 46L);
        
        System.out.println("\n==========================================================");
        System.out.println("   Curriculum Learning terminé avec succès !              ");
        System.out.println("==========================================================");
    }
    
    /**
     * Génère des requêtes dans une plage de tailles
     */
    private static List<QueryConfig> generateQueriesInRange(double minGb, double maxGb, 
                                                            int count, long seed) {
        List<QueryConfig> queries = new ArrayList<>();
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            double dataGb = minGb + random.nextDouble() * (maxGb - minGb);
            dataGb = Math.round(dataGb * 10.0) / 10.0;
            queries.add(new QueryConfig("Q" + (i + 1), dataGb));
        }
        
        return queries;
    }
    
    /**
     * Entraîne l'agent sur une phase du curriculum
     */
    private static QLearningAgent trainPhase(String phaseName, List<QueryConfig> queries, 
                                            int numEpisodes, Long seed, 
                                            QLearningAgent existingAgent) {
        System.out.println("=== " + phaseName + " ===");
        System.out.println("Requêtes : " + queries.size());
        System.out.println("Épisodes : " + numEpisodes);
        
        double minSize = queries.stream().mapToDouble(q -> q.dataGb).min().orElse(0);
        double maxSize = queries.stream().mapToDouble(q -> q.dataGb).max().orElse(0);
        double avgSize = queries.stream().mapToDouble(q -> q.dataGb).average().orElse(0);
        
        System.out.println("Taille min : " + String.format("%.1f GB", minSize));
        System.out.println("Taille max : " + String.format("%.1f GB", maxSize));
        System.out.println("Taille moyenne : " + String.format("%.1f GB", avgSize));
        System.out.println();
        
        Random random = new Random(seed);
        
        // Créer ou réutiliser l'agent
        QLearningAgent agent;
        if (existingAgent == null) {
            // Première phase : créer nouvel agent
            TcdrmEnvironmentV2 env = new TcdrmEnvironmentV2(avgSize);
            agent = new QLearningAgent(env, 0.1, 0.95, 1.0, 0.995, 0.01);
        } else {
            // Phases suivantes : continuer avec l'agent existant
            agent = existingAgent;
        }
        
        double totalReward = 0.0;
        int episodeCount = 0;
        
        for (int episode = 0; episode < numEpisodes; episode++) {
            QueryConfig query = queries.get(random.nextInt(queries.size()));
            TcdrmEnvironmentV2 env = new TcdrmEnvironmentV2(query.dataGb);
            TcdrmState state = env.reset(seed != null ? seed + episode : null);
            
            double episodeReward = 0.0;
            
            while (true) {
                int stateIndex = state.toIndex();
                int actionIndex = agent.getQTable().chooseAction(stateIndex, agent.getEpsilon());
                TcdrmAction action = TcdrmAction.fromValue(actionIndex);
                
                Environment.StepResult<TcdrmState> result = env.step(action);
                
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
            
            if ((episode + 1) % 100 == 0) {
                double avgReward = totalReward / episodeCount;
                System.out.println(String.format("  Épisode %d/%d - Récompense moyenne: %.2f",
                    episode + 1, numEpisodes, avgReward));
                totalReward = 0.0;
                episodeCount = 0;
            }
        }
        
        System.out.println("\n" + phaseName + " terminée");
        agent.getQTable().printStatistics();
        
        return agent;
    }
    
    /**
     * Valide la performance sur une requête spécifique
     */
    private static void validateQuery(String queryId, double dataGb, QLearningAgent agent, Long seed) {
        double totalReward = 0.0;
        double totalBudget = 0.0;
        double totalCost = 0.0;
        
        for (int i = 0; i < 10; i++) {
            TcdrmEnvironmentV2 env = new TcdrmEnvironmentV2(dataGb);
            TcdrmState state = env.reset(seed != null ? seed + i : null);
            double episodeReward = 0.0;
            double initialBudget = env.getInitialBudget();
            
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
            totalBudget += env.getCurrentBudget();
            totalCost += (initialBudget - env.getCurrentBudget());
        }
        
        double avgReward = totalReward / 10;
        double avgBudget = totalBudget / 10;
        double avgCost = totalCost / 10;
        
        System.out.println("Récompense moyenne : " + String.format("%.2f", avgReward));
        System.out.println("Budget moyen restant : $" + String.format("%.2f", avgBudget));
        System.out.println("Coût moyen : $" + String.format("%.2f", avgCost));
    }
    
    static class QueryConfig {
        final String queryId;
        final double dataGb;
        
        QueryConfig(String queryId, double dataGb) {
            this.queryId = queryId;
            this.dataGb = dataGb;
        }
    }
}
