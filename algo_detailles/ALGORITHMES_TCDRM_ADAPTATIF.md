# ALGORITHMES TCDRM-ADAPTIVE
## Implémentation Réelle avec Apprentissage par Renforcement

Ce document présente les algorithmes adaptatifs implémentés dans TCDRM v2, basés sur l'apprentissage par renforcement (Q-learning tabulaire et Deep Q-Network).

---

## SUJET 1 – TCDRM-ADAPTIVE

### Objectif

Introduire un mécanisme **adaptatif et auto-apprenant** pour décider quand répliquer et quand supprimer des réplicas, en utilisant l'apprentissage par renforcement au lieu de seuils SLA statiques.

**Implémentation** : Deux approches sont disponibles
- **Q-learning tabulaire** : `python_rl/envs/tcdrm_qlearning_env.py` + `python_rl/agents/simple_qlearning_agent.py`
- **Deep Q-Network (DQN)** : `python_rl/envs/tcdrm_env_v2.py` + `python_rl/agents/dqn_agent.py`

---

## ALGORITHME A1 – Décision Adaptative de Réplication
### (Apprentissage par Renforcement - Q-learning Tabulaire)

### Entrées

| Paramètre | Type | Description | Valeur par défaut |
|-----------|------|-------------|-------------------|
| **Q** | Query | Requête du locataire | - |
| **D(Q)** | Set | Ensemble des données impliquées | - |
| **BudgetTenant** | float | Budget du locataire | 1000.0 |
| **data_gb** | float | Taille des données (GB) | 5.3 |
| **QTable** | array[243, 3] | Table de valeurs Q(s,a) | Initialisée à 0 |
| **α** (alpha) | float | Taux d'apprentissage | 0.1 |
| **γ** (gamma) | float | Facteur d'actualisation | 0.99 |
| **ε** (epsilon) | float | Taux d'exploration | 1.0 → 0.01 |
| **ΔT** | int | Fenêtre d'observation | 100 requêtes |
| **MAX_REPLICAS** | int | Nombre maximum de réplicas | 3 |

### Sorties

| Sortie | Type | Description |
|--------|------|-------------|
| **action** | int | Action sélectionnée : {0: NOOP, 1: REPLICATE, 2: DELETE} |
| **QTable** | array | Table Q mise à jour |

### Pseudocode

```
1. Exécuter Q avec l'état courant du système.

2. Mesurer tQ ← responseTime(Q) et estimer cQ ← monetaryCost(Q) via CostModel.

3. Calculer pop ← popularityFeatures(D(Q), ΔT) via PLSA.

4. Construire l'état discret s :
   s = (bin(tQ), bin(cQ), bin(pop), bin(budgetRemaining), bin(networkType))
   où :
   - bin(tQ) ∈ {RT0, RT1, RT2} selon μRT et σRT (adaptatifs)
   - bin(cQ) ∈ {C0, C1, C2} selon CSLA
   - bin(pop) ∈ {P0, P1, P2} selon seuils [0.33, 0.67]
   - bin(budgetRemaining) ∈ {B0, B1, B2} selon [60%, 30%]
   - bin(networkType) ∈ {N0, N1, N2} selon nombre de réplicas
   
   Convertir s en index unique idx ∈ [0, 242] pour Q-table.

5. Obtenir les actions valides ValidActions :
   pour chaque a ∈ {NOOP, REPLICATE, DELETE} :
      si a = REPLICATE et (budget < 30% ou replicas ≥ MAX_REPLICAS) : exclure
      si a = DELETE et replicas = 0 : exclure
      sinon : ajouter à ValidActions

6. Sélectionner l'action a par stratégie ε-greedy :
   avec probabilité ε : choisir a aléatoire dans ValidActions
   sinon : a = argmax_{a' ∈ ValidActions} QTable[idx, a']

7. Appliquer l'action :
   si a = REPLICATE :
      si replicas < MAX_REPLICAS et budget ≥ replicationCost :
         replicas ← replicas + 1
         budget ← budget - replicationCost
   si a = DELETE :
      si replicas > 0 :
         replicas ← replicas - 1
   sinon (NOOP) :
      ne rien faire

8. Mettre à jour l'état du système :
   budget ← budget - cQ
   latency ← tQ
   queryCount ← queryCount + 1
   totalCost ← totalCost + cQ
   si tQ > TSLA_BASE : slaViolations ← slaViolations + 1
   ajouter a à actionHistory (garder 5 dernières)

9. Observer le nouvel état s' et convertir en idx'.

10. Calculer la récompense r :
    latency_norm ← min(1.0, tQ / 10000.0)
    r ← 10.0 × (1 - latency_norm)
    si a = REPLICATE et exécuté : r ← r - 0.5
    si a = REPLICATE et tQ > 2000ms : r ← r + 2.0 × min(1, tQ/5000)
    si budget < 30% : r ← r - 5.0
    si thrashing détecté (≥2 alternances dans actionHistory) : r ← r - 1.0

11. Mettre à jour QTable :
    si état terminal :
       target ← r
    sinon :
       target ← r + γ × max_{a'} QTable[idx', a']
    QTable[idx, a] ← QTable[idx, a] + α × (target - QTable[idx, a])

12. Retourner l'action a.
```

