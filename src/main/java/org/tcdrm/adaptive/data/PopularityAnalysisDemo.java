package org.tcdrm.adaptive.data;

import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;

import java.util.List;
import java.util.Map;

/**
 * Démo pour valider l'architecture multi-cloud et l'analyse de popularité.
 * 
 * Génère un workload de 1000 requêtes et analyse la popularité des relations
 * selon la définition formelle:
 * - Pop(Rj) = freq(Rj) / |Q|
 * - Pop_region(Rj, k) = freq(Rj dans région k) / nb requêtes dans région k
 */
public class PopularityAnalysisDemo {
    
    public static void main(String[] args) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  TCDRM-ADAPTIVE: Analyse de Popularité des Relations");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        // 1. Créer l'infrastructure multi-cloud
        System.out.println("📊 Étape 1: Création de l'infrastructure multi-cloud\n");
        MultiCloudInfrastructure infrastructure = new MultiCloudInfrastructure();
        System.out.println();
        
        // 2. Générer le workload
        System.out.println("📊 Étape 2: Génération du workload\n");
        long seed = 42L;
        int numQueries = 1000;
        
        WorkloadGenerator generator = new WorkloadGenerator(seed, infrastructure);
        List<Query> queries = generator.generateRegionalWorkload(numQueries);
        
        System.out.printf("  ✅ %d requêtes générées%n", queries.size());
        System.out.printf("  ✅ %d relations dans le système%n", generator.getRelations().size());
        System.out.println();
        
        // Afficher les relations et leur localisation
        System.out.println("📍 Relations et leur datacenter d'origine:");
        for (Relation rel : generator.getRelations()) {
            System.out.printf("  • %s: %s (%.2f GB)%n", 
                rel.getId(), rel.getHomeDatacenter(), rel.getSizeGb());
        }
        System.out.println();
        
        // 3. Analyser la popularité
        System.out.println("📊 Étape 3: Analyse de popularité\n");
        RelationPopularityTracker tracker = new RelationPopularityTracker();
        
        for (Query query : queries) {
            tracker.recordQuery(query);
        }
        
        System.out.printf("  ✅ %d requêtes analysées%n", tracker.getTotalQueries());
        System.out.println();
        
        // 4. Afficher les statistiques de popularité
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  RÉSULTATS: Popularité Globale");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        Map<String, RelationPopularityTracker.PopularityStats> stats = tracker.getAllStats();
        
        System.out.println("┌──────────┬──────────┬────────────┬─────────────────┬──────────────────────┐");
        System.out.println("│ Relation │ Fréq.    │ Pop(Rj)    │ Niveau          │ Décision             │");
        System.out.println("├──────────┼──────────┼────────────┼─────────────────┼──────────────────────┤");
        
        for (String relId : List.of("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10")) {
            RelationPopularityTracker.PopularityStats stat = stats.get(relId);
            if (stat != null) {
                String level = getLevelEmoji(stat.level);
                String decision = getReplicationDecision(tracker, relId);
                
                System.out.printf("│ %-8s │ %4d/%-4d │ %6.1f%%   │ %-15s │ %-20s │%n",
                    relId,
                    stat.frequency,
                    numQueries,
                    stat.globalPopularity * 100,
                    level,
                    decision);
            }
        }
        System.out.println("└──────────┴──────────┴────────────┴─────────────────┴──────────────────────┘");
        System.out.println();
        
        // 5. Afficher la popularité régionale
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  RÉSULTATS: Popularité Régionale");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        System.out.println("┌──────────┬────────────┬────────────┬────────────┐");
        System.out.println("│ Relation │ US         │ EU         │ AS         │");
        System.out.println("├──────────┼────────────┼────────────┼────────────┤");
        
        for (String relId : List.of("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10")) {
            double popUS = tracker.getRegionalPopularity(relId, "US");
            double popEU = tracker.getRegionalPopularity(relId, "EU");
            double popAS = tracker.getRegionalPopularity(relId, "AS");
            
            System.out.printf("│ %-8s │ %6.1f%% %s │ %6.1f%% %s │ %6.1f%% %s │%n",
                relId,
                popUS * 100, getPopEmoji(popUS),
                popEU * 100, getPopEmoji(popEU),
                popAS * 100, getPopEmoji(popAS));
        }
        System.out.println("└──────────┴────────────┴────────────┴────────────┘");
        System.out.println();
        
        // 6. Recommandations de réplication
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  RECOMMANDATIONS DE RÉPLICATION");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        for (String relId : List.of("R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "R9", "R10")) {
            System.out.printf("🔹 %s:%n", relId);
            
            if (tracker.shouldReplicateGlobally(relId)) {
                System.out.println("   ✅ RÉPLICATION GLOBALE");
                System.out.println("      → Répliquer dans toutes les régions (US, EU, AS)");
            } else {
                List<String> regions = tracker.getRegionsForLocalReplication(relId);
                if (!regions.isEmpty()) {
                    System.out.println("   ⚡ RÉPLICATION PARTIELLE");
                    System.out.printf("      → Répliquer dans: %s%n", String.join(", ", regions));
                } else {
                    System.out.println("   ❄️  PAS DE RÉPLICATION");
                    System.out.println("      → Garder uniquement dans le datacenter d'origine");
                }
            }
            System.out.println();
        }
        
        // 7. Résumé
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  RÉSUMÉ");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        long veryPopular = stats.values().stream()
            .filter(s -> s.level == RelationPopularityTracker.PopularityLevel.VERY_POPULAR)
            .count();
        long popular = stats.values().stream()
            .filter(s -> s.level == RelationPopularityTracker.PopularityLevel.POPULAR)
            .count();
        long low = stats.values().stream()
            .filter(s -> s.level == RelationPopularityTracker.PopularityLevel.LOW)
            .count();
        
        System.out.printf("  🔥 Relations très populaires (≥60%%): %d%n", veryPopular);
        System.out.printf("  ⚡ Relations populaires (30-60%%): %d%n", popular);
        System.out.printf("  ❄️  Relations peu populaires (<30%%): %d%n", low);
        System.out.println();
        
        System.out.println("  Seuils utilisés:");
        System.out.printf("    • θ_global = %.0f%% (réplication globale)%n", 
            RelationPopularityTracker.THRESHOLD_VERY_POPULAR * 100);
        System.out.printf("    • θ_partiel = %.0f%% (réplication partielle)%n", 
            RelationPopularityTracker.THRESHOLD_POPULAR * 100);
        System.out.printf("    • θ_régional = %.0f%% (réplication régionale)%n", 
            RelationPopularityTracker.THRESHOLD_REGIONAL * 100);
        System.out.println();
        
        System.out.println("✅ Analyse terminée!");
    }
    
    private static String getLevelEmoji(RelationPopularityTracker.PopularityLevel level) {
        switch (level) {
            case VERY_POPULAR: return "🔥 Très populaire";
            case POPULAR: return "⚡ Populaire";
            case LOW: return "❄️  Peu populaire";
            default: return "❓ Inconnu";
        }
    }
    
    private static String getPopEmoji(double pop) {
        if (pop >= 0.6) return "🔥";
        if (pop >= 0.5) return "⚡";
        if (pop >= 0.3) return "📊";
        return "  ";
    }
    
    private static String getReplicationDecision(RelationPopularityTracker tracker, String relId) {
        if (tracker.shouldReplicateGlobally(relId)) {
            return "Globale (toutes)";
        }
        List<String> regions = tracker.getRegionsForLocalReplication(relId);
        if (!regions.isEmpty()) {
            return "Partielle (" + String.join(",", regions) + ")";
        }
        return "Aucune";
    }
}
