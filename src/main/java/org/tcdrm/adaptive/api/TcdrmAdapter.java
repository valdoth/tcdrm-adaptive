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
    public static void runDqnSimple(int gatewayTimeoutSec) { runSingleRL("dqn", false, 2000L, gatewayTimeoutSec); }
    /** Run DQN on complex workload only. */
    public static void runDqnComplex(int gatewayTimeoutSec) { runSingleRL("dqn", true, 4000L, gatewayTimeoutSec); }

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
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export Q-Learning complex outputs", ioe);
            }

            System.out.println("\n[RL] Q-Learning simple+complex complete → see images/ and metrics/\n");
        } finally {
            gateway.stop();
        }
    }

    /** Run DQN on simple then complex workloads within a single Python session. */
    public static void runDqnBoth(int gatewayTimeoutSec) {
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
            if (!bridge.isDQNReady()) throw new IllegalStateException("Python DQN model not loaded. Aborting run.");

            bridge.resetCounters();
            BenchmarkData dqnSimple = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Simple", false, 2000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(dqnSimple, "metrics/rl_dqn_simple.csv");
                BenchmarkExporter.exportOvertimeAverages(dqnSimple, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(dqnSimple, "images/metrics_dqn_simple.png", false);
                ChartGenerator.generatePopularityAnalysis(dqnSimple, "images/popularity_dqn_simple.png", false);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export DQN simple outputs", ioe);
            }

            bridge.resetCounters();
            BenchmarkData dqnComplex = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Complex", true, 4000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(dqnComplex, "metrics/rl_dqn_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(dqnComplex, "metrics/log_overtime.csv", 100);
                ChartGenerator.generateModelMetrics(dqnComplex, "images/metrics_dqn_complex.png", true);
                ChartGenerator.generatePopularityAnalysis(dqnComplex, "images/popularity_dqn_complex.png", true);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export DQN complex outputs", ioe);
            }

            System.out.println("\n[RL] DQN simple+complex complete → see images/ and metrics/\n");
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
            if ("dqn".equals(model) && !bridge.isDQNReady()) {
                throw new IllegalStateException("Python DQN model not loaded. Aborting run.");
            }
            String name = ("qlearning".equals(model) ? "QLearning_" : "DQN_") + (complex ? "Complex" : "Simple");
            BenchmarkData data = BenchmarkRunner.runRL(bridge, model, name, complex, seed);

            // Export CSV metrics
            String csvName = "metrics/rl_" + ("qlearning".equals(model) ? "qlearning" : "dqn") + (complex ? "_complex.csv" : "_simple.csv");
            try {
                BenchmarkExporter.exportPerQueryCsv(data, csvName);
                BenchmarkExporter.exportOvertimeAverages(data, "metrics/log_overtime.csv", 100);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export CSV metrics", ioe);
            }

            // Export per-model figures only (no combined charts)
            String metricsPng = "images/metrics_" + ("qlearning".equals(model) ? "qlearning" : "dqn") + (complex ? "_complex.png" : "_simple.png");
            String popPng = "images/popularity_" + ("qlearning".equals(model) ? "qlearning" : "dqn") + (complex ? "_complex.png" : "_simple.png");
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
    public static void runQlearningVsDqnSimple(int gatewayTimeoutSec) { runTwoModels(false, gatewayTimeoutSec); }
    public static void runQlearningVsDqnComplex(int gatewayTimeoutSec) { runTwoModels(true, gatewayTimeoutSec); }

    /** Run Q-Learning vs DQN for simple then complex workloads within a single Python session. */
    public static void runQlearningVsDqnBoth(int gatewayTimeoutSec) {
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
            if (!bridge.isDQNReady()) throw new IllegalStateException("Python DQN model not loaded.");
            BenchmarkData dqnSimple = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Simple", false, 2000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlSimple, "metrics/rl_qlearning_simple.csv");
                BenchmarkExporter.exportPerQueryCsv(dqnSimple, "metrics/rl_dqn_simple.csv");
                BenchmarkExporter.exportOvertimeAverages(qlSimple, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(dqnSimple, "metrics/log_overtime.csv", 100);
                double tSlaS = org.tcdrm.adaptive.core.TcdrmConstants.TSLA_SIMPLE_MS;
                ChartGenerator.generateReplicaFactor2Models(qlSimple, dqnSimple, "images/rl2_replica_factor_simple.png");
                ChartGenerator.generateResponseTime2Models(qlSimple, dqnSimple, tSlaS, "images/rl2_response_time_simple.png");
                ChartGenerator.generateAvgBwPrice2Models(qlSimple, dqnSimple, "images/rl2_avg_bw_price_simple.png");
                ChartGenerator.generateCumulativeBwPrice2Models(qlSimple, dqnSimple, "images/rl2_cumulative_bw_price_simple.png");
                ChartGenerator.generateBwConsumption2Models(qlSimple, dqnSimple, "images/rl2_bw_consumption_simple.png");
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export simple comparison outputs", ioe);
            }

            // Complex
            bridge.resetCounters();
            BenchmarkData qlComplex = BenchmarkRunner.runRL(bridge, "qlearning", "QLearning_Complex", true, 3000L);
            bridge.resetCounters();
            BenchmarkData dqnComplex = BenchmarkRunner.runRL(bridge, "dqn", "DQN_Complex", true, 4000L);
            try {
                BenchmarkExporter.exportPerQueryCsv(qlComplex, "metrics/rl_qlearning_complex.csv");
                BenchmarkExporter.exportPerQueryCsv(dqnComplex, "metrics/rl_dqn_complex.csv");
                BenchmarkExporter.exportOvertimeAverages(qlComplex, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(dqnComplex, "metrics/log_overtime.csv", 100);
                double tSlaC = org.tcdrm.adaptive.core.TcdrmConstants.TSLA_COMPLEX_MS;
                ChartGenerator.generateReplicaFactor2Models(qlComplex, dqnComplex, "images/rl2_replica_factor_complex.png");
                ChartGenerator.generateResponseTime2Models(qlComplex, dqnComplex, tSlaC, "images/rl2_response_time_complex.png");
                ChartGenerator.generateAvgBwPrice2Models(qlComplex, dqnComplex, "images/rl2_avg_bw_price_complex.png");
                ChartGenerator.generateCumulativeBwPrice2Models(qlComplex, dqnComplex, "images/rl2_cumulative_bw_price_complex.png");
                ChartGenerator.generateBwConsumption2Models(qlComplex, dqnComplex, "images/rl2_bw_consumption_complex.png");
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
            if (!bridge.isDQNReady()) {
                throw new IllegalStateException("Python DQN model not loaded. Aborting two-model run.");
            }
            BenchmarkData dqn = BenchmarkRunner.runRL(bridge, "dqn", "DQN_" + (complex?"Complex":"Simple"), complex, complex?4000L:2000L);

            // Export CSVs
            try {
                BenchmarkExporter.exportPerQueryCsv(ql, "metrics/rl_qlearning_" + (complex?"complex":"simple") + ".csv");
                BenchmarkExporter.exportPerQueryCsv(dqn, "metrics/rl_dqn_" + (complex?"complex":"simple") + ".csv");
                BenchmarkExporter.exportOvertimeAverages(ql, "metrics/log_overtime.csv", 100);
                BenchmarkExporter.exportOvertimeAverages(dqn, "metrics/log_overtime.csv", 100);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to export CSV metrics", ioe);
            }

            double tSla = complex ? org.tcdrm.adaptive.core.TcdrmConstants.TSLA_COMPLEX_MS : org.tcdrm.adaptive.core.TcdrmConstants.TSLA_SIMPLE_MS;
            // Two-model comparisons (methods handle IO internally)
            ChartGenerator.generateReplicaFactor2Models(ql, dqn, "images/rl2_replica_factor_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateResponseTime2Models(ql, dqn, tSla, "images/rl2_response_time_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateAvgBwPrice2Models(ql, dqn, "images/rl2_avg_bw_price_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateCumulativeBwPrice2Models(ql, dqn, "images/rl2_cumulative_bw_price_" + (complex?"complex":"simple") + ".png");
            ChartGenerator.generateBwConsumption2Models(ql, dqn, "images/rl2_bw_consumption_" + (complex?"complex":"simple") + ".png");

            System.out.println("\n[RL] Two-model comparison complete → see images/ and metrics/\n");
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
