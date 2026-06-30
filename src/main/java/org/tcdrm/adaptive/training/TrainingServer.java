package org.tcdrm.adaptive.training;

import py4j.GatewayServer;

import java.util.HashMap;
import java.util.Map;
import ch.qos.logback.classic.Level;
import org.cloudsimplus.util.Log;


/**
 * Serveur Py4J pour l'entraînement RL.
 * 
 * Python (Gymnasium) se connecte à ce serveur pour:
 * - Créer des environnements d'entraînement
 * - Exécuter des simulations CloudSimPlus
 * - Recevoir les états et récompenses
 * 
 * Usage:
 *   java TrainingServer [port]
 */
public class TrainingServer {
    
    private TrainingEnvironment simpleEnv;
    private TrainingEnvironment complexEnv;
    private TrainingSettings settings = new TrainingSettings();
    
    /**
     * Crée un nouvel environnement d'entraînement.
     * 
     * @param seed Graine aléatoire pour reproductibilité
     * @param complex true pour requêtes complexes, false pour simples
     * @return L'environnement créé
     */
    public TrainingEnvironment createEnvironment(long seed, boolean complex) {
        if (complex) {
            complexEnv = new TrainingEnvironment(seed, true, settings);
            return complexEnv;
        } else {
            simpleEnv = new TrainingEnvironment(seed, false, settings);
            return simpleEnv;
        }
    }
    
