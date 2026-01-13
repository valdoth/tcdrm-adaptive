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
import java.util.Arrays;
import java.util.List;

/**
 * Generate combined metrics graphs (CPU, Bandwidth, Storage)
 */
public class TcdrmMetricsGraphs {

    public static void main(String[] args) throws IOException {
        System.out.println("Generating metrics graphs...");
        
        generateMetricsForQuery("R1", Arrays.asList(2.0, 1.5, 1.0), 3);
        generateMetricsForQuery("R2", Arrays.asList(3.0, 2.5, 2.0, 1.5), 3);
        
        System.out.println("All metrics graphs generated.");
    }

    private static void generateMetricsForQuery(String queryId, List<Double> fragmentSizes, int replicationFactor) throws IOException {
        TcdrmBenchmark tcdrmBench = new TcdrmBenchmark(replicationFactor, 42L);
        NoRepBenchmark norepBench = new NoRepBenchmark(84L);

        BenchmarkData tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        BenchmarkData norepData = norepBench.computeBenchmark(queryId, fragmentSizes);

        generateCombinedCPUBandwidth(queryId, tcdrmData, norepData);
        generateCombinedStorageTotal(queryId, tcdrmData, norepData);
    }

    /**
     * Combined: CPU Cost (left) + Bandwidth Cost (right)
     */
    private static void generateCombinedCPUBandwidth(String queryId, BenchmarkData tcdrm, BenchmarkData norep) throws IOException {
        XYChart cpuChart = createMetricChart("Impact of Replication on CPU consumption", 
                                             "Number of Repetitions", "CPU Cost ($)", 
                                             tcdrm, norep, tcdrm.cpuCost(), norep.cpuCost());
        
        XYChart bwChart = createMetricChart("Impact of Replication on BW PRICE", 
                                            "Number of Repetitions", "Bandwidth Cost ($)", 
                                            tcdrm, norep, tcdrm.bandwidthCost(), norep.bandwidthCost());
        
        BufferedImage combined = combineTwoCharts(cpuChart, bwChart);
        String filename = "images/combined_cpu_bandwidth_" + queryId + ".png";
        ImageIO.write(combined, "PNG", new File(filename));
        System.out.println("✓ " + filename);
    }

    /**
     * Combined: Storage Cost (left) + Total Cost (right)
     */
    private static void generateCombinedStorageTotal(String queryId, BenchmarkData tcdrm, BenchmarkData norep) throws IOException {
        XYChart storageChart = createMetricChart("Storage Cost Evolution", 
                                                 "Number of Repetitions", "Storage Cost ($)", 
                                                 tcdrm, norep, tcdrm.storageCost(), norep.storageCost());
        
        XYChart totalChart = createMetricChart("Total Cost Comparison", 
                                               "Number of Repetitions", "Total Cost ($)", 
                                               tcdrm, norep, tcdrm.getTotalCosts(), norep.getTotalCosts());
        
        BufferedImage combined = combineTwoCharts(storageChart, totalChart);
        String filename = "images/combined_storage_total_" + queryId + ".png";
        ImageIO.write(combined, "PNG", new File(filename));
        System.out.println("✓ " + filename);
    }

    private static XYChart createMetricChart(String title, String xLabel, String yLabel,
                                             BenchmarkData tcdrm, BenchmarkData norep,
                                             List<Double> tcdrmData, List<Double> norepData) {
        XYChart chart = new XYChartBuilder()
            .width(500)
            .height(400)
            .title(title)
            .xAxisTitle(xLabel)
            .yAxisTitle(yLabel)
            .build();

        styleChart(chart);

        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.repetitions(), tcdrmData);
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.5f);
        tcdrmSeries.setMarker(SeriesMarkers.NONE);

        XYSeries norepSeries = chart.addSeries("NOREP", norep.repetitions(), norepData);
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
}
