#!/bin/bash
# TCDRM-ADAPTIVE Complete Workflow
# 1. Train RL models using CloudSimPlus (Java) for simulations
# 2. Run Java benchmark with trained models via Py4J
# 3. Generate comparison graphs

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_DIR="$PROJECT_ROOT/tcdrm_gym"

# Ports (allow override via env)
GATEWAY_PORT="${TCDRM_PY4J_PORT:-25333}"
TRAIN_PORT="${TCDRM_TRAIN_PORT:-25335}"
TENSORBOARD_PORT="${TCDRM_TENSORBOARD_PORT:-6006}"
PYTHON_PID=""
JAVA_PID=""
TRAINING_SERVER_PID=""
TENSORBOARD_PID=""

cleanup_initial() {
    echo -e "${BLUE}🧹 Cleaning up processes and ports...${NC}"
    pkill -f "TcdrmMain" 2>/dev/null || true
    pkill -f "TrainingServer" 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "train_cloudsim.py" 2>/dev/null || true
    pkill -f "tensorboard" 2>/dev/null || true
    lsof -ti:"$GATEWAY_PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:"$TRAIN_PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:"$TENSORBOARD_PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
    sleep 2
    
    echo -e "${BLUE}🧹 Removing previous files...${NC}"
    rm -f images/*.png 2>/dev/null || true
    
    if [ "$SKIP_TRAINING" = false ]; then
        echo "   Removing old models (retraining planned)..."
            rm -f "$PYTHON_DIR/models/qlearning_cloudsim.pkl" 2>/dev/null || true
            rm -f "$PYTHON_DIR/models/rainbow_cloudsim.pt" 2>/dev/null || true
    else
        echo "   Keeping existing models..."
    fi
    
    rm -f /tmp/java_benchmark.log /tmp/python_client.log /tmp/training_server.log 2>/dev/null || true
    echo -e "${GREEN}✅ Initial cleanup done${NC}"
    echo ""
}

cleanup() {
    echo ""
    echo -e "${BLUE}🧹 Cleaning up...${NC}"
    [ -n "$PYTHON_PID" ] && kill $PYTHON_PID 2>/dev/null || true
    [ -n "$JAVA_PID" ] && kill $JAVA_PID 2>/dev/null || true
    [ -n "$TRAINING_SERVER_PID" ] && kill $TRAINING_SERVER_PID 2>/dev/null || true
    [ -n "$TENSORBOARD_PID" ] && kill $TENSORBOARD_PID 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmMain" 2>/dev/null || true
    pkill -f "TrainingServer" 2>/dev/null || true
    pkill -f "train_cloudsim.py" 2>/dev/null || true
    echo -e "${GREEN}✅ Cleanup done${NC}"
}
trap cleanup EXIT INT TERM

show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-training       Skip training (use existing models)"
    echo "  --skip-compile        Skip Java compilation"
    echo "  --skip-simulation     Skip Java simulation (training only)"
    echo "  --episodes N          Training episodes [default: 100] (benchmark toujours 1000 requêtes)"
    echo "  --help                Show this help"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Full workflow"
    echo "  $0 --skip-training                    # Simulation only"
    echo "  $0 --episodes 200                     # Extended training"
}

SKIP_TRAINING=false
SKIP_COMPILE=false
SKIP_SIMULATION=false
N_EPISODES=100

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-training)     SKIP_TRAINING=true; shift ;;
        --skip-compile)      SKIP_COMPILE=true; shift ;;
        --skip-simulation)   SKIP_SIMULATION=true; shift ;;
        --episodes)          N_EPISODES="$2"; shift 2 ;;
        --help)              show_help; exit 0 ;;
        *)                   echo -e "${RED}Unknown option: $1${NC}"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  TCDRM-ADAPTIVE COMPLETE WORKFLOW"
echo "  Training with CloudSimPlus + Benchmark"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Training episodes: $N_EPISODES"
echo "  - Skip training: $SKIP_TRAINING"
echo "  - Skip compile: $SKIP_COMPILE"
echo "  - Skip simulation: $SKIP_SIMULATION"
echo ""

cleanup_initial

# ============================================================
# TensorBoard (live training metrics from tcdrm_gym/logs)
# ============================================================

echo ">>> Starting TensorBoard on port ${TENSORBOARD_PORT}..."
mkdir -p "$PYTHON_DIR/logs"
cd "$PYTHON_DIR"
nohup uv run tensorboard --logdir logs --port "$TENSORBOARD_PORT" --reload_interval 5 > /tmp/tensorboard.log 2>&1 &
TENSORBOARD_PID=$!
cd "$PROJECT_ROOT"

for i in {1..15}; do
    if lsof -i:"$TENSORBOARD_PORT" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ TensorBoard ready: http://localhost:${TENSORBOARD_PORT}${NC}"
        break
    fi
    if [ $i -eq 15 ]; then
        echo -e "${YELLOW}⚠️  TensorBoard did not start after 15s (see /tmp/tensorboard.log)${NC}"
        TENSORBOARD_PID=""
    fi
    sleep 1
done
echo ""

# ============================================================
# STEP 1: Train RL Models
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  STEP 1/3: Training RL Models with CloudSimPlus"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "  Training uses CloudSimPlus (Java) for simulations."
    echo "  This ensures training and inference use the same environment."
    echo ""
    
    # Always rebuild the shaded JAR so the server runs the latest Java code
    echo ">>> Building shaded JAR for TrainingServer..."
    mvn -q -DskipTests package || { echo -e "${RED}❌ Maven build failed${NC}"; exit 1; }

    # Start Java Training Server from shaded JAR to avoid exec:java mainClass override issues
    echo ">>> Starting Java TrainingServer..."
    JAR_FILE=$(ls -1 "$PROJECT_ROOT/target"/*-with-dependencies.jar | head -n1)
    if [ -z "$JAR_FILE" ]; then
        echo -e "${RED}❌ Could not find shaded JAR in target/*.with-dependencies.jar${NC}"
        exit 1
    fi
    nohup java -cp "$JAR_FILE" org.tcdrm.adaptive.training.TrainingServer > /tmp/training_server.log 2>&1 &
    TRAINING_SERVER_PID=$!
    
    # Wait for server to start
    sleep 5
    for i in {1..15}; do
        if lsof -i:"$TRAIN_PORT" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ TrainingServer ready on port ${TRAIN_PORT}${NC}"
            break
        fi
        if [ $i -eq 15 ]; then
            echo -e "${RED}❌ ERROR: TrainingServer not ready after 30s${NC}"
            tail -30 /tmp/training_server.log
            exit 1
        fi
        echo "   Waiting... ($i/15)"
        sleep 2
    done
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Q-Learning training with CloudSimPlus
    echo ">>> 1.1 Training Q-Learning ($N_EPISODES episodes)..."
    echo "    Using CloudSimPlus for simulations"
    echo ""
    
    uv run python train_cloudsim.py \
        --agent qlearning \
        --episodes $N_EPISODES \
        --port "$TRAIN_PORT" \
        --output models/qlearning_cloudsim.pkl
    
    QLEARNING_MODEL="$PYTHON_DIR/models/qlearning_cloudsim.pkl"
    echo -e "${GREEN}✅ Q-Learning trained: $QLEARNING_MODEL${NC}"
    echo ""
    
    # Rainbow DQN training with CloudSimPlus
    echo ">>> 1.2 Training Rainbow DQN ($N_EPISODES episodes)..."
    echo "    Using CloudSimPlus for simulations"
    echo ""

    uv run python train_cloudsim.py \
        --agent rainbow \
        --episodes $N_EPISODES \
        --port "$TRAIN_PORT" \
        --reward-cost-over 25 \
        --output models/rainbow_cloudsim.pt

    DQN_MODEL="$PYTHON_DIR/models/rainbow_cloudsim.pt"
    echo -e "${GREEN}✅ Rainbow DQN trained: $DQN_MODEL${NC}"
    echo ""
    
    # Stop Training Server
    echo ">>> Stopping TrainingServer..."
    kill $TRAINING_SERVER_PID 2>/dev/null || true
    TRAINING_SERVER_PID=""
    sleep 2
    
    cd "$PROJECT_ROOT"
else
    QLEARNING_MODEL="$PYTHON_DIR/models/qlearning_cloudsim.pkl"
    DQN_MODEL="$PYTHON_DIR/models/rainbow_cloudsim.pt"

    echo "⏭️  Training skipped (--skip-training)"
    echo "   Q-Learning: $QLEARNING_MODEL"
    echo "   Rainbow DQN: $DQN_MODEL"
    echo ""
fi

# Verify models exist
if [ ! -f "$QLEARNING_MODEL" ]; then
    echo -e "${YELLOW}⚠️  Q-Learning model not found: $QLEARNING_MODEL${NC}"
    echo "   Will use fresh agent during simulation"
fi

if [ ! -f "$DQN_MODEL" ]; then
    echo -e "${YELLOW}⚠️  Rainbow DQN model not found: $DQN_MODEL${NC}"
    echo "   Will use fresh agent during simulation"
fi

# ============================================================
# STEP 2: Compile Java
# ============================================================

if [ "$SKIP_COMPILE" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  STEP 2/3: Compiling Java"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven not installed${NC}"
        exit 1
    fi

    echo ">>> Maven compilation..."
    # Step 1 already rebuilt the JAR if training ran — skip if redundant
    if [ "$SKIP_TRAINING" = false ] && ls "$PROJECT_ROOT/target"/*-with-dependencies.jar >/dev/null 2>&1; then
        echo "   (JAR already up to date from Step 1, skipping recompile)"
    else
        mvn clean package -q -DskipTests
    fi

    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Compilation error${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ Compilation successful${NC}"
    echo ""
else
    echo "⏭️  Java compilation skipped (--skip-compile)"
    echo ""
fi

# ============================================================
# STEP 3: Run Benchmark with Py4J
# ============================================================

if [ "$SKIP_SIMULATION" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  STEP 3/3: Running Benchmark (Java + Python via Py4J)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "    Comparison:"
    echo "    - Q-Learning (TCDRM-ADAPTIVE)"
    echo "    - Rainbow DQN (TCDRM-ADAPTIVE)"
    echo "    - TCDRM Static"
    echo "    - NoRepLc (baseline)"
    echo ""
    
    echo ">>> Starting Java benchmark (TcdrmMain)..."
    # --headless : export PNG sans serveur graphique (CI/terminal)
    # --rl-only  : non utilisé ici car on veut Phase 1 + Phase 2
    # La Phase 1 (4×1000 requêtes + graphiques) s'exécute AVANT l'ouverture du gateway Py4J.
    # Le timeout Python interne de TcdrmMain est 120s (--py-timeout, TcdrmMainArgs).
    mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.TcdrmMain" \
        -Dexec.args="--headless" > /tmp/java_benchmark.log 2>&1 &
    JAVA_PID=$!

    echo -e "${GREEN}✅ Java started (PID: $JAVA_PID)${NC}"
    echo ""

    # Attente du gateway Py4J — TcdrmMain n'ouvre le port QU'APRÈS la Phase 1
    # (Phase 1 = 4 scénarios × 1000 requêtes + génération graphiques ≈ 2-5 min)
    # Timeout total : 5 min (150 × 2s), avec affichage de progression toutes les 20s.
    echo ">>> Waiting for Py4J gateway (Phase 1 must complete first, may take ~3-5 min)..."
    GATEWAY_MAX_WAIT=150   # 150 × 2s = 300s = 5 minutes
    for i in $(seq 1 $GATEWAY_MAX_WAIT); do
        if lsof -i:"$GATEWAY_PORT" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Gateway ready on port ${GATEWAY_PORT} (after $((i*2))s)${NC}"
            break
        fi
        if ! ps -p $JAVA_PID > /dev/null 2>&1; then
            echo -e "${RED}❌ ERROR: TcdrmMain exited before opening gateway${NC}"
            tail -50 /tmp/java_benchmark.log
            exit 1
        fi
        if [ $i -eq $GATEWAY_MAX_WAIT ]; then
            echo -e "${RED}❌ ERROR: Gateway not ready after $((GATEWAY_MAX_WAIT*2))s${NC}"
            tail -30 /tmp/java_benchmark.log
            exit 1
        fi
        # Afficher la progression toutes les 20 secondes
        if [ $((i % 10)) -eq 0 ]; then
            echo "   Phase 1 running... ($((i*2))s / $((GATEWAY_MAX_WAIT*2))s)"
            # Montrer la dernière ligne du log pour avoir un feedback
            tail -1 /tmp/java_benchmark.log 2>/dev/null | sed 's/^/   > /'
        fi
        sleep 2
    done

    sleep 3
    
    # Start Python client
    echo ">>> Starting Python client..."
    cd "$PYTHON_DIR"
    
        if [ -f "$DQN_MODEL" ]; then
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" \
            --rainbow-model "$DQN_MODEL" \
            --port "$GATEWAY_PORT" > /tmp/python_client.log 2>&1 &
    else
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" \
            --port "$GATEWAY_PORT" > /tmp/python_client.log 2>&1 &
    fi
    
    PYTHON_PID=$!
    cd "$PROJECT_ROOT"
    
    echo -e "${GREEN}✅ Python client started (PID: $PYTHON_PID)${NC}"
    
    # Verify Python started
    sleep 3
    if ! ps -p $PYTHON_PID > /dev/null 2>&1; then
        echo -e "${RED}❌ ERROR: Python client stopped${NC}"
        cat /tmp/python_client.log
        exit 1
    fi
    
    echo -e "${GREEN}✅ Python client active${NC}"
    echo ""
    
    # Wait for Java to finish
    echo ">>> Benchmark running (real RL decisions via Py4J)..."
    echo ">>> Python models make decisions for each query"
    echo ">>> This may take a few minutes..."
    echo ""
    
    wait $JAVA_PID
    JAVA_EXIT=$?
    
    kill $PYTHON_PID 2>/dev/null || true
    
    echo ""
    if [ $JAVA_EXIT -eq 0 ]; then
        echo -e "${GREEN}✅ Benchmark completed successfully${NC}"
    else
        echo -e "${RED}❌ Benchmark error (exit code: $JAVA_EXIT)${NC}"
        echo ""
        echo "Java log:"
        tail -50 /tmp/java_benchmark.log
        echo ""
        echo "Python log:"
        tail -30 /tmp/python_client.log
        exit $JAVA_EXIT
    fi
    echo ""
else
    echo "⏭️  Simulation skipped (--skip-simulation)"
    echo ""
fi

# ============================================================
# SUMMARY
# ============================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  FINAL SUMMARY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

[ "$SKIP_TRAINING" = false ] && echo -e "${GREEN}✅ Training: Done${NC}" || echo -e "${YELLOW}⏭️  Training: Skipped${NC}"
[ "$SKIP_COMPILE" = false ] && echo -e "${GREEN}✅ Compilation: Done${NC}" || echo -e "${YELLOW}⏭️  Compilation: Skipped${NC}"
[ "$SKIP_SIMULATION" = false ] && echo -e "${GREEN}✅ Simulation: Done${NC}" || echo -e "${YELLOW}⏭️  Simulation: Skipped${NC}"

echo ""
echo "📊 Results:"
echo ""
echo "  1. Trained Models:"
echo "     • Q-Learning: $QLEARNING_MODEL"
[ -f "$DQN_MODEL" ] && echo "     • Rainbow DQN: $DQN_MODEL"
echo ""
echo "  2. Generated Graphs:"
if [ -d "images" ] && [ "$(ls -A images/*.png 2>/dev/null)" ]; then
    ls -1 images/*.png 2>/dev/null | sed 's/^/     • /'
else
    echo "     (no graphs generated)"
fi
echo ""

echo ""
echo " View graphs:"
echo "  open images/*.png"
echo ""

echo "============================================================"
echo "  🎉 Workflow complete!"
echo "============================================================"

exit 0
