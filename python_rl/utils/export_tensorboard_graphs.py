"""
Export automatique des graphes TensorBoard vers des images PNG
Ne garde que les derniers runs pour chaque algorithme
"""

import os
import argparse
from pathlib import Path
from datetime import datetime
import matplotlib.pyplot as plt
from tensorboard.backend.event_processing import event_accumulator
import numpy as np


def find_latest_runs(base_dir: str, max_runs: int = 1):
    """
    Trouve les derniers runs pour chaque algorithme.
    
    Args:
        base_dir: Répertoire de base des logs TensorBoard
        max_runs: Nombre maximum de runs à garder par algorithme
        
    Returns:
        Dict avec algorithme -> liste des chemins de runs
    """
    if not os.path.exists(base_dir):
        return {}
    
    runs_by_algo = {'qlearning': [], 'dqn': []}
    
    for entry in os.listdir(base_dir):
        run_path = os.path.join(base_dir, entry)
        if not os.path.isdir(run_path):
            continue
        
        # Déterminer l'algorithme
        algo = None
        if 'qlearning' in entry.lower():
            algo = 'qlearning'
        elif 'dqn' in entry.lower():
            algo = 'dqn'
        
        if algo:
            mtime = os.path.getmtime(run_path)
            runs_by_algo[algo].append((run_path, mtime))
    
    # Trier par date et garder les plus récents
    result = {}
    for algo, runs in runs_by_algo.items():
        if runs:
            runs.sort(key=lambda x: x[1], reverse=True)
            result[algo] = [r[0] for r in runs[:max_runs]]
    
    return result


def load_tensorboard_scalars(log_dir: str):
    """
    Charge les scalaires depuis les logs TensorBoard.
    
    Args:
        log_dir: Répertoire contenant les logs TensorBoard
        
    Returns:
        Dictionnaire avec tag -> {steps, values}
    """
    ea = event_accumulator.EventAccumulator(log_dir)
    ea.Reload()
    
    data = {}
    for tag in ea.Tags()['scalars']:
        events = ea.Scalars(tag)
        data[tag] = {
            'steps': [e.step for e in events],
            'values': [e.value for e in events]
        }
    
    return data


def export_training_overview(data: dict, output_path: str, algorithm: str):
    """
    Exporte un graphe d'aperçu de l'entraînement (reward, cost, SLA).
    
    Args:
        data: Données TensorBoard
        output_path: Chemin de sortie
        algorithm: Nom de l'algorithme
    """
    fig, axes = plt.subplots(1, 3, figsize=(15, 4))
    fig.suptitle(f'Aperçu Entraînement {algorithm.upper()}', fontsize=14, fontweight='bold')
    
    # Reward par épisode
    if 'Episode/Total_Reward' in data:
        axes[0].plot(data['Episode/Total_Reward']['steps'], 
                    data['Episode/Total_Reward']['values'], 
                    linewidth=1.5, color='#2E86AB')
        axes[0].set_title('Reward Total par Épisode')
        axes[0].set_xlabel('Épisode')
        axes[0].set_ylabel('Reward')
        axes[0].grid(True, alpha=0.3)
    
    # Coût par épisode
    if 'Episode/Total_Cost' in data:
        axes[1].plot(data['Episode/Total_Cost']['steps'], 
                    data['Episode/Total_Cost']['values'], 
                    linewidth=1.5, color='#A23B72')
        axes[1].set_title('Coût Total par Épisode')
        axes[1].set_xlabel('Épisode')
        axes[1].set_ylabel('Coût')
        axes[1].grid(True, alpha=0.3)
    
    # Violations SLA
    if 'Episode/SLA_Violations' in data:
        axes[2].plot(data['Episode/SLA_Violations']['steps'], 
                    data['Episode/SLA_Violations']['values'], 
                    linewidth=1.5, color='#F18F01')
        axes[2].set_title('Violations SLA par Épisode')
        axes[2].set_xlabel('Épisode')
        axes[2].set_ylabel('Violations')
        axes[2].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Graphe d'aperçu exporté: {output_path}")


