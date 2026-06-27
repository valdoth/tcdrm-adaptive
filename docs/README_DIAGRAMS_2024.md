# 📊 Diagrammes TCDRM-ADAPTIVE — État Actuel

> Mis à jour pour refléter le code réel du dépôt (vérifié le 21/06/2026).
> Voir `CHANGELOG_DIAGRAMS_2024.md` pour l'historique des corrections.

## 🎯 Ce que montrent les diagrammes

- **2 modèles RL** : Q-Learning (`tcdrm_gym/agents/simple_qlearning_agent.py`) et DQN
  (`tcdrm_gym/agents/dqn_agent.py`). **PPO n'est pas implémenté** dans ce projet.
- **Comparaison à 4 stratégies** : NoRep, TCDRM (seuil statique), Q-Learning, DQN.
- **Workload réel** : 10 relations (`R1`-`R10`) à popularité fixe générées par
  `WorkloadGenerator.java`, suivies via `RelationPopularityTracker` / `TinyLFU.java`,
  avec 3 stratégies de popularité interchangeables (`EMA`, `TinyLFU`, `EMA+TinyLFU`).
  **Il n'existe pas de "11 patterns cloud" (steady/burst/black_friday/...)** dans le
  code — cette idée figurait dans une version antérieure des diagrammes mais n'a
  jamais été implémentée.
- **Fonction de récompense réelle à 7 composantes**, calculée dans
  `TrainingEnvironment.calculateReward()` (Java) : `SLA_OK`, `SLA_VIOL`,
  `COST_OVER`, `REPL_COST`, `THRASH`, `UNUTILIZATION`, `INVALID_ACTION`.

## 📂 Fichiers

- `workflow_diagrams_2024.md` : source Mermaid des 10 diagrammes (vérifiée contre le code).
- `diagrams/` : PNG générés depuis ce fichier.
- `generate_diagrams_2024.sh` : script de génération via Mermaid CLI (`mmdc`), exécutable en local.

## 🎨 Les 10 Diagrammes

| # | Diagramme | Contenu |
|---|-----------|---------|
| 1 | Architecture Globale | 2 modèles RL + workload réel + baselines |
| 2 | Workload et Popularité | 10 relations, 3 stratégies de popularité, 3 stratégies de warmup |
| 3 | Workflow Détaillé | Entraînement → modèles → compilation → benchmark → graphes |
| 4 | Techniques d'Amélioration | Détail des techniques Q-Learning et DQN, avec formules |
| 5 | Processus de Décision | Flux état → action pour Q-Learning et DQN (état 9D réel) |
| 6 | Comparaison 4 Modèles | Classement mesuré (NoRep < TCDRM < Q-Learning < DQN) |
| 7 | Workflow Complet | 3 étapes de `run_complete_workflow.sh` |
| 8 | Architecture des Résultats | Modèles, logs CSV/TensorBoard, graphes réels |
| 9 | Fonction de Récompense | 7 composantes réelles de `calculateReward()` |
| 10 | Techniques RL Implémentées | Liste des techniques par fichier (sans numéros de ligne) |

Les diagrammes 3, 4 et 5 avaient initialement échoué à la génération (erreurs
403/503/400 de l'API `mermaid.ink`, documentées dans
`RESULTAT_GENERATION_DIAGRAMMES_2024.md`) — ce n'était pas un retrait
volontaire. Ils sont désormais générés en local sans erreur et ont été
corrigés pour correspondre au code actuel.

## 🔧 Génération des PNG

Mermaid CLI fonctionne désormais en local (Chrome headless installé via
Puppeteer) :

```bash
cd docs
bash generate_diagrams_2024.sh
```

Le script nettoie le dossier `diagrams/` et régénère les 10 fichiers à partir
de `workflow_diagrams_2024.md`. Pièges connus, déjà corrigés dans le script :

- Crochets `[...]` ou parenthèses `(...)` non échappés dans un label de
  nœud → entourer le label de guillemets, ex.
  `NODE["texte avec (parenthèses) ou [crochets]"]`.
- Apostrophes, flèches (`→`) ou autres caractères non-ASCII dans le **titre**
  d'un diagramme (`## N. Titre`) → le script assainit désormais le nom de
  fichier généré (suppression de tout caractère hors `[a-z0-9_-]`).

## ✅ À jour avec le code

- État DQN : **9 dimensions** (`latency, budget, replicas, popularity, cost,
  t_sla_violation, c_sla_violation, progress, p_sla_progress`), pas 8.
- Réseau DQN : `9 → 64 → 64 → [Value: 32→1, Advantage: 32→3]` (Dueling),
  conforme à `DuelingDQNNetwork` (hidden_dims par défaut `[64, 64]`).
- Classes d'environnement réelles : `CloudSimQLearningEnv`, `CloudSimEnv`
  (et non `TcdrmQLearningEnv` / `TcdrmV2Env`).
- Modèles sauvegardés : `models/qlearning_cloudsim.pkl`,
  `models/dqn_cloudsim.pt` (cf. `tcdrm_gym/config.yml`).
