# Limites des résultats de TCDRM-ADAPTIVE

Inventaire des limites qui pèsent sur l'interprétation des résultats publiés dans le
mémoire (tableau 3.5 et figures du chapitre 3). Chaque limite indique d'où elle vient
(code, protocole ou mesure) et comment la vérifier.

Mesures réalisées le 12/10/2026 sur les modèles livrés (`tcdrm_gym/models/*_final.*`),
avec `tcdrm_gym/utils/state_coverage.py`.

## 1. Couverture de l'espace d'états

### 1.1 Seule une petite partie des 1 458 états est atteinte

| Politique (60 épisodes, régimes variable/burst, simple/complexe alternés) | États atteints | Couverture |
|---|---|---|
| Aléatoire | 120 | 8,2 % |
| Q-Learning, avec exploration | 128 | 8,8 % |
| Q-Learning, sans exploration | 71 | 4,9 % |
| Rainbow, avec exploration (NoisyNet) | 141 | 9,7 % |
| Rainbow, sans exploration | 147 | 10,1 % |
| Q-Learning, pendant l'entraînement (compteurs `visit_counts`, 1,5 M pas) | 148 | 10,2 % |

Pour Q-Learning, 9,1 % des couples état-action ont été visités au moins une fois, et
les 50 états les plus fréquents concentrent 93,9 % des visites. Rainbow travaille sur
l'état continu (11 dimensions), mesuré ici sur la même grille à titre de référence
commune.

### 1.2 Cause : la grille contient surtout des états inatteignables

Une politique aléatoire ne fait pas mieux que les agents : le problème vient de la
définition des états, pas d'abord de l'exploration.

| Dimension | Niveaux | Constat |
|---|---|---|
| `BUD` (budget restant) | ≥ 0,6 / 0,3–0,6 / < 0,3 | Le budget observé ne descend jamais sous 0,55 (`INITIAL_BUDGET = 60 $`, coût d'un épisode ≈ 4 à 18 $). Le niveau `< 0,3` n'est jamais atteint et le niveau `0,3–0,6` presque jamais (moins de 1 % des pas). **Deux tiers de la grille sont donc inatteignables.** |
| `GAIN` (popularité) | < 0,2 / 0,2–0,6 / > 0,6 | Ne dépend pas de la politique (répartition identique pour toutes les politiques) : la popularité croît avec le temps, donc `GAIN` est fortement lié à `PROG`. Les combinaisons « popularité faible en fin d'épisode » sont rares. |
| `NET` (réplicas) | ≥ 0,5 / > 0 / 0 | Déséquilibré : de 45 % (Q-Learning sans exploration) à 94 % (aléatoire) des pas au niveau ≥ 0,5. |
| `COST` | < 0,4 / 0,4–0,7 / ≥ 0,7 | Niveau bas majoritaire (62 à 71 % des pas), sauf pour Q-Learning sans exploration (20 %). |

Conséquence (modèles livrés) : la Q-table contient des valeurs jamais mises à jour pour environ 90 % des
états. Si l'agent rencontre l'un d'eux en évaluation, il agit selon l'initialisation
(optimiste à 1,0) et non selon un apprentissage.

### 1.3 Dimensions de l'état continu (Rainbow)

Sur 60 épisodes, la dimension `budget` n'occupe que 5 bins sur 10 (valeurs entre 0,55
et 1,0) ; les autres dimensions continues couvrent toute leur plage.

### 1.4 Corrections apportées (branche `fix/limites-couverture-etats`)

La grille de 1 458 états est conservée. Les états qu'elle contient sont rendus atteignables
par l'environnement d'entraînement (le benchmark n'est pas modifié) :

- **Départs exploratoires** (*exploring starts*, Sutton & Barto 2018, §5.3) :
  `TcdrmSimulation.applyExploringStart`, activé par `exploringStartProb` (0 par défaut,
  0,5 dans `train_cloudsim.py --exploring-start-prob`). Un épisode sur deux démarre avec,
  tirés de façon stratifiée sur les niveaux de la grille (1/3 par niveau) : l'instant de
  départ, le budget restant, le nombre de réplicas déjà en place, la popularité maximale ;
  et, une fois sur deux, une fraction des données primaires regroupée sur un même site.
- **Calendrier ε du Q-Learning** calé sur 80 % des épisodes réellement entraînés.
- **Suivi de la couverture** pendant l'entraînement des deux agents (colonne
  `grid_coverage_pct` de `progress.csv`, comptes sauvés dans `*_coverage.npy`).
- **Discrétisation unique** (`tcdrm_gym/envs/state_grid.py`) partagée par l'entraînement,
  le pont d'évaluation et le diagnostic (vérifiée identique à l'ancienne sur 240 000 états).

Couverture obtenue par une politique aléatoire (référence indépendante des agents) :

