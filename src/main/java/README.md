# Code Java TCDRM-ADAPTIVE

## Vue d'ensemble

Le code Java de TCDRM-ADAPTIVE fournit les **baselines** et l'infrastructure de simulation multi-cloud. Il est **optionnel** dans le workflow principal, qui est maintenant entièrement en Python.

---

## 🎯 Rôle du Code Java

### Baselines de Référence
- **TCDRM Statique** : Politique avec seuils fixes (TSLA=150s, PSLA, CSLA)
- **NOREP** : Pas de réplication (baseline minimale)

### Infrastructure
- **CloudSim** : Simulation multi-cloud
- **Benchmarks** : Mesure de performance
- **Py4J Gateway** : Intégration optionnelle avec Python

---

## 📊 Architecture

```
src/main/java/org/tcdrm/adaptive/
├── benchmark/              # Benchmarks de performance
│   ├── BenchmarkData.java
│   ├── NoRepBenchmark.java
│   └── TcdrmBenchmark.java
├── bridge/                 # Intégration Py4J
│   └── Py4JGateway.java
├── cloudsim/               # Simulation CloudSim
│   ├── RLPolicyAdapter.java
│   └── TcdrmCloudSimEnvironment.java
├── core/                   # Infrastructure cloud
│   ├── CloudProvider.java
│   ├── CloudRegion.java
│   └── NetworkTopology.java
├── examples/               # Exemples et graphiques
│   ├── TcdrmArticleAllGraphs.java              # 2 courbes (TCDRM + NOREP)
│   ├── TcdrmArticleGraphs.java                 # Graphiques simples
│   ├── TcdrmArticleGraphsDual.java             # Graphiques duaux
│   └── TcdrmArticleAllGraphs3CurvesWithPy4J.java  # 3 courbes avec Py4J
├── replication/            # Stratégies de réplication
│   ├── NoReplicationStrategy.java
│   └── TcdrmReplicationStrategy.java
└── rl/                     # Environnement RL
    ├── Environment.java
    ├── TcdrmAction.java
    ├── TcdrmEnvironment.java
    ├── TcdrmEnvironmentV2.java
    └── TcdrmState.java
```

---

## 🚀 Utilisation

### 1. Compilation

```bash
# Depuis la racine du projet
mvn clean package -DskipTests
```

**Résultat :** `target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar`

### 2. Génération des Baselines (2 courbes)

```bash
# TCDRM Statique + NOREP
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs
```

**Génère :**
- `images/tcdrm_combined_response_time_R1.png`
- `images/tcdrm_combined_response_time_R2.png`
- `images/tcdrm_combined_cumulative_bw_price_R1.png`
- `images/tcdrm_combined_cumulative_bw_price_R2.png`

### 3. Graphiques Simples

```bash
# Graphiques individuels
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphs

# Graphiques duaux
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleGraphsDual
```

---

## 🔗 Intégration Java/Python (Avancé)

### Workflow Hybride avec Py4J

Le fichier `TcdrmArticleAllGraphs3CurvesWithPy4J.java` permet d'intégrer un modèle Python via Py4J pour générer des graphiques à 3 courbes.

**Note :** Cette approche est **optionnelle** et **complexe**. Le workflow Python pur est recommandé.

#### Prérequis
1. Modèle Q-Learning Python entraîné
2. Actions optimales générées
3. Py4J configuré

#### Exécution
```bash
# Voir run_workflow.sh pour le workflow complet
./run_workflow.sh
```

---

## 📝 Baselines Implémentées

### TCDRM Statique

**Fichier :** `replication/TcdrmReplicationStrategy.java`

**Paramètres :**
- TSLA = 150s (seuil de latence)
- CSLA = 1000$ (budget)
- PSLA = dynamique (popularité)

**Logique :**
1. Si latence > TSLA et popularité > PSLA → REPLICATE
2. Si budget < seuil → Pas de réplication
3. Sinon → NOOP

### NOREP (No Replication)

**Fichier :** `replication/NoReplicationStrategy.java`

**Logique :**
- Aucune réplication
- Baseline minimale pour comparaison

---

## 🎯 Workflow Recommandé

### Option 1 : Python Uniquement (Recommandé)

```bash
# Workflow complet en Python
./run_all_models.sh

# Équivalent :
cd python_rl
uv run python train_qlearning_formal.py --episodes 1000 --data-gb 5.3
uv run python train_dqn_policy.py --episodes 1000
uv run python generate_3curves_graphs.py --qlearning-model models/qlearning_formal.pkl
uv run python generate_4curves_graphs.py --qlearning-model models/qlearning_formal.pkl --dqn-model results/dqn/dqn_model.pt
```

