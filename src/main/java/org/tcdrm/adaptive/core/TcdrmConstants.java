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
    /** C_SLA for simple queries ($ per query) — Paper Table 1: 0.015 */
    public static final double CSLA_SIMPLE = 0.015;
    /** T_SLA for complex queries (ms) */
    public static final double TSLA_COMPLEX_MS = 400.0;
    /** C_SLA for complex queries ($ per query) */
    public static final double CSLA_COMPLEX = 0.040;
    /**
     * Seuil de popularité P_SLA (Paper Table 1 : 200).
     * Un fragment est "populaire" quand son accessCount ≥ P_SLA.
     * Avec 1000 requêtes accédant tous les fragments, la condition est atteinte au query 200.
     * Reproduit le comportement visible en Fig. 2 du papier : réplication démarre après ~200 requêtes.
     * Utilisé par TCDRM uniquement.
     */
    public static final int P_SLA = 200;

    // ==================================================================
    // Replica Limits (Paper Fig. 2 — img-003.png)
    // Simple: max ~6 replicas, Complex: max ~12 replicas
    // ==================================================================
    /** Max replicas for simple queries (Fig 2: red line stabilizes at 6) */
    public static final int MAX_REPLICAS_SIMPLE = 6;
    /** Max replicas for complex queries (Fig 2: blue line stabilizes at 12) */
    public static final int MAX_REPLICAS_COMPLEX = 12;
    /**
     * Minimum popularityScore (accessCount/P_SLA) before the RL agent is allowed to replicate.
     * = 0.3 → gate opens at query 60 (30% of P_SLA).
     * Aligns the pre-replication phase with NoRepLC/TCDRM so the initial states are identical,
     * making the comparison fair. Agent decides freely above this threshold.
     */
    public static final double MIN_POPULARITY_TO_REPLICATE = 0.3;

    // ==================================================================∏
    // Bandwidth Costs (Paper Table 1 — img-001.png, averages)
    // ==================================================================
    /** $/GB — Intra-datacenter (all 3 providers: 0.001 per SimulationProvidersParameters) */
    public static final double COST_BW_INTRA_DC = 0.001;
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
    /**
     * Coût de maintenance par réplica par requête.
     * Paper Eq. 2 : C_Q = C_CPU + C_IO + C_bandwidth uniquement.
     * Le stockage est implicitement capturé dans C_IO lors de la création.
     * Valeur 0.0 pour rester fidèle au modèle économique du papier.
     */
    public static final double REPLICA_MAINTENANCE_COST_PER_QUERY = 0.0;

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
    /** Number of queries per experiment (Paper Section 4.1: 1000) */
    public static final int MAX_QUERIES = 1000;
    /**
     * Budget initial du locataire ($).
     * Calibré à 2× le coût total attendu sous CSLA (simple: 3000×0.010×2 = 60$).
     * Permet que la dimension "budget" dans l'état RL soit informative (décroissance visible).
     */
    public static final double INITIAL_BUDGET = 60.0;
    
    // ==================================================================
    // Popularité (Paper Table 2)
    // ==================================================================
    /** Ratio lecture/écriture pour workload OLAP (90% lectures). */
    public static final double READ_WRITE_RATIO = 0.9;

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
    // Replica Lifetime / Anti-oscillation
    // Empêche les allers-retours réplication/suppression en imposant
    // une durée de vie minimale avant suppression.
    // ==================================================================
    /** Nombre minimal de requêtes avant qu'un réplica puisse être supprimé */
    public static final int MIN_REPLICA_LIFETIME_QUERIES = 30;
    /** Cooldown de re-création après une suppression pour éviter re-création immédiate */
    public static final int REPLICA_RECREATE_COOLDOWN_QUERIES = 80;
    /**
     * Fenêtre glissante de popularité. Un fragment accédé dans les dernières
     * POPULARITY_WINDOW_QUERIES requêtes est "encore utilisé" : son réplica est protégé
     * contre la suppression tant que les données restent populaires.
     */
    public static final int POPULARITY_WINDOW_QUERIES = 50;

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
