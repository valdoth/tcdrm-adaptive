package org.tcdrm.adaptive.data;

import java.util.Arrays;
import java.util.List;

/**
 * Définit les requêtes fixes utilisées pour le benchmark.
 * 
 * - R1: requête simple (1-2 relations, lecture)
 * - R2: requête complexe (4-6 relations, lecture + écriture)
 * 
 * Ces requêtes sont répétées 1000 fois pour évaluer les performances
 * des différentes stratégies de réplication.
 */
public class BenchmarkQueries {
    
    /**
     * R1: Requête simple
     * - Accède à 2 relations (R1, R2)
     * - Lecture seule
     * - Source: Google_US_sub_region1_DC1
     */
    public static Query createR1Simple(int queryId) {
        return new Query(
            queryId,
            Arrays.asList("R1", "R2"),
            "Google_US_sub_region1_DC1",
            true  // read
        );
    }
    
    /**
     * R2: Requête complexe
     * - Accède à 6 relations (R1, R2, R3, R4, R5, R6)
     * - Mix lecture + écriture
     * - Source: AWS_EU_sub_region2_DC2
     */
    public static Query createR2Complex(int queryId) {
        return new Query(
            queryId,
            Arrays.asList("R1", "R2", "R3", "R4", "R5", "R6"),
            "AWS_EU_sub_region2_DC2",
            false  // write (plus coûteux)
        );
    }
    
    /**
     * Génère un workload de benchmark avec la même requête répétée.
     * 
     * @param numQueries Nombre de répétitions
     * @param simple true pour R1, false pour R2
     * @return Liste de requêtes identiques
     */
    public static List<Query> generateBenchmarkWorkload(int numQueries, boolean simple) {
        Query[] queries = new Query[numQueries];
        for (int i = 0; i < numQueries; i++) {
            queries[i] = simple ? createR1Simple(i) : createR2Complex(i);
        }
        return Arrays.asList(queries);
    }
    
    /**
     * Retourne le nombre de relations pour une requête.
     */
    public static int getRelationCount(boolean simple) {
        return simple ? 2 : 6;
    }
    
    /**
     * Retourne la description d'une requête benchmark.
     */
    public static String getDescription(boolean simple) {
        if (simple) {
            return "R1 (Simple): 2 relations, READ, Google_US";
        } else {
            return "R2 (Complex): 6 relations, WRITE, AWS_EU";
        }
    }
}
