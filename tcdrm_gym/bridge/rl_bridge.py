"""
Python RL Bridge for Java/CloudSim via Py4J.

Charge les modèles RL entraînés avec CloudSimPlus et les expose à Java.
"""

import os
import numpy as np

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.rainbow_dqn_agent import RainbowDQNAgent


class PythonRLBridge:
    def __init__(self, qlearning_model_path=None, rainbow_model_path=None):
        self._ql_model_path      = qlearning_model_path
        self._rainbow_model_path = rainbow_model_path

        # ε=0 : protocole d'évaluation documenté (BenchmarkRunner) — exploitation greedy
        # pure + apprentissage online continu. Toute exploration résiduelle en éval
        # injecterait des actions aléatoires dans les courbes du benchmark.
        self.qlearning_agent = SimpleQLearningAgent(
            n_states=1458,
            n_actions=3,
            learning_rate=0.15,
            discount_factor=0.95,
            epsilon_start=0.0,
            epsilon_min=0.0,
            epsilon_decay=1.0,
            use_double_q=True,
            adaptive_lr=True
        )
        if qlearning_model_path and os.path.exists(qlearning_model_path):
            print(f"Loading Q-Learning model: {qlearning_model_path}")
            self.qlearning_agent.load(qlearning_model_path)
            self.qlearning_agent.epsilon = 0.0
            stats = self.qlearning_agent.get_stats()
            print(f"Q-Learning loaded (states explored: {stats['states_explored']}/{self.qlearning_agent.n_states})")
        else:
            print("Q-Learning: no trained model, using fresh agent")

        # Mêmes hyperparamètres que train_cloudsim.py (le load() réconcilie de toute
        # façon support C51 / n_step / reward_scale depuis le checkpoint).
        self.rainbow_agent = RainbowDQNAgent(
            state_dim=11,
            action_dim=3,
            hidden_dims=[128, 128],
            n_step=5,
            min_buffer_size=500,
            normalize_rewards=True,
            use_distributional=True,
            n_atoms=51,
            v_min=-12.0,
            v_max=12.0,
            reward_scale=0.04,
        )
        if rainbow_model_path and os.path.exists(rainbow_model_path):
            print(f"Loading Rainbow DQN model: {rainbow_model_path}")
            self.rainbow_agent.load(rainbow_model_path)
            print("Rainbow DQN loaded")
        else:
            print("Rainbow DQN: no trained model, using fresh agent")

        self._ql_last_state    = None
        self._ql_last_action   = None
        self._rainbow_last_state  = None
        self._rainbow_last_action = None
        self._ql_episode       = 0
        self._rainbow_episode  = 0
        print("Python bridge initialized")

    def resetCounters(self):
        self._ql_last_state    = None
        self._ql_last_action   = None
        self._rainbow_last_state  = None
        self._rainbow_last_action = None
        # Reproductibilité de l'apprentissage ONLINE effectué pendant le benchmark :
        #  • RNG dédié de l'agent QL (tirage A/B du Double Q-learning),
        #  • RNG GLOBAL np.random — utilisé par l'échantillonnage priorisé du replay
        #    buffer Rainbow (PrioritizedReplayBuffer.sample) lors des updates d'éval,
        #  • RNG global torch — bruit NoisyNet / opérations stochastiques.
        # Sans ces graines, chaque lancement du process Python divergeait (Rainbow_Complex
        # notamment passait de 24.96 à 27.76 d'un run à l'autre). Réinitialisé à CHAQUE run
        # (resetCounters est appelé par Java avant chaque évaluation) → runs indépendants
        # et reproductibles. Graine fixe : aucun impact sur la politique apprise, seulement
        # sur le déterminisme des tirages.
        self.qlearning_agent.reset_rng(0)
        np.random.seed(0)
        try:
            import torch
            torch.manual_seed(0)
        except Exception:
            pass

    def selectActionQLearning(self, state_array, can_replicate, can_delete):
        """Le masque d'actions vient de Java (mêmes règles que TrainingEnvironment.
        getActionMask : éligibilité popularité + limites physiques) — parité train/eval.
        Aucune règle en dur côté éval (le thrashing est géré par la pénalité APPRISE)."""
        try:
            state = self._parse_state(state_array)
            discrete_state = self._discretize_state_for_qlearning(state)
            valid_actions = self._valid_actions(can_replicate, can_delete)
            action = self.qlearning_agent.select_action(discrete_state, valid_actions=valid_actions, training=True)
            self._ql_last_state  = discrete_state
            self._ql_last_action = int(action)
            return int(action)
        except Exception as e:
            print(f"Q-Learning error: {e}")
            return 0

    def selectActionRainbow(self, state_array, can_replicate, can_delete):
        try:
            state = self._parse_state(state_array)
            continuous_state = self._build_rainbow_state(state)
            action_mask = self._action_mask(can_replicate, can_delete)
            # training=False : inférence déterministe (poids moyens, sans bruit NoisyNet) —
            # symétrique du ε=0 de Q-Learning. L'apprentissage online (updateRainbow)
            # continue d'entraîner le réseau, seule la SÉLECTION est greedy.
            action = self.rainbow_agent.select_action(continuous_state, training=False, action_mask=action_mask)
            self._rainbow_last_state  = continuous_state
            self._rainbow_last_action = int(action)
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
            state['eligible_fraction'],
            state['mean_popularity'],
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
            'eligible_fraction':     float(state_list[9]) if len(state_list) > 9 else 0.0,
            'mean_popularity':       float(state_list[10]) if len(state_list) > 10 else 0.0,
        }

    @staticmethod
    def _action_mask(can_replicate, can_delete) -> np.ndarray:
        """Masque [NOOP, REPLICATE, DELETE] depuis les flags de l'environnement Java."""
        return np.array([1.0, 1.0 if can_replicate else 0.0, 1.0 if can_delete else 0.0],
                        dtype=np.float32)

    @staticmethod
    def _valid_actions(can_replicate, can_delete):
        valid = [0]
        if can_replicate:
            valid.append(1)
        if can_delete:
            valid.append(2)
        return valid

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
