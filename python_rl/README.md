# TCDRM-ADAPTIVE: Module Python RL

**Entraînement des Modèles RL (Q-Learning Simple + DQN) et Génération des Graphiques**

---

## 🎯 Vue d'Ensemble

Ce module Python entraîne deux modèles de Reinforcement Learning pour la gestion adaptative de réplication de données dans le cloud :

1. **Q-Learning Simple** - Implémentation tabulaire propre et robuste (243 états)
2. **DQN** - Deep Q-Network pour apprentissage avec états continus

**Workflow complet** : Entraînement → Génération de graphiques comparatifs

---

## 📦 Installation

```bash
cd python_rl
uv sync
```

---

## 🚀 Utilisation Rapide

### Workflow Complet (Recommandé)

```bash
# Depuis la racine du projet
./run_workflow.sh

# Avec options
./run_workflow.sh --n-episodes 2000              # Entraînement prolongé
./run_workflow.sh --skip-qlearning               # Seulement DQN
./run_workflow.sh --skip-dqn                     # Seulement Q-Learning
```

**Résultat** :

- Modèles entraînés dans `models/`
- Graphiques générés dans `images/`

---

## 🧠 Modèles RL

### 1. Q-Learning Simple

**Implémentation propre inspirée de Sutton & Barto**

```bash
cd python_rl
uv run python train_simple_qlearning.py \
  --episodes 2000 \
  --lr 0.1 \
  --gamma 0.99 \
  --data-gb 5.3
```

**Caractéristiques** :

- 243 états discrets (3^5)
- 3 actions : NOOP, REPLICATE, DELETE
- Epsilon-greedy avec décroissance
- Reward basé sur réduction de latence

**Résultats attendus** :

- Reward : ~8600
- SLA compliance : ~83%
- Latence : ~1100ms (vs 5400ms NOREP)

### 2. DQN (Deep Q-Network)

```bash
cd python_rl
uv run python train_dqn_policy.py \
  --episodes 1000 \
  --buffer-size 50000 \
  --batch-size 128
```

**Architecture** :

- États continus (8 dimensions)
- Réseau : 64-64-32
- Experience replay + target network

---

## � Génération des Graphiques

### Graphiques 3 Courbes

**Q-Learning Simple + TCDRM Statique + NOREP**

```bash
cd python_rl
uv run python generate_3curves_graphs.py \
  --qlearning-model models/simple_qlearning.pkl \
  --output-dir ../images
```

### Graphiques 4 Courbes

**Q-Learning Simple + DQN + TCDRM Statique + NOREP**

```bash
cd python_rl
uv run python generate_4curves_graphs.py \
  --qlearning-model models/simple_qlearning.pkl \
  --dqn-model results/dqn/dqn_model.pt \
  --output-dir ../images
```

**Graphiques générés** :

- `tcdrm_combined_response_time_R1_*.png` - Latence (5.3 GB)
- `tcdrm_combined_response_time_R2_*.png` - Latence (11.9 GB)
- `tcdrm_combined_cumulative_bw_price_*.png` - Coût cumulatif
- `tcdrm_combined_total_cost_*.png` - Coût total

---

## 📁 Structure des Fichiers

```
python_rl/
├── agents/
│   ├── simple_qlearning_agent.py      # Agent Q-Learning (nouveau)
│   ├── simple_qlearning_wrapper.py    # Wrapper pour graphiques
│   └── dqn_agent.py                    # Agent DQN
├── envs/
│   └── tcdrm_qlearning_env.py          # Environnement TCDRM
├── train_simple_qlearning.py           # Entraînement Q-Learning
├── train_dqn_policy.py                 # Entraînement DQN
├── generate_3curves_graphs.py          # Graphiques 3 courbes
├── generate_4curves_graphs.py          # Graphiques 4 courbes
├── generate_graphs_unified.py          # Fonction unifiée
└── models/
    ├── simple_qlearning.pkl            # Modèle Q-Learning entraîné
    └── results/dqn/dqn_model.pt        # Modèle DQN entraîné
```

---

## 🎯 Résultats Attendus

### Q-Learning Simple

| Métrique             | Valeur  |
| -------------------- | ------- |
| Reward moyen         | ~8600   |
| SLA compliance       | ~83%    |
| Latence moyenne (R1) | ~1100ms |
| Latence NOREP (R1)   | ~5400ms |
| Amélioration         | **80%** |

### Comparaison des Algorithmes

**R1 (5.3 GB)** :

- NOREP : 5400ms constant
- Q-Learning : 1100ms (réplique rapidement)
- TCDRM Statique : 1500ms (après requête 200)
- DQN : 1000-1200ms (meilleur)

**R2 (11.9 GB)** :

- NOREP : 12000ms constant
- Q-Learning : 2500ms
- TCDRM Statique : 3000ms
- DQN : 2000-2500ms

---

## 🔬 Simulations Java (CloudSim)

**⚠️ Important**: Les simulations réalistes se font en **Java avec CloudSim**.

Pour exécuter les simulations Java:

```bash
cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive

# Compiler le projet Java
mvn clean package

# Exécuter la comparaison complète
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim
```

**Comparaisons effectuées en Java**:

1. ✅ Q-Learning Java (entraîné en Java)
2. ✅ Q-Learning Python (entraîné avec Gymnasium)
3. ✅ TCDRM Statique (seuils fixes)

**Graphes générés**: `results/cloudsim_comparison/`

---

## 📁 Structure

```
python_rl/
├── agents/
│   ├── __init__.py
│   └── tabular_qlearning.py          # Agent Q-Learning
├── envs/
│   ├── __init__.py
│   └── tcdrm_env.py                   # Environnement Gymnasium
├── utils/
│   ├── logger.py                      # Logging
│   ├── metrics.py                     # Métriques
│   └── visualization.py               # Visualisations
├── configs/
│   └── qlearning_config.yaml          # Configuration
├── train.py                           # ⭐ Entraînement
├── evaluate_model.py                  # Évaluation
├── run_experiments.sh                 # Script d'entraînement
└── pyproject.toml                     # Dépendances
```

---

## 🎓 Workflow Complet

### 1. Entraîner en Python (Rapide)

```bash
cd python_rl
./run_experiments.sh train-all
```

### 2. Simuler et Comparer en Java (Réaliste)

```bash
cd ..
mvn clean package
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim
```

### 3. Analyser les Résultats

```bash
# Voir les graphes
open results/cloudsim_comparison/*.png
```

---

## 📊 Avantages de cette Architecture

| Aspect        | Python Gymnasium | Java CloudSim                |
| ------------- | ---------------- | ---------------------------- |
| **Usage**     | Entraînement     | Simulation + Comparaison     |
| **Vitesse**   | ⚡ Rapide        | 🐢 Plus lent                 |
| **Réalisme**  | Simplifié        | ✅ Réaliste                  |
| **Métriques** | Basiques         | ✅ Détaillées (CPU, RAM, BW) |
| **CloudSim**  | Non              | ✅ Oui                       |

**Meilleur des deux mondes**:

- ✅ Entraînement rapide en Python
- ✅ Validation réaliste en Java

---

## 🔗 Documentation Java

Pour plus de détails sur les simulations CloudSim, voir:

- `src/main/java/org/tcdrm/adaptive/examples/TcdrmComparisonCloudSim.java`
- `src/main/java/org/tcdrm/adaptive/rl/StaticTcdrmPolicy.java`
- `src/main/java/org/tcdrm/adaptive/rl/PythonQLearningPolicy.java`

---

**Python pour l'entraînement, Java pour la validation! 🎯**
