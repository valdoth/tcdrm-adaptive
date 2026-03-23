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


class DuelingDQNNetwork(nn.Module):
    """
    Réseau Dueling DQN pour approximation de Q(s,a; θ).
    
    Architecture Dueling TCDRM v2:
    - Input: 8 dimensions (état continu)
    - Shared layers: 64 -> 64 neurones
    - Value stream: 32 -> 1 (V(s))
    - Advantage stream: 32 -> action_dim (A(s,a))
    - Output: Q(s,a) = V(s) + (A(s,a) - mean(A(s,:)))
    
    Avantages:
    - Sépare valeur d'état et avantage d'action
    - Meilleure généralisation
    - Convergence plus rapide
    """
    
    def __init__(self, state_dim: int = 8, action_dim: int = 3, hidden_dims: list = [64, 64]):
        super(DuelingDQNNetwork, self).__init__()
        
        # Shared feature layers
        self.feature_layer = nn.Sequential(
            nn.Linear(state_dim, hidden_dims[0]),
            nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]),
            nn.ReLU()
        )
        
        # Value stream V(s)
        self.value_stream = nn.Sequential(
            nn.Linear(hidden_dims[1], 32),
            nn.ReLU(),
            nn.Linear(32, 1)
        )
        
        # Advantage stream A(s,a)
        self.advantage_stream = nn.Sequential(
            nn.Linear(hidden_dims[1], 32),
            nn.ReLU(),
            nn.Linear(32, action_dim)
        )
    
    def forward(self, x):
        features = self.feature_layer(x)
        value = self.value_stream(features)
        advantage = self.advantage_stream(features)
        
        # Q(s,a) = V(s) + (A(s,a) - mean(A(s,:)))
        # Soustraction de la moyenne pour identifiabilité
        q_values = value + (advantage - advantage.mean(dim=1, keepdim=True))
        return q_values


