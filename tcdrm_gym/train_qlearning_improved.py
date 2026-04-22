#!/usr/bin/env python3
"""
Script d'entraînement Q-Learning amélioré pour TCDRM-ADAPTIVE
Corrige les problèmes identifiés dans l'analyse
"""

import argparse
import os
import sys
import json
import time
import yaml
import numpy as np
from typing import Dict, Any

from envs.cloudsim_env import CloudSimQLearningEnv
from agents.simple_qlearning_agent import SimpleQLearningAgent


class ImprovedQLearningTrainer:
    """Trainer avec stratégies d'exploration avancées et monitoring amélioré"""
    
    def __init__(self, config_path: str):
        with open(config_path, 'r') as f:
            self.config = yaml.safe_load(f)
        
        self.qlearning_config = self.config['training']['qlearning']
        self.env_config = self.config['environment']
        self.validation_config = self.config['validation']
        
    def create_agent(self) -> SimpleQLearningAgent:
        """Crée l'agent Q-Learning avec configuration améliorée"""
        return SimpleQLearningAgent(
            n_states=243,
            n_actions=3,
            learning_rate=self.qlearning_config['learning_rate'],
            discount_factor=self.qlearning_config['discount_factor'],
            epsilon_start=self.qlearning_config['epsilon_start'],
            epsilon_min=self.qlearning_config['epsilon_min'],
            epsilon_decay=self.qlearning_config['epsilon_decay'],
            use_double_q=True,
            adaptive_lr=True,
            optimistic_init=self.qlearning_config.get('optimistic_init', 0.0)
        )
    
    def select_action_with_ucb(self, agent: SimpleQLearningAgent, state: int, 
                              valid_actions: list, training: bool = True) -> int:
        """Sélection d'action avec UCB pour exploration intelligente"""
        if not training or not self.config['training']['exploration_strategy'].get('use_ucb', False):
            return agent.select_action(state, valid_actions, training)
        
        c = self.config['training']['exploration_strategy']['ucb_c']
        total_visits = np.sum(agent.visit_counts[state])
        
        if total_visits == 0:
            return np.random.choice(valid_actions)
        
        ucb_values = []
        for action in valid_actions:
            q_value = agent.get_action_values(state)[action]
            visits = agent.visit_counts[state, action]
            
            if visits == 0:
                ucb_values.append(float('inf'))
            else:
                confidence = c * np.sqrt(np.log(total_visits) / visits)
                ucb_values.append(q_value + confidence)
        
        return valid_actions[np.argmax(ucb_values)]
    
    def evaluate_agent(self, agent: SimpleQLearningAgent, env: CloudSimQLearningEnv, 
                      num_episodes: int) -> Dict[str, float]:
        """Évalue l'agent sur plusieurs épisodes sans apprentissage"""
        total_rewards = []
        total_sla_violations = []
        total_costs = []
        
        for episode in range(num_episodes):
            state, info = env.reset(seed=1000 + episode)  # Seeds différents pour évaluation
            episode_reward = 0
            done = False
            episode_violations = 0
            episode_cost = 0
            
            while not done:
                valid_actions = None
                if isinstance(info, dict) and 'action_mask' in info:
                    valid_actions = [i for i, m in enumerate(info['action_mask']) if m]
                
                action = self.select_action_with_ucb(agent, state, valid_actions, training=False)
                next_state, reward, done, truncated, info = env.step(action)
                
                episode_reward += reward
                state = next_state
                
                # Collecter métriques
                if isinstance(info, dict):
                    if 'sla_violations' in info:
                        episode_violations = info['sla_violations']
                    if 'cumulative_cost' in info:
                        episode_cost = info['cumulative_cost']
            
            total_rewards.append(episode_reward)
            total_sla_violations.append(episode_violations)
            total_costs.append(episode_cost)
        
        return {
            'avg_reward': np.mean(total_rewards),
            'std_reward': np.std(total_rewards),
            'avg_sla_violations': np.mean(total_sla_violations),
            'avg_cost': np.mean(total_costs)
        }
    
    def train(self, env: CloudSimQLearningEnv, episodes: int, save_path: str):
        """Entraînement amélioré avec validation et early stopping"""
        print("=" * 70)
        print("Q-LEARNING AMÉLIORÉ - TCDRM-ADAPTIVE")
        print("=" * 70)
        print(f"Configuration: {self.qlearning_config}")
        print()
        
        agent = self.create_agent()
        best_reward = float('-inf')
        rewards_history = []
        no_improvement_count = 0
        
        # Créer répertoires
        timestamp = time.strftime('%Y%m%d-%H%M%S')
        log_dir = f"logs/qlearning_improved_{timestamp}"
        os.makedirs(log_dir, exist_ok=True)
        
        # Sauvegarder la configuration
        with open(os.path.join(log_dir, 'config.yml'), 'w') as f:
            yaml.dump(self.config, f, default_flow_style=False)
        
        for episode in range(episodes):
            state, info = env.reset(seed=42 + episode)
            episode_reward = 0
            done = False
            last_info = info
            
            while not done:
                valid_actions = None
                if isinstance(last_info, dict) and 'action_mask' in last_info:
                    valid_actions = [i for i, m in enumerate(last_info['action_mask']) if m]
                
                action = self.select_action_with_ucb(agent, state, valid_actions, training=True)
                next_state, reward, done, truncated, info = env.step(action)
                agent.update(state, action, reward, next_state, done)
                state = next_state
                episode_reward += reward
                last_info = info
            
            agent.decay_epsilon()
            rewards_history.append(episode_reward)
            avg_reward = np.mean(rewards_history[-10:])
            stats = agent.get_stats()
            
            # Validation périodique
            if episode % self.validation_config['eval_frequency'] == 0:
                eval_metrics = self.evaluate_agent(agent, env, self.validation_config['eval_episodes'])
                print(f"  📊 Evaluation: Reward={eval_metrics['avg_reward']:.2f}±{eval_metrics['std_reward']:.2f}, "
                      f"SLA={eval_metrics['avg_sla_violations']:.1f}, Cost={eval_metrics['avg_cost']:.2f}")
            
            # Monitoring détaillé
            if episode % 5 == 0 or episode == episodes - 1:
                exploration_pct = stats['states_explored'] / 243 * 100
                metrics_tail = ""
                if last_info and isinstance(last_info, dict):
                    metrics_tail = (
                        f" | SLA: {last_info.get('sla_violations','?')} | "
                        f"Cost: {last_info.get('cumulative_cost','?')} | "
                        f"Replicas: {last_info.get('replica_count','?')}"
                    )
                
                print(f"Episode {episode:4d}/{episodes} | "
                      f"Reward: {episode_reward:8.2f} | Avg(10): {avg_reward:8.2f} | "
                      f"ε: {stats['epsilon']:.3f} | States: {stats['states_explored']:3d} ({exploration_pct:4.1f}%){metrics_tail}")
            
            # Early stopping
            if avg_reward > best_reward and episode >= 10:
                best_reward = avg_reward
                agent.save(save_path)
                no_improvement_count = 0
                print(f"  💾 Nouveau meilleur modèle (avg reward: {best_reward:.2f})")
            else:
                no_improvement_count += 1
            
            if (no_improvement_count >= self.validation_config['early_stopping']['patience'] and 
                episode >= self.validation_config['early_stopping']['min_episodes']):
                print(f"\n🛑 Early stopping: pas d'amélioration depuis {no_improvement_count} épisodes")
                break
        
        # Sauvegarder modèle final
        final_path = save_path.replace('.pkl', '_improved_final.pkl')
        agent.save(final_path)
        
        # Évaluation finale
        final_metrics = self.evaluate_agent(agent, env, 10)
        
        print("\n" + "=" * 70)
        print("ENTRAÎNEMENT TERMINÉ")
        print(f"  Meilleur reward moyen: {best_reward:.2f}")
        print(f"  États explorés: {stats['states_explored']}/243 ({stats['states_explored']/243*100:.1f}%)")
        print(f"  Epsilon final: {agent.epsilon:.4f}")
        print(f"  Performance finale: {final_metrics['avg_reward']:.2f}±{final_metrics['std_reward']:.2f}")
        print(f"  Violations SLA: {final_metrics['avg_sla_violations']:.1f}")
        print(f"  Coût moyen: {final_metrics['avg_cost']:.2f}")
        print(f"  Modèle sauvegardé: {save_path}")
        print("=" * 70)
        
        return agent, rewards_history


