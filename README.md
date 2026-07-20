# TCDRM-Adaptive

[![Java](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Python](https://img.shields.io/badge/Python-3.9%2B-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![CloudSim Plus](https://img.shields.io/badge/CloudSim%20Plus-simulation-orange)](https://cloudsimplus.org/)
[![PyTorch](https://img.shields.io/badge/PyTorch-Rainbow%20DQN-EE4C2C?logo=pytorch&logoColor=white)](https://pytorch.org/)
[![DOI](https://img.shields.io/badge/DOI-10.33168%2FJLISS.2025.0315-blue)](https://doi.org/10.33168/JLISS.2025.0315)

**Conception d'un mécanisme adaptatif et auto-apprenant pour la réplication de bases de
données orientée budget en environnement multi-cloud.**

Le point de départ est la stratégie **TCDRM**
([Bernardin et al., JLISS 2025](https://doi.org/10.33168/JLISS.2025.0315)), dont les seuils de
réplication sont fixés *a priori* par l'administrateur. Ce dépôt lui substitue un mécanisme qui
**apprend seul quand répliquer**, sous contrainte du budget du locataire, à partir de
simulations CloudSimPlus — et l'évalue face à cette référence.

- **Auto-apprenant à deux niveaux** — un agent RL décide à chaque requête de répliquer, maintenir ou supprimer un réplica ; en parallèle, des méta-contrôleurs Q-learning apprennent la **valeur des seuils** eux-mêmes (T_SLA, éligibilité popularité, fenêtre d'observation ΔT), qui ne sont donc jamais codés en dur ([Seuils adaptatifs](#seuils-adaptatifs)).
- **Orienté budget** — le budget n'est pas une métrique observée après coup mais une contrainte interne à la décision : coût par requête plafonné par `C_SLA`, enveloppe globale `INITIAL_BUDGET`, et pénalité pondérée par l'**urgence budgétaire**, qui double à mesure que l'enveloppe s'épuise ([Fonction de récompense](#fonction-de-récompense)).
- **Adaptatif à la charge** — la popularité des données évolue en cours d'exécution (stable, dérive Zipf, pics) ; la politique s'y ajuste sans reconfiguration ([Régimes de charge](#régimes-de-charge)).
- **Évaluation à protocole égal** — les quatre stratégies partagent seed, requêtes et poids de récompense, pour que l'écart mesuré vienne de la politique et non du tirage ([Reproductibilité](#reproductibilité)).

---

## Table des matières

1. [Démarrage rapide](#démarrage-rapide)
2. [Reproductibilité](#reproductibilité)
3. [Vue d'ensemble](#vue-densemble)
4. [Architecture](#architecture)
5. [Structure du projet](#structure-du-projet)
6. [Configuration](#configuration)
7. [Seuils adaptatifs](#seuils-adaptatifs)
8. [Régimes de charge](#régimes-de-charge)
9. [Agents RL](#agents-rl)
10. [Fonction de récompense](#fonction-de-récompense)
11. [Métriques et sorties](#métriques-et-sorties)
12. [Dépannage](#dépannage)
13. [Citation](#citation)

---

## Démarrage rapide

### Prérequis

| | Version | Notes |
| --- | --- | --- |
| Java | 17+ | avec Maven 3.8+ |
| Python | 3.9+ | via [uv](https://github.com/astral-sh/uv) |

```bash
# uv (macOS/Linux) — gère l'environnement et les dépendances Python
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Les dépendances Python (`gymnasium`, `torch`, `py4j`, `numpy`, `pandas`, `matplotlib`,
`tensorboard`…) sont installées automatiquement au premier lancement.

### Pipeline complet

```bash
./run_complete_workflow.sh
```

Trois étapes enchaînées : **entraînement** des agents via CloudSimPlus (300 épisodes par
défaut) → **compilation** Maven → **benchmark** des 4 stratégies sur 1 000 requêtes et
génération des figures.

> **Nombre d'épisodes** — Rainbow DQN part d'un réseau neuf et exige **≥ 300 épisodes** ;
> en dessous, le script avertit et les figures ne font pas foi. Les exécutions de référence
> du dépôt ont été produites avec `--episodes 1500` (compter plusieurs heures).

### Options

```bash
./run_complete_workflow.sh --skip-training              # réutiliser les modèles existants
./run_complete_workflow.sh --episodes 1500              # configuration des exécutions de référence
./run_complete_workflow.sh --workload variable          # forcer un régime de charge partout
./run_complete_workflow.sh --skip-simulation            # entraînement seul
./run_complete_workflow.sh --skip-compile --skip-training
```

### Consulter les sorties

```bash
open images/*.png        # macOS  (xdg-open sous Linux)
```

TensorBoard est lancé automatiquement pendant l'entraînement : <http://localhost:6006>.

---

## Reproductibilité

Le benchmark **apprend en ligne** : les agents continuent de mettre à jour leurs poids pendant
l'évaluation. Sans contrôle strict de l'aléa, deux exécutions ne sont pas comparables — un
classement inter-modèles observé une fois peut ne pas se reproduire.

### Garanties du protocole

- **Même seed, mêmes requêtes** pour les quatre stratégies au sein d'un run : l'équité inter-modèles est structurelle, pas statistique.
- **C_SLA statique** sur tout le run — le contrat budgétaire n'est pas une variable apprise.
- **Poids de récompense identiques** entre agents, et entre entraînement et évaluation : ils sont écrits dans `reward_config_<agent>.properties` à l'entraînement, puis rechargés au benchmark.
- **Tous les générateurs sont seedés** (`np.random`, `torch`, compteurs Java) à chaque reset.

### Deux régimes d'évaluation distincts

| | Commande | Workload | Seed | Objectif |
| --- | --- | --- | --- | --- |
| Benchmark | `./run_complete_workflow.sh` | `steady` | 42 | Protocole de l'article : la même requête répétée 1 000 fois |
| Validation | `./validation/run.sh comparison` | `variable` | 777 | **Généralisation** : requêtes variées, hot-set qui dérive, seed jamais vue à l'entraînement |

Les deux se surchargent par variables d'environnement :

```bash
TCDRM_WORKLOAD=burst TCDRM_SEED=888 ./validation/run.sh comparison
```

> **Limite connue** — `pom.xml` déclare CloudSim Plus en version `LATEST`. Les mesures ne
> sont donc pas garanties à l'identique dans le temps ; épingler une version précise avant
> toute soumission.

---

## Vue d'ensemble

**TCDRM** (*Tenant-Centric Data Replication for Multi-Cloud*) crée un réplica lorsque :

- le temps de réponse `t_Q` dépasse le seuil SLA `T_SLA`, **ou**
- le coût monétaire `c_Q` dépasse le seuil budgétaire `C_SLA`,
- **et** la popularité de la donnée dépasse `P_SLA`.

Quatre stratégies sont comparées :

| Stratégie | Description |
| --- | --- |
| **NoRepLc** | Pas de réplication — fournisseur le moins cher par requête (plancher) |
| **TCDRM** | Réplication à seuils fixes (article original) |
| **Q-Learning** | RL tabulaire, ε-greedy, avec méta-seuils appris |
| **Rainbow DQN** | RL profond — Double + Dueling + PER + n-step + NoisyLinear + C51 |

---

## Architecture

Un **moteur de simulation Java** et des **agents RL Python** communiquent via
[Py4J](https://www.py4j.org/) :

```text
┌─────────────────────────────────────────────────────────────┐
│                    PHASE D'ENTRAÎNEMENT                     │
│                                                             │
│  Python train_cloudsim.py  ←──Py4J──→  Java TrainingServer  │
│  (Q-Learning / Rainbow DQN)              (CloudSimPlus)     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    PHASE DE BENCHMARK                       │
│                                                             │
│  Python connect_to_java.py ←──Py4J──→  Java TcdrmMain       │
│  (modèles chargés, RL en ligne)          (benchmark 4 mod.) │
└─────────────────────────────────────────────────────────────┘
```

**Côté Java** — `src/main/java/org/tcdrm/adaptive/`

| Classe | Rôle |
| --- | --- |
| `TcdrmSimulation` | Moteur multi-cloud (CloudSimPlus) : exécution des requêtes, gestion des réplicas |
| `BenchmarkRunner` | Exécute les 4 stratégies, calcule les récompenses, appelle Python |
| `TrainingServer` / `TrainingEnvironment` | Passerelle Py4J et boucle step/reset/récompense |
| `ThresholdMetaLearner` | Méta-contrôleur Q-learning : **apprend la valeur des seuils** |
| `PopularityWorkloads` | Régimes de charge (steady / variable Zipf / burst) |
| `ReplicaPlacementOptimizer` | Placement heuristique sur 3 fournisseurs × 3 régions |

**Côté Python** — `tcdrm_gym/`

| Module | Rôle |
| --- | --- |
| `agents/simple_qlearning_agent.py` | Q-Learning tabulaire |
| `agents/rainbow_dqn_agent.py` | Rainbow DQN (6 composants) |
| `bridge/client.py` | Client Py4J : reçoit les états, retourne les actions |
| `train_cloudsim.py` | Boucle d'entraînement (épisodes × requêtes) |
| `connect_to_java.py` | Bridge de la phase de benchmark |

> Les décisions **d'action** sont prises côté Python par l'agent ; les décisions **de seuil**
> côté Java par les méta-contrôleurs, dont les Q-tables sont persistées entre l'entraînement
> et le benchmark.

---

## Structure du projet

```text
tcdrm-adaptive/
│
├── run_complete_workflow.sh        # Point d'entrée principal — pipeline complet
│
├── src/main/java/org/tcdrm/adaptive/
│   ├── TcdrmMain.java              # Phase 1 (références) + Phase 2 (RL)
│   ├── TcdrmMainArgs.java          # Analyse des arguments CLI
│   ├── core/
│   │   ├── TcdrmConstants.java     # Paramètres de simulation (SLA, coûts, limites)
│   │   └── RuntimeConfig.java      # Surcharges à l'exécution
│   ├── simulation/
│   │   ├── TcdrmSimulation.java    # Moteur de simulation multi-cloud
│   │   └── ReplicaPlacementOptimizer.java
│   ├── cloudsim/
│   │   ├── DataFragment.java       # Fragment (primaire + jusqu'à 2 réplicas)
│   │   ├── MultiCloudInfrastructure.java   # Topologie fournisseurs/régions/VMs
│   │   └── QueryCloudlet.java      # Cloudlet d'exécution de requête
│   ├── data/
│   │   ├── PopularityWorkloads.java        # Régimes steady / variable / burst
│   │   └── LegacyWorkloadTemplates.java    # Motifs de l'article original
│   ├── benchmark/
│   │   ├── BenchmarkRunner.java    # Benchmark 4 modèles + récompense en ligne
│   │   ├── BenchmarkData.java      # Métriques par requête
│   │   ├── BenchmarkExporter.java  # Export CSV
│   │   └── ChartGenerator.java     # Figures (JFreeChart)
│   ├── training/
│   │   ├── TrainingServer.java     # Passerelle Py4J
│   │   ├── TrainingEnvironment.java # Step/reset/récompense
│   │   └── TrainingSettings.java   # Poids de récompense (persistés)
│   ├── rl/
│   │   ├── PythonRLBridge.java     # Interface bridge côté Java
│   │   └── ThresholdMetaLearner.java # Méta-contrôleur Q-Threshold
│   ├── api/TcdrmAdapter.java       # Façade d'intégration
│   └── gateway/Py4JGateway.java    # Cycle de vie du serveur Py4J
│
├── tcdrm_gym/
│   ├── train_cloudsim.py           # Entraînement RL
│   ├── connect_to_java.py          # Bridge benchmark
│   ├── config.yml                  # Hyperparamètres
│   ├── agents/ envs/ bridge/       # Agents, wrapper Gymnasium, bridge Py4J
│   ├── models/                     # Modèles + Q-tables de méta-seuils
│   │   ├── qlearning_cloudsim.pkl                        # Q-table de l'agent
│   │   ├── rainbow_cloudsim.pt                           # Poids Rainbow (non versionné)
│   │   ├── meta_threshold_<agent>_{pop,tsla,delw}_<régime>.qtable
│   │   └── reward_config_<agent>.properties              # Parité train/éval
│   └── logs/                       # Journaux TensorBoard
│
├── metrics/                        # Sorties CSV du benchmark
├── images/                         # Figures générées (PNG)
├── validation/                     # Régime de validation + résultats de référence
├── scripts/run_full_simulation.sh  # Benchmark seul
├── paper/TCDRM_Paper.md            # Article original (Markdown)
├── docs/diagrams/                  # Diagrammes d'architecture
└── pom.xml
```

> **Non versionné** — `*.pt` (points de contrôle Rainbow, ~50 Mo) et `*.log` sont exclus par
> `.gitignore` : ils dépassaient la limite de 100 Mo par fichier de GitHub. Ils sont régénérés
> par le pipeline. Les Q-tables (`.qtable`, `.pkl`), petites et textuelles, restent versionnées.

---

## Configuration

### Paramètres SLA — `TcdrmConstants.java`

| Paramètre | Valeur | Description |
| --- | --- | --- |
| `TSLA_SIMPLE_MS` | 200 ms | Seuil de temps de réponse, requêtes simples |
| `TSLA_COMPLEX_MS` | 400 ms | Seuil de temps de réponse, requêtes complexes |
| `CSLA_SIMPLE` | 0,015 $ | Seuil de coût par requête simple |
| `CSLA_COMPLEX` | 0,040 $ | Seuil de coût par requête complexe |
| `P_SLA` | 200 | Seuil de popularité contractuel |
| `POPULARITY_EMA_LAMBDA` | — | Lissage EMA, calé pour atteindre 1,0 à `P_SLA` accès |
| `MAX_QUERIES` | 1 000 | Requêtes par exécution |
| `INITIAL_BUDGET` | 60,00 $ | Budget total du locataire |

> Pour les stratégies RL, ces seuils sont des **valeurs contractuelles de départ**, pas des
> constantes de décision. L'ancien portail statique `MIN_POPULARITY_TO_REPLICATE` (0,3) a été
> **supprimé** au profit de l'éligibilité apprise.

### Infrastructure

| Paramètre | Valeur |
| --- | --- |
| Fournisseurs × régions | 3 (Google, AWS, Azure) × 3 (US, EU, AS) |
| VMs par région | 20 |
| Taille moyenne d'une relation | 450 Mo |
| Relations par requête simple / complexe | 3 / 6 |
| `MAX_REPLICAS_SIMPLE` / `_COMPLEX` | 6 / 12 |

### Coûts de bande passante

| Type | Coût |
| --- | --- |
| Intra-datacenter | 0,001 $/Go |
| Inter-région (même fournisseur) | 0,008 $/Go |
| Inter-fournisseur | 0,010 $/Go |

### Hyperparamètres — `tcdrm_gym/config.yml`

| Paramètre | Q-Learning | Rainbow DQN |
| --- | --- | --- |
| Taux d'apprentissage | 0,1 | 1e-4 (scheduler cosine) |
| Facteur d'actualisation γ | 0,99 | 0,99 |
| Exploration | ε-greedy | NoisyLinear (σ₀ = 0,5) |
| Architecture | Q-table | 2 × 128, C51 (51 atomes, V ∈ [−15, 6]) |
| Replay | — | PER, capacité 1e5, n-step = 3 |

---

## Seuils adaptatifs

Aucune valeur de seuil n'est codée en dur pour les stratégies RL. Trois méta-contrôleurs
**Q-Threshold** (`ThresholdMetaLearner`) apprennent chacun la valeur d'un seuil :

| Seuil | Fichier | Intervalle | Rôle |
| --- | --- | --- | --- |
| Éligibilité popularité | `*_pop_*.qtable` | [0, 1], pas 0,10 | À partir de quelle popularité une donnée est réplicable |
| Multiplicateur T_SLA | `*_tsla_*.qtable` | [0,60, 1], pas 0,05 | Resserrement du seuil de latence effectif |
| Fenêtre ΔT | `*_delw_*.qtable` | [0,05, 1], pas 0,05 | Durée d'observation avant suppression (Algorithme 3) |

### Principes

- **Décision à chaque requête** — aucune cadence fixe ni fenêtre de N requêtes : le *moment* d'un changement de seuil est lui-même appris.
- **Sélection directe du niveau** — le contrôleur passe de n'importe quel niveau à n'importe quel autre ; il n'existe pas de règle « si stress alors ±X % ».
- **État** : (bucket violations × bucket coût × niveau courant), sur un signal de stress lissé par EMA (`META_EMA_ALPHA` = 0,04).
- **Récompense** : `−violations − max(0, coût/C_SLA − 1) − 0,3·coût − 0,3·fidélité − 1,0·baisse`.
  Le terme de **fidélité au contrat** impose que s'écarter de la valeur contractuelle soit
  « payé » par des violations évitées ; le terme de **baisse** empêche les ouvertures-éclair gratuites.
- **Q-tables par agent** — Q-Learning et Rainbow apprennent des politiques de seuil indépendantes, donc des moments de déclenchement différents. Persistées à l'entraînement, rechargées au benchmark (greedy, ε = 0, avec apprentissage en ligne).

---

## Régimes de charge

`PopularityWorkloads` génère trois régimes de popularité (option `--workload`) :

| Régime | Description |
| --- | --- |
| **steady** | Popularité stable — régime du benchmark par défaut |
| **variable** | Distribution Zipf sur les fragments, hot-set qui dérive à intervalles seedés |
| **burst** | Fond Zipf léger + pics de popularité sur un fragment tiré aléatoirement |

Par défaut, l'**entraînement alterne `variable` et `burst`** (pour apprendre à gérer dérive et
pics) tandis que le **benchmark tourne en `steady`**. `--workload MODE` force un régime unique.

---

## Agents RL

### Q-Learning

Q-Learning tabulaire, exploration ε-greedy, entraîné directement via CloudSimPlus.

### Rainbow DQN

Implémentation Rainbow à **six composants** : Double DQN, Dueling, Prioritized Experience
Replay, retours n-step (n = 3), NoisyLinear (exploration intrinsèque) et C51 (valeur
distributionnelle). La politique apprise est conservative — l'agent réplique jusqu'à ce que le
SLA soit respecté, puis s'arrête.

> **Parité de récompense** — les deux agents sont comparés à récompense identique. Les poids
> d'entraînement sont écrits dans `reward_config_<agent>.properties` et rechargés par le
> benchmark, ce qui interdit toute divergence entraînement/évaluation.

---

## Fonction de récompense

Calculée après chaque requête, elle reproduit exactement `TrainingEnvironment.calculateReward()` :

```text
R = r1·SLA_OK − r2·SLA_VIOL − r3·DEPASS_COUT(urgence) − r4·COUT_LINEAIRE
  − r5·COUT_REPL − r6·FAIBLE_POP − r7·REPL_PREMATUREE − r8·SUPPR_PREMATUREE
  + r9·DECLENCHEMENT_CORRECT − r10·THRASHING − r11·MAINTENANCE
  − r12·DETENTION_IMPOPULAIRE − r13·ACTION_INVALIDE
```

| Composante | Poids | Description |
| --- | --- | --- |
| `SLA_OK` | +10 | **Satisficing** : plein dès que la latence passe sous (1 − marge)·T_SLA, sans bonus au-delà |
| `SLA_VIOL` | −20 | Proportionnel au dépassement de T_SLA |
| `DEPASS_COUT` | −15 × urgence | Dépassement de C_SLA, pondéré par l'épuisement du budget (1,0 → 2,0) |
| `COUT_LINEAIRE` | −0 (défaut) | Sensibilité continue au coût, même sous C_SLA — activable par agent |
| `COUT_REPL` | −5 × coût_donnée | Coût BW de création d'un réplica |
| `FAIBLE_POP` | −5 × (1 − pop) | Pénalité ponctuelle : réplication de données peu populaires |
| `REPL_PREMATUREE` | −5 × marge | Réplication sans besoin (SLA confortable) |
| `SUPPR_PREMATUREE` | −5 × marge | Suppression alors que le SLA est respecté (anti-spirale) |
| `DECLENCHEMENT_CORRECT` | +8 × pop × (1 − remplissage) | Réplication sous violation SLA, à **utilité marginale décroissante** |
| `THRASHING` | −8 | Alternances rapides REPLICATE/DELETE |
| `MAINTENANCE` | −0,01 × réplicas | Coût léger par réplica actif |
| `DETENTION_IMPOPULAIRE` | −5 × Σ(1 − pop_f)^2,5 | Coût **récurrent** par fragment froid détenu |
| `ACTION_INVALIDE` | −2 | Action impossible (ex. répliquer au-delà de la capacité) |

`slaSatisfyMargin` (0,3) fixe la marge du plafond `SLA_OK` : le SLA est traité comme une
**contrainte**, pas comme une latence à minimiser — ce qui retire l'incitation au
sur-provisionnement et laisse l'agent minimiser le coût.

Aucune composante ne contient de seuil statique. `DECLENCHEMENT_CORRECT` est continu en
popularité et décroît avec le taux de remplissage : le point de bascule est **appris**. Le terme
`DETENTION_IMPOPULAIRE`, convexe (exposant 2,5), concentre la pénalité sur les données
franchement froides et ferme l'exploit consistant à répliquer dès la requête 0.

---

## Métriques et sorties

### Figures — `images/`

| Figure | Contenu |
| --- | --- |
| `fig1_replica_factor_4models.png` | Évolution du nombre de réplicas |
| `fig2_response_time_4models.png` | Évolution du temps de réponse |
| `fig3_bw_consumption_4models.png` | BW inter-fournisseur vs inter-région |
| `fig4_avg_bw_price_4models.png` | Coût BW moyen par requête |
| `fig5_cumulative_bw_price_4models.png` | Coût BW cumulé |
| `fig6_total_cost_4models.png` | Décomposition du coût total (CPU + BW + réplica) |

### Données — `metrics/`

| Fichier | Contenu |
| --- | --- |
| `norep_*.csv`, `tcdrm_*.csv` | Références par requête (simple / complexe) |
| `rl_qlearning_*.csv`, `rl_rainbow_*.csv` | Traces RL par requête |
| `log_overtime.csv` | Trajectoire des seuils appris et du signal de stress |
| `summary_phase1.csv`, `summary_phase2_rl.csv` | Statistiques agrégées |

`validation/` conserve métriques et modèles d'exécutions de référence antérieures.

---

## Dépannage

| Symptôme | Cause probable | Correctif |
| --- | --- | --- |
| `Py4JNetworkError` / connexion refusée | Port 25333 occupé par un run précédent | `TCDRM_PY4J_PORT=25444 ./run_complete_workflow.sh`, ou tuer le processus Java résiduel |
| Rainbow réplique de façon erratique | Entraînement trop court (réseau neuf) | `--episodes 1500` ; sous 300 le script avertit déjà |
| `rainbow_cloudsim.pt` introuvable | Poids non versionnés (limite GitHub) | Relancer sans `--skip-training` |
| Résultats différents d'un run à l'autre | Seed ou régime de charge différent | Fixer `TCDRM_SEED` et `TCDRM_WORKLOAD` — voir [Reproductibilité](#reproductibilité) |
| Q-tables de méta-seuils ignorées | Bornes/résolution modifiées → structure incompatible | Comportement attendu : le learner repart vierge. Supprimer les `.qtable` obsolètes |

---

## Citation

Ce dépôt est une extension. Si vous l'utilisez dans vos travaux, citez l'article original :

```bibtex
@article{bernardin2025tcdrm,
  title   = {TCDRM: A Tenant Budget-Aware Data Replication Framework
             for Multi-Cloud Computing},
  author  = {Bernardin, Santatra Hagamalala and Mokadem, Riad and
             Morvan, Franck and Ramanana, Hasinarivo and
             Rakotoarivelo, Hasimandimby},
  journal = {Journal of Logistics, Informatics and Service Science},
  volume  = {12},
  number  = {3},
  pages   = {246--263},
  year    = {2025},
  doi     = {10.33168/JLISS.2025.0315},
  issn    = {2409-2665}
}
```

Le simulateur **CloudSim Plus**, [github cloudsim plus](https://github.com/cloudsimplus/cloudsimplus).
