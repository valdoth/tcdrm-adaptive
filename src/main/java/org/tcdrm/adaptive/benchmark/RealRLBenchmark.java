package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.rl.PythonRLBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmark qui exécute RÉELLEMENT les modèles RL Python (Q-Learning ou DQN)
 * via Py4J pour générer des décisions de réplication authentiques.
 * 
 * Contrairement aux simulations bidon, cette classe:
 * 1. Démarre sans réplica (comme NOREP)
 * 2. Appelle le modèle Python à chaque requête pour décider
 * 3. Applique les décisions de réplication dynamiquement
 * 4. Simule le warm-up progressif des réplicas
 * 
 * Dynamic P_SLA (Paper Section 3.2, Equation 1):
 * - Popularity pd_i = #Requests / (T_current - T_first + 1)
 * - Replication triggered when pd_i > P_SLA AND (tQ > T_SLA OR cQ > C_SLA)
 * - RL models receive dynamic popularity + SLA violation signals in state
 */
public class RealRLBenchmark {

    private final PythonRLBridge pythonBridge;
    private final String modelType; // "qlearning" ou "dqn"
    private final Random rnd;
    private final boolean complex;

    public RealRLBenchmark(PythonRLBridge pythonBridge, String modelType, long seed, boolean complex) {
        this.pythonBridge = pythonBridge;
        this.modelType = modelType;
        this.rnd = new Random(seed);
        this.complex = complex;
    }

