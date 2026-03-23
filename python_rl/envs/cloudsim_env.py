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
    
    Observations (8 dimensions):
        0: latency - Latence normalisée (0-1)
        1: budget - Budget restant normalisé (0-1)
        2: replicas - Nombre de réplicas normalisé (0-1)
        3: popularity - Popularité normalisée (0-1)
        4: cost - Coût total normalisé (0-1)
        5: t_sla_violation - Violation T_SLA (0-1)
        6: c_sla_violation - Violation C_SLA (0-1)
        7: progress - Progression des requêtes (0-1)
    """
    
    metadata = {"render_modes": ["human"]}
    
    def __init__(self, port: int = 25335, complex: bool = False, seed: int = None):
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
        
        # Espaces d'action et d'observation
        self.action_space = spaces.Discrete(3)  # NOOP, REPLICATE, DELETE
        self.observation_space = spaces.Box(
            low=0.0, high=1.0, shape=(8,), dtype=np.float32
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
                gateway_parameters=GatewayParameters(port=self.port)
            )
            self._server = self._gateway.entry_point
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
        
        # Créer un nouvel environnement Java avec la graine
        self._server.createEnvironment(self._seed, self.complex)
        
        # Réinitialiser et obtenir l'état initial
        state = self._server.reset(self.complex)
        observation = np.array(list(state), dtype=np.float32)
        
        self.episode_count += 1
        
        info = {
            "episode": self.episode_count,
            "complex": self.complex,
            "seed": self._seed
        }
        
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
        result = self._server.step(int(action), self.complex)
        result_list = list(result)
        
        # Parser le résultat: [state..., reward, done]
        state = np.array(result_list[:-2], dtype=np.float32)
        reward = float(result_list[-2])
        done = bool(result_list[-1] > 0.5)
        
        self.total_steps += 1
        
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
    
    def __init__(self, port: int = 25335, complex: bool = False, seed: int = None):
        super().__init__(port=port, complex=complex, seed=seed)
        
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
        popularity = state[3]
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
