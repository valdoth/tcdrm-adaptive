# Couverture des objectifs du Sujet 1 (TCDRM-ADAPTIVE)

État de couverture point par point du Sujet 1 (« TCDRM-ADAPTIVE », `Sujets_TCDRM_v2.pdf`), avec les réserves honnêtes à connaître pour la soutenance.

## Axes de travail du sujet

| Exigence du PDF | Réalisé | Comment |
|---|---|---|
| 1. Modélisation en **problème de décision séquentiel** | ✓ | MDP complet : à chaque requête, l'agent observe un état et choisit NOOP/REPLICATE/DELETE ; plus aucune cadence ni fenêtre fixe |
| 2. **États, actions, récompense** définis | ✓ | État 9D (latence, budget, réplicas, coût, violations, progression, popularité, régime) ; 3 actions ; récompense multi-termes (SLA, coût linéaire + dépassement, détention impopulaire, déclenchement correct, thrash…) **persistée par agent** pour l'alignement train/eval |
| 3. **Algorithme d'apprentissage intégré** | ✓ | Exactement les deux techniques citées par le sujet : *Q-learning tabulaire* + *extension Deep Q-Network* (Rainbow 6/6 composants), plus les méta-contrôleurs Q-Threshold pour les seuils |
| 4. **Ajustement dynamique des seuils T_SLA, C_SLA, P_SLA** | ✓ sauf C_SLA | T_SLA : multiplicateur appris par requête ; P_SLA : seuil d'éligibilité popularité appris ; ΔT de suppression (Algorithm 3) appris. **C_SLA volontairement statique** — choix assumé |
| 5. **Validation par simulation multi-cloud** | ✓ (à finaliser) | CloudSimPlus (36 DC, 72 VMs, cloudlets réels), benchmark 4 modèles, figures ; il reste à lancer le run final post-correctifs |

## Techniques et résultats attendus

- **Optimisation sous contraintes** ✓ — c'est précisément la justification de l'exception C_SLA : le sujet demande le « respect strict du budget et des SLA », et un budget client est un **contrat**, pas un paramètre à assouplir. L'adaptativité budgétaire existe quand même, mais du bon côté : le budget restant est une dimension d'état, l'urgence budgétaire module la pénalité de coût, et la réplication est bloquée à budget épuisé. On apprend *sous* la contrainte au lieu de la modifier — argument solide en soutenance.
- **Apprentissage en ligne** ✓ — mise à jour continue pendant le benchmark (agents + méta-contrôleurs), exactement la « mise à jour continue des politiques de décision » du PDF.
- **Décisions plus stables** ✓ — pénalité de thrashing apprise, coût d'événement du méta-contrôleur (plus d'ouverture-éclair), fin de la réplication sur données inconnues.
- **Réduction des coûts inutiles** ✓ — coût de détention impopulaire + coût linéaire continu ; les figures montrent déjà RL < TCDRM < NoRepLc en coût BW.
- **Suppression de réplicas apprise** ✓ dans le mécanisme (action DELETE + ΔT méta) — mais noter qu'en régime `steady` elle ne s'exprime presque pas (rien ne refroidit jamais) ; ce sont les régimes `variable`/`burst` qui la démontrent. Prévoir une figure en workload dynamique pour illustrer cet objectif-là.

## Les deux choses qui restent pour dire « objectifs atteints » avec preuves

1. **Le run final** : les artefacts actuels sont antérieurs aux derniers correctifs (bridge, Rainbow, coût linéaire, parité des masques). `./run_complete_workflow.sh --episodes 1500` produira les figures qui font foi.
2. **Pour un mémoire « scientifiquement renforcé »** (l'ambition affichée du document) : une validation multi-seeds avec intervalles de confiance, et une figure de comparaison en workload `variable`/`burst` où les seuils statiques de TCDRM se dégradent face aux agents — c'est là que la thèse du sujet (« seuils statiques → politiques auto-apprenantes ») se démontre le plus visiblement.

## Conclusion

Les 5 axes, les techniques imposées et les résultats attendus sont couverts, avec une exception unique (C_SLA statique) qui est défendable comme un choix de conception, pas un manque.