def main():
    parser = argparse.ArgumentParser(description='Q-Learning amélioré pour TCDRM-ADAPTIVE')
    parser.add_argument('--config', type=str, 
                       default='config_qlearning_improved.yml',
                       help='Fichier de configuration')
    parser.add_argument('--episodes', type=int, default=100,
                       help='Nombre maximum d\'épisodes')
    parser.add_argument('--port', type=int, default=25335,
                       help='Port Java TrainingServer')
    parser.add_argument('--output', type=str, default='models/qlearning_improved.pkl',
                       help='Chemin de sauvegarde du modèle')
    
    args = parser.parse_args()
    
    if not os.path.exists(args.config):
        print(f"❌ Fichier de configuration non trouvé: {args.config}")
        sys.exit(1)
    
    trainer = ImprovedQLearningTrainer(args.config)
    
    try:
        env = CloudSimQLearningEnv(port=args.port, complex=False)
        agent, rewards = trainer.train(env, args.episodes, args.output)
        env.close()
    except ConnectionError as e:
        print(f"\n❌ Erreur de connexion: {e}")
        print("Assurez-vous que le TrainingServer Java est démarré:")
        print("  mvn exec:java -Dexec.mainClass=org.tcdrm.adaptive.training.TrainingServer")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n\n🛑 Entraînement interrompu par l'utilisateur")
        sys.exit(0)


if __name__ == '__main__':
    main()
