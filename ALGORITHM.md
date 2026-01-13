# 📘 Algorithme TCDRM-ADAPTIVE (Q-Learning) avec Reinforcement Learning

## 🎯 Vue d'Ensemble

**TCDRM-ADAPTIVE (Q-Learning)** est une extension du TCDRM statique qui utilise le **Q-Learning** pour apprendre dynamiquement la stratégie optimale de gestion des réplicas basée sur:

- **CPU Utilization** (40-70% optimal)
- **Budget du tenant**
- **Latence (SLA)**
- **Coûts de réplication**

---

## 📋 Algorithme Principal

### **Phase 1: Entraînement (Curriculum Learning)**

```
ALGORITHME: TrainTCDRM_Adaptive_QLearning
ENTRÉE: seed (pour reproductibilité)
SORTIE: Agent Q-Learning entraîné

1. INITIALISER agent Q-Learning:
   - α (learning rate) = 0.1
   - γ (discount factor) = 0.95
   - ε (exploration) = 1.0
   - ε_decay = 0.995
   - ε_min = 0.01

2. PHASE 1 - Petites requêtes (1-5 GB):
   POUR 800 épisodes FAIRE:
      a. Sélectionner requête aléatoire (1-5 GB)
      b. Créer environnement TcdrmEnvironmentV2(dataGb)
      c. état ← env.reset()
      d. TANT QUE non terminé FAIRE:
         - action ← agent.chooseAction(état, ε)
         - résultat ← env.step(action)
         - agent.updateQTable(état, action, récompense, état_suivant)
         - état ← état_suivant
      e. Décrémenter ε ← ε × ε_decay (min: ε_min)

3. PHASE 2 - Moyennes requêtes (5-10 GB):
   POUR 800 épisodes FAIRE:
      [Même processus avec requêtes 5-10 GB]

4. PHASE 3 - Grandes requêtes (10-20 GB):
   POUR 400 épisodes FAIRE:
      [Même processus avec requêtes 10-20 GB]

5. RETOURNER agent entraîné
```

---

### **Phase 2: Exécution (Inférence)**

```
ALGORITHME: SimulateTCDRM_Adaptive_QLearning
ENTRÉE: agent entraîné, dataGb, seed
SORTIE: Résultats de simulation (coûts, latences, réplicas)

1. INITIALISER environnement:
   - env ← TcdrmEnvironmentV2(dataGb)
   - état ← env.reset(seed)
   - budget_initial ← BASE_BUDGET + (dataGb × BUDGET_PER_GB)

2. POUR chaque requête q de 0 à 1999 FAIRE:

   a. Sélectionner meilleure action (exploitation pure):
      action ← agent.getBestAction(état)

   b. Exécuter action dans l'environnement:
      résultat ← env.step(action)

   c. Enregistrer métriques:
      - récompense[q] ← résultat.reward
      - budget[q] ← env.getCurrentBudget()
      - latence[q] ← env.getCurrentLatency()
      - replicas[q] ← env.getCurrentReplicaCount()
      - cpu[q] ← env.getCurrentCpuUtilization()

   d. Mettre à jour état:
      état ← résultat.nextState

3. CALCULER coût total:
   coût_total ← budget_initial - budget_final

4. RETOURNER résultats
```

---

## 🔧 Environnement TCDRM (Q-Learning)

### **Espace d'États**

```
État = (budget_ratio, latency, access_count, replica_count)

Discrétisation:
- budget_ratio: [0-0.2, 0.2-0.4, 0.4-0.6, 0.6-0.8, 0.8-1.0] → 5 bins
- latency: [0-50, 50-100, 100-150, 150+] → 4 bins
- access_count: [0-100, 100-500, 500+] → 3 bins
- replica_count: [0, 1, 2, 3] → 4 valeurs

Total états: 5 × 4 × 3 × 4 = 240 états discrets
```

### **Espace d'Actions**

```
Actions = {CREATE_REPLICA, DELETE_REPLICA, DO_NOTHING}
```

