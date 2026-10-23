"""
Script d'entraînement RL utilisant CloudSimPlus pour les simulations.
"""

import argparse
import os
import sys
import json
import time
import csv
import numpy as np
import torch
import yaml

from envs.cloudsim_env import CloudSimEnv, CloudSimQLearningEnv
from envs.state_grid import N_STATES, CoverageTracker
from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.rainbow_dqn_agent import RainbowDQNAgent, make_rainbow_agent


def load_config(config_path):
    with open(config_path, 'r') as file:
        return yaml.safe_load(file)

# Chargement des configurations au début du script
config_path = os.path.join(os.path.dirname(__file__), 'config.yml')
config = load_config(config_path)


def ensure_log_dir(agent_name: str) -> str:
	base = os.path.join(os.path.dirname(__file__), 'logs', agent_name)
	ts = time.strftime('%Y%m%d-%H%M%S')
	path = os.path.join(base, ts)
	os.makedirs(path, exist_ok=True)
	return path


class CSVLogger:
	def __init__(self, log_path: str, fieldnames: list[str]):
		self.log_path = log_path
		self.fieldnames = fieldnames
		self._init_file()

	def _init_file(self):
		new_file = not os.path.exists(self.log_path)
		self._fh = open(self.log_path, 'a', newline='')
		self._writer = csv.DictWriter(self._fh, fieldnames=self.fieldnames)
		if new_file:
			self._writer.writeheader()
			self._fh.flush()

	def log(self, row: dict):
		safe = {k: row.get(k, '') for k in self.fieldnames}
		self._writer.writerow(safe)
		self._fh.flush()

	def close(self):
		try:
			self._fh.close()
		except Exception:
			pass


def to_builtin(obj):
	"""Recursively convert numpy/py4j types to Python builtins for JSON/CSV."""
	try:
		import numpy as _np  # local import to avoid issues if numpy not present globally
	except Exception:  # pragma: no cover
		_np = None

	# numpy scalars
	if _np is not None and isinstance(obj, (_np.generic,)):
		try:
			return obj.item()
		except Exception:
			return float(obj) if hasattr(obj, '__float__') else int(obj) if hasattr(obj, '__int__') else str(obj)

	# numpy arrays -> list
	if _np is not None and isinstance(obj, _np.ndarray):
		return [to_builtin(x) for x in obj.tolist()]

	# dict
	if isinstance(obj, dict):
		return {str(k): to_builtin(v) for k, v in obj.items()}

	# list/tuple
	if isinstance(obj, (list, tuple)):
		return [to_builtin(x) for x in obj]

	# booleans that might be numpy.bool_
	if str(type(obj)).endswith("numpy.bool_'>"):
		return bool(obj)

	return obj


