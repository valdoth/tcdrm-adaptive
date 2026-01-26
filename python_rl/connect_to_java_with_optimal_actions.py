"""
Client Python pour Py4J - Version avec actions optimales pré-générées
Charge les actions optimales depuis un fichier et les applique de manière répétée
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


class PythonRLOptimalActions:
    """
    Pont entre Python et Java pour exécuter les actions optimales pré-générées
    Implémente l'interface Java PythonRLBridge via Py4J
    """
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
    
    def __init__(self, optimal_actions_path: str):
        """
        Initialise le pont avec les actions optimales pré-générées
        
        Args:
            optimal_actions_path: Chemin vers le fichier .pkl des actions optimales
        """
        print(f"📦 Chargement des actions optimales depuis: {optimal_actions_path}")
        
        with open(optimal_actions_path, 'rb') as f:
            data = pickle.load(f)
        
        self.scenario = data['scenario']
        self.data_gb = data['data_gb']
        self.optimal_actions = data['actions']
        self.n_queries = data['n_queries']
        
        print(f"✅ Actions optimales chargées:")
        print(f"   Scénario: {self.scenario}")
        print(f"   Taille des données: {self.data_gb:.2f} GB")
        print(f"   Nombre d'actions: {self.n_queries}")
        
        # Statistiques sur les actions
        action_names = ['CREATE', 'DELETE', 'DO_NOTHING']
        action_counts = [self.optimal_actions.count(i) for i in range(3)]
        print(f"   Distribution:")
        for name, count in zip(action_names, action_counts):
            percentage = (count / self.n_queries) * 100
            print(f"     {name}: {count} ({percentage:.1f}%)")
        print()
        
        # Variables d'état
        self.env = None
        self.current_state = None
        self.current_query_idx = 0
    
    def resetEpisode(self, data_gb: float, seed: int):
        """
        Réinitialise un épisode
        Appelé depuis Java via Py4J (implémente PythonRLBridge.resetEpisode)
        
        Args:
            data_gb: Taille des données en GB (ignorée, on utilise self.data_gb)
            seed: Seed pour la reproductibilité
        """
        print(f"   🔄 Reset épisode: data_gb={self.data_gb}, seed={seed}")
        
        # Créer ou réinitialiser l'environnement
        if self.env is None:
            self.env = TcdrmAdaptiveEnv(data_gb=self.data_gb)
        
        # Reset l'environnement
        observation, info = self.env.reset(seed=seed)
        self.current_state = observation
        self.current_query_idx = 0
    
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
        Retourne l'action optimale pré-générée pour la requête actuelle
        Appelé depuis Java via callback
        
        Args:
            state: État sous forme de liste (ignoré, on utilise l'action pré-générée)
        
        Returns:
            Index de l'action optimale
        """
        # Utiliser l'action pré-générée pour cette requête
        # Si on dépasse le nombre d'actions, on boucle (répétition)
        action_idx = self.current_query_idx % len(self.optimal_actions)
        action = self.optimal_actions[action_idx]
        
        return int(action)
    
    def executeStep(self, action: int):
        """
        Exécute un step dans l'environnement avec l'action donnée
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
        self.current_query_idx += 1
        
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
        description='Client Python pour Py4J - Actions optimales pré-générées'
    )
    parser.add_argument('--actions', type=str, required=True,
                       help='Chemin vers les actions optimales (.pkl)')
    parser.add_argument('--host', type=str, default='localhost',
                       help='Hôte du Gateway Java')
    parser.add_argument('--port', type=int, default=25333,
                       help='Port du Gateway Java')
    
    args = parser.parse_args()
    
    print("="*80)
    print("CLIENT PYTHON PY4J - ACTIONS OPTIMALES PRÉ-GÉNÉRÉES")
    print("="*80)
    print()
    
    # Vérifier que le fichier d'actions existe
    if not os.path.exists(args.actions):
        print(f"❌ ERREUR: Fichier d'actions introuvable: {args.actions}")
        sys.exit(1)
    
    # Charger les actions optimales
    bridge = PythonRLOptimalActions(args.actions)
    
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
        
        # Enregistrer le pont Python dans l'agent
        java_agent = java_gateway.getPythonAgent()
        java_agent.setPythonBridge(bridge)
        
        # Signaler que le modèle est chargé (actions optimales)
        model_info = f"Optimal Actions for {bridge.scenario} ({bridge.n_queries} actions)"
        java_agent.setModelInfo(model_info)
        
        # Signaler à Java que Python est prêt
        gateway.jvm.System.setProperty("python_bridge_ready", "true")
        
        print()
        print("="*80)
        print("✅ CLIENT PYTHON PRÊT")
        print("="*80)
        print("Les actions optimales sont maintenant disponibles pour Java")
        print("Java peut maintenant générer les graphes en répétant ces actions")
        print()
        print("Appuyez sur Ctrl+C pour arrêter le client...")
        print()
        
        # Garder le client actif
        try:
            import time
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n👋 Arrêt du client Python...")
    
    except Exception as e:
        print(f"❌ ERREUR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        try:
            gateway.shutdown()
        except:
            pass


if __name__ == '__main__':
    main()
