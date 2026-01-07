# 🧠 TCDRM-ADAPTIVE : Explication des Techniques d'Apprentissage Automatique

**Date** : 7 janvier 2026  
**Objectif** : Transformer TCDRM d'un modèle à seuils statiques vers un framework adaptatif auto-apprenant

---

## 📚 Vue d'ensemble des techniques mobilisées

Pour implémenter TCDRM-ADAPTIVE, nous allons utiliser **5 techniques principales** d'apprentissage automatique et d'optimisation :

1. **Apprentissage par Renforcement (Reinforcement Learning)**
2. **Q-Learning Tabulaire**
3. **Processus de Décision Markovien (MDP)**
4. **Optimisation sous Contraintes**
5. **Apprentissage en Ligne (Online Learning)**

---

## 1️⃣ Apprentissage par Renforcement (RL)

### **Concept de base**

L'apprentissage par renforcement est un paradigme d'apprentissage automatique où un **agent** apprend à prendre des **décisions optimales** en interagissant avec un **environnement**.

```
┌─────────────────────────────────────────────┐
│                                             │
│   Agent (TCDRM)                             │
│   ↓ Action (créer/supprimer réplica)       │
│   ↓                                         │
│   Environnement (Multi-Cloud)               │
│   ↓ État (budget, latence, popularité)     │
│   ↓ Récompense (économies, performance)    │
│   ↑                                         │
│   Agent apprend la meilleure politique      │
│                                             │
└─────────────────────────────────────────────┘
```

### **Composants clés**

| Composant               | Description                           | Exemple TCDRM                                   |
| ----------------------- | ------------------------------------- | ----------------------------------------------- |
| **Agent**               | Système qui prend les décisions       | TCDRM-ADAPTIVE                                  |
| **Environnement**       | Contexte dans lequel l'agent opère    | Infrastructure multi-cloud                      |
| **État (State)**        | Situation actuelle de l'environnement | Budget restant, latence, popularité             |
| **Action**              | Décision prise par l'agent            | Créer réplica, Supprimer réplica, Ne rien faire |
| **Récompense (Reward)** | Feedback de l'environnement           | Économies réalisées, Performance améliorée      |
| **Politique (Policy)**  | Stratégie de décision de l'agent      | Quand créer/supprimer un réplica                |

### **Pourquoi RL pour TCDRM ?**

✅ **Décisions séquentielles** : Chaque requête est une étape de décision  
✅ **Feedback retardé** : Les bénéfices de la réplication apparaissent après plusieurs requêtes  
✅ **Exploration vs Exploitation** : Équilibre entre tester de nouvelles stratégies et utiliser ce qui fonctionne  
✅ **Adaptation dynamique** : Apprend des patterns de trafic changeants

---

## 2️⃣ Q-Learning Tabulaire

### **Concept de base**

Q-Learning est un algorithme d'apprentissage par renforcement **sans modèle** (model-free) qui apprend la **valeur** de chaque action dans chaque état.

### **La Q-Table**

La Q-Table est une **table de référence** qui stocke la valeur estimée de chaque paire (état, action).

```
┌─────────────────────────────────────────────────────────────┐
│                    Q-TABLE TCDRM                            │
├──────────────────┬──────────────┬──────────────┬────────────┤
│ État             │ Créer Réplica│ Supprimer    │ Ne rien    │
│                  │              │ Réplica      │ faire      │
├──────────────────┼──────────────┼──────────────┼────────────┤
│ Budget: Élevé    │              │              │            │
│ Latence: Haute   │    +15.2     │    -5.0      │   +2.1     │
│ Popularité: 150  │              │              │            │
├──────────────────┼──────────────┼──────────────┼────────────┤
│ Budget: Faible   │              │              │            │
│ Latence: Haute   │    -10.5     │    +8.3      │   +3.2     │
│ Popularité: 250  │              │              │            │
├──────────────────┼──────────────┼──────────────┼────────────┤
│ Budget: Élevé    │              │              │            │
│ Latence: Basse   │    +5.0      │    +2.0      │   +7.5     │
│ Popularité: 50   │              │              │            │
└──────────────────┴──────────────┴──────────────┴────────────┘
```

