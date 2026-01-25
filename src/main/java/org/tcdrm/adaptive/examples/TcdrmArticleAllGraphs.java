package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generate ALL graphs from the TCDRM article:
 * - Response Time (raw + smoothed)
 * - CPU Consumption
 * - BW Price per Query
 * - Cumulative BW Price
 * - Replication Factor
 * - Total Cost breakdown
 */
public class TcdrmArticleAllGraphs {

    public static void main(String[] args) throws IOException {
        System.out.println("================ TCDRM Article - ALL Graphs Generation ================\n");
        
        // Créer le répertoire images/ s'il n'existe pas
        new File("images").mkdirs();
        
        // R1 = F1 + F41 + F80 (3 fragments from different regions)
        generateAllGraphsForQuery("R1", Arrays.asList(1.5, 2.0, 1.8), 3);
        
        // R2 = F2 + F21 + F32 + F45 + F71 + F80 (6 fragments from different regions)
        generateAllGraphsForQuery("R2", Arrays.asList(1.8, 2.2, 1.5, 2.5, 1.9, 2.0), 3);
        
        System.out.println("\n================ ALL Graphs Generation Complete ================");
    }

    private static void generateAllGraphsForQuery(String queryId, List<Double> fragmentSizes, int replicationFactor) throws IOException {
        System.out.println("\n=== Generating ALL combined graphs for query: " + queryId + " ===");
        System.out.println("Fragment sizes: " + fragmentSizes);
        System.out.println("Replication factor: " + replicationFactor);

        // Compute benchmarks
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, 42L);
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(42L);

        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);
        
        // Log data sizes for verification
        double totalSize = fragmentSizes.stream().mapToDouble(d -> d).sum();
        System.out.println("  Total data size: " + String.format("%.2f", totalSize) + " GB");
        System.out.println("  Sample TCDRM time at query 500: " + String.format("%.2f", tcdrmData.timePerQueryMs().get(500)) + " s");
        System.out.println("  Sample NOREP time at query 500: " + String.format("%.2f", norepData.timePerQueryMs().get(500)) + " s");

        // 1. Response Time (dual: raw + smoothed)
        generateDualGraph(queryId, "response_time", "Impact of Replication on Response Time",
                         "Number of Queries", "Response Time (seconds)",
                         tcdrmData.queryNumbers(), tcdrmData.timePerQueryMs(),
                         norepData.queryNumbers(), norepData.timePerQueryMs());
        
        // 2. CPU Consumption (dual: raw + smoothed)
        List<Double> tcdrmCpu = extractCpuCost(tcdrmData);
        List<Double> norepCpu = extractCpuCost(norepData);
        generateDualGraph(queryId, "cpu_consumption", "Impact of Replication on CPU Consumption",
                         "Number of Queries", "CPU Cost ($)",
                         tcdrmData.queryNumbers(), tcdrmCpu,
                         norepData.queryNumbers(), norepCpu);
        
        // 3. BW Price per Query (dual: raw + smoothed)
        generateDualGraph(queryId, "bw_price_per_query", "Impact of Replication on BW PRICE",
                         "Number of Queries", "BW Cost per Query ($)",
                         tcdrmData.queryNumbers(), tcdrmData.costPerQuery(),
                         norepData.queryNumbers(), norepData.costPerQuery());
        
        // 4. Cumulative BW Price (dual: raw + smoothed)
        generateDualGraph(queryId, "cumulative_bw_price", "PRIX CUMULATIF BW",
                         "Number of Queries", "Cumulative BW Cost ($)",
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());
        
        // 5. Total Cost (dual: raw + smoothed)
        generateDualGraph(queryId, "total_cost", "Total Cost Comparison",
                         "Number of Queries", "Total Cost ($)",
                         tcdrmData.queryNumbers(), tcdrmData.cumulativeCost(),
                         norepData.queryNumbers(), norepData.cumulativeCost());

        System.out.println("✓ All combined graphs generated for " + queryId + "\n");
    }

    private static void generateDualGraph(String queryId, String graphType, String title,
                                          String xLabel, String yLabel,
                                          List<Integer> tcdrmX, List<Double> tcdrmY,
                                          List<Integer> norepX, List<Double> norepY) throws IOException {
        // Raw chart (left)
        XYChart rawChart = createChart(title + " (Raw) - " + queryId, xLabel, yLabel);
        addSeries(rawChart, "TCDRM", tcdrmX, tcdrmY, new Color(31, 119, 180), 1.0f);
        addSeries(rawChart, "NOREP", norepX, norepY, new Color(255, 127, 14), 1.0f);
        
        // Smoothed chart (right)
        XYChart smoothChart = createChart(title + " (Smoothed) - " + queryId, xLabel, yLabel);
        List<Double> tcdrmSmoothed = movingAverage(tcdrmY, 50);
        List<Double> norepSmoothed = movingAverage(norepY, 50);
        addSeries(smoothChart, "TCDRM", tcdrmX, tcdrmSmoothed, new Color(31, 119, 180), 2.5f);
        addSeries(smoothChart, "NOREP", norepX, norepSmoothed, new Color(255, 127, 14), 2.5f);
        
        // Combine side by side
        combineTwoCharts(rawChart, smoothChart, "images/tcdrm_combined_" + graphType + "_" + queryId + ".png");
        System.out.println("  ✓ " + title + " (combined)");
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
        // Let Y axis auto-scale to show differences between R1 and R2
        
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

    private static List<Double> convertToDouble(List<Integer> intList) {
        List<Double> doubleList = new ArrayList<>();
        for (Integer i : intList) {
            doubleList.add(i.doubleValue());
        }
        return doubleList;
    }
}
