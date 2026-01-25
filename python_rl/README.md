# TCDRM-ADAPTIVE v2.0: Module Python RL

**Entraînement des Modèles Q-Learning avec Gymnasium**

---

## 🎯 Rôle du Module Python

Ce module Python est utilisé **uniquement pour l'entraînement** des modèles Q-Learning avec Gymnasium (environnement simplifié, rapide).

**Python**: Entraînement des modèles (rapide)  
**Java**: Toutes les simulations et comparaisons avec CloudSim (réaliste)

---

## 📦 Installation

```bash
cd python_rl
uv sync
```

---

## 🚀 Entraînement

### Entraîner un Modèle Q-Learning

```bash
# Entraînement simple
uv run python train.py --episodes 1000 --data-gb 5.3

# Entraînement pour les 3 scénarios
./run_experiments.sh train-all
```

**Résultat**: Modèles sauvegardés dans `results/qlearning/`

### Évaluer un Modèle

```bash
uv run python evaluate_model.py \
  --model results/qlearning/r1_simple/run_*/models/best_model.pkl \
  --episodes 100
```

---

## 🔬 Simulations et Comparaisons

**⚠️ Important**: Toutes les simulations et comparaisons se font en **Java avec CloudSim**.

Pour exécuter les simulations et comparaisons:

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
