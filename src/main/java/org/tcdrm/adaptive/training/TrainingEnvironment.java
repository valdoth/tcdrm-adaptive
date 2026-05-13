package org.tcdrm.adaptive.training;

import org.tcdrm.adaptive.core.TcdrmConstants;
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

    private int currentQuery;
    private double lastLatency;
    private double lastCost;
    private double tSla;
    private double cSla;
    private double cumulativeReward;
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
        double tSimple = this.settings.getTSlaSimpleMs() > 0 ? this.settings.getTSlaSimpleMs() : TcdrmConstants.TSLA_SIMPLE_MS;
        double tComplex = this.settings.getTSlaComplexMs() > 0 ? this.settings.getTSlaComplexMs() : TcdrmConstants.TSLA_COMPLEX_MS;
        this.tSla = complex ? tComplex : tSimple;
        this.cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        reset();
    }
    
    /**
     * Réinitialise l'environnement et retourne l'état initial.
     * 
     * @return État initial (voir {@link TcdrmSimulation#buildRLState})
     */
    public double[] reset() {
        this.simulation = new TcdrmSimulation(seed, complex);
        // Popularity strategy: default EMA (TinyLFU disabled unless explicitly configured)
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
        boolean decisionZone = (simulation.getQueryCount() >= TcdrmConstants.POPULARITY_THRESHOLD)
            || (simulation.getEmaPopularity() >= TcdrmConstants.EMA_REPLICATION_THRESHOLD);
        boolean canReplicate = lastReplicaCount < maxReplicas && decisionZone && simulation.getCurrentBudget() > 0;
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
     * Format: [latency, budget, replicas, popularity, cost, t_sla_violation, c_sla_violation,
     * progress, p_sla_progress]
     */
    public double[] getState() {
        return simulation.buildRLState(lastLatency, lastCost);
    }

    public double getLastLatency() { return lastLatency; }
    
    /**
     * Calcule la récompense alignée sur la spec DQN_TCDRM_v2.md:
     *   R = r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH
     *
     * tQ_norm = latency / T_SLA  (normalisé sur l'objectif SLA, pas 5×)
     * cQ_norm = cost   / C_SLA
     */
    private double calculateReward(TcdrmSimulation.QueryResult result, int action) {
        double latency = result.queryTimeMs();
        int replicas = result.replicaCount();

        double tQ_norm = latency / Math.max(1.0, tSla);
        double cQ_norm = result.totalCost() / Math.max(1e-9, cSla);

        // SLA_OK: reward proportional to how far below tSla we are (r1=10)
        rewardWaitTime = 10.0 * Math.max(0.0, 1.0 - tQ_norm);

        // SLA_VIOL: proportional penalty when latency exceeds tSla (r2=20)
        rewardQueuePenalty = -20.0 * Math.max(0.0, tQ_norm - 1.0);

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
        recordAction(action);
        double thrashPenalty = isThrashing() ? -8.0 : 0.0;

        // Light maintenance cost per active replica (encourages parsimony)
        rewardUnutilization = -0.05 * replicas;

        // Invalid action: stronger signal so agent stops wasting actions (was -1)
        rewardInvalidAction = lastInvalidAction ? -2.0 : 0.0;

        return rewardWaitTime + rewardQueuePenalty + costOverPenalty
            + replCostPenalty + thrashPenalty + rewardUnutilization + rewardInvalidAction;
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
        // Répliquer seulement si: capacité dispo + zone de décision atteinte + budget suffisant
        boolean decisionZone = simulation.getQueryCount() >= TcdrmConstants.POPULARITY_THRESHOLD
            || simulation.getEmaPopularity() >= TcdrmConstants.EMA_REPLICATION_THRESHOLD;
        boolean canReplicate = lastReplicaCount < maxReplicas && decisionZone
            && simulation.getCurrentBudget() > 0;
        boolean canDelete = lastReplicaCount > 0;
        return new boolean[] { true, canReplicate, canDelete };
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
