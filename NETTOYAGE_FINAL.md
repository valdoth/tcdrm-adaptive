# Nettoyage Final et Fichiers à Conserver

## 📋 Fichiers Java - Décision

### ✅ À CONSERVER

**Fichier principal pour simulation CloudSim avec Py4J** :
- `TcdrmArticleAllGraphs3CurvesWithPy4J.java` - Génère graphiques avec modèles Python via Py4J

**Fichiers de support** (si utilisés par le fichier principal) :
- `TcdrmCloudSimSimulation.java` - Classes de base pour simulation
- Classes dans `src/main/java/org/tcdrm/adaptive/gateway/` - Py4J Gateway
- Classes dans `src/main/java/org/tcdrm/adaptive/rl/` - Interfaces RL

### ❌ À SUPPRIMER (Anciens fichiers sans Py4J)

Ces fichiers génèrent des graphiques sans utiliser les vrais modèles Python :
- `TcdrmArticleAllGraphs.java` - Graphiques sans RL
- `TcdrmArticleGraphs.java` - Graphiques basiques
- `TcdrmArticleGraphsDual.java` - Graphiques dual
- `TcdrmCombinedGraphs.java` - Graphiques combinés
- `TcdrmMetricsGraphs.java` - Métriques
- `TcdrmComparisonCloudSim.java.backup` - Fichier backup

### 🔄 À CRÉER/MODIFIER

**Nouveau fichier principal** :
- `TcdrmComparisonCloudSim.java` - Simulation CloudSim unifiée avec Q-Learning Simple + DQN via Py4J

---

## 🗑️ Commandes de Nettoyage

```bash
cd src/main/java/org/tcdrm/adaptive/examples

# Supprimer les anciens fichiers
rm -f TcdrmArticleAllGraphs.java
rm -f TcdrmArticleGraphs.java
rm -f TcdrmArticleGraphsDual.java
rm -f TcdrmCombinedGraphs.java
rm -f TcdrmMetricsGraphs.java
rm -f TcdrmComparisonCloudSim.java.backup

# Renommer le fichier Py4J en fichier principal
mv TcdrmArticleAllGraphs3CurvesWithPy4J.java TcdrmComparisonCloudSim.java

# Mettre à jour la classe Java pour utiliser SimpleQLearning
# (modifier le nom de la classe dans le fichier)
```

---

## 📁 Structure Finale

```
src/main/java/org/tcdrm/adaptive/
├── examples/
│   ├── TcdrmComparisonCloudSim.java       # ✨ Principal (ex-3CurvesWithPy4J)
│   └── TcdrmCloudSimSimulation.java       # Support (si nécessaire)
├── gateway/
│   └── Py4JGateway.java                   # Py4J Gateway Server
├── rl/
│   ├── PythonRLBridge.java                # Interface pour Python
│   └── PythonQLearningAgent.java          # Interface agent
└── benchmark/
    └── ...                                 # Classes de benchmark
```

---

## 🔧 Modifications Nécessaires

### 1. Renommer la classe Java

Dans `TcdrmComparisonCloudSim.java` (ex-3CurvesWithPy4J) :

```java
// Avant
public class TcdrmArticleAllGraphs3CurvesWithPy4J {

// Après
public class TcdrmComparisonCloudSim {
```

### 2. Mettre à jour les imports si nécessaire

Vérifier que les imports pointent vers les bonnes classes.

### 3. Adapter pour Q-Learning Simple + DQN

Le fichier doit gérer :
- Q-Learning Simple (via Py4J)
- DQN (via Py4J)
- TCDRM Statique (baseline)
- NOREP (baseline)

---

## 🚀 Workflow Final

### Entraînement + Simulation

```bash
# Workflow complet
./run_complete_workflow.sh

# Ou par étapes
./run_complete_workflow.sh --skip-simulation  # Seulement entraînement
./run_complete_workflow.sh --skip-training    # Seulement simulation
```

### Résultat

- Modèles entraînés : `python_rl/models/simple_qlearning.pkl`, `python_rl/results/dqn/dqn_model.pt`
- Graphiques générés : `images/*.png` (par Java/CloudSim)

---

## ✅ Checklist de Nettoyage

- [ ] Supprimer les 6 anciens fichiers Java
- [ ] Renommer `TcdrmArticleAllGraphs3CurvesWithPy4J.java` → `TcdrmComparisonCloudSim.java`
- [ ] Mettre à jour le nom de la classe dans le fichier
- [ ] Vérifier que `connect_to_java.py` fonctionne avec le nouveau workflow
- [ ] Tester le workflow complet
- [ ] Supprimer `run_workflow.sh` (remplacé par `run_complete_workflow.sh`)
- [ ] Mettre à jour la documentation

---

## 📝 Notes

- **Python** : Entraîne uniquement (Q-Learning Simple + DQN)
- **Java/CloudSim** : Simule et génère graphiques (via Py4J)
- **Py4J** : Pont entre Java et modèles Python entraînés
- **Graphiques** : 3 ou 4 courbes selon disponibilité DQN
