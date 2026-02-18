# Modèle Deep Q-Network (DQN) pour TCDRM v2

Cette section présente l'implémentation réelle du modèle **Deep Q-Network (DQN)** pour TCDRM v2, permettant de gérer des **états continus** et des environnements multi-cloud complexes.

Le problème reste formulé comme un **Processus de Décision Markovien (MDP)** :
⟨S, A, P, R, γ⟩

La différence principale avec Q-learning tabulaire : la fonction Q(s,a) est **approximée par un réseau de neurones profond** Q(s,a; θ).

**Implémentation** : `python_rl/envs/tcdrm_env_v2.py` et `python_rl/agents/dqn_agent.py`

---

## 1. Représentation de l'état (State Representation)

L'état est représenté sous forme **vectorielle continue à 8 dimensions** :

**s = [tQ_norm, cQ_norm, pop_norm, bud_norm, net_inter_ratio, net_intercloud_ratio, repl_factor, trend_pop]**

Type : `np.ndarray` de shape (8,) avec dtype `float32`

### Description détaillée des variables

| #   | Variable                 | Description                | Calcul                                      | Plage   |
| --- | ------------------------ | -------------------------- | ------------------------------------------- | ------- |
| 1   | **tQ_norm**              | Temps de réponse normalisé | clip(current_latency / RT_MAX, 0, 1)        | [0, 1]  |
| 2   | **cQ_norm**              | Coût moyen normalisé       | clip(avg_cost / budget_per_query, 0, 1)     | [0, 1]  |
| 3   | **pop_norm**             | Popularité prédite (PLSA)  | plsa_model.predict_popularity()             | [0, 1]  |
| 4   | **bud_norm**             | Budget restant normalisé   | clip(current_budget / INITIAL_BUDGET, 0, 1) | [0, 1]  |
| 5   | **net_inter_ratio**      | Ratio trafic inter-région  | inter_region_traffic / total_traffic        | [0, 1]  |
| 6   | **net_intercloud_ratio** | Ratio trafic inter-cloud   | inter_cloud_traffic / total_traffic         | [0, 1]  |
| 7   | **repl_factor**          | Facteur de réplication     | current_replicas / MAX_REPLICAS             | [0, 1]  |
| 8   | **trend_pop**            | Tendance popularité        | clip(pop[t] - pop[t-1], -1, 1)              | [-1, 1] |

**Constantes** :

- RT_MAX = 250.0 secondes
- INITIAL_BUDGET = 1000.0
- MAX_REPLICAS = 3

---

## 2. Espace des actions (Action Space)

Identique au modèle Q-learning mais avec **action masking dynamique** :

**A = {0: NOOP, 1: REPLICATE, 2: DELETE}**

| Action    | Code | Description              |
| --------- | ---- | ------------------------ |
| NOOP      | 0    | Ne rien faire            |
| REPLICATE | 1    | Créer un nouveau réplica |
| DELETE    | 2    | Supprimer un réplica     |

### Action Masking

Le masque est un vecteur `[1, 1, 1]` où `0` = action interdite.

```python
mask = np.ones(3, dtype=np.float32)

# REPLICATE interdit si:
if replicas >= MAX_REPLICAS or budget < replication_cost * 2:
    mask[1] = 0

# DELETE interdit si:
if replicas == 0:
    mask[2] = 0
```

Le DQN utilise ce masque en mettant les Q-values invalides à `-inf`.

---

## 3. Architecture du Deep Q-Network

Le DQN approxime **Q(s,a; θ)** avec un réseau neuronal feed-forward.

### Architecture implémentée : Dueling DQN

Notre implémentation utilise une **architecture Dueling DQN** qui sépare la valeur d'état V(s) et l'avantage d'action A(s,a) :

```
Input: [8] (état continu)
   ↓
Shared Layers:
   Linear(8 → 64) + ReLU
   Linear(64 → 64) + ReLU
   ↓
   ┌──────────────┬──────────────┐
   ↓              ↓              ↓
Value Stream   Advantage Stream
Linear(64→32)  Linear(64→32)
+ ReLU         + ReLU
Linear(32→1)   Linear(32→3)
   ↓              ↓
   V(s)          A(s,a)
   └──────────────┴──────────────┘
                  ↓
      Q(s,a) = V(s) + (A(s,a) - mean_a(A(s,a)))
                  ↓
Output: [3] (Q-values pour NOOP, REPLICATE, DELETE)
```

