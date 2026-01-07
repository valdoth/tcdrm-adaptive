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

/**
 * Comparaison complète entre TCDRM (statique), TCDRM-ADAPTIVE (RL), et NoRep
 * Génère des graphiques de convergence et de performance
 */
public class TcdrmAdaptiveComparison {
    
    public static void main(String[] args) throws IOException {
        System.out.println("==========================================================");
        System.out.println("   TCDRM vs TCDRM-ADAPTIVE vs NoRep : Comparaison         ");
        System.out.println("==========================================================\n");
        
        // Paramètres
        List<Double> fragmentSizesR1 = Arrays.asList(1.5, 2.0, 1.8);
        List<Double> fragmentSizesR2 = Arrays.asList(1.8, 2.2, 1.5, 2.5, 1.9, 2.0);
        int replicationFactor = 3;
        int numEpisodes = 500;
        
        // Comparer pour R1
        System.out.println(">>> Comparaison pour R1 (Requête Simple) <<<\n");
        compareForQuery("R1", fragmentSizesR1, replicationFactor, numEpisodes, 42L);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Comparer pour R2
        System.out.println(">>> Comparaison pour R2 (Requête Complexe) <<<\n");
        compareForQuery("R2", fragmentSizesR2, replicationFactor, numEpisodes, 42L);
        
        System.out.println("\n==========================================================");
        System.out.println("   Comparaison terminée avec succès !                      ");
        System.out.println("==========================================================");
    }
    
    private static void compareForQuery(String queryId, List<Double> fragmentSizes, 
                                       int replicationFactor, int numEpisodes, Long seed) throws IOException {
        double dataGb = fragmentSizes.stream().mapToDouble(d -> d).sum();
        
        // 1. Entraîner TCDRM-ADAPTIVE
        System.out.println("=== Entraînement TCDRM-ADAPTIVE ===");
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        QLearningAgent agent = new QLearningAgent(env, 0.1, 0.95, 1.0, 0.995, 0.01);
        QLearningAgent.TrainingStats stats = agent.train(numEpisodes, seed);
        
        // Générer graphique de convergence
        generateConvergenceGraph(queryId, stats);
        
        // 2. Exécuter TCDRM statique
        System.out.println("\n=== Exécution TCDRM (statique) ===");
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, seed);
        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        System.out.println("TCDRM - Coût cumulatif final: $" + 
            String.format("%.2f", tcdrmData.cumulativeCost().get(tcdrmData.cumulativeCost().size() - 1)));
        
