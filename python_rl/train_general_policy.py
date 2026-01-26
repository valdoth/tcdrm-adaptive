"""
Entraînement d'une politique RL générale
Utilise 10,000 requêtes avec des tailles variées pour apprendre une politique optimale
"""

import os
import sys
import numpy as np
import pickle
from datetime import datetime
import random

# Ajouter le répertoire courant au path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent


def generate_random_query_sizes(n_queries=10000, min_size=0.5, max_size=5.0):
    """
    Génère des tailles de requêtes aléatoires variées
    
    Args:
        n_queries: Nombre de requêtes à générer
        min_size: Taille minimale en GB
        max_size: Taille maximale en GB
    
    Returns:
        Liste de tailles de requêtes en GB
    """
    # Utiliser une distribution qui favorise les tailles moyennes
    # avec quelques valeurs extrêmes
    sizes = []
    for _ in range(n_queries):
        # 70% de requêtes moyennes (1-3 GB)
        # 15% de petites requêtes (0.5-1 GB)
        # 15% de grandes requêtes (3-5 GB)
        rand = random.random()
        if rand < 0.15:
            size = random.uniform(min_size, 1.0)
        elif rand < 0.85:
            size = random.uniform(1.0, 3.0)
        else:
            size = random.uniform(3.0, max_size)
        sizes.append(round(size, 2))
    
    return sizes


