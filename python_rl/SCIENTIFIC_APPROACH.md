# TCDRM-ADAPTIVE v2.0: Approche Scientifique

**Conception d'un mécanisme adaptatif et auto-apprenant pour la réplication de bases de données orientée budget en environnement multi-cloud**

---

## 📋 Contexte et Motivation

### Problème de TCDRM v1.0

TCDRM (Tenant Cost-Driven Replication Management) v1.0 utilise des **seuils statiques** pour décider de la réplication:

- **TSLA** (Threshold SLA): Seuil de latence
- **CSLA** (Cost SLA): Seuil de coût
- **PSLA** (Popularity SLA): Seuil de popularité

**Limitations**:

- ❌ Seuils fixes ne s'adaptent pas aux patterns dynamiques
- ❌ Décisions sous-optimales dans des contextes changeants
- ❌ Coûts inutiles liés à des réplicas non nécessaires
- ❌ Violations SLA dans des situations imprévues

### Solution Proposée: TCDRM-ADAPTIVE v2.0

Remplacer les seuils statiques par un **mécanisme adaptatif auto-apprenant** basé sur l'apprentissage par renforcement.

---

## 🎯 Objectif Scientifique

**Transformer TCDRM d'un modèle à seuils statiques vers un framework adaptatif, capable d'apprendre automatiquement les décisions de réplication et de suppression de réplicas tout en respectant le budget du locataire.**

### Question de Recherche

**Comment remplacer des seuils SLA fixes par des politiques de décision auto-apprenantes afin d'améliorer la performance et la stabilité budgétaire dans un environnement multi-cloud dynamique?**

---

## 🔬 Axes de Travail

### 1. Modélisation du Processus de Décision

**Approche**: Modéliser la réplication comme un **Processus de Décision Markovien (MDP)**

```
MDP = (S, A, P, R, γ)
```

Où:

