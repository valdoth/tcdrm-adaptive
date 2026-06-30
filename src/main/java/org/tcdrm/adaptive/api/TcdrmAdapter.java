package org.tcdrm.adaptive.api;

import org.tcdrm.adaptive.TcdrmMain;
import org.tcdrm.adaptive.benchmark.BenchmarkData;
import org.tcdrm.adaptive.benchmark.BenchmarkExporter;
import org.tcdrm.adaptive.benchmark.BenchmarkRunner;
import org.tcdrm.adaptive.benchmark.ChartGenerator;
import org.tcdrm.adaptive.core.RuntimeConfig;
import org.tcdrm.adaptive.gateway.Py4JGateway;
import org.tcdrm.adaptive.rl.PythonRLBridge;

import java.io.File;
import java.io.IOException;

/**
 * Public adapter API for invoking TCDRM-ADAPTIVE from external projects
 * (e.g., cloudsim-multicloud-implementation) using the shaded JAR.
 *
 * Outputs are written to the host process working directory:
 * - images/*.png
 * - metrics/*.csv
 */
public final class TcdrmAdapter {

    private TcdrmAdapter() {}

    // =========================
    // Configuration
    // =========================

    /** Sets the execution region for the compute/join site (e.g., "EU", "US", "AS"). */
    public static void setExecRegion(String region) { RuntimeConfig.setExecRegion(region); }

    // Removed: setPopularityStrategy(...) — deprecated public API

    /** Reset all runtime configuration to defaults. */
    public static void resetConfig() { RuntimeConfig.reset(); }
    /** Override number of queries for the next runs. */
    public static void setMaxQueries(int queries) { RuntimeConfig.setMaxQueries(queries); }

    // =========================
    // High-level runners
    // =========================

    // Compatibility helpers (example-style API)
    /** Initialize/Reset simulation runtime configuration (compat helper). */
    public static void initSimulation() { RuntimeConfig.reset(); }
    // Removed: createArchitecture(String) — deprecated public API

    /**
     * Generates paper-style figures (Phase 1): NoRepLc vs. TCDRM, and writes metrics CSVs.
     */
    public static void runPaperFigures() {
        // Exécution conforme au papier: région d'origine aléatoire par requête
        org.tcdrm.adaptive.core.RuntimeConfig.setExecRegion("RANDOM");
        String[] args = new String[] { "--headless" , "--phase1-only" };
        try { TcdrmMain.main(args); }
        catch (Exception e) { throw new RuntimeException("TCDRM-ADAPTIVE Phase 1 failed", e); }
    }

    /**
     * Runs the RL extension (Phase 2): waits for Python client to connect to Py4J.
     * The Python client can be started from tcdrm_gym via connect_to_java.py.
     */
    public static void runRlFigures(int gatewayTimeoutSec) {
        // Par défaut, activer la randomisation par requête également pour RL si utilisé via cette méthode
        org.tcdrm.adaptive.core.RuntimeConfig.setExecRegion("RANDOM");
        String[] args = new String[] { "--headless", "--rl-only", "--py-timeout", Integer.toString(Math.max(10, gatewayTimeoutSec)) };
        try { TcdrmMain.main(args); }
        catch (Exception e) { throw new RuntimeException("TCDRM-ADAPTIVE Phase 2 failed", e); }
    }

    // =========================
    // RL single-model runners (separated Q-Learning / DQN)
    // =========================

    /** Run Q-Learning on simple workload only. Outputs go to images/ and metrics/. */
    public static void runQlearningSimple(int gatewayTimeoutSec) { runSingleRL("qlearning", false, 1000L, gatewayTimeoutSec); }
    /** Run Q-Learning on complex workload only. */
    public static void runQlearningComplex(int gatewayTimeoutSec) { runSingleRL("qlearning", true, 3000L, gatewayTimeoutSec); }
    /** Run DQN on simple workload only. */
    public static void runRainbowSimple(int gatewayTimeoutSec) { runSingleRL("rainbow", false, 2000L, gatewayTimeoutSec); }
    /** Run DQN on complex workload only. */
    public static void runRainbowComplex(int gatewayTimeoutSec) { runSingleRL("rainbow", true, 4000L, gatewayTimeoutSec); }

