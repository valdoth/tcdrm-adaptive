"""
Deep Q-Network (DQN) Agent pour TCDRM v2
"""

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from collections import deque
import random
from typing import Tuple, List


class DuelingDQNNetwork(nn.Module):
	def __init__(self, state_dim: int = 8, action_dim: int = 3, hidden_dims: list = [64, 64]):
		super(DuelingDQNNetwork, self).__init__()
		self.feature_layer = nn.Sequential(
			nn.Linear(state_dim, hidden_dims[0]),
			nn.ReLU(),
			nn.Linear(hidden_dims[0], hidden_dims[1]),
			nn.ReLU()
		)
		self.value_stream = nn.Sequential(
			nn.Linear(hidden_dims[1], 32),
			nn.ReLU(),
			nn.Linear(32, 1)
		)
		self.advantage_stream = nn.Sequential(
			nn.Linear(hidden_dims[1], 32),
			nn.ReLU(),
			nn.Linear(32, action_dim)
		)
    
	def forward(self, x):
		features = self.feature_layer(x)
		value = self.value_stream(features)
		advantage = self.advantage_stream(features)
		q_values = value + (advantage - advantage.mean(dim=1, keepdim=True))
		return q_values


class DQNNetwork(nn.Module):
	def __init__(self, state_dim: int = 8, action_dim: int = 3, hidden_dims: list = [64, 64, 32]):
		super(DQNNetwork, self).__init__()
		layers = []
		input_dim = state_dim
		for hidden_dim in hidden_dims:
			layers.append(nn.Linear(input_dim, hidden_dim))
			layers.append(nn.ReLU())
			input_dim = hidden_dim
		layers.append(nn.Linear(input_dim, action_dim))
		self.network = nn.Sequential(*layers)
    
	def forward(self, x):
		return self.network(x)


class PrioritizedReplayBuffer:
	def __init__(self, capacity: int = 10000, alpha: float = 0.6, beta_start: float = 0.4, beta_frames: int = 100000):
		self.capacity = capacity
		self.alpha = alpha
		self.beta_start = beta_start
		self.beta_frames = beta_frames
		self.frame = 1
		self.buffer = []
		self.priorities = np.zeros(capacity, dtype=np.float32)
		self.position = 0
    
	def push(self, state, action, reward, next_state, done):
		max_priority = self.priorities.max() if self.buffer else 1.0
		if len(self.buffer) < self.capacity:
			self.buffer.append((state, action, reward, next_state, done))
		else:
			self.buffer[self.position] = (state, action, reward, next_state, done)
		self.priorities[self.position] = max_priority
		self.position = (self.position + 1) % self.capacity
    
	def sample(self, batch_size: int) -> Tuple:
		priorities = self.priorities if len(self.buffer) == self.capacity else self.priorities[:len(self.buffer)]
		probs = priorities ** self.alpha
		probs /= probs.sum()
		indices = np.random.choice(len(self.buffer), batch_size, p=probs, replace=False)
		beta = min(1.0, self.beta_start + self.frame * (1.0 - self.beta_start) / self.beta_frames)
		weights = (len(self.buffer) * probs[indices]) ** (-beta)
		weights /= weights.max()
		self.frame += 1
		batch = [self.buffer[idx] for idx in indices]
		states, actions, rewards, next_states, dones = zip(*batch)
		return (
			np.array(states),
			np.array(actions),
			np.array(rewards),
			np.array(next_states),
			np.array(dones),
			indices,
			weights
		)
    
	def update_priorities(self, indices, priorities):
		for idx, priority in zip(indices, priorities):
			self.priorities[idx] = priority
    
	def __len__(self):
		return len(self.buffer)


class ReplayBuffer:
	def __init__(self, capacity: int = 10000):
		self.buffer = deque(maxlen=capacity)
    
	def push(self, state, action, reward, next_state, done):
		self.buffer.append((state, action, reward, next_state, done))
    
	def sample(self, batch_size: int) -> Tuple:
		batch = random.sample(self.buffer, batch_size)
		states, actions, rewards, next_states, dones = zip(*batch)
		return (
			np.array(states),
			np.array(actions),
			np.array(rewards),
			np.array(next_states),
			np.array(dones),
			None,
			np.ones(batch_size)
		)
    
	def update_priorities(self, indices, priorities):
		pass
    
	def __len__(self):
		return len(self.buffer)


