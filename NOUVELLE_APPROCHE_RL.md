# Nouvelle Approche RL - TCDRM Adaptive

## 🎯 Objectif

Implémenter une approche d'apprentissage par renforcement (RL) qui:

1. Apprend une **politique générale** sur des requêtes variées
2. Applique cette politique aux scénarios spécifiques (R1, R2)
3. Répète les actions optimales comme pour NOREP et TCDRM Statique

## 📋 Architecture

### Ancienne Approche (Problématique)

```
Entraînement direct sur R1/R2
    ↓
Génération des graphes en temps réel
    ↓
Pas de répétition des requêtes
```

**Problèmes:**

- Le modèle n'apprend pas une politique générale
- Chaque requête est différente (pas de répétition)
- Impossible de comparer équitablement avec NOREP/TCDRM

### Nouvelle Approche (Correcte)

```
1. Entraînement général (10,000 requêtes variées)
    ↓
2. Génération des actions optimales pour R1 et R2
    ↓
3. Répétition des actions optimales (comme NOREP/TCDRM)
    ↓
4. Génération des graphes comparatifs
```

**Avantages:**

- ✅ Politique générale apprise sur des données variées
- ✅ Actions optimales pré-calculées pour chaque scénario
- ✅ Répétition des requêtes pour comparaison équitable
- ✅ Séparation entraînement/inférence

## 🔧 Composants Implémentés

### 1. Entraînement Général (`train_general_policy.py`)

**Fonction:** Entraîne un agent Q-Learning sur 10,000 requêtes avec des tailles variées (0.5-5.0 GB)

**Paramètres:**

- `--n-queries`: Nombre de requêtes d'entraînement (défaut: 10000)
- `--n-episodes`: Nombre d'épisodes (défaut: 100)
- `--lr`: Learning rate (défaut: 0.1)
- `--gamma`: Discount factor (défaut: 0.95)

**Sortie:**

- Modèle entraîné: `results/qlearning/general_policy/run_YYYYMMDD_HHMMSS/models/best_model.pkl`
- Métriques d'entraînement: `logs/training_metrics.pkl`
- Requêtes d'entraînement: `training_queries.pkl`

**Exemple:**

```bash
cd python_rl
uv run python train_general_policy.py \
    --n-queries 10000 \
    --n-episodes 100
```

### 2. Génération des Actions Optimales (`generate_optimal_actions.py`)

**Fonction:** Utilise le modèle entraîné pour générer les actions optimales pour un scénario donné

**Paramètres:**

- `--model`: Chemin vers le modèle entraîné (.pkl)
- `--scenario`: Scénario (R1 ou R2)
- `--n-queries`: Nombre de requêtes à générer (défaut: 1000)
- `--seed`: Seed pour reproductibilité (défaut: 42)

**Sortie:**

- Actions optimales: `results/optimal_actions/optimal_actions_{scenario}.pkl`

**Exemple:**

```bash
cd python_rl
uv run python generate_optimal_actions.py \
    --model results/qlearning/general_policy/run_20260126_165356/models/best_model.pkl \
    --scenario R1 \
    --n-queries 1000
```

### 3. Client Py4J avec Actions Optimales (`connect_to_java_with_optimal_actions.py`)

**Fonction:** Se connecte au Gateway Java et fournit les actions optimales pré-générées

**Paramètres:**

- `--actions`: Chemin vers les actions optimales (.pkl)
- `--host`: Hôte du Gateway Java (défaut: localhost)
- `--port`: Port du Gateway Java (défaut: 25333)

**Exemple:**

```bash
cd python_rl
uv run python connect_to_java_with_optimal_actions.py \
    --actions results/optimal_actions/optimal_actions_R1.pkl
```

### 4. Workflow Complet (`run_workflow_new.sh`)

**Fonction:** Exécute le workflow complet de bout en bout

**Options:**

- `--skip-training`: Sauter l'entraînement (utiliser modèle existant)
- `--skip-actions`: Sauter la génération des actions
- `--skip-compile`: Sauter la compilation Java
- `--n-queries N`: Nombre de requêtes d'entraînement
- `--n-episodes N`: Nombre d'épisodes d'entraînement

**Exemple:**

```bash
# Workflow complet avec entraînement rapide
./run_workflow_new.sh --n-queries 1000 --n-episodes 20

# Workflow complet avec entraînement complet
./run_workflow_new.sh --n-queries 10000 --n-episodes 100

# Utiliser un modèle existant
./run_workflow_new.sh --skip-training --skip-actions --skip-compile
```

