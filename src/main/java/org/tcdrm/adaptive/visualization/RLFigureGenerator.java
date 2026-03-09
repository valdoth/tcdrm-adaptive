package org.tcdrm.adaptive.visualization;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.benchmark.BenchmarkDataPerQuery;

import java.io.IOException;
import java.util.List;

import static org.tcdrm.adaptive.visualization.ChartColors.*;
import static org.tcdrm.adaptive.visualization.ChartUtils.*;

/**
 * Generates RL extension figures (Phase 2: 4 models comparison).
 */
public final class RLFigureGenerator {
    
    private RLFigureGenerator() {}
    
    /** RL Fig 2: Replica Factor for all 4 models */
    public static void generateReplicaFactor(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                              BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                              BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC,
                                              BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        XYChart left = new XYChartBuilder().width(600).height(400)
            .title("Replica Factor — Simple (4 models)").xAxisTitle("Number of queries").yAxisTitle("Number of replica").build();
        left.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        left.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        left.getStyler().setMarkerSize(0);
        addSeriesInt(left, "TCDRM", tcdrmS.queryNumbers(), tcdrmS.replicaCount(), TCDRM, 2.0f);
        addSeriesInt(left, "NoRepLc", norepS.queryNumbers(), norepS.replicaCount(), NOREP, 2.0f);
        addSeriesInt(left, "Q-Learning", qlS.queryNumbers(), qlS.replicaCount(), QLEARNING, 2.0f);
        addSeriesInt(left, "DQN", dqnS.queryNumbers(), dqnS.replicaCount(), DQN, 2.0f);

        XYChart right = new XYChartBuilder().width(600).height(400)
            .title("Replica Factor — Complex (4 models)").xAxisTitle("Number of queries").yAxisTitle("Number of replica").build();
        right.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        right.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        right.getStyler().setMarkerSize(0);
        addSeriesInt(right, "TCDRM", tcdrmC.queryNumbers(), tcdrmC.replicaCount(), TCDRM, 2.0f);
        addSeriesInt(right, "NoRepLc", norepC.queryNumbers(), norepC.replicaCount(), NOREP, 2.0f);
        addSeriesInt(right, "Q-Learning", qlC.queryNumbers(), qlC.replicaCount(), QLEARNING, 2.0f);
        addSeriesInt(right, "DQN", dqnC.queryNumbers(), dqnC.replicaCount(), DQN, 2.0f);

        saveSideBySide(left, right, "images/rl_fig2_replica_factor.png");
        System.out.println("  [RL Fig 2] Replica Factor (4 models)");
    }
    
