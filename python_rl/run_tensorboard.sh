#!/bin/bash
# Script pour lancer TensorBoard
# Inspiré de rl-cloudsimplus

# Couleurs pour l'affichage
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Répertoire des logs TensorBoard
LOG_DIR="${1:-runs}"
PORT="${2:-6006}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  TCDRM TensorBoard Dashboard${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "📊 Log directory: ${GREEN}${LOG_DIR}${NC}"
echo -e "🌐 Port: ${GREEN}${PORT}${NC}"
echo -e "🔗 URL: ${GREEN}http://localhost:${PORT}${NC}"
echo ""
echo -e "${BLUE}Démarrage de TensorBoard...${NC}"
echo ""

# Lancer TensorBoard
tensorboard --logdir="${LOG_DIR}" --port="${PORT}" --bind_all

# Note: --bind_all permet d'accéder depuis d'autres machines sur le réseau
