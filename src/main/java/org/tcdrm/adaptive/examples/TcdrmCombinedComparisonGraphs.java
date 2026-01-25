package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;

/**
 * Génère des graphes combinés (2 par image) avec 3 courbes:
 * 1. TCDRM avec Python RL (Reinforcement Learning - Tabular Q-Learning)
 * 2. TCDRM Statique (seuils fixes de l'article)
 * 3. NOREP (pas de réplication)
 * Basé sur les templates fournis par l'utilisateur
 */
public class TcdrmCombinedComparisonGraphs {
    
    private static final String OUTPUT_DIR = "results/cloudsim_comparison/";
    private static Py4JGateway gateway;
    
    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("GÉNÉRATION DES GRAPHES COMBINÉS (2 par image, 3 courbes)");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Démarrer le Gateway Py4J
        System.out.println("🚀 Démarrage du Gateway Py4J...");
        gateway = new Py4JGateway();
        gateway.start();
        
        System.out.println();
        System.out.println("⏳ En attente de la connexion Python...");
        boolean pythonReady = gateway.waitForPythonModel(60);
        
        if (pythonReady) {
            System.out.println("✅ Modèle Python enregistré et prêt!");
        } else {
            System.out.println("⚠️  Python non connecté - graphes sans Python QL");
        }
        System.out.println();
        
        // Créer le répertoire de sortie
        new File(OUTPUT_DIR).mkdirs();
        
        // Paramètres de simulation
        double dataGbR1 = 5.3;
        double dataGbR2 = 11.9;
        Long seed = 42L;
        
        // Générer les résultats pour R1 et R2 (Python QL, TCDRM Statique, NOREP)
        System.out.println(">>> Génération des résultats pour R1 et R2...");
        ComparisonResult pythonQLR1 = runPythonQLearning(dataGbR1, seed);
        ComparisonResult staticR1 = runStaticTcdrm(dataGbR1, seed);
        ComparisonResult norepR1 = runNoReplication(dataGbR1, seed);
        
        ComparisonResult pythonQLR2 = runPythonQLearning(dataGbR2, seed);
        ComparisonResult staticR2 = runStaticTcdrm(dataGbR2, seed);
        ComparisonResult norepR2 = runNoReplication(dataGbR2, seed);
        
        System.out.println();
        System.out.println(">>> Génération des graphes combinés...");
        
        // Image 1: Response Time (2 vues différentes)
        generateResponseTimeComparison("R1", pythonQLR1, staticR1, norepR1);
        generateResponseTimeComparison("R2", pythonQLR2, staticR2, norepR2);
        
        // Image 2: BW Price + Cumulative BW Cost
        generateBandwidthComparison("R1", pythonQLR1, staticR1, norepR1);
        generateBandwidthComparison("R2", pythonQLR2, staticR2, norepR2);
        
        // Image 3: CPU Usage + Latency
        generateCpuLatencyComparison("R1", pythonQLR1, staticR1, norepR1);
        generateCpuLatencyComparison("R2", pythonQLR2, staticR2, norepR2);
        
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("✅ GÉNÉRATION TERMINÉE");
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
     * Image 1: Response Time (2 graphes côte à côte)
     */
    private static void generateResponseTimeComparison(String queryId, ComparisonResult pythonQL, 
                                                       ComparisonResult staticTcdrm, ComparisonResult norep) throws IOException {
        // Graphe gauche: Response Time brut
        XYChart chart1 = createResponseTimeChart("Impact of Replication on Response Time", 
                                                 pythonQL, staticTcdrm, norep);
        
        // Graphe droite: Response Time lissé
        XYChart chart2 = createSmoothedResponseTimeChart("Impact of Replication on Response Time (Smoothed)", 
                                                         pythonQL, staticTcdrm, norep);
        
        // Combiner les 2 graphes en une seule image
        BufferedImage combined = combineCharts(chart1, chart2);
        ImageIO.write(combined, "PNG", new File(OUTPUT_DIR + "response_time_comparison_" + queryId + ".png"));
        
        System.out.println("✅ " + OUTPUT_DIR + "response_time_comparison_" + queryId + ".png");
    }
    
