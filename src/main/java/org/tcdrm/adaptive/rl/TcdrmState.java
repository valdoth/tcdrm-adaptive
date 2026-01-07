package org.tcdrm.adaptive.rl;

import java.util.Objects;

/**
 * Représente l'état de l'environnement TCDRM pour l'apprentissage par renforcement
 * État = (Budget, Latence, Popularité, Nombre de réplicas)
 */
public class TcdrmState {
    
    // Niveaux discrétisés pour réduire l'espace d'états
    public enum BudgetLevel {
        LOW(0),      // < 33% du budget initial
        MEDIUM(1),   // 33-66% du budget initial
        HIGH(2);     // > 66% du budget initial
        
        private final int value;
        BudgetLevel(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    public enum LatencyLevel {
        LOW(0),      // < 100ms
        MEDIUM(1),   // 100-200ms
        HIGH(2);     // > 200ms
        
        private final int value;
        LatencyLevel(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    public enum PopularityLevel {
        LOW(0),      // < 150 accès
        MEDIUM(1),   // 150-250 accès
        HIGH(2);     // > 250 accès
        
        private final int value;
        PopularityLevel(int value) { this.value = value; }
        public int getValue() { return value; }
    }
    
    private final BudgetLevel budgetLevel;
    private final LatencyLevel latencyLevel;
    private final PopularityLevel popularityLevel;
    private final int replicaCount;  // 0, 1, 2, 3
    
    public TcdrmState(BudgetLevel budgetLevel, LatencyLevel latencyLevel, 
                      PopularityLevel popularityLevel, int replicaCount) {
        this.budgetLevel = budgetLevel;
        this.latencyLevel = latencyLevel;
        this.popularityLevel = popularityLevel;
        this.replicaCount = Math.max(0, Math.min(3, replicaCount));
    }
    
    /**
     * Convertit l'état en un index unique pour la Q-table
     * Index = budget * 36 + latency * 12 + popularity * 4 + replicaCount
     * Espace total: 3 * 3 * 3 * 4 = 108 états
     */
    public int toIndex() {
        return budgetLevel.getValue() * 36 
             + latencyLevel.getValue() * 12 
             + popularityLevel.getValue() * 4 
             + replicaCount;
    }
    
    /**
     * Crée un état à partir d'un index
     */
    public static TcdrmState fromIndex(int index) {
        int budget = index / 36;
        int latency = (index % 36) / 12;
        int popularity = (index % 12) / 4;
        int replicas = index % 4;
        
        return new TcdrmState(
            BudgetLevel.values()[budget],
            LatencyLevel.values()[latency],
            PopularityLevel.values()[popularity],
            replicas
        );
    }
    
    /**
     * Crée un état à partir de valeurs continues
     */
    public static TcdrmState fromContinuous(double budgetRatio, double latencyMs, 
                                           int accessCount, int replicaCount) {
        BudgetLevel budget = budgetRatio < 0.33 ? BudgetLevel.LOW 
                           : budgetRatio < 0.66 ? BudgetLevel.MEDIUM 
                           : BudgetLevel.HIGH;
        
        LatencyLevel latency = latencyMs < 100 ? LatencyLevel.LOW 
                             : latencyMs < 200 ? LatencyLevel.MEDIUM 
                             : LatencyLevel.HIGH;
        
        PopularityLevel popularity = accessCount < 150 ? PopularityLevel.LOW 
                                   : accessCount < 250 ? PopularityLevel.MEDIUM 
                                   : PopularityLevel.HIGH;
        
        return new TcdrmState(budget, latency, popularity, replicaCount);
    }
    
    // Getters
    public BudgetLevel getBudgetLevel() { return budgetLevel; }
    public LatencyLevel getLatencyLevel() { return latencyLevel; }
    public PopularityLevel getPopularityLevel() { return popularityLevel; }
    public int getReplicaCount() { return replicaCount; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TcdrmState that = (TcdrmState) o;
        return replicaCount == that.replicaCount &&
               budgetLevel == that.budgetLevel &&
               latencyLevel == that.latencyLevel &&
               popularityLevel == that.popularityLevel;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(budgetLevel, latencyLevel, popularityLevel, replicaCount);
    }
    
    @Override
    public String toString() {
        return String.format("State[Budget=%s, Latency=%s, Popularity=%s, Replicas=%d]",
            budgetLevel, latencyLevel, popularityLevel, replicaCount);
    }
}
