# 🚀 Script de Workflow Automatique

Script shell unique pour exécuter tout le workflow TCDRM-ADAPTIVE automatiquement.

---

## 📋 Qu'est-ce que ce script fait?

Le script `run_full_workflow.sh` orchestre automatiquement les **4 étapes** du workflow:

1. ✅ **Entraînement Python** - Q-Learning avec Gymnasium
2. ✅ **Compilation Java** - Maven package
3. ✅ **Serveur Py4J** - Charge le modèle Python (arrière-plan)
4. ✅ **Comparaison CloudSim** - Compare les 3 approches

**Avantage**: Une seule commande au lieu de 4 étapes manuelles! 🎯

---

## 🎯 Utilisation Rapide

### Workflow Complet (Défaut: R1)

```bash
./run_full_workflow.sh
```

**Ce qui se passe**:

1. Entraîne Q-Learning pour R1 (5.3 GB) - 20 min
2. Compile le projet Java - 1 min
3. Démarre serveur Py4J en arrière-plan
4. Exécute comparaison CloudSim (3 approches)
5. Arrête automatiquement le serveur Py4J

**Durée totale**: ~25-30 minutes

---

## 🎛️ Options Disponibles

### Choisir un Scénario

```bash
# R1 - Requête Simple (5.3 GB) [défaut]
./run_full_workflow.sh --scenario r1

# R2 - Requête Complexe (11.9 GB)
./run_full_workflow.sh --scenario r2

# R3 - Requête Large (20 GB)
./run_full_workflow.sh --scenario r3
```

---

### Sauter l'Entraînement (Utiliser Modèles Existants)

```bash
./run_full_workflow.sh --skip-training
```

**Utile si**:

- Modèles déjà entraînés
- Vous voulez juste retester la comparaison
- Gain de temps (~20 minutes)

---

### Sauter la Compilation (JAR Déjà Compilé)

```bash
./run_full_workflow.sh --skip-compile
```

**Utile si**:

- Code Java non modifié
- JAR déjà à jour
- Gain de temps (~1 minute)

---

### Combiner les Options

```bash
# Seulement comparaison (modèles et JAR existants)
./run_full_workflow.sh --skip-training --skip-compile

# R2 sans recompiler
./run_full_workflow.sh --scenario r2 --skip-compile
```

---

## 📊 Sortie du Script

### Console

```
============================================================
  TCDRM-ADAPTIVE: Workflow Complet (3 Approches)
  Scénario: r1_simple (5.3 GB)
============================================================

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ÉTAPE 1/4: Entraînement Q-Learning Python
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

>>> Entraînement pour r1_simple (5.3 GB)
[Progression de l'entraînement...]
✅ Entraînement terminé

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ÉTAPE 2/4: Compilation Java
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

>>> Compilation Maven...
✅ Compilation réussie

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ÉTAPE 3/4: Démarrage du serveur Py4J
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

>>> Modèle trouvé: results/qlearning/r1_simple/run_*/models/best_model.pkl
>>> Démarrage du serveur Py4J en arrière-plan...
✅ Serveur Py4J démarré (PID: 12345)
>>> Attente du démarrage du serveur (15 secondes)...
✅ Serveur Py4J prêt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ÉTAPE 4/4: Comparaison Java CloudSim (3 Approches)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

>>> Exécution de la comparaison...

[Résultats de la comparaison...]

✅ Comparaison terminée avec succès

============================================================
  RÉSUMÉ FINAL
============================================================

✅ Entraînement Python: r1_simple
✅ Compilation Java: Réussie
✅ Serveur Py4J: Actif (PID: 12345)
✅ Comparaison CloudSim: Réussie

Résultats disponibles dans:
  - Modèles Python: python_rl/results/qlearning/r1_simple/
  - Graphes Java: results/cloudsim_comparison/

Voir les graphes:
  open results/cloudsim_comparison/*.png

============================================================
  🎉 Workflow terminé!
============================================================

🧹 Nettoyage...
Arrêt du serveur Py4J (PID: 12345)...
✅ Serveur Py4J arrêté
✅ Nettoyage terminé
```

---

## 🔧 Fonctionnalités Avancées

### Gestion Automatique du Serveur Py4J

Le script:

- ✅ Démarre le serveur Py4J en arrière-plan
- ✅ Attend qu'il soit prêt (15 secondes)
- ✅ Vérifie qu'il ne crash pas
- ✅ L'arrête automatiquement à la fin
- ✅ Nettoie même en cas d'erreur (trap EXIT)

**Vous n'avez rien à faire manuellement!** 🎯

---

### Gestion des Erreurs

Le script s'arrête automatiquement si:

- ❌ uv n'est pas installé
- ❌ Maven n'est pas installé
- ❌ Modèle Python introuvable
- ❌ Compilation Java échoue
- ❌ Serveur Py4J crash

**Message d'erreur clair à chaque fois**

---

### Logs du Serveur Py4J

