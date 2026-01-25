#!/usr/bin/env python3
"""
Script d'entraînement Q-Learning pour TCDRM-ADAPTIVE v2.0
Mécanisme adaptatif auto-apprenant pour la réplication multi-cloud
"""

import argparse
import os
import yaml
import numpy as np
from datetime import datetime

from agents.tabular_qlearning import TabularQLearningAgent
from envs.tcdrm_env import TcdrmAdaptiveEnv
from utils.logger import setup_logger
from utils.metrics import MetricsTracker
from utils.visualization import plot_training_results

logger = setup_logger("TCDRM-Training", "logs")


def load_config(config_path):
    """Charge la configuration depuis un fichier YAML"""
    with open(config_path, 'r') as f:
        return yaml.safe_load(f)


def train_qlearning(args):
    """
    Entraîne un agent Q-Learning pour TCDRM-ADAPTIVE
    
    Objectif: Apprendre automatiquement quand créer/supprimer des réplicas
    pour optimiser le coût tout en respectant les SLA
    """
    logger.info("="*80)
    logger.info("TCDRM-ADAPTIVE v2.0: Entraînement Q-Learning")
    logger.info("Mécanisme Adaptatif Auto-Apprenant pour Réplication Multi-Cloud")
    logger.info("="*80)
    
    # Charger la configuration
    if args.config:
        config_file = load_config(args.config)
        logger.info(f"Configuration chargée depuis: {args.config}")
        # Extraire les valeurs du YAML
        config = {
            'alpha': config_file['training']['learning_rate'],
            'gamma': config_file['training']['discount_factor'],
            'epsilon_start': config_file['training']['epsilon'],
            'epsilon_decay': config_file['training']['epsilon_decay'],
            'epsilon_min': config_file['training']['epsilon_min'],
            'data_gb': args.data_gb if args.data_gb else config_file['environment']['data_gb'],
            'episodes': args.episodes if args.episodes else config_file['training']['n_episodes'],
        }
    else:
        config = {
            'alpha': args.alpha,
            'gamma': args.gamma,
            'epsilon_start': args.epsilon,
            'epsilon_decay': args.epsilon_decay,
            'epsilon_min': args.epsilon_min,
            'data_gb': args.data_gb,
            'episodes': args.episodes,
        }
    
    # Créer l'environnement Gymnasium
    logger.info(f"\n>>> Création de l'environnement TCDRM ({config['data_gb']} GB)")
    env = TcdrmAdaptiveEnv(data_gb=config['data_gb'])
    
    logger.info(f"Espace d'états: {env.observation_space}")
    logger.info(f"Espace d'actions: {env.action_space}")
    logger.info(f"États totaux: {env.get_state_space_size()}")
    logger.info(f"Actions totales: {env.get_action_space_size()}")
    
    # Créer l'agent Q-Learning
    logger.info("\n>>> Création de l'agent Q-Learning")
    agent = TabularQLearningAgent(
        n_states=env.get_state_space_size(),
        n_actions=env.get_action_space_size(),
        learning_rate=config['alpha'],
        discount_factor=config['gamma'],
        epsilon=config['epsilon_start'],
        epsilon_decay=config['epsilon_decay'],
        epsilon_min=config['epsilon_min']
    )
    
    logger.info(f"Hyperparamètres:")
    logger.info(f"  - Alpha (learning rate): {config['alpha']}")
    logger.info(f"  - Gamma (discount): {config['gamma']}")
    logger.info(f"  - Epsilon (exploration): {config['epsilon_start']} → {config['epsilon_min']}")
    logger.info(f"  - Epsilon decay: {config['epsilon_decay']}")
    
    # Créer le répertoire de sortie
    output_dir = args.output_dir
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(os.path.join(output_dir, "models"), exist_ok=True)
    os.makedirs(os.path.join(output_dir, "plots"), exist_ok=True)
    os.makedirs(os.path.join(output_dir, "metrics"), exist_ok=True)
    
    # Entraînement avec la méthode intégrée de l'agent
    logger.info(f"\n>>> Entraînement pour {config['episodes']} épisodes")
    logger.info("Objectif: Apprendre à décider QUAND répliquer pour optimiser coût/SLA")
    logger.info("")
    
    training_stats = agent.train(
        env=env,
        n_episodes=config['episodes'],
        max_steps_per_episode=1000,
        eval_freq=args.log_interval,
        verbose=True
    )
    
    # Sauvegarder le modèle
    model_path = os.path.join(output_dir, "models", "best_model.pkl")
    agent.save(model_path)
    logger.info(f"\n✅ Modèle sauvegardé: {model_path}")
    
    # Sauvegarder les métriques d'entraînement
    import pandas as pd
    metrics_df = pd.DataFrame({
        'episode': range(len(training_stats['episode_rewards'])),
        'reward': training_stats['episode_rewards'],
        'length': training_stats['episode_lengths']
    })
    metrics_path = os.path.join(output_dir, "metrics", "training_metrics.csv")
    metrics_df.to_csv(metrics_path, index=False)
    logger.info(f"✅ Métriques sauvegardées: {metrics_path}")
    
    # Générer les visualisations
    logger.info("\n>>> Génération des visualisations...")
    plot_path = os.path.join(output_dir, "plots", "training_curves.png")
    try:
        plot_training_results(metrics_path, save_path=plot_path)
        logger.info(f"✅ Courbes d'apprentissage: {plot_path}")
    except Exception as e:
        logger.warning(f"⚠️  Impossible de générer les courbes: {e}")
    
    # Résumé final
    logger.info("\n" + "="*80)
    logger.info("RÉSUMÉ DE L'ENTRAÎNEMENT")
    logger.info("="*80)
    logger.info(f"Récompense moyenne: {np.mean(training_stats['episode_rewards']):.2f} ± {np.std(training_stats['episode_rewards']):.2f}")
    logger.info(f"Récompense finale (derniers 100): {np.mean(training_stats['episode_rewards'][-100:]):.2f}")
    logger.info(f"Epsilon final: {training_stats['final_epsilon']:.4f}")
    logger.info("="*80)
    
    logger.info("\n✅ Entraînement terminé avec succès!")
    logger.info(f"Modèle sauvegardé dans: {output_dir}/models/")
    logger.info(f"Métriques dans: {output_dir}/metrics/")
    
    return agent, training_stats


