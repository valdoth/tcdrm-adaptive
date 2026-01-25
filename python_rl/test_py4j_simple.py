#!/usr/bin/env python3
"""
Test simple de connexion Py4J pour diagnostiquer le problème
"""

from py4j.java_gateway import JavaGateway, GatewayParameters
import time

print("🔍 Test de connexion Py4J simple")
print("=" * 60)

try:
    print("\n1. Connexion au GatewayServer Java...")
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(
            address='localhost',
            port=25333,
            auto_convert=True
        )
    )
    print("✅ Gateway créé")
    
    print("\n2. Récupération de l'entry point...")
    entry_point = gateway.entry_point
    print(f"✅ Entry point: {entry_point}")
    print(f"   Type: {type(entry_point)}")
    
    print("\n3. Test de la méthode isModelLoaded()...")
    is_loaded = entry_point.isModelLoaded()
    print(f"✅ isModelLoaded() = {is_loaded}")
    
    print("\n4. Test de la méthode getModelInfo()...")
    info = entry_point.getModelInfo()
    print(f"✅ getModelInfo() = {info}")
    
    print("\n5. Test d'enregistrement d'une Q-table simple...")
    # Créer une petite Q-table de test
    test_q_table = [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]
    print(f"   Q-table test: {test_q_table}")
    
    entry_point.registerQTable(test_q_table, "Test Q-Table")
    print("✅ registerQTable() réussi!")
    
    print("\n6. Vérification après enregistrement...")
    is_loaded = entry_point.isModelLoaded()
    print(f"✅ isModelLoaded() = {is_loaded}")
    
    info = entry_point.getModelInfo()
    print(f"✅ getModelInfo() = {info}")
    
    print("\n" + "=" * 60)
    print("✅ TOUS LES TESTS RÉUSSIS!")
    print("=" * 60)
    
    gateway.shutdown()
    
except Exception as e:
    print(f"\n❌ ERREUR: {e}")
    print(f"   Type: {type(e)}")
    import traceback
    traceback.print_exc()