### Notes d'implémentation

**Discrétisation dynamique** :
- μRT et σRT sont recalculés sur les 100 dernières requêtes
- Initialisation : μRT = 100ms (LAT_REMOTE_MS), σRT = 50ms

**Anti-thrashing** :
- Fenêtre d'observation : 5 dernières actions
- Détection : ≥2 alternances REPLICATE ↔ DELETE
- Pénalité : -1.0 dans la récompense

**Action masking** :
- REPLICATE interdit si budget < 30% ou replicas ≥ 3
- DELETE interdit si replicas = 0

---

## ALGORITHME A1 – Décision Adaptative de Réplication
### (Apprentissage par Renforcement - Deep Q-Network)

### Entrées

| Paramètre | Type | Description | Valeur par défaut |
|-----------|------|-------------|-------------------|
| **Q** | Query | Requête du locataire | - |
| **D(Q)** | Set | Ensemble des données impliquées | - |
| **BudgetTenant** | float | Budget du locataire | 1000.0 |
| **data_gb** | float | Taille des données (GB) | 5.3 |
| **DQN** | Network | Réseau de neurones Q(s,a;θ) | Architecture 64-64-32 |
| **α** (learning_rate) | float | Taux d'apprentissage Adam | 0.0003 |
| **γ** (gamma) | float | Facteur d'actualisation | 0.99 |
| **ε** (epsilon) | float | Taux d'exploration | 1.0 → 0.01 |
| **ReplayBuffer** | Buffer | Buffer d'expériences | Capacité 50000 |
| **batch_size** | int | Taille du batch | 128 |
| **target_update_freq** | int | Fréquence mise à jour target | 20 épisodes |

### Sorties

| Sortie | Type | Description |
|--------|------|-------------|
| **action** | int | Action sélectionnée : {0: NOOP, 1: REPLICATE, 2: DELETE} |
| **DQN** | Network | Réseau mis à jour |

### Pseudocode

