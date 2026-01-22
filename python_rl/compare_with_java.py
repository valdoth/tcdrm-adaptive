#!/usr/bin/env python3
"""
Compare Python Q-Learning with Java Q-Learning implementation
Loads results from Java execution and compares with Python implementation
"""

import os
import json
import argparse
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
from typing import Dict, List, Any

from utils.logger import setup_logger
from utils.metrics import MetricsTracker
from agents.tabular_qlearning import TabularQLearningAgent
from envs.tcdrm_env import TcdrmEnv

logger = setup_logger("JavaComparison")


def parse_java_logs(log_file: str) -> Dict[str, Any]:
    """
    Parse Java training logs to extract metrics
    Expected format from TcdrmAdaptiveTraining.java output
    """
    metrics = {
        'episodes': [],
        'rewards': [],
        'latencies': [],
        'costs': [],
        'actions': {'create_replica': 0, 'delete_replica': 0, 'do_nothing': 0}
    }
    
    if not os.path.exists(log_file):
        logger.warning(f"Java log file not found: {log_file}")
        return metrics
    
    with open(log_file, 'r') as f:
        lines = f.readlines()
    
    current_episode = None
    for line in lines:
        line = line.strip()
        
        # Parse episode number
        if "Episode" in line and "/" in line:
            try:
                episode_num = int(line.split("Episode")[1].split("/")[0].strip())
                current_episode = episode_num
                metrics['episodes'].append(episode_num)
            except:
                pass
        
        # Parse reward
        if "Reward:" in line or "reward:" in line.lower():
            try:
                reward = float(line.split(":")[-1].strip().replace(",", ""))
                metrics['rewards'].append(reward)
            except:
                pass
        
        # Parse latency
        if "Latency:" in line or "latency:" in line.lower():
            try:
                latency_str = line.split(":")[-1].strip()
                latency = float(latency_str.replace("ms", "").strip())
                metrics['latencies'].append(latency)
            except:
                pass
        
        # Parse cost
        if "Cost:" in line or "cost:" in line.lower():
            try:
                cost_str = line.split(":")[-1].strip()
                cost = float(cost_str.replace("$", "").replace(",", "").strip())
                metrics['costs'].append(cost)
            except:
                pass
        
        # Parse actions
        if "CREATE_REPLICA" in line:
            metrics['actions']['create_replica'] += 1
        elif "DELETE_REPLICA" in line:
            metrics['actions']['delete_replica'] += 1
        elif "DO_NOTHING" in line:
            metrics['actions']['do_nothing'] += 1
    
    return metrics


def load_java_results(results_dir: str) -> Dict[str, Dict[str, Any]]:
    """Load Java Q-Learning results from multiple queries"""
    java_results = {}
    
    # Look for R1 and R2 results
    for query in ['R1', 'R2']:
        log_file = os.path.join(results_dir, f"java_qlearning_{query}.log")
        if os.path.exists(log_file):
            logger.info(f"Loading Java results for {query} from {log_file}")
            java_results[query] = parse_java_logs(log_file)
        else:
            logger.warning(f"Java log file not found for {query}: {log_file}")
    
    return java_results


def train_python_qlearning(data_gb: float, episodes: int, seed: int = 42) -> Dict[str, Any]:
    """Train Python Q-Learning agent and collect metrics"""
    logger.info(f"Training Python Q-Learning for {data_gb}GB data...")
    
    env = TcdrmEnv(data_gb=data_gb)
    agent = TabularQLearningAgent(
        env=env,
        learning_rate=0.1,
        discount_factor=0.95,
        epsilon=1.0,
        epsilon_decay=0.995,
        epsilon_min=0.01
    )
    
    metrics_tracker = MetricsTracker()
    
    for episode in range(episodes):
        obs, info = env.reset(seed=seed + episode)
        done = False
        episode_reward = 0
        episode_latency = []
        episode_cost = 0
        
        while not done:
            action = agent.choose_action(obs)
            next_obs, reward, terminated, truncated, info = env.step(action)
            done = terminated or truncated
            
            agent.update(obs, action, reward, next_obs, done)
            
            episode_reward += reward
            episode_latency.append(info.get('latency', 0))
            episode_cost += info.get('cost', 0)
            
            obs = next_obs
        
        metrics_tracker.update(
            reward=episode_reward,
            latency=np.mean(episode_latency),
            cost=episode_cost,
            sla_compliance=info.get('sla_compliance', 0),
            action=action
        )
        
        if (episode + 1) % 50 == 0:
            logger.info(f"Episode {episode + 1}/{episodes} - Avg Reward: {np.mean(metrics_tracker.rewards[-50:]):.2f}")
    
    env.close()
    
    return {
        'episodes': list(range(episodes)),
        'rewards': metrics_tracker.rewards,
        'latencies': metrics_tracker.latencies,
        'costs': metrics_tracker.costs,
        'actions': metrics_tracker.action_counts,
        'summary': metrics_tracker.get_summary()
    }


