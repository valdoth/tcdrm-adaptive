# Diagrammes TCDRM-ADAPTIVE Mis à Jour

## 1. Architecture Globale TCDRM-ADAPTIVE (3 Modèles RL)

```mermaid
graph TB
    subgraph "🎯 TCDRM-ADAPTIVE Framework"
        subgraph "Python RL (3 Modèles)"
            QL[Q-Learning<br/>Tabular<br/>108 états]
            DQN[DQN<br/>Deep Q-Network<br/>Neural Net]
            PPO[PPO<br/>Policy Optimization<br/>Stable-Baselines3]
        end
        
        subgraph "Environnement Gymnasium"
            ENV[TcdrmAdaptiveEnv<br/>8D Observation Space<br/>Multi-Objective Reward]
        end
        
        subgraph "Baselines"
            TCDRM[TCDRM Statique<br/>Seuil fixe: 200]
            NOREP[NOREP<br/>Pas de réplication]
        end
    end
    
    subgraph "Entraînement"
        QL --> ENV
        DQN --> ENV
        PPO --> ENV
        ENV --> |Reward| QL
        ENV --> |Reward| DQN
        ENV --> |Reward| PPO
    end
    
    subgraph "Évaluation & Graphes"
        QL --> COMP[Comparaison<br/>5 Modèles]
        DQN --> COMP
        PPO --> COMP
        TCDRM --> COMP
        NOREP --> COMP
        COMP --> GRAPHS[Graphes 5 Courbes<br/>R1 & R2]
    end
    
    style QL fill:#FFA500
    style DQN fill:#00CED1
    style PPO fill:#9370DB
    style TCDRM fill:#DC143C
    style NOREP fill:#FF6347
    style ENV fill:#90EE90
    style COMP fill:#FFD700
    style GRAPHS fill:#87CEEB
```

## 2. Workflow Complet TCDRM-ADAPTIVE

```mermaid
graph LR
    subgraph "Phase 1: Entraînement (30-60 min)"
        T1[Q-Learning<br/>200 épisodes]
        T2[DQN<br/>200 épisodes]
        T3[PPO<br/>200k timesteps]
        
        T1 --> M1[Modèle Q-Learning<br/>adaptive_model.pkl]
        T2 --> M2[Modèle DQN<br/>dqn_model.pt]
        T3 --> M3[Modèle PPO<br/>ppo_model.zip]
    end
    
    subgraph "Phase 2: Évaluation (5-10 min)"
        M1 --> EVAL[Évaluation<br/>Tous Modèles<br/>R1 & R2]
        M2 --> EVAL
        M3 --> EVAL
        
        EVAL --> METRICS[Métriques<br/>Latence, Coût<br/>SLA, Réplicas]
    end
    
    subgraph "Phase 3: Visualisation (2-3 min)"
        METRICS --> G1[Graphes 5 Courbes<br/>Response Time]
        METRICS --> G2[Graphes 5 Courbes<br/>Cost]
        METRICS --> G3[Graphes 5 Courbes<br/>Replicas]
    end
    
    style T1 fill:#FFA500
    style T2 fill:#00CED1
    style T3 fill:#9370DB
    style M1 fill:#FFE4B5
    style M2 fill:#B0E0E6
    style M3 fill:#DDA0DD
    style EVAL fill:#90EE90
    style METRICS fill:#FFD700
```

## 3. Comparaison 5 Modèles

