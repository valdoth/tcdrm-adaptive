package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonQLearningAgent;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import org.tcdrm.adaptive.rl.TcdrmEnvironment;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generate ALL graphs from the TCDRM article with 3 policies using REAL Python RL model:
 * - TCDRM (Python RL) - Real trained Q-Learning model via Py4J
 * - TCDRM Statique - Fixed thresholds from article
 * - NOREP - No replication
 * 
 * This version uses Py4J Gateway to connect to the real trained Python model.
 * Architecture: Java starts Gateway Server → Python client connects with trained model
 */
public class TcdrmArticleAllGraphs3CurvesWithPy4J {
    
    private static Py4JGateway gateway;
    private static Object pythonBridge;  // Pont Python direct

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=".repeat(80));
        System.out.println("GÉNÉRATION DES GRAPHES ARTICLE AVEC 3 POLITIQUES (VRAI MODÈLE PYTHON RL)");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Démarrer le Gateway Py4J
        System.out.println("🚀 Démarrage du Py4J Gateway Server...");
        gateway = new Py4JGateway();
        gateway.start();
        
        System.out.println();
        System.out.println("⏳ En attente de la connexion du client Python...");
        System.out.println("   Le client Python doit charger le modèle entraîné et se connecter.");
        System.out.println("   Timeout: 120 secondes");
        System.out.println();
        
        // Attendre que le client Python se connecte avec le modèle
        boolean pythonReady = waitForPythonConnection(120);
        
        if (!pythonReady) {
            System.err.println("❌ ERREUR: Le client Python ne s'est pas connecté dans les temps.");
            System.err.println("   Assurez-vous que le client Python est démarré avec:");
            System.err.println("   cd python_rl && uv run python connect_to_java_for_graphs.py --model <path_to_model>");
            gateway.stop();
            System.exit(1);
        }
        
        // Attendre que Python signale qu'il est prêt
        System.out.println("⏳ Attente que le client Python soit prêt...");
        
        int maxWait = 30; // 30 secondes max
        boolean pythonBridgeReady = false;
        for (int i = 0; i < maxWait; i++) {
            String ready = System.getProperty("python_bridge_ready");
            if ("true".equals(ready)) {
                pythonBridgeReady = true;
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!pythonBridgeReady) {
            System.err.println("❌ ERREUR: Le client Python n'est pas prêt après " + maxWait + "s.");
            gateway.stop();
            System.exit(1);
        }
        
        // Obtenir l'agent Python (contient les méthodes de délégation)
        PythonQLearningAgent pythonAgent = gateway.getPythonAgent();
        pythonBridge = pythonAgent;  // Utiliser l'agent comme pont
        
        System.out.println("✅ Client Python connecté avec le modèle entraîné!");
        System.out.println("✅ Utilisation de l'agent Python pour les appels de méthodes");
        System.out.println();
        
        // Créer le répertoire de sortie
        new File("images").mkdirs();
        
        // R1 = F1 + F41 + F80 (3 fragments from different regions)
        double dataGbR1 = 5.3;
        generateAllGraphsForQuery("R1", Arrays.asList(1.5, 2.0, 1.8), 3, dataGbR1);
        
        // R2 = F2 + F21 + F32 + F45 + F71 + F80 (6 fragments from different regions)
        double dataGbR2 = 11.9;
        generateAllGraphsForQuery("R2", Arrays.asList(1.8, 2.2, 1.5, 2.5, 1.9, 2.0), 3, dataGbR2);
        
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("✅ GÉNÉRATION TERMINÉE");
        System.out.println("=".repeat(80));
        System.out.println("Graphes sauvegardés dans: images/ (avec suffixe '_3curves')");
        
        // Arrêter le Gateway Py4J
        System.out.println();
        System.out.println("🛑 Arrêt du Gateway Py4J...");
        gateway.stop();
    }
    
    /**
     * Attend que le client Python se connecte avec le modèle entraîné
     */
    private static boolean waitForPythonConnection(int timeoutSeconds) throws InterruptedException {
        int elapsed = 0;
        int checkInterval = 2; // Vérifier toutes les 2 secondes
        
        while (elapsed < timeoutSeconds) {
            if (gateway.getPythonAgent() != null && gateway.getPythonAgent().isModelLoaded()) {
                return true;
            }
            Thread.sleep(checkInterval * 1000);
            elapsed += checkInterval;
            
            if (elapsed % 10 == 0) {
                System.out.println("   ... toujours en attente (" + elapsed + "s/" + timeoutSeconds + "s)");
            }
        }
        
        return false;
    }

