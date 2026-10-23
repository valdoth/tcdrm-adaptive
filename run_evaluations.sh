#!/usr/bin/env bash
# Évaluations complémentaires sur les modèles DÉJÀ entraînés (aucun réentraînement) :
#   • benchmark (charge stable, 5 graines) en quatre variantes :
#       en_ligne      — protocole du mémoire (apprentissage en ligne pendant l'évaluation)
#       figee         — politique figée : agents ET méta-contrôleurs (audit 4.4)
#       sans_meta     — ablation : seuils fixés au contrat, méta-contrôleurs ignorés (audit 5.6)
#       stockage_x5   — sensibilité à la convention « 1000 requêtes = 1 mois » (audit 5.7)
#   • validation sur 10 graines (audit 5.5), six variantes (voir run_validation plus bas) :
#     charge variable en ligne et figée, rafales, ablations sans méta / sans bandit,
#     stockage ×5. Agrégat moyenne ± écart-type, IC 95 % et test de Wilcoxon apparié.
# Résultats : results/<variante>/ et results/validation/<mode>/ (+ agrégats moyenne/écart-type,
# figures de chaque graine dans results/validation/<mode>/images/seed_<graine>/).
#
# Usage : ./run_evaluations.sh            (après ./run_complete_workflow.sh --episodes N)
#         VALIDATION_SEEDS="777 778" ./run_evaluations.sh
#         SKIP_BENCHMARK=1 ./run_evaluations.sh   (validation seule)
#         VALIDATION_MODES="rafales sans_bandit" ./run_evaluations.sh
set -uo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
OUT="$ROOT/results"
VALIDATION_SEEDS="${VALIDATION_SEEDS:-777 778 779 780 781 782 783 784 785 786}"
mkdir -p "$OUT"

