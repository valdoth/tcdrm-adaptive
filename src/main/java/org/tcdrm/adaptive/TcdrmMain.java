package org.tcdrm.adaptive;

import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonRLBridge;

import java.io.File;
import java.io.IOException;

/**
 * TCDRM-ADAPTIVE Main Entry Point.
 * 
 * Utilise CloudSimPlus pour la simulation multi-cloud.
 * Génère les graphiques du paper (Phase 1) et les extensions RL (Phase 2).
 */
public class TcdrmMain {
    
    private static Py4JGateway gateway;

    public static void main(String[] args) throws IOException, InterruptedException {
        printHeader();
        new File("images").mkdirs();

        // Phase 1: Paper reproduction (TCDRM vs NoRepLc)
        System.out.println("\n━━━ PHASE 1: Paper Figures (TCDRM vs NoRepLc) ━━━");
        
        BenchmarkData norepSimple = BenchmarkRunner.runNoRep(42L, false, "NoRepLc_Simple");
        BenchmarkData tcdrmSimple = BenchmarkRunner.runTcdrm(42L, false, "TCDRM_Simple");
        BenchmarkData norepComplex = BenchmarkRunner.runNoRep(42L, true, "NoRepLc_Complex");
        BenchmarkData tcdrmComplex = BenchmarkRunner.runTcdrm(42L, true, "TCDRM_Complex");
        
        norepSimple.printSummary();
        tcdrmSimple.printSummary();
        norepComplex.printSummary();
        tcdrmComplex.printSummary();
        
        // Générer les graphiques du paper (Figs 2-7)
        System.out.println("\n  Generating paper figures...");
        ChartGenerator.generateReplicaFactor(tcdrmSimple, tcdrmComplex, "images/fig2_replica_factor.png");
        ChartGenerator.generateResponseTime(norepSimple, tcdrmSimple, norepComplex, tcdrmComplex, 
            "images/fig3_response_time.png");
        ChartGenerator.generateBwConsumption(norepSimple, tcdrmSimple, norepComplex, tcdrmComplex,
            "images/fig4_bw_consumption.png");
        ChartGenerator.generateAvgBwPrice(norepSimple, tcdrmSimple, norepComplex, tcdrmComplex,
            "images/fig5_avg_bw_price.png");
        ChartGenerator.generateCumulativeBwPrice(norepSimple, tcdrmSimple, norepComplex, tcdrmComplex,
            "images/fig6_cumulative_cost.png");
        ChartGenerator.generateTotalCost(norepSimple, tcdrmSimple, norepComplex, tcdrmComplex,
            "images/fig7_total_cost.png");
        
        // Métriques détaillées par modèle (NoRep, TCDRM)
        System.out.println("\n  Generating model metrics...");
        ChartGenerator.generateModelMetrics(norepSimple, "images/metrics_norep_simple.png", false);
        ChartGenerator.generateModelMetrics(tcdrmSimple, "images/metrics_tcdrm_simple.png", false);
        
        // Analyse de la popularité (NoRep, TCDRM)
        System.out.println("\n  Generating popularity analysis...");
        ChartGenerator.generatePopularityAnalysis(norepSimple, "images/popularity_norep_simple.png", false);
        ChartGenerator.generatePopularityAnalysis(tcdrmSimple, "images/popularity_tcdrm_simple.png", false);
        
        System.out.println("\n✅ Phase 1 complete: Paper figures generated (Figs 2-7 + Metrics + Popularity)");

        // Phase 2: RL extensions
        System.out.println("\n━━━ PHASE 2: RL Extensions (Q-Learning + DQN) ━━━");
        
        gateway = new Py4JGateway();
        gateway.start();
        
        PythonRLBridge bridge = waitForPythonConnection();
        if (bridge != null) {
            bridge.resetCounters();
            BenchmarkData qlSimple = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 1000L);

            bridge.resetCounters();
            BenchmarkData dqnSimple = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Simple", false, 2000L);

            bridge.resetCounters();
            BenchmarkData qlComplex = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 3000L);

            bridge.resetCounters();
            BenchmarkData dqnComplex = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Complex", true, 4000L);
            
            qlSimple.printSummary();
            dqnSimple.printSummary();
            qlComplex.printSummary();
            dqnComplex.printSummary();
            
            // Générer les graphiques avec 4 modèles (format PDF: Simple + Complex côte à côte)
            System.out.println("\n  Generating 4-model comparison figures (PDF format)...");
            
            // Fig 1: Replica Factor (4 models)
            ChartGenerator.generateReplicaFactor4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig1_replica_factor_4models.png");
            
            // Fig 2: Response Time (4 models)
            ChartGenerator.generateResponseTime4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig2_response_time_4models.png");
            
            // Fig 3: BW Consumption (4 models)
            ChartGenerator.generateBwConsumption4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig3_bw_consumption_4models.png");
            
            // Fig 4: Avg BW Price (4 models)
            ChartGenerator.generateAvgBwPrice4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig4_avg_bw_price_4models.png");
            
            // Fig 5: Cumulative BW Price (4 models)
            ChartGenerator.generateCumulativeBwPrice4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig5_cumulative_bw_price_4models.png");
            
            // Fig 6: Total Cost (4 models)
            ChartGenerator.generateTotalCost4Models(
                norepSimple, tcdrmSimple, qlSimple, dqnSimple,
                norepComplex, tcdrmComplex, qlComplex, dqnComplex,
                "images/fig6_total_cost_4models.png");
            
            // Métriques détaillées RL (Q-Learning, DQN)
            System.out.println("\n  Generating RL model metrics...");
            ChartGenerator.generateModelMetrics(qlSimple, "images/metrics_qlearning_simple.png", false);
            ChartGenerator.generateModelMetrics(dqnSimple, "images/metrics_dqn_simple.png", false);
            
            // Analyse de la popularité RL
            System.out.println("\n  Generating RL popularity analysis...");
            ChartGenerator.generatePopularityAnalysis(qlSimple, "images/popularity_qlearning_simple.png", false);
            ChartGenerator.generatePopularityAnalysis(dqnSimple, "images/popularity_dqn_simple.png", false);
            
            System.out.println("\n✅ Phase 2 complete: RL extension graphs generated (RL-2 to RL-7 + Metrics + Popularity)");
        } else {
            System.err.println("  Python client not connected. Phase 2 skipped.");
        }
        
        printFooter();
    }
    
    private static void printHeader() {
        System.out.println("=".repeat(80));
        System.out.println("  TCDRM-ADAPTIVE — CloudSimPlus Simulation");
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
}
