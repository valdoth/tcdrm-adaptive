package org.tcdrm.adaptive.training;

import py4j.GatewayServer;

import java.net.InetAddress;

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
    
    /**
     * Crée un nouvel environnement d'entraînement.
     * 
     * @param seed Graine aléatoire pour reproductibilité
     * @param complex true pour requêtes complexes, false pour simples
     * @return L'environnement créé
     */
    public TrainingEnvironment createEnvironment(long seed, boolean complex) {
        if (complex) {
            complexEnv = new TrainingEnvironment(seed, true);
            return complexEnv;
        } else {
            simpleEnv = new TrainingEnvironment(seed, false);
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
    
    /**
     * Point d'entrée pour démarrer le serveur d'entraînement.
     */
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 25335;
        
        System.out.println("=".repeat(70));
        System.out.println("TCDRM TRAINING SERVER - CloudSimPlus Environment for RL");
        System.out.println("=".repeat(70));
        
        try {
            TrainingServer server = new TrainingServer();
            
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
}
