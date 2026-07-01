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
from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.rainbow_dqn_agent import RainbowDQNAgent


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


def train_qlearning(env: CloudSimQLearningEnv, episodes: int, save_path: str, seed_base: int = 42):
	print("=" * 70)
	print("Q-LEARNING TRAINING with CloudSimPlus")
	print("=" * 70)
    
	agent = SimpleQLearningAgent(
		n_states=729,
		n_actions=3,
		learning_rate=float(os.environ.get('TCDRM_Q_LR', 0.08)),
		discount_factor=float(os.environ.get('TCDRM_Q_GAMMA', 0.99)),
		epsilon_start=float(os.environ.get('TCDRM_Q_EPS0', 1.0)),
		epsilon_min=float(os.environ.get('TCDRM_Q_EPS_MIN', 0.05)),
		epsilon_decay=float(os.environ.get('TCDRM_Q_EPS_DECAY', 0.995)),
		use_double_q=bool(int(os.environ.get('TCDRM_Q_DOUBLE', '1'))),
		adaptive_lr=bool(int(os.environ.get('TCDRM_Q_ADAPTIVE_LR', '1'))),
		epsilon_schedule=os.environ.get('TCDRM_Q_EPS_SCHED', 'linear'),
		epsilon_linear_steps=int(os.environ.get('TCDRM_Q_EPS_STEPS', '5000')),
		lambda_trace=float(os.environ.get('TCDRM_Q_LAMBDA', '0.6')),
		optimistic_init=float(os.environ.get('TCDRM_Q_OPT_INIT', '1.0'))
	)
    
	best_reward = float('-inf')
	rewards_history = []
	log_dir = ensure_log_dir('qlearning')
	csv_logger = CSVLogger(
		os.path.join(log_dir, 'progress.csv'),
		[
			'episode','reward','avg10','epsilon','states_explored',
			'sla_violations','cumulative_cost','replica_count','budget_remaining',
			'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action'
		]
	)
	# Optional TensorBoard
	tb_writer = None
	try:
		from torch.utils.tensorboard import SummaryWriter  # type: ignore
		tb_writer = SummaryWriter(log_dir=log_dir)
	except Exception:
		tb_writer = None
    
	for episode in range(episodes):
		state, info = env.reset(seed=seed_base + episode)
		agent.start_episode()
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
			next_state, reward, done, truncated, info = env.step(action)
			agent.update(state, action, reward, next_state, done)
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
			'states_explored': stats['states_explored']
		}
		if isinstance(last_info, dict):
			for k in ['sla_violations','cumulative_cost','replica_count','budget_remaining',
					  'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action']:
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
						  'reward_wait_time','reward_unutilization','reward_queue_penalty','reward_invalid_action']:
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
			exploration_pct = stats['states_explored'] / 729 * 100
			print(
				f"Episode {episode:4d}/{episodes} | Reward: {episode_reward:8.2f} | Avg(10): {avg_reward:8.2f} | "
				f"ε: {stats['epsilon']:.3f} | States: {stats['states_explored']} ({exploration_pct:.1f}%){metrics_tail}"
			)
        
		if avg_reward > best_reward and episode >= 10:
			best_reward = avg_reward
			agent.save(save_path)
			# Save meta
			best_meta = to_builtin({
				'episode': episode,
				'best_avg10_reward': best_reward,
				'epsilon': stats['epsilon'],
				'states_explored': stats['states_explored'],
				'metrics_tail': last_info if isinstance(last_info, dict) else {}
			})
			with open(os.path.join(log_dir, 'best_meta.json'), 'w') as f:
				json.dump(best_meta, f, indent=2)
			print(f"  💾 New best model saved (avg reward: {best_reward:.2f}) → {save_path}")
    
	final_path = save_path.replace('.pkl', '_final.pkl')
	agent.save(final_path)
	agent.save(save_path)  # also save under the canonical path expected by the benchmark

	csv_logger.close()
	if tb_writer is not None:
		try:
			tb_writer.flush(); tb_writer.close()
		except Exception:
			pass
	print()
	print("=" * 70)
	print("TRAINING COMPLETE")
	print(f"  Best avg reward: {best_reward:.2f}")
	print(f"  Final epsilon: {agent.epsilon:.4f}")
	print(f"  States explored: {stats['states_explored']}/729")
	print(f"  Model saved to: {save_path}")
	print("=" * 70)
    
	return agent, rewards_history


