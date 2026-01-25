#!/usr/bin/env python3
"""
Exporte la Q-table Python vers un fichier JSON pour Java
"""

import argparse
import json
import pickle
import numpy as np
from pathlib import Path

def export_qtable_to_json(model_path: str, output_path: str):
    """Exporte la Q-table d'un modèle pickle vers JSON"""
    
    print(f"📦 Chargement du modèle: {model_path}")
    
    # Charger le modèle pickle
    with open(model_path, 'rb') as f:
        model = pickle.load(f)
    
    # Le modèle peut être un objet ou un dictionnaire
    if isinstance(model, dict):
        q_table = model['q_table']
        state_space_raw = model.get('state_space', 108)
        action_space = model.get('action_space', 3)
    else:
        q_table = model.q_table
        state_space_raw = model.state_space
        action_space = model.action_space
    
    # Calculer le nombre total d'états
    if isinstance(state_space_raw, tuple):
        # state_space est (3, 3, 3, 4) -> 3*3*3*4 = 108
        state_space = int(np.prod(state_space_raw))
    else:
        state_space = int(state_space_raw)
    
    model_info = f"Q-Learning Model (States: {state_space}, Actions: {action_space})"
    
    print(f"✅ Modèle chargé:")
    print(f"   - Q-Table shape: {q_table.shape}")
    print(f"   - State space: {state_space}")
    print(f"   - Action space: {action_space}")
    print()
    
    # Convertir la Q-table numpy en liste Python
    q_table_list = q_table.tolist()
    
    # Créer le dictionnaire à exporter
    data = {
        "model_info": model_info,
        "state_space": state_space,
        "action_space": int(action_space),
        "q_table": q_table_list,
        "q_table_shape": list(q_table.shape)
    }
    
    # Sauvegarder en JSON
    print(f"💾 Sauvegarde vers: {output_path}")
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_file, 'w') as f:
        json.dump(data, f, indent=2)
    
    print(f"✅ Q-table exportée avec succès!")
    print(f"   - Fichier: {output_path}")
    print(f"   - Taille: {output_file.stat().st_size / 1024:.2f} KB")
    print()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Exporte la Q-table Python vers JSON pour Java")
    parser.add_argument("--model", required=True, help="Chemin vers le modèle pickle")
    parser.add_argument("--output", default="qtable_export.json", help="Chemin du fichier JSON de sortie")
    
    args = parser.parse_args()
    
    export_qtable_to_json(args.model, args.output)
