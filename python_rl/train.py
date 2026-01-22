#!/usr/bin/env python3
"""
Script d'entraînement Q-Learning pour TCDRM-ADAPTIVE v2.0
Mécanisme adaptatif auto-apprenant pour la réplication multi-cloud
"""

import argparse
import os
import yaml
from datetime import datetime

from agents.tabular_qlearning import TabularQLearningAgent
from envs.tcdrm_env import TcdrmEnv
from utils.logger import setup_logger
from utils.metrics import MetricsTracker
from utils.visualization import plot_training_results, plot_qtable_heatmap

logger = setup_logger("TCDRM-Training")


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
        config = load_config(args.config)
        logger.info(f"Configuration chargée depuis: {args.config}")
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
    env = TcdrmEnv(data_gb=config['data_gb'])
    
    logger.info(f"Espace d'états: {env.observation_space}")
    logger.info(f"Espace d'actions: {env.action_space}")
    logger.info(f"États totaux: {env.get_state_space_size()}")
    logger.info(f"Actions totales: {env.get_action_space_size()}")
    
    # Créer l'agent Q-Learning
    logger.info("\n>>> Création de l'agent Q-Learning")
    agent = TabularQLearningAgent(
        state_space_size=env.get_state_space_size(),
        action_space_size=env.get_action_space_size(),
        alpha=config['alpha'],
        gamma=config['gamma'],
        epsilon=config['epsilon_start'],
        epsilon_decay=config['epsilon_decay'],
        epsilon_min=config['epsilon_min']
    )
    
    logger.info(f"Hyperparamètres:")
    logger.info(f"  - Alpha (learning rate): {config['alpha']}")
    logger.info(f"  - Gamma (discount): {config['gamma']}")
    logger.info(f"  - Epsilon (exploration): {config['epsilon_start']} → {config['epsilon_min']}")
    logger.info(f"  - Epsilon decay: {config['epsilon_decay']}")
    
    # Créer le tracker de métriques
    metrics = MetricsTracker()
    
    # Créer le répertoire de sortie
    output_dir = args.output_dir
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(os.path.join(output_dir, "models"), exist_ok=True)
    os.makedirs(os.path.join(output_dir, "plots"), exist_ok=True)
    
    # Entraînement
    logger.info(f"\n>>> Entraînement pour {config['episodes']} épisodes")
    logger.info("Objectif: Apprendre à décider QUAND répliquer pour optimiser coût/SLA")
    logger.info("")
    
    best_reward = float('-inf')
    
    for episode in range(config['episodes']):
        state, _ = env.reset()
        episode_reward = 0
        episode_cost = 0
        episode_sla_violations = 0
        done = False
        step = 0
        
        while not done:
            # Choisir une action (exploration vs exploitation)
            action = agent.choose_action(state)
            
            # Exécuter l'action
            next_state, reward, terminated, truncated, info = env.step(action)
            done = terminated or truncated
            
            # Mettre à jour la Q-Table (apprentissage)
            agent.update(state, action, reward, next_state, done)
            
            # Accumuler les métriques
            episode_reward += reward
            episode_cost += info.get('cost', 0)
            if info.get('sla_violation', False):
                episode_sla_violations += 1
            
            state = next_state
            step += 1
        
        # Décroissance de l'exploration
        agent.decay_epsilon()
        
        # Enregistrer les métriques
        metrics.record_episode(
            episode=episode,
            reward=episode_reward,
            cost=episode_cost,
            sla_compliance=1.0 - (episode_sla_violations / max(step, 1)),
            steps=step
        )
        
        # Affichage périodique
        if (episode + 1) % args.log_interval == 0:
            avg_reward = metrics.get_average_reward(window=args.log_interval)
            avg_cost = metrics.get_average_cost(window=args.log_interval)
            avg_sla = metrics.get_average_sla_compliance(window=args.log_interval)
            
            logger.info(
                f"Épisode {episode + 1}/{config['episodes']} | "
                f"Récompense: {episode_reward:.2f} (avg: {avg_reward:.2f}) | "
                f"Coût: {episode_cost:.2f} | "
                f"SLA: {avg_sla:.2%} | "
                f"ε: {agent.epsilon:.3f}"
            )
        
        # Sauvegarder le meilleur modèle
        if episode_reward > best_reward:
            best_reward = episode_reward
            model_path = os.path.join(output_dir, "models", "best_model.pkl")
            agent.save(model_path)
            if (episode + 1) % 100 == 0:
                logger.info(f"  → Nouveau meilleur modèle sauvegardé: {model_path}")
    
    # Sauvegarder le modèle final
    final_model_path = os.path.join(output_dir, "models", "final_model.pkl")
    agent.save(final_model_path)
    logger.info(f"\n✅ Modèle final sauvegardé: {final_model_path}")
    
    # Sauvegarder les métriques
    metrics_path = os.path.join(output_dir, "training_metrics.csv")
    metrics.save_to_csv(metrics_path)
    logger.info(f"✅ Métriques sauvegardées: {metrics_path}")
    
    # Générer les visualisations
    logger.info("\n>>> Génération des visualisations...")
    
    # Courbes d'apprentissage
    plot_path = os.path.join(output_dir, "plots", "training_curves.png")
    plot_training_results(metrics, save_path=plot_path)
    logger.info(f"✅ Courbes d'apprentissage: {plot_path}")
    
    # Heatmap de la Q-Table
    heatmap_path = os.path.join(output_dir, "plots", "qtable_heatmap.png")
    plot_qtable_heatmap(agent.q_table, save_path=heatmap_path)
    logger.info(f"✅ Heatmap Q-Table: {heatmap_path}")
    
    # Résumé final
    logger.info("\n" + "="*80)
    logger.info("RÉSUMÉ DE L'ENTRAÎNEMENT")
    logger.info("="*80)
    summary = metrics.get_summary()
    logger.info(f"Récompense moyenne: {summary['mean_reward']:.2f} ± {summary['std_reward']:.2f}")
    logger.info(f"Coût moyen: {summary['mean_cost']:.2f} ± {summary['std_cost']:.2f}")
    logger.info(f"Conformité SLA moyenne: {summary['mean_sla_compliance']:.2%}")
    logger.info(f"Meilleure récompense: {best_reward:.2f}")
    logger.info("="*80)
    
    logger.info("\n✅ Entraînement terminé avec succès!")
    logger.info(f"Modèles sauvegardés dans: {output_dir}/models/")
    logger.info(f"Visualisations dans: {output_dir}/plots/")
    
    return agent, metrics


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
