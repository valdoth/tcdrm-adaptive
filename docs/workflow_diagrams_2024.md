# Diagrammes TCDRM-ADAPTIVE 2024 (Mis à jour — alignés sur le code)

> Ce document contient les 10 diagrammes (`docs/diagrams/`). Chaque diagramme
> est vérifié contre le code source (`tcdrm_gym/agents/`, `tcdrm_gym/envs/`,
> `src/main/java/.../training/TrainingEnvironment.java`). Les diagrammes 3, 4
> et 5 avaient initialement échoué à la génération (erreurs 403/503/400 de
> l'API `mermaid.ink`) — ils sont désormais générés en local via Mermaid CLI
> et ont été corrigés au passage pour correspondre au code actuel.

## 1. Architecture Globale TCDRM-ADAPTIVE (2 Modèles RL)

```mermaid
graph TB
    subgraph "TCDRM-ADAPTIVE Framework"
        subgraph "Python RL (2 Modeles)"
            QL["Q-Learning Ameliore<br/>Double Q-Learning<br/>Learning Rate Adaptatif<br/>Q(lambda) Eligibility Traces<br/>243 etats discrets"]
            DQN["DQN Rainbow<br/>Double DQN<br/>Dueling Architecture<br/>Prioritized Replay<br/>n-step (n=3)<br/>Reseau 9D vers 64 vers 64 vers V:1 / A:3"]
        end

        subgraph "Environnements Gymnasium"
            ENV_QL["CloudSimQLearningEnv<br/>Etat discret<br/>243 etats (3 puissance 5)"]
            ENV_DQN["CloudSimEnv<br/>Etat continu 9D<br/>Recompense multi-composantes"]
        end

        subgraph "Workload et Popularite"
            WG["WorkloadGenerator<br/>10 relations R1 a R10<br/>Popularite fixe par relation"]
            POP["3 strategies de popularite<br/>EMA / TinyLFU / EMA+TinyLFU"]
        end

        subgraph "Baselines"
            TCDRM["TCDRM Statique<br/>Seuil fixe P_SLA=200"]
            NOREP["NOREP<br/>Pas de replication"]
        end
    end

    subgraph "Entrainement"
        WG --> ENV_QL
        WG --> ENV_DQN
        POP --> ENV_QL
        POP --> ENV_DQN

        QL --> ENV_QL
        DQN --> ENV_DQN
        ENV_QL -->|Reward| QL
        ENV_DQN -->|Reward| DQN
    end

    subgraph "Evaluation et Graphes"
        QL --> COMP["Comparaison<br/>4 Modeles"]
        DQN --> COMP
        TCDRM --> COMP
        NOREP --> COMP
        COMP --> GRAPHS["Graphes comparatifs<br/>CloudSim + Py4J<br/>1000 requetes"]
    end

    style QL fill:#FFA500
    style DQN fill:#00CED1
    style TCDRM fill:#DC143C
    style NOREP fill:#FF6347
    style ENV_QL fill:#90EE90
    style ENV_DQN fill:#98FB98
    style WG fill:#FFE4B5
    style POP fill:#FFD700
    style COMP fill:#87CEEB
    style GRAPHS fill:#B0E0E6
```

**Vérifié contre** : `tcdrm_gym/envs/cloudsim_env.py` (`CloudSimQLearningEnv`, `CloudSimEnv`, état 9D), `tcdrm_gym/agents/simple_qlearning_agent.py`, `tcdrm_gym/agents/dqn_agent.py` (`DuelingDQNNetwork`, `hidden_dims=[64,64]`), `src/main/java/.../data/WorkloadGenerator.java` (10 relations).

---

## 2. Modèle de Workload et Stratégies de Popularité

```mermaid
graph TB
    subgraph "Workload genere (WorkloadGenerator, 10 relations)"
        R1["R1, R2 - Tres populaires<br/>80-85% des requetes"]
        R2["R3, R4, R5 - Populaires<br/>45-55% des requetes"]
        R3["R6 a R10 - Peu populaires<br/>5-20% des requetes"]
    end

    subgraph "Suivi de popularite par fragment"
        TRACK["RelationPopularityTracker<br/>1 estimateur par DataFragment"]
    end

    subgraph "Strategie configurable (--popularity-strategy)"
        EMA["EMA<br/>P_t = alpha.q_t + (1-alpha).P_t-1"]
        TLFU["TinyLFU<br/>Count-Min Sketch + aging periodique"]
        HYB["EMA + TinyLFU (hybride)"]
    end

    subgraph "Strategies de warmup (--warmup-strategy)"
        WR["random<br/>Actions aleatoires"]
        WT["tcdrm<br/>Regles statiques TCDRM"]
        WN["norep<br/>Aucune replication"]
    end

    R1 --> TRACK
    R2 --> TRACK
    R3 --> TRACK
    TRACK --> EMA
    TRACK --> TLFU
    TRACK --> HYB

    style R1 fill:#FFE082
    style R2 fill:#FFF3C4
    style R3 fill:#E8F5E9
    style TRACK fill:#B3E5FC
    style EMA fill:#90CAF9
    style TLFU fill:#90CAF9
    style HYB fill:#90CAF9
    style WR fill:#CE93D8
    style WT fill:#CE93D8
    style WN fill:#CE93D8
```

**Note** : ce diagramme remplace l'ancienne version "11 patterns cloud réels"
(`steady`, `burst`, `read_intensive`, `black_friday`, etc.), qui ne correspond
à aucun code existant dans le projet. Le workload réel est généré par
`WorkloadGenerator.java` (10 relations à popularité fixe) et la popularité est
suivie par fragment via `RelationPopularityTracker` / `TinyLFU.java`, avec un
choix de stratégie au lancement (`--popularity-strategy EMA|TINYLFU|EMA_TINYLFU`).

---

## 3. Workflow Détaillé (Entraînement → Compilation → Benchmark)

```mermaid
graph LR
    subgraph "Etape 1: Entrainement RL (Python)"
        T1["train_cloudsim.py --agent qlearning<br/>--episodes N (defaut 100)"]
        T2["train_cloudsim.py --agent dqn<br/>--episodes N (defaut 100)"]
        T1 --> M1["models/qlearning_cloudsim.pkl"]
        T2 --> M2["models/dqn_cloudsim.pt"]
    end

    subgraph "Etape 2: Compilation Java"
        C1["mvn package -DskipTests"]
    end

    subgraph "Etape 3: Benchmark CloudSim (Py4J)"
        SIM["TcdrmMain (benchmark)<br/>4 strategies x 1000 requetes<br/>NoRep, TCDRM, Q-Learning, DQN"]
        M1 --> SIM
        M2 --> SIM
        C1 --> SIM
        SIM --> METRICS["Metriques par strategie<br/>latence, cout, SLA, replicas, BW"]
    end

    subgraph "Sortie"
        METRICS --> G["images/*.png<br/>fig1-7, metrics_*, popularity_*"]
    end

    style T1 fill:#FFA500
    style T2 fill:#00CED1
    style C1 fill:#FFD700
    style SIM fill:#90EE90
    style METRICS fill:#87CEEB
    style G fill:#B0E0E6
```

**Vérifié contre** : `tcdrm_gym/train_cloudsim.py` (CLI `--agent`, `--episodes`),
`tcdrm_gym/config.yml` (chemins des modèles), `run_complete_workflow.sh`
(étapes Maven puis benchmark Py4J). Complète le diagramme 7 (vue Gantt/pipeline)
avec le détail des artefacts produits à chaque étape.

---

## 4. Techniques d'Amélioration des Algorithmes RL

```mermaid
graph TB
    subgraph "Q-Learning Ameliore (simple_qlearning_agent.py)"
        QL_BASE["Q-Learning Standard<br/>Q(s,a) tableau, mise a jour TD"]

        QL_IMP1["Double Q-Learning<br/>2 Q-tables (Q_A, Q_B)<br/>Alternance aleatoire<br/>Reduit la surestimation"]

        QL_IMP2["Learning Rate Adaptatif<br/>alpha_t = alpha_0 / (1 + 0.01 x visits)<br/>Decroit avec les visites"]

        QL_IMP3["Exploration ponderee<br/>Probabilite proportionnelle a 1 / (visits + 1)<br/>Favorise les actions peu explorees"]

        QL_IMP4["Q(lambda) Eligibility Traces<br/>lambda configurable (ex. 0.6)<br/>Propage la recompense sur plusieurs pas"]

        QL_BASE --> QL_IMP1
        QL_BASE --> QL_IMP2
        QL_BASE --> QL_IMP3
        QL_BASE --> QL_IMP4
    end

    subgraph "DQN Avance (dqn_agent.py)"
        DQN_BASE["DQN Standard<br/>Reseau de neurones + Experience Replay"]

        DQN_IMP1["Double DQN<br/>Policy net: selection<br/>Target net: evaluation<br/>Reduit la surestimation"]

        DQN_IMP2["Dueling Architecture<br/>V(s) + A(s,a) - moyenne(A)<br/>Separe valeur et avantage"]

        DQN_IMP3["Prioritized Experience Replay<br/>Echantillonnage par TD-error<br/>Apprend des erreurs"]

        DQN_IMP4["n-step Returns (n=3)<br/>Cumul des recompenses sur n pas<br/>Accelere la propagation"]

        DQN_IMP5["Soft Target Update<br/>theta_cible = tau x theta + (1-tau) x theta_cible<br/>tau=0.005"]

        DQN_IMP6["Normalisation Welford<br/>Recompenses normalisees en ligne<br/>Stabilise l'entrainement du reseau"]

        DQN_BASE --> DQN_IMP1
        DQN_BASE --> DQN_IMP2
        DQN_BASE --> DQN_IMP3
        DQN_BASE --> DQN_IMP4
        DQN_BASE --> DQN_IMP5
        DQN_BASE --> DQN_IMP6
    end

    style QL_BASE fill:#FFE4B5
    style QL_IMP1 fill:#FFA500
    style QL_IMP2 fill:#FFA500
    style QL_IMP3 fill:#FFA500
    style QL_IMP4 fill:#FFA500
    style DQN_BASE fill:#B0E0E6
    style DQN_IMP1 fill:#00CED1
    style DQN_IMP2 fill:#00CED1
    style DQN_IMP3 fill:#00CED1
    style DQN_IMP4 fill:#00CED1
    style DQN_IMP5 fill:#00CED1
    style DQN_IMP6 fill:#00CED1
```

**Vérifié contre** : `tcdrm_gym/agents/simple_qlearning_agent.py` (Double
Q-Learning, `adaptive_lr`, exploration pondérée par `visit_counts`,
`lambda_trace`) et `tcdrm_gym/agents/dqn_agent.py` (`use_double_dqn`,
`DuelingDQNNetwork`, `PrioritizedReplayBuffer`, `NStepBuffer`, `tau`,
`RunningMeanStd`). La version précédente de ce diagramme omettait Q(λ),
n-step et la normalisation Welford, pourtant bien présents dans le code.

---

## 5. Processus de Décision (Q-Learning vs DQN)

```mermaid
graph TB
    subgraph "Etat (9D Continu, buildRLState)"
        S["latency, budget, replicas,<br/>popularity, cost,<br/>t_sla_violation, c_sla_violation,<br/>progress, p_sla_progress"]
    end

    subgraph "Q-Learning (Discret)"
        S --> DISC["Discretisation<br/>3x3x3x3x3 = 243 etats<br/>(RT, COST, POP, BUD, NET)"]
        DISC --> QTABLE["Double Q-Tables<br/>Q_A[243x3], Q_B[243x3]"]
        QTABLE --> SELECT1["Selection epsilon-greedy<br/>Exploration ponderee par visites"]
        SELECT1 --> A1["Action<br/>0=NOOP, 1=REPLICATE, 2=DELETE"]
    end

    subgraph "DQN (Continu)"
        S --> NN1["Couches partagees<br/>Dense 64 + ReLU x2"]
        NN1 --> SPLIT["Dueling Split"]
        SPLIT --> V["Value Stream<br/>Dense 32 -> Dense 1<br/>V(s)"]
        SPLIT --> ADV["Advantage Stream<br/>Dense 32 -> Dense 3<br/>A(s,a)"]
        V --> COMBINE["Q(s,a) = V(s) + A(s,a) - moyenne(A)"]
        ADV --> COMBINE
        COMBINE --> SELECT2["Selection epsilon-greedy<br/>Action masking (valides uniquement)"]
        SELECT2 --> A2["Action<br/>0=NOOP, 1=REPLICATE, 2=DELETE"]
    end

    A1 --> ENV["Environnement<br/>CloudSimPlus (Py4J)"]
    A2 --> ENV

    ENV --> R["Recompense<br/>7 composantes (cf. diagramme 9)"]

    R --> QTABLE
    R --> NN1

    style S fill:#87CEEB
    style DISC fill:#FFA500
    style QTABLE fill:#FFE4B5
    style NN1 fill:#00CED1
    style SPLIT fill:#00CED1
    style V fill:#00CED1
    style ADV fill:#00CED1
    style COMBINE fill:#00CED1
    style ENV fill:#90EE90
    style R fill:#FFD700
```

**Vérifié contre** : `tcdrm_gym/envs/cloudsim_env.py` (état 9D,
`CloudSimQLearningEnv._discretize_state` pour la discrétisation 3⁵),
`tcdrm_gym/agents/dqn_agent.py` (`DuelingDQNNetwork`). La version précédente
indiquait un état 8D avec des noms de variables fictifs (`Budget Ratio,
Access Count, Query Complexity...`) ; corrigé pour correspondre exactement à
`buildRLState()`.

---

## 6. Comparaison 4 Modèles (Sans PPO)

```mermaid
graph TB
    subgraph "Performance mesuree (cf. contribution.tex, Ch.3)"
        RANK1["1. DQN<br/>Double DQN + Dueling<br/>Declenchement q=79 (-60% vs TCDRM)<br/>Moins de violations SLA"]

        RANK2["2. Q-Learning<br/>Double Q-Learning + Q(lambda)<br/>Declenchement q=99 (-50% vs TCDRM)<br/>Convergence rapide, entrainement leger"]

        RANK3["3. TCDRM Statique<br/>Seuil fixe P_SLA=200<br/>Reference (baseline)"]

        RANK4["4. NOREP<br/>Aucune replication<br/>Latence et cout les plus eleves"]
    end

    subgraph "Differenciateurs"
        DQN_ADV["DQN : etat continu 9D<br/>Meilleure generalisation<br/>Anticipation la plus precoce"]

        QL_ADV["Q-Learning : 243 etats discrets<br/>Convergence rapide (Q-lambda)<br/>Cout d'entrainement minimal"]
    end

    RANK1 -.-> DQN_ADV
    RANK2 -.-> QL_ADV

    style RANK1 fill:#FFD700
    style RANK2 fill:#C0C0C0
    style RANK3 fill:#CD7F32
    style RANK4 fill:#FF6347
    style DQN_ADV fill:#B0E0E6
    style QL_ADV fill:#FFE4B5
```

**Note** : l'ancienne version classait les modèles par pattern de charge
fictif (`geo_distributed`, `black_friday`, `read_intensive`...). Le classement
est remplacé par les résultats mesurés et reproductibles présentés dans
`overleaf/contribution.tex` (Section 4 — Résultats expérimentaux).

---

## 7. Workflow Complet (run-complete-workflow)

```mermaid
graph LR
    S1["Etape 1/3<br/>Entrainement RL (Python)<br/>Q-Learning + DQN<br/>--episodes N (defaut 100)"]
    S2["Etape 2/3<br/>Compilation Java<br/>Maven (mvn package)"]
    S3["Etape 3/3<br/>Benchmark CloudSim + Py4J<br/>4 modeles x 1000 requetes<br/>environ 2-5 min"]

    S1 --> S2 --> S3
    S3 --> G["Graphes et metriques<br/>images/*.png"]

    style S1 fill:#FFA500
    style S2 fill:#FFD700
    style S3 fill:#90EE90
    style G fill:#B0E0E6
```

**Vérifié contre** : `run_complete_workflow.sh` (`STEP 1/3` entraînement,
`STEP 2/3` compilation Maven, `STEP 3/3` benchmark Py4J ; option `--episodes`,
défaut 100 ; commentaire du script : phase benchmark ≈ 2-5 min pour 4
scénarios × 1000 requêtes).

---

## 8. Architecture des Résultats

```mermaid
graph TB
    subgraph "Modeles entraines"
        QL_MODEL["Q-Learning<br/>models/qlearning_cloudsim.pkl<br/>Double Q-Learning<br/>243 etats"]

        DQN_MODEL["DQN<br/>models/dqn_cloudsim.pt<br/>Double DQN, Dueling, PER, n-step"]
    end

    subgraph "Logs d'entrainement (par run)"
        LOGS["tcdrm_gym/logs/AGENT/TIMESTAMP/<br/>progress.csv (par episode)<br/>best_meta.json<br/>TensorBoard"]

        FIELDS["Champs logges<br/>reward, epsilon, sla_violations,<br/>cumulative_cost, replica_count,<br/>budget_remaining, reward_*"]
    end

    subgraph "Graphes CloudSim (images/)"
        GRAPHS["fig1-7_*.png (baseline + 4 modeles)<br/>metrics_AGENT_simple.png<br/>popularity_AGENT_simple.png"]
    end

    QL_MODEL -.-> LOGS
    DQN_MODEL -.-> LOGS
    LOGS --> FIELDS

    QL_MODEL --> GRAPHS
    DQN_MODEL --> GRAPHS

    style QL_MODEL fill:#FFA500
    style DQN_MODEL fill:#00CED1
    style LOGS fill:#FFE4B5
    style FIELDS fill:#B0E0E6
    style GRAPHS fill:#90EE90
```

**Vérifié contre** : `tcdrm_gym/config.yml` (chemins des modèles),
`tcdrm_gym/train_cloudsim.py` (`CSVLogger`, `ensure_log_dir`, champs CSV),
`tcdrm-adaptive/images/` (fichiers `fig*.png`, `metrics_*_simple.png`,
`popularity_*_simple.png` réellement présents). Les anciennes références à
`ANALYSE_DOUBLE_DQN_ET_PATTERNS.md`, `PATTERNS_CLOUD_IMPLEMENTES.md` et
`MODIFICATIONS_ENTRAINEMENT.md` ont été retirées : ces fichiers n'existent pas
dans le dépôt.

---

## 9. Fonction de Récompense

```mermaid
graph TB
    subgraph "Composantes (TrainingEnvironment.calculateReward)"
        R1["SLA_OK (r1=10)<br/>+10 x max(0, 1 - latency/T_SLA)<br/>Recompense si latence sous T_SLA"]

        R2["SLA_VIOL (r2=20)<br/>-20 x max(0, latency/T_SLA - 1)<br/>Penalite si T_SLA depasse"]

        R3["COST_OVER (r3=15)<br/>-15 x max(0, cost/C_SLA - 1)<br/>Penalite si cout par requete depasse"]

        R4["REPL_COST (r4=5)<br/>-5 x cout BW replica / budget initial<br/>Cout reel de creer une replique"]

        R5["THRASH (r5=8)<br/>-8 si 2+ REPLICATE et 2+ DELETE<br/>parmi les 10 dernieres actions"]

        R6["UNUTILIZATION<br/>-0.05 x nombre de replicas actifs<br/>Cout de maintenance"]

        R7["INVALID_ACTION<br/>-2 si action invalide"]
    end

    R1 --> TOTAL["Reward total<br/>Somme des 7 composantes"]
    R2 --> TOTAL
    R3 --> TOTAL
    R4 --> TOTAL
    R5 --> TOTAL
    R6 --> TOTAL
    R7 --> TOTAL

    TOTAL --> AGENT["Agent RL<br/>Q-Learning ou DQN"]

    style R1 fill:#90EE90
    style R2 fill:#FFB6C1
    style R3 fill:#87CEEB
    style R4 fill:#FFD700
    style R5 fill:#DDA0DD
    style R6 fill:#F0E68C
    style R7 fill:#FF8A80
    style TOTAL fill:#FF6347
    style AGENT fill:#FFA500
```

**Vérifié contre** : `src/main/java/org/tcdrm/adaptive/training/TrainingEnvironment.java`,
méthode `calculateReward()` (commentaire de code :
`R = r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH`,
complété par `rewardUnutilization` et `rewardInvalidAction`). L'ancienne
version (composantes `ALPHA/BETA/GAMMA/DELTA/EPSILON/ZETA/ETA`, valeurs
15/6/18/10/25/15/12) ne correspond à aucune implémentation présente dans le
code et a été remplacée.

---

## 10. Techniques RL Implémentées (Vérification Code)

```mermaid
graph TB
    subgraph "Q-Learning (tcdrm_gym/agents/simple_qlearning_agent.py)"
        QL_T["Double Q-Learning<br/>Learning Rate Adaptatif<br/>Exploration ponderee par visites<br/>Q(lambda) Eligibility Traces (configurable)"]
    end

    subgraph "DQN (tcdrm_gym/agents/dqn_agent.py)"
        DQN_T["Double DQN<br/>Dueling Architecture<br/>Prioritized Experience Replay<br/>n-step Returns (n=3)<br/>Soft Target Update (tau=0.005)<br/>Normalisation Welford"]
    end

    QL_T --> VERIFY_QL["Verifie dans le code source"]
    DQN_T --> VERIFY_DQN["Verifie dans le code source"]

    style QL_T fill:#FFA500
    style DQN_T fill:#00CED1
    style VERIFY_QL fill:#90EE90
    style VERIFY_DQN fill:#90EE90
```

**Note** : l'ancienne version renvoyait à `algo.md` et à des numéros de ligne
précis dans le code (ex. "Ligne 134-161"). `algo.md` a été supprimé du dépôt
(fichier de notes redondant) et les numéros de ligne deviennent obsolètes dès
la moindre modification du fichier ; ce diagramme renvoie donc aux noms de
fichiers/techniques uniquement, plus stables dans le temps.

---

## Résumé des Modifications

### Algorithmes RL

- **Q-Learning** (`simple_qlearning_agent.py`) : Double Q-Learning, learning rate adaptatif, exploration pondérée par visites, traces d'éligibilité Q(λ) — 243 états discrets (3⁵).
- **DQN** (`dqn_agent.py`) : Double DQN, Dueling, Prioritized Experience Replay, n-step returns (n=3), soft target update (τ=0.005), normalisation des récompenses (Welford) — état continu 9D.
- **PPO** : non implémenté dans ce projet (absent du code, donc absent des diagrammes).

### Workload et Popularité

- 10 relations (`R1`-`R10`) à popularité fixe générées par `WorkloadGenerator.java` — pas de "11 patterns cloud" (steady/burst/black_friday/...), qui ne correspondent à aucun code du dépôt.
- 3 stratégies de popularité interchangeables : EMA, TinyLFU (Count-Min Sketch), hybride EMA+TinyLFU.
- 3 stratégies de warmup : `random`, `tcdrm`, `norep`.

### Fonction de récompense

- 7 composantes réelles calculées dans `TrainingEnvironment.calculateReward()` : SLA_OK, SLA_VIOL, COST_OVER, REPL_COST, THRASH, UNUTILIZATION, INVALID_ACTION.

### Documentation

- Suppression des références à des fichiers `.md` inexistants (`ANALYSE_DOUBLE_DQN_ET_PATTERNS.md`, `PATTERNS_CLOUD_IMPLEMENTES.md`, `MODIFICATIONS_ENTRAINEMENT.md`, `algo.md`).
- 10 diagrammes générés (1 à 10). Les diagrammes 3, 4 et 5, qui échouaient
  auparavant à la génération (erreurs API), ont été corrigés et régénérés
  avec succès en local via Mermaid CLI.
