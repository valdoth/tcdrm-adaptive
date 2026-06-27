# 📊 Résultat Génération Diagrammes

**Date** : 21 juin 2026
**Script** : `generate_diagrams_2024.sh` (Mermaid CLI / `mmdc`, exécution locale)
**Source** : `workflow_diagrams_2024.md`
**Destination** : `docs/diagrams/`

---

## ✅ 10/10 Diagrammes Générés avec Succès

| # | Nom du Fichier |
|---|----------------|
| 1 | `01_architecture_globale_tcdrm-adaptive_2_modeles_rl.png` |
| 2 | `02_modele_de_workload_et_strategies_de_popularite.png` |
| 3 | `03_workflow_detaille_entrainement_compilation_benchmark.png` |
| 4 | `04_techniques_damelioration_des_algorithmes_rl.png` |
| 5 | `05_processus_de_decision_q-learning_vs_dqn.png` |
| 6 | `06_comparaison_4_modeles_sans_ppo.png` |
| 7 | `07_workflow_complet_run-complete-workflow.png` |
| 8 | `08_architecture_des_resultats.png` |
| 9 | `09_fonction_de_recompense.png` |
| 10 | `10_techniques_rl_implementees_verification_code.png` |

---

## 🔧 Historique : pourquoi 3, 4 et 5 manquaient

Lors d'une tentative antérieure (via l'API `mermaid.ink`), les diagrammes 3,
4 et 5 avaient échoué avec des erreurs **503 Service Unavailable** et
**400 Bad Request** (diagrammes jugés trop complexes par l'API). Ce n'était
pas un retrait volontaire : la cause était un problème d'outil, pas de
contenu. Une fois la génération basculée vers Mermaid CLI en local, les
mêmes erreurs n'avaient plus de raison de se reproduire ; ces 3 diagrammes
ont été restaurés, corrigés pour correspondre au code actuel, et génèrent
désormais sans erreur.

## 🔧 Ce qui a changé par rapport à la génération précédente

- **Outil** : génération via Mermaid CLI (`mmdc`) en local, au lieu de l'API
  `mermaid.ink`. Il a fallu installer Chrome headless pour Puppeteer
  (`npx puppeteer browsers install chrome-headless-shell@131.0.6778.204`,
  version exacte attendue par `puppeteer-core` embarqué dans `@mermaid-js/mermaid-cli`).
- **Contenu** : tous les diagrammes ont été vérifiés/corrigés pour
  correspondre au code actuel (état DQN 9D, classes `CloudSimEnv` /
  `CloudSimQLearningEnv`, fonction de récompense réelle à 7 composantes,
  workload réel à 10 relations au lieu des "11 patterns cloud" fictifs,
  techniques RL complètes incluant Q(λ), n-step et normalisation Welford
  qui manquaient dans la version précédente). Voir `CHANGELOG_DIAGRAMS_2024.md`
  pour le détail.
- **Layout** : le diagramme 5 (`graph LR` avec deux branches imbriquées)
  produisait une image quasi illisible (784×83 px) ; passé en `graph TB`
  (665×1996 px, lisible).
- **Noms de fichiers** : script corrigé pour zéro-padder les numéros
  (`01_...` au lieu de `1_...`), et pour retirer tout caractère hors
  `[a-z0-9_-]` (accents non gérés, apostrophes, flèches `→`, `+`, `%`) — une
  apostrophe dans le titre du diagramme 4 avait initialement cassé la
  commande shell de génération.

---

## ⚠️ Points d'attention pour les régénérations futures

1. **Labels de nœuds** : Mermaid échoue si un label contient des crochets
   `[...]` ou des parenthèses `(...)` non échappés (interprétés comme une
   nouvelle forme de nœud). Entourer le label de guillemets :
   `NODE["texte avec (parenthèses) et [crochets]"]`.
2. **Titres de section** (`## N. Titre`) : éviter les apostrophes et flèches
   si possible ; le script les retire désormais du nom de fichier généré,
   mais un titre 100% ASCII reste plus prévisible.
3. **Aspect ratio** : pour un diagramme avec plusieurs branches parallèles
   imbriquées dans des `subgraph`, préférer `graph TB` à `graph LR` — `LR`
   peut produire une image très large et plate, difficile à lire.
