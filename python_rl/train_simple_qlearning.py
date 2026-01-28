"""
Entraînement Q-Learning Simple pour TCDRM
Implémentation propre inspirée des meilleures pratiques GitHub
"""

import sys
import os
import numpy as np
import argparse
from tqdm import tqdm

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_qlearning_env import TcdrmQLearningEnv
from agents.simple_qlearning_agent import SimpleQLearningAgent


def train_qlearning(
    env: TcdrmQLearningEnv,
    agent: SimpleQLearningAgent,
    n_episodes: int = 2000,
    verbose: bool = True
):
    """
    Entraîne l'agent Q-Learning.
    
    Args:
        env: Environnement TCDRM
        agent: Agent Q-Learning
        n_episodes: Nombre d'épisodes
        verbose: Afficher progression
    """
    episode_rewards = []
    episode_lengths = []
    sla_rates = []
    
    iterator = tqdm(range(n_episodes), desc="Training") if verbose else range(n_episodes)
    
    for episode in iterator:
        state, _ = env.reset(seed=42 + episode)
        state_idx = env.state_to_index(state)
        
        episode_reward = 0
        episode_length = 0
        done = False
        
        while not done:
            # Obtenir actions valides
            valid_actions = []
            for action in range(env.action_space.n):
                if env._is_action_valid(action):
                    valid_actions.append(action)
            
            # Sélectionner action
            action = agent.select_action(state_idx, valid_actions, training=True)
            
            # Exécuter action
            next_state, reward, terminated, truncated, info = env.step(action)
            next_state_idx = env.state_to_index(next_state)
            done = terminated or truncated
            
            # Mettre à jour Q-table
            agent.update(state_idx, action, reward, next_state_idx, done)
            
            # Accumuler statistiques
            episode_reward += reward
            episode_length += 1
            
            # Transition
            state = next_state
            state_idx = next_state_idx
        
        # Décroissance epsilon
        agent.decay_epsilon()
        
        # Sauvegarder statistiques
        episode_rewards.append(episode_reward)
        episode_lengths.append(episode_length)
        sla_rates.append(info.get('sla_compliance_rate', 0))
        
        # Affichage périodique
        if verbose and (episode + 1) % 100 == 0:
            avg_reward = np.mean(episode_rewards[-100:])
            avg_sla = np.mean(sla_rates[-100:])
            stats = agent.get_stats()
            
            iterator.set_postfix({
                'reward': f'{avg_reward:.1f}',
                'sla': f'{avg_sla*100:.1f}%',
                'eps': f'{agent.epsilon:.3f}',
                'explored': f'{stats["exploration_rate"]:.1f}%'
            })
    
    return {
        'episode_rewards': episode_rewards,
        'episode_lengths': episode_lengths,
        'sla_rates': sla_rates
    }


def evaluate_qlearning(
    env: TcdrmQLearningEnv,
    agent: SimpleQLearningAgent,
    n_episodes: int = 10,
    seed: int = 42
):
    """Évalue l'agent Q-Learning."""
    episode_rewards = []
    sla_rates = []
    
    for episode in range(n_episodes):
        state, _ = env.reset(seed=seed + episode)
        state_idx = env.state_to_index(state)
        
        episode_reward = 0
        done = False
        
        while not done:
            # Actions valides
            valid_actions = []
            for action in range(env.action_space.n):
                if env._is_action_valid(action):
                    valid_actions.append(action)
            
            # Sélection greedy (pas d'exploration)
            action = agent.select_action(state_idx, valid_actions, training=False)
            
            # Exécution
            next_state, reward, terminated, truncated, info = env.step(action)
            next_state_idx = env.state_to_index(next_state)
            done = terminated or truncated
            
            episode_reward += reward
            state = next_state
            state_idx = next_state_idx
        
        episode_rewards.append(episode_reward)
        sla_rates.append(info.get('sla_compliance_rate', 0))
    
    return {
        'mean_reward': np.mean(episode_rewards),
        'std_reward': np.std(episode_rewards),
        'mean_sla': np.mean(sla_rates),
        'std_sla': np.std(sla_rates)
    }


