"""
Client Python Py4J pour connecter les modèles RL entraînés à Java/CloudSim
Charge les modèles Q-Learning Simple et DQN et les expose via Py4J
"""

import sys
import os
import argparse
import numpy as np
import torch
from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.dqn_agent import DQNAgent
from envs.tcdrm_qlearning_env import TcdrmQLearningEnv


class PythonRLBridge:
    """
    Pont Python pour exposer les modèles RL à Java via Py4J.
    """
    
    def __init__(self, qlearning_model_path=None, dqn_model_path=None):
        """
        Initialise le pont avec les modèles entraînés.
        
        Args:
            qlearning_model_path: Chemin vers le modèle Q-Learning (.pkl)
            dqn_model_path: Chemin vers le modèle DQN (.pt)
        """
        self.qlearning_agent = None
        self.dqn_agent = None
        self.env = None
        
        # Charger Q-Learning si fourni
        if qlearning_model_path and os.path.exists(qlearning_model_path):
            print(f"📦 Chargement Q-Learning: {qlearning_model_path}")
            self.qlearning_agent = SimpleQLearningAgent(n_states=243, n_actions=3)
            self.qlearning_agent.load(qlearning_model_path)
            print(f"✅ Q-Learning chargé (epsilon={self.qlearning_agent.epsilon:.4f})")
        
        # Charger DQN si fourni
        if dqn_model_path and os.path.exists(dqn_model_path):
            print(f"📦 Chargement DQN: {dqn_model_path}")
            self.dqn_agent = DQNAgent(
                state_dim=8,
                action_dim=3,
                learning_rate=0.001,
                discount_factor=0.99
            )
            checkpoint = torch.load(dqn_model_path, map_location='cpu')
            # Le modèle DQN sauvegarde avec 'policy_net_state_dict'
            self.dqn_agent.policy_net.load_state_dict(checkpoint['policy_net_state_dict'])
            self.dqn_agent.policy_net.eval()
            print(f"✅ DQN chargé")
        
        # Créer environnement pour conversions d'état
        self.env = TcdrmQLearningEnv(data_gb=5.3)
        
        print("✅ Pont Python initialisé")
    
    def selectActionQLearning(self, state_array):
        """
        Sélectionne une action avec Q-Learning.
        
        Args:
            state_array: Liste Java [latency, budget, replicas, popularity, cost]
            
        Returns:
            Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        if self.qlearning_agent is None:
            return 0  # NOOP par défaut
        
        # Convertir état Java en état discret
        state = self._java_state_to_discrete(state_array)
        state_idx = self.env.state_to_index(state)
        
        # Sélectionner action (greedy, pas d'exploration)
        action = self.qlearning_agent.select_action(state_idx, valid_actions=None, training=False)
        
        return int(action)
    
    def selectActionDQN(self, state_array):
        """
        Sélectionne une action avec DQN.
        
        Args:
            state_array: Liste Java [latency, budget, replicas, popularity, cost, ...]
            
        Returns:
            Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        if self.dqn_agent is None:
            return 0  # NOOP par défaut
        
        # Convertir en tensor PyTorch
        state_tensor = torch.FloatTensor(list(state_array)).unsqueeze(0)
        
        # Sélectionner action (greedy)
        with torch.no_grad():
            q_values = self.dqn_agent.policy_net(state_tensor)
            action = q_values.argmax(dim=1).item()
        
        return int(action)
    
    def _java_state_to_discrete(self, state_array):
        """
        Convertit un état Java en état discret pour Q-Learning.
        
        Args:
            state_array: [latency, budget, replicas, popularity, cost]
            
        Returns:
            État discret numpy array [RT, COST, POP, BUD, NET]
        """
        latency = float(state_array[0])
        budget = float(state_array[1])
        replicas = int(state_array[2])
        popularity = float(state_array[3])
        total_cost = float(state_array[4])
        
        # Discrétiser RT (Response Time)
        mu_RT = 100.0  # Moyenne
        sigma_RT = 50.0
        if latency <= mu_RT:
            rt_disc = 0
        elif latency <= mu_RT + sigma_RT:
            rt_disc = 1
        else:
            rt_disc = 2
        
        # Discrétiser COST
        cost_normalized = total_cost / max(1, 100)  # Normaliser
        if cost_normalized <= 0.7:
            cost_disc = 0
        elif cost_normalized <= 1.0:
            cost_disc = 1
        else:
            cost_disc = 2
        
        # Discrétiser POP (Popularity)
        if popularity < 0.33:
            pop_disc = 0
        elif popularity < 0.67:
            pop_disc = 1
        else:
            pop_disc = 2
        
        # Discrétiser BUD (Budget)
        budget_ratio = budget / 1000.0  # Budget initial
        if budget_ratio >= 0.6:
            bud_disc = 0
        elif budget_ratio >= 0.3:
            bud_disc = 1
        else:
            bud_disc = 2
        
        # Discrétiser NET (Network/Replicas)
        if replicas >= 2:
            net_disc = 0
        elif replicas == 1:
            net_disc = 1
        else:
            net_disc = 2
        
        return np.array([rt_disc, cost_disc, pop_disc, bud_disc, net_disc], dtype=np.int32)
    
    def isQLearningReady(self):
        """Vérifie si Q-Learning est chargé."""
        return self.qlearning_agent is not None
    
    def isDQNReady(self):
        """Vérifie si DQN est chargé."""
        return self.dqn_agent is not None
    
    def getModelInfo(self):
        """Retourne les informations sur les modèles chargés."""
        info = []
        if self.qlearning_agent:
            stats = self.qlearning_agent.get_stats()
            info.append(f"Q-Learning: {stats['states_explored']}/{stats['training_steps']} états explorés")
        if self.dqn_agent:
            info.append("DQN: Modèle chargé")
        return " | ".join(info) if info else "Aucun modèle chargé"
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]