    /** RL Fig 3: Response Time (4 models) */
    public static void generateResponseTime(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                             BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                             BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC,
                                             BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        XYChart left = createLineChart("Impact on Response Times — Simple (4 models)", "Number of Queries", "Response time (ms)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), movingAverage(tcdrmS.timePerQueryMs(), 30), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), movingAverage(norepS.timePerQueryMs(), 30), NOREP, 2.0f);
        addSeries(left, "Q-Learning", qlS.queryNumbers(), movingAverage(qlS.timePerQueryMs(), 30), QLEARNING, 2.0f);
        addSeries(left, "DQN", dqnS.queryNumbers(), movingAverage(dqnS.timePerQueryMs(), 30), DQN, 2.0f);
        
        XYChart right = createLineChart("Impact on Response Times — Complex (4 models)", "Number of Queries", "Response time (ms)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), movingAverage(tcdrmC.timePerQueryMs(), 30), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), movingAverage(norepC.timePerQueryMs(), 30), NOREP, 2.0f);
        addSeries(right, "Q-Learning", qlC.queryNumbers(), movingAverage(qlC.timePerQueryMs(), 30), QLEARNING, 2.0f);
        addSeries(right, "DQN", dqnC.queryNumbers(), movingAverage(dqnC.timePerQueryMs(), 30), DQN, 2.0f);
        
        saveSideBySide(left, right, "images/rl_fig3_response_time.png");
        System.out.println("  [RL Fig 3] Response Time (4 models)");
    }
    
    /** RL Fig 4: BW Consumption (4 models) */
    public static void generateBwConsumption(BenchmarkDataPerQuery norepS, BenchmarkDataPerQuery tcdrmS,
                                              BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                              BenchmarkDataPerQuery norepC, BenchmarkDataPerQuery tcdrmC,
                                              BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        List<String> cats = List.of("NoRepLc", "TCDRM", "Q-Learn", "DQN");
        
        CategoryChart left = createBarChart("BW Consumption - Simple (4 models)", "SIMPLE QUERIES", "BW (GByte)");
        left.addSeries("interProvider", cats,
            List.of(norepS.totalBwInterProviderGb(), tcdrmS.totalBwInterProviderGb(), qlS.totalBwInterProviderGb(), dqnS.totalBwInterProviderGb()));
        left.addSeries("interRegion", cats,
            List.of(norepS.totalBwInterRegionGb(), tcdrmS.totalBwInterRegionGb(), qlS.totalBwInterRegionGb(), dqnS.totalBwInterRegionGb()));
        setBarColor(left, "interProvider", INTER_PROVIDER);
        setBarColor(left, "interRegion", INTER_REGION);
        
        CategoryChart right = createBarChart("BW Consumption - Complex (4 models)", "COMPLEX QUERIES", "BW (GByte)");
        right.addSeries("interProvider", cats,
            List.of(norepC.totalBwInterProviderGb(), tcdrmC.totalBwInterProviderGb(), qlC.totalBwInterProviderGb(), dqnC.totalBwInterProviderGb()));
        right.addSeries("interRegion", cats,
            List.of(norepC.totalBwInterRegionGb(), tcdrmC.totalBwInterRegionGb(), qlC.totalBwInterRegionGb(), dqnC.totalBwInterRegionGb()));
        setBarColor(right, "interProvider", INTER_PROVIDER);
        setBarColor(right, "interRegion", INTER_REGION);
        
        saveSideBySide(left, right, "images/rl_fig4_bw_consumption.png");
        System.out.println("  [RL Fig 4] BW Consumption (4 models)");
    }
    
    /** RL Fig 5: Avg BW Price (4 models) */
    public static void generateAvgBwPrice(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                           BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                           BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC,
                                           BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        XYChart left = createLineChart("Avg. BW Price — Simple (4 models)", "Number of Queries", "Price ($)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), computeRunningAvgCost(tcdrmS.costPerQuery()), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), computeRunningAvgCost(norepS.costPerQuery()), NOREP, 2.0f);
        addSeries(left, "Q-Learning", qlS.queryNumbers(), computeRunningAvgCost(qlS.costPerQuery()), QLEARNING, 2.0f);
        addSeries(left, "DQN", dqnS.queryNumbers(), computeRunningAvgCost(dqnS.costPerQuery()), DQN, 2.0f);
        
        XYChart right = createLineChart("Avg. BW Price — Complex (4 models)", "Number of Queries", "Price ($)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), computeRunningAvgCost(tcdrmC.costPerQuery()), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), computeRunningAvgCost(norepC.costPerQuery()), NOREP, 2.0f);
        addSeries(right, "Q-Learning", qlC.queryNumbers(), computeRunningAvgCost(qlC.costPerQuery()), QLEARNING, 2.0f);
        addSeries(right, "DQN", dqnC.queryNumbers(), computeRunningAvgCost(dqnC.costPerQuery()), DQN, 2.0f);
        
        saveSideBySide(left, right, "images/rl_fig5_avg_bw_price.png");
        System.out.println("  [RL Fig 5] Avg BW Price (4 models)");
    }
    
    /** RL Fig 6: Cumulative BW Price (4 models) */
    public static void generateCumulativeBwPrice(BenchmarkDataPerQuery tcdrmS, BenchmarkDataPerQuery norepS,
                                                  BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                                  BenchmarkDataPerQuery tcdrmC, BenchmarkDataPerQuery norepC,
                                                  BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        XYChart left = createLineChart("Cumul. BW Price - Simple (4 models)", "Number of Queries", "Price ($)");
        addSeries(left, "TCDRM", tcdrmS.queryNumbers(), tcdrmS.cumulativeCost(), TCDRM, 2.0f);
        addSeries(left, "NoRepLc", norepS.queryNumbers(), norepS.cumulativeCost(), NOREP, 2.0f);
        addSeries(left, "Q-Learning", qlS.queryNumbers(), qlS.cumulativeCost(), QLEARNING, 2.0f);
        addSeries(left, "DQN", dqnS.queryNumbers(), dqnS.cumulativeCost(), DQN, 2.0f);
        
        XYChart right = createLineChart("Cumul. BW Price - Complex (4 models)", "Number of Queries", "Price ($)");
        addSeries(right, "TCDRM", tcdrmC.queryNumbers(), tcdrmC.cumulativeCost(), TCDRM, 2.0f);
        addSeries(right, "NoRepLc", norepC.queryNumbers(), norepC.cumulativeCost(), NOREP, 2.0f);
        addSeries(right, "Q-Learning", qlC.queryNumbers(), qlC.cumulativeCost(), QLEARNING, 2.0f);
        addSeries(right, "DQN", dqnC.queryNumbers(), dqnC.cumulativeCost(), DQN, 2.0f);
        
        saveSideBySide(left, right, "images/rl_fig6_cumulative_bw_price.png");
        System.out.println("  [RL Fig 6] Cumulative BW Price (4 models)");
    }
    
    /** RL Fig 7: Total Cost stacked bar (4 models) */
    public static void generateTotalCost(BenchmarkDataPerQuery norepS, BenchmarkDataPerQuery tcdrmS,
                                          BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
                                          BenchmarkDataPerQuery norepC, BenchmarkDataPerQuery tcdrmC,
                                          BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC) throws IOException {
        List<String> cats = List.of("NoRepLc", "TCDRM", "Q-Learn", "DQN");

        CategoryChart left = createStackedBarChart("Total Cost — Simple (4 models)", "Cost ($)");
        left.addSeries("CPU", cats, List.of(norepS.totalCpuCost(), tcdrmS.totalCpuCost(), qlS.totalCpuCost(), dqnS.totalCpuCost()));
        left.addSeries("Bandwidth", cats, List.of(norepS.totalBwCost(), tcdrmS.totalBwCost(), qlS.totalBwCost(), dqnS.totalBwCost()));
        left.addSeries("Replica (creation + storage)", cats, List.of(norepS.totalReplicaCost(), tcdrmS.totalReplicaCost(), qlS.totalReplicaCost(), dqnS.totalReplicaCost()));
        setBarColor(left, "CPU", CPU);
        setBarColor(left, "Bandwidth", BANDWIDTH);
        setBarColor(left, "Replica (creation + storage)", REPLICA);

        CategoryChart right = createStackedBarChart("Total Cost — Complex (4 models)", "Cost ($)");
        right.addSeries("CPU", cats, List.of(norepC.totalCpuCost(), tcdrmC.totalCpuCost(), qlC.totalCpuCost(), dqnC.totalCpuCost()));
        right.addSeries("Bandwidth", cats, List.of(norepC.totalBwCost(), tcdrmC.totalBwCost(), qlC.totalBwCost(), dqnC.totalBwCost()));
        right.addSeries("Replica (creation + storage)", cats, List.of(norepC.totalReplicaCost(), tcdrmC.totalReplicaCost(), qlC.totalReplicaCost(), dqnC.totalReplicaCost()));
        setBarColor(right, "CPU", CPU);
        setBarColor(right, "Bandwidth", BANDWIDTH);
        setBarColor(right, "Replica (creation + storage)", REPLICA);

        saveSideBySide(left, right, "images/rl_fig7_total_cost.png");
        System.out.println("  [RL Fig 7] Total Cost stacked bar (4 models)");
    }
}
