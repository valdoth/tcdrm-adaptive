"""
Génération des graphes avec 4 courbes
Q-Learning Formel (243 états) + DQN + TCDRM Statique + NOREP

WRAPPER pour le script unifié - garantit la cohérence des résultats
"""

import sys
import os

# Importer le script unifié
from generate_graphs_unified import main as unified_main
from generate_graphs_unified import generate_graphs


def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Générer graphes 4 courbes')
    parser.add_argument('--qlearning-model', type=str, required=True)
    parser.add_argument('--dqn-model', type=str, default=None)
    parser.add_argument('--output-dir', type=str, default='../images')
    parser.add_argument('--seed', type=int, default=42)
    
    args = parser.parse_args()
    
    print("="*80)
    print("GÉNÉRATION DES GRAPHES AVEC 4 COURBES")
    print("Q-Learning Formel (243 états) + DQN + TCDRM Statique + NOREP")
    print("="*80)
    print(f"Seed: {args.seed} (pour reproductibilité)")
    print()
    
    models = {
        'qlearning': args.qlearning_model,
        'dqn': args.dqn_model,
        'ppo': None  # Pas de PPO pour 4 courbes
    }
    
    # R1
    generate_graphs("R1", 5.3, models, args.output_dir, args.seed, "4curves")
    
    # R2 avec seed différent pour workload différent
    generate_graphs("R2", 11.9, models, args.output_dir, args.seed + 100, "4curves")
    
    print()
    print("="*80)
    print("✅ GÉNÉRATION TERMINÉE")
    print("="*80)
    print()
    print(f"Graphes sauvegardés dans: {args.output_dir}")
    print()
    print("Voir les graphes:")
    print(f"  open {args.output_dir}/*_4curves*.png")


if __name__ == '__main__':
    main()
