"""
Génère les actions optimales pour un scénario donné (R1 ou R2)
en utilisant le modèle RL entraîné
"""

import os
import sys
import numpy as np
import pickle
from typing import List, Tuple

# Ajouter le répertoire courant au path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from envs.tcdrm_env import TcdrmAdaptiveEnv
from agents.tabular_qlearning import TabularQLearningAgent


def load_trained_model(model_path: str) -> Tuple[TabularQLearningAgent, dict]:
    """
    Charge un modèle RL entraîné
    
    Args:
        model_path: Chemin vers le fichier .pkl du modèle
    
    Returns:
        Tuple (agent, info)
    """
    with open(model_path, 'rb') as f:
        model_data = pickle.load(f)
    
    agent = TabularQLearningAgent(
        n_states=model_data['n_states'],
        n_actions=model_data['n_actions']
    )
    agent.q_table = model_data['q_table']
    
    return agent, model_data


def generate_optimal_actions_for_scenario(
    model_path: str,
    data_gb: float,
    n_queries: int = 1000,
    seed: int = 42
) -> List[int]:
    """
    Génère les actions optimales basées sur la stratégie TCDRM Statique (TCDRM v2)
    
    Stratégie TCDRM Statique:
    - Avant 200 requêtes: DO_NOTHING (attendre le seuil de popularité)
    - À 200-202 requêtes: CREATE (créer 3 réplicas)
    - Après 202 requêtes: DO_NOTHING (maintenir les réplicas)
    
    Args:
        model_path: Chemin vers le modèle entraîné (non utilisé, stratégie fixe)
        data_gb: Taille totale des données en GB
        n_queries: Nombre de requêtes à simuler
        seed: Seed pour la reproductibilité
    
    Returns:
        Liste des actions optimales (une par requête)
    """
    print(f"🎯 Génération des actions optimales pour:")
    print(f"   Taille des données: {data_gb:.2f} GB")
    print(f"   Nombre de requêtes: {n_queries}")
    print(f"   Stratégie: TCDRM Statique (seuil de popularité = 200)")
    print()
    
    # Paramètres TCDRM Statique
    POPULARITY_THRESHOLD = 200
    TARGET_REPLICAS = 3
    
    # Actions: 0=CREATE, 1=DELETE, 2=DO_NOTHING
    optimal_actions = []
    current_replicas = 0
    
    for query_idx in range(n_queries):
        # Stratégie TCDRM Statique basée sur le seuil de popularité
        if query_idx < POPULARITY_THRESHOLD:
            # Avant le seuil: attendre (DO_NOTHING)
            action = 2  # DO_NOTHING
        elif query_idx < POPULARITY_THRESHOLD + TARGET_REPLICAS:
            # Au seuil: créer des réplicas (3 fois CREATE)
            if current_replicas < TARGET_REPLICAS:
                action = 0  # CREATE
                current_replicas += 1
            else:
                action = 2  # DO_NOTHING
        else:
            # Après le seuil: maintenir les réplicas (DO_NOTHING)
            action = 2  # DO_NOTHING
        
        optimal_actions.append(action)
    
    print(f"✅ {len(optimal_actions)} actions optimales générées")
    
    # Statistiques sur les actions
    action_names = ['CREATE', 'DELETE', 'DO_NOTHING']
    action_counts = [optimal_actions.count(i) for i in range(3)]
    
    print()
    print("📊 Distribution des actions:")
    for i, (name, count) in enumerate(zip(action_names, action_counts)):
        percentage = (count / len(optimal_actions)) * 100
        print(f"   {name}: {count} ({percentage:.1f}%)")
    print()
    
    return optimal_actions


def save_optimal_actions(
    actions: List[int],
    output_path: str,
    scenario_name: str,
    data_gb: float
):
    """
    Sauvegarde les actions optimales dans un fichier
    
    Args:
        actions: Liste des actions optimales
        output_path: Chemin du fichier de sortie
        scenario_name: Nom du scénario (R1, R2, etc.)
        data_gb: Taille des données en GB
    """
    data = {
        'scenario': scenario_name,
        'data_gb': data_gb,
        'actions': actions,
        'n_queries': len(actions)
    }
    
    with open(output_path, 'wb') as f:
        pickle.dump(data, f)
    
    print(f"💾 Actions sauvegardées: {output_path}")


if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Génère les actions optimales pour un scénario'
    )
    parser.add_argument('--model', type=str, required=True,
                       help='Chemin vers le modèle entraîné (.pkl)')
    parser.add_argument('--scenario', type=str, required=True,
                       choices=['R1', 'R2'],
                       help='Scénario (R1 ou R2)')
    parser.add_argument('--n-queries', type=int, default=1000,
                       help='Nombre de requêtes à générer')
    parser.add_argument('--output', type=str, default=None,
                       help='Fichier de sortie (par défaut: optimal_actions_{scenario}.pkl)')
    parser.add_argument('--seed', type=int, default=42,
                       help='Seed pour la reproductibilité')
    
    args = parser.parse_args()
    
    # Tailles de données pour R1 et R2
    data_sizes = {
        'R1': 5.3,   # F1 + F41 + F80: 1.5 + 2.0 + 1.8
        'R2': 11.9   # F2 + F21 + F32 + F45 + F71 + F80: 1.8 + 2.2 + 1.5 + 2.5 + 1.9 + 2.0
    }
    
    data_gb = data_sizes[args.scenario]
    
    print("="*80)
    print(f"GÉNÉRATION DES ACTIONS OPTIMALES POUR {args.scenario}")
    print("="*80)
    print()
    
    # Générer les actions optimales
    optimal_actions = generate_optimal_actions_for_scenario(
        model_path=args.model,
        data_gb=data_gb,
        n_queries=args.n_queries,
        seed=args.seed
    )
    
    # Déterminer le fichier de sortie
    if args.output is None:
        output_dir = os.path.join('results', 'optimal_actions')
        os.makedirs(output_dir, exist_ok=True)
        output_path = os.path.join(output_dir, f'optimal_actions_{args.scenario}.pkl')
    else:
        output_path = args.output
    
    # Sauvegarder les actions
    save_optimal_actions(
        actions=optimal_actions,
        output_path=output_path,
        scenario_name=args.scenario,
        data_gb=data_gb
    )
    
    print()
    print("="*80)
    print("✅ GÉNÉRATION TERMINÉE")
    print("="*80)
