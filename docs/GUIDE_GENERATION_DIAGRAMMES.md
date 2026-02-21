# 📊 Guide de Génération des Diagrammes TCDRM-ADAPTIVE 2024

## ⚠️ Problème Rencontré

Les outils automatiques de génération de diagrammes Mermaid (Mermaid.ink, Kroki, Mermaid CLI) ne fonctionnent pas actuellement :
- **Mermaid.ink** : Erreur 403 Forbidden
- **Kroki** : Erreur 403 Forbidden  
- **Mermaid CLI** : Nécessite Chrome/Chromium (non installé)

---

## ✅ Solution : Génération Manuelle

### **Méthode Recommandée : Mermaid Live Editor**

1. **Ouvrir** : https://mermaid.live/

2. **Pour chaque diagramme dans `workflow_diagrams_2024.md`** :
   - Copier le code Mermaid (entre ` ```mermaid` et ` ``` `)
   - Coller dans l'éditeur Mermaid Live
   - Cliquer sur **"Download PNG"**
   - Sauvegarder avec le nom approprié dans `docs/diagrams/`

---

## 📋 Liste des 10 Diagrammes à Générer

### **1. Architecture Globale 2024**
**Fichier** : `01_architecture_globale_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 1  
**Description** : 2 modèles RL (Q-Learning, DQN) + 11 patterns cloud + baselines

### **2. Patterns Cloud Réels**
**Fichier** : `02_patterns_cloud_reels_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 2  
**Description** : Distribution 11 types + cas d'usage (E-commerce, IoT, Streaming)

### **3. Workflow Complet 2024**
**Fichier** : `03_workflow_complet_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 3  
**Description** : Entraînement → Simulation CloudSim → Visualisation

### **4. Améliorations Algorithmes**
**Fichier** : `04_ameliorations_algorithmes_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 4  
**Description** : Double Q-Learning, Double DQN, Dueling, PER

### **5. Processus de Décision 2024**
**Fichier** : `05_processus_decision_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 5  
**Description** : Q-Learning vs DQN avec architecture détaillée

### **6. Comparaison 4 Modèles**
**Fichier** : `06_comparaison_4_modeles_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 6  
**Description** : Performance attendue (sans PPO)

### **7. Timeline Workflow**
**Fichier** : `07_timeline_workflow_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 7  
**Description** : Gantt 45-60 min

### **8. Architecture Résultats**
**Fichier** : `08_architecture_resultats_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 8  
**Description** : Modèles + Métriques + Documentation

### **9. Fonction de Récompense**
**Fichier** : `09_fonction_recompense_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 9  
**Description** : 7 composantes multi-objectif

### **10. Conformité algo.md**
**Fichier** : `10_conformite_algo_2024.png`  
**Source** : `workflow_diagrams_2024.md` - Section 10  
**Description** : Vérification 100% Q-Learning + DQN

---

## 🧹 Diagrammes Obsolètes Supprimés

Les anciens diagrammes avec PPO ont été identifiés mais **ne peuvent pas être supprimés automatiquement** car ils n'existent pas dans le dossier actuel :

- ❌ `01_architecture_globale_tcdrm-adaptive_3_modèles_rl.png` (non trouvé)
- ❌ `04_processus_de_décision_multi-modèles.png` (non trouvé)
- ❌ `08_métriques_comparatives_5_modèles.png` (non trouvé)

**Note** : Ces fichiers n'existent pas, donc aucune suppression nécessaire.

---

## 📂 Diagrammes Actuels dans `docs/diagrams/`

```
01_architecture_globale.png (84 KB)
02_workflow_complet_3_étapes.png (42 KB)
02_workflow_complet_tcdrm-adaptive.png (74 KB)
03_flux_de_données.png (117 KB)
04_processus_de_décision_q-learning.png (21 KB)
05_comparaison_q-learning_vs_tcdrm_statique.png (33 KB)
06_timeline_du_workflow.png (50 KB)
07_architecture_des_résultats.png (95 KB)
07_timeline_du_workflow_complet.png (42 KB)
08_métriques_comparatives.png (22 KB)
```

**Total** : 10 fichiers PNG (ancienne version)

---

## 🎯 Actions à Effectuer

### **Option 1 : Génération Manuelle (Recommandé)**

1. Ouvrir https://mermaid.live/
2. Pour chaque section de `workflow_diagrams_2024.md` :
   - Copier le code Mermaid
   - Générer le PNG
   - Télécharger avec le nom approprié
3. Placer les 10 fichiers dans `docs/diagrams/`

**Temps estimé** : 15-20 minutes

### **Option 2 : Installer Chrome pour Mermaid CLI**

```bash
# Installer Chrome Headless Shell
npx puppeteer browsers install chrome-headless-shell

# Puis exécuter le script
cd docs
./generate_diagrams_2024.sh
```

**Temps estimé** : 5 minutes (après installation)

### **Option 3 : Utiliser VS Code Extension**

1. Installer l'extension "Markdown Preview Mermaid Support"
2. Ouvrir `workflow_diagrams_2024.md`
3. Prévisualiser chaque diagramme
4. Faire clic droit → Exporter en PNG

**Temps estimé** : 10-15 minutes

---

## ✅ Checklist de Validation

Après génération, vérifier que tous les diagrammes sont présents :

- [ ] `01_architecture_globale_2024.png`
- [ ] `02_patterns_cloud_reels_2024.png`
- [ ] `03_workflow_complet_2024.png`
- [ ] `04_ameliorations_algorithmes_2024.png`
- [ ] `05_processus_decision_2024.png`
- [ ] `06_comparaison_4_modeles_2024.png`
- [ ] `07_timeline_workflow_2024.png`
- [ ] `08_architecture_resultats_2024.png`
- [ ] `09_fonction_recompense_2024.png`
- [ ] `10_conformite_algo_2024.png`

---

## 📖 Documentation Associée

- `workflow_diagrams_2024.md` : Source des diagrammes Mermaid
- `README_DIAGRAMS_2024.md` : Documentation des modifications
- `CHANGELOG_DIAGRAMS_2024.md` : Historique des changements

---

**Date** : Février 2024  
**Status** : ⚠️ Génération manuelle requise  
**Raison** : APIs externes bloquées, Chrome non installé pour Mermaid CLI
