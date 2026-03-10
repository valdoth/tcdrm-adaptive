package org.tcdrm.adaptive.benchmark;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates popularity and replication analysis charts.
 * Shows the relationship between query popularity, SLA violations, and replica creation.
 * 
 * Charts generated:
 * 1. Normalized Popularity over time (with P_SLA threshold)
 * 2. Replica Count with replication trigger point
 * 3. Response Time with SLA threshold and replica impact
 * 4. Popularity vs Replicas correlation
 */
public class PopularityMetricsPlotter {
    
    private static final int CHART_WIDTH = 700;
    private static final int CHART_HEIGHT = 450;
    private static final double P_SLA = TcdrmConstants.POPULARITY_THRESHOLD;
    private static final double T_SLA = TcdrmConstants.TSLA_SIMPLE_MS;
    private static final Color THRESHOLD_COLOR = new Color(220, 50, 50, 180);
    private static final Color REPLICA_ZONE_COLOR = new Color(50, 200, 50, 40);
    
    /**
     * Generate popularity analysis charts for a single model.
     */
    public static void generatePopularityCharts(
            BenchmarkDataPerQuery data,
            String modelName,
            Color modelColor,
            String outputPath) throws IOException {
        
        // Create 2x2 grid of charts
        BufferedImage combined = new BufferedImage(CHART_WIDTH * 2, CHART_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        
        // Chart 1: Normalized Popularity
        drawChart(g, createPopularityChart(data, modelName, modelColor), 0, 0);
        
        // Chart 2: Replica Count with trigger zone
        drawChart(g, createReplicaWithTriggerChart(data, modelName, modelColor), CHART_WIDTH, 0);
        
        // Chart 3: Response Time with replica impact zones
        drawChart(g, createResponseTimeZonesChart(data, modelName, modelColor), 0, CHART_HEIGHT);
        
        // Chart 4: Popularity vs Response Time correlation
        drawChart(g, createPopularityVsResponseChart(data, modelName, modelColor), CHART_WIDTH, CHART_HEIGHT);
        
        g.dispose();
        ImageIO.write(combined, "png", new File(outputPath));
        System.out.println("  ✓ Popularity analysis " + modelName + ": " + outputPath);
    }
    
    // === Chart 1: Normalized Popularity ===
    private static XYChart createPopularityChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Normalized Popularity", "Query", "Popularity (P / P_SLA)");
        
        // Calculate normalized popularity: query_number / P_SLA
        List<Double> popularity = new ArrayList<>();
        for (int q : data.queryNumbers()) {
            popularity.add((double)(q + 1) / P_SLA);
        }
        addSeries(chart, modelName, data.queryNumbers(), popularity, color, 2.0f);
        
        // P_SLA threshold line at 1.0
        List<Double> threshold = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) threshold.add(1.0);
        XYSeries thresholdSeries = chart.addSeries("P_SLA Threshold", data.queryNumbers(), threshold);
        thresholdSeries.setLineColor(THRESHOLD_COLOR);
        thresholdSeries.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{10.0f, 5.0f}, 0.0f));
        thresholdSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        // DQN adaptive threshold (0.4)
        List<Double> dqnThreshold = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) dqnThreshold.add(0.4);
        XYSeries dqnSeries = chart.addSeries("DQN Threshold (40%)", data.queryNumbers(), dqnThreshold);
        dqnSeries.setLineColor(new Color(52, 168, 83, 150));
        dqnSeries.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{5.0f, 3.0f}, 0.0f));
        dqnSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        // Q-Learning adaptive threshold (0.5)
        List<Double> qlThreshold = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) qlThreshold.add(0.5);
        XYSeries qlSeries = chart.addSeries("Q-Learn Threshold (50%)", data.queryNumbers(), qlThreshold);
        qlSeries.setLineColor(new Color(251, 188, 4, 150));
        qlSeries.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{5.0f, 3.0f}, 0.0f));
        qlSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax(5.5);
        
        return chart;
    }
    
    // === Chart 2: Replica Count with Trigger Point ===
    private static XYChart createReplicaWithTriggerChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Replica Creation Timeline", "Query", "Replicas");
        
        // Find first replication query
        int triggerQuery = -1;
        for (int i = 0; i < data.replicaCounts().size(); i++) {
            if (data.replicaCounts().get(i) > 0) {
                triggerQuery = data.queryNumbers().get(i);
                break;
            }
        }
        
        // Replica count
        List<Double> replicas = new ArrayList<>();
        for (int r : data.replicaCounts()) replicas.add((double) r);
        addSeries(chart, modelName + " Replicas", data.queryNumbers(), replicas, color, 2.5f);
        
        // Add trigger point annotation
        if (triggerQuery >= 0) {
            List<Integer> triggerX = List.of(triggerQuery, triggerQuery);
            List<Double> triggerY = List.of(0.0, (double) TcdrmConstants.MAX_REPLICAS_SIMPLE);
            XYSeries trigger = chart.addSeries("Trigger @ q" + triggerQuery, triggerX, triggerY);
            trigger.setLineColor(THRESHOLD_COLOR);
            trigger.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
                10.0f, new float[]{8.0f, 4.0f}, 0.0f));
            trigger.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        }
        
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax((double) TcdrmConstants.MAX_REPLICAS_SIMPLE + 1);
        
        return chart;
    }
    
    // === Chart 3: Response Time with Replica Impact Zones ===
    private static XYChart createResponseTimeZonesChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Response Time & Replica Impact", "Query", "Time (ms)");
        
        // Response time
        addSeries(chart, modelName, data.queryNumbers(), data.timePerQueryMs(), color, 1.5f);
        
        // T_SLA threshold
        List<Double> slaLine = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) slaLine.add(T_SLA);
        XYSeries slaSeries = chart.addSeries("T_SLA = " + (int) T_SLA + "ms", data.queryNumbers(), slaLine);
        slaSeries.setLineColor(THRESHOLD_COLOR);
        slaSeries.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{10.0f, 5.0f}, 0.0f));
        slaSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        // Smoothed response time to show trend
        List<Double> smoothed = smoothData(data.timePerQueryMs(), 30);
        XYSeries smoothSeries = chart.addSeries(modelName + " (trend)", data.queryNumbers(), smoothed);
        smoothSeries.setLineColor(color.darker());
        smoothSeries.setLineStyle(new BasicStroke(3.0f));
        smoothSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        return chart;
    }
    
    // === Chart 4: Popularity vs Response Time ===
    private static XYChart createPopularityVsResponseChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Popularity Impact on Response Time", "Normalized Popularity (P/P_SLA)", "Avg Response Time (ms)");
        
        // Group queries by popularity buckets and calculate average response time
        int numBuckets = 20;
        double maxPopularity = 5.0; // 1000 queries / 200 P_SLA = 5
        List<Double> bucketPopularity = new ArrayList<>();
        List<Double> bucketAvgTime = new ArrayList<>();
        
        for (int b = 0; b < numBuckets; b++) {
            double popStart = b * maxPopularity / numBuckets;
            double popEnd = (b + 1) * maxPopularity / numBuckets;
            double popMid = (popStart + popEnd) / 2;
            
            double sumTime = 0;
            int count = 0;
            for (int i = 0; i < data.queryNumbers().size(); i++) {
                double pop = (double)(data.queryNumbers().get(i) + 1) / P_SLA;
                if (pop >= popStart && pop < popEnd) {
                    sumTime += data.timePerQueryMs().get(i);
                    count++;
                }
            }
            
            if (count > 0) {
                bucketPopularity.add(popMid);
                bucketAvgTime.add(sumTime / count);
            }
        }
        
        if (!bucketPopularity.isEmpty()) {
            XYSeries series = chart.addSeries(modelName, bucketPopularity, bucketAvgTime);
            series.setLineColor(color);
            series.setLineStyle(new BasicStroke(2.5f));
            series.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.CIRCLE);
            series.setMarkerColor(color);
        }
        
        // P_SLA = 1.0 vertical line
        List<Double> pSlaX = List.of(1.0, 1.0);
        List<Double> pSlaY = List.of(0.0, 250.0);
        XYSeries pSlaSeries = chart.addSeries("P_SLA = 1.0", pSlaX, pSlaY);
        pSlaSeries.setLineColor(THRESHOLD_COLOR);
        pSlaSeries.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{8.0f, 4.0f}, 0.0f));
        pSlaSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        // T_SLA horizontal line
        List<Double> tSlaX = List.of(0.0, maxPopularity);
        List<Double> tSlaY = List.of(T_SLA, T_SLA);
        XYSeries tSlaSeries = chart.addSeries("T_SLA = " + (int) T_SLA + "ms", tSlaX, tSlaY);
        tSlaSeries.setLineColor(new Color(100, 100, 100, 150));
        tSlaSeries.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 
            10.0f, new float[]{5.0f, 3.0f}, 0.0f));
        tSlaSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax(maxPopularity);
        chart.getStyler().setYAxisMin(0.0);
        
        return chart;
    }
    
    // === Utility Methods ===
    
    private static List<Double> smoothData(List<Double> data, int windowSize) {
        List<Double> smoothed = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(data.size(), i + windowSize / 2 + 1);
            double sum = 0.0;
            for (int j = start; j < end; j++) sum += data.get(j);
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
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax((double) TcdrmConstants.MAX_QUERIES);
        chart.getStyler().setXAxisDecimalPattern("#");
        chart.getStyler().setYAxisDecimalPattern("#.##");
        chart.getStyler().setPlotContentSize(0.92);
        chart.getStyler().setChartPadding(10);
        chart.getStyler().setXAxisTickMarkSpacingHint(150);
        
        return chart;
    }
    
    private static void drawChart(Graphics2D g, XYChart chart, int x, int y) {
        g.drawImage(BitmapEncoder.getBufferedImage(chart), x, y, null);
    }
    
    private static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float strokeWidth) {
        List<Double> xd = new ArrayList<>();
        for (int v : x) xd.add((double) v);
        XYSeries series = chart.addSeries(name, xd, y);
        series.setLineColor(color);
        series.setLineStyle(new BasicStroke(strokeWidth));
        series.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
    }
}
