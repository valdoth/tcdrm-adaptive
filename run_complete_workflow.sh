#!/bin/bash
# Workflow complet TCDRM-ADAPTIVE
# 1. Entraîne les modèles RL (Q-Learning Simple + DQN) avec Python
# 2. Lance les simulations CloudSim avec Java + Py4J
# 3. Génère les graphiques comparatifs

set -e

# Couleurs
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Variables
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_DIR="$PROJECT_ROOT/python_rl"
PYTHON_PID=""
JAVA_PID=""

# Fonction de nettoyage initial
cleanup_initial() {
    echo -e "${BLUE}🧹 Nettoyage des processus et ports existants...${NC}"
    
    # Tuer les processus Java/Python
    pkill -f "TcdrmComparisonCloudSim" 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmArticle" 2>/dev/null || true
    
    # Libérer les ports Py4J
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:25334 2>/dev/null | xargs kill -9 2>/dev/null || true
    
    sleep 2
    
    echo -e "${BLUE}🧹 Suppression des fichiers générés précédents...${NC}"
    
    # Supprimer les anciens graphiques
    rm -f images/*.png 2>/dev/null || true
    rm -f images/*.jpg 2>/dev/null || true
    
    # Supprimer les anciens modèles entraînés
    rm -f "$PYTHON_DIR/models/simple_qlearning.pkl" 2>/dev/null || true
    rm -rf "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null || true
    rm -f "$PYTHON_DIR/results/dqn/dqn_model.pt" 2>/dev/null || true
    
    # Supprimer les logs temporaires
    rm -f /tmp/java_simulation.log 2>/dev/null || true
    rm -f /tmp/python_client.log 2>/dev/null || true
    rm -f /tmp/java_*.log 2>/dev/null || true
    rm -f /tmp/python_*.log 2>/dev/null || true
    
    echo -e "${GREEN}✅ Nettoyage initial terminé${NC}"
    echo ""
}

# Fonction de nettoyage final
cleanup() {
    echo ""
    echo -e "${BLUE}🧹 Nettoyage...${NC}"
    
    if [ ! -z "$PYTHON_PID" ]; then
        echo "Arrêt du client Python (PID: $PYTHON_PID)..."
        kill $PYTHON_PID 2>/dev/null || true
    fi
    
    if [ ! -z "$JAVA_PID" ]; then
        echo "Arrêt de Java (PID: $JAVA_PID)..."
        kill $JAVA_PID 2>/dev/null || true
    fi
    
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmComparison" 2>/dev/null || true
    
    echo -e "${GREEN}✅ Nettoyage terminé${NC}"
}

trap cleanup EXIT INT TERM

# Fonction d'aide
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-training       Sauter l'entraînement (utiliser modèles existants)"
    echo "  --skip-compile        Sauter la compilation Java"
    echo "  --skip-simulation     Sauter la simulation Java (seulement entraîner)"
    echo "  --n-episodes N        Nombre d'épisodes d'entraînement [défaut: 2000]"
    echo "  --help                Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                              # Workflow complet"
    echo "  $0 --skip-training              # Seulement simulation avec modèles existants"
    echo "  $0 --n-episodes 3000            # Entraînement prolongé"
    echo "  $0 --skip-simulation            # Seulement entraînement"
}

# Parser les arguments
SKIP_TRAINING=false
SKIP_COMPILE=false
SKIP_SIMULATION=false
N_EPISODES=2000

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-training)
            SKIP_TRAINING=true
            shift
            ;;
        --skip-compile)
            SKIP_COMPILE=true
            shift
            ;;
        --skip-simulation)
            SKIP_SIMULATION=true
            shift
            ;;
        --n-episodes)
            N_EPISODES="$2"
            shift 2
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Option inconnue: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

echo "============================================================"
echo "  WORKFLOW COMPLET TCDRM-ADAPTIVE"
echo "  Python (Entraînement) + Java/CloudSim (Simulation)"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Épisodes d'entraînement: $N_EPISODES"
echo "  - Skip training: $SKIP_TRAINING"
echo "  - Skip compile: $SKIP_COMPILE"
echo "  - Skip simulation: $SKIP_SIMULATION"
echo ""

cleanup_initial

# ============================================================
# ÉTAPE 1: Entraînement Python RL
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 1/4: Entraînement des Modèles RL (Python)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Q-Learning Simple
    echo ">>> 1.1 Entraînement Q-Learning Simple ($N_EPISODES épisodes)..."
    uv run python train_simple_qlearning.py \
        --episodes $N_EPISODES \
        --lr 0.1 \
        --gamma 0.99 \
        --epsilon-decay 0.995 \
        --data-gb 5.3 \
        --output models/simple_qlearning.pkl
    
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning.pkl"
    echo -e "${GREEN}✅ Q-Learning Simple entraîné: $QLEARNING_MODEL${NC}"
    echo ""
    
    # DQN
    echo ">>> 1.2 Entraînement DQN ($N_EPISODES épisodes)..."
    uv run python train_dqn_policy.py \
        --episodes $N_EPISODES \
        --buffer-size 50000 \
        --batch-size 128 \
        --lr 0.0003 \
        --gamma 0.99 \
        --output-dir results/dqn
    
    # Trouver le modèle DQN
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model.pt"
    else
        DQN_MODEL="$DQN_RUN/dqn_model.pt"
    fi
    
    echo -e "${GREEN}✅ DQN entraîné: $DQN_MODEL${NC}"
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
    
    echo "⏭️  Entraînement ignoré (--skip-training)"
    echo "   Q-Learning: $QLEARNING_MODEL"
    echo "   DQN: $DQN_MODEL"
    echo ""
fi

# Vérifier que les modèles existent
if [ ! -f "$QLEARNING_MODEL" ]; then
    echo -e "${RED}❌ ERREUR: Modèle Q-Learning introuvable: $QLEARNING_MODEL${NC}"
    exit 1
fi

if [ ! -f "$DQN_MODEL" ]; then
    echo -e "${YELLOW}⚠️  Modèle DQN introuvable: $DQN_MODEL${NC}"
    echo "   La simulation continuera avec Q-Learning uniquement"
fi

# ============================================================
# ÉTAPE 2: Compilation Java
# ============================================================

if [ "$SKIP_COMPILE" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 2/4: Compilation Java"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven n'est pas installé${NC}"
        exit 1
    fi
    
    echo ">>> Compilation Maven..."
    mvn clean package -q
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Erreur de compilation${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Compilation réussie${NC}"
    echo ""
else
    echo "⏭️  Compilation Java ignorée (--skip-compile)"
    echo ""
fi

# ============================================================
# ÉTAPE 3: Simulation CloudSim avec Py4J
# ============================================================

if [ "$SKIP_SIMULATION" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 3/4: Simulation CloudSim avec Modèles RL (Py4J)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    echo ">>> Lancement du Java Gateway Server..."
    java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
        org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim > /tmp/java_simulation.log 2>&1 &
    JAVA_PID=$!
    
    echo -e "${GREEN}✅ Java Gateway Server démarré (PID: $JAVA_PID)${NC}"
    echo ""
    
    # Attendre que le Gateway soit prêt
    echo ">>> Attente du démarrage du Gateway Server..."
    sleep 10
    
    # Vérifier que le port est ouvert
    for i in {1..10}; do
        if lsof -i:25333 > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Gateway prêt sur le port 25333${NC}"
            break
        fi
        if [ $i -eq 10 ]; then
            echo -e "${RED}❌ ERREUR: Gateway non prêt après 20s${NC}"
            tail -30 /tmp/java_simulation.log
            exit 1
        fi
        echo "   Attente... ($i/10)"
        sleep 2
    done
    
    sleep 5
    
    # Démarrer le client Python avec les modèles
    echo ">>> Démarrage du client Python avec les modèles entraînés..."
    cd python_rl
    
    if [ -f "$DQN_MODEL" ]; then
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" \
            --dqn-model "$DQN_MODEL" > /tmp/python_client.log 2>&1 &
    else
        uv run python connect_to_java.py \
            --qlearning-model "$QLEARNING_MODEL" > /tmp/python_client.log 2>&1 &
    fi
    
    PYTHON_PID=$!
    cd ..
    
    echo -e "${GREEN}✅ Client Python démarré (PID: $PYTHON_PID)${NC}"
    
    # Vérifier que Python démarre
    sleep 3
    if ! ps -p $PYTHON_PID > /dev/null 2>&1; then
        echo -e "${RED}❌ ERREUR: Le client Python s'est arrêté${NC}"
        cat /tmp/python_client.log
        exit 1
    fi
    
    echo -e "${GREEN}✅ Client Python actif${NC}"
    echo ""
    
    # Attendre que Java termine
    echo ">>> Simulation CloudSim en cours..."
    echo ">>> Cela peut prendre quelques minutes..."
    echo ""
    
    wait $JAVA_PID
    JAVA_EXIT_CODE=$?
    
    # Arrêter Python
    kill $PYTHON_PID 2>/dev/null || true
    
    echo ""
    if [ $JAVA_EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}✅ Simulation terminée avec succès${NC}"
    else
        echo -e "${RED}❌ Erreur lors de la simulation (code: $JAVA_EXIT_CODE)${NC}"
        echo ""
        echo "Logs Java:"
        tail -50 /tmp/java_simulation.log
        echo ""
        echo "Logs Python:"
        tail -30 /tmp/python_client.log
        exit $JAVA_EXIT_CODE
    fi
    echo ""
else
    echo "⏭️  Simulation Java ignorée (--skip-simulation)"
    echo ""
fi

# ============================================================
# ÉTAPE 4: Résumé
# ============================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  RÉSUMÉ FINAL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

if [ "$SKIP_TRAINING" = false ]; then
    echo -e "${GREEN}✅ Entraînement Python: Terminé${NC}"
else
    echo -e "${YELLOW}⏭️  Entraînement Python: Ignoré${NC}"
fi

if [ "$SKIP_COMPILE" = false ]; then
    echo -e "${GREEN}✅ Compilation Java: Réussie${NC}"
else
    echo -e "${YELLOW}⏭️  Compilation Java: Ignorée${NC}"
fi

if [ "$SKIP_SIMULATION" = false ]; then
    echo -e "${GREEN}✅ Simulation CloudSim: Terminée${NC}"
else
    echo -e "${YELLOW}⏭️  Simulation CloudSim: Ignorée${NC}"
fi

echo ""
echo "Résultats disponibles:"
echo "  - Modèles Python:"
echo "    • Q-Learning: $QLEARNING_MODEL"
if [ -f "$DQN_MODEL" ]; then
    echo "    • DQN: $DQN_MODEL"
fi
echo ""
echo "  - Graphiques CloudSim: images/"
ls -1 images/*.png 2>/dev/null | sed 's/^/    • /' || echo "    (aucun graphique généré)"

echo ""
echo "Voir les graphiques:"
echo "  open images/*.png"
echo ""

echo "============================================================"
echo "  🎉 Workflow terminé!"
echo "============================================================"

exit 0
