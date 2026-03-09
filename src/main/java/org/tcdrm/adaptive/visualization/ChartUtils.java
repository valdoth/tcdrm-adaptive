package org.tcdrm.adaptive.visualization;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.core.TcdrmConstants;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for creating and combining charts.
 */
public final class ChartUtils {
    
    private ChartUtils() {}
    
    public static XYChart createLineChart(String title, String xLabel, String yLabel) {
        XYChart chart = new XYChartBuilder()
            .width(600).height(400)
            .title(title).xAxisTitle(xLabel).yAxisTitle(yLabel)
            .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisMin(0.0);
        chart.getStyler().setXAxisMax((double) TcdrmConstants.MAX_QUERIES);
        return chart;
    }
    
    public static CategoryChart createBarChart(String title, String xLabel, String yLabel) {
        CategoryChart chart = new CategoryChartBuilder()
            .width(500).height(400)
            .title(title).xAxisTitle(xLabel).yAxisTitle(yLabel)
            .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setAvailableSpaceFill(0.5);
        return chart;
    }
    
    public static CategoryChart createStackedBarChart(String title, String yLabel) {
        CategoryChart chart = new CategoryChartBuilder()
            .width(500).height(450)
            .title(title).yAxisTitle(yLabel)
            .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setStacked(true);
        chart.getStyler().setAvailableSpaceFill(0.5);
        chart.getStyler().setPlotContentSize(0.85);
        chart.getStyler().setChartPadding(15);
        return chart;
    }
    
    public static void addSeries(XYChart chart, String name, List<Integer> x, List<Double> y, Color color, float width) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineWidth(width);
    }
    
    public static void addSeriesInt(XYChart chart, String name, List<Integer> x, List<Integer> y, Color color, float width) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setLineWidth(width);
    }
    
    public static void setBarColor(CategoryChart chart, String seriesName, Color color) {
        chart.getSeriesMap().get(seriesName).setFillColor(color);
    }
    
    public static void saveSideBySide(XYChart left, XYChart right, String filename) throws IOException {
        BufferedImage leftImg = BitmapEncoder.getBufferedImage(left);
        BufferedImage rightImg = BitmapEncoder.getBufferedImage(right);
        combineAndSave(leftImg, rightImg, filename);
    }
    
    public static void saveSideBySide(CategoryChart left, CategoryChart right, String filename) throws IOException {
        BufferedImage leftImg = BitmapEncoder.getBufferedImage(left);
        BufferedImage rightImg = BitmapEncoder.getBufferedImage(right);
        combineAndSave(leftImg, rightImg, filename);
    }
    
    private static void combineAndSave(BufferedImage leftImg, BufferedImage rightImg, String filename) throws IOException {
        int w = leftImg.getWidth() + rightImg.getWidth();
        int h = Math.max(leftImg.getHeight(), rightImg.getHeight());
        BufferedImage combined = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(leftImg, 0, 0, null);
        g.drawImage(rightImg, leftImg.getWidth(), 0, null);
        g.dispose();
        ImageIO.write(combined, "png", new File(filename));
    }
    
    public static List<Double> movingAverage(List<Double> data, int windowSize) {
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
    
    public static List<Double> computeRunningAvgCost(List<Double> costPerQuery) {
        List<Double> running = new ArrayList<>();
        double sum = 0.0;
        for (int i = 0; i < costPerQuery.size(); i++) {
            sum += costPerQuery.get(i);
            running.add(sum / (i + 1));
        }
        return running;
    }
}
