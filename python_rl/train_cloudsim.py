"""
Script d'entraînement RL utilisant CloudSimPlus pour les simulations.

L'entraînement se fait via Gymnasium côté Python, mais les simulations
sont exécutées dans CloudSimPlus Java via Py4J.

Usage:
    1. Démarrer le serveur Java:
       mvn exec:java -Dexec.mainClass=org.tcdrm.adaptive.training.TrainingServer
    
    2. Lancer l'entraînement:
       python train_cloudsim.py --agent qlearning --episodes 100
       python train_cloudsim.py --agent dqn --episodes 100
"""

import argparse
import os
import sys
import numpy as np
import torch

from envs.cloudsim_env import CloudSimEnv, CloudSimQLearningEnv
from agents.simple_qlearning_agent import SimpleQLearningAgent
from agents.dqn_agent import DQNAgent


def train_qlearning(env: CloudSimQLearningEnv, episodes: int, save_path: str):
    """
    Entraîne un agent Q-Learning dans CloudSimPlus.
    """
    print("=" * 70)
    print("Q-LEARNING TRAINING with CloudSimPlus")
    print("=" * 70)
    
    agent = SimpleQLearningAgent(
        n_states=243,
        n_actions=3,
        learning_rate=0.1,
        discount_factor=0.99,
        epsilon_start=1.0,
        epsilon_min=0.05,
        epsilon_decay=0.995,
        use_double_q=True,
        adaptive_lr=True
    )
    
    best_reward = float('-inf')
    rewards_history = []
    
    for episode in range(episodes):
        state, info = env.reset(seed=42 + episode)
        episode_reward = 0
        done = False
        
        while not done:
            # Sélection d'action epsilon-greedy
            action = agent.select_action(state, training=True)
            
            # Exécuter dans CloudSimPlus
            next_state, reward, done, truncated, info = env.step(action)
            
            # Mise à jour Q-table
            agent.update(state, action, reward, next_state, done)
            
            state = next_state
            episode_reward += reward
        
        # Décroissance epsilon
        agent.decay_epsilon()
        rewards_history.append(episode_reward)
        
        # Statistiques
        avg_reward = np.mean(rewards_history[-10:])
        stats = agent.get_stats()
        
        if episode % 10 == 0 or episode == episodes - 1:
            print(f"Episode {episode:4d}/{episodes} | "
                  f"Reward: {episode_reward:8.2f} | "
                  f"Avg(10): {avg_reward:8.2f} | "
                  f"ε: {stats['epsilon']:.3f} | "
                  f"States: {stats['states_explored']}")
        
        # Sauvegarder le meilleur modèle
        if avg_reward > best_reward and episode >= 10:
            best_reward = avg_reward
            agent.save(save_path)
            print(f"  💾 New best model saved (avg reward: {best_reward:.2f})")
    
    # Sauvegarder le modèle final
    final_path = save_path.replace('.pkl', '_final.pkl')
    agent.save(final_path)
    
    print()
    print("=" * 70)
    print("TRAINING COMPLETE")
    print(f"  Best avg reward: {best_reward:.2f}")
    print(f"  Final epsilon: {agent.epsilon:.4f}")
    print(f"  States explored: {stats['states_explored']}/243")
    print(f"  Model saved to: {save_path}")
    print("=" * 70)
    
    return agent, rewards_history