def train_rainbow(env: CloudSimEnv, episodes: int, save_path: str, seed_base: int = 42, agent: RainbowDQNAgent = None):
	"""
	Entraîne un agent Rainbow DQN complet (6/6 composants Rainbow) :
	  Double DQN + Dueling + PER + N-step(3) + NoisyLinear + C51 Distributional.

	NoisyLinear remplace epsilon-greedy : l'agent explore via son bruit appris (sigma).
	C51 modélise la distribution complète des retours → meilleure politique sous incertitude.
	"""
	print("=" * 70)
	print("RAINBOW DQN TRAINING with CloudSimPlus")
	print("  Components: Double DQN + Dueling + PER + N-step + Noisy + C51")
	print("=" * 70)

	if agent is None:
		agent = RainbowDQNAgent(
			state_dim=8,
			action_dim=3,
			hidden_dims=[128, 128],
			learning_rate=0.0001,
			discount_factor=0.99,
			buffer_capacity=100000,
			batch_size=32,
			tau=0.005,
			gradient_clip=10.0,
			lr_scheduler='cosine',
			scheduler_params={'T_max': 20000, 'eta_min': 1e-6},
			n_step=3,
			min_buffer_size=1500,
			normalize_rewards=True,
			sigma_init=0.65,
			use_distributional=True,
			n_atoms=51,
			v_min=-15.0,
			v_max=6.0,
		)

	best_reward = float('-inf')
	rewards_history = []
	log_dir = ensure_log_dir('rainbow')
	csv_logger = CSVLogger(
		os.path.join(log_dir, 'progress.csv'),
		[
			'episode', 'reward', 'avg10', 'buffer', 'avg_noisy_sigma',
			'sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining',
			'reward_wait_time', 'reward_unutilization', 'reward_queue_penalty',
			'reward_invalid_action', 'avg_loss'
		]
	)
	tb_writer = None
	try:
		from torch.utils.tensorboard import SummaryWriter
		tb_writer = SummaryWriter(log_dir=log_dir)
	except Exception:
		pass

	for episode in range(episodes):
		state, info = env.reset(seed=seed_base + episode)
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
			next_state, reward, done, truncated, info = env.step(action)
			agent.update(state, action, reward, next_state, done)
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
			'buffer': len(agent.replay_buffer),
			'avg_noisy_sigma': net_stats.get('avg_noisy_sigma', 0),
			'avg_loss': net_stats.get('avg_loss', 0)
		}
		if isinstance(last_info, dict):
			for k in ['sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining',
					  'reward_wait_time', 'reward_unutilization', 'reward_queue_penalty', 'reward_invalid_action']:
				if k in last_info:
					row[k] = last_info[k]
		csv_logger.log(to_builtin(row))

		if tb_writer is not None:
			tb_writer.add_scalar('reward/episode', episode_reward, episode)
			tb_writer.add_scalar('reward/avg10', float(avg_reward), episode)
			tb_writer.add_scalar('rainbow/buffer', float(len(agent.replay_buffer)), episode)
			tb_writer.add_scalar('rainbow/avg_loss', float(net_stats.get('avg_loss', 0)), episode)
			if 'avg_noisy_sigma' in net_stats:
				tb_writer.add_scalar('rainbow/noisy_sigma', float(net_stats['avg_noisy_sigma']), episode)
			if isinstance(last_info, dict):
				for k in ['sla_violations', 'cumulative_cost', 'replica_count', 'budget_remaining']:
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
				f"Avg(10): {avg_reward:8.2f} | Buffer: {len(agent.replay_buffer)}"
				f"{sigma_str}{metrics_tail}"
			)

		if avg_reward > best_reward and episode >= 10:
			best_reward = avg_reward
			agent.save(save_path)
			best_meta = to_builtin({
				'episode': episode,
				'best_avg10_reward': best_reward,
				'buffer': len(agent.replay_buffer),
				'network': net_stats,
				'metrics_tail': last_info if isinstance(last_info, dict) else {}
			})
			with open(os.path.join(log_dir, 'best_meta.json'), 'w') as f:
				json.dump(best_meta, f, indent=2)
			print(f"  New best model saved (avg reward: {best_reward:.2f}) -> {save_path}")

	final_path = save_path.replace('.pt', '_final.pt')
	agent.save(final_path)
	agent.save(save_path)  # also save under the canonical path expected by the benchmark

	csv_logger.close()
	if tb_writer is not None:
		try:
			tb_writer.flush(); tb_writer.close()
		except Exception:
			pass
	print()
	print("=" * 70)
	print("RAINBOW TRAINING COMPLETE")
	print(f"  Best avg reward: {best_reward:.2f}")
	print(f"  Mode: {net_stats.get('mode', 'rainbow')}")
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
					   help='Use complex queries')
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
	parser.add_argument('--seed-base', type=int, default=42,
					   help='Base seed; episode seed = seed_base + episode (decorrelate agents)')
    
	args = parser.parse_args()
	os.makedirs('models', exist_ok=True)
	if args.output is None:
		args.output = ('models/qlearning_cloudsim.pkl' if args.agent == 'qlearning'
		               else 'models/rainbow_cloudsim.pt')
    
	print()
	print("🎓 TCDRM RL Training with CloudSimPlus")
	print(f"   Agent: {args.agent.upper()}")
	print(f"   Episodes: {args.episodes}")
	print(f"   Query type: {'complex' if args.complex else 'simple'}")
	print(f"   Server port: {args.port}")
	print(f"   Output: {args.output}")
	print()
    
	try:
		cfg = {}
		if args.max_episode_length is not None:
			cfg['maxEpisodeLength'] = int(args.max_episode_length)
		# Forward dynamic warmup config
		cfg['warmupQueries'] = int(max(0, args.warmup_queries))
		cfg['warmupStrategy'] = str(args.warmup_strategy)
		cfg['warmupRandomProb'] = float(max(0.0, min(1.0, args.warmup_random_prob)))
		if args.agent == 'qlearning':
			env = CloudSimQLearningEnv(port=args.port, complex=args.complex, config=cfg)
			train_qlearning(env, args.episodes, args.output, seed_base=args.seed_base)
		else:  # rainbow
			env = CloudSimEnv(port=args.port, complex=args.complex, config=cfg)
			train_rainbow(env, args.episodes, args.output, seed_base=args.seed_base)
		env.close()
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
