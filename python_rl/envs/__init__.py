from gymnasium.envs.registration import register

register(
    id='TcdrmQLearning-v2',
    entry_point='envs.tcdrm_qlearning_env:TcdrmQLearningEnv',
    max_episode_steps=1000,
)

register(
    id='TcdrmDQN-v2',
    entry_point='envs.tcdrm_env_v2:TcdrmV2Env',
    max_episode_steps=1000,
)
