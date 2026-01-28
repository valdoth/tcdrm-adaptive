"""
Deep Q-Network (DQN) Agent pour TCDRM v2

Implémentation d'un agent DQN avec:
- Réseau de neurones profond (64-64-32) pour approximation de Q
- Experience Replay Buffer
- Target Network
- Epsilon-greedy exploration décroissante
- Action masking pour contraintes
- Gestion d'états continus multi-cloud
"""

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from collections import deque
import random
from typing import Tuple, List


class DQNNetwork(nn.Module):
    """
    Réseau de neurones profond pour approximation de Q(s,a; θ).
    
    Architecture TCDRM v2:
    - Input: 8 dimensions (état continu)
      [tQ_norm, cQ_norm, pop_norm, bud_norm, net_inter_ratio, 
       net_intercloud_ratio, repl_factor, trend_pop]
    - Hidden Layer 1: 64 neurones + ReLU
    - Hidden Layer 2: 64 neurones + ReLU
    - Hidden Layer 3: 32 neurones + ReLU
    - Output: 3 dimensions (Q-values pour NOOP, REPLICATE, DELETE)
    """
    
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


class ReplayBuffer:
    """
    Experience Replay Buffer pour stocker les transitions.
    """
    
    def __init__(self, capacity: int = 10000):
        self.buffer = deque(maxlen=capacity)
    
    def push(self, state, action, reward, next_state, done):
        """Ajouter une transition au buffer"""
        self.buffer.append((state, action, reward, next_state, done))
    
    def sample(self, batch_size: int) -> Tuple:
        """Échantillonner un batch de transitions"""
        batch = random.sample(self.buffer, batch_size)
        states, actions, rewards, next_states, dones = zip(*batch)
        
        return (
            np.array(states),
            np.array(actions),
            np.array(rewards),
            np.array(next_states),
            np.array(dones)
        )
    
    def __len__(self):
        return len(self.buffer)


