"""
Script unifié pour générer des graphiques cohérents avec N courbes
Garantit que les mêmes modèles produisent les mêmes résultats
"""

import os
import sys
import numpy as np
import pickle
import torch
import matplotlib.pyplot as plt
from typing import Dict, List, Optional
import random

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from envs.tcdrm_env_v2 import TcdrmV2Env
from envs.tcdrm_qlearning_env import TcdrmQLearningEnv
from agents.simple_qlearning_wrapper import SimpleQLearningWrapper
from agents.dqn_agent import DQNAgent

try:
    from stable_baselines3 import PPO
    SB3_AVAILABLE = True
except ImportError:
    SB3_AVAILABLE = False


def set_global_seed(seed: int):
    """Fixe toutes les sources d'aléatoire pour garantir la reproductibilité"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    # Rendre les opérations déterministes
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def evaluate_model(model_type: str, model_path: str, data_gb: float, seed: int = 42, n_queries: int = 1000) -> Dict[str, List]:
    """
    Évalue un modèle RL de manière déterministe
    
    Returns:
        Dict avec 'times', 'costs', 'replicas'
    """
    print(f"  Évaluation {model_type}...")
    
    # Fixer le seed global
    set_global_seed(seed)
    
    # Debug flag
    DEBUG = False
    
    # Utiliser TcdrmQLearningEnv pour tous les modèles pour une comparaison équitable
    # Cet environnement n'a pas de réplication automatique
    
    if model_type == "Q-Learning":
        # Q-Learning utilise TcdrmQLearningEnv avec états discrets
        env_ql = TcdrmQLearningEnv(data_gb=data_gb)
        
        # Charger le modèle SimpleQLearning
        agent = SimpleQLearningWrapper(n_states=243, n_actions=3)
        agent.load(model_path)
        
        state, _ = env_ql.reset(seed=seed)
        times = []
        costs = []
        replicas = []
        
        for i in range(n_queries):
            # Obtenir actions valides
            valid_actions = []
            for action in range(3):
                if env_ql._is_action_valid(action):
                    valid_actions.append(action)
            
            # Sélectionner action
            action = agent.select_action(state, training=False, valid_actions=valid_actions)
            state, reward, terminated, truncated, info = env_ql.step(action)
            times.append(info['latency'])
            costs.append(info.get('avg_cost_per_query', 0) if i == 0 else info['total_cost'] - sum(costs))
            replicas.append(info['replicas'])
            if terminated or truncated:
                break
    
    elif model_type == "DQN":
        # DQN utilise aussi TcdrmQLearningEnv pour cohérence, mais on doit convertir
        # l'état discret en état continu pour DQN
        env_ql = TcdrmQLearningEnv(data_gb=data_gb)
        agent = DQNAgent(state_dim=8, action_dim=3)
        agent.load(model_path)
        
        state_discrete, _ = env_ql.reset(seed=seed)
        times = []
        costs = []
        replicas = []
        
        for i in range(n_queries):
            # Convertir état discret [RT, COST, POP, BUD, NET] en état continu pour DQN
            # Utiliser les infos de l'environnement pour reconstruire un état continu approximatif
            info_current = env_ql._get_info()
            
            # Créer un état continu approximatif pour DQN (8 dimensions)
            state_continuous = np.array([
                info_current['latency'] / 300.0,  # tQ_norm
                info_current['total_cost'] / max(1, i+1) / 10.0,  # cQ_norm approximatif
                0.5,  # pop_norm (on n'a pas accès direct)
                info_current['budget'] / env_ql.INITIAL_BUDGET,  # bud_norm
                float(info_current['replicas']) / env_ql.MAX_REPLICAS,  # net_inter_ratio approximatif
                0.5,  # net_intercloud_ratio
                float(info_current['replicas']) / env_ql.MAX_REPLICAS,  # repl_factor
                0.0   # trend_pop
            ], dtype=np.float32)
            
            # Créer action mask basé sur les contraintes
            action_mask = np.ones(3, dtype=bool)
            if info_current['replicas'] >= env_ql.MAX_REPLICAS:
                action_mask[1] = False  # Pas de REPLICATE
            if info_current['replicas'] == 0:
                action_mask[2] = False  # Pas de DELETE
            
            action = agent.select_action(state_continuous, training=False, action_mask=action_mask)
            state_discrete, reward, terminated, truncated, info = env_ql.step(action)
            times.append(info['latency'])
            costs.append(info.get('avg_cost_per_query', 0) if i == 0 else info['total_cost'] - sum(costs))
            replicas.append(info['replicas'])
            if terminated or truncated:
                break
    
    elif model_type == "PPO":
        model = PPO.load(model_path)
        
        state, _ = env.reset(seed=seed)
        times = []
        costs = []
        replicas = []
        
        for i in range(n_queries):
            action, _ = model.predict(state, deterministic=True)
            state, reward, terminated, truncated, info = env.step(action)
            times.append(info['latency'])
            costs.append(info['total_cost'] - sum(costs))  # Coût incrémental
            replicas.append(info['replicas'])
            if terminated or truncated:
                break
    
    return {'times': times, 'costs': costs, 'replicas': replicas}


def evaluate_tcdrm_static(data_gb: float, seed: int = 42, n_queries: int = 1000) -> Dict[str, List]:
    """Évalue TCDRM Statique (seuil fixe à 200) de manière déterministe"""
    print(f"  Évaluation TCDRM Statique...")
    
    # Fixer le seed global
    set_global_seed(seed)
    
    # Utiliser TcdrmQLearningEnv pour cohérence
    env = TcdrmQLearningEnv(data_gb=data_gb)
    state, _ = env.reset(seed=seed)
    
    times = []
    costs = []
    replicas = []
    POPULARITY_THRESHOLD = 200
    TARGET_REPLICAS = 3
    
    for i in range(n_queries):
        # Actions: 0=NOOP, 1=REPLICATE, 2=DELETE dans TcdrmQLearningEnv
        if i < POPULARITY_THRESHOLD:
            action = 0  # NOOP
        elif i < POPULARITY_THRESHOLD + TARGET_REPLICAS:
            action = 1  # REPLICATE
        else:
            action = 0  # NOOP
        
        state, reward, terminated, truncated, info = env.step(action)
        times.append(info['latency'])
        costs.append(info.get('avg_cost_per_query', 0) if i == 0 else info['total_cost'] - sum(costs))
        replicas.append(info['replicas'])
        if terminated or truncated:
            break
    
    return {'times': times, 'costs': costs, 'replicas': replicas}


def evaluate_norep(data_gb: float, seed: int = 42, n_queries: int = 1000) -> Dict[str, List]:
    """Évalue NOREP (pas de réplication) de manière déterministe"""
    print(f"  Évaluation NOREP...")
    
    # Fixer le seed global
    set_global_seed(seed)
    
    # Utiliser TcdrmQLearningEnv pour cohérence
    env = TcdrmQLearningEnv(data_gb=data_gb)
    state, _ = env.reset(seed=seed)
    
    times = []
    costs = []
    replicas = []
    
    for i in range(n_queries):
        action = 0  # NOOP (pas de réplication)
        state, reward, terminated, truncated, info = env.step(action)
        times.append(info['latency'])
        costs.append(info.get('avg_cost_per_query', 0) if i == 0 else info['total_cost'] - sum(costs))
        replicas.append(info['replicas'])
        if terminated or truncated:
            break
    
    return {'times': times, 'costs': costs, 'replicas': replicas}


def smooth_data(data, window=50):
    """Lisse les données avec une moyenne mobile"""
    smoothed = []
    for i in range(len(data)):
        start = max(0, i - window // 2)
        end = min(len(data), i + window // 2)
        smoothed.append(np.mean(data[start:end]))
    return smoothed


def generate_graphs(
    scenario: str,
    data_gb: float,
    models: Dict[str, Optional[str]],
    output_dir: str = "../images",
    seed: int = 42,
    suffix: str = ""
):
    """
    Génère les graphes pour un scénario avec les modèles spécifiés
    
    Args:
        scenario: Nom du scénario (R1, R2, etc.)
        data_gb: Taille des données en GB
        models: Dict avec les chemins des modèles {'qlearning': path, 'dqn': path, 'ppo': path}
        output_dir: Répertoire de sortie
        seed: Seed pour reproductibilité
        suffix: Suffixe pour les noms de fichiers (ex: '3curves', '4curves', '5curves')
    """
    
    print(f"\n{'='*80}")
    print(f"GÉNÉRATION DES GRAPHES - {scenario} ({data_gb} GB) - {suffix}")
    print(f"{'='*80}\n")
    
    os.makedirs(output_dir, exist_ok=True)
    
    # Collecter les résultats de tous les modèles
    results = {}
    
    # Évaluer les modèles RL si fournis
    if models.get('qlearning') and os.path.exists(models['qlearning']):
        results['Q-Learning'] = evaluate_model("Q-Learning", models['qlearning'], data_gb, seed)
    
    if models.get('dqn') and os.path.exists(models['dqn']):
        results['DQN'] = evaluate_model("DQN", models['dqn'], data_gb, seed)
    
    if models.get('ppo') and os.path.exists(models['ppo']) and SB3_AVAILABLE:
        results['PPO'] = evaluate_model("PPO", models['ppo'], data_gb, seed)
    
    # Toujours évaluer TCDRM Statique et NOREP
    results['TCDRM Statique'] = evaluate_tcdrm_static(data_gb, seed)
    results['NOREP'] = evaluate_norep(data_gb, seed)
    
    # Couleurs cohérentes
    colors = {
        'Q-Learning': '#FFC107',      # Jaune/Orange (comme Python RL dans Java)
        'DQN': '#00CED1',             # Turquoise
        'PPO': '#9370DB',             # Purple
        'TCDRM Statique': '#F44336',  # Rouge (comme dans Java)
        'NOREP': '#FF7F0E'            # Orange (comme dans Java)
    }
    
    # 1. Graphe combiné Response Time (Raw + Smoothed)
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(20, 7))
    
    # Raw (gauche)
    for name, data in results.items():
        ax1.plot(data['times'], label=name, color=colors.get(name, '#000000'), 
                linewidth=1.5, alpha=0.8)
    ax1.set_title(f'Impact of Replication on Response Time (Raw) - {scenario}', 
                  fontsize=14, fontweight='bold')
    ax1.set_xlabel('Number of Queries', fontsize=12)
    ax1.set_ylabel('Response Time (ms)', fontsize=12)
    ax1.legend(loc='best', fontsize=10)
    ax1.grid(True, alpha=0.3)
    
    # Smoothed (droite)
    for name, data in results.items():
        linewidth = 3.0 if name == 'TCDRM Statique' else 2.5
        ax2.plot(smooth_data(data['times']), label=name, color=colors.get(name, '#000000'), 
                linewidth=linewidth)
    ax2.set_title(f'Impact of Replication on Response Time (Smoothed) - {scenario}', 
                  fontsize=14, fontweight='bold')
    ax2.set_xlabel('Number of Queries', fontsize=12)
    ax2.set_ylabel('Response Time (ms)', fontsize=12)
    ax2.legend(loc='best', fontsize=10)
    ax2.grid(True, alpha=0.3)
    
    plt.tight_layout()
    filename = os.path.join(output_dir, f'tcdrm_combined_response_time_{scenario}_{suffix}.png')
    plt.savefig(filename, dpi=150)
    print(f"  ✓ {filename}")
    plt.close()
    
    # 2. Coût cumulatif BW (Raw + Smoothed)
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(20, 7))
    
    # Raw (gauche)
    for name, data in results.items():
        cumulative_costs = np.cumsum(data['costs'])
        ax1.plot(cumulative_costs, label=name, color=colors.get(name, '#000000'), 
                linewidth=1.5, alpha=0.8)
    ax1.set_title(f'PRIX CUMULATIF BW (Raw) - {scenario}', fontsize=14, fontweight='bold')
    ax1.set_xlabel('Number of Queries', fontsize=12)
    ax1.set_ylabel('Cumulative BW Cost ($)', fontsize=12)
    ax1.legend(loc='best', fontsize=10)
    ax1.grid(True, alpha=0.3)
    
    # Smoothed (droite)
    for name, data in results.items():
        cumulative_costs = np.cumsum(data['costs'])
        linewidth = 3.0 if name == 'TCDRM Statique' else 2.5
        ax2.plot(smooth_data(cumulative_costs.tolist()), label=name, 
                color=colors.get(name, '#000000'), linewidth=linewidth)
    ax2.set_title(f'PRIX CUMULATIF BW (Smoothed) - {scenario}', fontsize=14, fontweight='bold')
    ax2.set_xlabel('Number of Queries', fontsize=12)
    ax2.set_ylabel('Cumulative BW Cost ($)', fontsize=12)
    ax2.legend(loc='best', fontsize=10)
    ax2.grid(True, alpha=0.3)
    
    plt.tight_layout()
    filename = os.path.join(output_dir, f'tcdrm_combined_cumulative_bw_price_{scenario}_{suffix}.png')
    plt.savefig(filename, dpi=150)
    print(f"  ✓ {filename}")
    plt.close()
    
    # 3. Total Cost
    plt.figure(figsize=(12, 7))
    for name, data in results.items():
        cumulative_costs = np.cumsum(data['costs'])
        plt.plot(cumulative_costs, label=name, color=colors.get(name, '#000000'), linewidth=2.5)
    
    plt.title(f'Total Cost Comparison - {scenario}', fontsize=14, fontweight='bold')
    plt.xlabel('Number of Queries', fontsize=12)
    plt.ylabel('Cumulative Cost ($)', fontsize=12)
    plt.legend(loc='best', fontsize=10)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    
    filename = os.path.join(output_dir, f'tcdrm_combined_total_cost_{scenario}_{suffix}.png')
    plt.savefig(filename, dpi=150)
    print(f"  ✓ {filename}")
    plt.close()
    
    print(f"\n✅ Graphes générés pour {scenario} - {suffix}")


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Générer graphes cohérents avec N courbes')
    parser.add_argument('--qlearning-model', type=str, required=True, 
                       help='Chemin vers le modèle Q-Learning')
    parser.add_argument('--dqn-model', type=str, default=None,
                       help='Chemin vers le modèle DQN (optionnel)')
    parser.add_argument('--ppo-model', type=str, default=None,
                       help='Chemin vers le modèle PPO (optionnel)')
    parser.add_argument('--output-dir', type=str, default='../images',
                       help='Répertoire de sortie')
    parser.add_argument('--seed', type=int, default=42,
                       help='Seed pour reproductibilité')
    parser.add_argument('--suffix', type=str, default='unified',
                       help='Suffixe pour les noms de fichiers')
    
    args = parser.parse_args()
    
    # Déterminer le suffixe basé sur le nombre de courbes
    models = {
        'qlearning': args.qlearning_model,
        'dqn': args.dqn_model,
        'ppo': args.ppo_model
    }
    
    # Compter les modèles RL disponibles + 2 (TCDRM Statique + NOREP)
    n_rl_models = sum(1 for m in [args.qlearning_model, args.dqn_model, args.ppo_model] 
                      if m and os.path.exists(m))
    n_total_curves = n_rl_models + 2
    
    if args.suffix == 'unified':
        suffix = f'{n_total_curves}curves'
    else:
        suffix = args.suffix
    
    print("="*80)
    print(f"GÉNÉRATION DES GRAPHES COHÉRENTS - {suffix.upper()}")
    print("="*80)
    print(f"Seed: {args.seed}")
    print(f"Modèles RL: {n_rl_models}")
    print(f"Total courbes: {n_total_curves}")
    print()
    
    # R1 (3 fragments, 5.3 GB)
    generate_graphs("R1", 5.3, models, args.output_dir, args.seed, suffix)
    
    # R2 (6 fragments, 11.9 GB)
    generate_graphs("R2", 11.9, models, args.output_dir, args.seed, suffix)
    
    print()
    print("="*80)
    print("✅ GÉNÉRATION TERMINÉE")
    print("="*80)
    print()
    print(f"Graphes sauvegardés dans: {args.output_dir}")
    print(f"Voir les graphes: open {args.output_dir}/*_{suffix}.png")


if __name__ == '__main__':
    main()
