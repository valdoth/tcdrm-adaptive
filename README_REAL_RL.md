# Utilisation des Modèles RL Réels pour les Graphes

Ce document explique comment générer les graphes avec les **vraies décisions** des modèles Q-Learning et DQN entraînés, au lieu des simulations bidon.

## 🎯 Changements Implémentés

### Avant (Simulation Bidon)
- Les courbes Q-Learning et DQN étaient **fausses**
- Elles utilisaient TCDRM Statique avec un facteur d'amélioration aléatoire (85-95%)
- **Problème** : Les courbes commençaient avec des latences basses (~170s) dès la requête 0, alors qu'elles devraient commencer comme NOREP (~200s)

### Maintenant (Vraie Simulation RL)
- **`RealRLBenchmark.java`** : Nouvelle classe qui exécute réellement les modèles Python via Py4J
- Les modèles Python prennent les **vraies décisions** de réplication à chaque requête
- Les courbes commencent correctement à ~200s (sans réplica) et évoluent selon les décisions RL

## 📋 Prérequis

### Modèles Entraînés
Vous devez avoir les modèles entraînés :
- **Q-Learning** : `python_rl/models/simple_qlearning.pkl`
- **DQN** : `python_rl/results/dqn/dqn_model.pt`

Si vous n'avez pas ces modèles, entraînez-les d'abord :
```bash
cd python_rl

# Entraîner Q-Learning
uv run python train_simple_qlearning.py

# Entraîner DQN
uv run python train_dqn_policy.py
```

## 🚀 Utilisation

### Étape 1 : Démarrer le serveur Java (Terminal 1)

```bash
# Compiler le projet
mvn clean compile

# Lancer le serveur Java Py4J
mvn exec:java -Dexec.mainClass="org.tcdrm.adaptive.examples.TcdrmComparisonCloudSim"
```

Le serveur Java va :
1. Démarrer le Gateway Py4J sur le port 25333
2. Attendre la connexion du client Python (60 secondes max)
3. Afficher : `🔌 Gateway Py4J démarré sur le port 25333`

### Étape 2 : Connecter le client Python (Terminal 2)

Dans un **nouveau terminal** :

```bash
cd python_rl

# Connecter avec les deux modèles
uv run python connect_to_java.py \
  --qlearning-model models/simple_qlearning.pkl \
  --dqn-model results/dqn/dqn_model.pt
```

Le client Python va :
1. Charger les modèles Q-Learning et DQN
2. Se connecter au Gateway Java
3. Afficher : `✅ Connecté au Gateway Java!`

### Étape 3 : Génération Automatique

Une fois connecté, Java va automatiquement :
1. Exécuter 2000 requêtes avec Q-Learning (vraies décisions)
2. Exécuter 2000 requêtes avec DQN (vraies décisions)
3. Exécuter TCDRM Statique et NOREP
4. Générer les graphes dans `images/`

## 📊 Résultats Attendus

### Courbes Correctes
- **Q-Learning/DQN** : Commencent à ~200s (comme NOREP), puis descendent progressivement selon les décisions de réplication
- **TCDRM Statique** : Commence à ~200s, descend à ~180s après la requête 200 (seuil PSLA)
- **NOREP** : Reste à ~200s tout le temps (pas de réplica)

### Graphes Générés
Les graphes sont sauvegardés dans `images/` :
- `tcdrm_combined_response_time_R1_4curves.png`
- `tcdrm_combined_bw_price_per_query_R1_4curves.png`
- `tcdrm_combined_cumulative_bw_price_R1_4curves.png`
- Et versions pour R2

## 🔧 Architecture

```
┌─────────────────────┐         Py4J          ┌──────────────────────┐
│   Java (Serveur)    │ ◄─────────────────► │  Python (Client)     │
│                     │                       │                      │
│ TcdrmComparison     │   selectAction()      │ PythonRLBridge       │
│   CloudSim          │ ──────────────────►  │                      │
│                     │                       │ - Q-Learning Agent   │
│ RealRLBenchmark     │   ◄── action (0/1/2)  │ - DQN Agent          │
│   - Simule 2000     │                       │                      │
│     requêtes        │                       │ Modèles entraînés:   │
│   - Appelle Python  │                       │ - simple_qlearning   │
│     à chaque query  │                       │ - dqn_model.pt       │
└─────────────────────┘                       └──────────────────────┘
```

## 🐛 Dépannage

### Erreur : "Le client Python ne s'est pas connecté"
- Vérifiez que le client Python est lancé dans les 60 secondes
- Vérifiez que les modèles existent aux chemins spécifiés

### Erreur : "Modèle Q-Learning non chargé"
- Le système utilisera TCDRM Statique comme fallback
- Vérifiez le chemin du modèle : `models/simple_qlearning.pkl`

### Graphes identiques à avant
- Assurez-vous d'avoir recompilé : `mvn clean compile`
- Vérifiez que le client Python est bien connecté

## 📝 Fichiers Modifiés

1. **`RealRLBenchmark.java`** (NOUVEAU)
   - Exécute réellement les modèles RL via Py4J
   - Simule 2000 requêtes avec vraies décisions

2. **`TcdrmComparisonCloudSim.java`** (MODIFIÉ)
   - Utilise `RealRLBenchmark` au lieu de simulations bidon
   - Passe le bridge Python aux méthodes

3. **`PythonRLBridge.java`** (MODIFIÉ)
   - Ajout de `selectActionQLearning(double[])`
   - Ajout de `selectActionDQN(double[])`
   - Ajout de `isQLearningReady()` et `isDQNReady()`

4. **`connect_to_java.py`** (MODIFIÉ)
   - Support pour `double[]` Java
   - Conversion automatique en liste Python

## ✅ Vérification

Pour vérifier que tout fonctionne :

1. Les logs Java doivent afficher :
   ```
   ✓ Simulation Q-Learning terminée (VRAIES décisions du modèle Python)
   ✓ Simulation DQN terminée (VRAIES décisions du modèle Python)
   ```

2. Les courbes Q-Learning/DQN doivent commencer à ~200s (comme NOREP)

3. Les décisions de réplication doivent varier (pas toujours 3 réplicas à la requête 200)
