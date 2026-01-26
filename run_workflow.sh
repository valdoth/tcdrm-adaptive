#!/bin/bash
# Script unifié pour le workflow complet TCDRM-ADAPTIVE
# Combine: Entraînement Python + Génération de graphes avec VRAI modèle RL via Py4J

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
SCENARIO="r1"  # Par défaut R1
PYTHON_PID=""
JAVA_PID=""

# Fonction de nettoyage initial (avant démarrage)
cleanup_initial() {
    echo -e "${BLUE}🧹 Nettoyage des processus et ports existants...${NC}"
    
    # Tuer tous les processus Java/Python liés au workflow
    pkill -f "TcdrmArticleAllGraphs3CurvesWithPy4J" 2>/dev/null || true
    pkill -f "connect_to_java_for_graphs.py" 2>/dev/null || true
    pkill -f "TcdrmArticleGraphs" 2>/dev/null || true
    
    # Libérer les ports Py4J (25333 et 25334)
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:25334 2>/dev/null | xargs kill -9 2>/dev/null || true
    
    # Attendre un peu pour que les ports soient libérés
    sleep 2
    
    echo -e "${GREEN}✅ Nettoyage initial terminé${NC}"
    echo ""
}

# Fonction de nettoyage (à la fin)
cleanup() {
    echo ""
    echo -e "${BLUE}🧹 Nettoyage...${NC}"
    
    # Arrêter le client Python
    if [ ! -z "$PYTHON_PID" ]; then
        echo "Arrêt du client Python (PID: $PYTHON_PID)..."
        kill $PYTHON_PID 2>/dev/null || true
    fi
    
    # Arrêter Java
    if [ ! -z "$JAVA_PID" ]; then
        echo "Arrêt de Java (PID: $JAVA_PID)..."
        kill $JAVA_PID 2>/dev/null || true
    fi
    
    # Tuer tous les processus restants
    pkill -f "connect_to_java_for_graphs.py" 2>/dev/null || true
    pkill -f "TcdrmArticleAllGraphs" 2>/dev/null || true
    
    echo -e "${GREEN}✅ Nettoyage terminé${NC}"
}

# Configurer le trap pour nettoyer à la sortie
trap cleanup EXIT INT TERM

# Fonction d'aide
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-training    Sauter l'entraînement Python (utiliser modèles existants)"
    echo "  --skip-compile     Sauter la compilation Java"
    echo "  --scenario SCENARIO Scénario à utiliser (r1, r2, r3) [défaut: r1]"
    echo "  --help             Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                           # Workflow complet avec R1"
    echo "  $0 --scenario r2             # Workflow complet avec R2"
    echo "  $0 --skip-training           # Sauter entraînement, utiliser modèles existants"
    echo "  $0 --skip-training --skip-compile  # Seulement génération de graphes"
}

# Parser les arguments
SKIP_TRAINING=false
SKIP_COMPILE=false

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
        --scenario)
            SCENARIO="$2"
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

# Valider le scénario
case $SCENARIO in
    r1|r2|r3)
        ;;
    *)
        echo -e "${RED}❌ Scénario invalide: $SCENARIO (doit être r1, r2 ou r3)${NC}"
        exit 1
        ;;
esac

# Mapper scénario vers nom complet
case $SCENARIO in
    r1)
        SCENARIO_FULL="r1_simple"
        DATA_GB="5.3"
        ;;
    r2)
        SCENARIO_FULL="r2_complex"
        DATA_GB="11.9"
        ;;
    r3)
        SCENARIO_FULL="r3_large"
        DATA_GB="20.0"
        ;;
esac

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  TCDRM-ADAPTIVE: Workflow Complet${NC}"
echo -e "${BLUE}  Scénario: ${SCENARIO_FULL} (${DATA_GB} GB)${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Nettoyage initial des processus et ports
cleanup_initial

