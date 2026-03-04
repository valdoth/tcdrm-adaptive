package org.tcdrm.adaptive.benchmark;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Génère des graphes de métriques pour TCDRM Statique
 * similaires aux graphes d'entraînement DQN/Q-Learning
 */
public class TcdrmMetricsPlotter {
    
    private static final int CHART_WIDTH = 600;
    private static final int CHART_HEIGHT = 400;
    
    /**
     * Génère un graphe de métriques pour TCDRM Statique
     * Métriques : Reward, Coût, Violations SLA, Changements de réplicas, Reward lissé
     */
    public static void generateMetricsPlot(BenchmarkDataPerQuery data, String outputPath) throws IOException {
        // Créer 5 graphes (2x3 grid, sans Loss qui est spécifique aux NN)
        BufferedImage combined = new BufferedImage(CHART_WIDTH * 3, CHART_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        
        // 1. Reward par requête (ligne 0, col 0)
        XYChart rewardChart = createRewardChart(data);
        BufferedImage rewardImg = BitmapEncoder.getBufferedImage(rewardChart);
        g.drawImage(rewardImg, 0, 0, null);
        
        // 2. Coût par requête (ligne 0, col 1)
        XYChart costChart = createCostChart(data);
        BufferedImage costImg = BitmapEncoder.getBufferedImage(costChart);
        g.drawImage(costImg, CHART_WIDTH, 0, null);
        
        // 3. Violations SLA par requête (ligne 0, col 2)
        XYChart slaChart = createSLAViolationsChart(data);
        BufferedImage slaImg = BitmapEncoder.getBufferedImage(slaChart);
        g.drawImage(slaImg, CHART_WIDTH * 2, 0, null);
        
        // 4. Changements de réplicas (ligne 1, col 0)
        XYChart replicaChangesChart = createReplicaChangesChart(data);
        BufferedImage replicaImg = BitmapEncoder.getBufferedImage(replicaChangesChart);
        g.drawImage(replicaImg, 0, CHART_HEIGHT, null);
        
        // 5. Espace vide (ligne 1, col 1) - pas de Loss pour TCDRM Statique
        g.setColor(Color.WHITE);
        g.fillRect(CHART_WIDTH, CHART_HEIGHT, CHART_WIDTH, CHART_HEIGHT);
        g.setColor(Color.GRAY);
        g.drawString("N/A (pas de réseau de neurones)", CHART_WIDTH + 200, CHART_HEIGHT + 200);
        
        // 6. Reward lissé (ligne 1, col 2)
        XYChart smoothedRewardChart = createSmoothedRewardChart(data);
        BufferedImage smoothedImg = BitmapEncoder.getBufferedImage(smoothedRewardChart);
        g.drawImage(smoothedImg, CHART_WIDTH * 2, CHART_HEIGHT, null);
        
        g.dispose();
        
        // Sauvegarder l'image combinée
        ImageIO.write(combined, "png", new File(outputPath));
        System.out.println("  ✓ Graphes de métriques TCDRM Statique générés: " + outputPath);
    }
    
    private static XYChart createRewardChart(BenchmarkDataPerQuery data) {
        XYChart chart = createBaseChart("Reward par requête", "Requête", "Reward");
        
        // Calculer le reward (négatif du coût + pénalité pour latence élevée)
        List<Double> rewards = new ArrayList<>();
        double SLA_THRESHOLD = 200.0; // ms (Article Tableau 1)
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            double latency = data.timePerQueryMs().get(i) * 1000; // s to ms
            double cost = data.costPerQuery().get(i);
            double slaViolation = latency > SLA_THRESHOLD ? -10.0 : 0.0;
            double reward = -cost * 100 + slaViolation; // Échelle similaire à RL
            rewards.add(reward);
        }
        
        addSeries(chart, "TCDRM Statique", data.queryNumbers(), rewards, Color.BLUE, 1.0f);
        return chart;
    }
    
    private static XYChart createCostChart(BenchmarkDataPerQuery data) {
        XYChart chart = createBaseChart("Coût par requête", "Requête", "Coût total");
        addSeries(chart, "TCDRM Statique", data.queryNumbers(), data.cumulativeCost(), Color.BLUE, 1.0f);
        return chart;
    }
    
    private static XYChart createSLAViolationsChart(BenchmarkDataPerQuery data) {
        XYChart chart = createBaseChart("Violations SLA par requête", "Requête", "Nombre de violations");
        
        // Compter les violations SLA cumulatives
        List<Integer> violations = new ArrayList<>();
        int totalViolations = 0;
        double SLA_THRESHOLD = 150.0; // ms
        
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            double latency = data.timePerQueryMs().get(i) * 1000; // s to ms
            if (latency > SLA_THRESHOLD) {
                totalViolations++;
            }
            violations.add(totalViolations);
        }
        
        addSeriesInt(chart, "TCDRM Statique", data.queryNumbers(), violations, Color.BLUE, 1.0f);
        return chart;
    }
    
    private static XYChart createReplicaChangesChart(BenchmarkDataPerQuery data) {
        XYChart chart = createBaseChart("Changements de réplicas par requête", "Requête", "Nombre de changements");
        
        // Compter les changements de réplicas
        List<Integer> changes = new ArrayList<>();
        int totalChanges = 0;
        int previousReplicas = 0;
        
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            int currentReplicas = data.replicaCount().get(i);
            if (currentReplicas != previousReplicas) {
                totalChanges++;
            }
            changes.add(totalChanges);
            previousReplicas = currentReplicas;
        }
        
        addSeriesInt(chart, "TCDRM Statique", data.queryNumbers(), changes, Color.BLUE, 1.0f);
        return chart;
    }
    
    private static XYChart createSmoothedRewardChart(BenchmarkDataPerQuery data) {
        XYChart chart = createBaseChart("Reward lissé (fenêtre=10)", "Requête", "Reward");
        
        // Calculer les rewards
        List<Double> rewards = new ArrayList<>();
        double SLA_THRESHOLD = 150.0; // ms
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            double latency = data.timePerQueryMs().get(i) * 1000; // s to ms
            double cost = data.costPerQuery().get(i);
            double slaViolation = latency > SLA_THRESHOLD ? -10.0 : 0.0;
            double reward = -cost * 100 + slaViolation;
            rewards.add(reward);
        }
        
        // Lisser avec fenêtre mobile
        List<Double> smoothed = movingAverage(rewards, 10);
        List<Integer> smoothedX = new ArrayList<>();
        for (int i = 0; i < smoothed.size(); i++) {
            smoothedX.add(i);
        }
        
        addSeries(chart, "TCDRM Statique", smoothedX, smoothed, Color.BLUE, 2.0f);
        return chart;
    }
    
    private static XYChart createBaseChart(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(CHART_WIDTH)
            .height(CHART_HEIGHT)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        return chart;
    }
    
    private static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float width) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineWidth(width);
    }
    
    private static void addSeriesInt(XYChart chart, String name, List<Integer> x, List<Integer> y, Color color, float width) {
        List<Double> yDouble = new ArrayList<>();
        for (Integer val : y) {
            yDouble.add(val.doubleValue());
        }
        addSeries(chart, name, x, yDouble, color, width);
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
