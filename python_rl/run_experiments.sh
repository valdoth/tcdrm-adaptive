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
    
    compare-java)
        echo "Comparaison Python vs Java Q-Learning"
        
        # Vérifier si le log Java existe
        if [ -f "../logs/qlearning_training.log" ]; then
            uv run python compare_with_java.py \
                --java-log ../logs/qlearning_training.log \
                --episodes 500 \
                --output-dir results/comparison
            echo -e "${GREEN}✅ Comparaison terminée${NC}"
        else
            echo -e "${YELLOW}⚠️  Log Java non trouvé. Exécutez d'abord l'entraînement Java.${NC}"
        fi
        ;;
    
    test-java)
        echo "Test de connexion Python-Java (Py4J)"
        uv run python test_java_connection.py
        ;;
    
    all)
        echo "Workflow complet: Entraînement + Évaluation + Comparaison"
        
        # Entraîner tous les scénarios
        train_qlearning 5.3 1000 "r1_simple"
        train_qlearning 11.9 1000 "r2_complex"
        train_qlearning 20.0 1000 "r3_large"
        
        # Évaluer tous les modèles
        sleep 2
        $0 evaluate
        
        # Comparaison avec Java (si disponible)
        if [ -f "../logs/qlearning_training.log" ]; then
            $0 compare-java
        fi
        
        echo ""
        echo -e "${GREEN}============================================================${NC}"
        echo -e "${GREEN}  ✅ Toutes les expériences terminées avec succès!${NC}"
        echo -e "${GREEN}============================================================${NC}"
        echo ""
        echo "Résultats disponibles dans:"
        echo "  - results/qlearning/     (modèles entraînés)"
        echo "  - results/evaluation/    (métriques d'évaluation)"
        echo "  - results/comparison/    (comparaison Python vs Java)"
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
        echo "Usage: $0 {train-r1|train-r2|train-r3|train-all|evaluate|compare-java|test-java|all|clean}"
        echo ""
        echo "Commandes:"
        echo "  train-r1       - Entraîner pour R1 (5.3 GB)"
        echo "  train-r2       - Entraîner pour R2 (11.9 GB)"
        echo "  train-r3       - Entraîner pour R3 (20 GB)"
        echo "  train-all      - Entraîner tous les scénarios"
        echo "  evaluate       - Évaluer les modèles entraînés"
        echo "  compare-java   - Comparer Python vs Java"
        echo "  test-java      - Tester la connexion Py4J"
        echo "  all            - Workflow complet (défaut)"
        echo "  clean          - Nettoyer les résultats"
        echo ""
        echo "Exemples:"
        echo "  $0 all              # Tout exécuter"
        echo "  $0 train-r1         # Entraîner R1 uniquement"
        echo "  $0 evaluate         # Évaluer les modèles"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}Terminé!${NC}"
