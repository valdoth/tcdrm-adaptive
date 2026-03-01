# TensorBoard pour TCDRM - Monitoring des Entraînements RL

Ce document explique comment utiliser TensorBoard pour monitorer les entraînements Q-Learning et DQN en temps réel.

## 🎯 Fonctionnalités

TensorBoard permet de visualiser :
- **Métriques par step** : Reward, latence, coût, budget, réplicas, actions
- **Métriques par épisode** : Reward total, latence moyenne, coût total, violations SLA
- **Métriques d'entraînement** : Epsilon (Q-Learning), Loss (DQN), Q-values
- **Hyperparamètres** : Comparaison de différentes configurations

## 🚀 Utilisation Rapide

### 1. Entraîner avec TensorBoard activé

**Q-Learning** :
```bash
cd python_rl
uv run python train_simple_qlearning.py --tensorboard --episodes 2000
```

**DQN** (à venir) :
```bash
cd python_rl
uv run python train_dqn_policy.py --tensorboard --episodes 2000
```

### 2. Lancer TensorBoard

Dans un **nouveau terminal** :

```bash
cd python_rl
./run_tensorboard.sh
```

Ou manuellement :
```bash
tensorboard --logdir=runs --port=6006
```

### 3. Ouvrir le Dashboard

Ouvrir dans le navigateur : **http://localhost:6006**

## 📊 Visualisations Disponibles

### Onglet "Scalars"

#### Step Metrics (par requête)
- `Step/Reward` : Récompense à chaque step
- `Step/Latency` : Latence de la requête (ms)
- `Step/Cost` : Coût du step ($)
- `Step/Budget` : Budget restant ($)
- `Step/Replicas` : Nombre de réplicas actifs
- `Step/Action` : Action prise (0=NOOP, 1=REPLICATE, 2=DELETE)
- `Step/SLA_Violations` : Violations SLA cumulées

#### Episode Metrics (par épisode)
- `Episode/Total_Reward` : Récompense totale de l'épisode
- `Episode/Avg_Latency` : Latence moyenne
- `Episode/Total_Cost` : Coût total
- `Episode/Final_Budget` : Budget final
- `Episode/Avg_Replicas` : Nombre moyen de réplicas
- `Episode/SLA_Violations` : Total de violations SLA

#### Training Metrics
- `Training/Epsilon` : Valeur d'epsilon (exploration vs exploitation)
- `Training/States_Explored` : Nombre d'états explorés
- `Training/Loss` : Perte du réseau (DQN uniquement)
- `Training/Q_Value_Mean` : Q-value moyenne (DQN uniquement)

### Onglet "HParams"

Compare les hyperparamètres et leurs résultats :
- Learning rate (`lr`)
- Discount factor (`gamma`)
- Epsilon start/min/decay
- Nombre d'épisodes
- Taille des données (`data_gb`)

## 🔧 Options Avancées

### Changer le répertoire de logs

```bash
uv run python train_simple_qlearning.py \
  --tensorboard \
  --tensorboard-dir my_experiments \
  --episodes 2000
```

### Lancer TensorBoard sur un port différent

```bash
./run_tensorboard.sh runs 8080
```

Ou :
```bash
tensorboard --logdir=runs --port=8080
```

### Comparer plusieurs expériences

Tous les entraînements sont sauvegardés dans `runs/` avec un timestamp :
```
runs/
  ├── qlearning_20260221_230000/
  ├── qlearning_20260221_231500/
  └── dqn_20260221_232000/
```

TensorBoard affichera automatiquement toutes les expériences pour comparaison.

## 📈 Exemple de Workflow

### Expérience 1 : Baseline
```bash
# Terminal 1 : Entraînement
uv run python train_simple_qlearning.py \
  --tensorboard \
  --episodes 2000 \
  --lr 0.1 \
  --gamma 0.99

# Terminal 2 : TensorBoard
./run_tensorboard.sh
```

### Expérience 2 : Learning rate plus élevé
```bash
# Terminal 1 : Nouvel entraînement
uv run python train_simple_qlearning.py \
  --tensorboard \
  --episodes 2000 \
  --lr 0.2 \
  --gamma 0.99
```

TensorBoard affichera les deux courbes pour comparaison !

## 🎨 Astuces TensorBoard

### Lisser les courbes
- Utiliser le slider "Smoothing" en haut à gauche (recommandé : 0.6-0.8)

### Comparer des runs spécifiques
- Décocher les runs non désirés dans la colonne de gauche

### Télécharger les données
- Cliquer sur les 3 points verticaux → "Download CSV"

### Mode sombre
- Cliquer sur l'icône paramètres (⚙️) → "Dark mode"

## 🐛 Dépannage

### TensorBoard ne démarre pas
```bash
# Vérifier que tensorboard est installé
pip install tensorboard

# Ou avec uv
uv pip install tensorboard
```

### Port déjà utilisé
```bash
# Utiliser un autre port
./run_tensorboard.sh runs 6007
```

### Pas de données affichées
- Vérifier que l'entraînement a bien été lancé avec `--tensorboard`
- Vérifier que le répertoire `runs/` existe et contient des données
- Rafraîchir la page TensorBoard

### Logs trop volumineux
```bash
# Supprimer les anciens logs
rm -rf runs/qlearning_*
```

## 📚 Inspiration

Cette implémentation est inspirée de :
- **rl-cloudsimplus** : https://github.com/tgasla/rl-cloudsimplus
- Architecture Docker avec TensorBoard standalone
- Monitoring en temps réel des entraînements cloud

## 🔗 Ressources

- [TensorBoard Documentation](https://www.tensorflow.org/tensorboard)
- [PyTorch TensorBoard Tutorial](https://pytorch.org/tutorials/recipes/recipes/tensorboard_with_pytorch.html)
- [rl-cloudsimplus GitHub](https://github.com/tgasla/rl-cloudsimplus)

## ✅ Checklist

- [x] TensorBoard callback créé (`utils/tensorboard_callback.py`)
- [x] Intégration Q-Learning (`train_simple_qlearning.py`)
- [ ] Intégration DQN (`train_dqn_policy.py`) - À faire
- [x] Script de lancement (`run_tensorboard.sh`)
- [x] Documentation complète

---

**Note** : TensorBoard est optionnel. Si vous ne voulez pas l'utiliser, entraînez simplement sans le flag `--tensorboard`.
