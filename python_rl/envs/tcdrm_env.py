import gymnasium as gym
from gymnasium import spaces
import numpy as np
from typing import Optional, Tuple, Dict, Any
import sys
import os

# Ajouter le chemin pour importer le module PLSA
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from utils.plsa_fast import PLSAPopularityModel
from envs.reward_function_tsla import calculate_reward_with_dynamic_tsla


class TcdrmAdaptiveEnv(gym.Env):
    """
    Gymnasium Environment for TCDRM-ADAPTIVE Cloud Resource Management
    
    Inspired by:
    - https://github.com/tgasla/rl-cloudsimplus
    - https://github.com/diabahmed/drl-cloudsimplus-loadbalancer
    - https://github.com/FCBayern1/rl-cloudsimplus-greenscheduling
    
    State Space:
        - Budget ratio: [0, 1] (continuous)
        - Current latency: [0, 300] ms (continuous)
        - Access count: [0, 1000] (normalized)
        - Replica count: [0, 3] (discrete)
        - Query complexity: [0, 1] (normalized data size)
        - SLA violation rate: [0, 1] (continuous)
        - Cost rate: [0, 1] (normalized)
    
    Action Space:
        - 0: Create replica
        - 1: Delete replica
        - 2: Do nothing
    
    Reward:
        Multi-objective reward balancing:
        - Latency reduction
        - Cost optimization
        - SLA compliance
        - Resource efficiency
    """
    
    metadata = {'render_modes': ['human', 'rgb_array'], 'render_fps': 4}
    
    def __init__(self, data_gb: float = 5.3, render_mode: Optional[str] = None):
        super().__init__()
        
        self.data_gb = data_gb
        self.render_mode = render_mode
        
        # Constants
        self.MAX_QUERIES = 1000
        self.INITIAL_BUDGET = 1000.0
        self.MAX_REPLICAS = 3
        
        # TSLA Dynamique (appris par l'agent RL)
        self.TSLA_MIN = 100.0  # Seuil minimum de latence (secondes)
        self.TSLA_MAX = 250.0  # Seuil maximum de latence (secondes)
        self.TSLA_INITIAL = 150.0  # Seuil initial
        self.TSLA_STEP = 10.0  # Pas d'ajustement
        self.current_tsla = self.TSLA_INITIAL  # TSLA dynamique
        
        # Costs (from article)
        self.COST_BW_INTRA_DC = 0.002
        self.COST_BW_INTER_PROVIDER = 0.10
        self.STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0
        self.REPLICATION_COST_PER_GB = self.COST_BW_INTER_PROVIDER
        
        # Network parameters
        self.BW_LOCAL_GBPS = 10.0
        self.BW_REMOTE_GBPS = 1.0
        self.LAT_LOCAL_MS = 1.0
        self.LAT_REMOTE_MS = 100.0
        
        # Action space: Actions combinées (réplication + ajustement TSLA)
        # 0=CREATE_REPLICA, 1=DELETE_REPLICA, 2=DO_NOTHING,
        # 3=INCREASE_TSLA, 4=DECREASE_TSLA, 5=MAINTAIN_TSLA
        # Total: 9 actions (3 réplication × 3 TSLA)
        # Encodage: action = replica_action * 3 + tsla_action
        self.action_space = spaces.Discrete(9)
        
        # Mapping des actions
        self.REPLICA_ACTIONS = ['CREATE', 'DELETE', 'DO_NOTHING']
        self.TSLA_ACTIONS = ['INCREASE', 'DECREASE', 'MAINTAIN']
        
        # Observation space: [budget_ratio, latency, access_count_norm, replica_count, 
        #                     query_complexity, sla_violation_rate, cost_rate, popularity, tsla_normalized]
        self.observation_space = spaces.Box(
            low=np.array([0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0]),
            high=np.array([1.0, 300.0, 1.0, float(self.MAX_REPLICAS), 1.0, 1.0, 1.0, 1.0, 1.0]),
            dtype=np.float32
        )
        
        # State variables
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = 0.0
        self.current_replica_count = 0
        self.pending_replica_count = 0  # Réplicas en cours de création (disponibles à la requête suivante)
        self.access_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.episode_rewards = []
        self.last_action = 2  # DO_NOTHING par défaut
        
        # Random number generator
        self.np_random = None
        self._seed = None
        
        # PLSA model for popularity prediction (sera initialisé avec seed dans reset)
        self.plsa_model = None
        
    def reset(self, seed: Optional[int] = None, options: Optional[Dict[str, Any]] = None) -> Tuple[np.ndarray, Dict[str, Any]]:
        super().reset(seed=seed)
        
        # Sauvegarder le seed pour reproductibilité
        self._seed = seed
        
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = self.LAT_REMOTE_MS
        self.access_count = 0
        self.current_replica_count = 0
        self.pending_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.episode_rewards = []
        self.last_action = 2  # DO_NOTHING par défaut
        self.current_tsla = self.TSLA_INITIAL  # Réinitialiser TSLA
        self.tsla_history = []  # Historique des ajustements TSLA
        self.last_replica_action = 2  # Pour tracking instabilité
        self.last_tsla_action = 2  # Pour tracking instabilité
        
        # Réinitialiser le modèle PLSA avec le même seed pour reproductibilité
        if self.plsa_model is None:
            self.plsa_model = PLSAPopularityModel(n_topics=3, max_iterations=20, seed=seed)
        else:
            self.plsa_model.reset(seed=seed)
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, info
    
    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict[str, Any]]:
        previous_replica_count = self.current_replica_count
        previous_budget = self.current_budget
        previous_tsla = self.current_tsla
        
        # Décoder l'action combinée: action = replica_action * 3 + tsla_action
        replica_action = action // 3  # 0=CREATE, 1=DELETE, 2=DO_NOTHING
        tsla_action = action % 3      # 0=INCREASE, 1=DECREASE, 2=MAINTAIN
        
        # Appliquer les réplicas en attente (créés à la requête précédente)
        # Cela simule le délai de création des réplicas comme dans TCDRM Statique
        if self.pending_replica_count > 0:
            self.current_replica_count += self.pending_replica_count
            self.pending_replica_count = 0
        
        # IMPORTANT: Simuler la requête AVANT d'exécuter l'action
        # pour que les réplicas créés ne bénéficient qu'aux requêtes suivantes
        # (cohérent avec TCDRM Statique)
        query_latency = self._simulate_query()
        query_cost = self._calculate_query_cost()
        
        # Execute actions (après la simulation de la requête)
        replica_executed = self._execute_replica_action(replica_action)
        tsla_executed = self._execute_tsla_action(tsla_action)
        
        # Update state
        self.current_budget -= query_cost
        self.current_latency = query_latency
        self.access_count += 1
        self.current_query += 1
        self.total_cost += query_cost
        
        # Mettre à jour le modèle PLSA avec le nouvel accès
        self.plsa_model.add_access(self.access_count)
        
        # Track SLA violations (utilise TSLA dynamique)
        if query_latency > self.current_tsla:
            self.sla_violations += 1
        
        # Calculate reward (avec TSLA dynamique)
        reward = calculate_reward_with_dynamic_tsla(
            self, replica_action, tsla_action, replica_executed, tsla_executed,
            previous_replica_count, previous_tsla, previous_budget, 
            query_cost, query_latency
        )
        self.episode_rewards.append(reward)
        
        # Check termination
        terminated = self.current_query >= self.MAX_QUERIES
        truncated = self.current_budget <= 0
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, reward, terminated, truncated, info
    
    def _execute_replica_action(self, action: int) -> bool:
        """Exécute l'action de réplication"""
        if action == 0:  # CREATE_REPLICA
            # Les réplicas sont mis en attente et seront disponibles à la requête suivante
            total_replicas = self.current_replica_count + self.pending_replica_count
            if total_replicas < self.MAX_REPLICAS:
                creation_cost = self.data_gb * self.REPLICATION_COST_PER_GB
                if self.current_budget >= creation_cost:
                    self.pending_replica_count += 1
                    self.current_budget -= creation_cost
                    return True
            return False
            
        elif action == 1:  # DELETE_REPLICA
            # La suppression est immédiate (pas de délai)
            if self.current_replica_count > 0:
                self.current_replica_count -= 1
                # Annuler aussi les réplicas en attente si nécessaire
                if self.pending_replica_count > 0:
                    self.pending_replica_count -= 1
                return True
            return False
            
        else:  # DO_NOTHING
            return True
    
    def _execute_tsla_action(self, action: int) -> bool:
        """Exécute l'action d'ajustement TSLA"""
        if action == 0:  # INCREASE_TSLA
            new_tsla = min(self.current_tsla + self.TSLA_STEP, self.TSLA_MAX)
            if new_tsla != self.current_tsla:
                self.current_tsla = new_tsla
                self.tsla_history.append(('INCREASE', self.current_tsla))
                return True
            return False
            
        elif action == 1:  # DECREASE_TSLA
            new_tsla = max(self.current_tsla - self.TSLA_STEP, self.TSLA_MIN)
            if new_tsla != self.current_tsla:
                self.current_tsla = new_tsla
                self.tsla_history.append(('DECREASE', self.current_tsla))
                return True
            return False
            
        else:  # MAINTAIN_TSLA
            self.tsla_history.append(('MAINTAIN', self.current_tsla))
            return True
    
    def _simulate_query(self) -> float:
        """
        Simule le temps total d'une requête incluant:
        - Temps de transfert des données (basé sur la bande passante)
        - Latence réseau
        - Temps de traitement CPU
        
        Cohérent avec TcdrmBenchmarkPerQuery.java
        """
        # Déterminer si on utilise un réplica local ou distant
        use_local = False
        if self.current_replica_count > 0:
            # Probability of local access based on replica count
            local_probability = self.current_replica_count / (self.current_replica_count + 2)
            use_local = self.np_random.random() < local_probability
        
        # Paramètres réseau selon le type d'accès
        if use_local:
            bw_gbps = self.BW_LOCAL_GBPS  # 10.0 Gbps
            latency_ms = self.LAT_LOCAL_MS  # 1.0 ms
        else:
            bw_gbps = self.BW_REMOTE_GBPS  # 1.0 Gbps
            latency_ms = self.LAT_REMOTE_MS  # 100.0 ms
        
        # Temps de transfert des données (data_gb * 8000 bits/byte / bw_gbps) + latence
        transfer_ms = (self.data_gb * 8_000.0 / bw_gbps) + latency_ms
        
        # Ajouter du jitter (variance) au temps de transfert
        jitter_ratio = 0.05
        transfer_ms *= (1.0 + jitter_ratio * (self.np_random.random() * 2 - 1))
        
        # Temps de traitement CPU (0.5 minutes par GB)
        processing_min_per_gb = 0.5
        processing_min = self.data_gb * processing_min_per_gb
        
        # Ajouter du jitter au temps de traitement
        cpu_jitter_ratio = 0.05
        processing_min *= (1.0 + cpu_jitter_ratio * (self.np_random.random() * 2 - 1))
        
        # Convertir le temps de traitement en millisecondes
        processing_ms = processing_min * 60_000.0
        
        # Temps total de la requête
        total_time_ms = transfer_ms + processing_ms
        
        # Retourner en secondes pour cohérence avec Java
        return total_time_ms / 1000.0
    
    def _calculate_query_cost(self) -> float:
        """
        Calcule le coût total d'une requête incluant:
        - Coût de transfert réseau (bande passante)
        - Coût CPU (traitement)
        - Coût de stockage (si réplicas existent)
        
        Cohérent avec TcdrmBenchmarkPerQuery.java
        """
        # Déterminer si on utilise un réplica local ou distant
        use_local = False
        if self.current_replica_count > 0:
            local_probability = self.current_replica_count / (self.current_replica_count + 2)
            use_local = self.np_random.random() < local_probability
            transfer_cost = self.data_gb * (self.COST_BW_INTRA_DC if use_local else self.COST_BW_INTER_PROVIDER)
        else:
            transfer_cost = self.data_gb * self.COST_BW_INTER_PROVIDER
        
        # Coût CPU basé sur le temps de traitement
        CPU_COST_PER_HOUR = 0.02  # Article: 0.020
        PROCESSING_MIN_PER_GB = 0.5
        processing_min = self.data_gb * PROCESSING_MIN_PER_GB
        cpu_cost = (processing_min / 60.0) * CPU_COST_PER_HOUR
        
        # Coût de stockage proportionnel au nombre de réplicas
        # Calculé par heure d'utilisation (temps de la requête)
        storage_cost = 0.0
        if self.current_replica_count > 0:
            # Temps de la requête en heures (approximation basée sur le temps de traitement)
            query_duration_hours = processing_min / 60.0
            storage_cost = self.current_replica_count * self.data_gb * self.STORAGE_COST_PER_GB_PER_HOUR * query_duration_hours
        
        return transfer_cost + cpu_cost + storage_cost
    
    def _get_observation(self) -> np.ndarray:
        budget_ratio = np.clip(self.current_budget / self.INITIAL_BUDGET, 0.0, 1.0)
        latency = np.clip(self.current_latency, 0.0, 300.0)
        access_count_norm = np.clip(self.access_count / self.MAX_QUERIES, 0.0, 1.0)
        replica_count = float(self.current_replica_count)
        query_complexity = np.clip(self.data_gb / 20.0, 0.0, 1.0)  # Normalize by max expected size
        sla_violation_rate = self.sla_violations / max(1, self.current_query)
        cost_rate = np.clip(self.total_cost / self.INITIAL_BUDGET, 0.0, 1.0)
        popularity = self.plsa_model.predict_popularity()  # Popularité prédite par PLSA (PSLA)
        tsla_normalized = (self.current_tsla - self.TSLA_MIN) / (self.TSLA_MAX - self.TSLA_MIN)  # TSLA normalisé [0,1]
        
        return np.array([
            budget_ratio, latency, access_count_norm, replica_count,
            query_complexity, sla_violation_rate, cost_rate, popularity, tsla_normalized
        ], dtype=np.float32)
    
    def _get_info(self) -> Dict[str, Any]:
        return {
            'query': self.current_query,
            'budget': self.current_budget,
            'latency': self.current_latency,
            'replicas': self.current_replica_count,
            'sla_violations': self.sla_violations,
            'sla_compliance_rate': 1.0 - (self.sla_violations / max(1, self.current_query)),
            'total_cost': self.total_cost,
            'access_count': self.access_count,
            'current_tsla': self.current_tsla,  # TSLA dynamique actuel
            'tsla_adjustments': len([a for a, _ in self.tsla_history if a != 'MAINTAIN'])  # Nombre d'ajustements
        }
    
    def render(self):
        if self.render_mode == 'human':
            print(f"Query: {self.current_query}/{self.MAX_QUERIES}")
            print(f"Budget: ${self.current_budget:.2f}")
            print(f"Latency: {self.current_latency:.2f}ms")
            print(f"Replicas: {self.current_replica_count}")
            print(f"SLA Violations: {self.sla_violations}")
            print("-" * 50)
    
    def close(self):
        pass
    
    def get_state_space_size(self):
        """Retourne la taille de l'espace d'états pour Q-Learning tabulaire"""
        # Pour Q-Learning tabulaire, on utilise un état discret
        # 3 niveaux de budget × 3 niveaux de latence × 3 niveaux de popularité × 4 niveaux de réplicas
        return 3 * 3 * 3 * 4  # 108 états
    
    def get_action_space_size(self):
        """Retourne la taille de l'espace d'actions"""
        return self.action_space.n  # 3 actions