def main():
    parser = argparse.ArgumentParser(description='Client Python Py4J pour modèles RL')
    parser.add_argument('--qlearning-model', type=str, 
                       default='models/simple_qlearning.pkl',
                       help='Chemin vers le modèle Q-Learning')
    parser.add_argument('--dqn-model', type=str,
                       default='results/dqn/dqn_model.pt',
                       help='Chemin vers le modèle DQN')
    parser.add_argument('--port', type=int, default=25333,
                       help='Port du Gateway Java')
    
    args = parser.parse_args()
    
    print("="*80)
    print("CLIENT PYTHON PY4J - MODÈLES RL POUR JAVA/CLOUDSIM")
    print("="*80)
    print()
    
    # Créer le pont avec les modèles
    bridge = PythonRLBridge(
        qlearning_model_path=args.qlearning_model if os.path.exists(args.qlearning_model) else None,
        dqn_model_path=args.dqn_model if os.path.exists(args.dqn_model) else None
    )
    
    print()
    print(f"📡 Connexion au Gateway Java (port {args.port})...")
    
    try:
        # Connexion au Gateway Java
        gateway = JavaGateway(
            gateway_parameters=GatewayParameters(port=args.port),
            callback_server_parameters=CallbackServerParameters()
        )
        
        # Enregistrer le pont Python
        gateway.entry_point.registerPythonBridge(bridge)
        
        print("✅ Connecté au Gateway Java!")
        print()
        print("Modèles disponibles:")
        print(f"  - Q-Learning: {'✅' if bridge.isQLearningReady() else '❌'}")
        print(f"  - DQN: {'✅' if bridge.isDQNReady() else '❌'}")
        print()
        print("🎯 Prêt à recevoir les requêtes de Java/CloudSim")
        print("   Appuyez sur Ctrl+C pour arrêter")
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


if __name__ == '__main__':
    main()
