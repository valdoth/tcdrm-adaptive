"""
Génère des graphes de métriques d'entraînement pour Q-Learning et DQN
Compatible avec les logs TensorBoard
"""

import os
import matplotlib.pyplot as plt
import numpy as np
from typing import Dict, List, Optional
from torch.utils.tensorboard import SummaryWriter
from tensorboard.backend.event_processing import event_accumulator


def load_tensorboard_data(log_dir: str) -> Dict[str, List]:
    """
    Charge les données depuis les logs TensorBoard.
    
    Args:
        log_dir: Répertoire contenant les logs TensorBoard
        
    Returns:
        Dictionnaire contenant les métriques par tag
    """
    ea = event_accumulator.EventAccumulator(log_dir)
    ea.Reload()
    
    data = {}
    
    # Charger tous les scalaires disponibles
    for tag in ea.Tags()['scalars']:
        events = ea.Scalars(tag)
        data[tag] = {
            'steps': [e.step for e in events],
            'values': [e.value for e in events]
        }
    
    return data


def plot_training_metrics_from_dict(metrics: Dict, output_path: str, algorithm: str = "RL"):
    """
    Génère des graphes de métriques d'entraînement à partir d'un dictionnaire.
    
    Args:
        metrics: Dictionnaire contenant les métriques
        output_path: Chemin de sortie pour l'image
        algorithm: Nom de l'algorithme (Q-Learning ou DQN)
    """
    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    fig.suptitle(f'Métriques d\'entraînement {algorithm}', fontsize=16)
    
    # 1. Reward par épisode (ligne 0, col 0)
    if 'episode_rewards' in metrics:
        axes[0, 0].plot(metrics['episode_rewards'], linewidth=0.5)
        axes[0, 0].set_title('Reward par épisode')
        axes[0, 0].set_xlabel('Épisode')
        axes[0, 0].set_ylabel('Reward total')
        axes[0, 0].grid(True, alpha=0.3)
    
    # 2. Coût par épisode (ligne 0, col 1)
    if 'episode_costs' in metrics:
        axes[0, 1].plot(metrics['episode_costs'], linewidth=0.5)
        axes[0, 1].set_title('Coût par épisode')
        axes[0, 1].set_xlabel('Épisode')
        axes[0, 1].set_ylabel('Coût total')
        axes[0, 1].grid(True, alpha=0.3)
    
    # 3. Violations SLA par épisode (ligne 0, col 2)
    if 'episode_sla_violations' in metrics:
        axes[0, 2].plot(metrics['episode_sla_violations'], linewidth=0.5)
        axes[0, 2].set_title('Violations SLA par épisode')
        axes[0, 2].set_xlabel('Épisode')
        axes[0, 2].set_ylabel('Nombre de violations')
        axes[0, 2].grid(True, alpha=0.3)
    
    # 4. Changements de réplicas (ligne 1, col 0)
    if 'episode_replica_changes' in metrics:
        axes[1, 0].plot(metrics['episode_replica_changes'], linewidth=0.5)
        axes[1, 0].set_title('Changements de réplicas par épisode')
        axes[1, 0].set_xlabel('Épisode')
        axes[1, 0].set_ylabel('Nombre de changements')
        axes[1, 0].grid(True, alpha=0.3)
    
    # 5. Loss d'entraînement (ligne 1, col 1) - DQN uniquement
    if 'losses' in metrics and len(metrics['losses']) > 0:
        axes[1, 1].plot(metrics['losses'], linewidth=0.5)
        axes[1, 1].set_title('Loss d\'entraînement')
        axes[1, 1].set_xlabel('Update step')
        axes[1, 1].set_ylabel('Loss')
        axes[1, 1].grid(True, alpha=0.3)
    else:
        axes[1, 1].text(0.5, 0.5, 'N/A\n(Q-Learning n\'a pas de Loss)', 
                       ha='center', va='center', fontsize=12, color='gray')
        axes[1, 1].set_xticks([])
        axes[1, 1].set_yticks([])
    
    # 6. Reward lissé (ligne 1, col 2)
    if 'episode_rewards' in metrics:
        window = 10
        if len(metrics['episode_rewards']) >= window:
            smoothed = np.convolve(metrics['episode_rewards'], 
                                  np.ones(window)/window, mode='valid')
            axes[1, 2].plot(smoothed, linewidth=1.5)
            axes[1, 2].set_title(f'Reward lissé (fenêtre={window})')
            axes[1, 2].set_xlabel('Épisode')
            axes[1, 2].set_ylabel('Reward')
            axes[1, 2].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Graphes de métriques {algorithm} sauvegardés: {output_path}")


