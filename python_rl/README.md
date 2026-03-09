# TCDRM-ADAPTIVE: Module Python RL

**Entraînement des Modèles RL (Q-Learning + Dueling DQN) pour la réplication adaptative dans le cloud**

Référence: Article TCDRM V1 — Tableau 1 pour tous les paramètres.

---

## Installation

```bash
cd python_rl
uv sync
```

---

## Workflow Complet (Recommandé)

```bash
# Depuis la racine du projet
./run_complete_workflow.sh
```

Ce script enchaîne: validation → entraînement Python → compilation Java → simulation CloudSim → graphiques.

---

## Modèles RL

### Q-Learning Simple (Tabular)

```bash
cd python_rl
uv run python train_simple_qlearning.py --episodes 2000
```

- **243 états discrets** (3^5 : RT, COST, POP, BUD, NET)
- **3 actions** : NOOP, REPLICATE, DELETE
- Double Q-Learning + epsilon-greedy adaptatif

### Dueling DQN

```bash
cd python_rl
uv run python train_dqn_policy.py --episodes 1000
```

- **8 dimensions continues** (RT, cost, popularity, budget, traffic ratios, replication, trend)
- **Architecture Dueling** : 64-64 shared + 32-neuron value/advantage streams
- Prioritized Experience Replay + soft target update

---

## Structure des Fichiers

```
python_rl/
├── config/
│   ├── constants.py                   # TcdrmConstants (source unique de vérité)
│   └── optimized_config.json          # Hyperparamètres optimisés
├── agents/
│   ├── simple_qlearning_agent.py      # Agent Q-Learning (Double Q, epsilon adaptatif)
│   ├── simple_qlearning_wrapper.py    # Wrapper Py4J pour Java bridge
│   └── dqn_agent.py                   # Agent Dueling DQN + PER
├── envs/
│   ├── __init__.py                    # Enregistrement Gymnasium
│   ├── tcdrm_qlearning_env.py         # Env discret pour Q-Learning
│   └── tcdrm_env_v2.py                # Env continu pour DQN
├── utils/
│   ├── plsa_fast.py                   # PLSA optimisé avec cache
│   ├── workload_generator.py          # Générateur de charges réalistes (11 patterns)
│   └── tensorboard_callback.py        # Callback TensorBoard
├── train_simple_qlearning.py          # Script d'entraînement Q-Learning
├── train_dqn_policy.py                # Script d'entraînement DQN
├── connect_to_java.py                 # Bridge Py4J pour simulations Java
├── optimize_hyperparameters.py        # Optimisation des hyperparamètres
└── models/                            # Modèles entraînés (.pkl, .pt)
```

---

## Constantes Centralisées

Toutes les constantes sont définies dans `config/constants.py` (`TcdrmConstants`), miroir de
`src/main/java/org/tcdrm/adaptive/core/TcdrmConstants.java` côté Java.

| Paramètre              | Valeur | Source            |
| ---------------------- | ------ | ----------------- |
| TSLA                   | 200 ms | Article Tableau 1 |
| CSLA                   | $0.015 | Article Tableau 1 |
| MAX_REPLICAS (simple)  | 5      | Article Tableau 1 |
| MAX_REPLICAS (complex) | 13     | Article Tableau 1 |
| COST_BW_INTRA_DC       | $0.002 | Article Tableau 1 |
| COST_BW_INTER_PROVIDER | $0.01  | Article Tableau 1 |
| Warm-up                | 600 q  | Sigmoid k=5       |

---

## Simulations Java (CloudSim + Py4J)

Les simulations réalistes se font en Java via CloudSim. Les modèles Python sont
appelés en temps réel via Py4J (`RealRLBenchmark`).

```bash
# Depuis la racine du projet
mvn clean package
./run_complete_workflow.sh
```

**Benchmarks comparés** :

- **NOREP** — Pas de réplication (baseline)
- **TCDRM Statique** — Réplication à seuil fixe (P_SLA = 200)
- **Q-Learning RL** — Décisions Python via Py4J
- **DQN RL** — Décisions Python via Py4J

---

**Python pour l'entraînement, Java pour la validation.**
