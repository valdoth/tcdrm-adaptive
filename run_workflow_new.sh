#!/bin/bash
# Script unifié pour le workflow complet TCDRM-ADAPTIVE
# 1. Entraînement avec fonction de récompense multi-objectif adaptative
# 2. Génération des actions optimales pour R1 et R2
# 3. Génération des graphes avec 3 courbes (Python RL + TCDRM Statique + NOREP)

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
    
    pkill -f "TcdrmArticleAllGraphs3CurvesWithPy4J" 2>/dev/null || true
    pkill -f "connect_to_java" 2>/dev/null || true
    pkill -f "TcdrmArticleGraphs" 2>/dev/null || true
    
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:25334 2>/dev/null | xargs kill -9 2>/dev/null || true
    
    sleep 2
    
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
    echo "  --skip-training       Sauter l'entraînement adaptatif (utiliser modèle existant)"
    echo "  --skip-actions        Sauter la génération des actions optimales"
    echo "  --skip-compile        Sauter la compilation Java"
    echo "  --n-queries N         Nombre de requêtes par épisode [défaut: 1000]"
    echo "  --n-episodes N        Nombre d'épisodes d'entraînement [défaut: 200]"
    echo "  --help                Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                                    # Workflow complet (200 épisodes)"
    echo "  $0 --skip-training                    # Utiliser modèle existant"
    echo "  $0 --n-queries 500 --n-episodes 50    # Entraînement rapide"
    echo ""
    echo "Note: TCDRM-ADAPTIVE utilise l'apprentissage par renforcement avec"
    echo "      fonction de récompense multi-objectif pour apprendre les seuils"
    echo "      de réplication de manière adaptative."
}

# Parser les arguments
SKIP_TRAINING=false
SKIP_ACTIONS=false
SKIP_COMPILE=false
N_QUERIES=1000
N_EPISODES=200

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-training)
            SKIP_TRAINING=true
            shift
            ;;
        --skip-actions)
            SKIP_ACTIONS=true
            shift
            ;;
        --skip-compile)
            SKIP_COMPILE=true
            shift
            ;;
        --n-queries)
            N_QUERIES="$2"
            shift 2
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
echo "  WORKFLOW TCDRM-ADAPTIVE - NOUVELLE APPROCHE RL"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Requêtes d'entraînement: $N_QUERIES"
echo "  - Épisodes d'entraînement: $N_EPISODES"
echo "  - Skip training: $SKIP_TRAINING"
echo "  - Skip actions: $SKIP_ACTIONS"
echo "  - Skip compile: $SKIP_COMPILE"
echo ""

