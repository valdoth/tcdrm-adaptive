# TCDRM-ADAPTIVE

**Adaptive Self-Learning Mechanism for Multi-Cloud Data Replication**

Double Q-Learning + Double DQN vs. TCDRM Static Thresholds

---

## Quick Start

```bash
# 1. Install Python dependencies
cd python_rl && uv sync && cd ..

# 2. Terminal 1: Start Java benchmark + Py4J gateway
mvn compile -q && mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.TcdrmMain"

# 3. Terminal 2: Start Python RL client
cd python_rl && uv run python connect_to_java.py
```

All graphs are generated in `images/`.

---

## Architecture

| Component  | Role                                    | Technology                 |
| ---------- | --------------------------------------- | -------------------------- |
| **Java**   | Benchmark simulation + graph generation | CloudSim + XChart + Py4J   |
| **Python** | RL model training + inference           | Gymnasium + PyTorch + Py4J |

### Java Structure

```
src/main/java/org/tcdrm/adaptive/
├── TcdrmMain.java                   # Entry point (Phase 1 + Phase 2)
├── benchmark/
│   ├── BenchmarkDataPerQuery.java   # Data record for all metrics
│   ├── NoRepBenchmarkPerQuery.java  # NoRep baseline
│   ├── TcdrmBenchmarkPerQuery.java  # TCDRM static benchmark
│   ├── RealRLBenchmark.java         # RL benchmark (calls Python via Py4J)
│   ├── QuerySimulator.java          # Query simulation engine
│   └── SingleModelMetricsPlotter.java
├── core/
│   └── TcdrmConstants.java          # All simulation parameters
├── gateway/
│   └── Py4JGateway.java             # Java-Python bridge server
├── rl/
│   └── PythonRLBridge.java          # Interface for Python RL models
├── runner/
│   └── BenchmarkRunner.java         # Benchmark orchestration
└── visualization/
    ├── ChartColors.java             # Color definitions
    ├── ChartUtils.java              # Chart utilities
    ├── PaperFigureGenerator.java    # Paper figures (TCDRM vs NoRep)
    └── RLFigureGenerator.java       # RL figures (4 models)
```

### Python Structure

```
python_rl/
├── connect_to_java.py               # Entry point
├── bridge/
│   ├── adaptive_strategy.py         # TCDRM-ADAPTIVE algorithms (A1, A3)
│   ├── rl_bridge.py                 # Model loading + Py4J interface
│   └── client.py                    # CLI + Java connection
├── agents/
│   ├── simple_qlearning_agent.py    # Double Q-Learning
│   └── dqn_agent.py                 # Double DQN + Dueling + PER
├── envs/
│   ├── tcdrm_qlearning_env.py       # Discrete env (243 states)
│   └── tcdrm_env_v2.py              # Continuous env (8 dims)
├── config/
│   └── constants.py                 # Constants (mirrors Java)
└── utils/
    ├── plsa_fast.py                 # PLSA popularity model
    ├── workload_generator.py        # Workload patterns
    └── tensorboard_callback.py      # Training callback
```

---

## Benchmarks

**Phase 1**: Paper reproduction (TCDRM vs NoRepLc) - 6 figures
**Phase 2**: RL extensions (4 models) - 6 figures + individual metrics

### 4 Models Compared

| Model          | Strategy                    | Replica Start |
| -------------- | --------------------------- | ------------- |
| **NoRepLc**    | No replication (baseline)   | Never         |
| **TCDRM**      | Fixed threshold (P_SLA=200) | q200          |
| **Q-Learning** | Adaptive (80% of P_SLA)     | q99           |
| **DQN**        | Aggressive (60% of P_SLA)   | q79           |

### TCDRM-ADAPTIVE Features

- **Adaptive thresholds**: Dynamic P_SLA adjustment based on SLA violations
- **Anti-thrashing (Algorithm A3)**: Prevents rapid create-delete cycles
- **Budget-aware deletion**: Removes replicas when budget low + popularity drops

---

## Training RL Models

```bash
cd python_rl

# Q-Learning (Double Q-Learning, 243 discrete states)
uv run python train_simple_qlearning.py --episodes 2000

# DQN (Double DQN + Dueling + PER, 8 continuous dims)
uv run python train_dqn_policy.py --episodes 1000
```

---

## Prerequisites

- **Python 3.11+** with `uv`
- **Java 17+**
- **Maven 3.8+**
