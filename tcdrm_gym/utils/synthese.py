"""Synthèse de toutes les évaluations d'un dossier results/ : un tableau et des figures
qui rassemblent benchmark (charge stable) et validations (charge variable, rafales,
ablations, stockage ×5), au lieu de les chercher variante par variante.

Entrées : results/<variante>/summary_seeds_agg.csv (benchmark),
          results/validation/<variante>/aggregate.csv et stats.csv (validation).
Sorties : results/synthese/SYNTHESE.md, synthese_validation.csv, synthese_benchmark.csv,
          synthese_validation_cout.png, synthese_validation_violations.png,
          synthese_ablation.png, synthese_benchmark_cout.png

Usage : python utils/synthese.py <dossier results>
"""
import csv
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

STRATEGIES = ["TCDRM", "Glouton", "QLearning", "Rainbow"]
LABELS = {"TCDRM": "TCDRM", "Glouton": "Glouton", "QLearning": "Q-Learning", "Rainbow": "Rainbow DQN"}
# Palette catégorielle validée (ordre fixe ; contraste < 3:1 pour les deux derniers,
# d'où les valeurs écrites sur chaque barre et le tableau SYNTHESE.md).
COLORS = {"TCDRM": "#2a78d6", "Glouton": "#eb6834", "QLearning": "#1baf7a", "Rainbow": "#eda100"}
INK, INK_2, GRID = "#1f1f1e", "#5f5e58", "#e4e3df"

VALIDATIONS = [
	("en_ligne", "Charge variable, apprentissage en ligne (protocole du mémoire)"),
	("figee", "Charge variable, politique figée (audit 4.4)"),
	("rafales", "Pics de popularité soudains (audit 5.1, 5.2)"),
	("sans_bandit", "Ablation : ρ = 1, autres méta-contrôleurs actifs (audit 5.6)"),
	("sans_meta", "Ablation : méta-contrôleurs et bandit désactivés (audit 5.6)"),
	("stockage_x5", "Charge variable, coût de stockage ×5 (audit 5.7)"),
]
BENCHMARKS = [
	("en_ligne", "Charge stable, apprentissage en ligne"),
	("figee", "Charge stable, politique figée"),
	("sans_meta", "Charge stable, sans méta-contrôleurs"),
	("stockage_x5", "Charge stable, stockage ×5"),
]
REGIMES = [("Simple", "Simple"), ("Complex", "Complexe")]


def read_csv(path):
	if not os.path.isfile(path):
		return []
	with open(path, newline="") as fh:
		return list(csv.DictReader(fh))


def load_validation(root):
	rows = []
	for variant, _ in VALIDATIONS:
		folder = os.path.join(root, "validation", variant)
		agg = {r["name"]: r for r in read_csv(os.path.join(folder, "aggregate.csv"))}
		stats = read_csv(os.path.join(folder, "stats.csv"))
		for regime, _ in REGIMES:
			for s in STRATEGIES:
				r = agg.get(f"{s}_{regime}")
				if r is None:
					continue
				ci = next((x for x in stats if x["name"] == f"{s}_{regime}" and x["metric"] == "total_cost"), None)
				p = {x["vs"]: x["wilcoxon_p"] for x in stats
				     if x["name"] == f"{s}_{regime}" and x["metric"] == "total_cost" and x["vs"]}
				g = lambda k: float(r.get(k, 0) or 0)
				rows.append({
					"variante": variant, "regime": regime, "strategie": s, "graines": r["seeds"],
					"cout": g("total_cost_mean"), "cout_std": g("total_cost_std"),
					"ic95_bas": float(ci["ci95_low"]) if ci else g("total_cost_mean"),
					"ic95_haut": float(ci["ci95_high"]) if ci else g("total_cost_mean"),
					"viol_tsla": g("tsla_contract_violations_mean"),
					"viol_csla": g("csla_contract_violations_mean"),
					"premier_replica": g("first_replica_query_mean"),
					"creations": g("replica_creations_mean"), "suppressions": g("replica_deletions_mean"),
					"oscillations": g("thrash_events_mean"),
					"p_vs_tcdrm": p.get("TCDRM", ""), "p_vs_glouton": p.get("Glouton", ""),
				})
	return rows


