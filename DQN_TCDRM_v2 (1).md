# Modèle Deep Q-Network (DQN) pour TCDRM v2

Cette section présente l’extension du modèle Q-learning tabulaire vers une approche **Deep Q-Network (DQN)** afin de gérer un espace d’états plus large, des variables continues et des environnements multi-cloud plus complexes.

Le problème reste formulé comme un **Processus de Décision Markovien (MDP)** :
⟨S, A, P, R, γ⟩

La différence principale réside dans l’approximation de la fonction Q(s,a) par un **réseau de neurones profond**.

---

## 1. Représentation de l’état (State Representation)

Contrairement au modèle tabulaire discret, l’état est ici représenté sous forme **vectorielle continue**.

s = [tQ_norm, cQ_norm, pop_norm, bud_norm, net_inter_ratio, net_intercloud_ratio, repl_factor, trend_pop]

### Description des variables

| Variable | Description | Normalisation |
|----------|-------------|---------------|
| tQ_norm | Temps de réponse normalisé | tQ / RT_max |
| cQ_norm | Coût de requête normalisé | cQ / Budget_period |
| pop_norm | Popularité prédite des données | [0,1] |
| bud_norm | Budget restant | budget_remaining / budget_total |
| net_inter_ratio | Ratio trafic inter-région | [0,1] |
| net_intercloud_ratio | Ratio trafic inter-cloud | [0,1] |
| repl_factor | Nombre de réplicas existants | / max_replicas |
| trend_pop | Tendance de popularité (croissance/déclin) | [-1,1] |

---

## 2. Espace des actions (Action Space)

Identique au modèle précédent mais géré par le réseau :

A = {0: NOOP, 1: REPLICATE, 2: DELETE}

Des contraintes peuvent être appliquées via un **masquage d’actions** :
- REPLICATE interdit si budget critique
- DELETE interdit si aucun réplica

---

## 3. Architecture du Deep Q-Network

Le DQN approxime Q(s,a; θ) avec un réseau neuronal :

Entrée : vecteur d’état (dimension 8)

- Couche dense 1 : 64 neurones, ReLU
- Couche dense 2 : 64 neurones, ReLU
- Couche dense 3 : 32 neurones, ReLU
- Couche de sortie : 3 neurones (Q-values pour chaque action)

---

## 4. Fonction de récompense

Identique à la version tabulaire, mais appliquée à un contexte continu.

R = + r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH

### Composantes continues

| Terme | Description |
|------|-------------|
| SLA_OK | max(0, 1 − tQ_norm) |
| SLA_VIOL | max(0, tQ_norm − 1) |
| COST_OVER | max(0, cQ_norm − 1) |
| REPL_COST | coût réel normalisé |
| THRASH | pénalité si action inverse récente |

---

## 5. Apprentissage avec DQN

### 5.1. Replay Memory

Un buffer d’expériences est utilisé :

D = {(s, a, r, s')}

À chaque étape, un mini-batch aléatoire est échantillonné pour stabiliser l’apprentissage.

### 5.2. Réseau cible (Target Network)

Deux réseaux sont maintenus :
- Q-network principal (θ)
- Target network (θ⁻)

Mise à jour périodique :

θ⁻ ← θ

### 5.3. Mise à jour des poids

Pour chaque échantillon (s, a, r, s') :

y = r + γ max_a' Q_target(s', a'; θ⁻)  
Loss = (y − Q(s, a; θ))²

Optimisation via descente de gradient (Adam recommandé).

---

## 6. Politique d’exploration

ε-greedy décroissant :

ε_t = max(ε_min, ε_0 · exp(−λt))

---

## 7. Avantages du DQN pour TCDRM v2

- Gère des **variables continues** (coût, latence, popularité)
- S’adapte à un **grand nombre de fournisseurs**
- Capable d’apprendre des **politiques complexes non linéaires**
- Plus robuste dans des environnements dynamiques

---

## 8. Indicateurs d’évaluation spécifiques au DQN

- Convergence de la loss
- Stabilité de la politique (variance des actions)
- Réduction des violations SLA
- Réduction du coût moyen par requête
- Réduction du trafic inter-cloud

---

Ce modèle DQN constitue l’extension naturelle du Q-learning tabulaire vers un **TCDRM v2 intelligent, adaptatif et scalable**.