**Avantage** : Apprentissage plus efficace - le réseau apprend quels états sont bons indépendamment des actions.

### Implémentation PyTorch

```python
class DuelingDQNNetwork(nn.Module):
    def __init__(self, state_dim=8, action_dim=3, hidden_dims=[64, 64]):
        super().__init__()
        # Shared layers
        self.shared = nn.Sequential(
            nn.Linear(state_dim, hidden_dims[0]),
            nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]),
            nn.ReLU()
        )

        # Value stream: V(s)
        self.value_stream = nn.Sequential(
            nn.Linear(hidden_dims[1], 32),
            nn.ReLU(),
            nn.Linear(32, 1)
        )

        # Advantage stream: A(s,a)
        self.advantage_stream = nn.Sequential(
            nn.Linear(hidden_dims[1], 32),
            nn.ReLU(),
            nn.Linear(32, action_dim)
        )

    def forward(self, x):
        x = self.shared(x)
        value = self.value_stream(x)
        advantage = self.advantage_stream(x)
        # Combine: Q(s,a) = V(s) + (A(s,a) - mean(A))
        advantage_mean = advantage.mean(dim=1, keepdim=True)
        q_values = value + (advantage - advantage_mean)
        return q_values
```

**Paramètres totaux** : ~6K paramètres (architecture légère)

**Implémentation** : `dqn_agent.py` lignes 22-72, activé par défaut avec `use_dueling=True`

---

## 4. Fonction de récompense

Fonction de récompense **multi-objectifs** pour TCDRM v2 :

**R = +r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH**

### Composantes détaillées

| Terme         | Calcul                                         | Poids     |
| ------------- | ---------------------------------------------- | --------- |
| **SLA_OK**    | max(0, 1 - tQ_norm)                            | r1 = 10.0 |
| **SLA_VIOL**  | max(0, tQ_norm - 1)                            | r2 = 20.0 |
| **COST_OVER** | max(0, cQ_norm - 1)                            | r3 = 15.0 |
| **REPL_COST** | (data_gb × 0.10) / INITIAL_BUDGET si REPLICATE | r4 = 5.0  |
| **THRASH**    | 1 si alternance REPL↔DEL consécutive           | r5 = 8.0  |

### Implémentation

```python
tQ_norm = query_latency / RT_MAX
cQ_norm = query_cost / (INITIAL_BUDGET / MAX_QUERIES)

sla_ok = max(0.0, 1.0 - tQ_norm)
sla_viol = max(0.0, tQ_norm - 1.0)
cost_over = max(0.0, cQ_norm - 1.0)

repl_cost = 0.0
if action == 1 and executed:
    repl_cost = (data_gb * 0.10) / INITIAL_BUDGET

thrash = 1.0 if alternance_detectee else 0.0

reward = (R1_SLA_OK * sla_ok - R2_SLA_VIOL * sla_viol -
          R3_COST_OVER * cost_over - R4_REPL_COST * repl_cost -
          R5_THRASH * thrash)
```

---

## 5. Apprentissage avec DQN

### 5.1. Prioritized Experience Replay (PER)

Notre implémentation utilise **Prioritized Experience Replay** pour échantillonner les transitions importantes plus fréquemment :

```python
class PrioritizedReplayBuffer:
    def __init__(self, capacity=10000, alpha=0.6, beta=0.4):
        self.buffer = deque(maxlen=capacity)
        self.priorities = deque(maxlen=capacity)
        self.alpha = alpha  # Exposant de priorité
        self.beta = beta    # Correction importance sampling

    def push(self, state, action, reward, next_state, done):
        max_priority = max(self.priorities) if self.priorities else 1.0
        self.buffer.append((state, action, reward, next_state, done))
        self.priorities.append(max_priority)

    def sample(self, batch_size=64):
        # Probabilités basées sur priorités
        priorities = np.array(self.priorities)
        probs = priorities ** self.alpha
        probs /= probs.sum()

        # Échantillonner selon priorités
        indices = np.random.choice(len(self.buffer), batch_size, p=probs)

        # Importance sampling weights
        weights = (len(self.buffer) * probs[indices]) ** (-self.beta)
        weights /= weights.max()

        return batch, indices, weights

    def update_priorities(self, indices, td_errors):
        for idx, error in zip(indices, td_errors):
            self.priorities[idx] = abs(error) + 1e-6
```

