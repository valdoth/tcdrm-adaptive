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
    private final long seed;
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
    // Seuil T_SLA adaptatif — méta-boucle inter-épisodes (Sujet 1).
    // CSLA reste statique (contrat tenant) ; seul TSLA évolue selon les performances.
    private double dynamicTSla;
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
    
    public TrainingEnvironment(long seed, boolean complex) { this(seed, complex, new TrainingSettings()); }

    public TrainingEnvironment(long seed, boolean complex, TrainingSettings settings) {
        this.seed = seed;
        this.complex = complex;
        this.settings = settings != null ? settings : new TrainingSettings();
        this.cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        // Initialisation des seuils adaptatifs à partir des valeurs de référence (ou settings)
        double tSimple  = this.settings.getTSlaSimpleMs()  > 0 ? this.settings.getTSlaSimpleMs()  : TcdrmConstants.TSLA_SIMPLE_MS;
        double tComplex = this.settings.getTSlaComplexMs() > 0 ? this.settings.getTSlaComplexMs() : TcdrmConstants.TSLA_COMPLEX_MS;
        this.dynamicTSla = complex ? tComplex : tSimple;
        this.tSla = this.dynamicTSla;
        reset();
    }
    
    /**
     * Réinitialise l'environnement et retourne l'état initial.
     * 
     * @return État initial (voir {@link TcdrmSimulation#buildRLState})
     */
    public double[] reset() {
        // Méta-adaptation : ajuster TSLA/PSLA à partir des métriques de l'épisode précédent
        if (currentQuery > 0) {
            endEpisodeAndAdapt();
        }
        // Passer l'optimiseur de placement persistant à la nouvelle simulation (Sujet 2)
        this.simulation = new TcdrmSimulation(seed, complex, placementOptimizer);
        // Injecter le seuil T_SLA adaptatif dans la nouvelle simulation
        this.simulation.setDynamicThresholds(dynamicTSla);
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
        // Popularity gate: block REPLICATE until MIN_POPULARITY_TO_REPLICATE so training
        // matches evaluation and the pre-replication phase aligns with NoRepLC/TCDRM.
        boolean popularityGateOpen = simulation.getPopularityScore() >= TcdrmConstants.MIN_POPULARITY_TO_REPLICATE;
        if (action == 1 && !popularityGateOpen) action = 0;
        boolean canReplicate = popularityGateOpen && lastReplicaCount < maxReplicas && simulation.getCurrentBudget() > 0;
        boolean canDelete = lastReplicaCount > 0;
        lastInvalidAction = (action == 1 && !canReplicate) || (action == 2 && !canDelete);
        lastAssignmentSuccess = !lastInvalidAction;

        // Exécuter la requête avec l'action
        TcdrmSimulation.QueryResult result = simulation.executeRLQuery(action);
        currentQuery++;
        
        lastLatency = result.queryTimeMs();
        lastCost = result.totalCost();
        cumulativeCost += lastCost;
        if (lastLatency > tSla) {
            slaViolations++;
        }
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
     * 8 dimensions : [latency, budget, replicas, cost, t_sla_viol, c_sla_viol, progress, replication_gain].
     * Le coût est normalisé par le CSLA effectif = budget_restant / requêtes_restantes.
     */
    public double[] getState() {
        return simulation.buildRLState(lastLatency, lastCost, computeEffectiveCsla());
    }

    /**
     * CSLA effectif par requête = budget_restant / requêtes_restantes.
     * Le budget total est statique (défini par le locataire) ; ce calcul répartit
     * équitablement ce qui reste sur les requêtes à venir.
     * Borné à [10%, 100%] du CSLA de référence pour éviter les extrêmes.
     */
    private double computeEffectiveCsla() {
        int maxEp = settings.getMaxEpisodeLength() > 0 ? settings.getMaxEpisodeLength() : TcdrmConstants.MAX_QUERIES;
        int remaining = Math.max(1, maxEp - currentQuery);
        double perQueryBudget = simulation.getCurrentBudget() / remaining;
        return Math.min(cSla, Math.max(perQueryBudget, cSla * 0.1));
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
        double cQ_norm = result.totalCost() / Math.max(1e-9, computeEffectiveCsla());

        // SLA_OK : récompense proportionnelle à la marge sous T_SLA
        rewardWaitTime = settings.getRewardSlaOk() * Math.max(0.0, 1.0 - tQ_norm);

        // SLA_VIOL : pénalité proportionnelle au dépassement de T_SLA
        rewardQueuePenalty = -settings.getRewardSlaViol() * Math.max(0.0, tQ_norm - 1.0);

        // COST_OVER : pénalité si coût par requête dépasse C_SLA effectif.
        // Proportionnelle à l'urgence budgétaire : quand le budget est presque épuisé,
        // chaque dépassement est plus grave pour le tenant (Algorithme 1, contrainte C_SLA).
        double budgetRatio   = simulation.getCurrentBudget() / TcdrmConstants.INITIAL_BUDGET;
        double budgetUrgency = 1.0 + Math.max(0.0, 1.0 - budgetRatio); // 1.0 (plein) → 2.0 (vide)
        double costOverPenalty = -settings.getRewardCostOver() * budgetUrgency * Math.max(0.0, cQ_norm - 1.0);

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

            // PREMATURE_REPL : pénalité si on réplique quand les données ne sont pas encore connues.
            double slaMargin = Math.max(0.0, 1.0 - tQ_norm);
            // After stabilization data is fully popular: no premature penalty so the agent
            // can freely add replicas beyond the first batch without being penalised when
            // latency happens to be below T_SLA.
            double effectiveMargin = simulation.isWorkloadStabilized()
                ? 0.0
                : Math.max(slaMargin, 1.0 - popularityScore);
            prematureReplPenalty = -settings.getRewardPrematureRepl() * effectiveMargin;

            // CORRECT_TRIGGER : bonus quand l'agent réplique alors que SLA est violé et données
            // suffisamment connues. Seuil aligné sur MIN_POPULARITY_TO_REPLICATE (gate = 0.3)
            // pour couvrir les queries 60-100 (nouvelle zone morte post-gate).
            boolean slaViolated = latency > tSla || result.totalCost() > cSla;
            if (slaViolated && (simulation.isWorkloadStabilized()
                    || popularityScore >= TcdrmConstants.MIN_POPULARITY_TO_REPLICATE)) {
                correctTriggerBonus = settings.getRewardCorrectTrigger();
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

        // Action invalide
        rewardInvalidAction = lastInvalidAction ? -settings.getRewardInvalid() : 0.0;

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty
            + replCostPenalty + lowPopularityPenalty + prematureReplPenalty + prematureDeletePenalty
            + correctTriggerBonus + thrashPenalty + rewardUnutilization + rewardInvalidAction;
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
        boolean popularityGateOpen = simulation.getPopularityScore() >= TcdrmConstants.MIN_POPULARITY_TO_REPLICATE;
        boolean canReplicate = popularityGateOpen && lastReplicaCount < maxReplicas && simulation.getCurrentBudget() > 0;
        boolean canDelete = lastReplicaCount > 0;
        return new boolean[] { true, canReplicate, canDelete };
    }

    // === Méta-adaptation du seuil T_SLA — machine à états (Sujet 1) ===
    // CSLA reste statique (contrat budget du locataire).

    /** États de la politique de réplication. */
    private enum ReplicationState {
        /** Budget dépassé → restreindre (TSLA remonte vers contrat). */
        CONSERVATIVE,
        /** Fonctionnement normal. */
        BALANCED,
        /** Violations élevées → encourager la réplication (TSLA descend). */
        AGGRESSIVE
    }

    private ReplicationState replicationState = ReplicationState.BALANCED;
    private int consecutiveViolationEpisodes = 0;
    private int consecutiveCostOverEpisodes  = 0;

    /**
     * Évalue les événements et ajuste dynamicTSla selon la machine à états.
     *
     * Transitions (prédicat temporel = 2 épisodes consécutifs) :
     *   BALANCED → CONSERVATIVE  si coût élevé (≥2 épisodes)
     *   BALANCED → AGGRESSIVE    si violations élevées (≥2 épisodes)
     *   AGGRESSIVE/CONSERVATIVE → BALANCED si condition résolue ou stable
     */
    private void adaptThresholds(double violationRate, double avgCostRatio) {
        boolean highViolation = violationRate > 0.20;
        boolean highCost      = avgCostRatio   > 1.20;

        consecutiveViolationEpisodes = highViolation ? consecutiveViolationEpisodes + 1 : 0;
        consecutiveCostOverEpisodes  = highCost      ? consecutiveCostOverEpisodes  + 1 : 0;

        boolean evtHighViolation = consecutiveViolationEpisodes >= 2;
        boolean evtHighCost      = consecutiveCostOverEpisodes  >= 2;
        boolean evtStable        = violationRate < 0.05 && avgCostRatio < 0.80;

        ReplicationState prev = replicationState;
        switch (replicationState) {
            case BALANCED:
                if      (evtHighCost)      replicationState = ReplicationState.CONSERVATIVE;
                else if (evtHighViolation) replicationState = ReplicationState.AGGRESSIVE;
                break;
            case AGGRESSIVE:
                if (evtHighCost || evtStable) replicationState = ReplicationState.BALANCED;
                break;
            case CONSERVATIVE:
                if (evtHighViolation || evtStable) replicationState = ReplicationState.BALANCED;
                break;
        }
        if (replicationState != prev) {
            consecutiveViolationEpisodes = 0;
            consecutiveCostOverEpisodes  = 0;
        }

        double contractTSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double tStep = contractTSla * 0.05;

        switch (replicationState) {
            case AGGRESSIVE:
                dynamicTSla = Math.max(contractTSla * 0.60, dynamicTSla - tStep);
                break;
            case CONSERVATIVE:
                dynamicTSla = Math.min(contractTSla, dynamicTSla + tStep * 0.5);
                break;
            case BALANCED:
                dynamicTSla = Math.min(contractTSla, dynamicTSla + tStep * 0.3);
                break;
        }
    }

    /** Calcule les métriques de l'épisode courant et applique la méta-adaptation. */
    private void endEpisodeAndAdapt() {
        int maxEp = settings.getMaxEpisodeLength() > 0 ? settings.getMaxEpisodeLength() : TcdrmConstants.MAX_QUERIES;
        double violationRate = (double) slaViolations / Math.max(1, maxEp);
        double avgCostRatio  = (cumulativeCost / Math.max(1, maxEp))
            / Math.max(1e-9, complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE);
        // Sujet 1 : adaptation des seuils TSLA/PSLA
        adaptThresholds(violationRate, avgCostRatio);
        // Sujet 2 : adaptation des poids de l'optimiseur de placement
        placementOptimizer.adaptWeights(violationRate, avgCostRatio);
    }

    public double getDynamicTSla() { return dynamicTSla; }
    /** État courant de la politique de réplication (CONSERVATIVE / BALANCED / AGGRESSIVE). */
    public String getReplicationState() { return replicationState.name(); }
    /** Poids courants de l'optimiseur de placement [w_lat, w_cost, w_sat] (Sujet 2). */
    public double[] getPlacementWeights() {
        return new double[]{ placementOptimizer.getWLat(), placementOptimizer.getWCost(),
                             placementOptimizer.getWSat() };
    }


    public boolean isDifferentSeedOrSettings(long s, TrainingSettings st) {
        if (this.seed != s) return true;
        if (this.settings == null || st == null) return true; // force recreate when comparing null-defaults
        return this.settings.getMaxEpisodeLength() != st.getMaxEpisodeLength()
            || Double.compare(this.settings.getTSlaSimpleMs(), st.getTSlaSimpleMs()) != 0
            || Double.compare(this.settings.getTSlaComplexMs(), st.getTSlaComplexMs()) != 0;
    }
    
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
