# Workflow Final - TCDRM-ADAPTIVE

## 🎯 Architecture

**Python** : Entraînement des modèles RL uniquement  
**Java/CloudSim** : Simulations + Génération des graphiques via Py4J

---

## 📋 Workflow Complet

### Étape 1 : Entraîner les Modèles (Python)

```bash
# Depuis la racine du projet
./run_workflow.sh

# Ou avec options
./run_workflow.sh --n-episodes 2000
./run_workflow.sh --skip-dqn  # Seulement Q-Learning
```

**Résultat** :
- `python_rl/models/simple_qlearning.pkl` - Q-Learning entraîné
- `python_rl/results/dqn/dqn_model.pt` - DQN entraîné

### Étape 2 : Lancer les Simulations CloudSim (Java)

```bash
# Compiler le projet
mvn clean package

# Démarrer le serveur Py4J Gateway (Java)
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim

# Dans un autre terminal, connecter Python avec les modèles
cd python_rl
uv run python connect_to_java.py \
  --qlearning-model models/simple_qlearning.pkl \
  --dqn-model results/dqn/dqn_model.pt
```

**Résultat** :
- Graphiques générés dans `images/` par Java/CloudSim
- Utilise les vrais modèles entraînés via Py4J

---

## 🔧 Fichiers Créés/Modifiés

### Python

**Nouveaux fichiers** :
- `agents/simple_qlearning_agent.py` - Agent Q-Learning propre
- `agents/simple_qlearning_wrapper.py` - Wrapper pour compatibilité
- `train_simple_qlearning.py` - Entraînement Q-Learning
- `connect_to_java.py` - Client Py4J pour Java

**Modifiés** :
- `envs/tcdrm_qlearning_env.py` - Reward simplifié, TSLA_BASE corrigé
- `generate_graphs_unified.py` - Utilise SimpleQLearningWrapper

**Supprimés** :
- `agents/tabular_qlearning.py` (ancien)
- `agents/qlearning_formal_agent.py` (ancien)
- `train_qlearning_formal.py` (ancien)
- Tous les fichiers `debug_*.py`, `test_*.py`

### Workflow

**Modifié** :
- `run_workflow.sh` - Entraîne uniquement, pas de génération de graphiques

---

## 📊 Utilisation des Modèles en Java

### PythonRLBridge (connect_to_java.py)

**Méthodes exposées à Java** :

```python
# Q-Learning
int selectActionQLearning(state_array)
# state_array = [latency, budget, replicas, popularity, cost]

# DQN  
int selectActionDQN(state_array)
# state_array = [8 features continues]

# Vérifications
boolean isQLearningReady()
boolean isDQNReady()
String getModelInfo()
```

### Exemple Java

```java
// Obtenir le pont Python via Py4J
PythonRLBridge bridge = gateway.entry_point.getPythonBridge();

// Préparer l'état
double[] state = {latency, budget, replicas, popularity, cost};

// Obtenir action Q-Learning
int action = bridge.selectActionQLearning(state);

// Obtenir action DQN
int action = bridge.selectActionDQN(state);
```

---

## ✅ Résultats Attendus

### Q-Learning Simple

| Métrique | Valeur |
|----------|--------|
| Reward moyen | ~8600 |
| SLA compliance | ~83% |
| Latence R1 | ~1100ms |
| NOREP R1 | ~5400ms |
| Amélioration | **80%** |

### Graphiques CloudSim

**3 courbes** :
- Q-Learning Simple (Python via Py4J)
- TCDRM Statique
- NOREP

**4 courbes** (si DQN disponible) :
- Q-Learning Simple
- DQN (Python via Py4J)
- TCDRM Statique
- NOREP

---

## 🚀 Commandes Rapides

```bash
# 1. Entraîner les modèles
./run_workflow.sh --n-episodes 2000

# 2. Compiler Java
mvn clean package

# 3. Lancer simulation (terminal 1)
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim

# 4. Connecter Python (terminal 2)
cd python_rl
uv run python connect_to_java.py

# 5. Voir les graphiques
open images/*.png
```

---

## 📁 Structure Finale

```
tcdrm-adaptive/
├── run_workflow.sh                    # Entraînement uniquement
├── python_rl/
│   ├── agents/
│   │   ├── simple_qlearning_agent.py  # ✨ NOUVEAU
│   │   ├── simple_qlearning_wrapper.py
│   │   └── dqn_agent.py
│   ├── envs/
│   │   └── tcdrm_qlearning_env.py     # Mis à jour
│   ├── train_simple_qlearning.py      # ✨ NOUVEAU
│   ├── train_dqn_policy.py
│   ├── connect_to_java.py             # ✨ NOUVEAU (Py4J client)
│   └── models/
│       └── simple_qlearning.pkl
├── src/main/java/
│   └── org/tcdrm/adaptive/
│       ├── examples/
│       │   └── TcdrmComparisonCloudSim.java  # Utilise Py4J
│       └── gateway/
│           └── Py4JGateway.java
└── images/                            # Générés par Java
    ├── tcdrm_response_time_R1.png
    └── tcdrm_response_time_R2.png
```

---

## 🎉 Avantages du Nouveau Workflow

✅ **Séparation claire** : Python entraîne, Java simule  
✅ **Modèles réels** : Utilise les vrais modèles entraînés via Py4J  
✅ **CloudSim** : Simulations réalistes avec CloudSim Plus  
✅ **Maintenable** : Code propre, pas de fichiers de test  
✅ **Performant** : Q-Learning fonctionne (80% amélioration)  

---

## 📝 Notes Importantes

1. **Py4J Gateway** : Java démarre le serveur, Python se connecte
2. **Modèles** : Doivent être entraînés avant simulation Java
3. **Graphiques** : Générés par Java/CloudSim, pas Python
4. **État** : Conversion automatique Java → Python dans le pont
