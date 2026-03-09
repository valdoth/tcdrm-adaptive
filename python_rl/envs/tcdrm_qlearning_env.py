"""
Environnement TCDRM v2 pour Q-Learning Tabulaire
================================================

Implémentation formelle du modèle MDP défini dans la spécification TCDRM v2:
⟨S, A, P, R, γ⟩

Espace d'états S: 243 états discrets (3^5)
- RT (Response Time): {RT0, RT1, RT2}
- COST: {C0, C1, C2}
- POP (Popularity): {P0, P1, P2}
- BUD (Budget): {B0, B1, B2}
- NET (Network): {N0, N1, N2}

Espace d'actions A: {NOOP, REPLICATE, DELETE}

Fonction de récompense R:
R = +r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH
"""

import gymnasium as gym
from gymnasium import spaces
import numpy as np
from typing import Optional, Tuple, Dict, Any

from config.constants import TcdrmConstants as C
from utils.plsa_fast import PLSAPopularityModel


class TcdrmQLearningEnv(gym.Env):
    """
    Environnement Gymnasium pour TCDRM v2 avec Q-Learning tabulaire.
    
    Espace d'états discret: 243 états (3^5)
    Espace d'actions: 3 actions {NOOP=0, REPLICATE=1, DELETE=2}
    """
    
    metadata = {'render_modes': ['human'], 'render_fps': 4}
    
    # Constantes de discrétisation
    RT_BINS = 3  # RT0, RT1, RT2
    COST_BINS = 3  # C0, C1, C2
    POP_BINS = 3  # P0, P1, P2
    BUD_BINS = 3  # B0, B1, B2
    NET_BINS = 3  # N0, N1, N2
    
    # Actions (from centralized constants)
    ACTION_NOOP = C.ACTION_NOOP
    ACTION_REPLICATE = C.ACTION_REPLICATE
    ACTION_DELETE = C.ACTION_DELETE
    
    def __init__(self, data_gb: float = 0.45, render_mode: Optional[str] = None):
        super().__init__()
        
        self.data_gb = data_gb
        self.render_mode = render_mode
        
        # ====================================================================
        # PARAMÈTRES DU SYSTÈME (from config.constants - Article TCDRM V1)
        # ====================================================================
        self.MAX_QUERIES = C.MAX_QUERIES
        self.INITIAL_BUDGET = C.INITIAL_BUDGET
        self.MAX_REPLICAS = C.max_replicas_for_data(data_gb)
        
        # SLA Parameters
        self.TSLA_BASE = C.TSLA_MS
        self.CSLA = C.CSLA
        
        # Coûts (Tableau 1)
        self.COST_BW_INTRA_DC = C.COST_BW_INTRA_DC
        self.COST_BW_INTER_REGION = C.COST_BW_INTER_REGION
        self.COST_BW_INTER_PROVIDER = C.COST_BW_INTER_PROVIDER
        self.STORAGE_COST_PER_GB_PER_HOUR = C.STORAGE_COST_PER_GB_PER_HOUR
        self.REPLICATION_COST_PER_GB = C.COST_BW_INTER_PROVIDER
        
        # Paramètres réseau
        self.BW_LOCAL_GBPS = C.BW_LOCAL_GBPS
        self.BW_REMOTE_GBPS = C.BW_REMOTE_GBPS
        self.LAT_LOCAL_MS = C.LAT_LOCAL_MS
        self.LAT_REMOTE_MS = C.LAT_REMOTE_MS
        
        # ====================================================================
        # ESPACE D'ACTIONS: {NOOP, REPLICATE, DELETE}
        # ====================================================================
        self.action_space = spaces.Discrete(3)
        
        # ====================================================================
        # ESPACE D'ÉTATS: 243 états discrets (3^5)
        # ====================================================================
        # On utilise MultiDiscrete pour représenter les 5 dimensions
        self.observation_space = spaces.MultiDiscrete([
            self.RT_BINS,    # RT: {0=RT0, 1=RT1, 2=RT2}
            self.COST_BINS,  # COST: {0=C0, 1=C1, 2=C2}
            self.POP_BINS,   # POP: {0=P0, 1=P1, 2=P2}
            self.BUD_BINS,   # BUD: {0=B0, 1=B1, 2=B2}
            self.NET_BINS    # NET: {0=N0, 1=N1, 2=N2}
        ])
        
        # ====================================================================
        # POIDS DE LA FONCTION DE RÉCOMPENSE
        # ====================================================================
        self.r1 = 10.0  # SLA_OK (augmenté pour encourager respect SLA)
        self.r2 = 15.0  # SLA_VIOL (augmenté pour pénaliser violations)
        self.r3 = 5.0   # COST_OVER
        self.r4 = 0.5   # REPL_COST (réduit pour encourager réplication stratégique)
        self.r5 = 3.0   # THRASH
        
        # ====================================================================
        # VARIABLES D'ÉTAT
        # ====================================================================
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = 0.0
        self.current_replica_count = 0
        self.pending_replica_count = 0
        self.access_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        
        # Warm-up progressif des réplicas (pour descente graduelle)
        self.replica_warmup_progress = {}  # {replica_id: warmup_progress [0, 1]}
        self.WARMUP_QUERIES = 600  # Nombre de requêtes pour atteindre 100% d'efficacité (descente très progressive)
        
        # Tracking cumulatif de la bande passante (Fig. 6)
        self.cumulative_bandwidth = 0.0
        self.bandwidth_history = []
        
        # Statistiques pour discrétisation RT (moyenne et écart-type)
        self.latency_history = []
        # Initialiser avec des valeurs réalistes basées sur LAT_REMOTE_MS
        self.mu_RT = self.LAT_REMOTE_MS  # ~100ms pour accès distant
        self.sigma_RT = 50.0  # Écart-type initial réaliste
        
        # Historique des actions pour détecter le thrashing
        self.action_history = []
        self.THRASH_WINDOW = 5
        
        # Random number generator
        self.np_random = None
        self._seed = None
        
        # Modèle PLSA pour popularité (PSLA)
        self.plsa_model = None
        
    def reset(self, seed: Optional[int] = None, options: Optional[Dict[str, Any]] = None) -> Tuple[np.ndarray, Dict[str, Any]]:
        super().reset(seed=seed)
        self._seed = seed
        
        # Réinitialiser l'état
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = self.LAT_REMOTE_MS
        self.access_count = 0
        self.current_replica_count = 0
        self.pending_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.latency_history = []
        self.action_history = []
        
        # Réinitialiser warm-up et tracking bande passante
        self.replica_warmup_progress = {}
        self.cumulative_bandwidth = 0.0
        self.bandwidth_history = []
        
        # Réinitialiser statistiques RT avec des valeurs réalistes
        self.mu_RT = self.LAT_REMOTE_MS  # ~100ms pour accès distant
        self.sigma_RT = 50.0
        
        # Réinitialiser PLSA
        if self.plsa_model is None:
            self.plsa_model = PLSAPopularityModel(n_topics=3, max_iterations=20, seed=seed)
        else:
            self.plsa_model.reset(seed=seed)
        
        observation = self._get_discrete_state()
        info = self._get_info()
        
        return observation, info
    
    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict[str, Any]]:
        """
        Exécute une action selon l'algorithme A1 (Décision adaptative de réplication).
        """
        # Sauvegarder l'état précédent
        previous_replica_count = self.current_replica_count
        previous_budget = self.current_budget
        
        # Appliquer les réplicas en attente
        if self.pending_replica_count > 0:
            self.current_replica_count += self.pending_replica_count
            self.pending_replica_count = 0
        
        # Simuler la requête AVANT d'exécuter l'action
        query_latency = self._simulate_query()
        query_cost = self._calculate_query_cost()
        
        # Vérifier les contraintes d'action
        action_valid = self._is_action_valid(action)
        if not action_valid:
            action = self.ACTION_NOOP  # Forcer NOOP si action invalide
        
        # Exécuter l'action
        action_executed = self._execute_action(action)
        
        # Mettre à jour l'état
        self.current_budget -= query_cost
        self.current_latency = query_latency
        self.access_count += 1
        self.current_query += 1
        self.total_cost += query_cost
        
        # Mettre à jour historique de latence pour statistiques RT
        self.latency_history.append(query_latency)
        if len(self.latency_history) > 100:  # Garder les 100 dernières
            self.latency_history.pop(0)
            # Recalculer mu et sigma
            self.mu_RT = np.mean(self.latency_history)
            self.sigma_RT = np.std(self.latency_history)
        
        # Mettre à jour PLSA
        self.plsa_model.add_access(self.access_count)
        
        # Tracker violations SLA
        if query_latency > self.TSLA_BASE:
            self.sla_violations += 1
        
        # Mettre à jour historique d'actions
        self.action_history.append(action)
        if len(self.action_history) > self.THRASH_WINDOW:
            self.action_history.pop(0)
        
        # Calculer la récompense selon la formule formelle
        reward = self._calculate_reward(
            action, action_executed, previous_replica_count,
            previous_budget, query_cost, query_latency
        )
        
        # Vérifier terminaison
        terminated = self.current_query >= self.MAX_QUERIES
        truncated = self.current_budget <= 0
        
        observation = self._get_discrete_state()
        info = self._get_info()
        
        return observation, reward, terminated, truncated, info
    
    def _is_action_valid(self, action: int) -> bool:
        """
        Vérifie les contraintes d'action:
        - REPLICATE interdit si BUD = B2 (budget critique)
        - DELETE interdit s'il n'existe aucun réplica
        """
        if action == self.ACTION_REPLICATE:
            budget_state = self._discretize_budget()
            if budget_state == 2:  # B2: budget critique
                return False
            # Vérifier aussi qu'on n'a pas atteint le max de réplicas
            total_replicas = self.current_replica_count + self.pending_replica_count
            if total_replicas >= self.MAX_REPLICAS:
                return False
        
        elif action == self.ACTION_DELETE:
            if self.current_replica_count == 0:
                return False
        
        return True
    
    def _execute_action(self, action: int) -> bool:
        """Exécute l'action de réplication."""
        if action == self.ACTION_REPLICATE:
            total_replicas = self.current_replica_count + self.pending_replica_count
            if total_replicas < self.MAX_REPLICAS:
                creation_cost = self.data_gb * self.REPLICATION_COST_PER_GB
                if self.current_budget >= creation_cost:
                    self.pending_replica_count += 1
                    self.current_budget -= creation_cost
                    # Initialiser le warm-up du nouveau réplica à 0
                    replica_id = self.current_replica_count + self.pending_replica_count
                    self.replica_warmup_progress[replica_id] = 0.0
                    return True
            return False
        
        elif action == self.ACTION_DELETE:
            if self.current_replica_count > 0:
                self.current_replica_count -= 1
                # Supprimer le warm-up du réplica supprimé
                if self.current_replica_count + 1 in self.replica_warmup_progress:
                    del self.replica_warmup_progress[self.current_replica_count + 1]
                return True
            return False
        
        else:  # NOOP
            return True
    
    def _simulate_query(self) -> float:
        """Simule le temps de réponse d'une requête avec warm-up progressif (sigmoid)."""
        # Mettre à jour le warm-up des réplicas avec compteur de requêtes
        for replica_id in list(self.replica_warmup_progress.keys()):
            if self.replica_warmup_progress[replica_id] < 1.0:
                # Incrémenter le compteur de requêtes
                self.replica_warmup_progress[replica_id] = min(
                    1.0, 
                    self.replica_warmup_progress[replica_id] + (1.0 / self.WARMUP_QUERIES)
                )
        
        # Calculer l'efficacité moyenne des réplicas avec fonction sigmoid
        if self.current_replica_count > 0:
            # Appliquer une fonction sigmoid pour une montée progressive
            warmup_values = []
            for warmup_linear in self.replica_warmup_progress.values():
                # Sigmoid: 1 / (1 + exp(-k*(x - 0.5)))
                # k=5 pour une transition très douce (descente progressive comme dans l'article)
                x = warmup_linear
                warmup_sigmoid = 1.0 / (1.0 + np.exp(-5.0 * (x - 0.5)))
                warmup_values.append(warmup_sigmoid)
            avg_warmup = sum(warmup_values) / len(warmup_values)
        else:
            avg_warmup = 0.0
        
        # Probabilité d'accès local basée sur le nombre de réplicas ET leur warm-up
        p_local = min(0.9, (self.current_replica_count / self.MAX_REPLICAS) * avg_warmup)
        
        if self.np_random.random() < p_local:
            # Accès local (avec efficacité progressive)
            bandwidth_gbps = self.BW_LOCAL_GBPS
            base_latency = self.LAT_LOCAL_MS
        else:
            # Accès distant
            bandwidth_gbps = self.BW_REMOTE_GBPS
            base_latency = self.LAT_REMOTE_MS
        
        # Temps de transfert
        transfer_time = (self.data_gb / bandwidth_gbps) * 1000.0  # ms
        
        # Tracking cumulatif de la bande passante (Fig. 6)
        self.cumulative_bandwidth += self.data_gb
        self.bandwidth_history.append(self.cumulative_bandwidth)
        
        # Temps de traitement (simulé)
        processing_time = self.np_random.uniform(10.0, 50.0)
        
        # Latence réseau avec jitter
        jitter = self.np_random.uniform(-5.0, 5.0)
        network_latency = base_latency + jitter
        
        total_time = transfer_time + processing_time + network_latency
        return total_time
    
    def _calculate_query_cost(self) -> float:
        """Calcule le coût monétaire d'une requête."""
        # Coût de transfert
        p_local = min(0.9, self.current_replica_count / self.MAX_REPLICAS)
        if self.np_random.random() < p_local:
            transfer_cost = self.data_gb * self.COST_BW_INTRA_DC
        else:
            transfer_cost = self.data_gb * self.COST_BW_INTER_PROVIDER
        
        # Coût CPU (simplifié)
        cpu_cost = 0.01
        
        # Coût de stockage (négligeable selon Fig. 7)
        storage_cost = 0.0
        if self.current_replica_count > 0:
            query_duration_hours = 0.001  # Approximation
            storage_cost = self.current_replica_count * self.data_gb * \
                          self.STORAGE_COST_PER_GB_PER_HOUR * query_duration_hours
        
        return transfer_cost + cpu_cost + storage_cost
    
    # ========================================================================
    # DISCRÉTISATION DES ÉTATS (selon spécification formelle)
    # ========================================================================
    
    def _discretize_response_time(self) -> int:
        """
        Discrétise le temps de réponse en 3 bins:
        RT0: tQ ≤ μRT
        RT1: μRT < tQ ≤ μRT + σRT
        RT2: tQ > μRT + σRT
        """
        tQ = self.current_latency
        
        if tQ <= self.mu_RT:
            return 0  # RT0: satisfaisant
        elif tQ <= self.mu_RT + self.sigma_RT:
            return 1  # RT1: proche de la limite
        else:
            return 2  # RT2: violation
    
    def _discretize_cost(self) -> int:
        """
        Discrétise le coût en 3 bins:
        C0: cQ ≤ 0.7 × CSLA
        C1: 0.7 × CSLA < cQ ≤ CSLA
        C2: cQ > CSLA
        """
        # Normaliser le coût par rapport au budget initial
        cQ_normalized = self.total_cost / max(1, self.current_query)
        
        if cQ_normalized <= 0.7 * self.CSLA:
            return 0  # C0: faible
        elif cQ_normalized <= self.CSLA:
            return 1  # C1: modéré
        else:
            return 2  # C2: excessif
    
    def _discretize_popularity(self) -> int:
        """
        Discrétise la popularité en 3 bins:
        P0: faible popularité
        P1: popularité moyenne
        P2: forte popularité
        """
        pop = self.plsa_model.predict_popularity()
        
        if pop < 0.33:
            return 0  # P0
        elif pop < 0.67:
            return 1  # P1
        else:
            return 2  # P2
    
    def _discretize_budget(self) -> int:
        """
        Discrétise le budget résiduel en 3 bins:
        B0: budget ≥ 60%
        B1: 30% ≤ budget < 60%
        B2: budget < 30%
        """
        budget_ratio = self.current_budget / self.INITIAL_BUDGET
        
        if budget_ratio >= 0.6:
            return 0  # B0: confortable
        elif budget_ratio >= 0.3:
            return 1  # B1: tendu
        else:
            return 2  # B2: critique
    
    def _discretize_network(self) -> int:
        """
        Discrétise le type de trafic réseau en 3 bins:
        N0: Intra-région (local)
        N1: Inter-région
        N2: Inter-cloud
        
        Simplifié: basé sur le nombre de réplicas
        """
        if self.current_replica_count >= 2:
            return 0  # N0: majoritairement local
        elif self.current_replica_count == 1:
            return 1  # N1: mixte
        else:
            return 2  # N2: distant
    
    def _get_discrete_state(self) -> np.ndarray:
        """
        Retourne l'état discret s = (RT, COST, POP, BUD, NET).
        """
        return np.array([
            self._discretize_response_time(),
            self._discretize_cost(),
            self._discretize_popularity(),
            self._discretize_budget(),
            self._discretize_network()
        ], dtype=np.int32)
    
    def state_to_index(self, state: np.ndarray) -> int:
        """
        Convertit un état discret en index unique [0, 242].
        Utilisé pour indexer la Q-table.
        """
        rt, cost, pop, bud, net = state
        index = (rt * 81 + cost * 27 + pop * 9 + bud * 3 + net)
        return index
    
    def index_to_state(self, index: int) -> np.ndarray:
        """
        Convertit un index en état discret.
        """
        net = index % 3
        index //= 3
        bud = index % 3
        index //= 3
        pop = index % 3
        index //= 3
        cost = index % 3
        rt = index // 3
        
        return np.array([rt, cost, pop, bud, net], dtype=np.int32)
    
    # ========================================================================
    # FONCTION DE RÉCOMPENSE FORMELLE
    # ========================================================================
    
    def _calculate_reward(
        self,
        action: int,
        action_executed: bool,
        previous_replica_count: int,
        previous_budget: float,
        query_cost: float,
        query_latency: float
    ) -> float:
        """
        Fonction de récompense simplifiée basée sur la réduction de latence.
        Encourage la réplication quand elle améliore les performances.
        """
        # Normaliser latence [0, 10000ms] → [0, 1]
        MAX_LATENCY = 10000.0
        latency_norm = min(1.0, query_latency / MAX_LATENCY)
        
        # Reward basé sur la latence (plus c'est bas, mieux c'est)
        # Latence 0ms → reward +10.0
        # Latence 5000ms → reward +5.0
        # Latence 10000ms → reward +0.0
        latency_reward = 10.0 * (1.0 - latency_norm)
        
        # Pénalité légère pour réplication (coût)
        repl_penalty = 0.0
        if action == self.ACTION_REPLICATE and action_executed:
            repl_penalty = 0.5
        
        # Bonus si réplication quand latence élevée (reward shaping)
        repl_bonus = 0.0
        if action == self.ACTION_REPLICATE and action_executed:
            if query_latency > 2000.0:  # Si latence > 2 secondes
                # Bonus proportionnel à la latence
                repl_bonus = 2.0 * min(1.0, query_latency / 5000.0)
        
        # Pénalité pour dépassement budget
        budget_penalty = 0.0
        budget_ratio = self.current_budget / self.INITIAL_BUDGET
        if budget_ratio < 0.3:
            budget_penalty = 5.0
        
        # Pénalité légère pour thrashing
        thrash_penalty = 0.0
        if len(self.action_history) >= self.THRASH_WINDOW:
            alternations = 0
            for i in range(len(self.action_history) - 1):
                if (self.action_history[i] == self.ACTION_REPLICATE and 
                    self.action_history[i+1] == self.ACTION_DELETE) or \
                   (self.action_history[i] == self.ACTION_DELETE and 
                    self.action_history[i+1] == self.ACTION_REPLICATE):
                    alternations += 1
            if alternations >= 2:
                thrash_penalty = 1.0
        
        reward = latency_reward - repl_penalty + repl_bonus - budget_penalty - thrash_penalty
        
        return reward
    
    def _get_info(self) -> Dict[str, Any]:
        """Retourne les informations sur l'état actuel."""
        # Calculer l'efficacité moyenne des réplicas
        avg_warmup = 0.0
        if self.replica_warmup_progress:
            avg_warmup = sum(self.replica_warmup_progress.values()) / len(self.replica_warmup_progress)
        
        return {
            'query': self.current_query,
            'budget': self.current_budget,
            'latency': self.current_latency,
            'replicas': self.current_replica_count,
            'sla_violations': self.sla_violations,
            'sla_compliance_rate': 1.0 - (self.sla_violations / max(1, self.current_query)),
            'total_cost': self.total_cost,
            'access_count': self.access_count,
            'discrete_state': self._get_discrete_state(),
            'state_index': self.state_to_index(self._get_discrete_state()),
            'mu_RT': self.mu_RT,
            'sigma_RT': self.sigma_RT,
            'cumulative_bandwidth': self.cumulative_bandwidth,  # Bande passante cumulative (Fig. 6)
            'avg_replica_warmup': avg_warmup  # Efficacité moyenne des réplicas (Fig. 3)
        }
    
    def render(self):
        if self.render_mode == 'human':
            state = self._get_discrete_state()
            print(f"Query: {self.current_query}/{self.MAX_QUERIES}")
            print(f"State: RT={state[0]} COST={state[1]} POP={state[2]} BUD={state[3]} NET={state[4]}")
            print(f"Budget: ${self.current_budget:.2f}")
            print(f"Latency: {self.current_latency:.2f}ms (μ={self.mu_RT:.1f}, σ={self.sigma_RT:.1f})")
            print(f"Replicas: {self.current_replica_count}")
            print(f"SLA Violations: {self.sla_violations}")
            print("-" * 50)
    
    def close(self):
        pass
    
    def get_state_space_size(self) -> int:
        """Retourne la taille de l'espace d'états (243)."""
        return 243
    
    def get_action_space_size(self) -> int:
        """Retourne la taille de l'espace d'actions (3)."""
        return 3