def train_dqn(env: CloudSimEnv, episodes: int, save_path: str):
    """
    Entraîne un agent DQN dans CloudSimPlus.
    """
    print("=" * 70)
    print("DQN TRAINING with CloudSimPlus")
    print("=" * 70)
    
    agent = DQNAgent(
        state_dim=8,
        action_dim=3,
        learning_rate=0.001,
        discount_factor=0.99,
        epsilon=1.0,
        epsilon_min=0.05,
        epsilon_decay_lambda=0.001,
        buffer_capacity=50000,
        batch_size=64,
        use_double_dqn=True,
        use_dueling=True
    )
    
    best_reward = float('-inf')
    rewards_history = []
    
    for episode in range(episodes):
        state, info = env.reset(seed=42 + episode)
        episode_reward = 0
        done = False
        
        while not done:
            # Sélection d'action epsilon-greedy
            action = agent.select_action(state, training=True)
            
            # Exécuter dans CloudSimPlus
            next_state, reward, done, truncated, info = env.step(action)
            
            # Mise à jour DQN (stocke + entraîne)
            agent.update(state, action, reward, next_state, done)
            
            state = next_state
            episode_reward += reward
        
        # Décroissance epsilon
        agent.decay_epsilon()
        rewards_history.append(episode_reward)
        
        # Statistiques
        avg_reward = np.mean(rewards_history[-10:])
        
        if episode % 10 == 0 or episode == episodes - 1:
            print(f"Episode {episode:4d}/{episodes} | "
                  f"Reward: {episode_reward:8.2f} | "
                  f"Avg(10): {avg_reward:8.2f} | "
                  f"ε: {agent.epsilon:.3f} | "
                  f"Buffer: {len(agent.replay_buffer)}")
        
        # Sauvegarder le meilleur modèle
        if avg_reward > best_reward and episode >= 10:
            best_reward = avg_reward
            agent.save(save_path)
            print(f"  💾 New best model saved (avg reward: {best_reward:.2f})")
    
    # Sauvegarder le modèle final
    final_path = save_path.replace('.pt', '_final.pt')
    agent.save(final_path)
    
    print()
    print("=" * 70)
    print("TRAINING COMPLETE")
    print(f"  Best avg reward: {best_reward:.2f}")
    print(f"  Final epsilon: {agent.epsilon:.4f}")
    print(f"  Model saved to: {save_path}")
    print("=" * 70)
    
    return agent, rewards_history


def main():
    parser = argparse.ArgumentParser(description='Train RL agents with CloudSimPlus')
    parser.add_argument('--agent', type=str, choices=['qlearning', 'dqn'], 
                       default='qlearning', help='Agent type')
    parser.add_argument('--episodes', type=int, default=100, 
                       help='Number of training episodes')
    parser.add_argument('--complex', action='store_true', 
                       help='Use complex queries')
    parser.add_argument('--port', type=int, default=25335, 
                       help='Java TrainingServer port')
    parser.add_argument('--output', type=str, default=None,
                       help='Output model path')
    
    args = parser.parse_args()
    
    # Créer le dossier de sortie
    os.makedirs('models', exist_ok=True)
    
    # Chemins de sauvegarde par défaut
    if args.output is None:
        if args.agent == 'qlearning':
            args.output = 'models/qlearning_cloudsim.pkl'
        else:
            args.output = 'models/dqn_cloudsim.pt'
    
    print()
    print("🎓 TCDRM RL Training with CloudSimPlus")
    print(f"   Agent: {args.agent.upper()}")
    print(f"   Episodes: {args.episodes}")
    print(f"   Query type: {'complex' if args.complex else 'simple'}")
    print(f"   Server port: {args.port}")
    print(f"   Output: {args.output}")
    print()
    
    try:
        if args.agent == 'qlearning':
            env = CloudSimQLearningEnv(port=args.port, complex=args.complex)
            train_qlearning(env, args.episodes, args.output)
        else:
            env = CloudSimEnv(port=args.port, complex=args.complex)
            train_dqn(env, args.episodes, args.output)
        
        env.close()
        
    except ConnectionError as e:
        print(f"\n❌ {e}")
        print("\nMake sure to start the Java TrainingServer first:")
        print("  mvn exec:java -Dexec.mainClass=org.tcdrm.adaptive.training.TrainingServer")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\n🛑 Training interrupted by user")
        sys.exit(0)


if __name__ == '__main__':
    main()