### **Fonction step(action)**

```
FONCTION: step(action)
ENTRÉE: action ∈ {CREATE, DELETE, DO_NOTHING}
SORTIE: (état_suivant, récompense, terminé, info)

1. Sauvegarder replica_count_précédent

2. Exécuter action:
   SI action = CREATE_REPLICA ET replicas < MAX_REPLICAS:
      coût_création ← dataGb × COST_BW_INTER_PROVIDER
      SI budget ≥ coût_création:
         replicas ← replicas + 1
         budget ← budget - coût_création
         action_exécutée ← VRAI

   SI action = DELETE_REPLICA ET replicas > 0:
      replicas ← replicas - 1
      action_exécutée ← VRAI

3. Simuler requête:
   SI replicas > 0:
      prob_local ← replicas / (replicas + 2)
      SI random() < prob_local:
         latence ← LAT_LOCAL_MS (1ms) + bruit
      SINON:
         latence ← LAT_REMOTE_MS (100ms) + bruit
   SINON:
      latence ← LAT_REMOTE_MS (100ms) + bruit

4. Calculer coût de la requête:
   coût_transfert ← dataGb × (local ? COST_INTRA : COST_INTER)
   coût_stockage ← replicas × dataGb × STORAGE_COST
   coût_requête ← coût_transfert + coût_stockage

5. Calculer CPU utilization:
   SI replicas = 0: cpu ← 70-90% (inefficace)
   SI replicas = 1: cpu ← 50-70% (optimal)
   SI replicas = 2: cpu ← 40-60% (bon)
   SI replicas ≥ 3: cpu ← 20-40% (sous-utilisé)

6. Mettre à jour état:
   budget ← budget - coût_requête
   query_count ← query_count + 1

7. Calculer récompense Q-Learning (basée sur CPU):
   récompense ← calculateReward_QLearning(action, cpu, budget, latence, coût)

8. Vérifier terminaison:
   terminé ← (query_count ≥ MAX_QUERIES OU budget ≤ 0)

9. RETOURNER (état_suivant, récompense, terminé, info)
```

---

## 🎁 Fonction de Récompense (Q-Learning - Basée sur CPU)

```
FONCTION: calculateReward_QLearning(action, cpu, budget, latence, coût)
SORTIE: récompense ∈ ℝ

récompense ← 0

// ===== PÉNALITÉS CPU (SLA VIOLATIONS) =====
SI cpu > 80%:
   violation ← (cpu - 80) / 20
   récompense ← récompense - 50 × violation

SI cpu < 40%:
   inefficacité ← (40 - cpu) / 40
   récompense ← récompense - 30 × inefficacité

SI 40% ≤ cpu ≤ 70%:
   récompense ← récompense + 10  // CPU optimal

// ===== PÉNALITÉS DE COÛT =====
récompense ← récompense - coût × 2.0

budget_ratio ← budget / budget_initial
SI budget_ratio < 0.2:
   récompense ← récompense - 40
SINON SI budget_ratio < 0.4:
   récompense ← récompense - 15

SI budget ≤ 0:
   récompense ← récompense - 200

// ===== RÉCOMPENSES POUR RÉPLICATION EFFICACE =====
SI 1 ≤ replicas ≤ 2 ET 40% ≤ cpu ≤ 70%:
   récompense ← récompense + 15  // Réplication efficace

SI replicas > 0 ET latence < LAT_REMOTE_MS:
   économies ← dataGb × (COST_INTER - COST_INTRA)
   récompense ← récompense + économies × 12

SI action = CREATE ET cpu > 70%:
   récompense ← récompense + 10  // Création justifiée

SI action = DELETE ET cpu < 40%:
   récompense ← récompense + 12  // Suppression justifiée

// ===== PÉNALITÉS POUR MAUVAISES ACTIONS =====
SI action = CREATE ET cpu < 50%:
   récompense ← récompense - 15  // Création inutile

SI replicas > 2:
   récompense ← récompense - 8 × (replicas - 2)

SI latence > SLA_THRESHOLD:
   violation ← (latence - SLA) / SLA
   récompense ← récompense - 12 × violation

// ===== BONUS POUR BONNE GESTION =====
SI budget_ratio > 0.5 ET latence < SLA ET 40% ≤ cpu ≤ 80%:
   récompense ← récompense + 8

RETOURNER récompense
```

