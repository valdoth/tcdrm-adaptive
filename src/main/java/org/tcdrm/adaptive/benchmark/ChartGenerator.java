package org.tcdrm.adaptive.benchmark;

import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.tcdrm.adaptive.core.TcdrmConstants;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Génère les graphiques du paper TCDRM (Figs 2-8).
 */
public class ChartGenerator {

    // Couleurs du PDF (exactes)
    public static final Color COLOR_TCDRM = new Color(31, 119, 180);    // Bleu
    public static final Color COLOR_NOREP = new Color(214, 39, 40);     // Rouge
    public static final Color COLOR_QLEARNING = new Color(255, 187, 0); // Jaune/Or
    public static final Color COLOR_DQN = new Color(44, 160, 44);       // Vert
    
    // Couleurs pour barres
    public static final Color COLOR_INTER_PROVIDER = new Color(31, 119, 180); // Bleu
    public static final Color COLOR_INTER_REGION = new Color(214, 39, 40);    // Rouge
    public static final Color COLOR_CPU = new Color(31, 119, 180);            // Bleu
    public static final Color COLOR_BW = new Color(214, 39, 40);              // Rouge
    public static final Color COLOR_REPLICA = new Color(255, 187, 0);         // Jaune

    /**
     * Fig 1: Replica Factor (4 models) - 2 graphiques côte à côte
     */
    public static void generateReplicaFactor4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                     BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                     BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                     BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                     String filename) throws IOException {
        XYChart chartSimple = createReplicaChart("Replica Factor — Simple (4 models)", 
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        XYChart chartComplex = createReplicaChart("Replica Factor — Complex (4 models)", 
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 1] Replica Factor (4 models) saved");
    }
    
    private static XYChart createReplicaChart(String title, BenchmarkData norep, BenchmarkData tcdrm,
                                               BenchmarkData ql, BenchmarkData dqn) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of queries")
            .yAxisTitle("Number of replica")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = tcdrm.getQueryNumbers();
        chart.addSeries("TCDRM", x, toDoubleList(tcdrm.getReplicaCount())).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, toDoubleList(norep.getReplicaCount())).setLineColor(COLOR_NOREP);
        chart.addSeries("Q-Learning", x, toDoubleList(ql.getReplicaCount())).setLineColor(COLOR_QLEARNING);
        chart.addSeries("DQN", x, toDoubleList(dqn.getReplicaCount())).setLineColor(COLOR_DQN);

        return chart;
    }
    
    /**
     * Fig 2: Replica Factor (TCDRM only) - pour compatibilité
     */
    public static void generateReplicaFactor(BenchmarkData tcdrmSimple, BenchmarkData tcdrmComplex,
                                              String filename) throws IOException {
        // Version simplifiée pour TCDRM seulement
        XYChart chart = new XYChartBuilder()
            .width(800).height(500)
            .title("Replica Factor — TCDRM")
            .xAxisTitle("Number of queries")
            .yAxisTitle("Number of replica")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        
        List<Integer> x = tcdrmSimple.getQueryNumbers();
        chart.addSeries("Simple", x, toDoubleList(tcdrmSimple.getReplicaCount())).setLineColor(COLOR_TCDRM);
        chart.addSeries("Complex", x, toDoubleList(tcdrmComplex.getReplicaCount())).setLineColor(COLOR_DQN);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [Fig 2] Replica Factor saved");
    }

    /**
     * Fig 2: Response Time (4 models) - 2 graphiques côte à côte
     */
    public static void generateResponseTime4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                    BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                    BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                    BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                    String filename) throws IOException {
        XYChart chartSimple = createTimeChart4("Impact on Response Times — Simple (4 models)", 
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        XYChart chartComplex = createTimeChart4("Impact on Response Times — Complex (4 models)", 
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 2] Response Time (4 models) saved");
    }
    
    private static XYChart createTimeChart4(String title, BenchmarkData norep, BenchmarkData tcdrm,
                                             BenchmarkData ql, BenchmarkData dqn) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Response time (ms)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("TCDRM", x, smooth(tcdrm.getResponseTimeMs(), 20)).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, smooth(norep.getResponseTimeMs(), 20)).setLineColor(COLOR_NOREP);
        chart.addSeries("Q-Learning", x, smooth(ql.getResponseTimeMs(), 20)).setLineColor(COLOR_QLEARNING);
        chart.addSeries("DQN", x, smooth(dqn.getResponseTimeMs(), 20)).setLineColor(COLOR_DQN);

        return chart;
    }

    /**
     * Fig 3: Response Time (2 models) - pour compatibilité
     */
    public static void generateResponseTime(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                             BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                             String filename) throws IOException {
        XYChart chartSimple = createTimeChart("Simple Queries", norepSimple, tcdrmSimple);
        XYChart chartComplex = createTimeChart("Complex Queries", norepComplex, tcdrmComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 3] Response Time saved");
    }

    private static XYChart createTimeChart(String title, BenchmarkData norep, BenchmarkData tcdrm) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Response time (ms)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("TCDRM", x, smooth(tcdrm.getResponseTimeMs(), 20)).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, smooth(norep.getResponseTimeMs(), 20)).setLineColor(COLOR_NOREP);

        return chart;
    }

    /**
     * Fig 6: Cumulative BW Price
     */
    public static void generateCumulativeBwPrice(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                  BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                  String filename) throws IOException {
        XYChart chartSimple = createCumulChart("Simple Queries", norepSimple, tcdrmSimple);
        XYChart chartComplex = createCumulChart("Complex Queries", norepComplex, tcdrmComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "Fig 6: Cumulative BW Price");
        System.out.println("  [Fig 6] Cumulative BW Price saved");
    }

    private static XYChart createCumulChart(String title, BenchmarkData norep, BenchmarkData tcdrm) {
        XYChart chart = new XYChartBuilder()
            .width(400).height(400)
            .title(title)
            .xAxisTitle("Query Number")
            .yAxisTitle("Cumulative Cost ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setMarkerSize(0);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("NoRepLc", x, norep.getCumulativeCost())
            .setLineColor(COLOR_NOREP);
        chart.addSeries("TCDRM", x, tcdrm.getCumulativeCost())
            .setLineColor(COLOR_TCDRM);

        return chart;
    }

    /**
     * Génère les graphiques RL avec comparaison TCDRM/NoRep/QL/DQN.
     */
    public static void generateRLComparison(BenchmarkData norep, BenchmarkData tcdrm,
                                             BenchmarkData qlearning, BenchmarkData dqn,
                                             String prefix, boolean complex) throws IOException {
        String type = complex ? "Complex" : "Simple";
        
        // Response Time
        XYChart timeChart = new XYChartBuilder()
            .width(800).height(500)
            .title("Response Time - " + type + " Queries")
            .xAxisTitle("Query Number")
            .yAxisTitle("Response Time (ms)")
            .build();
        
        timeChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        timeChart.getStyler().setMarkerSize(0);
        List<Integer> x = norep.getQueryNumbers();
        
        timeChart.addSeries("NoRepLc", x, smooth(norep.getResponseTimeMs(), 20))
            .setLineColor(COLOR_NOREP);
        timeChart.addSeries("TCDRM", x, smooth(tcdrm.getResponseTimeMs(), 20))
            .setLineColor(COLOR_TCDRM);
        timeChart.addSeries("Q-Learning", x, smooth(qlearning.getResponseTimeMs(), 20))
            .setLineColor(COLOR_QLEARNING);
        timeChart.addSeries("DQN", x, smooth(dqn.getResponseTimeMs(), 20))
            .setLineColor(COLOR_DQN);
        
        BitmapEncoder.saveBitmap(timeChart, prefix + "_response_time.png", BitmapEncoder.BitmapFormat.PNG);
        
        // Replica Count
        XYChart replicaChart = new XYChartBuilder()
            .width(800).height(500)
            .title("Replica Count - " + type + " Queries")
            .xAxisTitle("Query Number")
            .yAxisTitle("Number of Replicas")
            .build();
        
        replicaChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        replicaChart.getStyler().setMarkerSize(0);
        
        replicaChart.addSeries("TCDRM", x, toDoubleList(tcdrm.getReplicaCount()))
            .setLineColor(COLOR_TCDRM);
        replicaChart.addSeries("Q-Learning", x, toDoubleList(qlearning.getReplicaCount()))
            .setLineColor(COLOR_QLEARNING);
        replicaChart.addSeries("DQN", x, toDoubleList(dqn.getReplicaCount()))
            .setLineColor(COLOR_DQN);
        
        BitmapEncoder.saveBitmap(replicaChart, prefix + "_replicas.png", BitmapEncoder.BitmapFormat.PNG);
        
        // Cumulative Cost
        XYChart costChart = new XYChartBuilder()
            .width(800).height(500)
            .title("Cumulative Cost - " + type + " Queries")
            .xAxisTitle("Query Number")
            .yAxisTitle("Cumulative Cost ($)")
            .build();
        
        costChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        costChart.getStyler().setMarkerSize(0);
        
        costChart.addSeries("NoRepLc", x, norep.getCumulativeCost())
            .setLineColor(COLOR_NOREP);
        costChart.addSeries("TCDRM", x, tcdrm.getCumulativeCost())
            .setLineColor(COLOR_TCDRM);
        costChart.addSeries("Q-Learning", x, qlearning.getCumulativeCost())
            .setLineColor(COLOR_QLEARNING);
        costChart.addSeries("DQN", x, dqn.getCumulativeCost())
            .setLineColor(COLOR_DQN);
        
        BitmapEncoder.saveBitmap(costChart, prefix + "_cost.png", BitmapEncoder.BitmapFormat.PNG);
        
        System.out.println("  [RL] " + type + " comparison charts saved");
    }

    /**
     * Fig 3: BW Consumption (4 models) - 2 graphiques côte à côte avec barres groupées
     */
    public static void generateBwConsumption4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                     BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                     BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                     BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                     String filename) throws IOException {
        CategoryChart chartSimple = createBwChart4("BW Consumption - Simple (4 models)", "SIMPLE QUERIES",
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        CategoryChart chartComplex = createBwChart4("BW Consumption - Complex (4 models)", "COMPLEX QUERIES",
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedCategoryChart(chartSimple, chartComplex, filename);
        System.out.println("  [Fig 3] BW Consumption (4 models) saved");
    }
    
    private static CategoryChart createBwChart4(String title, String subtitle,
                                                 BenchmarkData norep, BenchmarkData tcdrm,
                                                 BenchmarkData ql, BenchmarkData dqn) {
        CategoryChart chart = new CategoryChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle(subtitle)
            .yAxisTitle("BW (GByte)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setStacked(false); // Barres groupées, pas empilées
        chart.getStyler().setPlotGridLinesVisible(true);

        List<String> categories = Arrays.asList("NoRepLc", "TCDRM", "Q-Learn", "DQN");
        List<Double> interProvider = Arrays.asList(
            norep.getTotalBwInterProviderGb(), tcdrm.getTotalBwInterProviderGb(),
            ql.getTotalBwInterProviderGb(), dqn.getTotalBwInterProviderGb()
        );
        List<Double> interRegion = Arrays.asList(
            norep.getTotalBwInterRegionGb(), tcdrm.getTotalBwInterRegionGb(),
            ql.getTotalBwInterRegionGb(), dqn.getTotalBwInterRegionGb()
        );

        chart.addSeries("interProvider", categories, interProvider).setFillColor(COLOR_INTER_PROVIDER);
        chart.addSeries("interRegion", categories, interRegion).setFillColor(COLOR_INTER_REGION);

        return chart;
    }

    /**
     * Fig 4: BW Consumption (2 models) - pour compatibilité
     */
    public static void generateBwConsumption(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                              BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                              String filename) throws IOException {
        // Version simplifiée
        CategoryChart chart = new CategoryChartBuilder()
            .width(800).height(500)
            .title("BW Consumption")
            .xAxisTitle("Strategy")
            .yAxisTitle("BW (GByte)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setStacked(false);

        List<String> categories = Arrays.asList("NoRep Simple", "TCDRM Simple", "NoRep Complex", "TCDRM Complex");
        List<Double> interProvider = Arrays.asList(
            norepSimple.getTotalBwInterProviderGb(), tcdrmSimple.getTotalBwInterProviderGb(),
            norepComplex.getTotalBwInterProviderGb(), tcdrmComplex.getTotalBwInterProviderGb()
        );
        List<Double> interRegion = Arrays.asList(
            norepSimple.getTotalBwInterRegionGb(), tcdrmSimple.getTotalBwInterRegionGb(),
            norepComplex.getTotalBwInterRegionGb(), tcdrmComplex.getTotalBwInterRegionGb()
        );

        chart.addSeries("interProvider", categories, interProvider).setFillColor(COLOR_INTER_PROVIDER);
        chart.addSeries("interRegion", categories, interRegion).setFillColor(COLOR_INTER_REGION);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [Fig 4] BW Consumption saved");
    }

    /**
     * Fig 4: Avg BW Price (4 models) - 2 graphiques côte à côte
     */
    public static void generateAvgBwPrice4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                  BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                  BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                  BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                  String filename) throws IOException {
        XYChart chartSimple = createAvgPriceChart4("Avg. BW Price — Simple (4 models)", 
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        XYChart chartComplex = createAvgPriceChart4("Avg. BW Price — Complex (4 models)", 
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 4] Avg BW Price (4 models) saved");
    }
    
    private static XYChart createAvgPriceChart4(String title, BenchmarkData norep, BenchmarkData tcdrm,
                                                 BenchmarkData ql, BenchmarkData dqn) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Price ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("TCDRM", x, tcdrm.getAvgBwPrice()).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, norep.getAvgBwPrice()).setLineColor(COLOR_NOREP);
        chart.addSeries("Q-Learning", x, ql.getAvgBwPrice()).setLineColor(COLOR_QLEARNING);
        chart.addSeries("DQN", x, dqn.getAvgBwPrice()).setLineColor(COLOR_DQN);

        return chart;
    }

    /**
     * Fig 5: Avg BW Price (2 models) - pour compatibilité
     */
    public static void generateAvgBwPrice(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                           BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                           String filename) throws IOException {
        XYChart chartSimple = createAvgPriceChart("Simple Queries", norepSimple, tcdrmSimple);
        XYChart chartComplex = createAvgPriceChart("Complex Queries", norepComplex, tcdrmComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 5] Avg BW Price saved");
    }

    private static XYChart createAvgPriceChart(String title, BenchmarkData norep, BenchmarkData tcdrm) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Price ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("TCDRM", x, tcdrm.getAvgBwPrice()).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, norep.getAvgBwPrice()).setLineColor(COLOR_NOREP);

        return chart;
    }

    /**
     * Fig 5: Cumulative BW Price (4 models) - 2 graphiques côte à côte
     */
    public static void generateCumulativeBwPrice4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                         BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                         BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                         BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                         String filename) throws IOException {
        XYChart chartSimple = createCumulChart4("Cumul. BW Price - Simple (4 models)", 
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        XYChart chartComplex = createCumulChart4("Cumul. BW Price - Complex (4 models)", 
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedChart(chartSimple, chartComplex, filename, "");
        System.out.println("  [Fig 5] Cumulative BW Price (4 models) saved");
    }
    
    private static XYChart createCumulChart4(String title, BenchmarkData norep, BenchmarkData tcdrm,
                                              BenchmarkData ql, BenchmarkData dqn) {
        XYChart chart = new XYChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("Number of Queries")
            .yAxisTitle("Price ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setPlotGridLinesVisible(true);
        
        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("TCDRM", x, tcdrm.getCumulativeCost()).setLineColor(COLOR_TCDRM);
        chart.addSeries("NoRepLc", x, norep.getCumulativeCost()).setLineColor(COLOR_NOREP);
        chart.addSeries("Q-Learning", x, ql.getCumulativeCost()).setLineColor(COLOR_QLEARNING);
        chart.addSeries("DQN", x, dqn.getCumulativeCost()).setLineColor(COLOR_DQN);

        return chart;
    }

    /**
     * Fig 6: Total Cost (4 models) - 2 graphiques côte à côte avec barres empilées
     */
    public static void generateTotalCost4Models(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                                 BenchmarkData qlSimple, BenchmarkData dqnSimple,
                                                 BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                                 BenchmarkData qlComplex, BenchmarkData dqnComplex,
                                                 String filename) throws IOException {
        CategoryChart chartSimple = createTotalCostChart4("Total Cost — Simple (4 models)", 
            norepSimple, tcdrmSimple, qlSimple, dqnSimple);
        CategoryChart chartComplex = createTotalCostChart4("Total Cost — Complex (4 models)", 
            norepComplex, tcdrmComplex, qlComplex, dqnComplex);
        
        saveCombinedCategoryChart(chartSimple, chartComplex, filename);
        System.out.println("  [Fig 6] Total Cost (4 models) saved");
    }
    
    private static CategoryChart createTotalCostChart4(String title, BenchmarkData norep, BenchmarkData tcdrm,
                                                        BenchmarkData ql, BenchmarkData dqn) {
        CategoryChart chart = new CategoryChartBuilder()
            .width(500).height(400)
            .title(title)
            .xAxisTitle("")
            .yAxisTitle("Cost ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setStacked(true);
        chart.getStyler().setPlotGridLinesVisible(true);

        List<String> categories = Arrays.asList("NoRepLc", "TCDRM", "Q-Learn", "DQN");
        List<Double> cpuCost = Arrays.asList(
            norep.getTotalCpuCost(), tcdrm.getTotalCpuCost(),
            ql.getTotalCpuCost(), dqn.getTotalCpuCost()
        );
        List<Double> bwCost = Arrays.asList(
            norep.getTotalBwCost(), tcdrm.getTotalBwCost(),
            ql.getTotalBwCost(), dqn.getTotalBwCost()
        );
        List<Double> replicaCost = Arrays.asList(
            norep.getTotalReplicaCost(), tcdrm.getTotalReplicaCost(),
            ql.getTotalReplicaCost(), dqn.getTotalReplicaCost()
        );

        chart.addSeries("CPU", categories, cpuCost).setFillColor(COLOR_CPU);
        chart.addSeries("Bandwidth", categories, bwCost).setFillColor(COLOR_BW);
        chart.addSeries("Replica (creation + storage)", categories, replicaCost).setFillColor(COLOR_REPLICA);

        return chart;
    }

    /**
     * Fig 7: Total Cost Breakdown (2 models) - pour compatibilité
     */
    public static void generateTotalCost(BenchmarkData norepSimple, BenchmarkData tcdrmSimple,
                                          BenchmarkData norepComplex, BenchmarkData tcdrmComplex,
                                          String filename) throws IOException {
        CategoryChart chart = new CategoryChartBuilder()
            .width(800).height(500)
            .title("Total Cost Breakdown")
            .xAxisTitle("Strategy")
            .yAxisTitle("Cost ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setStacked(true);

        List<String> categories = Arrays.asList("NoRep Simple", "TCDRM Simple", "NoRep Complex", "TCDRM Complex");
        List<Double> cpuCost = Arrays.asList(
            norepSimple.getTotalCpuCost(), tcdrmSimple.getTotalCpuCost(),
            norepComplex.getTotalCpuCost(), tcdrmComplex.getTotalCpuCost()
        );
        List<Double> bwCost = Arrays.asList(
            norepSimple.getTotalBwCost(), tcdrmSimple.getTotalBwCost(),
            norepComplex.getTotalBwCost(), tcdrmComplex.getTotalBwCost()
        );
        List<Double> replicaCost = Arrays.asList(
            norepSimple.getTotalReplicaCost(), tcdrmSimple.getTotalReplicaCost(),
            norepComplex.getTotalReplicaCost(), tcdrmComplex.getTotalReplicaCost()
        );

        chart.addSeries("CPU", categories, cpuCost).setFillColor(COLOR_CPU);
        chart.addSeries("Bandwidth", categories, bwCost).setFillColor(COLOR_BW);
        chart.addSeries("Replica (creation + storage)", categories, replicaCost).setFillColor(COLOR_REPLICA);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [Fig 7] Total Cost saved");
    }

    /**
     * RL-4: BW Consumption (4 models)
     */
    public static void generateRLBwConsumption(BenchmarkData norep, BenchmarkData tcdrm,
                                                BenchmarkData qlearning, BenchmarkData dqn,
                                                String filename, boolean complex) throws IOException {
        String type = complex ? "Complex" : "Simple";
        CategoryChart chart = new CategoryChartBuilder()
            .width(800).height(500)
            .title("RL-4: BW Consumption - " + type)
            .xAxisTitle("Strategy")
            .yAxisTitle("Total BW (GB)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setStacked(true);

        List<String> categories = Arrays.asList("NoRepLc", "TCDRM", "Q-Learning", "DQN");
        List<Double> interProvider = Arrays.asList(
            norep.getTotalBwInterProviderGb(), tcdrm.getTotalBwInterProviderGb(),
            qlearning.getTotalBwInterProviderGb(), dqn.getTotalBwInterProviderGb()
        );
        List<Double> interRegion = Arrays.asList(
            norep.getTotalBwInterRegionGb(), tcdrm.getTotalBwInterRegionGb(),
            qlearning.getTotalBwInterRegionGb(), dqn.getTotalBwInterRegionGb()
        );

        chart.addSeries("Inter-Provider", categories, interProvider).setFillColor(COLOR_INTER_PROVIDER);
        chart.addSeries("Inter-Region", categories, interRegion).setFillColor(COLOR_INTER_REGION);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [RL-4] BW Consumption " + type + " saved");
    }

    /**
     * RL-5: Avg BW Price (4 models)
     */
    public static void generateRLAvgBwPrice(BenchmarkData norep, BenchmarkData tcdrm,
                                             BenchmarkData qlearning, BenchmarkData dqn,
                                             String filename, boolean complex) throws IOException {
        String type = complex ? "Complex" : "Simple";
        XYChart chart = new XYChartBuilder()
            .width(800).height(500)
            .title("RL-5: Avg BW Price - " + type)
            .xAxisTitle("Query Number")
            .yAxisTitle("Avg Cost ($/query)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);

        List<Integer> x = norep.getQueryNumbers();
        chart.addSeries("NoRepLc", x, norep.getAvgBwPrice()).setLineColor(COLOR_NOREP);
        chart.addSeries("TCDRM", x, tcdrm.getAvgBwPrice()).setLineColor(COLOR_TCDRM);
        chart.addSeries("Q-Learning", x, qlearning.getAvgBwPrice()).setLineColor(COLOR_QLEARNING);
        chart.addSeries("DQN", x, dqn.getAvgBwPrice()).setLineColor(COLOR_DQN);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [RL-5] Avg BW Price " + type + " saved");
    }

    /**
     * RL-7: Total Cost Breakdown (4 models)
     */
    public static void generateRLTotalCost(BenchmarkData norep, BenchmarkData tcdrm,
                                            BenchmarkData qlearning, BenchmarkData dqn,
                                            String filename, boolean complex) throws IOException {
        String type = complex ? "Complex" : "Simple";
        CategoryChart chart = new CategoryChartBuilder()
            .width(800).height(500)
            .title("RL-7: Total Cost - " + type)
            .xAxisTitle("Strategy")
            .yAxisTitle("Total Cost ($)")
            .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setStacked(true);

        List<String> categories = Arrays.asList("NoRepLc", "TCDRM", "Q-Learning", "DQN");
        List<Double> cpuCost = Arrays.asList(
            norep.getTotalCpuCost(), tcdrm.getTotalCpuCost(),
            qlearning.getTotalCpuCost(), dqn.getTotalCpuCost()
        );
        List<Double> bwCost = Arrays.asList(
            norep.getTotalBwCost(), tcdrm.getTotalBwCost(),
            qlearning.getTotalBwCost(), dqn.getTotalBwCost()
        );
        List<Double> replicaCost = Arrays.asList(
            norep.getTotalReplicaCost(), tcdrm.getTotalReplicaCost(),
            qlearning.getTotalReplicaCost(), dqn.getTotalReplicaCost()
        );

        chart.addSeries("CPU", categories, cpuCost).setFillColor(COLOR_CPU);
        chart.addSeries("Bandwidth", categories, bwCost).setFillColor(COLOR_BW);
        chart.addSeries("Replication", categories, replicaCost).setFillColor(COLOR_REPLICA);

        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.println("  [RL-7] Total Cost " + type + " saved");
    }

    /**
     * Métriques détaillées par modèle (5 sous-graphiques)
     */
    public static void generateModelMetrics(BenchmarkData data, String filename, boolean complex) throws IOException {
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        List<Integer> x = data.getQueryNumbers();
        int width = 1600, height = 1200;
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(data.getName() + " Metrics", width / 2 - 80, 25);
        
        // 1. Response Time
        XYChart timeChart = new XYChartBuilder().width(500).height(350)
            .title("Response Time").xAxisTitle("Query").yAxisTitle("Time (ms)").build();
        timeChart.getStyler().setMarkerSize(0);
        timeChart.addSeries("Response Time", x, smooth(data.getResponseTimeMs(), 20)).setLineColor(COLOR_TCDRM);
        timeChart.addSeries("T_SLA", Arrays.asList(0, x.size()-1), Arrays.asList(tSla, tSla)).setLineColor(Color.RED);
        g.drawImage(BitmapEncoder.getBufferedImage(timeChart), 30, 40, null);
        
        // 2. Cumulative BW Cost
        XYChart costChart = new XYChartBuilder().width(500).height(350)
            .title("Cumulative BW Cost").xAxisTitle("Query").yAxisTitle("Cost ($)").build();
        costChart.getStyler().setMarkerSize(0);
        costChart.addSeries("Cumulative Cost", x, data.getCumulativeCost()).setLineColor(COLOR_TCDRM);
        g.drawImage(BitmapEncoder.getBufferedImage(costChart), 550, 40, null);
        
        // 3. SLA Violations
        XYChart slaChart = new XYChartBuilder().width(500).height(350)
            .title("Cumulative SLA Violations").xAxisTitle("Query").yAxisTitle("Violations").build();
        slaChart.getStyler().setMarkerSize(0);
        slaChart.addSeries("SLA Violations", x, toDoubleList(data.getSlaViolations())).setLineColor(Color.RED);
        g.drawImage(BitmapEncoder.getBufferedImage(slaChart), 1070, 40, null);
        
        // 4. Replica Count
        XYChart replicaChart = new XYChartBuilder().width(500).height(350)
            .title("Replica Count").xAxisTitle("Query").yAxisTitle("Replicas").build();
        replicaChart.getStyler().setMarkerSize(0);
        replicaChart.addSeries("Replicas", x, toDoubleList(data.getReplicaCount())).setLineColor(COLOR_QLEARNING);
        g.drawImage(BitmapEncoder.getBufferedImage(replicaChart), 30, 420, null);
        
        // 5. SLA Compliance Rate
        List<Double> complianceRate = new ArrayList<>();
        for (int i = 0; i < x.size(); i++) {
            int violations = data.getSlaViolations().get(i);
            complianceRate.add(100.0 * (i + 1 - violations) / (i + 1));
        }
        XYChart compChart = new XYChartBuilder().width(500).height(350)
            .title("SLA Compliance Rate").xAxisTitle("Query").yAxisTitle("Compliance (%)").build();
        compChart.getStyler().setMarkerSize(0);
        compChart.addSeries("Compliance", x, complianceRate).setLineColor(new Color(0, 128, 0));
        g.drawImage(BitmapEncoder.getBufferedImage(compChart), 550, 420, null);
        
        g.dispose();
        ImageIO.write(combined, "png", new File(filename));
        System.out.println("  [Metrics] " + data.getName() + " saved");
    }

    /**
     * Analyse de la popularité par modèle (4 sous-graphiques)
     */
    public static void generatePopularityAnalysis(BenchmarkData data, String filename, boolean complex) throws IOException {
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double pSla = TcdrmConstants.POPULARITY_THRESHOLD;
        List<Integer> x = data.getQueryNumbers();
        int width = 1600, height = 900;
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString(data.getName() + " Popularity Analysis", width / 2 - 120, 25);
        
        // 1. Normalized Popularity
        List<Double> normPop = new ArrayList<>();
        for (int i = 0; i < x.size(); i++) {
            normPop.add((double)(i + 1) / pSla);
        }
        XYChart popChart = new XYChartBuilder().width(750).height(380)
            .title("Normalized Popularity").xAxisTitle("Query").yAxisTitle("Popularity / P_SLA").build();
        popChart.getStyler().setMarkerSize(0);
        popChart.addSeries("Popularity", x, normPop).setLineColor(COLOR_TCDRM);
        popChart.addSeries("Threshold", Arrays.asList(0, x.size()-1), Arrays.asList(1.0, 1.0)).setLineColor(Color.RED);
        g.drawImage(BitmapEncoder.getBufferedImage(popChart), 30, 40, null);
        
        // 2. Replica Creation Timeline
        XYChart replicaChart = new XYChartBuilder().width(750).height(380)
            .title("Replica Creation Timeline").xAxisTitle("Query").yAxisTitle("Replicas").build();
        replicaChart.getStyler().setMarkerSize(0);
        replicaChart.addSeries("Replicas", x, toDoubleList(data.getReplicaCount())).setLineColor(COLOR_QLEARNING);
        g.drawImage(BitmapEncoder.getBufferedImage(replicaChart), 810, 40, null);
        
        // 3. Response Time & Replica Impact
        XYChart timeChart = new XYChartBuilder().width(750).height(380)
            .title("Response Time & Replica Impact").xAxisTitle("Query").yAxisTitle("Time (ms)").build();
        timeChart.getStyler().setMarkerSize(0);
        timeChart.addSeries("Response Time", x, smooth(data.getResponseTimeMs(), 20)).setLineColor(COLOR_TCDRM);
        timeChart.addSeries("T_SLA", Arrays.asList(0, x.size()-1), Arrays.asList(tSla, tSla)).setLineColor(Color.RED);
        g.drawImage(BitmapEncoder.getBufferedImage(timeChart), 30, 450, null);
        
        // 4. Popularity Impact on Response Time
        XYChart impactChart = new XYChartBuilder().width(750).height(380)
            .title("Popularity Impact on Response Time").xAxisTitle("Normalized Popularity").yAxisTitle("Time (ms)").build();
        impactChart.getStyler().setMarkerSize(3);
        impactChart.addSeries("Response Time", normPop, smooth(data.getResponseTimeMs(), 20)).setLineColor(COLOR_TCDRM);
        g.drawImage(BitmapEncoder.getBufferedImage(impactChart), 810, 450, null);
        
        g.dispose();
        ImageIO.write(combined, "png", new File(filename));
        System.out.println("  [Popularity] " + data.getName() + " saved");
    }

    // === Utility methods ===

    private static void saveCombinedChart(XYChart left, XYChart right, String filename, String title) 
            throws IOException {
        BufferedImage imgLeft = BitmapEncoder.getBufferedImage(left);
        BufferedImage imgRight = BitmapEncoder.getBufferedImage(right);
        
        int width = imgLeft.getWidth() + imgRight.getWidth();
        int height = Math.max(imgLeft.getHeight(), imgRight.getHeight());
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(imgLeft, 0, 0, null);
        g.drawImage(imgRight, imgLeft.getWidth(), 0, null);
        g.dispose();
        
        ImageIO.write(combined, "png", new File(filename));
    }
    
    private static void saveCombinedCategoryChart(CategoryChart left, CategoryChart right, String filename) 
            throws IOException {
        BufferedImage imgLeft = BitmapEncoder.getBufferedImage(left);
        BufferedImage imgRight = BitmapEncoder.getBufferedImage(right);
        
        int width = imgLeft.getWidth() + imgRight.getWidth();
        int height = Math.max(imgLeft.getHeight(), imgRight.getHeight());
        
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = combined.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.drawImage(imgLeft, 0, 0, null);
        g.drawImage(imgRight, imgLeft.getWidth(), 0, null);
        g.dispose();
        
        ImageIO.write(combined, "png", new File(filename));
    }

    private static List<Double> toDoubleList(List<Integer> intList) {
        return intList.stream().map(Integer::doubleValue).collect(Collectors.toList());
    }

    private static List<Double> smooth(List<Double> data, int window) {
        List<Double> smoothed = new java.util.ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int start = Math.max(0, i - window / 2);
            int end = Math.min(data.size(), i + window / 2 + 1);
            double sum = 0;
            for (int j = start; j < end; j++) sum += data.get(j);
            smoothed.add(sum / (end - start));
        }
        return smoothed;
    }
}
