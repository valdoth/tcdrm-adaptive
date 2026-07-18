"""Thin wrapper: re-exports PythonRLBridge from tcdrm_gym for exact model compatibility.

The tcdrm_gym bridge uses the actual SimpleQLearningAgent and DQNAgent classes with
the correct state discretization and loading logic. The old standalone version had
a wrong state mapping (_discretize_state used indices mismatched with buildRLState()).
"""
import os
import sys
import importlib.util
import types

# Path to tcdrm_gym root (4 levels up from this file)
_HERE = os.path.dirname(os.path.abspath(__file__))
_TCDRM_GYM = os.path.abspath(os.path.join(_HERE, '..', '..', '..', 'tcdrm_gym'))

if not os.path.isdir(_TCDRM_GYM):
    raise ImportError(
        f"tcdrm_gym not found at {_TCDRM_GYM}. "
        "Ensure the validation directory is inside the tcdrm-adaptive repo."
    )

# Add tcdrm_gym root so absolute imports (agents.*, bridge.*) resolve correctly
if _TCDRM_GYM not in sys.path:
    sys.path.insert(0, _TCDRM_GYM)

# We load tcdrm_gym's bridge under a private package name to avoid conflicting
# with this validation/python/bridge package.
_PKG = '_tcdrm_gym_bridge'

if _PKG not in sys.modules:
    pkg = types.ModuleType(_PKG)
    pkg.__path__ = [os.path.join(_TCDRM_GYM, 'bridge')]
    pkg.__package__ = _PKG
    sys.modules[_PKG] = pkg

def _load(module_name: str, file_path: str):
    """Load a module under _PKG.* so relative imports within it work."""
    full_name = f'{_PKG}.{module_name}'
    if full_name in sys.modules:
        return sys.modules[full_name]
    spec = importlib.util.spec_from_file_location(full_name, file_path)
    mod = importlib.util.module_from_spec(spec)
    mod.__package__ = _PKG
    sys.modules[full_name] = mod
    spec.loader.exec_module(mod)
    return mod

_bridge_dir = os.path.join(_TCDRM_GYM, 'bridge')

# Load the actual rl_bridge (self-contained: adaptive_strategy a été supprimé du repo,
# la règle anti-thrashing en dur ayant été remplacée par la pénalité APPRISE)
_rl_mod = _load('rl_bridge', os.path.join(_bridge_dir, 'rl_bridge.py'))

PythonRLBridge = _rl_mod.PythonRLBridge