class DQNNetwork(nn.Module):
    """
    Réseau DQN standard (pour compatibilité)
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


class PrioritizedReplayBuffer:
    """
    Prioritized Experience Replay Buffer.
    
    Les transitions avec TD-error élevé sont échantillonnées plus fréquemment.
    Basé sur: Schaul et al. (2015) "Prioritized Experience Replay"
    """
    
    def __init__(self, capacity: int = 10000, alpha: float = 0.6, beta_start: float = 0.4, beta_frames: int = 100000):
        """
        Args:
            capacity: Taille maximale du buffer
            alpha: Degré de priorisation (0 = uniforme, 1 = full prioritization)
            beta_start: Importance sampling initial
            beta_frames: Nombre de frames pour atteindre beta=1
        """
        self.capacity = capacity
        self.alpha = alpha
        self.beta_start = beta_start
        self.beta_frames = beta_frames
        self.frame = 1
        
        self.buffer = []
        self.priorities = np.zeros(capacity, dtype=np.float32)
        self.position = 0
    
    def push(self, state, action, reward, next_state, done):
        """Ajouter une transition avec priorité maximale"""
        max_priority = self.priorities.max() if self.buffer else 1.0
        
        if len(self.buffer) < self.capacity:
            self.buffer.append((state, action, reward, next_state, done))
        else:
            self.buffer[self.position] = (state, action, reward, next_state, done)
        
        self.priorities[self.position] = max_priority
        self.position = (self.position + 1) % self.capacity
    
    def sample(self, batch_size: int) -> Tuple:
        """Échantillonner un batch avec priorités"""
        if len(self.buffer) == self.capacity:
            priorities = self.priorities
        else:
            priorities = self.priorities[:len(self.buffer)]
        
        # Probabilités d'échantillonnage
        probs = priorities ** self.alpha
        probs /= probs.sum()
        
        # Échantillonner indices
        indices = np.random.choice(len(self.buffer), batch_size, p=probs, replace=False)
        
        # Importance sampling weights
        beta = min(1.0, self.beta_start + self.frame * (1.0 - self.beta_start) / self.beta_frames)
        weights = (len(self.buffer) * probs[indices]) ** (-beta)
        weights /= weights.max()
        
        self.frame += 1
        
        # Extraire transitions
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
        """Mettre à jour les priorités"""
        for idx, priority in zip(indices, priorities):
            self.priorities[idx] = priority
    
    def __len__(self):
        return len(self.buffer)


class ReplayBuffer:
    """
    Experience Replay Buffer standard (pour compatibilité).
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
            np.array(dones),
            None,  # indices (pour compatibilité avec PER)
            np.ones(batch_size)  # weights uniformes
        )
    
    def update_priorities(self, indices, priorities):
        """No-op pour compatibilité"""
        pass
    
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
        learning_rate: float = 0.0005,
        discount_factor: float = 0.99,
        epsilon: float = 1.0,
        epsilon_min: float = 0.01,
        epsilon_decay_lambda: float = 0.0005,
        buffer_capacity: int = 50000,
        batch_size: int = 64,
        target_update_freq: int = 1000,
        use_double_dqn: bool = True,
        use_dueling: bool = True,
        use_prioritized_replay: bool = True,
        tau: float = 0.005,
        gradient_clip: float = 1.0,
        device: str = None
    ):
        """
        Args:
            state_dim: Dimension de l'espace d'état (8 pour TCDRM v2)
            action_dim: Dimension de l'espace d'action (3: NOOP, REPLICATE, DELETE)
            hidden_dims: Liste des dimensions des couches cachées [64, 64]
            learning_rate: Taux d'apprentissage (Adam) - réduit pour stabilité
            discount_factor: Facteur de discount γ
            epsilon: Epsilon initial pour exploration
            epsilon_min: Epsilon minimum
            epsilon_decay_lambda: Lambda pour décroissance exponentielle
            buffer_capacity: Capacité du replay buffer (augmenté à 50k)
            batch_size: Taille du batch pour l'entraînement
            target_update_freq: Fréquence de mise à jour du target network
            use_double_dqn: Utiliser Double DQN
            use_dueling: Utiliser architecture Dueling
            use_prioritized_replay: Utiliser Prioritized Experience Replay
            tau: Paramètre pour soft update du target network
            gradient_clip: Valeur max pour gradient clipping
            device: Device PyTorch (cpu/cuda)
        """
        if hidden_dims is None:
            hidden_dims = [64, 64]
        
        self.state_dim = state_dim
        self.action_dim = action_dim
        self.discount_factor = discount_factor
        self.epsilon = epsilon
        self.epsilon_min = epsilon_min
        self.epsilon_decay_lambda = epsilon_decay_lambda
        self.batch_size = batch_size
        self.target_update_freq = target_update_freq
        self.use_double_dqn = use_double_dqn
        self.use_dueling = use_dueling
        self.tau = tau
        self.gradient_clip = gradient_clip
        self.training_steps = 0
        
        # Device
        if device is None:
            self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        else:
            self.device = torch.device(device)
        
        # Networks (Dueling ou standard)
        if use_dueling:
            self.policy_net = DuelingDQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
            self.target_net = DuelingDQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
        else:
            self.policy_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
            self.target_net = DQNNetwork(state_dim, action_dim, hidden_dims).to(self.device)
        
        self.target_net.load_state_dict(self.policy_net.state_dict())
        self.target_net.eval()
        
        # Optimizer avec weight decay pour régularisation
        self.optimizer = optim.Adam(self.policy_net.parameters(), lr=learning_rate, weight_decay=1e-5)
        self.loss_fn = nn.SmoothL1Loss()  # Huber loss pour robustesse
        
        # Replay buffer (Prioritized ou standard)
        if use_prioritized_replay:
            self.replay_buffer = PrioritizedReplayBuffer(buffer_capacity)
        else:
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
        Effectuer un pas d'entraînement avec Double DQN et PER.
        """
        # Échantillonner un batch (avec priorités si PER)
        states, actions, rewards, next_states, dones, indices, weights = self.replay_buffer.sample(self.batch_size)
        
        # Convertir en tensors
        states = torch.FloatTensor(states).to(self.device)
        actions = torch.LongTensor(actions).to(self.device)
        rewards = torch.FloatTensor(rewards).to(self.device)
        next_states = torch.FloatTensor(next_states).to(self.device)
        dones = torch.FloatTensor(dones).to(self.device)
        weights = torch.FloatTensor(weights).to(self.device)
        
        # Calculer Q(s, a) actuel
        current_q_values = self.policy_net(states).gather(1, actions.unsqueeze(1)).squeeze(1)
        
        # Calculer Q(s', a') cible
        with torch.no_grad():
            if self.use_double_dqn:
                # Double DQN: utiliser policy_net pour sélectionner, target_net pour évaluer
                next_actions = self.policy_net(next_states).argmax(1)
                next_q_values = self.target_net(next_states).gather(1, next_actions.unsqueeze(1)).squeeze(1)
            else:
                # DQN standard
                next_q_values = self.target_net(next_states).max(1)[0]
            
            target_q_values = rewards + (1 - dones) * self.discount_factor * next_q_values
        
        # Calculer TD-errors pour PER
        td_errors = torch.abs(current_q_values - target_q_values).detach().cpu().numpy()
        
        # Calculer la perte pondérée (importance sampling)
        loss = (weights * self.loss_fn(current_q_values, target_q_values)).mean()
        
        # Backpropagation avec gradient clipping
        self.optimizer.zero_grad()
        loss.backward()
        torch.nn.utils.clip_grad_norm_(self.policy_net.parameters(), self.gradient_clip)
        self.optimizer.step()
        
        # Mettre à jour les priorités dans le buffer
        if indices is not None:
            self.replay_buffer.update_priorities(indices, td_errors + 1e-6)
        
        # Statistiques
        self.losses.append(loss.item())
        self.update_count += 1
        self.training_steps += 1
        
        # Soft update du target network (plus stable que hard update)
        if self.tau < 1.0:
            # Soft update: θ_target = τ*θ_policy + (1-τ)*θ_target
            for target_param, policy_param in zip(self.target_net.parameters(), self.policy_net.parameters()):
                target_param.data.copy_(self.tau * policy_param.data + (1.0 - self.tau) * target_param.data)
        else:
            # Hard update périodique
            if self.update_count % self.target_update_freq == 0:
                self.target_net.load_state_dict(self.policy_net.state_dict())
    
    def decay_epsilon(self):
        """
        Décroissance exponentielle d'epsilon améliorée:
        ε_t = max(ε_min, ε_0 · exp(-λt))
        """
        self.epsilon = max(
            self.epsilon_min,
            self.epsilon * np.exp(-self.epsilon_decay_lambda)
        )
    
    def get_network_stats(self):
        """Retourne des statistiques sur les réseaux"""
        policy_params = sum(p.numel() for p in self.policy_net.parameters())
        policy_grad_norm = sum(p.grad.norm().item() for p in self.policy_net.parameters() if p.grad is not None)
        
        return {
            'policy_params': policy_params,
            'policy_grad_norm': policy_grad_norm,
            'buffer_size': len(self.replay_buffer),
            'avg_loss': np.mean(self.losses[-100:]) if len(self.losses) > 0 else 0
        }
    
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
            'losses': self.losses,
            'use_double_dqn': self.use_double_dqn,
            'use_dueling': self.use_dueling,
            'tau': self.tau
        }, path)
    
    def load(self, path: str):
        """Charger le modèle"""
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
