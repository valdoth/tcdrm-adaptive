#!/usr/bin/env bash
# Évaluations complémentaires sur les modèles DÉJÀ entraînés (aucun réentraînement) :
#   • benchmark (charge stable, 5 graines) en quatre variantes :
#       en_ligne      — protocole du mémoire (apprentissage en ligne pendant l'évaluation)
#       figee         — politique figée : agents ET méta-contrôleurs (audit 4.4)
#       sans_meta     — ablation : seuils fixés au contrat, méta-contrôleurs ignorés (audit 5.6)
#       stockage_x5   — sensibilité à la convention « 1000 requêtes = 1 mois » (audit 5.7)
#   • validation (charge variable) sur plusieurs graines, en ligne et figée (audit 5.5).
# Résultats : results/<variante>/ et results/validation/<mode>/ (+ agrégats moyenne/écart-type).
#
# Usage : ./run_evaluations.sh            (après ./run_complete_workflow.sh --episodes N)
#         VALIDATION_SEEDS="777 778" ./run_evaluations.sh
set -uo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
OUT="$ROOT/results"
VALIDATION_SEEDS="${VALIDATION_SEEDS:-777 778 779 780 781}"
mkdir -p "$OUT"

run_benchmark() {
    local name="$1"; shift
    echo "=== Benchmark : $name ==="
    rm -f metrics/summary_seeds*.csv metrics/summary_phase2_rl.csv
    env "$@" bash run_complete_workflow.sh --skip-training --skip-compile > "$OUT/benchmark_$name.log" 2>&1
    echo "exit=$?"
    mkdir -p "$OUT/$name"
    cp -f metrics/summary_seeds.csv metrics/summary_seeds_agg.csv metrics/summary_phase2_rl.csv "$OUT/$name/" 2>/dev/null
    column -s, -t < "$OUT/$name/summary_seeds_agg.csv" 2>/dev/null
}

mvn -q -DskipTests package || { echo "❌ compilation"; exit 1; }

run_benchmark en_ligne    TCDRM_EVAL_ONLINE=1
run_benchmark figee       TCDRM_EVAL_ONLINE=0
run_benchmark sans_meta   TCDRM_EVAL_ONLINE=1 TCDRM_ABLATION_NO_META=1
run_benchmark stockage_x5 TCDRM_EVAL_ONLINE=1 TCDRM_STORAGE_SCALE=5

for mode in en_ligne figee; do
    online=1; [ "$mode" = figee ] && online=0
    mkdir -p "$OUT/validation/$mode"
    for seed in $VALIDATION_SEEDS; do
        echo "=== Validation : $mode, graine $seed ==="
        rm -f validation/metrics/summary_phase2_rl.csv
        (cd validation && TCDRM_SEED="$seed" TCDRM_EVAL_ONLINE="$online" ./run.sh comparison) \
            > "$OUT/validation/$mode/run_$seed.log" 2>&1
        cp -f validation/metrics/summary_phase2_rl.csv "$OUT/validation/$mode/seed_$seed.csv" 2>/dev/null \
            || echo "⚠️  pas de résumé pour la graine $seed"
    done
    python3 -I "$ROOT/tcdrm_gym/utils/aggregate_seeds.py" "$OUT/validation/$mode" \
        > "$OUT/validation/$mode/aggregate.csv"
    echo "--- Validation $mode (moyenne ± écart-type sur les graines) ---"
    column -s, -t < "$OUT/validation/$mode/aggregate.csv"
done
echo "✅ Évaluations terminées : $OUT"
