package org.tcdrm.adaptive.benchmark;

import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.Random;

/**
 * Simulateur de requêtes multi-relation aligné avec l'article TCDRM V1.
 * 
 * Modèle de requête (Paper Section 4.2):
 * - Simple query: 3 relations × 450 MB, chacune dans une région différente d'un provider différent
 * - Complex query: 6 relations × 450 MB, réparties sur 3 régions avec 2+ par région
 * 
 * Sans réplication (NoRepLc): chaque relation nécessite un transfert inter-provider.
 * Avec réplication (TCDRM): les relations répliquées localement utilisent l'intra-DC.
 * 
 * Coût par requête (Paper Equation 2): C_Q = C_CPU + C_IO + C_bandwidth
 */
public final class QuerySimulator {

    private QuerySimulator() {}

    /**
     * Résultat d'une simulation de requête.
     */
    public record QueryResult(
        double queryTimeMs,
        double bwCost,
        double cpuCost,
        double ioCost,
        double totalCost,
        double bwInterProviderGb,
        double bwInterRegionGb
    ) {}

    /**
     * Simule une requête NoRepLc (pas de réplication, toujours inter-provider).
     * Chaque relation est récupérée depuis un provider distant (Paper: NoRepLc selects cheapest provider).
     * 
     * Response time model:
     * - Uses QUERY_SELECTIVITY (effective data transferred for the join)
     * - PARALLEL_FETCH: relations fetched in parallel → time = max, not sum
     * 
     * Cost model (Paper Eq 2):
     * - BW cost charged on FULL relation size (provider billing)
     * - C_Q = C_CPU + C_IO + C_bandwidth
     * 
     * @param nRelations nombre de relations dans la requête (3 simple, 6 complex)
     * @param rnd source d'aléatoire pour le jitter
     */
    public static QueryResult simulateNoRepQuery(int nRelations, Random rnd) {
        double relationSize = TcdrmConstants.AVG_RELATION_SIZE_GB;
        double effectiveDataGb = relationSize * TcdrmConstants.QUERY_SELECTIVITY;
        double maxTransferMs = 0.0;
        double totalBwCost = 0.0;
        double totalBwInterProvider = 0.0;

        // Chaque relation est sur un provider distant → transfert inter-provider
        for (int r = 0; r < nRelations; r++) {
            double latencyMs = TcdrmConstants.LAT_INTER_PROVIDER_MS
                    * (1.0 + TcdrmConstants.LATENCY_VARIATION_RATIO * (rnd.nextDouble() * 2 - 1));
            double transferMs = (effectiveDataGb * 8_000.0 / TcdrmConstants.BW_INTER_PROVIDER_GBPS) + latencyMs;
            transferMs *= (1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1));

            if (TcdrmConstants.PARALLEL_FETCH) {
                maxTransferMs = Math.max(maxTransferMs, transferMs);
            } else {
                maxTransferMs += transferMs;
            }
            // BW cost is on FULL relation size (provider billing)
            totalBwCost += relationSize * TcdrmConstants.COST_BW_INTER_PROVIDER;
            totalBwInterProvider += relationSize;
        }

        // Join processing time: quadratic model — each successive join operates
        // on a larger intermediate result. joinTime = sum(1..nJoins) * JOIN_BASE_MS
        // Simple (3 rel, 2 joins): 3 * 20 = 60ms
        // Complex (6 rel, 5 joins): 15 * 20 = 300ms
        int nJoins = nRelations - 1;
        double joinFactor = nJoins * (nJoins + 1) / 2.0;
        double joinProcessingMs = joinFactor * TcdrmConstants.JOIN_BASE_MS
                * (1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
        double queryTimeMs = maxTransferMs + joinProcessingMs;

        // CPU cost (Paper Eq 2: C_CPU) — based on compute instructions, not data volume
        // Paper Table 1: $0.020 / 10^7 MI. A query uses nRelations * nJoins MI.
        double miTotal = nRelations * nJoins * TcdrmConstants.MI_PER_JOIN_PER_RELATION;
        double cpuCost = (miTotal / 10.0) * TcdrmConstants.CPU_COST_PER_10M_MI;
        cpuCost *= (1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));

        // I/O cost (Paper Eq 2: C_IO)
        double totalDataGb = nRelations * relationSize;
        double ioCost = totalDataGb * TcdrmConstants.IO_COST_PER_GB;