- **S**: Espace d'états (budget, latence, popularité, réplicas)
- **A**: Espace d'actions (CREATE, DELETE, DO_NOTHING)
- **P**: Probabilités de transition (dynamique de l'environnement)
- **R**: Fonction de récompense (coût + SLA + budget)
- **γ**: Facteur de discount (importance du futur)

### 2. Définition des États, Actions et Récompenses

#### États (S)

Espace d'états discret: **108 états** (3×3×3×4)

```python
État = (Budget_Level, Latency_Level, Popularity_Level, Num_Replicas)
```

- **Budget_Level** ∈ {LOW, MEDIUM, HIGH}
  - LOW: < 20% du budget initial
  - MEDIUM: 20-60%
  - HIGH: > 60%

- **Latency_Level** ∈ {LOW, MEDIUM, HIGH}
  - LOW: < 50ms
  - MEDIUM: 50-150ms
  - HIGH: > 150ms (violation SLA)

- **Popularity_Level** ∈ {LOW, MEDIUM, HIGH}
  - LOW: < 150 accès
  - MEDIUM: 150-300 accès
  - HIGH: > 300 accès

- **Num_Replicas** ∈ {0, 1, 2, 3}

#### Actions (A)

Espace d'actions: **3 actions**

```python
A = {CREATE_REPLICA, DELETE_REPLICA, DO_NOTHING}
```

- **CREATE_REPLICA (0)**: Créer un nouveau réplica
  - Coût: data_gb × REPLICATION_COST
  - Effet: Réduit la latence, augmente les coûts de stockage

- **DELETE_REPLICA (1)**: Supprimer un réplica existant
  - Coût: 0
  - Effet: Augmente la latence, réduit les coûts de stockage

- **DO_NOTHING (2)**: Maintenir l'état actuel
  - Coût: 0
  - Effet: Aucun changement

#### Fonction de Récompense (R)

```python
R(s, a) = Bonus_SLA + Économies_BW - Pénalité_Budget - Pénalité_Latence - Pénalité_Action
```

**Composantes**:

1. **Bonus SLA** (+5.0): Si latence < 150ms
2. **Économies Bande Passante** (+savings × 10): Si accès local vs distant
3. **Pénalité Budget**:
   - -20.0 si budget < 20% (critique)
   - -100.0 si budget épuisé
4. **Pénalité Latence** (-10.0 × violation): Si latence > 150ms
5. **Pénalité Action** (-5.0): Si action non exécutable

### 3. Intégration Q-Learning Tabulaire

**Algorithme**: Q-Learning avec ε-greedy exploration

#### Équation de Bellman

```
Q(s, a) ← Q(s, a) + α[r + γ max Q(s', a') - Q(s, a)]
                                    a'
```

Où:

- **α** (alpha): Taux d'apprentissage = 0.1
- **γ** (gamma): Facteur de discount = 0.95
- **ε** (epsilon): Taux d'exploration = 1.0 → 0.01

#### Politique d'Exploration (ε-greedy)

```python
π(s) = {
    action_aléatoire    avec probabilité ε
    argmax Q(s, a)      avec probabilité 1-ε
         a
}
```

#### Décroissance de l'Exploration

```python
ε_t = max(ε_min, ε_start × ε_decay^t)
```

- ε_start = 1.0 (exploration maximale au début)
- ε_decay = 0.995 (décroissance lente)
- ε_min = 0.01 (exploration minimale maintenue)

### 4. Ajustement Dynamique des Décisions

**Remplacement des seuils statiques**:

| TCDRM v1.0 (Statique)            | TCDRM v2.0 (Adaptatif)       |
| -------------------------------- | ---------------------------- |
| IF latency > TSLA THEN CREATE    | Q(s, CREATE) > Q(s, NOTHING) |
| IF cost > CSLA THEN DELETE       | Q(s, DELETE) > Q(s, NOTHING) |
| IF popularity < PSLA THEN DELETE | Politique apprise π\*(s)     |

**Avantages**:

- ✅ Adaptation aux patterns d'accès
- ✅ Optimisation multi-objectifs (coût + SLA)
- ✅ Apprentissage continu
- ✅ Décisions contextuelles

### 5. Validation Expérimentale

#### Protocole Expérimental

**Phase 1: Entraînement**

```bash
# Entraîner sur 1000 épisodes
uv run python train.py --episodes 1000 --data-gb 5.3
```

**Phase 2: Évaluation**

```bash
# Évaluer sur 100 épisodes
uv run python evaluate_model.py --model models/best_model.pkl --episodes 100
```

**Phase 3: Comparaison**

```bash
# Comparer avec Q-Learning Java (baseline)
uv run python compare_with_java.py --java-log ../logs/qlearning_training.log
```

#### Métriques d'Évaluation

1. **Récompense Moyenne** (↑)
   - Mesure la performance globale
   - Objectif: Maximiser

2. **Coût Total** (↓)
   - Réplication + Stockage + Transfert
   - Objectif: Minimiser

3. **Conformité SLA** (↑)
   - % d'épisodes avec latence < 150ms
   - Objectif: > 95%

4. **Stabilité Budgétaire** (↓)
   - Variance du budget restant
   - Objectif: Minimiser

5. **Convergence** (↑)
   - Stabilité de la politique apprise
   - Objectif: Convergence rapide

#### Scénarios de Test

| Scénario | Taille Données | Complexité  | Objectif            |
| -------- | -------------- | ----------- | ------------------- |
| **R1**   | 5.3 GB         | Simple      | Validation baseline |
| **R2**   | 11.9 GB        | Complexe    | Test robustesse     |
| **R3**   | 20 GB          | Volumineuse | Test scalabilité    |

---

## 🛠️ Techniques Mobilisées

### Apprentissage par Renforcement

**Q-Learning Tabulaire**:

- ✅ Adapté aux espaces d'états discrets (108 états)
- ✅ Convergence garantie sous conditions
- ✅ Interprétabilité de la Q-Table
- ✅ Pas de dépendance aux réseaux de neurones

**Pourquoi pas Deep RL (DQN, PPO, A2C)?**

- Espace d'états petit (108) → Q-Table suffisante
- Besoin d'interprétabilité pour validation scientifique
- Convergence plus rapide avec Q-Learning tabulaire
- Moins de hyperparamètres à ajuster

### Processus de Décision Markovien (MDP)

**Propriété de Markov**:

```
P(s_{t+1} | s_t, a_t, s_{t-1}, ..., s_0) = P(s_{t+1} | s_t, a_t)
```

L'état actuel contient toute l'information nécessaire pour prédire le futur.

**Horizon Fini**: Chaque épisode = 1000 requêtes max

### Optimisation sous Contraintes

**Contraintes**:

1. Budget > 0 (contrainte dure)
2. Latence < 150ms (contrainte SLA)
3. Réplicas ≤ 3 (contrainte physique)

**Approche**: Intégration des contraintes dans la fonction de récompense via pénalités.

### Apprentissage en Ligne

**Mise à jour incrémentale**:

- Pas de batch training
- Apprentissage à chaque transition (s, a, r, s')
- Adaptation continue aux changements

### Simulation Multi-Cloud

**Environnement Gymnasium**:

- Simulation des coûts de bande passante (intra-DC vs inter-provider)
- Simulation des latences (locale vs distante)
- Simulation des coûts de stockage
- Intégration CloudSim via Py4J (optionnel)

---

## 📊 Résultats Attendus

### Hypothèses à Valider

**H1**: Q-Learning surpasse les seuils statiques en termes de coût

```
Coût_QL < Coût_Statique
```

**H2**: Q-Learning maintient une meilleure conformité SLA

```
SLA_QL ≥ SLA_Statique
```

**H3**: Q-Learning s'adapte aux changements de patterns

```
Variance_QL < Variance_Statique
```

**H4**: Q-Learning converge vers une politique stable

```
∃ N : ∀ t > N, ||Q_t - Q_{t+1}|| < ε
```

### Contributions Scientifiques

1. **Modélisation MDP** du problème de réplication multi-cloud
2. **Fonction de récompense** intégrant coût, SLA et budget
3. **Politique adaptative** remplaçant les seuils statiques
4. **Validation expérimentale** sur CloudSim
5. **Framework open-source** reproductible

### Impact Attendu

- **Réduction des coûts**: 15-30% vs seuils statiques
- **Amélioration SLA**: +5-10% de conformité
- **Stabilité**: Réduction de la variance budgétaire
- **Adaptabilité**: Convergence en < 500 épisodes

---

## 🔗 Intégration avec CloudSim

### Architecture Python-Java (Py4J)

```
┌─────────────────────────────────────┐
│   Python Q-Learning Agent           │
│   - Q-Table (108 × 3)               │
│   - ε-greedy policy                 │
│   - Bellman update                  │
└──────────────┬──────────────────────┘
               │ Py4J (TCP)
               ▼
┌─────────────────────────────────────┐
│   Java Py4JGateway                  │
│   - Expose TcdrmEnvironment         │
│   - Bridge Python ↔ Java            │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   TcdrmEnvironment (Java)           │
│   - Simulation CloudSim             │
│   - Calcul coûts réels              │
│   - Métriques détaillées            │
└─────────────────────────────────────┘
```

### Avantages de l'Intégration

✅ **Validation réaliste** avec CloudSim  
✅ **Compatibilité** avec code TCDRM existant  
✅ **Métriques détaillées** (CPU, RAM, réseau)  
✅ **Flexibilité** Python pour l'apprentissage

---

## 📚 Références Théoriques

### Apprentissage par Renforcement

- Sutton & Barto (2018). _Reinforcement Learning: An Introduction_
- Watkins & Dayan (1992). _Q-learning_

### Cloud Computing

- Buyya et al. (2011). _CloudSim: A toolkit for modeling and simulation of cloud computing environments_
- Calheiros et al. (2014). _CloudSim Plus_

### Réplication de Données

- Saito & Shapiro (2005). _Optimistic replication_
- Vogels (2009). _Eventually consistent_

---

## 🎓 Publications Visées

### Conférences

- **IEEE International Conference on Cloud Computing (CLOUD)**
- **ACM Symposium on Cloud Computing (SoCC)**
- **IEEE International Conference on Services Computing (SCC)**

### Journaux

- **IEEE Transactions on Cloud Computing**
- **ACM Transactions on Internet Technology**
- **Journal of Cloud Computing**

### Contributions Clés pour Publication

1. Modélisation MDP du problème de réplication multi-cloud
2. Comparaison empirique Q-Learning vs seuils statiques
3. Analyse de convergence et stabilité
4. Framework open-source reproductible

---

## ✅ Checklist de Validation

### Implémentation

- [x] Environnement Gymnasium TCDRM
- [x] Agent Q-Learning tabulaire
- [x] Fonction de récompense multi-objectifs
- [x] Scripts d'entraînement et évaluation
- [x] Intégration CloudSim (Py4J)
- [x] Visualisations et métriques

### Expérimentation

- [ ] Entraîner sur R1 (5.3 GB)
- [ ] Entraîner sur R2 (11.9 GB)
- [ ] Entraîner sur R3 (20 GB)
- [ ] Évaluer convergence
- [ ] Comparer avec baseline Java
- [ ] Analyser distribution des actions

### Analyse

- [ ] Valider H1 (coût)
- [ ] Valider H2 (SLA)
- [ ] Valider H3 (adaptation)
- [ ] Valider H4 (convergence)
- [ ] Tests statistiques (t-test, ANOVA)
- [ ] Visualisations scientifiques

### Documentation

- [x] README scientifique
- [x] Documentation technique
- [x] Guide d'utilisation
- [ ] Article scientifique
- [ ] Présentation résultats

---

## 🚀 Prochaines Étapes

1. **Exécuter les expériences**:

   ```bash
   ./run_experiments.sh all
   ```

2. **Analyser les résultats**:
   - Courbes d'apprentissage
   - Heatmap Q-Table
   - Distribution des actions

3. **Comparer avec baseline**:
   - Q-Learning Java existant
   - Seuils statiques TCDRM v1.0

4. **Rédiger l'article**:
   - Introduction et motivation
   - Modélisation MDP
   - Résultats expérimentaux
   - Conclusion et perspectives

---

**TCDRM-ADAPTIVE v2.0 - Vers des décisions de réplication intelligentes et adaptatives! 🎯**
