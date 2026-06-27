# 📋 Changelog Diagrammes TCDRM-ADAPTIVE

## Version 3.0 - 21 juin 2026 (alignement sur le code réel)

### ⚠️ Constat

La Version 2.0 ci-dessous décrivait des "11 patterns cloud"
(`steady`, `burst`, `read_intensive`, `black_friday`, etc.), une fonction de
récompense à 7 composantes `ALPHA/BETA/GAMMA/DELTA/EPSILON/ZETA/ETA`, et
faisait référence à `algo.md` et à des chemins `python_rl/...`. **Aucun de
ces éléments n'existe dans le code actuel du dépôt** (`tcdrm_gym/`,
`src/main/java/org/tcdrm/adaptive/`). Il s'agissait d'une spécification
prévue qui n'a jamais été implémentée telle quelle, ou qui a été abandonnée
avant cette mise à jour.

### ✅ Corrections apportées

- **Workload** : remplacement des "11 patterns cloud" par le workload réel —
  10 relations (`R1`-`R10`) à popularité fixe (`WorkloadGenerator.java`) et
  3 stratégies de popularité interchangeables (`EMA`, `TinyLFU`, hybride),
  vérifiées dans `RelationPopularityTracker.java` / `TinyLFU.java`.
- **État RL** : 9 dimensions réelles (et non 8) — voir `buildRLState()` dans
  `TcdrmSimulation.java` et `CloudSimEnv` dans `cloudsim_env.py`.
- **Classes d'environnement** : `CloudSimQLearningEnv` / `CloudSimEnv` (et
  non `TcdrmQLearningEnv` / `TcdrmV2Env`, qui n'existent pas).
- **Fonction de récompense** : remplacement des 7 composantes fictives par
  les 7 composantes réelles de `TrainingEnvironment.calculateReward()` :
  `SLA_OK (r1=10)`, `SLA_VIOL (r2=20)`, `COST_OVER (r3=15)`,
  `REPL_COST (r4=5)`, `THRASH (r5=8)`, `UNUTILIZATION (-0.05/réplica)`,
  `INVALID_ACTION (-2)`.
- **Conformité "algo.md"** : `algo.md` a été supprimé du dépôt (fichier de
  notes redondant) ; le diagramme 10 référence désormais les fichiers/
  techniques sans numéros de ligne (instables dans le temps).
- **Périmètre** : les 10 diagrammes sont conservés. Les diagrammes 3, 4 et 5
  avaient été temporairement retirés en pensant qu'ils étaient redondants,
  avant de constater qu'ils manquaient en réalité à cause d'erreurs de
  génération (API `mermaid.ink`, 503/400) et non par choix de contenu. Ils
  ont été restaurés, corrigés (état 9D, Q(λ), n-step, Welford) et génèrent
  désormais sans erreur en local via Mermaid CLI.
- **Génération** : passage à Mermaid CLI (`mmdc`) en local — l'API
  `mermaid.ink` utilisée par la Version 2.0 renvoyait des erreurs 403/503/400
  pour plusieurs diagrammes.

---

## Version 2.0 - Février 2024

> ⚠️ **Cette version décrit une spécification qui n'a pas été retenue.** Les
> "11 patterns cloud", la fonction de récompense à composantes
> `ALPHA/BETA/GAMMA/...` et les chemins `python_rl/...` mentionnés ci-dessous
> ne correspondent à aucun code présent dans le dépôt actuel. Conservé pour
> mémoire ; se référer à la Version 3.0 ci-dessus pour l'état réel.

### 🎯 Modifications Majeures

#### **1. Suppression de PPO**
- ❌ Retrait de PPO de tous les diagrammes
- ✅ Focus sur 2 modèles RL : Q-Learning et DQN
- ✅ Comparaison 4 modèles au lieu de 5 : Q-Learning, DQN, TCDRM Statique, NOREP

**Raison** : PPO n'est pas utilisé actuellement dans le projet. Simplification de l'architecture.

---

#### **2. Ajout des Patterns Cloud Réels**

**Nouveaux patterns (28% des épisodes)** :
- ✅ `read_intensive` (12%) : 90% lectures, 10% écritures → E-commerce, CDN
- ✅ `write_intensive` (8%) : 30% lectures, 70% écritures → IoT, Logging
- ✅ `geo_distributed` (10%) : 40% EU, 35% US, 25% ASIA → Applications globales
- ✅ `black_friday` (2%) : Pic extrême 10x → Événements saisonniers

