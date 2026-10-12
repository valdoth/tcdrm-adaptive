"""
Diagnostic de couverture de l'espace d'états (Q-Learning et Rainbow DQN).

Fait jouer une politique (aléatoire, Q-Learning ou Rainbow) dans le simulateur Java et
mesure :
  - la couverture de la grille discrète de 1458 états (référence commune aux deux agents,
    via CloudSimQLearningEnv._discretize_state) ;
  - la distribution de chaque dimension de l'état continu (11 dims) ;
  - pour un modèle Q-Learning, la couverture issue de ses compteurs de visites
    d'entraînement (sans simulateur, option --from-visits).

Le serveur Java réécrit les Q-tables des méta-contrôleurs à chaque fin d'épisode : lancer
le TrainingServer depuis une COPIE du répertoire des modèles pour ne pas les modifier.

Usage :
  uv run python -m utils.state_coverage --from-visits models/qlearning_cloudsim_final.pkl
  uv run python -m utils.state_coverage --agent rainbow --model models/rainbow_cloudsim_final.pt \
      --episodes 20 --port 25335 --out coverage_rainbow.json
"""

import argparse
import json
import os
import pickle
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from envs.cloudsim_env import CloudSimEnv  # noqa: E402
from envs.state_grid import GRID_DIMS, N_STATES, discretize  # noqa: E402

DIM_NAMES = ["latence", "budget", "réplicas", "coût", "viol_T", "viol_C",
             "progression", "popularité", "complexe", "frac_éligible", "pop_moyenne"]


def grid_report(counts: np.ndarray) -> dict:
	"""Couverture de la grille de 1458 états à partir d'un vecteur de visites par état."""
	visited = counts > 0
	idx = np.arange(N_STATES) % 729
	per_dim = {}
	for name, w in GRID_DIMS:
		lv = (idx // w) % 3
		per_dim[name] = [int(counts[lv == k].sum()) for k in range(3)]
	top = np.sort(counts)[::-1]
	total = max(1.0, float(counts.sum()))
	return {
		"etats_visites": int(visited.sum()),
		"couverture_pct": round(100.0 * visited.mean(), 2),
		"simple_pct": round(100.0 * visited[:729].mean(), 2),
		"complexe_pct": round(100.0 * visited[729:].mean(), 2),
		"visites_par_niveau": per_dim,
		"part_10_etats_plus_visites_pct": round(100.0 * top[:10].sum() / total, 1),
	}


def continuous_report(states: np.ndarray, bins: int = 10) -> dict:
	"""Distribution de chaque dimension continue : bornes observées et bins occupés."""
	out = {}
	for d, name in enumerate(DIM_NAMES[: states.shape[1]]):
		col = states[:, d]
		lo, hi = float(col.min()), float(col.max())
		hist, _ = np.histogram(col, bins=bins, range=(0.0, max(1.0, hi)))
		out[name] = {"min": round(lo, 4), "max": round(hi, 4),
		             "bins_occupes": f"{int((hist > 0).sum())}/{bins}"}
	return out


def load_policy(agent: str, model: str | None, training: bool):
	if agent == "random":
		rng = np.random.default_rng(0)
		return lambda obs, valid: int(rng.choice(valid))
	if agent == "qlearning":
		from agents.simple_qlearning_agent import SimpleQLearningAgent
		q = SimpleQLearningAgent(n_states=N_STATES, n_actions=3)
		q.load(model)
		return lambda obs, valid: q.select_action(discretize(obs), valid_actions=valid, training=training)
	if agent == "rainbow":
		from agents.rainbow_dqn_agent import RainbowDQNAgent
		r = RainbowDQNAgent(state_dim=11, action_dim=3)
		r.load(model)
		def act(obs, valid):
			mask = np.zeros(3, dtype=np.float32)
			mask[valid] = 1.0
			return r.select_action(obs, training=training, action_mask=mask)
		return act
	raise ValueError(agent)


def rollout(agent: str, model: str | None, episodes: int, port: int, seed_base: int,
            training: bool, save_states: str | None = None,
            exploring_start_prob: float = 0.0) -> dict:
	policy = load_policy(agent, model, training)
	config = {"agentTag": agent, "exploringStartProb": float(exploring_start_prob)}
	envs = {c: CloudSimEnv(port=port, complex=c, config=config) for c in (False, True)}
	counts = np.zeros(N_STATES)
	states = []
	try:
		for ep in range(episodes):
			env = envs[ep % 2 == 1]
			obs, info = env.reset(seed=seed_base + ep)
			done = False
			while not done:
				states.append(obs)
				counts[discretize(obs)] += 1
				mask = info.get("action_mask") or [True, True, True]
				valid = [i for i, m in enumerate(mask) if m]
				obs, _, done, _, info = env.step(policy(obs, valid))
	finally:
		for env in envs.values():
			env.close()
	states = np.asarray(states, dtype=np.float32)
	if save_states:
		np.save(save_states, states)
	return {"agent": agent, "episodes": episodes, "pas": int(len(states)),
	        "grille_1458": grid_report(counts), "dimensions_continues": continuous_report(states)}


def main():
	p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	p.add_argument("--from-visits", help="modèle Q-Learning (.pkl) : couverture d'entraînement")
	p.add_argument("--agent", choices=["random", "qlearning", "rainbow"])
	p.add_argument("--model")
	p.add_argument("--episodes", type=int, default=20)
	p.add_argument("--port", type=int, default=25335)
	p.add_argument("--seed-base", type=int, default=5000)
	p.add_argument("--greedy", action="store_true", help="politique sans exploration")
	p.add_argument("--out", help="fichier JSON de sortie")
	p.add_argument("--save-states", help="fichier .npy des états continus visités")
	p.add_argument("--exploring-start-prob", type=float, default=0.0,
	               help="probabilité de départ exploratoire par épisode (entraînement)")
	a = p.parse_args()

	if a.from_visits:
		with open(a.from_visits, "rb") as f:
			d = pickle.load(f)
		v = d["visit_counts"]
		report = {"modele": a.from_visits, "pas_entrainement": d.get("training_steps"),
		          "epsilon_final": d.get("epsilon"),
		          "couples_etat_action_visites_pct": round(100.0 * (v > 0).mean(), 2),
		          "grille_1458": grid_report(v.sum(axis=1))}
	elif a.agent:
		report = rollout(a.agent, a.model, a.episodes, a.port, a.seed_base, training=not a.greedy,
		                 save_states=a.save_states, exploring_start_prob=a.exploring_start_prob)
	else:
		p.error("--from-visits ou --agent requis")

	text = json.dumps(report, ensure_ascii=False, indent=2)
	print(text)
	if a.out:
		with open(a.out, "w", encoding="utf-8") as f:
			f.write(text)


if __name__ == "__main__":
	main()
