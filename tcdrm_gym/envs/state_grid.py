"""
Grille discrète de l'agent Q-Learning : 3^6 × 2 = 1458 états.

Source unique de la discrétisation, partagée par l'entraînement (CloudSimQLearningEnv),
l'évaluation (bridge/rl_bridge.py) et le diagnostic de couverture (utils/state_coverage.py) :
une Q-table apprise n'a de sens que si les trois chemins encodent l'état de la même façon.

Dimensions de l'état continu utilisées (indices de TcdrmSimulation.buildRLState) :
  RT       [0] latence / T_SLA effectif   : < 0.4 | < 0.7 | ≥ 0.7
  COST     [3] coût / C_SLA               : < 0.4 | < 0.7 | ≥ 0.7
  PROG     [6] avancement de l'épisode    : < 0.33 | < 0.67 | ≥ 0.67
  BUD      [1] budget restant             : ≥ 0.6 | ≥ 0.3 | < 0.3
  NET      [2] réplicas / max             : ≥ 0.5 | > 0 | 0
  GAIN     [7] popularité normalisée max  : < 0.2 | < 0.6 | ≥ 0.6
  COMPLEX  [8] type de requête            : simple | complexe
Les dimensions 4, 5, 9 et 10 sont réservées à Rainbow DQN.
"""

import numpy as np

N_STATES = 1458

# (nom, poids dans l'index) — ordre de l'encodage
GRID_DIMS = [("RT", 243), ("COST", 81), ("PROG", 27), ("BUD", 9), ("NET", 3), ("GAIN", 1)]
COMPLEX_OFFSET = 729


def _three_levels(x: float, low: float, high: float) -> int:
	if x < low:
		return 0
	if x < high:
		return 1
	return 2


def discretize(state) -> int:
	"""Index (0-1457) de l'état continu `state` (séquence d'au moins 9 valeurs)."""
	s = np.asarray(state, dtype=np.float64)
	rt = _three_levels(s[0], 0.4, 0.7)
	cost_bin = _three_levels(s[3], 0.4, 0.7)
	prog_bin = _three_levels(s[6], 0.33, 0.67) if len(s) > 6 else 0
	budget = s[1]
	bud = 0 if budget >= 0.6 else (1 if budget >= 0.3 else 2)
	replicas = s[2]
	net = 0 if replicas >= 0.5 else (1 if replicas > 0 else 2)
	gain_bin = _three_levels(s[7], 0.2, 0.6) if len(s) > 7 else 0
	complex_bit = 1 if len(s) > 8 and s[8] >= 0.5 else 0
	return (rt * 243 + cost_bin * 81 + prog_bin * 27 + bud * 9 + net * 3 + gain_bin) \
		+ complex_bit * COMPLEX_OFFSET


def decode(index: int) -> dict:
	"""Niveaux (0-2) de chaque dimension pour un index de la grille."""
	out = {"COMPLEX": int(index >= COMPLEX_OFFSET)}
	base = index % COMPLEX_OFFSET
	for name, w in GRID_DIMS:
		out[name] = (base // w) % 3
	return out


class CoverageTracker:
	"""Compte les visites de chaque état de la grille pendant l'entraînement.

	Utilisé par les deux agents : Q-Learning lui passe directement son index d'état,
	Rainbow son état continu (discrétisé ici sur la même grille, à titre de référence).
	"""

	def __init__(self):
		self.counts = np.zeros(N_STATES, dtype=np.int64)

	def add(self, state) -> None:
		index = int(state) if np.isscalar(state) else discretize(state)
		self.counts[index] += 1

	def coverage_pct(self, min_visits: int = 1) -> float:
		return 100.0 * float((self.counts >= min_visits).mean())

	def summary(self) -> str:
		return (f"{int((self.counts > 0).sum())}/{N_STATES} états visités "
		        f"({self.coverage_pct():.1f} %), {int((self.counts >= 10).sum())} au moins 10 fois")

	def save(self, path: str) -> None:
		np.save(path, self.counts)