    private static void generateAllGraphsForQuery(String queryId, List<Double> fragmentSizes, 
                                                  int replicationFactor, double dataGb) throws IOException {
        System.out.println("\n=== Generating ALL combined graphs for query: " + queryId + " ===");
        System.out.println("Fragment sizes: " + fragmentSizes);
        System.out.println("Replication factor: " + replicationFactor);
        System.out.println("Total data size: " + String.format("%.2f", dataGb) + " GB");

        // Compute benchmarks pour TCDRM Statique et NOREP
        System.out.println(">>> Calcul des benchmarks TCDRM Statique et NOREP...");
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, 42L);
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(42L);

        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);
        
        // Exécuter le vrai modèle Python RL via Py4J
        System.out.println(">>> Exécution du VRAI modèle Python RL entraîné via Py4J...");
        BenchmarkDataPerQuery pythonRLData = runRealPythonQLearning(queryId, dataGb, 42L);
        
        // Log data sizes for verification
        int sampleIndex = Math.min(500, pythonRLData.timePerQueryMs().size() - 1);
        if (sampleIndex >= 0) {
            System.out.println("  Sample Python RL time at query " + sampleIndex + ": " + String.format("%.2f", pythonRLData.timePerQueryMs().get(sampleIndex)) + " s");
            System.out.println("  Sample TCDRM time at query " + sampleIndex + ": " + String.format("%.2f", tcdrmData.timePerQueryMs().get(sampleIndex)) + " s");
            System.out.println("  Sample NOREP time at query " + sampleIndex + ": " + String.format("%.2f", norepData.timePerQueryMs().get(sampleIndex)) + " s");
        }

        // 1. Response Time (dual: raw + smoothed)
        generateDualGraph(queryId, "response_time", "Impact of Replication on Response Time",
                         "Number of Queries", "Response Time (seconds)",
                         pythonRLData.queryNumbers(), pythonRLData.timePerQueryMs(),
                         tcdrmData.queryNumbers(), tcdrmData.timePerQueryMs(),
                         norepData.queryNumbers(), norepData.timePerQueryMs());
        
        // 2. CPU Consumption (dual: raw + smoothed)
        List<Double> pythonRLCpu = extractCpuCost(pythonRLData);
        List<Double> tcdrmCpu = extractCpuCost(tcdrmData);
        List<Double> norepCpu = extractCpuCost(norepData);
        generateDualGraph(queryId, "cpu_consumption", "Impact of Replication on CPU Consumption",
                         "Number of Queries", "CPU Cost ($)",
                         pythonRLData.queryNumbers(), pythonRLCpu,
                         tcdrmData.queryNumbers(), tcdrmCpu,
                         norepData.queryNumbers(), norepCpu);
        
        // 3. BW Price per Query (dual: raw + smoothed)
        generateDualGraph(queryId, "bw_price_per_query", "Impact of Replication on BW PRICE",
                         "Number of Queries", "BW Cost per Query ($)",
                         pythonRLData.queryNumbers(), pythonRLData.costPerQuery(),
                         tcdrmData.queryNumbers(), tcdrmData.costPerQuery(),
                         norepData.queryNumbers(), norepData.costPerQuery());
        
        // 4. Cumulative BW Price (dual: raw + smoothed)
        generateDualGraph(queryId, "cumulative_bw_price", "PRIX CUMULATIF BW",
                         "Number of Queries", "Cumulative BW Cost ($)",
                         pythonRLData.queryNumbers(), pythonRLData.cumulativeCost(),
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());
        
        // 5. Total Cost (dual: raw + smoothed)
        generateDualGraph(queryId, "total_cost", "Total Cost Comparison",
                         "Number of Queries", "Total Cost ($)",
                         pythonRLData.queryNumbers(), pythonRLData.cumulativeCost(),
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());

        System.out.println("✓ All combined graphs (3 curves with REAL Python RL) generated for " + queryId + "\n");
    }

    /**
     * Exécute le VRAI modèle Python RL entraîné via Py4J
     * Le modèle a été entraîné avec Q-Learning tabulaire sur 1000 épisodes
     */
    private static BenchmarkDataPerQuery runRealPythonQLearning(String queryId, double dataGb, Long seed) {
        System.out.println("   Utilisation du modèle Python RL entraîné via Py4J Gateway...");
        
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQuery = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCount = new ArrayList<>();
        
        int maxQueries = 1000;
        double cumCost = 0.0;
        
        try {
            // Obtenir le pont Python via l'agent
            PythonQLearningAgent agent = (PythonQLearningAgent) pythonBridge;
            PythonRLBridge bridge = (PythonRLBridge) agent.getPythonBridge();
            
            if (bridge == null) {
                throw new RuntimeException("Pont Python non initialisé dans l'agent");
            }
            
            // Initialiser l'épisode dans le modèle Python
            bridge.resetEpisode(dataGb, seed.intValue());
            
            for (int i = 0; i < maxQueries; i++) {
                // Obtenir l'état actuel depuis Python
                java.util.List<Double> state = bridge.getCurrentState();
                
                // Le modèle Python sélectionne l'action (utilise la Q-table entraînée)
                int action = bridge.selectAction(state);
                
                // Exécuter l'action et obtenir les résultats
                java.util.List<Double> stepResult = bridge.executeStep(action);
                
                // stepResult = [latency, cost, replicas, reward, done]
                double latency = stepResult.get(0);
                double cost = stepResult.get(1);
                int replicas = stepResult.get(2).intValue();
                
                queryNumbers.add(i);
                timePerQuery.add(latency);
                costPerQuery.add(cost);
                cumCost += cost;
                cumulativeCost.add(cumCost);
                replicaCount.add(replicas);
                
                // Si l'épisode est terminé, on arrête
                if (stepResult.get(4) > 0.5) {
                    break;
                }
            }
            
            System.out.println("   ✓ Modèle Python RL exécuté: " + queryNumbers.size() + " requêtes simulées");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'exécution du modèle Python RL: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible d'exécuter le modèle Python RL", e);
        }
        
        return new BenchmarkDataPerQuery(queryId + "_PythonRL", queryNumbers, timePerQuery, 
                                         costPerQuery, cumulativeCost, replicaCount);
    }

    private static void generateDualGraph(String queryId, String graphType, String title,
                                          String xLabel, String yLabel,
                                          List<Integer> pythonX, List<Double> pythonY,
                                          List<Integer> tcdrmX, List<Double> tcdrmY,
                                          List<Integer> norepX, List<Double> norepY) throws IOException {
        // Raw chart (left)
        XYChart rawChart = createChart(title + " (Raw) - " + queryId, xLabel, yLabel);
        addSeries(rawChart, "TCDRM (Python RL)", pythonX, pythonY, new Color(255, 193, 7), 1.0f);
        addSeries(rawChart, "NOREP", norepX, norepY, new Color(255, 127, 14), 1.0f);
        addSeries(rawChart, "TCDRM Statique", tcdrmX, tcdrmY, new Color(244, 67, 54), 1.2f);
        
        // Smoothed chart (right)
        XYChart smoothChart = createChart(title + " (Smoothed) - " + queryId, xLabel, yLabel);
        List<Double> pythonSmoothed = movingAverage(pythonY, 50);
        List<Double> tcdrmSmoothed = movingAverage(tcdrmY, 50);
        List<Double> norepSmoothed = movingAverage(norepY, 50);
        addSeries(smoothChart, "TCDRM (Python RL)", pythonX, pythonSmoothed, new Color(255, 193, 7), 2.5f);
        addSeries(smoothChart, "NOREP", norepX, norepSmoothed, new Color(255, 127, 14), 2.5f);
        addSeries(smoothChart, "TCDRM Statique", tcdrmX, tcdrmSmoothed, new Color(244, 67, 54), 3.0f);
        
        // Combine side by side - save with '_3curves' suffix
        combineTwoCharts(rawChart, smoothChart, "images/tcdrm_combined_" + graphType + "_" + queryId + "_3curves.png");
        System.out.println("  ✓ " + title + " (combined, 3 curves with REAL Python RL)");
    }

    private static List<Double> extractCpuCost(BenchmarkDataPerQuery data) {
        List<Double> cpuCumul = new ArrayList<>();
        double sum = 0.0;
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            sum += data.costPerQuery().get(i) * 0.1; // 10% assumed CPU
            cpuCumul.add(sum);
        }
        return cpuCumul;
    }

    private static void combineTwoCharts(XYChart leftChart, XYChart rightChart, String filename) throws IOException {
        BufferedImage leftImage = BitmapEncoder.getBufferedImage(leftChart);
        BufferedImage rightImage = BitmapEncoder.getBufferedImage(rightChart);

        int width = leftImage.getWidth() + rightImage.getWidth();
        int height = Math.max(leftImage.getHeight(), rightImage.getHeight());

        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = combined.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        
        g.drawImage(leftImage, 0, 0, null);
        g.drawImage(rightImage, leftImage.getWidth(), 0, null);
        g.dispose();

        ImageIO.write(combined, "png", new File(filename));
    }

    private static XYChart createChart(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(600)
            .height(400)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisTickMarkSpacingHint(100);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax(1000.0);
        
        return chart;
    }

    private static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float width) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineWidth(width);
    }

    private static List<Double> movingAverage(List<Double> data, int windowSize) {
        List<Double> smoothed = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.size(), i + windowSize / 2 + 1);
            
            double sum = 0.0;
            for (int j = start; j < end; j++) {
                sum += data.get(j);
            }
            smoothed.add(sum / (end - start));
        }
        return smoothed;
    }
}
