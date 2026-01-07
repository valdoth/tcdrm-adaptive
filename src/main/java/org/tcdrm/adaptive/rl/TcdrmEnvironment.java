package org.tcdrm.adaptive.rl;

import java.util.List;
import java.util.Random;

/**
 * Environnement TCDRM pour l'apprentissage par renforcement
 * Implémente l'interface Environment inspirée de Gymnasium
 */
public class TcdrmEnvironment implements Environment<TcdrmState, TcdrmAction> {
    
    // Paramètres de l'environnement
    private static final int MAX_QUERIES = 1000;
    private static final double INITIAL_BUDGET = 100.0;
    private static final double SLA_LATENCY_THRESHOLD = 150.0;  // ms
    private static final int MAX_REPLICAS = 3;
    
    // Coûts (conformes à l'article)
    private static final double COST_BW_INTRA_DC = 0.002;
    private static final double COST_BW_INTER_PROVIDER = 0.01;
    private static final double STORAGE_COST_PER_GB_PER_HOUR = 0.008 / 720.0;
    private static final double REPLICATION_COST_PER_GB = COST_BW_INTER_PROVIDER;
    
    // Paramètres réseau
    private static final double BW_LOCAL_GBPS = 10.0;
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_LOCAL_MS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;
    
    // État de l'environnement
    private double currentBudget;
    private double currentLatency;
    private int accessCount;
    private int currentReplicaCount;
    private int currentQuery;
    private double dataGb;
    private Random random;
    
    public TcdrmEnvironment(double dataGb) {
        this.dataGb = dataGb;
        this.random = new Random();
    }
    
    @Override
    public TcdrmState reset(Long seed) {
        if (seed != null) {
            random = new Random(seed);
        }
        
        currentBudget = INITIAL_BUDGET;
        currentLatency = LAT_REMOTE_MS;  // Commence avec latence élevée (pas de réplica)
        accessCount = 0;
        currentReplicaCount = 0;
        currentQuery = 0;
        
        return getCurrentState();
    }
    
    @Override
    public StepResult<TcdrmState> step(TcdrmAction action) {
        // Sauvegarder l'état précédent
        int previousReplicaCount = currentReplicaCount;
        
        // Exécuter l'action
        boolean actionExecuted = executeAction(action);
        
        // Simuler une requête
        double queryLatency = simulateQuery();
        double queryCost = calculateQueryCost();
        
        // Mettre à jour l'état
        currentBudget -= queryCost;
        currentLatency = queryLatency;
        accessCount++;
        currentQuery++;
        
        // Calculer la récompense
        double reward = calculateReward(action, actionExecuted, previousReplicaCount, queryCost);
        
        // Vérifier si l'épisode est terminé
        boolean terminated = currentQuery >= MAX_QUERIES;
        boolean truncated = currentBudget <= 0;
        
        // Informations supplémentaires
        String info = String.format("Query %d: Action=%s, Cost=%.4f, Budget=%.2f, Latency=%.2f, Replicas=%d",
            currentQuery, action, queryCost, currentBudget, currentLatency, currentReplicaCount);
        
        TcdrmState nextState = getCurrentState();
        
        return new StepResult<>(nextState, reward, terminated, truncated, info);
    }
    
    /**
     * Exécute l'action choisie par l'agent
     */
    private boolean executeAction(TcdrmAction action) {
        switch (action) {
            case CREATE_REPLICA:
                if (currentReplicaCount < MAX_REPLICAS) {
                    double creationCost = dataGb * REPLICATION_COST_PER_GB;
                    if (currentBudget >= creationCost) {
                        currentReplicaCount++;
                        currentBudget -= creationCost;
                        return true;
                    }
                }
                return false;
                
            case DELETE_REPLICA:
                if (currentReplicaCount > 0) {
                    currentReplicaCount--;
                    return true;
                }
                return false;
                
            case DO_NOTHING:
            default:
                return true;
        }
    }
    
