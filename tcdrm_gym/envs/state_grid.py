"""
Grille discrète de l'agent Q-Learning : 3^5 × 4 × 2 = 1944 états.

Source unique de la discrétisation, partagée par l'entraînement (CloudSimQLearningEnv),
l'évaluation (bridge/rl_bridge.py) et le diagnostic de couverture (utils/state_coverage.py) :
une Q-table apprise n'a de sens que si les trois chemins encodent l'état de la même façon.

Dimensions de l'état continu utilisées (indices de TcdrmSimulation.buildRLState) :
  RT       [0] latence / T_SLA effectif   : < 0.4 | < 0.7 | ≥ 0.7
  COST     [3] coût / C_SLA               : < 0.4 | < 0.7 | ≥ 0.7
  SLA      [4],[5] violations du contrat  : aucune | T_SLA ou C_SLA | les deux
           (remplace l'avancement de l'épisode [6] : un système en ligne n'a pas
           d'horizon, et la Q-table apprenait une politique calée sur le compteur de
           requêtes — en complexe, Q-Learning ne répliquait qu'au franchissement de
           0.33 (requête 330) ou 0.67 (requête 670), quel que soit le besoin)
  BUD      [1] budget restant             : ≥ 0.6 | ≥ 0.3 | < 0.3
  COV      [11] relations servies par un réplica : toutes | une partie | aucune
           (remplace « réplicas / max » : le nombre de réplicas ne dit pas s'ils
           couvrent les données lues — 6 réplicas peuvent laisser 3 relations
           distantes en charge variable, 5 suffire en charge stable)
  GAIN     [7],[12] popularité normalisée max : < plancher | < contrat et refroidit |
           < contrat et chauffe | contrat atteint
           (la tendance [12] dit si la donnée la plus populaire a été lue à la dernière
           requête. Sans elle, « entre plancher et contrat » mélangeait une donnée qui
           chauffe, où répliquer tôt est rentable, et une donnée qui refroidit (charges
           variable et en rafales), où c'est du gaspillage.)
           (plancher P_SLA ≈ 0.704 = TcdrmConstants.META_POPULARITY_MIN, sous lequel
           aucune réplication n'est permise ; contrat = 1.0, P_SLA du papier. Les anciens
           seuils 0.2/0.6 mélangeaient données tout juste éligibles et pleinement
           populaires : Q-Learning s'aidait alors du budget restant, qui baisse avec le
           temps, comme d'une horloge — en complexe il ne répliquait qu'au passage du
           budget sous 0.6, requête 782)
  COMPLEX  [8] type de requête            : simple | complexe
Les dimensions 2, 6, 9 et 10 sont réservées à Rainbow DQN.
"""

import numpy as np

N_STATES = 1944

# Mêmes formules que TcdrmConstants (POPULARITY_EMA_FULL, META_POPULARITY_MIN) :
# λ = POPULARITY_EMA_LAMBDA, P_SLA = 200 ; plancher = popularité normalisée atteinte
# après τ = 1/λ accès consécutifs.
_LAMBDA = 0.0115
_P_SLA = 200
_EMA_FULL = 1.0 - (1.0 - _LAMBDA) ** _P_SLA
POPULARITY_FLOOR = min(1.0, (1.0 - (1.0 - _LAMBDA) ** (1.0 / _LAMBDA)) / _EMA_FULL)

# (nom, poids dans l'index) — ordre de l'encodage ; GRID_LEVELS : niveaux par dimension
GRID_DIMS = [("RT", 324), ("COST", 108), ("SLA", 36), ("BUD", 12), ("COV", 4), ("GAIN", 1)]
GRID_LEVELS = {"RT": 3, "COST": 3, "SLA": 3, "BUD": 3, "COV": 3, "GAIN": 4}
COMPLEX_OFFSET = 972


def _three_levels(x: float, low: float, high: float) -> int:
	if x < low:
		return 0
	if x < high:
		return 1
	return 2


def discretize(state) -> int:
	"""Index (0-1943) de l'état continu `state` (séquence d'au moins 9 valeurs)."""
	s = np.asarray(state, dtype=np.float64)
	rt = _three_levels(s[0], 0.4, 0.7)
	cost_bin = _three_levels(s[3], 0.4, 0.7)
	sla_bin = int(s[4] >= 0.5) + int(s[5] >= 0.5) if len(s) > 5 else 0
	budget = s[1]
	bud = 0 if budget >= 0.6 else (1 if budget >= 0.3 else 2)
	served = s[11] if len(s) > 11 else 0.0
	net = 0 if served >= 0.999 else (1 if served > 0 else 2)
	gain_bin = _three_levels(s[7], POPULARITY_FLOOR, 1.0 - 1e-9) if len(s) > 7 else 0
	heating = len(s) > 12 and s[12] >= 0.5
	gain_bin = 3 if gain_bin == 2 else (2 if gain_bin == 1 and heating else gain_bin)
	complex_bit = 1 if len(s) > 8 and s[8] >= 0.5 else 0
	return (rt * 324 + cost_bin * 108 + sla_bin * 36 + bud * 12 + net * 4 + gain_bin) \
		+ complex_bit * COMPLEX_OFFSET


def decode(index: int) -> dict:
	"""Niveau de chaque dimension pour un index de la grille."""
	out = {"COMPLEX": int(index >= COMPLEX_OFFSET)}
	base = index % COMPLEX_OFFSET
	for name, w in GRID_DIMS:
		out[name] = (base // w) % GRID_LEVELS[name]
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
