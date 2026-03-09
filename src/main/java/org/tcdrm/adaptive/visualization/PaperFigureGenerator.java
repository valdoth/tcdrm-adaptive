package org.tcdrm.adaptive.visualization;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.BenchmarkDataPerQuery;

import java.io.IOException;
import java.util.List;

import static org.tcdrm.adaptive.visualization.ChartColors.*;
import static org.tcdrm.adaptive.visualization.ChartUtils.*;

/**
 * Generates the 6 paper figures (Phase 1: TCDRM vs NoRepLc).
 */
public final class PaperFigureGenerator {
    
    private PaperFigureGenerator() {}
    
    /** Fig 2: Replica Factor zoomed around P_SLA */
    public static void generateReplicaFactor(BenchmarkDataPerQuery simple, 
                                              BenchmarkDataPerQuery complex) throws IOException {
        XYChart chart = new XYChartBuilder()
            .width(600).height(400)
            .title("Replica Factor")
            .xAxisTitle("Number of queries")
            .yAxisTitle("Number of replica")
            .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setXAxisMin(190.0);
        chart.getStyler().setXAxisMax(220.0);
        
        addSeriesInt(chart, "Complex queries", complex.queryNumbers(), complex.replicaCount(), TCDRM, 2.5f);
        addSeriesInt(chart, "Simple queries", simple.queryNumbers(), simple.replicaCount(), NOREP, 2.5f);
        
        BitmapEncoder.saveBitmap(chart, "images/paper_fig2_replica_factor.png", BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [Fig 2] Replica Factor");
    }
    
    /** Fig 3: Response Time side-by-side */
    public static void generateResponseTime(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                             BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC) throws IOException {
        XYChart left = createLineChart("Impact on Response Times (Simple queries)", "Number of queries", "Response time (ms)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), movingAverage(tcdrmS.timePerQueryMs(), 30), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), movingAverage(norepS.timePerQueryMs(), 30), NOREP, 2.0f);
        
        XYChart right = createLineChart("Impact on response times (Complex queries)", "Number of queries", "Response time (ms)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), movingAverage(tcdrmC.timePerQueryMs(), 30), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), movingAverage(norepC.timePerQueryMs(), 30), NOREP, 2.0f);
        
        saveSideBySide(left, right, "images/paper_fig3_response_time.png");
        System.out.println("  [Fig 3] Response Time (Simple + Complex)");
    }
    
    /** Fig 4: BW Consumption bar chart */
    public static void generateBwConsumption(BenchmarkDataPerQuery norepS, BenchmarkDataPerQuery tcdrmS,
                                              BenchmarkDataPerQuery norepC, BenchmarkDataPerQuery tcdrmC) throws IOException {
        CategoryChart left = createBarChart("Bandwidth consumption (Simple queries)", "SIMPLE QUERIES", "BW (GByte)");
        left.addSeries("interProvider", List.of("NoRepLc", "TCDRM"),
            List.of(norepS.totalBwInterProviderGb(), tcdrmS.totalBwInterProviderGb()));
        left.addSeries("interRegion", List.of("NoRepLc", "TCDRM"),
            List.of(norepS.totalBwInterRegionGb(), tcdrmS.totalBwInterRegionGb()));
        setBarColor(left, "interProvider", INTER_PROVIDER);
        setBarColor(left, "interRegion", INTER_REGION);
        
        CategoryChart right = createBarChart("Bandwidth consumption (Complex queries)", "COMPLEX QUERIES", "BW (GByte)");
        right.addSeries("interProvider", List.of("NoRepLc", "TCDRM"),
            List.of(norepC.totalBwInterProviderGb(), tcdrmC.totalBwInterProviderGb()));
        right.addSeries("interRegion", List.of("NoRepLc", "TCDRM"),
            List.of(norepC.totalBwInterRegionGb(), tcdrmC.totalBwInterRegionGb()));
        setBarColor(right, "interProvider", INTER_PROVIDER);
        setBarColor(right, "interRegion", INTER_REGION);
        
        saveSideBySide(left, right, "images/paper_fig4_bw_consumption.png");
        System.out.println("  [Fig 4] BW Consumption bar chart (Simple + Complex)");
    }
    
    /** Fig 5: Avg BW Price per query */
    public static void generateAvgBwPrice(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                           BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC) throws IOException {
        XYChart left = createLineChart("Avg. BW PRICE (Simple Queries)", "Number of Queries", "Price ($)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), computeRunningAvgCost(tcdrmS.costPerQuery()), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), computeRunningAvgCost(norepS.costPerQuery()), NOREP, 2.0f);
        
        XYChart right = createLineChart("Avg. BW PRICE (Complex Queries)", "Number of Queries", "Price ($)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), computeRunningAvgCost(tcdrmC.costPerQuery()), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), computeRunningAvgCost(norepC.costPerQuery()), NOREP, 2.0f);
        
        saveSideBySide(left, right, "images/paper_fig5_avg_bw_price.png");
        System.out.println("  [Fig 5] Avg BW Price (Simple + Complex)");
    }
    
    /** Fig 6: Cumulative BW Price */
    public static void generateCumulativeBwPrice(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                                  BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC) throws IOException {
        XYChart left = createLineChart("Cumulation. BANDWIDTH PRICE (Simple Queries)", "Number of Queries", "Price ($)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), tcdrmS.cumulativeCost(), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), norepS.cumulativeCost(), NOREP, 2.0f);
        
        XYChart right = createLineChart("Cumulation. BANDWIDTH PRICE (Complex Queries)", "Number of Queries", "Price ($)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), tcdrmC.cumulativeCost(), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), norepC.cumulativeCost(), NOREP, 2.0f);
        
        saveSideBySide(left, right, "images/paper_fig6_cumulative_bw_price.png");
        System.out.println("  [Fig 6] Cumulative BW Price (Simple + Complex)");
    }
    
    /** Fig 7: Total Cost stacked bar */
    public static void generateTotalCost(BenchmarkDataPerQuery norepS, BenchmarkDataPerQuery tcdrmS,
                                          BenchmarkDataPerQuery norepC, BenchmarkDataPerQuery tcdrmC) throws IOException {
        List<String> cats = List.of("NoRepLc", "TCDRM");
        
        CategoryChart left = createStackedBarChart("Total Cost Breakdown (Simple queries)", "Cost ($)");
        left.addSeries("CPU", cats, List.of(norepS.totalCpuCost(), tcdrmS.totalCpuCost()));
        left.addSeries("Bandwidth", cats, List.of(norepS.totalBwCost(), tcdrmS.totalBwCost()));
        left.addSeries("Replica (creation + storage)", cats, List.of(norepS.totalReplicaCost(), tcdrmS.totalReplicaCost()));
        setBarColor(left, "CPU", CPU);
        setBarColor(left, "Bandwidth", BANDWIDTH);
        setBarColor(left, "Replica (creation + storage)", REPLICA);

        CategoryChart right = createStackedBarChart("Total Cost Breakdown (Complex queries)", "Cost ($)");
        right.addSeries("CPU", cats, List.of(norepC.totalCpuCost(), tcdrmC.totalCpuCost()));
        right.addSeries("Bandwidth", cats, List.of(norepC.totalBwCost(), tcdrmC.totalBwCost()));
        right.addSeries("Replica (creation + storage)", cats, List.of(norepC.totalReplicaCost(), tcdrmC.totalReplicaCost()));
        setBarColor(right, "CPU", CPU);
        setBarColor(right, "Bandwidth", BANDWIDTH);
        setBarColor(right, "Replica (creation + storage)", REPLICA);

        saveSideBySide(left, right, "images/paper_fig7_total_cost.png");
        System.out.println("  [Fig 7] Total Cost stacked bar (Simple + Complex)");
    }
}
