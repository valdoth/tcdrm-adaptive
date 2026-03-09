package org.tcdrm.adaptive;

import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import org.tcdrm.adaptive.runner.BenchmarkRunner;
import org.tcdrm.adaptive.visualization.*;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * TCDRM-ADAPTIVE Main Entry Point.
 * 
 * Generates all paper figures (Phase 1) and RL extension figures (Phase 2).
 */
public class TcdrmMain {
    
    private static Py4JGateway gateway;

    public static void main(String[] args) throws IOException, InterruptedException {
        printHeader();
        new File("images").mkdirs();

        // Phase 1: Paper reproduction
        BenchmarkResults paper = runPaperBenchmarks();
        generatePaperFigures(paper);
        System.out.println("\n✅ Phase 1 complete: All 6 paper figures generated");

        // Phase 2: RL extensions
        BenchmarkResults rl = runRLBenchmarks(paper);
        if (rl != null) {
            generateRLFigures(paper, rl);
            generateIndividualMetrics(paper, rl);
            System.out.println("\n✅ Phase 2 complete: All RL extension graphs generated");
        }
        
        printFooter();
    }
    
    private static void printHeader() {
        System.out.println("=".repeat(80));
        System.out.println("  TCDRM-ADAPTIVE — Paper Figures + RL Extensions");
        System.out.println("  Simple: " + TcdrmConstants.RELATIONS_SIMPLE + " relations x " 
            + (int)(TcdrmConstants.AVG_RELATION_SIZE_GB * 1000) + " MB");
        System.out.println("  Complex: " + TcdrmConstants.RELATIONS_COMPLEX + " relations x " 
            + (int)(TcdrmConstants.AVG_RELATION_SIZE_GB * 1000) + " MB");
        System.out.println("  Queries: " + TcdrmConstants.MAX_QUERIES 
            + ", P_SLA: " + TcdrmConstants.POPULARITY_THRESHOLD);
        System.out.println("=".repeat(80));
    }
    
    private static void printFooter() {
        if (gateway != null) gateway.stop();
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  ALL GRAPHS GENERATED IN images/");
        System.out.println("=".repeat(80));
    }
    
    // ========================================================================
    // Phase 1: Paper Benchmarks
    // ========================================================================
    
    private static BenchmarkResults runPaperBenchmarks() {
        System.out.println("\n━━━ PHASE 1: Paper Figures (TCDRM vs NoRepLc) ━━━");
        
        BenchmarkDataPerQuery norepS = BenchmarkRunner.runNoRep(42L, false, "NoRepLc_Simple");
        BenchmarkDataPerQuery tcdrmS = BenchmarkRunner.runTcdrm(42L, false, "TCDRM_Simple");
        BenchmarkDataPerQuery norepC = BenchmarkRunner.runNoRep(42L, true, "NoRepLc_Complex");
        BenchmarkDataPerQuery tcdrmC = BenchmarkRunner.runTcdrm(42L, true, "TCDRM_Complex");
        
        BenchmarkRunner.logSampleValues("NoRepLc Simple", norepS);
        BenchmarkRunner.logSampleValues("TCDRM   Simple", tcdrmS);
        BenchmarkRunner.logSampleValues("NoRepLc Complex", norepC);
        BenchmarkRunner.logSampleValues("TCDRM   Complex", tcdrmC);
        
        return new BenchmarkResults(norepS, tcdrmS, norepC, tcdrmC, null, null, null, null);
    }
    
    private static void generatePaperFigures(BenchmarkResults r) throws IOException {
        PaperFigureGenerator.generateReplicaFactor(r.tcdrmS, r.tcdrmC);
        PaperFigureGenerator.generateResponseTime(r.tcdrmS, r.norepS, r.tcdrmC, r.norepC);
        PaperFigureGenerator.generateBwConsumption(r.norepS, r.tcdrmS, r.norepC, r.tcdrmC);
        PaperFigureGenerator.generateAvgBwPrice(r.tcdrmS, r.norepS, r.tcdrmC, r.norepC);
        PaperFigureGenerator.generateCumulativeBwPrice(r.tcdrmS, r.norepS, r.tcdrmC, r.norepC);
        PaperFigureGenerator.generateTotalCost(r.norepS, r.tcdrmS, r.norepC, r.tcdrmC);
    }
    
    // ========================================================================
    // Phase 2: RL Benchmarks
    // ========================================================================
    
