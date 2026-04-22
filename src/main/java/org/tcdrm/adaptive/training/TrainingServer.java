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
        Object ps = p.get("popularityStrategy");
        if (ps instanceof String s) try { settings.getClass().getMethod("setPopularityStrategy", String.class).invoke(settings, s); } catch (Exception ignore) {}
        Object w = p.get("tinyLfuWidth");
        if (w instanceof Number n) try { settings.getClass().getMethod("setTinyLfuWidth", int.class).invoke(settings, n.intValue()); } catch (Exception ignore) {}
        Object d = p.get("tinyLfuDepth");
        if (d instanceof Number n) try { settings.getClass().getMethod("setTinyLfuDepth", int.class).invoke(settings, n.intValue()); } catch (Exception ignore) {}
        Object ap = p.get("tinyLfuAgingPeriod");
        if (ap instanceof Number n) try { settings.getClass().getMethod("setTinyLfuAgingPeriod", int.class).invoke(settings, n.intValue()); } catch (Exception ignore) {}
        Object th = p.get("tinyLfuTauHi");
        if (th instanceof Number n) try { settings.getClass().getMethod("setTinyLfuTauHi", double.class).invoke(settings, n.doubleValue()); } catch (Exception ignore) {}
        Object tl = p.get("tinyLfuTauLo");
        if (tl instanceof Number n) try { settings.getClass().getMethod("setTinyLfuTauLo", double.class).invoke(settings, n.doubleValue()); } catch (Exception ignore) {}
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
