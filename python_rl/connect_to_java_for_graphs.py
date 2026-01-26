#!/usr/bin/env python3
"""
Client Python pour Py4J - Version pour génération de graphes
Charge le modèle Q-Learning entraîné et se connecte au Gateway Java
Implémente les callbacks nécessaires pour exécuter des épisodes complets
"""

import argparse
import pickle
import numpy as np
from py4j.java_gateway import JavaGateway, CallbackServerParameters
from py4j.java_collections import ListConverter

# Variable globale pour stocker le gateway (nécessaire pour ListConverter)
_gateway = None
import sys
import os

# Ajouter le répertoire parent au path pour importer les modules
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent


class PythonQLearningCallback:
    """
    Pont entre Python et Java pour exécuter le modèle RL entraîné
    Implémente l'interface Java PythonRLBridge via Py4J
    """
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
    
    def __init__(self, model_path: str):
        """
        Initialise le pont avec le modèle entraîné
        
        Args:
            model_path: Chemin vers le fichier .pkl du modèle entraîné
        """
        print(f"📦 Chargement du modèle depuis: {model_path}")
        
        # Charger le modèle entraîné
        with open(model_path, 'rb') as f:
            model_data = pickle.load(f)
        
        # Reconstruire l'agent
        self.agent = TabularQLearningAgent(
            n_states=model_data['n_states'],
            n_actions=model_data['n_actions'],
            learning_rate=model_data['learning_rate'],
            discount_factor=model_data['discount_factor'],
            epsilon=0.0,  # Mode exploitation uniquement
            epsilon_decay=1.0,
            epsilon_min=0.0
        )
        
        # Charger la Q-table entraînée
        self.agent.q_table = model_data['q_table']
        
        print(f"✅ Modèle chargé:")
        print(f"   - États: {model_data['n_states']}")
        print(f"   - Actions: {model_data['n_actions']}")
        print(f"   - Episodes d'entraînement: {model_data.get('episodes_trained', 'N/A')}")
        
        # Environnement pour exécution
        self.env = None
        self.current_state = None
        
    def register_to_java(self, java_agent, gateway):
        """
        Enregistre la Q-table dans l'agent Java
        
        Args:
            java_agent: Instance de PythonQLearningAgent Java
            gateway: JavaGateway pour la conversion de types
        """
        print("📡 Enregistrement de la Q-table dans Java...")
        
        # Convertir la Q-table en format compatible Py4J
        # On doit aplatir la Q-table et l'envoyer ligne par ligne
        n_states = int(self.agent.n_states)  # Convertir en int Python natif
        n_actions = int(self.agent.n_actions)  # Convertir en int Python natif
        
        # Informations sur le modèle
        info = f"Q-Learning Model (States: {n_states}, Actions: {n_actions})"
        
        # Enregistrer les dimensions d'abord
        java_agent.setQTableDimensions(n_states, n_actions)
        
        # Enregistrer chaque ligne de la Q-table
        for state_idx in range(n_states):
            q_values = self.agent.q_table[state_idx].tolist()
            # Convertir la liste Python en ArrayList Java
            java_list = ListConverter().convert(q_values, gateway._gateway_client)
            java_agent.setQTableRow(state_idx, java_list)
        
        # Marquer le modèle comme chargé
        java_agent.setModelInfo(info)
        
        print(f"✅ Q-table enregistrée dans Java: {n_states} états × {n_actions} actions")
    
    def resetEpisode(self, data_gb: float, seed: int):
        """
        Réinitialise un épisode dans l'environnement Python
        Appelé depuis Java via Py4J (implémente PythonRLBridge.resetEpisode)
        
        Args:
            data_gb: Taille des données en GB
            seed: Seed pour la reproductibilité
        """
        print(f"   🔄 Reset épisode: data_gb={data_gb}, seed={seed}")
        
        # Créer ou réinitialiser l'environnement
        if self.env is None:
            self.env = TcdrmAdaptiveEnv(data_gb=data_gb)
        
        # Reset l'environnement
        observation, info = self.env.reset(seed=seed)
        self.current_state = observation
        
        # Ne rien retourner (void) pour éviter les problèmes de sérialisation Py4J
    
    def getCurrentState(self):
        """
        Retourne l'état actuel de l'environnement
        Appelé depuis Java via callback
        
        Returns:
            ArrayList Java représentant l'état actuel
        """
        global _gateway
        if self.current_state is None:
            state_list = [0.5, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0]
        else:
            state_list = self.current_state.tolist()
        
        # Convertir en ArrayList Java pour Py4J
        if _gateway is not None:
            return ListConverter().convert(state_list, _gateway._gateway_client)
        return state_list
    
    def selectAction(self, state):
        """
        Sélectionne une action en utilisant la Q-table entraînée
        Appelé depuis Java via callback
        
        Args:
            state: État sous forme de liste ou array
        
        Returns:
            Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        """
        if isinstance(state, list):
            state = np.array(state, dtype=np.float32)
        
        # Discrétiser l'état et choisir l'action
        state_index = self.agent.discretize_state(state)
        action = self.agent.select_action(state_index, training=False)
        
        return int(action)
    
    def get_bridge_instance(self):
        """
        Retourne cette instance (pour que Java puisse obtenir une référence)
        """
        return self
    
    def executeStep(self, action: int):
        """
        Exécute un step dans l'environnement
        Appelé depuis Java via callback
        
        Args:
            action: Action à exécuter (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        
        Returns:
            ArrayList Java [latency, cost, replicas, reward, done]
        """
        global _gateway
        if self.env is None:
            raise RuntimeError("Environnement non initialisé. Appelez resetEpisode d'abord.")
        
        # Exécuter l'action dans l'environnement
        observation, reward, terminated, truncated, info = self.env.step(action)
        self.current_state = observation
        
        # Extraire les métriques de l'info
        latency = info.get('latency', 100.0)
        cost = info.get('cost', 0.5)
        replicas = info.get('replicas', 1)
        
        # Pour la génération de graphes, ignorer truncated (budget épuisé)
        # afin de continuer jusqu'à 1000 requêtes comme les autres politiques
        done = 1.0 if terminated else 0.0
        
        result_list = [latency, cost, float(replicas), reward, done]
        
        # Convertir en ArrayList Java pour Py4J
        if _gateway is not None:
            return ListConverter().convert(result_list, _gateway._gateway_client)
        return result_list


