# Changelog des Diagrammes TCDRM-ADAPTIVE

## Version 3.0 (27 janvier 2026) - 5 Modèles

### 🎯 Changements Majeurs

#### Nouveaux Modèles Ajoutés
- ✨ **DQN (Deep Q-Network)** : Réseau de neurones avec experience replay
- ✨ **PPO (Proximal Policy Optimization)** : Algorithme policy-based avec Stable-Baselines3
- ✨ **Graphes 5 Courbes** : Comparaison visuelle de tous les modèles

#### Diagrammes Mis à Jour

1. **Architecture Globale** (01)
   - Avant : Q-Learning uniquement
   - Maintenant : Q-Learning + DQN + PPO
   - Ajout : Environnement Gymnasium avec observation space 8D
   - Ajout : Fonction de récompense multi-objectif

2. **Workflow Complet** (02)
   - Avant : Entraînement Q-Learning → Compilation Java → Graphes 3 courbes
   - Maintenant : Entraînement 3 modèles → Évaluation → Graphes 5 courbes
   - Durée : 40-75 minutes (au lieu de 30-45 min)

3. **Comparaison** (03)
   - Avant : Q-Learning vs TCDRM Statique (2 modèles)
   - Maintenant : 5 modèles avec classement et métriques
   - Ajout : Résultats pour R1 et R2

4. **Processus de Décision** (04)
   - Avant : Q-Learning uniquement (Q-Table)
   - Maintenant : 3 approches (Q-Table, Neural Network, Policy Network)
   - Ajout : Flux de données pour chaque modèle

5. **Fonction de Récompense** (05) - NOUVEAU
   - 5 composantes détaillées
   - Poids de chaque composante
   - Flux de calcul de la récompense totale

6. **Architecture des Résultats** (06)
   - Avant : `results/qlearning/`
   - Maintenant : `results/tcdrm_adaptive/`, `results/dqn/`, `results/ppo/`
   - Ajout : `results/comparison/` pour graphes 5 courbes

7. **Timeline** (07)
   - Avant : 3 étapes séquentielles
   - Maintenant : Entraînement parallèle possible + évaluation + visualisation
   - Détail : Q-Learning (15 min), DQN (20 min), PPO (25 min)

8. **Métriques Comparatives** (08)
   - Avant : Q-Learning vs TCDRM Statique
   - Maintenant : 5 modèles avec amélioration vs baseline
   - Ajout : Métriques de stabilité (changements de réplicas)

### 📊 Nouveaux Graphes Générés

**Graphes 5 Courbes** (images/) :
- Response Time Raw (R1 & R2)
- Response Time Smoothed (R1 & R2)
- Total Cost (R1 & R2)

**Graphes de Comparaison Python** (python_rl/results/comparison/) :
- Response Time (R1 & R2)
- Cumulative Cost (R1 & R2)
- Replicas (R1 & R2)

### 🎨 Couleurs des Modèles

| Modèle | Couleur | Code |
|--------|---------|------|
| Q-Learning | Orange | #FFA500 |
| DQN | Turquoise | #00CED1 |
| PPO | Purple | #9370DB |
| TCDRM Statique | Crimson | #DC143C |
| NOREP | Tomato | #FF6347 |

### 📈 Résultats Clés

**Scénario R1 (5.3 GB)** :
- PPO : 178.73s, 213.55$ (🥇)
- DQN : 179.45s, 228.61$ (🥈)
- Q-Learning : 181.35s, 246.27$ (🥉)
- TCDRM Statique : 183.41s, 277.43$ (baseline)
- NOREP : 201.59s, 530.88$ (pire)

**Scénario R2 (11.9 GB)** :
- PPO : 401.26s, 479.53$ (🥇)
- DQN : 402.44s, 501.69$ (🥈)
- Q-Learning : 407.12s, 552.99$ (🥉)
- TCDRM Statique : 411.75s, 622.96$ (baseline)
- NOREP : 452.40s, 1000.07$ (pire)

### 🔧 Outils et Scripts

**Nouveaux fichiers** :
- `workflow_diagrams_updated.md` : Source Mermaid des diagrammes v3.0
- `generate_diagrams_updated.py` : Script de génération automatique
- `python_rl/generate_5curves_graphs.py` : Génération graphes 5 courbes
- `run_all_models.sh` : Entraînement de tous les modèles
- `MODELS_COMPARISON.md` : Documentation comparative

**Fichiers mis à jour** :
- `README_DIAGRAMS.md` : Documentation complète v3.0
- `TCDRM_ADAPTIVE_SPEC.md` : Spécification technique
- `README_TCDRM_ADAPTIVE.md` : Guide d'utilisation

---

## Version 2.0 (Précédente)

### Fonctionnalités
- Q-Learning avec Q-Table tabulaire
- Graphes 3 courbes (Python RL + TCDRM Statique + NOREP)
- Communication Py4J Java-Python
- 8 diagrammes de base

### Limitations
- Un seul modèle RL (Q-Learning)
- Pas de deep learning
- Pas de comparaison multi-modèles

---

## Migration v2.0 → v3.0

### Pour mettre à jour vos diagrammes

```bash
# 1. Générer les nouveaux diagrammes
cd docs
python3 generate_diagrams_updated.py

# 2. Entraîner tous les modèles
cd ..
./run_all_models.sh --episodes 200

# 3. Générer les graphes 5 courbes
cd python_rl
uv run python generate_5curves_graphs.py \
    --qlearning-model results/tcdrm_adaptive/full_run_XXX/adaptive_model.pkl \
    --dqn-model results/dqn/dqn_model.pt \
    --ppo-model results/ppo/ppo_model.zip \
    --output-dir ../images
```

### Compatibilité

- ✅ Les anciens scripts fonctionnent toujours
- ✅ Les graphes 3 courbes sont toujours générés
- ✅ Les nouveaux scripts sont additifs, pas de breaking changes

---

## Prochaines Versions

### v3.1 (Planifié)
- [ ] Diagramme d'intégration CloudSim via Py4J
- [ ] Diagramme de l'architecture multi-cloud
- [ ] Graphes de convergence des modèles

### v4.0 (Futur)
- [ ] Support pour d'autres algorithmes RL (A3C, SAC, TD3)
- [ ] Diagrammes interactifs (HTML)
- [ ] Comparaison avec d'autres benchmarks

---

**Dernière mise à jour** : 27 janvier 2026
**Version** : 3.0
**Auteur** : TCDRM-ADAPTIVE Team
