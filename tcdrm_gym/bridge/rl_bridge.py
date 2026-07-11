"""
Python RL Bridge for Java/CloudSim via Py4J.

Charge les modèles RL entraînés avec CloudSimPlus et les expose à Java.
"""

import os
import numpy as np
import torch

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.rainbow_dqn_agent import RainbowDQNAgent
from .adaptive_strategy import AdaptiveState


class PythonRLBridge:
    def __init__(self, qlearning_model_path=None, rainbow_model_path=None):
        self._ql_model_path      = qlearning_model_path
        self._rainbow_model_path = rainbow_model_path

        self.qlearning_agent = SimpleQLearningAgent(
            n_states=1458,
            n_actions=3,
            learning_rate=0.15,
            discount_factor=0.95,
            epsilon_start=0.075,
            epsilon_min=0.01,
            epsilon_decay=0.997,
            use_double_q=True,
            adaptive_lr=True
        )
        if qlearning_model_path and os.path.exists(qlearning_model_path):
            print(f"Loading Q-Learning model: {qlearning_model_path}")
            self.qlearning_agent.load(qlearning_model_path)
            self.qlearning_agent.epsilon = 0.075
            stats = self.qlearning_agent.get_stats()
            print(f"Q-Learning loaded (states explored: {stats['states_explored']}/{self.qlearning_agent.n_states})")
        else:
            print("Q-Learning: no trained model, using fresh agent")

        self.rainbow_agent = RainbowDQNAgent(
            state_dim=9,
            action_dim=3,
            hidden_dims=[256, 256],
            n_step=3,
            min_buffer_size=500,
            normalize_rewards=True,
            use_distributional=True,
        )
        if rainbow_model_path and os.path.exists(rainbow_model_path):
            print(f"Loading Rainbow DQN model: {rainbow_model_path}")
            self.rainbow_agent.load(rainbow_model_path)
            print("Rainbow DQN loaded")
        else:
            print("Rainbow DQN: no trained model, using fresh agent")

        self._ql_state         = AdaptiveState()
        self._rainbow_state    = AdaptiveState()
        self._ql_last_state    = None
        self._ql_last_action   = None
        self._rainbow_last_state  = None
        self._rainbow_last_action = None
        self._thrashing_window = 10
        self._ql_episode       = 0
        self._rainbow_episode  = 0
        print("Python bridge initialized")

    def resetCounters(self):
        self._ql_state.reset(initial_p_threshold=0.5)
        self._rainbow_state.reset(initial_p_threshold=0.4)
        self._ql_last_state    = None
        self._ql_last_action   = None
        self._rainbow_last_state  = None
        self._rainbow_last_action = None

    def selectActionQLearning(self, state_array):
        try:
            state = self._parse_state(state_array)
            if self._is_thrashing(self._ql_state):
                self._record_action(self._ql_state, 0)
                return 0
            discrete_state = self._discretize_state_for_qlearning(state)
            valid_actions = self._physical_constraints(state)
            action = self.qlearning_agent.select_action(discrete_state, valid_actions=valid_actions, training=True)
            self._ql_last_state  = discrete_state
            self._ql_last_action = int(action)
            self._record_action(self._ql_state, action)
            return int(action)
        except Exception as e:
            print(f"Q-Learning error: {e}")
            return 0

    def selectActionRainbow(self, state_array):
        try:
            state = self._parse_state(state_array)
            if self._is_thrashing(self._rainbow_state):
                self._record_action(self._rainbow_state, 0)
                return 0
            continuous_state = self._build_rainbow_state(state)
            action_mask = self._physical_mask(state)
            action = self.rainbow_agent.select_action(continuous_state, training=True, action_mask=action_mask)
            self._rainbow_last_state  = continuous_state
            self._rainbow_last_action = int(action)
            self._record_action(self._rainbow_state, action)
            return int(action)
        except Exception as e:
            print(f"Rainbow DQN error: {e}")
            return 0

    def updateQLearning(self, reward, next_state_array, done):
        if self._ql_last_state is None:
            return
        next_state = self._parse_state(next_state_array)
        next_discrete = self._discretize_state_for_qlearning(next_state)
        self.qlearning_agent.update(self._ql_last_state, self._ql_last_action, float(reward), next_discrete, bool(done))
        if done:
            self.qlearning_agent.decay_epsilon()
            self._ql_episode += 1
            if self._ql_episode % 10 == 0:
                stats = self.qlearning_agent.get_stats()
                print(f"Q-Learning ep={self._ql_episode} eps={stats['epsilon']:.3f} explored={stats['states_explored']}")

    def updateRainbow(self, reward, next_state_array, done):
        if self._rainbow_last_state is None:
            return
        next_state = self._parse_state(next_state_array)
        next_continuous = self._build_rainbow_state(next_state)
        self.rainbow_agent.update(
            self._rainbow_last_state, self._rainbow_last_action,
            float(reward), next_continuous, bool(done))
        if done:
            self._rainbow_episode += 1
            if self._rainbow_episode % 10 == 0:
                stats = self.rainbow_agent.get_network_stats()
                print(f"Rainbow DQN ep={self._rainbow_episode} sigma={stats.get('avg_noisy_sigma', 0):.4f}")

    def saveModels(self):
        if self._ql_model_path:
            self.qlearning_agent.save(self._ql_model_path)
            print(f"Q-Learning saved to {self._ql_model_path}")
        if self._rainbow_model_path:
            self.rainbow_agent.save(self._rainbow_model_path)
            print(f"Rainbow DQN saved to {self._rainbow_model_path}")

    def _discretize_state_for_qlearning(self, state: dict) -> int:
        """Miroir exact de CloudSimQLearningEnv._discretize_state (cloudsim_env.py) : mêmes
        dimensions, mêmes seuils, même encodage — sans cela, le Q-table appris à l'entraînement
        (via TrainingServer/py4j) ne correspondrait à rien de cohérent une fois réutilisé ici
        pour l'évaluation (BenchmarkRunner.runRL)."""
        latency = state['latency']
        if latency < 0.4:   rt = 0
        elif latency < 0.7: rt = 1
        else:                rt = 2
        cost = state['total_cost']
        if cost < 0.4:   cost_bin = 0
        elif cost < 0.7: cost_bin = 1
        else:             cost_bin = 2
        progress = state['query_progress']
        if progress < 0.33:   prog_bin = 0
        elif progress < 0.67: prog_bin = 1
        else:                  prog_bin = 2
        budget = state['budget']
        if budget >= 0.6:  bud = 0
        elif budget >= 0.3: bud = 1
        else:               bud = 2
        replicas = state['replicas_normalized']
        if replicas >= 0.5: net = 0
        elif replicas > 0:  net = 1
        else:               net = 2
        gain = state['normalized_popularity']
        if gain < 0.2:   gain_bin = 0
        elif gain < 0.6: gain_bin = 1
        else:             gain_bin = 2
        complex_bit = 1 if state.get('is_complex', 0.0) >= 0.5 else 0
        return (rt * 243 + cost_bin * 81 + prog_bin * 27 + bud * 9 + net * 3 + gain_bin) + complex_bit * 729

    def _build_rainbow_state(self, state: dict) -> np.ndarray:
        """Construit le vecteur d'état 9D pour Rainbow DQN (aligné buildRLState Java)."""
        return np.array([
            state['latency'],
            state['budget'],
            state['replicas_normalized'],
            state['total_cost'],
            state['t_sla_violation'],
            state['c_sla_violation'],
            state['query_progress'],
            state['normalized_popularity'],
            state['is_complex'],
        ], dtype=np.float32)

    def _parse_state(self, state_array):
        state_list = list(state_array) if hasattr(state_array, '__iter__') else [state_array]
        return {
            'latency':               float(state_list[0]),
            'budget':                float(state_list[1]),
            'replicas_normalized':   float(state_list[2]),
            'total_cost':            float(state_list[3]) if len(state_list) > 3 else 0.0,
            't_sla_violation':       float(state_list[4]) if len(state_list) > 4 else 0.0,
            'c_sla_violation':       float(state_list[5]) if len(state_list) > 5 else 0.0,
            'query_progress':        float(state_list[6]) if len(state_list) > 6 else 0.0,
            'normalized_popularity': float(state_list[7]) if len(state_list) > 7 else 0.0,
            'is_complex':            float(state_list[8]) if len(state_list) > 8 else 0.0,
        }

    def _is_thrashing(self, adaptive_state: AdaptiveState) -> bool:
        recent = adaptive_state.action_history[-self._thrashing_window:]
        if len(recent) < self._thrashing_window:
            return False
        recent_creates = sum(1 for a in recent if a == 1)
        recent_deletes = sum(1 for a in recent if a == 2)
        return recent_creates >= 2 and recent_deletes >= 2

    def _record_action(self, adaptive_state: AdaptiveState, action: int):
        adaptive_state.action_history.append(action)
        if len(adaptive_state.action_history) > 20:
            adaptive_state.action_history.pop(0)
        adaptive_state.query_counter += 1
        if action == 1:
            adaptive_state.last_replicate_q = adaptive_state.query_counter
        elif action == 2:
            adaptive_state.last_delete_q = adaptive_state.query_counter

    def _physical_mask(self, state: dict) -> np.ndarray:
        mask = np.ones(3, dtype=np.float32)
        replicas = float(state['replicas_normalized'])
        budget   = float(state['budget'])
        if replicas <= 0.0:
            mask[2] = 0.0
        if replicas >= 1.0 or budget <= 0.0:
            mask[1] = 0.0
        return mask

    def _physical_constraints(self, state: dict):
        mask = self._physical_mask(state)
        return [i for i, v in enumerate(mask) if v > 0.5]

    def isQLearningReady(self):
        return self.qlearning_agent is not None

    def isRainbowReady(self):
        return self.rainbow_agent is not None

    def getModelInfo(self):
        info = []
        if self.qlearning_agent:
            stats = self.qlearning_agent.get_stats()
            info.append(f"Q-Learning: {stats['states_explored']}/{stats['training_steps']} states")
        if self.rainbow_agent:
            info.append("Rainbow DQN: loaded")
        return " | ".join(info) if info else "No models loaded"

    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
