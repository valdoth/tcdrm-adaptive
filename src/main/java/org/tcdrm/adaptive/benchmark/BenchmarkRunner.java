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
        // CSLA contractuel (budget du locataire, Paper Table 1) — STATIQUE, jamais ajusté.
        double cSlaRef = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        // Seuils adaptatifs (Sujet 1) : méta-contrôleurs Q-learning ENTRAÎNÉS (Q-tables
        // persistées par TrainingEnvironment, rechargées ici). ε=0 : exploitation greedy
        // de la politique apprise + apprentissage online continu pendant le run.
        // Les deux agents (QL/Rainbow) rechargent la même Q-table de départ → comparaison
        // équitable ; les seuils repartent des valeurs CONTRACTUELLES (1.0).
        double contractTSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        org.tcdrm.adaptive.rl.ThresholdMetaLearner popLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile("pop", complex),
                1.0, 0.0, 1.0, TcdrmConstants.META_POPULARITY_STEP, 0.0, seed);
        org.tcdrm.adaptive.rl.ThresholdMetaLearner tslaMetaLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile("tsla", complex),
                1.0, TcdrmConstants.META_TSLA_MIN_MULTIPLIER, 1.0,
                TcdrmConstants.META_TSLA_STEP, 0.0, seed ^ 0x9E3779B9L);
        double dynTSla = contractTSla;
        int windowViol = 0;
        double windowCost = 0.0;
        int windowN = 0;
        int lastReplicaCount = 0;

        // Ring buffer for thrash detection (mirrors TrainingEnvironment)
        int[] actionRing = new int[10];
        int ringPos = 0;
        int ringCount = 0;

        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            // État normalisé par le CSLA contractuel statique (le budget restant est déjà
            // une dimension d'état séparée : state[1]).
            double[] state = sim.buildRLState(lastLatency, lastCost, cSlaRef);

            // Determine action validity (mirrors TrainingEnvironment.step() / getActionMask()).
            // Éligibilité popularité (Paper Algorithm 1) : la réplication n'est possible que
            // sur données populaires — seuil P_SLA adaptatif (Sujet 1), contrat = P_SLA papier.
            // Au-dessus du seuil, l'agent décide librement QUAND/COMBIEN répliquer.
            int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
            boolean popularityEligible = sim.isReplicationAllowed();
            boolean canReplicate = popularityEligible && lastReplicaCount < maxReplicas
                && sim.getCurrentBudget() > 0;
            boolean canDelete = lastReplicaCount > 0;

            // Get action from Python RL bridge
            int action;
            if ("qlearning".equals(modelType)) {
                action = bridge.selectActionQLearning(state);
            } else {
                action = bridge.selectActionRainbow(state);
            }
            // Données non populaires → REPLICATE devient NOOP (contrainte contractuelle,
            // pas une action invalide de l'agent). Également appliqué dans executeRLQuery.
            if (action == 1 && !popularityEligible) action = 0;

            boolean lastAssignmentSuccess = !((action == 1 && !canReplicate)
                || (action == 2 && !canDelete));

            // Execute the query
            TcdrmSimulation.QueryResult result = sim.executeRLQuery(action);
            cumulCost += result.totalCost();

            lastLatency = result.queryTimeMs();
            lastCost = result.totalCost();
            lastReplicaCount = result.replicaCount();

            // Alimente la fenêtre glissante des méta-contrôleurs (Sujet 1) : à chaque fin de
            // fenêtre, les Q-learners observent le stress, apprennent et ajustent les seuils,
            // propagés immédiatement à la simulation (état RL + éligibilité popularité).
            windowN++;
            if (lastLatency > dynTSla) windowViol++;
            windowCost += lastCost;
            if (windowN >= TcdrmConstants.META_WINDOW_QUERIES) {
                double vr = (double) windowViol / windowN;
                double cr = (windowCost / windowN) / Math.max(1e-9, cSlaRef);
                double minPop = popLearner.observeAndAdjust(vr, cr);
                dynTSla = contractTSla * tslaMetaLearner.observeAndAdjust(vr, cr);
                sim.setDynamicThresholds(dynTSla, minPop);
                windowViol = 0; windowCost = 0.0; windowN = 0;
            }
            double tSla = dynTSla;

            // Record action for thrash detection then compute reward
            actionRing[ringPos % 10] = action;
            ringPos++;
            if (ringCount < 10) ringCount++;

            double reward = calculateReward(result, tSla, cSlaRef, action,
                lastAssignmentSuccess, complex, actionRing, ringCount, sim);
            double[] nextState = sim.buildRLState(lastLatency, lastCost, cSlaRef);
            boolean done = (q == maxQ - 1);

            if ("qlearning".equals(modelType)) {
                bridge.updateQLearning(reward, nextState, done);
            } else {
                bridge.updateRainbow(reward, nextState, done);
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
     * Reward mirroring TrainingEnvironment.calculateReward() exactly — prevents train/eval divergence.
     *   R = r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER(budgetUrgency) − r4·REPL_COST
     *       − r5·PREMATURE_REPL + r6·CORRECT_TRIGGER − r7·THRASH − r8·MAINT − r9·INVALID
     */
    private static double calculateReward(TcdrmSimulation.QueryResult result, double tSla, double cSla,
                                           int action, boolean lastAssignmentSuccess, boolean complex,
                                           int[] actionRing, int ringCount,
                                           TcdrmSimulation sim) {
        double latency = result.queryTimeMs();
        int replicas = result.replicaCount();

        double tQ_norm = latency / Math.max(1.0, tSla);
        double cQ_norm = result.totalCost() / Math.max(1e-9, cSla);

        // SLA_OK: reward proportional to how far below tSla (r1=10)
        double rewardWaitTime = 10.0 * Math.max(0.0, 1.0 - tQ_norm);

        // SLA_VIOL: proportional penalty when latency exceeds tSla (r2=20)
        double rewardQueuePenalty = -20.0 * Math.max(0.0, tQ_norm - 1.0);

        // COST_OVER: budget-urgency-scaled penalty (r3=15, urgency 1.0→2.0 as budget depletes)
        double budgetRatio = sim.getCurrentBudget() / TcdrmConstants.INITIAL_BUDGET;
        double budgetUrgency = 1.0 + Math.max(0.0, 1.0 - budgetRatio);
        double costOverPenalty = -15.0 * budgetUrgency * Math.max(0.0, cQ_norm - 1.0);

        // REPL_COST, LOW_POPULARITY, PREMATURE_REPL, CORRECT_TRIGGER
        double popularityScore = sim.getPopularityScore();
        double replCostPenalty = 0.0;
        double lowPopularityPenalty = 0.0;
        double prematureReplPenalty = 0.0;
        double correctTriggerBonus = 0.0;
        if (action == 1 && lastAssignmentSuccess) {
            double dataGb = TcdrmConstants.queryDataSizeGb(complex);
            replCostPenalty = -5.0 * (dataGb * TcdrmConstants.COST_BW_INTER_PROVIDER)
                / Math.max(1.0, TcdrmConstants.INITIAL_BUDGET);

            lowPopularityPenalty = -5.0 * (1.0 - popularityScore);

            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            // After stabilization data is fully popular: no premature penalty so the agent
            // can freely add replicas beyond the first batch without being penalised when
            // latency happens to be below T_SLA.
            double effectiveMargin = sim.isWorkloadStabilized()
                ? 0.0
                : Math.max(slaMargin, 1.0 - popularityScore);
            prematureReplPenalty = -5.0 * effectiveMargin;

            // CORRECT_TRIGGER continu : bonus proportionnel à la popularité observée quand
            // l'agent réplique sous violation SLA. Aucun seuil statique — combiné à
            // lowPopularityPenalty (-5·(1-pop)), le point de bascule net est APPRIS par
            // l'agent au lieu d'être imposé par un gate codé en dur.
            boolean slaViolated = latency > tSla || result.totalCost() > cSla;
            if (slaViolated) {
                correctTriggerBonus = 8.0 * popularityScore;
            }
        }

        // PREMATURE_DELETE: symmetric penalty — prevents thrashing by penalising DELETE when SLA OK
        double prematureDeletePenalty = 0.0;
        if (action == 2 && lastAssignmentSuccess) {
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            prematureDeletePenalty = -5.0 * slaMargin;
        }

        // THRASH: penalise rapid alternation of REPLICATE/DELETE (r5=8)
        double thrashPenalty = isRingThrashing(actionRing, ringCount) ? -8.0 : 0.0;

        // Light maintenance cost per active replica (reduced 0.05→0.01 to match training)
        double rewardUnutilization = -0.01 * replicas;

        // Invalid action signal
        double rewardInvalidAction = lastAssignmentSuccess ? 0.0 : -2.0;

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty
            + replCostPenalty + lowPopularityPenalty + prematureReplPenalty + prematureDeletePenalty
            + correctTriggerBonus + thrashPenalty + rewardUnutilization + rewardInvalidAction;
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
