"""
TCDRM v2 Environment - Deep Q-Network Compatible

Environnement Gymnasium pour TCDRM v2 avec:
- État continu à 8 dimensions
- Actions discrètes (NOOP, REPLICATE, DELETE)
- Fonction de récompense multi-objectifs
- Support pour action masking
"""

import gymnasium as gym
from gymnasium import spaces
import numpy as np
from typing import Optional, Tuple, Dict, Any
import sys
import os

sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from utils.plsa_fast import PLSAPopularityModel


class TcdrmV2Env(gym.Env):
    """
    Environnement TCDRM v2 pour Deep Q-Network.
    
    État (8 dimensions):
        - tQ_norm: Temps de réponse normalisé [0,1]
        - cQ_norm: Coût de requête normalisé [0,1]
        - pop_norm: Popularité prédite [0,1]
        - bud_norm: Budget restant normalisé [0,1]
        - net_inter_ratio: Ratio trafic inter-région [0,1]
        - net_intercloud_ratio: Ratio trafic inter-cloud [0,1]
        - repl_factor: Facteur de réplication normalisé [0,1]
        - trend_pop: Tendance de popularité [-1,1]
    
    Actions (3):
        - 0: NOOP (ne rien faire)
        - 1: REPLICATE (créer un réplica)
        - 2: DELETE (supprimer un réplica)
    
    Récompense:
        R = +r1·SLA_OK - r2·SLA_VIOL - r3·COST_OVER - r4·REPL_COST - r5·THRASH
    """
    
    metadata = {'render_modes': ['human'], 'render_fps': 4}
    
    def __init__(self, data_gb: float = 0.45, render_mode: Optional[str] = None):
        super().__init__()
        
        self.data_gb = data_gb
        self.render_mode = render_mode
        
        # Constantes
        self.MAX_QUERIES = 5000  # Aligné avec les benchmarks Java pour comparaison juste
        self.INITIAL_BUDGET = 1000.0
        # MAX_REPLICAS selon l'article: 5 pour simple queries, 13 pour complex queries
        self.MAX_REPLICAS_SIMPLE = 5
        self.MAX_REPLICAS_COMPLEX = 13
        self.COMPLEXITY_THRESHOLD = 10.0
        self.MAX_REPLICAS = self.MAX_REPLICAS_SIMPLE if data_gb < self.COMPLEXITY_THRESHOLD else self.MAX_REPLICAS_COMPLEX
        self.RT_MAX = 250.0  # Temps de réponse maximum (secondes)
        
        # Coûts
        self.COST_BW_INTRA_DC = 0.002
        self.COST_BW_INTER_REGION = 0.05
        self.COST_BW_INTER_CLOUD = 0.10
        # Storage cost (0.02 $/GB/mois -> /720 heures)
        self.STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0
        self.REPLICATION_COST_PER_GB = self.COST_BW_INTER_CLOUD
        self.CPU_COST_PER_HOUR = 0.02
        
        # Paramètres réseau
        self.BW_LOCAL_GBPS = 10.0
        self.BW_REMOTE_GBPS = 1.0
        self.LAT_LOCAL_MS = 1.0
        self.LAT_REMOTE_MS = 100.0
        
        # Poids de la fonction de récompense (A1 - Optimisés)
        self.R1_SLA_OK = 15.0
        self.R2_SLA_VIOL = 25.0
        self.R3_COST_OVER = 15.0
        self.R4_REPL_COST = 3.0  # Réduit pour encourager réplication avec A2
        self.R5_THRASH = 10.0  # Augmenté pour utiliser A3 efficacement
        
        # Action space: 0=NOOP, 1=REPLICATE, 2=DELETE
        self.action_space = spaces.Discrete(3)
        
        # Observation space: 8 dimensions continues
        self.observation_space = spaces.Box(
            low=np.array([0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0]),
            high=np.array([1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0]),
            dtype=np.float32
        )
        
        # Variables d'état
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = 0.0
        self.current_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.total_inter_region_traffic = 0.0
        self.total_inter_cloud_traffic = 0.0
        self.total_traffic = 0.0
        self.last_action = 0  # NOOP
        
        # Warm-up progressif des réplicas (pour descente graduelle)
        self.replica_warmup_progress = {}  # {replica_id: warmup_progress [0, 1]}
        self.WARMUP_QUERIES = 600  # Nombre de requêtes pour atteindre 100% d'efficacité (descente très progressive)
        self.action_history = []
        
        # Tracking cumulatif de la bande passante (Fig. 6)
        self.cumulative_bandwidth = 0.0
        self.bandwidth_history = []
        
        # PLSA model
        self.plsa_model = None
        self.popularity_history = []
        
        # A2: What-to-Replicate - Sélection TopK des données
        self.TOPK_RELATIONS = 3
        self.THETA_SCORE = 0.3
        self.lambda1 = 0.5  # Poids netImpact
        self.lambda2 = 0.3  # Poids popularité
        self.lambda3 = 0.2  # Poids storage cost
        
        # A3: Anti-Thrashing amélioré
        self.MIN_REPLICA_AGE = 100
        self.PSLA_DYN_BASE = 0.33
        self.replica_ages = {}
        self.replica_creation_query = {}
        self.TREND_WINDOW = 50
        
        # Random number generator
        self.np_random = None
        self._seed = None
    
    def reset(self, seed: Optional[int] = None, options: Optional[Dict[str, Any]] = None) -> Tuple[np.ndarray, Dict[str, Any]]:
        super().reset(seed=seed)
        
        self._seed = seed
        
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = self.LAT_REMOTE_MS / 1000.0  # En secondes
        self.current_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.total_inter_region_traffic = 0.0
        self.total_inter_cloud_traffic = 0.0
        self.total_traffic = 0.0
        self.last_action = 0
        self.action_history = []
        self.popularity_history = []
        
        # Réinitialiser A2 et A3
        self.replica_ages = {}
        self.replica_creation_query = {}
        
        # Réinitialiser warm-up et tracking bande passante
        self.replica_warmup_progress = {}
        self.cumulative_bandwidth = 0.0
        self.bandwidth_history = []
        
        # Créer un nouveau modèle PLSA indépendant pour chaque reset
        # Chaque modèle (Q-Learning, DQN, TCDRM Static, NOREP) aura son propre PLSA
        self.plsa_model = PLSAPopularityModel(n_topics=3, max_iterations=20, seed=seed)
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, info
    
    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict[str, Any]]:
        previous_replica_count = self.current_replica_count
        previous_budget = self.current_budget
        
        # Simuler la requête AVANT l'action
        query_latency = self._simulate_query()
        query_cost = self._calculate_query_cost()
        
        # Exécuter l'action
        action_executed = self._execute_action(action)
        
        # Mettre à jour l'état
        self.current_budget -= query_cost
        self.current_latency = query_latency
        self.current_query += 1
        self.total_cost += query_cost
        
        # Mettre à jour PLSA
        self.plsa_model.add_access(self.current_query)
        current_popularity = self.plsa_model.predict_popularity()
        self.popularity_history.append(current_popularity)
        
        # A3: Mettre à jour l'âge des réplicas
        for replica_id in list(self.replica_ages.keys()):
            self.replica_ages[replica_id] += 1
        
        # Tracking des actions
        self.action_history.append(action)
        
        # Violations SLA
        if query_latency > self.RT_MAX:
            self.sla_violations += 1
        
        # Calculer la récompense
        reward = self._calculate_reward(
            action, action_executed, previous_replica_count,
            previous_budget, query_cost, query_latency
        )
        
        # Termination
        terminated = self.current_query >= self.MAX_QUERIES
        truncated = self.current_budget <= 0
        
        # Mettre à jour last_action
        self.last_action = action
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, reward, terminated, truncated, info
    
    def _select_what_to_replicate(self) -> float:
        """
        A2: Sélection adaptative des données à répliquer (What-to-Replicate).
        Score = λ1*norm(netImpact) + λ2*norm(pop) − λ3*norm(storageCost)
        """
        net_impact = self.current_latency / self.RT_MAX
        net_impact_norm = min(1.0, net_impact)
        
        popularity = self.plsa_model.predict_popularity()
        pop_norm = popularity
        
        storage_cost = self.data_gb * self.STORAGE_COST_PER_GB_PER_HOUR
        storage_cost_norm = min(1.0, storage_cost / 0.1)
        
        score = (self.lambda1 * net_impact_norm + 
                self.lambda2 * pop_norm - 
                self.lambda3 * storage_cost_norm)
        
        if score > self.THETA_SCORE:
            fraction = 0.3 + 0.7 * min(1.0, (score - self.THETA_SCORE) / (1.0 - self.THETA_SCORE))
            return fraction
        else:
            return 0.0
    
    def _execute_action(self, action: int) -> bool:
        """Exécute l'action et retourne si elle a été effectuée"""
        if action == 1:  # REPLICATE
            if self.current_replica_count < self.MAX_REPLICAS:
                # A2: Sélectionner quelle fraction des données répliquer
                replication_fraction = self._select_what_to_replicate()
                
                if replication_fraction == 0.0:
                    return False
                
                data_to_replicate = self.data_gb * replication_fraction
                creation_cost = data_to_replicate * self.REPLICATION_COST_PER_GB
                
                if self.current_budget >= creation_cost:
                    self.current_replica_count += 1
                    self.current_budget -= creation_cost
                    self.total_cost += creation_cost
                    
                    # Initialiser le warm-up et tracking A3
                    self.replica_warmup_progress[self.current_replica_count] = 0.0
                    self.replica_creation_query[self.current_replica_count] = self.current_query
                    self.replica_ages[self.current_replica_count] = 0
                    return True
            return False
        
        elif action == 2:  # DELETE
            if self.current_replica_count > 0:
                # A3: Vérifier si le réplica peut être supprimé (anti-thrashing amélioré)
                replica_to_delete = self.current_replica_count
                
                # Vérifier l'âge minimal
                if replica_to_delete in self.replica_ages:
                    age = self.replica_ages[replica_to_delete]
                    if age < self.MIN_REPLICA_AGE:
                        return False
                
                # Calculer popularité moyenne et trend
                if len(self.popularity_history) >= 2:
                    pop_avg = np.mean(self.popularity_history[-self.TREND_WINDOW:])
                    
                    if len(self.popularity_history) >= 10:
                        recent_pop = np.mean(self.popularity_history[-10:])
                        older_pop = np.mean(self.popularity_history[:10])
                        trend = recent_pop - older_pop
                    else:
                        trend = 0.0
                    
                    # PSLA_dyn: seuil dynamique basé sur le budget
                    budget_ratio = self.current_budget / self.INITIAL_BUDGET
                    psla_dyn = self.PSLA_DYN_BASE * (1.0 + (1.0 - budget_ratio))
                    
                    if pop_avg >= psla_dyn or trend > 0.05:
                        return False
                
                # Supprimer le réplica
                if replica_to_delete in self.replica_warmup_progress:
                    del self.replica_warmup_progress[replica_to_delete]
                if replica_to_delete in self.replica_ages:
                    del self.replica_ages[replica_to_delete]
                if replica_to_delete in self.replica_creation_query:
                    del self.replica_creation_query[replica_to_delete]
                
                self.current_replica_count -= 1
                return True
            return False
        
        else:  # NOOP
            return True
    
    def _simulate_query(self) -> float:
        """Simule une requête et retourne la latence en secondes avec warm-up progressif (sigmoid)"""
        # Mettre à jour le warm-up des réplicas
        for replica_id in list(self.replica_warmup_progress.keys()):
            if self.replica_warmup_progress[replica_id] < 1.0:
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
        
        use_local = False
        traffic_type = 'inter_cloud'
        
        if self.current_replica_count > 0:
            # Probabilité d'accès local ajustée par le warm-up
            base_probability = self.current_replica_count / (self.current_replica_count + 2)
            local_probability = base_probability * avg_warmup
            use_local = self.np_random.random() < local_probability
            
            if use_local:
                traffic_type = 'intra_dc'
            else:
                # Probabilité d'utiliser inter-région vs inter-cloud
                if self.np_random.random() < 0.5:
                    traffic_type = 'inter_region'
        
        # Paramètres réseau
        if traffic_type == 'intra_dc':
            bw_gbps = self.BW_LOCAL_GBPS
            latency_ms = self.LAT_LOCAL_MS
        elif traffic_type == 'inter_region':
            bw_gbps = self.BW_REMOTE_GBPS * 0.5
            latency_ms = self.LAT_REMOTE_MS * 0.5
        else:  # inter_cloud
            bw_gbps = self.BW_REMOTE_GBPS
            latency_ms = self.LAT_REMOTE_MS
        
        # Tracking du trafic
        if traffic_type == 'inter_region':
            self.total_inter_region_traffic += self.data_gb
        elif traffic_type == 'inter_cloud':
            self.total_inter_cloud_traffic += self.data_gb
        self.total_traffic += self.data_gb
        
        # Tracking cumulatif de la bande passante (Fig. 6)
        self.cumulative_bandwidth += self.data_gb
        self.bandwidth_history.append(self.cumulative_bandwidth)
        
        # Temps de transfert
        transfer_ms = (self.data_gb * 8_000.0 / bw_gbps) + latency_ms
        transfer_ms *= (1.0 + 0.05 * (self.np_random.random() * 2 - 1))
        
        # Temps de traitement CPU
        processing_min = self.data_gb * 0.5
        processing_min *= (1.0 + 0.05 * (self.np_random.random() * 2 - 1))
        processing_ms = processing_min * 60_000.0
        
        total_time_ms = transfer_ms + processing_ms
        return total_time_ms / 1000.0  # Retourner en secondes
    
    def _calculate_query_cost(self) -> float:
        """Calcule le coût d'une requête"""
        use_local = False
        if self.current_replica_count > 0:
            local_probability = self.current_replica_count / (self.current_replica_count + 2)
            use_local = self.np_random.random() < local_probability
        
        # Coût de transfert
        if use_local:
            transfer_cost = self.data_gb * self.COST_BW_INTRA_DC
        else:
            # Mélange inter-région et inter-cloud
            if self.np_random.random() < 0.5:
                transfer_cost = self.data_gb * self.COST_BW_INTER_REGION
            else:
                transfer_cost = self.data_gb * self.COST_BW_INTER_CLOUD
        
        # Coût CPU
        processing_min = self.data_gb * 0.5
        cpu_cost = (processing_min / 60.0) * self.CPU_COST_PER_HOUR
        
        # Coût de stockage (négligeable selon Fig. 7)
        storage_cost = 0.0
        if self.current_replica_count > 0:
            query_duration_hours = processing_min / 60.0
            storage_cost = self.current_replica_count * self.data_gb * \
                          self.STORAGE_COST_PER_GB_PER_HOUR * query_duration_hours
        
        return transfer_cost + cpu_cost + storage_cost
    
    def _calculate_reward(self, action: int, action_executed: bool,
                         previous_replica_count: int, previous_budget: float,
                         query_cost: float, query_latency: float) -> float:
        """
        Fonction de récompense TCDRM v2:
        R = +r1·SLA_OK - r2·SLA_VIOL - r3·COST_OVER - r4·REPL_COST - r5·THRASH
        """
        
        # Normalisation
        tQ_norm = query_latency / self.RT_MAX
        cQ_norm = query_cost / (self.INITIAL_BUDGET / self.MAX_QUERIES)
        
        # 1. SLA_OK: Récompense si latence acceptable
        sla_ok = max(0.0, 1.0 - tQ_norm)
        
        # 2. SLA_VIOL: Pénalité si violation SLA
        sla_viol = max(0.0, tQ_norm - 1.0)
        
        # 3. COST_OVER: Pénalité si coût excessif
        cost_over = max(0.0, cQ_norm - 1.0)
        
        # 4. REPL_COST: Coût de réplication
        repl_cost = 0.0
        if action == 1 and action_executed:
            repl_cost = (self.data_gb * self.REPLICATION_COST_PER_GB) / self.INITIAL_BUDGET
        
        # 5. THRASH: Pénalité pour actions contradictoires
        thrash = 0.0
        if len(self.action_history) >= 2:
            if (self.action_history[-1] == 1 and action == 2) or \
               (self.action_history[-1] == 2 and action == 1):
                thrash = 1.0
        
        # Calcul de la récompense totale
        reward = (
            self.R1_SLA_OK * sla_ok -
            self.R2_SLA_VIOL * sla_viol -
            self.R3_COST_OVER * cost_over -
            self.R4_REPL_COST * repl_cost -
            self.R5_THRASH * thrash
        )
        
        return reward
    
    def _get_observation(self) -> np.ndarray:
        """
        Retourne l'état à 8 dimensions:
        [tQ_norm, cQ_norm, pop_norm, bud_norm, net_inter_ratio, 
         net_intercloud_ratio, repl_factor, trend_pop]
        """
        # 1. tQ_norm: Temps de réponse normalisé
        tQ_norm = np.clip(self.current_latency / self.RT_MAX, 0.0, 1.0)
        
        # 2. cQ_norm: Coût normalisé
        avg_cost_per_query = self.INITIAL_BUDGET / self.MAX_QUERIES
        current_cost = self.total_cost / max(1, self.current_query)
        cQ_norm = np.clip(current_cost / avg_cost_per_query, 0.0, 1.0)
        
        # 3. pop_norm: Popularité prédite
        pop_norm = self.plsa_model.predict_popularity()
        
        # 4. bud_norm: Budget restant
        bud_norm = np.clip(self.current_budget / self.INITIAL_BUDGET, 0.0, 1.0)
        
        # 5. net_inter_ratio: Ratio trafic inter-région
        net_inter_ratio = 0.0
        if self.total_traffic > 0:
            net_inter_ratio = self.total_inter_region_traffic / self.total_traffic
        
        # 6. net_intercloud_ratio: Ratio trafic inter-cloud
        net_intercloud_ratio = 0.0
        if self.total_traffic > 0:
            net_intercloud_ratio = self.total_inter_cloud_traffic / self.total_traffic
        
        # 7. repl_factor: Facteur de réplication normalisé
        repl_factor = self.current_replica_count / self.MAX_REPLICAS
        
        # 8. trend_pop: Tendance de popularité
        trend_pop = 0.0
        if len(self.popularity_history) >= 2:
            trend_pop = self.popularity_history[-1] - self.popularity_history[-2]
            trend_pop = np.clip(trend_pop, -1.0, 1.0)
        
        return np.array([
            tQ_norm, cQ_norm, pop_norm, bud_norm,
            net_inter_ratio, net_intercloud_ratio, repl_factor, trend_pop
        ], dtype=np.float32)
    
    def _get_info(self) -> Dict[str, Any]:
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
            'avg_cost_per_query': self.total_cost / max(1, self.current_query),
            'inter_region_traffic_ratio': self.total_inter_region_traffic / max(1, self.total_traffic),
            'inter_cloud_traffic_ratio': self.total_inter_cloud_traffic / max(1, self.total_traffic),
            'cumulative_bandwidth': self.cumulative_bandwidth,  # Bande passante cumulative (Fig. 6)
            'avg_replica_warmup': avg_warmup  # Efficacité moyenne des réplicas (Fig. 3)
        }
    
    def get_action_mask(self) -> np.ndarray:
        """
        Retourne un masque d'actions valides [1,1,1] où 0 = action interdite.
        """
        mask = np.ones(3, dtype=np.float32)
        
        # REPLICATE interdit si budget critique ou max réplicas atteint
        replication_cost = self.data_gb * self.REPLICATION_COST_PER_GB
        if self.current_replica_count >= self.MAX_REPLICAS or \
           self.current_budget < replication_cost * 2:  # Garde une marge
            mask[1] = 0
        
        # DELETE interdit si aucun réplica
        if self.current_replica_count == 0:
            mask[2] = 0
        
        return mask
    
    def render(self):
        if self.render_mode == 'human':
            print(f"Query: {self.current_query}/{self.MAX_QUERIES}")
            print(f"Budget: ${self.current_budget:.2f}")
            print(f"Latency: {self.current_latency:.2f}s")
            print(f"Replicas: {self.current_replica_count}")
            print(f"SLA Violations: {self.sla_violations}")
            print("-" * 50)
    
    def close(self):
        pass
