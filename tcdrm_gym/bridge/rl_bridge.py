"""
Python RL Bridge for Java/CloudSim via Py4J.

Charge les modèles RL entraînés avec CloudSimPlus et les expose à Java.
"""

import os
import numpy as np

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.rainbow_dqn_agent import make_rainbow_agent
from envs.state_grid import N_STATES, CoverageTracker, discretize


# Exploration au benchmark dans les états JAMAIS vus à l'entraînement : 5 % d'actions
# valides tirées au hasard, comme le ε_min de l'entraînement Q-Learning et l'ε des
# méta-contrôleurs. Dans un état appris, l'agent exploite (greedy) : l'évaluation
# reste celle de la politique apprise, mais un état nouveau n'est plus joué uniquement
# sur la valeur d'initialisation de la Q-table (ou une extrapolation du réseau).
NOVEL_STATE_EPSILON = 0.05


def _trained_states(model_path):
    """États de la grille vus à l'entraînement (<modèle>_coverage.npy, écrit par
    train_cloudsim.py à côté du modèle), ou None si le fichier est absent."""
    if not model_path:
        return None
    path = os.path.splitext(model_path)[0] + "_coverage.npy"
    if not os.path.exists(path):
        print(f"Couverture d'entraînement absente ({path}) : pas d'exploration des états nouveaux")
        return None
    return np.load(path) > 0


