"""
Script d'entraînement pour TCDRM-ADAPTIVE

Ce script entraîne un agent Q-Learning avec la nouvelle fonction de récompense
multi-objectif pour apprendre une politique adaptative de réplication.

Différences avec train_general_policy.py:
- Fonction de récompense multi-objectif (5 composantes)
- Apprentissage adaptatif des seuils (pas de seuil fixe à 200)
- Observation space étendu avec popularité
- Métriques avancées pour évaluation
"""

import os
import sys
import numpy as np
import pickle
from datetime import datetime
from typing import Dict, List, Tuple
import matplotlib.pyplot as plt

# Ajouter le répertoire courant au path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent


def generate_varied_queries(n_queries: int, seed: int = 42) -> List[float]:
    """
    Génère des requêtes avec tailles variées pour entraînement robuste.
    
    Distribution:
    - 40% petites requêtes (1-5 GB)
    - 40% moyennes requêtes (5-10 GB)
    - 20% grandes requêtes (10-20 GB)
    """
    np.random.seed(seed)
    
    query_sizes = []
    for _ in range(n_queries):
        rand = np.random.random()
        if rand < 0.4:
            # Petites requêtes
            size = np.random.uniform(1.0, 5.0)
        elif rand < 0.8:
            # Moyennes requêtes
            size = np.random.uniform(5.0, 10.0)
        else:
            # Grandes requêtes
            size = np.random.uniform(10.0, 20.0)
        
        query_sizes.append(size)
    
    return query_sizes


def train_adaptive_policy(
    n_episodes: int = 200,
    n_queries_per_episode: int = 1000,
    learning_rate: float = 0.1,
    discount_factor: float = 0.95,
    epsilon_start: float = 1.0,
    epsilon_end: float = 0.01,
    epsilon_decay: float = 0.995,
    seed: int = 42
) -> Tuple[TabularQLearningAgent, Dict]:
    """
    Entraîne un agent Q-Learning adaptatif pour TCDRM.
    
    Args:
        n_episodes: Nombre d'épisodes d'entraînement
        n_queries_per_episode: Nombre de requêtes par épisode
        learning_rate: Taux d'apprentissage (alpha)
        discount_factor: Facteur de discount (gamma)
        epsilon_start: Epsilon initial (exploration)
        epsilon_end: Epsilon final
        epsilon_decay: Taux de décroissance d'epsilon
        seed: Seed pour reproductibilité
    
    Returns:
        Tuple (agent entraîné, métriques d'entraînement)
    """
    print("="*80)
    print("ENTRAÎNEMENT TCDRM-ADAPTIVE")
    print("="*80)
    print()
    print(f"Configuration:")
    print(f"  Episodes: {n_episodes}")
    print(f"  Requêtes par épisode: {n_queries_per_episode}")
    print(f"  Learning rate: {learning_rate}")
    print(f"  Discount factor: {discount_factor}")
    print(f"  Epsilon: {epsilon_start} → {epsilon_end} (decay={epsilon_decay})")
    print()
    
    # Générer des requêtes variées pour l'entraînement
    query_sizes = generate_varied_queries(n_queries_per_episode * n_episodes, seed)
    
    # Créer l'environnement avec une taille moyenne
    avg_query_size = np.mean(query_sizes[:n_queries_per_episode])
    env = TcdrmAdaptiveEnv(data_gb=avg_query_size)
    
    # Créer l'agent Q-Learning
    agent = TabularQLearningAgent(
        n_states=108,  # 3*3*3*4 états discrets
        n_actions=3,   # CREATE, DELETE, DO_NOTHING
        learning_rate=learning_rate,
        discount_factor=discount_factor,
        epsilon=epsilon_start
    )
    
    # Métriques d'entraînement
    episode_rewards = []
    episode_costs = []
    episode_sla_violations = []
    episode_replica_changes = []
    best_reward = -float('inf')
    best_episode = 0
    
    print("Début de l'entraînement...")
    print()
    
    for episode in range(n_episodes):
        # Choisir une taille de requête pour cet épisode
        episode_query_size = query_sizes[episode * n_queries_per_episode]
        env.data_gb = episode_query_size
        
        # Reset environnement
        state, info = env.reset(seed=seed + episode)
        state_idx = agent.discretize_state(state)
        
        episode_reward = 0.0
        episode_cost = 0.0
        replica_changes = 0
        last_replica_count = 0
        
        for query_idx in range(n_queries_per_episode):
            # Sélectionner action
            action = agent.select_action(state_idx, training=True)
            
            # Exécuter action
            next_state, reward, terminated, truncated, info = env.step(action)
            next_state_idx = agent.discretize_state(next_state)
            
            # Mettre à jour Q-table
            agent.update(state_idx, action, reward, next_state_idx, terminated or truncated)
            
            # Tracking métriques
            episode_reward += reward
            episode_cost += info['total_cost']
            
            # Compter changements de réplicas
            if info['replicas'] != last_replica_count:
                replica_changes += 1
                last_replica_count = info['replicas']
            
            # Passer à l'état suivant
            state_idx = next_state_idx
            
            if terminated or truncated:
                break
        
        # Décroissance epsilon
        agent.epsilon = max(epsilon_end, agent.epsilon * epsilon_decay)
        
        # Sauvegarder métriques
        episode_rewards.append(episode_reward)
        episode_costs.append(episode_cost)
        episode_sla_violations.append(info['sla_violations'])
        episode_replica_changes.append(replica_changes)
        
        # Sauvegarder meilleur modèle
        if episode_reward > best_reward:
            best_reward = episode_reward
            best_episode = episode
        
        # Afficher progression
        if (episode + 1) % 10 == 0:
            avg_reward = np.mean(episode_rewards[-10:])
            avg_cost = np.mean(episode_costs[-10:])
            avg_violations = np.mean(episode_sla_violations[-10:])
            avg_changes = np.mean(episode_replica_changes[-10:])
            
            print(f"Episode {episode + 1}/{n_episodes}")
            print(f"  Reward moyen (10 derniers): {avg_reward:.2f}")
            print(f"  Coût moyen: {avg_cost:.2f}")
            print(f"  Violations SLA: {avg_violations:.1f}")
            print(f"  Changements réplicas: {avg_changes:.1f}")
            print(f"  Epsilon: {agent.epsilon:.3f}")
            print()
    
    print("="*80)
    print("ENTRAÎNEMENT TERMINÉ")
    print("="*80)
    print()
    print(f"Meilleur épisode: {best_episode + 1} (reward={best_reward:.2f})")
    print()
    
    # Préparer métriques de retour
    metrics = {
        'episode_rewards': episode_rewards,
        'episode_costs': episode_costs,
        'episode_sla_violations': episode_sla_violations,
        'episode_replica_changes': episode_replica_changes,
        'best_episode': best_episode,
        'best_reward': best_reward,
        'final_epsilon': agent.epsilon,
        'query_sizes': query_sizes[:n_queries_per_episode * n_episodes]
    }
    
    return agent, metrics


