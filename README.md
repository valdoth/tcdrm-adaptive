# TCDRM-ADAPTIVE

Adaptive, budget-aware multi-cloud data replication with RL (Q-Learning, DQN) vs. a static TCDRM baseline.

---

## Quick Start (one command)

```bash
# From repository root
bash run_complete_workflow.sh --episodes 50
```

What it does:

- Trains Q-Learning and DQN agents using CloudSimPlus simulations (can be skipped).
- Compiles Java and runs the full benchmark (NoRepLc, TCDRM, Q-Learning, DQN).
- Generates graphs under `images/` and CSVs under `metrics/`.

Useful options:

- `--skip-training` to reuse models in `tcdrm_gym/models/`.
- `--skip-compile` or `--skip-simulation` for partial runs.
- `--episodes N` to change RL training length.

---

## Outputs

- Graphs: `images/*.png` (paper-style figures and RL comparisons)
- CSV per model: `metrics/*_{simple,complex}.csv`
- Overtime aggregates: `metrics/log_overtime.csv`
- Global summaries: `metrics/summary_phase1.csv`, `metrics/summary_phase2_rl.csv`

Note: Reference P_SLA guide lines are not drawn in charts; popularity is tracked internally.

---

## Architecture Overview

| Component | Role                                     | Technology               |
| --------- | ---------------------------------------- | ------------------------ |
| Java      | Simulation, metrics, charts, Py4J bridge | CloudSim Plus, XChart    |
| Python    | RL training + online inference           | Gymnasium, PyTorch, Py4J |

### Java layout

```
src/main/java/org/tcdrm/adaptive/
├── TcdrmMain.java                    # Entry point (Phase 1 + Phase 2)
├── benchmark/
│   ├── BenchmarkRunner.java          # Orchestrates NoRep, TCDRM, RL runs
│   ├── BenchmarkData.java            # Per-query metrics container
│   ├── BenchmarkExporter.java        # CSV exports (per-query, summaries)
│   └── ChartGenerator.java           # Paper and RL figures
├── cloudsim/
│   ├── MultiCloudInfrastructure.java # Multi-cloud CloudSim Plus setup
│   ├── DataFragment.java             # Relation/fragment + replica state
│   └── QueryCloudlet.java            # Query execution (transfer + join)
├── data/
│   ├── LegacyWorkloadTemplates.java  # Simple/Complex query patterns (legacy-like)
│   └── WorkloadGenerator.java        # Synthetic/popularity-driven workloads
├── gateway/Py4JGateway.java          # Java ↔ Python bridge server
├── rl/PythonRLBridge.java            # Bridge methods called by Java
├── simulation/TcdrmSimulation.java   # NoRep, TCDRM baseline, RL actions
└── training/TrainingServer.java      # CloudSim-based training service
```

### Python layout

```
tcdrm_gym/
├── connect_to_java.py                # Online inference client (Py4J)
├── train_cloudsim.py                 # RL training over CloudSimPlus
├── agents/                           # Q-Learning, DQN implementations
├── envs/                             # Gym-style bridge wrappers
├── utils/                            # Helpers (callbacks, etc.)
└── models/                           # Saved models (created by the workflow)
```

---

## Benchmarks & Workloads

- Phase 1 (paper-style): NoRepLc vs. TCDRM.
- Phase 2 (RL): Q-Learning + DQN in online decision mode.

Workload patterns (aligned with the legacy project):

- Simple: ~3 relations per query (one per provider) executed from EU.
- Complex: ~6 relations per query across regions to stress inter-region/provider transfers.

---

## Requirements

- Java 17+
- Maven 3.9+
- Python 3.11+ and `uv` (https://github.com/astral-sh/uv)

The workflow script will call `uv run` inside `tcdrm_gym/` for training and online inference.

---

## Tips

- Skip retraining for faster iteration:
  ```bash
  bash run_complete_workflow.sh --skip-training --episodes 50
  ```
- After a run, list outputs:
  ```bash
  ls -1 images/*.png
  ls -1 metrics/*.csv
  ```
