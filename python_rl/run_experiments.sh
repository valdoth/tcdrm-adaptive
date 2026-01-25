#!/bin/bash

# Script d'exécution des expériences TCDRM-ADAPTIVE v2.0
# Focus: Q-Learning tabulaire pour décisions adaptatives de réplication

set -e

# Couleurs
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  TCDRM-ADAPTIVE v2.0: Expériences Q-Learning${NC}"
echo -e "${BLUE}  Mécanisme Adaptatif Auto-Apprenant${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Créer les répertoires nécessaires
mkdir -p results/qlearning
mkdir -p results/evaluation
mkdir -p results/comparison
mkdir -p logs

# Fonction pour entraîner Q-Learning
train_qlearning() {
    local data_gb=$1
    local episodes=$2
    local label=$3
    
    echo -e "${YELLOW}>>> Entraînement Q-Learning: ${label} (${data_gb} GB)${NC}"
    uv run python train.py \
        --data-gb ${data_gb} \
        --episodes ${episodes} \
        --output-dir results/qlearning/${label}
    echo -e "${GREEN}✅ Entraînement ${label} terminé${NC}"
    echo ""
}

# Fonction pour évaluer un modèle
evaluate_model() {
    local model_path=$1
    local data_gb=$2
    local label=$3
    
    echo -e "${YELLOW}>>> Évaluation: ${label}${NC}"
    uv run python evaluate_model.py \
        --model ${model_path} \
        --data-gb ${data_gb} \
        --episodes 100 \
        --output-dir results/evaluation/${label}
    echo -e "${GREEN}✅ Évaluation ${label} terminée${NC}"
    echo ""
}

# Menu principal
case "${1:-all}" in
    train-r1)
        echo "Entraînement pour Requête Simple (R1: 5.3 GB)"
        train_qlearning 5.3 1000 "r1_simple"
        ;;
    
    train-r2)
        echo "Entraînement pour Requête Complexe (R2: 11.9 GB)"
        train_qlearning 11.9 1000 "r2_complex"
        ;;
    
    train-r3)
        echo "Entraînement pour Requête Volumineuse (R3: 20 GB)"
        train_qlearning 20.0 1000 "r3_large"
        ;;
    
    train-all)
        echo "Entraînement pour tous les scénarios"
        train_qlearning 5.3 1000 "r1_simple"
        train_qlearning 11.9 1000 "r2_complex"
        train_qlearning 20.0 1000 "r3_large"
        ;;
    
    evaluate)
        echo "Évaluation des modèles entraînés"
        
        # Trouver les derniers modèles
        if [ -d "results/qlearning/r1_simple" ]; then
            latest_r1=$(ls -td results/qlearning/r1_simple/run_* 2>/dev/null | head -1)
            if [ -n "$latest_r1" ]; then
                evaluate_model "${latest_r1}/models/best_model.pkl" 5.3 "r1_simple"
            fi
        fi
        
        if [ -d "results/qlearning/r2_complex" ]; then
            latest_r2=$(ls -td results/qlearning/r2_complex/run_* 2>/dev/null | head -1)
            if [ -n "$latest_r2" ]; then
                evaluate_model "${latest_r2}/models/best_model.pkl" 11.9 "r2_complex"
            fi
        fi
        
        if [ -d "results/qlearning/r3_large" ]; then
            latest_r3=$(ls -td results/qlearning/r3_large/run_* 2>/dev/null | head -1)
            if [ -n "$latest_r3" ]; then
                evaluate_model "${latest_r3}/models/best_model.pkl" 20.0 "r3_large"
            fi
        fi
        ;;
    
    all)
        echo "Workflow complet: Entraînement + Évaluation"
        
        # Entraîner tous les scénarios
        train_qlearning 5.3 1000 "r1_simple"
        train_qlearning 11.9 1000 "r2_complex"
        train_qlearning 20.0 1000 "r3_large"
        
        # Évaluer tous les modèles
        sleep 2
        $0 evaluate
        
        echo ""
        echo -e "${GREEN}============================================================${NC}"
        echo -e "${GREEN}  ✅ Entraînement terminé avec succès!${NC}"
        echo -e "${GREEN}============================================================${NC}"
        echo ""
        echo "Modèles entraînés dans: results/qlearning/"
        echo ""
        echo "Prochaine étape: Simulations et comparaisons en Java"
        echo "  cd .."
        echo "  mvn clean package"
        echo "  java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \\"
        echo "    org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim"
        ;;
    
    clean)
        echo "Nettoyage des résultats précédents"
        read -p "Êtes-vous sûr? (y/N): " confirm
        if [[ $confirm =~ ^[Yy]$ ]]; then
            rm -rf results/qlearning/*
            rm -rf results/evaluation/*
            rm -rf results/comparison/*
            echo -e "${GREEN}✅ Nettoyage terminé${NC}"
        fi
        ;;
    
    *)
        echo "Usage: $0 {train-r1|train-r2|train-r3|train-all|evaluate|all|clean}"
        echo ""
        echo "Commandes:"
        echo "  train-r1       - Entraîner pour R1 (5.3 GB)"
        echo "  train-r2       - Entraîner pour R2 (11.9 GB)"
        echo "  train-r3       - Entraîner pour R3 (20 GB)"
        echo "  train-all      - Entraîner tous les scénarios"
        echo "  evaluate       - Évaluer les modèles entraînés"
        echo "  all            - Workflow complet (défaut)"
        echo "  clean          - Nettoyer les résultats"
        echo ""
        echo "Exemples:"
        echo "  $0 all              # Entraîner tous les scénarios"
        echo "  $0 train-r1         # Entraîner R1 uniquement"
        echo ""
        echo "Note: Les simulations et comparaisons se font en Java avec CloudSim"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}Terminé!${NC}"