def export_detailed_metrics(data: dict, output_path: str, algorithm: str):
    """
    Exporte un graphe détaillé avec toutes les métriques.
    
    Args:
        data: Données TensorBoard
        output_path: Chemin de sortie
        algorithm: Nom de l'algorithme
    """
    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    fig.suptitle(f'Métriques Détaillées - {algorithm.upper()}', fontsize=16, fontweight='bold')
    
    # 1. Reward par épisode
    if 'Episode/Total_Reward' in data:
        axes[0, 0].plot(data['Episode/Total_Reward']['values'], linewidth=0.8, alpha=0.7)
        axes[0, 0].set_title('Reward par Épisode')
        axes[0, 0].set_xlabel('Épisode')
        axes[0, 0].set_ylabel('Reward Total')
        axes[0, 0].grid(True, alpha=0.3)
    
    # 2. Coût par épisode
    if 'Episode/Total_Cost' in data:
        axes[0, 1].plot(data['Episode/Total_Cost']['values'], linewidth=0.8, alpha=0.7)
        axes[0, 1].set_title('Coût par Épisode')
        axes[0, 1].set_xlabel('Épisode')
        axes[0, 1].set_ylabel('Coût Total')
        axes[0, 1].grid(True, alpha=0.3)
    
    # 3. Violations SLA
    if 'Episode/SLA_Violations' in data:
        axes[0, 2].plot(data['Episode/SLA_Violations']['values'], linewidth=0.8, alpha=0.7)
        axes[0, 2].set_title('Violations SLA par Épisode')
        axes[0, 2].set_xlabel('Épisode')
        axes[0, 2].set_ylabel('Nombre de Violations')
        axes[0, 2].grid(True, alpha=0.3)
    
    # 4. Latence moyenne
    if 'Episode/Avg_Latency' in data:
        axes[1, 0].plot(data['Episode/Avg_Latency']['values'], linewidth=0.8, alpha=0.7)
        axes[1, 0].set_title('Latence Moyenne par Épisode')
        axes[1, 0].set_xlabel('Épisode')
        axes[1, 0].set_ylabel('Latence (ms)')
        axes[1, 0].grid(True, alpha=0.3)
    
    # 5. Loss (DQN uniquement) ou Epsilon (Q-Learning)
    if 'Training/Loss' in data:
        axes[1, 1].plot(data['Training/Loss']['values'], linewidth=0.8, alpha=0.7)
        axes[1, 1].set_title('Loss d\'Entraînement')
        axes[1, 1].set_xlabel('Update Step')
        axes[1, 1].set_ylabel('Loss')
        axes[1, 1].grid(True, alpha=0.3)
    elif 'Training/Epsilon' in data:
        axes[1, 1].plot(data['Training/Epsilon']['values'], linewidth=0.8, alpha=0.7)
        axes[1, 1].set_title('Epsilon (Exploration)')
        axes[1, 1].set_xlabel('Épisode')
        axes[1, 1].set_ylabel('Epsilon')
        axes[1, 1].grid(True, alpha=0.3)
    else:
        axes[1, 1].text(0.5, 0.5, 'N/A', ha='center', va='center', fontsize=12, color='gray')
        axes[1, 1].set_xticks([])
        axes[1, 1].set_yticks([])
    
    # 6. Reward lissé
    if 'Episode/Total_Reward' in data:
        rewards = data['Episode/Total_Reward']['values']
        window = min(10, len(rewards))
        if len(rewards) >= window:
            smoothed = np.convolve(rewards, np.ones(window)/window, mode='valid')
            axes[1, 2].plot(smoothed, linewidth=1.5)
            axes[1, 2].set_title(f'Reward Lissé (fenêtre={window})')
            axes[1, 2].set_xlabel('Épisode')
            axes[1, 2].set_ylabel('Reward')
            axes[1, 2].grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Graphe détaillé exporté: {output_path}")


