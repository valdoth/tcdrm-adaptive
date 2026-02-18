# Algorithmes de Reinforcement Learning - TCDRM-ADAPTIVE

---

## 1. MÉTHODES PROPOSÉES

### 1.1 Q-Learning Amélioré

Le Q-learning est une technique de Reinforcement Learning model-free. Il apprend une fonction de valeur action-état Q(s,a) qui donne l'utilité espérée de prendre une action `a` dans un état `s`.

#### Équation de mise à jour Q-Learning

**Formule de Bellman** :

```
Q(s,a) ← Q(s,a) + α[r + γ·max_a' Q(s',a') - Q(s,a)]
```

Où :

- **Q(s,a)** : Valeur action-état
- **α** : Learning rate (0.1)
- **r** : Récompense immédiate
- **γ** : Discount factor (0.99)
- **s'** : État suivant
- **max_a' Q(s',a')** : Meilleure valeur Q pour l'état suivant

#### Algorithme Q-Learning

```
1. Pour tout s ∈ S et a ∈ A(s) : initialiser Q(s, a)
2. s := état initial observé
3. loop
4.   Choisir a ∈ A(s) selon politique ε-greedy
5.   Exécuter action a et observer s' et r
6.   Q[s, a] := Q[s, a] + α(r + γ·max_a' Q[s', a'] - Q[s, a])
7.   s := s'
8. end loop
9. return π(s) = argmax_a Q(s, a)
```

#### Améliorations implémentées

**Double Q-Learning** : Utilise deux Q-tables (Q_A et Q_B) pour réduire l'overestimation des valeurs Q.

**Learning Rate Adaptatif** : α_t = α_0 / (1 + 0.01 \* visit_count[s, a])

**Exploration Intelligente** : Favorise les actions moins visitées pendant l'exploration.

#### Paramètres

- **α (Learning Rate)** : 0.1
- **γ (Discount Factor)** : 0.99
- **ε_start** : 1.0 → **ε_min** : 0.01 (decay=0.995)
- **États** : 243 états discrets
- **Actions** : 3 (NOOP, REPLICATE, DELETE)

---

### 1.2 Deep Q-Network (DQN)

Le DQN est une technique de Reinforcement Learning qui utilise un réseau de neurones profond pour approximer la fonction Q(s,a). Contrairement au Q-learning tabulaire, le DQN peut gérer des espaces d'états continus de haute dimension.

#### Algorithme DQN

```
1. Initialize replay buffer D avec capacité N
2. Initialize Q-network Q(s, a; θ) avec poids θ aléatoires
3. Initialize target network Q̂(s, a; θ⁻) avec θ⁻ = θ
4. Pour chaque épisode:
5.   Initialize état s
6.   Pour chaque step t:
7.     Avec probabilité ε: choisir action a aléatoire
8.     Sinon: a ← argmax_a Q(s, a; θ)
9.     Exécuter action a, observer récompense r et état suivant s'
10.    Stocker transition (s, a, r, s') dans D
11.    Échantillonner mini-batch aléatoire de transitions (sⱼ, aⱼ, rⱼ, s'ⱼ) de D
12.    Pour chaque transition j:
13.      Si s'ⱼ est terminal:
14.        yⱼ ← rⱼ
15.      Sinon:
16.        yⱼ ← rⱼ + γ * max_a' Q̂(s'ⱼ, a'; θ⁻)
17.    Effectuer gradient descent sur (yⱼ - Q(sⱼ, aⱼ; θ))²
18.    Tous les C steps: θ⁻ ← θ (mise à jour target network)
19.    s ← s'
20.  Fin step
21. Fin épisode
```

**Équation de Bellman pour DQN** :

```
Q(s, a; θ) ← Q(s, a; θ) + α(r + γ * max_a' Q̂(s', a'; θ⁻) - Q(s, a; θ))
```

#### Améliorations implémentées

Notre implémentation utilise des variantes avancées du DQN :