def train_qlearning(env: CloudSimQLearningEnv, episodes: int, save_path: str, seed_base: int = 42,
                     env_complex: CloudSimQLearningEnv = None, demo_episodes: int = 20):
	"""
	Entraîne un agent Q-Learning unique sur un mélange de requêtes simples et complexes :
	les épisodes pairs utilisent `env` (simple), les impairs `env_complex` (si fourni).
	Le même agent (même Q-table) accumule l'apprentissage des deux régimes — la dimension
	is_complex de l'état (buildRLState[8]) lui permet de conditionner sa politique dessus,
	au lieu d'entraîner un modèle par régime.
	"""
	print("=" * 70)
	print("Q-LEARNING TRAINING with CloudSimPlus")
	if env_complex is not None:
		print("  Mixed training: simple + complex queries (single adaptive model)")
	print("=" * 70)

	agent = SimpleQLearningAgent(
		n_states=N_STATES,
		n_actions=3,
		learning_rate=float(os.environ.get('TCDRM_Q_LR', 0.08)),
		discount_factor=float(os.environ.get('TCDRM_Q_GAMMA', 0.99)),
		epsilon_start=float(os.environ.get('TCDRM_Q_EPS0', 1.0)),
		epsilon_min=float(os.environ.get('TCDRM_Q_EPS_MIN', 0.05)),
		epsilon_decay=float(os.environ.get('TCDRM_Q_EPS_DECAY', 0.995)),
		use_double_q=bool(int(os.environ.get('TCDRM_Q_DOUBLE', '1'))),
		adaptive_lr=bool(int(os.environ.get('TCDRM_Q_ADAPTIVE_LR', '1'))),
		epsilon_schedule=os.environ.get('TCDRM_Q_EPS_SCHED', 'linear'),
		# Décroissance de ε sur 80 % des épisodes RÉELLEMENT entraînés (l'ancien défaut
		# fixe de 5000 laissait ε ≈ 0.72 en fin d'un entraînement de 1500 épisodes).
		epsilon_linear_steps=int(os.environ.get('TCDRM_Q_EPS_STEPS', max(1, int(0.8 * episodes)))),
		lambda_trace=float(os.environ.get('TCDRM_Q_LAMBDA', '0.6')),
		# Init. optimiste à l'échelle des valeurs Q apprises (≈ −35 à +65) : à 1.0,
		# REPLICATE restait presque jamais essayé dans les états où la donnée est déjà
		# populaire (0 à 12 essais en 1500 épisodes, run v12).
		optimistic_init=float(os.environ.get('TCDRM_Q_OPT_INIT', '50.0'))
	)
    
	best_reward = float('-inf')
	rewards_history = []
	log_dir = ensure_log_dir('qlearning')
	csv_logger = CSVLogger(
		os.path.join(log_dir, 'progress.csv'),
		[
			'episode','reward','avg10','epsilon','states_explored','grid_coverage_pct',
			'sla_violations','cumulative_cost','replica_count','budget_remaining',
			'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action',
			'dynamic_tsla','dynamic_min_popularity'
		]
	)
	# Optional TensorBoard
	tb_writer = None
	try:
		from torch.utils.tensorboard import SummaryWriter  # type: ignore
		tb_writer = SummaryWriter(log_dir=log_dir)
	except Exception:
		tb_writer = None
    
	# Warm-start par démonstrations TCDRM (DQfD-lite), comme Rainbow. Sans elles, en
	# complexe, Q-Learning ne voyait presque jamais la suite de ~5 réplications qui fait
	# baisser la latence (ε = 5 % en fin d'entraînement) : un réplica isolé ne sert à
	# rien, il apprenait REPLICATE < NOOP et attendait la popularité 1,0 (200 accès).
	# Mesuré au benchmark (graine 42, politique figée) : répliquer dès que possible
	# donne une récompense cumulée de +4658 contre −5166 pour la politique apprise.
	_run_demonstrations(agent, env, env_complex, demo_episodes, seed_base, None)

	coverage = CoverageTracker()
	for episode in range(episodes):
		use_complex = (env_complex is not None) and (episode % 2 == 1)
		active_env = env_complex if use_complex else env
		state, info = active_env.reset(seed=seed_base + episode)
		agent.start_episode()
		coverage.add(state)
		episode_reward = 0
		done = False
		last_info = info

		while not done:
			valid_actions = None
			if isinstance(last_info, dict) and 'action_mask' in last_info:
				try:
					valid_actions = [i for i, m in enumerate(last_info['action_mask']) if m]
				except Exception:
					valid_actions = None
			action = agent.select_action(state, valid_actions=valid_actions, training=True)
			next_state, reward, done, truncated, info = active_env.step(action)
			agent.update(state, action, reward, next_state, done)
			coverage.add(next_state)
			state = next_state
			episode_reward += reward
			last_info = info
        
		agent.decay_epsilon()
		rewards_history.append(episode_reward)
        
		avg_reward = np.mean(rewards_history[-10:])
		stats = agent.get_stats()
        
		# CSV log every episode
		row = {
			'episode': episode,
			'reward': episode_reward,
			'avg10': avg_reward,
			'epsilon': stats['epsilon'],
			'states_explored': stats['states_explored'],
			'grid_coverage_pct': coverage.coverage_pct()
		}
		if isinstance(last_info, dict):
			for k in ['sla_violations','cumulative_cost','replica_count','budget_remaining',
					  'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action',
					  'dynamic_tsla','dynamic_min_popularity']:
				if k in last_info:
					row[k] = last_info[k]
		csv_logger.log(to_builtin(row))

		# TensorBoard scalars
		if tb_writer is not None:
			tb_writer.add_scalar('reward/episode', episode_reward, episode)
			tb_writer.add_scalar('reward/avg10', float(avg_reward), episode)
			tb_writer.add_scalar('exploration/epsilon', float(stats['epsilon']), episode)
			if isinstance(last_info, dict):
				for k in ['sla_violations','cumulative_cost','replica_count','budget_remaining',
						  'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action',
						  'dynamic_tsla','dynamic_min_popularity']:
					if k in last_info:
						try:
							tb_writer.add_scalar(f'metrics/{k}', float(last_info[k]), episode)
						except Exception:
							pass

		if episode % 5 == 0 or episode == episodes - 1:  # Plus fréquent pour monitoring
			metrics_tail = ""
			if last_info and isinstance(last_info, dict):
				metrics_tail = (
					f" | SLA Viol: {last_info.get('sla_violations','?')} | "
					f"CostΣ: {last_info.get('cumulative_cost','?')} | "
					f"Replicas: {last_info.get('replica_count','?')} | "
					f"Budget: {last_info.get('budget_remaining','?')}"
				)
			# Ajouter pourcentage d'exploration
			print(
				f"Episode {episode:4d}/{episodes} | Reward: {episode_reward:8.2f} | Avg(10): {avg_reward:8.2f} | "
				f"ε: {stats['epsilon']:.3f} | Couverture: {coverage.coverage_pct():.1f}%{metrics_tail}"
			)
        
		# Sélection seulement une fois l'exploration terminée (ε au minimum) : avant, la
		# Q-table n'est qu'une ébauche — le run précédent retenait le modèle de l'épisode
		# 99 (ε ≈ 0.92), bien noté sur 4 épisodes d'évaluation mais pas entraîné.
		exploration_done = agent.epsilon <= agent.epsilon_min + 1e-9
		if exploration_done and ((episode + 1) % EVAL_EVERY == 0 or episode == episodes - 1):
			eval_reward = _greedy_eval(agent, env, env_complex, seed_base, 'qlearning')
			print(f"  Évaluation greedy (épisode {episode}) : {eval_reward:.2f}")
		else:
			eval_reward = None
		if eval_reward is not None and eval_reward > best_reward:
			best_reward = eval_reward
			agent.save(save_path)
			# Couverture À LA DATE du modèle retenu : le pont s'en sert pour savoir quels
			# états CE modèle a appris (exploration ε = 5 % ailleurs).
			coverage.save(save_path.replace('.pkl', '_coverage.npy'))
			# Save meta
			best_meta = to_builtin({
				'episode': episode,
				'best_greedy_eval_reward': best_reward,
				'epsilon': stats['epsilon'],
				'states_explored': stats['states_explored'],
				'metrics_tail': last_info if isinstance(last_info, dict) else {}
			})
			with open(os.path.join(log_dir, 'best_meta.json'), 'w') as f:
				json.dump(best_meta, f, indent=2)
			print(f"  💾 New best model saved (greedy eval: {best_reward:.2f}) → {save_path}")
    
	final_path = save_path.replace('.pkl', '_final.pkl')
	agent.save(final_path)
	coverage.save(save_path.replace('.pkl', '_final_coverage.npy'))
	# Le chemin canonique (chargé par le benchmark) garde le MEILLEUR checkpoint
	# (évaluation greedy) sauvé en cours d'entraînement — le final ne l'écrase plus : évaluer la
	# photo de fin de run (souvent en plein creux d'exploration) rendait les runs
	# courts trompeurs. Fallback : si aucun best n'a été sauvé,
	# le final sert de canonique.
	if best_reward == float('-inf'):
		agent.save(save_path)
		coverage.save(save_path.replace('.pkl', '_coverage.npy'))

	csv_logger.close()
	if tb_writer is not None:
		try:
			tb_writer.flush(); tb_writer.close()
		except Exception:
			pass
	print()
	print("=" * 70)
	print("TRAINING COMPLETE")
	print(f"  Meilleure évaluation greedy: {best_reward:.2f}")
	print(f"  Final epsilon: {agent.epsilon:.4f}")
	print(f"  Couverture de la grille: {coverage.summary()}")
	print(f"  Model saved to: {save_path}")
	print("=" * 70)
    
	return agent, rewards_history


