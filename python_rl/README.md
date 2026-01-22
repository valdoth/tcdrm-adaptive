# TCDRM-ADAPTIVE v2.0: Mécanisme Adaptatif Auto-Apprenant

**Conception d'un mécanisme adaptatif et auto-apprenant pour la réplication de bases de données orientée budget en environnement multi-cloud**

---

## 🎯 Objectif Scientifique

Transformer TCDRM d'un modèle à **seuils statiques** vers un **framework adaptatif**, capable d'**apprendre automatiquement** les décisions de réplication et de suppression de réplicas tout en respectant le budget du locataire.

### Problématique

**Comment remplacer des seuils SLA fixes par des politiques de décision auto-apprenantes afin d'améliorer la performance et la stabilité budgétaire dans un environnement multi-cloud dynamique?**

---

## 🔬 Axes de Travail

1. **Modélisation du processus de décision** comme un problème séquentiel (MDP)
2. **Définition formelle** des états, actions et fonction de récompense
3. **Intégration Q-Learning tabulaire** pour l'apprentissage automatique
4. **Ajustement dynamique** des décisions de réplication (remplace TSLA, CSLA, PSLA)
5. **Validation expérimentale** par simulation multi-cloud (CloudSim)

---

## 🛠️ Techniques Mobilisées

### Apprentissage par Renforcement

- **Q-Learning tabulaire** pour environnements simulés
- Apprentissage en ligne avec mise à jour continue des politiques

### Processus de Décision Markovien (MDP)

- Modélisation formelle des décisions de réplication
- États: Budget, Latence, Popularité, Nombre de réplicas
- Actions: CREATE_REPLICA, DELETE_REPLICA, DO_NOTHING

### Optimisation sous Contraintes

- Respect strict du budget du locataire
- Conformité SLA (latence < 150ms)
- Minimisation des coûts de réplication

### Simulation Multi-Cloud

- CloudSim étendu avec suivi dynamique des coûts
- Intégration Python-Java via Py4J

---

## 📊 Résultats Attendus

✅ **Décisions de réplication plus stables** que les seuils fixes  
✅ **Réduction des coûts inutiles** liés aux réplicas  
✅ **Adaptation automatique** aux patterns d'accès  
✅ **Contribution directe** à TCDRM v2.0

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    TCDRM-ADAPTIVE v2.0                      │
│            Mécanisme Adaptatif Auto-Apprenant               │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │   Q-Learning Agent (Python)           │
        │   - Q-Table (108 états × 3 actions)   │
        │   - ε-greedy exploration              │
        │   - Bellman update                    │
        └───────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │   Gymnasium Environment               │
        │   - État: [Budget, Latence, Pop, Rep] │
        │   - Actions: CREATE/DELETE/NOTHING    │
        │   - Récompense: Coût + SLA + Budget   │
        └───────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────────────────────────┐
        │   CloudSim Integration (Py4J)         │
        │   - Simulation réaliste multi-cloud   │
        │   - Métriques détaillées              │
        └───────────────────────────────────────┘
```

---

## 🚀 Installation

### Prérequis

- Python 3.9+
- Java 11+ (pour CloudSim)
- Maven 3.6+ (pour compilation Java)
- UV (gestionnaire de paquets Python)

### Installation Rapide

```bash
cd python_rl

# Installer les dépendances avec UV
uv sync

# Ou avec pip
pip install -e .
```

---

## 📖 Utilisation

### 1. Entraînement Q-Learning (Python Standalone)

```bash
# Entraînement avec configuration par défaut
uv run python train_qlearning.py

# Entraînement avec paramètres personnalisés
uv run python train_qlearning.py \
  --episodes 1000 \
  --data-gb 5.3 \
  --alpha 0.1 \
  --gamma 0.95 \
  --epsilon 1.0
