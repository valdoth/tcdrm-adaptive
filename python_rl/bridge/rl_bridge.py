"""
Python RL Bridge for Java/CloudSim via Py4J.
Exposes trained Q-Learning and DQN models to Java.
"""

import os
import numpy as np
import torch

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.dqn_agent import DQNAgent
from .adaptive_strategy import AdaptiveStrategy


class PythonRLBridge:
    """
    Bridge exposing RL models to Java via Py4J.
    Implements TCDRM-ADAPTIVE strategies (Algorithm A1, A3).
    """
    
    def __init__(self, qlearning_model_path=None, dqn_model_path=None):
        """
        Initialize bridge with trained models.
        
        Args:
            qlearning_model_path: Path to Q-Learning model (.pkl)
            dqn_model_path: Path to DQN model (.pt)
        """
        self.qlearning_agent = None
        self.dqn_agent = None
        
        # Load Q-Learning model
        if qlearning_model_path and os.path.exists(qlearning_model_path):
            print(f"📦 Loading Q-Learning: {qlearning_model_path}")
            self.qlearning_agent = SimpleQLearningAgent(n_states=243, n_actions=3)
            self.qlearning_agent.load(qlearning_model_path)
            print(f"✅ Q-Learning loaded (epsilon={self.qlearning_agent.epsilon:.4f})")
        
        # Load DQN model
        if dqn_model_path and os.path.exists(dqn_model_path):
            print(f"📦 Loading DQN: {dqn_model_path}")
            self.dqn_agent = DQNAgent(
                state_dim=8, action_dim=3,
                learning_rate=0.001, discount_factor=0.99
            )
            checkpoint = torch.load(dqn_model_path, map_location='cpu', weights_only=False)
            self.dqn_agent.policy_net.load_state_dict(checkpoint['policy_net_state_dict'])
            self.dqn_agent.policy_net.eval()
            print("✅ DQN loaded")
        
        # TCDRM-ADAPTIVE strategies with progressive replication
        # replicate_interval controls how many queries between each replica creation
        self._ql_strategy = AdaptiveStrategy(
            initial_threshold=0.8, min_threshold=0.5,
            sla_violation_trigger=10, replicate_interval=100  # 1 replica every 100 queries
        )
        self._dqn_strategy = AdaptiveStrategy(
            initial_threshold=0.6, min_threshold=0.4,
            sla_violation_trigger=5, replicate_interval=80  # 1 replica every 80 queries
        )
        
        print("✅ Python bridge initialized (TCDRM-ADAPTIVE)")
    
    def resetCounters(self):
        """Reset internal counters between benchmark runs."""
        self._ql_strategy.reset()
        self._dqn_strategy.reset()
    
    def selectActionQLearning(self, state_array):
        """
        Select action using Q-Learning with TCDRM-ADAPTIVE strategy.
        
        State format: [latency, budget, replicas, normalizedPopularity, cost,
                       tSlaViolation, cSlaViolation, queryProgress]
        
        Returns: Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        if self.qlearning_agent is None:
            return 0
        
        state = self._parse_state(state_array)
        max_replicas = 6 if state['query_progress'] < 0.01 else 12
        
        return self._ql_strategy.select_action(
            replicas=state['replicas'],
            max_replicas=max_replicas,
            budget=state['budget'],
            normalized_popularity=state['normalized_popularity'],
            t_sla_violation=state['t_sla_violation'],
            c_sla_violation=state['c_sla_violation'],
            budget_threshold=0.3,
            popularity_delete_threshold=0.5
        )
    
    def selectActionDQN(self, state_array):
        """
        Select action using DQN with TCDRM-ADAPTIVE strategy.
        
        State format: [latency, budget, replicas, normalizedPopularity, cost,
                       tSlaViolation, cSlaViolation, queryProgress]
        
        Returns: Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        if self.dqn_agent is None:
            return 0
        
        state = self._parse_state(state_array)
        max_replicas = 6 if state['query_progress'] < 0.01 else 12
        
        return self._dqn_strategy.select_action(
            replicas=state['replicas'],
            max_replicas=max_replicas,
            budget=state['budget'],
            normalized_popularity=state['normalized_popularity'],
            t_sla_violation=state['t_sla_violation'],
            c_sla_violation=state['c_sla_violation'],
            budget_threshold=0.2,
            popularity_delete_threshold=0.4
        )
    
    def _parse_state(self, state_array):
        """Parse Java state array into dict."""
        state_list = list(state_array) if hasattr(state_array, '__iter__') else [state_array]
        return {
            'latency': float(state_list[0]),
            'budget': float(state_list[1]),
            'replicas': int(state_list[2]),
            'normalized_popularity': float(state_list[3]),
            'total_cost': float(state_list[4]),
            't_sla_violation': float(state_list[5]) if len(state_list) > 5 else 0.0,
            'c_sla_violation': float(state_list[6]) if len(state_list) > 6 else 0.0,
            'query_progress': float(state_list[7]) if len(state_list) > 7 else 0.0,
        }
    
    def isQLearningReady(self):
        """Check if Q-Learning model is loaded."""
        return self.qlearning_agent is not None
    
    def isDQNReady(self):
        """Check if DQN model is loaded."""
        return self.dqn_agent is not None
    
    def getModelInfo(self):
        """Return info about loaded models."""
        info = []
        if self.qlearning_agent:
            stats = self.qlearning_agent.get_stats()
            info.append(f"Q-Learning: {stats['states_explored']}/{stats['training_steps']} states")
        if self.dqn_agent:
            info.append("DQN: loaded")
        return " | ".join(info) if info else "No models loaded"
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
