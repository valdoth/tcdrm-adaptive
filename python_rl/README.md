# TCDRM-ADAPTIVE: Module Python RL

**Double Q-Learning + Double DQN for adaptive cloud replication.**

Training uses CloudSimPlus (Java) for simulations, ensuring training/inference consistency.

---

## Installation

```bash
cd python_rl
uv sync
```

---

## Training with CloudSimPlus

```bash
# Terminal 1: Start Java TrainingServer
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.training.TrainingServer"

# Terminal 2: Train Q-Learning
uv run python train_cloudsim.py --agent qlearning --episodes 100

# Train DQN
uv run python train_cloudsim.py --agent dqn --episodes 100
```

---

## Running Benchmarks

```bash
# Complete workflow (train + benchmark)
./run_complete_workflow.sh

# Or manually:
# Terminal 1: Start Java
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.TcdrmMain"

# Terminal 2: Start Python client
cd python_rl && uv run python connect_to_java.py
```

Graphs are generated in `images/`.

---

## Project Structure

```
python_rl/
├── connect_to_java.py          # Entry point (Py4J client)
├── train_cloudsim.py           # Training with CloudSimPlus
├── bridge/
│   ├── rl_bridge.py            # Model loading + Py4J interface
│   ├── client.py               # CLI client + Java connection
│   └── adaptive_strategy.py    # Anti-thrashing state
├── agents/
│   ├── simple_qlearning_agent.py  # Double Q-Learning agent
│   └── dqn_agent.py               # Double DQN + Dueling + PER
├── envs/
│   └── cloudsim_env.py         # CloudSimPlus Gymnasium env
└── models/                     # Trained models
```

---

## Models

### Q-Learning (243 discrete states)

- **5 dimensions**: RT, COST, POP, BUD, NET (3 bins each)
- **3 actions**: NOOP, REPLICATE, DELETE
- Double Q-Learning + adaptive epsilon-greedy

### DQN (8 continuous dimensions)

- latency, budget, replicas, popularity, cost, t_sla_violation, c_sla_violation, progress
- Dueling architecture + Prioritized Experience Replay

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Python (Gymnasium)          │  Java (CloudSimPlus)         │
│  ─────────────────           │  ────────────────────        │
│  • train_cloudsim.py         │  • TrainingServer.java       │
│  • cloudsim_env.py           │  • TrainingEnvironment.java  │
│  • Q-Learning/DQN agents     │  • TcdrmSimulation.java      │
│                              │                               │
│         ◄──── Py4J (port 25335) ────►                       │
└─────────────────────────────────────────────────────────────┘
```