def export_graphs(run_dir: str, output_dir: str, algo_name: str):
    """
    Exporte les graphes TensorBoard vers des images PNG.
    
    Args:
        run_dir: Répertoire du run TensorBoard
        output_dir: Répertoire de sortie pour les images
        algo_name: Nom de l'algorithme (qlearning ou dqn)
    """
    print(f"  📊 Export des graphes pour {algo_name}...")
    
    try:
        data = load_tensorboard_scalars(run_dir)
        
        if not data:
            print(f"    ⚠️  Aucune donnée trouvée dans {run_dir}")
            return
        
        # Créer le répertoire de sortie
        os.makedirs(output_dir, exist_ok=True)
        
        # Graphes principaux à exporter (Episode metrics)
        episode_graphs = [
            ('Episode/Total_Reward', 'Total Reward per Episode'),
            ('Episode/Avg_Latency', 'Average Latency per Episode'),
            ('Episode/Total_Cost', 'Total Cost per Episode'),
            ('Episode/Final_Budget', 'Final Budget per Episode'),
            ('Episode/Avg_Replicas', 'Average Replicas per Episode'),
            ('Episode/SLA_Violations', 'SLA Violations per Episode'),
            ('Training/Epsilon', 'Epsilon Decay'),
            ('Training/Loss', 'Training Loss'),
            ('Training/Q_Value_Mean', 'Mean Q-Value'),
            ('Training/States_Explored', 'States Explored'),
        ]
        
        for tag, title in episode_graphs:
            if tag in data:
                plt.figure(figsize=(10, 6))
                plt.plot(data[tag]['steps'], data[tag]['values'], linewidth=2)
                plt.title(f'{title} - {algo_name.upper()}', fontsize=14, fontweight='bold')
                plt.xlabel('Episode', fontsize=12)
                plt.ylabel(title, fontsize=12)
                plt.grid(True, alpha=0.3)
                
                # Nom de fichier
                filename = f"{algo_name}_{tag.replace('/', '_').lower()}.png"
                filepath = os.path.join(output_dir, filename)
                
                plt.savefig(filepath, dpi=150, bbox_inches='tight')
                plt.close()
                
                print(f"    ✅ {filename}")
        
        # Graphes Step metrics (si disponibles)
        step_graphs = [
            ('Step/Reward', 'Reward per Step'),
            ('Step/Latency', 'Latency per Step'),
            ('Step/Cost', 'Cost per Step'),
            ('Step/Budget', 'Budget per Step'),
            ('Step/Replicas', 'Replicas per Step'),
            ('Step/Action', 'Action per Step'),
            ('Step/SLA_Violations', 'SLA Violations per Step'),
        ]
        
        for tag, title in step_graphs:
            if tag in data:
                plt.figure(figsize=(12, 6))
                steps = data[tag]['steps']
                values = data[tag]['values']
                
                # Sous-échantillonner si trop de points
                if len(steps) > 10000:
                    indices = np.linspace(0, len(steps)-1, 10000, dtype=int)
                    steps = [steps[i] for i in indices]
                    values = [values[i] for i in indices]
                
                plt.plot(steps, values, linewidth=1, alpha=0.7)
                plt.title(f'{title} - {algo_name.upper()}', fontsize=14, fontweight='bold')
                plt.xlabel('Global Step', fontsize=12)
                plt.ylabel(title, fontsize=12)
                plt.grid(True, alpha=0.3)
                
                filename = f"{algo_name}_{tag.replace('/', '_').lower()}.png"
                filepath = os.path.join(output_dir, filename)
                
                plt.savefig(filepath, dpi=150, bbox_inches='tight')
                plt.close()
                
                print(f"    ✅ {filename}")
        
        # Graphe combiné des métriques principales (Episode)
        fig, axes = plt.subplots(2, 3, figsize=(18, 10))
        fig.suptitle(f'Training Metrics (Episode) - {algo_name.upper()}', fontsize=16, fontweight='bold')
        
        episode_metrics = [
            ('Episode/Total_Reward', 'Total Reward', axes[0, 0]),
            ('Episode/Total_Cost', 'Total Cost', axes[0, 1]),
            ('Episode/Avg_Latency', 'Avg Latency', axes[0, 2]),
            ('Episode/SLA_Violations', 'SLA Violations', axes[1, 0]),
            ('Episode/Avg_Replicas', 'Avg Replicas', axes[1, 1]),
            ('Training/Epsilon', 'Epsilon' if 'Training/Epsilon' in data else 'Loss', axes[1, 2])
        ]
        
        # Utiliser Loss pour DQN si Epsilon n'est pas disponible
        if 'Training/Loss' in data and 'Training/Epsilon' not in data:
            episode_metrics[-1] = ('Training/Loss', 'Loss', axes[1, 2])
        
        for tag, title, ax in episode_metrics:
            if tag in data:
                ax.plot(data[tag]['steps'], data[tag]['values'], linewidth=2)
                ax.set_title(title, fontweight='bold')
                ax.set_xlabel('Episode')
                ax.grid(True, alpha=0.3)
            else:
                ax.text(0.5, 0.5, 'No data', ha='center', va='center', transform=ax.transAxes)
                ax.set_title(title, fontweight='bold')
        
        plt.tight_layout()
        combined_filename = f"{algo_name}_combined_episode_metrics.png"
        combined_filepath = os.path.join(output_dir, combined_filename)
        plt.savefig(combined_filepath, dpi=150, bbox_inches='tight')
        plt.close()
        
        print(f"    ✅ {combined_filename}")
        
        # Graphe combiné des métriques Step (si disponibles)
        step_tags = [tag for tag in data.keys() if tag.startswith('Step/')]
        if step_tags:
            fig, axes = plt.subplots(2, 3, figsize=(18, 10))
            fig.suptitle(f'Training Metrics (Step) - {algo_name.upper()}', fontsize=16, fontweight='bold')
            
            step_metrics = [
                ('Step/Reward', 'Reward', axes[0, 0]),
                ('Step/Cost', 'Cost', axes[0, 1]),
                ('Step/Latency', 'Latency', axes[0, 2]),
                ('Step/Budget', 'Budget', axes[1, 0]),
                ('Step/Replicas', 'Replicas', axes[1, 1]),
                ('Step/Action', 'Action', axes[1, 2])
            ]
            
            for tag, title, ax in step_metrics:
                if tag in data:
                    steps = data[tag]['steps']
                    values = data[tag]['values']
                    
                    # Sous-échantillonner si trop de points
                    if len(steps) > 5000:
                        indices = np.linspace(0, len(steps)-1, 5000, dtype=int)
                        steps = [steps[i] for i in indices]
                        values = [values[i] for i in indices]
                    
                    ax.plot(steps, values, linewidth=1, alpha=0.7)
                    ax.set_title(title, fontweight='bold')
                    ax.set_xlabel('Global Step')
                    ax.grid(True, alpha=0.3)
                else:
                    ax.text(0.5, 0.5, 'No data', ha='center', va='center', transform=ax.transAxes)
                    ax.set_title(title, fontweight='bold')
            
            plt.tight_layout()
            combined_step_filename = f"{algo_name}_combined_step_metrics.png"
            combined_step_filepath = os.path.join(output_dir, combined_step_filename)
            plt.savefig(combined_step_filepath, dpi=150, bbox_inches='tight')
            plt.close()
            
            print(f"    ✅ {combined_step_filename}")
        
    except Exception as e:
        print(f"    ❌ Erreur lors de l'export: {e}")


