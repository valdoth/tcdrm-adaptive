"""
Tabular Q-Learning Agent for TCDRM-ADAPTIVE
Compatible with Gymnasium environments

This is a direct Python port of the Java Q-Learning implementation
for adaptive replication decision-making in multi-cloud environments.
"""

import numpy as np
from typing import Optional, Dict, Any, Tuple
import json
import os


class TabularQLearningAgent:
    """
    Classic Tabular Q-Learning Agent for TCDRM-ADAPTIVE
    
    This implementation mirrors the Java Q-Learning agent and works
    with Gymnasium environments for standardized RL experiments.
    
    Key features:
    - Discrete state space (same as Java: 108 states)
    - Epsilon-greedy exploration
    - Q-table updates with Bellman equation
    - Compatible with Gymnasium API
    """
    
    def __init__(
        self,
        n_states: int,
        n_actions: int,
        learning_rate: float = 0.1,
        discount_factor: float = 0.95,
        epsilon: float = 1.0,
        epsilon_decay: float = 0.995,
        epsilon_min: float = 0.01,
        seed: Optional[int] = None
    ):
        """
        Initialize Q-Learning agent
        
        Args:
            n_states: Number of discrete states
            n_actions: Number of actions
            learning_rate: Learning rate (alpha)
            discount_factor: Discount factor (gamma)
            epsilon: Initial exploration rate
            epsilon_decay: Epsilon decay rate per episode
            epsilon_min: Minimum epsilon value
            seed: Random seed for reproducibility
        """
        self.n_states = n_states
        self.n_actions = n_actions
        self.learning_rate = learning_rate
        self.discount_factor = discount_factor
        self.epsilon = epsilon
        self.epsilon_decay = epsilon_decay
        self.epsilon_min = epsilon_min
        
        # Initialize Q-table with zeros
        self.q_table = np.zeros((n_states, n_actions), dtype=np.float64)
        
        # Statistics
        self.episode_rewards = []
        self.episode_lengths = []
        self.state_visit_counts = np.zeros(n_states, dtype=np.int32)
        
        # Random number generator
        self.rng = np.random.default_rng(seed)
        
    def discretize_state(self, observation: np.ndarray) -> int:
        """
        Convert continuous observation to discrete state index
        
        Observation format: [budget_ratio, latency, access_count_norm, replica_count, ...]
        
        Discretization (same as Java):
        - Budget: LOW (0-0.33), MEDIUM (0.33-0.66), HIGH (0.66-1.0)
        - Latency: LOW (<100ms), MEDIUM (100-200ms), HIGH (>200ms)
        - Popularity: LOW (<150), MEDIUM (150-250), HIGH (>250)
        - Replicas: 0, 1, 2, 3
        
        State index = budget * 36 + latency * 12 + popularity * 4 + replicas
        Total: 3 * 3 * 3 * 4 = 108 states
        """
        budget_ratio = observation[0]
        latency = observation[1]
        access_count_norm = observation[2]
        replica_count = int(observation[3])
        
        # Discretize budget (3 levels)
        if budget_ratio < 0.33:
            budget_level = 0  # LOW
        elif budget_ratio < 0.66:
            budget_level = 1  # MEDIUM
        else:
            budget_level = 2  # HIGH
        
        # Discretize latency (3 levels)
        if latency < 100:
            latency_level = 0  # LOW
        elif latency < 200:
            latency_level = 1  # MEDIUM
        else:
            latency_level = 2  # HIGH
        
        # Discretize popularity (3 levels)
        # access_count_norm is in [0, 1], denormalize to [0, 1000]
        access_count = access_count_norm * 1000
        if access_count < 150:
            popularity_level = 0  # LOW
        elif access_count < 250:
            popularity_level = 1  # MEDIUM
        else:
            popularity_level = 2  # HIGH
        
        # Clip replica count to [0, 3]
        replica_count = np.clip(replica_count, 0, 3)
        
        # Calculate state index
        state_index = (budget_level * 36 + 
                      latency_level * 12 + 
                      popularity_level * 4 + 
                      replica_count)
        
        return state_index
    
    def select_action(self, state_index: int, training: bool = True) -> int:
        """
        Select action using epsilon-greedy policy
        
        Args:
            state_index: Current state index
            training: If True, use epsilon-greedy; if False, use greedy
        
        Returns:
            Selected action index
        """
        if training and self.rng.random() < self.epsilon:
            # Explore: random action
            return self.rng.integers(0, self.n_actions)
        else:
            # Exploit: best action from Q-table
            return int(np.argmax(self.q_table[state_index]))
    
    def update(
        self,
        state_index: int,
        action: int,
        reward: float,
        next_state_index: int,
        done: bool
    ):
        """
        Update Q-table using Q-learning update rule
        
        Q(s,a) ← Q(s,a) + α[r + γ max_a' Q(s',a') - Q(s,a)]
        
        Args:
            state_index: Current state index
            action: Action taken
            reward: Reward received
            next_state_index: Next state index
            done: Whether episode is done
        """
        current_q = self.q_table[state_index, action]
        
        if done:
            # Terminal state: no future rewards
            target_q = reward
        else:
            # Non-terminal: include discounted future reward
            max_next_q = np.max(self.q_table[next_state_index])
            target_q = reward + self.discount_factor * max_next_q
        
        # Q-learning update
        self.q_table[state_index, action] += self.learning_rate * (target_q - current_q)
        
        # Track state visits
        self.state_visit_counts[state_index] += 1
    
    def train(
        self,
        env,
        n_episodes: int,
        max_steps_per_episode: int = 1000,
        eval_freq: int = 10,
        verbose: bool = True
    ) -> Dict[str, Any]:
        """
        Train the Q-Learning agent
        
        Args:
            env: Gymnasium environment
            n_episodes: Number of training episodes
            max_steps_per_episode: Maximum steps per episode
            eval_freq: Frequency of evaluation/logging
            verbose: Whether to print progress
        
        Returns:
            Training statistics
        """
        if verbose:
            print("="*60)
            print("Tabular Q-Learning Training")
            print("="*60)
            print(f"Episodes: {n_episodes}")
            print(f"States: {self.n_states}")
            print(f"Actions: {self.n_actions}")
            print(f"Learning rate: {self.learning_rate}")
            print(f"Discount factor: {self.discount_factor}")
            print(f"Initial epsilon: {self.epsilon}")
            print("="*60)
            print()
        
        for episode in range(n_episodes):
            obs, info = env.reset()
            state_index = self.discretize_state(obs)
            
            episode_reward = 0
            episode_length = 0
            
            for step in range(max_steps_per_episode):
                # Select action
                action = self.select_action(state_index, training=True)
                
                # Take action
                next_obs, reward, terminated, truncated, info = env.step(action)
                next_state_index = self.discretize_state(next_obs)
                done = terminated or truncated
                
                # Update Q-table
                self.update(state_index, action, reward, next_state_index, done)
                
                # Update statistics
                episode_reward += reward
                episode_length += 1
                
                # Move to next state
                state_index = next_state_index
                
                if done:
                    break
            
            # Store episode statistics
            self.episode_rewards.append(episode_reward)
            self.episode_lengths.append(episode_length)
            
            # Decay epsilon
            self.epsilon = max(self.epsilon_min, self.epsilon * self.epsilon_decay)
            
            # Log progress
            if verbose and (episode + 1) % eval_freq == 0:
                avg_reward = np.mean(self.episode_rewards[-eval_freq:])
                avg_length = np.mean(self.episode_lengths[-eval_freq:])
                print(f"Episode {episode + 1}/{n_episodes} | "
                      f"Avg Reward: {avg_reward:.2f} | "
                      f"Avg Length: {avg_length:.1f} | "
                      f"Epsilon: {self.epsilon:.4f}")
        
        if verbose:
            print()
            print("="*60)
            print("Training completed!")
            self.print_statistics()
        
        return {
            'episode_rewards': self.episode_rewards,
            'episode_lengths': self.episode_lengths,
            'final_epsilon': self.epsilon,
            'q_table': self.q_table.copy()
        }
    
    def evaluate(
        self,
        env,
        n_episodes: int = 10,
        render: bool = False
    ) -> Dict[str, Any]:
        """
        Evaluate the trained agent
        
        Args:
            env: Gymnasium environment
            n_episodes: Number of evaluation episodes
            render: Whether to render environment
        
        Returns:
            Evaluation metrics
        """
        eval_rewards = []
        eval_lengths = []
        
        for episode in range(n_episodes):
            obs, info = env.reset()
            state_index = self.discretize_state(obs)
            
            episode_reward = 0
            episode_length = 0
            
            done = False
            while not done:
                # Greedy action selection (no exploration)
                action = self.select_action(state_index, training=False)
                
                obs, reward, terminated, truncated, info = env.step(action)
                state_index = self.discretize_state(obs)
                done = terminated or truncated
                
                episode_reward += reward
                episode_length += 1
                
                if render:
                    env.render()
            
            eval_rewards.append(episode_reward)
            eval_lengths.append(episode_length)
        
        return {
            'mean_reward': np.mean(eval_rewards),
            'std_reward': np.std(eval_rewards),
            'min_reward': np.min(eval_rewards),
            'max_reward': np.max(eval_rewards),
            'mean_length': np.mean(eval_lengths)
        }
    
    def print_statistics(self):
        """Print Q-table statistics"""
        print("Q-Table Statistics:")
        print(f"  Non-zero entries: {np.count_nonzero(self.q_table)}/{self.q_table.size}")
        print(f"  States visited: {np.count_nonzero(self.state_visit_counts)}/{self.n_states}")
        print(f"  Q-value range: [{np.min(self.q_table):.2f}, {np.max(self.q_table):.2f}]")
        print(f"  Mean Q-value: {np.mean(self.q_table):.2f}")
        print("="*60)
    
    def save(self, filepath: str):
        """Save Q-table and parameters"""
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        
        save_dict = {
            'q_table': self.q_table.tolist(),
            'n_states': self.n_states,
            'n_actions': self.n_actions,
            'learning_rate': self.learning_rate,
            'discount_factor': self.discount_factor,
            'epsilon': self.epsilon,
            'episode_rewards': self.episode_rewards,
            'state_visit_counts': self.state_visit_counts.tolist()
        }
        
        with open(filepath, 'w') as f:
            json.dump(save_dict, f, indent=2)
        
        print(f"Q-Learning agent saved to {filepath}")
    
    def load(self, filepath: str):
        """Load Q-table and parameters"""
        with open(filepath, 'r') as f:
            save_dict = json.load(f)
        
        self.q_table = np.array(save_dict['q_table'])
        self.n_states = save_dict['n_states']
        self.n_actions = save_dict['n_actions']
        self.learning_rate = save_dict['learning_rate']
        self.discount_factor = save_dict['discount_factor']
        self.epsilon = save_dict['epsilon']
        self.episode_rewards = save_dict['episode_rewards']
        self.state_visit_counts = np.array(save_dict['state_visit_counts'])
        
        print(f"Q-Learning agent loaded from {filepath}")
    
    def get_best_actions(self, top_k: int = 10) -> list:
        """Get states with highest Q-values and their best actions"""
        best_q_values = []
        
        for state in range(self.n_states):
            if self.state_visit_counts[state] > 0:
                best_action = np.argmax(self.q_table[state])
                best_q = self.q_table[state, best_action]
                best_q_values.append((state, best_action, best_q, self.state_visit_counts[state]))
        
        # Sort by Q-value
        best_q_values.sort(key=lambda x: x[2], reverse=True)
        
        return best_q_values[:top_k]