def load_benchmark(root):
	rows = []
	for variant, _ in BENCHMARKS:
		for r in read_csv(os.path.join(root, variant, "summary_seeds_agg.csv")):
			if r["strategy"] not in STRATEGIES:
				continue
			g = lambda k: float(r.get(k, 0) or 0)
			rows.append({
				"variante": variant, "regime": r["regime"], "strategie": r["strategy"], "graines": r["seeds"],
				"cout": g("cost_mean"), "cout_std": g("cost_std"),
				"viol_tsla": g("tsla_viol_mean"), "viol_csla": g("csla_viol_mean"),
				"premier_replica": g("first_replica_mean"), "creations": g("creations_mean"),
				"suppressions": g("deletions_mean"),
			})
	return rows


def write_csv(path, rows):
	if not rows:
		return
	with open(path, "w", newline="") as fh:
		w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
		w.writeheader()
		w.writerows(rows)


def style(ax):
	for side in ("top", "right"):
		ax.spines[side].set_visible(False)
	for side in ("left", "bottom"):
		ax.spines[side].set_color(GRID)
	ax.tick_params(colors=INK_2, labelsize=9)
	ax.yaxis.grid(True, color=GRID, linewidth=0.8)
	ax.set_axisbelow(True)


def grouped_bars(rows, variants, metric, ylabel, title, path, err=True, fmt="{:.2f}"):
	"""Une colonne de panneaux par régime ; groupes = variantes, barres = stratégies."""
	# Panneaux empilés (un par régime) : six variantes côte à côte restent lisibles.
	fig, axes = plt.subplots(2, 1, figsize=(max(8, 1.9 * len(variants)), 8.6))
	width = 0.8 / len(STRATEGIES)
	for ax, (regime, regime_fr) in zip(axes, REGIMES):
		for i, s in enumerate(STRATEGIES):
			xs, ys, lo, hi = [], [], [], []
			for j, (v, _) in enumerate(variants):
				r = next((x for x in rows if x["variante"] == v and x["regime"] == regime and x["strategie"] == s), None)
				if r is None:
					continue
				xs.append(j + (i - (len(STRATEGIES) - 1) / 2) * width)
				ys.append(r[metric])
				lo.append(r[metric] - r.get("ic95_bas", r[metric]))
				hi.append(r.get("ic95_haut", r[metric]) - r[metric])
			if not xs:
				continue
			bars = ax.bar(xs, ys, width * 0.92, color=COLORS[s], label=LABELS[s],
			              edgecolor="white", linewidth=1)
			if err and metric == "cout":
				ax.errorbar(xs, ys, yerr=[lo, hi], fmt="none", ecolor=INK_2, elinewidth=1, capsize=2)
			tops = [y + (h if err and metric == "cout" else 0) for y, h in zip(ys, hi)]
			for b, y, top in zip(bars, ys, tops):
				ax.annotate(fmt.format(y), (b.get_x() + b.get_width() / 2, top),
				            xytext=(0, 2), textcoords="offset points",
				            ha="center", va="bottom", fontsize=7, color=INK)
		ax.set_xticks(range(len(variants)))
		ax.set_xticklabels([v for v, _ in variants], fontsize=9, color=INK)
		ax.set_title(regime_fr, fontsize=11, color=INK)
		ax.set_ylabel(ylabel, color=INK_2, fontsize=9)
		ax.margins(y=0.18)
		style(ax)
	handles, labels = axes[0].get_legend_handles_labels()
	fig.suptitle(title, fontsize=12, color=INK)
	fig.tight_layout(rect=(0, 0, 1, 0.93))
	fig.legend(handles, labels, loc="upper center", bbox_to_anchor=(0.5, 0.955),
	           ncol=len(STRATEGIES), frameon=False, fontsize=9)
	fig.savefig(path, dpi=150, bbox_inches="tight")
	plt.close(fig)


