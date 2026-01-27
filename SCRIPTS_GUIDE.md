# Guide des Scripts TCDRM-ADAPTIVE

## Vue d'ensemble

Ce projet contient plusieurs scripts pour faciliter l'entraînement, l'évaluation et la génération de graphes pour TCDRM-ADAPTIVE.

---

## Scripts Principaux

### 1. `run_tcdrm_adaptive.sh` ⭐ (NOUVEAU - Recommandé)

Script simplifié pour l'entraînement et l'évaluation rapide de TCDRM-ADAPTIVE.

#### Entraînement

```bash
# Entraînement complet (200 épisodes)
./run_tcdrm_adaptive.sh --train

# Entraînement rapide (50 épisodes)
./run_tcdrm_adaptive.sh --train --episodes 50 --queries 500
```

#### Évaluation

```bash
# Évaluer le dernier modèle entraîné
./run_tcdrm_adaptive.sh --evaluate
```

**Sorties** :
- Modèle : `python_rl/results/tcdrm_adaptive/full_run_YYYYMMDD_HHMMSS/`
- Métriques d'entraînement
- Graphiques de progression

---

### 2. `run_workflow_new.sh` (Workflow Complet)

Script complet pour tout le pipeline : entraînement + génération d'actions + graphes 3 courbes.

#### Utilisation

```bash
# Workflow complet
./run_workflow_new.sh

# Workflow rapide (entraînement court)
./run_workflow_new.sh --n-episodes 50 --n-queries 500

# Utiliser modèle existant
./run_workflow_new.sh --skip-training

# Sauter compilation Java
./run_workflow_new.sh --skip-compile
```

#### Options

| Option | Description | Défaut |
|--------|-------------|--------|
| `--skip-training` | Utiliser modèle existant | false |
| `--skip-actions` | Sauter génération actions | false |
| `--skip-compile` | Sauter compilation Java | false |
| `--n-queries N` | Requêtes par épisode | 1000 |
| `--n-episodes N` | Nombre d'épisodes | 200 |

**Sorties** :
- Modèle TCDRM-ADAPTIVE
- Actions optimales pour R1 et R2
- Graphes 2 courbes (TCDRM Statique vs NOREP)
- Graphes 3 courbes (+ Python RL adaptatif)

---

### 3. `run_workflow.sh` (Ancien - Déprécié)

⚠️ **Déprécié** : Utilise l'ancienne approche avec seuils fixes. Préférez `run_workflow_new.sh`.

---

## Scripts Python

### Entraînement

#### `train_adaptive_policy.py` ⭐ (NOUVEAU)

Entraîne TCDRM-ADAPTIVE avec fonction de récompense multi-objectif.

```bash
cd python_rl
uv run python train_adaptive_policy.py \
    --episodes 200 \
    --queries 1000 \
    --lr 0.1 \
    --gamma 0.95
```

**Caractéristiques** :
- Fonction de récompense multi-objectif (5 composantes)
- Apprentissage adaptatif des seuils
- Tracking de popularité
- Métriques avancées

#### `train_general_policy.py` (Ancien)

⚠️ **Déprécié** : Utilise l'ancienne approche. Préférez `train_adaptive_policy.py`.

---

### Génération d'Actions

#### `generate_optimal_actions.py`

Génère les actions optimales pour R1 et R2 en utilisant la stratégie TCDRM Statique.

```bash
cd python_rl
uv run python generate_optimal_actions.py \
    --model results/tcdrm_adaptive/full_run_XXX/adaptive_model.pkl \
    --scenario R1 \
    --n-queries 1000
```

**Note** : Ce script utilise actuellement une stratégie fixe (seuil à 200) pour garantir la cohérence avec TCDRM Statique. Pour utiliser le modèle RL, décommentez la logique dans le script.

---

## Workflows Recommandés

### Workflow 1 : Entraînement Rapide

Pour tester rapidement :