### **Formule de mise à jour Q-Learning**

```
Q(s, a) ← Q(s, a) + α × [r + γ × max Q(s', a') - Q(s, a)]
                         └─────────────────────┘
                              TD Target
```

**Où** :

- `Q(s, a)` : Valeur actuelle de l'action `a` dans l'état `s`
- `α` (alpha) : **Taux d'apprentissage** (0.1 - 0.3) → Vitesse d'adaptation
- `r` : **Récompense immédiate** reçue après l'action
- `γ` (gamma) : **Facteur de discount** (0.9 - 0.99) → Importance du futur
- `max Q(s', a')` : Meilleure valeur dans le prochain état `s'`

### **Algorithme Q-Learning pour TCDRM**

```python
# Pseudo-code simplifié
def q_learning_tcdrm():
    # 1. Initialiser Q-table à zéro
    Q = initialize_q_table()

    for episode in range(num_episodes):
        state = get_initial_state()  # Budget, latence, popularité

        for query in range(max_queries):
            # 2. Choisir action avec epsilon-greedy
            if random() < epsilon:
                action = random_action()  # Exploration
            else:
                action = argmax(Q[state])  # Exploitation

            # 3. Exécuter l'action
            next_state, reward = execute_action(state, action)

            # 4. Mettre à jour Q-table
            Q[state][action] += alpha * (
                reward + gamma * max(Q[next_state]) - Q[state][action]
            )

            state = next_state

            # 5. Décrémenter epsilon (moins d'exploration au fil du temps)
            epsilon *= decay_rate

    return Q  # Politique optimale apprise
```

### **Stratégie Epsilon-Greedy**

Balance entre **exploration** (tester de nouvelles actions) et **exploitation** (utiliser les meilleures actions connues).

```
┌─────────────────────────────────────────────┐
│  Début de l'apprentissage (ε = 1.0)         │
│  ┌─────────────────────────────────────┐    │
│  │ 100% Exploration (actions aléatoires)│    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Milieu de l'apprentissage (ε = 0.5)        │
│  ┌──────────────────┬──────────────────┐    │
│  │ 50% Exploration  │ 50% Exploitation │    │
│  └──────────────────┴──────────────────┘    │
│                                             │
│  Fin de l'apprentissage (ε = 0.1)           │
│  ┌───┬─────────────────────────────────┐    │
│  │10%│ 90% Exploitation (best actions) │    │
│  └───┴─────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

---

## 3️⃣ Processus de Décision Markovien (MDP)

### **Concept de base**

Un MDP est un **modèle mathématique** pour la prise de décision séquentielle dans un environnement stochastique.

### **Composants d'un MDP**

```
MDP = (S, A, P, R, γ)
```

| Composant       | Symbole | Description                                 | Exemple TCDRM                                                                            |
| --------------- | ------- | ------------------------------------------- | ---------------------------------------------------------------------------------------- |
| **États**       | S       | Ensemble de toutes les situations possibles | {Budget: [Faible, Moyen, Élevé], Latence: [Basse, Moyenne, Haute], Popularité: [0-1000]} |
| **Actions**     | A       | Ensemble de toutes les décisions possibles  | {Créer réplica, Supprimer réplica, Ne rien faire}                                        |
| **Transitions** | P       | Probabilité de passer d'un état à un autre  | P(s'│s, a) = probabilité d'aller à s' depuis s en faisant a                              |
| **Récompenses** | R       | Feedback numérique après chaque action      | R(s, a) = économies - pénalités                                                          |
| **Discount**    | γ       | Importance des récompenses futures          | γ = 0.95 (95% d'importance au futur)                                                     |

### **Propriété de Markov**

> "Le futur ne dépend que du présent, pas du passé"

```
P(s_{t+1} | s_t, a_t, s_{t-1}, ..., s_0) = P(s_{t+1} | s_t, a_t)
```

**Pour TCDRM** : La décision de réplication dépend uniquement de l'état actuel (budget, latence, popularité), pas de l'historique complet.

### **Modélisation TCDRM comme MDP**

#### **États (S)**

```java
class TcdrmState {
    // Discrétisation des variables continues
    BudgetLevel budget;        // LOW, MEDIUM, HIGH
    LatencyLevel latency;      // LOW, MEDIUM, HIGH
    PopularityLevel popularity; // LOW, MEDIUM, HIGH
    int replicaCount;          // 0, 1, 2, 3
}

