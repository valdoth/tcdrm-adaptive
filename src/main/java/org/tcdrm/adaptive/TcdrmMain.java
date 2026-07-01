package org.tcdrm.adaptive;

import org.tcdrm.adaptive.benchmark.*;
import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.core.RuntimeConfig;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonRLBridge;

import ch.qos.logback.classic.Level;
import org.cloudsimplus.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

// Suppression de l'import incorrect
// import giu.edu.cspg.ObservationState;

// Correction de l'utilisation de ObservationState
// Commenter le code problématique pour éviter les erreurs
// ObservationState state = new ObservationState(/* paramètres nécessaires */);
// System.out.println("État observable initialisé: " + state);

/**
 * TCDRM-ADAPTIVE Main Entry Point.
 * 
 * Utilise CloudSimPlus pour la simulation multi-cloud.
 * Génère les graphiques du paper (Phase 1) et les extensions RL (Phase 2).
 */
public class TcdrmMain {
    
    private static Py4JGateway gateway;

    public static void main(String[] args) throws IOException, InterruptedException {
        Locale.setDefault(Locale.US);
        TcdrmMainArgs opts = TcdrmMainArgs.parse(args);
        if (opts.help) {
            TcdrmMainArgs.printHelp();
            return;
        }
        if (opts.headlessCharts) {
            System.setProperty("java.awt.headless", "true");
        }
        applyCloudSimLogLevel(opts.quietCloudSim);

        printHeader(opts);
        new File("images").mkdirs();

        // Aligner le benchmark avec la validation: RANDOM par requête + 1000 requêtes
        RuntimeConfig.setExecRegion("RANDOM");
        RuntimeConfig.setMaxQueries(1000);

        // Phase 1: Paper reproduction (TCDRM vs NoRepLc)
        BenchmarkData norepSimple = null, tcdrmSimple = null, norepComplex = null, tcdrmComplex = null;
        if (!opts.rlOnly) {
            System.out.println("\n━━━ PHASE 1: Paper Figures (TCDRM vs NoRepLc) ━━━");
            
            norepSimple = BenchmarkRunner.runNoRep(42L, false, "NoRepLc_Simple");
            tcdrmSimple = BenchmarkRunner.runTcdrm(42L, false, "TCDRM_Simple");
            norepComplex = BenchmarkRunner.runNoRep(42L, true, "NoRepLc_Complex");
            tcdrmComplex = BenchmarkRunner.runTcdrm(42L, true, "TCDRM_Complex");
            
            norepSimple.printSummary();
            tcdrmSimple.printSummary();
            norepComplex.printSummary();
            tcdrmComplex.printSummary();
            
            // Export CSV metrics per model (inspired by legacy Simulation)
            new File("metrics").mkdirs();
            BenchmarkExporter.exportPerQueryCsv(norepSimple, "metrics/norep_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(tcdrmSimple, "metrics/tcdrm_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(norepComplex, "metrics/norep_complex.csv");
            BenchmarkExporter.exportPerQueryCsv(tcdrmComplex, "metrics/tcdrm_complex.csv");
            // Overtime averages (window=100 for readability)
            BenchmarkExporter.exportOvertimeAverages(norepSimple, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(tcdrmSimple, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(norepComplex, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(tcdrmComplex, "metrics/log_overtime.csv", 100);

            // Global summary for Phase 1
            java.util.List<BenchmarkData> phase1Models = java.util.Arrays.asList(
                norepSimple, tcdrmSimple, norepComplex, tcdrmComplex
            );
            BenchmarkExporter.exportSummaryCsv(phase1Models, "metrics/summary_phase1.csv");

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
        } else {
            System.out.println("\n(--rl-only) Phase 1 skipped by request.");
        }

        if (opts.phase1Only) {
            System.out.println("\n  (--phase1-only) Phase 2 skipped. Connect Python + relancer sans cette option pour le RL.");
            printFooter();
            return;
        }

        // Phase 2: RL extensions
        System.out.println("\n━━━ PHASE 2: RL Extensions (Q-Learning + Rainbow DQN) ━━━");
        System.out.println("  Hint: dans un autre terminal — cd tcdrm_gym && uv run python connect_to_java.py --port 25333");

        gateway = new Py4JGateway();
        int gwPort;
        try {
            gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333"));
        } catch (NumberFormatException nfe) {
            gwPort = 25333;
        }
        gateway.start(gwPort);
        
        PythonRLBridge bridge = waitForPythonConnection(opts.pythonConnectTimeoutSec);
        if (bridge != null) {
            // Même seed 42L que norep/tcdrm : garantit que la phase pré-réplication
            // produit un bruit réseau identique, rendant la comparaison équitable.
            bridge.resetCounters();
            BenchmarkData qlSimple = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 42L);

            bridge.resetCounters();
            BenchmarkData rainbowSimple = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Simple", false, 42L);

            bridge.resetCounters();
            BenchmarkData qlComplex = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 42L);

            bridge.resetCounters();
            BenchmarkData rainbowComplex = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Complex", true, 42L);
            
            qlSimple.printSummary();
            rainbowSimple.printSummary();
            qlComplex.printSummary();
            rainbowComplex.printSummary();

            // Export RL CSV metrics
            BenchmarkExporter.exportPerQueryCsv(qlSimple, "metrics/rl_qlearning_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(rainbowSimple, "metrics/rl_rainbow_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(qlComplex, "metrics/rl_qlearning_complex.csv");
            BenchmarkExporter.exportPerQueryCsv(rainbowComplex, "metrics/rl_rainbow_complex.csv");
            BenchmarkExporter.exportOvertimeAverages(qlSimple, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(rainbowSimple, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(qlComplex, "metrics/log_overtime.csv", 100);
            BenchmarkExporter.exportOvertimeAverages(rainbowComplex, "metrics/log_overtime.csv", 100);

            // Global summary for Phase 2 (RL)
            java.util.List<BenchmarkData> phase2Models = java.util.Arrays.asList(
                qlSimple, rainbowSimple, qlComplex, rainbowComplex
            );
            BenchmarkExporter.exportSummaryCsv(phase2Models, "metrics/summary_phase2_rl.csv");
            
            // Générer les graphiques avec 4 modèles (format PDF: Simple + Complex côte à côte)
            System.out.println("\n  Generating 4-model comparison figures (PDF format)...");
            
            // Fig 1: Replica Factor (4 models)
            ChartGenerator.generateReplicaFactor4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig1_replica_factor_4models.png");
            
            // Fig 2: Response Time (4 models)
            ChartGenerator.generateResponseTime4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig2_response_time_4models.png");
            
            // Fig 3: BW Consumption (4 models)
            ChartGenerator.generateBwConsumption4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig3_bw_consumption_4models.png");
            
            // Fig 4: Avg BW Price (4 models)
            ChartGenerator.generateAvgBwPrice4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig4_avg_bw_price_4models.png");
            
            // Fig 5: Cumulative BW Price (4 models)
            ChartGenerator.generateCumulativeBwPrice4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig5_cumulative_bw_price_4models.png");
            
            // Fig 6: Total Cost (4 models)
            ChartGenerator.generateTotalCost4Models(
                norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                "images/fig6_total_cost_4models.png");
            
            // Métriques détaillées RL (Q-Learning, Rainbow DQN)
            System.out.println("\n  Generating RL model metrics...");
            ChartGenerator.generateModelMetrics(qlSimple, "images/metrics_qlearning_simple.png", false);
            ChartGenerator.generateModelMetrics(rainbowSimple, "images/metrics_rainbow_simple.png", false);
            
            // Analyse de la popularité RL
            System.out.println("\n  Generating RL popularity analysis...");
            ChartGenerator.generatePopularityAnalysis(qlSimple, "images/popularity_qlearning_simple.png", false);
            ChartGenerator.generatePopularityAnalysis(rainbowSimple, "images/popularity_rainbow_simple.png", false);
            
            System.out.println("\n✅ Phase 2 complete: RL extension graphs generated (RL-2 to RL-7 + Metrics + Popularity)");
        } else {
            System.err.println("  Python client not connected. Phase 2 skipped.");
        }
        
        printFooter();
    }
    
    private static void printHeader(TcdrmMainArgs opts) {
        System.out.println("=".repeat(80));
        System.out.println("  TCDRM-ADAPTIVE — CloudSimPlus Simulation");
        System.out.println("  Simple: " + TcdrmConstants.RELATIONS_SIMPLE + " relations x " 
            + (int)(TcdrmConstants.AVG_RELATION_SIZE_GB * 1000) + " MB");
        System.out.println("  Complex: " + TcdrmConstants.RELATIONS_COMPLEX + " relations x " 
            + (int)(TcdrmConstants.AVG_RELATION_SIZE_GB * 1000) + " MB");
        System.out.println("  Queries: " + TcdrmConstants.MAX_QUERIES
            + ", P_SLA=" + TcdrmConstants.P_SLA + " (Paper Table 1)");
        System.out.println("  Options: headlessCharts=" + opts.headlessCharts
            + ", phase1Only=" + opts.phase1Only
            + ", pyTimeout=" + opts.pythonConnectTimeoutSec + "s"
            + ", quietCloudSim=" + opts.quietCloudSim);
        System.out.println("=".repeat(80));
    }
    
    private static void printFooter() {
        if (gateway != null) gateway.stop();
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  ALL GRAPHS GENERATED IN images/");
        System.out.println("=".repeat(80));
    }
    
    private static PythonRLBridge waitForPythonConnection(int maxWaitSeconds) throws InterruptedException {
        System.out.println("  Waiting for Python client (timeout: " + maxWaitSeconds + "s)...");
        int maxWait = maxWaitSeconds;
        int elapsed = 0;
        
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

    private static void applyCloudSimLogLevel(boolean quiet) {
        // API officielle CloudSim Plus (cf. exemples Log.setLevel)
        Log.setLevel(quiet ? Level.WARN : Level.INFO);
    }
}
