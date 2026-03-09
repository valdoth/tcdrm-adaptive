#!/bin/bash
# TCDRM-ADAPTIVE Complete Workflow with TensorBoard
# 1. Train RL models (Q-Learning + DQN) with optional TensorBoard monitoring
# 2. Run Java benchmark with Py4J (real RL decisions via TcdrmMain)
# 3. Generate all comparison graphs

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_DIR="$PROJECT_ROOT/python_rl"
PYTHON_PID=""
JAVA_PID=""
TENSORBOARD_PID=""

cleanup_initial() {
    echo -e "${BLUE}🧹 Cleaning up processes and ports...${NC}"
    pkill -f "TcdrmMain" 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "tensorboard" 2>/dev/null || true
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:6006 2>/dev/null | xargs kill -9 2>/dev/null || true
    sleep 2
    
    echo -e "${BLUE}🧹 Removing previous files...${NC}"
    rm -f images/*.png 2>/dev/null || true
    
    if [ "$SKIP_TRAINING" = false ]; then
        echo "   Removing old models (retraining planned)..."
        rm -f "$PYTHON_DIR/models/simple_qlearning.pkl" 2>/dev/null || true
        rm -rf "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null || true
        rm -f "$PYTHON_DIR/results/dqn/dqn_model.pt" 2>/dev/null || true
    else
        echo "   Keeping existing models..."
    fi
    
    rm -f /tmp/java_benchmark.log /tmp/python_client.log 2>/dev/null || true
    echo -e "${GREEN}✅ Initial cleanup done${NC}"
    echo ""
}

cleanup() {
    echo ""
    echo -e "${BLUE}🧹 Cleaning up...${NC}"
    [ -n "$PYTHON_PID" ] && kill $PYTHON_PID 2>/dev/null || true
    [ -n "$JAVA_PID" ] && kill $JAVA_PID 2>/dev/null || true
    [ -n "$TENSORBOARD_PID" ] && kill $TENSORBOARD_PID 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmMain" 2>/dev/null || true
    pkill -f "tensorboard" 2>/dev/null || true
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
    echo "  --episodes N          Training episodes [default: 2000]"
    echo "  --queries N           Benchmark queries [default: 1000]"
    echo "  --tensorboard         Enable TensorBoard monitoring"
    echo "  --tensorboard-port N  TensorBoard port [default: 6006]"
    echo "  --help                Show this help"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Full workflow"
    echo "  $0 --tensorboard                      # With TensorBoard"
    echo "  $0 --skip-training                    # Simulation only"
    echo "  $0 --episodes 3000 --queries 1500     # Extended training"
}

SKIP_TRAINING=false
SKIP_COMPILE=false
SKIP_SIMULATION=false
N_EPISODES=2000
N_QUERIES=1000
ENABLE_TENSORBOARD=false
TENSORBOARD_PORT=6006

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-training)     SKIP_TRAINING=true; shift ;;
        --skip-compile)      SKIP_COMPILE=true; shift ;;
        --skip-simulation)   SKIP_SIMULATION=true; shift ;;
        --episodes)          N_EPISODES="$2"; shift 2 ;;
        --queries)           N_QUERIES="$2"; shift 2 ;;
        --tensorboard)       ENABLE_TENSORBOARD=true; shift ;;
        --tensorboard-port)  TENSORBOARD_PORT="$2"; shift 2 ;;
        --help)              show_help; exit 0 ;;
        *)                   echo -e "${RED}Unknown option: $1${NC}"; show_help; exit 1 ;;
    esac
done

echo "============================================================"
echo "  TCDRM-ADAPTIVE COMPLETE WORKFLOW"
echo "  Python (Training) + Java/CloudSim (Real RL Decisions)"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Training episodes: $N_EPISODES"
echo "  - Benchmark queries: $N_QUERIES"
echo "  - Skip training: $SKIP_TRAINING"
echo "  - Skip compile: $SKIP_COMPILE"
echo "  - Skip simulation: $SKIP_SIMULATION"
echo "  - TensorBoard: $ENABLE_TENSORBOARD (port: $TENSORBOARD_PORT)"
echo ""

cleanup_initial

# ============================================================
# STEP 1: Train RL Models
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  STEP 1/3: Training RL Models (Python)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Start TensorBoard if enabled
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo -e "${BLUE}  📊 TENSORBOARD - REAL-TIME MONITORING${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
        echo ">>> Starting TensorBoard (port $TENSORBOARD_PORT)..."
        uv run tensorboard --logdir=runs --port=$TENSORBOARD_PORT --bind_all > /tmp/tensorboard.log 2>&1 &
        TENSORBOARD_PID=$!
        sleep 3
        
        echo -e "${GREEN}✅ TensorBoard started (PID: $TENSORBOARD_PID)${NC}"
        echo ""
        echo -e "${BLUE}┌─────────────────────────────────────────────────────────┐${NC}"
        echo -e "${BLUE}│  🌐 TENSORBOARD DASHBOARD:                             │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│     ${GREEN}http://localhost:$TENSORBOARD_PORT${BLUE}                            │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│  📊 Real-time metrics:                                 │${NC}"
        echo -e "${BLUE}│     • Reward per step and episode                      │${NC}"
        echo -e "${BLUE}│     • Latency, Cost, Budget, Replicas                  │${NC}"
        echo -e "${BLUE}│     • Epsilon, States Explored (Q-Learning)            │${NC}"
        echo -e "${BLUE}│     • Loss, Q-values (DQN)                             │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│  💡 Open in browser to see training curves            │${NC}"
        echo -e "${BLUE}└─────────────────────────────────────────────────────────┘${NC}"
        echo ""
        echo -e "${YELLOW}⏳ TensorBoard will stay active during workflow${NC}"
        echo ""
        sleep 2
    fi
    
    # Q-Learning training
    echo ">>> 1.1 Training Q-Learning ($N_EPISODES episodes)..."
    echo "    Hyperparameters: lr=0.1, gamma=0.95, epsilon_decay=0.995"
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo -e "    ${GREEN}📊 TensorBoard: ENABLED${NC}"
        echo -e "    ${BLUE}🌐 View metrics: http://localhost:$TENSORBOARD_PORT${NC}"
    fi
    echo ""
    
    TB_FLAG=""
    [ "$ENABLE_TENSORBOARD" = true ] && TB_FLAG="--tensorboard"
    
    uv run python train_simple_qlearning.py \
        --episodes $N_EPISODES \
        --lr 0.1 \
        --gamma 0.95 \
        --epsilon-decay 0.995 \
        --data-gb 5.3 \
        --output models/simple_qlearning.pkl \
        $TB_FLAG
    
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning.pkl"
    echo -e "${GREEN}✅ Q-Learning trained: $QLEARNING_MODEL${NC}"
    echo ""
    
    # DQN training
    echo ">>> 1.2 Training DQN ($N_EPISODES episodes, $N_QUERIES queries)..."
    echo "    Hyperparameters: lr=0.0003, gamma=0.99, batch_size=128"
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo -e "    ${GREEN}📊 TensorBoard: ENABLED${NC}"
        echo -e "    ${BLUE}🌐 View metrics: http://localhost:$TENSORBOARD_PORT${NC}"
    fi
    echo ""
    
    uv run python train_dqn_policy.py \
        --episodes $N_EPISODES \
        --queries $N_QUERIES \
        --buffer-size 50000 \
        --batch-size 128 \
        --lr 0.0003 \
        --gamma 0.99 \
        --output-dir results/dqn \
        $TB_FLAG
    
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model.pt"
    else
        DQN_MODEL="$DQN_RUN/dqn_model.pt"
    fi
    
    echo -e "${GREEN}✅ DQN trained: $DQN_MODEL${NC}"
    echo ""
    cd "$PROJECT_ROOT"
else
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning.pkl"
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model.pt"
    else
        DQN_MODEL="$DQN_RUN/dqn_model.pt"
    fi
    
    echo "⏭️  Training skipped (--skip-training)"
    echo "   Q-Learning: $QLEARNING_MODEL"
    echo "   DQN: $DQN_MODEL"
    echo ""
fi

# Verify models exist
if [ ! -f "$QLEARNING_MODEL" ]; then
    echo -e "${RED}❌ ERROR: Q-Learning model not found: $QLEARNING_MODEL${NC}"
    exit 1
fi

if [ ! -f "$DQN_MODEL" ]; then
    echo -e "${YELLOW}⚠️  DQN model not found: $DQN_MODEL${NC}"
    echo "   Simulation will continue with Q-Learning only"
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
    mvn clean package -q
    
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
    echo "    - DQN (TCDRM-ADAPTIVE)"
    echo "    - TCDRM Static"
    echo "    - NoRepLc (baseline)"
    echo ""
    
    echo ">>> Starting Java benchmark (TcdrmMain)..."
    mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.TcdrmMain" -q > /tmp/java_benchmark.log 2>&1 &
    JAVA_PID=$!
    
    echo -e "${GREEN}✅ Java started (PID: $JAVA_PID)${NC}"
    echo ""
    
    # Wait for Py4J gateway
    echo ">>> Waiting for Py4J gateway..."
    sleep 10
    
    for i in {1..10}; do
        if lsof -i:25333 > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Gateway ready on port 25333${NC}"
            break
        fi
        if [ $i -eq 10 ]; then
            echo -e "${RED}❌ ERROR: Gateway not ready after 20s${NC}"
            tail -30 /tmp/java_benchmark.log
            exit 1
        fi
        echo "   Waiting... ($i/10)"
        sleep 2
    done
    
    sleep 5
    
    # Start Python client
    echo ">>> Starting Python client..."
    cd "$PYTHON_DIR"
    
    if [ -f "$DQN_MODEL" ]; then
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" \
            --dqn-model "$DQN_MODEL" > /tmp/python_client.log 2>&1 &
    else
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" > /tmp/python_client.log 2>&1 &
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
[ -f "$DQN_MODEL" ] && echo "     • DQN: $DQN_MODEL"
echo ""
echo "  2. Generated Graphs:"
if [ -d "images" ] && [ "$(ls -A images/*.png 2>/dev/null)" ]; then
    ls -1 images/*.png 2>/dev/null | sed 's/^/     • /'
else
    echo "     (no graphs generated)"
fi
echo ""

if [ "$ENABLE_TENSORBOARD" = true ]; then
    echo "  3. TensorBoard (ACTIVE):"
    echo ""
    echo -e "     ${GREEN}┌─────────────────────────────────────────────────────┐${NC}"
    echo -e "     ${GREEN}│  📊 TENSORBOARD STILL ACTIVE                       │${NC}"
    echo -e "     ${GREEN}│                                                     │${NC}"
    echo -e "     ${GREEN}│  🌐 Dashboard: http://localhost:$TENSORBOARD_PORT                  │${NC}"
    echo -e "     ${GREEN}│  📁 Logs: python_rl/runs/                          │${NC}"
    echo -e "     ${GREEN}│                                                     │${NC}"
    echo -e "     ${GREEN}│  💡 Compare Q-Learning and DQN in the interface    │${NC}"
    echo -e "     ${GREEN}└─────────────────────────────────────────────────────┘${NC}"
    echo ""
else
    echo "  3. TensorBoard:"
    echo "     ⏭️  Not enabled (use --tensorboard for monitoring)"
fi

echo ""
echo "📈 View graphs:"
echo "  open images/*.png"
echo ""
[ "$ENABLE_TENSORBOARD" = true ] && echo "📊 TensorBoard:" && echo "  open http://localhost:$TENSORBOARD_PORT" && echo ""

echo "============================================================"
echo "  🎉 Workflow complete!"
echo "============================================================"

exit 0