```
1. Exécuter Q avec l'état courant du système.

2. Mesurer tQ ← responseTime(Q) et estimer cQ ← monetaryCost(Q) via CostModel.

3. Calculer pop ← popularityFeatures(D(Q), ΔT) via PLSA et trend_pop ← pop[t] - pop[t-1].

4. Construire l'état continu s (8 dimensions) :
   s = [tQ_norm, cQ_norm, pop_norm, bud_norm, net_inter_ratio, 
        net_intercloud_ratio, repl_factor, trend_pop]
   où :
   - tQ_norm = clip(tQ / RT_MAX, 0, 1)
   - cQ_norm = clip(avg_cost / budget_per_query, 0, 1)
   - pop_norm = pop ∈ [0, 1]
   - bud_norm = clip(budget / INITIAL_BUDGET, 0, 1)
   - net_inter_ratio = inter_region_traffic / total_traffic
   - net_intercloud_ratio = inter_cloud_traffic / total_traffic
   - repl_factor = replicas / MAX_REPLICAS
   - trend_pop = clip(pop[t] - pop[t-1], -1, 1)

5. Obtenir le masque d'actions valides ActionMask :
   ActionMask ← [1, 1, 1]
   si replicas ≥ MAX_REPLICAS ou budget < replicationCost × 2 :
      ActionMask[1] ← 0  (interdire REPLICATE)
   si replicas = 0 :
      ActionMask[2] ← 0  (interdire DELETE)

6. Sélectionner l'action a par stratégie ε-greedy avec masking :
   ValidActions ← indices où ActionMask = 1
   avec probabilité ε : choisir a aléatoire dans ValidActions
   sinon :
      Q_values ← PolicyNetwork(s)
      Q_values[ActionMask = 0] ← -∞
      a ← argmax(Q_values)

7. Appliquer l'action :
   si a = REPLICATE :
      si replicas < MAX_REPLICAS et budget ≥ replicationCost :
         replicas ← replicas + 1
         budget ← budget - replicationCost
   si a = DELETE :
      si replicas > 0 :
         replicas ← replicas - 1
   sinon (NOOP) :
      ne rien faire

8. Mettre à jour l'état du système :
   budget ← budget - cQ
   latency ← tQ
   queryCount ← queryCount + 1
   totalCost ← totalCost + cQ
   si tQ > RT_MAX : slaViolations ← slaViolations + 1
   ajouter a à actionHistory

9. Observer le nouvel état s'.

10. Calculer la récompense r (multi-objectifs) :
    tQ_norm ← tQ / RT_MAX
    cQ_norm ← cQ / (INITIAL_BUDGET / MAX_QUERIES)
    
    sla_ok ← max(0, 1 - tQ_norm)
    sla_viol ← max(0, tQ_norm - 1)
    cost_over ← max(0, cQ_norm - 1)
    repl_cost ← (data_gb × 0.10 / INITIAL_BUDGET) si a = REPLICATE et exécuté, sinon 0
    thrash ← 1 si alternance REPLICATE↔DELETE détectée, sinon 0
    
    r ← 10.0×sla_ok - 20.0×sla_viol - 15.0×cost_over - 5.0×repl_cost - 8.0×thrash

11. Stocker la transition dans ReplayBuffer :
    ReplayBuffer.push(s, a, r, s', done)

12. Si |ReplayBuffer| ≥ batch_size :
    Échantillonner un batch B de taille batch_size depuis ReplayBuffer
    Pour chaque (si, ai, ri, s'i, donei) dans B :
       Q_current ← PolicyNetwork(si)[ai]
       Q_next ← max_a' TargetNetwork(s'i)
       Q_target ← ri + γ × Q_next × (1 - donei)
    
    Loss ← MSE(Q_current, Q_target)
    Mettre à jour PolicyNetwork via backpropagation (Adam)
    
    Si update_count mod target_update_freq = 0 :
       TargetNetwork ← PolicyNetwork

13. Retourner l'action a.
```

### Notes d'implémentation

**Architecture DQN** :
- Input : 8 dimensions (état continu)
- Hidden : 64 → 64 → 32 neurones (ReLU)
- Output : 3 neurones (Q-values pour chaque action)
- Paramètres : ~6K

**Experience Replay** :
- Buffer circulaire de capacité 50,000 transitions
- Échantillonnage aléatoire pour décorréler les données

**Target Network** :
- Copie du policy network mise à jour tous les 20 épisodes
- Stabilise l'apprentissage

---

## ALGORITHME A2 – Sélection des Données Candidates à Répliquer
### (What-to-Replicate Adaptatif)

**Note** : Dans l'implémentation actuelle, la décision porte sur un ensemble de données unique (data_gb). La logique de sélection est **apprise automatiquement** par l'agent RL qui découvre quand répliquer en fonction de l'état global.

### Entrées

| Paramètre | Type | Description |
|-----------|------|-------------|
| **data_gb** | float | Taille des données (GB) |
| **pop(di)** | float | Popularité observée [0, 1] |
| **StatsNet** | dict | Métriques réseau (inter-cloud, inter-région) |
| **replicas** | int | Nombre de réplicas existants |
| **budget** | float | Budget restant |

### Sortie

| Sortie | Type | Description |
|--------|------|-------------|
| **score** | float | Score d'intérêt pour la réplication |

### Pseudocode

```
1. Estimer netImpact(di) : contribution à la bande passante inter-cloud
   si replicas = 0 :
      netImpact ← data_gb × COST_BW_INTER_CLOUD
   si replicas = 1 :
      netImpact ← data_gb × (0.5 × COST_BW_INTER_REGION + 0.5 × COST_BW_INTER_CLOUD)
   si replicas ≥ 2 :
      netImpact ← data_gb × COST_BW_INTRA_DC

2. Estimer storageCost(di) : coût de stockage d'une réplique
   storageCost ← data_gb × STORAGE_COST_PER_GB_PER_HOUR

3. Calculer un score :
   score(di) = λ1×norm(netImpact) + λ2×norm(pop) - λ3×norm(storageCost)
   où λ1 = 0.5, λ2 = 0.3, λ3 = 0.2

4. Décision :
   si score > θ et budget ≥ replicationCost × 2 et replicas < MAX_REPLICAS :
      candidat pour réplication
   sinon :
      ne pas répliquer

5. Retourner score.
```

