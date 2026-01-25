#!/usr/bin/env python3
"""
Client Python qui se connecte au serveur Java Py4J
Architecture basée sur rl-cloudsimplus-greenscheduling
"""

import argparse
import pickle
import numpy as np
from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters
import time
import sys


class QLearningModel:
    """Wrapper pour le modèle Q-Learning qui expose les méthodes à Java"""
    
    def __init__(self, model_path):
        """Charge le modèle Q-Learning depuis un fichier pickle"""
        print(f"📦 Chargement du modèle: {model_path}")
        
        with open(model_path, 'rb') as f:
            model_data = pickle.load(f)
        
        self.q_table = model_data['q_table']
        self.n_states = model_data['n_states']
        self.n_actions = model_data['n_actions']
        
        print(f"✅ Modèle chargé:")
        print(f"   - Q-Table shape: {self.q_table.shape}")
        print(f"   - State space: {self.n_states}")
        print(f"   - Action space: {self.n_actions}")
    
    def choose_action(self, state):
        """
        Choisit une action pour un état donné (politique greedy)
        
        Args:
            state: Array [budget_level, latency_level, popularity_level, replicas]
        
        Returns:
            int: Index de l'action (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        """
        # Convertir l'état en index de la Q-table
        state_index = self._state_to_index(state)
        
        # Politique greedy: choisir l'action avec la plus haute Q-value
        action = int(np.argmax(self.q_table[state_index]))
        
        return action
    
    def get_q_value(self, state, action):
        """
        Retourne la Q-value pour un état et une action
        
        Args:
            state: Array [budget_level, latency_level, popularity_level, replicas]
            action: Index de l'action
        
        Returns:
            float: Q-value
        """
        state_index = self._state_to_index(state)
        return float(self.q_table[state_index, action])
    
    def _state_to_index(self, state):
        """
        Convertit un état [budget, latency, popularity, replicas] en index
        
        Discrétisation (même que Java):
        - Budget: 3 niveaux (LOW=0, MEDIUM=1, HIGH=2)
        - Latency: 3 niveaux (LOW=0, MEDIUM=1, HIGH=2)
        - Popularity: 3 niveaux (LOW=0, MEDIUM=1, HIGH=2)
        - Replicas: 4 niveaux (0, 1, 2, 3)
        
        Index = budget * 36 + latency * 12 + popularity * 4 + replicas
        """
        budget = int(state[0])
        latency = int(state[1])
        popularity = int(state[2])
        replicas = int(state[3])
        
        index = budget * 36 + latency * 12 + popularity * 4 + replicas
        
        return index
    
    def get_model_info(self):
        """Retourne les informations sur le modèle"""
        return f"Q-Learning Model (States: {self.n_states}, Actions: {self.n_actions})"
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonQLearningAgent"]


def main():
    parser = argparse.ArgumentParser(
        description='Client Python pour Py4J Gateway'
    )
    parser.add_argument('--model', type=str, required=True,
                       help='Chemin vers le modèle (.pkl)')
    parser.add_argument('--host', type=str, default='localhost',
                       help='Hôte du gateway Java (défaut: localhost)')
    parser.add_argument('--port', type=int, default=25333,
                       help='Port du gateway Java (défaut: 25333)')
    
    args = parser.parse_args()
    
    print("="*70)
    print("Client Python Py4J pour Q-Learning")
    print("="*70)
    print()
    
    # Charger le modèle
    try:
        model = QLearningModel(args.model)
    except Exception as e:
        print(f"❌ Erreur lors du chargement du modèle: {e}")
        sys.exit(1)
    
    print()
    print(f"🔌 Connexion au serveur Java...")
    print(f"   - Host: {args.host}")
    print(f"   - Port: {args.port}")
    print()
    
    # Se connecter au serveur Java
    max_retries = 10
    retry_delay = 2
    
    for attempt in range(max_retries):
        try:
            # Connexion avec CallbackServer comme dans rl-cloudsimplus-greenscheduling
            gateway = JavaGateway(
                gateway_parameters=GatewayParameters(
                    address=args.host,
                    port=args.port,
                    auto_convert=True
                ),
                callback_server_parameters=CallbackServerParameters()
            )
            
            # Tester la connexion
            entry_point = gateway.entry_point
            
            print("✅ Connexion établie avec le serveur Java!")
            print()
            
            # Enregistrer la Q-table auprès de Java
            print("📝 Enregistrement de la Q-table auprès de Java...")
            
            # Convertir la Q-table numpy en liste de listes pour Py4J
            q_table_list = model.q_table.tolist()
            
            entry_point.registerQTable(q_table_list, model.get_model_info())
            
            print("✅ Q-table enregistrée avec succès!")
            print()
            print("📡 La Q-table Python est maintenant disponible pour Java")
            print("   Java peut utiliser la Q-table pour choisir des actions")
            print()
            print("⏳ Maintien de la connexion active...")
            print("   Appuyez sur Ctrl+C pour arrêter")
            print()
            
            # Garder la connexion active
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                print("\n\n🛑 Arrêt du client Python...")
                gateway.shutdown()
                print("✅ Connexion fermée")
                sys.exit(0)
                
        except Exception as e:
            if attempt < max_retries - 1:
                print(f"⚠️  Tentative {attempt + 1}/{max_retries} échouée: {e}")
                print(f"   Nouvelle tentative dans {retry_delay}s...")
                time.sleep(retry_delay)
            else:
                print(f"\n❌ Impossible de se connecter après {max_retries} tentatives")
                print(f"   Erreur: {e}")
                print()
                print("Vérifiez que:")
                print("  1. Le serveur Java Gateway est démarré")
                print("  2. Le port {args.port} est accessible")
                print("  3. Aucun firewall ne bloque la connexion")
                sys.exit(1)


if __name__ == "__main__":
    main()
