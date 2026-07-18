package org.tcdrm.adaptive.training;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.simulation.ReplicaPlacementOptimizer;
import org.tcdrm.adaptive.simulation.TcdrmSimulation;

/**
 * Environnement d'entraînement pour les agents RL.
 * 
 * Expose les méthodes nécessaires à Gymnasium (Python):
 * - reset(): réinitialise l'environnement et retourne l'état initial
 * - step(action): exécute une action et retourne (état, récompense, done, info)
 * 
 * Les simulations sont exécutées dans CloudSimPlus, garantissant que
 * l'entraînement et l'inférence utilisent exactement le même environnement.
 */
public class TrainingEnvironment {

    private TcdrmSimulation simulation;
    // Mutable pour permettre de varier la seed à chaque épisode (diversification) sans recréer
    // l'environnement — la recréation effacerait la méta-adaptation TSLA accumulée entre épisodes.
    private long seed;
    private final boolean complex;
    private final TrainingSettings settings;
    // Optimiseur de placement persistant entre épisodes (Sujet 2 : accumule son apprentissage)
    private final ReplicaPlacementOptimizer placementOptimizer = new ReplicaPlacementOptimizer();

    private int currentQuery;
    private double lastLatency;
    private double lastCost;
    private double tSla;
    private double cSla;
    private double cumulativeReward;
    // Seuils adaptatifs — APPRIS par méta-contrôleurs Q-learning (Sujet 1, approche
    // "Q-Threshold"). CSLA reste statique (contrat budget). dynamicMinPopularity =
    // éligibilité popularité du papier (Algorithm 1 : pd_i ≥ P_SLA), repart du contrat
    // (1.0) à chaque épisode ; la POLITIQUE d'ajustement est dans les Q-tables
    // persistées, aucune règle codée en dur. Au-dessus du seuil, l'agent principal
    // décide librement quand/combien répliquer.
    private double dynamicTSla;
    private double dynamicMinPopularity = 1.0;
    // Mode démonstration (warm-start DQfD) : gate popularité forcé ouvert pour que
    // l'heuristique de démonstration puisse réellement répliquer et enseigner le bénéfice.
    private boolean demoMode = false;
    private final org.tcdrm.adaptive.rl.ThresholdMetaLearner popularityLearner;
    private final org.tcdrm.adaptive.rl.ThresholdMetaLearner tslaLearner;
    // Fenêtre ΔT de suppression (Paper Algorithm 3), apprise — fraction de P_SLA.
    private final org.tcdrm.adaptive.rl.ThresholdMetaLearner deletionWindowLearner;
    // Metrics for external-style logging
    private int slaViolations;
    private double cumulativeCost;
    private int replicaChanges;
    private int lastReplicaCount;
    private boolean lastInvalidAction;
    private boolean lastAssignmentSuccess;
    private double rewardWaitTime;
    private double rewardUnutilization;
    private double rewardQueuePenalty;
    private double rewardInvalidAction;

    // Action history ring buffer for thrash detection (last 10 actions)
    private final int[] actionRingBuffer = new int[10];
    private int actionRingPos = 0;
    private int actionRingCount = 0;

    // Signal de stress lissé (EMA) pour la méta-adaptation des seuils (Sujet 1) :
    // observé et exploitable À CHAQUE REQUÊTE — aucune cadence fixe de décision.
    // Le moment où un seuil change est entièrement déterminé par la politique apprise
    // du méta-contrôleur de CET agent, pas par une frontière de fenêtre partagée.
    // Partagé avec BenchmarkRunner (TcdrmConstants.META_EMA_ALPHA) pour que
    // l'entraînement et l'évaluation aient la même dynamique.
    private double violEma = 0.0;
    private double costRatioEma = 0.0;
    
    // Identité de l'agent propriétaire des Q-tables de méta-seuils.
    private final String agentTag;

    public TrainingEnvironment(long seed, boolean complex) { this(seed, complex, new TrainingSettings()); }

    public TrainingEnvironment(long seed, boolean complex, TrainingSettings settings) {
        this(seed, complex, settings, "shared");
    }

