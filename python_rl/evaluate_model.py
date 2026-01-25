#!/usr/bin/env python3
"""
Script d'évaluation pour les modèles Q-Learning TCDRM-ADAPTIVE
Évalue la politique apprise et génère des métriques détaillées
"""

import argparse
import os
import numpy as np
import pandas as pd

from agents.tabular_qlearning import TabularQLearningAgent
from envs.tcdrm_env import TcdrmAdaptiveEnv
from utils.logger import setup_logger
from utils.metrics import MetricsTracker
from utils.visualization import plot_evaluation_results, plot_action_distribution

logger = setup_logger("TCDRM-Evaluation", "logs")


def evaluate_model(agent, env, num_episodes=100, render=False):
    """
    Évalue un agent Q-Learning sur plusieurs épisodes
    
    Args:
        agent: Agent Q-Learning entraîné
        env: Environnement TCDRM
        num_episodes: Nombre d'épisodes d'évaluation
        render: Afficher les détails de chaque épisode
    
    Returns:
        dict: Statistiques d'évaluation
    """
    metrics = MetricsTracker()
    action_counts = {0: 0, 1: 0, 2: 0}  # CREATE, DELETE, DO_NOTHING
    
    logger.info(f"Évaluation sur {num_episodes} épisodes...")
    
    for episode in range(num_episodes):
        state, _ = env.reset()
        episode_reward = 0
        episode_cost = 0
        episode_sla_violations = 0
        done = False
        step = 0
        
        episode_actions = []
        episode_states = []
        
        while not done:
            # Utiliser la politique apprise (pas d'exploration)
            action = agent.choose_action(state, explore=False)
            action_counts[action] += 1
            episode_actions.append(action)
            episode_states.append(state)
            
            # Exécuter l'action
            next_state, reward, terminated, truncated, info = env.step(action)
            done = terminated or truncated
            
            # Accumuler les métriques
            episode_reward += reward
            episode_cost += info.get('cost', 0)
            if info.get('sla_violation', False):
                episode_sla_violations += 1
            
            state = next_state
            step += 1
        
        # Enregistrer les métriques de l'épisode
        sla_compliance = 1.0 - (episode_sla_violations / max(step, 1))
        metrics.record_episode(
            episode=episode,
            reward=episode_reward,
            cost=episode_cost,
            sla_compliance=sla_compliance,
            steps=step
        )
        
        if render and (episode + 1) % 10 == 0:
            logger.info(
                f"Épisode {episode + 1}/{num_episodes} | "
                f"Récompense: {episode_reward:.2f} | "
                f"Coût: {episode_cost:.2f} | "
                f"SLA: {sla_compliance:.2%} | "
                f"Steps: {step}"
            )
    
    # Calculer les statistiques
    summary = metrics.get_summary()
    
    # Ajouter la distribution des actions
    total_actions = sum(action_counts.values())
    action_distribution = {
        'CREATE_REPLICA': action_counts[0] / total_actions,
        'DELETE_REPLICA': action_counts[1] / total_actions,
        'DO_NOTHING': action_counts[2] / total_actions
    }
    summary['action_distribution'] = action_distribution
    
    return summary, metrics, action_counts


def main():
    parser = argparse.ArgumentParser(
        description='Évaluation de modèles Q-Learning TCDRM-ADAPTIVE'
    )
    
    parser.add_argument('--model', type=str, required=True,
                       help='Chemin vers le modèle Q-Learning (.pkl)')
    parser.add_argument('--data-gb', type=float, default=5.3,
                       help='Taille des données en GB')
    parser.add_argument('--episodes', type=int, default=100,
                       help='Nombre d\'épisodes d\'évaluation')
    parser.add_argument('--render', action='store_true',
                       help='Afficher les détails de chaque épisode')
    parser.add_argument('--output-dir', type=str, default='results/evaluation',
                       help='Répertoire de sortie pour les résultats')
    
    args = parser.parse_args()
    
    logger.info("="*80)
    logger.info("TCDRM-ADAPTIVE v2.0: Évaluation du Modèle Q-Learning")
    logger.info("="*80)
    
    # Créer l'environnement
    logger.info(f"\n>>> Création de l'environnement ({args.data_gb} GB)")
    env = TcdrmAdaptiveEnv(data_gb=args.data_gb)
    
    # Charger le modèle
    logger.info(f"\n>>> Chargement du modèle: {args.model}")
    agent = TabularQLearningAgent(
        n_states=env.get_state_space_size(),
        n_actions=env.get_action_space_size()
    )
    agent.load(args.model)
    logger.info("✅ Modèle chargé avec succès")
    
    # Évaluer le modèle
    logger.info(f"\n>>> Évaluation sur {args.episodes} épisodes")
    summary, metrics, action_counts = evaluate_model(
        agent, env, args.episodes, args.render
    )
    
    # Créer le répertoire de sortie
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Sauvegarder les métriques
    metrics_path = os.path.join(args.output_dir, "evaluation_metrics.csv")
    metrics.save_to_csv(metrics_path)
    logger.info(f"\n✅ Métriques sauvegardées: {metrics_path}")
    
    # Générer les visualisations
    logger.info("\n>>> Génération des visualisations...")
    
    # Courbes d'évaluation
    plot_path = os.path.join(args.output_dir, "evaluation_results.png")
    plot_evaluation_results(metrics, save_path=plot_path)
    logger.info(f"✅ Résultats d'évaluation: {plot_path}")
    
    # Distribution des actions
    action_plot_path = os.path.join(args.output_dir, "action_distribution.png")
    plot_action_distribution(action_counts, save_path=action_plot_path)
    logger.info(f"✅ Distribution des actions: {action_plot_path}")
    
    # Afficher le résumé
    logger.info("\n" + "="*80)
    logger.info("RÉSUMÉ DE L'ÉVALUATION")
    logger.info("="*80)
    logger.info(f"Récompense moyenne: {summary['mean_reward']:.2f} ± {summary['std_reward']:.2f}")
    logger.info(f"Coût moyen: {summary['mean_cost']:.2f} ± {summary['std_cost']:.2f}")
    logger.info(f"Conformité SLA: {summary['mean_sla_compliance']:.2%}")
    logger.info(f"\nDistribution des actions:")
    logger.info(f"  - CREATE_REPLICA: {summary['action_distribution']['CREATE_REPLICA']:.2%}")
    logger.info(f"  - DELETE_REPLICA: {summary['action_distribution']['DELETE_REPLICA']:.2%}")
    logger.info(f"  - DO_NOTHING: {summary['action_distribution']['DO_NOTHING']:.2%}")
    logger.info("="*80)
    
    logger.info("\n✅ Évaluation terminée avec succès!")
    logger.info(f"Résultats sauvegardés dans: {args.output_dir}/")


if __name__ == "__main__":
    main()
