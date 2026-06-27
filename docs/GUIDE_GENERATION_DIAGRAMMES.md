# 📊 Guide de Génération des Diagrammes TCDRM-ADAPTIVE

## ✅ La génération locale fonctionne

Contrairement à une version précédente de ce guide, **Mermaid CLI (`mmdc`)
fonctionne désormais en local** et n'a plus besoin des API externes
(Mermaid.ink, Kroki) qui renvoyaient des erreurs 403/503/400.

```bash
# 1. Installer Mermaid CLI (une fois)
npm install -g @mermaid-js/mermaid-cli

# 2. Installer Chrome headless pour Puppeteer (une fois)
#    Utiliser la version exacte attendue par puppeteer-core embarqué
#    dans @mermaid-js/mermaid-cli (vérifier avec :
#    cat node_modules/@mermaid-js/mermaid-cli/node_modules/puppeteer-core/package.json)
npx --yes puppeteer@<version-de-puppeteer-core> browsers install chrome-headless-shell@<version-attendue>

# 3. Générer les 10 diagrammes
cd docs
bash generate_diagrams_2024.sh
```

Le script :
1. Vérifie que `mmdc` est installé.
2. Vide les anciens PNG du dossier `diagrams/`.
3. Extrait chaque bloc ` ```mermaid ` de `workflow_diagrams_2024.md` et génère le PNG correspondant.
4. Assainit le nom de fichier (accents, apostrophes, flèches, `+`, `%` retirés) pour éviter de casser la commande de génération.

## 📋 Les 10 Diagrammes

| # | Fichier | Description |
|---|---|---|
| 1 | `01_architecture_globale_tcdrm-adaptive_2_modeles_rl.png` | 2 modèles RL (Q-Learning, DQN) + workload réel + baselines |
| 2 | `02_modele_de_workload_et_strategies_de_popularite.png` | 10 relations à popularité fixe, 3 stratégies de popularité, 3 stratégies de warmup |
| 3 | `03_workflow_detaille_entrainement_compilation_benchmark.png` | Entraînement → modèles `.pkl`/`.pt` → compilation → benchmark → graphes |
| 4 | `04_techniques_damelioration_des_algorithmes_rl.png` | Détail des techniques Q-Learning et DQN, avec formules |
| 5 | `05_processus_de_decision_q-learning_vs_dqn.png` | Flux état (9D réel) → action pour Q-Learning et DQN |
| 6 | `06_comparaison_4_modeles_sans_ppo.png` | Classement mesuré NoRep / TCDRM / Q-Learning / DQN |
| 7 | `07_workflow_complet_run-complete-workflow.png` | 3 étapes de `run_complete_workflow.sh` |
| 8 | `08_architecture_des_resultats.png` | Modèles entraînés, logs CSV/TensorBoard, graphes réels |
| 9 | `09_fonction_de_recompense.png` | 7 composantes réelles de `TrainingEnvironment.calculateReward()` |
| 10 | `10_techniques_rl_implementees_verification_code.png` | Techniques RL implémentées, par fichier source |

Les diagrammes 3, 4 et 5 échouaient auparavant à la génération à cause
d'erreurs de l'API `mermaid.ink` (503/400), pas par choix de contenu. Ils
sont désormais générés en local sans erreur (voir
`RESULTAT_GENERATION_DIAGRAMMES_2024.md`).

## ⚠️ Erreur de parsing Mermaid la plus fréquente

```
Error: Parse error on line N: ... Expecting 'SQE', ... got 'PS'/'SQS'
```

Cause : un label de nœud contient des crochets `[...]` ou des parenthèses
`(...)` non échappés, que Mermaid interprète comme une nouvelle forme de
nœud. Solution : entourer tout le label de guillemets :

```
NODE["texte avec (parenthèses) et [crochets]"]
```

## 🔄 Avant de modifier un diagramme

Si le code change (nouvel hyperparamètre, nouvelle technique RL, nouveau
champ d'état), vérifier dans le code source avant de mettre à jour le
diagramme correspondant :

- État/actions/récompense : `tcdrm_gym/envs/cloudsim_env.py`,
  `src/main/java/.../simulation/TcdrmSimulation.java` (`buildRLState`),
  `src/main/java/.../training/TrainingEnvironment.java` (`calculateReward`).
- Agents RL : `tcdrm_gym/agents/simple_qlearning_agent.py`,
  `tcdrm_gym/agents/dqn_agent.py`.
- Workload/popularité : `src/main/java/.../data/WorkloadGenerator.java`,
  `src/main/java/.../data/RelationPopularityTracker.java`,
  `src/main/java/.../simulation/TinyLFU.java`.
- Workflow : `run_complete_workflow.sh`.

Éviter de référencer des numéros de ligne précis dans les diagrammes : ils
deviennent obsolètes dès la moindre modification du fichier. Préférer les
noms de classes/méthodes/fichiers, plus stables.
