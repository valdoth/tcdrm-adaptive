package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;
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
        
        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
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
        
        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
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
     * Exécute le benchmark RL (Q-Learning ou DQN).
     */
    public static BenchmarkData runRL(PythonRLBridge bridge, String modelType, 
                                       String name, boolean complex, long seed) {
        System.out.println("  >>> " + modelType.toUpperCase() + " (" + (complex ? "complex" : "simple") + ")...");
        
        TcdrmSimulation sim = new TcdrmSimulation(seed, complex);
        BenchmarkData data = new BenchmarkData(name);
        
        double cumulCost = 0;
        double lastLatency = 0;
        double lastCost = 0;
        
        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
            // Construire l'état pour le modèle RL
            double[] state = sim.buildRLState(lastLatency, lastCost);
            
            // Obtenir l'action du modèle Python
            int action;
            if ("qlearning".equals(modelType)) {
                action = bridge.selectActionQLearning(state);
            } else {
                action = bridge.selectActionDQN(state);
            }
            
            // Exécuter la requête avec l'action
            TcdrmSimulation.QueryResult result = sim.executeRLQuery(action);
            cumulCost += result.bwCost();
            
            lastLatency = result.queryTimeMs();
            lastCost = result.totalCost();
            
            double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
            data.addQueryResult(
                q, result.queryTimeMs(), result.bwCost(), cumulCost,
                result.replicaCount(), result.bwInterProviderGb(), result.bwInterRegionGb(),
                result.cpuCost(), 0, tSla
            );
        }
        
        System.out.println("      " + modelType.toUpperCase() + " terminé");
        return data;
    }
}