        // 3. Exécuter NoRep
        System.out.println("\n=== Exécution NoRep ===");
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(seed);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);
        System.out.println("NoRep - Coût cumulatif final: $" + 
            String.format("%.2f", norepData.cumulativeCost().get(norepData.cumulativeCost().size() - 1)));
        
        // 4. Simuler TCDRM-ADAPTIVE sur 1000 requêtes
        System.out.println("\n=== Simulation TCDRM-ADAPTIVE (1000 requêtes) ===");
        AdaptiveSimulationResult adaptiveResult = simulateAdaptive(agent, dataGb, seed);
        System.out.println("TCDRM-ADAPTIVE - Récompense totale: " + 
            String.format("%.2f", adaptiveResult.totalReward));
        System.out.println("TCDRM-ADAPTIVE - Budget restant: $" + 
            String.format("%.2f", adaptiveResult.finalBudget));
        
        // 5. Générer graphiques comparatifs
        generateComparisonGraphs(queryId, tcdrmData, norepData, adaptiveResult, fragmentSizes);
        
        // 6. Afficher résumé
        printComparisonSummary(queryId, tcdrmData, norepData, adaptiveResult);
    }
    
    /**
     * Simule TCDRM-ADAPTIVE sur 1000 requêtes
     */
    private static AdaptiveSimulationResult simulateAdaptive(QLearningAgent agent, double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        TcdrmState state = env.reset(seed);
        
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> rewards = new ArrayList<>();
        List<Double> budgets = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();
        List<Integer> replicaCounts = new ArrayList<>();
        
        double totalReward = 0.0;
        int query = 0;
        
        while (query < 1000) {
            // Choisir meilleure action (exploitation pure)
            TcdrmAction action = agent.getBestAction(state);
            
            // Exécuter l'action
            Environment.StepResult<TcdrmState> result = env.step(action);
            
            // Enregistrer métriques
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
        
        return new AdaptiveSimulationResult(queryNumbers, rewards, budgets, latencies, 
                                           replicaCounts, totalReward, env.getCurrentBudget());
    }
    
    /**
     * Génère le graphique de convergence de l'apprentissage
     */
    private static void generateConvergenceGraph(String queryId, QLearningAgent.TrainingStats stats) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("TCDRM-ADAPTIVE: Convergence de l'apprentissage - " + queryId)
            .xAxisTitle("Épisode")
            .yAxisTitle("Récompense totale")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        // Données brutes
        double[] episodes = new double[stats.getEpisodeRewards().length];
        for (int i = 0; i < episodes.length; i++) {
            episodes[i] = i + 1;
        }
        
        XYSeries series = chart.addSeries("Récompense par épisode", episodes, stats.getEpisodeRewards());
        series.setLineColor(new Color(31, 119, 180));
        series.setLineWidth(1.5f);
        
        // Moyenne mobile (fenêtre de 20)
        double[] smoothed = movingAverage(stats.getEpisodeRewards(), 20);
        XYSeries smoothSeries = chart.addSeries("Moyenne mobile (20)", episodes, smoothed);
        smoothSeries.setLineColor(new Color(255, 127, 14));
        smoothSeries.setLineWidth(3.0f);
        
        String filename = "images/tcdrm_adaptive_convergence_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de convergence: " + filename);
    }
    
    /**
     * Génère les graphiques comparatifs
     */
    private static void generateComparisonGraphs(String queryId, BenchmarkDataPerQuery tcdrmData, 
                                                 BenchmarkDataPerQuery norepData, 
                                                 AdaptiveSimulationResult adaptiveResult,
                                                 List<Double> fragmentSizes) throws IOException {
        // 1. Coût cumulatif
        generateCumulativeCostGraph(queryId, tcdrmData, norepData, adaptiveResult);
        
        // 2. Temps de réponse
        generateLatencyGraph(queryId, tcdrmData, norepData, adaptiveResult, fragmentSizes);
        
        // 3. Nombre de réplicas
        generateReplicaCountGraph(queryId, tcdrmData, adaptiveResult);
    }
    
    private static void generateCumulativeCostGraph(String queryId, BenchmarkDataPerQuery tcdrmData, 
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
        
        // TCDRM statique
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.cumulativeCost());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        // NoRep
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepData.cumulativeCost());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        // TCDRM-ADAPTIVE (budget décroissant = coût croissant)
        List<Double> adaptiveCumulativeCost = new ArrayList<>();
        double initialBudget = 100.0;
        for (Double budget : adaptiveResult.budgets) {
            adaptiveCumulativeCost.add(initialBudget - budget);
        }
        XYSeries adaptiveSeries = chart.addSeries("TCDRM-ADAPTIVE (RL)", adaptiveResult.queryNumbers, adaptiveCumulativeCost);
        adaptiveSeries.setLineColor(new Color(44, 160, 44));
        adaptiveSeries.setLineWidth(2.5f);
        
        String filename = "images/tcdrm_comparison_cost_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Graphique de coût: " + filename);
    }
    
    private static void generateLatencyGraph(String queryId, BenchmarkDataPerQuery tcdrmData, 
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
        
        // Utiliser directement le temps de réponse (en secondes)
        List<Double> tcdrmResponseTime = new ArrayList<>(tcdrmData.timePerQueryMs());
        List<Double> norepResponseTime = new ArrayList<>(norepData.timePerQueryMs());
        
        // TCDRM statique (lissé)
        List<Double> tcdrmSmoothed = toList(movingAverage(tcdrmResponseTime, 50));
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmSmoothed);
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        // NoRep (lissé)
        List<Double> norepSmoothed = toList(movingAverage(norepResponseTime, 50));
        XYSeries norepSeries = chart.addSeries("NoRep", norepData.queryNumbers(), norepSmoothed);
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        
        // TCDRM-ADAPTIVE: Calculer temps de réponse réel basé sur la simulation
        // Utiliser la même logique que TcdrmBenchmarkPerQuery
        List<Double> adaptiveResponseTime = new ArrayList<>();
        double dataGb = fragmentSizes.stream().mapToDouble(d -> d).sum();
        
        for (int i = 0; i < adaptiveResult.latencies.size(); i++) {
            double latencyMs = adaptiveResult.latencies.get(i);
            int replicaCount = adaptiveResult.replicaCounts.get(i);
            
            // Déterminer bande passante selon présence de réplicas
            double bwGbps = (replicaCount > 0 && latencyMs < 50) ? 10.0 : 1.0;
            
            // Temps de transfert (en ms)
            double transferMs = (dataGb * 8000.0 / bwGbps) + latencyMs;
            
            // Temps de traitement (en ms)
            double processingMs = dataGb * 0.5 * 60000.0;  // 0.5 min/GB
            
            // Temps total en secondes
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
    
    private static void generateReplicaCountGraph(String queryId, BenchmarkDataPerQuery tcdrmData, 
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
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax(4.0);
        
        // TCDRM statique
        XYSeries tcdrmSeries = chart.addSeries("TCDRM (statique)", tcdrmData.queryNumbers(), tcdrmData.replicaCount());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        
        // TCDRM-ADAPTIVE
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
        System.out.println("\n=== Résumé de la comparaison - " + queryId + " ===");
        
        double tcdrmFinalCost = tcdrmData.cumulativeCost().get(tcdrmData.cumulativeCost().size() - 1);
        double norepFinalCost = norepData.cumulativeCost().get(norepData.cumulativeCost().size() - 1);
        double adaptiveFinalCost = 100.0 - adaptiveResult.finalBudget;
        
        System.out.println("\nCoût cumulatif final:");
        System.out.println("  TCDRM (statique):     $" + String.format("%.2f", tcdrmFinalCost));
        System.out.println("  NoRep:                $" + String.format("%.2f", norepFinalCost));
        System.out.println("  TCDRM-ADAPTIVE (RL):  $" + String.format("%.2f", adaptiveFinalCost));
        
        double savingsVsNoRep = ((norepFinalCost - adaptiveFinalCost) / norepFinalCost) * 100;
        double savingsVsTCDRM = ((tcdrmFinalCost - adaptiveFinalCost) / tcdrmFinalCost) * 100;
        
        System.out.println("\nÉconomies TCDRM-ADAPTIVE:");
        System.out.println("  vs NoRep:    " + String.format("%+.1f%%", savingsVsNoRep));
        System.out.println("  vs TCDRM:    " + String.format("%+.1f%%", savingsVsTCDRM));
        
        System.out.println("\nRécompense totale TCDRM-ADAPTIVE: " + String.format("%.2f", adaptiveResult.totalReward));
        System.out.println("Budget restant: $" + String.format("%.2f", adaptiveResult.finalBudget));
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
    
    private static double[] movingAverage(double[] data, int windowSize) {
        double[] result = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.length, i + windowSize / 2 + 1);
            double sum = 0.0;
            for (int j = start; j < end; j++) {
                sum += data[j];
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
    
    /**
     * Classe pour stocker les résultats de simulation TCDRM-ADAPTIVE
     */
    static class AdaptiveSimulationResult {
        final List<Integer> queryNumbers;
        final List<Double> rewards;
        final List<Double> budgets;
        final List<Double> latencies;
        final List<Integer> replicaCounts;
        final double totalReward;
        final double finalBudget;
        
        AdaptiveSimulationResult(List<Integer> queryNumbers, List<Double> rewards, List<Double> budgets,
                                List<Double> latencies, List<Integer> replicaCounts, 
                                double totalReward, double finalBudget) {
            this.queryNumbers = queryNumbers;
            this.rewards = rewards;
            this.budgets = budgets;
            this.latencies = latencies;
            this.replicaCounts = replicaCounts;
            this.totalReward = totalReward;
            this.finalBudget = finalBudget;
        }
    }
}
