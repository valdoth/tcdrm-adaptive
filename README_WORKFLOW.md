# Guide d'Utilisation - Workflow TCDRM-Adaptive

## 🚀 Démarrage Rapide

### Option 1: Workflow Complet (Recommandé pour la première fois)

Entraînement complet avec 10,000 requêtes:

```bash
./run_workflow_new.sh --n-queries 10000 --n-episodes 100
```

Cela prendra environ 30-60 minutes et générera:

- Un modèle RL général entraîné
- Les actions optimales pour R1 et R2
- Tous les graphes (2 et 3 courbes)

### Option 2: Test Rapide

Entraînement rapide avec 1000 requêtes pour tester:

```bash
./run_workflow_new.sh --n-queries 1000 --n-episodes 20
```

Cela prendra environ 5-10 minutes.

### Option 3: Utiliser un Modèle Existant

Si vous avez déjà entraîné un modèle:

```bash
./run_workflow_new.sh --skip-training --skip-actions --skip-compile
```

## 📋 Étapes du Workflow

### 1. Entraînement Général

**Objectif:** Apprendre une politique RL générale sur des requêtes variées

**Commande manuelle:**

```bash
cd python_rl
uv run python train_general_policy.py \
    --n-queries 10000 \
    --n-episodes 100 \
    --lr 0.1 \
    --gamma 0.95
```

**Sortie:**

```
results/qlearning/general_policy/run_YYYYMMDD_HHMMSS/
├── models/
│   └── best_model.pkl          # Modèle entraîné
├── logs/
│   └── training_metrics.pkl    # Métriques d'entraînement
└── training_queries.pkl        # Requêtes utilisées
```

### 2. Génération des Actions Optimales

**Objectif:** Générer les actions optimales pour R1 et R2

**Commandes manuelles:**

```bash
cd python_rl

# Pour R1
uv run python generate_optimal_actions.py \
    --model results/qlearning/general_policy/run_YYYYMMDD_HHMMSS/models/best_model.pkl \
    --scenario R1 \
    --n-queries 1000

# Pour R2
uv run python generate_optimal_actions.py \
    --model results/qlearning/general_policy/run_YYYYMMDD_HHMMSS/models/best_model.pkl \
    --scenario R2 \
    --n-queries 1000
```

**Sortie:**

```
results/optimal_actions/
├── optimal_actions_R1.pkl
└── optimal_actions_R2.pkl
```

### 3. Compilation Java

**Commande:**

```bash
mvn clean package -DskipTests
```

### 4. Génération des Graphes

**Graphes 2 courbes (TCDRM vs NOREP):**

```bash
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs
```

**Graphes 3 courbes (RL + TCDRM + NOREP):**

Terminal 1 - Java Gateway:

```bash
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs3CurvesWithPy4J
```

Terminal 2 - Client Python:

```bash
cd python_rl
uv run python connect_to_java_with_optimal_actions.py \
    --actions results/optimal_actions/optimal_actions_R1.pkl
```

## 📊 Résultats

### Structure des Résultats

```
tcdrm-adaptive/
├── python_rl/
│   └── results/
│       ├── qlearning/
│       │   └── general_policy/
│       │       └── run_YYYYMMDD_HHMMSS/
│       │           ├── models/best_model.pkl
│       │           └── logs/training_metrics.pkl
│       └── optimal_actions/
│           ├── optimal_actions_R1.pkl
│           └── optimal_actions_R2.pkl
└── images/
    ├── tcdrm_combined_response_time_R1.png
    ├── tcdrm_combined_response_time_R1_3curves.png
    ├── tcdrm_combined_cpu_consumption_R1.png
    ├── tcdrm_combined_cpu_consumption_R1_3curves.png
    ├── tcdrm_combined_bw_price_per_query_R1.png
    ├── tcdrm_combined_bw_price_per_query_R1_3curves.png
    ├── tcdrm_combined_cumulative_bw_price_R1.png
    ├── tcdrm_combined_cumulative_bw_price_R1_3curves.png
    ├── tcdrm_combined_total_cost_R1.png
    ├── tcdrm_combined_total_cost_R1_3curves.png
    └── (idem pour R2)
```

### Visualiser les Graphes

```bash
# Tous les graphes
open images/*.png

# Seulement les graphes 3 courbes
open images/*_3curves.png

# Seulement R1
open images/*_R1*.png
```

## 🔧 Options Avancées

### Personnaliser l'Entraînement

```bash
./run_workflow_new.sh \
    --n-queries 5000 \      # Nombre de requêtes d'entraînement
    --n-episodes 50         # Nombre d'épisodes
```

### Sauter des Étapes

```bash
# Sauter l'entraînement (utiliser modèle existant)
./run_workflow_new.sh --skip-training

# Sauter la génération des actions (utiliser actions existantes)
./run_workflow_new.sh --skip-training --skip-actions

# Sauter la compilation Java
./run_workflow_new.sh --skip-compile

# Tout sauter (juste générer les graphes)
./run_workflow_new.sh --skip-training --skip-actions --skip-compile
```

