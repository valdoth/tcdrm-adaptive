"""
Wrapper pour SimpleQLearningAgent compatible avec generate_graphs_unified.py
"""

import numpy as np
from agents.simple_qlearning_agent import SimpleQLearningAgent


class SimpleQLearningWrapper:
    """
    Wrapper pour SimpleQLearningAgent avec interface compatible.
    """
    
    def __init__(self, n_states: int = 243, n_actions: int = 3):
        self.agent = SimpleQLearningAgent(n_states=n_states, n_actions=n_actions)
        self.n_states = n_states
        self.n_actions = n_actions
        self.q_table = self.agent.q_table
    
    def select_action(self, state, training: bool = False, valid_actions=None) -> int:
        """
        Sélectionne une action.
        
        Args:
            state: Vecteur d'état (5D) ou index
            training: Mode entraînement (ignoré, toujours greedy en évaluation)
            valid_actions: Actions valides (non utilisé ici)
        """
        # Convertir état en index
        if isinstance(state, np.ndarray) and state.shape == (5,):
            rt, cost, pop, bud, net = state
            state_idx = int(rt * 81 + cost * 27 + pop * 9 + bud * 3 + net)
        else:
            state_idx = int(state)
        
        # Toujours greedy en évaluation
        return self.agent.select_action(state_idx, valid_actions, training=False)
    
    def load(self, filepath: str):
        """Charge l'agent."""
        self.agent.load(filepath)
        self.q_table = self.agent.q_table


__all__ = ['SimpleQLearningWrapper']