def main():
    parser = argparse.ArgumentParser(description='Train Simple Q-Learning for TCDRM')
    
    # Environnement
    parser.add_argument('--data-gb', type=float, default=5.3, help='Data size in GB')
    parser.add_argument('--seed', type=int, default=42, help='Random seed')
    
    # Agent
    parser.add_argument('--episodes', type=int, default=2000, help='Number of training episodes')
    parser.add_argument('--lr', type=float, default=0.1, help='Learning rate (alpha)')
    parser.add_argument('--gamma', type=float, default=0.99, help='Discount factor')
    parser.add_argument('--epsilon-start', type=float, default=1.0, help='Initial epsilon')
    parser.add_argument('--epsilon-min', type=float, default=0.01, help='Minimum epsilon')
    parser.add_argument('--epsilon-decay', type=float, default=0.995, help='Epsilon decay rate')
    
    # Sauvegarde
    parser.add_argument('--output', type=str, default='models/simple_qlearning.pkl',
                       help='Output model path')
    parser.add_argument('--eval-episodes', type=int, default=10, help='Evaluation episodes')
    
    args = parser.parse_args()
    
    # Créer environnement
    print("="*80)
    print("ENTRAÎNEMENT Q-LEARNING SIMPLE - TCDRM")
    print("="*80)
    print(f"Data size: {args.data_gb} GB")
    print(f"Episodes: {args.episodes}")
    print(f"Learning rate: {args.lr}")
    print(f"Gamma: {args.gamma}")
    print(f"Epsilon: {args.epsilon_start} → {args.epsilon_min} (decay={args.epsilon_decay})")
    print("="*80)
    print()
    
    env = TcdrmQLearningEnv(data_gb=args.data_gb)
    
    # Créer agent
    agent = SimpleQLearningAgent(
        n_states=env.get_state_space_size(),
        n_actions=env.get_action_space_size(),
        learning_rate=args.lr,
        discount_factor=args.gamma,
        epsilon_start=args.epsilon_start,
        epsilon_min=args.epsilon_min,
        epsilon_decay=args.epsilon_decay
    )
    
    # Entraîner
    print("Entraînement en cours...")
    train_stats = train_qlearning(env, agent, args.episodes, verbose=True)
    
    # Statistiques finales
    print("\n" + "="*80)
    print("STATISTIQUES D'ENTRAÎNEMENT")
    print("="*80)
    stats = agent.get_stats()
    print(f"Episodes: {agent.episodes_completed}")
    print(f"Steps: {agent.training_steps}")
    print(f"Epsilon final: {agent.epsilon:.4f}")
    print(f"États explorés: {stats['states_explored']}/{agent.n_states} ({stats['exploration_rate']:.1f}%)")
    print(f"Q-values: mean={stats['q_mean']:.2f}, std={stats['q_std']:.2f}")
    print(f"Reward moyen (100 derniers): {np.mean(train_stats['episode_rewards'][-100:]):.2f}")
    print(f"SLA moyen (100 derniers): {np.mean(train_stats['sla_rates'][-100:])*100:.1f}%")
    print("="*80)
    print()
    
    # Évaluation
    print("Évaluation...")
    eval_stats = evaluate_qlearning(env, agent, args.eval_episodes, args.seed)
    
    print("\n" + "="*80)
    print("RÉSULTATS D'ÉVALUATION")
    print("="*80)
    print(f"Reward: {eval_stats['mean_reward']:.2f} ± {eval_stats['std_reward']:.2f}")
    print(f"SLA compliance: {eval_stats['mean_sla']*100:.1f}% ± {eval_stats['std_sla']*100:.1f}%")
    print("="*80)
    print()
    
    # Sauvegarder
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    agent.save(args.output)
    print(f"✅ Modèle sauvegardé: {args.output}")
    print()


if __name__ == '__main__':
    main()
