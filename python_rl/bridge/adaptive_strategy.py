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
    
    # Compteurs de violations SLA
    t_sla_violations: int = 0  # Violations de latence (T_SLA)
    c_sla_violations: int = 0  # Violations de coût (C_SLA)
    total_queries_window: int = 0  # Requêtes dans la fenêtre courante
    
    # Seuils adaptatifs
    adaptive_p_threshold: float = 0.8  # P_SLA dynamique (popularité)
    adaptive_t_factor: float = 1.0     # T_SLA dynamique (facteur multiplicatif)
    
    def reset(self, initial_p_threshold: float = 0.8, initial_replicate_gap: int = -200):
        """Reset state between benchmark runs."""
        self.query_counter = 0
        self.last_replicate_q = initial_replicate_gap
        self.last_delete_q = -1000
        self.action_history = []
        self.t_sla_violations = 0
        self.c_sla_violations = 0
        self.total_queries_window = 0
        self.adaptive_p_threshold = initial_p_threshold
        self.adaptive_t_factor = 1.0


class AdaptiveStrategy:
    """
    TCDRM-ADAPTIVE replication strategy.
    
    Implements:
    - Algorithm A1: Adaptive replication with dynamic P_SLA and T_SLA
    - Algorithm A3: Anti-thrashing + budget-aware deletion
    
    Seuils dynamiques (Axe 4 du sujet):
    - P_SLA(t): Seuil de popularité qui s'adapte selon le budget et les violations
    - T_SLA(t): Seuil de latence qui s'adapte selon le taux de violations
    """
    
    def __init__(self, 
                 initial_p_threshold: float = 0.8,
                 min_p_threshold: float = 0.3,
                 max_p_threshold: float = 1.2,
                 p_threshold_decay: float = 0.05,
                 p_threshold_increase: float = 0.02,
                 min_t_factor: float = 0.7,
                 max_t_factor: float = 1.3,
                 t_factor_step: float = 0.05,
                 sla_window: int = 50,
                 violation_rate_threshold: float = 0.1,
                 replicate_interval: int = 50,
                 delete_interval: int = 100,
                 history_window: int = 20,
                 thrashing_window: int = 10):
        """
        Initialize adaptive strategy with dynamic T_SLA and P_SLA.
        
        Args:
            initial_p_threshold: Starting P_SLA threshold (fraction of base P_SLA)
            min_p_threshold: Minimum P_SLA threshold
            max_p_threshold: Maximum P_SLA threshold
            p_threshold_decay: How much to lower P_SLA per adaptation
            p_threshold_increase: How much to raise P_SLA when stable
            min_t_factor: Minimum T_SLA factor (0.7 = 70% of base T_SLA)
            max_t_factor: Maximum T_SLA factor (1.3 = 130% of base T_SLA)
            t_factor_step: Step for T_SLA adjustment
            sla_window: Window size for calculating violation rate
            violation_rate_threshold: Violation rate above which to adapt
            replicate_interval: Min queries between replications
            delete_interval: Min queries between deletions
            history_window: Size of action history for anti-thrashing
            thrashing_window: Window to detect thrashing
        """
        # P_SLA parameters
        self.initial_p_threshold = initial_p_threshold
        self.min_p_threshold = min_p_threshold
        self.max_p_threshold = max_p_threshold
        self.p_threshold_decay = p_threshold_decay
        self.p_threshold_increase = p_threshold_increase
        
        # T_SLA parameters
        self.min_t_factor = min_t_factor
        self.max_t_factor = max_t_factor
        self.t_factor_step = t_factor_step
        
        # SLA monitoring
        self.sla_window = sla_window
        self.violation_rate_threshold = violation_rate_threshold
        
        # Replication intervals
        self.replicate_interval = replicate_interval
        self.delete_interval = delete_interval
        self.history_window = history_window
        self.thrashing_window = thrashing_window
        
        self.state = AdaptiveState(adaptive_p_threshold=initial_p_threshold)
    
    def reset(self):
        """Reset strategy state."""
        self.state.reset(self.initial_p_threshold, -self.replicate_interval * 4)
    
    def _update_dynamic_thresholds(self, t_sla_violation: float, c_sla_violation: float, budget_ratio: float):
        """
        Met à jour les seuils T_SLA et P_SLA dynamiquement.
        
        T_SLA dynamique:
        - Si taux de violations T_SLA élevé → baisser le seuil (tolérer plus de latence)
        - Si taux de violations faible → remonter le seuil (exiger moins de latence)
        
        P_SLA dynamique:
        - Si violations fréquentes → baisser le seuil (répliquer plus tôt)
        - Si budget faible → augmenter le seuil (économiser)
        - Si stable → remonter progressivement vers la valeur initiale
        """
        self.state.total_queries_window += 1
        
        # Compter les violations
        if t_sla_violation > 0.5:
            self.state.t_sla_violations += 1
        if c_sla_violation > 0.5:
            self.state.c_sla_violations += 1
        
        # Ajuster les seuils à chaque fin de fenêtre
        if self.state.total_queries_window >= self.sla_window:
            t_violation_rate = self.state.t_sla_violations / self.sla_window
            c_violation_rate = self.state.c_sla_violations / self.sla_window
            
            # === T_SLA DYNAMIQUE ===
            # Beaucoup de violations de latence → augmenter T_SLA (tolérer plus)
            if t_violation_rate > self.violation_rate_threshold:
                self.state.adaptive_t_factor = min(
                    self.max_t_factor,
                    self.state.adaptive_t_factor + self.t_factor_step
                )
            # Peu de violations → baisser T_SLA (exiger moins de latence)
            elif t_violation_rate < self.violation_rate_threshold / 2:
                self.state.adaptive_t_factor = max(
                    self.min_t_factor,
                    self.state.adaptive_t_factor - self.t_factor_step / 2
                )
            
            # === P_SLA DYNAMIQUE ===
            # Beaucoup de violations → baisser P_SLA (répliquer plus tôt)
            if t_violation_rate > self.violation_rate_threshold or c_violation_rate > self.violation_rate_threshold:
                self.state.adaptive_p_threshold = max(
                    self.min_p_threshold,
                    self.state.adaptive_p_threshold - self.p_threshold_decay
                )
            # Stable et budget OK → remonter P_SLA progressivement
            elif t_violation_rate < self.violation_rate_threshold / 2 and budget_ratio > 0.5:
                self.state.adaptive_p_threshold = min(
                    self.max_p_threshold,
                    self.state.adaptive_p_threshold + self.p_threshold_increase
                )
            
            # Reset compteurs pour la prochaine fenêtre
            self.state.t_sla_violations = 0
            self.state.c_sla_violations = 0
            self.state.total_queries_window = 0
    
    def get_dynamic_t_sla(self, base_t_sla: float) -> float:
        """Retourne le T_SLA dynamique actuel."""
        return base_t_sla * self.state.adaptive_t_factor
    
    def get_dynamic_p_sla(self, budget_ratio: float) -> float:
        """Retourne le P_SLA dynamique actuel, modulé par le budget."""
        # P_SLA(t) = P_base × (budget_restant / budget_initial)
        budget_factor = max(0.3, min(1.0, budget_ratio))
        return self.state.adaptive_p_threshold * budget_factor
    
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
        Select action using TCDRM-ADAPTIVE strategy with dynamic T_SLA and P_SLA.
        
        Args:
            replicas: Current replica count
            max_replicas: Maximum allowed replicas
            budget: Current budget (normalized 0-1)
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
        
        # Budget is already normalized (0-1)
        budget_ratio = budget
        
        # === MISE À JOUR DES SEUILS DYNAMIQUES (Axe 4 du sujet) ===
        self._update_dynamic_thresholds(t_sla_violation, c_sla_violation, budget_ratio)
        
        # Obtenir les seuils dynamiques
        dynamic_p_threshold = self.get_dynamic_p_sla(budget_ratio)
        
        sla_violated = (t_sla_violation > 0.5 or c_sla_violation > 0.5)
        popularity_ready = (normalized_popularity >= dynamic_p_threshold)
        
        # Anti-thrashing check
        self.state.action_history.append(0)
        if len(self.state.action_history) > self.history_window:
            self.state.action_history.pop(0)
        
        recent = self.state.action_history[-self.thrashing_window:]
        recent_creates = sum(1 for a in recent if a == 1)
        recent_deletes = sum(1 for a in recent if a == 2)
        thrashing = (recent_creates >= 2 and recent_deletes >= 2)
        
        # === REPLICATION DECISION ===
        if replicas < max_replicas and budget_ratio > 0.01 and not thrashing:
            # First replica when popularity reaches dynamic threshold
            if popularity_ready and replicas == 0:
                self._record_action(1)
                return 1
            
            # Additional replicas: SLA violated OR high popularity + interval passed
            if replicas > 0 and queries_since_replicate >= self.replicate_interval:
                if sla_violated or normalized_popularity >= 1.0:
                    self._record_action(1)
                    return 1
            
            # Proactive replication for very popular data (popularity > P_SLA)
            if normalized_popularity >= 1.2 and replicas < max_replicas // 2:
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