    /**
     * Réinitialise l'environnement et retourne l'état initial.
     */
    public double[] reset(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null) {
            env = createEnvironment(System.currentTimeMillis(), complex);
        }
        return env.reset();
    }
    
    /**
     * Exécute une action dans l'environnement.
     * 
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     * @param complex true pour environnement complexe
     * @return Tableau [state..., reward, done (0 ou 1)]
     */
    public double[] step(int action, boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null) {
            throw new IllegalStateException("Environment not created. Call reset() first.");
        }
        
        TrainingEnvironment.StepResult result = env.step(action);
        
        // Combiner état + reward + done dans un seul tableau pour Py4J
        double[] state = result.state();
        double[] output = new double[state.length + 2];
        System.arraycopy(state, 0, output, 0, state.length);
        output[state.length] = result.reward();
        output[state.length + 1] = result.done() ? 1.0 : 0.0;
        
        return output;
    }
    
    /**
     * Retourne l'état actuel de l'environnement.
     */
    public double[] getState(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null) {
            throw new IllegalStateException("Environment not created. Call reset() first.");
        }
        return env.getState();
    }
    
    /**
     * Retourne le nombre de requêtes exécutées.
     */
    public int getCurrentQuery(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getCurrentQuery() : 0;
    }
    
    /**
     * Retourne la récompense cumulative.
     */
    public double getCumulativeReward(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getCumulativeReward() : 0.0;
    }

    // === Extra getters to support Python fallback metrics ===
    public int getSlaViolations(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getSlaViolations() : 0;
    }
    public double getCumulativeCost(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getCumulativeCost() : 0.0;
    }
    public int getReplicaCount(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getReplicaCount() : 0;
    }
    public double getBudgetRemaining(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getBudgetRemaining() : 0.0;
    }
    public int getReplicaChanges(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getReplicaChanges() : 0;
    }

    /**
     * Poids courants de l'optimiseur de placement [w_lat, w_cost, w_pop, w_sat] (Sujet 2).
     * Permet au script Python de monitorer l'évolution des poids entre épisodes.
     */
    public double[] getPlacementWeights(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getPlacementWeights() : new double[]{0.45, 0.45, 0.10};
    }

    /**
     * État adaptatif courant (Sujet 1) : CONSERVATIVE / BALANCED / AGGRESSIVE.
     */
    public String getReplicationState(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getReplicationState() : "BALANCED";
    }


    /**
     * Point d'entrée pour démarrer le serveur d'entraînement.
     */
    public static void main(String[] args) {
        String envPort = System.getenv("TCDRM_TRAIN_PORT");
        int defaultPort = 25335;
        int port = defaultPort;
        try {
            port = args.length > 0 ? Integer.parseInt(args[0]) : (envPort != null ? Integer.parseInt(envPort) : defaultPort);
        } catch (NumberFormatException nfe) {
            System.err.println("⚠️  Invalid TCDRM_TRAIN_PORT, falling back to " + defaultPort);
            port = defaultPort;
        }
        
        System.out.println("=".repeat(70));
        System.out.println("TCDRM TRAINING SERVER - CloudSimPlus Environment for RL");
        System.out.println("=".repeat(70));
        
        try {
            TrainingServer server = new TrainingServer();
            // Réduire la verbosité CloudSimPlus pendant l'entraînement
            Log.setLevel(Level.WARN);
            
            GatewayServer gatewayServer = new GatewayServer(server, port);
            
            System.out.println("🚀 Starting training server on port " + port + "...");
            gatewayServer.start();
            
            System.out.println("✅ Training server ready!");
            System.out.println("📡 Waiting for Python connections...");
            System.out.println();
            System.out.println("Python can connect with:");
            System.out.println("  from py4j.java_gateway import JavaGateway");
            System.out.println("  gateway = JavaGateway(gateway_parameters=GatewayParameters(port=" + port + "))");
            System.out.println("  server = gateway.entry_point");
            System.out.println();
            
            // Keep running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Configure la simulation depuis Python (schéma inspiré du repo de référence).
     * Exemples de clés supportées: maxEpisodeLength (Integer), tSlaSimpleMs (Double), tSlaComplexMs (Double)
     */
    public void configureSimulation(Map<String, Object> params) {
        if (params == null) return;
        Map<String, Object> p = new HashMap<>(params);
        Object maxEp = p.get("maxEpisodeLength");
        if (maxEp instanceof Number n) settings.setMaxEpisodeLength(n.intValue());
        Object tSimple = p.get("tSlaSimpleMs");
        if (tSimple instanceof Number n) settings.setTSlaSimpleMs(n.doubleValue());
        Object tComplex = p.get("tSlaComplexMs");
        if (tComplex instanceof Number n) settings.setTSlaComplexMs(n.doubleValue());
        Object wu = p.get("warmupQueries");
        if (wu instanceof Number n) settings.setWarmupQueries(n.intValue());
        Object ws = p.get("warmupStrategy");
        if (ws instanceof String s) settings.setWarmupStrategy(s);
        Object wrp = p.get("warmupRandomProb");
        if (wrp instanceof Number n) settings.setWarmupRandomProb(n.doubleValue());
        // Poids de la fonction de récompense (Sujet 1 — configurables depuis Python)
        Object rOk   = p.get("rewardSlaOk");         if (rOk   instanceof Number n) settings.setRewardSlaOk(n.doubleValue());
        Object rViol = p.get("rewardSlaViol");        if (rViol instanceof Number n) settings.setRewardSlaViol(n.doubleValue());
        Object rCost = p.get("rewardCostOver");       if (rCost instanceof Number n) settings.setRewardCostOver(n.doubleValue());
        Object rRepl = p.get("rewardReplCost");       if (rRepl instanceof Number n) settings.setRewardReplCost(n.doubleValue());
        Object rPrem = p.get("rewardPrematureRepl");   if (rPrem instanceof Number n) settings.setRewardPrematureRepl(n.doubleValue());
        Object rDel  = p.get("rewardPrematureDelete"); if (rDel  instanceof Number n) settings.setRewardPrematureDelete(n.doubleValue());
        Object rThr  = p.get("rewardThrash");          if (rThr  instanceof Number n) settings.setRewardThrash(n.doubleValue());
        Object rMnt  = p.get("rewardMaintenance");    if (rMnt  instanceof Number n) settings.setRewardMaintenance(n.doubleValue());
        Object rInv  = p.get("rewardInvalid");        if (rInv  instanceof Number n) settings.setRewardInvalid(n.doubleValue());
        Object rPop  = p.get("rewardLowPopularity");  if (rPop  instanceof Number n) settings.setRewardLowPopularity(n.doubleValue());
        Object rCorr = p.get("rewardCorrectTrigger"); if (rCorr instanceof Number n) settings.setRewardCorrectTrigger(n.doubleValue());
    }

    /** Reset structuré (retourne un objet avec state + info). */
    public TrainingResetResult resetStructured(boolean complex, long seed) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null || env.isDifferentSeedOrSettings(seed, settings)) {
            env = createEnvironment(seed, complex);
        }
        double[] state = env.reset();
        TrainingStepInfo info = new TrainingStepInfo(
            0.0,
            env.getCurrentQuery(),
            env.getCumulativeReward(),
            env.getSlaViolations(),
            env.getCumulativeCost(),
            env.getReplicaCount(),
            env.getBudgetRemaining(),
            env.isInvalidActionTaken(),
            env.isAssignmentSuccess(),
            env.getRewardWaitTime(),
            env.getRewardUnutilization(),
            env.getRewardQueuePenalty(),
            env.getRewardInvalidAction(),
            env.getReplicaChanges()
        );
        return new TrainingResetResult(state, info);
    }

    /** Step structuré (retourne state + reward + done + info). */
    public TrainingStepResult stepStructured(int action, boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null) {
            throw new IllegalStateException("Environment not created. Call resetStructured() first.");
        }
        TrainingEnvironment.StepResult r = env.step(action);
        // Pas de troncation dans notre boucle actuelle (pas timestep), donc truncated=false
        boolean truncated = false;
        TrainingStepInfo info = new TrainingStepInfo(
            env.getLastLatency(),
            env.getCurrentQuery(),
            env.getCumulativeReward(),
            env.getSlaViolations(),
            env.getCumulativeCost(),
            env.getReplicaCount(),
            env.getBudgetRemaining(),
            env.isInvalidActionTaken(),
            env.isAssignmentSuccess(),
            env.getRewardWaitTime(),
            env.getRewardUnutilization(),
            env.getRewardQueuePenalty(),
            env.getRewardInvalidAction(),
            env.getReplicaChanges()
        );
        return new TrainingStepResult(r.state(), r.reward(), r.done(), truncated, info);
    }

    /** Masque d'actions valides pour l'état courant. */
    public boolean[] getActionMask(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null) {
            throw new IllegalStateException("Environment not created. Call reset() first.");
        }
        return env.getActionMask();
    }
}

