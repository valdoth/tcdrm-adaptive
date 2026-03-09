"""
Constantes centralisées pour TCDRM-Adaptive.

Toutes les valeurs sont alignées avec le Tableau 1 de l'article TCDRM V1
et synchronisées avec TcdrmConstants.java.
Ce module est la SOURCE UNIQUE DE VÉRITÉ pour les paramètres du système côté Python.

Références:
- TCDRM V1, Tableau 1: Paramètres de simulation
- Java: src/main/java/org/tcdrm/adaptive/core/TcdrmConstants.java
"""

import math


class TcdrmConstants:
    """Paramètres centralisés du système TCDRM, alignés avec l'article TCDRM V1."""

    # ==================================================================
    # Query Model (Article TCDRM V1 - Tableau 2)
    # ==================================================================
    AVG_RELATION_SIZE_GB = 0.45   # Taille moyenne d'une relation (450 MB)
    RELATIONS_SIMPLE = 3          # Nombre de relations par requête simple
    RELATIONS_COMPLEX = 6         # Nombre de relations par requête complexe

    # ==================================================================
    # SLA Parameters (Article TCDRM V1 - Tableau 1)
    # ==================================================================
    TSLA_SIMPLE_MS = 200.0   # Seuil SLA temps de réponse simple (ms)
    TSLA_COMPLEX_MS = 400.0  # Seuil SLA temps de réponse complexe (ms)
    CSLA_SIMPLE = 0.015      # Coût SLA par requête simple ($)
    CSLA_COMPLEX = 0.040     # Coût SLA par requête complexe ($)

    # Aliases for backward compatibility
    TSLA_MS = TSLA_SIMPLE_MS
    TSLA_S = TSLA_SIMPLE_MS / 1000.0
    CSLA = CSLA_SIMPLE

    # ==================================================================
    # Réplication (Article TCDRM V1 - Tableau 1)
    # ==================================================================
    MAX_REPLICAS_SIMPLE = 6   # Max réplicas pour simple queries (= RELATIONS_SIMPLE * 2)
    MAX_REPLICAS_COMPLEX = 12 # Max réplicas pour complex queries (= RELATIONS_COMPLEX * 2)

    # ==================================================================
    # Coûts de bande passante (Article TCDRM V1 - Tableau 1)
    # ==================================================================
    COST_BW_INTRA_DC = 0.002        # $/GB - Intra-datacenter
    COST_BW_INTER_REGION = 0.008    # $/GB - Inter-région (même fournisseur)
    COST_BW_INTER_PROVIDER = 0.01   # $/GB - Inter-fournisseur

    # ==================================================================
    # Coûts infrastructure (Article TCDRM V1 - Tableau 1)
    # ==================================================================
    CPU_COST_PER_10M_MI = 0.02             # $/10^7 MI (Paper Table 1)
    MI_PER_JOIN_PER_RELATION = 0.15         # MI per join step per relation
    IO_COST_PER_GB = 0.008                 # $/GB I/O
    STORAGE_COST_PER_GB_PER_MONTH = 0.02   # $/GB/mois
    REPLICA_MAINTENANCE_COST_PER_QUERY = 0.002  # storage + write I/O per replica per query

    # Backward-compatible aliases (used by RL envs)
    CPU_COST_PER_UNIT = CPU_COST_PER_10M_MI
    CPU_COST_PER_HOUR = CPU_COST_PER_10M_MI
    STORAGE_COST_PER_GB_PER_HOUR = STORAGE_COST_PER_GB_PER_MONTH / 720.0

    # ==================================================================
    # Paramètres réseau (Article TCDRM V1 - Tableau 1)
    # ==================================================================
    BW_INTRA_DC_GBPS = 10.0          # Intra-datacenter
    BW_INTER_REGION_GBPS = 5.0       # Inter-région (même fournisseur)
    BW_INTER_PROVIDER_GBPS = 1.0     # Inter-fournisseur

    LAT_INTRA_DC_MS = 1.0            # Intra-datacenter
    LAT_INTER_REGION_MS = 20.0       # Inter-région
    LAT_INTER_PROVIDER_MS = 80.0     # Inter-fournisseur

    # Aliases for backward compatibility (RL envs)
    BW_LOCAL_GBPS = BW_INTRA_DC_GBPS
    BW_REMOTE_GBPS = BW_INTER_PROVIDER_GBPS
    LAT_LOCAL_MS = LAT_INTRA_DC_MS
    LAT_REMOTE_MS = LAT_INTER_PROVIDER_MS

    # ==================================================================
    # Query Execution Model
    # Response time uses selectivity (partial data), costs use full relation.
    # ==================================================================
    QUERY_SELECTIVITY = 0.015   # Fraction of relation transferred per query
    JOIN_BASE_MS = 20.0         # Base join processing time per step (quadratic model)
    PARALLEL_FETCH = True       # Relations fetched in parallel

    # ==================================================================
    # Simulation
    # ==================================================================
    MAX_QUERIES = 1000         # Nombre max de requêtes par épisode
    INITIAL_BUDGET = 1000.0    # Budget initial ($)
    POPULARITY_THRESHOLD = 200 # Seuil de popularité (P_SLA)

    # ==================================================================
    # Warm-up réplicas
    # ==================================================================
    WARMUP_QUERIES = 10        # Requêtes pour atteindre 100% efficacité
    WARMUP_SIGMOID_K = 8.0     # Paramètre k de la sigmoid de warm-up

    # ==================================================================
    # Simulation bruit (realistic cloud variability)
    # ==================================================================
    JITTER_RATIO = 0.10        # Ratio de jitter réseau (10% for realistic behavior)
    CPU_JITTER_RATIO = 0.08    # Ratio de jitter CPU (8% for processing variation)
    LATENCY_VARIATION_RATIO = 0.15  # Variation latence inter-provider (15% internet variability)

    # ==================================================================
    # Actions RL
    # ==================================================================
    ACTION_NOOP = 0
    ACTION_REPLICATE = 1
    ACTION_DELETE = 2
    N_ACTIONS = 3

    # ==================================================================
    # Q-Learning spécifique
    # ==================================================================
    QLEARNING_STATE_DIMS = 5   # (RT, COST, POP, BUD, NET)
    QLEARNING_BINS_PER_DIM = 3
    QLEARNING_N_STATES = 243   # 3^5

    # ==================================================================
    # DQN spécifique
    # ==================================================================
    DQN_STATE_DIM = 8          # Dimensions de l'état continu
    DQN_HIDDEN_DIMS = [64, 64] # Couches cachées (partagées Dueling)

    @classmethod
    def max_replicas_for_query_type(cls, complex_query: bool) -> int:
        """Retourne le nombre max de réplicas selon le type de requête."""
        return cls.MAX_REPLICAS_COMPLEX if complex_query else cls.MAX_REPLICAS_SIMPLE

    @classmethod
    def max_replicas_for_data(cls, data_gb: float) -> int:
        """Retourne le nombre max de réplicas selon la taille des données (backward compat)."""
        return cls.MAX_REPLICAS_COMPLEX if data_gb >= cls.RELATIONS_COMPLEX * cls.AVG_RELATION_SIZE_GB else cls.MAX_REPLICAS_SIMPLE

    @classmethod
    def replication_cost(cls, data_gb: float) -> float:
        """Coût de création d'un réplica (transfert inter-provider)."""
        return data_gb * cls.COST_BW_INTER_PROVIDER

    @classmethod
    def warmup_efficiency(cls, queries_since_creation: int) -> float:
        """Calcule l'efficacité du warm-up avec fonction sigmoid."""
        if queries_since_creation >= cls.WARMUP_QUERIES:
            return 1.0
        x = queries_since_creation / cls.WARMUP_QUERIES
        return 1.0 / (1.0 + math.exp(-cls.WARMUP_SIGMOID_K * (x - 0.5)))