def plot_training_metrics_from_tensorboard(log_dir: str, output_path: str, algorithm: str = "RL"):
    """
    Génère des graphes de métriques d'entraînement depuis les logs TensorBoard.
    
    Args:
        log_dir: Répertoire contenant les logs TensorBoard
        output_path: Chemin de sortie pour l'image
        algorithm: Nom de l'algorithme (Q-Learning ou DQN)
    """
    try:
        data = load_tensorboard_data(log_dir)
        
        # Extraire les métriques pertinentes
        metrics = {}
        
        # Reward par épisode
        if 'Episode/Total_Reward' in data:
            metrics['episode_rewards'] = data['Episode/Total_Reward']['values']
        
        # Coût par épisode
        if 'Episode/Total_Cost' in data:
            metrics['episode_costs'] = data['Episode/Total_Cost']['values']
        
        # Violations SLA
        if 'Episode/SLA_Violations' in data:
            metrics['episode_sla_violations'] = data['Episode/SLA_Violations']['values']
        
        # Changements de réplicas (depuis Episode/Replica_Changes ou approximation)
        if 'Episode/Replica_Changes' in data:
            # Utiliser directement la métrique si disponible (nouveau tracking)
            metrics['episode_replica_changes'] = data['Episode/Replica_Changes']['values']
        elif 'Episode/Avg_Replicas' in data:
            # Sinon, approximation depuis Avg_Replicas (ancien comportement)
            replicas = data['Episode/Avg_Replicas']['values']
            changes = [0]
            for i in range(1, len(replicas)):
                if abs(replicas[i] - replicas[i-1]) > 0.1:
                    changes.append(changes[-1] + 1)
                else:
                    changes.append(changes[-1])
            metrics['episode_replica_changes'] = changes
        
        # Loss (DQN uniquement)
        if 'Training/Loss' in data:
            metrics['losses'] = data['Training/Loss']['values']
        
        plot_training_metrics_from_dict(metrics, output_path, algorithm)
        
    except Exception as e:
        print(f"⚠️  Erreur lors du chargement des données TensorBoard: {e}")
        print(f"   Utilisation des métriques par défaut")


def find_latest_tensorboard_run(base_dir: str, algorithm: str) -> Optional[str]:
    """
    Trouve le dernier run TensorBoard pour un algorithme donné.
    
    Args:
        base_dir: Répertoire de base des logs
        algorithm: Nom de l'algorithme (qlearning ou dqn)
        
    Returns:
        Chemin vers le dernier run ou None
    """
    if not os.path.exists(base_dir):
        return None
    
    runs = []
    for entry in os.listdir(base_dir):
        if algorithm.lower() in entry.lower():
            run_path = os.path.join(base_dir, entry)
            if os.path.isdir(run_path):
                runs.append((run_path, os.path.getmtime(run_path)))
    
    if not runs:
        return None
    
    # Trier par date de modification (plus récent en premier)
    runs.sort(key=lambda x: x[1], reverse=True)
    return runs[0][0]


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Génère des graphes de métriques d\'entraînement')
    parser.add_argument('--tensorboard-dir', type=str, default='runs', 
                       help='Répertoire des logs TensorBoard')
    parser.add_argument('--algorithm', type=str, choices=['qlearning', 'dqn'], required=True,
                       help='Algorithme (qlearning ou dqn)')
    parser.add_argument('--output', type=str, required=True,
                       help='Chemin de sortie pour l\'image')
    
    args = parser.parse_args()
    
    # Trouver le dernier run
    run_dir = find_latest_tensorboard_run(args.tensorboard_dir, args.algorithm)
    
    if run_dir:
        print(f"📊 Génération des graphes depuis: {run_dir}")
        plot_training_metrics_from_tensorboard(run_dir, args.output, args.algorithm.upper())
    else:
        print(f"⚠️  Aucun run TensorBoard trouvé pour {args.algorithm}")
