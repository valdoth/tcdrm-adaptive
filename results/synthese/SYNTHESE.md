# Synthèse des évaluations

Coût total en $ (moyenne ± écart-type sur les graines) ; violations T_SLA et C_SLA du contrat ; p = test de Wilcoxon apparié sur le coût (agents seulement).

## Validation — en_ligne

Charge variable, apprentissage en ligne (protocole du mémoire)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 4.99 ± 0.80 | [4.41 ; 5.56] | 68.1 | 30.4 | 46 | 6.4 | 0.5 | 0.6 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 4.31 ± 0.10 | [4.24 ; 4.38] | 46.1 | 14.0 | 69 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 14.74 ± 0.21 | [14.59 ; 14.90] | 52.9 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 14.61 ± 0.22 | [14.45 ; 14.76] | 45.6 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |

## Validation — figee

Charge variable, politique figée (audit 4.4)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 4.60 ± 0.27 | [4.40 ; 4.79] | 69.4 | 20.8 | 47 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 4.32 ± 0.10 | [4.25 ; 4.40] | 48.7 | 14.3 | 72 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 17.35 ± 1.23 | [16.48 ; 18.23] | 48.8 | 5.9 | 40 | 7.1 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 14.61 ± 0.22 | [14.45 ; 14.76] | 45.6 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |

## Validation — rafales

Pics de popularité soudains (audit 5.1, 5.2)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.43 ± 0.21 | [7.27 ; 7.58] | 495.9 | 41.8 | 362 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.82 ± 0.25 | [6.64 ; 6.99] | 343.0 | 60.2 | 40 | 6.3 | 0.3 | 0.3 |  |  |
| Simple | Q-Learning | 4.38 ± 0.15 | [4.28 ; 4.49] | 49.2 | 16.6 | 44 | 6.3 | 0.3 | 0.6 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 4.30 ± 0.11 | [4.22 ; 4.38] | 39.0 | 16.6 | 58 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 25.84 ± 3.40 | [23.41 ; 28.28] | 283.3 | 68.8 | 234 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 25.06 ± 0.90 | [24.41 ; 25.70] | 411.1 | 35.5 | 40 | 6.6 | 0.4 | 0.4 |  |  |
| Complex | Q-Learning | 14.83 ± 0.20 | [14.68 ; 14.97] | 53.0 | 0.3 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 14.65 ± 0.23 | [14.48 ; 14.82] | 45.6 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |

## Validation — sans_bandit

Ablation : ρ = 1, autres méta-contrôleurs actifs (audit 5.6)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 7.05 ± 0.33 | [6.81 ; 7.28] | 420.0 | 31.2 | 46 | 4.7 | 1.0 | 1.2 | 0.2324 | 0.1641 |
| Simple | Rainbow DQN | 7.00 ± 0.35 | [6.75 ; 7.24] | 405.3 | 31.6 | 42 | 4.6 | 0.7 | 0.9 | 0.2324 | 0.6406 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 26.20 ± 0.55 | [25.80 ; 26.59] | 602.4 | 21.9 | 40 | 5.5 | 0.4 | 0.5 | 0.2754 | 0.1289 |
| Complex | Rainbow DQN | 26.19 ± 0.66 | [25.72 ; 26.66] | 594.6 | 13.2 | 40 | 5.3 | 0.9 | 0.9 | 0.2324 | 0.3008 |

## Validation — sans_meta

Ablation : méta-contrôleurs et bandit désactivés (audit 5.6)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.25 ± 0.28 | [7.05 ; 7.45] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 6.97 ± 0.36 | [6.72 ; 7.23] | 395.6 | 39.5 | 40 | 4.5 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 7.10 ± 0.23 | [6.93 ; 7.26] | 420.8 | 28.8 | 47 | 4.1 | 0.0 | 0.0 | 0.2324 | 0.25 |
| Simple | Rainbow DQN | 7.02 ± 0.36 | [6.76 ; 7.28] | 414.5 | 35.8 | 44 | 4.3 | 0.0 | 0.0 | 0.1602 | 0.04883 |
| Complex | TCDRM | 27.08 ± 2.18 | [25.52 ; 28.64] | 258.3 | 71.7 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.23 ± 0.62 | [25.79 ; 26.67] | 597.7 | 20.1 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 27.03 ± 0.80 | [26.46 ; 27.60] | 732.4 | 13.6 | 40 | 4.7 | 0.0 | 0.0 | 0.9219 | 0.003906 |
| Complex | Rainbow DQN | 26.32 ± 0.60 | [25.89 ; 26.75] | 610.2 | 18.9 | 40 | 5.0 | 0.1 | 0.1 | 0.3223 | 0.03125 |

