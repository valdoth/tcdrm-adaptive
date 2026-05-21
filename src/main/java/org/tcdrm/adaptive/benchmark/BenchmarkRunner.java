package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.core.RuntimeConfig;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import org.tcdrm.adaptive.simulation.TcdrmSimulation;

/**
 * Exécute les benchmarks TCDRM avec CloudSimPlus.
 */
public class BenchmarkRunner {

    /**
     * Exécute le benchmark NoRepLc (pas de réplication).
     */
    public static BenchmarkData runNoRep(long seed, boolean complex, String name) {
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);

        double cumulCost = 0;

        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            TcdrmSimulation.QueryResult result = sim.executeNoRepQuery();
            cumulCost += result.totalCost();

            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                0, result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }

        return data;
    }

    /**
     * Exécute le benchmark TCDRM (réplication à seuil fixe P_SLA).
     */
    public static BenchmarkData runTcdrm(long seed, boolean complex, String name) {
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);

        double cumulCost = 0;

        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            TcdrmSimulation.QueryResult result = sim.executeTcdrmQuery();
            cumulCost += result.totalCost();

            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }

        return data;
    }

    /**
     * Exécute le benchmark RL avec apprentissage en ligne.
     * La récompense est alignée avec TrainingEnvironment.calculateReward() pour éviter
     * toute divergence train/eval.
     */
    public static BenchmarkData runRL(PythonRLBridge bridge, String modelType,
                                       String name, boolean complex, long seed) {
        System.out.println("  >>> " + modelType.toUpperCase() + " (" + (complex ? "complex" : "simple") + ") - ADAPTIVE ONLINE...");

        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);

        double cumulCost = 0;
        double lastLatency = 0;
        double lastCost = 0;
        // TSLA adaptatif : utilise la valeur dynamique de la simulation (Axe 4)
        double tSla = sim.getDynamicTSla();
        // CSLA de référence (budget total / nombre de requêtes) — le budget total est statique
        double cSlaRef = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        int lastReplicaCount = 0;

        // Ring buffer for thrash detection (mirrors TrainingEnvironment)
        int[] actionRing = new int[10];
        int ringPos = 0;
        int ringCount = 0;

        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            // CSLA effectif = budget_restant / requêtes_restantes (miroir de TrainingEnvironment)
            int remaining = Math.max(1, maxQ - q);
            double perQueryBudget = sim.getCurrentBudget() / remaining;
            double effectiveCsla = Math.min(cSlaRef, Math.max(perQueryBudget, cSlaRef * 0.1));

            double[] state = sim.buildRLState(lastLatency, lastCost, effectiveCsla);

            // Determine action validity (mirrors TrainingEnvironment.step() / getActionMask())
            int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
            boolean canReplicate = lastReplicaCount < maxReplicas && sim.getCurrentBudget() > 0;
            boolean canDelete = lastReplicaCount > 0;

            // Get action from Python RL bridge (no policy assist override)
            int action;
            if ("qlearning".equals(modelType)) {
                action = bridge.selectActionQLearning(state);
            } else {
                action = bridge.selectActionDQN(state);
            }

            // DELETE silencieux si popularité dépasse le seuil PSLA dynamique de la simulation
            boolean deleteSilentlyBlocked = (action == 2 && canDelete
                && sim.getEmaPopularity() >= sim.getDynamicPSlaLo());
            boolean lastAssignmentSuccess = !((action == 1 && !canReplicate)
                || (action == 2 && !canDelete) || deleteSilentlyBlocked);

            // Execute the query
            TcdrmSimulation.QueryResult result = sim.executeRLQuery(action);
            cumulCost += result.totalCost();

            lastLatency = result.queryTimeMs();
            lastCost = result.totalCost();
            lastReplicaCount = result.replicaCount();

            // Record action for thrash detection then compute reward
            actionRing[ringPos % 10] = action;
            ringPos++;
            if (ringCount < 10) ringCount++;

            double reward = calculateReward(result, tSla, effectiveCsla, action,
                lastAssignmentSuccess, complex, actionRing, ringCount);
            // Next-state CSLA (budget already decremented by executeRLQuery)
            int remainingNext = Math.max(1, maxQ - q - 1);
            double effectiveCslaNext = Math.min(cSlaRef,
                Math.max(sim.getCurrentBudget() / remainingNext, cSlaRef * 0.1));
            double[] nextState = sim.buildRLState(lastLatency, lastCost, effectiveCslaNext);
            boolean done = (q == maxQ - 1);

            if ("qlearning".equals(modelType)) {
                bridge.updateQLearning(reward, nextState, done);
            } else {
                bridge.updateDQN(reward, nextState, done);
            }

            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }

        System.out.println("      " + modelType.toUpperCase() + " terminé (adaptive online)");
        return data;
    }

    /**
     * Reward aligned with TrainingEnvironment.calculateReward() (DQN_TCDRM_v2.md spec):
     *   R = r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH
     */
    private static double calculateReward(TcdrmSimulation.QueryResult result, double tSla, double cSla,
                                           int action, boolean lastAssignmentSuccess, boolean complex,
                                           int[] actionRing, int ringCount) {
        double latency = result.queryTimeMs();
        int replicas = result.replicaCount();

        double tQ_norm = latency / Math.max(1.0, tSla);
        double cQ_norm = result.totalCost() / Math.max(1e-9, cSla);

        // SLA_OK: reward proportional to how far below tSla (r1=10)
        double rewardWaitTime = 10.0 * Math.max(0.0, 1.0 - tQ_norm);

        // SLA_VIOL: proportional penalty when latency exceeds tSla (r2=20)
        double rewardQueuePenalty = -20.0 * Math.max(0.0, tQ_norm - 1.0);

        // COST_OVER: penalty when per-query cost exceeds C_SLA (r3=15)
        double costOverPenalty = -15.0 * Math.max(0.0, cQ_norm - 1.0);

        // REPL_COST: actual bandwidth cost of creating a replica, normalized (r4=5)
        double replCostPenalty = 0.0;
        if (action == 1 && lastAssignmentSuccess) {
            double dataGb = TcdrmConstants.queryDataSizeGb(complex);
            replCostPenalty = -5.0 * (dataGb * TcdrmConstants.COST_BW_INTER_PROVIDER)
                / Math.max(1.0, TcdrmConstants.INITIAL_BUDGET);
        }

        // THRASH: penalise rapid alternation of REPLICATE/DELETE (r5=8)
        double thrashPenalty = isRingThrashing(actionRing, ringCount) ? -8.0 : 0.0;

        // Light maintenance cost per active replica (encourages parsimony)
        double rewardUnutilization = -0.05 * replicas;

        // Invalid action signal
        double rewardInvalidAction = lastAssignmentSuccess ? 0.0 : -2.0;

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty
            + replCostPenalty + thrashPenalty + rewardUnutilization + rewardInvalidAction;
    }

    private static boolean isRingThrashing(int[] ring, int count) {
        if (count < 6) return false;
        int replicates = 0, deletes = 0;
        for (int a : ring) {
            if (a == 1) replicates++;
            else if (a == 2) deletes++;
        }
        return replicates >= 2 && deletes >= 2;
    }
}