| Configuration | États atteints | Couverture |
|---|---|---|
| Avant (60 épisodes, départ habituel) | 120 | 8,2 % |
| ES = 0,5, 1 500 épisodes (conditions d'entraînement) | 973 | 66,7 % |
| Union de toutes les mesures avec ES (2 100 épisodes) | 1 029 | 70,6 % |

### 1.5 États physiquement impossibles (270 états)

Dix configurations (réplicas × latence × coût) ne sont jamais observées, quel que soit le
départ, soit 10 × 27 = 270 états. Elles sont incompatibles avec le modèle réseau du
simulateur, où coût et latence d'une requête proviennent du même transfert de données :

| Régime | Réplicas | Configuration impossible | Constat mesuré |
|---|---|---|---|
| Simple | 0 | latence < 0,4·T avec coût ≥ 0,4·C (2 configurations) ; latence 0,4–0,7·T avec coût ≥ 0,7·C | Sur 9 551 pas : coût ≥ 0,4·C ⇒ latence ≥ 0,56·T ; latence < 0,4·T ⇒ coût ≤ 0,23·C |
| Complexe | 0 | latence < 0,7·T avec coût ≥ 0,4·C (2) ; latence 0,4–0,7·T quel que soit le coût (3) | Sur 7 418 pas, latence ≥ 0,71·T, même données regroupées |
| Complexe | 1–49 % | latence < 0,4·T avec coût ≥ 0,4·C (2) | Sur 30 370 pas, latence jamais < 0,4·T |

Les rendre atteignables imposerait de modifier la physique du simulateur (latences,
tarifs). Les 1 188 états restants sont physiquement possibles : 1 029 (86,6 %) ont été
atteints ; les 159 autres sont des combinaisons rares de configurations observées.

**Artefact à signaler :** l'observation initiale d'un épisode (avant toute requête) a une
latence et un coût nuls ; elle place l'agent dans un état « latence basse, coût bas » qui
n'existe pas physiquement pour certaines configurations (par exemple complexe sans réplica).

## 2. Entraînement

- **Calendrier d'exploration ε du Q-Learning inadapté.** ε décroît linéairement sur
  `TCDRM_Q_EPS_STEPS = 5000` épisodes, mais l'entraînement s'arrête à 1 500 épisodes :
  ε vaut encore **0,715** à la fin (environ 85 % d'actions aléatoires en moyenne sur
  l'entraînement). Le mémoire indique une décroissance jusqu'à ε_min = 0,05.
- **Double Q-Learning inactif.** Avec les traces d'éligibilité (λ = 0,6), les deux
  tables reçoivent la même correction : `Q_A == Q_B` est vérifié sur le modèle livré.
- **Asymétrie d'information entre agents.** Rainbow reçoit 11 dimensions, Q-Learning en
  discrétise 7 (les dimensions 4, 5, 9 et 10 sont ignorées). La comparaison des deux
  agents n'est pas faite à information égale.
- **Graine unique.** Un seul entraînement par agent ; la variabilité entre graines n'est
  pas mesurée.
- **Régimes d'entraînement.** Le code tire à chaque épisode un régime parmi `steady`,
  `variable` et `burst` (`TrainingEnvironment.reset`), alors que le mémoire décrit une
  alternance `variable`/`burst` seulement.

## 3. Protocole d'évaluation

- **Benchmark en charge stable uniquement.** Entraînement en régimes `variable` et
  `burst`, benchmark en `steady` (même requête répétée, popularité croissante). Le
  benchmark mesure l'anticipation de la réplication, pas l'adaptation à des changements
  de popularité.
- **Aucune suppression pendant le benchmark.** Le nombre de réplicas croît de façon
  monotone pour toutes les stratégies : ni le mécanisme anti-thrashing, ni la pénalité
  de suppression prématurée, ni le méta-contrôleur de la fenêtre de suppression ΔT ne
  sont sollicités.
- **Apprentissage actif pendant l'évaluation.** Les agents et les méta-contrôleurs
  continuent d'apprendre pendant le benchmark ; aucune évaluation à politique figée.
- **Exécution unique.** Une graine et une séquence de requêtes par régime : pas
  d'intervalle de confiance. L'écart de coût Q-Learning / TCDRM en régime complexe
  (+3 %) est dans l'ordre de grandeur d'une variation possible entre exécutions.
- **Deux compteurs de violations.** Les tableaux de bord comptent les dépassements du
  seuil effectif appris ; le tableau 3.5 compte ceux du T_SLA contractuel.
- **Violations de C_SLA non mesurées séparément.** Seul le coût total est rapporté.

## 4. Modèle et simulateur

- **Baseline TCDRM réimplémentée** avec une popularité EMA au lieu du taux cumulatif de
  l'article : reproduction qualitative, pas stricte.
- **Coût de stockage par requête** fondé sur la convention 1 000 requêtes = 1 mois.
- **Détection d'oscillation globale** (fenêtre de 10 actions, tous fragments
  confondus) : peut sanctionner des opérations légitimes sur des fragments distincts.
- **Paramètres encore calés sur TCDRM** : constante de l'EMA dérivée de P_SLA, seuils
  des méta-contrôleurs réinitialisés à la valeur du contrat à chaque épisode, bornes et
  résolution des intervalles fixées.

## 5. Attribution des gains

- Pas d'ablation des méta-contrôleurs (mêmes agents sous seuils fixes).
- Pas d'ablation des composantes de Rainbow ni des dimensions 9–10 de l'état.

## Reproduire les mesures de couverture

```bash
# Couverture d'entraînement du Q-Learning (sans simulateur)
cd tcdrm_gym
uv run python -m utils.state_coverage --from-visits models/qlearning_cloudsim_final.pkl

# Couverture en simulation : lancer le TrainingServer depuis une COPIE des modèles
# (il réécrit les Q-tables des méta-contrôleurs à chaque fin d'épisode)
uv run python -m utils.state_coverage --agent random --episodes 60
uv run python -m utils.state_coverage --agent qlearning --model models/qlearning_cloudsim_final.pkl --episodes 60
uv run python -m utils.state_coverage --agent rainbow --model models/rainbow_cloudsim_final.pt --episodes 60
```
