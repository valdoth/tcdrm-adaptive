package org.tcdrm.adaptive.examples;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.BenchmarkDataPerQuery;
import org.tcdrm.adaptive.benchmark.NoRepBenchmarkPerQuery;
import org.tcdrm.adaptive.benchmark.TcdrmBenchmarkPerQuery;
import org.tcdrm.adaptive.rl.*;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Comparaison V2 : Utilise le modèle pré-entraîné avec 10,000 requêtes
 * et TcdrmEnvironmentV2 avec budget adaptatif et récompenses ajustées
 */
public class TcdrmAdaptiveComparisonV2 {
    
    public static void main(String[] args) throws IOException {
        System.out.println("==========================================================");
        System.out.println("   TCDRM vs TCDRM-ADAPTIVE vs NoRep : Comparaison         ");
        System.out.println("==========================================================\n");
        
        // Paramètres
        List<Double> fragmentSizesR1 = Arrays.asList(1.5, 2.0, 1.8);
        List<Double> fragmentSizesR2 = Arrays.asList(1.8, 2.2, 1.5, 2.5, 1.9, 2.0);
        int replicationFactor = 3;
        
        // Entraîner le modèle avec 50,000 requêtes (curriculum learning)
        System.out.println(">>> Entraînement du modèle avec 50,000 requêtes <<<\n");
        QLearningAgent agent = trainWithCurriculumLearning(42L);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Comparer pour R1
        System.out.println(">>> Comparaison pour R1 (Requête Simple) <<<\n");
        compareForQuery("R1", fragmentSizesR1, replicationFactor, agent, 42L);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Comparer pour R2
        System.out.println(">>> Comparaison pour R2 (Requête Complexe) <<<\n");
        compareForQuery("R2", fragmentSizesR2, replicationFactor, agent, 43L);
        
        System.out.println("\n==========================================================");
        System.out.println("   Comparaison terminée avec succès !                      ");
        System.out.println("==========================================================");
    }
    
    /**
     * Entraîne le modèle avec curriculum learning (10,000 requêtes)
     */
    private static QLearningAgent trainWithCurriculumLearning(Long seed) {
        Random random = new Random(seed);
        
        // Phase 1 : Petites requêtes (15000)
        System.out.println("Phase 1 : Petites requêtes (1-5 GB) - 15000 requêtes, 800 épisodes");
        List<QueryConfig> smallQueries = generateQueriesInRange(1.0, 5.0, 15000, seed);
        QLearningAgent agent = trainPhase(smallQueries, 800, seed, null);
        
        // Phase 2 : Moyennes requêtes (20000)
        System.out.println("\nPhase 2 : Moyennes requêtes (5-10 GB) - 20000 requêtes, 800 épisodes");
        List<QueryConfig> mediumQueries = generateQueriesInRange(5.0, 10.0, 20000, seed + 1);
        agent = trainPhase(mediumQueries, 800, seed + 1, agent);
        
        // Phase 3 : Grandes requêtes (15000)
        System.out.println("\nPhase 3 : Grandes requêtes (10-20 GB) - 15000 requêtes, 400 épisodes");
        List<QueryConfig> largeQueries = generateQueriesInRange(10.0, 20.0, 15000, seed + 2);
        agent = trainPhase(largeQueries, 400, seed + 2, agent);
        
        System.out.println("\n✓ Entraînement terminé avec 50,000 requêtes");
        agent.getQTable().printStatistics();
        
        // Générer graphiques de convergence
        try {
            generateConvergenceGraphs(agent);
        } catch (IOException e) {
            System.err.println("Erreur lors de la génération des graphiques de convergence: " + e.getMessage());
        }
        
        return agent;
    }
    