run_benchmark() {
    local name="$1"; shift
    echo "=== Benchmark : $name ==="
    rm -f metrics/summary_seeds*.csv metrics/summary_phase2_rl.csv
    env "$@" bash run_complete_workflow.sh --skip-training --skip-compile > "$OUT/benchmark_$name.log" 2>&1
    echo "exit=$?"
    mkdir -p "$OUT/$name"
    cp -f metrics/summary_seeds.csv metrics/summary_seeds_agg.csv metrics/summary_phase2_rl.csv "$OUT/$name/" 2>/dev/null
    # Figures du benchmark : images/ est vidé par chaque variante, on garde celles-ci.
    rm -rf "$OUT/$name/images"; mkdir -p "$OUT/$name/images"
    cp -f images/*.png "$OUT/$name/images/" 2>/dev/null
    column -s, -t < "$OUT/$name/summary_seeds_agg.csv" 2>/dev/null
}

mvn -q -DskipTests package || { echo "❌ compilation"; exit 1; }

# SKIP_BENCHMARK=1 : validation seule (benchmark déjà fait).
if [ "${SKIP_BENCHMARK:-0}" != 1 ]; then
    run_benchmark en_ligne    TCDRM_EVAL_ONLINE=1
    run_benchmark figee       TCDRM_EVAL_ONLINE=0
    run_benchmark sans_meta   TCDRM_EVAL_ONLINE=1 TCDRM_ABLATION_NO_META=1
    run_benchmark stockage_x5 TCDRM_EVAL_ONLINE=1 TCDRM_STORAGE_SCALE=5
fi

# Validation multi-graines. Chaque variante : <nom> puis les variables d'environnement.
#   en_ligne     — charge variable, apprentissage en ligne (protocole du mémoire)
#   figee        — charge variable, politique figée (audit 4.4)
#   rafales      — pics de popularité soudains : suppressions et anti-thrashing (audit 5.1, 5.2)
#   sans_meta    — charge variable, méta-contrôleurs et bandit désactivés (audit 5.6)
#   sans_bandit  — charge variable, ρ = 1, autres méta-contrôleurs actifs (audit 5.6)
#   stockage_x5  — charge variable, stockage ×5 (audit 5.7)
run_validation() {
    local mode="$1"; shift
    local dir="$OUT/validation/$mode"
    # Résultats d'un run PRÉCÉDENT effacés une fois, au début ; rien n'est supprimé
    # pendant le run (figures écrites directement dans images/seed_<graine>/).
    rm -rf "$dir"; mkdir -p "$dir"
    for seed in $VALIDATION_SEEDS; do
        echo "=== Validation : $mode, graine $seed ==="
        local imgdir="$dir/images/seed_$seed"
        mkdir -p "$imgdir"
        # 2e essai si le client Python ne s'est pas connecté à temps (échec ponctuel).
        for attempt in 1 2; do
            rm -f validation/metrics/summary_phase2_rl.csv
            (cd validation && env TCDRM_SEED="$seed" TCDRM_IMAGES_DIR="$imgdir" "$@" ./run.sh comparison) \
                > "$dir/run_$seed.log" 2>&1
            [ -f validation/metrics/summary_phase2_rl.csv ] && break
            echo "⚠️  pas de résumé pour la graine $seed (essai $attempt)"
        done
        cp -f validation/metrics/summary_phase2_rl.csv "$dir/seed_$seed.csv" 2>/dev/null \
            || echo "⚠️  graine $seed exclue de l'agrégat"
        # CSV par requête de chaque stratégie (écrasés par la graine suivante sinon).
        mkdir -p "$dir/metrics/seed_$seed"
        cp -f validation/metrics/rl_*.csv validation/metrics/baseline_*.csv "$dir/metrics/seed_$seed/" 2>/dev/null
    done
    python3 -I "$ROOT/tcdrm_gym/utils/aggregate_seeds.py" "$dir" > "$dir/aggregate.csv"
    python3 -I "$ROOT/tcdrm_gym/utils/stats_seeds.py" "$dir" > "$dir/stats.csv"
    echo "--- Validation $mode (moyenne ± écart-type sur les graines ; IC 95 % et Wilcoxon : stats.csv) ---"
    column -s, -t < "$dir/aggregate.csv"
}

VALIDATION_MODES="${VALIDATION_MODES:-en_ligne figee rafales sans_meta sans_bandit stockage_x5}"
for mode in $VALIDATION_MODES; do
    case "$mode" in
        en_ligne)    run_validation en_ligne    TCDRM_EVAL_ONLINE=1 ;;
        figee)       run_validation figee       TCDRM_EVAL_ONLINE=0 ;;
        rafales)     run_validation rafales     TCDRM_EVAL_ONLINE=1 TCDRM_WORKLOAD=burst ;;
        sans_meta)   run_validation sans_meta   TCDRM_EVAL_ONLINE=1 TCDRM_ABLATION_NO_META=1 ;;
        sans_bandit) run_validation sans_bandit TCDRM_EVAL_ONLINE=1 TCDRM_ABLATION_NO_BANDIT=1 ;;
        stockage_x5) run_validation stockage_x5 TCDRM_EVAL_ONLINE=1 TCDRM_STORAGE_SCALE=5 ;;
        # Charges qui sollicitent la suppression (audit 4.5, 5.2), jamais vues à l'entraînement :
        bascule)     run_validation bascule     TCDRM_EVAL_ONLINE=1 TCDRM_WORKLOAD=shift ;;
        alternance)  run_validation alternance  TCDRM_EVAL_ONLINE=1 TCDRM_WORKLOAD=alternate ;;
        alternance_sans_garde) run_validation alternance_sans_garde TCDRM_EVAL_ONLINE=1 TCDRM_WORKLOAD=alternate TCDRM_ABLATION_NO_DELETION_GUARD=1 ;;
        *) echo "⚠️  variante de validation inconnue : $mode" ;;
    esac
done
# Synthèse de toutes les variantes (tableau + figures) : results/synthese/
(cd "$ROOT/tcdrm_gym" && uv run python utils/synthese.py "$OUT") || echo "⚠️  synthèse non produite"
echo "✅ Évaluations terminées : $OUT"
