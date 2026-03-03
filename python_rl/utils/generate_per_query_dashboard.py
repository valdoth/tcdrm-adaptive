"""
Génère des dashboards de métriques par requête pour chaque modèle
Format: 8 graphes en grille 2x4 montrant l'évolution des métriques par requête
"""

import os
import argparse
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
from typing import Dict, List, Tuple
import json


def load_benchmark_data(results_dir: str, model_name: str) -> Dict:
    """
    Charge les données de benchmark pour un modèle.
    
    Args:
        results_dir: Répertoire des résultats
        model_name: Nom du modèle (norep, tcdrm_static, qlearning, dqn)
    
    Returns:
        Dictionnaire contenant les métriques par requête
    """
    # Chercher les fichiers de résultats
    json_files = list(Path(results_dir).glob(f"*{model_name}*.json"))
    
    if not json_files:
        print(f"⚠️  Aucun fichier de résultats trouvé pour {model_name}")
        return None
    
    # Prendre le plus récent
    latest_file = max(json_files, key=lambda p: p.stat().st_mtime)
    
    with open(latest_file, 'r') as f:
        data = json.load(f)
    
    return data


def create_dashboard(data: Dict, model_name: str, output_path: str):
    """
    Crée un dashboard 2x4 avec 8 graphes de métriques par requête.
    
    Args:
        data: Données du modèle
        model_name: Nom du modèle
        output_path: Chemin de sortie pour l'image
    """
    # Extraire les métriques par requête
    queries = data.get('query_numbers', [])
    
    # Métriques à afficher
    metrics = {
        'bw_inter_provider': data.get('bw_inter_provider_per_query', []),
        'bw_inter_region': data.get('bw_inter_region_per_query', []),
        'bw_total': data.get('bw_total_per_query', []),
        'cpu': data.get('cpu_per_query', []),
        'io': data.get('io_per_query', []),
        'response_time': data.get('response_time_per_query', []),
        'exec_time': data.get('exec_time_per_query', []),
        'total_cost': data.get('cost_per_query', [])
    }
    
    # Vérifier que les données existent
    if not queries or not any(metrics.values()):
        print(f"⚠️  Données insuffisantes pour {model_name}")
        return
    
    # Créer la figure avec 8 subplots (2 lignes x 4 colonnes)
    fig, axes = plt.subplots(2, 4, figsize=(20, 10))
    fig.suptitle(f'Dashboard per-query: {model_name.upper()}', 
                 fontsize=16, fontweight='bold', y=0.995)
    
    # Configuration des graphes
    graph_configs = [
        ('bw_inter_provider', 'BW inter-provider ($/query)', axes[0, 0], 'blue'),
        ('bw_inter_region', 'BW inter-region ($/query)', axes[0, 1], 'blue'),
        ('bw_total', 'BW total ($/query)', axes[0, 2], 'blue'),
        ('cpu', 'CPU ($/query)', axes[0, 3], 'blue'),
        ('io', 'IO ($/query)', axes[1, 0], 'blue'),
        ('response_time', 'Response time (ms)', axes[1, 1], 'blue'),
        ('exec_time', 'Exec time (ms)', axes[1, 2], 'blue'),
        ('total_cost', 'Total cost ($/query)', axes[1, 3], 'blue')
    ]
    
    for metric_key, title, ax, color in graph_configs:
        values = metrics.get(metric_key, [])
        
        if values and len(values) > 0:
            # Tracer le graphe en barres verticales
            ax.bar(queries, values, color=color, alpha=0.8, width=1.0, edgecolor='none')
            
            # Ajouter une ligne horizontale pour la moyenne (si pertinent)
            if metric_key == 'response_time' and 'tsla' in data:
                tsla = data['tsla']
                ax.axhline(y=tsla, color='orange', linestyle='--', linewidth=2, label=f'TSLA={tsla}')
                ax.legend(loc='upper right', fontsize=8)
            
            # Formater l'axe Y
            ax.ticklabel_format(style='scientific', axis='y', scilimits=(0,0))
            
        else:
            # Pas de données
            ax.text(0.5, 0.5, 'No data', ha='center', va='center', 
                   transform=ax.transAxes, fontsize=12, color='gray')
        
        # Titre et labels
        ax.set_title(title, fontsize=10, fontweight='bold')
        ax.set_xlabel('Query', fontsize=9)
        ax.grid(True, alpha=0.3, axis='y')
        
        # Limiter le nombre de ticks sur l'axe X
        if len(queries) > 20:
            step = len(queries) // 10
            ax.set_xticks(queries[::step])
    
    plt.tight_layout()
    plt.savefig(output_path, dpi=150, bbox_inches='tight')
    plt.close()
    
    print(f"✅ Dashboard généré: {output_path}")


def generate_all_dashboards(results_dir: str, output_dir: str):
    """
    Génère les dashboards pour tous les modèles.
    
    Args:
        results_dir: Répertoire des résultats
        output_dir: Répertoire de sortie pour les dashboards
    """
    os.makedirs(output_dir, exist_ok=True)
    
    models = {
        'norep': 'NOREP',
        'tcdrm_static': 'TCDRM Static',
        'qlearning': 'Q-Learning',
        'dqn': 'DQN'
    }
    
    print("="*80)
    print("GÉNÉRATION DES DASHBOARDS PER-QUERY")
    print("="*80)
    print()
    
    for model_key, model_display in models.items():
        print(f"📊 Traitement de {model_display}...")
        
        # Charger les données
        data = load_benchmark_data(results_dir, model_key)
        
        if data is None:
            print(f"   ⚠️  Aucune donnée trouvée pour {model_display}")
            continue
        
        # Générer le dashboard
        output_path = os.path.join(output_dir, f"dashboard_{model_key}.png")
        create_dashboard(data, model_display, output_path)
    
    print()
    print("="*80)
    print(f"✅ Dashboards générés dans: {output_dir}")
    print("="*80)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Génère des dashboards de métriques par requête')
    parser.add_argument('--results-dir', type=str, default='results',
                       help='Répertoire des résultats de benchmark')
    parser.add_argument('--output-dir', type=str, default='dashboards',
                       help='Répertoire de sortie pour les dashboards')
    
    args = parser.parse_args()
    
    generate_all_dashboards(args.results_dir, args.output_dir)
