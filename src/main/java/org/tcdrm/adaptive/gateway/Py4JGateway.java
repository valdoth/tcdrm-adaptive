package org.tcdrm.adaptive.gateway;

import org.tcdrm.adaptive.rl.PythonRLBridge;
import py4j.CallbackClient;
import py4j.GatewayServer;

import java.net.InetAddress;

/**
 * Gateway Py4J pour connecter Java et Python
 * Java démarre le GatewayServer avec CallbackClient, Python se connecte
 */
public class Py4JGateway {
    
    private GatewayServer gatewayServer;
    private PythonRLBridge pythonBridge;  // Instance du pont Python
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
            // Configurer le GatewayServer
            InetAddress address = InetAddress.getByName("0.0.0.0");
            InetAddress callbackAddress = InetAddress.getByName("127.0.0.1");  // localhost pour callback
            
            gatewayServer = new GatewayServer(
                this,                                          // Entry point (le Gateway lui-même)
                port,                                           // Gateway port (25333)
                address,                                        // Listen on all interfaces
                GatewayServer.DEFAULT_CONNECT_TIMEOUT,         // Connect timeout
                GatewayServer.DEFAULT_READ_TIMEOUT,            // Read timeout
                null,                                          // Custom commands
                new CallbackClient(                            // Callback client for Python callbacks
                    GatewayServer.DEFAULT_PYTHON_PORT,         // Python callback port (25334)
                    callbackAddress                            // Callback address (localhost)
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
     * Enregistre le pont Python (appelé depuis Python)
     */
    public void registerPythonBridge(Object bridge) {
        this.pythonBridge = (PythonRLBridge) bridge;
        System.out.println("✅ Instance du pont Python enregistrée");
    }
    
    /**
     * Retourne le pont Python
     */
    public PythonRLBridge getPythonBridge() {
        return pythonBridge;
    }
    
    /**
     * Vérifie si le gateway est actif
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Attend que Python enregistre son pont
     * @param timeoutSeconds Timeout en secondes
     * @return true si le pont est enregistré, false sinon
     */
    public boolean waitForPythonBridge(int timeoutSeconds) {
        int checkIntervalMs = 100;
        int totalChecks = timeoutSeconds * (1000 / checkIntervalMs);
        
        for (int i = 0; i < totalChecks; i++) {
            if (pythonBridge != null) {
                return true;
            }
            
            try {
                Thread.sleep(checkIntervalMs);
                if (i % 100 == 0 && i > 0) {
                    System.out.println("   ... toujours en attente (" + (i / 10) + "s/" + timeoutSeconds + "s)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        System.err.println("❌ Timeout: Le pont Python n'a pas été enregistré après " + timeoutSeconds + "s");
        return false;
    }
}
