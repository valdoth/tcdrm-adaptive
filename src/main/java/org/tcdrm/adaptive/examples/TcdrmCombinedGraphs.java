package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.tcdrm.adaptive.benchmark.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generate combined graphs (raw + smoothed) for TCDRM article
 */
public class TcdrmCombinedGraphs {

    public static void main(String[] args) throws IOException {
        System.out.println("Generating combined graphs...");
        
        // Query R1
        generateCombinedGraphsForQuery("R1", Arrays.asList(2.0, 1.5, 1.0), 3);
        
        // Query R2
        generateCombinedGraphsForQuery("R2", Arrays.asList(3.0, 2.5, 2.0, 1.5), 3);
        
        System.out.println("All combined graphs generated successfully.");
    }

    private static void generateCombinedGraphsForQuery(String queryId, List<Double> fragmentSizes, int replicationFactor) throws IOException {
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, 42L);
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(42L);

        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);

        // Generate combined graphs
        generateCombinedResponseTime(queryId, tcdrmData, norepData);
        generateCombinedCost(queryId, tcdrmData, norepData);
    }

    /**
     * Combined Response Time: Raw (left) + Smoothed (right)
     */
    private static void generateCombinedResponseTime(String queryId, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) throws IOException {
        // Raw chart
        XYChart rawChart = createResponseTimeChart("Impact of Replication on Response Time", tcdrm, norep, false);
        
        // Smoothed chart
        XYChart smoothedChart = createResponseTimeChart("Impact of Replication on Response Time", tcdrm, norep, true);
        
        // Combine and save
        BufferedImage combined = combineTwoCharts(rawChart, smoothedChart);
        String filename = "images/combined_response_time_" + queryId + ".png";
        ImageIO.write(combined, "PNG", new File(filename));
        System.out.println("✓ " + filename);
    }

    /**
     * Combined Cost: Raw (left) + Cumulative (right)
     */
    private static void generateCombinedCost(String queryId, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) throws IOException {
        // Cost per query chart
        XYChart costChart = createCostPerQueryChart("Impact of Replication on BW PRICE", tcdrm, norep);
        
        // Cumulative cost chart
        XYChart cumulativeChart = createCumulativeCostChart("PRIX CUMULATIF BW", tcdrm, norep);
        
        // Combine and save
        BufferedImage combined = combineTwoCharts(costChart, cumulativeChart);
        String filename = "images/combined_cost_" + queryId + ".png";
        ImageIO.write(combined, "PNG", new File(filename));
        System.out.println("✓ " + filename);
    }

    private static XYChart createResponseTimeChart(String title, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep, boolean smoothed) {
        XYChart chart = new XYChartBuilder()
            .width(500)
            .height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Response Time (seconds)")
            .build();

        styleChart(chart);

        List<Double> tcdrmData = smoothed ? smooth(tcdrm.timePerQueryMs(), 20) : tcdrm.timePerQueryMs();
        List<Double> norepData = smoothed ? smooth(norep.timePerQueryMs(), 20) : norep.timePerQueryMs();

        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrmData);
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(smoothed ? 3.0f : 1.5f);
        tcdrmSeries.setMarker(SeriesMarkers.NONE);

        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norepData);
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(smoothed ? 3.0f : 1.5f);
        norepSeries.setMarker(SeriesMarkers.NONE);

        return chart;
    }

    private static XYChart createCostPerQueryChart(String title, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) {
        XYChart chart = new XYChartBuilder()
            .width(500)
            .height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Cost per Query ($)")
            .build();

        styleChart(chart);

        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrm.costPerQuery());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(1.5f);
        tcdrmSeries.setMarker(SeriesMarkers.NONE);

        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norep.costPerQuery());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(1.5f);
        norepSeries.setMarker(SeriesMarkers.NONE);

        return chart;
    }

    private static XYChart createCumulativeCostChart(String title, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) {
        XYChart chart = new XYChartBuilder()
            .width(500)
            .height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Cumulative Cost ($)")
            .build();

        styleChart(chart);

        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrm.cumulativeCost());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        tcdrmSeries.setMarker(SeriesMarkers.NONE);

        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norep.cumulativeCost());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.5f);
        norepSeries.setMarker(SeriesMarkers.NONE);

        return chart;
    }

    private static void styleChart(XYChart chart) {
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setLegendBackgroundColor(Color.WHITE);
        chart.getStyler().setAxisTickLabelsFont(new Font("Arial", Font.PLAIN, 11));
        chart.getStyler().setAxisTitleFont(new Font("Arial", Font.BOLD, 12));
        chart.getStyler().setLegendFont(new Font("Arial", Font.PLAIN, 11));
    }

    private static BufferedImage combineTwoCharts(XYChart leftChart, XYChart rightChart) {
        BufferedImage leftImage = BitmapEncoder.getBufferedImage(leftChart);
        BufferedImage rightImage = BitmapEncoder.getBufferedImage(rightChart);
        
        int width = leftImage.getWidth() + rightImage.getWidth();
        int height = Math.max(leftImage.getHeight(), rightImage.getHeight());
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(leftImage, 0, 0, null);
        g.drawImage(rightImage, leftImage.getWidth(), 0, null);
        g.dispose();
        
        return combined;
    }

    private static List<Double> smooth(List<Double> data, int windowSize) {
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
}
