# Définition du modèle Q-learning pour TCDRM v2

Cette section présente une définition précise et formelle des **états**, **actions** et **récompenses**
du modèle d’apprentissage par renforcement (**Q-learning**) utilisé dans **TCDRM v2**.
Le problème est modélisé comme un **Processus de Décision Markovien (MDP)** :

⟨S, A, P, R, γ⟩

où  
S = espace des états  
A = espace des actions  
P = probabilité de transition  
R = fonction de récompense  
γ = facteur d’actualisation

---

## 1. Espace des états (State Space S)

Un état représente la **situation du système observée au moment de la décision de réplication**.

Un état est défini comme :

s = (RT, COST, POP, BUD, NET)

### RT – État du temps de réponse

| Valeur | Signification |
|--------|---------------|
| RT0 | Temps de réponse satisfaisant |
| RT1 | Temps proche de la limite SLA |
| RT2 | Violation du SLA |

Discrétisation :

RT0 : tQ ≤ μRT  
RT1 : μRT < tQ ≤ μRT + σRT  
RT2 : tQ > μRT + σRT

---

### COST – État du coût de la requête

| Valeur | Signification |
|--------|---------------|
| C0 | Coût faible |
| C1 | Coût modéré |
| C2 | Coût excessif |

Discrétisation :

C0 : cQ ≤ 0.7 × CSLA  
C1 : 0.7 × CSLA < cQ ≤ CSLA  
C2 : cQ > CSLA

---

### POP – État de popularité des données

| Valeur | Signification |
|--------|---------------|
| P0 | Faible popularité |
| P1 | Popularité moyenne |
| P2 | Forte popularité |

---

### BUD – État du budget résiduel du locataire

| Valeur | Signification |
|--------|---------------|
| B0 | Budget confortable |
| B1 | Budget tendu |
| B2 | Budget critique |

Discrétisation :

B0 : budget ≥ 60%  
B1 : 30% ≤ budget < 60%  
B2 : budget < 30%

---

### NET – Type dominant de trafic réseau

| Valeur | Signification |
|--------|---------------|
| N0 | Intra-région |
| N1 | Inter-région |
| N2 | Inter-cloud |

---

### Taille de l’espace d’état

|S| = 3 × 3 × 3 × 3 × 3 = 243 états

Ce volume est **compatible avec Q-learning tabulaire**.

---

## 2. Espace des actions (Action Space A)

L’agent choisit une action à chaque décision :

A = {NOOP, REPLICATE, DELETE}

| Action | Description |
|--------|-------------|
| NOOP | Ne rien faire |
| REPLICATE | Créer de nouvelles répliques |
| DELETE | Supprimer des répliques peu utiles |

Contraintes :
- REPLICATE interdit si BUD = B2  
- DELETE interdit s’il n’existe aucun réplica

---

## 3. Fonction de récompense (Reward Function R)

La récompense mesure la qualité globale d’une décision en combinant performance, coût et stabilité.

R = + r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH

### Composantes

| Terme | Définition |
|------|-----------|
| SLA_OK | 1 si SLA respecté |
| SLA_VIOL | 1 si violation SLA |
| COST_OVER | 1 si dépassement du budget |
| REPL_COST | Coût normalisé de la réplication |
| THRASH | 1 si alternance rapide replicate/delete |

### Pondérations typiques

r1 = 5  
r2 = 6  
r3 = 5  
r4 = 2  
r5 = 3

---

## 4. Politique d’apprentissage

### Exploration

Politique ε-greedy décroissante :

ε_t = max(ε_min, ε_0 · exp(−λt))

### Mise à jour Q-learning

Q(s,a) ← (1−α)Q(s,a) + α [ R + γ · max Q(s’,a’) ]

---

## 5. Propriétés garanties

Ce modèle permet :

- Décisions **adaptatives** sans seuils fixes  
- Respect prioritaire du **budget du locataire**  
- Réduction des réplications inutiles  
- Stabilité des politiques (anti-thrashing)  
- Compatibilité avec simulation **CloudSim multi-cloud**

---

## 6. Extensions possibles

- Remplacement de la Q-table par un **Deep Q-Network**
- Ajout de dimensions d’état (consistance, énergie, SLA multi-niveaux)
- Apprentissage multi-locataires avec agents indépendants

---
