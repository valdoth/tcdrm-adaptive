package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.core.RuntimeConfig;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import org.tcdrm.adaptive.simulation.TcdrmSimulation;
import org.tcdrm.adaptive.training.TrainingSettings;

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

        // Poids de récompense de CET agent, sauvegardés à l'ENTRAÎNEMENT et rechargés
        // ici : la récompense d'évaluation (apprentissage online) est alignée sur celle
        // de l'entraînement — aucun poids codé en dur, aucune divergence train/eval.
        java.io.File rwFile = TcdrmConstants.rewardConfigFile(modelType);
        TrainingSettings rw = TrainingSettings.loadRewardConfig(rwFile);
        if (!rwFile.isFile()) {
            System.out.println("      ⚠️  " + rwFile.getName()
                + " absent — poids de récompense par défaut (réentraîner pour le générer)");
        }

        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        // Profil de routage persisté à l'entraînement de CET agent (0 = temps seul) :
        // les agents « priorité coût » privilégient les liens inter-région une fois
        // les données migrées.
        sim.setCostAwareRouting(rw.getCostRoutingToleranceMs());
        BenchmarkData data = new BenchmarkData(name);

        double cumulCost = 0;
        double lastLatency = 0;
        double lastCost = 0;
        // CSLA contractuel (budget du locataire, Paper Table 1) — STATIQUE, jamais ajusté.
        double cSlaRef = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        // Seuils adaptatifs (Sujet 1) : méta-contrôleurs Q-learning ENTRAÎNÉS, PAR AGENT —
        // chaque modèle recharge SES Q-tables (apprises pendant son propre entraînement),
        // donc sa propre politique de seuil et son propre moment de déclenchement.
        // ε=0 : exploitation greedy + apprentissage online continu pendant le run.
        // Les seuils repartent des valeurs CONTRACTUELLES (1.0).
        double contractTSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        org.tcdrm.adaptive.rl.ThresholdMetaLearner popLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile(modelType, "pop", complex),
                1.0, 0.0, 1.0, TcdrmConstants.META_POPULARITY_RESOLUTION, 0.0,
                seed ^ modelType.hashCode());
        org.tcdrm.adaptive.rl.ThresholdMetaLearner tslaMetaLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile(modelType, "tsla", complex),
                1.0, TcdrmConstants.META_TSLA_MIN_MULTIPLIER, 1.0,
                TcdrmConstants.META_TSLA_RESOLUTION, 0.0,
                (seed ^ modelType.hashCode()) ^ 0x9E3779B9L);
        org.tcdrm.adaptive.rl.ThresholdMetaLearner deletionWindowLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile(modelType, "delw", complex),
                1.0, TcdrmConstants.META_DELETION_WINDOW_MIN, 1.0,
                TcdrmConstants.META_DELETION_WINDOW_RESOLUTION, 0.0,
                (seed ^ modelType.hashCode()) ^ 0x51ED270CL);
        double dynTSla = contractTSla;
        // Signal de stress lissé (EMA) — observé à CHAQUE requête, aucune cadence fixe :
        // le moment d'un changement de seuil est déterminé par la politique apprise de
        // CET agent (mêmes dynamiques que TrainingEnvironment).
        double violEma = 0.0;
        double costRatioEma = 0.0;
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

            // Get action from Python RL bridge — le masque de validité (éligibilité
            // popularité + limites physiques) est transmis pour présenter à l'agent le
            // même espace d'actions qu'à l'entraînement (TrainingEnvironment.getActionMask).
            int action;
            if ("qlearning".equals(modelType)) {
                action = bridge.selectActionQLearning(state, canReplicate, canDelete);
            } else {
                action = bridge.selectActionRainbow(state, canReplicate, canDelete);
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

            // Méta-adaptation continue (Sujet 1) : à CHAQUE requête, les Q-learners
            // observent le stress lissé (EMA), apprennent et choisissent les seuils,
            // propagés immédiatement à la simulation (état RL + éligibilité popularité).
            // Aucune cadence fixe — le moment du déclenchement est appris par agent.
            double alpha = TcdrmConstants.META_EMA_ALPHA;
            violEma = (1.0 - alpha) * violEma + alpha * (lastLatency > dynTSla ? 1.0 : 0.0);
            costRatioEma = (1.0 - alpha) * costRatioEma
                + alpha * (lastCost / Math.max(1e-9, cSlaRef));
            double minPop = popLearner.observeAndAdjust(violEma, costRatioEma);
            dynTSla = contractTSla * tslaMetaLearner.observeAndAdjust(violEma, costRatioEma);
            sim.setDynamicThresholds(dynTSla, minPop);
            sim.setDynamicDeletionWindow(deletionWindowLearner.observeAndAdjust(violEma, costRatioEma));
            double tSla = dynTSla;

            // Record action for thrash detection then compute reward
            actionRing[ringPos % 10] = action;
            ringPos++;
            if (ringCount < 10) ringCount++;

            double reward = calculateReward(result, tSla, cSlaRef, action,
                lastAssignmentSuccess, complex, actionRing, ringCount, sim, rw);
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
     * Les poids proviennent de la configuration PERSISTÉE À L'ENTRAÎNEMENT de cet agent
     * ({@link TrainingSettings#loadRewardConfig}) — aucun poids codé en dur ici.
     *   R = r_slaOk·SLA_OK − r_slaViol·SLA_VIOL − r_costOver·COST_OVER(budgetUrgency)
     *       − r_replCost·REPL_COST − r_premature·PREMATURE_REPL + r_correct·CORRECT_TRIGGER
     *       − r_thrash·THRASH − r_maint·MAINT − r_invalid·INVALID
     */
    private static double calculateReward(TcdrmSimulation.QueryResult result, double tSla, double cSla,
                                           int action, boolean lastAssignmentSuccess, boolean complex,
                                           int[] actionRing, int ringCount,
                                           TcdrmSimulation sim, TrainingSettings rw) {
        double latency = result.queryTimeMs();
        int replicas = result.replicaCount();

        double tQ_norm = latency / Math.max(1.0, tSla);
        double cQ_norm = result.totalCost() / Math.max(1e-9, cSla);

        // SLA_OK: satisficing — full reward once comfortably below tSla ((1−margin)·tSla),
        // no extra for going lower (mirror of TrainingEnvironment). Removes the
        // over-provisioning incentive so the agent minimises replica count and cost.
        double slaMarginNorm = Math.max(1e-6, rw.getSlaSatisfyMargin());
        double slaOkFactor = Math.max(0.0, Math.min(1.0, (1.0 - tQ_norm) / slaMarginNorm));
        double rewardWaitTime = rw.getRewardSlaOk() * slaOkFactor;

        // SLA_VIOL: proportional penalty when latency exceeds tSla
        double rewardQueuePenalty = -rw.getRewardSlaViol() * Math.max(0.0, tQ_norm - 1.0);

        // COST_OVER: budget-urgency-scaled penalty (urgency 1.0→2.0 as budget depletes)
        double budgetRatio = sim.getCurrentBudget() / TcdrmConstants.INITIAL_BUDGET;
        double budgetUrgency = 1.0 + Math.max(0.0, 1.0 - budgetRatio);
        double costOverPenalty = -rw.getRewardCostOver() * budgetUrgency * Math.max(0.0, cQ_norm - 1.0);

        // COST_LINEAR (miroir de TrainingEnvironment) : coût réel de chaque requête,
        // même sous C_SLA — sensibilité continue au prix de la bande passante.
        double costLinearPenalty = -rw.getRewardCostLinear() * cQ_norm;

        // REPL_COST, LOW_POPULARITY, PREMATURE_REPL, CORRECT_TRIGGER
        double popularityScore = sim.getPopularityScore();
        double replCostPenalty = 0.0;
        double lowPopularityPenalty = 0.0;
        double prematureReplPenalty = 0.0;
        double correctTriggerBonus = 0.0;
        if (action == 1 && lastAssignmentSuccess) {
            double dataGb = TcdrmConstants.queryDataSizeGb(complex);
            replCostPenalty = -rw.getRewardReplCost() * (dataGb * TcdrmConstants.COST_BW_INTER_PROVIDER)
                / Math.max(1.0, TcdrmConstants.INITIAL_BUDGET);

            lowPopularityPenalty = -rw.getRewardLowPopularity() * (1.0 - popularityScore);

            // PREMATURE_REPL = slaMargin seul (miroir de TrainingEnvironment) : pénalise
            // la réplication sans besoin (SLA confortable). La faible popularité est déjà
            // pénalisée par LOW_POPULARITY + le coût de détention — l'ancien
            // max(slaMargin, 1−pop) doublait la pénalité et enseignait la réplication
            // tardive (bascule nette à pop 0.56).
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            prematureReplPenalty = -rw.getRewardPrematureRepl() * slaMargin;

            // CORRECT_TRIGGER continu : bonus proportionnel à la popularité observée quand
            // l'agent réplique sous violation SLA. Aucun seuil statique — combiné à
            // lowPopularityPenalty, le point de bascule net est APPRIS par
            // l'agent au lieu d'être imposé par un gate codé en dur.
            boolean slaViolated = latency > tSla || result.totalCost() > cSla;
            if (slaViolated) {
                // Utilité marginale décroissante (miroir de TrainingEnvironment) :
                // bonus plein pour les premières réplications, décroissant avec le
                // remplissage — l'agent s'arrête près de l'optimum au lieu de saturer.
                double fillBefore = Math.max(0, replicas - 1)
                    / (double) TcdrmConstants.maxReplicasForQueryType(complex);
                correctTriggerBonus = rw.getRewardCorrectTrigger() * popularityScore
                    * (1.0 - fillBefore);
            }
        }

        // PREMATURE_DELETE: symmetric penalty — prevents thrashing by penalising DELETE when SLA OK
        double prematureDeletePenalty = 0.0;
        if (action == 2 && lastAssignmentSuccess) {
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            prematureDeletePenalty = -rw.getRewardPrematureDelete() * slaMargin;
        }

        // THRASH: penalise rapid alternation of REPLICATE/DELETE
        double thrashPenalty = isRingThrashing(actionRing, ringCount) ? -rw.getRewardThrash() : 0.0;

        // Light maintenance cost per active replica (aligned with training config)
        double rewardUnutilization = -rw.getRewardMaintenance() * replicas;

        // DÉTENTION IMPOPULAIRE (miroir de TrainingEnvironment) : coût récurrent
        // Σ (1−pop_f)×réplicas_f — chaque réplica évalué sur la popularité de SA donnée.
        double unpopularHoldingPenalty =
            -rw.getRewardUnpopularHolding() * sim.getUnpopularReplicaLoad();

        // Invalid action signal
        double rewardInvalidAction = lastAssignmentSuccess ? 0.0 : -rw.getRewardInvalid();

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty + costLinearPenalty
            + replCostPenalty + lowPopularityPenalty + prematureReplPenalty + prematureDeletePenalty
            + correctTriggerBonus + thrashPenalty + rewardUnutilization + unpopularHoldingPenalty
            + rewardInvalidAction;
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