    /**
     * Image 2: BW Price + Cumulative BW Cost
     */
    private static void generateBandwidthComparison(String queryId, ComparisonResult pythonQL, 
                                                    ComparisonResult staticTcdrm, ComparisonResult norep) throws IOException {
        // Graphe gauche: BW Price
        XYChart chart1 = createBandwidthPriceChart("Impact of Replication on BW PRICE", 
                                                   pythonQL, staticTcdrm, norep);
        
        // Graphe droite: Cumulative BW Cost
        XYChart chart2 = createCumulativeBwCostChart("PRIX CUMULATIF BW", 
                                                     pythonQL, staticTcdrm, norep);
        
        // Combiner les 2 graphes
        BufferedImage combined = combineCharts(chart1, chart2);
        ImageIO.write(combined, "PNG", new File(OUTPUT_DIR + "bandwidth_comparison_" + queryId + ".png"));
        
        System.out.println("✅ " + OUTPUT_DIR + "bandwidth_comparison_" + queryId + ".png");
    }
    
    /**
     * Image 3: CPU Usage + Latency
     */
    private static void generateCpuLatencyComparison(String queryId, ComparisonResult pythonQL, 
                                                     ComparisonResult staticTcdrm, ComparisonResult norep) throws IOException {
        // Graphe gauche: CPU Usage
        XYChart chart1 = createCpuUsageChart("Impact of Replication on CPU Consumption", 
                                            pythonQL, staticTcdrm, norep);
        
        // Graphe droite: Latency
        XYChart chart2 = createLatencyChart("Latency Comparison", 
                                           pythonQL, staticTcdrm, norep);
        
        // Combiner les 2 graphes
        BufferedImage combined = combineCharts(chart1, chart2);
        ImageIO.write(combined, "PNG", new File(OUTPUT_DIR + "cpu_latency_comparison_" + queryId + ".png"));
        
        System.out.println("✅ " + OUTPUT_DIR + "cpu_latency_comparison_" + queryId + ".png");
    }
    
    /**
     * Crée un graphe Response Time avec 3 courbes
     */
    private static XYChart createResponseTimeChart(String title, ComparisonResult pythonQL, 
                                                   ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
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
        
        return chart;
    }
    
    /**
     * Crée un graphe Response Time lissé avec 3 courbes
     */
    private static XYChart createSmoothedResponseTimeChart(String title, ComparisonResult pythonQL, 
                                                          ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Request Number")
            .yAxisTitle("Response Time (ms)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        // Lisser les données (moyenne mobile)
        if (!pythonQL.responseTimes.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.responseTimes.size()), 
                           smoothData(pythonQL.responseTimes, 10));
        }
        chart.addSeries("TCDRM Statique", createXData(staticTcdrm.responseTimes.size()), 
                       smoothData(staticTcdrm.responseTimes, 10));
        chart.addSeries("NOREP", createXData(norep.responseTimes.size()), 
                       smoothData(norep.responseTimes, 10));
        