class PythonRLBridge:
    def __init__(self, qlearning_model_path=None, rainbow_model_path=None):
        self._ql_model_path      = qlearning_model_path
        self._rainbow_model_path = rainbow_model_path

        # Apprentissage online pendant le benchmark (protocole du mémoire) ; 0 = politique
        # FIGÉE, pour évaluer le modèle entraîné seul (TCDRM_EVAL_ONLINE=0).
        self._online = os.environ.get("TCDRM_EVAL_ONLINE", "1") != "0"
        self._load_models(verbose=True)
        print(f"Évaluation : apprentissage online {'actif' if self._online else 'désactivé (politique figée)'}")

        self._ql_last_state    = None
        self._ql_last_action   = None
        self._rainbow_last_state  = None
        self._rainbow_last_action = None
        self._ql_episode       = 0
        self._rainbow_episode  = 0
        # États rencontrés AU BENCHMARK (grille de 1458 états, simple + complexe) : comparés
        # aux états vus à l'entraînement (*_coverage.npy) par utils.state_coverage
        # --benchmark-overlap — un état jamais vu à l'entraînement est joué sans apprentissage.
        self._bench_coverage = {"qlearning": CoverageTracker(), "rainbow": CoverageTracker()}
        self._trained = {"qlearning": _trained_states(qlearning_model_path),
                         "rainbow": _trained_states(rainbow_model_path)}
        self._explore_rng = np.random.default_rng(0)
        self._explorations = {"qlearning": 0, "rainbow": 0}
        print("Python bridge initialized")

    def _load_models(self, verbose=False):
        """(Re)charge les modèles ENTRAÎNÉS. Appelé avant chaque run du benchmark
        (resetCounters) : chaque run évalue le modèle issu de l'entraînement, sans
        hériter de l'apprentissage online d'un run précédent (simple → complexe,
        graine précédente)."""
        # ε=0 : exploitation greedy (exploration seulement en état nouveau, voir
        # NOVEL_STATE_EPSILON) ; load() restaure α, γ, λ et l'init. optimiste.
        self.qlearning_agent = SimpleQLearningAgent(
            n_states=N_STATES, n_actions=3, epsilon_start=0.0, epsilon_min=0.0,
            epsilon_decay=1.0, use_double_q=True, adaptive_lr=True)
        if self._ql_model_path and os.path.exists(self._ql_model_path):
            self.qlearning_agent.load(self._ql_model_path)
            self.qlearning_agent.epsilon = 0.0
            if verbose:
                stats = self.qlearning_agent.get_stats()
                print(f"Q-Learning loaded: {self._ql_model_path} "
                      f"({stats['training_steps']} pas d'entraînement)")
        elif verbose:
            print("Q-Learning: no trained model, using fresh agent")
        # Même configuration que l'entraînement (make_rainbow_agent) ; load() restaure en
        # plus l'état du scheduler (taux d'apprentissage) et l'avancement β du PER.
        self.rainbow_agent = make_rainbow_agent()
        if self._rainbow_model_path and os.path.exists(self._rainbow_model_path):
            self.rainbow_agent.load(self._rainbow_model_path)
            if verbose:
                print(f"Rainbow DQN loaded: {self._rainbow_model_path} "
                      f"({self.rainbow_agent.update_count} pas de gradient)")
        elif verbose:
            print("Rainbow DQN: no trained model, using fresh agent")

    def resetCounters(self):
        self._load_models()
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
        self._explore_rng = np.random.default_rng(0)
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
            self._record_benchmark_state("qlearning", discrete_state)
            valid_actions = self._valid_actions(can_replicate, can_delete)
            action = self._explore_if_novel("qlearning", discrete_state, valid_actions)
            if action is None:
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
            grid_state = discretize(continuous_state)
            self._record_benchmark_state("rainbow", grid_state)
            action_mask = self._action_mask(can_replicate, can_delete)
            explored = self._explore_if_novel("rainbow", grid_state,
                                              self._valid_actions(can_replicate, can_delete))
            # training=False : inférence déterministe (poids moyens, sans bruit NoisyNet) —
            # symétrique du ε=0 de Q-Learning. L'apprentissage online (updateRainbow)
            # continue d'entraîner le réseau, seule la SÉLECTION est greedy.
            action = explored if explored is not None else \
                self.rainbow_agent.select_action(continuous_state, training=False, action_mask=action_mask)
            self._rainbow_last_state  = continuous_state
            self._rainbow_last_action = int(action)
            return int(action)
        except Exception as e:
            print(f"Rainbow DQN error: {e}")
            return 0

    def updateQLearning(self, reward, next_state_array, done):
        if self._ql_last_state is None or not self._online:
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
        if self._rainbow_last_state is None or not self._online:
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

    def _explore_if_novel(self, agent: str, index: int, valid_actions):
        """Action aléatoire valide avec probabilité NOVEL_STATE_EPSILON si l'état n'a
        jamais été vu à l'entraînement ; None sinon (l'agent exploite)."""
        trained = self._trained[agent]
        if trained is None or trained[index] or self._explore_rng.random() >= NOVEL_STATE_EPSILON:
            return None
        self._explorations[agent] += 1
        return int(self._explore_rng.choice(valid_actions))

    def _record_benchmark_state(self, agent: str, index: int) -> None:
        """Compte l'état et sauvegarde les comptes à chaque fin de run (1000 requêtes) :
        le processus est arrêté par le workflow sans hook de fin."""
        tracker = self._bench_coverage[agent]
        tracker.add(index)
        if tracker.counts.sum() % 1000 == 0:
            # TCDRM_COVERAGE_DIR : la validation écrit dans son propre dossier pour ne pas
            # écraser la couverture du benchmark du workflow (tcdrm_gym/models).
            out_dir = os.environ.get("TCDRM_COVERAGE_DIR") or os.path.join(
                os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models")
            tracker.save(os.path.join(out_dir, f"{agent}_benchmark_coverage.npy"))
            print(f"{agent}: {int(tracker.counts.sum())} décisions de benchmark, "
                  f"{self._explorations[agent]} explorations en état nouveau", flush=True)

    def _discretize_state_for_qlearning(self, state: dict) -> int:
        """Même encodage qu'à l'entraînement (envs/state_grid.py, source unique) : sans
        cela, la Q-table apprise via TrainingServer/py4j ne correspondrait à rien de
        cohérent une fois réutilisée ici pour l'évaluation (BenchmarkRunner.runRL)."""
        return discretize(self._build_rainbow_state(state))

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
