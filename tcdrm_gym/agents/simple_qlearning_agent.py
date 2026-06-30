"""
Simple Q-Learning Agent - Implémentation propre et robuste
Inspiré des meilleures pratiques des repositories GitHub RL
"""

import numpy as np
import pickle
from typing import Optional, List


class SimpleQLearningAgent:
	"""
	Agent Q-Learning amélioré avec Double Q-learning et techniques modernes.
	Basé sur les recherches de Sutton & Barto + améliorations récentes.
	"""
    
	def __init__(
		self,
		n_states: int,
		n_actions: int,
		learning_rate: float = 0.1,
		discount_factor: float = 0.99,
		epsilon_start: float = 1.0,
		epsilon_min: float = 0.01,
		epsilon_decay: float = 0.995,
		use_double_q: bool = True,
		adaptive_lr: bool = True,
		epsilon_schedule: str = 'exp',
		epsilon_linear_steps: int = 10000,
		lambda_trace: float = 0.0,
		optimistic_init: float = 0.0
	):
		"""
		Initialise l'agent Q-Learning amélioré.
        
		Args:
			n_states: Nombre d'états
			n_actions: Nombre d'actions
			learning_rate: Taux d'apprentissage initial (alpha)
			discount_factor: Facteur de discount (gamma)
			epsilon_start: Epsilon initial pour exploration
			epsilon_min: Epsilon minimum
			epsilon_decay: Facteur de décroissance epsilon (multiplicatif)
			use_double_q: Utiliser Double Q-learning
			adaptive_lr: Utiliser learning rate adaptatif
			optimistic_init: Valeur d'initialisation optimiste (0.0 = neutre)
		"""
		self.n_states = n_states
		self.n_actions = n_actions
		self.alpha_init = learning_rate
		self.alpha = learning_rate
		self.gamma = discount_factor
		self.epsilon = epsilon_start
		self.epsilon_min = epsilon_min
		self.epsilon_decay = epsilon_decay
		self.use_double_q = use_double_q
		self.adaptive_lr = adaptive_lr
		self.epsilon_schedule = epsilon_schedule
		self.epsilon_linear_steps = max(1, epsilon_linear_steps)
		self._eps_step = 0
        
		# Initialiser Q-tables (deux pour Double Q-learning)
		if use_double_q:
			self.q_table_a = np.full((n_states, n_actions), optimistic_init)
			self.q_table_b = np.full((n_states, n_actions), optimistic_init)
			self.q_table = (self.q_table_a + self.q_table_b) / 2.0  # Moyenne pour compatibilité
		else:
			self.q_table = np.full((n_states, n_actions), optimistic_init)
        
		# Compteurs de visites pour learning rate adaptatif
		self.visit_counts = np.zeros((n_states, n_actions))

		# Eligibility traces (Q(λ))
		self.lambda_trace = float(lambda_trace)
		self.traces = np.zeros((n_states, n_actions)) if self.lambda_trace > 0 else None
        
		# Statistiques
		self.training_steps = 0
		self.episodes_completed = 0
		self.recent_rewards = []

	def start_episode(self):
		if self.traces is not None:
			self.traces.fill(0.0)

	def select_action(self, state: int, valid_actions: Optional[List[int]] = None, training: bool = True) -> int:
		"""
		Sélectionne une action selon epsilon-greedy amélioré.
		"""
		if valid_actions is None:
			valid_actions = list(range(self.n_actions))
        
		if training and np.random.random() < self.epsilon:
			if self.training_steps > 100:
				visit_counts_valid = self.visit_counts[state, valid_actions]
				if visit_counts_valid.sum() > 0:
					probs = 1.0 / (visit_counts_valid + 1.0)
					probs = probs / probs.sum()
					return np.random.choice(valid_actions, p=probs)
			return np.random.choice(valid_actions)
		else:
			if self.use_double_q:
				q_values = (self.q_table_a[state, valid_actions] + 
						   self.q_table_b[state, valid_actions]) / 2.0
			else:
				q_values = self.q_table[state, valid_actions]
			best_idx = np.argmax(q_values)
			return valid_actions[best_idx]
    
	def update(self, state: int, action: int, reward: float, next_state: int, done: bool):
		"""Met à jour la Q-table: Double Q-learning ou Q(λ) + LR adaptatif."""
		self.visit_counts[state, action] += 1
        
		if self.adaptive_lr:
			alpha = self.alpha_init / (1.0 + 0.01 * self.visit_counts[state, action])
		else:
			alpha = self.alpha

		# If using eligibility traces — Q(λ) avec support Double-Q cohérent
		if self.lambda_trace > 0.0 and self.traces is not None:
			# Accumulating traces
			self.traces[state, action] += 1.0
			# TD error — utilise la moyenne des deux tables pour le calcul de la cible
			if done:
				target_q = reward
			else:
				if self.use_double_q:
					max_next = np.max((self.q_table_a[next_state] + self.q_table_b[next_state]) / 2.0)
				else:
					max_next = np.max(self.q_table[next_state])
				target_q = reward + self.gamma * max_next
			if self.use_double_q:
				current_q = (self.q_table_a[state, action] + self.q_table_b[state, action]) / 2.0
			else:
				current_q = self.q_table[state, action]
			delta = target_q - current_q
			# Appliquer les traces aux DEUX tables pour rester cohérent avec select_action
			if self.use_double_q:
				self.q_table_a += alpha * delta * self.traces
				self.q_table_b += alpha * delta * self.traces
				self.q_table = (self.q_table_a + self.q_table_b) / 2.0
			else:
				self.q_table += alpha * delta * self.traces
			# Decay traces
			self.traces *= self.gamma * self.lambda_trace
		else:
			# Default path: Double Q-learning or single-table Q-learning
			if self.use_double_q:
				if np.random.random() < 0.5:
					current_q = self.q_table_a[state, action]
					if done:
						target_q = reward
					else:
						best_next_action = np.argmax(self.q_table_a[next_state])
						next_q = self.q_table_b[next_state, best_next_action]
						target_q = reward + self.gamma * next_q
					self.q_table_a[state, action] = current_q + alpha * (target_q - current_q)
				else:
					current_q = self.q_table_b[state, action]
					if done:
						target_q = reward
					else:
						best_next_action = np.argmax(self.q_table_b[next_state])
						next_q = self.q_table_a[next_state, best_next_action]
						target_q = reward + self.gamma * next_q
					self.q_table_b[state, action] = current_q + alpha * (target_q - current_q)
				self.q_table[state, action] = (self.q_table_a[state, action] + self.q_table_b[state, action]) / 2.0
			else:
				current_q = self.q_table[state, action]
				if done:
					target_q = reward
				else:
					max_next_q = np.max(self.q_table[next_state])
					target_q = reward + self.gamma * max_next_q
				self.q_table[state, action] = current_q + alpha * (target_q - current_q)
        
		self.recent_rewards.append(reward)
		if len(self.recent_rewards) > 100:
			self.recent_rewards.pop(0)

		self.training_steps += 1
    
	def decay_epsilon(self):
		if self.epsilon_schedule == 'exp':
			self.epsilon = max(self.epsilon_min, self.epsilon * self.epsilon_decay)
		elif self.epsilon_schedule == 'linear':
			self._eps_step += 1
			eps0 = 1.0
			progress = min(1.0, self._eps_step / float(self.epsilon_linear_steps))
			self.epsilon = max(self.epsilon_min, eps0 - (eps0 - self.epsilon_min) * progress)
		elif self.epsilon_schedule == 'cosine':
			self._eps_step += 1
			T = max(1, self.epsilon_linear_steps)
			cos_term = 0.5 * (1 + np.cos(np.pi * min(1.0, self._eps_step / T)))
			self.epsilon = self.epsilon_min + (1.0 - self.epsilon_min) * cos_term
		else:
			self.epsilon = max(self.epsilon_min, self.epsilon * self.epsilon_decay)
		self.episodes_completed += 1
    
	def get_action_values(self, state: int) -> np.ndarray:
		if self.use_double_q:
			return (self.q_table_a[state] + self.q_table_b[state]) / 2.0
		return self.q_table[state].copy()
    
	def save(self, filepath: str):
		data = {
			'q_table': self.q_table,
			'n_states': self.n_states,
			'n_actions': self.n_actions,
			'alpha': self.alpha,
			'alpha_init': self.alpha_init,
			'gamma': self.gamma,
			'epsilon': self.epsilon,
			'epsilon_min': self.epsilon_min,
			'epsilon_decay': self.epsilon_decay,
			'use_double_q': self.use_double_q,
			'adaptive_lr': self.adaptive_lr,
			'training_steps': self.training_steps,
			'episodes_completed': self.episodes_completed,
			'visit_counts': self.visit_counts,
			'recent_rewards': self.recent_rewards
		}
        
		if self.use_double_q:
			data['q_table_a'] = self.q_table_a
			data['q_table_b'] = self.q_table_b
        
		with open(filepath, 'wb') as f:
			pickle.dump(data, f)
    
	def load(self, filepath: str):
		with open(filepath, 'rb') as f:
			data = pickle.load(f)
        
		self.q_table = data['q_table']
		self.n_states = data['n_states']
		self.n_actions = data['n_actions']
		self.alpha = data.get('alpha', 0.1)
		self.alpha_init = data.get('alpha_init', self.alpha)
		self.gamma = data['gamma']
		self.epsilon = data.get('epsilon', self.epsilon_min)
		self.epsilon_min = data.get('epsilon_min', 0.01)
		self.epsilon_decay = data.get('epsilon_decay', 0.995)
		self.use_double_q = data.get('use_double_q', False)
		self.adaptive_lr = data.get('adaptive_lr', False)
		self.training_steps = data.get('training_steps', 0)
		self.episodes_completed = data.get('episodes_completed', 0)
		self.visit_counts = data.get('visit_counts', np.zeros((self.n_states, self.n_actions)))
		self.recent_rewards = data.get('recent_rewards', [])
        
		if self.use_double_q:
			self.q_table_a = data.get('q_table_a', self.q_table.copy())
			self.q_table_b = data.get('q_table_b', self.q_table.copy())
    
	def get_stats(self):
		non_zero_states = np.sum(np.any(self.q_table != 0, axis=1))
		stats = {
			'training_steps': self.training_steps,
			'episodes_completed': self.episodes_completed,
			'epsilon': self.epsilon,
			'states_explored': non_zero_states,
			'exploration_rate': non_zero_states / self.n_states * 100,
			'q_mean': np.mean(self.q_table),
			'q_std': np.std(self.q_table),
			'q_max': np.max(self.q_table),
			'q_min': np.min(self.q_table),
			'avg_visits_per_state_action': np.mean(self.visit_counts[self.visit_counts > 0]) if np.any(self.visit_counts > 0) else 0
		}
        
		if len(self.recent_rewards) > 0:
			stats['avg_recent_reward'] = np.mean(self.recent_rewards)
			stats['std_recent_reward'] = np.std(self.recent_rewards)
        
		if self.use_double_q:
			stats['q_a_mean'] = np.mean(self.q_table_a)
			stats['q_b_mean'] = np.mean(self.q_table_b)
			stats['q_diff'] = np.mean(np.abs(self.q_table_a - self.q_table_b))
        
		return stats


__all__ = ['SimpleQLearningAgent']
