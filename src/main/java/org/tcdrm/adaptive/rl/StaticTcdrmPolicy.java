package org.tcdrm.adaptive.rl;

/**
 * Politique TCDRM Statique (Baseline)
 * Utilise des seuils fixes pour les décisions de réplication
 * 
 * Seuils:
 * - TSLA (Threshold SLA): Seuil de latence = 80ms (ajusté pour données réelles)
 * - PSLA (Popularity SLA): Seuil de popularité = 200 accès
 * - CSLA (Cost SLA): Seuil de budget = 20%
 */
public class StaticTcdrmPolicy {
    
    private final double latencyThreshold;      // TSLA
    private final int popularityThreshold;      // PSLA
    private final double budgetThreshold;       // CSLA (ratio)
    private final int maxReplicas;
    
    // Statistiques
    private int totalCreates = 0;
    private int totalDeletes = 0;
    private int totalNothing = 0;
    
    /**
     * Constructeur avec seuils par défaut
     * TSLA=80ms (ajusté pour les données réelles), PSLA=200, CSLA=20%
     */
    public StaticTcdrmPolicy() {
        this(80.0, 200, 0.2, 3);
    }
    
    /**
     * Constructeur avec seuils personnalisés
     */
    public StaticTcdrmPolicy(double latencyThreshold, int popularityThreshold, 
                            double budgetThreshold, int maxReplicas) {
        this.latencyThreshold = latencyThreshold;
        this.popularityThreshold = popularityThreshold;
        this.budgetThreshold = budgetThreshold;
        this.maxReplicas = maxReplicas;
    }
    
    /**
     * Choisit une action basée sur des seuils fixes
     * 
     * Règles de décision:
     * 1. Si budget < 20% ET réplicas > 0 → DELETE (prioritaire)
     * 2. Si popularité ≥ 200 ET latence > 80ms ET réplicas < max → CREATE
     * 3. Si popularité < 200 ET réplicas > 0 → DELETE
     * 4. Sinon → DO_NOTHING
     */
    public TcdrmAction chooseAction(double budgetRatio, double latency, 
                                    int accessCount, int currentReplicas) {
        
        // Règle 1: Budget critique - supprimer réplicas (prioritaire)
        if (budgetRatio < budgetThreshold && currentReplicas > 0) {
            totalDeletes++;
            return TcdrmAction.DELETE_REPLICA;
        }
        
        // Règle 2: Popularité élevée ET latence élevée - créer réplica
        if (accessCount >= popularityThreshold && 
            latency > latencyThreshold && 
            currentReplicas < maxReplicas) {
            totalCreates++;
            return TcdrmAction.CREATE_REPLICA;
        }
        
        // Règle 3: Popularité faible - supprimer réplicas
        if (accessCount < popularityThreshold && currentReplicas > 0) {
            totalDeletes++;
            return TcdrmAction.DELETE_REPLICA;
        }
        
        // Règle 4: Sinon, ne rien faire
        totalNothing++;
        return TcdrmAction.DO_NOTHING;
    }
    
    /**
     * Obtient les statistiques d'utilisation
     */
    public String getStatistics() {
        int total = totalCreates + totalDeletes + totalNothing;
        if (total == 0) return "Aucune décision prise";
        
        return String.format(
            "Décisions: CREATE=%.1f%% DELETE=%.1f%% NOTHING=%.1f%%",
            (totalCreates * 100.0 / total),
            (totalDeletes * 100.0 / total),
            (totalNothing * 100.0 / total)
        );
    }
    
    /**
     * Reset les statistiques
     */
    public void resetStatistics() {
        totalCreates = 0;
        totalDeletes = 0;
        totalNothing = 0;
    }
    
    // Getters
    public double getLatencyThreshold() { return latencyThreshold; }
    public int getPopularityThreshold() { return popularityThreshold; }
    public double getBudgetThreshold() { return budgetThreshold; }
    public int getTotalCreates() { return totalCreates; }
    public int getTotalDeletes() { return totalDeletes; }
    public int getTotalNothing() { return totalNothing; }
    
    @Override
    public String toString() {
        return String.format(
            "StaticTcdrmPolicy(TSLA=%.0fms, PSLA=%d, Budget=%.0f%%)",
            latencyThreshold, popularityThreshold, budgetThreshold * 100
        );
    }
}