def markdown(val, bench, path):
	lines = ["# Synthèse des évaluations", "",
	         "Coût total en $ (moyenne ± écart-type sur les graines) ; violations T_SLA et C_SLA "
	         "du contrat ; p = test de Wilcoxon apparié sur le coût (agents seulement).", ""]
	for variant, desc in VALIDATIONS:
		sub = [r for r in val if r["variante"] == variant]
		if not sub:
			continue
		lines += [f"## Validation — {variant}", "", desc, "",
		          "| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica "
		          "| Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |",
		          "|---|---|---|---|---|---|---|---|---|---|---|---|"]
		for r in sub:
			lines.append(f"| {r['regime']} | {LABELS[r['strategie']]} | {r['cout']:.2f} ± {r['cout_std']:.2f} "
			             f"| [{r['ic95_bas']:.2f} ; {r['ic95_haut']:.2f}] | {r['viol_tsla']:.1f} | {r['viol_csla']:.1f} "
			             f"| {r['premier_replica']:.0f} | {r['creations']:.1f} | {r['suppressions']:.1f} "
			             f"| {r['oscillations']:.1f} | {r['p_vs_tcdrm']} | {r['p_vs_glouton']} |")
		lines.append("")
	for variant, desc in BENCHMARKS:
		sub = [r for r in bench if r["variante"] == variant]
		if not sub:
			continue
		lines += [f"## Benchmark — {variant}", "", desc, "",
		          "| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |",
		          "|---|---|---|---|---|---|---|---|"]
		for r in sub:
			lines.append(f"| {r['regime']} | {LABELS[r['strategie']]} | {r['cout']:.2f} ± {r['cout_std']:.2f} "
			             f"| {r['viol_tsla']:.1f} | {r['viol_csla']:.1f} | {r['premier_replica']:.0f} "
			             f"| {r['creations']:.1f} | {r['suppressions']:.1f} |")
		lines.append("")
	lines += ["## Figures", "",
	          "- `synthese_validation_cout.png` : coût de chaque stratégie dans les six validations (IC 95 %).",
	          "- `synthese_validation_violations.png` : violations T_SLA du contrat dans les six validations.",
	          "- `synthese_ablation.png` : part du gain due au bandit ρ et aux méta-contrôleurs.",
	          "- `synthese_benchmark_cout.png` : coût au benchmark en charge stable.",
	          "- Par graine : `validation/<variante>/images/seed_<graine>/` (dont `fig8_budget_csla.png` "
	          "et `metrics_*.png`) ; CSV par requête : `validation/<variante>/metrics/seed_<graine>/`.", ""]
	with open(path, "w") as fh:
		fh.write("\n".join(lines))


def main(root):
	out = os.path.join(root, "synthese")
	os.makedirs(out, exist_ok=True)
	val, bench = load_validation(root), load_benchmark(root)
	write_csv(os.path.join(out, "synthese_validation.csv"), val)
	write_csv(os.path.join(out, "synthese_benchmark.csv"), bench)
	markdown(val, bench, os.path.join(out, "SYNTHESE.md"))
	present = [v for v in VALIDATIONS if any(r["variante"] == v[0] for r in val)]
	if present:
		grouped_bars(val, present, "cout", "Coût total ($)",
		             "Validation : coût total par variante (moyenne et IC 95 %, 10 graines)",
		             os.path.join(out, "synthese_validation_cout.png"))
		grouped_bars(val, present, "viol_tsla", "Violations T_SLA (contrat)",
		             "Validation : violations du T_SLA du contrat par variante",
		             os.path.join(out, "synthese_validation_violations.png"), err=False, fmt="{:.0f}")
		abl = [v for v in present if v[0] in ("en_ligne", "sans_bandit", "sans_meta")]
		if len(abl) > 1:
			grouped_bars(val, abl, "cout", "Coût total ($)",
			             "Ablation : agent complet, sans bandit ρ, sans méta-contrôleurs (charge variable)",
			             os.path.join(out, "synthese_ablation.png"))
	bpresent = [v for v in BENCHMARKS if any(r["variante"] == v[0] for r in bench)]
	if bpresent:
		grouped_bars(bench, bpresent, "cout", "Coût total ($)",
		             "Benchmark (charge stable, 5 graines) : coût total par variante",
		             os.path.join(out, "synthese_benchmark_cout.png"), err=False)
	print(f"Synthèse : {out}")


if __name__ == "__main__":
	main(sys.argv[1])
