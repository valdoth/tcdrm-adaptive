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
import sys
import os

# Ajouter le répertoire parent au path pour importer les modules
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent


class PythonQLearningBridge:
    """
    Pont entre Python et Java pour exécuter le modèle RL entraîné
    Implémente les callbacks nécessaires pour l'exécution d'épisodes
    """
    
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
        
    def register_to_java(self, java_agent):
        """
        Enregistre la Q-table dans l'agent Java
        
        Args:
            java_agent: Instance de PythonQLearningAgent Java
        """
        print("📡 Enregistrement de la Q-table dans Java...")
        
        # Convertir numpy array en liste de listes pour Py4J
        q_table_list = self.agent.q_table.tolist()
        
        # Informations sur le modèle
        info = f"Q-Learning Model (States: {self.agent.n_states}, Actions: {self.agent.n_actions})"
        
        # Enregistrer dans Java
        java_agent.registerQTable(q_table_list, info)
        
        print("✅ Q-table enregistrée dans Java")
    
    def reset_episode(self, data_gb: float, seed: int):
        """
        Réinitialise un épisode dans l'environnement Python
        Appelé depuis Java via callback
        
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
        
        return observation.tolist()
    
    def get_current_state(self):
        """
        Retourne l'état actuel de l'environnement
        Appelé depuis Java via callback
        
        Returns:
            Liste représentant l'état actuel
        """
        if self.current_state is None:
            return [0.5, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0]
        return self.current_state.tolist()
    
    def select_action(self, state):
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
    
    def execute_step(self, action: int):
        """
        Exécute un step dans l'environnement
        Appelé depuis Java via callback
        
        Args:
            action: Action à exécuter (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        
        Returns:
            Liste [latency, cost, replicas, reward, done]
        """
        if self.env is None:
            print("⚠️  Environnement non initialisé")
            return [100.0, 0.5, 1.0, 0.0, 0.0]
        
        # Exécuter l'action
        observation, reward, terminated, truncated, info = self.env.step(action)
        self.current_state = observation
        
        # Extraire les informations
        latency = observation[1]  # Latence
        
        # Calculer le coût (simplifié)
        replica_count = int(observation[3])
        if replica_count > 0:
            cost = self.env.data_gb * self.env.COST_BW_INTRA_DC
        else:
            cost = self.env.data_gb * self.env.COST_BW_INTER_PROVIDER
        cost += replica_count * self.env.data_gb * self.env.STORAGE_COST_PER_GB_PER_HOUR
        
        done = 1.0 if (terminated or truncated) else 0.0
        
        return [float(latency), float(cost), float(replica_count), float(reward), float(done)]
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonQLearningBridge"]


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
    
    # Créer le pont Python-Java
    bridge = PythonQLearningBridge(args.model)
    
    # Se connecter au Gateway Java
    print(f"\n📡 Connexion au Gateway Java sur {args.host}:{args.port}...")
    
    try:
        gateway = JavaGateway(
            gateway_parameters=None,
            callback_server_parameters=CallbackServerParameters(port=25334)
        )
        
        # Obtenir le Gateway Java (entry point)
        java_gateway = gateway.entry_point
        
        print("✅ Connexion établie avec le Gateway Java")
        print()
        
        # Enregistrer la Q-table dans l'agent Java
        java_agent = java_gateway.getPythonAgent()
        bridge.register_to_java(java_agent)
        
        # Enregistrer le pont Python dans le Gateway pour les appels directs
        java_gateway.registerPythonBridge(bridge)
        
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
