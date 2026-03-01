package org.tcdrm.adaptive.benchmark;

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
 */
public class RealRLBenchmark {
    private static final int MAX_QUERIES = 5000;
    
    // Paramètres réseau
    private static final double BW_LOCAL_GBPS = 10.0;
    private static final double LAT_LOCAL_MS = 1.0;
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;

    // Coûts
    private static final double COST_BW_INTRA_DC = 0.002;
    private static final double COST_BW_INTER_PROVIDER = 0.10;
    private static final double CPU_COST_PER_HOUR = 0.02;
    private static final double STORAGE_COST_PER_GB_PER_MONTH = 0.02;
    private static final double PROCESSING_MIN_PER_GB = 0.5;
    
    // Warm-up
    private static final int WARMUP_QUERIES = 600;
    private static final double JITTER_RATIO = 0.05;
    private static final double CPU_JITTER_RATIO = 0.05;
    
    // Budget et contraintes
    private static final double INITIAL_BUDGET = 1000.0;
    private static final int MAX_REPLICAS = 3;

    private final PythonRLBridge pythonBridge;
    private final String modelType; // "qlearning" ou "dqn"
    private final Random rnd;

    public RealRLBenchmark(PythonRLBridge pythonBridge, String modelType, long seed) {
        this.pythonBridge = pythonBridge;
        this.modelType = modelType;
        this.rnd = new Random(seed);
    }
    
    /**
     * Calcule l'efficacité du warm-up avec fonction sigmoid
     */
    private double calculateWarmupEfficiency(int queriesSinceCreation) {
        if (queriesSinceCreation >= WARMUP_QUERIES) {
            return 1.0;
        }
        double x = (double) queriesSinceCreation / WARMUP_QUERIES;
        return 1.0 / (1.0 + Math.exp(-5.0 * (x - 0.5)));
    }
    
    /**
     * Sélection du réplica basée sur la proximité et le warm-up
     */
    private boolean selectBestReplica(int totalReplicas, double warmupEfficiency) {
        double baseProbability = (double) totalReplicas / (totalReplicas + 2);
        double localProbability = baseProbability * warmupEfficiency;
        return rnd.nextDouble() < localProbability;
    }

    /**
     * Exécute le benchmark avec le modèle RL Python
     */
    public BenchmarkDataPerQuery computeBenchmark(String queryId, double dataGb) {
        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCountList = new ArrayList<>();

        double totalCost = 0.0;
        double currentBudget = INITIAL_BUDGET;
        int currentReplicaCount = 0;
        int replicaCreationQuery = -1;
        double totalLatency = 0.0;
        
        for (int q = 0; q < MAX_QUERIES; q++) {
            // === 1. OBTENIR L'ÉTAT ACTUEL ===
            double avgLatency = (q > 0) ? totalLatency / q : LAT_REMOTE_MS;
            double popularity = (double) q / MAX_QUERIES; // Normaliser entre 0 et 1
            
            // État pour Python: [latency, budget, replicas, popularity, total_cost]
            double[] state = new double[]{
                avgLatency,
                currentBudget,
                currentReplicaCount,
                popularity,
                totalCost
            };
            
            // === 2. DEMANDER DÉCISION AU MODÈLE PYTHON ===
            int action = 0; // NOOP par défaut
            try {
                if ("qlearning".equalsIgnoreCase(modelType)) {
                    action = pythonBridge.selectActionQLearning(state);
                } else if ("dqn".equalsIgnoreCase(modelType)) {
                    action = pythonBridge.selectActionDQN(state);
                }
            } catch (Exception e) {
                System.err.println("⚠️  Erreur appel Python (query " + q + "): " + e.getMessage());
                action = 0; // NOOP en cas d'erreur
            }
            
            // === 3. EXÉCUTER L'ACTION ===
            // Actions: 0=NOOP, 1=REPLICATE, 2=DELETE
            if (action == 1 && currentReplicaCount < MAX_REPLICAS) {
                // REPLICATE
                double creationCost = dataGb * COST_BW_INTER_PROVIDER;
                if (currentBudget >= creationCost) {
                    currentReplicaCount++;
                    currentBudget -= creationCost;
                    totalCost += creationCost;
                    if (replicaCreationQuery < 0) {
                        replicaCreationQuery = q;
                    }
                }
            } else if (action == 2 && currentReplicaCount > 0) {
                // DELETE
                currentReplicaCount--;
                if (currentReplicaCount == 0) {
                    replicaCreationQuery = -1;
                }
            }
            // action == 0 : NOOP, ne rien faire
            
            // === 4. SIMULER LA REQUÊTE ===
            boolean replicaExists = currentReplicaCount > 0;
            
            // Calculer l'efficacité du warm-up
            double warmupEfficiency = 0.0;
            if (replicaExists && replicaCreationQuery >= 0) {
                int queriesSinceCreation = q - replicaCreationQuery;
                warmupEfficiency = calculateWarmupEfficiency(queriesSinceCreation);
            }
            
            // Sélection du réplica
            boolean useLocal = replicaExists && selectBestReplica(currentReplicaCount, warmupEfficiency);
            
            // Paramètres réseau
            double bwGbps = useLocal ? BW_LOCAL_GBPS : BW_REMOTE_GBPS;
            double latencyMs = useLocal ? LAT_LOCAL_MS : LAT_REMOTE_MS;
            double costPerGb = useLocal ? COST_BW_INTRA_DC : COST_BW_INTER_PROVIDER;

            // Temps de transfert avec jitter
            double transferMs = (dataGb * 8_000.0 / bwGbps) + latencyMs;
            transferMs *= (1.0 + JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Temps de traitement avec jitter
            double processingMin = dataGb * PROCESSING_MIN_PER_GB;
            processingMin *= (1.0 + CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
            
            // Temps total pour cette requête
            double queryTimeMs = transferMs + processingMin * 60_000.0;
            totalLatency += latencyMs;
            
            // === 5. CALCULER LES COÛTS ===
            double transferCost = dataGb * costPerGb;
            double cpuCost = (processingMin / 60.0) * CPU_COST_PER_HOUR;
            
            // Coût de stockage par heure
            double queryDurationHours = queryTimeMs / 3600000.0;
            double storageCost = replicaExists ? 
                (dataGb * STORAGE_COST_PER_GB_PER_MONTH * currentReplicaCount * queryDurationHours / 720.0) : 0.0;
            
            double queryCost = transferCost + cpuCost + storageCost;
            totalCost += queryCost;
            currentBudget -= queryCost;

            // === 6. ENREGISTRER LES MÉTRIQUES ===
            queryNumbers.add(q);
            timePerQueryMs.add(queryTimeMs / 1000.0); // Convertir en secondes
            costPerQuery.add(queryCost);
            cumulativeCost.add(totalCost);
            replicaCountList.add(currentReplicaCount);
            
            // Arrêter si budget épuisé
            if (currentBudget <= 0) {
                System.out.println("⚠️  Budget épuisé à la requête " + q);
                break;
            }
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs, 
                                         costPerQuery, cumulativeCost, replicaCountList);
    }
}
