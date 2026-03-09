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
 * Génère des graphes de métriques pour un seul modèle
 * Métriques : Reward, Coût, Violations SLA, Changements de réplicas, Reward lissé
 */
public class SingleModelMetricsPlotter {
    
    private static final int CHART_WIDTH = 600;
    private static final int CHART_HEIGHT = 400;
    private static final double SLA_THRESHOLD = 200.0; // ms (Article Tableau 1)
    
    /**
     * Génère un graphe de métriques pour un seul modèle
     */
    public static void generateMetricsPlot(
            BenchmarkDataPerQuery data,
            String modelName,
            Color modelColor,
            String outputPath) throws IOException {
        
        // Créer 5 graphes (2x3 grid)
        BufferedImage combined = new BufferedImage(CHART_WIDTH * 3, CHART_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        
        // 1. Reward par requête (ligne 0, col 0)
        XYChart rewardChart = createRewardChart(data, modelName, modelColor);
        BufferedImage rewardImg = BitmapEncoder.getBufferedImage(rewardChart);
        g.drawImage(rewardImg, 0, 0, null);
        
        // 2. Coût cumulatif (ligne 0, col 1)
        XYChart costChart = createCostChart(data, modelName, modelColor);
        BufferedImage costImg = BitmapEncoder.getBufferedImage(costChart);
        g.drawImage(costImg, CHART_WIDTH, 0, null);
        
        // 3. Violations SLA par requête (ligne 0, col 2)
        XYChart slaChart = createSLAViolationsChart(data, modelName, modelColor);
        BufferedImage slaImg = BitmapEncoder.getBufferedImage(slaChart);
        g.drawImage(slaImg, CHART_WIDTH * 2, 0, null);
        
        // 4. Changements de réplicas (ligne 1, col 0)
        XYChart replicaChangesChart = createReplicaChangesChart(data, modelName, modelColor);
        BufferedImage replicaImg = BitmapEncoder.getBufferedImage(replicaChangesChart);
        g.drawImage(replicaImg, 0, CHART_HEIGHT, null);
        
        // 5. Coût par requête (non cumulatif) (ligne 1, col 1)
        XYChart costPerQueryChart = createCostPerQueryChart(data, modelName, modelColor);
        BufferedImage costPerQueryImg = BitmapEncoder.getBufferedImage(costPerQueryChart);
        g.drawImage(costPerQueryImg, CHART_WIDTH, CHART_HEIGHT, null);
        
        // 6. Reward lissé (ligne 1, col 2)
        XYChart smoothedRewardChart = createSmoothedRewardChart(data, modelName, modelColor);
        BufferedImage smoothedImg = BitmapEncoder.getBufferedImage(smoothedRewardChart);
        g.drawImage(smoothedImg, CHART_WIDTH * 2, CHART_HEIGHT, null);
        
        g.dispose();
        
        // Sauvegarder l'image combinée
        ImageIO.write(combined, "png", new File(outputPath));
        System.out.println("  ✓ Graphes de métriques " + modelName + " générés: " + outputPath);
    }
    
    private static XYChart createRewardChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Reward par requête", "Requête", "Reward");
        
        List<Double> rewards = calculateRewards(data);
        addSeries(chart, modelName, data.queryNumbers(), rewards, modelColor, 1.5f);
        
        return chart;
    }
    
    private static List<Double> calculateRewards(BenchmarkDataPerQuery data) {
        List<Double> rewards = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) {
            double latency = data.timePerQueryMs().get(i) * 1000; // s to ms
            double cost = data.costPerQuery().get(i);
            double slaViolation = latency > SLA_THRESHOLD ? -10.0 : 0.0;
            double reward = -cost * 100 + slaViolation; // Échelle similaire à RL
            rewards.add(reward);
        }
        return rewards;
    }
    
    private static XYChart createCostChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Coût cumulatif", "Requête", "Coût total ($)");
        addSeries(chart, modelName, data.queryNumbers(), data.cumulativeCost(), modelColor, 1.5f);
        return chart;
    }
    
    private static XYChart createCostPerQueryChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Coût par requête", "Requête", "Coût ($)");
        addSeries(chart, modelName, data.queryNumbers(), data.costPerQuery(), modelColor, 1.5f);
        return chart;
    }
    
    private static XYChart createSLAViolationsChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Violations SLA cumulatives", "Requête", "Nombre de violations");
        addSeries(chart, modelName, data.queryNumbers(), data.cumulativeSLAViolations(), modelColor, 1.5f);
        return chart;
    }
    
    private static XYChart createReplicaChangesChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Nombre de réplicas", "Requête", "Réplicas");
        addSeriesInt(chart, modelName, data.queryNumbers(), data.replicaCounts(), modelColor, 1.5f);
        return chart;
    }
    
    private static XYChart createSmoothedRewardChart(BenchmarkDataPerQuery data, String modelName, Color modelColor) {
        XYChart chart = createBaseChart("Reward lissé (fenêtre=10)", "Requête", "Reward");
        
        List<Double> rewards = calculateRewards(data);
        List<Double> smoothed = smoothData(rewards, 10);
        addSeries(chart, modelName, data.queryNumbers(), smoothed, modelColor, 1.5f);
        
        return chart;
    }
    
    private static List<Double> smoothData(List<Double> data, int windowSize) {
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
    
    private static XYChart createBaseChart(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(CHART_WIDTH)
            .height(CHART_HEIGHT)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        
        return chart;
    }
    
    private static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float strokeWidth) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineStyle(new BasicStroke(strokeWidth));
        series.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
    }
    
    private static void addSeriesInt(XYChart chart, String name, List<Integer> x, List<Integer> y, Color color, float strokeWidth) {
        // Convertir List<Integer> en List<Double>
        List<Double> yDouble = new ArrayList<>();
        for (Integer val : y) {
            yDouble.add(val.doubleValue());
        }
        addSeries(chart, name, x, yDouble, color, strokeWidth);
    }
}
