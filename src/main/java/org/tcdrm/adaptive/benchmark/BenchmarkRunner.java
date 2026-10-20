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

        data.setReplicaStats(sim.getReplicaCreations(), sim.getReplicaDeletions(),
            sim.getThrashEvents(), sim.getUnusedReplicaCost());
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
                q, result.queryTimeMs(),
                result.bwCost() - result.replicaCreationCost(),   // BW pur (transfert), création exclue
                cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(),
                result.replicaCreationCost() + result.replicaMaintenanceCost(), // coût réplica = création + maintenance
                tSla
            );
        }

        data.setReplicaStats(sim.getReplicaCreations(), sim.getReplicaDeletions(),
            sim.getThrashEvents(), sim.getUnusedReplicaCost());
        return data;
    }

    /**
     * Référence GLOUTONNE : mêmes règles que les agents (plancher de popularité, seuils
     * à leur valeur initiale, même optimiseur de placement), sans apprentissage —
     * REPLICATE dès que c'est possible, sinon DELETE dès que c'est possible, sinon NOOP.
     * Mesure ce que les agents apportent au-delà du masque d'actions.
     */
    public static BenchmarkData runGreedy(long seed, boolean complex, String name) {
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        sim.setDynamicThresholds(tSla, 1.0);
        BenchmarkData data = new BenchmarkData(name);

        double cumulCost = 0;
        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            int action = sim.canCreateReplica() ? 1 : (sim.canDeleteReplica() ? 2 : 0);
            TcdrmSimulation.QueryResult result = sim.executeRLQuery(action);
            cumulCost += result.totalCost();
            data.addQueryResult(
                q, result.queryTimeMs(),
                result.bwCost() - result.replicaCreationCost(),   // BW pur (transfert), création exclue
                cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(),
                result.replicaCreationCost() + result.replicaMaintenanceCost(), // coût réplica = création + maintenance
                tSla
            );
        }

        data.setReplicaStats(sim.getReplicaCreations(), sim.getReplicaDeletions(),
            sim.getThrashEvents(), sim.getUnusedReplicaCost());
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
        // CSLA contractuel (budget du locataire, Paper Table 1) — jamais dépassé : signal de
        // stress et comptage des violations. Le C_SLA INTERNE (état, récompense) est appris
        // sous ce contrat par un méta-contrôleur (Sujet 1, axe 4).
        double cSlaRef = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        // TCDRM_EVAL_ONLINE=0 : politique FIGÉE (audit 4.4) — ni l'agent (côté Python) ni
        // les méta-contrôleurs ne mettent à jour leur politique pendant l'évaluation.
        boolean online = !"0".equals(System.getenv("TCDRM_EVAL_ONLINE"));
        // TCDRM_ABLATION_NO_META=1 : ablation (audit 5.6) — seuils fixés à leur valeur
        // initiale (contrat ; ρ = 1 pour la popularité), méta-contrôleurs ignorés ;
        // isole leur part dans le gain des agents.
        boolean noMeta = "1".equals(System.getenv("TCDRM_ABLATION_NO_META"));
        // Seuils adaptatifs (Sujet 1) : méta-contrôleurs Q-learning ENTRAÎNÉS, PAR AGENT —
        // chaque modèle recharge SES Q-tables (apprises pendant son propre entraînement),
        // donc sa propre politique de seuil et son propre moment de déclenchement.
        // ε=0 : exploitation greedy + apprentissage online continu pendant le run.
        // Les seuils repartent des valeurs CONTRACTUELLES (1.0). Le seuil relatif de
        // popularité ρ est le niveau de plus faible coût d'épisode appris par le bandit
        // de CET agent à l'entraînement, fixe pendant le run (ρ = 1 sans méta).
        double contractTSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double popRatio = noMeta ? 1.0 : org.tcdrm.adaptive.rl.EpisodeThresholdBandit.loadOrCreate(
                TcdrmConstants.popularityBanditFile(modelType, complex),
                1.0, TcdrmConstants.META_POPULARITY_RATIO_MIN, 1.0,
                TcdrmConstants.META_POPULARITY_RESOLUTION, seed ^ modelType.hashCode()).best();
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
        org.tcdrm.adaptive.rl.ThresholdMetaLearner cslaMetaLearner =
            org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
                TcdrmConstants.metaQtableFile(modelType, "csla", complex),
                1.0, TcdrmConstants.META_CSLA_MIN_MULTIPLIER, 1.0,
                TcdrmConstants.META_CSLA_RESOLUTION, 0.0,
                (seed ^ modelType.hashCode()) ^ 0x3C6EF372L);
        for (org.tcdrm.adaptive.rl.ThresholdMetaLearner m : java.util.List.of(
                tslaMetaLearner, deletionWindowLearner, cslaMetaLearner)) {
            m.setLearning(online);
        }
        double dynTSla = contractTSla;
        double dynCSla = cSlaRef;
        // Seuils initiaux (aussi ceux de l'ablation sans méta-contrôleurs).
        sim.setDynamicThresholds(dynTSla, popRatio);
        // Signal de stress lissé (EMA) — observé à CHAQUE requête, aucune cadence fixe :
        // le moment d'un changement de seuil est déterminé par la politique apprise de
        // CET agent (mêmes dynamiques que TrainingEnvironment).
        double violEma = 0.0;
        double costRatioEma = 0.0;
        double churnEma = 0.0;
        int lastReplicaCount = 0;


        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            // État normalisé par le C_SLA interne appris (comme à l'entraînement) ; le
            // budget restant est une dimension d'état séparée : state[1].
            double[] state = sim.buildRLState(lastLatency, lastCost, dynCSla);

            // Determine action validity (mirrors TrainingEnvironment.step() / getActionMask()).
            // Éligibilité popularité (Paper Algorithm 1) : la réplication n'est possible que
            // sur données populaires — seuil P_SLA adaptatif (Sujet 1), contrat = P_SLA papier.
            // Au-dessus du seuil, l'agent décide librement QUAND/COMBIEN répliquer.
            int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
            boolean popularityEligible = sim.isReplicationAllowed();
            boolean canReplicate = sim.canCreateReplica();  // réplication utile possible
            boolean canDelete = sim.canDeleteReplica();  // miroir de TrainingEnvironment

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
            // propagés immédiatement à la simulation (état RL, T_SLA, ΔT ; ρ reste fixe).
            // Aucune cadence fixe — le moment du déclenchement est appris par agent.
            double alpha = TcdrmConstants.META_EMA_ALPHA;
            violEma = (1.0 - alpha) * violEma + alpha * (lastLatency > dynTSla ? 1.0 : 0.0);
            costRatioEma = (1.0 - alpha) * costRatioEma
                + alpha * (lastCost / Math.max(1e-9, cSlaRef));
            churnEma = (1.0 - alpha) * churnEma + alpha * (sim.isThrashingThisQuery() ? 1.0 : 0.0);
            if (!noMeta) {
                dynTSla = contractTSla * tslaMetaLearner.observeAndAdjust(violEma, costRatioEma);
                dynCSla = cSlaRef * cslaMetaLearner.observeAndAdjust(violEma, costRatioEma);
                sim.setDynamicThresholds(dynTSla, popRatio);
                sim.setDynamicDeletionWindow(
                    deletionWindowLearner.observeAndAdjust(violEma, costRatioEma, 0.0, churnEma));
            }
            double tSla = dynTSla;

            double reward = calculateReward(result, tSla, dynCSla, action,
                lastAssignmentSuccess, complex, sim, rw);

            double[] nextState = sim.buildRLState(lastLatency, lastCost, dynCSla);
            boolean done = (q == maxQ - 1);

            if ("qlearning".equals(modelType)) {
                bridge.updateQLearning(reward, nextState, done);
            } else {
                bridge.updateRainbow(reward, nextState, done);
            }

            data.addQueryResult(
                q, result.queryTimeMs(),
                result.bwCost() - result.replicaCreationCost(),   // BW pur (transfert), création exclue
                cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(),
                result.replicaCreationCost() + result.replicaMaintenanceCost(), // coût réplica = création + maintenance
                tSla
            );
        }

        System.out.println("      " + modelType.toUpperCase() + " terminé (adaptive online)");
        data.setReplicaStats(sim.getReplicaCreations(), sim.getReplicaDeletions(),
            sim.getThrashEvents(), sim.getUnusedReplicaCost());
        return data;
    }

    /**
     * Reward mirroring TrainingEnvironment.calculateReward() exactly — prevents train/eval divergence.
     * Les poids proviennent de la configuration PERSISTÉE À L'ENTRAÎNEMENT de cet agent
     * ({@link TrainingSettings#loadRewardConfig}) — aucun poids codé en dur ici.
     *   R = r_slaOk·SLA_OK − r_slaViol·SLA_VIOL − r_costOver·COST_OVER(budgetUrgency)
     *       − r_costLinear·COST_LINEAR − r_costViol·COST_VIOL
     *       − r_replCost·REPL_COST − r_premature·PREMATURE_REPL + r_correct·CORRECT_TRIGGER
     *       − r_thrash·THRASH − r_maint·MAINT − r_invalid·INVALID
     */
    private static double calculateReward(TcdrmSimulation.QueryResult result, double tSla, double cSla,
                                           int action, boolean lastAssignmentSuccess, boolean complex,
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
        // COST_VIOL (miroir de TrainingEnvironment) : forfait par dépassement du C_SLA.
        double costViolPenalty = cQ_norm > 1.0 ? -rw.getRewardCostViolStep() : 0.0;

        // REPL_COST, LOW_POPULARITY, PREMATURE_REPL, CORRECT_TRIGGER
        double popularityScore = sim.getPopularityScore();
        double replCostPenalty = 0.0;
        double lowPopularityPenalty = 0.0;
        double prematureReplPenalty = 0.0;
        double correctTriggerBonus = 0.0;
        if (action == 1 && lastAssignmentSuccess) {
            // Coût par réplica créé (miroir de TrainingEnvironment).
            int created = Math.max(1, sim.getLastCreatedCount());
            double dataGb = TcdrmConstants.queryDataSizeGb(complex);
            replCostPenalty = -rw.getRewardReplCost() * created * (dataGb * TcdrmConstants.COST_BW_INTER_PROVIDER)
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
            // T_SLA seul (miroir de TrainingEnvironment) : répliquer aggrave un
            // dépassement du C_SLA dû à la synchronisation.
            boolean slaViolated = latency > tSla;
            if (slaViolated) {
                // Utilité marginale décroissante (miroir de TrainingEnvironment) :
                // bonus plein pour les premières réplications, décroissant avec le
                // remplissage — l'agent s'arrête près de l'optimum au lieu de saturer.
                double fillBefore = Math.max(0, replicas - created)
                    / (double) TcdrmConstants.maxReplicasForQueryType(complex);
                correctTriggerBonus = rw.getRewardCorrectTrigger() * popularityScore
                    * (1.0 - fillBefore);
            }
        }

        // RISKY_DELETE (miroir de TrainingEnvironment) : pénalité croissante à
        // l'approche de T_SLA, nulle quand la latence en est confortablement loin.
        double prematureDeletePenalty = 0.0;
        if (action == 2 && lastAssignmentSuccess) {
            prematureDeletePenalty = -rw.getRewardPrematureDelete()
                * Math.max(0.0, Math.min(1.0, (tQ_norm - (1.0 - slaMarginNorm)) / slaMarginNorm));
        }

        // THRASH : oscillation création/suppression sur la MÊME donnée (miroir).
        double thrashPenalty = sim.isThrashingThisQuery() ? -rw.getRewardThrash() : 0.0;

        // Light maintenance cost per active replica (aligned with training config)
        double rewardUnutilization = -rw.getRewardMaintenance() * replicas;

        // DÉTENTION IMPOPULAIRE (miroir de TrainingEnvironment) : coût récurrent
        // Σ (1−pop_f)×réplicas_f — chaque réplica évalué sur la popularité de SA donnée.
        double unpopularHoldingPenalty =
            -rw.getRewardUnpopularHolding() * sim.getUnpopularReplicaLoad();

        // Invalid action signal
        double rewardInvalidAction = lastAssignmentSuccess ? 0.0 : -rw.getRewardInvalid();

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty + costLinearPenalty + costViolPenalty
            + replCostPenalty + lowPopularityPenalty + prematureReplPenalty + prematureDeletePenalty
            + correctTriggerBonus + thrashPenalty + rewardUnutilization + unpopularHoldingPenalty
            + rewardInvalidAction;
    }

}
