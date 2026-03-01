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
from utils.tensorboard_callback import TensorBoardCallback


def generate_varied_queries(n_queries: int, seed: int = 42, pattern: str = 'steady'):
    """
    Génère des requêtes avec tailles variées selon différents patterns.
    
    Patterns disponibles:
    - 'steady': Charge constante (40% petites, 40% moyennes, 20% grandes)
    - 'burst': Pic soudain au milieu (50% grosses requêtes pendant le pic)
    - 'cold_to_hot': Transition progressive froid→chaud
    - 'hot_to_cold': Refroidissement chaud→froid
    - 'daily_cycle': Cycle jour/nuit (sinusoïde)
    - 'weekend': Baisse week-end (5j hauts, 2j bas)
    - 'budget_critical': Budget décroissant avec petites requêtes à la fin
    
    NOUVEAUX PATTERNS (Priorités cloud réels):
    - 'read_intensive': 90% lectures (petites), 10% écritures (grosses)
    - 'write_intensive': 30% lectures, 70% écritures (grosses)
    - 'geo_distributed': Requêtes depuis différentes régions (EU/US/ASIA)
    - 'black_friday': Événement saisonnier avec pic extrême
    """
    rng = np.random.RandomState(seed)
    query_sizes = []
    
    if pattern == 'steady':
        # Distribution normale
        for _ in range(n_queries):
            rand = rng.random()
            if rand < 0.4:
                size = rng.uniform(1.0, 5.0)
            elif rand < 0.8:
                size = rng.uniform(5.0, 10.0)
            else:
                size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'burst':
        # Pic soudain au milieu
        burst_start = n_queries // 3
        burst_end = 2 * n_queries // 3
        for i in range(n_queries):
            if burst_start <= i < burst_end:
                # Pendant le pic: 50% grosses requêtes
                rand = rng.random()
                if rand < 0.2:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.5:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            else:
                # Hors pic: distribution normale
                rand = rng.random()
                if rand < 0.6:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.9:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'cold_to_hot':
        # Transition progressive: petites → grosses requêtes
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < progress:
                size = rng.uniform(8.0, 20.0)  # Requêtes chaudes
            else:
                size = rng.uniform(1.0, 6.0)   # Requêtes froides
            query_sizes.append(size)
    
    elif pattern == 'hot_to_cold':
        # Refroidissement: grosses → petites requêtes
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < (1.0 - progress):
                size = rng.uniform(8.0, 20.0)
            else:
                size = rng.uniform(1.0, 6.0)
            query_sizes.append(size)
    
    elif pattern == 'daily_cycle':
        # Cycle jour/nuit (sinusoïde)
        for i in range(n_queries):
            phase = (i / n_queries) * 2 * np.pi
            intensity = 0.5 + 0.5 * np.sin(phase)
            rand = rng.random()
            if rand < intensity:
                size = rng.uniform(5.0, 20.0)  # Haute intensité
            else:
                size = rng.uniform(1.0, 8.0)   # Basse intensité
            query_sizes.append(size)
    
    elif pattern == 'weekend':
        # Baisse week-end
        week_length = max(1, n_queries // 7)
        for i in range(n_queries):
            day_of_week = (i // week_length) % 7
            is_weekend = day_of_week >= 5
            if is_weekend:
                rand = rng.random()
                if rand < 0.7:
                    size = rng.uniform(1.0, 5.0)
                else:
                    size = rng.uniform(5.0, 12.0)
            else:
                rand = rng.random()
                if rand < 0.4:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.8:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'budget_critical':
        # Budget décroissant: forcer petites requêtes à la fin
        for i in range(n_queries):
            progress = i / n_queries
            budget_level = 1.0 - progress
            if budget_level < 0.1:  # Budget critique (<10%)
                size = rng.uniform(1.0, 3.0)
            else:
                rand = rng.random()
                if rand < 0.4:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.8:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'read_intensive':
        # 90% lectures (petites), 10% écritures (grosses)
        # Simule e-commerce, CDN, applications read-heavy
        for _ in range(n_queries):
            is_read = rng.random() < 0.9
            if is_read:
                # Lectures: généralement petites (catalogue, prix, etc.)
                size = rng.uniform(0.1, 5.0)
            else:
                # Écritures: plus grosses (commandes, uploads)
                size = rng.uniform(5.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'write_intensive':
        # 30% lectures, 70% écritures (grosses)
        # Simule data ingestion, IoT, logging
        for _ in range(n_queries):
            is_read = rng.random() < 0.3
            if is_read:
                # Lectures: petites requêtes de monitoring
                size = rng.uniform(0.5, 5.0)
            else:
                # Écritures: grosses données (logs, metrics, events)
                size = rng.uniform(10.0, 50.0)
            query_sizes.append(size)
    
    elif pattern == 'geo_distributed':
        # Requêtes depuis différentes régions géographiques
        # 40% EU, 35% US, 25% ASIA
        regions = ['EU', 'US', 'ASIA']
        region_probs = [0.40, 0.35, 0.25]
        
        for _ in range(n_queries):
            region = rng.choice(regions, p=region_probs)
            
            # Taille varie selon la région (patterns régionaux)
            if region == 'EU':
                # Europe: requêtes moyennes (e-commerce, services)
                size = rng.uniform(2.0, 10.0)
            elif region == 'US':
                # USA: requêtes plus grosses (streaming, analytics)
                size = rng.uniform(5.0, 15.0)
            else:  # ASIA
                # Asie: requêtes plus petites (mobile-first)
                size = rng.uniform(0.5, 8.0)
            
            query_sizes.append(size)
    
    elif pattern == 'black_friday':
        # Événement saisonnier: Black Friday
        # Baseline → Montée → Pic extrême → Descente → Retour
        for i in range(n_queries):
            progress = i / n_queries
            
            if progress < 0.3:  # Baseline (30%)
                multiplier = 1.0
            elif progress < 0.4:  # Montée progressive (10%)
                # Montée de 1.0 à 6.0
                multiplier = 1.0 + (progress - 0.3) * 50
            elif progress < 0.5:  # Pic extrême (10%)
                # Pic à 10x le trafic normal
                multiplier = 10.0
            elif progress < 0.6:  # Descente rapide (10%)
                # Descente de 10.0 à 2.0
                multiplier = 10.0 - (progress - 0.5) * 80
            else:  # Retour baseline (40%)
                multiplier = 1.0
            
            # Taille de base
            base_size = rng.uniform(1.0, 5.0)
            size = base_size * multiplier
            
            # Limiter à des valeurs raisonnables
            size = min(size, 100.0)
            query_sizes.append(size)
    
    else:
        # Par défaut: steady
        return generate_varied_queries(n_queries, seed, 'steady')
    
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
    print(f"  Workload: Patterns réalistes (steady, burst, cold_to_hot, etc.)")
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
    
    # Distribution des patterns sur les épisodes (incluant nouveaux patterns prioritaires)
    patterns = [
        'steady', 'burst', 'cold_to_hot', 'hot_to_cold', 'daily_cycle', 'weekend', 'budget_critical',
        'read_intensive', 'write_intensive', 'geo_distributed', 'black_friday'
    ]
    pattern_probs = [
        0.15,  # steady (réduit)
        0.15,  # burst
        0.10,  # cold_to_hot
        0.10,  # hot_to_cold
        0.08,  # daily_cycle
        0.05,  # weekend
        0.05,  # budget_critical
        0.12,  # read_intensive (NOUVEAU - Priorité 1)
        0.08,  # write_intensive (NOUVEAU - Priorité 1)
        0.10,  # geo_distributed (NOUVEAU - Priorité 2)
        0.02   # black_friday (NOUVEAU - Priorité 3)
    ]  # Somme = 1.0
    
    # Créer environnement TCDRM v2
    env = TcdrmV2Env(data_gb=5.3)  # Taille initiale, sera mise à jour
    
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
    episode_patterns = []  # Nouveau: tracker les patterns utilisés
    action_counts = {'NOOP': 0, 'REPLICATE': 0, 'DELETE': 0}  # Nouveau: compter les actions
    best_reward = -float('inf')
    best_episode = 0
    
    print("Début de l'entraînement...")
    print()
    
    for episode in range(n_episodes):
        # Sélectionner un pattern pour cet épisode
        pattern = np.random.choice(patterns, p=pattern_probs)
        episode_patterns.append(pattern)
        
        # Générer les requêtes selon le pattern
        query_sizes = generate_varied_queries(n_queries_per_episode, seed + episode, pattern)
        
        # Reset
        state, info = env.reset(seed=seed + episode)
        episode_reward = 0.0
        replica_changes = 0
        last_replica_count = 0
        
        for query_idx in range(n_queries_per_episode):
            # Mettre à jour la taille de la requête (variable à chaque requête)
            env.data_gb = query_sizes[query_idx]
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
            
            # Compter les actions
            action_names = ['NOOP', 'REPLICATE', 'DELETE']
            action_counts[action_names[action]] += 1
            
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
            print(f"  Pattern: {pattern}")
            print(f"  Reward moyen (10 derniers): {avg_reward:.2f}")
            print(f"  Coût moyen: {avg_cost:.2f}")
            print(f"  Violations SLA: {avg_violations:.1f}")
            print(f"  Changements réplicas: {avg_changes:.1f}")
            print(f"  Loss moyenne: {avg_loss:.4f}")
            print(f"  Epsilon: {agent.epsilon:.3f}")
            print(f"  Actions: NOOP={action_counts['NOOP']}, REPLICATE={action_counts['REPLICATE']}, DELETE={action_counts['DELETE']}")
            print()
    
    print("="*80)
    print("ENTRAÎNEMENT TERMINÉ")
    print("="*80)
    print()
    print(f"Meilleur épisode: {best_episode + 1} (reward={best_reward:.2f})")
    print()
    print("Distribution des actions:")
    total_actions = sum(action_counts.values())
    for action_name, count in action_counts.items():
        pct = (count / total_actions) * 100 if total_actions > 0 else 0
        print(f"  {action_name:10s}: {count:7d} ({pct:5.1f}%)")
    print()
    print("Distribution des patterns:")
    from collections import Counter
    pattern_counter = Counter(episode_patterns)
    for pattern_name, count in pattern_counter.most_common():
        pct = (count / len(episode_patterns)) * 100
        print(f"  {pattern_name:15s}: {count:3d} épisodes ({pct:5.1f}%)")
    print()
    
    # Métriques
    metrics = {
        'episode_rewards': episode_rewards,
        'episode_costs': episode_costs,
        'episode_sla_violations': episode_sla_violations,
        'episode_replica_changes': episode_replica_changes,
        'episode_patterns': episode_patterns,
        'action_counts': action_counts,
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
    parser.add_argument('--tensorboard', action='store_true', help='Enable TensorBoard logging')
    parser.add_argument('--tensorboard-dir', type=str, default='runs', help='TensorBoard log directory')
    
    args = parser.parse_args()
    
    if args.output_dir is None:
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        args.output_dir = f'results/dqn/run_{timestamp}'
    
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Créer callback TensorBoard si activé
    tb_callback = None
    if args.tensorboard:
        exp_name = f"dqn_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        tb_callback = TensorBoardCallback(log_dir=args.tensorboard_dir, experiment_name=exp_name)
        print(f"📊 TensorBoard activé: {tb_callback.log_path}")
        print()
    
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
    
    # Logger hyperparamètres dans TensorBoard
    if tb_callback:
        hparams = {
            'lr': args.lr,
            'gamma': args.gamma,
            'epsilon_start': args.epsilon_start,
            'epsilon_min': args.epsilon_min,
            'epsilon_decay': args.epsilon_decay,
            'batch_size': args.batch_size,
            'buffer_size': args.buffer_size,
            'episodes': args.episodes
        }
        final_metrics = {
            'final_reward': metrics['episode_rewards'][-1] if metrics['episode_rewards'] else 0,
            'avg_reward_last_100': np.mean(metrics['episode_rewards'][-100:]) if len(metrics['episode_rewards']) >= 100 else 0
        }
        tb_callback.log_hyperparameters(hparams, final_metrics)
        tb_callback.close()
    
    print()
    print("="*80)
    print("✅ ENTRAÎNEMENT DQN TERMINÉ")
    print("="*80)
    print()
    print(f"Résultats disponibles dans: {args.output_dir}")
