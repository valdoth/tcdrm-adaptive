#!/usr/bin/env python3
"""
Charge un modèle Q-Learning Python et le sert via Py4J pour Java
Permet à Java CloudSim d'utiliser les modèles entraînés en Python
"""

import argparse
import pickle
import numpy as np
from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters
import time
import sys

class PythonQLearningAgent:
    """Agent Q-Learning Python exposé à Java via Py4J"""
    
    def __init__(self, model_path):
        """Charge le modèle Q-Learning depuis un fichier pickle"""
        print(f"📦 Chargement du modèle: {model_path}")
        
        with open(model_path, 'rb') as f:
            data = pickle.load(f)
        
        self.q_table = data['q_table']
        self.state_space = data.get('state_space', (3, 3, 3, 4))
        self.action_space = data.get('action_space', 3)
        self.model_path = model_path
        
        print(f"✅ Modèle chargé:")
        print(f"   - Q-Table shape: {self.q_table.shape}")
        print(f"   - State space: {self.state_space}")
        print(f"   - Action space: {self.action_space}")
        print(f"   - Total states: {np.prod(self.state_space)}")
    
    def chooseAction(self, state):
        """
        Choisit la meilleure action pour un état donné
        
        Args:
            state: [budget_level, latency_level, popularity_level, num_replicas]
        
        Returns:
            int: Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        """
        try:
            # Convertir en tuple pour indexer la Q-table
            state_tuple = tuple(state)
            
            # Vérifier que l'état est valide
            if len(state_tuple) != 4:
                print(f"⚠️  État invalide (longueur {len(state_tuple)}): {state_tuple}")
                return 2  # DO_NOTHING par défaut
            
            # Obtenir les Q-values pour cet état
            q_values = self.q_table[state_tuple]
            
            # Choisir l'action avec la meilleure Q-value
            action = int(np.argmax(q_values))
            
            return action
            
        except Exception as e:
            print(f"❌ Erreur chooseAction: {e}")
            print(f"   État: {state}")
            return 2  # DO_NOTHING par défaut
    
    def getQValue(self, state, action):
        """
        Obtient la Q-value pour un état et une action
        
        Args:
            state: [budget_level, latency_level, popularity_level, num_replicas]
            action: Index de l'action
        
        Returns:
            float: Q-value
        """
        try:
            state_tuple = tuple(state)
            return float(self.q_table[state_tuple][action])
        except Exception as e:
            print(f"❌ Erreur getQValue: {e}")
            return 0.0
    
    def getModelInfo(self):
        """Retourne des informations sur le modèle"""
        return f"Q-Learning Model ({self.model_path})"
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonQLearningPolicy$PythonQLearningAgent"]


def main():
    parser = argparse.ArgumentParser(description='Serveur Py4J pour modèle Q-Learning')
    parser.add_argument('--model', type=str, required=True,
                       help='Chemin vers le modèle (.pkl)')
    parser.add_argument('--port', type=int, default=25333,
                       help='Port du gateway Py4J (défaut: 25333)')
    parser.add_argument('--callback-port', type=int, default=25334,
                       help='Port du callback server (défaut: 25334)')
    
    args = parser.parse_args()
    
    print("="*70)
    print("Serveur Py4J pour Q-Learning Python")
    print("="*70)
    print()
    
    # Charger le modèle
    try:
        agent = PythonQLearningAgent(args.model)
    except Exception as e:
        print(f"❌ Erreur lors du chargement du modèle: {e}")
        sys.exit(1)
    
    print()
    print(f"🚀 Démarrage du serveur Py4J...")
    print(f"   - Gateway port: {args.port}")
    print(f"   - Callback port: {args.callback_port}")
    print()
    
    # Démarrer le gateway Py4J avec Python comme entry point
    try:
        gateway = JavaGateway(
            gateway_parameters=GatewayParameters(port=args.port),
            callback_server_parameters=CallbackServerParameters(port=args.callback_port),
            python_server_entry_point=agent
        )
        
        print("✅ Serveur Py4J démarré avec succès!")
        print()
        print("📡 En attente de connexions Java...")
        print(f"   Java peut maintenant se connecter sur le port {args.port}")
        print("   Exécutez maintenant le code Java:")
        print("   java -cp target/tcdrm-adaptive-*.jar org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim")
        print()
        print("Appuyez sur Ctrl+C pour arrêter le serveur")
        print()
        
        # Garder le serveur actif
        while True:
            time.sleep(1)
            
    except KeyboardInterrupt:
        print("\n\n🛑 Arrêt du serveur Py4J...")
        gateway.shutdown()
        print("✅ Serveur arrêté")
    except Exception as e:
        print(f"\n❌ Erreur serveur: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