    private static BenchmarkResults runRLBenchmarks(BenchmarkResults paper) throws InterruptedException {
        System.out.println("\n━━━ PHASE 2: RL Extensions (Q-Learning + DQN) ━━━");
        
        gateway = new Py4JGateway();
        gateway.start();
        
        PythonRLBridge bridge = waitForPythonConnection();
        if (bridge == null) {
            System.err.println("  Python client not connected. Phase 2 skipped.");
            return null;
        }
        
        BenchmarkDataPerQuery qlS = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 42L);
        BenchmarkDataPerQuery dqnS = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Simple", false, 43L);
        bridge.resetCounters();
        BenchmarkDataPerQuery qlC = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 42L);
        BenchmarkDataPerQuery dqnC = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Complex", true, 43L);
        
        return new BenchmarkResults(paper.norepS, paper.tcdrmS, paper.norepC, paper.tcdrmC, qlS, dqnS, qlC, dqnC);
    }
    
    private static PythonRLBridge waitForPythonConnection() throws InterruptedException {
        System.out.println("  Waiting for Python client (timeout: 120s)...");
        int maxWait = 120, elapsed = 0;
        
        while (elapsed < maxWait) {
            Object bridge = gateway.getPythonBridge();
            if (bridge != null) {
                System.out.println("  Python client connected!");
                return (PythonRLBridge) bridge;
            }
            Thread.sleep(2000);
            elapsed += 2;
            if (elapsed % 10 == 0) {
                System.out.println("   ... waiting (" + elapsed + "s/" + maxWait + "s)");
            }
        }
        return null;
    }
    
    private static void generateRLFigures(BenchmarkResults paper, BenchmarkResults rl) throws IOException {
        RLFigureGenerator.generateReplicaFactor(paper.tcdrmS, paper.norepS, rl.qlS, rl.dqnS,
                                                 paper.tcdrmC, paper.norepC, rl.qlC, rl.dqnC);
        RLFigureGenerator.generateResponseTime(paper.tcdrmS, paper.norepS, rl.qlS, rl.dqnS,
                                                paper.tcdrmC, paper.norepC, rl.qlC, rl.dqnC);
        RLFigureGenerator.generateBwConsumption(paper.norepS, paper.tcdrmS, rl.qlS, rl.dqnS,
                                                 paper.norepC, paper.tcdrmC, rl.qlC, rl.dqnC);
        RLFigureGenerator.generateAvgBwPrice(paper.tcdrmS, paper.norepS, rl.qlS, rl.dqnS,
                                              paper.tcdrmC, paper.norepC, rl.qlC, rl.dqnC);
        RLFigureGenerator.generateCumulativeBwPrice(paper.tcdrmS, paper.norepS, rl.qlS, rl.dqnS,
                                                     paper.tcdrmC, paper.norepC, rl.qlC, rl.dqnC);
        RLFigureGenerator.generateTotalCost(paper.norepS, paper.tcdrmS, rl.qlS, rl.dqnS,
                                             paper.norepC, paper.tcdrmC, rl.qlC, rl.dqnC);
    }
    
    private static void generateIndividualMetrics(BenchmarkResults paper, BenchmarkResults rl) throws IOException {
        for (var entry : List.of(
                new Object[]{ paper.norepS, "NOREP", ChartColors.NOREP, "norep_simple" },
                new Object[]{ paper.tcdrmS, "TCDRM", ChartColors.TCDRM, "tcdrm_simple" },
                new Object[]{ rl.qlS, "Q-Learning", ChartColors.QLEARNING, "qlearning_simple" },
                new Object[]{ rl.dqnS, "DQN", ChartColors.DQN, "dqn_simple" })) {
            SingleModelMetricsPlotter.generateMetricsPlot(
                (BenchmarkDataPerQuery) entry[0], (String) entry[1], (Color) entry[2],
                "images/tcdrm_metrics_" + entry[3] + ".png");
        }
    }
    
    /** Container for benchmark results */
    private record BenchmarkResults(
        BenchmarkDataPerQuery norepS, BenchmarkDataPerQuery tcdrmS,
        BenchmarkDataPerQuery norepC, BenchmarkDataPerQuery tcdrmC,
        BenchmarkDataPerQuery qlS, BenchmarkDataPerQuery dqnS,
        BenchmarkDataPerQuery qlC, BenchmarkDataPerQuery dqnC
    ) {}
}
