# Synthèse des évaluations

Coût total en $ (moyenne ± écart-type sur les graines) ; violations T_SLA et C_SLA du contrat ; p = test de Wilcoxon apparié sur le coût (agents seulement).

## Validation — en_ligne

Charge variable, apprentissage en ligne (protocole du mémoire)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 4.18 ± 0.11 | [4.11 ; 4.26] | 25.7 | 14.3 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 5.03 ± 0.32 | [4.80 ; 5.26] | 142.4 | 13.4 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 15.49 ± 1.10 | [14.70 ; 16.27] | 46.5 | 11.0 | 40 | 8.5 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 15.31 ± 0.29 | [15.11 ; 15.52] | 45.7 | 5.9 | 40 | 8.2 | 0.2 | 0.2 | 0.001953 | 0.001953 |

## Validation — figee

Charge variable, politique figée (audit 4.4)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 4.18 ± 0.11 | [4.11 ; 4.26] | 25.7 | 14.3 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 4.91 ± 0.20 | [4.77 ; 5.05] | 131.8 | 13.6 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 16.40 ± 2.67 | [14.49 ; 18.31] | 53.8 | 16.7 | 40 | 8.5 | 0.4 | 0.4 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 15.44 ± 0.29 | [15.23 ; 15.64] | 45.7 | 10.7 | 40 | 8.5 | 0.5 | 0.5 | 0.001953 | 0.001953 |

## Validation — rafales

Pics de popularité soudains (audit 5.1, 5.2)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.43 ± 0.21 | [7.27 ; 7.58] | 495.9 | 41.8 | 362 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.82 ± 0.25 | [6.64 ; 6.99] | 343.0 | 60.2 | 40 | 6.3 | 0.3 | 0.3 |  |  |
| Simple | Q-Learning | 4.24 ± 0.13 | [4.15 ; 4.33] | 27.0 | 19.3 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 5.26 ± 0.14 | [5.17 ; 5.36] | 166.3 | 15.4 | 41 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 25.84 ± 3.40 | [23.41 ; 28.28] | 283.3 | 68.8 | 234 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 25.06 ± 0.90 | [24.41 ; 25.70] | 411.1 | 35.5 | 40 | 6.6 | 0.4 | 0.4 |  |  |
| Complex | Q-Learning | 14.82 ± 0.34 | [14.58 ; 15.06] | 47.8 | 4.5 | 40 | 8.2 | 0.2 | 0.2 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 15.12 ± 0.51 | [14.75 ; 15.48] | 47.7 | 1.5 | 40 | 8.1 | 0.1 | 0.1 | 0.001953 | 0.001953 |

## Validation — sans_bandit

Ablation : ρ = 1, autres méta-contrôleurs actifs (audit 5.6)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 6.99 ± 0.35 | [6.74 ; 7.24] | 393.9 | 39.5 | 40 | 4.7 | 0.4 | 0.5 | 0.1602 | 0.75 |
| Simple | Rainbow DQN | 7.72 ± 0.27 | [7.53 ; 7.91] | 584.9 | 16.3 | 483 | 2.5 | 0.0 | 0.0 | 0.005859 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 26.24 ± 0.62 | [25.80 ; 26.68] | 599.6 | 19.7 | 40 | 5.3 | 0.3 | 0.3 | 0.2754 | 0.07422 |
| Complex | Rainbow DQN | 26.35 ± 0.47 | [26.02 ; 26.69] | 628.6 | 18.4 | 60 | 5.0 | 0.1 | 0.1 | 0.4316 | 0.1309 |

## Validation — sans_meta

Ablation : méta-contrôleurs et bandit désactivés (audit 5.6)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.4 | 39.4 | 40 | 4.5 | 0.1 | 0.1 | 0.1602 | 0.5 |
| Simple | Rainbow DQN | 7.99 ± 0.38 | [7.71 ; 8.26] | 634.9 | 8.1 | 496 | 1.9 | 0.0 | 0.0 | 0.009766 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 26.70 ± 0.43 | [26.40 ; 27.01] | 691.5 | 10.3 | 40 | 4.9 | 0.1 | 0.1 | 0.8457 | 0.01953 |
| Complex | Rainbow DQN | 26.37 ± 0.48 | [26.03 ; 26.71] | 632.1 | 19.2 | 40 | 4.9 | 0.1 | 0.1 | 0.4316 | 0.1289 |

## Validation — stockage_x5

Charge variable, coût de stockage ×5 (audit 5.7)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.38 ± 0.28 | [7.18 ; 7.58] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 7.07 ± 0.33 | [6.84 ; 7.30] | 397.0 | 36.3 | 40 | 4.4 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 4.39 ± 0.11 | [4.31 ; 4.47] | 25.7 | 14.3 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 5.19 ± 0.32 | [4.96 ; 5.42] | 143.1 | 13.6 | 40 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.40 ± 2.18 | [25.84 ; 28.96] | 258.3 | 71.8 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.33 ± 0.58 | [25.92 ; 26.75] | 593.6 | 21.0 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 15.72 ± 1.12 | [14.92 ; 16.53] | 46.5 | 10.7 | 40 | 8.5 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 15.59 ± 0.29 | [15.38 ; 15.80] | 45.7 | 5.9 | 40 | 8.2 | 0.2 | 0.2 | 0.001953 | 0.001953 |

## Benchmark — en_ligne

Charge stable, apprentissage en ligne

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.80 ± 0.10 | 47.2 | 0.0 | 41 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.69 ± 0.09 | 45.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — figee

Charge stable, politique figée

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 19.77 ± 5.44 | 399.4 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.69 ± 0.09 | 45.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — sans_meta

Charge stable, sans méta-contrôleurs

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 17.96 ± 0.19 | 283.2 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.69 ± 0.09 | 45.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — stockage_x5

Charge stable, stockage ×5

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.49 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.36 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.36 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.36 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.55 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.85 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.96 ± 0.10 | 47.2 | 0.0 | 41 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.86 ± 0.09 | 45.0 | 0.0 | 40 | 5.0 | 0.0 |

## Figures

- `synthese_validation_cout.png` : coût de chaque stratégie dans les six validations (IC 95 %).
- `synthese_validation_violations.png` : violations T_SLA du contrat dans les six validations.
- `synthese_ablation.png` : part du gain due au bandit ρ et aux méta-contrôleurs.
- `synthese_benchmark_cout.png` : coût au benchmark en charge stable.
- Par graine : `validation/<variante>/images/seed_<graine>/` (dont `fig8_budget_csla.png` et `metrics_*.png`) ; CSV par requête : `validation/<variante>/metrics/seed_<graine>/`.
