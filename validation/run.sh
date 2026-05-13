#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Log file for this run
LOG_FILE="run.log"
exec > >(tee -a "$LOG_FILE") 2>&1
echo "========================================"
echo "Validation run started at $(date)"
echo "========================================"

# Single unified runners now execute simple + complex in sequence.
# Scenario arg kept for backward-compat but ignored.
SCENARIO="${1:-both}"

# --- Helpers ---------------------------------------------------------------
export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$PATH"

ensure_uv() {
  if command -v uv >/dev/null 2>&1; then
    return 0
  fi
  echo "\n🛠  Installing 'uv' (Python package manager)..."
  if command -v curl >/dev/null 2>&1; then
    (curl -LsSf https://astral.sh/uv/install.sh | sh) || true
    hash -r || true
  fi
  if ! command -v uv >/dev/null 2>&1; then
    echo "⚠️  Couldn't install 'uv' automatically. Will fallback to venv+pip."
    return 1
  fi
  echo "✅ 'uv' installed: $(uv --version)"
}

start_python_client_with_uv() {
  echo "🟡 Starting Python gateway via uv..."
  # Free default Py4J callback port 25334 if occupied
  (lsof -ti:25334 | xargs -r kill -9) >/dev/null 2>&1 || true
  (cd python && uv sync && nohup uv run python connect_to_java.py --port 25333 \
      >/tmp/tcdrm_py4j.log 2>&1 &)
}

start_python_client_with_venv() {
  echo "🟡 Starting Python gateway via venv+pip..."
  (
    cd python
    (lsof -ti:25334 | xargs -r kill -9) >/dev/null 2>&1 || true
    python3 -m venv .venv || python -m venv .venv
    . .venv/bin/activate
    python -m pip install -U pip >/dev/null 2>&1 || true
    # Minimal deps for Py4J client
    python -m pip install py4j >/dev/null 2>&1 || true
    # Launch client
    nohup .venv/bin/python connect_to_java.py --port 25333 \
      >/tmp/tcdrm_py4j.log 2>&1 &
  )
}

wait_for_port() {
  local port="$1"; local timeout="$2"; local i
  for i in $(seq 1 "$timeout"); do
    if lsof -i:"$port" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# Rebuild shaded JAR and copy locally to validation/lib
if [ -f "../pom.xml" ]; then
  echo "Building shaded JAR (mvn -DskipTests package)..."
  (cd .. && mvn -q -DskipTests package)
  mkdir -p lib
  SRC_JAR=$(ls -1 ../target/*with-dependencies.jar | head -n1 || true)
  if [ -n "${SRC_JAR:-}" ]; then
    cp -f "$SRC_JAR" ./lib/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar
  fi
fi

# Locate shaded JAR (prefer local validation/lib, then target)
JAR=""
if ls ./lib/*with-dependencies.jar >/dev/null 2>&1; then
  JAR=$(ls -1 ./lib/*with-dependencies.jar | head -n1)
elif ls ../target/*with-dependencies.jar >/dev/null 2>&1; then
  JAR=$(ls -1 ../target/*with-dependencies.jar | head -n1)
else
  echo "❌ Shaded JAR not found. Build it with: mvn -DskipTests package" >&2
  exit 1
fi

echo "Using JAR: $JAR"

# Ensure local folders (store models at validation/models)
mkdir -p images metrics models

# Copy RL models locally if available (do not overwrite user-provided)
if [ -d "../tcdrm_gym/models" ]; then
  [ -f models/qlearning_cloudsim.pkl ] || cp -f ../tcdrm_gym/models/qlearning_cloudsim.pkl models/ 2>/dev/null || true
  [ -f models/dqn_cloudsim.pt ] || cp -f ../tcdrm_gym/models/dqn_cloudsim.pt models/ 2>/dev/null || true
fi

# Report model presence to avoid silent fallbacks (QL → random)
echo "\n🔎 RL model files (validation/models):"
if [ -f models/qlearning_cloudsim.pkl ]; then
  echo "   • Q-Learning: models/qlearning_cloudsim.pkl"
else
  echo "   • Q-Learning: MISSING ❌"
  echo "❌ Required Q-Learning model missing. Aborting (no random fallback)."
  exit 2
fi
if [ -f models/dqn_cloudsim.pt ]; then
  echo "   • DQN       : models/dqn_cloudsim.pt"
else
  echo "   • DQN       : MISSING ❌"
  echo "❌ Required DQN model missing. Aborting (no random fallback)."
  exit 2
fi

# Clean previous RL-only outputs
rm -f images/*.png 2>/dev/null || true
rm -f metrics/rl_* metrics/summary_phase2_rl.csv 2>/dev/null || true
# keep log_overtime.csv as history

# Compile validation runners at repo root (no subfolders)
find . -maxdepth 1 -name "*.java" -print0 | xargs -0 -I{} javac -cp "$JAR" {}

printf "\nℹ️  RL validation ready.\n"
echo "   (If needed) Manual start: cd validation/python && uv sync && uv run python connect_to_java.py --port 25333"
printf "\n▶ Java runners:\n"
echo "   java -cp .:$JAR QLearningEvaluation          # Q-Learning (simple+complex)"
echo "   java -cp .:$JAR DNNEvaluation               # DQN (simple+complex)"
echo "   java -cp .:$JAR RLComparisonEvaluation      # QL vs DQN (2-models)"

ensure_uv || true

# Orchestrate per-run: start Java (gateway), then start Python client, then wait
run_with_python_client() {
  local main_class="$1"
  echo "\n🚀 Running $main_class ..."
  # Start Java (gateway opens on 25333)
  (java -cp .:$JAR "$main_class") &
  local JAVA_PID=$!
  # Wait until gateway listens on 25333 (max 20s)
  for i in $(seq 1 20); do
    if lsof -i:25333 >/dev/null 2>&1; then break; fi
    sleep 1
  done
  # Ensure no stale Python client is running and free callback port
  (pgrep -f 'connect_to_java.py' | xargs -r kill -9) >/dev/null 2>&1 || true
  (lsof -ti:25334 | xargs -r kill -9) >/dev/null 2>&1 || true
  # Start Python client (prefer uv)
  if command -v uv >/dev/null 2>&1; then
    # Pass explicit model paths to ensure correctness
    echo "   → Launching Python client with explicit model paths"
    (cd python && uv sync && nohup uv run python connect_to_java.py --port 25333 \
        --qlearning-model ../models/qlearning_cloudsim.pkl \
        --dqn-model ../models/dqn_cloudsim.pt \
        >/tmp/tcdrm_py4j.log 2>&1 &)
  else
    start_python_client_with_venv
  fi
  # Optional: brief tail for diagnostics
  sleep 1; tail -n 5 /tmp/tcdrm_py4j.log 2>/dev/null || true
  # Wait for Java process to finish
  wait "$JAVA_PID" || true
}

# Always run unified runners (each does simple+complex)
run_with_python_client QLearningEvaluation
run_with_python_client DNNEvaluation
run_with_python_client RLComparisonEvaluation

printf "\n📊 Generated files:\n"
echo "Images:"
ls -1 images 2>/dev/null || echo "(none)"
printf "\nCSVs:\n"
ls -1 metrics/*.csv 2>/dev/null || echo "(none)"
