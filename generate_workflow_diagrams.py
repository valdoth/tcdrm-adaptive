#!/usr/bin/env python3
"""
Génère les images PNG des diagrammes Mermaid du workflow
Utilise mmdc (mermaid-cli) pour convertir Mermaid en PNG
"""

import subprocess
import os
import re
from pathlib import Path

# Configuration
DIAGRAMS_FILE = "workflow_diagrams.md"
OUTPUT_DIR = "docs/diagrams"
MERMAID_CLI = "mmdc"  # Nécessite: npm install -g @mermaid-js/mermaid-cli

def check_mermaid_cli():
    """Vérifie que mermaid-cli est installé"""
    try:
        result = subprocess.run([MERMAID_CLI, "--version"], 
                              capture_output=True, text=True)
        print(f"✅ Mermaid CLI trouvé: {result.stdout.strip()}")
        return True
    except FileNotFoundError:
        print("❌ Mermaid CLI non trouvé")
        print("\nInstallation requise:")
        print("  npm install -g @mermaid-js/mermaid-cli")
        print("\nOu utilisez Docker:")
        print("  docker pull minlag/mermaid-cli")
        return False

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

def generate_diagram_image(diagram_code, output_path, title):
    """Génère une image PNG depuis du code Mermaid"""
    # Créer un fichier temporaire avec le code Mermaid
    temp_file = "temp_diagram.mmd"
    with open(temp_file, 'w', encoding='utf-8') as f:
        f.write(diagram_code)
    
    try:
        # Générer l'image avec mmdc
        cmd = [
            MERMAID_CLI,
            "-i", temp_file,
            "-o", output_path,
            "-b", "transparent",
            "-w", "1200",
            "-H", "800"
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True)
        
        if result.returncode == 0:
            print(f"✅ Généré: {output_path}")
            return True
        else:
            print(f"❌ Erreur pour {title}:")
            print(result.stderr)
            return False
    finally:
        # Nettoyer le fichier temporaire
        if os.path.exists(temp_file):
            os.remove(temp_file)

def sanitize_filename(title):
    """Convertit un titre en nom de fichier valide"""
    # Retirer le numéro et nettoyer
    title = re.sub(r'^\d+\.\s*', '', title)
    # Remplacer les caractères spéciaux
    title = re.sub(r'[^\w\s-]', '', title)
    # Remplacer les espaces par des underscores
    title = re.sub(r'\s+', '_', title)
    return title.lower()

def main():
    print("="*70)
    print("Génération des Diagrammes Workflow TCDRM-ADAPTIVE")
    print("="*70)
    print()
    
    # Vérifier mermaid-cli
    if not check_mermaid_cli():
        print("\n⚠️  Génération annulée - Installez mermaid-cli d'abord")
        return
    
    # Créer le répertoire de sortie
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"\n📁 Répertoire de sortie: {OUTPUT_DIR}")
    
    # Extraire les diagrammes
    print(f"\n📖 Lecture de {DIAGRAMS_FILE}...")
    diagrams = extract_mermaid_diagrams(DIAGRAMS_FILE)
    print(f"✅ {len(diagrams)} diagrammes trouvés")
    
    # Générer chaque diagramme
    print("\n🎨 Génération des images...")
    success_count = 0
    
    for i, (title, code) in enumerate(diagrams, 1):
        filename = f"{i:02d}_{sanitize_filename(title)}.png"
        output_path = os.path.join(OUTPUT_DIR, filename)
        
        print(f"\n[{i}/{len(diagrams)}] {title}")
        if generate_diagram_image(code, output_path, title):
            success_count += 1
    
    # Résumé
    print("\n" + "="*70)
    print(f"✅ Génération terminée: {success_count}/{len(diagrams)} réussis")
    print("="*70)
    print(f"\nImages disponibles dans: {OUTPUT_DIR}/")
    print("\nUtilisation dans Markdown:")
    print(f"  ![Titre](docs/diagrams/01_architecture_globale.png)")

if __name__ == "__main__":
    main()
