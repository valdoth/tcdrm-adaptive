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

    /**
     * Constante de lissage (λ) de la POPULARITÉ = EMA du taux d'accès récent par
     * fragment (définition littérature : fréquence d'accès sur fenêtre à décroissance
     * exponentielle — cf. DPRS, modèles time-decay). Un fragment accédé en continu voit
     * son EMA atteindre ~0.9 après ~1/λ ≈ 200 requêtes, calé sur le P_SLA du papier
     * (maturité de popularité). Encode la CONFIANCE (accès soutenu requis) et gère la
     * décroissance des données qui refroidissent — sans aucun seuil statique.
     * λ ≈ ln(10)/200 ≈ 0.0115.
     */
    public static final double POPULARITY_EMA_LAMBDA = 0.0115;

    /**
     * Valeur d'ÉQUILIBRE de l'EMA pour un fragment accédé à CHAQUE requête pendant
     * P_SLA requêtes : 1 − (1−λ)^P_SLA. L'EMA brute étant asymptotique (converge vers 1
     * sans jamais l'atteindre), on NORMALISE la popularité par cette valeur pour qu'un
     * accès soutenu sur P_SLA requêtes corresponde à popularité = 1.0 EXACTEMENT (comme
     * l'ancienne formule accessCount/P_SLA atteignait 1.0 à q=200). Rend le contrat
     * (1.0) atteignable — sinon TCDRM (seuil fixe 1.0) et le contrat RL ne pourraient
     * jamais déclencher de réplication.
     */
    public static final double POPULARITY_EMA_FULL =
        1.0 - Math.pow(1.0 - POPULARITY_EMA_LAMBDA, P_SLA);

    /** Popularité normalisée [0,1] à partir de l'EMA brute (taux d'accès récent). */
    public static double normalizedPopularity(double ema) {
        return Math.min(1.0, ema / POPULARITY_EMA_FULL);
    }

    // ==================================================================
    // Replica Limits (Paper Fig. 2 — img-003.png)
    // Simple: max ~6 replicas, Complex: max ~12 replicas
    // ==================================================================
    /** Max replicas for simple queries (Fig 2: red line stabilizes at 6) */
    public static final int MAX_REPLICAS_SIMPLE = 6;
    /** Max replicas for complex queries (Fig 2: blue line stabilizes at 12) */
    public static final int MAX_REPLICAS_COMPLEX = 12;
    /**
     * Éligibilité popularité (Paper Algorithm 1 : IF pd_i > P_SLA THEN répliquer).
     * Le contrat = popularité normalisée 1.0 (accessCount ≥ P_SLA).
     *
     * Sujet 1 : la VALEUR du seuil n'existe nulle part en dur — elle est choisie à
     * CHAQUE REQUÊTE par un méta-contrôleur Q-learning PAR AGENT
     * ({@link org.tcdrm.adaptive.rl.ThresholdMetaLearner}) qui sélectionne librement
     * un niveau dans [0,1] (aucune limite de vitesse d'ajustement, aucune cadence fixe).
     * Le seuil repart du contrat (1.0) à chaque run, et la récompense méta inclut un
     * terme de fidélité au contrat : s'en éloigner n'est appris que si cela supprime
     * des violations SLA.
     */
    /**
     * Constante de lissage (EMA) du signal de stress observé par les méta-contrôleurs
     * (taux de violations T_SLA, ratio coût/C_SLA). Les méta-contrôleurs observent ce
     * signal et peuvent ajuster les seuils À CHAQUE REQUÊTE — il n'existe AUCUNE
     * cadence fixe de décision (aucune « fenêtre de N requêtes ») : le MOMENT du
     * déclenchement est entièrement déterminé par la politique apprise de chaque agent
     * (Sujet 1 : « quand décider » est appris, pas cadencé). α est un paramètre de
     * filtrage du bruit de mesure (mémoire effective ≈ 2/α − 1 requêtes), au même
     * titre que les buckets d'état — pas une règle de comportement.
     */
    public static final double META_EMA_ALPHA = 0.04;
    /**
     * Résolution de discrétisation de l'espace d'action du seuil de popularité
     * (11 niveaux sur [0,1]) — granularité de grille, PAS une limite d'ajustement :
     * le méta-contrôleur peut passer de n'importe quel niveau à n'importe quel autre.
     */
    public static final double META_POPULARITY_RESOLUTION = 0.10;
    /** Résolution de discrétisation du multiplicateur T_SLA (grille, pas une limite). */
    public static final double META_TSLA_RESOLUTION = 0.05;
    /** Borne basse du multiplicateur T_SLA appris (fraction du contrat). */
    public static final double META_TSLA_MIN_MULTIPLIER = 0.60;
    /** Répertoire des Q-tables du méta-contrôleur (persistées entre entraînement et éval). */
    public static final String META_QTABLE_DIR = "tcdrm_gym/models";

    /**
     * Fichier de Q-table du méta-contrôleur pour un agent, un seuil et un workload donnés.
     * Les Q-tables sont PAR AGENT : Q-Learning et Rainbow apprennent chacun leur propre
     * politique d'ajustement des seuils — leurs moments de déclenchement sont donc
     * indépendants (pas de seuil commun partagé).
     */
    public static java.io.File metaQtableFile(String agentTag, String kind, boolean complex) {
        return new java.io.File(META_QTABLE_DIR,
            "meta_threshold_" + agentTag + "_" + kind + "_" + (complex ? "complex" : "simple") + ".qtable");
    }

    /**
     * Fichier des poids de récompense utilisés à l'ENTRAÎNEMENT d'un agent — écrit par
     * le TrainingServer, rechargé par le benchmark pour que la récompense d'évaluation
     * (apprentissage online) soit toujours alignée sur celle de l'entraînement.
     */
    public static java.io.File rewardConfigFile(String agentTag) {
        return new java.io.File(META_QTABLE_DIR, "reward_config_" + agentTag + ".properties");
    }

    // ==================================================================
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

    /**
     * Convexité du coût de détention des réplicas (Sujet 1) : le coût est
     * (1 − popularité)^k — fonction LISSE, sans seuil statique. k > 1 concentre la
     * pénalité sur les données FROIDES (pop → 0) tout en l'écrasant pour les données
     * qui chauffent (pop moyenne). Réglage de FORME de récompense (comme les poids
     * r1..r9), pas un seuil de comportement : rien n'est codé en dur sur QUAND répliquer.
     */
    public static final double REPLICA_HOLDING_CONVEXITY = 2.5;

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
     * Facteur de taille du pool de fragments pour les workloads dynamiques
     * (variable/burst) : pool = relations_par_requête × facteur. Une requête ne
     * touche qu'une fraction du pool — la popularité devient une propriété émergente
     * par fragment (inspiré des workloads de traces réelles Google/Alibaba).
     */
    public static final int WORKLOAD_POOL_FACTOR = 3;
    /**
     * Budget initial du locataire ($) — STATIQUE (contrat client, comme C_SLA).
     * Couvre le coût contractuel max d'un run de 1000 requêtes avec marge :
     * simple 1000×0.015 = 15$, complex 1000×0.040 = 40$ → 60$ laisse une marge
     * tout en rendant la dimension "budget" de l'état RL informative (décroissance visible).
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
    // not instantly — replicas need time to become fully effective,
    // and keeps improving until the MAX number of replicas is reached.
    // ==================================================================
    /** Queries for a new replica to reach full efficiency */
    public static final int WARMUP_QUERIES = 10;
    /** Sigmoid steepness for warmup */
    public static final double WARMUP_SIGMOID_K = 8.0;
    /**
     * Réduction du temps de transfert apportée par le PREMIER réplica d'un fragment
     * (copie plus proche + moins de contention sur le primaire).
     */
    public static final double REPLICA_TRANSFER_GAIN_FIRST = 0.30;
    /**
     * Réduction ADDITIONNELLE par réplica supplémentaire du même fragment (partage de
     * charge entre copies). Rend le gain PROGRESSIF avec le facteur de réplication,
     * comme la Fig. 3 du papier : le temps baisse jusqu'au nombre max de réplicas.
     */
    public static final double REPLICA_TRANSFER_GAIN_EXTRA = 0.15;
    /**
     * Accélération maximale de la phase de jointure quand la couverture réplicas est
     * complète (toutes les relations servies par des copies chaudes locales).
     */
    public static final double JOIN_COVERAGE_SPEEDUP = 0.6;

    // ==================================================================
    // Suppression de réplicas (Paper Algorithm 3) — fenêtre ΔT APPRISE
    // ==================================================================
    /**
     * La fenêtre d'observation ΔT de l'Algorithme 3 (le papier la définit comme
     * « configurable observation window ») est APPRISE par un méta-contrôleur
     * Q-learning par agent — aucune valeur en dur. Elle unifie les trois anciens
     * paramètres statiques (durée de vie minimale 30, fenêtre de popularité 50,
     * cooldown 80) : l'Algorithme 3 s'exprime entièrement en unités de ΔT —
     * un réplica ne peut être jugé froid qu'après ΔT d'observation (durée de vie
     * minimale), il n'est supprimable que si les données ne sont plus accédées
     * depuis ΔT, et après suppression on attend ΔT avant re-création (anti-
     * oscillation). ΔT est exprimé en fraction du P_SLA contractuel.
     */
    /** Résolution de la grille d'action du ΔT de suppression (fraction de P_SLA). */
    public static final double META_DELETION_WINDOW_RESOLUTION = 0.05;
    /** Borne basse de définition du ΔT (fraction de P_SLA) — une fenêtre nulle n'a pas de sens. */
    public static final double META_DELETION_WINDOW_MIN = 0.05;

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
