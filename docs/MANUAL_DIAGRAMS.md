# Diagrammes à Générer Manuellement

Certains diagrammes sont trop complexes pour l'API Mermaid.ink et doivent être générés manuellement.

## Diagrammes Manquants

### 3. Comparaison 5 Modèles

Aller sur https://mermaid.live et coller le code suivant :

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

Sauvegarder comme : `03_comparaison_5_modèles.png`

---

### 5. Fonction de Récompense Multi-Objectif

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

Sauvegarder comme : `05_fonction_de_récompense_multi-objectif.png`

---

### 6. Architecture des Résultats

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

Sauvegarder comme : `06_architecture_des_résultats.png`

---

## Instructions

1. Ouvrir https://mermaid.live
2. Coller le code Mermaid d'un diagramme
3. Cliquer sur "Download PNG"
4. Sauvegarder dans `docs/diagrams/` avec le nom indiqué

## Alternative : Utiliser les Graphes Générés

Les graphes 5 courbes dans `images/` peuvent aussi être utilisés directement dans la documentation :
- `tcdrm_combined_response_time_R1_5curves_smoothed.png`
- `tcdrm_combined_response_time_R2_5curves_smoothed.png`
- `tcdrm_combined_total_cost_R1_5curves.png`
- `tcdrm_combined_total_cost_R2_5curves.png`

Ces graphes montrent déjà la comparaison des 5 modèles de manière visuelle.