### Notes d'implémentation

**Apprentissage automatique** :
- L'agent RL apprend implicitement cette logique
- Découvre automatiquement quand la popularité justifie une réplication
- Apprend à équilibrer coût réseau vs coût de stockage
- S'adapte aux contraintes budgétaires dynamiques

---

## ALGORITHME A3 – Suppression Adaptative des Réplicas
### (Anti-Thrashing + Budget-Aware)

### Entrées

| Paramètre | Type | Description | Valeur |
|-----------|------|-------------|--------|
| **DR** | Set | Ensemble des réplicas existants | - |
| **ΔT** | int | Fenêtre d'observation | 100 requêtes |
| **minAge** | int | Âge minimal avant suppression | 50 requêtes |
| **PSLA_dyn** | float | Seuil dynamique de popularité | 0.33 |
| **actionHistory** | list | Historique des actions récentes | 5 dernières |

### Sortie

| Sortie | Type | Description |
|--------|------|-------------|
| **should_delete** | bool | Décision de supprimer ou non |

### Pseudocode

```
1. Pour chaque réplica rd dans DR :

2. Si age(rd) < minAge : continuer (trop récent).

3. Calculer popAvg ← avgPopularity(rd, ΔT) et trend ← popTrend(rd, ΔT).

4. Détecter thrashing :
   alternations ← 0
   pour i de 0 à len(actionHistory) - 2 :
      si (actionHistory[i] = REPLICATE et actionHistory[i+1] = DELETE) ou
         (actionHistory[i] = DELETE et actionHistory[i+1] = REPLICATE) :
         alternations ← alternations + 1
   
   si alternations ≥ 2 :
      thrashing_detected ← vrai
      retourner should_delete = faux (bloquer suppression)

5. Décision basée sur popularité et tendance :
   si popAvg < PSLA_dyn et trend ≤ 0 et replicas > 0 :
      should_delete ← vrai
   sinon :
      should_delete ← faux

6. Si should_delete = vrai, estimer économie future :
   current_storage_cost ← replicas × data_gb × STORAGE_COST_PER_GB_PER_HOUR
   future_storage_cost ← (replicas - 1) × data_gb × STORAGE_COST_PER_GB_PER_HOUR
   network_cost_increase ← data_gb × (COST_BW_INTER_CLOUD - COST_BW_INTRA_DC)
   net_savings ← (current_storage_cost - future_storage_cost) - network_cost_increase
   
   si net_savings ≤ 0 :
      should_delete ← faux

7. Considérer le budget :
   si budget < 30% et replicas > 1 :
      should_delete ← vrai (favoriser suppression pour économiser)

8. Retourner should_delete.
```

### Notes d'implémentation

**Apprentissage automatique** :
- L'agent RL apprend également cette logique automatiquement
- La pénalité anti-thrashing (r5·THRASH) guide vers des politiques stables
- L'agent découvre quand supprimer pour économiser du budget
- Apprend à garder des réplicas malgré une faible popularité si nécessaire

**Anti-thrashing** :
- Fenêtre d'observation : 5 dernières actions
- Pénalité dans la récompense : -1.0 (Q-learning) / -8.0 (DQN)
- Âge minimal : 50 requêtes avant suppression possible

---

## PARAMÈTRES DU SYSTÈME

### Constantes Globales

```
# Environnement
MAX_QUERIES = 1000              # Requêtes par épisode
INITIAL_BUDGET = 1000.0         # Budget initial ($)
MAX_REPLICAS = 3                # Nombre maximum de réplicas
TSLA_BASE = 1000.0              # SLA temps de réponse (ms) - Q-learning
RT_MAX = 250.0                  # Temps de réponse max (secondes) - DQN

# Coûts réseau
COST_BW_INTRA_DC = 0.002        # $/GB (intra-datacenter)
COST_BW_INTER_REGION = 0.05     # $/GB (inter-région)
COST_BW_INTER_CLOUD = 0.10      # $/GB (inter-cloud)
STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0  # $/GB/h
REPLICATION_COST_PER_GB = 0.10  # $/GB (coût de création)
CPU_COST_PER_HOUR = 0.02        # $/h

# Paramètres réseau
BW_LOCAL_GBPS = 10.0            # Bande passante locale (Gbps)
BW_REMOTE_GBPS = 1.0            # Bande passante distante (Gbps)
LAT_LOCAL_MS = 1.0              # Latence locale (ms)
LAT_REMOTE_MS = 100.0           # Latence distante (ms)

# Q-learning
ALPHA = 0.1                     # Taux d'apprentissage
GAMMA = 0.99                    # Facteur d'actualisation
EPSILON_START = 1.0             # Epsilon initial
EPSILON_MIN = 0.01              # Epsilon minimum
EPSILON_DECAY = 0.995           # Décroissance epsilon (multiplicatif)

# DQN
LEARNING_RATE = 0.0003          # Taux d'apprentissage Adam
BUFFER_CAPACITY = 50000         # Capacité replay buffer
BATCH_SIZE = 128                # Taille du batch
TARGET_UPDATE_FREQ = 20         # Fréquence mise à jour target (épisodes)
EPSILON_DECAY_LAMBDA = 0.0005   # Lambda décroissance exponentielle

# Anti-thrashing
THRASH_WINDOW = 5               # Fenêtre d'observation
MIN_REPLICA_AGE = 50            # Âge minimal avant suppression (requêtes)

# Popularité (PLSA)
N_TOPICS = 3                    # Nombre de topics PLSA
MAX_ITERATIONS = 20             # Itérations max PLSA
```

