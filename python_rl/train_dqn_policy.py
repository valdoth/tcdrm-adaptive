"""
Script d'entraînement pour DQN (Deep Q-Network) - TCDRM v2

Entraîne un agent DQN pour TCDRM v2 avec:
- État continu à 8 dimensions
- Architecture 64-64-32
- Action masking
- Fonction de récompense multi-objectifs
"""

import os
import sys
import numpy as np
import torch
from datetime import datetime
from typing import Dict
import matplotlib.pyplot as plt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env_v2 import TcdrmV2Env
from agents.dqn_agent import DQNAgent


def generate_varied_queries(n_queries: int, seed: int = 42):
    """Génère des requêtes avec tailles variées"""
    np.random.seed(seed)
    query_sizes = []
    for _ in range(n_queries):
        rand = np.random.random()
        if rand < 0.4:
            size = np.random.uniform(1.0, 5.0)
        elif rand < 0.8:
            size = np.random.uniform(5.0, 10.0)
        else:
            size = np.random.uniform(10.0, 20.0)
        query_sizes.append(size)
    return query_sizes


def train_dqn_policy(
    n_episodes: int = 200,
    n_queries_per_episode: int = 1000,
    learning_rate: float = 0.001,
    discount_factor: float = 0.95,
    epsilon_start: float = 1.0,
    epsilon_end: float = 0.01,
    epsilon_decay_lambda: float = 0.001,
    batch_size: int = 64,
    buffer_capacity: int = 10000,
    target_update_freq: int = 10,
    seed: int = 42
):
    """Entraîne un agent DQN pour TCDRM v2"""
    
    print("="*80)
    print("ENTRAÎNEMENT DQN - TCDRM v2")
    print("="*80)
    print()
    print(f"Configuration:")
    print(f"  Episodes: {n_episodes}")
    print(f"  Requêtes par épisode: {n_queries_per_episode}")
    print(f"  État: 8 dimensions continues")
    print(f"  Actions: 3 (NOOP, REPLICATE, DELETE)")
    print(f"  Architecture: 64-64-32")
    print(f"  Learning rate: {learning_rate}")
    print(f"  Discount factor: {discount_factor}")
    print(f"  Epsilon: {epsilon_start} → {epsilon_end} (λ={epsilon_decay_lambda})")
    print(f"  Batch size: {batch_size}")
    print(f"  Buffer capacity: {buffer_capacity}")
    print(f"  Target update freq: {target_update_freq}")
    print(f"  Device: {'cuda' if torch.cuda.is_available() else 'cpu'}")
    print()
    
    # Générer requêtes
    query_sizes = generate_varied_queries(n_queries_per_episode * n_episodes, seed)
    
    # Créer environnement TCDRM v2
    avg_query_size = np.mean(query_sizes[:n_queries_per_episode])
    env = TcdrmV2Env(data_gb=avg_query_size)
    
    # Créer agent DQN
    agent = DQNAgent(
        state_dim=8,
        action_dim=3,
        hidden_dims=[64, 64, 32],
        learning_rate=learning_rate,
        discount_factor=discount_factor,
        epsilon=epsilon_start,
        epsilon_min=epsilon_end,
        epsilon_decay_lambda=epsilon_decay_lambda,
        buffer_capacity=buffer_capacity,
        batch_size=batch_size,
        target_update_freq=target_update_freq
    )
    
    # Métriques
    episode_rewards = []
    episode_costs = []
    episode_sla_violations = []
    episode_replica_changes = []
    best_reward = -float('inf')
    best_episode = 0
    
    print("Début de l'entraînement...")
    print()
    
    for episode in range(n_episodes):
        # Choisir taille de requête
        episode_query_size = query_sizes[episode * n_queries_per_episode]
        env.data_gb = episode_query_size
        
        # Reset
        state, info = env.reset(seed=seed + episode)
        episode_reward = 0.0
        replica_changes = 0
        last_replica_count = 0
        
        for query_idx in range(n_queries_per_episode):
            # Obtenir le masque d'actions valides
            action_mask = env.get_action_mask()
            
            # Sélectionner action avec masking
            action = agent.select_action(state, training=True, action_mask=action_mask)
            
            # Exécuter
            next_state, reward, terminated, truncated, info = env.step(action)
            
            # Mettre à jour agent
            agent.update(state, action, reward, next_state, terminated or truncated)
            
            # Tracking
            episode_reward += reward
            if info['replicas'] != last_replica_count:
                replica_changes += 1
                last_replica_count = info['replicas']
            
            state = next_state
            
            if terminated or truncated:
                break
        
        # Décroissance epsilon
        agent.decay_epsilon()
        
        # Sauvegarder métriques
        episode_rewards.append(episode_reward)
        episode_costs.append(info['total_cost'])
        episode_sla_violations.append(info['sla_violations'])
        episode_replica_changes.append(replica_changes)
        
        # Meilleur modèle
        if episode_reward > best_reward:
            best_reward = episode_reward
            best_episode = episode
        
        # Afficher progression
        if (episode + 1) % 10 == 0:
            avg_reward = np.mean(episode_rewards[-10:])
            avg_cost = np.mean(episode_costs[-10:])
            avg_violations = np.mean(episode_sla_violations[-10:])
            avg_changes = np.mean(episode_replica_changes[-10:])
            avg_loss = np.mean(agent.losses[-100:]) if len(agent.losses) > 0 else 0
            
            print(f"Episode {episode + 1}/{n_episodes}")
            print(f"  Reward moyen (10 derniers): {avg_reward:.2f}")
            print(f"  Coût moyen: {avg_cost:.2f}")
            print(f"  Violations SLA: {avg_violations:.1f}")
            print(f"  Changements réplicas: {avg_changes:.1f}")
            print(f"  Loss moyenne: {avg_loss:.4f}")
            print(f"  Epsilon: {agent.epsilon:.3f}")
            print()
    
    print("="*80)
    print("ENTRAÎNEMENT TERMINÉ")
    print("="*80)
    print()
    print(f"Meilleur épisode: {best_episode + 1} (reward={best_reward:.2f})")
    print()
    
    # Métriques
    metrics = {
        'episode_rewards': episode_rewards,
        'episode_costs': episode_costs,
        'episode_sla_violations': episode_sla_violations,
        'episode_replica_changes': episode_replica_changes,
        'losses': agent.losses,
        'best_episode': best_episode,
        'best_reward': best_reward,
        'final_epsilon': agent.epsilon
    }
    
    return agent, metrics


