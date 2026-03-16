package org.tcdrm.adaptive.core;

/**
 * Constantes centralisées pour TCDRM-Adaptive.
 * 
 * Toutes les valeurs sont alignées avec l'article TCDRM V1:
 * - Table 1: Pricing parameters (Google, AWS, Azure)
 * - Table 2: Configuration parameters (img-002.png)
 * - Figures 2-7: Simulation results
 * 
 * Ce fichier est la SOURCE UNIQUE DE VÉRITÉ côté Java.
 * Miroir Python: python_rl/config/constants.py
 */
public final class TcdrmConstants {

    private TcdrmConstants() {}

    // ==================================================================
    // Multi-Cloud Architecture (Paper Section 3.1)
    // ==================================================================
    /** Number of cloud providers (Google, AWS, Azure) */
    public static final int NUM_PROVIDERS = 3;
    /** Number of regions per provider (US, UE, AS) */
    public static final int NUM_REGIONS_PER_PROVIDER = 3;
    /** Number of VMs per region */
    public static final int NUM_VMS_PER_REGION = 20;

    // ==================================================================
    // Data Model (Paper Table 2 — img-002.png)
    // ==================================================================
    /** Average size of a relation in GB (Paper: 450 MB) */
    public static final double AVG_RELATION_SIZE_GB = 0.45;
    /** Number of relations per simple query (3 relations, 1 per region) */
    public static final int RELATIONS_SIMPLE = 3;
    /** Number of relations per complex query (6 relations, 2+ per region) */
    public static final int RELATIONS_COMPLEX = 6;

    // ==================================================================
    // SLA Parameters (Paper Table 2 — img-002.png)
    // ==================================================================
    /** T_SLA for simple queries (ms) */
    public static final double TSLA_SIMPLE_MS = 200.0;
    /** C_SLA for simple queries ($ per query) */
    public static final double CSLA_SIMPLE = 0.015;
    /** T_SLA for complex queries (ms) */
    public static final double TSLA_COMPLEX_MS = 400.0;
    /** C_SLA for complex queries ($ per query) */
    public static final double CSLA_COMPLEX = 0.040;
    /** P_SLA — popularity threshold (Paper: P_SEUIL = 200) */
    public static final int POPULARITY_THRESHOLD = 200;

    // ==================================================================
    // Replica Limits (Paper Fig. 2 — img-003.png)
    // Simple: max ~6 replicas, Complex: max ~12 replicas
    // ==================================================================
    /** Max replicas for simple queries (Fig 2: red line stabilizes at 6) */
    public static final int MAX_REPLICAS_SIMPLE = 6;
    /** Max replicas for complex queries (Fig 2: blue line stabilizes at 12) */
    public static final int MAX_REPLICAS_COMPLEX = 12;

    // ==================================================================∏
    // Bandwidth Costs (Paper Table 1 — img-001.png, averages)
    // ==================================================================
    /** $/GB — Intra-datacenter (avg of Google/AWS/Azure) */
    public static final double COST_BW_INTRA_DC = 0.002;
    /** $/GB — Inter-region (same provider) */
    public static final double COST_BW_INTER_REGION = 0.008;
    /** $/GB — Inter-provider */
    public static final double COST_BW_INTER_PROVIDER = 0.01;

    // ==================================================================
    // Infrastructure Costs (Paper Table 1 — img-001.png)
    // ==================================================================
    /** CPU cost: $/10^7 MI ~ 0.020 average (Paper Table 1).
     *  A query executes ~1 MI per join step per relation.
     *  Simple (3 rel, 2 joins): ~6 MI → 6/10^7 * $0.020 per query.
     *  This makes CPU a small fraction of total cost (matching Paper Fig 7). */
    public static final double CPU_COST_PER_10M_MI = 0.020;
    /** Estimated MI per join-step per relation (millions of instructions).
     *  Tuned so CPU is ~10-15% of total cost, matching Paper Fig 7 proportions. */
    public static final double MI_PER_JOIN_PER_RELATION = 0.15;
    /** I/O cost: $/GB average across providers */
    public static final double IO_COST_PER_GB = 0.008;
    /** Storage cost: $/GB/month (Paper: ~0.02) */
    public static final double STORAGE_COST_PER_GB_PER_MONTH = 0.02;
    /** Replica maintenance cost per replica per query: storage amortized + write I/O overhead.
     *  Each replica incurs write amplification (sync writes) + storage cost.
     *  Paper Eq 2 includes C_IO for replicas. This makes replica cost visible in Fig 7. */
    public static final double REPLICA_MAINTENANCE_COST_PER_QUERY = 0.002;

