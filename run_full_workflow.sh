#!/bin/bash

# Script complet pour exécuter tout le workflow TCDRM-ADAPTIVE
# Combine l'entraînement Python, le serveur Py4J et la comparaison Java

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
PY4J_PID=""
SCENARIO="r1"  # Par défaut R1
PYTHON_PID=""
JAVA_PID=""

# Fonction de nettoyage
cleanup() {
    echo ""
    echo -e "${BLUE}🧹 Nettoyage...${NC}"
    
    # Arrêter le client Python s'il est actif
    if [ ! -z "$PYTHON_PID" ]; then
        echo "Arrêt du client Python (PID: $PYTHON_PID)..."
        kill $PYTHON_PID 2>/dev/null || true
    fi
    
    # Arrêter le client Python combiné s'il est actif
    if [ ! -z "$PYTHON_COMBINED_PID" ]; then
        echo "Arrêt du client Python combiné (PID: $PYTHON_COMBINED_PID)..."
        kill $PYTHON_COMBINED_PID 2>/dev/null || true
    fi
    
    # Arrêter Java s'il est actif
    if [ ! -z "$JAVA_PID" ]; then
        echo "Arrêt de Java (PID: $JAVA_PID)..."
        kill $JAVA_PID 2>/dev/null || true
    fi
    
    # Arrêter Java combiné s'il est actif
    if [ ! -z "$COMBINED_PID" ]; then
        echo "Arrêt de Java combiné (PID: $COMBINED_PID)..."
        kill $COMBINED_PID 2>/dev/null || true
    fi
    
    # Tuer tous les processus restants
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmComparisonCloudSim" 2>/dev/null || true
    pkill -f "TcdrmCombinedComparisonGraphs" 2>/dev/null || true
    pkill -f "TcdrmArticleGraphs" 2>/dev/null || true
    
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
    echo "  $0 --skip-training --skip-compile  # Seulement comparaison"
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
echo -e "${BLUE}  TCDRM-ADAPTIVE: Workflow Complet (3 Approches)${NC}"
echo -e "${BLUE}  Scénario: ${SCENARIO_FULL} (${DATA_GB} GB)${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# ============================================================
# ÉTAPE 1: Entraînement Python (optionnel)
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  ÉTAPE 1/4: Entraînement Python RL (Tabular Q-Learning)${NC}"
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
    echo -e "${YELLOW}  ÉTAPE 2/4: Compilation Java${NC}"
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
# ÉTAPE 3: Préparation du modèle Python pour Py4J
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 3/4: Préparation du modèle Python pour Py4J${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Trouver le modèle entraîné
MODEL_PATH=$(find python_rl/results/qlearning/${SCENARIO_FULL} -name "best_model.pkl" -type f 2>/dev/null | sort -r | head -1)

if [ -z "$MODEL_PATH" ]; then
    echo -e "${RED}❌ Aucun modèle trouvé pour ${SCENARIO_FULL}${NC}"
    echo "Entraînez d'abord le modèle Python"
    exit 1
fi

echo -e "${GREEN}>>> Modèle trouvé: ${MODEL_PATH}${NC}"
echo -e "${BLUE}>>> Architecture Py4J: Java GatewayServer + Python Client${NC}"
echo ""

# Sauvegarder le chemin du modèle pour le client Python
export PYTHON_MODEL_PATH="$MODEL_PATH"

# ============================================================
# ÉTAPE 4: Comparaison Java CloudSim
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 4/4: Comparaison Java CloudSim (3 Approches)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo -e "${BLUE}>>> Lancement de Java GatewayServer...${NC}"
echo ""

# Lancer Java en arrière-plan (démarre le GatewayServer Py4J)
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim > /tmp/java_comparison.log 2>&1 &
JAVA_PID=$!

echo -e "${GREEN}✅ Java GatewayServer démarré (PID: $JAVA_PID)${NC}"
echo ""

# Attendre que le GatewayServer soit prêt
echo -e "${BLUE}>>> Attente du démarrage du GatewayServer (5s)...${NC}"
sleep 5

# Démarrer le client Python qui se connecte au GatewayServer Java
echo -e "${BLUE}>>> Démarrage du client Python...${NC}"
cd python_rl
uv run python connect_to_java.py --model "../$PYTHON_MODEL_PATH" > /tmp/python_client.log 2>&1 &
PYTHON_PID=$!
cd ..

echo -e "${GREEN}✅ Client Python démarré (PID: $PYTHON_PID)${NC}"
echo ""

# Attendre que Java termine la comparaison
echo -e "${BLUE}>>> Attente de la fin de la comparaison...${NC}"
wait $JAVA_PID
JAVA_EXIT_CODE=$?

# Arrêter le client Python
echo -e "${BLUE}>>> Arrêt du client Python...${NC}"
kill $PYTHON_PID 2>/dev/null || true

echo ""
if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Comparaison terminée avec succès${NC}"
    echo ""
    echo -e "${BLUE}Dernières lignes du log Java:${NC}"
    tail -50 /tmp/java_comparison.log
else
    echo -e "${RED}❌ Erreur lors de la comparaison (code: $JAVA_EXIT_CODE)${NC}"
    echo ""
    echo -e "${YELLOW}Logs Java:${NC}"
    tail -100 /tmp/java_comparison.log
    echo ""
    echo -e "${YELLOW}Logs Python:${NC}"
    tail -50 /tmp/python_client.log
fi
echo ""

# ============================================================
# ÉTAPE 5: Génération des graphes combinés (images/)
# ============================================================

echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  ÉTAPE 5/5: Génération des graphes combinés (2 par image)${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo -e "${BLUE}>>> Génération des graphes combinés avec 3 courbes...${NC}"
echo ""

# Lancer Java pour générer les graphes combinés
java -cp "target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar" \
    org.tcdrm.adaptive.examples.TcdrmCombinedComparisonGraphs > /tmp/combined_graphs.log 2>&1 &
COMBINED_PID=$!

# Attendre 5 secondes pour le démarrage du Gateway
sleep 5

# Démarrer le client Python
echo -e "${BLUE}>>> Connexion Python pour les graphes combinés...${NC}"
cd python_rl
uv run python connect_to_java.py --model "../$PYTHON_MODEL_PATH" > /tmp/python_combined.log 2>&1 &
PYTHON_COMBINED_PID=$!
cd ..

# Attendre que Java termine
wait $COMBINED_PID
COMBINED_EXIT_CODE=$?

# Arrêter le client Python
kill $PYTHON_COMBINED_PID 2>/dev/null || true

echo ""
if [ $COMBINED_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Graphes combinés générés avec succès${NC}"
    echo ""
    echo -e "${BLUE}Graphes générés dans images/:${NC}"
    ls -lh images/*.png 2>/dev/null | awk '{print "   - " $9 " (" $5 ")"}'
else
    echo -e "${RED}❌ Erreur lors de la génération des graphes combinés${NC}"
    echo ""
    echo -e "${YELLOW}Logs:${NC}"
    tail -50 /tmp/combined_graphs.log
fi
echo ""

# Générer aussi les graphes article et autres (sans Py4J, dans images/)
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
echo -e "${GREEN}✅ Tous les graphes générés${NC}"
echo ""
echo -e "${BLUE}Note: Graphes avec 3 courbes (Python RL, TCDRM Statique, NOREP) dans results/cloudsim_comparison/${NC}"
echo -e "${BLUE}Note: Graphes article (2 courbes) dans images/${NC}"
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

echo -e "${GREEN}✅ Comparaison CloudSim: Réussie${NC}"
echo -e "${GREEN}✅ Génération des graphes: Terminée${NC}"

echo ""
echo "Résultats disponibles dans:"
echo "  - Modèles Python: python_rl/results/qlearning/${SCENARIO_FULL}/"
echo "  - Graphes avec 3 courbes (Python RL, TCDRM Statique, NOREP): results/cloudsim_comparison/"
echo "  - Graphes article (2 courbes): images/"

echo ""
echo "Graphes générés:"
echo ""
echo "  📊 Dans results/cloudsim_comparison/ (avec 3 courbes):"
ls -1 results/cloudsim_comparison/*.png 2>/dev/null | sed 's/^/     - /' || echo "     (aucun graphe)"

echo ""
echo "  📊 Dans images/ (graphes article):"
ls -1 images/*.png 2>/dev/null | sed 's/^/     - /' || echo "     (aucun graphe)"

echo ""
echo "Voir tous les graphes:"
echo "  open results/cloudsim_comparison/*.png images/*.png"
echo ""

echo -e "${BLUE}============================================================${NC}"
echo -e "${GREEN}  🎉 Workflow terminé!${NC}"
echo -e "${BLUE}============================================================${NC}"

# Le cleanup sera appelé automatiquement via trap EXIT
exit $JAVA_EXIT_CODE