## 📊 Résultats

### Distribution des Actions (Exemple avec 1000 requêtes, 20 épisodes)

**R1 (5.3 GB):**

- CREATE: 391 (39.1%)
- DELETE: 50 (5.0%)
- DO_NOTHING: 559 (55.9%)

**R2 (11.9 GB):**

- CREATE: ~40%
- DELETE: ~5%
- DO_NOTHING: ~55%

### Graphes Générés

**2 courbes (TCDRM Statique vs NOREP):**

- `images/tcdrm_combined_response_time_R1.png`
- `images/tcdrm_combined_cpu_consumption_R1.png`
- `images/tcdrm_combined_bw_price_per_query_R1.png`
- `images/tcdrm_combined_cumulative_bw_price_R1.png`
- `images/tcdrm_combined_total_cost_R1.png`
- (Idem pour R2)

**3 courbes (Python RL + TCDRM Statique + NOREP):**

- `images/tcdrm_combined_response_time_R1_3curves.png`
- `images/tcdrm_combined_cpu_consumption_R1_3curves.png`
- `images/tcdrm_combined_bw_price_per_query_R1_3curves.png`
- `images/tcdrm_combined_cumulative_bw_price_R1_3curves.png`
- `images/tcdrm_combined_total_cost_R1_3curves.png`
- (Idem pour R2)

## 🔄 Workflow Complet

```
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 1: Entraînement Général                               │
│ - 10,000 requêtes variées (0.5-5.0 GB)                     │
│ - 100 épisodes d'entraînement                               │
│ - Politique générale apprise                                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 2: Génération des Actions Optimales                   │
│ - R1: 1000 actions optimales                                │
│ - R2: 1000 actions optimales                                │
│ - Sauvegarde dans results/optimal_actions/                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 3: Compilation Java                                   │
│ - mvn clean package -DskipTests                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 4: Génération des Graphes 2 Courbes                   │
│ - TCDRM Statique vs NOREP                                   │
│ - 5 graphes par scénario (R1, R2)                          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ ÉTAPE 5: Génération des Graphes 3 Courbes                   │
│ - Python RL + TCDRM Statique + NOREP                        │
│ - Utilisation des actions optimales via Py4J                │
│ - 5 graphes par scénario (R1, R2)                          │
└─────────────────────────────────────────────────────────────┘
```

## 🎓 Concepts Clés

### Q-Learning Tabulaire

- **États:** 108 états discrétisés (latency × cost × replicas × queries)
- **Actions:** 3 actions (CREATE, DELETE, DO_NOTHING)
- **Récompense:** Basée sur latence, coût et nombre de réplicas

### Discrétisation de l'État

L'environnement utilise un espace d'observation continu (Box), mais l'agent Q-Learning discrétise cet espace en 108 états:

- 3 niveaux de latence (faible, moyen, élevé)
- 3 niveaux de coût (faible, moyen, élevé)
- 3 niveaux de réplicas (1, 2, 3+)
- 4 niveaux de requêtes (début, milieu, fin, très fin)

### Exploration vs Exploitation

- **Epsilon-greedy:** Balance entre exploration (actions aléatoires) et exploitation (actions optimales)
- **Décroissance d'epsilon:** De 1.0 à 0.01 pendant l'entraînement
- **Inférence:** Epsilon = 0 (exploitation pure)

## 📈 Métriques de Performance

### Entraînement

- **Récompense par épisode:** Mesure la qualité de la politique
- **Longueur d'épisode:** Nombre de steps avant terminaison
- **Coût par épisode:** Coût total accumulé

### Inférence

- **Latence par requête:** Temps de réponse
- **Coût par requête:** Coût CPU + Bande passante
- **Nombre de réplicas:** Évolution du nombre de réplicas
- **Coût cumulatif:** Coût total accumulé

## 🚀 Prochaines Étapes

1. **Entraînement complet:** Lancer avec 10,000 requêtes et 100 épisodes
2. **Analyse des résultats:** Comparer les 3 politiques (RL, TCDRM, NOREP)
3. **Optimisation:** Ajuster les hyperparamètres si nécessaire
4. **Documentation:** Ajouter les résultats dans l'article

## 📝 Notes Importantes

- Les actions optimales sont **déterministes** une fois générées
- La répétition des requêtes permet une **comparaison équitable**
- Le modèle apprend une **politique générale** applicable à différents scénarios
- L'approche est **scalable** et peut être étendue à d'autres scénarios (R3, R4, etc.)