class DQNAgent:
    """
    Agent DQN pour TCDRM v2.
    
    Gère:
    - États continus (8 dimensions)
    - Actions discrètes (3: NOOP, REPLICATE, DELETE)
    - Action masking pour contraintes
    - Exploration ε-greedy décroissante
    """
    
    def __init__(
        self,
        state_dim: int = 8,
        action_dim: int = 3,
        hidden_dims: list = None,
        learning_rate: float = 0.001,
        discount_factor: float = 0.95,
        epsilon: float = 1.0,
        epsilon_min: float = 0.01,
        epsilon_decay_lambda: float = 0.001,
        buffer_capacity: int = 10000,
        batch_size: int = 64,
        target_update_freq: int = 10,
        device: str = None
    ):
        """
        Args:
            state_dim: Dimension de l'espace d'état (8 pour TCDRM v2)
            action_dim: Dimension de l'espace d'action (3: NOOP, REPLICATE, DELETE)
            hidden_dims: Liste des dimensions des couches cachées [64, 64, 32]
            learning_rate: Taux d'apprentissage (Adam)
            discount_factor: Facteur de discount γ
            epsilon: Epsilon initial pour exploration
            epsilon_min: Epsilon minimum
            epsilon_decay_lambda: Lambda pour décroissance exponentielle
            buffer_capacity: Capacité du replay buffer
            batch_size: Taille du batch pour l'entraînement
            target_update_freq: Fréquence de mise à jour du target network
            device: Device PyTorch (cpu/cuda)
        """
        if hidden_dims is None:
            hidden_dims = [64, 64, 32]
        
        self.state_dim = state_dim
        self.action_dim = action_dim
        self.discount_factor = discount_factor
        self.epsilon = epsilon
        self.epsilon_min = epsilon_min
        self.epsilon_decay_lambda = epsilon_decay_lambda
        self.batch_size = batch_size
        self.target_update_freq = target_update_freq
        self.training_steps = 0
        
        # Device
        if device is None:
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)
        
        # Networks
        self.policy_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
        self.target_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
        self.target_net.load_state_dict(self.policy_net.state_dict())
        self.target_net.eval()
        
        # Optimizer
        self.optimizer = optim.Adam(self.policy_net.parameters(), lr=learning_rate)
        self.loss_fn = nn.MSELoss()
        
        # Replay buffer
        self.replay_buffer = ReplayBuffer(buffer_capacity)
        
        # Statistics
        self.update_count = 0
        self.episode_rewards = []
        self.losses = []
    
    def select_action(self, state: np.ndarray, training: bool = True, action_mask: np.ndarray = None) -> int:
        """
        Sélectionner une action avec epsilon-greedy et action masking.
        
        Args:
            state: État actuel (vecteur 8D)
            training: Si True, utilise epsilon-greedy; sinon greedy
            action_mask: Masque binaire [1,1,1] où 0 = action interdite
        
        Returns:
            Action sélectionnée (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        if action_mask is None:
            action_mask = np.ones(self.action_dim)
        
        valid_actions = np.where(action_mask > 0)[0]
        
        if len(valid_actions) == 0:
            return 0  # NOOP par défaut
        
        if training and random.random() < self.epsilon:
            # Exploration: action aléatoire parmi les actions valides
            return np.random.choice(valid_actions)
        else:
            # Exploitation: meilleure Q-value parmi les actions valides
            with torch.no_grad():
                state_tensor = torch.FloatTensor(state).unsqueeze(0).to(self.device)
                q_values = self.policy_net(state_tensor).cpu().numpy()[0]
                
                # Masquer les actions invalides
                q_values[action_mask == 0] = -np.inf
                
                return q_values.argmax()
    
    def update(self, state, action, reward, next_state, done):
        """
        Mettre à jour l'agent avec une transition.
        
        Args:
            state: État actuel
            action: Action prise
            reward: Récompense reçue
            next_state: État suivant
            done: Si l'épisode est terminé
        """
        # Ajouter au replay buffer
        self.replay_buffer.push(state, action, reward, next_state, done)
        
        # Entraîner si le buffer est suffisamment rempli
        if len(self.replay_buffer) >= self.batch_size:
            self._train_step()
    
    def _train_step(self):
        """
        Effectuer un pas d'entraînement.
        """
        # Échantillonner un batch
        states, actions, rewards, next_states, dones = self.replay_buffer.sample(self.batch_size)
        
        # Convertir en tensors
        states = torch.FloatTensor(states).to(self.device)
        actions = torch.LongTensor(actions).to(self.device)
        rewards = torch.FloatTensor(rewards).to(self.device)
        next_states = torch.FloatTensor(next_states).to(self.device)
        dones = torch.FloatTensor(dones).to(self.device)
        
        # Calculer Q(s, a) actuel
        current_q_values = self.policy_net(states).gather(1, actions.unsqueeze(1)).squeeze(1)
        
        # Calculer Q(s', a') cible avec target network
        with torch.no_grad():
            next_q_values = self.target_net(next_states).max(1)[0]
            target_q_values = rewards + (1 - dones) * self.discount_factor * next_q_values
        
        # Calculer la perte
        loss = self.loss_fn(current_q_values, target_q_values)
        
        # Backpropagation
        self.optimizer.zero_grad()
        loss.backward()
        self.optimizer.step()
        
        # Statistiques
        self.losses.append(loss.item())
        self.update_count += 1
        self.training_steps += 1
        
        # Mettre à jour le target network
        if self.update_count % self.target_update_freq == 0:
            self.target_net.load_state_dict(self.policy_net.state_dict())
    
    def decay_epsilon(self):
        """
        Décroissance exponentielle d'epsilon:
        ε_t = max(ε_min, ε_0 · exp(-λt))
        """
        self.epsilon = max(
            self.epsilon_min,
            self.epsilon * np.exp(-self.epsilon_decay_lambda * self.training_steps)
        )
    
    def save(self, path: str):
        """Sauvegarder le modèle"""
        torch.save({
            'policy_net_state_dict': self.policy_net.state_dict(),
            'target_net_state_dict': self.target_net.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
            'epsilon': self.epsilon,
            'update_count': self.update_count,
            'training_steps': self.training_steps,
            'episode_rewards': self.episode_rewards,
            'losses': self.losses
        }, path)
    
    def load(self, path: str):
        """Charger le modèle"""
        checkpoint = torch.load(path, map_location=self.device)
        self.policy_net.load_state_dict(checkpoint['policy_net_state_dict'])
        self.target_net.load_state_dict(checkpoint['target_net_state_dict'])
        self.optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        self.epsilon = checkpoint['epsilon']
        self.update_count = checkpoint['update_count']
        self.training_steps = checkpoint.get('training_steps', 0)
        self.episode_rewards = checkpoint['episode_rewards']
        self.losses = checkpoint['losses']