def export_tensorboard_graphs(tensorboard_dir: str, output_dir: str, max_runs: int = 1):
    """
    Exporte les graphes TensorBoard pour les derniers runs.
    
    Args:
        tensorboard_dir: Répertoire des logs TensorBoard
        output_dir: Répertoire de sortie pour les images
        max_runs: Nombre de runs à garder par algorithme
    """
    os.makedirs(output_dir, exist_ok=True)
    
    # Trouver les derniers runs
    latest_runs = find_latest_runs(tensorboard_dir, max_runs)
    
    if not latest_runs:
        print("⚠️  Aucun run TensorBoard trouvé")
        return
    
    # Exporter pour chaque algorithme
    for algo, runs in latest_runs.items():
        if not runs:
            continue
        
        print(f"\n📊 Export des graphes {algo.upper()}...")
        
        for i, run_path in enumerate(runs):
            print(f"  • Chargement des données depuis: {run_path}")
            
            try:
                export_graphs(run_path, output_dir, algo)
                
            except Exception as e:
                print(f"    ⚠️  Erreur lors de l'export: {e}")
    
    print(f"\n✅ Export TensorBoard terminé dans: {output_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Export automatique des graphes TensorBoard')
    parser.add_argument('--tensorboard-dir', type=str, default='runs',
                       help='Répertoire des logs TensorBoard')
    parser.add_argument('--output-dir', type=str, default='tensorboard_exports',
                       help='Répertoire de sortie pour les images')
    parser.add_argument('--max-runs', type=int, default=1,
                       help='Nombre de runs à garder par algorithme')
    
    args = parser.parse_args()
    
    export_tensorboard_graphs(args.tensorboard_dir, args.output_dir, args.max_runs)
