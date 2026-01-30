# Modèle Q-learning Tabulaire pour TCDRM v2

Cette section présente l'implémentation réelle du modèle **Q-learning tabulaire** utilisé dans **TCDRM v2**.
Le problème est modélisé comme un **Processus de Décision Markovien (MDP)** :

⟨S, A, P, R, γ⟩

où  
S = espace des états (243 états discrets)  
A = espace des actions (3 actions)  
P = probabilité de transition  
R = fonction de récompense  
γ = facteur d'actualisation

**Implémentation**: `python_rl/envs/tcdrm_qlearning_env.py` et `python_rl/agents/simple_qlearning_agent.py`

---

## 1. Espace des états (State Space S)

Un état représente la **situation du système observée au moment de la décision de réplication**.

Un état est défini comme un vecteur discret à 5 dimensions :

**s = (RT, COST, POP, BUD, NET)**

Chaque dimension prend 3 valeurs possibles, donnant **243 états au total** (3^5).

### RT – État du temps de réponse

| Valeur | Signification                 |
| ------ | ----------------------------- |
| RT0    | Temps de réponse satisfaisant |
| RT1    | Temps proche de la limite SLA |
| RT2    | Violation du SLA              |

**Discrétisation dynamique** (basée sur statistiques adaptatives) :

- **RT0** : tQ ≤ μRT (satisfaisant)
- **RT1** : μRT < tQ ≤ μRT + σRT (proche limite)
- **RT2** : tQ > μRT + σRT (violation)

Où μRT et σRT sont calculés dynamiquement sur les 100 dernières requêtes.
Initialisation : μRT = 100ms (LAT_REMOTE_MS), σRT = 50ms

---

### COST – État du coût de la requête

| Valeur | Signification |
| ------ | ------------- |
| C0     | Coût faible   |
| C1     | Coût modéré   |
| C2     | Coût excessif |

**Discrétisation** (basée sur coût moyen normalisé) :

- **C0** : cQ_norm ≤ 0.7 × CSLA (faible)
- **C1** : 0.7 × CSLA < cQ_norm ≤ CSLA (modéré)
- **C2** : cQ_norm > CSLA (excessif)

Où cQ_norm = total_cost / max(1, current_query) et CSLA = 1.0

---

### POP – État de popularité des données

| Valeur | Signification                    |
| ------ | -------------------------------- |
| P0     | Faible popularité (< 0.33)       |
| P1     | Popularité moyenne (0.33 - 0.67) |
| P2     | Forte popularité (≥ 0.67)        |

**Prédiction** : Modèle PLSA (Probabilistic Latent Semantic Analysis) avec 3 topics

---

### BUD – État du budget résiduel du locataire

| Valeur | Signification      |
| ------ | ------------------ |
| B0     | Budget confortable |
| B1     | Budget tendu       |
| B2     | Budget critique    |

**Discrétisation** (ratio par rapport au budget initial) :

- **B0** : budget_ratio ≥ 0.6 (confortable)
- **B1** : 0.3 ≤ budget_ratio < 0.6 (tendu)
- **B2** : budget_ratio < 0.3 (critique)

Où budget_ratio = current_budget / INITIAL_BUDGET  
INITIAL_BUDGET = 1000.0

---

### NET – Type dominant de trafic réseau

| Valeur | Signification         | Condition    |
| ------ | --------------------- | ------------ |
| N0     | Intra-région (local)  | replicas ≥ 2 |
| N1     | Inter-région (mixte)  | replicas = 1 |
| N2     | Inter-cloud (distant) | replicas = 0 |

**Implémentation simplifiée** basée sur le nombre de réplicas actifs

---

### Taille de l’espace d’état

|S| = 3 × 3 × 3 × 3 × 3 = 243 états

Ce volume est **compatible avec Q-learning tabulaire**.

---

## 2. Espace des actions (Action Space A)

L’agent choisit une action à chaque décision :

**A = {0: NOOP, 1: REPLICATE, 2: DELETE}**

| Action    | Code | Description              |
| --------- | ---- | ------------------------ |
| NOOP      | 0    | Ne rien faire            |
| REPLICATE | 1    | Créer un nouveau réplica |
| DELETE    | 2    | Supprimer un réplica     |

### Contraintes d'actions (Action Masking)

- **REPLICATE interdit si** :
  - Budget état = B2 (critique)
  - OU replicas ≥ MAX_REPLICAS (3)
  - OU budget < coût_réplication

- **DELETE interdit si** :
  - replicas = 0

**Coût de réplication** : data_gb × REPLICATION_COST_PER_GB (0.10 $/GB)

---

## 3. Fonction de récompense (Reward Function R)

