#!/bin/bash
# Script d'entraînement des modèles RL pour TCDRM-ADAPTIVE
# 1. Entraîne Q-Learning Simple (Python)
# 2. Entraîne DQN (Python)
# Les graphiques sont générés par Java/CloudSim avec Py4J

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
    
    # Tuer les processus
    pkill -f "TcdrmArticleAllGraphs3CurvesWithPy4J" 2>/dev/null || true
    pkill -f "connect_to_java" 2>/dev/null || true
    pkill -f "TcdrmArticleGraphs" 2>/dev/null || true
    
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:25334 2>/dev/null | xargs kill -9 2>/dev/null || true
    
    sleep 2
    
    echo -e "${BLUE}🧹 Suppression des anciens fichiers générés...${NC}"
    
    # Supprimer les anciens graphes
    rm -f images/tcdrm_*.png 2>/dev/null || true
    rm -f images/*_3curves*.png 2>/dev/null || true
    rm -f images/*_4curves*.png 2>/dev/null || true
    
    # Supprimer les anciens résultats d'entraînement
    rm -rf "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null || true
    rm -rf "$PYTHON_DIR/models/simple_qlearning.pkl" 2>/dev/null || true
    
    # Supprimer les logs temporaires
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
    
    pkill -f "connect_to_java" 2>/dev/null || true
    pkill -f "TcdrmArticleAllGraphs" 2>/dev/null || true
    
    echo -e "${GREEN}✅ Nettoyage terminé${NC}"
}

trap cleanup EXIT INT TERM

# Fonction d'aide
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-qlearning      Sauter l'entraînement Q-Learning Simple"
    echo "  --skip-dqn            Sauter l'entraînement DQN"
    echo "  --n-episodes N        Nombre d'épisodes d'entraînement [défaut: 1000]"
    echo "  --help                Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                                    # Entraîner les 2 modèles"
    echo "  $0 --skip-dqn                         # Seulement Q-Learning Simple"
    echo "  $0 --n-episodes 2000                  # Entraînement prolongé"
    echo ""
    echo "Note: Ce script entraîne les modèles RL (Q-Learning Simple, DQN)."
    echo "      Les graphiques sont générés par Java/CloudSim avec Py4J."
}

# Parser les arguments
SKIP_QLEARNING=false
SKIP_DQN=false
N_EPISODES=1000

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-qlearning)
            SKIP_QLEARNING=true
            shift
            ;;
        --skip-dqn)
            SKIP_DQN=true
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

# Nettoyage initial
cleanup_initial

echo "============================================================"
echo "  WORKFLOW TCDRM-ADAPTIVE - Q-Learning Simple + DQN"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Épisodes d'entraînement: $N_EPISODES"
echo "  - Skip Q-Learning Simple: $SKIP_QLEARNING"
echo "  - Skip DQN: $SKIP_DQN"
echo ""

# ÉTAPE 1: Entraînement des modèles RL
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ÉTAPE 1/3: Entraînement des Modèles RL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd "$PYTHON_DIR"

# 1.1 Q-Learning Simple
if [ "$SKIP_QLEARNING" = false ]; then
    echo ">>> 1.1 Entraînement Q-Learning Simple ($N_EPISODES épisodes, 243 états)..."
    uv run python train_simple_qlearning.py \
        --episodes $N_EPISODES \
        --data-gb 5.3 \
        --output models/simple_qlearning.pkl
    
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning.pkl"
    echo -e "${GREEN}✅ Q-Learning Simple entraîné: $QLEARNING_MODEL${NC}"
    echo ""
else
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning.pkl"
    if [ ! -f "$QLEARNING_MODEL" ]; then
        echo -e "${RED}❌ ERREUR: Aucun modèle Q-Learning Simple trouvé${NC}"
        exit 1
    fi
    echo "⏭️  Q-Learning Simple: Utilisation du modèle existant"
    echo "   Modèle: $QLEARNING_MODEL"
    echo ""
fi

# 1.2 DQN
if [ "$SKIP_DQN" = false ]; then
    echo ">>> 1.2 Entraînement DQN ($N_EPISODES épisodes)..."
    uv run python train_dqn_policy.py \
        --episodes $N_EPISODES \
        --queries 1000 \
        --output-dir results/dqn
    
    # Chercher d'abord dans run_*, sinon directement dans results/dqn/
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        if [ -f "$PYTHON_DIR/results/dqn/dqn_model.pt" ]; then
            DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model.pt"
        else
            DQN_MODEL=""
        fi
    else
        DQN_MODEL="$DQN_RUN/dqn_model.pt"
    fi
    echo -e "${GREEN}✅ DQN entraîné: $DQN_MODEL${NC}"
    echo ""
else
    # Chercher d'abord dans run_*, sinon directement dans results/dqn/
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        if [ -f "$PYTHON_DIR/results/dqn/dqn_model.pt" ]; then
            DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model.pt"
        else
            DQN_MODEL=""
        fi
    else
        DQN_MODEL="$DQN_RUN/dqn_model.pt"
    fi
    echo "⏭️  DQN: Utilisation du modèle existant"
    echo "   Modèle: $DQN_MODEL"
    echo ""
fi

cd "$PROJECT_ROOT"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ ENTRAÎNEMENT TERMINÉ"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Modèles entraînés et prêts pour utilisation Java/CloudSim:"
echo "  - Q-Learning Simple: $QLEARNING_MODEL"
if [ ! -z "$DQN_MODEL" ] && [ -f "$DQN_MODEL" ]; then
    echo "  - DQN: $DQN_MODEL"
fi
echo ""
echo "Pour générer les graphiques avec Java/CloudSim:"
echo "  1. Compiler le projet Java: mvn clean package"
echo "  2. Lancer la simulation: java -cp target/... org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim"
echo ""

# Résumé final
echo ""
echo "============================================================"
echo "  RÉSUMÉ FINAL"
echo "============================================================"
echo ""

echo "Modèles entraînés:"
if [ "$SKIP_QLEARNING" = false ]; then
    echo "  ✅ Q-Learning Simple (243 états): $QLEARNING_MODEL"
else
    echo "  ⏭️  Q-Learning Simple: Modèle existant utilisé"
fi

if [ "$SKIP_DQN" = false ]; then
    echo "  ✅ DQN: $DQN_MODEL"
else
    echo "  ⏭️  DQN: Modèle existant utilisé"
fi

echo ""
echo "Modèles disponibles pour Java/CloudSim:"
echo "  - Q-Learning Simple: $QLEARNING_MODEL"
if [ ! -z "$DQN_MODEL" ]; then
    echo "  - DQN: $DQN_MODEL"
fi
echo ""
echo "Prochaines étapes:"
echo "  1. Compiler le projet Java:"
echo "     cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive"
echo "     mvn clean package"
echo ""
echo "  2. Lancer la simulation CloudSim avec les modèles entraînés:"
echo "     java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \\"
echo "       org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim"
echo ""
echo "  3. Les graphiques seront générés par Java/CloudSim dans images/"
echo ""
echo "============================================================"
echo "  🎉 Workflow terminé!"
echo "============================================================"
