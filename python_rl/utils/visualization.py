"""
Visualization utilities for TCDRM-ADAPTIVE training results
"""

import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import pandas as pd
from typing import Dict, List, Any, Optional
import os


sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (12, 8)


def plot_training_results(metrics_file: str, save_path: Optional[str] = None):
    """
    Plot training results from metrics file
    
    Args:
        metrics_file: Path to JSON metrics file
        save_path: Path to save plot (optional)
    """
    import json
    
    with open(metrics_file, 'r') as f:
        metrics = json.load(f)
    
    if not metrics:
        print("No metrics to plot")
        return
    
    episodes = [m['episode'] for m in metrics]
    avg_latencies = [m['avg_latency'] for m in metrics]
    sla_compliance = [m['sla_compliance_rate'] * 100 for m in metrics]
    total_costs = [m['total_cost'] for m in metrics]
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    
    # Average Latency
    axes[0, 0].plot(episodes, avg_latencies, 'b-', linewidth=2)
    axes[0, 0].axhline(y=150, color='r', linestyle='--', label='SLA Threshold')
    axes[0, 0].set_xlabel('Episode')
    axes[0, 0].set_ylabel('Average Latency (ms)')
    axes[0, 0].set_title('Average Latency per Episode')
    axes[0, 0].legend()
    axes[0, 0].grid(True, alpha=0.3)
    
    # SLA Compliance Rate
    axes[0, 1].plot(episodes, sla_compliance, 'g-', linewidth=2)
    axes[0, 1].axhline(y=95, color='r', linestyle='--', label='Target 95%')
    axes[0, 1].set_xlabel('Episode')
    axes[0, 1].set_ylabel('SLA Compliance (%)')
    axes[0, 1].set_title('SLA Compliance Rate per Episode')
    axes[0, 1].legend()
    axes[0, 1].grid(True, alpha=0.3)
    axes[0, 1].set_ylim([0, 105])
    
    # Total Cost
    axes[1, 0].plot(episodes, total_costs, 'orange', linewidth=2)
    axes[1, 0].set_xlabel('Episode')
    axes[1, 0].set_ylabel('Total Cost ($)')
    axes[1, 0].set_title('Total Cost per Episode')
    axes[1, 0].grid(True, alpha=0.3)
    
    # Moving Average of Latency
    window = min(10, len(avg_latencies))
    if window > 1:
        moving_avg = pd.Series(avg_latencies).rolling(window=window).mean()
        axes[1, 1].plot(episodes, avg_latencies, 'b-', alpha=0.3, label='Raw')
        axes[1, 1].plot(episodes, moving_avg, 'b-', linewidth=2, label=f'MA({window})')
        axes[1, 1].axhline(y=150, color='r', linestyle='--', label='SLA Threshold')
        axes[1, 1].set_xlabel('Episode')
        axes[1, 1].set_ylabel('Average Latency (ms)')
        axes[1, 1].set_title('Latency Trend (Moving Average)')
        axes[1, 1].legend()
        axes[1, 1].grid(True, alpha=0.3)
    
    plt.tight_layout()
    
    if save_path:
        plt.savefig(save_path, dpi=300, bbox_inches='tight')
        print(f"Plot saved to {save_path}")
    else:
        plt.show()
    
    plt.close()


def plot_comparison(results: Dict[str, Dict[str, Any]], save_path: Optional[str] = None):
    """
    Compare multiple algorithms
    
    Args:
        results: Dictionary mapping algorithm name to metrics
        save_path: Path to save plot
    """
    algorithms = list(results.keys())
    
    # Extract metrics
    avg_latencies = [results[algo].get('avg_latency', 0) for algo in algorithms]
    sla_compliance = [results[algo].get('sla_compliance_rate', 0) * 100 for algo in algorithms]
    total_costs = [results[algo].get('total_cost', 0) for algo in algorithms]
    avg_rewards = [results[algo].get('avg_reward', 0) for algo in algorithms]
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    
    # Average Latency Comparison
    axes[0, 0].bar(algorithms, avg_latencies, color=['#1f77b4', '#ff7f0e', '#2ca02c'])
    axes[0, 0].axhline(y=150, color='r', linestyle='--', label='SLA Threshold')
    axes[0, 0].set_ylabel('Average Latency (ms)')
    axes[0, 0].set_title('Average Latency Comparison')
    axes[0, 0].legend()
    axes[0, 0].grid(True, alpha=0.3, axis='y')
    
    # SLA Compliance Comparison
    axes[0, 1].bar(algorithms, sla_compliance, color=['#1f77b4', '#ff7f0e', '#2ca02c'])
    axes[0, 1].axhline(y=95, color='r', linestyle='--', label='Target 95%')
    axes[0, 1].set_ylabel('SLA Compliance (%)')
    axes[0, 1].set_title('SLA Compliance Rate Comparison')
    axes[0, 1].legend()
    axes[0, 1].grid(True, alpha=0.3, axis='y')
    axes[0, 1].set_ylim([0, 105])
    
    # Total Cost Comparison
    axes[1, 0].bar(algorithms, total_costs, color=['#1f77b4', '#ff7f0e', '#2ca02c'])
    axes[1, 0].set_ylabel('Total Cost ($)')
    axes[1, 0].set_title('Total Cost Comparison')
    axes[1, 0].grid(True, alpha=0.3, axis='y')
    
    # Average Reward Comparison
    axes[1, 1].bar(algorithms, avg_rewards, color=['#1f77b4', '#ff7f0e', '#2ca02c'])
    axes[1, 1].set_ylabel('Average Reward')
    axes[1, 1].set_title('Average Reward Comparison')
    axes[1, 1].grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    
    if save_path:
        plt.savefig(save_path, dpi=300, bbox_inches='tight')
        print(f"Comparison plot saved to {save_path}")
    else:
        plt.show()
    
    plt.close()


