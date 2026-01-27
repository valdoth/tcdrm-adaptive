# TCDRM-ADAPTIVE : Apprentissage par Renforcement pour la Réplication Adaptative

## Vue d'ensemble

TCDRM-ADAPTIVE transforme TCDRM d'un modèle à seuils statiques vers un framework adaptatif capable d'apprendre automatiquement les décisions de réplication et de suppression de réplicas tout en respectant le budget du locataire.

### Différences clés avec TCDRM Statique

| Aspect | TCDRM Statique | TCDRM-ADAPTIVE |
|--------|----------------|----------------|
| **Décision** | Seuil fixe (200 requêtes) | Politique apprise par RL |
| **Seuils** | TSLA, CSLA, PSLA fixes | Dynamiques et adaptatifs |
| **Récompense** | N/A | Multi-objectif (5 composantes) |
| **Adaptabilité** | Aucune | S'adapte aux workloads variés |

---

## Architecture

### Environnement Gymnasium

**Fichier** : `python_rl/envs/tcdrm_env.py`

**Observation Space** (8 dimensions) :
```python
[budget_ratio, latency, access_count_norm, replica_count,
 query_complexity, sla_violation_rate, cost_rate, popularity]
```

**Action Space** (3 actions discrètes) :
- `0` : CREATE_REPLICA
- `1` : DELETE_REPLICA
- `2` : DO_NOTHING

**Fonction de Récompense Multi-objectif** :
```python
reward = (
    + α * sla_compliance_reward      # Respect du SLA (α=10)
    - β * cost_penalty                # Minimiser coûts (β=5)
    + γ * budget_efficiency_reward    # Efficacité budgétaire (γ=15)
    - δ * instability_penalty         # Stabilité décisions (δ=8)
    + ε * strategic_timing_reward     # Timing stratégique (ε=20)
)
```

### Agent Q-Learning

**Fichier** : `python_rl/agents/tabular_qlearning.py`

**Discrétisation** : 108 états (3 × 3 × 3 × 4)
- Budget : 3 bins (low, medium, high)
- Latence : 3 bins (good, near SLA, violation)
- Popularité : 3 bins (low, medium, high)
- Réplicas : 4 bins (0, 1, 2, 3+)

**Algorithme** : Q-Learning tabulaire avec epsilon-greedy

---

## Utilisation

### 1. Entraînement

#### Entraînement rapide (test)
```bash
cd python_rl
uv run python train_adaptive_policy.py \
    --episodes 20 \
    --queries 500 \
    --output-dir results/tcdrm_adaptive/test_run
```

#### Entraînement complet
```bash
cd python_rl
uv run python train_adaptive_policy.py \
    --episodes 200 \
    --queries 1000 \
    --lr 0.1 \
    --gamma 0.95 \
    --epsilon-start 1.0 \
    --epsilon-end 0.01 \
    --epsilon-decay 0.995 \
    --output-dir results/tcdrm_adaptive/full_run
```

#### Paramètres disponibles
```
--episodes         Nombre d'épisodes d'entraînement (défaut: 200)
--queries          Nombre de requêtes par épisode (défaut: 1000)
--lr               Learning rate (défaut: 0.1)
--gamma            Discount factor (défaut: 0.95)
--epsilon-start    Epsilon initial (défaut: 1.0)
--epsilon-end      Epsilon final (défaut: 0.01)
--epsilon-decay    Taux de décroissance epsilon (défaut: 0.995)
--seed             Random seed (défaut: 42)
--output-dir       Répertoire de sortie
```

### 2. Résultats

Après l'entraînement, les fichiers suivants sont générés :

```
results/tcdrm_adaptive/run_YYYYMMDD_HHMMSS/
├── adaptive_model.pkl          # Modèle Q-Learning entraîné
├── training_metrics.pkl        # Métriques d'entraînement
└── training_metrics.png        # Graphiques de progression
```

### 3. Évaluation

Pour évaluer le modèle entraîné :

```bash
cd python_rl
uv run python -c "
import pickle
import numpy as np
from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent

# Charger le modèle
with open('results/tcdrm_adaptive/full_run/adaptive_model.pkl', 'rb') as f:
    model_data = pickle.load(f)

# Créer agent
agent = TabularQLearningAgent(
    n_states=model_data['n_states'],
    n_actions=model_data['n_actions']
)
agent.q_table = model_data['q_table']

# Tester sur R1 (5.3 GB)
env = TcdrmAdaptiveEnv(data_gb=5.3)
state, info = env.reset(seed=42)

total_reward = 0
for i in range(1000):
    state_idx = agent.discretize_state(state)
    action = agent.select_action(state_idx, training=False)
    state, reward, terminated, truncated, info = env.step(action)
    total_reward += reward
    if terminated or truncated:
        break

print(f'Reward total: {total_reward:.2f}')
print(f'Coût total: {info[\"total_cost\"]:.2f}')
print(f'Violations SLA: {info[\"sla_violations\"]}')
"
```

