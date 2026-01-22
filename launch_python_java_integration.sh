#!/bin/bash

# Script pour lancer l'intégration Python-Java TCDRM-ADAPTIVE
# Lance le gateway Java et permet d'exécuter des scripts Python

set -e

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Répertoire du projet
PROJECT_DIR="/Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive"
PYTHON_RL_DIR="${PROJECT_DIR}/python_rl"
JAR_FILE="${PROJECT_DIR}/target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar"
GATEWAY_CLASS="org.tcdrm.adaptive.bridge.Py4JGateway"
GATEWAY_PORT=25333

# Fichier PID pour le gateway
GATEWAY_PID_FILE="/tmp/tcdrm_gateway.pid"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  TCDRM-ADAPTIVE: Intégration Python-Java via Py4J${NC}"
echo -e "${BLUE}============================================================${NC}"
echo ""

# Fonction pour vérifier si Maven est installé
check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven n'est pas installé${NC}"
        echo "Installez Maven avec: brew install maven"
        exit 1
    fi
    echo -e "${GREEN}✅ Maven trouvé: $(mvn -version | head -n 1)${NC}"
}

# Fonction pour compiler le projet Java
compile_java() {
    echo ""
    echo -e "${YELLOW}>>> Compilation du projet Java...${NC}"
    cd "${PROJECT_DIR}"
    
    if [ -f "${JAR_FILE}" ]; then
        echo -e "${GREEN}✅ JAR déjà compilé: ${JAR_FILE}${NC}"
        read -p "Recompiler? (y/N): " recompile
        if [[ ! $recompile =~ ^[Yy]$ ]]; then
            return 0
        fi
    fi
    
    echo "Exécution de: mvn clean package"
    if mvn clean package -q; then
        echo -e "${GREEN}✅ Compilation réussie${NC}"
        ls -lh "${JAR_FILE}"
    else
        echo -e "${RED}❌ Erreur de compilation${NC}"
        exit 1
    fi
}

