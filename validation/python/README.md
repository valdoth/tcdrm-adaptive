# Python Client for Validation

This folder provides a minimal Python environment to run the RL client that connects to the Java shaded JAR via Py4J.

## Setup with `uv`

```bash
cd validation/python
uv sync
```

## Run the client

```bash
# Default port 25333 (can be overridden by TCDRM_PY4J_PORT)
uv run python connect_to_java.py --port 25333 \
  --qlearning-model ../tcdrm_gym/models/qlearning_cloudsim.pkl \
  --dqn-model       ../tcdrm_gym/models/dqn_cloudsim.pt
```

The client imports the bridge from `tcdrm_gym/` in the repository root to ensure parity with training.
