# Changements apportés au framework TCDRM-ADAPTIVE

## Contexte — Sujet 1 (TCDRM-ADAPTIVE)

L'objectif du Sujet 1 est de **transformer TCDRM d'un modèle à seuils statiques vers un framework adaptatif et auto-apprenant** capable de décider dynamiquement de la réplication et de la suppression de réplicas, tout en respectant le budget du locataire.

L'**Axe 4** du sujet stipule explicitement :
> *"Ajustement dynamique des seuils TSLA, CSLA et PSLA."*

Les changements documentés ici portent entièrement sur cet axe. Ils ont été réalisés en deux phases, avec pour inspiration principale la thèse de Mechouche (2024) : *"Gérer et assurer la qualité de services de ressources dans un environnement multi-cloud"* (HAL tel-04791345v1).

---

## Phase 1 — Remplacement des seuils statiques par des seuils dynamiques

### Problème initial

Dans le code original, les seuils SLA utilisés par les agents RL étaient des **constantes statiques** lues directement depuis `TcdrmConstants` :

```java
// AVANT — TcdrmSimulation.java
double normalizedPopularity = emaPopularity / TcdrmConstants.EMA_REPLICATION_THRESHOLD;
double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;

// AVANT — executeRLQuery()
if (emaPopularity < TcdrmConstants.EMA_DELETE_THRESHOLD) { ... }
```

Ces constantes ne s'adaptaient jamais, quelle que soit la performance observée. L'agent RL apprenait donc à optimiser par rapport à des objectifs fixes, ce qui contredit l'objectif du Sujet 1.

### Décision sur le CSLA

Le **CSLA (Cost SLA)** reste **statique**. C'est le budget contractuel déclaré par le locataire — une contrainte externe qui ne doit pas être modifiée par le système. Seuls TSLA et PSLA sont rendus dynamiques.

### Fichiers modifiés

#### `TcdrmSimulation.java`

Trois nouveaux champs mutable remplacent les lectures directes aux constantes :

```java
// APRÈS — nouveaux champs (initialisés aux valeurs de référence du contrat)
private double dynamicTSla;    // seuil de latence adaptatif
private double dynamicPSlaHi;  // seuil EMA pour déclencher REPLICATE
private double dynamicPSlaLo;  // seuil EMA pour autoriser DELETE
```

Dans `buildRLState()` :
```java
// AVANT
double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
double normalizedPopularity = emaPopularity / TcdrmConstants.EMA_REPLICATION_THRESHOLD;

// APRÈS — utilise les seuils adaptatifs
double tSla = dynamicTSla;  // TSLA adaptatif (Axe 4) — CSLA reste statique
double normalizedPopularity = emaPopularity / Math.max(1e-9, dynamicPSlaHi);
```

Dans `executeRLQuery()` :
```java
// AVANT
if (emaPopularity < TcdrmConstants.EMA_DELETE_THRESHOLD) { ... }

// APRÈS — utilise le seuil PSLA dynamique
if (emaPopularity < dynamicPSlaLo) { ... }
```

Nouveaux accesseurs ajoutés : `setDynamicThresholds()`, `getDynamicTSla()`, `getDynamicPSlaHi()`, `getDynamicPSlaLo()`.

**Note :** Les baselines TCDRM v1 (`executeNoRepQuery()`, `executeTcdrmQuery()`) sont **intentionnellement inchangées** — elles utilisent encore les constantes statiques pour rester fidèles au papier original.

#### `TrainingEnvironment.java`

Initialisation des seuils adaptatifs dans le constructeur :
```java
this.dynamicTSla   = complex ? tComplex : tSimple;
this.dynamicPSlaHi = TcdrmConstants.EMA_REPLICATION_THRESHOLD;
this.dynamicPSlaLo = TcdrmConstants.EMA_DELETE_THRESHOLD;
```

Dans `reset()`, les seuils sont injectés dans chaque nouvelle simulation :
```java
this.simulation = new TcdrmSimulation(seed, complex);
this.simulation.setDynamicThresholds(dynamicTSla, dynamicPSlaHi, dynamicPSlaLo);
this.tSla = this.dynamicTSla;
```

Le DELETE silencieux utilise désormais le seuil dynamique :
```java
boolean deleteSilentlyBlocked = (action == 2 && canDelete
    && simulation.getEmaPopularity() >= simulation.getDynamicPSlaLo());
```

#### `BenchmarkRunner.java`

```java
// AVANT
double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
boolean deleteSilentlyBlocked = (action == 2 && canDelete
    && sim.getEmaPopularity() >= TcdrmConstants.EMA_DELETE_THRESHOLD);

// APRÈS — TSLA adaptatif : utilise la valeur dynamique de la simulation (Axe 4)
double tSla = sim.getDynamicTSla();
boolean deleteSilentlyBlocked = (action == 2 && canDelete
    && sim.getEmaPopularity() >= sim.getDynamicPSlaLo());
```

---

