package org.tcdrm.adaptive.gateway;

import org.tcdrm.adaptive.rl.PythonQLearningAgent;
import py4j.CallbackClient;
import py4j.GatewayServer;

import java.net.InetAddress;

/**
 * Gateway Py4J pour connecter Java et Python
 * Architecture basée sur rl-cloudsimplus-greenscheduling
 * Java démarre le GatewayServer avec CallbackClient, Python se connecte
 */
public class Py4JGateway {
    
    private GatewayServer gatewayServer;
    private PythonQLearningAgent pythonAgent;
    private boolean isRunning = false;
    
    /**
     * Démarre le serveur Py4J sur un port spécifique
     * Implémentation exacte de rl-cloudsimplus-greenscheduling
     */
    public void start(int port) {
        if (isRunning) {
            System.out.println("⚠️  Gateway déjà démarré");
            return;
        }
        
        try {
            // Créer l'entry point (sera accessible depuis Python)
            pythonAgent = new PythonQLearningAgent();
            
            // Configurer le GatewayServer exactement comme rl-cloudsimplus-greenscheduling
            InetAddress address = InetAddress.getByName("0.0.0.0");
            
            gatewayServer = new GatewayServer(
                pythonAgent,                                    // Entry point
                port,                                           // Gateway port (25333)
                address,                                        // Listen on all interfaces
                GatewayServer.DEFAULT_CONNECT_TIMEOUT,         // Connect timeout
                GatewayServer.DEFAULT_READ_TIMEOUT,            // Read timeout
                null,                                          // Custom commands
                new CallbackClient(                            // Callback client for Python callbacks
                    GatewayServer.DEFAULT_PYTHON_PORT,         // Python callback port (25334)
                    address                                    // Callback address
                )
            );
            
            System.out.println("🚀 Démarrage du Py4J Gateway sur " + address.getHostAddress() + ":" + port);
            gatewayServer.start();
            
            isRunning = true;
            System.out.println("✅ Py4J Gateway démarré et prêt");
            System.out.println("📡 En attente des connexions Python...");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du démarrage du Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Démarre le serveur sur le port par défaut (25333)
     */
    public void start() {
        start(25333);
    }
    
    /**
     * Arrête le serveur Py4J Gateway
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        try {
            if (gatewayServer != null) {
                gatewayServer.shutdown();
                System.out.println("✅ Py4J Gateway arrêté");
            }
            isRunning = false;
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'arrêt du Gateway: " + e.getMessage());
        }
    }
    
    /**
     * Retourne l'agent Python connecté
     */
    public PythonQLearningAgent getPythonAgent() {
        return pythonAgent;
    }
    
    /**
     * Vérifie si le gateway est actif
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Attend que Python enregistre son modèle
     * @param timeoutSeconds Timeout en secondes
     * @return true si le modèle est enregistré, false sinon
     */
    public boolean waitForPythonModel(int timeoutSeconds) {
        System.out.println("⏳ Attente de l'enregistrement du modèle Python...");
        
        int elapsed = 0;
        int checkIntervalMs = 100; // Vérifier toutes les 100ms pour permettre au GatewayServer de traiter les requêtes
        int checksPerSecond = 1000 / checkIntervalMs;
        int totalChecks = timeoutSeconds * checksPerSecond;
        
        for (int i = 0; i < totalChecks; i++) {
            if (pythonAgent != null && pythonAgent.isModelLoaded()) {
                System.out.println("✅ Modèle Python enregistré: " + pythonAgent.getModelInfo());
                return true;
            }
            
            try {
                Thread.sleep(checkIntervalMs);
                elapsed = i / checksPerSecond;
                if (i % (5 * checksPerSecond) == 0 && i > 0) {
                    System.out.println("   ... attente (" + elapsed + "s/" + timeoutSeconds + "s)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        System.err.println("❌ Timeout: Le modèle Python n'a pas été enregistré après " + timeoutSeconds + "s");
        return false;
    }
}