        return chart;
    }
    
    /**
     * Crée un graphe BW Price avec 3 courbes
     */
    private static XYChart createBandwidthPriceChart(String title, ComparisonResult pythonQL, 
                                                    ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Request Number")
            .yAxisTitle("BW Price ($)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        if (!pythonQL.bandwidthCosts.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.bandwidthCosts.size()), pythonQL.bandwidthCosts);
        }
        chart.addSeries("TCDRM Statique", createXData(staticTcdrm.bandwidthCosts.size()), staticTcdrm.bandwidthCosts);
        chart.addSeries("NOREP", createXData(norep.bandwidthCosts.size()), norep.bandwidthCosts);
        
        return chart;
    }
    
    /**
     * Crée un graphe Cumulative BW Cost avec 3 courbes
     */
    private static XYChart createCumulativeBwCostChart(String title, ComparisonResult pythonQL, 
                                                      ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Request Number")
            .yAxisTitle("Cumulative BW Cost ($)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        if (!pythonQL.cumulativeBwCosts.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.cumulativeBwCosts.size()), pythonQL.cumulativeBwCosts);
        }
        chart.addSeries("TCDRM Statique", createXData(staticTcdrm.cumulativeBwCosts.size()), staticTcdrm.cumulativeBwCosts);
        chart.addSeries("NOREP", createXData(norep.cumulativeBwCosts.size()), norep.cumulativeBwCosts);
        
        return chart;
    }
    
    /**
     * Crée un graphe CPU Usage avec 3 courbes
     */
    private static XYChart createCpuUsageChart(String title, ComparisonResult pythonQL, 
                                              ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Request Number")
            .yAxisTitle("CPU Usage (%)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        if (!pythonQL.cpuUsage.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.cpuUsage.size()), pythonQL.cpuUsage);
        }
        chart.addSeries("TCDRM Statique", createXData(staticTcdrm.cpuUsage.size()), staticTcdrm.cpuUsage);
        chart.addSeries("NOREP", createXData(norep.cpuUsage.size()), norep.cpuUsage);
        
        return chart;
    }
    
    /**
     * Crée un graphe Latency avec 3 courbes
     */
    private static XYChart createLatencyChart(String title, ComparisonResult pythonQL, 
                                             ComparisonResult staticTcdrm, ComparisonResult norep) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Request Number")
            .yAxisTitle("Latency (ms)")
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        
        if (!pythonQL.latencies.isEmpty()) {
            chart.addSeries("TCDRM (Python RL)", createXData(pythonQL.latencies.size()), pythonQL.latencies);
        }
        chart.addSeries("TCDRM Statique", createXData(staticTcdrm.latencies.size()), staticTcdrm.latencies);
        chart.addSeries("NOREP", createXData(norep.latencies.size()), norep.latencies);
        
        return chart;
    }
    
    /**
     * Combine 2 graphes en une seule image (côte à côte)
     */
    private static BufferedImage combineCharts(XYChart chart1, XYChart chart2) {
        BufferedImage img1 = BitmapEncoder.getBufferedImage(chart1);
        BufferedImage img2 = BitmapEncoder.getBufferedImage(chart2);
        
        int width = img1.getWidth() + img2.getWidth();
        int height = Math.max(img1.getHeight(), img2.getHeight());
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        
        g.drawImage(img1, 0, 0, null);
        g.drawImage(img2, img1.getWidth(), 0, null);
        
        g.dispose();
        return combined;
    }
    
    /**
     * Lisse les données avec une moyenne mobile
     */
    private static List<Double> smoothData(List<Double> data, int windowSize) {
        List<Double> smoothed = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.size(), i + windowSize / 2 + 1);
            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += data.get(j);
            }
            smoothed.add(sum / (end - start));
        }
        return smoothed;
    }
    
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
    
    // Méthodes de simulation
    
    private static ComparisonResult runPythonQLearning(double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        
        boolean modelReady = gateway != null && gateway.getPythonAgent() != null && gateway.getPythonAgent().isModelLoaded();
        
        if (!modelReady) {
            return createEmptyResult("Python QL (NON CONNECTÉ)");
        }
        
        PythonQLearningPolicy policy = new PythonQLearningPolicy(gateway.getPythonAgent());
        return evaluatePolicy(env, policy, "Python QL", seed);
    }
    
    private static ComparisonResult runStaticTcdrm(double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        StaticTcdrmPolicy policy = new StaticTcdrmPolicy();
        return evaluatePolicy(env, policy, "TCDRM Statique", seed);
    }
    
    private static ComparisonResult runNoReplication(double dataGb, Long seed) {
        TcdrmEnvironment env = new TcdrmEnvironment(dataGb);
        return evaluatePolicy(env, null, "NOREP", seed);
    }
    
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
                int accessCount = state.getPopularityLevel().ordinal() * 100;
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
            
            double bwCost = calculateBandwidthCost(currentReplicas, dataGb);
            double storageCost = calculateStorageCost(currentReplicas, dataGb);
            double replicationCost = (action == TcdrmAction.CREATE_REPLICA) ? (0.10 * dataGb) : 0.0;
            double stepCost = bwCost + storageCost + replicationCost;
            
            totalCost += stepCost;
            cumulativeBwCost += bwCost;
            
            double cpuUsage = calculateCpuUsage(currentReplicas, env.getCurrentLatency());
            double responseTime = env.getCurrentLatency() + (currentReplicas * 2.0);
            double latency = env.getCurrentLatency();
            
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
        
        return result;
    }
    
    private static double calculateBandwidthCost(int replicas, double dataGb) {
        if (replicas > 0) {
            return 0.002 * dataGb;
        } else {
            return 0.10 * dataGb;
        }
    }
    
    private static double calculateStorageCost(int replicas, double dataGb) {
        return replicas * dataGb * (0.02 / 720.0);
    }
    
    private static double calculateCpuUsage(int replicas, double latency) {
        double baseUsage = 30.0;
        double replicaFactor = (3 - replicas) * 15.0;
        double latencyFactor = (latency / 200.0) * 20.0;
        return Math.min(100.0, baseUsage + replicaFactor + latencyFactor);
    }
    
    private static ComparisonResult createEmptyResult(String name) {
        ComparisonResult result = new ComparisonResult();
        result.name = name;
        result.totalReward = 0.0;
        result.totalCost = 0.0;
        result.avgLatency = 0.0;
        result.slaCompliance = 0.0;
        result.budgetRemaining = 0.0;
        return result;
    }
    
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