    /**
     * Génère les graphiques de convergence de l'entraînement
     */
    private static void generateConvergenceGraphs(QLearningAgent agent) throws IOException {
        List<Double> rewards = agent.getTrainingRewards();
        if (rewards.isEmpty()) {
            return;
        }
        
        // Graphique de convergence global
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Convergence de l'entraînement TCDRM-ADAPTIVE")
            .xAxisTitle("Épisode")
            .yAxisTitle("Récompense")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        List<Integer> episodes = new ArrayList<>();
        for (int i = 0; i < rewards.size(); i++) {
            episodes.add(i + 1);
        }
        
        // Moyenne mobile sur 50 épisodes
        double[] smoothed = movingAverage(rewards, 50);
        List<Double> smoothedList = toList(smoothed);
        
        XYSeries series = chart.addSeries("Récompense (moyenne mobile 50)", episodes, smoothedList);
        series.setLineColor(new Color(44, 160, 44));
        series.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_adaptive_convergence.png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de convergence: " + filename);
    }
    
    private static List<QueryConfig> generateQueriesInRange(double minGb, double maxGb, int count, long seed) {
        List<QueryConfig> queries = new ArrayList<>();
        Random random = new Random(seed);
        
        for (int i = 0; i < count; i++) {
            double dataGb = minGb + random.nextDouble() * (maxGb - minGb);
            dataGb = Math.round(dataGb * 10.0) / 10.0;
            queries.add(new QueryConfig("Q" + (i + 1), dataGb));
        }
        
        return queries;
    }
    
    private static QLearningAgent trainPhase(List<QueryConfig> queries, int numEpisodes, 
                                            Long seed, QLearningAgent existingAgent) {
        Random random = new Random(seed);
        double avgSize = queries.stream().mapToDouble(q -> q.dataGb).average().orElse(10.0);
        
        QLearningAgent agent;
        if (existingAgent == null) {
            TcdrmEnvironmentV2 env = new TcdrmEnvironmentV2(avgSize);
            agent = new QLearningAgent(env, 0.1, 0.95, 1.0, 0.995, 0.01);
        } else {
            agent = existingAgent;
        }
        
        List<Double> episodeRewards = new ArrayList<>();
        
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
            
            episodeRewards.add(episodeReward);
        }
        
        // Stocker les récompenses pour graphique de convergence
        if (existingAgent == null) {
            agent.setTrainingRewards(episodeRewards);
        } else {
            List<Double> existingRewards = agent.getTrainingRewards();
            existingRewards.addAll(episodeRewards);
        }
        