**Double DQN** : Pour réduire l'overestimation, utilise le Q-network pour sélectionner l'action et le target network pour l'évaluer :

```
yⱼ ← rⱼ + γ * Q̂(s'ⱼ, argmax_a' Q(s'ⱼ, a'; θ); θ⁻)
```

**Prioritized Experience Replay** : Échantillonne les transitions importantes plus fréquemment selon leur TD-error.

**Soft Target Update** : Mise à jour progressive au lieu de copie périodique :

```
θ⁻ ← τ*θ + (1-τ)*θ⁻
```

#### Architecture Dueling DQN

Le réseau utilise une architecture Dueling qui sépare la valeur d'état V(s) et l'avantage d'action A(s,a):

```
Input (8D) → Shared Layers [Dense(64) + ReLU, Dense(64) + ReLU]
                ↓
         ┌──────────────┬──────────────┐
         ↓              ↓              ↓
    Value Stream   Advantage Stream
    Dense(32)+ReLU  Dense(32)+ReLU
    Dense(1)        Dense(3)
         ↓              ↓
         V(s)          A(s,a)
         └──────────────┴──────────────┘
                    ↓
        Q(s,a) = V(s) + (A(s,a) - mean_a(A(s,a)))
```

#### Paramètres

- **α (Learning Rate)** : 0.001
- **γ (Discount Factor)** : 0.99
- **ε_start** : 1.0 → **ε_min** : 0.01 (decay λ=0.0005)
- **Batch Size** : 64
- **Buffer Capacity** : 10,000
- **Target Update τ** : 0.005 (soft update)
- **Gradient Clip** : 1.0
- **États** : 8 dimensions continues
- **Actions** : 3 (NOOP, REPLICATE, DELETE)

---

### 1.3 Fonction de Récompense

Notre fonction de récompense est multi-objectifs et étend la formulation de READ2.md.

#### Formulation

```
R = ALPHA * R_sla + BETA * R_cost + GAMMA * R_budget
  + DELTA * R_stability + EPSILON * R_timing + ZETA * R_tsla
  + ETA * R_proactive
```

Où :

- **R_sla** : Conformité SLA dynamique (TSLA)
- **R_cost** : Efficacité des coûts
- **R_budget** : Efficacité budgétaire
- **R_stability** : Stabilité des décisions
- **R_timing** : Timing stratégique (PLSA)
- **R_tsla** : Qualité ajustement TSLA
- **R_proactive** : Bonus proactif

#### Poids

| Composante        | Poids |
| ----------------- | ----- |
| ALPHA (SLA)       | 15.0  |
| BETA (Cost)       | 6.0   |
| GAMMA (Budget)    | 18.0  |
| DELTA (Stability) | 10.0  |
| EPSILON (Timing)  | 25.0  |
| ZETA (TSLA)       | 15.0  |
| ETA (Proactive)   | 12.0  |

---

## 2. RÉSULTATS EXPÉRIMENTAUX

### 2.1 Résultats Q-Learning Amélioré

**Entraînement** (1500 épisodes) :

| Métrique             | Valeur           |
| -------------------- | ---------------- |
| Reward moyen         | 6170.69 ± 228.06 |
| SLA compliance       | 32.8% ± 4.7%     |
| États explorés       | 38/243 (15.6%)   |
| Temps d'entraînement | ~48 secondes     |

### 2.2 Résultats DQN

**Configuration** :

- Episodes : 1500
- Requêtes/épisode : 1000
- Architecture : Dueling DQN (64-64-32)

### 2.3 Comparaison Q-Learning vs DQN

| Métrique             | Q-Learning    | DQN          |
| -------------------- | ------------- | ------------ |
| Temps d'entraînement | ~48s          | ~30-40 min   |
| Espace d'états       | Discret (243) | Continu (8D) |
| Généralisation       | Limitée       | Excellente   |
| Mémoire              | Faible        | Élevée       |
