"""
Environnement Gymnasium utilisant CloudSimPlus (Java) pour les simulations.

L'entraînement RL se fait côté Python (Gymnasium), mais les simulations
sont exécutées dans CloudSimPlus Java. Cela garantit que l'entraînement
et l'inférence utilisent exactement le même environnement.

Architecture:
- Python (Gymnasium): gère l'entraînement, les récompenses, l'exploration
- Java (CloudSimPlus): exécute les simulations réelles via Py4J
"""

import gymnasium as gym
import numpy as np
from gymnasium import spaces
from py4j.java_gateway import JavaGateway, GatewayParameters


class CloudSimEnv(gym.Env):
	"""
	Environnement Gymnasium connecté à CloudSimPlus via Py4J.
    
	Actions:
		0: NOOP - Ne rien faire
		1: REPLICATE - Créer un réplica
		2: DELETE - Supprimer un réplica
    
	Observations (9 dimensions, aligné TcdrmSimulation.buildRLState):
		0: latency - Latence normalisée
		1: budget - Budget restant normalisé
		2: replicas - Réplicas normalisés
		3: popularity - Popularité EMA normalisée
		4: cost - Coût total normalisé
		5: t_sla_violation
		6: c_sla_violation
		7: progress - Progression des requêtes / MAX_QUERIES
		8: p_sla_progress - Indice requête / P_SLA (papier, seuil 200)
	"""
    
	metadata = {"render_modes": ["human"]}
    
	def __init__(self, port: int = 25335, complex: bool = False, seed: int = None, config: dict | None = None):
		"""
		Initialise l'environnement CloudSim.
        
		Args:
			port: Port du serveur Java TrainingServer
			complex: True pour requêtes complexes, False pour simples
			seed: Graine aléatoire pour reproductibilité
		"""
		super().__init__()
        
		self.port = port
		self.complex = complex
		self._seed = seed if seed is not None else 42
		self._config = config or {}
        
		# Espaces d'action et d'observation
		self.action_space = spaces.Discrete(3)  # NOOP, REPLICATE, DELETE
		self.observation_space = spaces.Box(
			low=np.zeros(9, dtype=np.float32),
			high=np.array([5.0, 5.0, 5.0, 5.0, 5.0, 1.0, 1.0, 1.0, 1.0], dtype=np.float32),
			dtype=np.float32,
		)
        
		# Connexion Java (lazy initialization)
		self._gateway = None
		self._server = None
		self._connected = False
        
		# Statistiques
		self.episode_count = 0
		self.total_steps = 0
		self.episode_rewards = []
    
	def _connect(self):
		"""Connexion au serveur Java."""
		if self._connected:
			return
        
		try:
			print(f"📡 Connecting to CloudSimPlus training server (port {self.port})...")
			self._gateway = JavaGateway(
				gateway_parameters=GatewayParameters(port=self.port, auto_convert=True)
			)
			self._server = self._gateway.entry_point
			# Aligner avec le repo: configurer la simulation côté Java si fourni
			if self._config:
				try:
					self._server.configureSimulation(dict(self._config))
				except Exception:
					pass
			self._connected = True
			print("✅ Connected to CloudSimPlus!")
		except Exception as e:
			raise ConnectionError(
				f"Cannot connect to Java TrainingServer on port {self.port}. "
				f"Make sure to start it first with: "
				f"mvn exec:java -Dexec.mainClass=org.tcdrm.adaptive.training.TrainingServer"
			) from e
    
	def reset(self, seed=None, options=None):
		"""
		Réinitialise l'environnement.
        
		Returns:
			observation: État initial
			info: Informations supplémentaires
		"""
		super().reset(seed=seed)
        
		if seed is not None:
			self._seed = seed
        
		self._connect()
        
		# Préférence: reset structuré si disponible (à la manière du repo de référence)
		try:
			result = self._server.resetStructured(bool(self.complex), int(self._seed))
			state = result.getState()
			infoJ = result.getInfo()
		except Exception:
			# Fallback compat: ancienne API
			self._server.createEnvironment(self._seed, self.complex)
			state = self._server.reset(self.complex)
			infoJ = None
		observation = np.array(list(state), dtype=np.float32)
        
		self.episode_count += 1
        
		info = {
			"episode": self.episode_count,
			"complex": self.complex,
			"seed": self._seed
		}
		if infoJ is not None:
			try:
				info.update({
					"last_latency_ms": float(infoJ.getLastLatencyMs()),
					"query": int(infoJ.getCurrentQuery()),
					"cumulative_reward": float(infoJ.getCumulativeReward()),
					"sla_violations": int(infoJ.getSlaViolations()),
					"cumulative_cost": float(infoJ.getCumulativeCost()),
					"replica_count": int(infoJ.getReplicaCount()),
					"budget_remaining": float(infoJ.getBudgetRemaining()),
					"reward_wait_time": float(infoJ.getRewardWaitTime()),
					"reward_unutilization": float(infoJ.getRewardUnutilization()),
					"reward_queue_penalty": float(infoJ.getRewardQueuePenalty()),
					"reward_invalid_action": float(infoJ.getRewardInvalidAction()),
					"invalid_action_taken": bool(infoJ.getInvalidActionTaken()),
					"assignment_success": bool(infoJ.getAssignmentSuccess()),
					"replica_changes": int(infoJ.getReplicaChanges())
				})
			except Exception:
				pass
		# Fallback: query metrics directly if missing
		missing = [k for k in ("sla_violations","cumulative_cost","replica_count","budget_remaining") if k not in info]
		if missing:
			try:
				info.setdefault("sla_violations", int(self._server.getSlaViolations(bool(self.complex))))
				info.setdefault("cumulative_cost", float(self._server.getCumulativeCost(bool(self.complex))))
				info.setdefault("replica_count", int(self._server.getReplicaCount(bool(self.complex))))
				info.setdefault("budget_remaining", float(self._server.getBudgetRemaining(bool(self.complex))))
			except Exception:
				pass
		# Action mask depuis Java
		try:
			mask = self._server.getActionMask(bool(self.complex))
			info["action_mask"] = [bool(x) for x in list(mask)]
		except Exception:
			pass
        
		return observation, info
    
	def step(self, action: int):
		"""
		Exécute une action dans l'environnement.
        
		Args:
			action: 0=NOOP, 1=REPLICATE, 2=DELETE
        
		Returns:
			observation: Nouvel état
			reward: Récompense
			terminated: True si l'épisode est terminé
			truncated: False (pas de troncature)
			info: Informations supplémentaires
		"""
		if not self._connected:
			raise RuntimeError("Environment not connected. Call reset() first.")
        
		# Exécuter l'action dans CloudSimPlus
		# Préférence: step structuré si dispo
		try:
			sr = self._server.stepStructured(int(action), bool(self.complex))
			state = np.array(list(sr.getState()), dtype=np.float32)
			reward = float(sr.getReward())
			done = bool(sr.isTerminated())
			infoJ = sr.getInfo()
		except Exception:
			result = self._server.step(int(action), self.complex)
			result_list = list(result)
			state = np.array(result_list[:-2], dtype=np.float32)
			reward = float(result_list[-2])
			done = bool(result_list[-1] > 0.5)
			infoJ = None
        
		self.total_steps += 1
        
		info = {}
		if infoJ is not None:
			try:
				info.update({
					"last_latency_ms": float(infoJ.getLastLatencyMs()),
					"query": int(infoJ.getCurrentQuery()),
					"cumulative_reward": float(infoJ.getCumulativeReward()),
					"sla_violations": int(infoJ.getSlaViolations()),
					"cumulative_cost": float(infoJ.getCumulativeCost()),
					"replica_count": int(infoJ.getReplicaCount()),
					"budget_remaining": float(infoJ.getBudgetRemaining()),
					"reward_wait_time": float(infoJ.getRewardWaitTime()),
					"reward_unutilization": float(infoJ.getRewardUnutilization()),
					"reward_queue_penalty": float(infoJ.getRewardQueuePenalty()),
					"reward_invalid_action": float(infoJ.getRewardInvalidAction()),
					"invalid_action_taken": bool(infoJ.getInvalidActionTaken()),
					"assignment_success": bool(infoJ.getAssignmentSuccess()),
					"replica_changes": int(infoJ.getReplicaChanges())
				})
			except Exception:
				pass
		# Fallback: query metrics directly if missing
		missing = [k for k in ("sla_violations","cumulative_cost","replica_count","budget_remaining") if k not in info]
		if missing:
			try:
				info.setdefault("sla_violations", int(self._server.getSlaViolations(bool(self.complex))))
				info.setdefault("cumulative_cost", float(self._server.getCumulativeCost(bool(self.complex))))
				info.setdefault("replica_count", int(self._server.getReplicaCount(bool(self.complex))))
				info.setdefault("budget_remaining", float(self._server.getBudgetRemaining(bool(self.complex))))
			except Exception:
				pass
		# Action mask depuis Java
		try:
			mask = self._server.getActionMask(bool(self.complex))
			info["action_mask"] = [bool(x) for x in list(mask)]
		except Exception:
			pass
		else:
			info = {
				"query": self._server.getCurrentQuery(self.complex),
				"cumulative_reward": self._server.getCumulativeReward(self.complex)
			}
        
		if done:
			self.episode_rewards.append(info["cumulative_reward"])
        
		return state, reward, done, False, info
    
	def close(self):
		"""Ferme la connexion Java."""
		if self._gateway is not None:
			try:
				self._gateway.close()
			except:
				pass
			self._connected = False
			print("🔌 Disconnected from CloudSimPlus")
    
	def get_stats(self):
		"""Retourne les statistiques d'entraînement."""
		return {
			"episodes": self.episode_count,
			"total_steps": self.total_steps,
			"avg_reward": np.mean(self.episode_rewards[-100:]) if self.episode_rewards else 0,
			"last_reward": self.episode_rewards[-1] if self.episode_rewards else 0
		}