def plot_training_metrics(metrics: Dict, output_dir: str):
    """
    Génère des graphiques des métriques d'entraînement.
    """
    os.makedirs(output_dir, exist_ok=True)
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    
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
    axes[1, 0].plot(metrics['episode_sla_violations'])
    axes[1, 0].set_title('Violations SLA par épisode')
    axes[1, 0].set_xlabel('Épisode')
    axes[1, 0].set_ylabel('Nombre de violations')
    axes[1, 0].grid(True)
    
    # Changements de réplicas
    axes[1, 1].plot(metrics['episode_replica_changes'])
    axes[1, 1].set_title('Changements de réplicas par épisode')
    axes[1, 1].set_xlabel('Épisode')
    axes[1, 1].set_ylabel('Nombre de changements')
    axes[1, 1].grid(True)
    
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'training_metrics.png'), dpi=150)
    print(f"✓ Graphiques sauvegardés: {output_dir}/training_metrics.png")


def save_model(agent: TabularQLearningAgent, metrics: Dict, output_dir: str):
    """
    Sauvegarde le modèle et les métriques.
    """
    os.makedirs(output_dir, exist_ok=True)
    
    # Sauvegarder Q-table et configuration
    model_data = {
        'q_table': agent.q_table,
        'n_states': agent.n_states,
        'n_actions': agent.n_actions,
        'learning_rate': agent.learning_rate,
        'discount_factor': agent.discount_factor,
        'epsilon': agent.epsilon,
        'metrics': metrics
    }
    
    model_path = os.path.join(output_dir, 'adaptive_model.pkl')
    with open(model_path, 'wb') as f:
        pickle.dump(model_data, f)
    
    print(f"✓ Modèle sauvegardé: {model_path}")
    
    # Sauvegarder métriques séparément
    metrics_path = os.path.join(output_dir, 'training_metrics.pkl')
    with open(metrics_path, 'wb') as f:
        pickle.dump(metrics, f)
    
    print(f"✓ Métriques sauvegardées: {metrics_path}")


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Entraînement TCDRM-ADAPTIVE avec Q-Learning'
    )
    parser.add_argument('--episodes', type=int, default=200,
                       help='Nombre d\'épisodes d\'entraînement')
    parser.add_argument('--queries', type=int, default=1000,
                       help='Nombre de requêtes par épisode')
    parser.add_argument('--lr', type=float, default=0.1,
                       help='Learning rate')
    parser.add_argument('--gamma', type=float, default=0.95,
                       help='Discount factor')
    parser.add_argument('--epsilon-start', type=float, default=1.0,
                       help='Epsilon initial')
    parser.add_argument('--epsilon-end', type=float, default=0.01,
                       help='Epsilon final')
    parser.add_argument('--epsilon-decay', type=float, default=0.995,
                       help='Taux de décroissance epsilon')
    parser.add_argument('--seed', type=int, default=42,
                       help='Random seed')
    parser.add_argument('--output-dir', type=str, default=None,
                       help='Répertoire de sortie')
    
    args = parser.parse_args()
    
    # Créer répertoire de sortie
    if args.output_dir is None:
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        args.output_dir = f'results/tcdrm_adaptive/run_{timestamp}'
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Entraîner
    agent, metrics = train_adaptive_policy(
        n_episodes=args.episodes,
        n_queries_per_episode=args.queries,
        learning_rate=args.lr,
        discount_factor=args.gamma,
        epsilon_start=args.epsilon_start,
        epsilon_end=args.epsilon_end,
        epsilon_decay=args.epsilon_decay,
        seed=args.seed
    )
    
    # Sauvegarder
    save_model(agent, metrics, args.output_dir)
    plot_training_metrics(metrics, args.output_dir)
    
    print()
    print("="*80)
    print("✅ ENTRAÎNEMENT TCDRM-ADAPTIVE TERMINÉ")
    print("="*80)
    print()
    print(f"Résultats disponibles dans: {args.output_dir}")