**Impact** :
- Couverture cas d'usage : 60% → **95%**
- Total patterns : 7 → **11**
- Distribution : 7 patterns base (72%) + 4 patterns cloud (28%)

---

#### **3. Documentation Améliorations Algorithmes**

**Q-Learning Amélioré** :
- ✅ Double Q-Learning (2 Q-tables, alternance aléatoire)
- ✅ Learning Rate Adaptatif (α_t = α_0 / (1 + 0.01×visits))
- ✅ Exploration Intelligente (probabilités ∝ 1/visits)
- ✅ Conformité 100% avec `algo.md` (ligne 44-54)

**DQN Avancé** :
- ✅ Double DQN (policy net sélection, target net évaluation)
- ✅ Dueling Architecture (V(s) + A(s,a) - mean(A))
- ✅ Prioritized Experience Replay (échantillonnage par TD-error)
- ✅ Soft Target Update (θ⁻ ← τ·θ + (1-τ)·θ⁻, τ=0.005)
- ✅ Conformité 100% avec `algo.md` (ligne 100-144)

---

### 📊 Diagrammes Modifiés

| Diagramme | Avant | Après | Changements |
|-----------|-------|-------|-------------|
| **Architecture Globale** | 3 modèles RL (Q-Learning, DQN, PPO) | 2 modèles RL (Q-Learning, DQN) | ❌ PPO supprimé<br/>✅ 11 patterns ajoutés |
| **Patterns Cloud** | N/A | NOUVEAU | ✅ Diagramme créé<br/>✅ Distribution 11 types<br/>✅ Cas d'usage |
| **Workflow Complet** | 3 phases avec PPO | 3 phases sans PPO | ❌ PPO supprimé<br/>✅ Timeline mise à jour |
| **Améliorations Algorithmes** | N/A | NOUVEAU | ✅ Diagramme créé<br/>✅ Double Q/DQN détaillé |
| **Processus de Décision** | Q-Learning simple | Q-Learning + DQN détaillé | ✅ Dueling Architecture<br/>✅ Double Q-Learning |
| **Comparaison Modèles** | 5 modèles | 4 modèles | ❌ PPO supprimé |
| **Timeline** | 40-75 min | 45-60 min | ✅ Temps ajusté |
| **Architecture Résultats** | Modèles + Métriques | Modèles + Métriques + Docs | ✅ Documentation ajoutée |
| **Fonction Récompense** | 5 composantes | 7 composantes | ✅ TSLA + Proactive ajoutés |
| **Conformité algo.md** | N/A | NOUVEAU | ✅ Diagramme créé<br/>✅ Vérification 100% |

---

### 📂 Fichiers Créés

#### **Nouveaux Fichiers**
- ✅ `workflow_diagrams_2024.md` : Diagrammes mis à jour (10 diagrammes Mermaid)
- ✅ `README_DIAGRAMS_2024.md` : Documentation des modifications
- ✅ `CHANGELOG_DIAGRAMS_2024.md` : Ce fichier

#### **Fichiers Conservés (Référence)**
- 📁 `workflow_diagrams_updated.md` : Version précédente avec PPO
- 📁 `diagrams/` : Anciens PNG (référence)

---

### 🎨 Nouveaux Diagrammes (10 total)

1. **Architecture Globale 2024** : 2 modèles RL + 11 patterns + baselines
2. **Patterns Cloud Réels** : Distribution 11 types + cas d'usage
3. **Workflow Complet 2024** : Entraînement → Simulation → Visualisation
4. **Améliorations Algorithmes** : Double Q/DQN + Dueling + PER
5. **Processus de Décision** : Q-Learning vs DQN détaillé
6. **Comparaison 4 Modèles** : Performance attendue sans PPO
7. **Timeline Workflow** : Gantt 45-60 min
8. **Architecture Résultats** : Modèles + Métriques + Documentation
9. **Fonction Récompense** : 7 composantes multi-objectif
10. **Conformité algo.md** : Vérification 100% Q-Learning + DQN

---

### 📈 Statistiques

#### **Patterns de Données**
| Métrique | Avant | Après | Amélioration |
|----------|-------|-------|--------------|
| Nombre de patterns | 7 | 11 | +57% |
| Couverture cas d'usage | ~60% | ~95% | +35% |
| Patterns cloud réels | 0 | 4 | ✅ Nouveau |
| Read/Write ratio | ❌ Non | ✅ Oui | ✅ |
| Origine géographique | ❌ Non | ✅ Oui | ✅ |
| Événements saisonniers | ❌ Non | ✅ Oui | ✅ |