```mermaid
graph TB
    subgraph "Scénario R1 (5.3 GB)"
        R1_PPO[PPO<br/>178.73s<br/>213.55$]
        R1_DQN[DQN<br/>179.45s<br/>228.61$]
        R1_QL[Q-Learning<br/>181.35s<br/>246.27$]
        R1_TCDRM[TCDRM Statique<br/>183.41s<br/>277.43$]
        R1_NOREP[NOREP<br/>201.59s<br/>530.88$]
    end
    
    subgraph "Scénario R2 (11.9 GB)"
        R2_PPO[PPO<br/>401.26s<br/>479.53$]
        R2_DQN[DQN<br/>402.44s<br/>501.69$]
        R2_QL[Q-Learning<br/>407.12s<br/>552.99$]
        R2_TCDRM[TCDRM Statique<br/>411.75s<br/>622.96$]
        R2_NOREP[NOREP<br/>452.40s<br/>1000.07$]
    end
    
    subgraph "Classement"
        RANK1[🥇 PPO]
        RANK2[🥈 DQN]
        RANK3[🥉 Q-Learning]
        RANK4[4️⃣ TCDRM Statique]
        RANK5[5️⃣ NOREP]
    end
    
    R1_PPO -.-> RANK1
    R2_PPO -.-> RANK1
    R1_DQN -.-> RANK2
    R2_DQN -.-> RANK2
    R1_QL -.-> RANK3
    R2_QL -.-> RANK3
    
    style R1_PPO fill:#9370DB
    style R2_PPO fill:#9370DB
    style R1_DQN fill:#00CED1
    style R2_DQN fill:#00CED1
    style R1_QL fill:#FFA500
    style R2_QL fill:#FFA500
    style R1_TCDRM fill:#DC143C
    style R2_TCDRM fill:#DC143C
    style R1_NOREP fill:#FF6347
    style R2_NOREP fill:#FF6347
    style RANK1 fill:#FFD700
    style RANK2 fill:#C0C0C0
    style RANK3 fill:#CD7F32
```

## 4. Processus de Décision Multi-Modèles

```mermaid
graph LR
    subgraph "État (8D)"
        S[Budget Ratio<br/>Latency<br/>Access Count<br/>Replica Count<br/>Query Complexity<br/>SLA Violation Rate<br/>Cost Rate<br/>Popularity]
    end
    
    subgraph "Q-Learning"
        S --> DISC[Discrétisation<br/>108 états]
        DISC --> QTABLE[Q-Table<br/>108x3]
        QTABLE --> A1[Action]
    end
    
    subgraph "DQN"
        S --> NN[Neural Network<br/>64-64-3]
        NN --> A2[Action]
    end
    
    subgraph "PPO"
        S --> POLICY[Policy Network<br/>Stable-Baselines3]
        POLICY --> A3[Action]
    end
    
    A1 --> ENV[Environnement]
    A2 --> ENV
    A3 --> ENV
    
    ENV --> R[Récompense<br/>Multi-Objectif<br/>5 Composantes]
    
    R --> |Update| QTABLE
    R --> |Backprop| NN
    R --> |PPO Update| POLICY
    
    style S fill:#87CEEB
    style DISC fill:#FFA500
    style NN fill:#00CED1
    style POLICY fill:#9370DB
    style ENV fill:#90EE90
    style R fill:#FFD700
```

## 5. Fonction de Récompense Multi-Objectif

```mermaid
graph TB
    subgraph "Composantes de la Récompense"
        R1[SLA Compliance<br/>α = 10<br/>+10 si OK<br/>-20 si violation]
        R2[Cost Penalty<br/>β = 5<br/>Minimiser coûts<br/>BW + CPU + Storage]
        R3[Budget Efficiency<br/>γ = 15<br/>+15 si >50%<br/>-15 si <20%]
        R4[Instability Penalty<br/>δ = 8<br/>Pénaliser<br/>changements fréquents]
        R5[Strategic Timing<br/>ε = 20<br/>+20 si création<br/>au bon moment]
    end
    
    R1 --> TOTAL[Reward Total]
    R2 --> TOTAL
    R3 --> TOTAL
    R4 --> TOTAL
    R5 --> TOTAL
    
    TOTAL --> AGENT[Agent RL]
    
    style R1 fill:#90EE90
    style R2 fill:#FFB6C1
    style R3 fill:#87CEEB
    style R4 fill:#FFD700
    style R5 fill:#DDA0DD
    style TOTAL fill:#FF6347
    style AGENT fill:#FFA500
```

## 6. Architecture des Résultats