# ============================================================
# ÉTAPE 1: Entraînement Python (optionnel)
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  ÉTAPE 1/5: Entraînement Python RL (Tabular Q-Learning)${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Vérifier si uv est installé
    if ! command -v uv &> /dev/null; then
        echo -e "${RED}❌ uv n'est pas installé${NC}"
        echo "Installation: curl -LsSf https://astral.sh/uv/install.sh | sh"
        exit 1
    fi
    
    # Entraîner le scénario spécifique
    echo -e "${BLUE}>>> Entraînement pour ${SCENARIO_FULL} (${DATA_GB} GB)${NC}"
    ./run_experiments.sh train-${SCENARIO}
    
    echo ""
    echo -e "${GREEN}✅ Entraînement terminé${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
else
    echo -e "${YELLOW}⏭️  Entraînement Python ignoré (--skip-training)${NC}"
    echo ""
fi

# ============================================================
# ÉTAPE 2: Compilation Java (optionnel)
# ============================================================

if [ "$SKIP_COMPILE" = false ]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  ÉTAPE 2/5: Compilation Java${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    
    # Vérifier Maven
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven n'est pas installé${NC}"
        echo "Installation: brew install maven"
        exit 1
    fi
    
    echo -e "${BLUE}>>> Compilation Maven...${NC}"
    mvn clean package -q
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Erreur de compilation${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}✅ Compilation réussie${NC}"
    echo ""
else
    echo -e "${YELLOW}⏭️  Compilation Java ignorée (--skip-compile)${NC}"
    echo ""
fi

# ============================================================
# ÉTAPE 3: Vérification du modèle entraîné
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 3/5: Vérification du modèle Python RL${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Trouver le modèle entraîné le plus récent
MODEL_PATH=$(find python_rl/results/qlearning/${SCENARIO_FULL} -name "best_model.pkl" -type f 2>/dev/null | sort -r | head -1)

if [ -z "$MODEL_PATH" ]; then
    echo -e "${RED}❌ Aucun modèle entraîné trouvé pour ${SCENARIO_FULL}${NC}"
    echo "Entraînez d'abord le modèle avec:"
    echo "  $0 --scenario ${SCENARIO}"
    exit 1
fi

echo -e "${GREEN}>>> Modèle trouvé: ${MODEL_PATH}${NC}"
echo -e "${BLUE}>>> Architecture: Java Gateway Server + Python Client${NC}"
echo ""

# Sauvegarder le chemin du modèle
export PYTHON_MODEL_PATH="$MODEL_PATH"

# ============================================================
# ÉTAPE 4: Génération des graphes article (2 courbes)
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 4/5: Génération des graphes article (2 courbes)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo -e "${BLUE}>>> Génération des graphes article (TcdrmArticleGraphs)...${NC}"
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphs

echo ""
echo -e "${BLUE}>>> Génération des graphes dual (TcdrmArticleGraphsDual)...${NC}"
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphsDual

echo ""
echo -e "${BLUE}>>> Génération de tous les graphes article (TcdrmArticleAllGraphs)...${NC}"
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs

echo ""
echo -e "${GREEN}✅ Graphes article (2 courbes) générés dans images/${NC}"
echo ""

# ============================================================
# ÉTAPE 5: Génération des graphes avec 3 courbes (VRAI modèle RL)
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 5/5: Génération des graphes avec 3 courbes (VRAI modèle RL)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo -e "${BLUE}>>> Lancement du Java Gateway Server...${NC}"
echo ""

# Lancer Java en arrière-plan
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs3CurvesWithPy4J > /tmp/java_graphs_3curves.log 2>&1 &
JAVA_PID=$!

echo -e "${GREEN}✅ Java Gateway Server démarré (PID: $JAVA_PID)${NC}"
echo ""

# Attendre que le GatewayServer soit prêt
echo -e "${BLUE}>>> Attente du démarrage du Gateway Server...${NC}"
sleep 10

# Vérifier que le port est ouvert
echo -e "${BLUE}>>> Vérification que le Gateway est prêt...${NC}"
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
echo -e "${BLUE}>>> Attente supplémentaire pour stabilisation du Gateway (5s)...${NC}"
sleep 5

# Démarrer le client Python avec le modèle entraîné
echo -e "${BLUE}>>> Démarrage du client Python avec le modèle entraîné...${NC}"
cd python_rl
uv run python connect_to_java_for_graphs.py --model "../$MODEL_PATH" > /tmp/python_graphs_client.log 2>&1 &
PYTHON_PID=$!
cd ..

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

# Attendre que Java termine la génération des graphes
echo -e "${BLUE}>>> Génération des graphes avec le VRAI modèle Python RL...${NC}"
echo -e "${BLUE}>>> Cela peut prendre quelques minutes...${NC}"
echo ""

wait $JAVA_PID
JAVA_EXIT_CODE=$?

# Arrêter le client Python
echo -e "${BLUE}>>> Arrêt du client Python...${NC}"
kill $PYTHON_PID 2>/dev/null || true

echo ""
if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Génération des graphes 3 courbes terminée avec succès${NC}"
    echo ""
    echo -e "${BLUE}Dernières lignes du log Java:${NC}"
    tail -30 /tmp/java_graphs_3curves.log
else
    echo -e "${RED}❌ Erreur lors de la génération des graphes (code: $JAVA_EXIT_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Logs Java:${NC}"
    tail -50 /tmp/java_graphs_3curves.log
    echo ""
    echo -e "${YELLOW}Logs Python:${NC}"
    tail -30 /tmp/python_graphs_client.log
fi
echo ""

# ============================================================
# Résumé Final
# ============================================================

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  RÉSUMÉ FINAL${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

if [ "$SKIP_TRAINING" = false ]; then
    echo -e "${GREEN}✅ Entraînement Python: ${SCENARIO_FULL}${NC}"
else
    echo -e "${YELLOW}⏭️  Entraînement Python: Ignoré${NC}"
fi

if [ "$SKIP_COMPILE" = false ]; then
    echo -e "${GREEN}✅ Compilation Java: Réussie${NC}"
else
    echo -e "${YELLOW}⏭️  Compilation Java: Ignorée${NC}"
fi

echo -e "${GREEN}✅ Génération des graphes: Terminée${NC}"

echo ""
echo "Résultats disponibles dans:"
echo "  - Modèles Python: python_rl/results/qlearning/${SCENARIO_FULL}/"
echo "  - Graphes (2 courbes): images/tcdrm_*.png"
echo "  - Graphes (3 courbes avec VRAI RL): images/*_3curves.png"

echo ""
echo "Graphes générés:"
echo ""
echo "  📊 Graphes avec 2 courbes (TCDRM Statique vs NOREP):"
ls -1 images/tcdrm_combined_*.png 2>/dev/null | grep -v "_3curves" | sed 's/^/     - /' || echo "     (aucun graphe)"

echo ""
echo "  📊 Graphes avec 3 courbes (Python RL + TCDRM Statique + NOREP):"
ls -1 images/*_3curves.png 2>/dev/null | sed 's/^/     - /' || echo "     (aucun graphe)"

echo ""
echo "Voir tous les graphes:"
echo "  open images/*.png"
echo ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${GREEN}  🎉 Workflow terminé!${NC}"
echo -e "${BLUE}============================================================${NC}"

# Le cleanup sera appelé automatiquement via trap EXIT
exit $JAVA_EXIT_CODE
