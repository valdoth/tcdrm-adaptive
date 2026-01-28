"""
Fonction de récompense pour TCDRM-ADAPTIVE avec TSLA dynamique appris par l'agent RL.
"""

def calculate_reward_with_dynamic_tsla(
    env,
    replica_action: int,
    tsla_action: int,
    replica_executed: bool,
    tsla_executed: bool,
    previous_replica_count: int,
    previous_tsla: float,
    previous_budget: float,
    query_cost: float,
    query_latency: float
) -> float:
    """
    Fonction de récompense multi-objectif pour TCDRM-ADAPTIVE avec TSLA dynamique.
    
    Objectifs:
    1. Respect du SLA (latence avec TSLA dynamique)
    2. Minimisation des coûts
    3. Efficacité budgétaire
    4. Stabilité des décisions de réplication
    5. Timing stratégique basé sur popularité (PSLA)
    6. Ajustement intelligent de TSLA
    
    Args:
        env: L'environnement TcdrmAdaptiveEnv
        replica_action: Action de réplication (0=CREATE, 1=DELETE, 2=DO_NOTHING)
        tsla_action: Action TSLA (0=INCREASE, 1=DECREASE, 2=MAINTAIN)
        replica_executed: Si l'action de réplication a été exécutée
        tsla_executed: Si l'action TSLA a été exécutée
        previous_replica_count: Nombre de réplicas avant l'action
        previous_tsla: Valeur de TSLA avant l'action
        previous_budget: Budget avant l'action
        query_cost: Coût de la requête
        query_latency: Latence de la requête
    
    Returns:
        Récompense totale
    """
    reward = 0.0
    
    # Poids des différentes composantes
    ALPHA = 10.0   # SLA compliance (TSLA dynamique)
    BETA = 5.0     # Cost penalty
    GAMMA = 15.0   # Budget efficiency
    DELTA = 8.0    # Instability penalty (réplication)
    EPSILON = 20.0 # Strategic timing (PSLA)
    ZETA = 12.0    # TSLA adjustment quality
    
    # Calculer la popularité avec le modèle PLSA (PSLA)
    popularity = env.plsa_model.predict_popularity()
    
    # ========================================================================
    # 1. SLA COMPLIANCE REWARD (utilise TSLA dynamique)
    # ========================================================================
    sla_compliance_reward = 0.0
    if query_latency <= env.current_tsla:
        # Récompense pour respect du SLA
        sla_compliance_reward = ALPHA
        # Bonus si on respecte avec une marge confortable
        margin = (env.current_tsla - query_latency) / env.current_tsla
        if margin > 0.3:  # Marge > 30%
            sla_compliance_reward += ALPHA * 0.5
    else:
        # Pénalité proportionnelle au dépassement
        overshoot = (query_latency / env.current_tsla) - 1.0
        sla_compliance_reward = -ALPHA * 2.0 * overshoot
    
    # ========================================================================
    # 2. COST PENALTY
    # ========================================================================
    cost_penalty = query_cost * BETA
    if replica_action == 0 and replica_executed:  # CREATE_REPLICA
        replication_cost = env.data_gb * env.REPLICATION_COST_PER_GB
        cost_penalty += replication_cost * 2.0
    
    # ========================================================================
    # 3. BUDGET EFFICIENCY REWARD
    # ========================================================================
    budget_ratio = env.current_budget / env.INITIAL_BUDGET
    budget_efficiency_reward = 0.0
    if budget_ratio > 0.5:
        budget_efficiency_reward = GAMMA
    elif budget_ratio > 0.2:
        budget_efficiency_reward = GAMMA / 3.0
    else:
        budget_efficiency_reward = -GAMMA
    
    # ========================================================================
    # 4. INSTABILITY PENALTY (pénaliser changements fréquents de réplication)
    # ========================================================================
    instability_penalty = 0.0
    if replica_action in [0, 1] and replica_executed:  # CREATE ou DELETE
        # Vérifier si on change souvent de stratégie
        if hasattr(env, 'last_replica_action') and env.last_replica_action != replica_action:
            instability_penalty = DELTA
    
    # ========================================================================
    # 5. STRATEGIC TIMING REWARD (créer au bon moment basé sur PSLA)
    # ========================================================================
    strategic_timing_reward = 0.0
    if replica_action == 0 and replica_executed:  # CREATE_REPLICA
        if popularity > 0.7:
            # Excellente décision: créer quand popularité élevée
            strategic_timing_reward = EPSILON
        elif popularity < 0.3:
            # Mauvaise décision: créer trop tôt
            strategic_timing_reward = -EPSILON * 0.75
        else:
            # Décision acceptable
            strategic_timing_reward = EPSILON * 0.5
    elif replica_action == 1 and replica_executed:  # DELETE_REPLICA
        if popularity < 0.3:
            # Bonne décision: supprimer quand popularité faible
            strategic_timing_reward = EPSILON * 0.5
        elif popularity > 0.7:
            # Mauvaise décision: supprimer quand popularité élevée
            strategic_timing_reward = -EPSILON
    elif replica_action == 2:  # DO_NOTHING
        # Récompenser la patience avant le bon moment
        if popularity < 0.7 and env.current_replica_count == 0:
            strategic_timing_reward = EPSILON * 0.3
    
    # ========================================================================
    # 6. TSLA ADJUSTMENT QUALITY REWARD (nouveau pour TSLA dynamique)
    # ========================================================================
    tsla_adjustment_reward = 0.0
    
    # Analyser la qualité de l'ajustement TSLA
    if tsla_action == 0 and tsla_executed:  # INCREASE_TSLA
        # Augmenter TSLA est bon si:
        # - On viole souvent le SLA (besoin de plus de tolérance)
        # - Le budget est faible (économiser en tolérant plus de latence)
        # - La popularité est faible (moins critique)
        
        sla_violation_rate = env.sla_violations / max(env.current_query, 1)
        
        if sla_violation_rate > 0.3:  # Beaucoup de violations
            tsla_adjustment_reward = ZETA  # Bonne décision
        elif budget_ratio < 0.3:  # Budget faible
            tsla_adjustment_reward = ZETA * 0.7
        elif popularity < 0.4:  # Faible popularité
            tsla_adjustment_reward = ZETA * 0.5
        else:
            # Augmenter TSLA sans raison = mauvais
            tsla_adjustment_reward = -ZETA * 0.5
    
    elif tsla_action == 1 and tsla_executed:  # DECREASE_TSLA
        # Diminuer TSLA est bon si:
        # - On respecte facilement le SLA (on peut être plus strict)
        # - Le budget est confortable
        # - La popularité est élevée (plus critique)
        
        sla_violation_rate = env.sla_violations / max(env.current_query, 1)
        
        if sla_violation_rate < 0.1 and budget_ratio > 0.5:
            # On respecte bien le SLA et on a du budget
            tsla_adjustment_reward = ZETA
        elif popularity > 0.7:
            # Haute popularité = être plus strict
            tsla_adjustment_reward = ZETA * 0.8
        elif env.current_replica_count >= 2:
            # Beaucoup de réplicas = on peut être strict
            tsla_adjustment_reward = ZETA * 0.6
        else:
            # Diminuer TSLA sans capacité = mauvais
            tsla_adjustment_reward = -ZETA * 0.7
    
    elif tsla_action == 2:  # MAINTAIN_TSLA
        # Maintenir TSLA est neutre/légèrement positif si la situation est stable
        sla_violation_rate = env.sla_violations / max(env.current_query, 1)
        
        if 0.1 <= sla_violation_rate <= 0.2:
            # Taux de violation acceptable, maintenir est bon
            tsla_adjustment_reward = ZETA * 0.3
        else:
            # Neutre
            tsla_adjustment_reward = 0.0
    
    # Pénalité pour ajustements TSLA trop fréquents
    if len(env.tsla_history) >= 10:
        recent_changes = sum(1 for action, _ in env.tsla_history[-10:] if action != 'MAINTAIN')
        if recent_changes > 5:  # Plus de 50% de changements
            tsla_adjustment_reward -= ZETA * 0.5
    
    # ========================================================================
    # COMBINER TOUTES LES COMPOSANTES
    # ========================================================================
    reward = (
        sla_compliance_reward +
        budget_efficiency_reward +
        strategic_timing_reward +
        tsla_adjustment_reward -
        cost_penalty -
        instability_penalty
    )
    
    # Sauvegarder les actions pour le prochain step
    env.last_replica_action = replica_action
    env.last_tsla_action = tsla_action
    
    return reward
