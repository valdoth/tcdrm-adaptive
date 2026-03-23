"""
Python RL Bridge for Java/CloudSim via Py4J.

Charge les modèles RL entraînés avec CloudSimPlus et les expose à Java.
Les modèles sont entraînés via train_cloudsim.py qui utilise le même
environnement CloudSimPlus, garantissant la cohérence entraînement/inférence.

- Q-Learning: Q-table (243 états discrets)
- DQN: réseau de neurones (8 dimensions continues)
"""

import os
import numpy as np
import torch

from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.dqn_agent import DQNAgent
from .adaptive_strategy import AdaptiveState


class PythonRLBridge:
    """
    Bridge exposing trained RL models to Java via Py4J.
    Models are trained with CloudSimPlus via train_cloudsim.py.
    """
    
    def __init__(self, qlearning_model_path=None, dqn_model_path=None):
        """
        Initialize bridge and load trained models.
        
        Args:
            qlearning_model_path: Path to Q-Learning model (.pkl)
            dqn_model_path: Path to DQN model (.pt)
        """
        self._ql_model_path = qlearning_model_path
        self._dqn_model_path = dqn_model_path
        
        # Charger ou créer l'agent Q-Learning
        self.qlearning_agent = SimpleQLearningAgent(
            n_states=243, 
            n_actions=3,
            learning_rate=0.15,
            discount_factor=0.95,
            epsilon_start=0.3,      # Exploration modérée pour online learning
            epsilon_min=0.05,
            epsilon_decay=0.997,
            use_double_q=True,
            adaptive_lr=True
        )
        
        if qlearning_model_path and os.path.exists(qlearning_model_path):
            print(f"📦 Loading Q-Learning model: {qlearning_model_path}")
            self.qlearning_agent.load(qlearning_model_path)
            self.qlearning_agent.epsilon = 0.20  # Online learning: exploration maintenue
            stats = self.qlearning_agent.get_stats()
            print(f"✅ Q-Learning loaded (states explored: {stats['states_explored']}/243)")
        else:
            print("⚠️  Q-Learning: no trained model, using fresh agent")
        
        # Charger ou créer l'agent DQN
        self.dqn_agent = DQNAgent(
            state_dim=8, 
            action_dim=3,
            learning_rate=0.001, 
            discount_factor=0.99,
            epsilon=0.15,  # Exploration modérée, distincte du Q-Learning
            epsilon_min=0.02,
            epsilon_decay_lambda=0.003
        )
        
        if dqn_model_path and os.path.exists(dqn_model_path):
            print(f"📦 Loading DQN model: {dqn_model_path}")
            self.dqn_agent.load(dqn_model_path)
            self.dqn_agent.epsilon = 0.10  # Online learning: exploration modérée
            print("✅ DQN loaded")
        else:
            print("⚠️  DQN: no trained model, using fresh agent")
        
        # État pour tracking
        self._ql_state = AdaptiveState()
        self._dqn_state = AdaptiveState()
        
        # Derniers états/actions pour les mises à jour (online learning)
        self._ql_last_state = None
        self._ql_last_action = None
        self._dqn_last_state = None
        self._dqn_last_action = None
        
        # Anti-thrashing
        self._thrashing_window = 10
        self._warmup_queries = 20
        
        # Compteurs d'épisodes
        self._ql_episode = 0
        self._dqn_episode = 0
        
        print("✅ Python bridge initialized")
    
    def resetCounters(self):
        """Reset internal counters between benchmark runs."""
        self._ql_state.reset(initial_p_threshold=0.5)
        self._dqn_state.reset(initial_p_threshold=0.4)
        self._ql_last_state = None
        self._ql_last_action = None
        self._dqn_last_state = None
        self._dqn_last_action = None
    
    def selectActionQLearning(self, state_array):
        """
        Sélection d'action par Q-Learning (warmup NoRep + online learning).
        Seules les contraintes physiques sont appliquées.
        La politique apprise décide librement.
        
        Returns: Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        try:
            state = self._parse_state(state_array)
            
            # Warmup initial: NoRep pour collecter de l'info sur l'environnement
            if self._ql_state.query_counter < self._warmup_queries:
                self._record_action(self._ql_state, 0)
                self._ql_last_state = None
                self._ql_last_action = None
                return 0

            # Anti-thrashing
            if self._is_thrashing(self._ql_state):
                self._record_action(self._ql_state, 0)
                return 0
            
            # Discrétiser l'état pour Q-Learning (243 états)
            discrete_state = self._discretize_state_for_qlearning(state)

            # Contraintes physiques uniquement
            valid_actions = self._physical_constraints(state)
            
            # Sélection epsilon-greedy — la politique apprise décide
            action = self.qlearning_agent.select_action(discrete_state, valid_actions=valid_actions, training=True)

            # Sauvegarder transition pour update online
            self._ql_last_state = discrete_state
            self._ql_last_action = int(action)
            
            self._record_action(self._ql_state, action)
            return int(action)
        except Exception as e:
            print(f"❌ Q-Learning error: {e}")
            return 0
    
    def selectActionDQN(self, state_array):
        """
        Sélection d'action par DQN (warmup NoRep + online learning).
        Seules les contraintes physiques sont appliquées.
        La politique apprise (réseau de neurones) décide librement.
        
        Returns: Action (0=NOOP, 1=REPLICATE, 2=DELETE)
        """
        try:
            state = self._parse_state(state_array)
            
            # Warmup initial: NoRep pour collecter de l'info sur l'environnement
            if self._dqn_state.query_counter < self._warmup_queries:
                self._record_action(self._dqn_state, 0)
                self._dqn_last_state = None
                self._dqn_last_action = None
                return 0

            # Anti-thrashing
            if self._is_thrashing(self._dqn_state):
                self._record_action(self._dqn_state, 0)
                return 0
            
            # Construire l'état continu pour DQN (8 dimensions)
            continuous_state = self._build_dqn_state(state)

            # Contraintes physiques uniquement (masque binaire)
            action_mask = self._physical_mask(state)
            
            # Sélection epsilon-greedy — la politique apprise décide
            action = self.dqn_agent.select_action(continuous_state, training=True, action_mask=action_mask)

            # Sauvegarder transition pour update online
            self._dqn_last_state = continuous_state
            self._dqn_last_action = int(action)
            
            self._record_action(self._dqn_state, action)
            return int(action)
        except Exception as e:
            print(f"❌ DQN error: {e}")
            return 0
    
    def updateQLearning(self, reward, next_state_array, done):
        """
        Met à jour Q-Learning avec la récompense de la dernière action.
        Appelé par Java après chaque requête.
        """
        if self._ql_last_state is None:
            return
        
        next_state = self._parse_state(next_state_array)
        next_discrete = self._discretize_state_for_qlearning(next_state)
        
        # Mise à jour Q-table
        self.qlearning_agent.update(
            self._ql_last_state,
            self._ql_last_action,
            float(reward),
            next_discrete,
            bool(done)
        )
        
        # Décroissance epsilon en fin d'épisode
        if done:
            self.qlearning_agent.decay_epsilon()
            self._ql_episode += 1
            if self._ql_episode % 10 == 0:
                stats = self.qlearning_agent.get_stats()
                print(f"📊 Q-Learning ep={self._ql_episode} ε={stats['epsilon']:.3f} explored={stats['states_explored']}")
    
    def updateDQN(self, reward, next_state_array, done):
        """
        Met à jour DQN avec la récompense de la dernière action.
        Appelé par Java après chaque requête.
        """
        if self._dqn_last_state is None:
            return
        
        next_state = self._parse_state(next_state_array)
        next_continuous = self._build_dqn_state(next_state)
        
        # Stocker la transition dans le replay buffer
        self.dqn_agent.replay_buffer.push(
            self._dqn_last_state,
            self._dqn_last_action,
            float(reward),
            next_continuous,
            bool(done)
        )
        
        # Training périodique (tous les 10 pas) pour garder Py4J rapide
        if (self._dqn_state.query_counter % 10 == 0 or bool(done)):
            if len(self.dqn_agent.replay_buffer) >= self.dqn_agent.batch_size:
                self.dqn_agent._train_step()
        
        # Décroissance epsilon en fin d'épisode
        if done:
            self.dqn_agent.decay_epsilon()
            self._dqn_episode += 1
            if self._dqn_episode % 10 == 0:
                print(f"📊 DQN ep={self._dqn_episode} ε={self.dqn_agent.epsilon:.3f}")
    
    def saveModels(self):
        """
        Sauvegarde les modèles appris sur disque.
        """
        if self._ql_model_path:
            self.qlearning_agent.save(self._ql_model_path)
            print(f"💾 Q-Learning saved to {self._ql_model_path}")
        
        if self._dqn_model_path:
            torch.save({
                'policy_net_state_dict': self.dqn_agent.policy_net.state_dict(),
                'target_net_state_dict': self.dqn_agent.target_net.state_dict(),
                'epsilon': self.dqn_agent.epsilon
            }, self._dqn_model_path)
            print(f"💾 DQN saved to {self._dqn_model_path}")
    
    def _discretize_state_for_qlearning(self, state: dict) -> int:
        """
        Discrétise l'état continu en index pour Q-Learning (0-242).
        
        Même discrétisation que pendant l'entraînement:
        - RT: temps de réponse (0=bon, 1=moyen, 2=mauvais)
        - COST: coût (0=faible, 1=moyen, 2=élevé)
        - POP: popularité (0=faible, 1=moyenne, 2=haute)
        - BUD: budget (0=confortable, 1=tendu, 2=critique)
        - NET: réseau/réplicas (0=local, 1=mixte, 2=distant)
        """
        # RT: basé sur la latence normalisée
        latency = state['latency']
        if latency < 0.4:
            rt = 0  # Bon
        elif latency < 0.7:
            rt = 1  # Moyen
        else:
            rt = 2  # Mauvais
        
        # COST: basé sur le coût total normalisé
        cost = state['total_cost']
        if cost < 0.4:
            cost_bin = 0
        elif cost < 0.7:
            cost_bin = 1
        else:
            cost_bin = 2
        
        # POP: basé sur la popularité normalisée
        pop = state['normalized_popularity']
        if pop < 0.33:
            pop_bin = 0
        elif pop < 0.67:
            pop_bin = 1
        else:
            pop_bin = 2
        
        # BUD: basé sur le budget restant
        budget = state['budget']
        if budget >= 0.6:
            bud = 0  # Confortable
        elif budget >= 0.3:
            bud = 1  # Tendu
        else:
            bud = 2  # Critique
        
        # NET: basé sur le nombre de réplicas normalisé
        replicas = state['replicas_normalized']
        if replicas >= 0.5:
            net = 0  # Majoritairement local
        elif replicas > 0:
            net = 1  # Mixte
        else:
            net = 2  # Distant
        
        # Encoder en index unique (base 3, 5 dimensions = 243 états)
        return rt * 81 + cost_bin * 27 + pop_bin * 9 + bud * 3 + net
    
    def _build_dqn_state(self, state: dict) -> np.ndarray:
        """
        Construit l'état continu pour DQN (8 dimensions).
        
        Même format que pendant l'entraînement.
        """
        return np.array([
            state['latency'],
            state['budget'],
            state['replicas_normalized'],
            state['normalized_popularity'],
            state['total_cost'],
            state['t_sla_violation'],
            state['c_sla_violation'],
            state['query_progress']
        ], dtype=np.float32)
    
    def _parse_state(self, state_array):
        """Parse Java state array into dict."""
        state_list = list(state_array) if hasattr(state_array, '__iter__') else [state_array]
        return {
            'latency': float(state_list[0]),
            'budget': float(state_list[1]),
            'replicas_normalized': float(state_list[2]),  # Normalized (0-1), NOT actual count!
            'normalized_popularity': float(state_list[3]),
            'total_cost': float(state_list[4]),
            't_sla_violation': float(state_list[5]) if len(state_list) > 5 else 0.0,
            'c_sla_violation': float(state_list[6]) if len(state_list) > 6 else 0.0,
            'query_progress': float(state_list[7]) if len(state_list) > 7 else 0.0,
        }
    
    def _is_thrashing(self, adaptive_state: AdaptiveState) -> bool:
        """
        Détecte si on est en situation de thrashing (oscillation rapide).
        """
        recent = adaptive_state.action_history[-self._thrashing_window:]
        if len(recent) < self._thrashing_window:
            return False
        recent_creates = sum(1 for a in recent if a == 1)
        recent_deletes = sum(1 for a in recent if a == 2)
        return recent_creates >= 2 and recent_deletes >= 2
    
    def _record_action(self, adaptive_state: AdaptiveState, action: int):
        """
        Enregistre l'action pour le suivi anti-thrashing.
        """
        adaptive_state.action_history.append(action)
        # Garder seulement les 20 dernières actions
        if len(adaptive_state.action_history) > 20:
            adaptive_state.action_history.pop(0)
        
        adaptive_state.query_counter += 1
        if action == 1:
            adaptive_state.last_replicate_q = adaptive_state.query_counter
        elif action == 2:
            adaptive_state.last_delete_q = adaptive_state.query_counter

    def _physical_mask(self, state: dict) -> np.ndarray:
        """
        Masque d'actions basé uniquement sur les contraintes physiques.
        L'agent RL décide librement parmi les actions physiquement possibles.
        """
        mask = np.ones(3, dtype=np.float32)
        # DELETE impossible sans réplica
        if float(state['replicas_normalized']) <= 0.0:
            mask[2] = 0.0
        # REPLICATE impossible si déjà au max (normalisé = 1.0)
        if float(state['replicas_normalized']) >= 1.0:
            mask[1] = 0.0
        return mask

    def _physical_constraints(self, state: dict):
        """Version liste d'actions valides (contraintes physiques) pour Q-Learning."""
        mask = self._physical_mask(state)
        return [i for i, v in enumerate(mask) if v > 0.5]
    
    def isQLearningReady(self):
        """Check if Q-Learning model is loaded."""
        return self.qlearning_agent is not None
    
    def isDQNReady(self):
        """Check if DQN model is loaded."""
        return self.dqn_agent is not None
    
    def getModelInfo(self):
        """Return info about loaded models."""
        info = []
        if self.qlearning_agent:
            stats = self.qlearning_agent.get_stats()
            info.append(f"Q-Learning: {stats['states_explored']}/{stats['training_steps']} states")
        if self.dqn_agent:
            info.append("DQN: loaded")
        return " | ".join(info) if info else "No models loaded"
    
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