def main():
    parser = argparse.ArgumentParser(
        description='Client Python pour Py4J - Génération de graphes avec modèle RL'
    )
    parser.add_argument('--model', type=str, required=True,
                       help='Chemin vers le modèle entraîné (.pkl)')
    parser.add_argument('--host', type=str, default='localhost',
                       help='Hôte du Gateway Java')
    parser.add_argument('--port', type=int, default=25333,
                       help='Port du Gateway Java')
    
    args = parser.parse_args()
    
    print("="*80)
    print("CLIENT PYTHON PY4J - GÉNÉRATION DE GRAPHES AVEC MODÈLE RL")
    print("="*80)
    print()
    
    # Vérifier que le modèle existe
    if not os.path.exists(args.model):
        print(f"❌ ERREUR: Modèle introuvable: {args.model}")
        sys.exit(1)
    
    # Charger le modèle
    bridge = PythonQLearningCallback(args.model)
    
    # Se connecter au Gateway Java
    print(f"\n📡 Connexion au Gateway Java sur {args.host}:{args.port}...")
    
    try:
        # Démarrer le callback server Python explicitement
        print("🔧 Démarrage du callback server Python sur le port 25334...")
        callback_params = CallbackServerParameters(port=25334, daemonize=True, daemonize_connections=True)
        
        gateway = JavaGateway(
            gateway_parameters=None,
            callback_server_parameters=callback_params
        )
        
        # Stocker le gateway dans la variable globale pour ListConverter
        global _gateway
        _gateway = gateway
        
        print("✅ Callback server Python démarré")
        
        # Obtenir le Gateway Java (entry point)
        java_gateway = gateway.entry_point
        
        print("✅ Connexion établie avec le Gateway Java")
        print()
        
        # Enregistrer la Q-table dans l'agent Java
        java_agent = java_gateway.getPythonAgent()
        bridge.register_to_java(java_agent, gateway)
        
        # Enregistrer le pont Python dans l'agent
        # Maintenant que bridge implémente l'interface Java PythonRLBridge,
        # Py4J peut le passer correctement
        java_agent.setPythonBridge(bridge)
        
        # Signaler à Java que Python est prêt
        gateway.jvm.System.setProperty("python_bridge_ready", "true")
        
        print()
        print("="*80)
        print("✅ CLIENT PYTHON PRÊT")
        print("="*80)
        print("Le modèle Python RL est maintenant disponible pour Java")
        print("Java peut maintenant générer les graphes avec le vrai modèle entraîné")
        print()
        print("Appuyez sur Ctrl+C pour arrêter le client...")
        print()
        
        # Garder le client actif
        import time
        while True:
            time.sleep(1)
            
    except KeyboardInterrupt:
        print("\n\n🛑 Arrêt du client Python...")
        gateway.shutdown()
        print("✅ Client arrêté")
        
    except Exception as e:
        print(f"\n❌ ERREUR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
