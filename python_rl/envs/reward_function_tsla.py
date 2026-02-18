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
    Fonction de récompense multi-objectif AMÉLIORÉE pour TCDRM-ADAPTIVE.
    
    Améliorations:
    - Intégration PLSA amélioré (burst detection, trend analysis)
    - Récompenses adaptatives contextuelles
    - Pénalités plus intelligentes et progressives
    - Bonus pour décisions stratégiques optimales
    - Meilleure gestion de TSLA dynamique
    
    Objectifs:
    1. Respect du SLA (latence avec TSLA dynamique)
    2. Minimisation des coûts
    3. Efficacité budgétaire
    4. Stabilité des décisions de réplication
    5. Timing stratégique basé sur popularité (PLSA amélioré)
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
    
    # Poids des différentes composantes (ajustés pour meilleur équilibre)
    ALPHA = 15.0   # SLA compliance (TSLA dynamique) - augmenté
    BETA = 6.0     # Cost penalty - légèrement augmenté
    GAMMA = 18.0   # Budget efficiency - augmenté
    DELTA = 10.0   # Instability penalty (réplication) - augmenté
    EPSILON = 25.0 # Strategic timing (PLSA amélioré) - augmenté
    ZETA = 15.0    # TSLA adjustment quality - augmenté
    ETA = 12.0     # Proactive decision bonus (nouveau)
    
    # Calculer la popularité avec le modèle PLSA amélioré
    popularity = env.plsa_model.predict_popularity()
    
    # Obtenir les métriques avancées du PLSA
    topic_distribution = env.plsa_model.get_topic_distribution()
    
    # Détecter burst et tendance (nouvelles métriques)
    burst_score = env.plsa_model._detect_burst() if hasattr(env.plsa_model, '_detect_burst') else 0.0
    trend_score = env.plsa_model._detect_trend() if hasattr(env.plsa_model, '_detect_trend') else 0.0
    
    # ========================================================================
    # 1. SLA COMPLIANCE REWARD AMÉLIORÉ (utilise TSLA dynamique)
    # ========================================================================
    sla_compliance_reward = 0.0
    if query_latency <= env.current_tsla:
        # Récompense pour respect du SLA
        sla_compliance_reward = ALPHA
        
        # Bonus progressif selon la marge
        margin = (env.current_tsla - query_latency) / env.current_tsla
        if margin > 0.4:  # Excellente marge
            sla_compliance_reward += ALPHA * 0.7
        elif margin > 0.25:  # Bonne marge
            sla_compliance_reward += ALPHA * 0.4
        elif margin > 0.1:  # Marge acceptable
            sla_compliance_reward += ALPHA * 0.2
        
        # Bonus supplémentaire si haute popularité (plus critique)
        if popularity > 0.7:
            sla_compliance_reward += ALPHA * 0.3
    else:
        # Pénalité progressive selon le dépassement
        overshoot = (query_latency / env.current_tsla) - 1.0
        
        if overshoot < 0.1:  # Léger dépassement
            sla_compliance_reward = -ALPHA * 1.5 * overshoot
        elif overshoot < 0.3:  # Dépassement modéré
            sla_compliance_reward = -ALPHA * 2.5 * overshoot
        else:  # Dépassement sévère
            sla_compliance_reward = -ALPHA * 3.5 * overshoot
        
        # Pénalité supplémentaire si haute popularité
        if popularity > 0.7:
            sla_compliance_reward -= ALPHA * 0.5
    
    # ========================================================================
    # 2. COST PENALTY AMÉLIORÉ
    # ========================================================================
    cost_penalty = query_cost * BETA
    
    if replica_action == 0 and replica_executed:  # CREATE_REPLICA
        replication_cost = env.data_gb * env.REPLICATION_COST_PER_GB
        
        # Pénalité adaptative selon le contexte
        if popularity > 0.7 and burst_score > 0.5:
            # Burst détecté avec haute popularité: coût justifié
            cost_penalty += replication_cost * 1.2
        elif popularity < 0.3:
            # Faible popularité: coût moins justifié
            cost_penalty += replication_cost * 2.5
        else:
            # Cas normal
            cost_penalty += replication_cost * 1.8
    
    # ========================================================================
    # 3. BUDGET EFFICIENCY REWARD AMÉLIORÉ
    # ========================================================================
    budget_ratio = env.current_budget / env.INITIAL_BUDGET
    budget_efficiency_reward = 0.0
    
    # Récompense progressive selon le budget restant
    if budget_ratio > 0.7:
        budget_efficiency_reward = GAMMA * 1.2  # Excellent
    elif budget_ratio > 0.5:
        budget_efficiency_reward = GAMMA  # Bon
    elif budget_ratio > 0.3:
        budget_efficiency_reward = GAMMA * 0.5  # Acceptable
    elif budget_ratio > 0.15:
        budget_efficiency_reward = -GAMMA * 0.3  # Préoccupant
    else:
        budget_efficiency_reward = -GAMMA * 1.5  # Critique
    
    # Ajustement selon la phase (début vs fin)
    progress_ratio = env.current_query / max(env.MAX_QUERIES, 1)
    if progress_ratio < 0.3 and budget_ratio < 0.4:
        # Budget faible tôt dans l'exécution: très mauvais
        budget_efficiency_reward -= GAMMA * 0.5
    elif progress_ratio > 0.7 and budget_ratio > 0.5:
        # Budget confortable en fin d'exécution: excellent
        budget_efficiency_reward += GAMMA * 0.3
    
    # ========================================================================
    # 4. INSTABILITY PENALTY (pénaliser changements fréquents de réplication)
    # ========================================================================
    instability_penalty = 0.0
    if replica_action in [0, 1] and replica_executed:  # CREATE ou DELETE
        # Vérifier si on change souvent de stratégie
        if hasattr(env, 'last_replica_action') and env.last_replica_action != replica_action:
            instability_penalty = DELTA
    
    # ========================================================================
    # 5. STRATEGIC TIMING REWARD AMÉLIORÉ (PLSA avancé)
    # ========================================================================
    strategic_timing_reward = 0.0
    
    if replica_action == 0 and replica_executed:  # CREATE_REPLICA
        # Évaluation multi-critères pour la création
        if popularity > 0.75 and (burst_score > 0.4 or trend_score > 0.3):
            # Parfait: haute popularité + burst ou tendance positive
            strategic_timing_reward = EPSILON * 1.3
        elif popularity > 0.65 and trend_score > 0.2:
            # Très bon: popularité élevée + tendance positive
            strategic_timing_reward = EPSILON * 1.1
        elif popularity > 0.5:
            # Bon: popularité modérée
            strategic_timing_reward = EPSILON * 0.7
        elif popularity < 0.3 and trend_score < 0:
            # Très mauvais: faible popularité + tendance négative
            strategic_timing_reward = -EPSILON * 1.2
        elif popularity < 0.4:
            # Mauvais: popularité faible
            strategic_timing_reward = -EPSILON * 0.8
        else:
            # Acceptable
            strategic_timing_reward = EPSILON * 0.3
        
        # Bonus proactif si création anticipe un burst
        if burst_score > 0.6 and env.current_replica_count == 0:
            strategic_timing_reward += ETA * 0.8
    
    elif replica_action == 1 and replica_executed:  # DELETE_REPLICA
        # Évaluation pour la suppression
        if popularity < 0.25 and (burst_score < 0.1 and trend_score < 0):
            # Parfait: faible popularité + pas de burst + tendance négative
            strategic_timing_reward = EPSILON * 0.8
        elif popularity < 0.35 and trend_score < -0.2:
            # Bon: popularité faible + tendance négative
            strategic_timing_reward = EPSILON * 0.6
        elif popularity > 0.7 or burst_score > 0.5:
            # Très mauvais: haute popularité ou burst détecté
            strategic_timing_reward = -EPSILON * 1.3
        elif popularity > 0.5:
            # Mauvais: popularité modérée
            strategic_timing_reward = -EPSILON * 0.7
        else:
            # Acceptable
            strategic_timing_reward = EPSILON * 0.2
    
    elif replica_action == 2:  # DO_NOTHING
        # Récompenser la patience stratégique
        if env.current_replica_count == 0:
            if 0.4 < popularity < 0.65 and trend_score > 0:
                # Bonne patience: attendre que ça monte
                strategic_timing_reward = EPSILON * 0.4
            elif popularity < 0.4 and burst_score < 0.2:
                # Patience justifiée
                strategic_timing_reward = EPSILON * 0.25
        elif env.current_replica_count > 0:
            if popularity > 0.5:
                # Bon: maintenir les réplicas quand nécessaire
                strategic_timing_reward = EPSILON * 0.3
    
    # ========================================================================
    # 6. TSLA ADJUSTMENT QUALITY REWARD AMÉLIORÉ
    # ========================================================================
    tsla_adjustment_reward = 0.0
    sla_violation_rate = env.sla_violations / max(env.current_query, 1)
    
    if tsla_action == 0 and tsla_executed:  # INCREASE_TSLA
        # Augmenter TSLA est bon si:
        # - Violations fréquentes du SLA
        # - Budget faible (trade-off coût/performance)
        # - Popularité faible (moins critique)
        # - Tendance négative (demande décroissante)
        
        score = 0.0
        
        if sla_violation_rate > 0.35:  # Violations sévères
            score += 1.2
        elif sla_violation_rate > 0.2:  # Violations modérées
            score += 0.8
        
        if budget_ratio < 0.25:  # Budget critique
            score += 1.0
        elif budget_ratio < 0.4:  # Budget faible
            score += 0.6
        
        if popularity < 0.3:  # Faible popularité
            score += 0.5
        
        if trend_score < -0.3:  # Tendance négative forte
            score += 0.4
        
        if score > 1.5:
            tsla_adjustment_reward = ZETA * 1.2  # Excellente décision
        elif score > 0.8:
            tsla_adjustment_reward = ZETA * 0.8  # Bonne décision
        elif score > 0:
            tsla_adjustment_reward = ZETA * 0.4  # Décision acceptable
        else:
            tsla_adjustment_reward = -ZETA * 0.6  # Mauvaise décision
    
    elif tsla_action == 1 and tsla_executed:  # DECREASE_TSLA
        # Diminuer TSLA est bon si:
        # - Peu de violations (marge de manœuvre)
        # - Budget confortable
        # - Popularité élevée (plus critique)
        # - Tendance positive ou burst (demande croissante)
        # - Capacité suffisante (réplicas)
        
        score = 0.0
        
        if sla_violation_rate < 0.05:  # Très peu de violations
            score += 1.2
        elif sla_violation_rate < 0.15:  # Peu de violations
            score += 0.7
        
        if budget_ratio > 0.6:  # Budget confortable
            score += 0.9
        elif budget_ratio > 0.4:  # Budget acceptable
            score += 0.5
        
        if popularity > 0.7 or burst_score > 0.5:  # Haute demande
            score += 0.8
        
        if trend_score > 0.3:  # Tendance positive
            score += 0.5
        
        if env.current_replica_count >= 2:  # Capacité suffisante
            score += 0.6
        
        if score > 2.0:
            tsla_adjustment_reward = ZETA * 1.3  # Excellente décision
        elif score > 1.2:
            tsla_adjustment_reward = ZETA  # Bonne décision
        elif score > 0.5:
            tsla_adjustment_reward = ZETA * 0.5  # Décision acceptable
        else:
            tsla_adjustment_reward = -ZETA * 0.8  # Mauvaise décision
    
    elif tsla_action == 2:  # MAINTAIN_TSLA
        # Maintenir TSLA est bon si la situation est stable
        if 0.08 <= sla_violation_rate <= 0.18:
            # Taux de violation dans la zone cible
            tsla_adjustment_reward = ZETA * 0.4
        elif 0.05 <= sla_violation_rate <= 0.25:
            # Taux acceptable
            tsla_adjustment_reward = ZETA * 0.2
        else:
            # Situation instable, devrait ajuster
            tsla_adjustment_reward = -ZETA * 0.2
    
    # Pénalité progressive pour ajustements TSLA trop fréquents
    if hasattr(env, 'tsla_history') and len(env.tsla_history) >= 10:
        recent_changes = sum(1 for action, _ in env.tsla_history[-10:] if action != 'MAINTAIN')
        if recent_changes > 6:  # Très instable
            tsla_adjustment_reward -= ZETA * 0.7
        elif recent_changes > 4:  # Instable
            tsla_adjustment_reward -= ZETA * 0.4
    
    # ========================================================================
    # 7. BONUS PROACTIF (nouveau)
    # ========================================================================
    proactive_bonus = 0.0
    
    # Bonus pour anticipation intelligente
    if replica_action == 0 and replica_executed:
        # Création proactive avant un burst
        if burst_score > 0.5 and trend_score > 0.2:
            proactive_bonus = ETA * 0.9
        elif trend_score > 0.4:  # Anticipe tendance positive
            proactive_bonus = ETA * 0.6
    
    # Bonus pour coordination réplication-TSLA
    if replica_action == 0 and tsla_action == 1 and replica_executed and tsla_executed:
        # Créer réplica + diminuer TSLA = bonne coordination
        if popularity > 0.6:
            proactive_bonus += ETA * 0.5
    elif replica_action == 1 and tsla_action == 0 and replica_executed and tsla_executed:
        # Supprimer réplica + augmenter TSLA = bonne coordination
        if popularity < 0.4:
            proactive_bonus += ETA * 0.4
    
    # ========================================================================
    # COMBINER TOUTES LES COMPOSANTES
    # ========================================================================
    reward = (
        sla_compliance_reward +
        budget_efficiency_reward +
        strategic_timing_reward +
        tsla_adjustment_reward +
        proactive_bonus -
        cost_penalty -
        instability_penalty
    )
    
    # Sauvegarder les actions et métriques pour le prochain step
    env.last_replica_action = replica_action
    env.last_tsla_action = tsla_action
    
    # Sauvegarder les composantes de récompense pour analyse (optionnel)
    if hasattr(env, 'reward_components'):
        env.reward_components = {
            'sla_compliance': sla_compliance_reward,
            'budget_efficiency': budget_efficiency_reward,
            'strategic_timing': strategic_timing_reward,
            'tsla_adjustment': tsla_adjustment_reward,
            'proactive_bonus': proactive_bonus,
            'cost_penalty': -cost_penalty,
            'instability_penalty': -instability_penalty,
            'total': reward,
            'popularity': popularity,
            'burst_score': burst_score,
            'trend_score': trend_score
        }
    
    return reward
