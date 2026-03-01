"""
TensorBoard Callback pour le monitoring des entraînements RL
Inspiré de rl-cloudsimplus et compatible avec Q-Learning et DQN
"""

import os
from typing import Dict, Any, Optional
from torch.utils.tensorboard import SummaryWriter
import numpy as np


class TensorBoardCallback:
    """
    Callback TensorBoard pour logger les métriques d'entraînement.
    Compatible avec Q-Learning et DQN.
    """
    
    def __init__(self, log_dir: str = "runs", experiment_name: Optional[str] = None):
        """
        Initialise le callback TensorBoard.
        
        Args:
            log_dir: Répertoire de base pour les logs TensorBoard
            experiment_name: Nom de l'expérience (auto-généré si None)
        """
        if experiment_name is None:
            from datetime import datetime
            experiment_name = f"tcdrm_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
        
        self.log_path = os.path.join(log_dir, experiment_name)
        self.writer = SummaryWriter(log_dir=self.log_path)
        self.episode = 0
        self.step = 0
        
        print(f"📊 TensorBoard logging to: {self.log_path}")
        print(f"   Run: tensorboard --logdir={log_dir}")
    
    def on_episode_start(self, episode: int):
        """Appelé au début de chaque épisode."""
        self.episode = episode
    
    def on_step(self, step: int, metrics: Dict[str, Any]):
        """
        Appelé à chaque step de l'entraînement.
        
        Args:
            step: Numéro du step global
            metrics: Dictionnaire de métriques à logger
                - reward: Récompense du step
                - latency: Latence de la requête
                - cost: Coût du step
                - budget: Budget restant
                - replicas: Nombre de réplicas
                - action: Action prise
                - sla_violations: Nombre de violations SLA
                - etc.
        """
        self.step = step
        
        # Logger les métriques principales
        if 'reward' in metrics:
            self.writer.add_scalar('Step/Reward', metrics['reward'], step)
        
        if 'latency' in metrics:
            self.writer.add_scalar('Step/Latency', metrics['latency'], step)
        
        if 'cost' in metrics:
            self.writer.add_scalar('Step/Cost', metrics['cost'], step)
        
        if 'budget' in metrics:
            self.writer.add_scalar('Step/Budget', metrics['budget'], step)
        
        if 'replicas' in metrics:
            self.writer.add_scalar('Step/Replicas', metrics['replicas'], step)
        
        if 'action' in metrics:
            self.writer.add_scalar('Step/Action', metrics['action'], step)
        
        if 'sla_violations' in metrics:
            self.writer.add_scalar('Step/SLA_Violations', metrics['sla_violations'], step)
    
    def on_episode_end(self, episode: int, metrics: Dict[str, Any]):
        """
        Appelé à la fin de chaque épisode.
        
        Args:
            episode: Numéro de l'épisode
            metrics: Métriques de l'épisode
                - total_reward: Récompense totale de l'épisode
                - avg_latency: Latence moyenne
                - total_cost: Coût total
                - final_budget: Budget final
                - avg_replicas: Nombre moyen de réplicas
                - sla_violations: Total de violations SLA
                - epsilon: Valeur d'epsilon (pour Q-Learning)
                - loss: Perte moyenne (pour DQN)
                - etc.
        """
        # Métriques d'épisode
        if 'total_reward' in metrics:
            self.writer.add_scalar('Episode/Total_Reward', metrics['total_reward'], episode)
        
        if 'avg_latency' in metrics:
            self.writer.add_scalar('Episode/Avg_Latency', metrics['avg_latency'], episode)
        
        if 'total_cost' in metrics:
            self.writer.add_scalar('Episode/Total_Cost', metrics['total_cost'], episode)
        
        if 'final_budget' in metrics:
            self.writer.add_scalar('Episode/Final_Budget', metrics['final_budget'], episode)
        
        if 'avg_replicas' in metrics:
            self.writer.add_scalar('Episode/Avg_Replicas', metrics['avg_replicas'], episode)
        
        if 'sla_violations' in metrics:
            self.writer.add_scalar('Episode/SLA_Violations', metrics['sla_violations'], episode)
        
        # Métriques spécifiques Q-Learning
        if 'epsilon' in metrics:
            self.writer.add_scalar('Training/Epsilon', metrics['epsilon'], episode)
        
        if 'states_explored' in metrics:
            self.writer.add_scalar('Training/States_Explored', metrics['states_explored'], episode)
        
        # Métriques spécifiques DQN
        if 'loss' in metrics:
            self.writer.add_scalar('Training/Loss', metrics['loss'], episode)
        
        if 'q_value_mean' in metrics:
            self.writer.add_scalar('Training/Q_Value_Mean', metrics['q_value_mean'], episode)
        
        # Flush pour s'assurer que les données sont écrites
        self.writer.flush()
    
    def log_histogram(self, tag: str, values: np.ndarray, step: int):
        """
        Logger un histogramme (utile pour les Q-values, poids du réseau, etc.)
        
        Args:
            tag: Nom du tag
            values: Valeurs à logger
            step: Step actuel
        """
        self.writer.add_histogram(tag, values, step)
    
    def log_scalar(self, tag: str, value: float, step: int):
        """
        Logger une métrique scalaire personnalisée.
        
        Args:
            tag: Nom du tag
            value: Valeur à logger
            step: Step actuel
        """
        self.writer.add_scalar(tag, value, step)
    
    def log_text(self, tag: str, text: str, step: int):
        """
        Logger du texte (utile pour les hyperparamètres, notes, etc.)
        
        Args:
            tag: Nom du tag
            text: Texte à logger
            step: Step actuel
        """
        self.writer.add_text(tag, text, step)
    
    def log_hyperparameters(self, hparams: Dict[str, Any], metrics: Dict[str, float]):
        """
        Logger les hyperparamètres et métriques finales.
        
        Args:
            hparams: Dictionnaire des hyperparamètres
            metrics: Dictionnaire des métriques finales
        """
        self.writer.add_hparams(hparams, metrics)
    
    def close(self):
        """Ferme le writer TensorBoard."""
        self.writer.close()
        print(f"✅ TensorBoard logs sauvegardés dans: {self.log_path}")
    
    def __enter__(self):
        """Support pour context manager."""
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Support pour context manager."""
        self.close()
