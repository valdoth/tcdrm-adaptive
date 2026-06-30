# Alias de compatibilité — le fichier canonique est rainbow_dqn_agent.py
from agents.rainbow_dqn_agent import (
    RainbowDQNAgent,
    RainbowDQNAgent as DQNAgent,  # compat
    NoisyLinear,
    NoisyDuelingNetwork,
    C51NoisyDuelingNetwork,
    PrioritizedReplayBuffer,
    NStepBuffer,
    RunningMeanStd,
)

__all__ = [
    'RainbowDQNAgent',
    'DQNAgent',
    'NoisyLinear',
    'NoisyDuelingNetwork',
    'C51NoisyDuelingNetwork',
    'PrioritizedReplayBuffer',
    'NStepBuffer',
    'RunningMeanStd',
]