Le serveur Py4J écrit ses logs dans `/tmp/py4j_server.log`

```bash
# Voir les logs en temps réel
tail -f /tmp/py4j_server.log

# Voir tous les logs
cat /tmp/py4j_server.log
```

---

## 📁 Résultats Générés

### Modèles Python

```
python_rl/results/qlearning/r1_simple/run_YYYYMMDD_HHMMSS/
├── models/
│   └── best_model.pkl          # Modèle utilisé par Py4J
├── metrics/
│   └── training_metrics.csv
└── plots/
    └── training_curves.png
```

### Graphes Java (9 fichiers)

```
results/cloudsim_comparison/
├── cost_comparison_R1.png      # 3 courbes
├── latency_comparison_R1.png   # 3 courbes + SLA
├── replicas_comparison_R1.png  # 3 courbes
├── cost_comparison_R2.png
├── latency_comparison_R2.png
└── replicas_comparison_R2.png
```

---

## 🐛 Dépannage

### Problème: "uv n'est pas installé"

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

---

### Problème: "Maven n'est pas installé"

```bash
brew install maven
```

---

### Problème: "Aucun modèle trouvé"

**Cause**: Modèle pas encore entraîné

**Solution**:

```bash
# Entraîner d'abord
cd python_rl
./run_experiments.sh train-r1

# Puis relancer
cd ..
./run_full_workflow.sh --skip-training
```

---

### Problème: "Le serveur Py4J a crashé"

**Cause**: Erreur dans le modèle Python ou Py4J

**Solution**:

```bash
# Voir les logs
cat /tmp/py4j_server.log

# Vérifier que py4j est installé
cd python_rl
uv pip install py4j
```

---

### Problème: Script bloqué

**Cause**: Serveur Py4J ne démarre pas

**Solution**:

```bash
# Ctrl+C pour arrêter
# Le script nettoiera automatiquement

# Vérifier les ports
lsof -ti:25333 | xargs kill -9
lsof -ti:25334 | xargs kill -9

# Relancer
./run_full_workflow.sh
```

---

## 🎓 Cas d'Usage

### 1. Premier Run Complet

```bash
./run_full_workflow.sh
```

**Durée**: ~25-30 minutes  
**Résultat**: Tout est fait automatiquement

---

### 2. Tester un Autre Scénario

```bash
# Déjà entraîné R1, tester R2
./run_full_workflow.sh --scenario r2
```

**Durée**: ~25-30 minutes  
**Résultat**: Nouveau modèle R2 + comparaison

---

### 3. Retester Après Modification Java

```bash
# Code Java modifié, modèles Python OK
./run_full_workflow.sh --skip-training
```

**Durée**: ~5-10 minutes  
**Résultat**: Recompile + comparaison

---

### 4. Comparaison Rapide

```bash
# Tout est déjà prêt, juste recomparer
./run_full_workflow.sh --skip-training --skip-compile
```

**Durée**: ~5 minutes  
**Résultat**: Seulement comparaison CloudSim

---

## 📊 Comparaison avec Workflow Manuel

| Étape         | Manuel                                              | Script Automatique            |
| ------------- | --------------------------------------------------- | ----------------------------- |
| Entraînement  | `cd python_rl && ./run_experiments.sh train-r1`     | ✅ Automatique                |
| Compilation   | `cd .. && mvn clean package`                        | ✅ Automatique                |
| Serveur Py4J  | `python3 load_model_for_java.py ...` (Terminal 1)   | ✅ Automatique (arrière-plan) |
| Comparaison   | `java -cp ... TcdrmComparisonCloudSim` (Terminal 2) | ✅ Automatique                |
| Arrêt Py4J    | `Ctrl+C` dans Terminal 1                            | ✅ Automatique                |
| **Terminaux** | **2 terminaux**                                     | **1 terminal** ✅             |
| **Commandes** | **5 commandes**                                     | **1 commande** ✅             |

---

## ✅ Checklist

### Avant Premier Run

- [ ] Python 3.11+ installé
- [ ] Java 17+ installé
- [ ] Maven installé
- [ ] uv installé
- [ ] Script exécutable (`chmod +x run_full_workflow.sh`)

### Pendant l'Exécution

- [ ] Ne pas interrompre (sauf Ctrl+C)
- [ ] Surveiller les messages d'erreur
- [ ] Vérifier que Py4J démarre (logs)

### Après l'Exécution

- [ ] Vérifier les graphes générés
- [ ] Vérifier le tableau comparatif
- [ ] Vérifier que Py4J est bien arrêté

---

## 🎯 Commandes Rapides

```bash
# Workflow complet R1
./run_full_workflow.sh

# Workflow complet R2
./run_full_workflow.sh --scenario r2

# Seulement comparaison (tout existe)
./run_full_workflow.sh --skip-training --skip-compile

# Aide
./run_full_workflow.sh --help
```

---

**Un seul script pour tout automatiser! 🚀**