# Sélection du meilleur modèle : toutes les EVAL_EVERY épisodes, l'agent joue en greedy,
# sans apprendre, EVAL_EPISODES épisodes fixes (moitié simple, moitié complexe) sans
# départ exploratoire. La moyenne glissante des épisodes d'entraînement mélangeait
# départs aléatoires, régimes et exploration : un « meilleur » modèle choisi sur elle
# reflétait surtout des épisodes faciles.
EVAL_EVERY = int(os.environ.get('TCDRM_EVAL_EVERY', '50'))
EVAL_EPISODES = int(os.environ.get('TCDRM_EVAL_EPISODES', '4'))
EVAL_SEED_OFFSET = 200000  # graines disjointes de l'entraînement et des démonstrations


def _set_eval(env_obj, on):
	if env_obj is None:
		return
	try:
		env_obj._connect()
		env_obj._server.setEvalMode(bool(on))
	except Exception as e:
		print(f"  (setEvalMode indisponible: {e})")


def _greedy_eval(agent, env, env_complex, seed_base, kind):
	"""Récompense moyenne de la politique greedy sur les épisodes d'évaluation fixes."""
	_set_eval(env, True)
	_set_eval(env_complex, True)
	total = 0.0
	try:
		for k in range(EVAL_EPISODES):
			use_complex = (env_complex is not None) and (k % 2 == 1)
			active_env = env_complex if use_complex else env
			state, info = active_env.reset(seed=seed_base + EVAL_SEED_OFFSET + k)
			last_info = info
			done = False
			while not done:
				mask = None
				if isinstance(last_info, dict) and 'action_mask' in last_info:
					mask = np.array(last_info['action_mask'], dtype=np.float32)
				if kind == 'qlearning':
					valid = None if mask is None else [i for i, m in enumerate(mask) if m]
					action = agent.select_action(state, valid_actions=valid, training=False)
				else:
					action = agent.select_action(state, training=False, action_mask=mask)
				state, reward, done, truncated, last_info = active_env.step(action)
				total += reward
	finally:
		_set_eval(env, False)
		_set_eval(env_complex, False)
	return total / max(1, EVAL_EPISODES)


