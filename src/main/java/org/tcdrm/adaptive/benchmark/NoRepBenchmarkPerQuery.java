package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * NoRepLc Benchmark (Paper: "No-Replication-Less cost").
 * 
 * Pas de réplication. Chaque requête récupère toutes les relations
 * depuis des providers distants (inter-provider).
 * Sélectionne le provider le moins cher pour chaque relation.
 * 
 * Résultats attendus (Paper Fig 3-7):
 * - Simple: ~200ms constant, cumul BW ~$18 à 1000 queries
 * - Complex: ~420ms constant, cumul BW ~$38 à 1000 queries
 */
public class NoRepBenchmarkPerQuery {

    private final Random rnd;
    private final boolean complex;

    public NoRepBenchmarkPerQuery(long seed, boolean complex) {
        this.rnd = new Random(seed);
        this.complex = complex;
    }

    public BenchmarkDataPerQuery computeBenchmark(String queryId) {
        int nRelations = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;

        List<Integer> queryNumbers = new ArrayList<>();
        List<Double> timePerQueryMs = new ArrayList<>();
        List<Double> costPerQuery = new ArrayList<>();
        List<Double> cumulativeCost = new ArrayList<>();
        List<Integer> replicaCount = new ArrayList<>();

        double cumulBwCost = 0.0;
        double sumBwInterProviderGb = 0.0;
        double sumBwInterRegionGb = 0.0;
        double sumBwCost = 0.0;
        double sumCpuCost = 0.0;

        for (int q = 0; q < TcdrmConstants.MAX_QUERIES; q++) {
            QuerySimulator.QueryResult result = QuerySimulator.simulateNoRepQuery(nRelations, rnd);

            cumulBwCost += result.bwCost();
            sumBwInterProviderGb += result.bwInterProviderGb();
            sumBwInterRegionGb += result.bwInterRegionGb();
            sumBwCost += result.bwCost();
            sumCpuCost += result.cpuCost();

            queryNumbers.add(q);
            timePerQueryMs.add(result.queryTimeMs());
            costPerQuery.add(result.bwCost());
            cumulativeCost.add(cumulBwCost);
            replicaCount.add(0);
        }

        return new BenchmarkDataPerQuery(queryId, queryNumbers, timePerQueryMs,
                costPerQuery, cumulativeCost, replicaCount,
                sumBwInterProviderGb, sumBwInterRegionGb, sumBwCost, sumCpuCost, 0.0);
    }
}