    public TrainingEnvironment(long seed, boolean complex, TrainingSettings settings, String agentTag) {
        this.seed = seed;
        this.complex = complex;
        this.agentTag = (agentTag == null || agentTag.isBlank()) ? "shared" : agentTag;
        this.settings = settings != null ? settings : new TrainingSettings();
        this.cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        // Initialisation des seuils adaptatifs à partir des valeurs de référence (ou settings)
        double tSimple  = this.settings.getTSlaSimpleMs()  > 0 ? this.settings.getTSlaSimpleMs()  : TcdrmConstants.TSLA_SIMPLE_MS;
        double tComplex = this.settings.getTSlaComplexMs() > 0 ? this.settings.getTSlaComplexMs() : TcdrmConstants.TSLA_COMPLEX_MS;
        this.dynamicTSla = complex ? tComplex : tSimple;
        this.tSla = this.dynamicTSla;
        // Méta-contrôleurs Q-learning des seuils (Sujet 1) : Q-tables PAR AGENT — chaque
        // agent apprend sa propre politique d'ajustement des seuils (persistance
        // inter-sessions), ε>0 pendant l'entraînement. ε calibré pour la cadence
        // PAR REQUÊTE (1000 décisions/épisode) : 0.05 ≈ 50 explorations par épisode.
        final double metaEps = 0.05;
        this.popularityLearner = org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
            TcdrmConstants.metaQtableFile(this.agentTag, "pop", complex),
            1.0, 0.0, 1.0, TcdrmConstants.META_POPULARITY_RESOLUTION, metaEps,
            seed ^ this.agentTag.hashCode());
        this.tslaLearner = org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
            TcdrmConstants.metaQtableFile(this.agentTag, "tsla", complex),
            1.0, TcdrmConstants.META_TSLA_MIN_MULTIPLIER, 1.0,
            TcdrmConstants.META_TSLA_RESOLUTION, metaEps,
            (seed ^ this.agentTag.hashCode()) ^ 0x9E3779B9L);
        this.deletionWindowLearner = org.tcdrm.adaptive.rl.ThresholdMetaLearner.loadOrCreate(
            TcdrmConstants.metaQtableFile(this.agentTag, "delw", complex),
            1.0, TcdrmConstants.META_DELETION_WINDOW_MIN, 1.0,
            TcdrmConstants.META_DELETION_WINDOW_RESOLUTION, metaEps,
            (seed ^ this.agentTag.hashCode()) ^ 0x51ED270CL);
        reset();
    }
    
    /**
     * Réinitialise l'environnement et retourne l'état initial.
     * 
     * @return État initial (voir {@link TcdrmSimulation#buildRLState})
     */
    public double[] reset() {
        // Méta-adaptation : ajuster dynamicTSla à partir des métriques de l'épisode précédent
        if (currentQuery > 0) {
            endEpisodeAndAdapt();
        }
        // Nouvel épisode : les seuils repartent des valeurs CONTRACTUELLES ; la connaissance
        // accumulée (Q-tables des méta-contrôleurs) est conservée entre épisodes.
        double contractTSla = complex
            ? (settings.getTSlaComplexMs() > 0 ? settings.getTSlaComplexMs() : TcdrmConstants.TSLA_COMPLEX_MS)
            : (settings.getTSlaSimpleMs()  > 0 ? settings.getTSlaSimpleMs()  : TcdrmConstants.TSLA_SIMPLE_MS);
        popularityLearner.startEpisode(1.0);
        tslaLearner.startEpisode(1.0);
        deletionWindowLearner.startEpisode(1.0);
        this.dynamicMinPopularity = popularityLearner.getValue();
        this.dynamicTSla = contractTSla * tslaLearner.getValue();
        // Régime de workload d'ENTRAÎNEMENT : variable/burst tiré de la seed de
        // l'épisode (~50/50, reproductible) — décorrélé de l'alternance simple/complex
        // pour couvrir toutes les combinaisons (type de requête × régime de popularité).
        // TCDRM_WORKLOAD force un mode unique. Le BENCHMARK, lui, utilise steady par
        // défaut (protocole du papier) — voir RuntimeConfig.
        if (!org.tcdrm.adaptive.core.RuntimeConfig.hasExplicitWorkloadMode()) {
            // splitmix64 : brassage fort de la seed — java.util.Random donne un premier
            // tirage corrélé pour des seeds consécutives (biais connu), pas ce mixeur.
            long z = seed + 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= (z >>> 31);
            org.tcdrm.adaptive.core.RuntimeConfig.setWorkloadMode(
                ((z & 1L) == 0L) ? "variable" : "burst");
        }
        // Passer l'optimiseur de placement persistant à la nouvelle simulation (Sujet 2)
        this.simulation = new TcdrmSimulation(seed, complex, placementOptimizer);
        // Profil de routage de l'agent (0 = temps seul) — même valeur qu'au benchmark
        // via la config persistée : cohérence train/eval.
        this.simulation.setCostAwareRouting(settings.getCostRoutingToleranceMs());
        // Injecter les seuils adaptatifs (T_SLA, éligibilité popularité, ΔT suppression) ;
        // en mode démonstration, gate forcé ouvert (minPop=0) dès le 1er pas.
        this.simulation.setDynamicThresholds(dynamicTSla, demoMode ? 0.0 : dynamicMinPopularity);
        this.simulation.setDynamicDeletionWindow(deletionWindowLearner.getValue());
        this.tSla = this.dynamicTSla;
        this.currentQuery = 0;
        this.lastLatency = 0.0;
        this.lastCost = 0.0;
        this.cumulativeReward = 0.0;
        this.slaViolations = 0;
        this.cumulativeCost = 0.0;
        this.replicaChanges = 0;
        this.lastReplicaCount = 0;
        this.lastInvalidAction = false;
        this.lastAssignmentSuccess = false;
        this.rewardWaitTime = 0.0;
        this.rewardUnutilization = 0.0;
        this.rewardQueuePenalty = 0.0;
        this.rewardInvalidAction = 0.0;
        this.actionRingPos = 0;
        this.actionRingCount = 0;
        java.util.Arrays.fill(actionRingBuffer, 0);
        this.violEma = 0.0;
        this.costRatioEma = 0.0;
        // Optional dynamic warmup before RL actions to avoid static starts
        int wu = settings.getWarmupQueries();
        if (wu > 0) {
            java.util.Random rnd = new java.util.Random(seed ^ 0x5F3759DF);
            String strategy = settings.getWarmupStrategy();
            double p = settings.getWarmupRandomProb();
            for (int i = 0; i < wu; i++) {
                TcdrmSimulation.QueryResult res;
                if ("norep".equalsIgnoreCase(strategy)) {
                    res = simulation.executeNoRepQuery();
                } else if ("tcdrm".equalsIgnoreCase(strategy)) {
                    res = simulation.executeTcdrmQuery();
                } else {
                    // random: choose valid RL action with probabilities
                    int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
                    int rc = simulation.getCurrentReplicaCount();
                    int action; // 0 noop, 1 replicate, 2 delete
                    if (rc <= 0) {
                        action = (rnd.nextDouble() < p ? 1 : 0);
                    } else if (rc >= maxReplicas) {
                        action = (rnd.nextDouble() < p ? 2 : 0);
                    } else {
                        double r = rnd.nextDouble();
                        if (r < p) action = 1; else if (r < 2*p) action = 2; else action = 0;
                    }
                    res = simulation.executeRLQuery(action);
                }
                // Update last known metrics; don't increment currentQuery or cumulativeReward
                lastLatency = res.queryTimeMs();
                lastCost = res.totalCost();
                cumulativeCost += lastCost;
                if (lastLatency > tSla) slaViolations++;
                int rcNow = res.replicaCount();
                if (rcNow != lastReplicaCount) {
                    replicaChanges += Math.abs(rcNow - lastReplicaCount);
                    lastReplicaCount = rcNow;
                }
            }
            // Reset episode counters for RL part only
            this.currentQuery = 0;
            this.cumulativeReward = 0.0;
            this.lastInvalidAction = false;
            this.lastAssignmentSuccess = false;
        }
        return getState();
    }
    
    /**
     * Exécute une action et retourne le résultat.
     * 
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     * @return StepResult contenant (état, récompense, done, info)
     */
    public StepResult step(int action) {
        // Déterminer validité de l'action (action masking — miroir de getActionMask)
        int maxEp = settings.getMaxEpisodeLength() > 0 ? settings.getMaxEpisodeLength() : TcdrmConstants.MAX_QUERIES;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        // Éligibilité popularité (Paper Algorithm 1, seuil adaptatif — Sujet 1) : REPLICATE
        // sur données non populaires devient NOOP (contrainte contractuelle, également
        // appliquée dans executeRLQuery). Au-dessus du seuil, l'agent décide librement.
        boolean popularityEligible = simulation.isReplicationAllowed();
        if (action == 1 && !popularityEligible) action = 0;
        boolean canReplicate = popularityEligible && lastReplicaCount < maxReplicas
            && simulation.getCurrentBudget() > 0;
        boolean canDelete = lastReplicaCount > 0;
        lastInvalidAction = (action == 1 && !canReplicate) || (action == 2 && !canDelete);
        lastAssignmentSuccess = !lastInvalidAction;

        // Exécuter la requête avec l'action
        TcdrmSimulation.QueryResult result = simulation.executeRLQuery(action);
        currentQuery++;
        
        lastLatency = result.queryTimeMs();
        lastCost = result.totalCost();
        cumulativeCost += lastCost;
        boolean violated = lastLatency > tSla;
        if (violated) slaViolations++;
        // Signal de stress lissé, mis à jour et exploité À CHAQUE REQUÊTE : les
        // méta-contrôleurs décident du moment ET de la valeur des seuils sans
        // aucune cadence fixe (Sujet 1 : « quand décider » est appris).
        double alpha = TcdrmConstants.META_EMA_ALPHA;
        violEma = (1.0 - alpha) * violEma + alpha * (violated ? 1.0 : 0.0);
        costRatioEma = (1.0 - alpha) * costRatioEma
            + alpha * (lastCost / Math.max(1e-9, cSla));
        adaptThresholds(violEma, costRatioEma);
        applyThresholds();
        int rc = result.replicaCount();
        if (rc != lastReplicaCount) {
            replicaChanges += Math.abs(rc - lastReplicaCount);
            lastReplicaCount = rc;
        }

        // Calculer la récompense et ses composantes
        double reward = calculateReward(result, action);
        cumulativeReward += reward;
        
        // Vérifier si l'épisode est terminé
        boolean done = (currentQuery >= maxEp);
        // Persister les Q-tables des méta-contrôleurs à chaque fin d'épisode (le reset
        // suivant peut ne jamais arriver pour le dernier épisode de la session).
        if (done) saveMetaLearners();
        
        // Construire l'info
        String info = String.format(
            "query=%d,latency=%.2f,cost=%.4f,replicas=%d,reward=%.2f",
            currentQuery, lastLatency, lastCost, result.replicaCount(), reward
        );
        
        return new StepResult(getState(), reward, done, info);
    }
    
    /**
     * Retourne l'état actuel de l'environnement.
     *
     * 9 dimensions : [latency, budget, replicas, cost, t_sla_viol, c_sla_viol, progress,
     * popularity_score, is_complex]. Le coût est normalisé par le CSLA contractuel
     * STATIQUE (budget du locataire, Paper Table 1) — le budget restant est déjà porté
     * par la dimension [1] de l'état.
     */
    public double[] getState() {
        return simulation.buildRLState(lastLatency, lastCost, cSla);
    }

    public double getLastLatency() { return lastLatency; }
    
    /**
     * Calcule la récompense (Sujet 1 — apprentissage auto de QUAND répliquer) :
     *
     *   R = r_slaOk·SLA_OK
     *     − r_slaViol·SLA_VIOL
     *     − r_costOver·COST_OVER
     *     − r_replCost·REPL_COST
     *     − r_premature·PREMATURE_REPL    ← pénalise REPLICATE inutile (SLA OK)
     *     − r_premDel·PREMATURE_DELETE    ← pénalise DELETE inutile (SLA OK) : évite le thrashing
     *     + r_correct·CORRECT_TRIGGER
     *     − r_thrash·THRASH
     *     − r_maint·replicas
     *     − r_invalid·INVALID
     *
     * PREMATURE_REPL = slaMargin × r_premature  où slaMargin = max(0, 1 − latency/T_SLA).
     * Plus la latence est loin d'une violation, plus répliquer est pénalisé.
     * Quand le SLA est violé (slaMargin=0), la pénalité disparaît → réplication justifiée.
     */
    private double calculateReward(TcdrmSimulation.QueryResult result, int action) {
        double latency = result.queryTimeMs();
        int replicas = result.replicaCount();

        double tQ_norm = latency / Math.max(1.0, tSla);
        double cQ_norm = result.totalCost() / Math.max(1e-9, cSla);

        // SLA_OK : récompense SATISFICING — pleine dès que la latence est confortablement
        // sous T_SLA (à (1−marge)·T_SLA), sans bonus supplémentaire en-dessous. Le SLA est
        // une CONTRAINTE, pas une latence à minimiser : ce plafond retire l'incitation au
        // sur-provisionnement (réplicas inutiles) et laisse l'agent minimiser le coût.
        double slaMarginNorm = Math.max(1e-6, settings.getSlaSatisfyMargin());
        double slaOkFactor = Math.max(0.0, Math.min(1.0, (1.0 - tQ_norm) / slaMarginNorm));
        rewardWaitTime = settings.getRewardSlaOk() * slaOkFactor;

        // SLA_VIOL : pénalité proportionnelle au dépassement de T_SLA
        rewardQueuePenalty = -settings.getRewardSlaViol() * Math.max(0.0, tQ_norm - 1.0);

        // COST_OVER : pénalité si coût par requête dépasse C_SLA effectif.
        // Proportionnelle à l'urgence budgétaire : quand le budget est presque épuisé,
        // chaque dépassement est plus grave pour le tenant (Algorithme 1, contrainte C_SLA).
        double budgetRatio   = simulation.getCurrentBudget() / TcdrmConstants.INITIAL_BUDGET;
        double budgetUrgency = 1.0 + Math.max(0.0, 1.0 - budgetRatio); // 1.0 (plein) → 2.0 (vide)
        double costOverPenalty = -settings.getRewardCostOver() * budgetUrgency * Math.max(0.0, cQ_norm - 1.0);

        // COST_LINEAR : coût réel de CHAQUE requête, même sous C_SLA — donne à l'agent
        // une sensibilité continue au prix de la bande passante (rewardCostOver est
        // inerte tant que cQ < C_SLA). 0 par défaut ; activé par agent pour une
        // politique qui priorise le coût.
        double costLinearPenalty = -settings.getRewardCostLinear() * cQ_norm;

        // LOW_POPULARITY : pénalité proportionnelle à (1 - popularityScore) lors d'une réplication.
        // Enseigne à l'agent de ne pas répliquer avant que les données soient connues,
        // sans aucun seuil statique — le signal vient de l'état et de la récompense.
        double popularityScore = simulation.getPopularityScore();

        // REPL_COST : coût réel bande passante de création du réplica
        double replCostPenalty = 0.0;
        double lowPopularityPenalty = 0.0;
        double prematureReplPenalty = 0.0;
        double correctTriggerBonus = 0.0;
        if (action == 1 && lastAssignmentSuccess) {
            double dataGb = TcdrmConstants.queryDataSizeGb(complex);
            replCostPenalty = -settings.getRewardReplCost()
                * (dataGb * TcdrmConstants.COST_BW_INTER_PROVIDER)
                / Math.max(1.0, TcdrmConstants.INITIAL_BUDGET);

            // LOW_POPULARITY : pénalité maximale au query 0, nulle à partir de P_SLA.
            lowPopularityPenalty = -settings.getRewardLowPopularity() * (1.0 - popularityScore);

            // PREMATURE_REPL : pénalise la réplication SANS BESOIN (SLA confortable) —
            // uniquement slaMargin. La protection « données pas encore connues » est
            // déjà portée par LOW_POPULARITY (ponctuel) et le coût de détention
            // convexe (récurrent) : l'ancien max(slaMargin, 1−pop) DOUBLAIT la pénalité
            // de faible popularité (−10×(1−pop) combiné vs bonus +8×pop → bascule
            // nette à pop 0.56 ≈ q130), enseignant la réplication TARDIVE — la source
            // du surcoût (requêtes payées plein tarif en attendant les réplicas).
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            prematureReplPenalty = -settings.getRewardPrematureRepl() * slaMargin;

            // CORRECT_TRIGGER continu : bonus proportionnel à la popularité observée quand
            // l'agent réplique sous violation SLA. Aucun seuil statique — combiné à
            // lowPopularityPenalty, le point de bascule net est APPRIS par l'agent.
            boolean slaViolated = latency > tSla || result.totalCost() > cSla;
            if (slaViolated) {
                // Utilité MARGINALE décroissante : bonus plein pour les PREMIÈRES
                // réplications (timing préservé), décroissant linéairement avec le taux
                // de remplissage AVANT l'action — les réplications au-delà du nécessaire
                // rapportent peu, l'agent s'arrête près de l'optimum au lieu de saturer
                // au max. Continu, aucun seuil statique.
                double fillBefore = Math.max(0, replicas - 1) / (double) maxReplicasForReward();
                correctTriggerBonus = settings.getRewardCorrectTrigger() * popularityScore
                    * (1.0 - fillBefore);
            }
        }

        // PREMATURE_DELETE : pénalité symétrique si on supprime un réplica quand SLA est OK.
        // Corrige la spirale de thrashing : sans cette pénalité, l'agent supprime tout pour
        // économiser le coût de maintenance, puis accumule des violations en masse.
        double prematureDeletePenalty = 0.0;
        if (action == 2 && lastAssignmentSuccess) {
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            prematureDeletePenalty = -settings.getRewardPrematureDelete() * slaMargin;
        }

        // THRASH : pénalise les allers-retours rapides REPLICATE/DELETE
        recordAction(action);
        double thrashPenalty = isThrashing() ? -settings.getRewardThrash() : 0.0;

        // Maintenance : coût léger par réplica actif (encourage la parcimonie)
        rewardUnutilization = -settings.getRewardMaintenance() * replicas;

        // DÉTENTION IMPOPULAIRE (Paper Algorithm 1) : coût RÉCURRENT — chaque réplica
        // est évalué à l'aune de la popularité de SA donnée (Σ (1−pop_f)×réplicas_f) :
        // détenir un réplica sur une donnée froide coûte même quand une autre donnée
        // est chaude. Ferme l'exploit « répliquer à q≈0 » ET enseigne la suppression
        // des réplicas devenus inutiles quand la popularité dérive ou qu'un pic
        // retombe — sans aucun seuil codé en dur.
        double unpopularHoldingPenalty =
            -settings.getRewardUnpopularHolding() * simulation.getUnpopularReplicaLoad();

        // Action invalide
        rewardInvalidAction = lastInvalidAction ? -settings.getRewardInvalid() : 0.0;

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty + costLinearPenalty
            + replCostPenalty + lowPopularityPenalty + prematureReplPenalty + prematureDeletePenalty
            + correctTriggerBonus + thrashPenalty + rewardUnutilization + unpopularHoldingPenalty
            + rewardInvalidAction;
    }

    /** Nombre max de réplicas du régime courant (pour le bonus marginal). */
    private int maxReplicasForReward() {
        return TcdrmConstants.maxReplicasForQueryType(complex);
    }

    /** Records action into ring buffer for thrash detection. */
    private void recordAction(int action) {
        actionRingBuffer[actionRingPos % 10] = action;
        actionRingPos++;
        if (actionRingCount < 10) actionRingCount++;
    }

    /** Returns true if last 10 actions contain ≥2 REPLICATEs and ≥2 DELETEs. */
    private boolean isThrashing() {
        if (actionRingCount < 6) return false;
        int replicates = 0, deletes = 0;
        for (int a : actionRingBuffer) {
            if (a == 1) replicates++;
            else if (a == 2) deletes++;
        }
        return replicates >= 2 && deletes >= 2;
    }
    
    /**
     * Retourne le nombre de requêtes exécutées.
     */
    public int getCurrentQuery() {
        return currentQuery;
    }
    
    /**
     * Retourne la récompense cumulative de l'épisode.
     */
    public double getCumulativeReward() {
        return cumulativeReward;
    }

    /** Nombre total de violations T_SLA dans l'épisode courant. */
    public int getSlaViolations() { return slaViolations; }

    /** Coût cumulé (toutes composantes) pour l'épisode courant. */
    public double getCumulativeCost() { return cumulativeCost; }

    /** Nombre total de changements de réplicas pendant l'épisode. */
    public int getReplicaChanges() { return replicaChanges; }

    /** Nombre de réplicas courants. */
    public int getReplicaCount() { return simulation.getCurrentReplicaCount(); }

    /** Budget restant courant. */
    public double getBudgetRemaining() { return simulation.getCurrentBudget(); }

    public boolean isInvalidActionTaken() { return lastInvalidAction; }
    public boolean isAssignmentSuccess() { return lastAssignmentSuccess; }
    public double getRewardWaitTime() { return rewardWaitTime; }
    public double getRewardUnutilization() { return rewardUnutilization; }
    public double getRewardQueuePenalty() { return rewardQueuePenalty; }
    public double getRewardInvalidAction() { return rewardInvalidAction; }

    /** Masque d'actions valides: [noop, replicate, delete] */
    public boolean[] getActionMask() {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        boolean canReplicate = simulation.isReplicationAllowed()
            && lastReplicaCount < maxReplicas && simulation.getCurrentBudget() > 0;
        boolean canDelete = lastReplicaCount > 0;
        return new boolean[] { true, canReplicate, canDelete };
    }

    // === Méta-adaptation des seuils T_SLA et popularité (P_SLA) — APPRISE (Sujet 1) ===
    // CSLA reste statique (contrat budget du locataire). L'ajustement des seuils est
    // décidé par deux méta-contrôleurs Q-learning (approche "Q-Threshold", Horovitz &
    // Arian FiCloud 2018) : aucune règle "si stress alors ±X%" codée en dur — la
    // politique d'ajustement est apprise et persistée dans les Q-tables.

    /**
     * À chaque requête : chaque méta-contrôleur observe le stress lissé (violations,
     * coût), apprend (TD-update) et choisit le niveau courant de son seuil — le moment
     * d'un changement est donc entièrement déterminé par sa politique apprise.
     */
    private void adaptThresholds(double violationRate, double avgCostRatio) {
        dynamicMinPopularity = popularityLearner.observeAndAdjust(violationRate, avgCostRatio);

        double contractTSla = complex
            ? (settings.getTSlaComplexMs() > 0 ? settings.getTSlaComplexMs() : TcdrmConstants.TSLA_COMPLEX_MS)
            : (settings.getTSlaSimpleMs()  > 0 ? settings.getTSlaSimpleMs()  : TcdrmConstants.TSLA_SIMPLE_MS);
        dynamicTSla = contractTSla * tslaLearner.observeAndAdjust(violationRate, avgCostRatio);

        // ΔT de suppression (Algorithm 3) : appris sur le même signal de stress ;
        // inerte tant que le workload garde toutes les données populaires, s'activera
        // avec les workloads à popularité variable (requêtes réellement différentes).
        if (simulation != null) {
            simulation.setDynamicDeletionWindow(
                deletionWindowLearner.observeAndAdjust(violationRate, avgCostRatio));
        }
    }

    /**
     * Calcule les métriques de fin d'épisode et applique la méta-adaptation restante.
     *
     * Sujet 1 (seuils) : l'adaptation est déjà continue (à chaque requête, signal EMA) —
     * rien à vider ici. Sujet 2 (placement) : agrégat sur l'épisode complet, inchangé.
     */
    private void endEpisodeAndAdapt() {
        // Persister la connaissance des méta-contrôleurs de seuils (rechargée par le
        // benchmark : c'est le MODÈLE qui porte la politique d'ajustement des seuils).
        saveMetaLearners();

        // Sujet 2 : adaptation des poids de l'optimiseur de placement (agrégat épisode complet)
        int maxEp = settings.getMaxEpisodeLength() > 0 ? settings.getMaxEpisodeLength() : TcdrmConstants.MAX_QUERIES;
        double violationRate = (double) slaViolations / Math.max(1, maxEp);
        double avgCostRatio  = (cumulativeCost / Math.max(1, maxEp))
            / Math.max(1e-9, complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE);
        placementOptimizer.adaptWeights(violationRate, avgCostRatio);
    }

    /** Sauvegarde les Q-tables des méta-contrôleurs de seuils de CET agent (Sujet 1). */
    private void saveMetaLearners() {
        try {
            popularityLearner.save(TcdrmConstants.metaQtableFile(agentTag, "pop", complex));
            tslaLearner.save(TcdrmConstants.metaQtableFile(agentTag, "tsla", complex));
            deletionWindowLearner.save(TcdrmConstants.metaQtableFile(agentTag, "delw", complex));
        } catch (java.io.IOException e) {
            System.err.println("[TrainingEnvironment] Failed to save meta-threshold Q-tables: " + e.getMessage());
        }
    }

    /**
     * Propage immédiatement les seuils courants : l'adaptation étant continue (chaque
     * requête), tSla (récompense), l'état RL normalisé et l'éligibilité popularité
     * doivent suivre en temps réel.
     *
     * MODE DÉMONSTRATION (warm-start DQfD) : le gate de popularité est forcé OUVERT
     * (minPop=0) pour que l'heuristique de démonstration puisse réellement RÉPLIQUER et
     * montrer à l'agent le bénéfice — indispensable en complex où un seul réplica aide
     * peu (l'agent ne découvrirait jamais la réplication seul). Le méta continue
     * d'observer/apprendre du stress ; seul le gate est court-circuité pendant les démos.
     */
    private void applyThresholds() {
        this.tSla = this.dynamicTSla;
        if (this.simulation != null) {
            double effMinPop = demoMode ? 0.0 : this.dynamicMinPopularity;
            this.simulation.setDynamicThresholds(this.dynamicTSla, effMinPop);
        }
    }

    /** Active le mode démonstration (gate popularité forcé ouvert pour le warm-start). */
    public void setDemoMode(boolean on) { this.demoMode = on; }

    public double getDynamicTSla() { return dynamicTSla; }
    /** Seuil de popularité adaptatif courant (P_SLA normalisé, appris — Sujet 1). */
    public double getDynamicMinPopularity() { return dynamicMinPopularity; }
    /** Poids courants de l'optimiseur de placement [w_lat, w_cost, w_sat] (Sujet 2). */
    public double[] getPlacementWeights() {
        return new double[]{ placementOptimizer.getWLat(), placementOptimizer.getWCost(),
                             placementOptimizer.getWSat() };
    }


    /**
     * Indique si les paramètres structurels (hors seed) ont changé au point de nécessiter une
     * recréation complète de l'environnement. La seed est volontairement exclue : elle change à
     * chaque épisode (diversification), et recréer l'environnement à chaque changement de seed
     * effacerait l'apprentissage des méta-contrôleurs de seuils (Q-tables en mémoire)
     * qui doit s'accumuler sur plusieurs épisodes consécutifs.
     */
    public boolean isDifferentSettings(TrainingSettings st) {
        if (this.settings == null || st == null) return true; // force recreate when comparing null-defaults
        return this.settings.getMaxEpisodeLength() != st.getMaxEpisodeLength()
            || Double.compare(this.settings.getTSlaSimpleMs(), st.getTSlaSimpleMs()) != 0
            || Double.compare(this.settings.getTSlaComplexMs(), st.getTSlaComplexMs()) != 0;
    }

    /** Change la seed utilisée par le prochain reset(), sans recréer l'environnement ni son état adaptatif. */
    public void setSeed(long s) { this.seed = s; }
    
    /**
     * Résultat d'un step.
     */
    public record StepResult(
        double[] state,
        double reward,
        boolean done,
        String info
    ) {}
}
