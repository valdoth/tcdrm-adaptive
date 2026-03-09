package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TCDRM Statique Benchmark (Paper Algorithms 1-3).
 * 
 * Stratégie de réplication à seuil fixe:
 * - Avant P_SLA (200 requêtes): aucun réplica → comportement identique à NoRepLc
 * - À P_SLA: les relations sont progressivement répliquées (Paper Fig 2 — img-003.png)
 * - Après réplication: transferts deviennent intra-provider → latence et coût réduits
 * 
 * Paper Fig 2 montre que les réplicas augmentent progressivement à partir de P_SLA:
 * - Simple: 0 → 6 réplicas sur ~10 requêtes
 * - Complex: 0 → 12 réplicas sur ~10 requêtes
 * Chaque relation est analysée individuellement (Algorithm 1 lines 5-10).
 */
public class TcdrmBenchmarkPerQuery {

    private final Random rnd;
    private final boolean complex;

    public TcdrmBenchmarkPerQuery(long seed, boolean complex) {
        this.rnd = new Random(seed);
        this.complex = complex;
    }

    public BenchmarkDataPerQuery computeBenchmark(String queryId) {
        int nRelations = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double relationSize = TcdrmConstants.AVG_RELATION_SIZE_GB;

        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCountList = new ArrayList<>();

        double cumulBwCost = 0.0;
        double sumBwInterProviderGb = 0.0;
        double sumBwInterRegionGb = 0.0;
        double sumBwCost = 0.0;
        double sumCpuCost = 0.0;
        double sumReplicaCost = 0.0;
        int currentReplicas = 0;
        int replicaCreationQuery = -1;

        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
            // === Algorithm 1: Replica Creation at P_SLA ===
            double creationCost = 0.0;
            if (q >= TcdrmConstants.POPULARITY_THRESHOLD && currentReplicas < maxReplicas) {
                currentReplicas++;
                creationCost = TcdrmConstants.replicationCost(relationSize);
                if (replicaCreationQuery < 0) {
                    replicaCreationQuery = q;
                }
            }

            // Warm-up efficiency for replicated relations
            double warmupEff = 0.0;
            if (currentReplicas > 0 && replicaCreationQuery >= 0) {
                warmupEff = TcdrmConstants.warmupEfficiency(q - replicaCreationQuery);
            }

            // Simulate query
            QuerySimulator.QueryResult result = QuerySimulator.simulateTcdrmQuery(
                    nRelations, currentReplicas, warmupEff, rnd);

            // Ongoing replica maintenance cost (storage + write I/O overhead)
            double maintenanceCost = currentReplicas * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;

            double queryCostBw = result.bwCost() + creationCost;
            cumulBwCost += result.bwCost() + creationCost;
            sumBwInterProviderGb += result.bwInterProviderGb();
            sumBwInterRegionGb += result.bwInterRegionGb();
            sumBwCost += result.bwCost();
            sumCpuCost += result.cpuCost();
            sumReplicaCost += creationCost + maintenanceCost;

            queryNumbers.add(q);
            timePerQueryMs.add(result.queryTimeMs());
            costPerQuery.add(queryCostBw);
            cumulativeCost.add(cumulBwCost);
            replicaCountList.add(currentReplicas);
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs,
                costPerQuery, cumulativeCost, replicaCountList,
                sumBwInterProviderGb, sumBwInterRegionGb, sumBwCost, sumCpuCost, sumReplicaCost);
    }
}
