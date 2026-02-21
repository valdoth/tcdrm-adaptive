"""
Entraînement Q-Learning Simple pour TCDRM
Implémentation propre inspirée des meilleures pratiques GitHub
"""

import sys
import os
import numpy as np
import argparse
from tqdm import tqdm
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_qlearning_env import TcdrmQLearningEnv
from agents.simple_qlearning_agent import SimpleQLearningAgent


def generate_varied_queries(n_queries: int, seed: int, pattern: str = 'steady'):
    """
    Génère des requêtes avec tailles variées selon différents patterns.
    Identique à train_dqn_policy.py pour cohérence.
    
    NOUVEAUX PATTERNS (Priorités cloud réels):
    - 'read_intensive': 90% lectures (petites), 10% écritures (grosses)
    - 'write_intensive': 30% lectures, 70% écritures (grosses)
    - 'geo_distributed': Requêtes depuis différentes régions (EU/US/ASIA)
    - 'black_friday': Événement saisonnier avec pic extrême
    """
    rng = np.random.RandomState(seed)
    query_sizes = []
    
    if pattern == 'steady':
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
        burst_start = n_queries // 3
        burst_end = 2 * n_queries // 3
        for i in range(n_queries):
            if burst_start <= i < burst_end:
                rand = rng.random()
                if rand < 0.2:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.5:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            else:
                rand = rng.random()
                if rand < 0.6:
                    size = rng.uniform(1.0, 5.0)
                elif rand < 0.9:
                    size = rng.uniform(5.0, 10.0)
                else:
                    size = rng.uniform(10.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'cold_to_hot':
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < progress:
                size = rng.uniform(8.0, 20.0)
            else:
                size = rng.uniform(1.0, 6.0)
            query_sizes.append(size)
    
    elif pattern == 'hot_to_cold':
        for i in range(n_queries):
            progress = i / n_queries
            if rng.random() < (1.0 - progress):
                size = rng.uniform(8.0, 20.0)
            else:
                size = rng.uniform(1.0, 6.0)
            query_sizes.append(size)
    
    elif pattern == 'daily_cycle':
        for i in range(n_queries):
            phase = (i / n_queries) * 2 * np.pi
            intensity = 0.5 + 0.5 * np.sin(phase)
            rand = rng.random()
            if rand < intensity:
                size = rng.uniform(5.0, 20.0)
            else:
                size = rng.uniform(1.0, 8.0)
            query_sizes.append(size)
    
    elif pattern == 'weekend':
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
        for i in range(n_queries):
            progress = i / n_queries
            budget_level = 1.0 - progress
            if budget_level < 0.1:
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
        for _ in range(n_queries):
            is_read = rng.random() < 0.9
            if is_read:
                size = rng.uniform(0.1, 5.0)
            else:
                size = rng.uniform(5.0, 20.0)
            query_sizes.append(size)
    
    elif pattern == 'write_intensive':
        # 30% lectures, 70% écritures (grosses)
        for _ in range(n_queries):
            is_read = rng.random() < 0.3
            if is_read:
                size = rng.uniform(0.5, 5.0)
            else:
                size = rng.uniform(10.0, 50.0)
            query_sizes.append(size)
    
    elif pattern == 'geo_distributed':
        # Requêtes depuis différentes régions géographiques
        regions = ['EU', 'US', 'ASIA']
        region_probs = [0.40, 0.35, 0.25]
        
        for _ in range(n_queries):
            region = rng.choice(regions, p=region_probs)
            
            if region == 'EU':
                size = rng.uniform(2.0, 10.0)
            elif region == 'US':
                size = rng.uniform(5.0, 15.0)
            else:  # ASIA
                size = rng.uniform(0.5, 8.0)
            
            query_sizes.append(size)
    
    elif pattern == 'black_friday':
        # Événement saisonnier: Black Friday
        for i in range(n_queries):
            progress = i / n_queries
            
            if progress < 0.3:
                multiplier = 1.0
            elif progress < 0.4:
                multiplier = 1.0 + (progress - 0.3) * 50
            elif progress < 0.5:
                multiplier = 10.0
            elif progress < 0.6:
                multiplier = 10.0 - (progress - 0.5) * 80
            else:
                multiplier = 1.0
            
            base_size = rng.uniform(1.0, 5.0)
            size = base_size * multiplier
            size = min(size, 100.0)
            query_sizes.append(size)
    
    else:
        return generate_varied_queries(n_queries, seed, 'steady')
    
    return query_sizes


def train_qlearning(
    env: TcdrmQLearningEnv,
    agent: SimpleQLearningAgent,
    n_episodes: int = 2000,
    verbose: bool = True,
    use_realistic_workload: bool = True
):
    """
    Entraîne l'agent Q-Learning avec patterns réalistes.
    
    Args:
        env: Environnement TCDRM
        agent: Agent Q-Learning
        n_episodes: Nombre d'épisodes
        verbose: Afficher progression
        use_realistic_workload: Utiliser patterns variés (True) ou steady (False)
    """
    episode_rewards = []
    episode_lengths = []
    sla_rates = []
    episode_patterns = []
    action_counts = {'NOOP': 0, 'REPLICATE': 0, 'DELETE': 0}
    
    # Distribution des patterns (incluant nouveaux patterns prioritaires)
    if use_realistic_workload:
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
    else:
        patterns = ['steady']
        pattern_probs = [1.0]
    
    iterator = tqdm(range(n_episodes), desc="Training") if verbose else range(n_episodes)
    
    for episode in iterator:
        # Sélectionner pattern
        pattern = np.random.choice(patterns, p=pattern_probs)
        episode_patterns.append(pattern)
        
        # Générer requêtes selon le pattern
        query_sizes = generate_varied_queries(env.MAX_QUERIES, 42 + episode, pattern)
        
        state, _ = env.reset(seed=42 + episode)
        state_idx = env.state_to_index(state)
        
        episode_reward = 0
        episode_length = 0
        done = False
        query_idx = 0
        
        while not done:
            # Mettre à jour la taille de la requête
            if query_idx < len(query_sizes):
                env.data_gb = query_sizes[query_idx]
                query_idx += 1
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
            
            # Compter les actions
            action_names = ['NOOP', 'REPLICATE', 'DELETE']
            action_counts[action_names[action]] += 1
            
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
            
            postfix = {
                'reward': f'{avg_reward:.1f}',
                'sla': f'{avg_sla*100:.1f}%',
                'eps': f'{agent.epsilon:.3f}',
                'explored': f'{stats["exploration_rate"]:.1f}%'
            }
            if use_realistic_workload:
                postfix['pattern'] = pattern
            iterator.set_postfix(postfix)
    
    return {
        'episode_rewards': episode_rewards,
        'episode_lengths': episode_lengths,
        'sla_rates': sla_rates,
        'episode_patterns': episode_patterns,
        'action_counts': action_counts
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
    print(f"Data size initiale: {args.data_gb} GB (variable par requête)")
    print(f"Episodes: {args.episodes}")
    print(f"Workload: Patterns réalistes (steady, burst, cold_to_hot, etc.)")
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
    print()
    print("Distribution des actions:")
    total_actions = sum(train_stats['action_counts'].values())
    for action_name, count in train_stats['action_counts'].items():
        pct = (count / total_actions) * 100 if total_actions > 0 else 0
        print(f"  {action_name:10s}: {count:7d} ({pct:5.1f}%)")
    print()
    print("Distribution des patterns:")
    pattern_counter = Counter(train_stats['episode_patterns'])
    for pattern_name, count in pattern_counter.most_common():
        pct = (count / len(train_stats['episode_patterns'])) * 100
        print(f"  {pattern_name:15s}: {count:4d} épisodes ({pct:5.1f}%)")
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