/** Paramètres configurables de l'entraînement/simulation. */
class TrainingSettings {
    private int maxEpisodeLength = -1; // -1 = utiliser constantes
    private double tSlaSimpleMs = -1;  // -1 = utiliser constantes
    private double tSlaComplexMs = -1; // -1 = utiliser constantes
    // Dynamic warmup configuration (applied on TrainingEnvironment.reset)
    private int warmupQueries = 0;           // number of warmup queries before RL actions
    private String warmupStrategy = "random"; // random | tcdrm | norep
    private double warmupRandomProb = 0.2;   // probability for replicate/delete in random mode

    // Sujet 1 : poids de la fonction de récompense — configurables depuis Python
    // R = r_slaOk × SLA_OK − r_slaViol × SLA_VIOL − r_costOver × COST_OVER
    //     − r_replCost × REPL_COST − r_premature × PREMATURE_REPL
    //     − r_thrash × THRASH − r_maint × replicas − r_invalid × INVALID
    private double rewardSlaOk        = 10.0;
    private double rewardSlaViol      = 20.0;
    private double rewardCostOver     = 15.0;
    private double rewardReplCost     =  5.0;
    // Pénalités symétriques REPLICATE/DELETE prématurés : dissuadent d'agir sans pression SLA.
    // slaMargin ∈ [0,1] : 1 = loin de la violation (très prématuré), 0 = SLA violé (justifié).
    private double rewardPrematureRepl   = 5.0;
    private double rewardPrematureDelete = 5.0;  // symétrique : pénalise DELETE inutile quand SLA OK
    private double rewardThrash          =  8.0;
    private double rewardMaintenance     =  0.01; // réduit 0.05→0.01 : supprime l'incitation à détruire des réplicas
    private double rewardInvalid         =  2.0;
    // Pénalité proportionnelle à (1 - popularityScore) lors d'une réplication.
    // 0 quand P_SLA atteint, maximale au query 0 — enseigne à ne pas répliquer avant que
    // les données soient connues, sans aucun seuil statique dans le code de simulation.
    private double rewardLowPopularity  =  5.0;
    // Bonus quand l'agent réplique exactement quand l'Algorithme 1 le ferait :
    // SLA violé (temps ou coût) ET workload stabilisé (P_SLA atteint).
    private double rewardCorrectTrigger =  8.0;

    public int getMaxEpisodeLength() { return maxEpisodeLength; }
    public void setMaxEpisodeLength(int v) { this.maxEpisodeLength = v; }
    public double getTSlaSimpleMs() { return tSlaSimpleMs; }
    public void setTSlaSimpleMs(double v) { this.tSlaSimpleMs = v; }
    public double getTSlaComplexMs() { return tSlaComplexMs; }
    public void setTSlaComplexMs(double v) { this.tSlaComplexMs = v; }
    public int getWarmupQueries() { return warmupQueries; }
    public void setWarmupQueries(int v) { this.warmupQueries = Math.max(0, v); }
    public String getWarmupStrategy() { return warmupStrategy; }
    public void setWarmupStrategy(String s) { this.warmupStrategy = (s == null ? "random" : s); }
    public double getWarmupRandomProb() { return warmupRandomProb; }
    public void setWarmupRandomProb(double p) { this.warmupRandomProb = Math.max(0.0, Math.min(1.0, p)); }

    public double getRewardSlaOk()             { return rewardSlaOk; }
    public double getRewardSlaViol()           { return rewardSlaViol; }
    public double getRewardCostOver()          { return rewardCostOver; }
    public double getRewardReplCost()          { return rewardReplCost; }
    public double getRewardPrematureRepl()     { return rewardPrematureRepl; }
    public double getRewardPrematureDelete()   { return rewardPrematureDelete; }
    public double getRewardThrash()            { return rewardThrash; }
    public double getRewardMaintenance()       { return rewardMaintenance; }
    public double getRewardInvalid()           { return rewardInvalid; }
    public double getRewardLowPopularity()     { return rewardLowPopularity; }
    public double getRewardCorrectTrigger()    { return rewardCorrectTrigger; }

