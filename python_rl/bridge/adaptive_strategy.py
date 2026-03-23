"""
État adaptatif pour le suivi anti-thrashing des modèles RL.
"""

from dataclasses import dataclass, field
from typing import List


@dataclass
class AdaptiveState:
    """
    État adaptatif pour un modèle RL.
    
    Utilisé pour:
    - Suivi anti-thrashing (historique des actions)
    - Compteurs de requêtes
    """
    query_counter: int = 0
    last_replicate_q: int = -200
    last_delete_q: int = -1000
    action_history: List[int] = field(default_factory=list)
    
    def reset(self, initial_p_threshold: float = 0.5, initial_replicate_gap: int = -200):
        """Reset state between benchmark runs."""
        self.query_counter = 0
        self.last_replicate_q = initial_replicate_gap
        self.last_delete_q = -1000
        self.action_history = []