---

## 📊 Paramètres Clés

### Environnement

```java
MAX_QUERIES = 2000
BASE_BUDGET = 150.0
BUDGET_PER_GB = 10.0
SLA_LATENCY_THRESHOLD = 150.0 ms
MAX_REPLICAS = 3
```

### Coûts

```java
COST_BW_INTRA_DC = 0.002 $/GB
COST_BW_INTER_PROVIDER = 0.10 $/GB
STORAGE_COST_PER_GB_PER_HOUR = 0.02/720 $/GB/h
REPLICATION_COST_PER_GB = 0.10 $/GB
```

### Réseau

```java
BW_LOCAL_GBPS = 10.0
BW_REMOTE_GBPS = 1.0
LAT_LOCAL_MS = 1.0
LAT_REMOTE_MS = 100.0
```

### Q-Learning

```java
α (learning rate) = 0.1
γ (discount factor) = 0.95
ε_initial = 1.0
ε_decay = 0.995
ε_min = 0.001
```

---

## 🔄 Différences: TCDRM Statique vs TCDRM-ADAPTIVE (Q-Learning)

| Aspect           | **TCDRM Statique**        | **TCDRM-ADAPTIVE (Q-Learning)**  |     |
| ---------------- | ------------------------- | -------------------------------- | --- |
| **Décision**     | Seuil fixe (200 requêtes) | Apprentissage dynamique          |
| **Réplicas**     | Tous créés en une fois    | Création/suppression progressive |
| **Critères**     | Popularité uniquement     | CPU + Budget + Latence + Coût    |
| **Adaptation**   | Non                       | Oui (s'adapte aux conditions)    |
| **Optimisation** | Règle simple              | Optimisation multi-objectifs     |

---

## 📈 Résultats Expérimentaux

### Performance Comparative (2000 requêtes)

#### Requête R1 (Simple)

| Métrique               | TCDRM Statique | NOREP    | TCDRM-ADAPTIVE (Q-Learning) |
| ---------------------- | -------------- | -------- | --------------------------- |
| **Coût Total**         | $498.29        | $1114.77 | $572.80                     |
| **Économies vs NOREP** | +55.3%         | -        | +48.6%                      |
| **vs TCDRM**           | -              | -55.3%   | -15.0%                      |

#### Requête R2 (Complexe)

| Métrique               | TCDRM Statique | NOREP    | TCDRM-ADAPTIVE (Q-Learning) |
| ---------------------- | -------------- | -------- | --------------------------- |
| **Coût Total**         | $1186.55       | $2502.97 | $1262.05                    |
| **Économies vs NOREP** | +52.6%         | -        | +49.6%                      |
| **vs TCDRM**           | -              | -52.6%   | -6.4%                       |

### Observations

- ✅ TCDRM-ADAPTIVE (Q-Learning) réduit les coûts de **~50%** par rapport à NOREP
- ✅ Performance proche de TCDRM statique (-6% à -15%)
- ✅ Adaptation dynamique aux conditions changeantes
- ✅ Gestion intelligente basée sur CPU, budget et latence

---

## 🎯 Avantages de TCDRM-ADAPTIVE (Q-Learning)

1. **Adaptation Dynamique**: S'adapte aux conditions changeantes (charge, budget, latence)
2. **Optimisation Multi-Objectifs**: Balance CPU, coût, latence et budget simultanément
3. **Apprentissage Progressif**: Curriculum learning sur différentes tailles de requêtes
4. **Gestion Intelligente**: Crée/supprime des réplicas au bon moment
5. **Respect du SLA**: Pénalise fortement les violations de CPU et latence

---