    /** Run Q-Learning on simple then complex workloads within a single Python session. */
    public static void runQlearningBoth(int gatewayTimeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try { gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333")); }
        catch (NumberFormatException nfe) { gwPort = 25333; }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, gatewayTimeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");
            if (!bridge.isQLearningReady()) throw new IllegalStateException("Python Q-Learning model not loaded. Aborting run.");

            bridge.resetCounters();
            BenchmarkData qlSimple = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 1000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlSimple, "metrics/rl_qlearning_simple.csv");
                BenchmarkExporter.exportOvertimeAverages(qlSimple, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(qlSimple, "images/metrics_qlearning_simple.png", false);
                ChartGenerator.generatePopularityAnalysis(qlSimple, "images/popularity_qlearning_simple.png", false);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export Q-Learning simple outputs", ioe);
            }

            bridge.resetCounters();
            BenchmarkData qlComplex = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 3000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlComplex, "metrics/rl_qlearning_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(qlComplex, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(qlComplex, "images/metrics_qlearning_complex.png", true);
                ChartGenerator.generatePopularityAnalysis(qlComplex, "images/popularity_qlearning_complex.png", true);
                BenchmarkExporter.exportSummaryCsv(
                    java.util.Arrays.asList(qlSimple, qlComplex),
                    "metrics/summary_qlearning.csv");
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export Q-Learning complex outputs", ioe);
            }

