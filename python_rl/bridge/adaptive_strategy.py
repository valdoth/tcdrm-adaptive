"""
TCDRM-ADAPTIVE replication strategies.
Implements Algorithm A1 (adaptive replication) and A3 (anti-thrashing, budget-aware deletion).
"""

from dataclasses import dataclass, field
from typing import List


@dataclass
class AdaptiveState:
    """Tracks adaptive state for a single RL model."""
    query_counter: int = 0
    last_replicate_q: int = -200
    last_delete_q: int = -1000
    action_history: List[int] = field(default_factory=list)
    sla_violations: int = 0
    adaptive_threshold: float = 0.8
    
    def reset(self, initial_threshold: float = 0.8, initial_replicate_gap: int = -200):
        """Reset state between benchmark runs."""
        self.query_counter = 0
        self.last_replicate_q = initial_replicate_gap
        self.last_delete_q = -1000
        self.action_history = []
        self.sla_violations = 0
        self.adaptive_threshold = initial_threshold


class AdaptiveStrategy:
    """
    TCDRM-ADAPTIVE replication strategy.
    
    Implements:
    - Algorithm A1: Adaptive replication with dynamic thresholds
    - Algorithm A3: Anti-thrashing + budget-aware deletion
    """
    
    def __init__(self, 
                 initial_threshold: float = 0.8,
                 min_threshold: float = 0.5,
                 threshold_decay: float = 0.05,
                 sla_violation_trigger: int = 10,
                 replicate_interval: int = 50,
                 delete_interval: int = 100,
                 history_window: int = 20,
                 thrashing_window: int = 10):
        """
        Initialize adaptive strategy.
        
        Args:
            initial_threshold: Starting popularity threshold (fraction of P_SLA)
            min_threshold: Minimum threshold after adaptation
            threshold_decay: How much to lower threshold per adaptation
            sla_violation_trigger: SLA violations before adapting threshold
            replicate_interval: Min queries between replications
            delete_interval: Min queries between deletions
            history_window: Size of action history for anti-thrashing
            thrashing_window: Window to detect thrashing
        """
        self.initial_threshold = initial_threshold
        self.min_threshold = min_threshold
        self.threshold_decay = threshold_decay
        self.sla_violation_trigger = sla_violation_trigger
        self.replicate_interval = replicate_interval
        self.delete_interval = delete_interval
        self.history_window = history_window
        self.thrashing_window = thrashing_window
        
        self.state = AdaptiveState(adaptive_threshold=initial_threshold)
    
    def reset(self):
        """Reset strategy state."""
        self.state.reset(self.initial_threshold, -self.replicate_interval * 4)
    
    def select_action(self, 
                      replicas: int,
                      max_replicas: int,
                      budget: float,
                      normalized_popularity: float,
                      t_sla_violation: float,
                      c_sla_violation: float,
                      budget_threshold: float = 0.3,
                      popularity_delete_threshold: float = 0.5) -> int:
        """
        Select action using TCDRM-ADAPTIVE strategy.
        
        Args:
            replicas: Current replica count
            max_replicas: Maximum allowed replicas
            budget: Current budget
            normalized_popularity: Popularity as fraction of P_SLA (1.0 = at threshold)
            t_sla_violation: T_SLA violation flag (>0.5 = violated)
            c_sla_violation: C_SLA violation flag (>0.5 = violated)
            budget_threshold: Budget ratio below which to consider deletion
            popularity_delete_threshold: Popularity below which to consider deletion
            
        Returns:
            Action: 0=NOOP, 1=REPLICATE, 2=DELETE
        """
        self.state.query_counter += 1
        queries_since_replicate = self.state.query_counter - self.state.last_replicate_q
        queries_since_delete = self.state.query_counter - self.state.last_delete_q
        
        sla_violated = (t_sla_violation > 0.5 or c_sla_violation > 0.5)
        
        # Track SLA violations for adaptive threshold
        if sla_violated:
            self.state.sla_violations += 1
            if self.state.sla_violations > self.sla_violation_trigger:
                self.state.adaptive_threshold = max(
                    self.min_threshold, 
                    self.state.adaptive_threshold - self.threshold_decay
                )
        
        popularity_ready = (normalized_popularity >= self.state.adaptive_threshold)
        budget_ratio = budget / 1000.0
        
        # Anti-thrashing check
        self.state.action_history.append(0)
        if len(self.state.action_history) > self.history_window:
            self.state.action_history.pop(0)
        
        recent = self.state.action_history[-self.thrashing_window:]
        recent_creates = sum(1 for a in recent if a == 1)
        recent_deletes = sum(1 for a in recent if a == 2)
        thrashing = (recent_creates >= 2 and recent_deletes >= 2)
        
        # === REPLICATION DECISION ===
        if replicas < max_replicas and budget > 5 and not thrashing:
            # First replica when SLA violated + popularity building
            if sla_violated and popularity_ready and replicas == 0:
                self._record_action(1)
                return 1
            
            # Gradual replication while SLA violated
            if sla_violated and replicas > 0 and queries_since_replicate >= self.replicate_interval:
                self._record_action(1)
                return 1
            
            # Proactive replication for popular data
            if normalized_popularity >= 1.0 and replicas < max_replicas // 2:
                if queries_since_replicate >= self.replicate_interval // 2:
                    self._record_action(1)
                    return 1
        
        # === DELETION DECISION (Algorithm A3) ===
        if (replicas > 1 and budget_ratio < budget_threshold 
            and normalized_popularity < popularity_delete_threshold
            and not sla_violated and queries_since_delete > self.delete_interval 
            and not thrashing):
            self._record_action(2)
            return 2
        
        return 0  # NOOP
    
    def _record_action(self, action: int):
        """Record action and update tracking."""
        self.state.action_history[-1] = action
        if action == 1:
            self.state.last_replicate_q = self.state.query_counter
        elif action == 2:
            self.state.last_delete_q = self.state.query_counter
