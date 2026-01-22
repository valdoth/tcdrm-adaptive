# 🚀 Quick Start: Intégration Python-Java

Guide rapide pour lancer l'intégration Python-Java TCDRM-ADAPTIVE.

---

## ⚡ Démarrage Rapide (1 commande)

```bash
cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive

# Workflow complet automatique: Compile → Démarre Gateway → Test → Prêt
./launch_python_java_integration.sh full
```

**Ce que fait cette commande:**

1. ✅ Vérifie Maven
2. ✅ Compile le projet Java (avec Py4J)
3. ✅ Démarre le gateway Java en arrière-plan
4. ✅ Teste la connexion Python-Java
5. ✅ Laisse le gateway actif pour l'entraînement

---

## 📋 Utilisation du Script

### Mode Interactif (Menu)

```bash
./launch_python_java_integration.sh
# ou
./launch_python_java_integration.sh menu
```

**Menu disponible:**

```
=== Menu Principal ===
1. Compiler le projet Java
2. Démarrer le gateway Java
3. Tester la connexion
4. Entraîner PPO (50k timesteps)
5. Entraîner DQN (50k timesteps)
6. Entraînement personnalisé
7. Afficher le statut
8. Afficher les logs
9. Arrêter le gateway
0. Quitter
```

### Commandes Directes

```bash
# Compiler le projet Java
./launch_python_java_integration.sh compile

# Démarrer le gateway Java (en arrière-plan)
./launch_python_java_integration.sh start

# Tester la connexion
./launch_python_java_integration.sh test

# Entraîner PPO avec paramètres par défaut
./launch_python_java_integration.sh train ppo

# Entraîner DQN avec paramètres personnalisés
./launch_python_java_integration.sh train dqn 100000 11.9

# Afficher le statut
./launch_python_java_integration.sh status

# Voir les logs en temps réel
./launch_python_java_integration.sh logs

# Arrêter le gateway
./launch_python_java_integration.sh stop
```

---

## 🎯 Workflows Typiques

### Workflow 1: Premier Démarrage

```bash
# 1. Workflow complet automatique
./launch_python_java_integration.sh full

# 2. Le gateway est maintenant actif, entraîner un agent
./launch_python_java_integration.sh train ppo 50000 5.3

# 3. Quand terminé, arrêter le gateway
./launch_python_java_integration.sh stop
```

### Workflow 2: Développement Itératif

```bash
# 1. Démarrer le gateway une fois
./launch_python_java_integration.sh start

# 2. Tester différents algorithmes
./launch_python_java_integration.sh train ppo 50000 5.3
./launch_python_java_integration.sh train dqn 50000 5.3

# 3. Vérifier le statut entre les entraînements
./launch_python_java_integration.sh status

# 4. À la fin, arrêter le gateway
./launch_python_java_integration.sh stop
```

### Workflow 3: Debugging

```bash
# 1. Vérifier le statut
./launch_python_java_integration.sh status

# 2. Voir les logs en temps réel
./launch_python_java_integration.sh logs
# (Ctrl+C pour quitter)

# 3. Tester la connexion
./launch_python_java_integration.sh test

# 4. Redémarrer le gateway si nécessaire
./launch_python_java_integration.sh stop
./launch_python_java_integration.sh start
```

---

## 📊 Exemples d'Utilisation

### Exemple 1: Entraînement PPO Standard

```bash
# Workflow complet
./launch_python_java_integration.sh full

# Entraîner PPO avec 50k timesteps sur 5.3 GB
./launch_python_java_integration.sh train ppo 50000 5.3
```

**Résultat attendu:**

```
>>> Entraînement PPO avec environnement Java...
Paramètres:
  - Algorithme: ppo
  - Timesteps: 50000
  - Data: 5.3 GB

✅ Connexion réussie!
Java TCDRM Environment created with 5.3 GB data
State space size: 108
Action space size: 3

>>> Création de l'agent PPO...
Using cpu device

>>> Entraînement pour 50000 timesteps...
[Progress bar...]

✅ Entraînement terminé!
Modèle sauvegardé: models/ppo_java_tcdrm/final_model
```

### Exemple 2: Comparaison PPO vs DQN

```bash
# Démarrer le gateway
./launch_python_java_integration.sh start

# Entraîner PPO
./launch_python_java_integration.sh train ppo 100000 5.3

# Entraîner DQN
./launch_python_java_integration.sh train dqn 100000 5.3

# Arrêter le gateway
./launch_python_java_integration.sh stop

# Comparer les résultats
cd python_rl
uv run python compare_algorithms.py \
  --ppo-model models/ppo_java_tcdrm/final_model.zip \
  --dqn-model models/dqn_java_tcdrm/final_model.zip \
  --episodes 20
```

