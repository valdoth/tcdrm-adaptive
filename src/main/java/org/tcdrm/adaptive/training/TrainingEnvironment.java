package org.tcdrm.adaptive.training;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.simulation.TcdrmSimulation;

/**
 * Environnement d'entraînement pour les agents RL.
 * 
 * Expose les méthodes nécessaires à Gymnasium (Python):
 * - reset(): réinitialise l'environnement et retourne l'état initial
 * - step(action): exécute une action et retourne (état, récompense, done, info)
 * 
 * Les simulations sont exécutées dans CloudSimPlus, garantissant que
 * l'entraînement et l'inférence utilisent exactement le même environnement.
 */
public class TrainingEnvironment {
    
    private TcdrmSimulation simulation;
    private final long seed;
    private final boolean complex;
    
    private int currentQuery;
    private double lastLatency;
    private double lastCost;
    private double tSla;
    private double cumulativeReward;
    
    public TrainingEnvironment(long seed, boolean complex) {
        this.seed = seed;
        this.complex = complex;
        this.tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        reset();
    }
    
    /**
     * Réinitialise l'environnement et retourne l'état initial.
     * 
     * @return État initial [8 dimensions]
     */
    public double[] reset() {
        this.simulation = new TcdrmSimulation(seed, complex);
        this.currentQuery = 0;
        this.lastLatency = 0.0;
        this.lastCost = 0.0;
        this.cumulativeReward = 0.0;
        
        return getState();
    }
    
    /**
     * Exécute une action et retourne le résultat.
     * 
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     * @return StepResult contenant (état, récompense, done, info)
     */
    public StepResult step(int action) {
        // Exécuter la requête avec l'action
        TcdrmSimulation.QueryResult result = simulation.executeRLQuery(action);
        currentQuery++;
        
        lastLatency = result.queryTimeMs();
        lastCost = result.totalCost();
        
        // Calculer la récompense
        double reward = calculateReward(result, action);
        cumulativeReward += reward;
        
        // Vérifier si l'épisode est terminé
        boolean done = (currentQuery >= TcdrmConstants.MAX_QUERIES);
        
        // Construire l'info
        String info = String.format(
            "query=%d,latency=%.2f,cost=%.4f,replicas=%d,reward=%.2f",
            currentQuery, lastLatency, lastCost, result.replicaCount(), reward
        );
        
        return new StepResult(getState(), reward, done, info);
    }
    
    /**
     * Retourne l'état actuel de l'environnement.
     * 
     * Format: [latency, budget, replicas, popularity, cost, t_sla_violation, c_sla_violation, progress]
     */
    public double[] getState() {
        return simulation.buildRLState(lastLatency, lastCost);
    }
    
    /**
     * Calcule la récompense pour une action.
     * 
     * Récompense basée sur:
     * - Latence basse = récompense positive
     * - Violation SLA = pénalité
     * - Réplication = petit coût (encourage la parcimonie)
     * - Bonus si réplication réduit la latence
     */
    private double calculateReward(TcdrmSimulation.QueryResult result, int action) {
        double latency = result.queryTimeMs();
        
        // Récompense de base: inversement proportionnelle à la latence
        double latencyReward = 10.0 * (1.0 - Math.min(1.0, latency / 10000.0));
        
        // Pénalité pour violation SLA
        double slaPenalty = 0.0;
        if (latency > tSla) {
            slaPenalty = 5.0;
        }
        
        // Petit coût pour réplication
        double replicationCost = 0.0;
        if (action == 1) {
            replicationCost = 0.5;
        }
        
        // Bonus si réplication quand latence haute
        double replicationBonus = 0.0;
        if (action == 1 && latency > 2000.0) {
            replicationBonus = 2.0;
        }
        
        return latencyReward - slaPenalty - replicationCost + replicationBonus;
    }
    
    /**
     * Retourne le nombre de requêtes exécutées.
     */
    public int getCurrentQuery() {
        return currentQuery;
    }
    
    /**
     * Retourne la récompense cumulative de l'épisode.
     */
    public double getCumulativeReward() {
        return cumulativeReward;
    }
    
    /**
     * Résultat d'un step.
     */
    public record StepResult(
        double[] state,
        double reward,
        boolean done,
        String info
    ) {}
}