        return agent;
    }
    
    private static void compareForQuery(String queryId, List<Double> fragmentSizes, 
                                       int replicationFactor, QLearningAgent agent, Long seed) throws IOException {
        double dataGb = fragmentSizes.stream().mapToDouble(d -> d).sum();
        
        // 1. Exécuter TCDRM statique
        System.out.println("=== Exécution TCDRM (statique) ===");
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, seed);
        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        System.out.println("TCDRM - Coût cumulatif final: $" + 
            String.format("%.2f", tcdrmData.cumulativeCost().get(tcdrmData.cumulativeCost().size() - 1)));
        
        // 2. Exécuter NoRep
        System.out.println("\n=== Exécution NoRep ===");
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(seed);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);
        System.out.println("NoRep - Coût cumulatif final: $" + 
            String.format("%.2f", norepData.cumulativeCost().get(norepData.cumulativeCost().size() - 1)));
        
        // 3. Simuler TCDRM-ADAPTIVE sur 2000 requêtes (avec modèle pré-entraîné)
        System.out.println("\n=== Simulation TCDRM-ADAPTIVE (2000 requêtes) ===");
        AdaptiveSimulationResult adaptiveResult = simulateAdaptiveV2(agent, dataGb, seed);
        System.out.println("TCDRM-ADAPTIVE - Récompense totale: " + 
            String.format("%.2f", adaptiveResult.totalReward));
        System.out.println("TCDRM-ADAPTIVE - Budget restant: $" + 
            String.format("%.2f", adaptiveResult.finalBudget));
        System.out.println("TCDRM-ADAPTIVE - Coût total: $" + 
            String.format("%.2f", adaptiveResult.totalCost));
        
        // 4. Générer graphiques comparatifs (2 versions)
        // Version 1 : Seulement NoRep vs TCDRM statique
        generateComparisonGraphs2Curves(queryId, tcdrmData, norepData, fragmentSizes);
        
        // Version 2 : Les 3 (NoRep vs TCDRM vs TCDRM-ADAPTIVE)
        generateComparisonGraphs3Curves(queryId, tcdrmData, norepData, adaptiveResult, fragmentSizes);
        
        // 5. Afficher résumé
        printComparisonSummary(queryId, tcdrmData, norepData, adaptiveResult);
    }
    
    /**
     * Simule TCDRM-ADAPTIVE V2 avec TcdrmEnvironmentV2
     */
    private static AdaptiveSimulationResult simulateAdaptiveV2(QLearningAgent agent, double dataGb, Long seed) {
        TcdrmEnvironmentV2 env = new TcdrmEnvironmentV2(dataGb);
        TcdrmState state = env.reset(seed);
        
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> rewards = new ArrayList<>();
        List<Double> budgets = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();
        List<Integer> replicaCounts = new ArrayList<>();
        
        double totalReward = 0.0;
        double initialBudget = env.getInitialBudget();
        int query = 0;
        
        while (query < 2000) {
            TcdrmAction action = agent.getBestAction(state);
            Environment.StepResult<TcdrmState> result = env.step(action);
            
            queryNumbers.add(query);
            rewards.add(result.getReward());
            budgets.add(env.getCurrentBudget());
            latencies.add(env.getCurrentLatency());
            replicaCounts.add(env.getCurrentReplicaCount());
            
            totalReward += result.getReward();
            state = result.getNextState();
            query++;
            
            if (result.isDone()) {
                break;
            }
        }
        
        double totalCost = initialBudget - env.getCurrentBudget();
        
        return new AdaptiveSimulationResult(queryNumbers, rewards, budgets, latencies, 
                                           replicaCounts, totalReward, env.getCurrentBudget(), totalCost);
    }
    
    /**
     * Génère les graphiques avec 2 courbes (NoRep + TCDRM statique)
     */
    private static void generateComparisonGraphs2Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                        BenchmarkDataPerQuery norepData,
                                                        List<Double> fragmentSizes) throws IOException {
        generateCostGraph2Curves(queryId, tcdrmData, norepData);
        generateResponseTimeGraph2Curves(queryId, tcdrmData, norepData, fragmentSizes);
        generateReplicaCountGraph2Curves(queryId, tcdrmData);
    }
    
    /**
     * Génère les graphiques avec 3 courbes (NoRep + TCDRM + TCDRM-ADAPTIVE)
     */
    private static void generateComparisonGraphs3Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                        BenchmarkDataPerQuery norepData, 
                                                        AdaptiveSimulationResult adaptiveResult,
                                                        List<Double> fragmentSizes) throws IOException {
        generateCostGraph3Curves(queryId, tcdrmData, norepData, adaptiveResult);
        generateResponseTimeGraph3Curves(queryId, tcdrmData, norepData, adaptiveResult, fragmentSizes);
        generateReplicaCountGraph3Curves(queryId, tcdrmData, adaptiveResult);
    }
    
    /**
     * Graphique de coût avec 2 courbes (NoRep + TCDRM)
     */
    private static void generateCostGraph2Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                 BenchmarkDataPerQuery norepData) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Comparaison des coûts cumulatifs - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Coût cumulatif ($)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.cumulativeCost());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepData.cumulativeCost());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_norep_cost_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de coût (2 courbes): " + filename);
    }
    
    /**
     * Graphique de coût avec 3 courbes (NoRep + TCDRM + TCDRM-ADAPTIVE)
     */
    private static void generateCostGraph3Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                 BenchmarkDataPerQuery norepData, 
                                                 AdaptiveSimulationResult adaptiveResult) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Comparaison des coûts cumulatifs - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Coût cumulatif ($)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.cumulativeCost());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepData.cumulativeCost());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        List<Double> adaptiveCumulativeCost = new ArrayList<>();
        double cumCost = 0.0;
        for (int i = 0; i < adaptiveResult.budgets.size(); i++) {
            cumCost = adaptiveResult.totalCost * (i + 1) / adaptiveResult.budgets.size();
            adaptiveCumulativeCost.add(cumCost);
        }
        
        XYSeries adaptiveSeries = chart.addSeries("TCDRM-ADAPTIVE (RL)", adaptiveResult.queryNumbers, adaptiveCumulativeCost);
        adaptiveSeries.setLineColor(new Color(44, 160, 44));
        adaptiveSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_comparison_cost_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de coût: " + filename);
    }
    
    /**
     * Graphique de temps de réponse avec 2 courbes (NoRep + TCDRM)
     */
    private static void generateResponseTimeGraph2Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                         BenchmarkDataPerQuery norepData,
                                                         List<Double> fragmentSizes) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Comparaison du temps de réponse - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Temps de réponse (secondes)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        List<Double> tcdrmResponseTime = new ArrayList<>(tcdrmData.timePerQueryMs());
        List<Double> norepResponseTime = new ArrayList<>(norepData.timePerQueryMs());
        
        List<Double> tcdrmSmoothed = toList(movingAverage(tcdrmResponseTime, 50));
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmSmoothed);
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        List<Double> norepSmoothed = toList(movingAverage(norepResponseTime, 50));
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepSmoothed);
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_norep_response_time_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de temps de réponse (2 courbes): " + filename);
    }
    
    /**
     * Graphique de temps de réponse avec 3 courbes (NoRep + TCDRM + TCDRM-ADAPTIVE)
     */
    private static void generateResponseTimeGraph3Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                         BenchmarkDataPerQuery norepData, 
                                                         AdaptiveSimulationResult adaptiveResult,
                                                         List<Double> fragmentSizes) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Comparaison du temps de réponse - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Temps de réponse (secondes)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        List<Double> tcdrmResponseTime = new ArrayList<>(tcdrmData.timePerQueryMs());
        List<Double> norepResponseTime = new ArrayList<>(norepData.timePerQueryMs());
        
        List<Double> tcdrmSmoothed = toList(movingAverage(tcdrmResponseTime, 50));
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmSmoothed);
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        List<Double> norepSmoothed = toList(movingAverage(norepResponseTime, 50));
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepSmoothed);
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        List<Double> adaptiveResponseTime = new ArrayList<>();
        double dataGb = fragmentSizes.stream().mapToDouble(d -> d).sum();
        
        for (int i = 0; i < adaptiveResult.latencies.size(); i++) {
            double latencyMs = adaptiveResult.latencies.get(i);
            int replicaCount = adaptiveResult.replicaCounts.get(i);
            
            double bwGbps = (replicaCount > 0 && latencyMs < 50) ? 10.0 : 1.0;
            double transferMs = (dataGb * 8000.0 / bwGbps) + latencyMs;
            double processingMs = dataGb * 0.5 * 60000.0;
            double totalTimeSeconds = (transferMs + processingMs) / 1000.0;
            adaptiveResponseTime.add(totalTimeSeconds);
        }
        
        List<Double> adaptiveSmoothed = toList(movingAverage(adaptiveResponseTime, 50));
        XYSeries adaptiveSeries = chart.addSeries("TCDRM-ADAPTIVE (RL)", adaptiveResult.queryNumbers, adaptiveSmoothed);
        adaptiveSeries.setLineColor(new Color(44, 160, 44));
        adaptiveSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_comparison_response_time_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de temps de réponse: " + filename);
    }
    
    /**
     * Graphique de réplicas avec 1 courbe (TCDRM uniquement)
     */
    private static void generateReplicaCountGraph2Curves(String queryId, BenchmarkDataPerQuery tcdrmData) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Nombre de réplicas - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Nombre de réplicas")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.replicaCount());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_replicas_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de réplicas (TCDRM): " + filename);
    }
    
    /**
     * Graphique de réplicas avec 2 courbes (TCDRM + TCDRM-ADAPTIVE)
     */
    private static void generateReplicaCountGraph3Curves(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                         AdaptiveSimulationResult adaptiveResult) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Comparaison du nombre de réplicas - " + queryId)
            .xAxisTitle("Nombre de requêtes")
            .yAxisTitle("Nombre de réplicas")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.replicaCount());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        XYSeries adaptiveSeries = chart.addSeries("TCDRM-ADAPTIVE (RL)", adaptiveResult.queryNumbers, adaptiveResult.replicaCounts);
        adaptiveSeries.setLineColor(new Color(44, 160, 44));
        adaptiveSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_comparison_replicas_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de réplicas: " + filename);
    }
    
    private static void printComparisonSummary(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                              BenchmarkDataPerQuery norepData, 
                                              AdaptiveSimulationResult adaptiveResult) {
        double tcdrmCost = tcdrmData.cumulativeCost().get(tcdrmData.cumulativeCost().size() - 1);
        double norepCost = norepData.cumulativeCost().get(norepData.cumulativeCost().size() - 1);
        double adaptiveCost = adaptiveResult.totalCost;
        
        double savingsVsNorep = ((norepCost - adaptiveCost) / norepCost) * 100;
        double savingsVsTcdrm = ((tcdrmCost - adaptiveCost) / tcdrmCost) * 100;
        
        System.out.println("\n=== Résumé de la comparaison - " + queryId + " ===\n");
        System.out.println("Coût cumulatif final:");
        System.out.println("  TCDRM (statique):     $" + String.format("%.2f", tcdrmCost));
        System.out.println("  NoRep:                $" + String.format("%.2f", norepCost));
        System.out.println("  TCDRM-ADAPTIVE (RL):  $" + String.format("%.2f", adaptiveCost));
        System.out.println("\nÉconomies TCDRM-ADAPTIVE:");
        System.out.println("  vs NoRep:    " + (savingsVsNorep > 0 ? "+" : "") + String.format("%.1f%%", savingsVsNorep));
        System.out.println("  vs TCDRM:    " + (savingsVsTcdrm > 0 ? "+" : "") + String.format("%.1f%%", savingsVsTcdrm));
        System.out.println("\nRécompense totale TCDRM-ADAPTIVE: " + String.format("%.2f", adaptiveResult.totalReward));
        System.out.println("Budget restant: $" + String.format("%.2f", adaptiveResult.finalBudget));
        System.out.println("\n" + "=".repeat(60));
    }
    
    private static double[] movingAverage(List<Double> data, int windowSize) {
        double[] result = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.size(), i + windowSize / 2 + 1);
            double sum = 0.0;
            for (int j = start; j < end; j++) {
                sum += data.get(j);
            }
            result[i] = sum / (end - start);
        }
        return result;
    }
    
    private static List<Double> toList(double[] array) {
        List<Double> list = new ArrayList<>();
        for (double value : array) {
            list.add(value);
        }
        return list;
    }
    
    static class QueryConfig {
        final String queryId;
        final double dataGb;
        
        QueryConfig(String queryId, double dataGb) {
            this.queryId = queryId;
            this.dataGb = dataGb;
        }
    }
    
    static class AdaptiveSimulationResult {
        final List<Integer> queryNumbers;
        final List<Double> rewards;
        final List<Double> budgets;
        final List<Double> latencies;
        final List<Integer> replicaCounts;
        final double totalReward;
        final double finalBudget;
        final double totalCost;
        
        AdaptiveSimulationResult(List<Integer> queryNumbers, List<Double> rewards, 
                                List<Double> budgets, List<Double> latencies, 
                                List<Integer> replicaCounts, double totalReward, 
                                double finalBudget, double totalCost) {
            this.queryNumbers = queryNumbers;
            this.rewards = rewards;
            this.budgets = budgets;
            this.latencies = latencies;
            this.replicaCounts = replicaCounts;
            this.totalReward = totalReward;
            this.finalBudget = finalBudget;
            this.totalCost = totalCost;
        }
    }
}