// Nombre total d'états = 3 × 3 × 3 × 4 = 108 états
```

#### **Actions (A)**

```java
enum ReplicationAction {
    CREATE_REPLICA,    // Créer un nouveau réplica
    DELETE_REPLICA,    // Supprimer un réplica existant
    DO_NOTHING         // Maintenir l'état actuel
}
```

#### **Fonction de récompense (R)**

```java
double calculateReward(State s, Action a, State nextState) {
    double reward = 0.0;

    // Récompense pour économies de bande passante
    if (a == CREATE_REPLICA && nextState.latency < s.latency) {
        reward += 10.0 * (s.latency - nextState.latency);
    }

    // Pénalité pour dépassement de budget
    if (nextState.budget < BUDGET_THRESHOLD) {
        reward -= 50.0;  // Forte pénalité
    }

    // Récompense pour respect du SLA
    if (nextState.latency < SLA_THRESHOLD) {
        reward += 5.0;
    }

    // Pénalité pour coût de création
    if (a == CREATE_REPLICA) {
        reward -= replicationCost;
    }

    // Récompense pour suppression si inutile
    if (a == DELETE_REPLICA && s.popularity < POPULARITY_THRESHOLD) {
        reward += storageSavings;
    }

    return reward;
}
```

---

## 4️⃣ Optimisation sous Contraintes

### **Concept de base**

L'optimisation sous contraintes consiste à **maximiser** (ou minimiser) une fonction objectif tout en **respectant** des contraintes strictes.

### **Formulation pour TCDRM**

```
Maximiser : Performance (réduction latence, disponibilité)

Sous contraintes :
    1. Budget total ≤ Budget_max (contrainte budgétaire)
    2. Latence ≤ SLA_latence (contrainte de performance)
    3. Nombre de réplicas ≤ Max_replicas (contrainte de ressources)
    4. Coût_stockage + Coût_transfert ≤ Budget_restant
```

### **Intégration dans la fonction de récompense**

```java
double calculateConstrainedReward(State s, Action a, State nextState) {
    double reward = baseReward(s, a, nextState);

    // Pénalités exponentielles pour violations de contraintes
    if (nextState.totalCost > maxBudget) {
        double violation = nextState.totalCost - maxBudget;
        reward -= 100.0 * Math.exp(violation / maxBudget);  // Pénalité exponentielle
    }

    if (nextState.latency > slaLatency) {
        double violation = nextState.latency - slaLatency;
        reward -= 50.0 * violation;
    }

    if (nextState.replicaCount > maxReplicas) {
        reward -= 200.0;  // Violation stricte
    }

    return reward;
}
```

### **Méthode de Lagrange (optionnel, pour extension)**

Pour des contraintes plus complexes, on peut utiliser les **multiplicateurs de Lagrange** :

```
L(π, λ) = Performance(π) + λ₁(Budget_max - Budget_utilisé)
                         + λ₂(SLA_latence - Latence_actuelle)
```

---

## 5️⃣ Apprentissage en Ligne (Online Learning)

### **Concept de base**

L'apprentissage en ligne permet au système d'**apprendre continuellement** à partir de nouvelles données, sans réentraînement complet.

### **Différence avec apprentissage batch**

```
┌─────────────────────────────────────────────────────────────┐
│  APPRENTISSAGE BATCH (Traditionnel)                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Collecter    │→ │ Entraîner    │→ │ Déployer     │      │
│  │ données      │  │ modèle       │  │ modèle fixe  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                             │
│  APPRENTISSAGE EN LIGNE (TCDRM-ADAPTIVE)                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Requête → Décision → Feedback → Mise à jour Q-table │  │
│  │    ↑                                            ↓     │  │
│  │    └────────────────────────────────────────────┘     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### **Avantages pour TCDRM**

