package org.tcdrm.adaptive.examples;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.*;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Generate graphs matching the TCDRM article figures
 * Shows per-query metrics with the characteristic drop after PSLA threshold
 */
public class TcdrmArticleGraphs {

    public static void main(String[] args) throws IOException {
        System.out.println("================ TCDRM Article Graphs Generation ================\n");
        
        // Simple query (R1)
        generateGraphsForQuery("R1", Arrays.asList(2.0, 1.5, 1.0), 3);
        
        // Complex query (R2)
        generateGraphsForQuery("R2", Arrays.asList(3.0, 2.5, 2.0, 1.5), 3);
        
        System.out.println("\n================ Graphs Generation Complete ================");
    }

    private static void generateGraphsForQuery(String queryId, List<Double> fragmentSizes, int replicationFactor) throws IOException {
        System.out.println("Generating graphs for query: " + queryId);
        System.out.println("Fragment sizes: " + fragmentSizes);
        System.out.println("Replication factor: " + replicationFactor);

        // Compute benchmarks
        TcdrmBenchmarkPerQuery tcdrmBench = new TcdrmBenchmarkPerQuery(replicationFactor, 42L);
        NoRepBenchmarkPerQuery norepBench = new NoRepBenchmarkPerQuery(42L);

        BenchmarkDataPerQuery tcdrmData = tcdrmBench.computeBenchmark(queryId, fragmentSizes);
        BenchmarkDataPerQuery norepData = norepBench.computeBenchmark(queryId, fragmentSizes);

        // 1. Response Time per Query
        generateResponseTimeChart(queryId, tcdrmData, norepData);
        
        // 2. Cost per Query (showing oscillations)
        generateCostPerQueryChart(queryId, tcdrmData, norepData);
        
        // 3. Cumulative Cost
        generateCumulativeCostChart(queryId, tcdrmData, norepData);

        System.out.println();
    }

    private static void generateResponseTimeChart(String queryId, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Impact of Replication on Response Time - " + queryId)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Response Time (seconds)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);

        // TCDRM series
        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrm.timePerQueryMs());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.0f);

        // NOREP series
        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norep.timePerQueryMs());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.0f);

        String filename = "images/tcdrm_article_response_time_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Response time chart: " + filename);
    }

    private static void generateCostPerQueryChart(String queryId, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Impact of Replication on Cost per Query - " + queryId)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Cost per Query ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);

        // TCDRM series
        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrm.costPerQuery());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.0f);

        // NOREP series
        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norep.costPerQuery());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.0f);

        String filename = "images/tcdrm_article_cost_per_query_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Cost per query chart: " + filename);
    }

    private static void generateCumulativeCostChart(String queryId, BenchmarkDataPerQuery tcdrm, BenchmarkDataPerQuery norep) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Cumulative Cost - " + queryId)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Cumulative Cost ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);

        // TCDRM series
        XYSeries tcdrmSeries = chart.addSeries("TCDRM", tcdrm.queryNumbers(), tcdrm.cumulativeCost());
        tcdrmSeries.setLineColor(new Color(31, 119, 180));
        tcdrmSeries.setLineWidth(2.0f);

        // NOREP series
        XYSeries norepSeries = chart.addSeries("NOREP", norep.queryNumbers(), norep.cumulativeCost());
        norepSeries.setLineColor(new Color(255, 127, 14));
        norepSeries.setLineWidth(2.0f);

        String filename = "images/tcdrm_article_cumulative_cost_" + queryId + ".png";
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("✓ Cumulative cost chart: " + filename);
    }
}