def compare_implementations(java_results: Dict, python_results: Dict, query: str, output_dir: str):
    """Compare Java and Python implementations"""
    logger.info(f"\n{'='*80}")
    logger.info(f"COMPARISON FOR {query}")
    logger.info(f"{'='*80}")
    
    # Calculate statistics
    java_avg_reward = np.mean(java_results['rewards']) if java_results['rewards'] else 0
    python_avg_reward = np.mean(python_results['rewards'])
    
    java_avg_latency = np.mean(java_results['latencies']) if java_results['latencies'] else 0
    python_avg_latency = np.mean(python_results['latencies'])
    
    java_avg_cost = np.mean(java_results['costs']) if java_results['costs'] else 0
    python_avg_cost = np.mean(python_results['costs'])
    
    # Print comparison
    print(f"\n{'Metric':<20} {'Java':<20} {'Python':<20} {'Difference':<20}")
    print("-" * 80)
    print(f"{'Avg Reward':<20} {java_avg_reward:<20.2f} {python_avg_reward:<20.2f} {(python_avg_reward - java_avg_reward):<20.2f}")
    print(f"{'Avg Latency (ms)':<20} {java_avg_latency:<20.2f} {python_avg_latency:<20.2f} {(python_avg_latency - java_avg_latency):<20.2f}")
    print(f"{'Avg Cost ($)':<20} {java_avg_cost:<20.2f} {python_avg_cost:<20.2f} {(python_avg_cost - java_avg_cost):<20.2f}")
    
    # Action distribution
    print(f"\n{'Action Distribution':<20} {'Java':<20} {'Python':<20}")
    print("-" * 60)
    for action in ['create_replica', 'delete_replica', 'do_nothing']:
        java_count = java_results['actions'].get(action, 0)
        python_count = python_results['actions'].get(action, 0)
        print(f"{action:<20} {java_count:<20} {python_count:<20}")
    
    # Create comparison plots
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    fig.suptitle(f'Java vs Python Q-Learning Comparison - {query}', fontsize=16)
    
    # Rewards over episodes
    if java_results['rewards']:
        axes[0, 0].plot(java_results['episodes'][:len(java_results['rewards'])], 
                       java_results['rewards'], label='Java', alpha=0.7)
    axes[0, 0].plot(python_results['episodes'], python_results['rewards'], 
                   label='Python', alpha=0.7)
    axes[0, 0].set_xlabel('Episode')
    axes[0, 0].set_ylabel('Reward')
    axes[0, 0].set_title('Rewards Over Episodes')
    axes[0, 0].legend()
    axes[0, 0].grid(True, alpha=0.3)
    
    # Latency comparison
    if java_results['latencies']:
        axes[0, 1].plot(java_results['episodes'][:len(java_results['latencies'])], 
                       java_results['latencies'], label='Java', alpha=0.7)
    axes[0, 1].plot(python_results['episodes'], python_results['latencies'], 
                   label='Python', alpha=0.7)
    axes[0, 1].set_xlabel('Episode')
    axes[0, 1].set_ylabel('Latency (ms)')
    axes[0, 1].set_title('Latency Over Episodes')
    axes[0, 1].legend()
    axes[0, 1].grid(True, alpha=0.3)
    
    # Cost comparison
    if java_results['costs']:
        axes[1, 0].plot(java_results['episodes'][:len(java_results['costs'])], 
                       java_results['costs'], label='Java', alpha=0.7)
    axes[1, 0].plot(python_results['episodes'], python_results['costs'], 
                   label='Python', alpha=0.7)
    axes[1, 0].set_xlabel('Episode')
    axes[1, 0].set_ylabel('Cost ($)')
    axes[1, 0].set_title('Cost Over Episodes')
    axes[1, 0].legend()
    axes[1, 0].grid(True, alpha=0.3)
    
    # Action distribution bar chart
    actions = ['create_replica', 'delete_replica', 'do_nothing']
    java_counts = [java_results['actions'].get(a, 0) for a in actions]
    python_counts = [python_results['actions'].get(a, 0) for a in actions]
    
    x = np.arange(len(actions))
    width = 0.35
    
    axes[1, 1].bar(x - width/2, java_counts, width, label='Java', alpha=0.7)
    axes[1, 1].bar(x + width/2, python_counts, width, label='Python', alpha=0.7)
    axes[1, 1].set_xlabel('Action')
    axes[1, 1].set_ylabel('Count')
    axes[1, 1].set_title('Action Distribution')
    axes[1, 1].set_xticks(x)
    axes[1, 1].set_xticklabels(actions, rotation=45, ha='right')
    axes[1, 1].legend()
    axes[1, 1].grid(True, alpha=0.3, axis='y')
    
    plt.tight_layout()
    
    # Save plot
    plot_path = os.path.join(output_dir, f'java_vs_python_{query}.png')
    plt.savefig(plot_path, dpi=300, bbox_inches='tight')
    logger.info(f"Comparison plot saved to {plot_path}")
    plt.close()
    
    # Save comparison results to JSON
    comparison_data = {
        'query': query,
        'java': {
            'avg_reward': float(java_avg_reward),
            'avg_latency': float(java_avg_latency),
            'avg_cost': float(java_avg_cost),
            'actions': java_results['actions']
        },
        'python': {
            'avg_reward': float(python_avg_reward),
            'avg_latency': float(python_avg_latency),
            'avg_cost': float(python_avg_cost),
            'actions': python_results['actions']
        },
        'differences': {
            'reward': float(python_avg_reward - java_avg_reward),
            'latency': float(python_avg_latency - java_avg_latency),
            'cost': float(python_avg_cost - java_avg_cost)
        }
    }
    
    json_path = os.path.join(output_dir, f'java_vs_python_{query}.json')
    with open(json_path, 'w') as f:
        json.dump(comparison_data, f, indent=2)
    logger.info(f"Comparison data saved to {json_path}")