### Exemple 3: Entraînement avec Grandes Données

```bash
# Workflow complet
./launch_python_java_integration.sh full

# Entraîner avec 11.9 GB (requête complexe R2)
./launch_python_java_integration.sh train ppo 100000 11.9

# Vérifier les logs pendant l'entraînement (autre terminal)
./launch_python_java_integration.sh logs
```

---

## 🔍 Vérification du Statut

### Commande Status

```bash
./launch_python_java_integration.sh status
```

**Sortie exemple:**

```
=== Statut de l'intégration ===
✅ Gateway Java: En cours (PID: 12345)
✅ Port 25333: Ouvert
✅ JAR compilé: 45M
```

### Vérification Manuelle

```bash
# Vérifier si le gateway est actif
ps aux | grep Py4JGateway

# Vérifier si le port est ouvert
lsof -i :25333

# Voir les logs
tail -f /tmp/tcdrm_gateway.log
```

---

## 🐛 Dépannage

### Problème: "mvn: command not found"

**Solution:**

```bash
# Installer Maven
brew install maven

# Vérifier l'installation
mvn -version
```

### Problème: Gateway ne démarre pas

**Diagnostic:**

```bash
# Voir les logs
cat /tmp/tcdrm_gateway.log

# Vérifier si le JAR existe
ls -lh target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar

# Recompiler si nécessaire
./launch_python_java_integration.sh compile
```

### Problème: Port déjà utilisé

**Solution:**

```bash
# Trouver le processus
lsof -i :25333

# Tuer le processus
kill -9 <PID>

# Ou utiliser le script
./launch_python_java_integration.sh stop
./launch_python_java_integration.sh start
```

### Problème: Test de connexion échoue

**Diagnostic:**

```bash
# 1. Vérifier que le gateway est actif
./launch_python_java_integration.sh status

# 2. Voir les logs Java
./launch_python_java_integration.sh logs

# 3. Tester manuellement
cd python_rl
uv run python -c "from py4j.java_gateway import JavaGateway; g = JavaGateway(); print(g.entry_point)"
```

---

## 📁 Fichiers Générés

### Logs et PID

- `/tmp/tcdrm_gateway.log` - Logs du gateway Java
- `/tmp/tcdrm_gateway.pid` - PID du processus gateway

### Modèles Entraînés

- `python_rl/models/ppo_java_tcdrm/` - Modèles PPO
- `python_rl/models/dqn_java_tcdrm/` - Modèles DQN
- `python_rl/logs/` - Logs TensorBoard

### Visualisation TensorBoard

```bash
cd python_rl

# Voir les logs PPO
tensorboard --logdir logs/ppo_java_tcdrm

# Voir les logs DQN
tensorboard --logdir logs/dqn_java_tcdrm

# Ouvrir dans le navigateur: http://localhost:6006
```

---

## 🎓 Commandes Avancées

### Entraînement Parallèle (Plusieurs Terminaux)

**Terminal 1:**

```bash
./launch_python_java_integration.sh start
```

**Terminal 2:**

```bash
./launch_python_java_integration.sh train ppo 50000 5.3
```

**Terminal 3:**

```bash
./launch_python_java_integration.sh logs
```

### Script Personnalisé

```bash
#!/bin/bash
# mon_workflow.sh

# Démarrer le gateway
./launch_python_java_integration.sh start

# Entraîner plusieurs configurations
for data_gb in 5.3 11.9 20.0; do
    echo "Entraînement avec ${data_gb} GB..."
    ./launch_python_java_integration.sh train ppo 50000 ${data_gb}
done

# Arrêter le gateway
./launch_python_java_integration.sh stop
```

---

## 📚 Documentation Complète

Pour plus de détails, consultez:

- **`JAVA_INTEGRATION_GUIDE.md`** - Guide complet d'intégration
- **`ADVANCED_FEATURES.md`** - Fonctionnalités avancées
- **`python_rl/README.md`** - Documentation Python RL

---

## ✅ Checklist de Démarrage

- [ ] Maven installé (`brew install maven`)
- [ ] Projet compilé (`./launch_python_java_integration.sh compile`)
- [ ] Gateway démarré (`./launch_python_java_integration.sh start`)
- [ ] Connexion testée (`./launch_python_java_integration.sh test`)
- [ ] Entraînement lancé (`./launch_python_java_integration.sh train ppo`)

---

## 🎉 Prêt à Commencer!

```bash
# Une seule commande pour tout faire:
./launch_python_java_integration.sh full

# Puis entraîner:
./launch_python_java_integration.sh train ppo 50000 5.3
```

**Bon entraînement! 🚀**
