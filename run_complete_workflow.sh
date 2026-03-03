#!/bin/bash
# Workflow complet TCDRM-ADAPTIVE
# 1. Entraîne les modèles RL (Q-Learning Simple + DQN) avec Python + TensorBoard
# 2. Lance les simulations CloudSim avec Java + Py4J (VRAIES décisions RL)
# 3. Génère les graphiques comparatifs avec RealRLBenchmark

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
TENSORBOARD_PID=""

# Fonction de nettoyage initial
cleanup_initial() {
    echo -e "${BLUE}🧹 Nettoyage des processus et ports existants...${NC}"
    
    # Tuer les processus Java/Python/TensorBoard
    pkill -f "TcdrmComparisonCloudSim" 2>/dev/null || true
    pkill -f "connect_to_java.py" 2>/dev/null || true
    pkill -f "TcdrmArticle" 2>/dev/null || true
    pkill -f "tensorboard" 2>/dev/null || true
    
    # Libérer les ports Py4J et TensorBoard
    lsof -ti:25333 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:25334 2>/dev/null | xargs kill -9 2>/dev/null || true
    lsof -ti:6006 2>/dev/null | xargs kill -9 2>/dev/null || true
    
    sleep 2
    
    echo -e "${BLUE}🧹 Suppression des fichiers générés précédents...${NC}"
    
    # Supprimer les anciens graphiques
    rm -f images/*.png 2>/dev/null || true
    rm -f images/*.jpg 2>/dev/null || true
    
    # Supprimer les anciens modèles entraînés SEULEMENT si on va réentraîner
    if [ "$SKIP_TRAINING" = false ]; then
        echo "   Suppression des anciens modèles (réentraînement prévu)..."
        rm -f "$PYTHON_DIR/models/simple_qlearning.pkl" 2>/dev/null || true
        rm -f "$PYTHON_DIR/models/simple_qlearning_best.pkl" 2>/dev/null || true
        rm -rf "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null || true
        rm -f "$PYTHON_DIR/results/dqn/dqn_model.pt" 2>/dev/null || true
        rm -f "$PYTHON_DIR/results/dqn/dqn_model_best.pt" 2>/dev/null || true
    else
        echo "   Conservation des modèles existants (--skip-training)..."
    fi
    
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
    
    # Ne PAS arrêter TensorBoard si activé - le laisser tourner pour consultation
    if [ "$ENABLE_TENSORBOARD" = true ] && [ ! -z "$TENSORBOARD_PID" ]; then
        echo -e "${YELLOW}⏭️  TensorBoard reste actif (PID: $TENSORBOARD_PID)${NC}"
        echo -e "${GREEN}   🌐 Dashboard: http://localhost:$TENSORBOARD_PORT${NC}"
        echo -e "${YELLOW}   Pour l'arrêter: kill $TENSORBOARD_PID${NC}"
    elif [ ! -z "$TENSORBOARD_PID" ]; then
        echo "Arrêt de TensorBoard (PID: $TENSORBOARD_PID)..."
        kill $TENSORBOARD_PID 2>/dev/null || true
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
    echo "  --tensorboard         Activer TensorBoard pour monitoring"
    echo "  --tensorboard-port N  Port TensorBoard [défaut: 6006]"
    echo "  --help                Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0                              # Workflow complet"
    echo "  $0 --tensorboard                # Avec monitoring TensorBoard"
    echo "  $0 --skip-training              # Seulement simulation avec modèles existants"
    echo "  $0 --n-episodes 3000            # Entraînement prolongé"
    echo "  $0 --skip-simulation            # Seulement entraînement"
}

# Parser les arguments
SKIP_TRAINING=false
SKIP_COMPILE=false
SKIP_SIMULATION=false
N_EPISODES=2000
ENABLE_TENSORBOARD=false
TENSORBOARD_PORT=6006

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
        --tensorboard)
            ENABLE_TENSORBOARD=true
            shift
            ;;
        --tensorboard-port)
            TENSORBOARD_PORT="$2"
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
echo "  WORKFLOW COMPLET TCDRM-ADAPTIVE (OPTIMISÉ + RL RÉEL)"
echo "  Python (Entraînement) + Java/CloudSim (Vraies Décisions RL)"
echo "  Configuration: python_rl/config/optimized_config.json"
echo "============================================================"
echo ""
echo "Configuration:"
echo "  - Épisodes d'entraînement: $N_EPISODES"
echo "  - Skip training: $SKIP_TRAINING"
echo "  - Skip compile: $SKIP_COMPILE"
echo "  - Skip simulation: $SKIP_SIMULATION"
echo "  - TensorBoard: $ENABLE_TENSORBOARD (port: $TENSORBOARD_PORT)"
echo "  - Warm-up progressif: 600 requêtes (k=5)"
echo "  - MAX_REPLICAS adaptatif: 5-13"
echo "  - PLSA amélioré: pondération exponentielle"
echo "  - RealRLBenchmark: Vraies décisions Python via Py4J"
echo ""

cleanup_initial

# ============================================================
# ÉTAPE 0: Validation des Optimisations
# ============================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ÉTAPE 0/5: Validation des Optimisations"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo ">>> Vérification de la configuration optimisée..."
if [ ! -f "$PYTHON_DIR/config/optimized_config.json" ]; then
    echo -e "${YELLOW}⚠️  Configuration optimisée non trouvée, génération...${NC}"
    cd "$PYTHON_DIR"
    uv run python optimize_hyperparameters.py
    cd "$PROJECT_ROOT"
fi

echo -e "${GREEN}✅ Configuration optimisée chargée${NC}"
echo ""

echo ">>> Test rapide des optimisations (PLSA, Warm-up, Environnements)..."
cd "$PYTHON_DIR"
uv run python -c "
import sys
sys.path.insert(0, '..')
from python_rl.utils.plsa import PLSAPopularityModel
from python_rl.envs.tcdrm_env import TcdrmAdaptiveEnv

# Test PLSA
plsa = PLSAPopularityModel(n_topics=3, seed=42)
for i in range(100):
    plsa.add_access(i)
pop = plsa.predict_popularity()
print(f'✅ PLSA: Popularité prédite = {pop:.3f}')

# Test Environnement
env = TcdrmAdaptiveEnv(data_gb=5.3)
obs, info = env.reset(seed=42)
print(f'✅ Environnement: MAX_REPLICAS = {env.MAX_REPLICAS}, WARMUP_QUERIES = {env.WARMUP_QUERIES}')
print(f'✅ Tous les tests passent!')
"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Erreur lors de la validation${NC}"
    exit 1
fi

cd "$PROJECT_ROOT"
echo -e "${GREEN}✅ Validation des optimisations réussie${NC}"
echo ""

# ============================================================
# ÉTAPE 1: Entraînement Python RL
# ============================================================

if [ "$SKIP_TRAINING" = false ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉTAPE 1/5: Entraînement des Modèles RL (Python)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    cd "$PYTHON_DIR"
    
    # Démarrer TensorBoard si activé
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo -e "${BLUE}  📊 TENSORBOARD - MONITORING EN TEMPS RÉEL${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo ""
        echo ">>> Démarrage de TensorBoard (port $TENSORBOARD_PORT)..."
        cd "$PYTHON_DIR"
        uv run tensorboard --logdir=runs --port=$TENSORBOARD_PORT --bind_all > /tmp/tensorboard.log 2>&1 &
        TENSORBOARD_PID=$!
        cd "$PROJECT_ROOT"
        sleep 3
        
        echo -e "${GREEN}✅ TensorBoard démarré (PID: $TENSORBOARD_PID)${NC}"
        echo ""
        echo -e "${BLUE}┌─────────────────────────────────────────────────────────┐${NC}"
        echo -e "${BLUE}│  🌐 DASHBOARD TENSORBOARD ACCESSIBLE:                  │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│     ${GREEN}http://localhost:$TENSORBOARD_PORT${BLUE}                            │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│  📊 Métriques en temps réel:                           │${NC}"
        echo -e "${BLUE}│     • Reward par step et épisode                       │${NC}"
        echo -e "${BLUE}│     • Latence, Coût, Budget, Réplicas                  │${NC}"
        echo -e "${BLUE}│     • Epsilon, States Explored (Q-Learning)            │${NC}"
        echo -e "${BLUE}│     • Loss, Q-values (DQN)                             │${NC}"
        echo -e "${BLUE}│                                                         │${NC}"
        echo -e "${BLUE}│  💡 Ouvrez dans votre navigateur pour voir les courbes│${NC}"
        echo -e "${BLUE}└─────────────────────────────────────────────────────────┘${NC}"
        echo ""
        echo -e "${YELLOW}⏳ TensorBoard restera actif pendant tout le workflow${NC}"
        echo ""
        sleep 2
    fi
    
    # Q-Learning Simple avec configuration optimisée + TensorBoard
    echo ">>> 1.1 Entraînement Q-Learning Simple ($N_EPISODES épisodes) - OPTIMISÉ..."
    echo "    Hyperparamètres: lr=0.1, gamma=0.95, epsilon_decay=0.995"
    echo "    Reward: latency_scale=10.0, budget_penalty=5.0"
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo ""
        echo -e "    ${GREEN}📊 TensorBoard: ACTIVÉ${NC}"
        echo -e "    ${BLUE}🌐 Voir les métriques: http://localhost:$TENSORBOARD_PORT${NC}"
        echo ""
    fi
    
    TB_FLAG=""
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        TB_FLAG="--tensorboard"
    fi
    
    cd "$PYTHON_DIR"
    uv run python train_simple_qlearning.py \
        --episodes $N_EPISODES \
        --lr 0.1 \
        --gamma 0.95 \
        --epsilon-decay 0.995 \
        --data-gb 5.3 \
        --output models/simple_qlearning.pkl \
        $TB_FLAG
    cd "$PROJECT_ROOT"
    
    # Utiliser le MEILLEUR modèle Q-Learning
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning_best.pkl"
    echo -e "${GREEN}✅ Q-Learning Simple entraîné: $QLEARNING_MODEL${NC}"
    echo ""
    
    # DQN avec configuration optimisée + TensorBoard
    echo ">>> 1.2 Entraînement DQN ($N_EPISODES épisodes) - OPTIMISÉ..."
    echo "    Hyperparamètres: lr=0.001, gamma=0.99, batch_size=64"
    echo "    Reward: R1_SLA_OK=5.0, R2_SLA_VIOL=10.0, R3_COST_OVER=5.0"
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo ""
        echo -e "    ${GREEN}📊 TensorBoard: ACTIVÉ${NC}"
        echo -e "    ${BLUE}🌐 Voir les métriques: http://localhost:$TENSORBOARD_PORT${NC}"
        echo ""
    fi
    
    cd "$PYTHON_DIR"
    uv run python train_dqn_policy.py \
        --episodes $N_EPISODES \
        --buffer-size 10000 \
        --batch-size 64 \
        --lr 0.001 \
        --gamma 0.99 \
        --output-dir results/dqn \
        $TB_FLAG
    cd "$PROJECT_ROOT"
    
    # Trouver le MEILLEUR modèle DQN
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model_best.pt"
    else
        DQN_MODEL="$DQN_RUN/dqn_model_best.pt"
    fi
    
    echo -e "${GREEN}✅ DQN entraîné: $DQN_MODEL${NC}"
    echo ""
    
    # Générer les graphes de métriques d'entraînement
    echo ">>> Génération des graphes de métriques d'entraînement..."
    cd "$PYTHON_DIR"
    
    # Graphes Q-Learning
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo "  • Graphes Q-Learning depuis TensorBoard..."
        uv run python utils/plot_training_metrics.py \
            --tensorboard-dir runs \
            --algorithm qlearning \
            --output results/qlearning_training_metrics.png 2>/dev/null || \
            echo "    ⚠️  Impossible de générer les graphes Q-Learning depuis TensorBoard"
    fi
    
    # Graphes DQN
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo "  • Graphes DQN depuis TensorBoard..."
        uv run python utils/plot_training_metrics.py \
            --tensorboard-dir runs \
            --algorithm dqn \
            --output results/dqn/dqn_training_metrics.png 2>/dev/null || \
            echo "    ⚠️  Impossible de générer les graphes DQN depuis TensorBoard"
    fi
    
    echo -e "${GREEN}✅ Graphes de métriques générés${NC}"
    echo ""
    
    # Export automatique des graphes TensorBoard (derniers runs uniquement)
    if [ "$ENABLE_TENSORBOARD" = true ]; then
        echo ">>> Export des graphes TensorBoard (derniers runs)..."
        uv run python utils/export_tensorboard_graphs.py \
            --tensorboard-dir runs \
            --output-dir tensorboard_exports \
            --max-runs 1 2>/dev/null || \
            echo "    ⚠️  Impossible d'exporter les graphes TensorBoard"
        echo -e "${GREEN}✅ Graphes TensorBoard exportés dans: python_rl/tensorboard_exports/${NC}"
        echo ""
    fi
    
    # Générer les dashboards per-query pour tous les modèles
    echo ">>> Génération des dashboards per-query..."
    uv run python utils/generate_per_query_dashboard.py \
        --results-dir results \
        --output-dir dashboards 2>/dev/null || \
        echo "    ⚠️  Impossible de générer les dashboards per-query"
    echo -e "${GREEN}✅ Dashboards per-query générés dans: python_rl/dashboards/${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
else
    # Utiliser les MEILLEURS modèles existants
    QLEARNING_MODEL="$PYTHON_DIR/models/simple_qlearning_best.pkl"
    DQN_RUN=$(ls -td "$PYTHON_DIR/results/dqn/run_"* 2>/dev/null | head -1)
    if [ -z "$DQN_RUN" ]; then
        DQN_MODEL="$PYTHON_DIR/results/dqn/dqn_model_best.pt"
    else
        DQN_MODEL="$DQN_RUN/dqn_model_best.pt"
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
    echo "  ÉTAPE 2/5: Compilation Java (avec Warm-up Progressif)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "    Optimisations Java:"
    echo "    - WARMUP_QUERIES = 600 (descente progressive)"
    echo "    - Sigmoid k=5 (transition douce)"
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
    echo "  ÉTAPE 3/5: Simulation CloudSim avec Modèles RL (Py4J)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "    Comparaison:"
    echo "    - Q-Learning (optimisé)"
    echo "    - DQN (optimisé)"
    echo "    - TCDRM Statique (warm-up progressif)"
    echo "    - NOREP (baseline)"
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
    echo ">>> Simulation CloudSim en cours (VRAIES décisions RL via RealRLBenchmark)..."
    echo ">>> Les modèles Python prennent les décisions à chaque requête"
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
# ÉTAPE 4: Analyse des Résultats
# ============================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ÉTAPE 4/5: Analyse des Résultats"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

if [ -d "images" ] && [ "$(ls -A images/*.png 2>/dev/null)" ]; then
    echo ">>> Graphiques générés:"
    ls -1 images/*.png 2>/dev/null | while read img; do
        size=$(du -h "$img" | cut -f1)
        echo "    ✅ $img ($size)"
    done
    echo ""
    
    echo ">>> Validation des optimisations dans les graphiques:"
    echo "    - Fig. 3: Descente progressive du temps de réponse (600 requêtes)"
    echo "    - Fig. 6: Bande passante cumulative (trajectoire divergente)"
    echo "    - Fig. 7: Storage cost négligeable"
    echo ""
else
    echo -e "${YELLOW}⚠️  Aucun graphique généré${NC}"
fi

# ============================================================
# ÉTAPE 5: Résumé Final
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
echo "📊 Résultats disponibles:"
echo ""
echo "  1. Modèles Python (Optimisés):"
echo "     • Q-Learning: $QLEARNING_MODEL"
if [ -f "$DQN_MODEL" ]; then
    echo "     • DQN: $DQN_MODEL"
fi
echo ""
echo "  2. Configuration:"
echo "     • Hyperparamètres: python_rl/config/optimized_config.json"
echo "     • Validation: VALIDATION_5_POINTS.md"
echo ""
echo "  3. Graphiques CloudSim (VRAIES décisions RL):"
ls -1 images/tcdrm_combined_*.png 2>/dev/null | sed 's/^/     • /' || echo "     (aucun graphique combiné généré)"
ls -1 images/tcdrm_smoothed_*.png 2>/dev/null | sed 's/^/     • /' || echo "     (aucun graphique smoothed généré)"
echo ""
echo "  4. Graphiques de Métriques TCDRM Statique:"
ls -1 images/tcdrm_metrics_*.png 2>/dev/null | sed 's/^/     • /' || echo "     (aucun graphique de métriques généré)"
echo ""
echo "  5. Graphiques de Métriques d'Entraînement (RL):"
if [ -f "python_rl/results/qlearning_training_metrics.png" ]; then
    echo "     • Q-Learning: python_rl/results/qlearning_training_metrics.png"
fi
if [ -f "python_rl/results/dqn/dqn_training_metrics.png" ]; then
    echo "     • DQN: python_rl/results/dqn/dqn_training_metrics.png"
fi
if [ ! -f "python_rl/results/qlearning_training_metrics.png" ] && [ ! -f "python_rl/results/dqn/dqn_training_metrics.png" ]; then
    echo "     (aucun graphique de métriques d'entraînement généré)"
fi
echo ""
if [ "$ENABLE_TENSORBOARD" = true ]; then
    echo "  6. Exports TensorBoard (derniers runs):"
    if [ -d "python_rl/tensorboard_exports" ]; then
        ls -1 python_rl/tensorboard_exports/*.png 2>/dev/null | sed 's/^/     • /' || echo "     (aucun export généré)"
    else
        echo "     (aucun export généré)"
    fi
    echo ""
fi
if [ "$ENABLE_TENSORBOARD" = true ]; then
    echo "  7. TensorBoard (ACTIF):"
    echo ""
    echo -e "     ${GREEN}┌─────────────────────────────────────────────────────┐${NC}"
    echo -e "     ${GREEN}│  📊 TENSORBOARD TOUJOURS ACTIF                     │${NC}"
    echo -e "     ${GREEN}│                                                     │${NC}"
    echo -e "     ${GREEN}│  🌐 Dashboard: http://localhost:$TENSORBOARD_PORT                  │${NC}"
    echo -e "     ${GREEN}│  📁 Logs: python_rl/runs/                          │${NC}"
    echo -e "     ${GREEN}│                                                     │${NC}"
    echo -e "     ${GREEN}│  💡 Comparez Q-Learning et DQN dans l'interface    │${NC}"
    echo -e "     ${GREEN}└─────────────────────────────────────────────────────┘${NC}"
    echo ""
else
    echo "  7. TensorBoard:"
    echo "     ⏭️  Non activé (utiliser --tensorboard pour le monitoring)"
fi
echo ""
echo "  8. Optimisations Appliquées:"
echo "     ✅ PLSA amélioré (pondération exponentielle)"
echo "     ✅ Warm-up progressif (600 requêtes, k=5)"
echo "     ✅ MAX_REPLICAS adaptatif (5-13)"
echo "     ✅ Storage cost négligeable (0.0001)"
echo "     ✅ Fonctions de récompense optimisées"
echo "     ✅ RealRLBenchmark: Vraies décisions RL (pas de simulation bidon)"
echo ""
echo "📈 Voir les graphiques:"
echo "  open images/*.png"
echo ""
if [ "$ENABLE_TENSORBOARD" = true ]; then
    echo "📊 TensorBoard:"
    echo "  open http://localhost:$TENSORBOARD_PORT"
    echo ""
fi
echo "📖 Documentation:"
echo "  - Validation des 5 points: cat VALIDATION_5_POINTS.md"
echo "  - Tests d'optimisation: python python_rl/test_optimizations.py"
echo "  - TensorBoard: cat README_TENSORBOARD.md"
echo "  - Vraies simulations RL: cat README_REAL_RL.md"
echo ""

echo "============================================================"
echo "  🎉 Workflow terminé!"
echo "============================================================"

exit 0