class DQNAgent:
	def __init__(
		self,
		state_dim: int = 8,
		action_dim: int = 3,
		hidden_dims: list = None,
		learning_rate: float = 0.0005,
		discount_factor: float = 0.99,
		epsilon: float = 1.0,
		epsilon_min: float = 0.01,
		epsilon_decay_lambda: float = 0.0005,
		epsilon_schedule: str = 'exp',
		epsilon_linear_steps: int = 10000,
		buffer_capacity: int = 50000,
		batch_size: int = 64,
		target_update_freq: int = 1000,
		use_double_dqn: bool = True,
		use_dueling: bool = True,
		use_prioritized_replay: bool = True,
		tau: float = 0.005,
		gradient_clip: float = 1.0,
		device: str = None,
		lr_scheduler: str = 'none',
		scheduler_params: dict | None = None
	):
		if hidden_dims is None:
			hidden_dims = [64, 64]
		self.state_dim = state_dim
		self.action_dim = action_dim
		self.discount_factor = discount_factor
		self.epsilon = epsilon
		self.epsilon_min = epsilon_min
		self.epsilon_decay_lambda = epsilon_decay_lambda
		self.epsilon_schedule = epsilon_schedule
		self.epsilon_linear_steps = max(1, epsilon_linear_steps)
		self.batch_size = batch_size
		self.target_update_freq = target_update_freq
		self.use_double_dqn = use_double_dqn
		self.use_dueling = use_dueling
		self.tau = tau
		self.gradient_clip = gradient_clip
		self.training_steps = 0
		self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu") if device is None else torch.device(device)
		if use_dueling:
			self.policy_net = DuelingDQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
			self.target_net = DuelingDQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
		else:
			self.policy_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
			self.target_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
		self.target_net.load_state_dict(self.policy_net.state_dict())
		self.target_net.eval()
		self.optimizer = optim.Adam(self.policy_net.parameters(), lr=learning_rate, weight_decay=1e-5)
		self._init_scheduler(lr_scheduler, scheduler_params or {})
		self.loss_fn = nn.SmoothL1Loss()
		self.replay_buffer = PrioritizedReplayBuffer(buffer_capacity) if use_prioritized_replay else ReplayBuffer(buffer_capacity)
		self.update_count = 0
		self.episode_rewards = []
		self.losses = []
		self._eps_step = 0

	def _init_scheduler(self, lr_scheduler: str, params: dict):
		self.scheduler = None
		if lr_scheduler == 'cosine':
			T_max = int(params.get('T_max', 10000))
			eta_min = float(params.get('eta_min', 1e-6))
			self.scheduler = optim.lr_scheduler.CosineAnnealingLR(self.optimizer, T_max=T_max, eta_min=eta_min)
		elif lr_scheduler == 'step':
			step_size = int(params.get('step_size', 1000))
			gamma = float(params.get('gamma', 0.9))
			self.scheduler = optim.lr_scheduler.StepLR(self.optimizer, step_size=step_size, gamma=gamma)
    
	def select_action(self, state: np.ndarray, training: bool = True, action_mask: np.ndarray = None) -> int:
		if action_mask is None:
			action_mask = np.ones(self.action_dim)
		valid_actions = np.where(action_mask > 0)[0]
		if len(valid_actions) == 0:
			return 0
		if training and random.random() < self.epsilon:
			return np.random.choice(valid_actions)
		else:
			with torch.no_grad():
				state_tensor = torch.FloatTensor(state).unsqueeze(0).to(self.device)
				q_values = self.policy_net(state_tensor).cpu().numpy()[0]
				q_values[action_mask == 0] = -np.inf
				return q_values.argmax()
    
	def update(self, state, action, reward, next_state, done):
		self.replay_buffer.push(state, action, reward, next_state, done)
		if len(self.replay_buffer) >= self.batch_size:
			self._train_step()
    
	def _train_step(self):
		states, actions, rewards, next_states, dones, indices, weights = self.replay_buffer.sample(self.batch_size)
		states = torch.FloatTensor(states).to(self.device)
		actions = torch.LongTensor(actions).to(self.device)
		rewards = torch.FloatTensor(rewards).to(self.device)
		next_states = torch.FloatTensor(next_states).to(self.device)
		dones = torch.FloatTensor(dones).to(self.device)
		weights = torch.FloatTensor(weights).to(self.device)
		current_q_values = self.policy_net(states).gather(1, actions.unsqueeze(1)).squeeze(1)
		with torch.no_grad():
			if self.use_double_dqn:
				next_actions = self.policy_net(next_states).argmax(1)
				next_q_values = self.target_net(next_states).gather(1, next_actions.unsqueeze(1)).squeeze(1)
			else:
				next_q_values = self.target_net(next_states).max(1)[0]
			target_q_values = rewards + (1 - dones) * self.discount_factor * next_q_values
		td_errors = torch.abs(current_q_values - target_q_values).detach().cpu().numpy()
		loss = (weights * self.loss_fn(current_q_values, target_q_values)).mean()
		self.optimizer.zero_grad()
		loss.backward()
		torch.nn.utils.clip_grad_norm_(self.policy_net.parameters(), self.gradient_clip)
		self.optimizer.step()
		if self.scheduler is not None:
			self.scheduler.step()
		if indices is not None:
			self.replay_buffer.update_priorities(indices, td_errors + 1e-6)
		self.losses.append(loss.item())
		self.update_count += 1
		self.training_steps += 1
		if self.tau < 1.0:
			for target_param, policy_param in zip(self.target_net.parameters(), self.policy_net.parameters()):
				target_param.data.copy_(self.tau * policy_param.data + (1.0 - self.tau) * target_param.data)
		else:
			if self.update_count % self.target_update_freq == 0:
				self.target_net.load_state_dict(self.policy_net.state_dict())
    
	def decay_epsilon(self):
		# exp: eps = eps * exp(-lambda)
		if self.epsilon_schedule == 'exp':
			self.epsilon = max(self.epsilon_min, self.epsilon * np.exp(-self.epsilon_decay_lambda))
		# linear: eps_t = eps0 - (eps0-eps_min)*t/steps
		elif self.epsilon_schedule == 'linear':
			self._eps_step += 1
			eps0 = 1.0
			progress = min(1.0, self._eps_step / float(self.epsilon_linear_steps))
			self.epsilon = max(self.epsilon_min, eps0 - (eps0 - self.epsilon_min) * progress)
		# cosine: eps oscillates but decays to epsilon_min baseline
		elif self.epsilon_schedule == 'cosine':
			self._eps_step += 1
			T = max(1, self.epsilon_linear_steps)
			cos_term = 0.5 * (1 + np.cos(np.pi * min(1.0, self._eps_step / T)))
			self.epsilon = self.epsilon_min + (1.0 - self.epsilon_min) * cos_term
		else:
			self.epsilon = max(self.epsilon_min, self.epsilon * np.exp(-self.epsilon_decay_lambda))
    
	def get_network_stats(self):
		policy_params = sum(p.numel() for p in self.policy_net.parameters())
		policy_grad_norm = sum(p.grad.norm().item() for p in self.policy_net.parameters() if p.grad is not None)
		return {
			'policy_params': policy_params,
			'policy_grad_norm': policy_grad_norm,
			'buffer_size': len(self.replay_buffer),
			'avg_loss': np.mean(self.losses[-100:]) if len(self.losses) > 0 else 0
		}
    
	def save(self, path: str):
		torch.save({
			'policy_net_state_dict': self.policy_net.state_dict(),
			'target_net_state_dict': self.target_net.state_dict(),
			'optimizer_state_dict': self.optimizer.state_dict(),
			'epsilon': self.epsilon,
			'update_count': self.update_count,
			'training_steps': self.training_steps,
			'episode_rewards': self.episode_rewards,
			'losses': self.losses,
			'use_double_dqn': self.use_double_dqn,
			'use_dueling': self.use_dueling,
			'tau': self.tau
		}, path)
    
	def load(self, path: str):
		checkpoint = torch.load(path, map_location=self.device, weights_only=False)
		self.policy_net.load_state_dict(checkpoint['policy_net_state_dict'])
		self.target_net.load_state_dict(checkpoint['target_net_state_dict'])
		self.optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
		self.epsilon = checkpoint['epsilon']
		self.update_count = checkpoint['update_count']
		self.training_steps = checkpoint.get('training_steps', 0)
		self.episode_rewards = checkpoint['episode_rewards']
		self.losses = checkpoint['losses']
		self.use_double_dqn = checkpoint.get('use_double_dqn', True)
		self.use_dueling = checkpoint.get('use_dueling', True)
		self.tau = checkpoint.get('tau', 0.005)