    public void setRewardSlaOk(double v)             { rewardSlaOk           = Math.max(0, v); }
    public void setRewardSlaViol(double v)            { rewardSlaViol         = Math.max(0, v); }
    public void setRewardCostOver(double v)           { rewardCostOver        = Math.max(0, v); }
    public void setRewardReplCost(double v)           { rewardReplCost        = Math.max(0, v); }
    public void setRewardPrematureRepl(double v)      { rewardPrematureRepl   = Math.max(0, v); }
    public void setRewardPrematureDelete(double v)    { rewardPrematureDelete = Math.max(0, v); }
    public void setRewardThrash(double v)             { rewardThrash          = Math.max(0, v); }
    public void setRewardMaintenance(double v)        { rewardMaintenance     = Math.max(0, v); }
    public void setRewardInvalid(double v)            { rewardInvalid         = Math.max(0, v); }
    public void setRewardLowPopularity(double v)      { rewardLowPopularity   = Math.max(0, v); }
    public void setRewardCorrectTrigger(double v)     { rewardCorrectTrigger  = Math.max(0, v); }
}

/** Info renvoyée à chaque reset/step (horodatage simple). */
class TrainingStepInfo {
    private final double lastLatencyMs;
    private final int currentQuery;
    private final double cumulativeReward;
    private final int slaViolations;
    private final double cumulativeCost;
    private final int replicaCount;
    private final double budgetRemaining;
    private final boolean invalidActionTaken;
    private final boolean assignmentSuccess;
    private final double rewardWaitTime;
    private final double rewardUnutilization;
    private final double rewardQueuePenalty;
    private final double rewardInvalidAction;
    private final int replicaChanges;

    public TrainingStepInfo(
        double lastLatencyMs,
        int currentQuery,
        double cumulativeReward,
        int slaViolations,
        double cumulativeCost,
        int replicaCount,
        double budgetRemaining,
        boolean invalidActionTaken,
        boolean assignmentSuccess,
        double rewardWaitTime,
        double rewardUnutilization,
        double rewardQueuePenalty,
        double rewardInvalidAction,
        int replicaChanges
    ) {
        this.lastLatencyMs = lastLatencyMs;
        this.currentQuery = currentQuery;
        this.cumulativeReward = cumulativeReward;
        this.slaViolations = slaViolations;
        this.cumulativeCost = cumulativeCost;
        this.replicaCount = replicaCount;
        this.budgetRemaining = budgetRemaining;
        this.invalidActionTaken = invalidActionTaken;
        this.assignmentSuccess = assignmentSuccess;
        this.rewardWaitTime = rewardWaitTime;
        this.rewardUnutilization = rewardUnutilization;
        this.rewardQueuePenalty = rewardQueuePenalty;
        this.rewardInvalidAction = rewardInvalidAction;
        this.replicaChanges = replicaChanges;
    }

    public double getLastLatencyMs() { return lastLatencyMs; }
    public int getCurrentQuery() { return currentQuery; }
    public double getCumulativeReward() { return cumulativeReward; }
    public int getSlaViolations() { return slaViolations; }
    public double getCumulativeCost() { return cumulativeCost; }
    public int getReplicaCount() { return replicaCount; }
    public double getBudgetRemaining() { return budgetRemaining; }
    public boolean getInvalidActionTaken() { return invalidActionTaken; }
    public boolean getAssignmentSuccess() { return assignmentSuccess; }
    public double getRewardWaitTime() { return rewardWaitTime; }
    public double getRewardUnutilization() { return rewardUnutilization; }
    public double getRewardQueuePenalty() { return rewardQueuePenalty; }
    public double getRewardInvalidAction() { return rewardInvalidAction; }
    public int getReplicaChanges() { return replicaChanges; }
}

/** Résultat de reset structuré: état + info. */
class TrainingResetResult {
    private final double[] state;
    private final TrainingStepInfo info;

    public TrainingResetResult(double[] state, TrainingStepInfo info) {
        this.state = state;
        this.info = info;
    }

    public double[] getState() { return state; }
    public TrainingStepInfo getInfo() { return info; }
}

/** Résultat de step structuré: état + reward + done + truncated + info. */
class TrainingStepResult {
    private final double[] state;
    private final double reward;
    private final boolean terminated;
    private final boolean truncated;
    private final TrainingStepInfo info;

    public TrainingStepResult(double[] state, double reward, boolean terminated, boolean truncated, TrainingStepInfo info) {
        this.state = state;
        this.reward = reward;
        this.terminated = terminated;
        this.truncated = truncated;
        this.info = info;
    }

    public double[] getState() { return state; }
    public double getReward() { return reward; }
    public boolean isTerminated() { return terminated; }
    public boolean isTruncated() { return truncated; }
    public TrainingStepInfo getInfo() { return info; }
}