```

**Résultat**: Modèle sauvegardé dans `models/qlearning_tcdrm/`

### 2. Comparaison Python vs Java

```bash
# Comparer l'implémentation Python avec l'implémentation Java existante
uv run python compare_with_java.py \
  --java-log ../logs/qlearning_training.log \
  --episodes 500
```

**Résultat**: Graphiques de comparaison dans `results/python_vs_java/`

### 3. Intégration avec CloudSim (Java)

#### Étape 1: Compiler le projet Java

```bash
cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive
mvn clean package
```

#### Étape 2: Démarrer le Gateway Java

```bash
# Terminal 1: Gateway Java
java -cp target/tcdrm-adaptive-1.0.0-SNAPSHOT-with-dependencies.jar \
  org.tcdrm.adaptive.bridge.Py4JGateway
```

#### Étape 3: Tester la connexion

```bash
# Terminal 2: Test Python
cd python_rl
uv run python test_java_connection.py
```

#### Étape 4: Entraîner avec l'environnement Java

```bash
# Entraîner Q-Learning avec l'environnement Java CloudSim
uv run python train_qlearning.py --use-java-env
```

### 4. Évaluation du Modèle

```bash
# Évaluer un modèle entraîné
uv run python evaluate.py \
  --model models/qlearning_tcdrm/best_model.pkl \
  --episodes 100 \
  --render
```

---

## 📁 Structure du Projet

```
python_rl/
├── agents/
│   ├── __init__.py
│   └── tabular_qlearning.py      # Agent Q-Learning principal
├── envs/
│   ├── __init__.py
│   └── tcdrm_env.py               # Environnement Gymnasium TCDRM
├── utils/
│   ├── __init__.py
│   ├── logger.py                  # Logging
│   ├── metrics.py                 # Calcul des métriques
│   └── visualization.py           # Visualisation des résultats
├── configs/
│   └── qlearning_config.yaml      # Configuration Q-Learning
├── train_qlearning.py             # Script d'entraînement
├── evaluate.py                    # Script d'évaluation
├── compare_with_java.py           # Comparaison Python vs Java
├── test_java_connection.py        # Test intégration Py4J
└── README.md                      # Ce fichier
```

---

## 🧪 Expérimentations

### Configuration de Base

- **Espace d'états**: 108 états (3×3×3×4)
  - Budget: {LOW, MEDIUM, HIGH}
  - Latence: {LOW, MEDIUM, HIGH}
  - Popularité: {LOW, MEDIUM, HIGH}
  - Réplicas: {0, 1, 2, 3}

- **Espace d'actions**: 3 actions
  - CREATE_REPLICA (0)
  - DELETE_REPLICA (1)
  - DO_NOTHING (2)

- **Fonction de récompense**:
  ```
  R = Bonus_SLA + Économies_BW - Pénalité_Budget - Pénalité_Latence
  ```

### Hyperparamètres Q-Learning

```yaml
alpha: 0.1 # Taux d'apprentissage
gamma: 0.95 # Facteur de discount
epsilon_start: 1.0 # Exploration initiale
epsilon_decay: 0.995 # Décroissance de l'exploration
epsilon_min: 0.01 # Exploration minimale
```

### Scénarios de Test

1. **Requête Simple (R1)**: 5.3 GB
2. **Requête Complexe (R2)**: 11.9 GB
3. **Requête Volumineuse (R3)**: 20 GB

---

## 📊 Métriques d'Évaluation

### Métriques Principales

- **Récompense moyenne** par épisode
- **Taux de conformité SLA** (latence < 150ms)
- **Coût total** (réplication + stockage + transfert)
- **Stabilité budgétaire** (variance du budget)
- **Nombre de réplicas moyen**

### Comparaison avec Baseline

- **Baseline**: Seuils statiques TCDRM v1.0
- **Métrique**: Amélioration en % du coût et de la conformité SLA

---

## 🔬 Validation Scientifique

### Hypothèses à Valider

1. **H1**: Q-Learning surpasse les seuils statiques en termes de coût
2. **H2**: Q-Learning maintient une meilleure conformité SLA
3. **H3**: Q-Learning s'adapte aux changements de patterns d'accès
4. **H4**: Q-Learning converge vers une politique stable

### Protocole Expérimental

1. Entraîner Q-Learning sur 1000 épisodes
2. Comparer avec Q-Learning Java existant
3. Évaluer sur 100 épisodes de test
4. Mesurer: Coût, SLA, Stabilité, Convergence
5. Analyse statistique (t-test, ANOVA)

---

## 📈 Visualisations

Le système génère automatiquement:

- **Courbes d'apprentissage** (récompense vs épisode)
- **Heatmap de la Q-Table** (états × actions)
- **Distribution des actions** prises
- **Évolution du budget** au cours du temps
- **Conformité SLA** par épisode
- **Comparaison Python vs Java**

---

## 🔗 Intégration Java (Py4J)

### Avantages de l'Intégration

✅ Utilise l'environnement Java CloudSim existant  
✅ Simulations multi-cloud réalistes  
✅ Métriques détaillées (CPU, RAM, réseau)  
✅ Compatibilité avec le code TCDRM existant

### Architecture Py4J

```
Python Q-Learning Agent
         │
         │ Py4J (TCP Socket)
         │
         ▼
