#!/usr/bin/env python3
"""
Test de connexion Python-Java via Py4J
Vérifie que le gateway Java fonctionne correctement
"""

import sys
import time
from py4j.java_gateway import JavaGateway, GatewayParameters

def test_connection():
    """Test la connexion au gateway Java"""
    print("="*60)
    print("Test de Connexion Python-Java (Py4J)")
    print("="*60)
    print()
    
    # Étape 1: Connexion au gateway
    print(">>> Étape 1: Connexion au gateway Java...")
    try:
        gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25333))
        tcdrm = gateway.entry_point
        print("✅ Connexion réussie!")
    except Exception as e:
        print(f"❌ Erreur de connexion: {e}")
        print()
        print("Assurez-vous que le gateway Java est démarré:")
        print("  cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive")
        print("  mvn clean package")
        print("  java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar org.tcdrm.adaptive.bridge.Py4JGateway")
        return False
    
    print()
    
    # Étape 2: Créer l'environnement
    print(">>> Étape 2: Création de l'environnement TCDRM...")
    try:
        env = tcdrm.createEnvironment(5.3)
        print(f"✅ Environnement créé avec 5.3 GB de données")
        print(f"   - Espace d'états: {tcdrm.getStateSpaceSize()}")
        print(f"   - Espace d'actions: {tcdrm.getActionSpaceSize()}")
    except Exception as e:
        print(f"❌ Erreur lors de la création: {e}")
        gateway.close()
        return False
    
    print()
    
    # Étape 3: Reset l'environnement
    print(">>> Étape 3: Reset de l'environnement...")
    try:
        state = tcdrm.reset(42)
        print(f"✅ État initial: {state}")
        print(f"   - Budget: ${tcdrm.getCurrentBudget():.2f}")
        print(f"   - Latence: {tcdrm.getCurrentLatency():.2f} ms")
        print(f"   - Réplicas: {tcdrm.getCurrentReplicaCount()}")
    except Exception as e:
        print(f"❌ Erreur lors du reset: {e}")
        gateway.close()
        return False
    
    print()
    
    # Étape 4: Tester les actions
    print(">>> Étape 4: Test des actions...")
    actions = [
        (0, "CREATE_REPLICA"),
        (2, "DO_NOTHING"),
        (1, "DELETE_REPLICA")
    ]
    
    for action_idx, action_name in actions:
        try:
            result = tcdrm.step(action_idx)
            reward = result.getReward()
            terminated = result.isTerminated()
            
            print(f"   Action: {action_name}")
            print(f"     → Récompense: {reward:.2f}")
            print(f"     → Budget: ${tcdrm.getCurrentBudget():.2f}")
            print(f"     → Latence: {tcdrm.getCurrentLatency():.2f} ms")
            print(f"     → Réplicas: {tcdrm.getCurrentReplicaCount()}")
            print(f"     → Terminé: {terminated}")
            
            if terminated:
                print("     ⚠️  Épisode terminé")
                break
                
        except Exception as e:
            print(f"   ❌ Erreur avec action {action_name}: {e}")
            gateway.close()
            return False
    
    print()
    
    # Étape 5: Fermeture
    print(">>> Étape 5: Fermeture de la connexion...")
    try:
        tcdrm.close()
        gateway.close()
        print("✅ Connexion fermée proprement")
    except Exception as e:
        print(f"⚠️  Erreur lors de la fermeture: {e}")
    
    print()
    print("="*60)
    print("✅ Test de connexion réussi!")
    print("="*60)
    print()
    print("Prochaines étapes:")
    print("  1. Entraîner un agent Python avec l'environnement Java:")
    print("     uv run python train_with_java_env.py")
    print("  2. Comparer les performances Python vs Java")
    print("  3. Utiliser CloudSimPlus pour des simulations réalistes")
    
    return True


if __name__ == "__main__":
    success = test_connection()
    sys.exit(0 if success else 1)