def plot_training_metrics(metrics: Dict, output_dir: str):
    """Génère graphiques des métriques"""
    os.makedirs(output_dir, exist_ok=True)
    
    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    
    # Reward
    axes[0, 0].plot(metrics['episode_rewards'])
    axes[0, 0].set_title('Reward par épisode')
    axes[0, 0].set_xlabel('Épisode')
    axes[0, 0].set_ylabel('Reward total')
    axes[0, 0].grid(True)
    
    # Coût
    axes[0, 1].plot(metrics['episode_costs'])
    axes[0, 1].set_title('Coût par épisode')
    axes[0, 1].set_xlabel('Épisode')
    axes[0, 1].set_ylabel('Coût total')
    axes[0, 1].grid(True)
    
    # Violations SLA
    axes[0, 2].plot(metrics['episode_sla_violations'])
    axes[0, 2].set_title('Violations SLA par épisode')
    axes[0, 2].set_xlabel('Épisode')
    axes[0, 2].set_ylabel('Nombre de violations')
    axes[0, 2].grid(True)
    
    # Changements réplicas
    axes[1, 0].plot(metrics['episode_replica_changes'])
    axes[1, 0].set_title('Changements de réplicas par épisode')
    axes[1, 0].set_xlabel('Épisode')
    axes[1, 0].set_ylabel('Nombre de changements')
    axes[1, 0].grid(True)
    
    # Loss
    if len(metrics['losses']) > 0:
        axes[1, 1].plot(metrics['losses'])
        axes[1, 1].set_title('Loss d\'entraînement')
        axes[1, 1].set_xlabel('Update step')
        axes[1, 1].set_ylabel('Loss')
        axes[1, 1].grid(True)
    
    # Reward lissé
    window = 10
    if len(metrics['episode_rewards']) >= window:
        smoothed = np.convolve(metrics['episode_rewards'], 
                              np.ones(window)/window, mode='valid')
        axes[1, 2].plot(smoothed)
        axes[1, 2].set_title(f'Reward lissé (fenêtre={window})')
        axes[1, 2].set_xlabel('Épisode')
        axes[1, 2].set_ylabel('Reward moyen')
        axes[1, 2].grid(True)
    
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'dqn_training_metrics.png'), dpi=150)
    print(f"✓ Graphiques sauvegardés: {output_dir}/dqn_training_metrics.png")