**Avantage** : Apprentissage plus rapide en rejouant les transitions importantes plus souvent.

**Paramètres par défaut** :

- Capacité : 10,000 transitions
- Batch size : 64
- α (priorité) : 0.6
- β (importance sampling) : 0.4 → 1.0

**Implémentation** : `dqn_agent.py` lignes 99-178, activé par défaut avec `use_prioritized_replay=True`

### 5.2. Target Network avec Soft Update

**Deux réseaux identiques** pour stabiliser l'apprentissage :

1. **Policy Network** (θ) : Mis à jour à chaque step
2. **Target Network** (θ⁻) : Mise à jour progressive (soft update)

```python
# Initialisation
self.policy_net = DuelingDQNNetwork(8, 3, [64, 64])
self.target_net = DuelingDQNNetwork(8, 3, [64, 64])
self.target_net.load_state_dict(self.policy_net.state_dict())

# Soft update à chaque step (τ = 0.005)
for target_param, policy_param in zip(self.target_net.parameters(),
                                       self.policy_net.parameters()):
    target_param.data.copy_(
        τ * policy_param.data + (1 - τ) * target_param.data
    )
```

**Avantage** : Stabilité accrue - mise à jour progressive au lieu de copies périodiques brusques.

**Implémentation** : `dqn_agent.py` lignes 419-423, avec `tau=0.005` par défaut

### 5.3. Algorithme d'entraînement avec Double DQN

**Pour chaque transition** :

1. **Stocker** : `replay_buffer.push(s, a, r, s', done)`

2. **Échantillonner** un batch avec priorités si buffer suffisamment rempli

3. **Calculer Q actuel** :

   ```python
   Q_current = policy_net(s)[a]
   ```

4. **Calculer Q cible avec Double DQN** :

   ```python
   # Double DQN: utiliser policy_net pour sélectionner, target_net pour évaluer
   next_actions = policy_net(s').argmax(1)  # Sélection
   Q_target = target_net(s').gather(1, next_actions)  # Évaluation
   target = r + γ × Q_target × (1 - done)
   ```

5. **Loss Huber (SmoothL1Loss)** avec importance sampling weights :

   ```python
   td_errors = abs(Q_current - target)
   loss = (weights * SmoothL1Loss(Q_current, target)).mean()
   ```

6. **Backpropagation avec gradient clipping** :

   ```python
   optimizer.zero_grad()
   loss.backward()
   torch.nn.utils.clip_grad_norm_(policy_net.parameters(), max_norm=1.0)
   optimizer.step()
   ```

7. **Mettre à jour priorités** :

   ```python
   replay_buffer.update_priorities(indices, td_errors)
   ```

8. **Soft update du target network** :
   ```python
   θ⁻ ← τ*θ + (1-τ)*θ⁻
   ```

**Optimiseur** : Adam avec learning_rate = 0.001 (par défaut)

**Implémentation** : `dqn_agent.py` lignes 368-427

---

## 6. Politique d'exploration

### ε-greedy avec décroissance exponentielle

```
ε_t = max(ε_min, ε_0 × exp(-λ × t))
```

**Paramètres par défaut** :

- ε_start = 1.0 (exploration totale)
- ε_min = 0.01 (exploration minimale)
- λ (epsilon_decay_lambda) = 0.0005

### Sélection d'action avec masking

```python
def select_action(state, action_mask, training=True):
    valid_actions = np.where(action_mask > 0)[0]

    if training and random() < epsilon:
        # Exploration: action aléatoire valide
        return np.random.choice(valid_actions)
    else:
        # Exploitation: meilleure Q-value valide
        q_values = policy_net(state)
        q_values[action_mask == 0] = -inf  # Masquer invalides
        return q_values.argmax()
```

