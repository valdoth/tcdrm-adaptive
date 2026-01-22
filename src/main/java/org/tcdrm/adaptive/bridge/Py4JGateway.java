package org.tcdrm.adaptive.bridge;

import org.tcdrm.adaptive.rl.TcdrmEnvironment;
import org.tcdrm.adaptive.rl.TcdrmAction;
import org.tcdrm.adaptive.rl.TcdrmState;
import org.tcdrm.adaptive.rl.Environment.StepResult;
import py4j.GatewayServer;

/**
 * Py4J Gateway pour exposer l'environnement TCDRM à Python
 * Permet aux agents RL Python de contrôler l'environnement Java
 */
public class Py4JGateway {
    
    private TcdrmEnvironment environment;
    private GatewayServer gatewayServer;
    
    /**
     * Crée un nouveau gateway Py4J
     */
    public Py4JGateway() {
        System.out.println("Py4J Gateway initialized");
    }
    
    /**
     * Crée un nouvel environnement TCDRM
     * @param dataGb Taille des données en GB
     * @return L'environnement créé
     */
    public TcdrmEnvironment createEnvironment(double dataGb) {
        this.environment = new TcdrmEnvironment(dataGb);
        System.out.println("Created TCDRM environment with " + dataGb + " GB data");
        return this.environment;
    }
    
    /**
     * Obtient l'environnement actuel
     * @return L'environnement TCDRM
     */
    public TcdrmEnvironment getEnvironment() {
        return this.environment;
    }
    
    /**
     * Reset l'environnement
     * @param seed Seed pour la génération aléatoire (peut être null)
     * @return L'état initial
     */
    public TcdrmState reset(Long seed) {
        if (environment == null) {
            throw new IllegalStateException("Environment not created. Call createEnvironment() first.");
        }
        return environment.reset(seed);
    }
    
    /**
     * Exécute une action dans l'environnement
     * @param actionIndex Index de l'action (0=CREATE_REPLICA, 1=DELETE_REPLICA, 2=DO_NOTHING)
     * @return Résultat du step
     */
    public StepResult<TcdrmState> step(int actionIndex) {
        if (environment == null) {
            throw new IllegalStateException("Environment not created. Call createEnvironment() first.");
        }
        
        TcdrmAction action;
        switch (actionIndex) {
            case 0:
                action = TcdrmAction.CREATE_REPLICA;
                break;
            case 1:
                action = TcdrmAction.DELETE_REPLICA;
                break;
            case 2:
                action = TcdrmAction.DO_NOTHING;
                break;
            default:
                throw new IllegalArgumentException("Invalid action index: " + actionIndex);
        }
        
        return environment.step(action);
    }
    
    /**
     * Obtient la taille de l'espace d'actions
     * @return Nombre d'actions possibles
     */
    public int getActionSpaceSize() {
        if (environment == null) {
            return 3; // Par défaut
        }
        return environment.getActionSpaceSize();
    }
    
    /**
     * Obtient la taille de l'espace d'états
     * @return Nombre d'états possibles
     */
    public int getStateSpaceSize() {
        if (environment == null) {
            return 108; // Par défaut
        }
        return environment.getStateSpaceSize();
    }
    
    /**
     * Obtient le budget actuel
     * @return Budget actuel
     */
    public double getCurrentBudget() {
        if (environment == null) {
            throw new IllegalStateException("Environment not created.");
        }
        return environment.getCurrentBudget();
    }
    
    /**
     * Obtient la latence actuelle
     * @return Latence actuelle en ms
     */
    public double getCurrentLatency() {
        if (environment == null) {
            throw new IllegalStateException("Environment not created.");
        }
        return environment.getCurrentLatency();
    }
    
    /**
     * Obtient le nombre de réplicas actuels
     * @return Nombre de réplicas
     */
    public int getCurrentReplicaCount() {
        if (environment == null) {
            throw new IllegalStateException("Environment not created.");
        }
        return environment.getCurrentReplicaCount();
    }
    
    /**
     * Ferme l'environnement
     */
    public void close() {
        if (environment != null) {
            environment.close();
            System.out.println("Environment closed");
        }
    }
    
    /**
     * Démarre le serveur Py4J
     * @param port Port du serveur Java (par défaut 25333)
     */
    public void startServer(int port) {
        gatewayServer = new GatewayServer(this, port);
        gatewayServer.start();
        System.out.println("Py4J Gateway Server started on port " + port);
        System.out.println("Python can now connect to this gateway");
    }
    
    /**
     * Démarre le serveur Py4J avec le port par défaut
     */
    public void startServer() {
        startServer(25333);
    }
    
    /**
     * Arrête le serveur Py4J
     */
    public void stopServer() {
        if (gatewayServer != null) {
            gatewayServer.shutdown();
            System.out.println("Py4J Gateway Server stopped");
        }
    }
    
    /**
     * Point d'entrée principal pour démarrer le gateway
     */
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("TCDRM-ADAPTIVE Py4J Gateway");
        System.out.println("=".repeat(60));
        System.out.println();
        
        // Créer et démarrer le gateway
        Py4JGateway gateway = new Py4JGateway();
        
        // Déterminer le port
        int port = 25333;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.err.println("Using default port: 25333");
            }
        }
        
        gateway.startServer(port);
        
        System.out.println();
        System.out.println("Gateway is ready!");
        System.out.println("Python can now connect using:");
        System.out.println("  from py4j.java_gateway import JavaGateway");
        System.out.println("  gateway = JavaGateway()");
        System.out.println("  tcdrm = gateway.entry_point");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the gateway");
        
        // Ajouter un shutdown hook pour nettoyer proprement
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down gateway...");
            gateway.close();
            gateway.stopServer();
        }));
        
        // Garder le programme en vie
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Gateway interrupted");
        }
    }
}