## Validation — stockage_x5

Charge variable, coût de stockage ×5 (audit 5.7)

| Régime | Stratégie | Coût ($) | IC 95 % | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions | Oscillations | p vs TCDRM | p vs Glouton |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 7.38 ± 0.28 | [7.18 ; 7.58] | 466.0 | 50.3 | 280 | 6.0 | 0.0 | 0.0 |  |  |
| Simple | Glouton | 7.07 ± 0.33 | [6.84 ; 7.30] | 397.0 | 36.3 | 40 | 4.4 | 0.1 | 0.1 |  |  |
| Simple | Q-Learning | 5.33 ± 0.77 | [4.78 ; 5.88] | 81.3 | 31.8 | 46 | 6.6 | 0.6 | 0.9 | 0.001953 | 0.001953 |
| Simple | Rainbow DQN | 4.51 ± 0.10 | [4.44 ; 4.58] | 46.1 | 14.0 | 69 | 6.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | TCDRM | 27.40 ± 2.18 | [25.84 ; 28.96] | 258.3 | 71.8 | 202 | 12.0 | 0.0 | 0.0 |  |  |
| Complex | Glouton | 26.33 ± 0.58 | [25.92 ; 26.75] | 593.6 | 21.0 | 40 | 5.3 | 0.3 | 0.3 |  |  |
| Complex | Q-Learning | 15.02 ± 0.21 | [14.87 ; 15.17] | 52.9 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |
| Complex | Rainbow DQN | 14.88 ± 0.22 | [14.73 ; 15.04] | 45.6 | 0.0 | 40 | 8.0 | 0.0 | 0.0 | 0.001953 | 0.001953 |

## Benchmark — en_ligne

Charge stable, apprentissage en ligne

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.30 ± 0.09 | 33.6 | 0.0 | 41 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.80 ± 0.08 | 52.4 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — figee

Charge stable, politique figée

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.29 ± 0.09 | 32.8 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.73 ± 0.09 | 47.4 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — sans_meta

Charge stable, sans méta-contrôleurs

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.44 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.35 ± 0.14 | 40.8 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.29 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.41 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.70 ± 0.09 | 45.4 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.68 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |

## Benchmark — stockage_x5

Charge stable, stockage ×5

| Régime | Stratégie | Coût ($) | Viol. T_SLA | Viol. C_SLA | 1er réplica | Créations | Suppressions |
|---|---|---|---|---|---|---|---|
| Simple | TCDRM | 5.49 ± 0.07 | 189.4 | 1.0 | 199 | 2.0 | 0.0 |
| Simple | Glouton | 4.36 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Simple | Q-Learning | 4.37 ± 0.10 | 34.2 | 0.0 | 41 | 2.0 | 0.0 |
| Simple | Rainbow DQN | 4.36 ± 0.09 | 32.2 | 0.2 | 40 | 2.0 | 0.0 |
| Complex | TCDRM | 17.55 ± 0.12 | 204.0 | 0.0 | 199 | 5.0 | 0.0 |
| Complex | Glouton | 14.85 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Q-Learning | 14.97 ± 0.08 | 52.4 | 0.0 | 40 | 5.0 | 0.0 |
| Complex | Rainbow DQN | 14.85 ± 0.09 | 44.0 | 0.0 | 40 | 5.0 | 0.0 |

## Figures

- `synthese_validation_cout.png` : coût de chaque stratégie dans les six validations (IC 95 %).
- `synthese_validation_violations.png` : violations T_SLA du contrat dans les six validations.
- `synthese_ablation.png` : part du gain due au bandit ρ et aux méta-contrôleurs.
- `synthese_benchmark_cout.png` : coût au benchmark en charge stable.
- Par graine : `validation/<variante>/images/seed_<graine>/` (dont `fig8_budget_csla.png` et `metrics_*.png`) ; CSV par requête : `validation/<variante>/metrics/seed_<graine>/`.