class CloudSimQLearningEnv(CloudSimEnv):
	"""
	Environnement CloudSim avec états discrets pour Q-Learning.
    
	L'état continu (8 dimensions) est discrétisé en 243 états (3^5).
	"""
    
	def __init__(self, port: int = 25335, complex: bool = False, seed: int = None, config: dict | None = None):
		super().__init__(port=port, complex=complex, seed=seed, config=config)
        
		# Espace d'observation discret pour Q-Learning
		self.observation_space = spaces.Discrete(243)
    
	def _discretize_state(self, state: np.ndarray) -> int:
		"""
		Discrétise l'état continu en index (0-242).
        
		Dimensions:
		- RT: temps de réponse (0=bon, 1=moyen, 2=mauvais)
		- COST: coût (0=faible, 1=moyen, 2=élevé)
		- POP: popularité (0=faible, 1=moyenne, 2=haute)
		- BUD: budget (0=confortable, 1=tendu, 2=critique)
		- NET: réseau/réplicas (0=local, 1=mixte, 2=distant)
		"""
		latency = state[0]
		budget = state[1]
		replicas = state[2]
		p_sla = float(state[8]) if len(state) > 8 else 0.0
		popularity = max(float(state[3]), p_sla)
		cost = state[4]
        
		# RT
		if latency < 0.4:
			rt = 0
		elif latency < 0.7:
			rt = 1
		else:
			rt = 2
        
		# COST
		if cost < 0.4:
			cost_bin = 0
		elif cost < 0.7:
			cost_bin = 1
		else:
			cost_bin = 2
        
		# POP
		if popularity < 0.33:
			pop_bin = 0
		elif popularity < 0.67:
			pop_bin = 1
		else:
			pop_bin = 2
        
		# BUD
		if budget >= 0.6:
			bud = 0
		elif budget >= 0.3:
			bud = 1
		else:
			bud = 2
        
		# NET
		if replicas >= 0.5:
			net = 0
		elif replicas > 0:
			net = 1
		else:
			net = 2
        
		return rt * 81 + cost_bin * 27 + pop_bin * 9 + bud * 3 + net
    
	def reset(self, seed=None, options=None):
		"""Reset et retourne l'état discret."""
		obs, info = super().reset(seed=seed, options=options)
		return self._discretize_state(obs), info
    
	def step(self, action: int):
		"""Step et retourne l'état discret."""
		obs, reward, terminated, truncated, info = super().step(action)
		return self._discretize_state(obs), reward, terminated, truncated, info
