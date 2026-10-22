"""Statistiques inter-graines d'un dossier de validation (seed_<graine>.csv), audit 5.5 :
intervalle de confiance à 95 % de la moyenne (loi de Student, n−1 degrés de liberté) et
test de Wilcoxon apparié (signé des rangs, bilatéral, exact) entre chaque agent et les
références TCDRM et Glouton, graine par graine.

Usage : python3 stats_seeds.py <dossier>   → CSV sur la sortie standard
"""
import csv
import glob
import itertools
import math
import os
import statistics
import sys

METRICS = ["total_cost", "tsla_contract_violations", "csla_contract_violations"]
AGENTS = ["QLearning", "Rainbow"]
REFERENCES = ["TCDRM", "Glouton"]
# Quantiles t de Student bilatéraux à 95 % (ddl 1 à 30).
T95 = [12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179,
       2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064,
       2.060, 2.056, 2.052, 2.048, 2.045, 2.042]


def wilcoxon_exact(diffs):
	"""p-valeur bilatérale exacte du test des rangs signés (différences nulles écartées)."""
	d = [x for x in diffs if x != 0]
	n = len(d)
	if n == 0:
		return 1.0
	order = sorted(range(n), key=lambda i: abs(d[i]))
	ranks = [0.0] * n
	i = 0
	while i < n:  # rangs moyens en cas d'ex æquo
		j = i
		while j + 1 < n and abs(d[order[j + 1]]) == abs(d[order[i]]):
			j += 1
		for k in range(i, j + 1):
			ranks[order[k]] = (i + j) / 2 + 1
		i = j + 1
	w_plus = sum(r for r, x in zip(ranks, d) if x > 0)
	total = sum(ranks)
	extreme = min(w_plus, total - w_plus)
	count = sum(1 for signs in itertools.product((0, 1), repeat=n)
	            if min(s := sum(r for r, b in zip(ranks, signs) if b), total - s) <= extreme + 1e-9)
	return min(1.0, count / 2 ** n)


def main(folder):
	runs = {}
	for path in sorted(glob.glob(os.path.join(folder, "seed_*.csv"))):
		seed = os.path.basename(path)[5:-4]
		with open(path, newline="") as fh:
			for row in csv.DictReader(fh):
				runs.setdefault(row["name"], {})[seed] = row
	if not runs:
		sys.exit(f"aucun fichier seed_*.csv dans {folder}")
	out = csv.writer(sys.stdout)
	out.writerow(["name", "metric", "seeds", "mean", "ci95_low", "ci95_high",
	              "vs", "mean_diff", "wilcoxon_p"])
	for name, by_seed in runs.items():
		regime = name.rsplit("_", 1)[-1]
		strategy = name.rsplit("_", 1)[0]
		for m in METRICS:
			vals = [float(r[m]) for r in by_seed.values()]
			n = len(vals)
			mean = statistics.fmean(vals)
			half = T95[min(n - 2, 29)] * statistics.stdev(vals) / math.sqrt(n) if n > 1 else 0.0
			base = [name, m, n, f"{mean:.6g}", f"{mean - half:.6g}", f"{mean + half:.6g}"]
			if strategy not in AGENTS:
				out.writerow(base + ["", "", ""])
				continue
			for ref in REFERENCES:
				ref_runs = runs.get(f"{ref}_{regime}", {})
				seeds = sorted(set(by_seed) & set(ref_runs))
				diffs = [float(by_seed[s][m]) - float(ref_runs[s][m]) for s in seeds]
				if not diffs:
					continue
				out.writerow(base + [ref, f"{statistics.fmean(diffs):.6g}", f"{wilcoxon_exact(diffs):.4g}"])


if __name__ == "__main__":
	main(sys.argv[1])
