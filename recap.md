# Récapitulatif — Résultats reproductibles (TCDRM-ADAPTIVE)

**Régime de validation** : workload `variable`, seed `777` (généralisation — requêtes différentes du benchmark steady/42).
**1000 requêtes par modèle.** Reproductibilité **vérifiée** : 8/8 lignes identiques sur 2 runs consécutifs (A et B).

## Requêtes simples

| Modèle | Coût total ($) | Viol. SLA | Réplicas | Inter-région (GB) | Inter-provider (GB, cher) |
|---|---|---|---|---|---|
| NoRepLc | 8.169 | 755 | 0 | 791.55 | 558.45 |
| TCDRM | 8.169 | 755 | 0 | 791.55 | 558.45 |
| Q-Learning | 6.925 | 545 | 3 | 1001.70 | 410.85 |
| **Rainbow DQN** | **6.531** | **432** | 6 | **1075.50** | **363.60** |

## Requêtes complexes

| Modèle | Coût total ($) | Viol. SLA | Réplicas | Inter-région (GB) | Inter-provider (GB, cher) |
|---|---|---|---|---|---|
| NoRepLc | 30.370 | 1000 | 0 | 1224.45 | 1475.55 |
| TCDRM | 27.545 | 540 | 6 | 1624.05 | 1215.90 |
| Q-Learning | 26.183 | 334 | 12 | 1876.05 | 1165.50 |
| **Rainbow DQN** | **24.957** | **272** | 12 | **1992.60** | **1096.20** |

## Lecture

- **Rainbow DQN est le meilleur sur les deux régimes** — coût, violations SLA *et* usage inter-région — devant Q-Learning, lui-même devant TCDRM et NoRepLc.
- Les agents apprenants basculent le trafic depuis l'inter-provider coûteux vers l'inter-région moins cher (via les réplicas), d'où la réduction de coût.
- Réduction de coût de Rainbow vs baselines : **−20.0 %** vs NoRepLc et **−20.0 %** vs TCDRM en simple ; **−17.8 %** vs NoRepLc et **−9.4 %** vs TCDRM en complexe.

## Note méthodologique — reproductibilité

Le benchmark/validation effectue de l'**apprentissage en ligne** pendant l'évaluation (mise à jour des Q-tables et méta-adaptation des seuils à chaque requête). Deux tirages aléatoires globaux non initialisés (`np.random`) le rendaient non reproductible d'un lancement à l'autre : le choix de table A/B du Double Q-learning et l'échantillonnage priorisé du replay buffer de Rainbow. Après initialisation déterministe des générateurs (RNG dédié pour Q-Learning + `np.random.seed` / `torch.manual_seed` réinitialisés à chaque run), les résultats sont stables et comparables entre modèles.

> Colonnes brutes : `validation/metrics/summary_phase2_rl.csv` — reproduire avec `cd validation && ./run.sh comparison`.