Java Py4JGateway
         │
         ▼
TcdrmEnvironment (Java)
         │
         ▼
CloudSim Simulation
```

### Script de Lancement Automatisé

```bash
# Workflow complet: Compile → Démarre Gateway → Test → Entraîne
cd /Users/valdo/Desktop/cloud/avancement/tcdrm-adaptive
./launch_python_java_integration.sh full
```

---

## 📚 Documentation Complémentaire

- **`JAVA_INTEGRATION_GUIDE.md`**: Guide complet d'intégration Python-Java
- **`QUICK_START_INTEGRATION.md`**: Démarrage rapide de l'intégration
- **`USAGE_GUIDE.md`**: Guide d'utilisation détaillé
- **`configs/qlearning_config.yaml`**: Configuration complète

---

## 🎓 Contribution Scientifique

### Innovation Principale

**Remplacement des seuils statiques (TSLA, CSLA, PSLA) par un mécanisme adaptatif auto-apprenant basé sur Q-Learning tabulaire.**

### Contributions Spécifiques

1. **Modélisation MDP** du problème de réplication multi-cloud
2. **Fonction de récompense** intégrant coût, SLA et budget
3. **Intégration Gymnasium** pour standardisation
4. **Pont Python-Java** pour validation CloudSim
5. **Protocole expérimental** reproductible

### Publications Visées

- Conférence: IEEE Cloud Computing, ACM SIGMOD
- Journal: IEEE Transactions on Cloud Computing

---

## 🤝 Équipe

**TCDRM Research Team**  
Université / Institution  
Contact: tcdrm@example.com

---

## 📄 Licence

MIT License - Voir LICENSE pour plus de détails

---

## 🚀 Quick Start

```bash
# 1. Installation
cd python_rl
uv sync

# 2. Entraînement Q-Learning
uv run python train_qlearning.py --episodes 1000

# 3. Évaluation
uv run python evaluate.py --model models/qlearning_tcdrm/best_model.pkl

# 4. Comparaison avec Java
uv run python compare_with_java.py --java-log ../logs/qlearning_training.log

# 5. Intégration CloudSim (optionnel)
cd ..
./launch_python_java_integration.sh full
```

---

## 📞 Support

Pour toute question ou problème:

1. Consultez la documentation dans `USAGE_GUIDE.md`
2. Vérifiez les issues GitHub
3. Contactez l'équipe TCDRM

---

**TCDRM-ADAPTIVE v2.0 - Vers des décisions de réplication intelligentes et adaptatives! 🎯**