---

## ENTRAÎNEMENT

### Q-learning Tabulaire

```bash
python python_rl/train_simple_qlearning.py \
  --episodes 2000 \
  --lr 0.1 \
  --gamma 0.99 \
  --epsilon-start 1.0 \
  --epsilon-min 0.01 \
  --epsilon-decay 0.995 \
  --data-gb 5.3 \
  --output models/simple_qlearning.pkl
```

### Deep Q-Network

```bash
python python_rl/train_dqn_policy.py \
  --episodes 200 \
  --queries 1000 \
  --buffer-size 50000 \
  --batch-size 128 \
  --lr 0.0003 \
  --gamma 0.99 \
  --epsilon-start 1.0 \
  --epsilon-min 0.01 \
  --epsilon-decay 0.0005 \
  --target-update 20 \
  --output-dir results/dqn/
```

---

## MÉTRIQUES D'ÉVALUATION

### Métriques de Performance

| Métrique | Description | Objectif |
|----------|-------------|----------|
| **SLA Compliance Rate** | Taux de respect du SLA | Maximiser (> 95%) |
| **Average Latency** | Latence moyenne des requêtes | Minimiser (< 1000ms) |
| **Total Cost** | Coût total par épisode | Minimiser |
| **Budget Remaining** | Budget restant en fin d'épisode | Maximiser |
| **Replica Changes** | Nombre de changements de réplicas | Minimiser (stabilité) |

### Métriques d'Apprentissage

| Métrique | Description | Objectif |
|----------|-------------|----------|
| **Episode Reward** | Récompense totale par épisode | Maximiser |
| **Q-values Mean/Std** | Statistiques des Q-values | Convergence |
| **Exploration Rate** | % d'états explorés (Q-learning) | > 80% |
| **Loss** | Loss d'entraînement (DQN) | Convergence vers 0 |
| **Epsilon** | Valeur d'epsilon | Décroissance vers ε_min |

---

## COMPARAISON DES APPROCHES

| Aspect | Q-learning Tabulaire | Deep Q-Network |
|--------|---------------------|----------------|
| **Espace d'états** | 243 états discrets | ∞ états continus |
| **Représentation** | Q-table [243×3] | Réseau neuronal ~6K params |
| **Mémoire** | ~2 KB | ~24 KB + buffer |
| **Généralisation** | Aucune | Forte |
| **Temps d'entraînement** | ~2-5 min (2000 épisodes) | ~10-20 min (200 épisodes) |
| **Interprétabilité** | Haute (Q-table visible) | Faible (boîte noire) |
| **Scalabilité** | Limitée (explosion combinatoire) | Excellente |
| **Convergence** | Garantie (sous conditions) | Non garantie |

---

## RÉFÉRENCES

**Code source** :
- Q-learning : `python_rl/envs/tcdrm_qlearning_env.py` + `python_rl/agents/simple_qlearning_agent.py`
- DQN : `python_rl/envs/tcdrm_env_v2.py` + `python_rl/agents/dqn_agent.py`
- Entraînement : `python_rl/train_simple_qlearning.py` + `python_rl/train_dqn_policy.py`

**Documentation** :
- Q-learning : `Qlearning_TCDRM_v2.md`
- DQN : `DQN_TCDRM_v2.md`

---

**Date de création** : 29 janvier 2026  
**Version** : 1.0 - Implémentation réelle
