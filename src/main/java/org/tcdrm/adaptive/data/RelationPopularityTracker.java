package org.tcdrm.adaptive.data;

import java.util.*;

/**
 * Calcule et suit la popularité des relations selon la définition formelle:
 * 
 * Pop(Rj) = freq(Rj) / |Q|
 * Pop_region(Rj, k) = freq(Rj dans région k) / nb requêtes dans région k
 * 
 * Seuils de décision:
 * - θ ≥ 0.6 (60%) → très populaire (réplication globale)
 * - θ ≥ 0.3 (30%) → populaire (réplication partielle)
 * - θ < 0.3 → faible (pas de réplication)
 */
public class RelationPopularityTracker {
    
    // Seuils de popularité
    public static final double THRESHOLD_VERY_POPULAR = 0.6;  // 60%
    public static final double THRESHOLD_POPULAR = 0.3;       // 30%
    public static final double THRESHOLD_REGIONAL = 0.5;      // 50% pour réplication régionale
    
    // Compteurs globaux
    private int totalQueries = 0;
    private final Map<String, Integer> relationFrequency = new HashMap<>();
    
    // Compteurs par région
    private final Map<String, Integer> queriesPerRegion = new HashMap<>();
    private final Map<String, Map<String, Integer>> relationFrequencyPerRegion = new HashMap<>();
    
    /**
     * Enregistre une requête et met à jour les compteurs de popularité.
     */
    public void recordQuery(Query query) {
        totalQueries++;
        
        String region = query.getSourceRegion();
        queriesPerRegion.put(region, queriesPerRegion.getOrDefault(region, 0) + 1);
        
        // Mettre à jour la fréquence globale et régionale de chaque relation
        for (String relationId : query.getRelationIds()) {
            // Fréquence globale
            relationFrequency.put(relationId, relationFrequency.getOrDefault(relationId, 0) + 1);
            
            // Fréquence régionale
            relationFrequencyPerRegion.putIfAbsent(region, new HashMap<>());
            Map<String, Integer> regionMap = relationFrequencyPerRegion.get(region);
            regionMap.put(relationId, regionMap.getOrDefault(relationId, 0) + 1);
        }
    }
    
    /**
     * Calcule Pop(Rj) = freq(Rj) / |Q|
     */
    public double getGlobalPopularity(String relationId) {
        if (totalQueries == 0) return 0.0;
        int freq = relationFrequency.getOrDefault(relationId, 0);
        return (double) freq / totalQueries;
    }
    
    /**
     * Calcule Pop_region(Rj, k) = freq(Rj dans région k) / nb requêtes dans région k
     */
    public double getRegionalPopularity(String relationId, String region) {
        int regionQueries = queriesPerRegion.getOrDefault(region, 0);
        if (regionQueries == 0) return 0.0;
        
        Map<String, Integer> regionMap = relationFrequencyPerRegion.get(region);
        if (regionMap == null) return 0.0;
        
        int freq = regionMap.getOrDefault(relationId, 0);
        return (double) freq / regionQueries;
    }
    
    /**
     * Détermine le niveau de popularité d'une relation.
     */
    public PopularityLevel getPopularityLevel(String relationId) {
        double pop = getGlobalPopularity(relationId);
        
        if (pop >= THRESHOLD_VERY_POPULAR) {
            return PopularityLevel.VERY_POPULAR;
        } else if (pop >= THRESHOLD_POPULAR) {
            return PopularityLevel.POPULAR;
        } else {
            return PopularityLevel.LOW;
        }
    }
    
    /**
     * Détermine si une relation doit être répliquée globalement.
     * Règle: Pop(Rj) ≥ 0.6 ET utilisée dans ≥ 2 régions
     */
    public boolean shouldReplicateGlobally(String relationId) {
        if (getGlobalPopularity(relationId) < THRESHOLD_VERY_POPULAR) {
            return false;
        }
        
        // Compter dans combien de régions cette relation est utilisée
        int regionsUsed = 0;
        for (String region : relationFrequencyPerRegion.keySet()) {
            if (relationFrequencyPerRegion.get(region).containsKey(relationId)) {
                regionsUsed++;
            }
        }
        
        return regionsUsed >= 2;
    }
    
    /**
     * Détermine les régions où une relation doit être répliquée localement.
     * Règle: Pop_region(Rj, k) ≥ 0.5
     */
    public List<String> getRegionsForLocalReplication(String relationId) {
        List<String> regions = new ArrayList<>();
        
        for (String region : relationFrequencyPerRegion.keySet()) {
            if (getRegionalPopularity(relationId, region) >= THRESHOLD_REGIONAL) {
                regions.add(region);
            }
        }
        
        return regions;
    }
    
    /**
     * Calcule un score de priorité combinant popularité et coût de transfert.
     * Score(Rj) = Pop(Rj) × Cout_transfert(Rj)
     */
    public double getPriorityScore(String relationId, double transferCost) {
        return getGlobalPopularity(relationId) * transferCost;
    }
    
    /**
     * Retourne les statistiques de popularité pour toutes les relations.
     */
    public Map<String, PopularityStats> getAllStats() {
        Map<String, PopularityStats> stats = new HashMap<>();
        
        for (String relationId : relationFrequency.keySet()) {
            int freq = relationFrequency.get(relationId);
            double globalPop = getGlobalPopularity(relationId);
            
            Map<String, Double> regionalPop = new HashMap<>();
            for (String region : relationFrequencyPerRegion.keySet()) {
                regionalPop.put(region, getRegionalPopularity(relationId, region));
            }
            
            stats.put(relationId, new PopularityStats(
                relationId, freq, globalPop, regionalPop, getPopularityLevel(relationId)
            ));
        }
        
        return stats;
    }
    
    public int getTotalQueries() {
        return totalQueries;
    }
    
    public int getRelationFrequency(String relationId) {
        return relationFrequency.getOrDefault(relationId, 0);
    }
    
    /**
     * Niveau de popularité d'une relation.
     */
    public enum PopularityLevel {
        VERY_POPULAR,  // ≥ 60% → réplication globale
        POPULAR,       // 30-60% → réplication partielle
        LOW            // < 30% → pas de réplication
    }
    
    /**
     * Statistiques de popularité pour une relation.
     */
    public static class PopularityStats {
        public final String relationId;
        public final int frequency;
        public final double globalPopularity;
        public final Map<String, Double> regionalPopularity;
        public final PopularityLevel level;
        
        public PopularityStats(String relationId, int frequency, double globalPopularity,
                             Map<String, Double> regionalPopularity, PopularityLevel level) {
            this.relationId = relationId;
            this.frequency = frequency;
            this.globalPopularity = globalPopularity;
            this.regionalPopularity = regionalPopularity;
            this.level = level;
        }
        
        @Override
        public String toString() {
            return String.format("Relation %s: freq=%d, Pop=%.2f%%, level=%s, regions=%s",
                relationId, frequency, globalPopularity * 100, level, regionalPopularity);
        }
    }
}
