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
 * Generates a 2x3 metrics dashboard for a single model.
 * Row 1: Response Time (with SLA), Cumulative Cost, SLA Violations
 * Row 2: Replica Count, Response Time (smoothed), SLA Compliance Rate
 */
public class SingleModelMetricsPlotter {
    
    private static final int CHART_WIDTH = 550;
    private static final int CHART_HEIGHT = 380;
    private static final double SLA_THRESHOLD = TcdrmConstants.TSLA_SIMPLE_MS;
    private static final int SMOOTH_WINDOW = 50;
    private static final Color SLA_LINE_COLOR = new Color(220, 50, 50, 180);
    
    public static void generateMetricsPlot(
            BenchmarkDataPerQuery data,
            String modelName,
            Color modelColor,
            String outputPath) throws IOException {
        
        BufferedImage combined = new BufferedImage(CHART_WIDTH * 3, CHART_HEIGHT * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, combined.getWidth(), combined.getHeight());
        
        // Row 1
        drawChart(g, createResponseTimeChart(data, modelName, modelColor), 0, 0);
        drawChart(g, createCumulativeCostChart(data, modelName, modelColor), CHART_WIDTH, 0);
        drawChart(g, createSLAViolationsChart(data, modelName, modelColor), CHART_WIDTH * 2, 0);
        
        // Row 2
        drawChart(g, createReplicaChart(data, modelName, modelColor), 0, CHART_HEIGHT);
        drawChart(g, createSmoothedResponseTimeChart(data, modelName, modelColor), CHART_WIDTH, CHART_HEIGHT);
        drawChart(g, createSLAComplianceChart(data, modelName, modelColor), CHART_WIDTH * 2, CHART_HEIGHT);
        
        g.dispose();
        ImageIO.write(combined, "png", new File(outputPath));
        System.out.println("  ✓ Metrics dashboard " + modelName + ": " + outputPath);
    }
    
    // === Chart 1: Response Time with SLA threshold ===
    private static XYChart createResponseTimeChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Response Time (ms)", "Query", "Time (ms)");
        addSeries(chart, modelName, data.queryNumbers(), data.timePerQueryMs(), color, 1.0f);
        
        // SLA threshold line
        List<Double> slaLine = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) slaLine.add(SLA_THRESHOLD);
        XYSeries slaSeries = chart.addSeries("SLA = " + (int) SLA_THRESHOLD + "ms", data.queryNumbers(), slaLine);
        slaSeries.setLineColor(SLA_LINE_COLOR);
        slaSeries.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f, 5.0f}, 0.0f));
        slaSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        return chart;
    }
    
    // === Chart 2: Cumulative Cost ===
    private static XYChart createCumulativeCostChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Cumulative BW Cost ($)", "Query", "Cost ($)");
        addSeries(chart, modelName, data.queryNumbers(), data.cumulativeCost(), color, 2.0f);
        return chart;
    }
    
    // === Chart 3: Cumulative SLA Violations ===
    private static XYChart createSLAViolationsChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Cumulative SLA Violations", "Query", "Violations");
        addSeries(chart, modelName, data.queryNumbers(), data.cumulativeSLAViolations(), color, 2.0f);
        return chart;
    }
    
    // === Chart 4: Replica Count ===
    private static XYChart createReplicaChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Replica Count", "Query", "Replicas");
        addSeriesInt(chart, modelName, data.queryNumbers(), data.replicaCounts(), color, 2.0f);
        chart.getStyler().setYAxisMin(0.0);
        return chart;
    }
    
    // === Chart 5: Smoothed Response Time ===
    private static XYChart createSmoothedResponseTimeChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("Response Time — smoothed (w=" + SMOOTH_WINDOW + ")", "Query", "Time (ms)");
        List<Double> smoothed = smoothData(data.timePerQueryMs(), SMOOTH_WINDOW);
        addSeries(chart, modelName, data.queryNumbers(), smoothed, color, 2.0f);
        
        // SLA threshold line
        List<Double> slaLine = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) slaLine.add(SLA_THRESHOLD);
        XYSeries slaSeries = chart.addSeries("SLA = " + (int) SLA_THRESHOLD + "ms", data.queryNumbers(), slaLine);
        slaSeries.setLineColor(SLA_LINE_COLOR);
        slaSeries.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f, 5.0f}, 0.0f));
        slaSeries.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        return chart;
    }
    
    // === Chart 6: SLA Compliance Rate (rolling %) ===
    private static XYChart createSLAComplianceChart(BenchmarkDataPerQuery data, String modelName, Color color) {
        XYChart chart = createBaseChart("SLA Compliance Rate (%)", "Query", "Compliance (%)");
        
        List<Double> compliance = new ArrayList<>();
        double violations = 0;
        for (int i = 0; i < data.timePerQueryMs().size(); i++) {
            if (data.timePerQueryMs().get(i) > SLA_THRESHOLD) violations++;
            compliance.add(((i + 1 - violations) / (i + 1)) * 100.0);
        }
        addSeries(chart, modelName, data.queryNumbers(), compliance, color, 2.0f);
        
        // 95% target line
        List<Double> targetLine = new ArrayList<>();
        for (int i = 0; i < data.queryNumbers().size(); i++) targetLine.add(95.0);
        XYSeries target = chart.addSeries("Target 95%", data.queryNumbers(), targetLine);
        target.setLineColor(SLA_LINE_COLOR);
        target.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{8.0f, 4.0f}, 0.0f));
        target.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
        
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax(100.0);
        return chart;
    }
    
    // === Utility methods ===
    
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
        
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax((double) TcdrmConstants.MAX_QUERIES);
        chart.getStyler().setXAxisDecimalPattern("#");
        chart.getStyler().setYAxisDecimalPattern("#.##");
        chart.getStyler().setPlotContentSize(0.9);
        chart.getStyler().setChartPadding(8);
        chart.getStyler().setXAxisTickMarkSpacingHint(120);
        
        return chart;
    }
    
    private static void drawChart(Graphics2D g, XYChart chart, int x, int y) {
        g.drawImage(BitmapEncoder.getBufferedImage(chart), x, y, null);
    }
    
    private static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float strokeWidth) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineStyle(new BasicStroke(strokeWidth));
        series.setMarker(org.knowm.xchart.style.markers.SeriesMarkers.NONE);
    }
    
    private static void addSeriesInt(XYChart chart, String name, List<Integer> x, List<Integer> y, Color color, float strokeWidth) {
        List<Double> yDouble = new ArrayList<>();
        for (Integer val : y) yDouble.add(val.doubleValue());
        addSeries(chart, name, x, yDouble, color, strokeWidth);
    }
}
