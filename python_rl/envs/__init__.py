from gymnasium.envs.registration import register

register(
    id='TcdrmAdaptive-v0',
    entry_point='envs.tcdrm_env:TcdrmAdaptiveEnv',
    max_episode_steps=1000,
)

register(
    id='TcdrmAdaptiveMultiObj-v0',
    entry_point='envs.tcdrm_env_multi:TcdrmAdaptiveMultiObjEnv',
    max_episode_steps=1000,
)
