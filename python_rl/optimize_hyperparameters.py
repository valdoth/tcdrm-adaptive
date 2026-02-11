"""
Script d'optimisation des hyperparamètres pour Q-Learning et DQN
Optimise les fonctions de récompense et les paramètres d'apprentissage
"""

import numpy as np
from typing import Dict, Tuple, List
import json
from pathlib import Path

class RewardOptimizer:
    """
    Optimise les poids de la fonction de récompense pour maximiser les performances
    """
    
    def __init__(self):
        # Poids initiaux pour DQN (tcdrm_env_v2.py)
        self.dqn_weights = {
            'R1_SLA_OK': 5.0,      # Récompense pour respect SLA
            'R2_SLA_VIOL': 10.0,   # Pénalité pour violation SLA
            'R3_COST_OVER': 5.0,   # Pénalité pour coût excessif
            'R4_REPL_COST': 2.0,   # Coût de réplication
            'R5_THRASH': 3.0       # Pénalité thrashing
        }
        
        # Poids initiaux pour Q-Learning (tcdrm_qlearning_env.py)
        self.qlearning_weights = {
            'latency_scale': 10.0,      # Échelle de récompense latence
            'repl_penalty': 0.5,        # Pénalité création réplica
            'repl_bonus_scale': 2.0,    # Bonus réplication utile
            'budget_penalty': 5.0,      # Pénalité budget critique
            'thrash_penalty': 1.0       # Pénalité thrashing
        }
        
        # Hyperparamètres RL
        self.rl_params = {
            'q_learning': {
                'learning_rate': 0.1,
                'discount_factor': 0.95,
                'epsilon_start': 1.0,
                'epsilon_end': 0.01,
                'epsilon_decay': 0.995
            },
            'dqn': {
                'learning_rate': 0.001,
                'discount_factor': 0.99,
                'batch_size': 64,
                'buffer_size': 10000,
                'target_update_freq': 100,
                'epsilon_start': 1.0,
                'epsilon_end': 0.01,
                'epsilon_decay': 0.995
            },
            'ppo': {
                'learning_rate': 0.0003,
                'n_steps': 2048,
                'batch_size': 64,
                'n_epochs': 10,
                'gamma': 0.99,
                'gae_lambda': 0.95,
                'clip_range': 0.2
            }
        }
    
    def optimize_dqn_reward(self, performance_metrics: Dict[str, float]) -> Dict[str, float]:
        """
        Optimise les poids de récompense DQN basés sur les métriques de performance
        
        Args:
            performance_metrics: {
                'sla_compliance': float,  # [0, 1]
                'avg_cost': float,
                'thrashing_rate': float
            }
        
        Returns:
            Nouveaux poids optimisés
        """
        new_weights = self.dqn_weights.copy()
        
        # Si taux de conformité SLA faible, augmenter R1 et R2
        if performance_metrics.get('sla_compliance', 1.0) < 0.85:
            new_weights['R1_SLA_OK'] *= 1.2
            new_weights['R2_SLA_VIOL'] *= 1.3
        
        # Si coût trop élevé, augmenter R3 et R4
        if performance_metrics.get('avg_cost', 0.0) > 0.8:
            new_weights['R3_COST_OVER'] *= 1.2
            new_weights['R4_REPL_COST'] *= 1.1
        
        # Si thrashing élevé, augmenter R5
        if performance_metrics.get('thrashing_rate', 0.0) > 0.2:
            new_weights['R5_THRASH'] *= 1.5
        
        return new_weights
    
    def optimize_qlearning_reward(self, performance_metrics: Dict[str, float]) -> Dict[str, float]:
        """
        Optimise les poids de récompense Q-Learning
        """
        new_weights = self.qlearning_weights.copy()
        
        # Ajustements basés sur les performances
        if performance_metrics.get('avg_latency', 0.0) > 200.0:
            new_weights['latency_scale'] *= 1.2
            new_weights['repl_bonus_scale'] *= 1.1
        
        if performance_metrics.get('budget_usage', 0.0) > 0.9:
            new_weights['budget_penalty'] *= 1.3
            new_weights['repl_penalty'] *= 1.2
        
        return new_weights
    
    def tune_learning_rate(self, algorithm: str, convergence_rate: float) -> float:
        """
        Ajuste le learning rate basé sur le taux de convergence
        
        Args:
            algorithm: 'q_learning', 'dqn', ou 'ppo'
            convergence_rate: Vitesse de convergence observée
        
        Returns:
            Nouveau learning rate
        """
        current_lr = self.rl_params[algorithm]['learning_rate']
        
        # Si convergence trop lente, augmenter LR
        if convergence_rate < 0.1:
            return min(current_lr * 1.5, 0.01)
        
        # Si convergence trop rapide (risque d'instabilité), réduire LR
        elif convergence_rate > 0.5:
            return max(current_lr * 0.7, 0.0001)
        
        return current_lr
    
    def save_optimized_params(self, filepath: str):
        """Sauvegarde les paramètres optimisés"""
        config = {
            'dqn_reward_weights': self.dqn_weights,
            'qlearning_reward_weights': self.qlearning_weights,
            'rl_hyperparameters': self.rl_params
        }
        
        with open(filepath, 'w') as f:
            json.dump(config, f, indent=2)
        
        print(f"✅ Paramètres optimisés sauvegardés dans {filepath}")
    
    def load_optimized_params(self, filepath: str):
        """Charge les paramètres optimisés"""
        with open(filepath, 'r') as f:
            config = json.load(f)
        
        self.dqn_weights = config['dqn_reward_weights']
        self.qlearning_weights = config['qlearning_reward_weights']
        self.rl_params = config['rl_hyperparameters']
        
        print(f"✅ Paramètres optimisés chargés depuis {filepath}")


