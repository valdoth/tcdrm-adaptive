# TCDRM-ADAPTIVE : Spécification Technique

## Objectif Scientifique

Transformer TCDRM d'un modèle à seuils statiques vers un framework adaptatif, capable d'apprendre automatiquement les décisions de réplication et de suppression de réplicas tout en respectant le budget du locataire.

## Problématique

Comment remplacer des seuils SLA fixes par des politiques de décision auto-apprenantes afin d'améliorer la performance et la stabilité budgétaire dans un environnement multi-cloud dynamique?

---

## 1. Modélisation MDP (Markov Decision Process)

### 1.1 États (Observation Space)

L'état du système à chaque instant est représenté par un vecteur de 8 dimensions :

```python
observation_space = spaces.Box(
    low=np.array([0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]),
    high=np.array([1.0, 1000.0, 1.0, 5.0, 1.0, 1.0, 1.0, 1.0]),
    dtype=np.float32
)
```

| Dimension | Description | Plage | Normalisation |
|-----------|-------------|-------|---------------|
| `budget_ratio` | Budget restant / Budget initial | [0, 1] | Oui |
| `avg_latency` | Latence moyenne des N dernières requêtes | [0, 500] ms | Non |
| `query_count_norm` | Nombre de requêtes / MAX_QUERIES | [0, 1] | Oui |
| `current_replicas` | Nombre de réplicas actuels | [0, MAX_REPLICAS] | Non |
| `data_size_norm` | Taille des données / 20 GB | [0, 1] | Oui |
| `sla_violation_rate` | Violations SLA / Total requêtes | [0, 1] | Oui |
| `cost_rate` | Coût cumulé / Budget initial | [0, 1] | Oui |
| `popularity` | Taux d'accès récent | [0, 1] | Oui |

### 1.2 Actions (Action Space)

L'agent peut choisir parmi 3 actions discrètes :

```python
action_space = spaces.Discrete(3)
```

| Action | ID | Description | Contraintes |
|--------|----|-----------|--------------|
| `CREATE_REPLICA` | 0 | Créer un nouveau réplica | `current_replicas < MAX_REPLICAS` ET `budget >= replication_cost` |
| `DELETE_REPLICA` | 1 | Supprimer un réplica existant | `current_replicas > 0` |
| `DO_NOTHING` | 2 | Maintenir l'état actuel | Aucune |

### 1.3 Fonction de Récompense (Multi-objectif)

La récompense combine plusieurs objectifs :

```python
reward = (
    + α * sla_compliance_reward      # Respect du SLA
    - β * cost_penalty                # Minimiser les coûts
    + γ * budget_efficiency_reward    # Efficacité budgétaire
    - δ * instability_penalty         # Stabilité des décisions
    + ε * strategic_timing_reward     # Timing stratégique
)
```

#### Composantes de la récompense

**1. SLA Compliance Reward (α = 10.0)**
```python
if query_latency <= SLA_THRESHOLD:
    sla_compliance_reward = +10.0
else:
    sla_compliance_reward = -20.0 * (query_latency / SLA_THRESHOLD - 1)
```

**2. Cost Penalty (β = 5.0)**
```python
cost_penalty = query_cost * 5.0
if action == CREATE_REPLICA:
    cost_penalty += replication_cost * 2.0
```

**3. Budget Efficiency Reward (γ = 15.0)**
```python
if budget_ratio > 0.5:
    budget_efficiency_reward = +15.0
elif budget_ratio > 0.2:
    budget_efficiency_reward = +5.0
else:
    budget_efficiency_reward = -10.0
```

**4. Instability Penalty (δ = 8.0)**
```python
# Pénaliser les changements fréquents de réplicas
if action in [CREATE_REPLICA, DELETE_REPLICA]:
    if last_action_was_different:
        instability_penalty = 8.0
```

**5. Strategic Timing Reward (ε = 20.0)**
```python
# Récompenser la création au bon moment (popularité élevée)
if action == CREATE_REPLICA and popularity > 0.7:
    strategic_timing_reward = +20.0
elif action == CREATE_REPLICA and popularity < 0.3:
    strategic_timing_reward = -15.0
```

---

## 2. Apprentissage Adaptatif des Seuils

### 2.1 Seuils Dynamiques

Au lieu de seuils fixes (TSLA=200, CSLA, PSLA), l'agent apprend :

| Seuil | TCDRM Statique | TCDRM-ADAPTIVE |
|-------|----------------|----------------|
| **TSLA** (Popularité) | 200 requêtes (fixe) | Appris dynamiquement basé sur `popularity`, `budget_ratio`, `query_count` |
| **CSLA** (Coût) | Fixe | Adapté selon `cost_rate` et `budget_ratio` |
| **PSLA** (Performance) | Fixe | Adapté selon `avg_latency` et `sla_violation_rate` |

### 2.2 Mécanisme d'Apprentissage

L'agent RL apprend une **politique π(s) → a** qui mappe chaque état vers l'action optimale, remplaçant les règles if-then de TCDRM Statique.

**Exemple de décision apprise :**
```
SI (popularity > 0.7 ET budget_ratio > 0.5 ET current_replicas < 3)
ALORS CREATE_REPLICA
SINON SI (popularity < 0.3 ET current_replicas > 1)
ALORS DELETE_REPLICA
SINON DO_NOTHING
```

