#!/bin/bash
# Script pour générer les diagrammes TCDRM-ADAPTIVE 2024 avec Mermaid CLI

set -e

echo "================================================================================"
echo "  GÉNÉRATION DES DIAGRAMMES TCDRM-ADAPTIVE 2024"
echo "================================================================================"
echo ""

# Vérifier si mmdc est installé
if ! command -v mmdc &> /dev/null; then
    echo "❌ Mermaid CLI (mmdc) n'est pas installé"
    echo ""
    echo "Installation:"
    echo "  npm install -g @mermaid-js/mermaid-cli"
    echo ""
    echo "Ou utilisez Docker:"
    echo "  docker pull minlag/mermaid-cli"
    echo ""
    exit 1
fi

echo "✓ Mermaid CLI trouvé"
echo ""

# Répertoires
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MD_FILE="$SCRIPT_DIR/workflow_diagrams_2024.md"
OUTPUT_DIR="$SCRIPT_DIR/diagrams"

# Créer le répertoire de sortie
mkdir -p "$OUTPUT_DIR"

# Supprimer les anciens diagrammes obsolètes (avec PPO)
echo "🧹 Nettoyage des anciens diagrammes obsolètes..."
rm -f "$OUTPUT_DIR/01_architecture_globale_tcdrm-adaptive_3_modèles_rl.png"
rm -f "$OUTPUT_DIR/04_processus_de_décision_multi-modèles.png"
rm -f "$OUTPUT_DIR/08_métriques_comparatives_5_modèles.png"
echo "  ✓ Anciens diagrammes supprimés"
echo ""

# Extraire et générer chaque diagramme
echo "📊 Génération des diagrammes depuis $MD_FILE..."
echo ""

# Créer un fichier temporaire pour chaque diagramme
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Extraire les diagrammes Mermaid
awk '
/^## [0-9]+\. / {
    title = $0
    gsub(/^## [0-9]+\. /, "", title)
    num = $2
    gsub(/\./, "", num)
    next
}
/^```mermaid$/ {
    in_mermaid = 1
    mermaid_content = ""
    next
}
in_mermaid && /^```$/ {
    in_mermaid = 0
    if (mermaid_content != "") {
        # Nettoyer le titre pour le nom de fichier
        filename = sprintf("%02d", num) "_" tolower(title)
        gsub(/ /, "_", filename)
        gsub(/[()]/, "", filename)
        gsub(/é/, "e", filename)
        gsub(/è/, "e", filename)
        gsub(/ê/, "e", filename)
        gsub(/î/, "i", filename)
        gsub(/à/, "a", filename)
        gsub(/ô/, "o", filename)
        gsub(/û/, "u", filename)
        gsub(/ç/, "c", filename)
        gsub(/\+/, "", filename)
        gsub(/%/, "", filename)
        gsub(/→/, "", filename)
        # Filet de securite : supprime tout caractere restant hors [a-z0-9_-]
        gsub(/[^a-z0-9_-]/, "", filename)
        gsub(/_+/, "_", filename)
        gsub(/_$/, "", filename)

        # Écrire le fichier .mmd
        mmd_file = "'$TEMP_DIR'/" filename ".mmd"
        print mermaid_content > mmd_file
        close(mmd_file)
        
        # Générer le PNG
        png_file = "'$OUTPUT_DIR'/" filename ".png"
        cmd = "mmdc -i " mmd_file " -o " png_file " -b transparent 2>&1"
        print "Génération: " filename ".png"
        system(cmd)
    }
    next
}
in_mermaid {
    mermaid_content = mermaid_content $0 "\n"
}
' "$MD_FILE"

echo ""
echo "================================================================================"
echo "  ✅ GÉNÉRATION TERMINÉE"
echo "================================================================================"
echo ""
echo "Diagrammes générés dans: $OUTPUT_DIR"
echo ""
ls -1 "$OUTPUT_DIR"/*.png 2>/dev/null | wc -l | xargs echo "Total:"
echo ""
