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
    // Identité de l'agent en cours d'entraînement : chaque agent possède ses propres
    // Q-tables de méta-seuils (meta_threshold_<tag>_*.qtable).
    private String agentTag = "shared";
    
    /**
     * Crée un nouvel environnement d'entraînement.
     * 
     * @param seed Graine aléatoire pour reproductibilité
     * @param complex true pour requêtes complexes, false pour simples
     * @return L'environnement créé
     */
    public TrainingEnvironment createEnvironment(long seed, boolean complex) {
        // Persiste les poids de récompense de CET agent : le benchmark les recharge
        // pour garantir l'alignement train/eval (aucun poids codé en dur côté éval).
        saveRewardConfig();
        if (complex) {
            complexEnv = new TrainingEnvironment(seed, true, settings, agentTag);
            return complexEnv;
        } else {
            simpleEnv = new TrainingEnvironment(seed, false, settings, agentTag);
            return simpleEnv;
        }
    }

    private void saveRewardConfig() {
        try {
            settings.saveRewardConfig(
                org.tcdrm.adaptive.core.TcdrmConstants.rewardConfigFile(agentTag));
        } catch (java.io.IOException e) {
            System.err.println("[TrainingServer] Failed to save reward config: " + e.getMessage());
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

    /** Seuil T_SLA adaptatif courant (ms), pour monitoring Python (Sujet 1). */
    public double getDynamicTSla(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getDynamicTSla() : 0.0;
    }

    /** Seuil de popularité adaptatif courant (P_SLA normalisé), pour monitoring Python (Sujet 1). */
    public double getDynamicMinPopularity(boolean complex) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        return env != null ? env.getDynamicMinPopularity() : 1.0;
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
        // Identité de l'agent : si elle change (ex: Q-Learning puis Rainbow dans la même
        // session serveur), recréer les environnements pour que chaque agent charge et
        // sauvegarde SES Q-tables de méta-seuils — jamais celles de l'autre.
        Object tag = p.get("agentTag");
        if (tag instanceof String s && !s.isBlank() && !s.equals(agentTag)) {
            agentTag = s;
            // Chaque agent part des DÉFAUTS + SES overrides : sans ce reset, le second
            // agent entraîné dans la même session hériterait silencieusement des poids
            // de récompense du premier pour toute clé qu'il ne renvoie pas
            // (ex: rewardCostOver=25 de Rainbow contaminerait Q-Learning).
            settings = new TrainingSettings();
            simpleEnv = null;
            complexEnv = null;
        }
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
        Object rCLin = p.get("rewardCostLinear");     if (rCLin instanceof Number n) settings.setRewardCostLinear(n.doubleValue());
        Object rRepl = p.get("rewardReplCost");       if (rRepl instanceof Number n) settings.setRewardReplCost(n.doubleValue());
        Object rPrem = p.get("rewardPrematureRepl");   if (rPrem instanceof Number n) settings.setRewardPrematureRepl(n.doubleValue());
        Object rDel  = p.get("rewardPrematureDelete"); if (rDel  instanceof Number n) settings.setRewardPrematureDelete(n.doubleValue());
        Object rThr  = p.get("rewardThrash");          if (rThr  instanceof Number n) settings.setRewardThrash(n.doubleValue());
        Object rMnt  = p.get("rewardMaintenance");    if (rMnt  instanceof Number n) settings.setRewardMaintenance(n.doubleValue());
        Object rInv  = p.get("rewardInvalid");        if (rInv  instanceof Number n) settings.setRewardInvalid(n.doubleValue());
        Object rPop  = p.get("rewardLowPopularity");  if (rPop  instanceof Number n) settings.setRewardLowPopularity(n.doubleValue());
        Object rCorr = p.get("rewardCorrectTrigger"); if (rCorr instanceof Number n) settings.setRewardCorrectTrigger(n.doubleValue());
        Object rHold = p.get("rewardUnpopularHolding"); if (rHold instanceof Number n) settings.setRewardUnpopularHolding(n.doubleValue());
        // Persiste la configuration de récompense de CET agent après application des
        // overrides Python (ex: --reward-cost-over) — rechargée par le benchmark.
        saveRewardConfig();
    }

    /** Reset structuré (retourne un objet avec state + info). */
    public TrainingResetResult resetStructured(boolean complex, long seed) {
        TrainingEnvironment env = complex ? complexEnv : simpleEnv;
        if (env == null || env.isDifferentSettings(settings)) {
            env = createEnvironment(seed, complex);
        } else {
            // Changer la seed sans recréer l'environnement : préserve la méta-adaptation
            // TSLA (dynamicTSla, replicationState) entre épisodes.
            env.setSeed(seed);
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