# Fonction pour démarrer le gateway Java
start_gateway() {
    echo ""
    echo -e "${YELLOW}>>> Démarrage du gateway Java...${NC}"
    
    # Vérifier si le gateway est déjà en cours d'exécution
    if [ -f "${GATEWAY_PID_FILE}" ]; then
        OLD_PID=$(cat "${GATEWAY_PID_FILE}")
        if ps -p "${OLD_PID}" > /dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  Gateway déjà en cours d'exécution (PID: ${OLD_PID})${NC}"
            read -p "Arrêter et redémarrer? (y/N): " restart
            if [[ $restart =~ ^[Yy]$ ]]; then
                stop_gateway
            else
                return 0
            fi
        else
            rm -f "${GATEWAY_PID_FILE}"
        fi
    fi
    
    # Vérifier si le port est disponible
    if lsof -Pi :${GATEWAY_PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${RED}❌ Port ${GATEWAY_PORT} déjà utilisé${NC}"
        echo "Processus utilisant le port:"
        lsof -i :${GATEWAY_PORT}
        exit 1
    fi
    
    # Démarrer le gateway en arrière-plan
    echo "Démarrage sur le port ${GATEWAY_PORT}..."
    cd "${PROJECT_DIR}"
    nohup java -cp "${JAR_FILE}" "${GATEWAY_CLASS}" ${GATEWAY_PORT} > /tmp/tcdrm_gateway.log 2>&1 &
    GATEWAY_PID=$!
    echo ${GATEWAY_PID} > "${GATEWAY_PID_FILE}"
    
    # Attendre que le gateway soit prêt
    echo -n "Attente du démarrage du gateway"
    for i in {1..10}; do
        sleep 1
        echo -n "."
        if lsof -Pi :${GATEWAY_PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo ""
            echo -e "${GREEN}✅ Gateway démarré (PID: ${GATEWAY_PID})${NC}"
            echo "Logs: /tmp/tcdrm_gateway.log"
            return 0
        fi
    done
    
    echo ""
    echo -e "${RED}❌ Timeout: Le gateway n'a pas démarré${NC}"
    echo "Vérifiez les logs: tail -f /tmp/tcdrm_gateway.log"
    exit 1
}

# Fonction pour arrêter le gateway
stop_gateway() {
    echo ""
    echo -e "${YELLOW}>>> Arrêt du gateway Java...${NC}"
    
    if [ -f "${GATEWAY_PID_FILE}" ]; then
        PID=$(cat "${GATEWAY_PID_FILE}")
        if ps -p "${PID}" > /dev/null 2>&1; then
            kill ${PID}
            echo -e "${GREEN}✅ Gateway arrêté (PID: ${PID})${NC}"
        else
            echo -e "${YELLOW}⚠️  Gateway déjà arrêté${NC}"
        fi
        rm -f "${GATEWAY_PID_FILE}"
    else
        echo -e "${YELLOW}⚠️  Aucun PID trouvé${NC}"
    fi
}

# Fonction pour tester la connexion
test_connection() {
    echo ""
    echo -e "${YELLOW}>>> Test de la connexion Python-Java...${NC}"
    cd "${PYTHON_RL_DIR}"
    
    if uv run python test_java_connection.py; then
        echo -e "${GREEN}✅ Test de connexion réussi${NC}"
    else
        echo -e "${RED}❌ Test de connexion échoué${NC}"
        exit 1
    fi
}

# Fonction pour entraîner avec l'environnement Java
train_with_java() {
    local algorithm=${1:-ppo}
    local timesteps=${2:-50000}
    local data_gb=${3:-5.3}
    
    echo ""
    echo -e "${YELLOW}>>> Entraînement ${algorithm^^} avec environnement Java...${NC}"
    echo "Paramètres:"
    echo "  - Algorithme: ${algorithm}"
    echo "  - Timesteps: ${timesteps}"
    echo "  - Data: ${data_gb} GB"
    echo ""
    
    cd "${PYTHON_RL_DIR}"
    uv run python train_with_java_env.py \
        --algorithm "${algorithm}" \
        --timesteps "${timesteps}" \
        --data-gb "${data_gb}"
}

# Fonction pour afficher le statut
show_status() {
    echo ""
    echo -e "${BLUE}=== Statut de l'intégration ===${NC}"
    
    # Gateway Java
    if [ -f "${GATEWAY_PID_FILE}" ]; then
        PID=$(cat "${GATEWAY_PID_FILE}")
        if ps -p "${PID}" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Gateway Java: En cours (PID: ${PID})${NC}"
        else
            echo -e "${RED}❌ Gateway Java: Arrêté (PID obsolète)${NC}"
        fi
    else
        echo -e "${RED}❌ Gateway Java: Non démarré${NC}"
    fi
    
    # Port
    if lsof -Pi :${GATEWAY_PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Port ${GATEWAY_PORT}: Ouvert${NC}"
    else
        echo -e "${RED}❌ Port ${GATEWAY_PORT}: Fermé${NC}"
    fi
    
    # JAR
    if [ -f "${JAR_FILE}" ]; then
        echo -e "${GREEN}✅ JAR compilé: $(ls -lh ${JAR_FILE} | awk '{print $5}')${NC}"
    else
        echo -e "${RED}❌ JAR non trouvé${NC}"
    fi
    
    echo ""
}

# Fonction pour afficher les logs
show_logs() {
    if [ -f "/tmp/tcdrm_gateway.log" ]; then
        echo -e "${YELLOW}>>> Logs du gateway (Ctrl+C pour quitter):${NC}"
        tail -f /tmp/tcdrm_gateway.log
    else
        echo -e "${RED}❌ Aucun log trouvé${NC}"
    fi
}

# Menu interactif
show_menu() {
    echo ""
    echo -e "${BLUE}=== Menu Principal ===${NC}"
    echo "1. Compiler le projet Java"
    echo "2. Démarrer le gateway Java"
    echo "3. Tester la connexion"
    echo "4. Entraîner PPO (50k timesteps)"
    echo "5. Entraîner DQN (50k timesteps)"
    echo "6. Entraînement personnalisé"
    echo "7. Afficher le statut"
    echo "8. Afficher les logs"
    echo "9. Arrêter le gateway"
    echo "0. Quitter"
    echo ""
}

# Workflow complet automatique
run_full_workflow() {
    echo -e "${BLUE}============================================================${NC}"
    echo -e "${BLUE}  Workflow Complet: Compilation → Gateway → Test → Train${NC}"
    echo -e "${BLUE}============================================================${NC}"
    
    check_maven
    compile_java
    start_gateway
    sleep 2
    test_connection
    
    echo ""
    echo -e "${GREEN}✅ Workflow complet terminé!${NC}"
    echo ""
    echo "Le gateway Java est maintenant actif."
    echo "Vous pouvez:"
    echo "  - Entraîner un agent: ./launch_python_java_integration.sh train ppo"
    echo "  - Arrêter le gateway: ./launch_python_java_integration.sh stop"
    echo "  - Voir le statut: ./launch_python_java_integration.sh status"
}

# Point d'entrée principal
main() {
    case "${1:-menu}" in
        compile)
            check_maven
            compile_java
            ;;
        start)
            start_gateway
            ;;
        stop)
            stop_gateway
            ;;
        test)
            test_connection
            ;;
        train)
            algorithm=${2:-ppo}
            timesteps=${3:-50000}
            data_gb=${4:-5.3}
            train_with_java "${algorithm}" "${timesteps}" "${data_gb}"
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs
            ;;
        full)
            run_full_workflow
            ;;
        menu)
            while true; do
                show_status
                show_menu
                read -p "Choisissez une option: " choice
                case $choice in
                    1) check_maven; compile_java ;;
                    2) start_gateway ;;
                    3) test_connection ;;
                    4) train_with_java "ppo" 50000 5.3 ;;
                    5) train_with_java "dqn" 50000 5.3 ;;
                    6)
                        read -p "Algorithme (ppo/dqn): " algo
                        read -p "Timesteps: " steps
                        read -p "Data GB: " data
                        train_with_java "${algo}" "${steps}" "${data}"
                        ;;
                    7) show_status ;;
                    8) show_logs ;;
                    9) stop_gateway ;;
                    0) 
                        echo "Au revoir!"
                        exit 0
                        ;;
                    *)
                        echo -e "${RED}Option invalide${NC}"
                        ;;
                esac
                echo ""
                read -p "Appuyez sur Entrée pour continuer..."
            done
            ;;
        *)
            echo "Usage: $0 {compile|start|stop|test|train|status|logs|full|menu}"
            echo ""
            echo "Commandes:"
            echo "  compile          - Compiler le projet Java"
            echo "  start            - Démarrer le gateway Java"
            echo "  stop             - Arrêter le gateway Java"
            echo "  test             - Tester la connexion"
            echo "  train [algo]     - Entraîner un agent (ppo/dqn)"
            echo "  status           - Afficher le statut"
            echo "  logs             - Afficher les logs en temps réel"
            echo "  full             - Workflow complet automatique"
            echo "  menu             - Menu interactif (par défaut)"
            echo ""
            echo "Exemples:"
            echo "  $0 full                    # Workflow complet"
            echo "  $0 train ppo 100000 5.3    # Entraîner PPO"
            echo "  $0 status                  # Voir le statut"
            exit 1
            ;;
    esac
}

# Gestion de Ctrl+C
trap 'echo -e "\n${YELLOW}Interruption détectée${NC}"; exit 130' INT

# Exécuter
main "$@"
