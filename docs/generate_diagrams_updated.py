#!/usr/bin/env python3
"""
Script pour générer les diagrammes TCDRM-ADAPTIVE mis à jour via Mermaid.ink
"""

import os
import re
import urllib.parse
import urllib.request
from pathlib import Path

def extract_mermaid_diagrams(md_file):
    """Extrait les diagrammes Mermaid d'un fichier Markdown"""
    with open(md_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Pattern pour extraire les blocs mermaid
    pattern = r'```mermaid\n(.*?)```'
    diagrams = re.findall(pattern, content, re.DOTALL)
    
    # Extraire aussi les titres
    titles = re.findall(r'## (\d+)\. (.+)', content)
    
    return diagrams, titles

def generate_diagram_url(mermaid_code):
    """Génère l'URL Mermaid.ink pour un diagramme"""
    # Encoder le code Mermaid en base64
    import base64
    import json
    
    # Créer la configuration Mermaid
    config = {
        "code": mermaid_code,
        "mermaid": {
            "theme": "default"
        }
    }
    
    # Encoder en JSON puis en base64
    json_str = json.dumps(config)
    encoded = base64.urlsafe_b64encode(json_str.encode('utf-8')).decode('utf-8')
    
    # URL de l'API Mermaid.ink
    url = f"https://mermaid.ink/img/{encoded}"
    
    return url

def download_diagram(url, output_path):
    """Télécharge un diagramme depuis Mermaid.ink"""
    try:
        print(f"  Téléchargement: {output_path}")
        urllib.request.urlretrieve(url, output_path)
        print(f"  ✓ Sauvegardé")
        return True
    except Exception as e:
        print(f"  ✗ Erreur: {e}")
        return False

def main():
    print("="*80)
    print("GÉNÉRATION DES DIAGRAMMES TCDRM-ADAPTIVE MIS À JOUR")
    print("="*80)
    print()
    
    # Chemins
    script_dir = Path(__file__).parent
    md_file = script_dir / "workflow_diagrams_updated.md"
    output_dir = script_dir / "diagrams"
    
    # Créer le répertoire de sortie
    output_dir.mkdir(exist_ok=True)
    
    # Extraire les diagrammes
    print(f"Lecture de {md_file}...")
    diagrams, titles = extract_mermaid_diagrams(md_file)
    
    print(f"✓ {len(diagrams)} diagrammes trouvés")
    print()
    
    # Générer chaque diagramme
    for i, (diagram, (num, title)) in enumerate(zip(diagrams, titles), 1):
        print(f"Diagramme {i}/{len(diagrams)}: {title}")
        
        # Nettoyer le titre pour le nom de fichier
        filename = f"{num.zfill(2)}_{title.lower().replace(' ', '_').replace('(', '').replace(')', '')}.png"
        output_path = output_dir / filename
        
        # Générer l'URL
        url = generate_diagram_url(diagram)
        
        # Télécharger
        success = download_diagram(url, output_path)
        
        if success:
            print(f"  ✓ {filename}")
        else:
            print(f"  ✗ Échec pour {filename}")
        
        print()
    
    print("="*80)
    print("✅ GÉNÉRATION TERMINÉE")
    print("="*80)
    print()
    print(f"Diagrammes sauvegardés dans: {output_dir}")
    print()
    print("Note: Si certains diagrammes n'ont pas été générés,")
    print("vous pouvez les générer manuellement sur https://mermaid.live")

if __name__ == '__main__':
    main()