# ÉTAPE 1: Entraînement TCDRM-ADAPTIVE
if [ "$SKIP_TRAINING" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 1/5: Entraînement TCDRM-ADAPTIVE (Récompense Multi-objectif)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    cd "$PYTHON_DIR"
    
    echo ">>> Entraînement adaptatif avec $N_EPISODES épisodes de $N_QUERIES requêtes..."
    echo ">>> Fonction de récompense: SLA + Coût + Budget + Stabilité + Timing"
    uv run python train_adaptive_policy.py \
        --episodes $N_EPISODES \
        --queries $N_QUERIES \
        --lr 0.1 \
        --gamma 0.95 \
        --epsilon-start 1.0 \
        --epsilon-end 0.01 \
        --epsilon-decay 0.995 \
        --output-dir results/tcdrm_adaptive
    
    # Trouver le dernier modèle entraîné
    LATEST_RUN=$(ls -td results/tcdrm_adaptive/full_run_* 2>/dev/null | head -1)
    if [ -z "$LATEST_RUN" ]; then
        echo -e "${RED}❌ ERREUR: Aucun modèle entraîné trouvé${NC}"
        exit 1
    fi
    
    MODEL_PATH="$LATEST_RUN/adaptive_model.pkl"
    echo -e "${GREEN}✅ Modèle TCDRM-ADAPTIVE entraîné: $MODEL_PATH${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 1/5: Entraînement (IGNORÉ)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    # Trouver le dernier modèle TCDRM-ADAPTIVE
    LATEST_RUN=$(ls -td "$PYTHON_DIR/results/tcdrm_adaptive/full_run_"* 2>/dev/null | head -1)
    if [ -z "$LATEST_RUN" ]; then
        # Fallback: chercher l'ancien format
        LATEST_RUN=$(ls -td "$PYTHON_DIR/results/qlearning/general_policy/run_"* 2>/dev/null | head -1)
        if [ -z "$LATEST_RUN" ]; then
            echo -e "${RED}❌ ERREUR: Aucun modèle existant trouvé${NC}"
            echo "Lancez d'abord l'entraînement sans --skip-training"
            exit 1
        fi
        MODEL_PATH="$LATEST_RUN/models/best_model.pkl"
    else
        MODEL_PATH="$LATEST_RUN/adaptive_model.pkl"
    fi
    
    echo ">>> Utilisation du modèle existant: $MODEL_PATH"
    echo ""
fi

# ÉTAPE 2: Génération des actions optimales pour R1 et R2
if [ "$SKIP_ACTIONS" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 2/5: Génération des actions optimales"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Générer les actions pour R1
    echo ">>> Génération des actions optimales pour R1..."
    uv run python generate_optimal_actions.py \
        --model "$MODEL_PATH" \
        --scenario R1 \
        --n-queries 1000
    
    # Générer les actions pour R2
    echo ""
    echo ">>> Génération des actions optimales pour R2..."
    uv run python generate_optimal_actions.py \
        --model "$MODEL_PATH" \
        --scenario R2 \
        --n-queries 1000
    
    echo -e "${GREEN}✅ Actions optimales générées pour R1 et R2${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 2/5: Génération des actions (IGNORÉE)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
fi

# ÉTAPE 3: Compilation Java
if [ "$SKIP_COMPILE" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 3/5: Compilation Java"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    mvn clean package -DskipTests -q
    echo -e "${GREEN}✅ Compilation Java terminée${NC}"
    echo ""
else
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 3/5: Compilation Java (IGNORÉE)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
fi

# ÉTAPE 4: Génération des graphes 2 courbes
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ÉTAPE 4/5: Génération des graphes article (2 courbes)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Graphes simples, dual et all (2 courbes)
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphs

java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphsDual

java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs

echo -e "${GREEN}✅ Graphes article (2 courbes) générés dans images/${NC}"
echo ""

# ÉTAPE 5: Génération des graphes 3 courbes avec actions optimales
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ÉTAPE 5/5: Génération des graphes avec 3 courbes (Actions optimales)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Lancer le Java Gateway Server en arrière-plan
echo ">>> Lancement du Java Gateway Server..."
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs3CurvesWithPy4J \
    > /tmp/java_graphs_3curves.log 2>&1 &
JAVA_PID=$!

echo -e "${GREEN}✅ Java Gateway Server démarré (PID: $JAVA_PID)${NC}"
echo ""

# Attendre que le Gateway soit prêt
echo ">>> Attente du démarrage du Gateway Server..."
sleep 10

# Vérifier que le port est ouvert
echo ">>> Vérification que le Gateway est prêt..."
for i in {1..10}; do
    if lsof -i:25333 > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Gateway prêt sur le port 25333${NC}"
        break
    fi
    if [ $i -eq 10 ]; then
        echo -e "${RED}❌ ERREUR: Gateway non prêt après 20s${NC}"
        tail -30 /tmp/java_graphs_3curves.log
        exit 1
    fi
    echo "   Attente... ($i/10)"
    sleep 2
done

# Attendre un peu plus pour que Java soit vraiment prêt à accepter les connexions
echo ">>> Attente supplémentaire pour stabilisation du Gateway (5s)..."
sleep 5

# Lancer le client Python avec les actions optimales pour R1
echo ">>> Démarrage du client Python avec les actions optimales..."
cd "$PYTHON_DIR"

ACTIONS_R1="results/optimal_actions/optimal_actions_R1.pkl"
if [ ! -f "$ACTIONS_R1" ]; then
    echo -e "${RED}❌ ERREUR: Actions optimales R1 introuvables: $ACTIONS_R1${NC}"
    exit 1
fi

# Lancer Python et capturer la sortie pour déboguer
uv run python connect_to_java_with_optimal_actions.py \
    --actions "$ACTIONS_R1" \
    > /tmp/python_graphs_client.log 2>&1 &
PYTHON_PID=$!

echo -e "${GREEN}✅ Client Python démarré (PID: $PYTHON_PID)${NC}"

# Vérifier que Python démarre correctement
sleep 3
if ! ps -p $PYTHON_PID > /dev/null 2>&1; then
    echo -e "${RED}❌ ERREUR: Le client Python s'est arrêté immédiatement${NC}"
    echo "Logs Python:"
    cat /tmp/python_graphs_client.log
    exit 1
fi

echo -e "${GREEN}✅ Client Python actif${NC}"
echo ""

cd "$PROJECT_ROOT"

# Attendre que Java termine
echo ">>> Génération des graphes avec les actions optimales..."
echo ">>> Cela peut prendre quelques minutes..."
echo ""

wait $JAVA_PID
JAVA_EXIT_CODE=$?

if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Génération des graphes 3 courbes terminée avec succès${NC}"
    echo ""
    echo "Dernières lignes du log Java:"
    tail -20 /tmp/java_graphs_3curves.log
else
    echo -e "${RED}❌ ERREUR lors de la génération des graphes${NC}"
    echo ""
    echo "Logs Java:"
    tail -50 /tmp/java_graphs_3curves.log
    echo ""
    echo "Logs Python:"
    tail -50 /tmp/python_graphs_client.log
    exit 1
fi

# Résumé final
echo ""
echo "============================================================"
echo "  RÉSUMÉ FINAL"
echo "============================================================"
echo ""

if [ "$SKIP_TRAINING" = true ]; then
    echo "⏭️  Entraînement: Ignoré"
else
    echo "✅ Entraînement TCDRM-ADAPTIVE: Terminé ($N_EPISODES épisodes × $N_QUERIES requêtes)"
    echo "   - Fonction de récompense multi-objectif (5 composantes)"
    echo "   - Apprentissage adaptatif des seuils de réplication"
fi

if [ "$SKIP_ACTIONS" = true ]; then
    echo "⏭️  Génération des actions: Ignorée"
else
    echo "✅ Génération des actions: Terminée (R1 et R2)"
fi

if [ "$SKIP_COMPILE" = true ]; then
    echo "⏭️  Compilation Java: Ignorée"
else
    echo "✅ Compilation Java: Terminée"
fi

echo "✅ Génération des graphes: Terminée"
echo ""
echo "Résultats disponibles dans:"
echo "  - Modèle TCDRM-ADAPTIVE: $MODEL_PATH"
echo "  - Métriques d'entraînement: $(dirname $MODEL_PATH)/training_metrics.pkl"
echo "  - Graphiques d'entraînement: $(dirname $MODEL_PATH)/training_metrics.png"
echo "  - Actions optimales: python_rl/results/optimal_actions/"
echo "  - Graphes (2 courbes): images/tcdrm_*.png"
echo "  - Graphes (3 courbes avec RL adaptatif): images/*_3curves.png"
echo ""
echo "Graphes générés:"
echo ""
echo "  📊 Graphes avec 2 courbes (TCDRM Statique vs NOREP):"
ls -1 images/tcdrm_combined_*.png 2>/dev/null | grep -v "_3curves" | sed 's/^/     - /'
echo ""
echo "  📊 Graphes avec 3 courbes (Python RL + TCDRM Statique + NOREP):"
ls -1 images/*_3curves.png 2>/dev/null | sed 's/^/     - /'
echo ""
echo "Voir tous les graphes:"
echo "  open images/*.png"
echo ""
echo "============================================================"
echo "  🎉 Workflow terminé!"
echo "============================================================"