def save_model(agent: DQNAgent, metrics: Dict, output_dir: str):
    """Sauvegarde le modèle"""
    os.makedirs(output_dir, exist_ok=True)
    
    model_path = os.path.join(output_dir, 'dqn_model.pt')
    agent.save(model_path)
    print(f"✓ Modèle DQN sauvegardé: {model_path}")
    
    # Sauvegarder métriques
    import pickle
    metrics_path = os.path.join(output_dir, 'dqn_training_metrics.pkl')
    with open(metrics_path, 'wb') as f:
        pickle.dump(metrics, f)
    print(f"✓ Métriques sauvegardées: {metrics_path}")


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Entraînement DQN pour TCDRM v2')
    parser.add_argument('--episodes', type=int, default=200)
    parser.add_argument('--queries', type=int, default=1000)
    parser.add_argument('--buffer-size', type=int, default=50000,
                        help='Taille du replay buffer (défaut: 50000)')
    parser.add_argument('--batch-size', type=int, default=128,
                        help='Taille du batch (défaut: 128)')
    parser.add_argument('--lr', type=float, default=0.0003,
                        help='Learning rate (défaut: 0.0003)')
    parser.add_argument('--gamma', type=float, default=0.99,
                        help='Discount factor (défaut: 0.99)')
    parser.add_argument('--epsilon-start', type=float, default=1.0,
                        help='Epsilon initial (défaut: 1.0)')
    parser.add_argument('--epsilon-min', type=float, default=0.01,
                        help='Epsilon minimum (défaut: 0.01)')
    parser.add_argument('--epsilon-decay', type=float, default=0.0005,
                        help='Epsilon decay lambda (défaut: 0.0005)')
    parser.add_argument('--target-update', type=int, default=20,
                        help='Fréquence de mise à jour du target network (défaut: 20)')
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--output-dir', type=str, default=None)
    
    args = parser.parse_args()
    
    if args.output_dir is None:
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        args.output_dir = f'results/dqn/run_{timestamp}'
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Entraîner
    agent, metrics = train_dqn_policy(
        n_episodes=args.episodes,
        n_queries_per_episode=args.queries,
        learning_rate=args.lr,
        discount_factor=args.gamma,
        epsilon_start=args.epsilon_start,
        epsilon_end=args.epsilon_min,
        epsilon_decay_lambda=args.epsilon_decay,
        batch_size=args.batch_size,
        buffer_capacity=args.buffer_size,
        target_update_freq=args.target_update,
        seed=args.seed
    )
    
    # Sauvegarder
    save_model(agent, metrics, args.output_dir)
    plot_training_metrics(metrics, args.output_dir)
    
    print()
    print("="*80)
    print("✅ ENTRAÎNEMENT DQN TERMINÉ")
    print("="*80)
    print()
    print(f"Résultats disponibles dans: {args.output_dir}")