    // ==================================================================
    // Network Parameters
    // ==================================================================
    /** Intra-DC bandwidth (Gbps) — high-speed local */
    public static final double BW_INTRA_DC_GBPS = 10.0;
    /** Inter-region bandwidth (Gbps) — within same provider */
    public static final double BW_INTER_REGION_GBPS = 2.0;
    /** Inter-provider bandwidth (Gbps) — internet */
    public static final double BW_INTER_PROVIDER_GBPS = 1.0;
    /** Latency intra-DC (ms) */
    public static final double LAT_INTRA_DC_MS = 1.0;
    /** Latency inter-region (ms) */
    public static final double LAT_INTER_REGION_MS = 30.0;
    /** Latency inter-provider (ms) */
    public static final double LAT_INTER_PROVIDER_MS = 80.0;

    // ==================================================================
    // Simulation (Paper Section 4.1)
    // ==================================================================
    /** Number of queries per experiment (Paper: 1000) */
    public static final int MAX_QUERIES = 1000;
    /** Budget initial ($) */
    public static final double INITIAL_BUDGET = 1000.0;

    // ==================================================================
    // Warm-up / Gradual Replica Effectiveness
    // Paper Fig 3: response time drops gradually after P_SLA,
    // not instantly — replicas need time to become fully effective.
    // ==================================================================
    /** Queries for a new replica to reach full efficiency */
    public static final int WARMUP_QUERIES = 10;
    /** Sigmoid steepness for warmup */
    public static final double WARMUP_SIGMOID_K = 8.0;

    // ==================================================================
    // Query Execution Model
    // Paper uses CloudSim cloudlets — queries don't transfer full relations.
    // Response time depends on effective data (selectivity), but BW cost
    // is charged on full relation size by providers.
    // ==================================================================
    /** Fraction of relation data actually transferred per query (join selectivity) */
    public static final double QUERY_SELECTIVITY = 0.015;
    /** Base join processing time per join step (ms) */
    public static final double JOIN_BASE_MS = 20.0;
    /** Whether relation fetches happen in parallel (true) or sequential */
    public static final boolean PARALLEL_FETCH = true;

    // ==================================================================
    // Simulation Noise (realistic cloud variability)
    // ==================================================================
    /** Network jitter ratio — 10% variation for realistic cloud behavior */
    public static final double JITTER_RATIO = 0.10;
    /** CPU jitter ratio — 8% variation for processing time */
    public static final double CPU_JITTER_RATIO = 0.08;
    /** Inter-provider latency variation — 15% for internet variability */
    public static final double LATENCY_VARIATION_RATIO = 0.15;

    // ==================================================================
    // Utility Methods
    // ==================================================================

    /**
     * Max replicas based on query type.
     * Simple queries (3 relations) → 6, Complex (6 relations) → 12.
     * Each relation can have up to 2 replicas placed across providers.
     */
    public static int maxReplicasForQueryType(boolean complex) {
        return complex ? MAX_REPLICAS_COMPLEX : MAX_REPLICAS_SIMPLE;
    }

    /**
     * Cost of creating one replica of a relation (inter-provider transfer).
     */
    public static double replicationCost(double relationSizeGb) {
        return relationSizeGb * COST_BW_INTER_PROVIDER;
    }

    /**
     * Warmup efficiency using sigmoid function.
     * Models the gradual effectiveness shown in Paper Fig 3.
     */
    public static double warmupEfficiency(int queriesSinceCreation) {
        if (queriesSinceCreation >= WARMUP_QUERIES) {
            return 1.0;
        }
        double x = (double) queriesSinceCreation / WARMUP_QUERIES;
        return 1.0 / (1.0 + Math.exp(-WARMUP_SIGMOID_K * (x - 0.5)));
    }

    /**
     * Compute total data size for a query type.
     */
    public static double queryDataSizeGb(boolean complex) {
        int nRelations = complex ? RELATIONS_COMPLEX : RELATIONS_SIMPLE;
        return nRelations * AVG_RELATION_SIZE_GB;
    }
}