## Phase 2 — Machine à états pour la méta-adaptation (inspirée de Mechouche 2024)

### Problème de la première approche heuristique

Après la Phase 1, la méthode `adaptThresholds()` dans `TrainingEnvironment.java` utilisait des règles if/else simples appliquées à chaque fin d'épisode :

```java
// AVANT — heuristique plate, sans mémoire
if (violationRate > 0.20) {
    dynamicTSla = Math.max(contractTSla * 0.60, dynamicTSla - step);
} else if (violationRate < 0.05) {
    dynamicTSla = Math.min(contractTSla, dynamicTSla + step * 0.5);
}
if (avgCostRatio > 1.2) {
    dynamicPSlaHi = Math.min(0.80, dynamicPSlaHi + pslaStep);
    ...
} else if (avgCostRatio < 0.8 && violationRate > 0.10) {
    dynamicPSlaHi = Math.max(0.15, dynamicPSlaHi - pslaStep);
    ...
}
```

Cette approche avait **deux défauts majeurs** :

1. **Signaux contradictoires** : si `violationRate > 20%` ET `avgCostRatio > 120%` simultanément, les deux blocs s'activent. La première branche demande à répliquer plus facilement (baisser TSLA), la seconde à répliquer moins (remonter pSlaHi) — les actions se contredisent.

2. **Absence d'hystérésis** : un seul épisode dégradé suffit à modifier les seuils, même si c'était une anomalie transitoire. Le système oscille entre épisodes.

### Inspiration — Mechouche 2024 (Chapitre 3)

La thèse de Mechouche formalise les stratégies de reconfiguration de SLA multi-cloud sous forme de **machines à états** (Définitions 3.4 à 3.10) :

| Concept Mechouche | Rôle |
|---|---|
| **Def 3.4** — Stratégie de reconfiguration `(S, T)` | Machine à états pilotant les actions de reconfiguration |
| **Def 3.5** — État `(l, t, R)` | État typé (initial / normal / final) avec ressources associées |
| **Def 3.7** — Transition `(id, s_s, s_t, E, A)` | Passage d'un état à un autre déclenché par un événement |
| **Def 3.8** — Événement `(id, t, p)` | Événement lié à une ressource, avec **prédicat temporel** (ex. : CPU > 85% pendant 60s) |
| **Def 3.9** — Action de reconfiguration `(id, t, AA)` | Scale-out / Scale-in appliqué lors de la transition |

L'exemple du chapitre 3 (listings 3.3 à 3.5) montre des états nommés `normalNeeds`, `highNeeds`, `lowNeeds` et `End`, avec des transitions déclenchées par des métriques CPU soutenues dans le temps. Ce même principe est appliqué ici à la politique de réplication TCDRM.

### Solution — Machine à états `ReplicationState`

#### Les états (Def 3.5)

```java
// TrainingEnvironment.java — enum interne
private enum ReplicationState {
    CONSERVATIVE,  // Budget dépassé → restreindre la réplication (pSlaHi élevé)
    BALANCED,      // Fonctionnement normal
    AGGRESSIVE     // Taux de violations élevé → encourager la réplication (pSlaHi bas)
}
```

Correspondance avec l'exemple Mechouche :
- `CONSERVATIVE` ↔ `lowNeeds` (moins de ressources allouées)
- `BALANCED` ↔ `normalNeeds` (état initial, fonctionnement normal)
- `AGGRESSIVE` ↔ `highNeeds` (plus de ressources allouées)

#### Les événements avec prédicat temporel (Def 3.8)

Mechouche utilise des événements de type `ResourceRelatedEvent` avec une durée minimale (ex. : `cpuUsage > 85% for 60s`). L'équivalent dans TCDRM utilise **2 épisodes consécutifs** comme fenêtre temporelle :

```java
// Compteurs de soutien — persistent entre épisodes (ne pas réinitialiser dans reset())
private int consecutiveViolationEpisodes = 0;
private int consecutiveCostOverEpisodes  = 0;

// Evaluation des événements
boolean highViolation = violationRate > 0.20;
boolean highCost      = avgCostRatio   > 1.20;

consecutiveViolationEpisodes = highViolation ? consecutiveViolationEpisodes + 1 : 0;
consecutiveCostOverEpisodes  = highCost      ? consecutiveCostOverEpisodes  + 1 : 0;

// Les événements ne se déclenchent qu'après 2 épisodes consécutifs (hystérésis)
boolean evtHighViolation = consecutiveViolationEpisodes >= 2;
boolean evtHighCost      = consecutiveCostOverEpisodes  >= 2;
boolean evtStable        = violationRate < 0.05 && avgCostRatio < 0.80;  // instantané
```

#### Les transitions (Def 3.7)

Les transitions sont ordonnées par priorité — la **pression budgétaire prime** sur la pression de latence (le budget est la contrainte hard du locataire) :

