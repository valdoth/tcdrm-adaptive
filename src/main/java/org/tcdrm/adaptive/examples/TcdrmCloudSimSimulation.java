package org.tcdrm.adaptive.examples;

import org.tcdrm.adaptive.cloudsim.TcdrmCloudSimEnvironment;
import org.tcdrm.adaptive.cloudsim.RLPolicyAdapter;
import org.tcdrm.adaptive.rl.TcdrmAction;
import org.tcdrm.adaptive.rl.TcdrmState;
import py4j.GatewayServer;

/**
 * Exemple de simulation CloudSim utilisant un modèle Q-Learning Python
 * 
 * Usage:
 * 1. Compiler: mvn clean package
 * 2. Démarrer ce programme Java (gateway Py4J)
 * 3. Exécuter le script Python avec le modèle:
 *    uv run python simulate_with_cloudsim.py --model models/best_model.pkl
 */
public class TcdrmCloudSimSimulation {
    
    private TcdrmCloudSimEnvironment cloudSimEnv;
    private RLPolicyAdapter rlAdapter;
    private GatewayServer gatewayServer;
    
    public TcdrmCloudSimSimulation() {
        this.rlAdapter = new RLPolicyAdapter();
    }
    
    /**
     * Crée l'environnement CloudSim
     */
    public TcdrmCloudSimEnvironment createCloudSimEnvironment(double dataGb) {
        System.out.println("Création de l'environnement CloudSim avec " + dataGb + " GB");
        this.cloudSimEnv = new TcdrmCloudSimEnvironment(dataGb);
        return this.cloudSimEnv;
    }
    
    /**
     * Obtient l'adaptateur RL (pour que Python puisse enregistrer l'agent)
     */
    public RLPolicyAdapter getRLAdapter() {
        return this.rlAdapter;
    }
    
    /**
     * Exécute une simulation complète avec le modèle RL Python
     */
    public void runSimulationWithRLModel(double dataGb, int numQueries) {
        System.out.println("\n=== Simulation CloudSim avec Modèle Q-Learning ===");
        System.out.println("Données: " + dataGb + " GB");
        System.out.println("Requêtes: " + numQueries);
        System.out.println();
        
        // Vérifier que l'agent Python est connecté
        if (!rlAdapter.isConnected()) {
            System.err.println("ERREUR: Agent Python non connecté!");
            System.err.println("Démarrez le script Python: simulate_with_cloudsim.py");
            return;
        }
        
        // Reset l'environnement
        TcdrmState state = cloudSimEnv.reset(42L);
        
        // Boucle de simulation
        for (int query = 0; query < numQueries; query++) {
            // Obtenir l'action du modèle Python
            TcdrmAction action = rlAdapter.getBestAction(state);
            
            // Exécuter l'action dans CloudSim
            TcdrmCloudSimEnvironment.StepResult result = cloudSimEnv.step(action);
            
            // Affichage périodique
            if ((query + 1) % 100 == 0) {
                System.out.println(String.format(
                    "Requête %d/%d | Action: %s | Récompense: %.2f",
                    query + 1, numQueries, action, result.getReward()
                ));
            }
            
            // Passer à l'état suivant
            state = result.getNextState();
            
            // Vérifier si terminé
            if (result.isDone()) {
                System.out.println("Simulation terminée à la requête " + (query + 1));
                break;
            }
        }
        
        // Exécuter la simulation CloudSim complète
        cloudSimEnv.runSimulation();
        
        // Afficher les résultats
        cloudSimEnv.printResults();
    }
    
    /**
     * Démarre le serveur Py4J
     */
    public void startGatewayServer(int port) {
        gatewayServer = new GatewayServer(this, port);
        gatewayServer.start();
        System.out.println("Py4J Gateway Server démarré sur le port " + port);
        System.out.println("En attente de connexion Python...");
        System.out.println();
        System.out.println("Démarrez maintenant le script Python:");
        System.out.println("  cd python_rl");
        System.out.println("  uv run python simulate_with_cloudsim.py --model results/qlearning/.../models/best_model.pkl");
        System.out.println();
    }
    
    /**
     * Arrête le serveur Py4J
     */
    public void stopGatewayServer() {
        if (gatewayServer != null) {
            gatewayServer.shutdown();
            System.out.println("Py4J Gateway Server arrêté");
        }
    }
    
    /**
     * Point d'entrée principal
     */
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("TCDRM-ADAPTIVE: Simulation CloudSim avec Modèle Q-Learning Python");
        System.out.println("=".repeat(70));
        System.out.println();
        
        // Créer la simulation
        TcdrmCloudSimSimulation simulation = new TcdrmCloudSimSimulation();
        
        // Déterminer le port
        int port = 25333;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Port invalide: " + args[0]);
                System.err.println("Utilisation du port par défaut: 25333");
            }
        }
        
        // Démarrer le gateway Py4J
        simulation.startGatewayServer(port);
        
        // Ajouter un shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nArrêt du gateway...");
            simulation.stopGatewayServer();
        }));
        
        // Garder le programme en vie
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Simulation interrompue");
        }
    }
}
