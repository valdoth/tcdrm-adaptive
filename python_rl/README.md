# TCDRM-ADAPTIVE: Module Python RL

**Double Q-Learning + Double DQN for adaptive cloud replication.**

Reference: TCDRM V1 Paper — Table 1 for all parameters.

---

## Installation

```bash
cd python_rl
uv sync
```

---

## Running Benchmarks

```bash
# Terminal 1: Start Java (compiles + launches Py4J gateway)
mvn compile -q && mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.TcdrmMain"

# Terminal 2: Start Python RL client
cd python_rl && uv run python connect_to_java.py
```

Graphs are generated in `images/`.

---

## Training RL Models

### Q-Learning (Tabular, Double Q-Learning)

```bash
uv run python train_simple_qlearning.py --episodes 2000
```

- **243 discrete states** (3^5: RT, COST, POP, BUD, NET)
- **3 actions**: NOOP, REPLICATE, DELETE
- Double Q-Learning + adaptive epsilon-greedy

### Dueling DQN (Double DQN)

```bash
uv run python train_dqn_policy.py --episodes 1000
```

- **8 continuous dimensions** (RT, cost, popularity, budget, traffic ratios, replication, trend)
- **Dueling architecture**: 64-64 shared + 32-neuron value/advantage streams
- Prioritized Experience Replay + soft target update

---

## Project Structure

```
python_rl/
├── connect_to_java.py                 # Entry point (Py4J client)
├── bridge/
│   ├── adaptive_strategy.py           # TCDRM-ADAPTIVE algorithms (A1, A3)
│   ├── rl_bridge.py                   # Model loading + Py4J interface
│   └── client.py                      # CLI client + Java connection
├── agents/
│   ├── simple_qlearning_agent.py      # Double Q-Learning agent
│   └── dqn_agent.py                   # Double DQN + Dueling + PER
├── envs/
│   ├── tcdrm_qlearning_env.py         # Discrete env for Q-Learning
│   └── tcdrm_env_v2.py                # Continuous env for DQN
├── config/
│   └── constants.py                   # Centralized constants (mirrors Java)
├── utils/
│   ├── plsa_fast.py                   # PLSA popularity model
│   ├── workload_generator.py          # Realistic workload patterns
│   └── tensorboard_callback.py        # Training callback
├── train_simple_qlearning.py          # Q-Learning training script
├── train_dqn_policy.py                # DQN training script
├── models/                            # Trained models (.pkl)
└── results/                           # Training results (.pt, .json)
```

---

## Centralized Constants

All constants in `config/constants.py` mirror `TcdrmConstants.java` on the Java side.

| Parameter              | Value  | Source        |
| ---------------------- | ------ | ------------- |
| T_SLA (simple)         | 200 ms | Paper Table 1 |
| T_SLA (complex)        | 400 ms | Paper Table 1 |
| C_SLA (simple)         | $0.015 | Paper Table 1 |
| C_SLA (complex)        | $0.040 | Paper Table 1 |
| P_SLA                  | 200    | Paper Table 1 |
| MAX_REPLICAS (simple)  | 6      | Paper Table 1 |
| MAX_REPLICAS (complex) | 12     | Paper Table 1 |
| Jitter (network)       | 10%    | Realistic sim |
| Jitter (CPU)           | 8%     | Realistic sim |

---

## TCDRM-ADAPTIVE Features

- **Adaptive thresholds**: Q-Learning starts at 80% of P_SLA, DQN at 60%
- **Anti-thrashing (Algorithm A3)**: Prevents rapid create-delete cycles
- **Budget-aware deletion**: Removes replicas when budget low + popularity drops
- **SLA-driven replication**: Uses T_SLA and C_SLA violation signals

**Benchmarks compared**: NOREP, TCDRM Static, Q-Learning RL, DQN RL
