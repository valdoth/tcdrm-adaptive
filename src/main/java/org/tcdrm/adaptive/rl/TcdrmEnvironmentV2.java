package org.tcdrm.adaptive.rl;

import java.util.Random;

/**
 * Environnement TCDRM V2 avec améliorations :
 * 1. Fonction de récompense ajustée (pénalités plus fortes)
 * 2. Budget adaptatif proportionnel à la taille des données
 * 3. Meilleure gestion des coûts
 */
public class TcdrmEnvironmentV2 implements Environment<TcdrmState, TcdrmAction> {
    
    // Paramètres de l'environnement
    private static final int MAX_QUERIES = 2000;
    private static final double BASE_BUDGET = 150.0;
    private static final double BUDGET_PER_GB = 10.0;  // Budget additionnel par GB
    private static final double SLA_LATENCY_THRESHOLD = 150.0;  // ms
    private static final int MAX_REPLICAS = 3;
    
    // Coûts (conformes à l'article)
    private static final double COST_BW_INTRA_DC = 0.002;
    private static final double COST_BW_INTER_PROVIDER = 0.10;
    private static final double STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0;
    private static final double REPLICATION_COST_PER_GB = COST_BW_INTER_PROVIDER;
    
    // Paramètres réseau
    private static final double BW_LOCAL_GBPS = 10.0;
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_LOCAL_MS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;
    
    // État de l'environnement
    private double initialBudget;
    private double currentBudget;
    private double currentLatency;
    private int accessCount;
    private int currentReplicaCount;
    private int currentQuery;
    private double dataGb;
    private Random random;
    
    public TcdrmEnvironmentV2(double dataGb) {
        this.dataGb = dataGb;
        this.random = new Random();
        // Budget adaptatif : base + proportionnel à la taille
        this.initialBudget = BASE_BUDGET + (dataGb * BUDGET_PER_GB);
    }
    
    @Override
    public TcdrmState reset(Long seed) {
        if (seed != null) {
            random = new Random(seed);
        }
        
        currentBudget = initialBudget;
        currentLatency = LAT_REMOTE_MS;
        accessCount = 0;
        currentReplicaCount = 0;
        currentQuery = 0;
        
        return getCurrentState();
    }
    
    @Override
    public StepResult<TcdrmState> step(TcdrmAction action) {
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
        
        // Calculer la récompense (V2 : ajustée)
        double reward = calculateRewardV2(action, actionExecuted, previousReplicaCount, queryCost);
        
        // Vérifier si l'épisode est terminé
        boolean terminated = currentQuery >= MAX_QUERIES;
        boolean truncated = currentBudget <= 0;
        
        String info = String.format("Query %d: Action=%s, Cost=%.4f, Budget=%.2f, Latency=%.2f, Replicas=%d",
            currentQuery, action, queryCost, currentBudget, currentLatency, currentReplicaCount);
        
        TcdrmState nextState = getCurrentState();
        
        return new StepResult<>(nextState, reward, terminated, truncated, info);
    }
    
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
    
    private double simulateQuery() {
        if (currentReplicaCount > 0) {
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = random.nextDouble() < localProbability;
            
            if (useLocal) {
                return LAT_LOCAL_MS + (random.nextDouble() * 2 - 1) * 0.5;
            }
        }
        
        return LAT_REMOTE_MS * (1.0 + (random.nextDouble() * 2 - 1) * 0.15);
    }
    
    private double calculateQueryCost() {
        double transferCost;
        
        if (currentReplicaCount > 0) {
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = random.nextDouble() < localProbability;
            transferCost = dataGb * (useLocal ? COST_BW_INTRA_DC : COST_BW_INTER_PROVIDER);
        } else {
            transferCost = dataGb * COST_BW_INTER_PROVIDER;
        }
        
        double storageCost = currentReplicaCount * dataGb * STORAGE_COST_PER_GB_PER_HOUR;
        
        return transferCost + storageCost;
    }
    
    /**
     * Fonction de récompense V2 : Ajustée pour meilleure performance
     * - Pénalités budgétaires plus fortes
     * - Récompenses pour économies amplifiées
     * - Meilleur équilibre performance/coût
     */
    private double calculateRewardV2(TcdrmAction action, boolean actionExecuted, 
                                     int previousReplicaCount, double queryCost) {
        double reward = 0.0;
        
        // Récompense pour respect du SLA
        if (currentLatency < SLA_LATENCY_THRESHOLD) {
            reward += 5.0;
        }
        
        // Récompense pour économies de bande passante (AMPLIFIÉE)
        if (currentReplicaCount > 0 && currentLatency < LAT_REMOTE_MS) {
            double savings = dataGb * (COST_BW_INTER_PROVIDER - COST_BW_INTRA_DC);
            reward += savings * 20.0;  // Amplifié de 10.0 à 20.0
        }
        
        // Pénalité pour budget critique (RENFORCÉE)
        double budgetRatio = currentBudget / initialBudget;
        if (budgetRatio < 0.2) {
            reward -= 30.0;  // Augmenté de 20.0 à 30.0
        }
        
        // Pénalité pour budget épuisé (TRÈS FORTE)
        if (currentBudget <= 0) {
            reward -= 200.0;  // Augmenté de 100.0 à 200.0
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
        
        // Récompense pour suppression de réplica inutile (AMPLIFIÉE)
        if (action == TcdrmAction.DELETE_REPLICA && actionExecuted && accessCount < 150) {
            reward += 5.0;  // Augmenté de 3.0 à 5.0
        }
        
        // Pénalité pour trop de réplicas
        if (currentReplicaCount > 2) {
            reward -= 2.0 * (currentReplicaCount - 2);
        }
        
        // Bonus pour bonne gestion budgétaire
        if (budgetRatio > 0.5 && currentLatency < SLA_LATENCY_THRESHOLD) {
            reward += 3.0;  // Nouveau bonus
        }
        
        return reward;
    }
    
    private TcdrmState getCurrentState() {
        double budgetRatio = currentBudget / initialBudget;
        return TcdrmState.fromContinuous(budgetRatio, currentLatency, accessCount, currentReplicaCount);
    }
    
    @Override
    public int getActionSpaceSize() {
        return TcdrmAction.getActionSpaceSize();
    }
    
    @Override
    public int getStateSpaceSize() {
        return 3 * 3 * 3 * 4;
    }
    
    @Override
    public void close() {
    }
    
    // Getters
    public double getCurrentBudget() { return currentBudget; }
    public double getInitialBudget() { return initialBudget; }
    public double getCurrentLatency() { return currentLatency; }
    public int getAccessCount() { return accessCount; }
    public int getCurrentReplicaCount() { return currentReplicaCount; }
    public int getCurrentQuery() { return currentQuery; }
}