def _tcdrm_demo_action(state, action_mask):
	"""Heuristique de DÉMONSTRATION type TCDRM (Paper Algorithm 1) pour le warm-start :
	répliquer quand le T_SLA est violé ET les données sont populaires ET la réplication est
	possible ; supprimer si un réplica est inutile (SLA très confortable) ; sinon NOOP.
	état : [0]lat/tSla [4]viol_tSla [5]viol_cSla [7]popularité [9]fraction_éligible.
	"""
	can_repl = action_mask is None or action_mask[1] > 0.5
	can_del  = action_mask is not None and action_mask[2] > 0.5
	lat_norm   = float(state[0])
	# T_SLA seul (comme le bonus CORRECT_TRIGGER) : un dépassement du C_SLA vient de la
	# synchronisation des réplicas, répliquer l'aggraverait.
	viol       = float(state[4]) >= 0.5 or lat_norm >= 1.0
	# Éligible dès qu'un fragment franchit le seuil courant (state[9]>0), OU données
	# clairement populaires — démontre l'ouverture PRÉCOCE gagnante, pas seulement à 0.9.
	eligible   = float(state[9]) > 0.0 or float(state[7]) >= 0.9
	if can_repl and viol and eligible:
		return 1                      # REPLICATE dès que le méta autorise sous violation
	if can_del and lat_norm < 0.4:    # SLA très confortable → un réplica est superflu
		return 2
	return 0                          # NOOP


