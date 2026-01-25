package org.tcdrm.adaptive.examples;

import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.*;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Comparaison Complète avec CloudSim (3 approches):
 * 1. TCDRM avec Reinforcement Learning Python (entraîné en Python via Tabular Q-Learning, chargé via Py4J)
 * 2. TCDRM Statique (seuils fixes TSLA=150ms, PSLA=200, CSLA=20% - baseline de l'article)
 * 3. NOREP (pas de réplication - baseline)
 * 
 * Génère des graphes comparatifs détaillés pour validation scientifique
 * Note: Aucun Q-Learning Java n'est utilisé - tout le RL est fait en Python
 */
public class TcdrmComparisonCloudSim {
    
    private static final String OUTPUT_DIR = "results/cloudsim_comparison/";
    private static Py4JGateway gateway;
    
    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("COMPARAISON COMPLÈTE: 3 Approches (TCDRM Python RL, TCDRM Statique, NOREP)");
        System.out.println("  1. TCDRM avec Python RL (Reinforcement Learning - Tabular Q-Learning)");
        System.out.println("  2. TCDRM Statique (seuils fixes: TSLA=150ms, PSLA=200, CSLA=20%)");
        System.out.println("  3. NOREP (pas de réplication)");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Démarrer le Gateway Py4J
        System.out.println("🚀 Démarrage du Gateway Py4J...");
        gateway = new Py4JGateway();
        gateway.start();
        
        System.out.println();
        System.out.println("⏳ En attente de la connexion Python...");
        System.out.println("   Le script Python doit se connecter et enregistrer la Q-table");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Attendre que Python enregistre son modèle
        boolean pythonReady = gateway.waitForPythonModel(60);
        
        if (pythonReady) {
            System.out.println("✅ Modèle Python enregistré et prêt!");
        } else {
            System.out.println("⚠️  Python non connecté - comparaison sans Python QL");
        }
        System.out.println();
        
        // Paramètres de simulation
        double dataGbR1 = 5.3;
        double dataGbR2 = 11.9;
        Long seed = 42L;
        
        // Créer le répertoire de sortie
        new java.io.File(OUTPUT_DIR).mkdirs();
        
        // Scénarios à comparer
        System.out.println(">>> Scénarios à comparer:");
        System.out.println("  - R1: Requête Simple (" + dataGbR1 + " GB)");
        System.out.println("  - R2: Requête Complexe (" + dataGbR2 + " GB)");
        System.out.println();
        
        // Comparaison R1
        System.out.println("=== COMPARAISON R1 (" + dataGbR1 + " GB) ===");
        System.out.println();
        System.out.println(">>> Comparaison pour R1 (5.3 GB)");
        ComparisonResult pythonQLR1 = runPythonQLearning(dataGbR1, seed, "R1");
        ComparisonResult staticR1 = runStaticTcdrm(dataGbR1, seed);
        ComparisonResult norepR1 = runNoReplication(dataGbR1, seed);
        
        displayResults("R1", pythonQLR1, staticR1, norepR1);
        
        System.out.println(">>> Génération des graphes comparatifs...");
        generateDetailedComparisonGraphs("R1", pythonQLR1, staticR1, norepR1);
        System.out.println("✅ Comparaison R1 terminée");
        System.out.println();
        
        // Comparaison R2
        System.out.println("=== COMPARAISON R2 (" + dataGbR2 + " GB) ===");
        System.out.println();
        System.out.println(">>> Comparaison pour R2 (11.9 GB)");
        ComparisonResult pythonQLR2 = runPythonQLearning(dataGbR2, seed, "R2");
        ComparisonResult staticR2 = runStaticTcdrm(dataGbR2, seed);
        ComparisonResult norepR2 = runNoReplication(dataGbR2, seed);
        
        displayResults("R2", pythonQLR2, staticR2, norepR2);
        
        System.out.println(">>> Génération des graphes comparatifs...");
        generateDetailedComparisonGraphs("R2", pythonQLR2, staticR2, norepR2);
        System.out.println("✅ Comparaison R2 terminée");
        System.out.println();
        
        System.out.println("=".repeat(80));
        System.out.println("✅ COMPARAISON TERMINÉE");
        System.out.println("=".repeat(80));
        System.out.println("Graphes sauvegardés dans: " + OUTPUT_DIR);
        
        // Arrêter le Gateway Py4J pour permettre au processus de se terminer
        if (gateway != null) {
            System.out.println();
            System.out.println("🛑 Arrêt du Gateway Py4J...");
            gateway.stop();
        }
    }
    
    /**
     * Exécute Q-Learning Python (via Py4J)
     */
    private static ComparisonResult runPythonQLearning(double dataGb, Long seed, String queryId) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        
        System.out.println(">>> 2. Q-Learning Python (entraîné en Python, chargé via Py4J)");
        
        // Vérifier si le modèle Python est enregistré
        boolean modelReady = gateway != null && gateway.getPythonAgent() != null && gateway.getPythonAgent().isModelLoaded();
        
        if (!modelReady) {
            System.err.println("  ❌ Modèle Python non connecté via Py4J");
            return createEmptyResult("Q-Learning Python (NON CONNECTÉ)");
        }
        
        // Créer la politique avec l'agent Python
        PythonQLearningPolicy policy = new PythonQLearningPolicy(gateway.getPythonAgent());
        System.out.println("  ✅ Modèle Python connecté via Py4J. Évaluation...");
        
        return evaluatePolicy(env, policy, "Q-Learning Python", seed);
    }
    
    /**
     * Exécute TCDRM Statique
     */
    private static ComparisonResult runStaticTcdrm(double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        StaticTcdrmPolicy policy = new StaticTcdrmPolicy();
        
        System.out.println(">>> 3. TCDRM Statique (seuils fixes)");
        System.out.println("  Évaluation politique statique...");
        
        return evaluatePolicy(env, policy, "TCDRM Statique", seed);
    }
    
    /**
     * Exécute sans réplication (NOREP)
     */
    private static ComparisonResult runNoReplication(double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        
        System.out.println(">>> 4. NOREP (pas de réplication)");
        System.out.println("  Évaluation sans réplication...");
        
        // Politique qui ne fait jamais de réplication (toujours DO_NOTHING)
        return evaluatePolicy(env, null, "NOREP", seed);
    }
    
    /**
     * Affiche les résultats de comparaison
     */
    private static void displayResults(String queryId, ComparisonResult pythonQL,
                                      ComparisonResult staticTcdrm, ComparisonResult norep) {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println(String.format("%-25s | %-20s | %-20s | %-20s", 
            "Métrique", "Python Q-Learning", "TCDRM Statique", "NOREP"));
        System.out.println("=".repeat(80));
        System.out.println(String.format("%-25s | %20.2f | %20.2f | %20.2f", 
            "Récompense Totale", pythonQL.totalReward, staticTcdrm.totalReward, norep.totalReward));
        System.out.println(String.format("%-25s | %20.2f | %20.2f | %20.2f", 
            "Coût Total ($)", pythonQL.totalCost, staticTcdrm.totalCost, norep.totalCost));
        System.out.println(String.format("%-25s | %20.2f | %20.2f | %20.2f", 
            "Latence Moyenne (ms)", pythonQL.avgLatency, staticTcdrm.avgLatency, norep.avgLatency));
        System.out.println(String.format("%-25s | %19.1f%% | %19.1f%% | %19.1f%%", 
            "Conformité SLA", pythonQL.slaCompliance * 100, 
            staticTcdrm.slaCompliance * 100, norep.slaCompliance * 100));
        System.out.println(String.format("%-25s | %20.2f | %20.2f | %20.2f", 
            "Budget Restant ($)", pythonQL.budgetRemaining, 
            staticTcdrm.budgetRemaining, norep.budgetRemaining));
        System.out.println("=".repeat(80));
        System.out.println();
    }
    
    /**
     * Évalue une politique
     */
    private static ComparisonResult evaluatePolicy(TcdrmEnvironment env, Object policy, 
                                                   String name, Long seed) {
        TcdrmState state = env.reset(seed);
        ComparisonResult result = new ComparisonResult();
        result.name = name;
        
        double totalReward = 0.0;
        double totalCost = 0.0;
        double cumulativeBwCost = 0.0;
        int slaViolations = 0;
        int steps = 0;
        
        while (true) {
            TcdrmAction action;
            if (policy instanceof StaticTcdrmPolicy) {
                double initialBudget = 2000.0; // Budget initial (doit correspondre à TcdrmEnvironment.INITIAL_BUDGET)
                double budgetRatio = env.getCurrentBudget() / initialBudget;
                double latency = env.getCurrentLatency();
                // Mapper le niveau de popularité vers un nombre d'accès réaliste
                // LOW (0) -> 100 accès, MEDIUM (1) -> 200 accès, HIGH (2) -> 300 accès
                int accessCount = 100 + (state.getPopularityLevel().ordinal() * 100);
                int currentReplicas = env.getCurrentReplicaCount();
                action = ((StaticTcdrmPolicy) policy).chooseAction(budgetRatio, latency, accessCount, currentReplicas);
            } else if (policy instanceof PythonQLearningPolicy) {
                action = ((PythonQLearningPolicy) policy).chooseAction(state);
            } else {
                action = TcdrmAction.DO_NOTHING;
            }
            
            Environment.StepResult<TcdrmState> stepResult = env.step(action);
            totalReward += stepResult.getReward();
            
            int currentReplicas = env.getCurrentReplicaCount();
            double dataGb = env.getDataGb();
            
            // Calculer les métriques détaillées
            double bwCost = calculateBandwidthCost(currentReplicas, dataGb);
            double storageCost = calculateStorageCost(currentReplicas, dataGb);
            double replicationCost = (action == TcdrmAction.CREATE_REPLICA) ? (0.10 * dataGb) : 0.0;
            double stepCost = bwCost + storageCost + replicationCost;
            
            totalCost += stepCost;
            cumulativeBwCost += bwCost;
            
            double cpuUsage = calculateCpuUsage(currentReplicas, env.getCurrentLatency());
            double responseTime = env.getCurrentLatency() + (currentReplicas * 2.0);
            double latency = env.getCurrentLatency();
            
            // Collecter les métriques
            result.latencies.add(latency);
            result.costs.add(totalCost);
            result.replicas.add(currentReplicas);
            result.cpuUsage.add(cpuUsage);
            result.bandwidthCosts.add(bwCost);
            result.cumulativeBwCosts.add(cumulativeBwCost);
            result.responseTimes.add(responseTime);
            result.storageUsage.add(currentReplicas * dataGb);
            
            if (latency > 150.0) slaViolations++;
            steps++;
            
            state = stepResult.getNextState();
            if (stepResult.isDone()) break;
        }
        
        result.totalReward = totalReward;
        result.totalCost = totalCost;
        result.avgLatency = result.latencies.stream().mapToDouble(d -> d).average().orElse(0);
        result.slaCompliance = 1.0 - ((double) slaViolations / steps);
        result.budgetRemaining = env.getCurrentBudget();
        result.statistics = (policy instanceof StaticTcdrmPolicy) ? "Statique" : "Python QL";
        
        return result;
    }
    
    /**
     * Calcule le coût de bande passante
     */
    private static double calculateBandwidthCost(int replicas, double dataGb) {
        if (replicas > 0) {
            return 0.002 * dataGb; // Intra-DC
        } else {
            return 0.10 * dataGb;  // Inter-provider
        }
    }
    
    /**
     * Calcule le coût de stockage
     */
    private static double calculateStorageCost(int replicas, double dataGb) {
        return replicas * dataGb * (0.02 / 720.0);
    }
    
    /**
     * Calcule l'utilisation CPU (simulée)
     */
    private static double calculateCpuUsage(int replicas, double latency) {
        // CPU usage augmente avec moins de réplicas et plus de latence
        double baseUsage = 30.0;
        double replicaFactor = (3 - replicas) * 15.0;
        double latencyFactor = (latency / 200.0) * 20.0;
        return Math.min(100.0, baseUsage + replicaFactor + latencyFactor);
    }
    
    /**
     * Crée un résultat vide pour les cas d'erreur
     */
    private static ComparisonResult createEmptyResult(String name) {
        ComparisonResult result = new ComparisonResult();
        result.name = name;
        result.totalReward = 0.0;
        result.totalCost = 0.0;
        result.avgLatency = 0.0;
        result.slaCompliance = 0.0;
        result.budgetRemaining = 0.0;
        result.statistics = "Non connecté";
        return result;
    }
    
    /**
     * Affiche un tableau comparatif (3 approches: Python RL, TCDRM Statique, NOREP)
     */
    private static void printComparisonTable(ComparisonResult pythonRL, ComparisonResult staticTcdrm, ComparisonResult norep) {
        System.out.println("=".repeat(80));
        System.out.println(String.format("%-25s | %15s | %15s | %15s", 
            "Métrique", "Python RL", "TCDRM Statique", "NOREP"));
        System.out.println("-".repeat(80));
        
        System.out.println(String.format("%-25s | %15.2f | %15.2f | %15.2f",
            "Récompense Totale", pythonRL.totalReward, staticTcdrm.totalReward, norep.totalReward));
        
        System.out.println(String.format("%-25s | %15.2f | %15.2f | %15.2f",
            "Coût Total ($)", pythonRL.totalCost, staticTcdrm.totalCost, norep.totalCost));
        
        System.out.println(String.format("%-25s | %15.2f | %15.2f | %15.2f",
            "Latence Moyenne (ms)", pythonRL.avgLatency, staticTcdrm.avgLatency, norep.avgLatency));
        
        System.out.println(String.format("%-25s | %14.1f%% | %14.1f%% | %14.1f%%",
            "Conformité SLA", pythonRL.slaCompliance * 100, staticTcdrm.slaCompliance * 100, norep.slaCompliance * 100));
        
        System.out.println(String.format("%-25s | %15.2f | %15.2f | %15.2f",
            "Budget Restant ($)", pythonRL.budgetRemaining, staticTcdrm.budgetRemaining, norep.budgetRemaining));
        
        System.out.println();
        System.out.println("Amélioration vs NOREP (baseline):");
        
        double costImprovRL = ((norep.totalCost - pythonRL.totalCost) / norep.totalCost) * 100;
        double costImprovStatic = ((norep.totalCost - staticTcdrm.totalCost) / norep.totalCost) * 100;
        System.out.println(String.format("  Coût Python RL:      %+.1f%%", costImprovRL));
        System.out.println(String.format("  Coût TCDRM Statique: %+.1f%%", costImprovStatic));
        
        double slaImprovRL = ((pythonRL.slaCompliance - norep.slaCompliance) / norep.slaCompliance) * 100;
        double slaImprovStatic = ((staticTcdrm.slaCompliance - norep.slaCompliance) / norep.slaCompliance) * 100;
        System.out.println(String.format("  SLA Python RL:       %+.1f%%", slaImprovRL));
        System.out.println(String.format("  SLA TCDRM Statique:  %+.1f%%", slaImprovStatic));
    }
    
    /**
     * Génère les graphes combinés (2 par image, 3 courbes)
     */
    private static void generateDetailedComparisonGraphs(String queryId, 
                                                         ComparisonResult pythonQL,
                                                         ComparisonResult staticTcdrm,
                                                         ComparisonResult norep) throws IOException {
        
        // Image 1: Response Time + CPU Consumption
        generateDualCombinedGraph(queryId, pythonQL, staticTcdrm, norep, "dual_combined");
        
        // Image 2: CPU + Bandwidth
        generateDualCombinedGraph(queryId, pythonQL, staticTcdrm, norep, "cpu_bandwidth");
        
        // Image 3: Storage + Total Cost
        generateDualCombinedGraph(queryId, pythonQL, staticTcdrm, norep, "storage_total");
        
        // Image 4: Total Cost (2 vues)
        generateDualCombinedGraph(queryId, pythonQL, staticTcdrm, norep, "total_cost");
        
        // Graphes individuels supplémentaires (avec 3 courbes)
        generateSingleGraph(queryId, pythonQL, staticTcdrm, norep, "comparison_cost");
        generateSingleGraph(queryId, pythonQL, staticTcdrm, norep, "comparison_response_time");
        generateSingleGraph(queryId, pythonQL, staticTcdrm, norep, "comparison_replicas");
        
        System.out.println("✅ Graphes combinés sauvegardés:");
        System.out.println("   - " + OUTPUT_DIR + "tcdrm_dual_combined_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "combined_cpu_bandwidth_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "combined_storage_total_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "tcdrm_combined_total_cost_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "tcdrm_comparison_cost_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "tcdrm_comparison_response_time_" + queryId + ".png");
        System.out.println("   - " + OUTPUT_DIR + "tcdrm_comparison_replicas_" + queryId + ".png");
    }
    
    /**
     * Génère un graphe combiné (2 graphes côte à côte, 3 courbes chacun)
     */
    private static void generateDualCombinedGraph(String queryId, ComparisonResult pythonQL,
                                                  ComparisonResult staticTcdrm,
                                                  ComparisonResult norep, String type) throws IOException {
        XYChart chart1, chart2;
        String filename;
        
        switch (type) {
            case "dual_combined":
                // Graphe gauche: Response Time
                chart1 = createChart("Impact of Replication on Response Time", "Request Number", "Response Time (ms)",
                                   pythonQL.responseTimes, staticTcdrm.responseTimes, norep.responseTimes);
                // Graphe droite: CPU Consumption
                chart2 = createChart("Impact of Replication on CPU Consumption", "Request Number", "CPU Usage (%)",
                                   pythonQL.cpuUsage, staticTcdrm.cpuUsage, norep.cpuUsage);
                filename = OUTPUT_DIR + "tcdrm_dual_combined_" + queryId + ".png";
                break;
                
            case "cpu_bandwidth":
                // Graphe gauche: CPU Usage
                chart1 = createChart("CPU Consumption", "Request Number", "CPU Usage (%)",
                                   pythonQL.cpuUsage, staticTcdrm.cpuUsage, norep.cpuUsage);
                // Graphe droite: Bandwidth Cost
                chart2 = createChart("Bandwidth Cost", "Request Number", "BW Cost ($)",
                                   pythonQL.bandwidthCosts, staticTcdrm.bandwidthCosts, norep.bandwidthCosts);
                filename = OUTPUT_DIR + "combined_cpu_bandwidth_" + queryId + ".png";
                break;
                
            case "storage_total":
                // Graphe gauche: Storage Usage
                chart1 = createChart("Storage Usage", "Request Number", "Storage (GB)",
                                   pythonQL.storageUsage, staticTcdrm.storageUsage, norep.storageUsage);
                // Graphe droite: Total Cost
                chart2 = createChart("Total Cost", "Request Number", "Cumulative Cost ($)",
                                   pythonQL.costs, staticTcdrm.costs, norep.costs);
                filename = OUTPUT_DIR + "combined_storage_total_" + queryId + ".png";
                break;
                
            case "total_cost":
                // Graphe gauche: Total Cost
                chart1 = createChart("Total Cost", "Request Number", "Cumulative Cost ($)",
                                   pythonQL.costs, staticTcdrm.costs, norep.costs);
                // Graphe droite: Cumulative BW Cost
                chart2 = createChart("Cumulative BW Cost", "Request Number", "Cumulative BW Cost ($)",
                                   pythonQL.cumulativeBwCosts, staticTcdrm.cumulativeBwCosts, norep.cumulativeBwCosts);
                filename = OUTPUT_DIR + "tcdrm_combined_total_cost_" + queryId + ".png";
                break;
                
            default:
                return;
        }
        
        // Combiner les 2 graphes en une seule image
        java.awt.image.BufferedImage combined = combineCharts(chart1, chart2);
        javax.imageio.ImageIO.write(combined, "PNG", new java.io.File(filename));
    }
    
    /**
     * Crée un graphe avec 3 courbes (Python QL, TCDRM Statique, NOREP)
     */
    private static XYChart createChart(String title, String xLabel, String yLabel,
                                      List<Double> pythonData, List<Double> staticData, List<Double> norepData) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        if (!pythonData.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonData.size()), pythonData);
        }
        chart.addSeries("TCDRM Statique", createXData(staticData.size()), staticData);
        chart.addSeries("NOREP", createXData(norepData.size()), norepData);
        
        return chart;
    }
    
    /**
     * Crée un graphe de réplicas avec 3 courbes (Python QL, TCDRM Statique, NOREP)
     */
    private static XYChart createReplicasChart(String title, String xLabel, String yLabel,
                                              List<Integer> pythonData, List<Integer> staticData, List<Integer> norepData) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Step);
        
        if (!pythonData.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonData.size()), pythonData);
        }
        chart.addSeries("TCDRM Statique", createXData(staticData.size()), staticData);
        chart.addSeries("NOREP", createXData(norepData.size()), norepData);
        
        return chart;
    }
    
    /**
     * Génère un graphe individuel (1 graphe, 3 courbes)
     */
    private static void generateSingleGraph(String queryId, ComparisonResult pythonQL,
                                           ComparisonResult staticTcdrm,
                                           ComparisonResult norep, String type) throws IOException {
        XYChart chart;
        String filename;
        
        switch (type) {
            case "comparison_cost":
                chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Cost Comparison")
                    .xAxisTitle("Request Number")
                    .yAxisTitle("Cumulative Cost ($)")
                    .build();
                
                chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
                chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
                
                if (!pythonQL.costs.isEmpty()) {
                    chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.costs.size()), pythonQL.costs);
                }
                chart.addSeries("TCDRM Statique", createXData(staticTcdrm.costs.size()), staticTcdrm.costs);
                chart.addSeries("NOREP", createXData(norep.costs.size()), norep.costs);
                
                filename = OUTPUT_DIR + "tcdrm_comparison_cost_" + queryId + ".png";
                break;
                
            case "comparison_response_time":
                chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Response Time Comparison")
                    .xAxisTitle("Request Number")
                    .yAxisTitle("Response Time (ms)")
                    .build();
                
                chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
                chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
                
                if (!pythonQL.responseTimes.isEmpty()) {
                    chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.responseTimes.size()), pythonQL.responseTimes);
                }
                chart.addSeries("TCDRM Statique", createXData(staticTcdrm.responseTimes.size()), staticTcdrm.responseTimes);
                chart.addSeries("NOREP", createXData(norep.responseTimes.size()), norep.responseTimes);
                
                filename = OUTPUT_DIR + "tcdrm_comparison_response_time_" + queryId + ".png";
                break;
                
            case "comparison_replicas":
                chart = new XYChartBuilder()
                    .width(800).height(600)
                    .title("Number of Replicas Comparison")
                    .xAxisTitle("Request Number")
                    .yAxisTitle("Number of Replicas")
                    .build();
                
                chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
                chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Step);
                
                if (!pythonQL.replicas.isEmpty()) {
                    chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.replicas.size()), pythonQL.replicas);
                }
                chart.addSeries("TCDRM Statique", createXData(staticTcdrm.replicas.size()), staticTcdrm.replicas);
                chart.addSeries("NOREP", createXData(norep.replicas.size()), norep.replicas);
                
                filename = OUTPUT_DIR + "tcdrm_comparison_replicas_" + queryId + ".png";
                break;
                
            default:
                return;
        }
        
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
    }
    
    /**
     * Combine 2 graphes en une seule image (côte à côte)
     */
    private static java.awt.image.BufferedImage combineCharts(XYChart chart1, XYChart chart2) {
        java.awt.image.BufferedImage img1 = BitmapEncoder.getBufferedImage(chart1);
        java.awt.image.BufferedImage img2 = BitmapEncoder.getBufferedImage(chart2);
        
        int width = img1.getWidth() + img2.getWidth();
        int height = Math.max(img1.getHeight(), img2.getHeight());
        
        java.awt.image.BufferedImage combined = new java.awt.image.BufferedImage(width, height, 
                                                                                 java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = combined.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        
        g.drawImage(img1, 0, 0, null);
        g.drawImage(img2, img1.getWidth(), 0, null);
        
        g.dispose();
        return combined;
    }
    
    /**
     * Méthodes de génération de graphes supprimées - remplacées par generateDetailedComparisonGraphs,
     * generateDualCombinedGraph et generateSingleGraph qui utilisent Python RL au lieu de Java QL
     */
    
    // Anciennes méthodes supprimées:
    // - generateResponseTimeGraph (remplacée)
    // - generateCpuUsageGraph (remplacée)
    // - generateBandwidthPriceGraph (remplacée)
    // - generateCumulativeBwCostGraph (remplacée)
    // - generateStandardGraphs (remplacée)
    
    // Méthode generateStandardGraphs supprimée - elle utilisait encore Java QL et n'était plus appelée
    
    /**
     * Crée les données X pour les graphes
     */
    private static List<Integer> createXData(int size) {
        List<Integer> xData = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            xData.add(i);
        }
        return xData;
    }
    
    /**
     * Classe pour stocker les résultats d'une approche
     */
    static class ComparisonResult {
        String name;
        double totalReward;
        double totalCost;
        double avgLatency;
        double slaCompliance;
        double budgetRemaining;
        List<Double> latencies;
        List<Double> costs;
        List<Integer> replicas;
        String statistics;
        
        // Métriques supplémentaires pour les graphes détaillés
        List<Double> cpuUsage;
        List<Double> bandwidthCosts;
        List<Double> cumulativeBwCosts;
        List<Double> responseTimes;
        List<Double> storageUsage;
        
        public ComparisonResult() {
            latencies = new ArrayList<>();
            costs = new ArrayList<>();
            replicas = new ArrayList<>();
            cpuUsage = new ArrayList<>();
            bandwidthCosts = new ArrayList<>();
            cumulativeBwCosts = new ArrayList<>();
            responseTimes = new ArrayList<>();
            storageUsage = new ArrayList<>();
        }
    }
}