class PLSAOptimizer:
    """
    Optimise les paramètres du modèle PLSA
    """
    
    def __init__(self):
        self.optimal_params = {
            'n_topics': 3,              # Nombre de topics latents
            'max_iterations': 20,       # Itérations EM
            'convergence_threshold': 1e-3,
            'refit_interval': 100,      # Réentraînement tous les N steps
            'window_size': 50,          # Taille fenêtre temporelle
            'plsa_weight': 0.7,         # Poids PLSA dans prédiction
            'recent_weight': 0.3        # Poids moyenne récente
        }
    
    def optimize_topics(self, data_variance: float) -> int:
        """
        Détermine le nombre optimal de topics basé sur la variance des données
        
        Args:
            data_variance: Variance des patterns d'accès
        
        Returns:
            Nombre optimal de topics
        """
        if data_variance < 0.1:
            return 2  # Patterns stables, peu de topics
        elif data_variance < 0.3:
            return 3  # Variance moyenne
        else:
            return 5  # Patterns très variables
    
    def optimize_refit_interval(self, pattern_stability: float) -> int:
        """
        Ajuste l'intervalle de réentraînement basé sur la stabilité des patterns
        
        Args:
            pattern_stability: Stabilité des patterns [0, 1]
        
        Returns:
            Intervalle optimal de réentraînement
        """
        if pattern_stability > 0.8:
            return 200  # Patterns stables, réentraîner moins souvent
        elif pattern_stability > 0.5:
            return 100  # Stabilité moyenne
        else:
            return 50   # Patterns instables, réentraîner plus souvent
    
    def get_optimal_config(self) -> Dict:
        """Retourne la configuration optimale"""
        return self.optimal_params


def generate_optimized_config():
    """
    Génère un fichier de configuration optimisé
    """
    optimizer = RewardOptimizer()
    plsa_optimizer = PLSAOptimizer()
    
    # Configuration complète
    config = {
        'version': '2.0',
        'description': 'Configuration optimisée pour TCDRM-Adaptive',
        
        # Récompenses DQN
        'dqn': {
            'reward_weights': optimizer.dqn_weights,
            'hyperparameters': optimizer.rl_params['dqn']
        },
        
        # Récompenses Q-Learning
        'q_learning': {
            'reward_weights': optimizer.qlearning_weights,
            'hyperparameters': optimizer.rl_params['q_learning']
        },
        
        # PPO
        'ppo': {
            'hyperparameters': optimizer.rl_params['ppo']
        },
        
        # PLSA
        'plsa': plsa_optimizer.get_optimal_config(),
        
        # Environnement
        'environment': {
            'MAX_QUERIES': 1000,
            'INITIAL_BUDGET': 1000.0,
            'MAX_REPLICAS_SIMPLE': 5,
            'MAX_REPLICAS_COMPLEX': 13,
            'COMPLEXITY_THRESHOLD': 10.0,
            'WARMUP_QUERIES': 600,
            'STORAGE_COST_PER_GB_PER_HOUR': 0.0001,
            'TSLA_INITIAL': 150.0,
            'TSLA_MIN': 100.0,
            'TSLA_MAX': 250.0
        }
    }
    
    # Sauvegarder
    output_path = Path(__file__).parent / 'config' / 'optimized_config.json'
    output_path.parent.mkdir(exist_ok=True)
    
    with open(output_path, 'w') as f:
        json.dump(config, f, indent=2)
    
    print(f"✅ Configuration optimisée générée: {output_path}")
    print("\n📊 Résumé de la configuration:")
    print(f"  - DQN reward weights: {config['dqn']['reward_weights']}")
    print(f"  - Q-Learning reward weights: {config['q_learning']['reward_weights']}")
    print(f"  - PLSA n_topics: {config['plsa']['n_topics']}")
    print(f"  - PLSA refit_interval: {config['plsa']['refit_interval']}")
    
    return config


if __name__ == '__main__':
    print("🚀 Génération de la configuration optimisée TCDRM-Adaptive\n")
    config = generate_optimized_config()
    print("\n✅ Configuration générée avec succès!")
    print("\n💡 Pour utiliser cette configuration:")
    print("   1. Charger le fichier config/optimized_config.json")
    print("   2. Appliquer les poids de récompense dans les environnements")
    print("   3. Utiliser les hyperparamètres RL pour l'entraînement")