def _run_demonstrations(agent, env, env_complex, demo_episodes, seed_base, max_episode_length):
	"""Pré-remplit le buffer de l'agent avec des transitions de l'heuristique TCDRM et
	le fait apprendre dessus (DQfD-lite, Hester et al. 2018) : l'agent démarre d'une
	politique compétente au lieu d'explorer de zéro → convergence en bien moins d'épisodes."""
	if demo_episodes <= 0:
		return
	print(f"  Warm-start : {demo_episodes} épisodes de démonstration TCDRM (DQfD-lite)...")

	def _set_demo(env_obj, on):
		# Mode démonstration côté Java : la démo joue avec les mêmes règles que l'agent
		# (seuil de popularité appris), pour ne produire que des transitions possibles au benchmark.
		try:
			env_obj._connect()
			env_obj._server.setDemoMode(bool(on))
		except Exception as e:
			print(f"  (setDemoMode indisponible: {e})")

	_set_demo(env, True)
	if env_complex is not None:
		_set_demo(env_complex, True)
	try:
		for d in range(demo_episodes):
			use_complex = (env_complex is not None) and (d % 2 == 1)
			active_env = env_complex if use_complex else env
			state, info = active_env.reset(seed=seed_base + 100000 + d)  # seeds disjointes du train
			if hasattr(agent, "start_episode"):
				agent.start_episode()
			last_info = info
			done = False
			while not done:
				mask = None
				if isinstance(last_info, dict) and 'action_mask' in last_info:
					try:
						mask = np.array(last_info['action_mask'], dtype=np.float32)
					except Exception:
						mask = None
				# Q-Learning : l'heuristique lit l'état CONTINU, l'agent apprend sur l'état discret.
				action = _tcdrm_demo_action(getattr(active_env, "last_obs", state), mask)
				next_state, reward, done, truncated, info = active_env.step(action)
				agent.update(state, action, reward, next_state, done)  # apprend de la démo
				state = next_state
				last_info = info
	finally:
		# Toujours restaurer le mode normal avant l'entraînement RL.
		_set_demo(env, False)
		if env_complex is not None:
			_set_demo(env_complex, False)