---

## 7. Paramètres d'entraînement

### Configuration par défaut

| Paramètre           | Valeur   | Description                      |
| ------------------- | -------- | -------------------------------- |
| **Episodes**        | 1500     | Nombre d'épisodes d'entraînement |
| **Queries/episode** | 1000     | Requêtes par épisode             |
| **Learning rate**   | 0.001    | Taux d'apprentissage Adam        |
| **Discount γ**      | 0.99     | Facteur de discount              |
| **Buffer size**     | 10,000   | Capacité replay buffer (PER)     |
| **Batch size**      | 64       | Taille du batch                  |
| **Target update τ** | 0.005    | Coefficient soft update          |
| **Gradient clip**   | 1.0      | Norme maximale des gradients     |
| **PER α**           | 0.6      | Exposant de priorité             |
| **PER β**           | 0.4→1.0  | Correction importance sampling   |
| **Device**          | CPU/CUDA | Automatique                      |

### Script d'entraînement

```bash
python python_rl/train_dqn_policy.py \
  --episodes 1500 \
  --queries 1000 \
  --buffer-size 10000 \
  --batch-size 64 \
  --lr 0.001 \
  --gamma 0.99 \
  --epsilon-start 1.0 \
  --epsilon-min 0.01 \
  --epsilon-decay-lambda 0.0005
```

### Métriques suivies

- **episode_rewards** : Récompense totale par épisode
- **episode_costs** : Coût total par épisode
- **episode_sla_violations** : Nombre de violations SLA
- **episode_replica_changes** : Nombre de changements de réplicas
- **losses** : Loss d'entraînement (MSE)
- **epsilon** : Valeur d'epsilon au cours du temps

---

## 8. Avantages du DQN pour TCDRM v2

### ✅ Avantages

- **États continus** : Gère 8 dimensions continues sans discrétisation
- **Scalabilité** : Peut facilement ajouter de nouvelles dimensions d'état
- **Généralisation** : Apprend des politiques pour états non vus
- **Politiques complexes** : Capture des relations non-linéaires
- **Action masking** : Respecte les contraintes dynamiques
- **Stabilité** : Target network + soft update + PER
- **Double DQN** : Réduit l'overestimation des valeurs Q
- **Dueling DQN** : Apprentissage plus efficace (V(s) + A(s,a))
- **Prioritized Replay** : Apprentissage plus rapide
- **Gradient clipping** : Évite les explosions de gradients

### ⚠️ Limitations

- **Complexité** : Plus difficile à déboguer que Q-learning tabulaire
- **Hyperparamètres** : Sensible au choix des hyperparamètres
- **Temps d'entraînement** : Plus long que Q-learning (200 épisodes)
- **Ressources** : Bénéficie d'un GPU (optionnel)

## 9. Comparaison Q-learning vs DQN

| Aspect                   | Q-learning Tabulaire             | DQN                        |
| ------------------------ | -------------------------------- | -------------------------- |
| **Espace d'états**       | 243 états discrets               | ∞ états continus           |
| **Représentation**       | Q-table [243×3]                  | Réseau neuronal ~6K params |
| **Mémoire**              | ~2 KB                            | ~24 KB (modèle) + buffer   |
| **Généralisation**       | Aucune                           | Forte                      |
| **Temps d'entraînement** | ~48s (1500 ep)                   | ~30-40 min (1500 ep)       |
| **Interprétabilité**     | Haute (Q-table visible)          | Faible (boîte noire)       |
| **Scalabilité**          | Limitée (explosion combinatoire) | Excellente                 |
| **Convergence**          | Garantie (sous conditions)       | Non garantie               |

---

**Le DQN est recommandé pour** :

- Environnements avec nombreuses variables continues
- Besoin de généralisation à de nouveaux états
- Scalabilité vers des systèmes multi-cloud complexes

**Le Q-learning tabulaire est recommandé pour** :

- Prototypage rapide
- Environnements simples et bien définis
- Besoin d'interprétabilité maximale
