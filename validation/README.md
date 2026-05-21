# TCDRM‑ADAPTIVE — Validation RL

Ce dossier contient les runners Java et le client Python pour valider les modèles RL entraînés.

## Prérequis

- Java 17+
- Maven 3.9+ (pour construire le JAR si nécessaire)
- Python 3.9+ avec `uv` (client RL)

## Structure

```
validation/
├── lib/                    # JAR ombré (shaded) avec toutes les dépendances
├── models/                 # Modèles RL (synchronisés depuis tcdrm_gym/models/)
│   ├── qlearning_cloudsim.pkl
│   ├── qlearning_cloudsim_final.pkl
│   ├── dqn_cloudsim.pt
│   └── dqn_cloudsim_final.pt
├── images/                 # PNG générés
├── metrics/                # CSV générés
├── python/                 # Client Py4J
│   ├── pyproject.toml
│   ├── connect_to_java.py
│   └── bridge/
│       ├── rl_bridge.py    # Wrapper → tcdrm_gym/bridge/rl_bridge.py (agents réels)
│       └── client.py
├── QLearningEvaluation.java
├── DNNEvaluation.java
└── RLComparisonEvaluation.java
```

## Lancer la validation (recommandé)

```bash
cd validation
bash run.sh
```

Ce script :
1. Rebuild le JAR (`mvn -DskipTests package`)
2. Synchronise les derniers modèles depuis `tcdrm_gym/models/` (écrasement forcé)
3. Compile les runners Java
4. Lance les 3 runners dans l'ordre : QLearning → DQN → RLComparison

## Sorties produites

| Fichier | Description |
|---------|-------------|
| `images/metrics_qlearning_simple.png` | Latence/replicas/coût Q-Learning (simple) |
| `images/metrics_qlearning_complex.png` | Latence/replicas/coût Q-Learning (complex) |
| `images/metrics_dqn_simple.png` | Latence/replicas/coût DQN (simple) |
| `images/metrics_dqn_complex.png` | Latence/replicas/coût DQN (complex) |
| `images/popularity_*.png` | Analyse popularité par modèle |
| `images/fig1_replica_factor_4models.png` | **Figure papier 1** : facteur de réplication (4 modèles) |
| `images/fig2_response_time_4models.png` | **Figure papier 2** : temps de réponse (4 modèles) |
| `images/fig3_bw_consumption_4models.png` | **Figure papier 3** : consommation BW |
| `images/fig4_avg_bw_price_4models.png` | **Figure papier 4** : prix moyen BW |
| `images/fig5_cumulative_bw_price_4models.png` | **Figure papier 5** : coût BW cumulatif |
| `images/fig6_total_cost_4models.png` | **Figure papier 6** : coût total |
| `metrics/rl_qlearning_simple.csv` | Métriques par requête Q-Learning simple |
| `metrics/rl_qlearning_complex.csv` | Métriques par requête Q-Learning complex |
| `metrics/rl_dqn_simple.csv` | Métriques par requête DQN simple |
| `metrics/rl_dqn_complex.csv` | Métriques par requête DQN complex |
| `metrics/summary_phase2_rl.csv` | Résumé comparatif 8 runs (4 modèles × 2 workloads) |

## Résultats attendus (après les 5 corrections de simulation)

| Modèle | time[500] | replicas[999] |
|--------|-----------|---------------|
| NoRepLc_Simple | ~210ms ≈ TSLA_S=200ms | 0 |
| TCDRM_Simple | ~99ms (baisse après réplication) | 6 |
| NoRepLc_Complex | ~444ms ≈ TSLA_C=400ms | 0 |
| TCDRM_Complex | ~225ms | 12 |
| QLearning_Simple | < TSLA_S après apprentissage | ≤ 6 |
| DQN_Simple | < TSLA_S après apprentissage | ≤ 6 |

Ordre coût BW cumulatif attendu : NoRepLc > NoReplication > TCDRM > RL (fig5).

## Lancement manuel (debugging)

```bash
# Démarrer le client Python
cd validation/python
uv sync
uv run python connect_to_java.py --port 25333 \
  --qlearning-model ../models/qlearning_cloudsim.pkl \
  --dqn-model       ../models/dqn_cloudsim.pt

# Dans un autre terminal
cd validation
JAR=lib/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar
java -cp .:$JAR QLearningEvaluation
java -cp .:$JAR DNNEvaluation
java -cp .:$JAR RLComparisonEvaluation
```

## Notes

- Le port Py4J par défaut est `25333` (modifiable via `TCDRM_PY4J_PORT`).
- `validation/python/bridge/rl_bridge.py` est un wrapper qui délègue au vrai bridge de `tcdrm_gym/`. Cela garantit que la discrétisation de l'état Q-Learning et le chargement DQN sont identiques à l'entraînement.
- `TIMEOUT_SEC = 300` dans les runners Java : temps d'attente max pour la connexion Python.
- Les `_final.pkl` / `_final.pt` dans `models/` sont les modèles de fin d'entraînement (meilleure performance).