        double totalCost = totalBwCost + cpuCost + ioCost;

        return new QueryResult(queryTimeMs, totalBwCost, cpuCost, ioCost, totalCost,
                totalBwInterProvider, 0.0);
    }

    /**
     * Simule une requête TCDRM avec réplicas.
     * 
     * Response time: uses QUERY_SELECTIVITY + PARALLEL_FETCH (same model as NoRepLc).
     * Replicated relations use faster inter-region path instead of inter-provider.
     * Cost: BW charged on full relation size.
     * 
     * @param nRelations nombre de relations
     * @param replicatedRelations nombre de relations déjà répliquées localement
     * @param warmupEfficiency efficacité du warm-up (0.0 → 1.0)
     * @param rnd source d'aléatoire
     */
    public static QueryResult simulateTcdrmQuery(int nRelations, int replicatedRelations,
                                                  double warmupEfficiency, Random rnd) {
        double relationSize = TcdrmConstants.AVG_RELATION_SIZE_GB;
        double effectiveDataGb = relationSize * TcdrmConstants.QUERY_SELECTIVITY;
        double maxTransferMs = 0.0;
        double totalBwCost = 0.0;
        double totalBwInterProvider = 0.0;
        double totalBwInterRegion = 0.0;

        for (int r = 0; r < nRelations; r++) {
            boolean isReplicated = r < replicatedRelations;

            double transferMs;
            if (isReplicated && rnd.nextDouble() < warmupEfficiency) {
                // Réplica disponible → accès inter-region (même provider, faster)
                double latencyMs = TcdrmConstants.LAT_INTER_REGION_MS
                        * (1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
                transferMs = (effectiveDataGb * 8_000.0 / TcdrmConstants.BW_INTER_REGION_GBPS) + latencyMs;
                transferMs *= (1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1));

                totalBwCost += relationSize * TcdrmConstants.COST_BW_INTER_REGION;
                totalBwInterRegion += relationSize;
            } else {
                // Pas de réplica → accès inter-provider (distant)
                double latencyMs = TcdrmConstants.LAT_INTER_PROVIDER_MS
                        * (1.0 + TcdrmConstants.LATENCY_VARIATION_RATIO * (rnd.nextDouble() * 2 - 1));
                transferMs = (effectiveDataGb * 8_000.0 / TcdrmConstants.BW_INTER_PROVIDER_GBPS) + latencyMs;
                transferMs *= (1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1));

                totalBwCost += relationSize * TcdrmConstants.COST_BW_INTER_PROVIDER;
                totalBwInterProvider += relationSize;
            }

            if (TcdrmConstants.PARALLEL_FETCH) {
                maxTransferMs = Math.max(maxTransferMs, transferMs);
            } else {
                maxTransferMs += transferMs;
            }
        }

        // Join processing time (quadratic model)
        // When relations are replicated locally, join processing is faster
        // because intermediate results stay local (no cross-provider joins).
        // localFraction reduces join time: fully replicated → ~40% of remote join time.
        int nJoins = nRelations - 1;
        double joinFactor = nJoins * (nJoins + 1) / 2.0;
        int localRelations = Math.min(replicatedRelations, nRelations);
        double localFraction = (double) localRelations / nRelations;
        double joinSpeedup = 1.0 - 0.6 * localFraction * warmupEfficiency;
        double joinProcessingMs = joinFactor * TcdrmConstants.JOIN_BASE_MS * joinSpeedup
                * (1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
        double queryTimeMs = maxTransferMs + joinProcessingMs;

        // CPU cost — based on compute instructions (same model as NoRep)
        double miTotal = nRelations * nJoins * TcdrmConstants.MI_PER_JOIN_PER_RELATION;
        double cpuCost = (miTotal / 10.0) * TcdrmConstants.CPU_COST_PER_10M_MI;
        cpuCost *= (1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));

        // I/O cost
        double totalDataGb = nRelations * relationSize;
        double ioCost = totalDataGb * TcdrmConstants.IO_COST_PER_GB;

        double totalCost = totalBwCost + cpuCost + ioCost;

        return new QueryResult(queryTimeMs, totalBwCost, cpuCost, ioCost, totalCost,
                totalBwInterProvider, totalBwInterRegion);
    }
}
