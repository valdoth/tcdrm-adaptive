package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * TCDRM CloudSim Comparison - Simulation complète avec modèles RL Python
 * Compare les politiques de réplication:
 * - Q-Learning Simple (Python via Py4J)
 * - DQN (Python via Py4J) - optionnel
 * - TCDRM Statique - Seuils fixes de l'article
 * - NOREP - Pas de réplication
 * 
 * Architecture: Java Gateway Server → Client Python avec modèles entraînés
 */
public class TcdrmComparisonCloudSim {
    
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
        
        // Attendre que le client Python se connecte et enregistre le pont
        System.out.println("⏳ Attente de l'enregistrement du pont Python...");
        
        int maxWait = 120; // 120 secondes max
        int elapsed = 0;
        Object bridge = null;
        
        while (elapsed < maxWait) {
            bridge = gateway.getPythonBridge();
            if (bridge != null) {
                System.out.println("✅ Pont Python enregistré!");
                break;
            }
            
            try {
                Thread.sleep(2000);
                elapsed += 2;
                
                if (elapsed % 10 == 0) {
                    System.out.println("   ... toujours en attente (" + elapsed + "s/" + maxWait + "s)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (bridge == null) {
            System.err.println("❌ ERREUR: Le client Python ne s'est pas connecté dans les temps.");
            System.err.println("   Assurez-vous que le client Python est démarré avec:");
            System.err.println("   cd python_rl && uv run python connect_to_java.py --qlearning-model <path> --dqn-model <path>");
            gateway.stop();
            System.exit(1);
        }
        
        pythonBridge = bridge;
        PythonRLBridge rlBridge = (PythonRLBridge) bridge;
        
        System.out.println("✅ Client Python connecté avec le modèle entraîné!");
        System.out.println("✅ Utilisation de l'agent Python pour les appels de méthodes");
        System.out.println();
        
        // Créer le répertoire de sortie
        new File("images").mkdirs();
        
        // R1 = F1 + F41 + F80 (3 fragments from different regions)
        double dataGbR1 = 5.3;
        generateAllGraphsForQuery(rlBridge, "R1", Arrays.asList(1.5, 2.0, 1.8), 3, dataGbR1);
        
        // R2 = F2 + F21 + F32 + F45 + F71 + F80 (6 fragments from different regions)
        double dataGbR2 = 11.9;
        generateAllGraphsForQuery(rlBridge, "R2", Arrays.asList(1.8, 2.2, 1.5, 2.5, 1.9, 2.0), 3, dataGbR2);
        
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

    private static void generateAllGraphsForQuery(PythonRLBridge bridge, String queryId, List<Double> fragmentSizes, 
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
        
        // Exécuter le vrai modèle Python RL (Q-Learning) via Py4J
        System.out.println(">>> Exécution du VRAI modèle Python Q-Learning entraîné via Py4J...");
        BenchmarkDataPerQuery pythonQLearningData = runRealPythonQLearning(bridge, queryId, dataGb, 42L);
        
        // Exécuter le vrai modèle Python RL (DQN) via Py4J
        System.out.println(">>> Exécution du VRAI modèle Python DQN entraîné via Py4J...");
        BenchmarkDataPerQuery pythonDQNData = runRealPythonDQN(bridge, queryId, dataGb, 43L);
        
        // Log data sizes for verification
        int sampleIndex = Math.min(500, pythonQLearningData.timePerQueryMs().size() - 1);
        if (sampleIndex >= 0) {
            System.out.println("  Sample Q-Learning time at query " + sampleIndex + ": " + String.format("%.2f", pythonQLearningData.timePerQueryMs().get(sampleIndex)) + " s");
            System.out.println("  Sample DQN time at query " + sampleIndex + ": " + String.format("%.2f", pythonDQNData.timePerQueryMs().get(sampleIndex)) + " s");
            System.out.println("  Sample TCDRM time at query " + sampleIndex + ": " + String.format("%.2f", tcdrmData.timePerQueryMs().get(sampleIndex)) + " s");
            System.out.println("  Sample NOREP time at query " + sampleIndex + ": " + String.format("%.2f", norepData.timePerQueryMs().get(sampleIndex)) + " s");
        }

        // 1. Response Time - 4 curves (Q-Learning, DQN, TCDRM, NOREP)
        generateQuadGraph(queryId, "response_time", "Impact of Replication on Response Time",
                         "Number of Queries", "Response Time (seconds)",
                         pythonQLearningData.queryNumbers(), pythonQLearningData.timePerQueryMs(),
                         pythonDQNData.queryNumbers(), pythonDQNData.timePerQueryMs(),
                         tcdrmData.queryNumbers(), tcdrmData.timePerQueryMs(),
                         norepData.queryNumbers(), norepData.timePerQueryMs());
        
        // 2. CPU Consumption - 4 curves
        List<Double> pythonQLearningCpu = extractCpuCost(pythonQLearningData);
        List<Double> pythonDQNCpu = extractCpuCost(pythonDQNData);
        List<Double> tcdrmCpu = extractCpuCost(tcdrmData);
        List<Double> norepCpu = extractCpuCost(norepData);
        generateQuadGraph(queryId, "cpu_consumption", "Impact of Replication on CPU Consumption",
                         "Number of Queries", "CPU Cost ($)",
                         pythonQLearningData.queryNumbers(), pythonQLearningCpu,
                         pythonDQNData.queryNumbers(), pythonDQNCpu,
                         tcdrmData.queryNumbers(), tcdrmCpu,
                         norepData.queryNumbers(), norepCpu);
        
        // 3. BW Price per Query - 4 curves
        generateQuadGraph(queryId, "bw_price_per_query", "Impact of Replication on BW PRICE",
                         "Number of Queries", "BW Cost per Query ($)",
                         pythonQLearningData.queryNumbers(), pythonQLearningData.costPerQuery(),
                         pythonDQNData.queryNumbers(), pythonDQNData.costPerQuery(),
                         tcdrmData.queryNumbers(), tcdrmData.costPerQuery(),
                         norepData.queryNumbers(), norepData.costPerQuery());
        
        // 4. Cumulative BW Price - 4 curves
        generateQuadGraph(queryId, "cumulative_bw_price", "PRIX CUMULATIF BW",
                         "Number of Queries", "Cumulative BW Cost ($)",
                         pythonQLearningData.queryNumbers(), pythonQLearningData.cumulativeCost(),
                         pythonDQNData.queryNumbers(), pythonDQNData.cumulativeCost(),
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());
        
        // 5. Total Cost - 4 curves
        generateQuadGraph(queryId, "total_cost", "Total Cost Comparison",
                         "Number of Queries", "Total Cost ($)",
                         pythonQLearningData.queryNumbers(), pythonQLearningData.cumulativeCost(),
                         pythonDQNData.queryNumbers(), pythonDQNData.cumulativeCost(),
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());

        // 6. Graphes de métriques TCDRM Statique (similaires aux graphes d'entraînement)
        System.out.println(">>> Génération des graphes de métriques TCDRM Statique...");
        TcdrmMetricsPlotter.generateMetricsPlot(tcdrmData, "images/tcdrm_metrics_" + queryId + ".png");

        System.out.println("✓ All graphs (4 curves) generated for " + queryId + "\n");
    }

    /**
     * Exécute le VRAI modèle Python Q-Learning entraîné via Py4J
     * Utilise RealRLBenchmark pour exécuter réellement le modèle Python
     */
    private static BenchmarkDataPerQuery runRealPythonQLearning(PythonRLBridge bridge, String queryId, double dataGb, Long seed) {
        System.out.println("   Exécution du VRAI modèle Q-Learning Python via Py4J...");
        
        // Vérifier que le modèle est chargé
        if (!bridge.isQLearningReady()) {
            throw new RuntimeException("❌ Modèle Q-Learning non chargé! Assurez-vous que le client Python est connecté avec --qlearning-model");
        }
        
        try {
            // Créer le benchmark RL réel
            RealRLBenchmark rlBenchmark = new RealRLBenchmark(bridge, "qlearning", seed);
            BenchmarkDataPerQuery result = rlBenchmark.computeBenchmark(queryId + "_QLearning", dataGb);
            
            System.out.println("   ✓ Simulation Q-Learning terminée (VRAIES décisions du modèle Python)");
            return result;
                                             
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la simulation Q-Learning: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible d'exécuter la simulation Q-Learning", e);
        }
    }

    /**
     * Exécute le VRAI modèle Python DQN entraîné via Py4J
     * Utilise RealRLBenchmark pour exécuter réellement le modèle Python
     */
    private static BenchmarkDataPerQuery runRealPythonDQN(PythonRLBridge bridge, String queryId, double dataGb, Long seed) {
        System.out.println("   Exécution du VRAI modèle DQN Python via Py4J...");
        
        // Vérifier que le modèle est chargé
        if (!bridge.isDQNReady()) {
            throw new RuntimeException("❌ Modèle DQN non chargé! Assurez-vous que le client Python est connecté avec --dqn-model");
        }
        
        try {
            // Créer le benchmark RL réel
            RealRLBenchmark rlBenchmark = new RealRLBenchmark(bridge, "dqn", seed);
            BenchmarkDataPerQuery result = rlBenchmark.computeBenchmark(queryId + "_DQN", dataGb);
            
            System.out.println("   ✓ Simulation DQN terminée (VRAIES décisions du modèle Python)");
            return result;
                                             
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la simulation DQN: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible d'exécuter la simulation DQN", e);
        }
    }

    private static void generateQuadGraph(String queryId, String graphType, String title,
                                          String xLabel, String yLabel,
                                          List<Integer> qlearningX, List<Double> qlearningY,
                                          List<Integer> dqnX, List<Double> dqnY,
                                          List<Integer> tcdrmX, List<Double> tcdrmY,
                                          List<Integer> norepX, List<Double> norepY) throws IOException {
        // Raw chart (left) - pour graphes combinés (max 1000)
        XYChart rawChart = createChartCombined(title + " (Raw) - " + queryId, xLabel, yLabel);
        addSeries(rawChart, "Q-Learning", qlearningX, qlearningY, new Color(255, 193, 7), 1.0f);
        addSeries(rawChart, "DQN", dqnX, dqnY, new Color(76, 175, 80), 1.0f);
        addSeries(rawChart, "NOREP", norepX, norepY, new Color(255, 127, 14), 1.0f);
        addSeries(rawChart, "TCDRM Statique", tcdrmX, tcdrmY, new Color(244, 67, 54), 1.2f);
        
        // Smoothed chart (right) - pour graphes combinés (max 1000)
        XYChart smoothChartCombined = createChartCombined(title + " (Smoothed) - " + queryId, xLabel, yLabel);
        List<Double> qlearningSmoothed = movingAverage(qlearningY, 50);
        List<Double> dqnSmoothed = movingAverage(dqnY, 50);
        List<Double> tcdrmSmoothed = movingAverage(tcdrmY, 50);
        List<Double> norepSmoothed = movingAverage(norepY, 50);
        addSeries(smoothChartCombined, "Q-Learning", qlearningX, qlearningSmoothed, new Color(255, 193, 7), 2.5f);
        addSeries(smoothChartCombined, "DQN", dqnX, dqnSmoothed, new Color(76, 175, 80), 2.5f);
        addSeries(smoothChartCombined, "NOREP", norepX, norepSmoothed, new Color(255, 127, 14), 2.5f);
        addSeries(smoothChartCombined, "TCDRM Statique", tcdrmX, tcdrmSmoothed, new Color(244, 67, 54), 3.0f);
        
        // Combine side by side - save with '_4curves' suffix (max X = 1000)
        combineTwoCharts(rawChart, smoothChartCombined, "images/tcdrm_combined_" + graphType + "_" + queryId + "_4curves.png");
        System.out.println("  ✓ " + title + " (4 curves combined: Q-Learning, DQN, TCDRM, NOREP)");
        
        // Save smoothed chart separately (max X = 5000)
        XYChart smoothChartAlone = createChartSmoothedAlone(title + " (Smoothed) - " + queryId, xLabel, yLabel);
        addSeries(smoothChartAlone, "Q-Learning", qlearningX, qlearningSmoothed, new Color(255, 193, 7), 2.5f);
        addSeries(smoothChartAlone, "DQN", dqnX, dqnSmoothed, new Color(76, 175, 80), 2.5f);
        addSeries(smoothChartAlone, "NOREP", norepX, norepSmoothed, new Color(255, 127, 14), 2.5f);
        addSeries(smoothChartAlone, "TCDRM Statique", tcdrmX, tcdrmSmoothed, new Color(244, 67, 54), 3.0f);
        BitmapEncoder.saveBitmap(smoothChartAlone, "images/tcdrm_smoothed_" + graphType + "_" + queryId + "_4curves.png", BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  ✓ " + title + " (4 curves smoothed only: Q-Learning, DQN, TCDRM, NOREP)");
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

    private static XYChart createChartCombined(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(1200)
            .height(500)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisTickMarkSpacingHint(100);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax(1000.0);  // Max 1000 pour les graphes combinés
        
        return chart;
    }

    private static XYChart createChartSmoothedAlone(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(1200)
            .height(500)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisTickMarkSpacingHint(100);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax(5000.0);  // Max 5000 pour les graphes smoothed seuls
        
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
