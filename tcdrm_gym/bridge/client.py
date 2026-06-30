"""
Py4J client for connecting Python RL models to Java/CloudSim.
"""

import os
import sys
import time
import argparse

from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters

from .rl_bridge import PythonRLBridge


def create_bridge(qlearning_path: str, rainbow_path: str) -> PythonRLBridge:
    """Create RL bridge with fresh agents for online learning."""
    return PythonRLBridge(
        qlearning_model_path=qlearning_path,
        rainbow_model_path=rainbow_path
    )


def connect_to_java(bridge: PythonRLBridge, port: int) -> JavaGateway:
    """Connect to Java Gateway and register bridge."""
    print(f"Connecting to Java Gateway (port {port})...")

    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(port=port),
        callback_server_parameters=CallbackServerParameters()
    )

    gateway.entry_point.registerPythonBridge(bridge)

    print("Connected to Java Gateway!")
    print(f"  - Q-Learning:  {'OK' if bridge.isQLearningReady() else 'missing'}")
    print(f"  - Rainbow DQN: {'OK' if bridge.isRainbowReady() else 'missing'}")

    return gateway


def run_client(qlearning_path: str, rainbow_path: str, port: int):
    """Run the Py4J client."""
    print("=" * 70)
    print("PYTHON PY4J CLIENT - RL MODELS FOR JAVA/CLOUDSIM")
    print("=" * 70)
    print()

    bridge = create_bridge(qlearning_path, rainbow_path)

    try:
        gateway = connect_to_java(bridge, port)

        print()
        print("Ready to receive requests from Java/CloudSim")
        print("   Press Ctrl+C to stop")
        print()

        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        print("\n\nStopping Python client...")
        gateway.shutdown()
        print("Client stopped")
    except Exception as e:
        print(f"\nERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


def main():
    """Entry point with CLI argument parsing."""
    parser = argparse.ArgumentParser(description='Python Py4J client for RL models')
    parser.add_argument('--qlearning-model', type=str,
                        default='models/qlearning_cloudsim.pkl',
                        help='Path to Q-Learning model')
    parser.add_argument('--rainbow-model', type=str,
                        default='models/rainbow_cloudsim.pt',
                        help='Path to Rainbow DQN model')
    default_port = int(os.environ.get('TCDRM_PY4J_PORT', '25333'))
    parser.add_argument('--port', type=int, default=default_port,
                        help='Java Gateway port')

    args = parser.parse_args()
    run_client(args.qlearning_model, args.rainbow_model, args.port)


if __name__ == '__main__':
    main()