✅ **Adaptation continue** : S'adapte aux changements de patterns de trafic  
✅ **Pas de réentraînement** : Mise à jour incrémentale de la Q-table  
✅ **Réactivité** : Réagit rapidement aux nouvelles conditions  
✅ **Efficacité mémoire** : Pas besoin de stocker tout l'historique

### **Implémentation dans TCDRM**

```java
public class OnlineQLearningTCDRM {
    private QTable qTable;
    private double alpha = 0.1;      // Taux d'apprentissage
    private double gamma = 0.95;     // Facteur de discount
    private double epsilon = 1.0;    // Exploration initiale
    private double epsilonDecay = 0.995;

    public void processQuery(Query query) {
        // 1. Observer l'état actuel
        State currentState = observeState(query);

        // 2. Choisir action (epsilon-greedy)
        Action action = chooseAction(currentState, epsilon);

        // 3. Exécuter l'action
        ExecutionResult result = executeAction(action, query);

        // 4. Observer nouvel état et récompense
        State nextState = result.getNextState();
        double reward = result.getReward();

        // 5. Mise à jour en ligne de la Q-table
        updateQTableOnline(currentState, action, reward, nextState);

        // 6. Décrémenter epsilon
        epsilon *= epsilonDecay;
    }

    private void updateQTableOnline(State s, Action a, double r, State nextS) {
        double currentQ = qTable.get(s, a);
        double maxNextQ = qTable.getMaxQ(nextS);

        // Mise à jour incrémentale
        double newQ = currentQ + alpha * (r + gamma * maxNextQ - currentQ);
        qTable.set(s, a, newQ);
    }
}
```

---

## 🎯 Synthèse : Comment tout s'articule

```
┌─────────────────────────────────────────────────────────────────┐
│                    TCDRM-ADAPTIVE PIPELINE                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. MODÉLISATION MDP                                            │
│     ↓ Définir États, Actions, Récompenses                      │
│                                                                 │
│  2. Q-LEARNING TABULAIRE                                        │
│     ↓ Initialiser Q-table, Algorithme d'apprentissage          │
│                                                                 │
│  3. OPTIMISATION SOUS CONTRAINTES                               │
│     ↓ Fonction de récompense avec pénalités                    │
│                                                                 │
│  4. APPRENTISSAGE EN LIGNE                                      │
│     ↓ Mise à jour continue à chaque requête                    │
│                                                                 │
│  5. APPRENTISSAGE PAR RENFORCEMENT                              │
│     ↓ Agent apprend la politique optimale                      │
│                                                                 │
│  RÉSULTAT : Décisions adaptatives de réplication               │
│             sans seuils fixes (PSLA, TSLA, CSLA)                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Comparaison TCDRM vs TCDRM-ADAPTIVE

| Aspect           | TCDRM (Actuel)          | TCDRM-ADAPTIVE (Futur)          |
| ---------------- | ----------------------- | ------------------------------- |
| **Décision**     | Seuils fixes (PSLA=200) | Politique apprise dynamiquement |
| **Adaptation**   | Aucune                  | Continue via Q-learning         |
| **Optimisation** | Heuristique statique    | Optimisation sous contraintes   |
| **Réactivité**   | Lente (seuils fixes)    | Rapide (apprentissage en ligne) |
| **Performance**  | Bonne mais rigide       | Optimale et adaptative          |
| **Complexité**   | Faible                  | Moyenne (Q-table)               |

---

## 🚀 Prochaines étapes d'implémentation

1. **Définir l'espace d'états** : Discrétiser budget, latence, popularité
2. **Définir les actions** : Créer, Supprimer, Ne rien faire
3. **Implémenter la fonction de récompense** : Économies - Pénalités
4. **Créer la Q-table** : Structure de données pour stocker les valeurs
5. **Implémenter Q-learning** : Algorithme d'apprentissage
6. **Intégrer dans CloudSim** : Simulation multi-cloud
7. **Valider expérimentalement** : Comparer TCDRM vs TCDRM-ADAPTIVE

---

**Fin du document**
