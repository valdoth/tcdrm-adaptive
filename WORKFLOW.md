# 🚀 TCDRM-ADAPTIVE: Workflow Unifié

Ce document décrit le workflow complet pour entraîner le modèle Python RL et générer les graphes de l'article.

## 📋 Vue d'Ensemble

Le workflow unifié combine:

1. **Entraînement Python RL** (Q-Learning tabulaire)
2. **Génération de graphes 2 courbes** (TCDRM Statique vs NOREP)
3. **Génération de graphes 3 courbes** (Python RL + TCDRM Statique + NOREP) avec le **VRAI modèle entraîné** via Py4J

## 🎯 Script Principal: `run_workflow.sh`

### Usage Basique

```bash
# Workflow complet (entraînement + graphes) pour R1
./run_workflow.sh

# Workflow complet pour R2
./run_workflow.sh --scenario r2

# Workflow complet pour R3
./run_workflow.sh --scenario r3
```

### Options Avancées

```bash
# Sauter l'entraînement (utiliser modèle existant)
./run_workflow.sh --skip-training

# Sauter la compilation Java
./run_workflow.sh --skip-compile

# Combiner les options
./run_workflow.sh --scenario r2 --skip-training --skip-compile
```

### Aide

```bash
./run_workflow.sh --help
```

## 📊 Étapes du Workflow

### **ÉTAPE 1: Entraînement Python RL** (optionnel avec `--skip-training`)

- Algorithme: **Q-Learning Tabulaire**
- États: **108 états discrets** (budget × latence × popularité × réplicas)
- Actions: **3 actions** (CREATE_REPLICA, DELETE_REPLICA, DO_NOTHING)
- Épisodes: **1000**
- Sortie: `python_rl/results/qlearning/{scenario}/run_{timestamp}/models/best_model.pkl`

### **ÉTAPE 2: Compilation Java** (optionnel avec `--skip-compile`)

- Compile le projet Maven
- Génère: `target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar`

### **ÉTAPE 3: Vérification du Modèle**

- Vérifie que le modèle entraîné existe
- Trouve automatiquement le modèle le plus récent

### **ÉTAPE 4: Génération Graphes 2 Courbes**

Génère les graphes classiques (TCDRM Statique vs NOREP):

- `TcdrmArticleGraphs.java`
- `TcdrmArticleGraphsDual.java`
- `TcdrmArticleAllGraphs.java`

**Sortie:** `images/tcdrm_*.png`

### **ÉTAPE 5: Génération Graphes 3 Courbes avec VRAI Modèle RL**

Architecture **Py4J**:

1. Java démarre un **Gateway Server** (port 25333)
2. Python se connecte avec le **modèle entraîné** (port 25334 callback)
3. Java exécute le modèle Python RL pour générer les données
4. Génère les graphes avec 3 courbes

**Sortie:** `images/*_3curves.png`

## 📈 Graphes Générés

### Graphes avec 2 Courbes (TCDRM Statique vs NOREP)

- `tcdrm_combined_response_time_R1.png`
- `tcdrm_combined_bw_price_per_query_R1.png`
- `tcdrm_combined_cpu_consumption_R1.png`
- `tcdrm_combined_cumulative_bw_price_R1.png`
- `tcdrm_combined_total_cost_R1.png`
- (Et les mêmes pour R2)

### Graphes avec 3 Courbes (VRAI Python RL + TCDRM Statique + NOREP)

- `tcdrm_combined_response_time_R1_3curves.png`
- `tcdrm_combined_bw_price_per_query_R1_3curves.png`
- `tcdrm_combined_cpu_consumption_R1_3curves.png`
- `tcdrm_combined_cumulative_bw_price_R1_3curves.png`
- `tcdrm_combined_total_cost_R1_3curves.png`
- (Et les mêmes pour R2)

### Légende des Couleurs

- 🟡 **TCDRM (Python RL)** = Jaune `RGB(255, 193, 7)` - **VRAI modèle Q-Learning entraîné**
- 🟠 **NOREP** = Orange `RGB(255, 127, 14)` - Sans réplication
- 🔴 **TCDRM Statique** = Rouge `RGB(244, 67, 54)` - Seuils fixes de l'article