def main():
    parser = argparse.ArgumentParser(description='Compare Python Q-Learning with Java implementation')
    parser.add_argument('--java-results-dir', type=str, default='../java_results',
                       help='Directory containing Java Q-Learning results')
    parser.add_argument('--episodes', type=int, default=500,
                       help='Number of episodes for Python training')
    parser.add_argument('--output-dir', type=str, default='java_comparison_results',
                       help='Output directory for comparison results')
    parser.add_argument('--seed', type=int, default=42,
                       help='Random seed')
    
    args = parser.parse_args()
    
    # Create output directory
    os.makedirs(args.output_dir, exist_ok=True)
    
    logger.info("="*80)
    logger.info("JAVA vs PYTHON Q-LEARNING COMPARISON")
    logger.info("="*80)
    
    # Load Java results
    logger.info(f"\nLoading Java results from {args.java_results_dir}...")
    java_results = load_java_results(args.java_results_dir)
    
    # Train Python Q-Learning for R1 (5.3 GB)
    logger.info("\n" + "="*80)
    logger.info("Training Python Q-Learning for R1 (5.3 GB)")
    logger.info("="*80)
    python_r1 = train_python_qlearning(data_gb=5.3, episodes=args.episodes, seed=args.seed)
    
    # Train Python Q-Learning for R2 (11.9 GB)
    logger.info("\n" + "="*80)
    logger.info("Training Python Q-Learning for R2 (11.9 GB)")
    logger.info("="*80)
    python_r2 = train_python_qlearning(data_gb=11.9, episodes=args.episodes, seed=args.seed)
    
    # Compare R1
    if 'R1' in java_results:
        compare_implementations(java_results['R1'], python_r1, 'R1', args.output_dir)
    else:
        logger.warning("No Java results found for R1, skipping comparison")
        logger.info(f"\nPython R1 Results:")
        logger.info(f"  Avg Reward: {np.mean(python_r1['rewards']):.2f}")
        logger.info(f"  Avg Latency: {np.mean(python_r1['latencies']):.2f} ms")
        logger.info(f"  Avg Cost: ${np.mean(python_r1['costs']):.2f}")
    
    # Compare R2
    if 'R2' in java_results:
        compare_implementations(java_results['R2'], python_r2, 'R2', args.output_dir)
    else:
        logger.warning("No Java results found for R2, skipping comparison")
        logger.info(f"\nPython R2 Results:")
        logger.info(f"  Avg Reward: {np.mean(python_r2['rewards']):.2f}")
        logger.info(f"  Avg Latency: {np.mean(python_r2['latencies']):.2f} ms")
        logger.info(f"  Avg Cost: ${np.mean(python_r2['costs']):.2f}")
    
    logger.info("\n" + "="*80)
    logger.info("COMPARISON COMPLETE")
    logger.info("="*80)
    logger.info(f"\nResults saved to: {args.output_dir}")
    logger.info("\nTo generate Java logs, run your Java training with output redirection:")
    logger.info("  mvn exec:java -Dexec.mainClass=\"org.tcdrm.adaptive.examples.TcdrmAdaptiveTraining\" > java_results/java_qlearning_R1.log")


if __name__ == "__main__":
    main()