def train_general_policy(
    n_training_queries=10000,
    n_episodes=100,
    learning_rate=0.1,
    discount_factor=0.95,
    epsilon_start=1.0,
    epsilon_end=0.01,
    epsilon_decay=0.995,
    output_dir='results/qlearning/general_policy'
):
    """
    Entraîne une politique RL générale sur des requêtes variées
    
    Args:
        n_training_queries: Nombre de requêtes d'entraînement
        n_episodes: Nombre d'épisodes d'entraînement
        learning_rate: Taux d'apprentissage
        discount_factor: Facteur de discount
        epsilon_start: Epsilon initial pour exploration
        epsilon_end: Epsilon final
        epsilon_decay: Taux de décroissance d'epsilon
        output_dir: Répertoire de sortie
    """
    print("="*80)
    print("ENTRAÎNEMENT D'UNE POLITIQUE RL GÉNÉRALE")
    print("="*80)
    print()
    
    # Créer le répertoire de sortie
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = os.path.join(output_dir, f'run_{timestamp}')
    models_dir = os.path.join(run_dir, 'models')
    logs_dir = os.path.join(run_dir, 'logs')
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(logs_dir, exist_ok=True)
    
    print(f"📁 Répertoire de sortie: {run_dir}")
    print()
    
    # Générer les tailles de requêtes pour l'entraînement
    print(f"🎲 Génération de {n_training_queries} requêtes d'entraînement variées...")
    query_sizes = generate_random_query_sizes(n_training_queries)
    
    print(f"   Taille min: {min(query_sizes):.2f} GB")
    print(f"   Taille max: {max(query_sizes):.2f} GB")
    print(f"   Taille moyenne: {np.mean(query_sizes):.2f} GB")
    print(f"   Taille médiane: {np.median(query_sizes):.2f} GB")
    print()
    
    # Sauvegarder les tailles de requêtes
    with open(os.path.join(run_dir, 'training_queries.pkl'), 'wb') as f:
        pickle.dump(query_sizes, f)
    
    # Créer l'environnement avec une taille moyenne pour l'initialisation
    avg_size = np.mean(query_sizes)
    env = TcdrmAdaptiveEnv(data_gb=avg_size)
    
    # Créer l'agent
    # L'agent utilise la discrétisation interne pour gérer l'espace Box
    # Nombre d'états discrétisés: 3 (latency) * 3 (cost) * 3 (replicas) * 4 (queries) = 108
    n_states = 108
    n_actions = env.action_space.n
    
    agent = TabularQLearningAgent(
        n_states=n_states,
        n_actions=n_actions,
        learning_rate=learning_rate,
        discount_factor=discount_factor,
        epsilon=epsilon_start
    )
    
    print(f"🤖 Agent créé:")
    print(f"   États discrétisés: {n_states}")
    print(f"   Actions: {n_actions}")
    print(f"   Learning rate: {learning_rate}")
    print(f"   Discount factor: {discount_factor}")
    print()
    
    # Métriques d'entraînement
    episode_rewards = []
    episode_lengths = []
    episode_costs = []
    best_reward = float('-inf')
    
    print(f"🏋️  Début de l'entraînement sur {n_episodes} épisodes...")
    print()
    
    for episode in range(n_episodes):
        # Sélectionner aléatoirement une taille de requête pour cet épisode
        query_size = random.choice(query_sizes)
        
        # Réinitialiser l'environnement avec cette taille
        env = TcdrmAdaptiveEnv(data_gb=query_size)
        state, info = env.reset()
        state_idx = agent.discretize_state(state)
        
        episode_reward = 0
        episode_cost = 0
        step = 0
        done = False
        
        while not done and step < 1000:  # Max 1000 steps par épisode
            # Sélectionner et exécuter une action
            action = agent.select_action(state_idx, training=True)
            next_state, reward, terminated, truncated, info = env.step(action)
            next_state_idx = agent.discretize_state(next_state)
            done = terminated or truncated
            
            # Mettre à jour l'agent
            agent.update(state_idx, action, reward, next_state_idx, done)
            
            # Accumuler les métriques
            episode_reward += reward
            episode_cost += info.get('cost', 0)
            
            state_idx = next_state_idx
            step += 1
        
        # Décroissance d'epsilon
        agent.epsilon = max(epsilon_end, agent.epsilon * epsilon_decay)
        
        # Enregistrer les métriques
        episode_rewards.append(episode_reward)
        episode_lengths.append(step)
        episode_costs.append(episode_cost)
        
        # Sauvegarder le meilleur modèle
        if episode_reward > best_reward:
            best_reward = episode_reward
            best_model_path = os.path.join(models_dir, 'best_model.pkl')
            with open(best_model_path, 'wb') as f:
                pickle.dump({
                    'q_table': agent.q_table,
                    'n_states': agent.n_states,
                    'n_actions': agent.n_actions,
                    'episode': episode,
                    'reward': episode_reward
                }, f)
        
        # Afficher la progression
        if (episode + 1) % 10 == 0:
            avg_reward = np.mean(episode_rewards[-10:])
            avg_length = np.mean(episode_lengths[-10:])
            avg_cost = np.mean(episode_costs[-10:])
            print(f"Episode {episode+1}/{n_episodes} | "
                  f"Reward: {avg_reward:.2f} | "
                  f"Length: {avg_length:.1f} | "
                  f"Cost: {avg_cost:.2f} | "
                  f"Epsilon: {agent.epsilon:.3f}")
    
    print()
    print("="*80)
    print("✅ ENTRAÎNEMENT TERMINÉ")
    print("="*80)
    print()
    print(f"📊 Statistiques finales:")
    print(f"   Meilleure récompense: {best_reward:.2f}")
    print(f"   Récompense moyenne (10 derniers): {np.mean(episode_rewards[-10:]):.2f}")
    print(f"   Longueur moyenne (10 derniers): {np.mean(episode_lengths[-10:]):.1f}")
    print(f"   Coût moyen (10 derniers): {np.mean(episode_costs[-10:]):.2f}")
    print()
    print(f"💾 Modèle sauvegardé: {best_model_path}")
    print()
    
    # Sauvegarder les métriques
    metrics = {
        'episode_rewards': episode_rewards,
        'episode_lengths': episode_lengths,
        'episode_costs': episode_costs,
        'best_reward': best_reward,
        'n_training_queries': n_training_queries,
        'n_episodes': n_episodes
    }
    
    with open(os.path.join(logs_dir, 'training_metrics.pkl'), 'wb') as f:
        pickle.dump(metrics, f)
    
    return best_model_path, run_dir


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Entraînement d\'une politique RL générale')
    parser.add_argument('--n-queries', type=int, default=10000,
                       help='Nombre de requêtes d\'entraînement')
    parser.add_argument('--n-episodes', type=int, default=100,
                       help='Nombre d\'épisodes d\'entraînement')
    parser.add_argument('--lr', type=float, default=0.1,
                       help='Learning rate')
    parser.add_argument('--gamma', type=float, default=0.95,
                       help='Discount factor')
    parser.add_argument('--output-dir', type=str, default='results/qlearning/general_policy',
                       help='Répertoire de sortie')
    
    args = parser.parse_args()
    
    model_path, run_dir = train_general_policy(
        n_training_queries=args.n_queries,
        n_episodes=args.n_episodes,
        learning_rate=args.lr,
        discount_factor=args.gamma,
        output_dir=args.output_dir
    )
    
    print(f"✅ Entraînement terminé!")
    print(f"   Modèle: {model_path}")
    print(f"   Résultats: {run_dir}")
