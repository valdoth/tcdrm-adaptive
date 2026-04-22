#!/usr/bin/env bash
# Phase 1 + Phase 2 : démarre le client Python Py4J puis la simulation Java.
# Usage:
#   ./scripts/run_full_simulation.sh
#   PY_PORT=25333 ./scripts/run_full_simulation.sh
# Phase 1 seule (sans Python) :
#   mvn -q exec:java -Dexec.args="--phase1-only"

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export MAVEN_OPTS="${MAVEN_OPTS:--Djava.awt.headless=true}"
PY_PORT="${PY_PORT:-25333}"

cleanup() {
  if [[ -n "${PY_PID:-}" ]] && kill -0 "$PY_PID" 2>/dev/null; then
    kill "$PY_PID" 2>/dev/null || true
    wait "$PY_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "Starting Python Py4J client on port ${PY_PORT}..."
cd "${ROOT}/tcdrm_gym"
if command -v uv >/dev/null 2>&1; then
  uv run python connect_to_java.py --port "${PY_PORT}" &
else
  python3 connect_to_java.py --port "${PY_PORT}" &
fi
PY_PID=$!

sleep 3

cd "$ROOT"
echo "Starting TcdrmMain (full run)..."
mvn -q exec:java -Dexec.args="--py-timeout 300"