            System.out.println("\n[RL] Q-Learning simple+complex complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    /** Run DQN on simple then complex workloads within a single Python session. */
    public static void runRainbowBoth(int gatewayTimeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try { gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333")); }
        catch (NumberFormatException nfe) { gwPort = 25333; }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, gatewayTimeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");
            if (!bridge.isRainbowReady()) throw new IllegalStateException("Python Rainbow DQN model not loaded. Aborting run.");

            bridge.resetCounters();
            BenchmarkData rainbowSimple = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Simple", false, 2000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(rainbowSimple, "metrics/rl_rainbow_simple.csv");
                BenchmarkExporter.exportOvertimeAverages(rainbowSimple, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(rainbowSimple, "images/metrics_rainbow_simple.png", false);
                ChartGenerator.generatePopularityAnalysis(rainbowSimple, "images/popularity_rainbow_simple.png", false);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export Rainbow DQN simple outputs", ioe);
            }

            bridge.resetCounters();
            BenchmarkData rainbowComplex = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Complex", true, 4000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(rainbowComplex, "metrics/rl_rainbow_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(rainbowComplex, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(rainbowComplex, "images/metrics_rainbow_complex.png", true);
                ChartGenerator.generatePopularityAnalysis(rainbowComplex, "images/popularity_rainbow_complex.png", true);
                BenchmarkExporter.exportSummaryCsv(
                    java.util.Arrays.asList(rainbowSimple, rainbowComplex),
                    "metrics/summary_rainbow.csv");
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export Rainbow DQN complex outputs", ioe);
            }

            System.out.println("\n[RL] Rainbow DQN simple+complex complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    private static void runSingleRL(String model, boolean complex, long seed, int timeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try {
            gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333"));
        } catch (NumberFormatException nfe) {
            gwPort = 25333;
        }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, timeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");

            bridge.resetCounters();
            if ("qlearning".equals(model) && !bridge.isQLearningReady()) {
                throw new IllegalStateException("Python Q-Learning model not loaded. Aborting run.");
            }
            if ("rainbow".equals(model) && !bridge.isRainbowReady()) {
                throw new IllegalStateException("Python Rainbow DQN model not loaded. Aborting run.");
            }
            String name = ("qlearning".equals(model) ? "QLearning_" : "Rainbow_") + (complex ? "Complex" : "Simple");
            BenchmarkData data = BenchmarkRunner.runRL(bridge, model, name, complex, seed);

            // Export CSV metrics
            String csvName = "metrics/rl_" + ("qlearning".equals(model) ? "qlearning" : "rainbow") + (complex ? "_complex.csv" : "_simple.csv");
            try {
                BenchmarkExporter.exportPerQueryCsv(data, csvName);
                BenchmarkExporter.exportOvertimeAverages(data, "metrics/log_overtime.csv", 100);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export CSV metrics", ioe);
            }

            // Export per-model figures only (no combined charts)
            String metricsPng = "images/metrics_" + ("qlearning".equals(model) ? "qlearning" : "rainbow") + (complex ? "_complex.png" : "_simple.png");
            String popPng = "images/popularity_" + ("qlearning".equals(model) ? "qlearning" : "rainbow") + (complex ? "_complex.png" : "_simple.png");
            try {
                ChartGenerator.generateModelMetrics(data, metricsPng, complex);
                ChartGenerator.generatePopularityAnalysis(data, popPng, complex);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to generate charts", ioe);
            }

            System.out.println("\n[RL] " + name + " complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    // === Two-model comparison helpers ===
    public static void runQlearningVsRainbowSimple(int gatewayTimeoutSec) { runTwoModels(false, gatewayTimeoutSec); }
    public static void runQlearningVsRainbowComplex(int gatewayTimeoutSec) { runTwoModels(true, gatewayTimeoutSec); }

    /** Run Q-Learning vs DQN for simple then complex workloads within a single Python session. */
    public static void runQlearningVsRainbowBoth(int gatewayTimeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try { gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333")); }
        catch (NumberFormatException nfe) { gwPort = 25333; }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, gatewayTimeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");

            // Simple
            bridge.resetCounters();
            if (!bridge.isQLearningReady()) throw new IllegalStateException("Python Q-Learning model not loaded.");
            BenchmarkData qlSimple = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 1000L);
            bridge.resetCounters();
            if (!bridge.isRainbowReady()) throw new IllegalStateException("Python Rainbow DQN model not loaded.");
            BenchmarkData rainbowSimple = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Simple", false, 2000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlSimple, "metrics/rl_qlearning_simple.csv");
                BenchmarkExporter.exportPerQueryCsv(rainbowSimple, "metrics/rl_rainbow_simple.csv");
                BenchmarkExporter.exportOvertimeAverages(qlSimple, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(rainbowSimple, "metrics/log_overtime.csv", 100);
                double tSlaS = org.tcdrm.adaptive.core.TcdrmConstants.TSLA_SIMPLE_MS;
                ChartGenerator.generateReplicaFactor2Models(qlSimple, rainbowSimple, "images/rl2_replica_factor_simple.png");
                ChartGenerator.generateResponseTime2Models(qlSimple, rainbowSimple, tSlaS, "images/rl2_response_time_simple.png");
                ChartGenerator.generateAvgBwPrice2Models(qlSimple, rainbowSimple, "images/rl2_avg_bw_price_simple.png");
                ChartGenerator.generateCumulativeBwPrice2Models(qlSimple, rainbowSimple, "images/rl2_cumulative_bw_price_simple.png");
                ChartGenerator.generateBwConsumption2Models(qlSimple, rainbowSimple, "images/rl2_bw_consumption_simple.png");
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export simple comparison outputs", ioe);
            }

            // Complex
            bridge.resetCounters();
            BenchmarkData qlComplex = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 3000L);
            bridge.resetCounters();
            BenchmarkData rainbowComplex = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_Complex", true, 4000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlComplex, "metrics/rl_qlearning_complex.csv");
                BenchmarkExporter.exportPerQueryCsv(rainbowComplex, "metrics/rl_rainbow_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(qlComplex, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(rainbowComplex, "metrics/log_overtime.csv", 100);
                double tSlaC = org.tcdrm.adaptive.core.TcdrmConstants.TSLA_COMPLEX_MS;
                ChartGenerator.generateReplicaFactor2Models(qlComplex, rainbowComplex, "images/rl2_replica_factor_complex.png");
                ChartGenerator.generateResponseTime2Models(qlComplex, rainbowComplex, tSlaC, "images/rl2_response_time_complex.png");
                ChartGenerator.generateAvgBwPrice2Models(qlComplex, rainbowComplex, "images/rl2_avg_bw_price_complex.png");
                ChartGenerator.generateCumulativeBwPrice2Models(qlComplex, rainbowComplex, "images/rl2_cumulative_bw_price_complex.png");
                ChartGenerator.generateBwConsumption2Models(qlComplex, rainbowComplex, "images/rl2_bw_consumption_complex.png");
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export complex comparison outputs", ioe);
            }

            System.out.println("\n[RL] Two-model comparison complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    private static void runTwoModels(boolean complex, int timeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try { gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333")); }
        catch (NumberFormatException nfe) { gwPort = 25333; }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, timeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");

            bridge.resetCounters();
            if (!bridge.isQLearningReady()) {
                throw new IllegalStateException("Python Q-Learning model not loaded. Aborting two-model run.");
            }
            BenchmarkData ql = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_" + (complex?"Complex":"Simple"), complex, complex?3000L:1000L);

            bridge.resetCounters();
            if (!bridge.isRainbowReady()) {
                throw new IllegalStateException("Python Rainbow DQN model not loaded. Aborting two-model run.");
            }
            BenchmarkData rainbow = BenchmarkRunner.runRL(bridge, "rainbow", "Rainbow_" + (complex?"Complex":"Simple"), complex, complex?4000L:2000L);

            // Export CSVs
            try {
                BenchmarkExporter.exportPerQueryCsv(ql, "metrics/rl_qlearning_" + (complex?"complex":"simple") + ".csv");
                BenchmarkExporter.exportPerQueryCsv(rainbow, "metrics/rl_rainbow_" + (complex?"complex":"simple") + ".csv");
                BenchmarkExporter.exportOvertimeAverages(ql, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(rainbow, "metrics/log_overtime.csv", 100);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export CSV metrics", ioe);
            }

            double tSla = complex ? org.tcdrm.adaptive.core.TcdrmConstants.TSLA_COMPLEX_MS : org.tcdrm.adaptive.core.TcdrmConstants.TSLA_SIMPLE_MS;
            // Two-model comparisons (methods handle IO internally)
            ChartGenerator.generateReplicaFactor2Models(ql, rainbow, "images/rl2_replica_factor_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateResponseTime2Models(ql, rainbow, tSla, "images/rl2_response_time_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateAvgBwPrice2Models(ql, rainbow, "images/rl2_avg_bw_price_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateCumulativeBwPrice2Models(ql, rainbow, "images/rl2_cumulative_bw_price_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateBwConsumption2Models(ql, rainbow, "images/rl2_bw_consumption_" + (complex?"complex":"simple") + ".png");

            System.out.println("\n[RL] Two-model comparison complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    /**
     * Full 4-model comparison (NoRepLc + TCDRM + Q-Learning + DQN) for both simple and complex
     * workloads. Generates all paper-style figures and the summary CSV.
     *
     * Run sequence:
     *   1. NoRepLc and TCDRM baselines (no Python needed)
     *   2. Wait for Python, then run Q-Learning and DQN online
     *   3. Generate all 4-model figures and summary_phase2_rl.csv
     */
    public static void runAllFourModels(int gatewayTimeoutSec) {
        System.setProperty("java.awt.headless", "true");
        new File("images").mkdirs();
        new File("metrics").mkdirs();

        System.out.println("\n[4-model] Running NoRepLc and TCDRM baselines...");
        BenchmarkData norepSimple  = BenchmarkRunner.runNoRep(1000L, false, "NoRepLc_Simple");
        BenchmarkData tcdrmSimple  = BenchmarkRunner.runTcdrm(1000L, false, "TCDRM_Simple");
        BenchmarkData norepComplex = BenchmarkRunner.runNoRep(3000L, true,  "NoRepLc_Complex");
        BenchmarkData tcdrmComplex = BenchmarkRunner.runTcdrm(3000L, true,  "TCDRM_Complex");

        try {
            BenchmarkExporter.exportPerQueryCsv(norepSimple,  "metrics/baseline_norep_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(tcdrmSimple,  "metrics/baseline_tcdrm_simple.csv");
            BenchmarkExporter.exportPerQueryCsv(norepComplex, "metrics/baseline_norep_complex.csv");
            BenchmarkExporter.exportPerQueryCsv(tcdrmComplex, "metrics/baseline_tcdrm_complex.csv");
        } catch (java.io.IOException ioe) {
            throw new RuntimeException("Failed to export baseline CSVs", ioe);
        }

        Py4JGateway gateway = new Py4JGateway();
        int gwPort;
        try { gwPort = Integer.parseInt(System.getenv().getOrDefault("TCDRM_PY4J_PORT", "25333")); }
        catch (NumberFormatException nfe) { gwPort = 25333; }
        gateway.start(gwPort);

        try {
            PythonRLBridge bridge = waitForPython(gateway, Math.max(5, gatewayTimeoutSec));
            if (bridge == null) throw new IllegalStateException("Python client not connected within timeout");
            if (!bridge.isQLearningReady()) throw new IllegalStateException("Python Q-Learning model not loaded.");
            if (!bridge.isRainbowReady())       throw new IllegalStateException("Python Rainbow DQN model not loaded.");

            System.out.println("\n[4-model] Running Q-Learning and Rainbow DQN (simple)...");
            bridge.resetCounters();
            BenchmarkData qlSimple  = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Simple", false, 1000L);
            bridge.resetCounters();
            BenchmarkData rainbowSimple = BenchmarkRunner.runRL(bridge, "rainbow",       "Rainbow_Simple",       false, 2000L);

            System.out.println("\n[4-model] Running Q-Learning and Rainbow DQN (complex)...");
            bridge.resetCounters();
            BenchmarkData qlComplex  = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 3000L);
            bridge.resetCounters();
            BenchmarkData rainbowComplex = BenchmarkRunner.runRL(bridge, "rainbow",       "Rainbow_Complex",       true, 4000L);

            try {
                // Per-query CSVs
                BenchmarkExporter.exportPerQueryCsv(qlSimple,   "metrics/rl_qlearning_simple.csv");
                BenchmarkExporter.exportPerQueryCsv(rainbowSimple,  "metrics/rl_rainbow_simple.csv");
                BenchmarkExporter.exportPerQueryCsv(qlComplex,  "metrics/rl_qlearning_complex.csv");
                BenchmarkExporter.exportPerQueryCsv(rainbowComplex, "metrics/rl_rainbow_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(qlSimple,   "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(rainbowSimple,  "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(qlComplex,  "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(rainbowComplex, "metrics/log_overtime.csv", 100);

                // Summary CSV (all 8 runs)
                java.util.List<BenchmarkData> allModels = java.util.Arrays.asList(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex
                );
                BenchmarkExporter.exportSummaryCsv(allModels, "metrics/summary_phase2_rl.csv");

                // 4-model paper figures
                ChartGenerator.generateReplicaFactor4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig1_replica_factor_4models.png");
                ChartGenerator.generateResponseTime4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig2_response_time_4models.png");
                ChartGenerator.generateBwConsumption4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig3_bw_consumption_4models.png");
                ChartGenerator.generateAvgBwPrice4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig4_avg_bw_price_4models.png");
                ChartGenerator.generateCumulativeBwPrice4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig5_cumulative_bw_price_4models.png");
                ChartGenerator.generateTotalCost4Models(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple,
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex,
                    "images/fig6_total_cost_4models.png");

                // Per-workload RL comparison figures (response_time, replicas, cost)
                ChartGenerator.generateRLComparison(
                    norepSimple, tcdrmSimple, qlSimple, rainbowSimple, "images/rl4_simple", false);
                ChartGenerator.generateRLComparison(
                    norepComplex, tcdrmComplex, qlComplex, rainbowComplex, "images/rl4_complex", true);

            } catch (java.io.IOException ioe) {
                throw new RuntimeException("Failed to export 4-model outputs", ioe);
            }

            System.out.println("\n[4-model] Complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    private static PythonRLBridge waitForPython(Py4JGateway gateway, int maxWaitSeconds) {
        System.out.println("  Waiting for Python client (timeout: " + maxWaitSeconds + "s)...");
        int elapsed = 0;
        while (elapsed < maxWaitSeconds) {
            Object b = gateway.getPythonBridge();
            if (b != null) {
                System.out.println("  Python client connected!");
                return (PythonRLBridge) b;
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
            elapsed += 2;
            if (elapsed % 10 == 0) {
                System.out.println("   ... waiting (" + elapsed + "s/" + maxWaitSeconds + "s)");
            }
        }
        return null;
    }

    // Convenience aliases exposing intent
    public static void generateCsvAndChartsPhase1() { runPaperFigures(); }
    public static void generateCsvAndChartsRl(int timeoutSec) { runRlFigures(timeoutSec); }
}
