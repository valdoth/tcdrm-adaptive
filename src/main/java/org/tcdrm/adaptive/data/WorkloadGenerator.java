package org.tcdrm.adaptive.data;

import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;

import java.util.*;

/**
 * Génère un workload réaliste de requêtes avec des patterns de popularité.
 * 
 * Patterns implémentés:
 * - Relations très populaires (≥60%): accédées par la majorité des requêtes
 * - Relations populaires (30-60%): accédées par une portion significative
 * - Relations peu populaires (<30%): accédées rarement
 * - Distribution géographique: certaines relations plus populaires dans certaines régions
 */
public class WorkloadGenerator {
    
    private final Random random;
    private final List<Relation> relations;
    private final MultiCloudInfrastructure infrastructure;
    
    // Configuration du workload
    private static final int NUM_RELATIONS = 10;  // R1 à R10 comme dans ton schéma
    private static final double RELATION_SIZE_GB = 0.1;  // 100 MB par relation
    private static final double READ_RATIO = 0.8;  // 80% lectures, 20% écritures
    
    public WorkloadGenerator(long seed, MultiCloudInfrastructure infrastructure) {
        this.random = new Random(seed);
        this.infrastructure = infrastructure;
        this.relations = generateRelations();
    }
    
    /**
     * Génère les relations initiales distribuées dans les datacenters.
     */
    private List<Relation> generateRelations() {
        List<Relation> rels = new ArrayList<>();
        List<String> dcNames = new ArrayList<>(infrastructure.getAllDatacenters().keySet());
        
        for (int i = 0; i < NUM_RELATIONS; i++) {
            String relationId = "R" + (i + 1);
            // Distribuer les relations de manière aléatoire dans les DCs
            String homeDC = dcNames.get(random.nextInt(dcNames.size()));
            rels.add(new Relation(relationId, homeDC, RELATION_SIZE_GB));
        }
        
        return rels;
    }
    
    /**
     * Génère un workload de requêtes avec des patterns de popularité définis.
     * 
     * Pattern:
     * - R1, R2: très populaires (70-80% des requêtes) → réplication globale
     * - R3, R4, R5: populaires (40-50% des requêtes) → réplication partielle
     * - R6-R10: peu populaires (5-15% des requêtes) → pas de réplication
     */
    public List<Query> generateWorkload(int numQueries) {
        List<Query> queries = new ArrayList<>();
        List<String> dcNames = new ArrayList<>(infrastructure.getAllDatacenters().keySet());
        
        // Définir les probabilités d'accès pour chaque relation
        double[] accessProbabilities = {
            0.75,  // R1 - très populaire
            0.70,  // R2 - très populaire
            0.50,  // R3 - populaire
            0.45,  // R4 - populaire
            0.40,  // R5 - populaire
            0.15,  // R6 - peu populaire
            0.12,  // R7 - peu populaire
            0.10,  // R8 - peu populaire
            0.08,  // R9 - peu populaire
            0.05   // R10 - peu populaire
        };
        
        for (int i = 0; i < numQueries; i++) {
            // Choisir le DC source de la requête
            String sourceDC = dcNames.get(random.nextInt(dcNames.size()));
            
            // Déterminer si c'est une lecture ou écriture
            boolean isRead = random.nextDouble() < READ_RATIO;
            
            // Sélectionner les relations accédées par cette requête
            List<String> relationIds = new ArrayList<>();
            for (int j = 0; j < NUM_RELATIONS; j++) {
                // Ajouter un biais régional: relations plus populaires dans leur région d'origine
                double prob = accessProbabilities[j];
                String relationHomeDC = relations.get(j).getHomeDatacenter();
                
                // Bonus de 20% si la requête vient de la même région que la relation
                if (sourceDC.split("_")[1].equals(relationHomeDC.split("_")[1])) {
                    prob = Math.min(1.0, prob * 1.2);
                }
                
                if (random.nextDouble() < prob) {
                    relationIds.add(relations.get(j).getId());
                }
            }
            
            // Assurer qu'au moins une relation est accédée
            if (relationIds.isEmpty()) {
                int idx = random.nextInt(NUM_RELATIONS);
                relationIds.add(relations.get(idx).getId());
            }
            
            queries.add(new Query(i, relationIds, sourceDC, isRead));
        }
        
        return queries;
    }
    
    /**
     * Génère un workload avec des patterns régionaux distincts.
     * 
     * - US: R1, R2, R3 très populaires
     * - EU: R2, R4, R5 très populaires
     * - AS: R1, R6, R7 très populaires
     */
    public List<Query> generateRegionalWorkload(int numQueries) {
        List<Query> queries = new ArrayList<>();
        List<String> dcNames = new ArrayList<>(infrastructure.getAllDatacenters().keySet());
        
        for (int i = 0; i < numQueries; i++) {
            String sourceDC = dcNames.get(random.nextInt(dcNames.size()));
            String region = sourceDC.split("_")[1];
            boolean isRead = random.nextDouble() < READ_RATIO;
            
            // Probabilités d'accès par région
            double[] probabilities = new double[NUM_RELATIONS];
            
            switch (region) {
                case "US":
                    probabilities = new double[]{0.80, 0.75, 0.70, 0.30, 0.25, 0.15, 0.10, 0.08, 0.05, 0.03};
                    break;
                case "EU":
                    probabilities = new double[]{0.40, 0.80, 0.35, 0.75, 0.70, 0.20, 0.15, 0.10, 0.08, 0.05};
                    break;
                case "AS":
                    probabilities = new double[]{0.75, 0.30, 0.25, 0.20, 0.15, 0.80, 0.70, 0.40, 0.30, 0.20};
                    break;
            }
            
            List<String> relationIds = new ArrayList<>();
            for (int j = 0; j < NUM_RELATIONS; j++) {
                if (random.nextDouble() < probabilities[j]) {
                    relationIds.add(relations.get(j).getId());
                }
            }
            
            if (relationIds.isEmpty()) {
                relationIds.add(relations.get(random.nextInt(NUM_RELATIONS)).getId());
            }
            
            queries.add(new Query(i, relationIds, sourceDC, isRead));
        }
        
        return queries;
    }
    
    public List<Relation> getRelations() {
        return relations;
    }
    
    public Relation getRelation(String relationId) {
        return relations.stream()
            .filter(r -> r.getId().equals(relationId))
            .findFirst()
            .orElse(null);
    }
}
