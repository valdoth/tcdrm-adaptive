"""
Simple Q-Learning Agent - Implémentation propre et robuste
Inspiré des meilleures pratiques des repositories GitHub RL
"""

import numpy as np
import pickle
from typing import Optional, List


class SimpleQLearningAgent:
    """
    Agent Q-Learning tabulaire simple et robuste.
    Basé sur l'algorithme classique de Sutton & Barto.
    """
    
    def __init__(
        self,
        n_states: int,
        n_actions: int,
        learning_rate: float = 0.1,
        discount_factor: float = 0.99,
        epsilon_start: float = 1.0,
        epsilon_min: float = 0.01,
        epsilon_decay: float = 0.995
    ):
        """
        Initialise l'agent Q-Learning.
        
        Args:
            n_states: Nombre d'états
            n_actions: Nombre d'actions
            learning_rate: Taux d'apprentissage (alpha)
            discount_factor: Facteur de discount (gamma)
            epsilon_start: Epsilon initial pour exploration
            epsilon_min: Epsilon minimum
            epsilon_decay: Facteur de décroissance epsilon (multiplicatif)
        """
        self.n_states = n_states
        self.n_actions = n_actions
        self.alpha = learning_rate
        self.gamma = discount_factor
        self.epsilon = epsilon_start
        self.epsilon_min = epsilon_min
        self.epsilon_decay = epsilon_decay
        
        # Initialiser Q-table à zéro
        self.q_table = np.zeros((n_states, n_actions))
        
        # Statistiques
        self.training_steps = 0
        self.episodes_completed = 0
        
    def select_action(self, state: int, valid_actions: Optional[List[int]] = None, training: bool = True) -> int:
        """
        Sélectionne une action selon epsilon-greedy.
        
        Args:
            state: Index de l'état actuel
            valid_actions: Liste des actions valides (None = toutes valides)
            training: Si True, utilise epsilon-greedy; sinon greedy pur
            
        Returns:
            Action sélectionnée
        """
        if valid_actions is None:
            valid_actions = list(range(self.n_actions))
        
        # Epsilon-greedy
        if training and np.random.random() < self.epsilon:
            # Exploration: action aléatoire
            return np.random.choice(valid_actions)
        else:
            # Exploitation: meilleure action
            q_values = self.q_table[state, valid_actions]
            best_idx = np.argmax(q_values)
            return valid_actions[best_idx]
    
    def update(self, state: int, action: int, reward: float, next_state: int, done: bool):
        """
        Met à jour la Q-table selon l'équation de Bellman.
        
        Q(s,a) ← Q(s,a) + α[r + γ·max_a' Q(s',a') - Q(s,a)]
        
        Args:
            state: État actuel
            action: Action prise
            reward: Récompense reçue
            next_state: État suivant
            done: Episode terminé?
        """
        # Valeur actuelle
        current_q = self.q_table[state, action]
        
        # Valeur future (0 si terminal)
        if done:
            target_q = reward
        else:
            max_next_q = np.max(self.q_table[next_state])
            target_q = reward + self.gamma * max_next_q
        
        # Mise à jour Q-table
        self.q_table[state, action] = current_q + self.alpha * (target_q - current_q)
        
        self.training_steps += 1
    
    def decay_epsilon(self):
        """Décroissance epsilon (appelé après chaque épisode)."""
        self.epsilon = max(self.epsilon_min, self.epsilon * self.epsilon_decay)
        self.episodes_completed += 1
    
    def save(self, filepath: str):
        """Sauvegarde l'agent."""
        data = {
            'q_table': self.q_table,
            'n_states': self.n_states,
            'n_actions': self.n_actions,
            'alpha': self.alpha,
            'gamma': self.gamma,
            'epsilon': self.epsilon,
            'epsilon_min': self.epsilon_min,
            'epsilon_decay': self.epsilon_decay,
            'training_steps': self.training_steps,
            'episodes_completed': self.episodes_completed
        }
        with open(filepath, 'wb') as f:
            pickle.dump(data, f)
    
    def load(self, filepath: str):
        """Charge l'agent."""
        with open(filepath, 'rb') as f:
            data = pickle.load(f)
        
        self.q_table = data['q_table']
        self.n_states = data['n_states']
        self.n_actions = data['n_actions']
        self.alpha = data['alpha']
        self.gamma = data['gamma']
        self.epsilon = data.get('epsilon', self.epsilon_min)
        self.epsilon_min = data.get('epsilon_min', 0.01)
        self.epsilon_decay = data.get('epsilon_decay', 0.995)
        self.training_steps = data.get('training_steps', 0)
        self.episodes_completed = data.get('episodes_completed', 0)
    
    def get_stats(self):
        """Retourne les statistiques de l'agent."""
        non_zero_states = np.sum(np.any(self.q_table != 0, axis=1))
        return {
            'training_steps': self.training_steps,
            'episodes_completed': self.episodes_completed,
            'epsilon': self.epsilon,
            'states_explored': non_zero_states,
            'exploration_rate': non_zero_states / self.n_states * 100,
            'q_mean': np.mean(self.q_table),
            'q_std': np.std(self.q_table),
            'q_max': np.max(self.q_table),
            'q_min': np.min(self.q_table)
        }


__all__ = ['SimpleQLearningAgent']
