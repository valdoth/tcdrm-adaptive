"""
Générateur de workloads pour l'entraînement RL - TCDRM v2

Module partagé entre train_dqn_policy.py et train_simple_qlearning.py.
Génère des séquences de requêtes avec des patterns de charge variés
simulant des scénarios cloud réalistes.

Patterns disponibles:
- steady: Charge constante
- burst: Pic soudain au milieu
- cold_to_hot / hot_to_cold: Transition 
- daily_cycle: Cycle jour/nuit (sinusoïde)
- weekend: Baisse week-end
- budget_critical: Budget décroissant
- read_intensive: 90% lectures, 10% écritures
- write_intensive: 30% lectures, 70% écritures
- geo_distributed: Requêtes multi-régions (EU/US/ASIA)
- black_friday: Événement saisonnier avec pic extrême

Références:
- READ2.md: Habib (2016) - workload patterns for VM management
- TCDRM V1: patterns d'accès aux données distribuées
"""

import numpy as np
from typing import List


# Distribution des patterns pour l'entraînement (somme = 1.0)
DEFAULT_PATTERN_DISTRIBUTION = {
    'steady': 0.15,
    'burst': 0.15,
    'cold_to_hot': 0.10,
    'hot_to_cold': 0.10,
    'daily_cycle': 0.08,
    'weekend': 0.05,
    'budget_critical': 0.05,
    'read_intensive': 0.12,
    'write_intensive': 0.08,
    'geo_distributed': 0.10,
    'black_friday': 0.02,
}

ALL_PATTERNS = list(DEFAULT_PATTERN_DISTRIBUTION.keys())
DEFAULT_PATTERN_PROBS = list(DEFAULT_PATTERN_DISTRIBUTION.values())


def generate_varied_queries(n_queries: int, seed: int = 42, pattern: str = 'steady') -> List[float]:
    """
    Génère des requêtes avec tailles variées selon différents patterns.

    Args:
        n_queries: Nombre de requêtes à générer
        seed: Seed pour reproductibilité
        pattern: Type de pattern de charge

    Returns:
        Liste de tailles de requêtes en GB
    """
    rng = np.random.RandomState(seed)
    query_sizes = []

    if pattern == 'steady':
        for _ in range(n_queries):
            rand = rng.random()
            if rand < 0.4:
                size = rng.uniform(1.0, 5.0)
            elif rand < 0.8:
                size = rng.uniform(5.0, 10.0)
            else:
                size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)

    elif pattern == 'burst':
        burst_start = n_queries // 3
        burst_end = 2 * n_queries // 3
        for i in range(n_queries):
            if burst_start <= i < burst_end:
                rand = rng.random()
                if rand < 0.2:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.5:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            else:
                rand = rng.random()
                if rand < 0.6:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.9:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)

    elif pattern == 'cold_to_hot':
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < progress:
                size = rng.uniform(8.0, 20.0)
            else:
                size = rng.uniform(1.0, 6.0)
            query_sizes.append(size)

    elif pattern == 'hot_to_cold':
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < (1.0 - progress):
                size = rng.uniform(8.0, 20.0)
            else:
                size = rng.uniform(1.0, 6.0)
            query_sizes.append(size)

    elif pattern == 'daily_cycle':
        for i in range(n_queries):
            phase = (i / n_queries) * 2 * np.pi
            intensity = 0.5 + 0.5 * np.sin(phase)
            rand = rng.random()
            if rand < intensity:
                size = rng.uniform(5.0, 20.0)
            else:
                size = rng.uniform(1.0, 8.0)
            query_sizes.append(size)

    elif pattern == 'weekend':
        week_length = max(1, n_queries // 7)
        for i in range(n_queries):
            day_of_week = (i // week_length) % 7
            is_weekend = day_of_week >= 5
            if is_weekend:
                rand = rng.random()
                if rand < 0.7:
                    size = rng.uniform(1.0, 5.0)
                else:
                    size = rng.uniform(5.0, 12.0)
            else:
                rand = rng.random()
                if rand < 0.4:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.8:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)

    elif pattern == 'budget_critical':
        for i in range(n_queries):
            progress = i / n_queries
            budget_level = 1.0 - progress
            if budget_level < 0.1:
                size = rng.uniform(1.0, 3.0)
            else:
                rand = rng.random()
                if rand < 0.4:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.8:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)

    elif pattern == 'read_intensive':
        for _ in range(n_queries):
            is_read = rng.random() < 0.9
            if is_read:
                size = rng.uniform(0.1, 5.0)
            else:
                size = rng.uniform(5.0, 20.0)
            query_sizes.append(size)

    elif pattern == 'write_intensive':
        for _ in range(n_queries):
            is_read = rng.random() < 0.3
            if is_read:
                size = rng.uniform(0.5, 5.0)
            else:
                size = rng.uniform(10.0, 50.0)
            query_sizes.append(size)

    elif pattern == 'geo_distributed':
        regions = ['EU', 'US', 'ASIA']
        region_probs = [0.40, 0.35, 0.25]

        for _ in range(n_queries):
            region = rng.choice(regions, p=region_probs)

            if region == 'EU':
                size = rng.uniform(2.0, 10.0)
            elif region == 'US':
                size = rng.uniform(5.0, 15.0)
            else:  # ASIA
                size = rng.uniform(0.5, 8.0)

            query_sizes.append(size)

    elif pattern == 'black_friday':
        for i in range(n_queries):
            progress = i / n_queries

            if progress < 0.3:
                multiplier = 1.0
            elif progress < 0.4:
                multiplier = 1.0 + (progress - 0.3) * 50
            elif progress < 0.5:
                multiplier = 10.0
            elif progress < 0.6:
                multiplier = 10.0 - (progress - 0.5) * 80
            else:
                multiplier = 1.0

            base_size = rng.uniform(1.0, 5.0)
            size = min(base_size * multiplier, 100.0)
            query_sizes.append(size)

    else:
        return generate_varied_queries(n_queries, seed, 'steady')

    return query_sizes


def select_pattern(rng: np.random.RandomState = None) -> str:
    """Sélectionne un pattern aléatoire selon la distribution par défaut."""
    if rng is None:
        return np.random.choice(ALL_PATTERNS, p=DEFAULT_PATTERN_PROBS)
    return rng.choice(ALL_PATTERNS, p=DEFAULT_PATTERN_PROBS)
