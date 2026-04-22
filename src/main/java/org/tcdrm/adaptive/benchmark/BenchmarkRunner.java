package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;
import org.tcdrm.adaptive.core.RuntimeConfig;
import org.tcdrm.adaptive.rl.PythonRLBridge;
import org.tcdrm.adaptive.simulation.TcdrmSimulation;

/**
 * Exécute les benchmarks TCDRM avec CloudSimPlus.
 */
public class BenchmarkRunner {

    /**
     * Exécute le benchmark NoRepLc (pas de réplication).
     */
    public static BenchmarkData runNoRep(long seed, boolean complex, String name) {
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);
        
        double cumulCost = 0;
        
        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            TcdrmSimulation.QueryResult result = sim.executeNoRepQuery();
            cumulCost += result.bwCost();
            
            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                0, result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }
        
        return data;
    }

    /**
     * Exécute le benchmark TCDRM (réplication à seuil fixe P_SLA).
     */
    public static BenchmarkData runTcdrm(long seed, boolean complex, String name) {
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);
        
        double cumulCost = 0;
        
        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            TcdrmSimulation.QueryResult result = sim.executeTcdrmQuery();
            cumulCost += result.bwCost();
            
            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }
        
        return data;
    }

    /**
     * Exécute le benchmark RL avec apprentissage en ligne contrôlé.
     *
     * Le démarrage en mode NoRep est géré côté bridge Python (phase warmup),
     * puis les agents adaptent leurs politiques pendant la simulation.
     */
    public static BenchmarkData runRL(PythonRLBridge bridge, String modelType, 
                                       String name, boolean complex, long seed) {
        System.out.println("  >>> " + modelType.toUpperCase() + " (" + (complex ? "complex" : "simple") + ") - ADAPTIVE ONLINE...");
        
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);
        
        double cumulCost = 0;
        double lastLatency = 0;
        double lastCost = 0;
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        
        int maxQ = RuntimeConfig.getMaxQueries() != null ? RuntimeConfig.getMaxQueries() : TcdrmConstants.MAX_QUERIES;
        for (int q = 0; q < maxQ; q++) {
            // Construire l'état pour le modèle RL
            double[] state = sim.buildRLState(lastLatency, lastCost);
            double previousLatency = lastLatency;
            
            // Obtenir l'action du modèle Python
            int action;
            if ("qlearning".equals(modelType)) {
                action = bridge.selectActionQLearning(state);
                // Policy assist (anti-stall): lorsque l'on est en zone de décision,
                // continuer à répliquer tant que le nombre de réplicas n'a pas atteint le maximum autorisé.
                boolean replicateAllowed = (sim.getQueryCount() >= TcdrmConstants.POPULARITY_THRESHOLD)
                    || (sim.getEmaPopularity() >= TcdrmConstants.EMA_REPLICATION_THRESHOLD);
                int maxReplicas = TcdrmConstants.maxReplicasForQueryType(sim.isComplex());
                if (action == 0 && replicateAllowed && sim.getCurrentReplicaCount() < maxReplicas) {
                    action = 1; // Continuer la réplication jusqu'au plafond lorsque c'est permis
                }
            } else {
                action = bridge.selectActionDQN(state);
            }
            
            // Exécuter la requête avec l'action
            TcdrmSimulation.QueryResult result = sim.executeRLQuery(action);
            cumulCost += result.bwCost();
            
            lastLatency = result.queryTimeMs();
            lastCost = result.totalCost();

            // Apprentissage en ligne: récompense + transition
            double reward = calculateReward(result, tSla, action, previousLatency);
            double[] nextState = sim.buildRLState(lastLatency, lastCost);
            boolean done = (q == TcdrmConstants.MAX_QUERIES - 1);

            if ("qlearning".equals(modelType)) {
                bridge.updateQLearning(reward, nextState, done);
            } else {
                bridge.updateDQN(reward, nextState, done);
            }
            
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }
        
        System.out.println("      " + modelType.toUpperCase() + " terminé (adaptive online)");
        return data;
    }

    /**
     * Fonction de récompense orientée objectifs TCDRM-ADAPTIVE.
     *
     * Objectifs principaux:
     * - minimiser latence et violations SLA,
     * - limiter le coût et la sur-réplication,
     * - encourager une réplication utile (pas systématique).
     */
    private static double calculateReward(TcdrmSimulation.QueryResult result, double tSla, int action, double previousLatency) {
        double latency = result.queryTimeMs();
        double bwCost = result.bwCost();
        int replicas = result.replicaCount();
        double interProvider = result.bwInterProviderGb();
        double interRegion = result.bwInterRegionGb();

        // 1) Qualité de service (latence)
        double latencyScore = 1.0 - Math.min(1.0, latency / Math.max(1.0, tSla * 2.0));
        double reward = 4.0 * latencyScore;

        // 2) Pénalité forte si violation SLA
        if (latency > tSla) {
            reward -= 3.0;
        }

        // 3) Coût bande passante (normalisé)
        reward -= Math.min(2.0, bwCost * 12.0);

        // 3bis) Récompenser la réduction du trafic coûteux inter-provider/inter-region
        reward -= Math.min(1.5, interProvider * 18.0);
        reward -= Math.min(0.8, interRegion * 8.0);

        // 4) Coût de complexité: trop de réplicas
        reward -= Math.max(0, replicas - 1) * 0.25;

        // 5) Régularisation des actions
        if (action == 1) {
            reward -= 0.2; // REPLICATE a un coût
            if (latency > tSla) {
                reward += 0.6; // bonus si réplication quand SLA violé
            }
        } else if (action == 2) {
            reward -= 0.1; // DELETE: léger coût de risque
        }

        // 6) Encourager l'usage des réplicas existants si cela améliore réellement la latence
        if (replicas > 0 && action == 0 && previousLatency > 0.0 && latency < previousLatency) {
            reward += 0.4;
        }

        // 7) Bonus de stabilité SLA en présence de réplicas
        if (replicas > 0 && latency <= tSla) {
            reward += 0.3;
        }

        return reward;
    }
}