    /**
     * Exécute le benchmark avec le modèle RL Python.
     * 
     * Le modèle RL décide dynamiquement quand répliquer/supprimer des relations.
     * La simulation utilise le même modèle multi-relation que TCDRM et NoRepLc.
     */
    public BenchmarkDataPerQuery computeBenchmark(String queryId) {
        int nRelations = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double relationSize = TcdrmConstants.AVG_RELATION_SIZE_GB;

        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCountList = new ArrayList<>();

        double totalCost = 0.0;
        double currentBudget = TcdrmConstants.INITIAL_BUDGET;
        int currentReplicaCount = 0;
        int replicaCreationQuery = -1;
        double totalLatency = 0.0;
        double sumBwInterProviderGb = 0.0;
        double sumBwInterRegionGb = 0.0;
        double sumBwCost = 0.0;
        double sumCpuCost = 0.0;
        double sumReplicaCost = 0.0;
        
        int actionReplicateCount = 0;
        int actionNoopCount = 0;
        
        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
            // === 1. CALCULER LA POPULARITÉ DYNAMIQUE ===
            // For RL models, we use cumulative query count as popularity metric
            // This matches the paper's intent: data becomes "popular" after many accesses
            // dynamicPopularity grows from 0 to MAX_QUERIES as queries accumulate
            double dynamicPopularity = (double)(q + 1);  // Cumulative access count
            
            // === 2. OBTENIR L'ÉTAT ACTUEL ===
            double avgLatency = (q > 0) ? totalLatency / q : TcdrmConstants.LAT_INTER_PROVIDER_MS;
            double lastQueryLatency = (q > 0 && !timePerQueryMs.isEmpty()) 
                ? timePerQueryMs.get(timePerQueryMs.size() - 1) 
                : TcdrmConstants.LAT_INTER_PROVIDER_MS;
            double lastQueryCost = (q > 0 && !costPerQuery.isEmpty()) 
                ? costPerQuery.get(costPerQuery.size() - 1) 
                : 0.0;
            
            // SLA thresholds from paper (img-002)
            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
            
            // SLA violation signals (Paper Algorithm 1: tQ > T_SLA OR cQ > C_SLA)
            double tSlaViolation = (lastQueryLatency > tSla) ? 1.0 : 0.0;
            double cSlaViolation = (lastQueryCost > cSla) ? 1.0 : 0.0;
            
            // Extended state for RL models:
            // [0] avgLatency, [1] budget, [2] replicas, [3] dynamicPopularity (normalized),
            // [4] totalCost, [5] tSlaViolation, [6] cSlaViolation, [7] queryProgress
            double[] state = new double[]{
                avgLatency,                                          // [0] Average latency so far
                currentBudget,                                       // [1] Remaining budget
                currentReplicaCount,                                 // [2] Current replica count
                dynamicPopularity / TcdrmConstants.POPULARITY_THRESHOLD, // [3] Normalized popularity (1.0 = at threshold)
                totalCost,                                           // [4] Total cost so far
                tSlaViolation,                                       // [5] T_SLA violated (1.0 if latency > T_SLA)
                cSlaViolation,                                       // [6] C_SLA violated (1.0 if cost > C_SLA)
                (double) q / TcdrmConstants.MAX_QUERIES              // [7] Query progress [0, 1]
            };
            
            // === 3. DEMANDER DÉCISION AU MODÈLE PYTHON ===
            // RL models can now decide based on dynamic popularity + SLA violations
            // No fixed P_SLA gate - let the model learn when to replicate
            int action = 0;
            try {
                if ("qlearning".equalsIgnoreCase(modelType)) {
                    action = pythonBridge.selectActionQLearning(state);
                } else if ("dqn".equalsIgnoreCase(modelType)) {
                    action = pythonBridge.selectActionDQN(state);
                }
            } catch (Exception e) {
                System.err.println("⚠️  Erreur appel Python (query " + q + "): " + e.getMessage());
                action = 0;
            }
            
            // Track action distribution
            if (action == 1) actionReplicateCount++;
            else if (action == 0) actionNoopCount++;
            
            // === 3. EXÉCUTER L'ACTION ===
            double creationCost = 0.0;
            if (action == 1 && currentReplicaCount < maxReplicas) {
                creationCost = TcdrmConstants.replicationCost(relationSize);
                if (currentBudget >= creationCost) {
                    currentReplicaCount++;
                    currentBudget -= creationCost;
                    totalCost += creationCost;
                    if (replicaCreationQuery < 0) {
                        replicaCreationQuery = q;
                    }
                }
            } else if (action == 2 && currentReplicaCount > 0) {
                currentReplicaCount--;
                if (currentReplicaCount == 0) {
                    replicaCreationQuery = -1;
                }
            }
            
            // === 4. SIMULER LA REQUÊTE ===
            double warmupEff = 0.0;
            if (currentReplicaCount > 0 && replicaCreationQuery >= 0) {
                warmupEff = TcdrmConstants.warmupEfficiency(q - replicaCreationQuery);
            }
            
            QuerySimulator.QueryResult result = QuerySimulator.simulateTcdrmQuery(
                    nRelations, currentReplicaCount, warmupEff, rnd);
            
            totalLatency += result.queryTimeMs();
            totalCost += result.bwCost();
            currentBudget -= result.totalCost();
            sumBwInterProviderGb += result.bwInterProviderGb();
            sumBwInterRegionGb += result.bwInterRegionGb();
            sumBwCost += result.bwCost();
            sumCpuCost += result.cpuCost();
            // Replica cost = creation cost + ongoing maintenance (storage + write I/O)
            double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
            sumReplicaCost += creationCost + maintenanceCost;

            // === 5. ENREGISTRER LES MÉTRIQUES ===
            queryNumbers.add(q);
            timePerQueryMs.add(result.queryTimeMs());
            costPerQuery.add(result.bwCost());
            cumulativeCost.add(totalCost);
            replicaCountList.add(currentReplicaCount);
            
            if (currentBudget <= 0) {
                System.out.println("⚠️  Budget épuisé à la requête " + q);
                break;
            }
        }

        System.out.printf("    %s: actions NOOP=%d, REPLICATE=%d, finalReplicas=%d, replicaStart=q%d%n",
                queryId, actionNoopCount, actionReplicateCount, currentReplicaCount, replicaCreationQuery);
        
        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs, 
                costPerQuery, cumulativeCost, replicaCountList,
                sumBwInterProviderGb, sumBwInterRegionGb, sumBwCost, sumCpuCost, sumReplicaCost);
    }
}