def train_rainbow(env: CloudSimEnv, episodes: int, save_path: str, seed_base: int = 42, agent: RainbowDQNAgent = None,
                   env_complex: CloudSimEnv = None, max_episode_length: int = 1000, demo_episodes: int = 20):
	"""
	Entraîne un agent Rainbow DQN complet (6/6 composants Rainbow) :
	  Double DQN + Dueling + PER + N-step(3) + NoisyLinear + C51 Distributional.

	NoisyLinear remplace epsilon-greedy : l'agent explore via son bruit appris (sigma).
	C51 modélise la distribution complète des retours → meilleure politique sous incertitude.

	Entraîné sur un mélange de requêtes simples et complexes (épisodes pairs/impairs) : le même
	réseau apprend les deux régimes, la dimension is_complex de l'état (buildRLState[8]) lui
	permettant de conditionner sa politique dessus au lieu d'un modèle séparé par régime.
	"""
	print("=" * 70)
	print("RAINBOW DQN TRAINING with CloudSimPlus")
	print("  Components: Double DQN + Dueling + PER + N-step + Noisy + C51")
	if env_complex is not None:
		print("  Mixed training: simple + complex queries (single adaptive model)")
	print("=" * 70)

	if agent is None:
		# Le scheduler cosinus doit décroître sur toute la durée réelle de l'entraînement,
		# sinon le LR tombe à eta_min bien avant la fin et les derniers épisodes n'apprennent
		# quasiment plus (observé avec T_max=20000 fixe : déjà atteint vers l'épisode ~20-21
		# alors qu'un Rainbow DQN avec C51+PER+NoisyNet a besoin de bien plus d'épisodes pour
		# converger sur un état 9D face au Q-learning tabulaire).
		# ×4 : scheduler cosine et β-PER comptent en PAS DE GRADIENT, et
		# updates_per_step=4 en fait quatre par transition d'environnement.
		total_steps_estimate = max(20000, episodes * max_episode_length * 4)
		agent = make_rainbow_agent(total_steps_estimate)

	best_reward = float('-inf')
	rewards_history = []
	log_dir = ensure_log_dir('rainbow')
	csv_logger = CSVLogger(
		os.path.join(log_dir, 'progress.csv'),
		[
			'episode', 'reward', 'avg10', 'buffer', 'avg_noisy_sigma', 'grid_coverage_pct',
			'sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining',
			'reward_wait_time', 'reward_unutilization', 'reward_queue_penalty',
			'reward_invalid_action', 'avg_loss',
			'dynamic_tsla', 'dynamic_min_popularity'
		]
	)
	tb_writer = None
	try:
		from torch.utils.tensorboard import SummaryWriter
		tb_writer = SummaryWriter(log_dir=log_dir)
	except Exception:
		pass

	# Warm-start par démonstrations TCDRM (DQfD-lite) — AVANT l'entraînement RL.
	_run_demonstrations(agent, env, env_complex, demo_episodes, seed_base, max_episode_length)

	coverage = CoverageTracker()
	for episode in range(episodes):
		use_complex = (env_complex is not None) and (episode % 2 == 1)
		active_env = env_complex if use_complex else env
		state, info = active_env.reset(seed=seed_base + episode)
		coverage.add(state)
		episode_reward = 0
		done = False
		last_info = info

		while not done:
			action_mask = None
			if isinstance(last_info, dict) and 'action_mask' in last_info:
				try:
					action_mask = np.array(last_info['action_mask'], dtype=np.float32)
				except Exception:
					action_mask = None
			action = agent.select_action(state, training=True, action_mask=action_mask)
			next_state, reward, done, truncated, info = active_env.step(action)
			agent.update(state, action, reward, next_state, done)
			coverage.add(next_state)
			state = next_state
			episode_reward += reward
			last_info = info

		# NoisyLinear gère l'exploration — decay_epsilon() est un no-op mais on l'appelle par cohérence
		agent.decay_epsilon()
		rewards_history.append(episode_reward)
		avg_reward = np.mean(rewards_history[-10:])

		net_stats = agent.get_network_stats()
		row = {
			'episode': episode,
			'reward': episode_reward,
			'avg10': avg_reward,
			'buffer': (len(agent.replay_buffers[0]) + len(agent.replay_buffers[1])),
			'avg_noisy_sigma': net_stats.get('avg_noisy_sigma', 0),
			'avg_loss': net_stats.get('avg_loss', 0),
			'grid_coverage_pct': coverage.coverage_pct()
		}
		if isinstance(last_info, dict):
			for k in ['sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining',
					  'reward_wait_time', 'reward_unutilization', 'reward_queue_penalty', 'reward_invalid_action',
					  'dynamic_tsla', 'dynamic_min_popularity']:
				if k in last_info:
					row[k] = last_info[k]
		csv_logger.log(to_builtin(row))

		if tb_writer is not None:
			tb_writer.add_scalar('reward/episode', episode_reward, episode)
			tb_writer.add_scalar('reward/avg10', float(avg_reward), episode)
			tb_writer.add_scalar('rainbow/buffer', float((len(agent.replay_buffers[0]) + len(agent.replay_buffers[1]))), episode)
			tb_writer.add_scalar('rainbow/avg_loss', float(net_stats.get('avg_loss', 0)), episode)
			if 'avg_noisy_sigma' in net_stats:
				tb_writer.add_scalar('rainbow/noisy_sigma', float(net_stats['avg_noisy_sigma']), episode)
			if isinstance(last_info, dict):
				for k in ['sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining',
						  'dynamic_tsla', 'dynamic_min_popularity']:
					if k in last_info:
						try:
							tb_writer.add_scalar(f'metrics/{k}', float(last_info[k]), episode)
						except Exception:
							pass

		if episode % 10 == 0 or episode == episodes - 1:
			metrics_tail = ""
			if last_info and isinstance(last_info, dict):
				metrics_tail = (
					f" | SLA Viol: {last_info.get('sla_violations','?')} | "
					f"CostΣ: {last_info.get('cumulative_cost','?')} | "
					f"Replicas: {last_info.get('replica_count','?')} | "
					f"Budget: {last_info.get('budget_remaining','?')}"
				)
			sigma_str = f" | σ: {net_stats.get('avg_noisy_sigma', 0):.4f}" if 'avg_noisy_sigma' in net_stats else ""
			print(
				f"Episode {episode:4d}/{episodes} | Reward: {episode_reward:8.2f} | "
				f"Avg(10): {avg_reward:8.2f} | Buffer: {(len(agent.replay_buffers[0]) + len(agent.replay_buffers[1]))}"
				f" | Couverture: {coverage.coverage_pct():.1f}%{sigma_str}{metrics_tail}"
			)

		if (episode + 1) % EVAL_EVERY == 0 or episode == episodes - 1:
			eval_reward = _greedy_eval(agent, env, env_complex, seed_base, 'rainbow')
			print(f"  Évaluation greedy (épisode {episode}) : {eval_reward:.2f}")
		else:
			eval_reward = None
		if eval_reward is not None and eval_reward > best_reward:
			best_reward = eval_reward
			agent.save(save_path)
			coverage.save(save_path.replace('.pt', '_coverage.npy'))  # couverture du modèle retenu
			best_meta = to_builtin({
				'episode': episode,
				'best_greedy_eval_reward': best_reward,
				'buffer': (len(agent.replay_buffers[0]) + len(agent.replay_buffers[1])),
				'network': net_stats,
				'metrics_tail': last_info if isinstance(last_info, dict) else {}
			})
			with open(os.path.join(log_dir, 'best_meta.json'), 'w') as f:
				json.dump(best_meta, f, indent=2)
			print(f"  New best model saved (greedy eval: {best_reward:.2f}) -> {save_path}")

	final_path = save_path.replace('.pt', '_final.pt')
	agent.save(final_path)
	coverage.save(save_path.replace('.pt', '_final_coverage.npy'))
	# Le canonique = MEILLEUR checkpoint (voir train_qlearning) ; final -> _final only.
	if best_reward == float('-inf'):
		agent.save(save_path)
		coverage.save(save_path.replace('.pt', '_coverage.npy'))

	csv_logger.close()
	if tb_writer is not None:
		try:
			tb_writer.flush(); tb_writer.close()
		except Exception:
			pass
	print()
	print("=" * 70)
	print("RAINBOW TRAINING COMPLETE")
	print(f"  Meilleure évaluation greedy: {best_reward:.2f}")
	print(f"  Mode: {net_stats.get('mode', 'rainbow')}")
	print(f"  Couverture de la grille (référence Q-Learning): {coverage.summary()}")
	print(f"  Model saved to: {save_path}")
	print("=" * 70)

	return agent, rewards_history