```bash
# 1. Entraîner (5-10 minutes)
./run_tcdrm_adaptive.sh --train --episodes 50 --queries 500

# 2. Évaluer
./run_tcdrm_adaptive.sh --evaluate
```

### Workflow 2 : Entraînement Complet

Pour résultats de recherche :

```bash
# 1. Entraîner (30-60 minutes)
./run_tcdrm_adaptive.sh --train --episodes 200 --queries 1000

# 2. Générer graphes complets
./run_workflow_new.sh --skip-training
```

### Workflow 3 : Pipeline Complet

Tout en une seule commande :

```bash
# Tout le pipeline (1-2 heures)
./run_workflow_new.sh
```

---

## Structure des Résultats

```
python_rl/results/
├── tcdrm_adaptive/                    # Modèles TCDRM-ADAPTIVE
│   ├── full_run_20260127_084048/
│   │   ├── adaptive_model.pkl         # Modèle Q-Learning
│   │   ├── training_metrics.pkl       # Métriques
│   │   └── training_metrics.png       # Graphiques
│   └── test_run/                      # Tests rapides
│
├── optimal_actions/                   # Actions pour graphes
│   ├── optimal_actions_R1.pkl
│   └── optimal_actions_R2.pkl
│
└── qlearning/                         # Anciens modèles (déprécié)
    └── general_policy/

images/
├── tcdrm_combined_*.png               # Graphes 2 courbes
└── *_3curves.png                      # Graphes 3 courbes
```

---

## Comparaison des Approches

| Aspect | Ancienne (train_general_policy) | Nouvelle (train_adaptive_policy) |
|--------|--------------------------------|----------------------------------|
| **Récompense** | Simple (latence + coût) | Multi-objectif (5 composantes) |
| **Seuils** | Fixes (200 requêtes) | Adaptatifs (appris) |
| **Popularité** | Non utilisée | Utilisée pour décisions |
| **Stabilité** | Non prise en compte | Pénalise changements fréquents |
| **Timing** | Fixe | Stratégique |

---

## Dépannage

### Erreur : "Aucun modèle trouvé"

```bash
# Solution : Entraîner d'abord
./run_tcdrm_adaptive.sh --train
```

### Erreur : "Port 25333 déjà utilisé"

```bash
# Solution : Nettoyer les processus
pkill -f "TcdrmArticle"
lsof -ti:25333 | xargs kill -9
```

### Erreur : "uv: command not found"

```bash
# Solution : Installer uv
curl -LsSf https://astral.sh/uv/install.sh | sh
```

---

## Scripts Utilitaires

### Nettoyer les résultats

```bash
# Supprimer tous les modèles
rm -rf python_rl/results/tcdrm_adaptive/full_run_*

# Supprimer tous les graphes
rm -f images/*.png
```

### Voir les métriques d'un modèle

```bash
cd python_rl
uv run python -c "
import pickle
with open('results/tcdrm_adaptive/full_run_XXX/training_metrics.pkl', 'rb') as f:
    metrics = pickle.load(f)
print('Meilleur épisode:', metrics['best_episode'])
print('Meilleur reward:', metrics['best_reward'])
"
```

---

## Prochaines Étapes

### Phase 2 : Deep Q-Network (DQN)

Pour espaces d'état plus larges :

```bash
cd python_rl
uv run python train_dqn_policy.py --episodes 500
```

### Phase 3 : PPO (Stable-Baselines3)

Pour performance state-of-the-art :

```bash
cd python_rl
uv run python train_ppo_policy.py --timesteps 100000
```

### Phase 4 : Intégration CloudSim

Remplacer simulation Python par CloudSim via Py4J.

---

## Références

- **Spécification** : `TCDRM_ADAPTIVE_SPEC.md`
- **Guide d'utilisation** : `README_TCDRM_ADAPTIVE.md`
- **Code source** : `python_rl/`
