import os
import random
import pickle
from typing import Optional

import numpy as np

try:
    import torch
    TORCH_AVAILABLE = True
except Exception:  # pragma: no cover
    TORCH_AVAILABLE = False
    torch = None  # type: ignore


class PythonRLBridge(object):
    """Minimal RL bridge for validation.

    - If a Q-Learning pickle is provided, it is expected to contain a policy
      with a ``predict(state) -> action`` method or a mapping-like object. If it
      can't be used, we fallback to random actions.
    - If a DQN PyTorch model is provided, we run a forward pass and pick argmax.
    """

    def __init__(self, qlearning_model_path: str = "models/qlearning_cloudsim.pkl",
                 dqn_model_path: str = "models/dqn_cloudsim.pt") -> None:
        self._q_path = qlearning_model_path
        self._dqn_path = dqn_model_path
        self._q_model = self._load_q_model(qlearning_model_path)
        self._dqn_model = self._load_dqn_model(dqn_model_path)
        self._last_action = 0

    # === Utility loaders ===
    def _load_q_model(self, path: str):
        if not path or not os.path.exists(path):
            return None
        try:
            with open(path, "rb") as f:
                obj = pickle.load(f)
            # Check if it's a SimpleQLearningAgent saved model
            if isinstance(obj, dict) and 'q_table' in obj and isinstance(obj['q_table'], np.ndarray):
                # Return the full dict so we can use q_table, q_table_a, q_table_b
                return obj
            # Accept direct mapping or object with known keys
            if isinstance(obj, dict):
                # Common keys: 'q_table', 'table'
                if 'q_table' in obj and isinstance(obj['q_table'], (dict,)):
                    return {'_map': obj['q_table']}
                if 'table' in obj and isinstance(obj['table'], (dict,)):
                    return {'_map': obj['table']}
                # Sometimes saved directly as mapping
                return {'_map': obj}
            return obj
        except Exception:
            return None

    def _load_dqn_model(self, path: str):
        if not TORCH_AVAILABLE or not path or not os.path.exists(path):
            print(f"[DEBUG] DQN load skipped: torch={TORCH_AVAILABLE}, path={path}, exists={os.path.exists(path) if path else 'N/A'}")
            return None
        try:
            checkpoint = torch.load(path, map_location="cpu", weights_only=False)
            print(f"[DEBUG] DQN checkpoint loaded, keys: {list(checkpoint.keys())}")
            
            # Check if it's a checkpoint with state_dict (from DQNAgent.save())
            if isinstance(checkpoint, dict) and 'policy_net_state_dict' in checkpoint:
                # Reconstruct the appropriate network architecture
                use_dueling = checkpoint.get('use_dueling', True)
                
                # Infer dimensions from state dict
                state_dict = checkpoint['policy_net_state_dict']
                # First layer weight shape: [hidden_dim, state_dim]
                first_weight = state_dict.get('feature_layer.0.weight')
                if first_weight is None:
                    first_weight = state_dict.get('network.0.weight')
                if first_weight is not None:
                    state_dim = first_weight.shape[1]
                    adv_weight = state_dict.get('advantage_stream.2.weight')
                    if adv_weight is not None:
                        action_dim = adv_weight.shape[0]
                    else:
                        action_dim = 3  # Default
                else:
                    state_dim = 9  # Default for TCDRM
                    action_dim = 3
                
                print(f"[DEBUG] Inferred state_dim={state_dim}, action_dim={action_dim}, use_dueling={use_dueling}")
                
                # Reconstruct network
                from torch import nn
                if use_dueling:
                    class DuelingDQNNetwork(nn.Module):
                        def __init__(self, state_dim, action_dim, hidden_dims=[64, 64]):
                            super().__init__()
                            self.feature_layer = nn.Sequential(
                                nn.Linear(state_dim, hidden_dims[0]),
                                nn.ReLU(),
                                nn.Linear(hidden_dims[0], hidden_dims[1]),
                                nn.ReLU()
                            )
                            self.value_stream = nn.Sequential(
                                nn.Linear(hidden_dims[1], 32),
                                nn.ReLU(),
                                nn.Linear(32, 1)
                            )
                            self.advantage_stream = nn.Sequential(
                                nn.Linear(hidden_dims[1], 32),
                                nn.ReLU(),
                                nn.Linear(32, action_dim)
                            )
                        
                        def forward(self, x):
                            features = self.feature_layer(x)
                            value = self.value_stream(features)
                            advantage = self.advantage_stream(features)
                            q_values = value + (advantage - advantage.mean(dim=1, keepdim=True))
                            return q_values
                    
                    model = DuelingDQNNetwork(state_dim, action_dim)
                else:
                    class DQNNetwork(nn.Module):
                        def __init__(self, state_dim, action_dim, hidden_dims=[64, 64, 32]):
                            super().__init__()
                            layers = []
                            input_dim = state_dim
                            for hidden_dim in hidden_dims:
                                layers.append(nn.Linear(input_dim, hidden_dim))
                                layers.append(nn.ReLU())
                                input_dim = hidden_dim
                            layers.append(nn.Linear(input_dim, action_dim))
                            self.network = nn.Sequential(*layers)
                        
                        def forward(self, x):
                            return self.network(x)
                    
                    model = DQNNetwork(state_dim, action_dim)
                
                # Load weights
                print(f"[DEBUG] Loading state dict into model...")
                model.load_state_dict(checkpoint['policy_net_state_dict'])
                model.eval()
                print(f"[DEBUG] DQN model loaded successfully!")
                return model
            
            # Fallback: try loading as TorchScript
            try:
                model = torch.jit.load(path, map_location="cpu")
                model.eval()
                return model
            except Exception:
                pass
            
            # If a state_dict is returned without architecture, cannot use
            if isinstance(checkpoint, dict) and 'state_dict' in checkpoint and not hasattr(checkpoint, 'eval'):
                return None
            if hasattr(checkpoint, 'eval'):
                checkpoint.eval()
                return checkpoint
            return None
        except Exception as e:
            print(f"[DEBUG] Exception during DQN loading: {e}")
            import traceback
            traceback.print_exc()
            return None

    # === Methods used by Java ===
    def isQLearningReady(self) -> bool:
        return self._q_model is not None

    def isDQNReady(self) -> bool:
        return self._dqn_model is not None

    def selectActionQLearning(self, state: list) -> int:
        if self._q_model is None:
            raise RuntimeError("QLearning model not loaded (strict mode)")
        s = np.array(state, dtype=np.float32)
        
        # Handle SimpleQLearningAgent format (q_table numpy array)
        if isinstance(self._q_model, dict) and 'q_table' in self._q_model:
            q_table = self._q_model['q_table']
            if isinstance(q_table, np.ndarray):
                # Convert continuous state to discrete state index
                # State format from Java: [budget_ratio, query_freq, last_rt, replica_count, 
                #                         query_interval, consecutive_count, has_replica, cpu_util, mem_util]
                # Need to discretize to match training
                state_idx = self._discretize_state(s)
                
                # Use double Q-learning if available
                if self._q_model.get('use_double_q', False) and 'q_table_a' in self._q_model:
                    q_a = self._q_model['q_table_a']
                    q_b = self._q_model['q_table_b']
                    q_values = (q_a[state_idx] + q_b[state_idx]) / 2.0
                else:
                    q_values = q_table[state_idx]
                
                return int(np.argmax(q_values))
        
        # Try predict(state) if available
        if hasattr(self._q_model, "predict"):
            a = self._q_model.predict(s)
            if isinstance(a, (list, tuple, np.ndarray)):
                a = int(np.argmax(a))
            return int(a)
        # Mapping-like lookup by tuple key
        if hasattr(self._q_model, "get"):
            key = tuple(np.round(s, 2))
            a = self._q_model.get(key, None)
            if a is not None:
                return int(a)
        # Saved as wrapper {'_map': dict}
        if isinstance(self._q_model, dict) and '_map' in self._q_model:
            key = tuple(np.round(s, 2))
            val = self._q_model['_map'].get(key)
            if val is None:
                # Try coarser rounding
                key = tuple(np.round(s, 1))
                val = self._q_model['_map'].get(key)
            if val is None:
                # Try stringified keys often used in pickles
                skey = str(tuple(np.round(s, 2)))
                val = self._q_model['_map'].get(skey)
                if val is None:
                    skey = str(tuple(np.round(s, 1)))
                    val = self._q_model['_map'].get(skey)
            if val is not None:
                if isinstance(val, (list, tuple, np.ndarray)):
                    return int(np.argmax(np.array(val)))
                return int(val)
        raise RuntimeError("QLearning model present but unsupported interface: expected predict() or mapping")
    
    def _discretize_state(self, state: np.ndarray) -> int:
        """Convert continuous state to discrete state index (0-242).
        
        Must match the discretization used during training in CloudSimQLearningEnv.
        State: [budget_ratio, query_freq, last_rt, replica_count, query_interval, 
                consecutive_count, has_replica, cpu_util, mem_util]
        """
        # Discretization bins (3 bins each = 3^5 = 243 states for 5 key features)
        # Using the same logic as in the training environment
        
        budget_bin = min(2, int(state[0] * 3))  # budget_ratio: [0, 0.33) -> 0, [0.33, 0.66) -> 1, [0.66, 1] -> 2
        
        # Query frequency discretization
        freq = state[1]
        if freq < 0.33:
            freq_bin = 0
        elif freq < 0.66:
            freq_bin = 1
        else:
            freq_bin = 2
        
        # Response time discretization
        rt = state[2]
        if rt < 0.5:
            rt_bin = 0
        elif rt < 1.0:
            rt_bin = 1
        else:
            rt_bin = 2
        
        # Replica count (already discrete 0-2, map to bins)
        replica = int(state[3])
        replica_bin = min(2, replica)
        
        # CPU utilization
        cpu = state[7]
        if cpu < 0.33:
            cpu_bin = 0
        elif cpu < 0.66:
            cpu_bin = 1
        else:
            cpu_bin = 2
        
        # Compute state index: base-3 encoding
        # state_idx = bin0 * 3^4 + bin1 * 3^3 + bin2 * 3^2 + bin3 * 3^1 + bin4 * 3^0
        state_idx = (budget_bin * 81 + freq_bin * 27 + rt_bin * 9 + replica_bin * 3 + cpu_bin)
        
        # Ensure within bounds
        return min(242, max(0, state_idx))

    def selectActionDQN(self, state: list) -> int:
        if self._dqn_model is None or not TORCH_AVAILABLE:
            raise RuntimeError("DQN model not loaded or torch missing (strict mode)")
        s = np.array(state, dtype=np.float32)
        with torch.no_grad():
            x = torch.tensor(s).unsqueeze(0)
            q = self._dqn_model(x)
            if hasattr(q, "detach"):
                q = q.detach().cpu().numpy()
            return int(np.argmax(q))

    # Online updates are optional for validation; no-ops are fine
    def updateQLearning(self, reward: float, nextState: list, done: bool) -> None:
        self._last_action = 0

    def updateDQN(self, reward: float, nextState: list, done: bool) -> None:
        self._last_action = 0

    # === Optional utility methods expected by Java interface ===
    def resetCounters(self) -> None:
        self._last_action = 0

    def saveModels(self) -> None:
        # No-op for validation (not training persistently)
        pass

    def getModelInfo(self) -> str:
        return (
            f"QLearning={'yes' if self._q_model is not None else 'no'}; "
            f"DQN={'yes' if self._dqn_model is not None else 'no'}"
        )

    # Py4J expects a getClass method name sometimes
    class Java:
        implements = ["org.tcdrm.adaptive.rl.PythonRLBridge"]