**Avantages :**
- ✅ Simple et rapide
- ✅ Pas de compilation Java
- ✅ Tout en Python

### Option 2 : Validation avec Java (Optionnel)

```bash
# 1. Entraîner modèles Python
./run_all_models.sh

# 2. Compiler Java
mvn clean package -DskipTests

# 3. Générer baselines Java
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs

# 4. Comparer résultats Python vs Java
open images/*.png
```

**Avantages :**
- ✅ Validation croisée Python/Java
- ✅ Baselines de référence
- ✅ Compatibilité avec implémentation originale

---

## 📊 Comparaison Python vs Java

| Aspect | Python | Java |
|--------|--------|------|
| **Modèles RL** | Q-Learning Formel, DQN | Baselines (TCDRM Statique, NOREP) |
| **Entraînement** | ✅ Oui | ❌ Non |
| **Graphiques** | ✅ 3 et 4 courbes | 2 courbes |
| **Flexibilité** | ✅ Haute | Moyenne |
| **Performance** | Rapide | Moyenne |
| **Utilisation** | Principal | Optionnel |

---

## 🔧 Développement

### Ajouter une Nouvelle Baseline

1. Créer une classe dans `replication/`
2. Implémenter `ReplicationStrategy`
3. Ajouter dans `examples/` pour graphiques

### Modifier les Paramètres

**TCDRM Statique :**
```java
// Dans TcdrmReplicationStrategy.java
private static final double TSLA = 150.0;  // Modifier ici
private static final double CSLA = 1000.0;
```

### Ajouter un Nouveau Graphique

1. Créer une classe dans `examples/`
2. Utiliser `XChart` pour génération
3. Sauvegarder dans `images/`

---

## 📚 Dépendances

### Maven (pom.xml)

```xml
<dependencies>
    <!-- XChart pour graphiques -->
    <dependency>
        <groupId>org.knowm.xchart</groupId>
        <artifactId>xchart</artifactId>
        <version>3.8.1</version>
    </dependency>
    
    <!-- Py4J pour intégration Python (optionnel) -->
    <dependency>
        <groupId>net.sf.py4j</groupId>
        <artifactId>py4j</artifactId>
        <version>0.10.9.7</version>
    </dependency>
</dependencies>
```

---

## 🎯 Cas d'Usage

### 1. Publication Scientifique
- Générer baselines Java pour validation
- Comparer avec résultats Python
- Montrer compatibilité avec implémentation originale

### 2. Validation Croisée
- Vérifier cohérence Python/Java
- Tester différents scénarios
- Valider métriques

### 3. Simulation CloudSim
- Utiliser infrastructure Java
- Tester dans environnement réaliste
- Mesurer performance multi-cloud

---

## ⚠️ Notes Importantes

### Java est Optionnel

Le workflow principal est **100% Python**. Java est utilisé uniquement pour :
- Baselines de référence
- Validation croisée
- Compatibilité avec implémentation originale

### Py4J est Avancé

L'intégration Py4J est **complexe** et **optionnelle**. Pour la plupart des cas, utilisez le workflow Python pur.

### Maintenance

Le code Java est **stable** et **fonctionnel**. Il n'est pas nécessaire de le modifier sauf pour :
- Ajouter de nouvelles baselines
- Modifier paramètres existants
- Étendre fonctionnalités CloudSim

---

## 🚀 Démarrage Rapide

### Pour Utilisateurs Python (Recommandé)

```bash
# Ignorer Java complètement
./run_all_models.sh
```

### Pour Validation avec Java

```bash
# 1. Entraîner modèles Python
./run_all_models.sh

# 2. Compiler et exécuter Java
mvn clean package -DskipTests
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
    org.tcdrm.adaptive.examples.TcdrmArticleAllGraphs
```

---

## 📖 Documentation Complémentaire

- **Python RL** : `python_rl/README_QLEARNING_FORMAL.md`
- **Graphiques** : `python_rl/README_GRAPHS.md`
- **Implémentation** : `IMPLEMENTATION_SUMMARY.md`
- **Validation** : `VALIDATION_SPECIFICATION.md`

---

## ✅ Conclusion

Le code Java fournit une **infrastructure solide** pour les baselines et la validation, mais le **workflow principal est en Python**. Utilisez Java uniquement si vous avez besoin de :
- Générer baselines de référence
- Validation croisée Python/Java
- Simulation CloudSim avancée

Pour la plupart des cas, **utilisez uniquement Python** avec `./run_all_models.sh`.