```
BALANCED    → CONSERVATIVE  si evtHighCost              (coût trop élevé × 2 épisodes)
BALANCED    → AGGRESSIVE    si evtHighViolation          (violations T_SLA × 2 épisodes)
AGGRESSIVE  → BALANCED      si evtHighCost ou evtStable  (budget sous pression ou stabilité)
CONSERVATIVE→ BALANCED      si evtHighViolation ou evtStable
```

En code :
```java
switch (replicationState) {
    case BALANCED:
        if      (evtHighCost)      replicationState = ReplicationState.CONSERVATIVE;
        else if (evtHighViolation) replicationState = ReplicationState.AGGRESSIVE;
        break;
    case AGGRESSIVE:
        if (evtHighCost || evtStable) replicationState = ReplicationState.BALANCED;
        break;
    case CONSERVATIVE:
        if (evtHighViolation || evtStable) replicationState = ReplicationState.BALANCED;
        break;
}
// Réinitialiser les compteurs de soutien en cas de changement d'état
if (replicationState != prev) {
    consecutiveViolationEpisodes = 0;
    consecutiveCostOverEpisodes  = 0;
}
```

#### Les actions de reconfiguration (Def 3.9)

Après chaque transition (ou maintien d'état), les seuils sont ajustés en fonction de l'**état courant** :

```java
switch (replicationState) {
    case AGGRESSIVE:
        // Scale-out : baisser dynamicTSla + baisser pSlaHi (répliquer plus facilement)
        dynamicTSla   = Math.max(contractTSla * 0.60, dynamicTSla - tStep);
        dynamicPSlaHi = Math.max(0.15, dynamicPSlaHi - pslaStep);
        dynamicPSlaLo = Math.max(0.05, dynamicPSlaLo - pslaStep);
        break;
    case CONSERVATIVE:
        // Scale-in : remonter pSlaHi (répliquer moins souvent) + récupérer dynamicTSla
        dynamicPSlaHi = Math.min(0.80, dynamicPSlaHi + pslaStep);
        dynamicPSlaLo = Math.min(dynamicPSlaHi - 0.05, dynamicPSlaLo + pslaStep);
        dynamicTSla   = Math.min(contractTSla, dynamicTSla + tStep * 0.5);
        break;
    case BALANCED:
        // Dérive douce vers les valeurs de référence du contrat (10%/épisode)
        dynamicTSla   = Math.min(contractTSla, dynamicTSla + tStep * 0.3);
        dynamicPSlaHi += (TcdrmConstants.EMA_REPLICATION_THRESHOLD - dynamicPSlaHi) * 0.10;
        dynamicPSlaLo += (TcdrmConstants.EMA_DELETE_THRESHOLD       - dynamicPSlaLo) * 0.10;
        break;
}
```

L'état `BALANCED` applique une dérive exponentielle vers les valeurs contractuelles : si le système se stabilise, les seuils reviennent progressivement aux valeurs d'origine sans oscillation brutale.

---

## Résumé des bénéfices par rapport aux objectifs du Sujet 1

| Axe du Sujet 1 | Ce qui a été fait |
|---|---|
| **Axe 1** — Modélisation comme problème séquentiel | MDP formalisé avec états, actions (NOOP/REPLICATE/DELETE) et transitions dans `TcdrmSimulation` |
| **Axe 2** — États, actions, fonction de récompense | State vector à 9 dimensions, reward `R = r1·SLA_OK − r2·SLA_VIOL − r3·COST_OVER − r4·REPL_COST − r5·THRASH` |
| **Axe 3** — Algorithme d'apprentissage automatique | Q-Learning tabulaire et DQN intégrés via `PythonRLBridge` |
| **Axe 4** — Ajustement dynamique des seuils TSLA/PSLA | ✅ **Phases 1 & 2** : seuils dynamiques + machine à états inter-épisodes inspirée Mechouche 2024. CSLA reste statique (contrat). |
| **Axe 5** — Validation expérimentale | CloudSimPlus avec `BenchmarkRunner` comparant NoRep / TCDRM v1 / Q-Learning / DQN |

### Résultats attendus adressés

- **Décisions de réplication plus stables** : l'hystérésis à 2 épisodes et les états discrets éliminent le thrash entre politiques contradictoires.
- **Réduction des coûts inutiles** : l'état `CONSERVATIVE` retarde la réplication quand le budget est sous pression, indépendamment du taux de violations.
- **Respect du budget** : le CSLA reste statique (budget contractuel inviolable) ; seuls TSLA et PSLA s'adaptent pour équilibrer performance et coût.

---

## Fichier modifié dans cette session

| Fichier | Nature du changement |
|---|---|
| `src/main/java/org/tcdrm/adaptive/training/TrainingEnvironment.java` | Remplacement de `adaptThresholds()` heuristique par la machine à états `ReplicationState` + ajout de `getReplicationState()` |

Les fichiers `TcdrmSimulation.java` et `BenchmarkRunner.java` avaient été modifiés dans la session précédente (Phase 1) et ne sont pas retouchés ici. L'API publique est **inchangée**.
