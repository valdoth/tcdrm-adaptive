#!/usr/bin/env python3
"""
Génère les images PNG des diagrammes Mermaid
Utilise playwright pour rendre les diagrammes via mermaid.ink
"""

import os
import re
import base64
import urllib.parse
import requests
from pathlib import Path

# Configuration
DIAGRAMS_FILE = "workflow_diagrams.md"
OUTPUT_DIR = "docs/diagrams"

def extract_mermaid_diagrams(md_file):
    """Extrait tous les diagrammes Mermaid d'un fichier Markdown"""
    with open(md_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Trouver tous les blocs mermaid
    pattern = r'```mermaid\n(.*?)\n```'
    diagrams = re.findall(pattern, content, re.DOTALL)
    
    # Extraire les titres (sections ##)
    sections = re.findall(r'^## (.+)$', content, re.MULTILINE)
    
    return list(zip(sections, diagrams))

def sanitize_filename(title):
    """Convertit un titre en nom de fichier valide"""
    title = re.sub(r'^\d+\.\s*', '', title)
    title = re.sub(r'[^\w\s-]', '', title)
    title = re.sub(r'\s+', '_', title)
    return title.lower()

def generate_diagram_via_mermaid_ink(diagram_code, output_path, title):
    """Génère une image PNG via mermaid.ink"""
    try:
        # Encoder le diagramme en base64
        encoded = base64.b64encode(diagram_code.encode('utf-8')).decode('utf-8')
        
        # URL du service mermaid.ink
        url = f"https://mermaid.ink/img/{encoded}"
        
        # Télécharger l'image
        response = requests.get(url, timeout=30)
        
        if response.status_code == 200:
            with open(output_path, 'wb') as f:
                f.write(response.content)
            print(f"✅ Généré: {output_path}")
            return True
        else:
            print(f"❌ Erreur HTTP {response.status_code} pour {title}")
            return False
            
    except Exception as e:
        print(f"❌ Erreur pour {title}: {e}")
        return False

def main():
    print("="*70)
    print("Génération des Diagrammes Workflow TCDRM-ADAPTIVE")
    print("Méthode: mermaid.ink (service en ligne)")
    print("="*70)
    print()
    
    # Créer le répertoire de sortie
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"📁 Répertoire de sortie: {OUTPUT_DIR}")
    
    # Extraire les diagrammes
    print(f"\n📖 Lecture de {DIAGRAMS_FILE}...")
    diagrams = extract_mermaid_diagrams(DIAGRAMS_FILE)
    print(f"✅ {len(diagrams)} diagrammes trouvés")
    
    # Générer chaque diagramme
    print("\n🎨 Génération des images via mermaid.ink...")
    success_count = 0
    
    for i, (title, code) in enumerate(diagrams, 1):
        filename = f"{i:02d}_{sanitize_filename(title)}.png"
        output_path = os.path.join(OUTPUT_DIR, filename)
        
        print(f"\n[{i}/{len(diagrams)}] {title}")
        if generate_diagram_via_mermaid_ink(code, output_path, title):
            success_count += 1
    
    # Résumé
    print("\n" + "="*70)
    print(f"✅ Génération terminée: {success_count}/{len(diagrams)} réussis")
    print("="*70)
    print(f"\nImages disponibles dans: {OUTPUT_DIR}/")
    
    # Créer un fichier Markdown avec les images
    create_visual_workflow_with_images(diagrams)

def create_visual_workflow_with_images(diagrams):
    """Crée WORKFLOW_VISUAL.md avec les images générées"""
    
    content = """# 📊 Workflow Visuel TCDRM-ADAPTIVE v2.0

Diagrammes visuels du workflow complet (Architecture Python-Java)

---

"""
    
    for i, (title, code) in enumerate(diagrams, 1):
        filename = f"{i:02d}_{sanitize_filename(title)}.png"
        
        content += f"## {title}\n\n"
        content += f"![{title}](docs/diagrams/{filename})\n\n"
        content += f"<details>\n<summary>Code Mermaid</summary>\n\n"
        content += f"```mermaid\n{code}\n```\n\n"
        content += f"</details>\n\n---\n\n"
    
    # Sauvegarder
    with open("WORKFLOW_VISUAL.md", 'w', encoding='utf-8') as f:
        f.write(content)
    
    print("\n✅ WORKFLOW_VISUAL.md mis à jour avec les images")

if __name__ == "__main__":
    main()