    /**
     * Simule une requête et retourne la latence
     */
    private double simulateQuery() {
        if (currentReplicaCount > 0) {
            // Accès local avec probabilité basée sur le nombre de réplicas
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = random.nextDouble() < localProbability;
            
            if (useLocal) {
                return LAT_LOCAL_MS + (random.nextDouble() * 2 - 1) * 0.5;
            }
        }
        
        // Accès distant avec variabilité
        return LAT_REMOTE_MS * (1.0 + (random.nextDouble() * 2 - 1) * 0.15);
    }
    
    /**
     * Calcule le coût de la requête actuelle
     */
    private double calculateQueryCost() {
        double transferCost;
        
        if (currentReplicaCount > 0) {
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = random.nextDouble() < localProbability;
            transferCost = dataGb * (useLocal ? COST_BW_INTRA_DC : COST_BW_INTER_PROVIDER);
        } else {
            transferCost = dataGb * COST_BW_INTER_PROVIDER;
        }
        
        // Coût de stockage proportionnel au nombre de réplicas
        double storageCost = currentReplicaCount * dataGb * STORAGE_COST_PER_GB_PER_HOUR;
        
        return transferCost + storageCost;
    }
    
    /**
     * Calcule la récompense pour l'action prise
     * Récompense = Économies - Pénalités
     */
    private double calculateReward(TcdrmAction action, boolean actionExecuted, 
                                   int previousReplicaCount, double queryCost) {
        double reward = 0.0;
        
        // Récompense pour réduction de latence
        if (currentLatency < SLA_LATENCY_THRESHOLD) {
            reward += 5.0;  // Bonus pour respect du SLA
        }
        
        // Récompense pour économies de bande passante
        if (currentReplicaCount > 0 && currentLatency < LAT_REMOTE_MS) {
            double savings = dataGb * (COST_BW_INTER_PROVIDER - COST_BW_INTRA_DC);
            reward += savings * 10.0;  // Amplifier les économies
        }
        
        // Pénalité pour dépassement de budget
        if (currentBudget < INITIAL_BUDGET * 0.2) {
            reward -= 20.0;  // Budget critique
        }
        if (currentBudget <= 0) {
            reward -= 100.0;  // Budget épuisé (très mauvais)
        }
        
        // Pénalité pour latence élevée
        if (currentLatency > SLA_LATENCY_THRESHOLD) {
            double violation = (currentLatency - SLA_LATENCY_THRESHOLD) / SLA_LATENCY_THRESHOLD;
            reward -= 10.0 * violation;
        }
        
        // Pénalité pour action non exécutée
        if (!actionExecuted && action != TcdrmAction.DO_NOTHING) {
            reward -= 5.0;
        }
        
        // Récompense pour suppression de réplica inutile
        if (action == TcdrmAction.DELETE_REPLICA && actionExecuted && accessCount < 150) {
            reward += 3.0;  // Bon choix de supprimer si peu d'accès
        }
        
        // Pénalité pour trop de réplicas
        if (currentReplicaCount > 2) {
            reward -= 2.0 * (currentReplicaCount - 2);
        }
        
        return reward;
    }
    
    /**
     * Retourne l'état actuel de l'environnement
     */
    private TcdrmState getCurrentState() {
        double budgetRatio = currentBudget / INITIAL_BUDGET;
        return TcdrmState.fromContinuous(budgetRatio, currentLatency, accessCount, currentReplicaCount);
    }
    
    @Override
    public int getActionSpaceSize() {
        return TcdrmAction.getActionSpaceSize();
    }
    
    @Override
    public int getStateSpaceSize() {
        return 3 * 3 * 3 * 4;  // 108 états
    }
    
    @Override
    public void close() {
        // Libérer les ressources si nécessaire
    }
    
    // Getters pour inspection
    public double getCurrentBudget() { return currentBudget; }
    public double getCurrentLatency() { return currentLatency; }
    public int getAccessCount() { return accessCount; }
    public int getCurrentReplicaCount() { return currentReplicaCount; }
    public int getCurrentQuery() { return currentQuery; }
}
