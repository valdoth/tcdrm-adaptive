"""
Entry point for connecting Python RL models to Java/CloudSim via Py4J.
This is a thin wrapper around the bridge module.
"""

import sys
import os

# Add current directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bridge.client import main

if __name__ == '__main__':
    main()