def main():
    parser = argparse.ArgumentParser(
        description='Entraînement Q-Learning pour TCDRM-ADAPTIVE v2.0'
    )
    
    # Configuration
    parser.add_argument('--config', type=str, default='configs/qlearning_config.yaml',
                       help='Chemin vers le fichier de configuration YAML')
    
    # Hyperparamètres Q-Learning
    parser.add_argument('--alpha', type=float, default=0.1,
                       help='Taux d\'apprentissage (learning rate)')
    parser.add_argument('--gamma', type=float, default=0.95,
                       help='Facteur de discount')
    parser.add_argument('--epsilon', type=float, default=1.0,
                       help='Epsilon initial (exploration)')
    parser.add_argument('--epsilon-decay', type=float, default=0.995,
                       help='Décroissance de l\'epsilon')
    parser.add_argument('--epsilon-min', type=float, default=0.01,
                       help='Epsilon minimal')
    
    # Environnement
    parser.add_argument('--data-gb', type=float, default=5.3,
                       help='Taille des données en GB')
    parser.add_argument('--episodes', type=int, default=1000,
                       help='Nombre d\'épisodes d\'entraînement')
    
    # Logging et sauvegarde
    parser.add_argument('--output-dir', type=str, default='results/qlearning',
                       help='Répertoire de sortie')
    parser.add_argument('--log-interval', type=int, default=50,
                       help='Intervalle d\'affichage des logs')
    
    args = parser.parse_args()
    
    # Créer un sous-répertoire avec timestamp
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    args.output_dir = os.path.join(args.output_dir, f"run_{timestamp}")
    
    # Entraîner
    train_qlearning(args)


if __name__ == "__main__":
    main()
