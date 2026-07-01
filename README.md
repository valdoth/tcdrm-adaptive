# TCDRM-Adaptive

**Cadre de réplication de données tenant-centrique pour le multi-cloud — extension par apprentissage par renforcement**

Ce dépôt étend la stratégie **TCDRM** originale (publiée dans le *Journal of Logistics, Informatics and Service Science*, Vol. 12, 2025) en y intégrant des agents d'apprentissage par renforcement (Q-Learning et Rainbow DQN) qui apprennent des politiques de réplication adaptatives directement à partir de simulations CloudSimPlus.

---

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Architecture](#architecture)
3. [Prérequis](#prérequis)
4. [Structure du projet](#structure-du-projet)
5. [Démarrage rapide](#démarrage-rapide)
6. [Configuration](#configuration)
7. [Agents RL](#agents-rl)
8. [Fonction de récompense](#fonction-de-récompense)
9. [Résultats et métriques](#résultats-et-métriques)

---

## Vue d'ensemble

**TCDRM** (Tenant-Centric Data Replication for Multi-Cloud) est une stratégie de réplication dynamique qui crée des réplicas de données lorsque :
- Le temps de réponse d'une requête `t_Q` dépasse le seuil SLA `T_SLA`, **ou**
- Le coût monétaire d'une requête `c_Q` dépasse le seuil budgétaire `C_SLA`,
- **et** la popularité de la donnée dépasse `P_SLA`.

Cette extension adaptative remplace le déclencheur à seuil fixe par des agents RL qui apprennent *quand* répliquer, *maintenir* ou *supprimer* des réplicas à partir du retour de la simulation en temps réel.

**Quatre stratégies sont comparées :**

| Stratégie | Description |
|---|---|
| **NoRepLc** | Pas de réplication — fournisseur le moins cher par requête (référence) |
| **TCDRM** | Réplication à seuil fixe (article original) |
| **Q-Learning** | RL tabulaire avec exploration ε-greedy |
| **Rainbow DQN** | RL profond distributionnel (C51 + NoisyLinear) |

---

## Architecture

Le système connecte un **moteur de simulation Java** et des **agents RL Python** via [Py4J](https://www.py4j.org/) :

```
┌─────────────────────────────────────────────────────────────┐
│                    PHASE D'ENTRAÎNEMENT                     │
│                                                             │
│  Python train_cloudsim.py  ←──Py4J──→  Java TrainingServer │
│  (Q-Learning / Rainbow DQN)              (CloudSimPlus)     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    PHASE DE BENCHMARK                       │
│                                                             │
│  Python connect_to_java.py ←──Py4J──→  Java TcdrmMain      │
│  (modèles chargés, RL en ligne)          (benchmark 4 mod.) │
└─────────────────────────────────────────────────────────────┘
```

**Côté Java** (`src/main/java/org/tcdrm/adaptive/`) :
- `TcdrmSimulation` — moteur de simulation multi-cloud (CloudSimPlus), exécute les requêtes, gère les réplicas
- `BenchmarkRunner` — exécute les 4 stratégies, calcule les récompenses, appelle Python via le bridge
- `TrainingServer` — passerelle Py4J pour la phase d'entraînement
- `ReplicaPlacementOptimizer` — placement heuristique sur 3 fournisseurs × 3 régions

**Côté Python** (`tcdrm_gym/`) :
- `agents/simple_qlearning_agent.py` — Q-Learning tabulaire
- `agents/rainbow_dqn_agent.py` — Rainbow DQN (C51 distributionnel + NoisyLinear)
- `bridge/client.py` — client Py4J, reçoit les vecteurs d'état, retourne les actions
- `train_cloudsim.py` — boucle d'entraînement (épisodes × requêtes)
- `connect_to_java.py` — bridge pour la phase de benchmark

---

## Prérequis

### Java
- **Java 17+**
- **Maven 3.8+**

### Python
- **Python 3.9+**
- **[uv](https://github.com/astral-sh/uv)** (gestionnaire de paquets, remplace pip/venv)

```bash
# Installation de uv (macOS/Linux)
curl -LsSf https://astral.sh/uv/install.sh | sh
```

### Dépendances Python (installées automatiquement via uv)
```
gymnasium, numpy, pandas, matplotlib, seaborn
py4j, torch, tqdm, pyyaml, tensorboard
```

---

## Structure du projet

```
tcdrm-adaptive/
│
├── run_complete_workflow.sh        # Point d'entrée principal — pipeline complet
│
├── src/main/java/org/tcdrm/adaptive/
│   ├── TcdrmMain.java              # Point d'entrée : Phase 1 (bases) + Phase 2 (RL)
│   ├── TcdrmMainArgs.java          # Analyse des arguments CLI
│   ├── core/
│   │   ├── TcdrmConstants.java     # Tous les paramètres de simulation (SLA, coûts, limites)
│   │   └── RuntimeConfig.java      # Surcharges à l'exécution (nb requêtes, etc.)
│   ├── simulation/
│   │   ├── TcdrmSimulation.java    # Moteur de simulation multi-cloud principal
│   │   └── ReplicaPlacementOptimizer.java  # Placement heuristique des réplicas
│   ├── cloudsim/
│   │   ├── DataFragment.java       # Fragment de relation (primaire + jusqu'à 2 réplicas)
│   │   ├── MultiCloudInfrastructure.java   # Topologie fournisseurs/régions/VMs
│   │   └── QueryCloudlet.java      # Cloudlet CloudSim pour l'exécution des requêtes
│   ├── benchmark/
│   │   ├── BenchmarkRunner.java    # Benchmark 4 modèles + récompense RL en ligne
│   │   ├── BenchmarkData.java      # Stockage des métriques par requête
│   │   ├── BenchmarkExporter.java  # Export CSV
│   │   └── ChartGenerator.java     # Génération des figures (JFreeChart)
│   ├── training/
│   │   ├── TrainingServer.java     # Passerelle Py4J pour l'entraînement
│   │   └── TrainingEnvironment.java # Step/reset/récompense pour la boucle d'entraînement
│   ├── rl/
│   │   └── PythonRLBridge.java     # Interface bridge Py4J côté Java
│   └── gateway/
│       └── Py4JGateway.java        # Cycle de vie du serveur Py4J
│
├── tcdrm_gym/
│   ├── train_cloudsim.py           # Script d'entraînement RL (Q-Learning + Rainbow DQN)
│   ├── connect_to_java.py          # Bridge phase benchmark (charge les modèles entraînés)
│   ├── config.yml                  # Hyperparamètres d'entraînement
│   ├── agents/
│   │   ├── simple_qlearning_agent.py   # Q-Learning tabulaire
│   │   ├── rainbow_dqn_agent.py        # Rainbow DQN (C51 + NoisyLinear)
│   │   └── dqn_agent.py                # DQN classique (non utilisé dans le benchmark)
│   ├── envs/
│   │   └── cloudsim_env.py         # Wrapper Gymnasium autour de la simulation Java
│   ├── bridge/
│   │   ├── client.py               # Client Py4J + chargement des modèles
│   │   ├── rl_bridge.py            # Encodage des états / décodage des actions
│   │   └── adaptive_strategy.py    # Sélecteur de stratégie
│   ├── models/                     # Poids des modèles sauvegardés
│   │   ├── qlearning_cloudsim.pkl      # Q-Table (utilisé par le benchmark)
│   │   ├── qlearning_cloudsim_final.pkl
│   │   ├── rainbow_cloudsim.pt         # Poids Rainbow DQN (utilisé par le benchmark)
│   │   └── rainbow_cloudsim_final.pt
│   └── logs/                       # Journaux TensorBoard (entraînement)
│
├── metrics/                        # Sorties CSV du benchmark
│   ├── norep_simple.csv / norep_complex.csv
│   ├── tcdrm_simple.csv / tcdrm_complex.csv
│   ├── rl_qlearning_simple.csv / rl_qlearning_complex.csv
│   ├── rl_rainbow_simple.csv / rl_rainbow_complex.csv
│   └── summary_phase2_rl.csv       # Statistiques agrégées
│
├── images/                         # Figures générées (PNG)
│   ├── fig1_replica_factor_4models.png
│   ├── fig2_response_time_4models.png
│   ├── fig3_bw_consumption_4models.png
│   ├── fig4_avg_bw_price_4models.png
│   ├── fig5_cumulative_bw_price_4models.png
│   └── fig6_total_cost_4models.png
│
├── paper/
│   └── TCDRM_Paper.md              # Article complet (version Markdown)
│
├── docs/
│   └── diagrams/                   # Diagrammes d'architecture
│
├── pom.xml                         # Fichier de build Maven
└── validation/                     # Données de validation de référence (exécutions précédentes)
```

---

## Démarrage rapide

### Pipeline complet (entraînement + simulation + génération des figures)

```bash
./run_complete_workflow.sh
```

Ce script exécute trois étapes automatiquement :
1. **Entraînement** des modèles Q-Learning et Rainbow DQN via CloudSimPlus (défaut : 100 épisodes)
2. **Compilation** du projet Java (Maven)
3. **Benchmark** des 4 stratégies sur 1 000 requêtes et génération des figures de comparaison

### Options courantes

```bash
# Passer l'entraînement (utiliser les modèles existants)
./run_complete_workflow.sh --skip-training

# Entraînement prolongé (plus d'épisodes)
./run_complete_workflow.sh --episodes 200

# Entraînement uniquement (sans benchmark)
./run_complete_workflow.sh --skip-simulation

# Passer la compilation (JAR déjà construit)
./run_complete_workflow.sh --skip-compile --skip-training
```

### Consulter les résultats

```bash
open images/*.png        # macOS
xdg-open images/*.png    # Linux
```

TensorBoard est lancé automatiquement pendant l'entraînement :
```
http://localhost:6006
```

---

## Configuration

### Paramètres SLA (`TcdrmConstants.java`)

| Paramètre | Valeur | Description |
|---|---|---|
| `TSLA_SIMPLE_MS` | 200 ms | Seuil de temps de réponse SLA pour les requêtes simples |
| `TSLA_COMPLEX_MS` | 400 ms | Seuil de temps de réponse SLA pour les requêtes complexes |
| `CSLA_SIMPLE` | 0,015 $ | Seuil de coût SLA par requête simple |
| `CSLA_COMPLEX` | 0,040 $ | Seuil de coût SLA par requête complexe |
| `P_SLA` | 200 | Seuil de popularité (déclencheur TCDRM) |
| `MIN_POPULARITY_TO_REPLICATE` | 0,3 | Portail RL : fraction de P_SLA avant que la réplication RL soit autorisée |
| `MAX_QUERIES` | 1 000 | Nombre de requêtes par exécution de simulation |
| `INITIAL_BUDGET` | 60,00 $ | Budget total du locataire |

### Infrastructure (`TcdrmConstants.java`)

| Paramètre | Valeur | Description |
|---|---|---|
| Fournisseurs | 3 | Google Cloud, AWS, Azure |
| Régions par fournisseur | 3 | US, EU, AS |
| VMs par région | 20 | — |
| Taille d'une relation | 450 Mo | Taille moyenne d'une relation |
| Relations par requête simple | 3 | 1 relation par région |
| Relations par requête complexe | 6 | 2 relations par région |
| `MAX_REPLICAS_SIMPLE` | 6 | 3 fragments × 2 réplicas chacun |
| `MAX_REPLICAS_COMPLEX` | 12 | 6 fragments × 2 réplicas chacun |

### Coûts de bande passante

| Type | Coût |
|---|---|
| Intra-datacenter | 0,001 $/Go |
| Inter-région (même fournisseur) | 0,008 $/Go |
| Inter-fournisseur | 0,010 $/Go |

### Hyperparamètres d'entraînement (`tcdrm_gym/config.yml`)

Les paramètres clés de chaque agent sont définis dans `config.yml`. Les plus importants :

| Paramètre | Q-Learning | Rainbow DQN |
|---|---|---|
| Taux d'apprentissage | 0,1 | 1e-4 |
| Facteur d'actualisation (γ) | 0,99 | 0,99 |
| Exploration (ε) | 0,999 (évaluation) | NoisyLinear |
| Architecture | Q-table | C51 + NoisyLinear |

---

## Agents RL

### Q-Learning

Q-Learning tabulaire avec exploration ε-greedy, entraîné directement via CloudSimPlus.

### Rainbow DQN

Implémentation complète Rainbow combinant C51 (estimation de valeur distributionnelle) et NoisyLinear (exploration intrinsèque). Rainbow DQN apprend une politique conservative — il réplique jusqu'à ce que le SLA soit respecté, puis s'arrête.

---

## Fonction de récompense

La récompense est calculée après chaque requête et reproduit exactement `TrainingEnvironment.calculateReward()` pour éviter toute divergence entraînement/évaluation :

```
R = r1·SLA_OK − r2·SLA_VIOL − r3·DEPASS_COUT(urgence)
  − r4·COUT_REPL − r5·FAIBLE_POP − r6·REPL_PREMATUREE
  + r7·DECLENCHEMENT_CORRECT − r8·THRASHING − r9·MAINTENANCE − r10·ACTION_INVALIDE
```

| Composante | Poids | Description |
|---|---|---|
| `SLA_OK` | +10 | Proportionnel à la marge en dessous de T_SLA |
| `SLA_VIOL` | −20 | Proportionnel au dépassement de T_SLA |
| `DEPASS_COUT` | −15 × urgence | Dépassement de coût, pondéré par l'épuisement du budget |
| `COUT_REPL` | −5 × coût_donnée | Coût BW de la création d'un réplica |
| `FAIBLE_POP` | −5 × (1 − pop) | Pénalité pour réplication de données peu populaires |
| `REPL_PREMATUREE` | −5 × marge | Pénalité pour réplication quand le SLA est respecté |
| `DECLENCHEMENT_CORRECT` | +8 | Bonus pour réplication quand le SLA est violé et popularité ≥ 0,3 |
| `THRASHING` | −8 | Pénalité pour alternances rapides REPLICATE/DELETE |
| `MAINTENANCE` | −0,01 × réplicas | Coût de maintenance léger par réplica actif |
| `ACTION_INVALIDE` | −2 | Action invalide (ex. répliquer au-delà de la capacité maximale) |

Le bonus `DECLENCHEMENT_CORRECT` se déclenche dès l'ouverture du portail de popularité (requête 60), et non à P_SLA=200, garantissant que l'agent reçoit un signal positif dès la première fenêtre de réplication possible.

---

## Résultats et métriques

Les figures générées sont sauvegardées dans `images/` :

| Figure | Contenu |
|---|---|
| `fig1_replica_factor_4models.png` | Évolution du nombre de réplicas (simple + complexe) |
| `fig2_response_time_4models.png` | Évolution du temps de réponse |
| `fig3_bw_consumption_4models.png` | Consommation BW inter-fournisseur vs inter-région |
| `fig4_avg_bw_price_4models.png` | Coût BW moyen par requête |
| `fig5_cumulative_bw_price_4models.png` | Coût BW cumulé |
| `fig6_total_cost_4models.png` | Décomposition du coût total (CPU + BW + réplica) |

Les données brutes par requête sont exportées dans `metrics/` sous forme de fichiers CSV pour une analyse approfondie.