def plot_episode_details(latencies: List[float], costs: List[float], 
                         actions: List[int], replicas: List[int],
                         sla_threshold: float = 150.0,
                         save_path: Optional[str] = None):
    """
    Plot detailed episode information
    
    Args:
        latencies: List of latencies per step
        costs: List of cumulative costs per step
        actions: List of actions taken
        replicas: List of replica counts
        sla_threshold: SLA latency threshold
        save_path: Path to save plot
    """
    steps = list(range(len(latencies)))
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    
    # Latency over time
    axes[0, 0].plot(steps, latencies, 'b-', linewidth=1, alpha=0.7)
    axes[0, 0].axhline(y=sla_threshold, color='r', linestyle='--', label=f'SLA ({sla_threshold}ms)')
    axes[0, 0].set_xlabel('Step')
    axes[0, 0].set_ylabel('Latency (ms)')
    axes[0, 0].set_title('Latency per Step')
    axes[0, 0].legend()
    axes[0, 0].grid(True, alpha=0.3)
    
    # Cumulative cost
    axes[0, 1].plot(steps, costs, 'orange', linewidth=2)
    axes[0, 1].set_xlabel('Step')
    axes[0, 1].set_ylabel('Cumulative Cost ($)')
    axes[0, 1].set_title('Cumulative Cost over Time')
    axes[0, 1].grid(True, alpha=0.3)
    
    # Actions distribution
    action_names = ['Create Replica', 'Delete Replica', 'Do Nothing']
    action_counts = [actions.count(i) for i in range(3)]
    axes[1, 0].bar(action_names, action_counts, color=['#2ca02c', '#d62728', '#7f7f7f'])
    axes[1, 0].set_ylabel('Count')
    axes[1, 0].set_title('Action Distribution')
    axes[1, 0].grid(True, alpha=0.3, axis='y')
    
    # Replica count over time
    axes[1, 1].plot(steps, replicas, 'g-', linewidth=2)
    axes[1, 1].set_xlabel('Step')
    axes[1, 1].set_ylabel('Number of Replicas')
    axes[1, 1].set_title('Replica Count over Time')
    axes[1, 1].set_ylim([-0.5, 3.5])
    axes[1, 1].grid(True, alpha=0.3)
    
    plt.tight_layout()
    
    if save_path:
        plt.savefig(save_path, dpi=300, bbox_inches='tight')
        print(f"Episode details plot saved to {save_path}")
    else:
        plt.show()
    
    plt.close()


def plot_learning_curve(rewards: List[float], window: int = 100, 
                       save_path: Optional[str] = None):
    """
    Plot learning curve with moving average
    
    Args:
        rewards: List of episode rewards
        window: Window size for moving average
        save_path: Path to save plot
    """
    episodes = list(range(len(rewards)))
    
    plt.figure(figsize=(12, 6))
    
    # Raw rewards
    plt.plot(episodes, rewards, alpha=0.3, color='blue', label='Episode Reward')
    
    # Moving average
    if len(rewards) >= window:
        moving_avg = pd.Series(rewards).rolling(window=window).mean()
        plt.plot(episodes, moving_avg, linewidth=2, color='red', 
                label=f'Moving Average ({window} episodes)')
    
    plt.xlabel('Episode')
    plt.ylabel('Total Reward')
    plt.title('Learning Curve')
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    if save_path:
        plt.savefig(save_path, dpi=300, bbox_inches='tight')
        print(f"Learning curve saved to {save_path}")
    else:
        plt.show()
    
    plt.close()
