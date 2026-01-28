"""
Génération des graphes avec 3 courbes
Q-Learning Formel (243 états) + TCDRM Statique + NOREP

Alternative Python au script Java TcdrmArticleAllGraphs3CurvesWithPy4J
Utilise le script unifié pour garantir la cohérence des résultats
"""

import sys
import os

# Importer le script unifié
from generate_graphs_unified import main as unified_main
from generate_graphs_unified import generate_graphs


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Générer graphes 3 courbes')
    parser.add_argument('--qlearning-model', type=str, required=True,
                       help='Chemin vers le modèle Q-Learning')
    parser.add_argument('--output-dir', type=str, default='../images',
                       help='Répertoire de sortie')
    parser.add_argument('--seed', type=int, default=42,
                       help='Seed pour reproductibilité')
    
    args = parser.parse_args()
    
    print("="*80)
    print("GÉNÉRATION DES GRAPHES AVEC 3 COURBES")
    print("Q-Learning Formel (243 états) + TCDRM Statique + NOREP")
    print("="*80)
    print(f"Seed: {args.seed} (pour reproductibilité)")
    print()
    
    models = {
        'qlearning': args.qlearning_model,
        'dqn': None,  # Pas de DQN pour 3 courbes
        'ppo': None   # Pas de PPO pour 3 courbes
    }
    
    # R1
    generate_graphs("R1", 5.3, models, args.output_dir, args.seed, "3curves")
    
    # R2 avec seed différent pour workload différent
    generate_graphs("R2", 11.9, models, args.output_dir, args.seed + 100, "3curves")
    
    print()
    print("="*80)
    print("✅ GÉNÉRATION TERMINÉE")
    print("="*80)
    print()
    print(f"Graphes sauvegardés dans: {args.output_dir}")
    print()
    print("Voir les graphes:")
    print(f"  open {args.output_dir}/*_3curves*.png")


if __name__ == '__main__':
    main()
