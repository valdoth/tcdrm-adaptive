"""Agrège les résumés de validation d'un dossier (seed_<graine>.csv) : moyenne et
écart-type (échantillon, n−1) de chaque colonne numérique, par stratégie (colonne name).

Usage : python3 aggregate_seeds.py <dossier>   → CSV sur la sortie standard
"""
import csv
import glob
import os
import statistics
import sys


def main(folder: str) -> None:
	rows = {}
	files = sorted(glob.glob(os.path.join(folder, "seed_*.csv")))
	for path in files:
		with open(path, newline="") as fh:
			for row in csv.DictReader(fh):
				rows.setdefault(row["name"], []).append(row)
	if not rows:
		sys.exit(f"aucun fichier seed_*.csv dans {folder}")
	columns = [c for c in next(iter(rows.values()))[0] if c != "name"]
	out = csv.writer(sys.stdout)
	header = ["name", "seeds"]
	for c in columns:
		header += [f"{c}_mean", f"{c}_std"]
	out.writerow(header)
	for name, group in rows.items():
		line = [name, len(group)]
		for c in columns:
			values = [float(r[c]) for r in group if r.get(c) not in (None, "")]
			mean = statistics.fmean(values) if values else 0.0
			std = statistics.stdev(values) if len(values) > 1 else 0.0
			line += [f"{mean:.6g}", f"{std:.6g}"]
		out.writerow(line)


if __name__ == "__main__":
	main(sys.argv[1])
