import argparse
import time
from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters
from .rl_bridge import PythonRLBridge


def connect_and_register(port: int, q_path: str, rainbow_path: str, timeout_sec: int = 120):
    print(f"📡 Connecting to Java Gateway (port {port})...")
    start = time.time()
    gw = None
    last_log = 0
    while True:
        try:
            gw = JavaGateway(
                gateway_parameters=GatewayParameters(port=port),
                callback_server_parameters=CallbackServerParameters()
            )
            # Touch entry point to validate connection
            _ = gw.entry_point
            break
        except Exception as e:
            if time.time() - start >= timeout_sec:
                print(f"❌ Could not connect to Java gateway within {timeout_sec}s: {e}")
                raise
            # periodic wait log
            if int(time.time() - start) // 5 > last_log:
                last_log = int(time.time() - start) // 5
                print(f"   ... waiting for Java gateway ({int(time.time()-start)}s/{timeout_sec}s)")
            time.sleep(1)

    bridge = PythonRLBridge(qlearning_model_path=q_path, rainbow_model_path=rainbow_path)
    gw.entry_point.registerPythonBridge(bridge)

    print("✅ Connected!")
    print(f"  - Q-Learning ready: {bridge.isQLearningReady()}")
    print(f"  - Rainbow DQN ready: {bridge.isRainbowReady()}")
    return gw


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--qlearning-model', default='models/qlearning_cloudsim.pkl')
    # --dqn-model conservé comme alias rétro-compatible du modèle Rainbow DQN
    parser.add_argument('--dqn-model', '--rainbow-model', dest='dqn_model',
                        default='models/rainbow_cloudsim.pt')
    parser.add_argument('--port', type=int, default=25333)
    args = parser.parse_args()

    gw = connect_and_register(args.port, args.qlearning_model, args.dqn_model)

    print("\n🎯 Ready. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        gw.shutdown()
        print("🛑 Stopped.")


if __name__ == '__main__':
    main()