## 🐛 Dépannage

### Problème: Port déjà utilisé

**Erreur:**

```
Address already in use: bind
```

**Solution:**

```bash
# Libérer les ports Py4J
lsof -ti:25333 | xargs kill -9
lsof -ti:25334 | xargs kill -9
```

### Problème: Modèle introuvable

**Erreur:**

```
❌ ERREUR: Aucun modèle existant trouvé
```

**Solution:**
Entraîner d'abord un modèle:

```bash
./run_workflow_new.sh --n-queries 1000 --n-episodes 20
```

### Problème: Actions optimales introuvables

**Erreur:**

```
❌ ERREUR: Actions optimales R1 introuvables
```

**Solution:**
Générer les actions optimales:

```bash
cd python_rl
uv run python generate_optimal_actions.py \
    --model results/qlearning/general_policy/run_LATEST/models/best_model.pkl \
    --scenario R1
```

### Problème: Connexion Py4J échoue

**Symptômes:**

- Client Python ne se connecte pas
- Timeout après 120 secondes

**Solution:**

1. Vérifier que Java est bien démarré
2. Vérifier les logs: `tail -f /tmp/java_graphs_3curves.log`
3. Vérifier les ports: `lsof -i:25333`

## 📝 Logs

### Logs Java

```bash
tail -f /tmp/java_graphs_3curves.log
```

### Logs Python

```bash
tail -f /tmp/python_graphs_client.log
```

### Log du Workflow

```bash
tail -f /tmp/workflow_new.log
```

## 🎯 Cas d'Usage

### 1. Première Utilisation

```bash
# Entraînement complet
./run_workflow_new.sh --n-queries 10000 --n-episodes 100
```

### 2. Développement/Test

```bash
# Test rapide
./run_workflow_new.sh --n-queries 1000 --n-episodes 20
```

### 3. Régénérer les Graphes

```bash
# Utiliser le modèle et les actions existantes
./run_workflow_new.sh --skip-training --skip-actions --skip-compile
```

### 4. Nouveau Scénario (R3)

```bash
# 1. Utiliser le modèle existant
# 2. Générer les actions pour R3
cd python_rl
uv run python generate_optimal_actions.py \
    --model results/qlearning/general_policy/run_LATEST/models/best_model.pkl \
    --scenario R3 \
    --n-queries 1000

# 3. Modifier le code Java pour inclure R3
# 4. Régénérer les graphes
```

## 📈 Métriques de Performance

### Temps d'Exécution Estimés

| Étape              | 1000 requêtes | 10000 requêtes |
| ------------------ | ------------- | -------------- |
| Entraînement       | 2-5 min       | 20-40 min      |
| Génération actions | 10-30 sec     | 10-30 sec      |
| Compilation Java   | 30-60 sec     | 30-60 sec      |
| Graphes 2 courbes  | 1-2 min       | 1-2 min        |
| Graphes 3 courbes  | 2-3 min       | 2-3 min        |
| **TOTAL**          | **5-10 min**  | **30-60 min**  |

### Espace Disque

| Composant         | Taille                |
| ----------------- | --------------------- |
| Modèle entraîné   | ~50 KB                |
| Actions optimales | ~2 KB par scénario    |
| Graphes PNG       | ~50-100 KB par graphe |
| Logs              | ~10-50 KB             |

## 🔄 Workflow Recommandé

### Pour la Production

1. **Entraînement initial:**

   ```bash
   ./run_workflow_new.sh --n-queries 10000 --n-episodes 100
   ```

2. **Sauvegarder le modèle:**

   ```bash
   cp python_rl/results/qlearning/general_policy/run_LATEST/models/best_model.pkl \
      python_rl/models/production_model.pkl
   ```

3. **Utiliser le modèle en production:**
   ```bash
   ./run_workflow_new.sh --skip-training --skip-actions --skip-compile
   ```

### Pour le Développement

1. **Test rapide:**

   ```bash
   ./run_workflow_new.sh --n-queries 1000 --n-episodes 20
   ```

2. **Itérer sur les hyperparamètres:**

   ```bash
   cd python_rl
   uv run python train_general_policy.py \
       --n-queries 1000 \
       --n-episodes 20 \
       --lr 0.2 \
       --gamma 0.99
   ```

3. **Comparer les résultats:**
   ```bash
   # Visualiser les métriques d'entraînement
   python -c "import pickle; print(pickle.load(open('results/qlearning/general_policy/run_LATEST/logs/training_metrics.pkl', 'rb')))"
   ```

## 📚 Documentation Complémentaire

- `NOUVELLE_APPROCHE_RL.md`: Explication détaillée de l'architecture
- `python_rl/README.md`: Documentation du code Python
- `src/main/java/org/tcdrm/adaptive/README.md`: Documentation du code Java