---

## Métriques d'Entraînement

### Métriques suivies

1. **Reward par épisode** : Récompense totale cumulée
2. **Coût par épisode** : Coût total (BW + CPU + Storage)
3. **Violations SLA** : Nombre de requêtes > SLA_THRESHOLD
4. **Changements de réplicas** : Fréquence CREATE/DELETE

### Graphiques générés

Le script génère automatiquement un graphique 2×2 avec :
- Évolution du reward
- Évolution du coût
- Violations SLA
- Changements de réplicas

---

## Composantes de la Récompense

### 1. SLA Compliance (α=10)
```python
if query_latency <= SLA_THRESHOLD:
    reward += 10.0
else:
    reward -= 20.0 * (query_latency / SLA_THRESHOLD - 1.0)
```

### 2. Cost Penalty (β=5)
```python
penalty = query_cost * 5.0
if action == CREATE_REPLICA:
    penalty += replication_cost * 2.0
```

### 3. Budget Efficiency (γ=15)
```python
if budget_ratio > 0.5:
    reward += 15.0
elif budget_ratio > 0.2:
    reward += 5.0
else:
    reward -= 15.0
```

### 4. Instability Penalty (δ=8)
```python
if action_changed_from_last:
    penalty = 8.0
```

### 5. Strategic Timing (ε=20)
```python
if action == CREATE and popularity > 0.7:
    reward += 20.0
elif action == CREATE and popularity < 0.3:
    reward -= 15.0
```

---

## Comparaison avec TCDRM Statique

### Avantages de TCDRM-ADAPTIVE

1. **Adaptabilité** : S'adapte automatiquement aux workloads variés
2. **Pas de seuils fixes** : Apprend les seuils optimaux
3. **Multi-objectif** : Optimise simultanément SLA, coût, budget
4. **Stabilité** : Pénalise les changements fréquents
5. **Timing stratégique** : Crée réplicas au bon moment

### Résultats attendus

- **Réduction des coûts** : -25% (réplicas au bon moment)
- **Moins de violations SLA** : -20%
- **Stabilité** : -30% de changements de réplicas
- **Efficacité budgétaire** : +15%

---

## Prochaines Étapes

### Phase 2 : Deep Q-Network (DQN)

Pour gérer des espaces d'état plus larges :

```python
import torch
import torch.nn as nn

class DQN(nn.Module):
    def __init__(self, state_dim=8, action_dim=3):
        super().__init__()
        self.network = nn.Sequential(
            nn.Linear(state_dim, 64),
            nn.ReLU(),
            nn.Linear(64, 64),
            nn.ReLU(),
            nn.Linear(64, action_dim)
        )
    
    def forward(self, x):
        return self.network(x)
```

### Phase 3 : Proximal Policy Optimization (PPO)

Avec Stable-Baselines3 :

```python
from stable_baselines3 import PPO

model = PPO(
    "MlpPolicy",
    env,
    learning_rate=3e-4,
    n_steps=2048,
    batch_size=64,
    n_epochs=10,
    gamma=0.99,
    verbose=1
)

model.learn(total_timesteps=100000)
model.save("tcdrm_adaptive_ppo")
```

### Phase 4 : Intégration CloudSim

Remplacer la simulation Python par CloudSim via Py4J :

```python
# Python
from py4j.java_gateway import JavaGateway

gateway = JavaGateway()
cloudsim_manager = gateway.entry_point.getSimulationManager()

# Dans step()
result = cloudsim_manager.executeAction(action)
latency = result.getLatency()
cost = result.getCost()
```

---

## Fichiers Importants

### Code Principal
- `python_rl/envs/tcdrm_env.py` : Environnement Gymnasium
- `python_rl/agents/tabular_qlearning.py` : Agent Q-Learning
- `python_rl/train_adaptive_policy.py` : Script d'entraînement

### Documentation
- `TCDRM_ADAPTIVE_SPEC.md` : Spécification technique complète
- `README_TCDRM_ADAPTIVE.md` : Ce fichier

### Résultats
- `results/tcdrm_adaptive/` : Modèles et métriques

---

## Références

- **Article TCDRM** : Threshold-based Cost-aware Data Replication Management
- **Gymnasium** : https://gymnasium.farama.org/
- **Stable-Baselines3** : https://stable-baselines3.readthedocs.io/
- **CloudSim Plus** : https://github.com/cloudsimplus/cloudsimplus
- **Repo référence** : https://github.com/FCBayern1/rl-cloudsimplus-greenscheduling

---

## Support

Pour toute question ou problème :
1. Vérifier la spécification technique : `TCDRM_ADAPTIVE_SPEC.md`
2. Consulter les logs d'entraînement
3. Vérifier les métriques dans `training_metrics.pkl`
