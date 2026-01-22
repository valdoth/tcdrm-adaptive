"""
Metrics calculation and tracking for TCDRM-ADAPTIVE
"""

import numpy as np
from typing import Dict, List, Any
from dataclasses import dataclass, field


@dataclass
class MetricsTracker:
    """Track and calculate TCDRM metrics"""
    
    latencies: List[float] = field(default_factory=list)
    costs: List[float] = field(default_factory=list)
    sla_violations: int = 0
    total_queries: int = 0
    rewards: List[float] = field(default_factory=list)
    actions: List[int] = field(default_factory=list)
    replica_counts: List[int] = field(default_factory=list)
    budgets: List[float] = field(default_factory=list)
    
    def add_step(self, latency: float, cost: float, reward: float, 
                 action: int, replicas: int, budget: float, sla_violated: bool):
        """Add metrics from a single step"""
        self.latencies.append(latency)
        self.costs.append(cost)
        self.rewards.append(reward)
        self.actions.append(action)
        self.replica_counts.append(replicas)
        self.budgets.append(budget)
        self.total_queries += 1
        if sla_violated:
            self.sla_violations += 1
    
    def get_summary(self) -> Dict[str, Any]:
        """Get summary statistics"""
        if self.total_queries == 0:
            return {}
        
        return {
            'avg_latency': np.mean(self.latencies),
            'std_latency': np.std(self.latencies),
            'max_latency': np.max(self.latencies),
            'min_latency': np.min(self.latencies),
            'p95_latency': np.percentile(self.latencies, 95),
            'p99_latency': np.percentile(self.latencies, 99),
            
            'total_cost': sum(self.costs),
            'avg_cost_per_query': np.mean(self.costs),
            'std_cost': np.std(self.costs),
            
            'total_reward': sum(self.rewards),
            'avg_reward': np.mean(self.rewards),
            'std_reward': np.std(self.rewards),
            
            'sla_compliance_rate': 1.0 - (self.sla_violations / self.total_queries),
            'sla_violations': self.sla_violations,
            'total_queries': self.total_queries,
            
            'avg_replicas': np.mean(self.replica_counts),
            'max_replicas': np.max(self.replica_counts),
            
            'final_budget': self.budgets[-1] if self.budgets else 0,
            'budget_used': self.budgets[0] - self.budgets[-1] if self.budgets else 0,
            
            'action_distribution': {
                'create_replica': self.actions.count(0) / len(self.actions),
                'delete_replica': self.actions.count(1) / len(self.actions),
                'do_nothing': self.actions.count(2) / len(self.actions)
            }
        }
    
    def reset(self):
        """Reset all metrics"""
        self.latencies = []
        self.costs = []
        self.sla_violations = 0
        self.total_queries = 0
        self.rewards = []
        self.actions = []
        self.replica_counts = []
        self.budgets = []


def calculate_metrics(env_infos: List[Dict[str, Any]], 
                     sla_threshold: float = 150.0) -> Dict[str, Any]:
    """
    Calculate comprehensive metrics from environment info
    
    Args:
        env_infos: List of info dicts from environment steps
        sla_threshold: SLA latency threshold in ms
    
    Returns:
        Dictionary of calculated metrics
    """
    if not env_infos:
        return {}
    
    latencies = [info.get('latency', 0) for info in env_infos]
    costs = [info.get('total_cost', 0) for info in env_infos]
    sla_violations = sum(1 for lat in latencies if lat > sla_threshold)
    
    metrics = {
        'latency': {
            'mean': np.mean(latencies),
            'std': np.std(latencies),
            'min': np.min(latencies),
            'max': np.max(latencies),
            'p50': np.percentile(latencies, 50),
            'p95': np.percentile(latencies, 95),
            'p99': np.percentile(latencies, 99)
        },
        'cost': {
            'total': costs[-1] if costs else 0,
            'mean_per_query': np.mean(np.diff([0] + costs)) if len(costs) > 1 else 0,
            'std': np.std(np.diff([0] + costs)) if len(costs) > 1 else 0
        },
        'sla': {
            'violations': sla_violations,
            'compliance_rate': 1.0 - (sla_violations / len(latencies)),
            'violation_rate': sla_violations / len(latencies)
        },
        'queries': {
            'total': len(env_infos)
        }
    }
    
    return metrics


def compare_algorithms(results: Dict[str, MetricsTracker]) -> Dict[str, Any]:
    """
    Compare multiple algorithm results
    
    Args:
        results: Dictionary mapping algorithm name to MetricsTracker
    
    Returns:
        Comparison metrics
    """
    comparison = {}
    
    for algo_name, tracker in results.items():
        summary = tracker.get_summary()
        comparison[algo_name] = {
            'avg_latency': summary.get('avg_latency', 0),
            'sla_compliance': summary.get('sla_compliance_rate', 0),
            'total_cost': summary.get('total_cost', 0),
            'total_reward': summary.get('total_reward', 0),
            'avg_reward': summary.get('avg_reward', 0)
        }
    
    # Find best algorithm for each metric
    if comparison:
        comparison['best'] = {
            'latency': min(comparison.items(), key=lambda x: x[1]['avg_latency'])[0],
            'sla_compliance': max(comparison.items(), key=lambda x: x[1]['sla_compliance'])[0],
            'cost': min(comparison.items(), key=lambda x: x[1]['total_cost'])[0],
            'reward': max(comparison.items(), key=lambda x: x[1]['total_reward'])[0]
        }
    
    return comparison