La récompense est **simplifiée et basée sur la réduction de latence** pour encourager l'apprentissage.

### Formule implémentée

```python
reward = latency_reward - repl_penalty + repl_bonus - budget_penalty - thrash_penalty
```

### Composantes détaillées

| Composante         | Calcul                                      | Valeur                      |
| ------------------ | ------------------------------------------- | --------------------------- |
| **latency_reward** | 10.0 × (1 - latency_norm)                   | [0, 10]                     |
| **repl_penalty**   | Si REPLICATE exécuté                        | -0.5                        |
| **repl_bonus**     | Si REPLICATE et latency > 2000ms            | +2.0 × min(1, latency/5000) |
| **budget_penalty** | Si budget < 30%                             | -5.0                        |
| **thrash_penalty** | Si ≥2 alternances REPL/DEL sur fenêtre de 5 | -1.0                        |

**Normalisation latence** : latency_norm = min(1.0, query_latency / 10000.0)

### Objectifs

1. **Minimiser la latence** (reward principal)
2. **Encourager réplication stratégique** (bonus si latence élevée)
3. **Pénaliser dépassement budget** (pénalité forte)
4. **Éviter le thrashing** (pénalité légère)

---

## 4. Algorithme Q-learning

### 4.1. Mise à jour de la Q-table

**Équation de Bellman** :

```
Q(s,a) ← Q(s,a) + α [r + γ · max_a' Q(s',a') - Q(s,a)]
```

Où :

- **α** (learning_rate) : 0.1 (par défaut)
- **γ** (discount_factor) : 0.99 (par défaut)
- **Q-table** : Matrice [243 × 3] initialisée à zéro

### 4.2. Politique d'exploration ε-greedy

**Décroissance multiplicative** :

```
ε_t = max(ε_min, ε_{t-1} × decay)
```

Paramètres par défaut :

- **ε_start** : 1.0 (exploration totale)
- **ε_min** : 0.01 (exploration minimale)
- **decay** : 0.995 (décroissance par épisode)

### 4.3. Sélection d'action

```python
if random() < ε:
    action = random_choice(valid_actions)  # Exploration
else:
    action = argmax(Q[state, valid_actions])  # Exploitation
```

---

## 5. Paramètres du système

### Environnement

| Paramètre      | Valeur    | Description                    |
| -------------- | --------- | ------------------------------ |
| MAX_QUERIES    | 1000      | Nombre de requêtes par épisode |
| INITIAL_BUDGET | 1000.0    | Budget initial du locataire    |
| MAX_REPLICAS   | 3         | Nombre maximum de réplicas     |
| TSLA_BASE      | 1000.0 ms | Temps de réponse SLA de base   |

### Coûts réseau

| Type                   | Coût            | Description                |
| ---------------------- | --------------- | -------------------------- |
| COST_BW_INTRA_DC       | 0.002 $/GB      | Transfert intra-datacenter |
| COST_BW_INTER_PROVIDER | 0.10 $/GB       | Transfert inter-cloud      |
| STORAGE_COST           | 0.02/720 $/GB/h | Stockage par heure         |
| REPLICATION_COST       | 0.10 $/GB       | Coût de création réplica   |

### Paramètres réseau

| Paramètre       | Local     | Distant  |
| --------------- | --------- | -------- |
| Bande passante  | 10.0 Gbps | 1.0 Gbps |
| Latence de base | 1.0 ms    | 100.0 ms |

## 6. Entraînement

### Script d'entraînement

```bash
python python_rl/train_simple_qlearning.py \
  --episodes 2000 \
  --lr 0.1 \
  --gamma 0.99 \
  --epsilon-start 1.0 \
  --epsilon-min 0.01 \
  --epsilon-decay 0.995
```

### Métriques suivies

- **episode_rewards** : Récompense totale par épisode
- **episode_lengths** : Nombre de steps par épisode
- **sla_rates** : Taux de conformité SLA
- **exploration_rate** : Pourcentage d'états explorés
- **Q-values statistics** : Moyenne, écart-type, min, max

## 7. Avantages et limitations

### Avantages

- **Simple et interprétable** : Q-table explicite
- **Convergence garantie** : Sous conditions standards
- **Faible complexité** : 243 états gérables
- **Pas de GPU requis** : Calculs légers

### Limitations

- **Espace d'états limité** : 243 états seulement
- **Discrétisation** : Perte d'information continue
- **Scalabilité** : Difficile d'ajouter des dimensions
- **Généralisation** : Limitée aux états vus

---

**Pour des environnements plus complexes, voir le modèle DQN** → `DQN_TCDRM_v2.md`