## 🔧 Architecture Technique

```
┌─────────────────────────────────────────────────────────────┐
│  WORKFLOW UNIFIÉ TCDRM-ADAPTIVE                             │
└─────────────────────────────────────────────────────────────┘

1. Entraînement Python RL (optionnel)
   ├─ Q-Learning Tabulaire
   ├─ 108 états × 3 actions
   └─ Sortie: best_model.pkl

2. Compilation Java (optionnel)
   └─ Maven package

3. Génération Graphes 2 Courbes
   ├─ TcdrmArticleGraphs
   ├─ TcdrmArticleGraphsDual
   └─ TcdrmArticleAllGraphs

4. Génération Graphes 3 Courbes (Py4J)
   ├─ Java Gateway Server (port 25333)
   │  └─ TcdrmArticleAllGraphs3CurvesWithPy4J
   │
   ├─ Python Client (port 25334 callback)
   │  ├─ Charge best_model.pkl
   │  ├─ Enregistre Q-table dans Java
   │  └─ Exécute épisodes complets
   │
   └─ Génération graphes avec VRAI modèle RL
```

## 📦 Prérequis

### Python

```bash
# Installer uv (gestionnaire de paquets Python)
curl -LsSf https://astral.sh/uv/install.sh | sh
```

### Java

```bash
# Installer Maven
brew install maven

# Vérifier Java (version 11+)
java -version
```

## 🐛 Dépannage

### Erreur: "Aucun modèle entraîné trouvé"

Entraînez d'abord le modèle:

```bash
./run_workflow.sh --scenario r1
```

### Erreur: "Python client ne se connecte pas"

Vérifiez les logs:

```bash
tail -f /tmp/java_graphs_3curves.log
tail -f /tmp/python_graphs_client.log
```

### Erreur de compilation Java

Nettoyez et recompilez:

```bash
mvn clean package
```

## 📝 Logs

Les logs sont sauvegardés dans `/tmp/`:

- **Java:** `/tmp/java_graphs_3curves.log`
- **Python:** `/tmp/python_graphs_client.log`

## 🎓 Algorithme Q-Learning

### Équation de Bellman

```
Q(s,a) ← Q(s,a) + α[r + γ max_a' Q(s',a') - Q(s,a)]
```

### Hyperparamètres

- **α (learning rate):** 0.1
- **γ (discount factor):** 0.95
- **ε (epsilon):** 1.0 → 0.01 (décroissance: 0.995)
- **Épisodes:** 1000
- **Max steps/épisode:** 1000

### Fonction de Récompense Multi-Objectif

Optimise 8 critères:

1. **SLA Compliance** (+10 si latence < 150ms)
2. **Cost Efficiency** (économies bande passante)
3. **Budget Management** (pénalité si budget critique)
4. **Resource Efficiency** (pénalité sur-réplication)
5. **Smart Actions** (bonus décisions intelligentes)
6. **Failed Actions** (pénalité actions échouées)
7. **SLA Compliance Rate** (bonus si >95%)
8. **Cost-Latency Balance** (bonus équilibre optimal)

## 📚 Références

- **Environnement:** `python_rl/envs/tcdrm_env.py`
- **Agent:** `python_rl/agents/tabular_qlearning.py`
- **Entraînement:** `python_rl/train.py`
- **Client Py4J:** `python_rl/connect_to_java_for_graphs.py`
- **Génération 3 courbes:** `src/main/java/org/tcdrm/adaptive/examples/TcdrmArticleAllGraphs3CurvesWithPy4J.java`

## 🎉 Résultat Final

Après exécution complète du workflow, vous aurez:

- ✅ Modèle Python RL entraîné
- ✅ Graphes 2 courbes (TCDRM Statique vs NOREP)
- ✅ Graphes 3 courbes (avec VRAI modèle Python RL entraîné)
- ✅ Tous les graphes dans `images/`

**Commande pour voir tous les graphes:**

```bash
open images/*.png
```
