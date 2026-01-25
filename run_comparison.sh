#!/bin/bash

# Script pour exécuter la comparaison complète Q-Learning vs TCDRM Statique
# Utilise CloudSim pour des simulations réalistes

set -e

# Couleurs
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  TCDRM-ADAPTIVE: Comparaison Q-Learning vs Statique${NC}"
echo -e "${BLUE}  Simulations CloudSim Réalistes${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Vérifier que Maven est installé
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven n'est pas installé${NC}"
    echo "Installez Maven: brew install maven"
    exit 1
fi

# Vérifier que Java est installé
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java n'est pas installé${NC}"
    exit 1
fi

echo -e "${YELLOW}>>> Compilation du projet Java...${NC}"
mvn clean package -q

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Erreur de compilation${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Compilation réussie${NC}"
echo ""

echo -e "${YELLOW}>>> Exécution de la comparaison CloudSim...${NC}"
echo ""

# Exécuter la comparaison
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim

echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  ✅ Comparaison terminée avec succès!${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
echo "Résultats disponibles dans:"
echo "  - results/cloudsim_comparison/cost_comparison_R1.png"
echo "  - results/cloudsim_comparison/latency_comparison_R1.png"
echo "  - results/cloudsim_comparison/replicas_comparison_R1.png"
echo "  - results/cloudsim_comparison/cost_comparison_R2.png"
echo "  - results/cloudsim_comparison/latency_comparison_R2.png"
echo "  - results/cloudsim_comparison/replicas_comparison_R2.png"
echo ""
echo "Ouvrir les graphes:"
echo "  open results/cloudsim_comparison/*.png"