#### **Algorithmes RL**
| Algorithme | Améliorations | Conformité algo.md |
|------------|---------------|-------------------|
| Q-Learning | 3 (Double Q, LR adaptatif, Exploration) | ✅ 100% |
| DQN | 4 (Double DQN, Dueling, PER, Soft Update) | ✅ 100% |
| PPO | ❌ Supprimé | N/A |

---

### 🔧 Génération des PNG

#### **Commande Recommandée**
```bash
# Installer Mermaid CLI
npm install -g @mermaid-js/mermaid-cli

# Générer tous les diagrammes
cd docs
mmdc -i workflow_diagrams_2024.md -o diagrams/2024/
```

#### **Diagrammes PNG Générés**
```
docs/diagrams/2024/
├── 01_architecture_globale_2024.png
├── 02_patterns_cloud_reels.png
├── 03_workflow_complet_2024.png
├── 04_ameliorations_algorithmes.png
├── 05_processus_decision_2024.png
├── 06_comparaison_4_modeles.png
├── 07_timeline_workflow_2024.png
├── 08_architecture_resultats_2024.png
├── 09_fonction_recompense.png
└── 10_conformite_algo.png
```

---

### 📖 Documentation Associée

#### **Analyses Créées**
- `ANALYSE_DOUBLE_DQN_ET_PATTERNS.md` : Vérification algorithmes + patterns manquants
- `PATTERNS_CLOUD_IMPLEMENTES.md` : Détails 11 patterns + cas d'usage réels
- `MODIFICATIONS_ENTRAINEMENT.md` : Changements appliqués aux fichiers d'entraînement

#### **Code Modifié**
- `python_rl/train_dqn_policy.py` : Ligne 155-229 (4 nouveaux patterns)
- `python_rl/train_simple_qlearning.py` : Ligne 132-188 (4 nouveaux patterns)
- `python_rl/agents/dqn_agent.py` : Double DQN, Dueling, PER, Soft Update
- `python_rl/agents/simple_qlearning_agent.py` : Double Q-Learning, LR adaptatif, Exploration

---

### 🎯 Impact Attendu

#### **Entraînement**
- **Avant** : 200 épisodes, 7 patterns, 50 épisodes steady
- **Après** : 200 épisodes, 11 patterns, 30 épisodes steady + 24 read_intensive + 20 geo_distributed + ...

#### **Performance**
- **Q-Learning** : Meilleur sur patterns simples (steady, burst, read_intensive)
- **DQN** : Meilleur sur patterns complexes (geo_distributed, black_friday, write_intensive)

#### **Décisions de Réplication**
- **read_intensive** : Réplication agressive (lectures fréquentes)
- **write_intensive** : Centralisation (écritures coûteuses)
- **geo_distributed** : Réplication par région (proximité)
- **black_friday** : Réplication anticipée (avant pic)

---

### ✅ Checklist de Validation

- [x] Diagrammes mis à jour dans `workflow_diagrams_2024.md`
- [x] README créé (`README_DIAGRAMS_2024.md`)
- [x] Changelog créé (`CHANGELOG_DIAGRAMS_2024.md`)
- [x] PPO supprimé de tous les diagrammes
- [x] 4 nouveaux patterns cloud ajoutés
- [x] Améliorations algorithmes documentées
- [x] Conformité 100% avec `algo.md` vérifiée
- [ ] PNG générés (à faire avec `mmdc`)

---

### 🚀 Prochaines Étapes

1. **Générer les PNG** :
   ```bash
   cd docs
   mmdc -i workflow_diagrams_2024.md -o diagrams/2024/
   ```

2. **Entraîner les modèles** avec les nouveaux patterns :
   ```bash
   cd python_rl
   python train_dqn_policy.py --episodes 200
   python train_simple_qlearning.py --episodes 2000
   ```

3. **Analyser les résultats** par pattern :
   - Comparer performance Q-Learning vs DQN
   - Identifier patterns où chaque algorithme excelle
   - Valider les décisions de réplication

---

**Date** : Février 2024  
**Version** : 2.0  
**Auteur** : TCDRM-ADAPTIVE Team  
**Status** : ✅ Diagrammes mis à jour, prêts pour génération PNG