---

## 3. Algorithmes d'Apprentissage

### 3.1 Q-Learning Tabulaire (Phase 1)

**Avantages :**
- Simple à implémenter
- Interprétable
- Pas de GPU nécessaire

**Limitations :**
- Espace d'état discret limité
- Ne scale pas pour grands espaces

**Implémentation :**
```python
Q[state, action] ← Q[state, action] + α * (reward + γ * max_a' Q[next_state, a'] - Q[state, action])
```

### 3.2 Deep Q-Network (DQN) (Phase 2)

**Avantages :**
- Gère espaces d'état continus
- Meilleure généralisation
- Scalable

**Architecture réseau :**
```
Input (8) → Dense(64, ReLU) → Dense(64, ReLU) → Dense(3, Linear)
```

### 3.3 Proximal Policy Optimization (PPO) (Phase 3)

**Avantages :**
- Stable et robuste
- Policy-based (exploration naturelle)
- State-of-the-art pour RL

---

## 4. Intégration CloudSim + Gymnasium

### 4.1 Architecture

```
Python (Gymnasium)  ←→  Py4J  ←→  Java (CloudSim Plus)
```

### 4.2 Flux d'exécution

1. **Python** : `env.reset()` → Initialise l'environnement
2. **Java** : Crée simulation CloudSim
3. **Python** : `env.step(action)` → Envoie action via Py4J
4. **Java** : Exécute action, simule requête, calcule métriques
5. **Java** : Retourne `(observation, reward, done, info)` à Python
6. **Python** : Agent RL met à jour politique
7. Répéter 3-6 jusqu'à fin d'épisode

### 4.3 Modifications nécessaires

**Python (`tcdrm_env.py`) :**
- Ajouter support Py4J pour communiquer avec Java
- Modifier `step()` pour appeler Java au lieu de simuler localement
- Adapter observation/reward pour être cohérent avec CloudSim

**Java (nouveau package `org.tcdrm.adaptive.rl`) :**
- Créer `RLGatewayServer` (Py4J)
- Créer `TcdrmSimulationManager` (gère CloudSim)
- Créer `ReplicationController` (exécute actions RL)

---

## 5. Validation Expérimentale

### 5.1 Métriques de Comparaison

| Métrique | Description | Objectif |
|----------|-------------|----------|
| **Temps de réponse moyen** | Latence moyenne sur toutes les requêtes | Minimiser |
| **Coût total** | Coût cumulé (BW + CPU + Storage) | Minimiser |
| **Taux de violation SLA** | % de requêtes > SLA_THRESHOLD | Minimiser |
| **Stabilité budgétaire** | Variance du budget restant | Maximiser |
| **Nombre de changements de réplicas** | Fréquence CREATE/DELETE | Minimiser |

### 5.2 Scénarios de Test

1. **Workload stable** : Popularité constante
2. **Workload variable** : Pics et creux de popularité
3. **Budget contraint** : Budget initial faible
4. **Données volumineuses** : 20+ GB

### 5.3 Baselines

- **NOREP** : Pas de réplication
- **TCDRM Statique** : Seuils fixes (TSLA=200)
- **TCDRM-ADAPTIVE** : Seuils appris par RL

---

## 6. Résultats Attendus

### 6.1 Amélioration de la Stabilité

- **Réduction des changements de réplicas** : -30% vs TCDRM Statique
- **Moins de violations SLA** : -20% vs TCDRM Statique

### 6.2 Réduction des Coûts

- **Coûts inutiles** : -25% (réplicas créés au bon moment)
- **Efficacité budgétaire** : +15% (meilleure utilisation du budget)

### 6.3 Adaptabilité

- **Workloads variables** : Performance stable même avec pics imprévisibles
- **Différentes tailles de données** : Politique adaptée automatiquement

---

## 7. Roadmap d'Implémentation

### Phase 1 : Fondations (Semaine 1-2)
- [ ] Créer document de spécification (ce fichier)
- [ ] Adapter environnement Gymnasium pour Py4J
- [ ] Implémenter Q-Learning tabulaire
- [ ] Tests unitaires

### Phase 2 : Intégration CloudSim (Semaine 3-4)
- [ ] Créer RLGatewayServer Java
- [ ] Intégrer TcdrmSimulationManager
- [ ] Tests d'intégration Python-Java
- [ ] Validation métriques

### Phase 3 : Entraînement et Évaluation (Semaine 5-6)
- [ ] Entraîner Q-Learning sur workloads variés
- [ ] Comparer vs NOREP et TCDRM Statique
- [ ] Générer graphes et analyses
- [ ] Rédiger rapport

### Phase 4 : Extensions (Optionnel)
- [ ] Implémenter DQN
- [ ] Implémenter PPO
- [ ] Multi-agent (plusieurs datacenters)
- [ ] Apprentissage en ligne

---

## 8. Références

- **Article TCDRM** : Threshold-based Cost-aware Data Replication Management
- **CloudSim Plus** : https://github.com/cloudsimplus/cloudsimplus
- **Gymnasium** : https://gymnasium.farama.org/
- **Stable-Baselines3** : https://stable-baselines3.readthedocs.io/
- **Repo référence** : https://github.com/FCBayern1/rl-cloudsimplus-greenscheduling
