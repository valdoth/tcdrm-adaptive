import gymnasium as gym
from gymnasium import spaces
import numpy as np
from typing import Optional, Tuple, Dict, Any


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
        self.INITIAL_BUDGET = 100.0
        self.SLA_LATENCY_THRESHOLD = 150.0
        self.MAX_REPLICAS = 3
        
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
        
        # Observation space: [budget_ratio, latency, access_count_norm, replica_count, 
        #                     query_complexity, sla_violation_rate, cost_rate]
        self.observation_space = spaces.Box(
            low=np.array([0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0], dtype=np.float32),
            high=np.array([1.0, 300.0, 1.0, 3.0, 1.0, 1.0, 1.0], dtype=np.float32),
            dtype=np.float32
        )
        
        # Action space: discrete actions
        self.action_space = spaces.Discrete(3)
        
        # State variables
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = self.LAT_REMOTE_MS
        self.access_count = 0
        self.current_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.episode_rewards = []
        
        self.np_random = None
        
    def reset(self, seed: Optional[int] = None, options: Optional[Dict[str, Any]] = None) -> Tuple[np.ndarray, Dict[str, Any]]:
        super().reset(seed=seed)
        
        self.current_budget = self.INITIAL_BUDGET
        self.current_latency = self.LAT_REMOTE_MS
        self.access_count = 0
        self.current_replica_count = 0
        self.current_query = 0
        self.sla_violations = 0
        self.total_cost = 0.0
        self.episode_rewards = []
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, info
    
    def step(self, action: int) -> Tuple[np.ndarray, float, bool, bool, Dict[str, Any]]:
        previous_replica_count = self.current_replica_count
        previous_budget = self.current_budget
        
        # Execute action
        action_executed = self._execute_action(action)
        
        # Simulate query
        query_latency = self._simulate_query()
        query_cost = self._calculate_query_cost()
        
        # Update state
        self.current_budget -= query_cost
        self.current_latency = query_latency
        self.access_count += 1
        self.current_query += 1
        self.total_cost += query_cost
        
        # Track SLA violations
        if query_latency > self.SLA_LATENCY_THRESHOLD:
            self.sla_violations += 1
        
        # Calculate reward
        reward = self._calculate_reward(
            action, action_executed, previous_replica_count, 
            previous_budget, query_cost, query_latency
        )
        self.episode_rewards.append(reward)
        
        # Check termination
        terminated = self.current_query >= self.MAX_QUERIES
        truncated = self.current_budget <= 0
        
        observation = self._get_observation()
        info = self._get_info()
        
        return observation, reward, terminated, truncated, info
    
    def _execute_action(self, action: int) -> bool:
        if action == 0:  # CREATE_REPLICA
            if self.current_replica_count < self.MAX_REPLICAS:
                creation_cost = self.data_gb * self.REPLICATION_COST_PER_GB
                if self.current_budget >= creation_cost:
                    self.current_replica_count += 1
                    self.current_budget -= creation_cost
                    return True
            return False
            
        elif action == 1:  # DELETE_REPLICA
            if self.current_replica_count > 0:
                self.current_replica_count -= 1
                return True
            return False
            
        else:  # DO_NOTHING
            return True
    
    def _simulate_query(self) -> float:
        if self.current_replica_count > 0:
            # Probability of local access based on replica count
            local_probability = self.current_replica_count / (self.current_replica_count + 2)
            use_local = self.np_random.random() < local_probability
            
            if use_local:
                # Local access with small variance
                return self.LAT_LOCAL_MS + self.np_random.normal(0, 0.5)
        
        # Remote access with variance
        return self.LAT_REMOTE_MS * (1.0 + self.np_random.normal(0, 0.15))
    
    def _calculate_query_cost(self) -> float:
        if self.current_replica_count > 0:
            local_probability = self.current_replica_count / (self.current_replica_count + 2)
            use_local = self.np_random.random() < local_probability
            transfer_cost = self.data_gb * (self.COST_BW_INTRA_DC if use_local else self.COST_BW_INTER_PROVIDER)
        else:
            transfer_cost = self.data_gb * self.COST_BW_INTER_PROVIDER
        
        # Storage cost proportional to replica count
        storage_cost = self.current_replica_count * self.data_gb * self.STORAGE_COST_PER_GB_PER_HOUR
        
        return transfer_cost + storage_cost
    
    def _calculate_reward(self, action: int, action_executed: bool, 
                         previous_replica_count: int, previous_budget: float,
                         query_cost: float, query_latency: float) -> float:
        """
        Advanced multi-objective reward function
        Inspired by rl-cloudsimplus projects
        """
        reward = 0.0
        
        # 1. SLA Compliance Reward (highest priority)
        if query_latency < self.SLA_LATENCY_THRESHOLD:
            reward += 10.0
            # Extra bonus for very low latency
            if query_latency < 50.0:
                reward += 5.0
        else:
            # Penalty proportional to violation severity
            violation_ratio = (query_latency - self.SLA_LATENCY_THRESHOLD) / self.SLA_LATENCY_THRESHOLD
            reward -= 15.0 * violation_ratio
        
        # 2. Cost Efficiency Reward
        if self.current_replica_count > 0 and query_latency < self.LAT_REMOTE_MS:
            # Reward for bandwidth savings
            savings = self.data_gb * (self.COST_BW_INTER_PROVIDER - self.COST_BW_INTRA_DC)
            reward += savings * 20.0
        
        # 3. Budget Management
        budget_ratio = self.current_budget / self.INITIAL_BUDGET
        if budget_ratio < 0.1:
            reward -= 50.0  # Critical budget
        elif budget_ratio < 0.2:
            reward -= 20.0  # Low budget warning
        elif budget_ratio > 0.5:
            reward += 5.0   # Good budget management
        
        # 4. Resource Efficiency
        if self.current_replica_count > 2:
            # Penalty for over-replication
            reward -= 3.0 * (self.current_replica_count - 2)
        
        # 5. Smart Action Rewards
        if action == 1 and action_executed and self.access_count < 150:
            # Good decision to delete replica when low popularity
            reward += 5.0
        
        if action == 0 and action_executed and self.access_count > 250:
            # Good decision to create replica when high popularity
            reward += 5.0
        
        # 6. Failed Action Penalty
        if not action_executed and action != 2:
            reward -= 3.0
        
        # 7. Progressive SLA Compliance Bonus
        if self.current_query > 0:
            sla_compliance_rate = 1.0 - (self.sla_violations / self.current_query)
            if sla_compliance_rate > 0.95:
                reward += 10.0
            elif sla_compliance_rate > 0.90:
                reward += 5.0
        
        # 8. Cost-Latency Trade-off Balance
        if query_cost < 0.5 and query_latency < self.SLA_LATENCY_THRESHOLD:
            # Excellent balance
            reward += 8.0
        
        return reward
    
    def _get_observation(self) -> np.ndarray:
        budget_ratio = np.clip(self.current_budget / self.INITIAL_BUDGET, 0.0, 1.0)
        latency = np.clip(self.current_latency, 0.0, 300.0)
        access_count_norm = np.clip(self.access_count / self.MAX_QUERIES, 0.0, 1.0)
        replica_count = float(self.current_replica_count)
        query_complexity = np.clip(self.data_gb / 20.0, 0.0, 1.0)  # Normalize by max expected size
        sla_violation_rate = self.sla_violations / max(1, self.current_query)
        cost_rate = np.clip(self.total_cost / self.INITIAL_BUDGET, 0.0, 1.0)
        
        return np.array([
            budget_ratio, latency, access_count_norm, replica_count,
            query_complexity, sla_violation_rate, cost_rate
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
            'access_count': self.access_count
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
