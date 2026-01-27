#!/bin/bash
# Script simplifié pour TCDRM-ADAPTIVE
# Entraînement rapide et génération de graphes

set -e

# Couleurs
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_DIR="$PROJECT_ROOT/python_rl"

echo "============================================================"
echo "  TCDRM-ADAPTIVE - Entraînement et Évaluation"
echo "============================================================"
echo ""

# Fonction d'aide
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --train               Entraîner un nouveau modèle"
    echo "  --episodes N          Nombre d'épisodes [défaut: 200]"
    echo "  --queries N           Requêtes par épisode [défaut: 1000]"
    echo "  --evaluate            Évaluer le dernier modèle"
    echo "  --help                Afficher cette aide"
    echo ""
    echo "Exemples:"
    echo "  $0 --train --episodes 50 --queries 500    # Entraînement rapide"
    echo "  $0 --train                                 # Entraînement complet"
    echo "  $0 --evaluate                              # Évaluer modèle"
}

# Parser arguments
MODE=""
N_EPISODES=200
N_QUERIES=1000

while [[ $# -gt 0 ]]; do
    case $1 in
        --train)
            MODE="train"
            shift
            ;;
        --evaluate)
            MODE="evaluate"
            shift
            ;;
        --episodes)
            N_EPISODES="$2"
            shift 2
            ;;
        --queries)
            N_QUERIES="$2"
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

if [ -z "$MODE" ]; then
    echo -e "${RED}❌ Veuillez spécifier --train ou --evaluate${NC}"
    show_help
    exit 1
fi

cd "$PYTHON_DIR"

if [ "$MODE" = "train" ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ENTRAÎNEMENT TCDRM-ADAPTIVE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "Configuration:"
    echo "  - Épisodes: $N_EPISODES"
    echo "  - Requêtes par épisode: $N_QUERIES"
    echo "  - Fonction de récompense: Multi-objectif (5 composantes)"
    echo ""
    
    uv run python train_adaptive_policy.py \
        --episodes $N_EPISODES \
        --queries $N_QUERIES
    
    echo ""
    echo -e "${GREEN}✅ Entraînement terminé${NC}"
    
elif [ "$MODE" = "evaluate" ]; then
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ÉVALUATION TCDRM-ADAPTIVE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    # Trouver le dernier modèle
    LATEST_RUN=$(ls -td results/tcdrm_adaptive/full_run_* 2>/dev/null | head -1)
    if [ -z "$LATEST_RUN" ]; then
        echo -e "${RED}❌ Aucun modèle trouvé. Entraînez d'abord avec --train${NC}"
        exit 1
    fi
    
    MODEL_PATH="$LATEST_RUN/adaptive_model.pkl"
    echo "Modèle: $MODEL_PATH"
    echo ""
    
    uv run python -c "
import pickle
import numpy as np
from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent

# Charger modèle
print('Chargement du modèle...')
with open('$MODEL_PATH', 'rb') as f:
    model_data = pickle.load(f)

agent = TabularQLearningAgent(
    n_states=model_data['n_states'],
    n_actions=model_data['n_actions']
)
agent.q_table = model_data['q_table']
print('✓ Modèle chargé')
print()

# Tester sur R1 et R2
scenarios = [('R1', 5.3), ('R2', 11.9)]

for name, size in scenarios:
    print(f'=== Test {name} ({size} GB) ===')
    env = TcdrmAdaptiveEnv(data_gb=size)
    state, _ = env.reset(seed=42)
    
    total_reward = 0
    replica_changes = 0
    last_replicas = 0
    
    for i in range(1000):
        state_idx = agent.discretize_state(state)
        action = agent.select_action(state_idx, training=False)
        state, reward, terminated, truncated, info = env.step(action)
        total_reward += reward
        
        if info['replicas'] != last_replicas:
            replica_changes += 1
            last_replicas = info['replicas']
        
        if terminated or truncated:
            break
    
    print(f'  Reward total: {total_reward:.2f}')
    print(f'  Coût total: {info[\"total_cost\"]:.2f}')
    print(f'  Violations SLA: {info[\"sla_violations\"]}')
    print(f'  Changements réplicas: {replica_changes}')
    print(f'  Réplicas finaux: {info[\"replicas\"]}')
    print()
"
    
    echo -e "${GREEN}✅ Évaluation terminée${NC}"
fi

echo ""
echo "============================================================"
echo "  Terminé!"
echo "============================================================"