def main():
	parser = argparse.ArgumentParser(description='Train RL agents with CloudSimPlus')
	parser.add_argument('--agent', type=str, choices=['qlearning', 'rainbow'],
					   default='qlearning', help='Agent type: qlearning (Double Q-Learning) ou rainbow (Rainbow DQN complet)')
	parser.add_argument('--episodes', type=int, default=100, 
					   help='Number of training episodes')
	parser.add_argument('--complex', action='store_true',
					   help='(Ignoré) L\'entraînement mélange toujours simple + complex pour produire un modèle unique adaptatif')
	default_port = int(os.environ.get('TCDRM_TRAIN_PORT', '25335'))
	parser.add_argument('--port', type=int, default=default_port, 
					   help='Java TrainingServer port')
	parser.add_argument('--output', type=str, default=None,
					   help='Output model path')
	parser.add_argument('--max-episode-length', type=int, default=None,
					   help='Optional cap on queries per episode (forwarded to Java via configureSimulation)')
	parser.add_argument('--warmup-queries', type=int, default=0,
					   help='Number of dynamic warmup queries before RL (configureSimulation.warmupQueries)')
	parser.add_argument('--warmup-strategy', type=str, default='random', choices=['random','tcdrm','norep'],
					   help='Warmup strategy: random|tcdrm|norep')
	parser.add_argument('--warmup-random-prob', type=float, default=0.2,
					   help='Random warmup probability for replicate/delete actions (0..1)')
	parser.add_argument('--opening-window', type=int, default=None,
	                    help="Verrou d'ouverture à l'entraînement (requêtes sans réplication) ; "
	                         "défaut : 40 pour Q-Learning (comme à l'évaluation), 0 pour Rainbow")
	parser.add_argument('--seed-base', type=int, default=42,
					   help='Base seed; episode seed = seed_base + episode (decorrelate agents)')
	parser.add_argument('--exploring-start-prob', type=float, default=0.5,
	                    help="Probabilité qu'un épisode d'entraînement démarre par un départ exploratoire "
	                         "(budget, réplicas, popularité et instant tirés au hasard) — 0 = départ habituel")
	parser.add_argument('--cost-routing-tolerance', type=float, default=None,
	                    help='Tolerance (ms) du routage sensible au cout : a temps quasi egal, prefere le site le moins cher — 0 = temps seul')
	parser.add_argument('--reward-correct-trigger', type=float, default=None,
	                    help='Bonus par réplication sous violation (défaut 8) ; PLUS BAS = arrête de répliquer plus tôt (moins de réplicas, moins cher)')
	parser.add_argument('--reward-unpopular-holding', type=float, default=None,
	                    help='Poids du coût de détention (défaut 5) ; PLUS BAS = agent plus proactif, réplique plus tôt les données qui chauffent (profil par agent, pas un seuil)')
	parser.add_argument('--reward-cost-linear', type=float, default=None,
	                    help='Poids du coût linéaire continu (pénalité ∝ coût réel/C_SLA, même sous contrat) — priorise le coût')
	parser.add_argument('--reward-cost-viol-step', type=float, default=None,
	                    help='Pénalité forfaitaire par requête qui dépasse le C_SLA du contrat (défaut Java : 10)')
	parser.add_argument('--reward-cost-over', type=float, default=None,
					   help='Override rewardCostOver weight (default Java: 15.0) — plus élevé pénalise davantage le coût récurrent, encourage plus de réplication')
    
	args = parser.parse_args()
	os.makedirs('models', exist_ok=True)
	if args.output is None:
		args.output = ('models/qlearning_cloudsim.pkl' if args.agent == 'qlearning'
		               else 'models/rainbow_cloudsim.pt')
    
	print()
	print("🎓 TCDRM RL Training with CloudSimPlus")
	print(f"   Agent: {args.agent.upper()}")
	print(f"   Episodes: {args.episodes}")
	print(f"   Query type: mixed (simple + complex, single adaptive model)")
	print(f"   Server port: {args.port}")
	print(f"   Output: {args.output}")
	print()

	try:
		cfg = {}
		# Identité de l'agent : chaque agent entraîne et possède SES Q-tables de
		# méta-seuils (meta_threshold_<agent>_*.qtable) — les politiques de seuil
		# de Q-Learning et Rainbow sont apprises indépendamment.
		cfg['agentTag'] = str(args.agent)
		if args.max_episode_length is not None:
			cfg['maxEpisodeLength'] = int(args.max_episode_length)
		# Forward dynamic warmup config
		cfg['warmupQueries'] = int(max(0, args.warmup_queries))
		# Verrou d'ouverture : TcdrmConstants.OPENING_WINDOW_QUERIES pour Q-Learning, dont
		# la grille ne distingue pas l'instant de la requête ; Rainbow (état continu)
		# est entraîné sans verrou. Toujours envoyé : le serveur garde ses réglages.
		if args.opening_window is None:
			args.opening_window = 40 if args.agent == 'qlearning' else 0
		cfg['openingWindowQueries'] = int(max(0, args.opening_window))
		cfg['warmupStrategy'] = str(args.warmup_strategy)
		cfg['warmupRandomProb'] = float(max(0.0, min(1.0, args.warmup_random_prob)))
		cfg['exploringStartProb'] = float(max(0.0, min(1.0, args.exploring_start_prob)))
		if args.reward_cost_over is not None:
			cfg['rewardCostOver'] = float(args.reward_cost_over)
		if args.reward_cost_linear is not None:
			cfg['rewardCostLinear'] = float(args.reward_cost_linear)
		if args.reward_cost_viol_step is not None:
			cfg['rewardCostViolStep'] = float(args.reward_cost_viol_step)
		if args.reward_unpopular_holding is not None:
			cfg['rewardUnpopularHolding'] = float(args.reward_unpopular_holding)
		if args.reward_correct_trigger is not None:
			cfg['rewardCorrectTrigger'] = float(args.reward_correct_trigger)
		if args.cost_routing_tolerance is not None:
			cfg['costRoutingToleranceMs'] = float(args.cost_routing_tolerance)
		if args.agent == 'qlearning':
			env = CloudSimQLearningEnv(port=args.port, complex=False, config=cfg)
			env_complex = CloudSimQLearningEnv(port=args.port, complex=True, config=cfg)
			train_qlearning(env, args.episodes, args.output, seed_base=args.seed_base, env_complex=env_complex)
		else:  # rainbow
			env = CloudSimEnv(port=args.port, complex=False, config=cfg)
			env_complex = CloudSimEnv(port=args.port, complex=True, config=cfg)
			train_rainbow(env, args.episodes, args.output, seed_base=args.seed_base, env_complex=env_complex,
			              max_episode_length=int(args.max_episode_length or 1000))
		env.close()
		env_complex.close()
	except ConnectionError as e:
		print(f"\n❌ {e}")
		print("\nMake sure to start the Java TrainingServer first:")
		print("  mvn exec:java -Dexec.mainClass=org.tcdrm.adaptive.training.TrainingServer")
		sys.exit(1)
	except KeyboardInterrupt:
		print("\n\n🛑 Training interrupted by user")
		sys.exit(0)


if __name__ == '__main__':
	main()