```mermaid
graph TB
    subgraph "Modèles Entraînés"
        QL_MODEL[Q-Learning<br/>results/tcdrm_adaptive/<br/>adaptive_model.pkl]
        DQN_MODEL[DQN<br/>results/dqn/<br/>dqn_model.pt]
        PPO_MODEL[PPO<br/>results/ppo/<br/>ppo_model.zip]
    end
    
    subgraph "Métriques d'Entraînement"
        QL_METRICS[training_metrics.pkl<br/>Reward, Cost, SLA<br/>Replica Changes]
        DQN_METRICS[dqn_training_metrics.pkl<br/>+ Loss]
        PPO_METRICS[ppo_training_metrics.pkl<br/>+ Policy Loss]
    end
    
    subgraph "Graphes de Comparaison"
        COMP_R1[R1 (5.3 GB)<br/>response_time_5curves<br/>total_cost_5curves]
        COMP_R2[R2 (11.9 GB)<br/>response_time_5curves<br/>total_cost_5curves]
    end
    
    QL_MODEL --> COMP_R1
    DQN_MODEL --> COMP_R1
    PPO_MODEL --> COMP_R1
    
    QL_MODEL --> COMP_R2
    DQN_MODEL --> COMP_R2
    PPO_MODEL --> COMP_R2
    
    QL_MODEL -.-> QL_METRICS
    DQN_MODEL -.-> DQN_METRICS
    PPO_MODEL -.-> PPO_METRICS
    
    style QL_MODEL fill:#FFA500
    style DQN_MODEL fill:#00CED1
    style PPO_MODEL fill:#9370DB
    style COMP_R1 fill:#90EE90
    style COMP_R2 fill:#90EE90
```

## 7. Timeline du Workflow Complet

```mermaid
gantt
    title Workflow TCDRM-ADAPTIVE (Total: 40-75 min)
    dateFormat HH:mm
    axisFormat %H:%M
    
    section Entraînement
    Q-Learning (200 ep)     :q1, 00:00, 15m
    DQN (200 ep)           :d1, 00:15, 20m
    PPO (200k steps)       :p1, 00:35, 25m
    
    section Évaluation
    Évaluer tous modèles   :e1, 01:00, 5m
    Calculer métriques     :e2, 01:05, 3m
    
    section Visualisation
    Générer graphes 5 courbes :v1, 01:08, 2m
    
    section Total
    Workflow complet       :crit, 00:00, 70m
```

## 8. Métriques Comparatives (5 Modèles)

```mermaid
graph TB
    subgraph "Amélioration vs TCDRM Statique"
        PPO_GAIN[PPO<br/>Latence: -2.5%<br/>Coût: -23.0%<br/>🥇 Meilleur]
        DQN_GAIN[DQN<br/>Latence: -2.3%<br/>Coût: -19.5%<br/>🥈 2ème]
        QL_GAIN[Q-Learning<br/>Latence: -1.1%<br/>Coût: -11.2%<br/>🥉 3ème]
        TCDRM_BASE[TCDRM Statique<br/>Baseline<br/>0%]
        NOREP_LOSS[NOREP<br/>Latence: +9.9%<br/>Coût: +60.5%<br/>❌ Pire]
    end
    
    subgraph "Stabilité"
        PPO_STABLE[PPO<br/>245 changements<br/>Stable ✓]
        DQN_STABLE[DQN<br/>280 changements<br/>Stable ✓]
        QL_STABLE[Q-Learning<br/>268 changements<br/>Stable ✓]
    end
    
    PPO_GAIN -.-> PPO_STABLE
    DQN_GAIN -.-> DQN_STABLE
    QL_GAIN -.-> QL_STABLE
    
    style PPO_GAIN fill:#9370DB
    style DQN_GAIN fill:#00CED1
    style QL_GAIN fill:#FFA500
    style TCDRM_BASE fill:#DC143C
    style NOREP_LOSS fill:#FF6347
    style PPO_STABLE fill:#90EE90
    style DQN_STABLE fill:#90EE90
    style QL_STABLE fill:#90EE90
```
