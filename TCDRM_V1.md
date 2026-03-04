# TCDRM : Un Framework de Réplication de Données Sensible au Budget du Tenant pour le Cloud Multi-Cloud

> **Journal of Logistics, Informatics and Service Science**  
> Vol. 12 (2025) No. 3, pp. 246–263  
> DOI: 10.33168/JLISS.2025.0315  
> ISSN: 2409-2665

---

## Auteurs

| Nom | Affiliation |
|-----|-------------|
| Santatra Hagamalala Bernardin | Université d'Antananarivo, Mention Informatique et Technologies, Madagascar |
| Riad Mokadem | Institut de Recherche en Informatique de Toulouse (IRIT), Université de Toulouse, France |
| Franck Morvan | Institut de Recherche en Informatique de Toulouse (IRIT), Université de Toulouse, France |
| Hasinarivo Ramanana | Université d'Antananarivo, Madagascar |
| Hasimandimby Rakotoarivelo | Université d'Antananarivo, Madagascar |

**Contact :** hagamalala.bernardin@univ-antananarivo.mg

---

## Résumé

Les systèmes de cloud multi-cloud font face à des défis importants pour garantir des performances acceptables tout en respectant les contraintes budgétaires des tenants. Cet article propose **TCDRM** (_Tenant Budget-Aware Data Replication Framework for Multi-Cloud Computing_), un framework de réplication de données centré sur le tenant.

### Points clés du résumé

- **Stratégie dynamique** : création de répliques basée sur des seuils prédéfinis de temps de réponse, budget économique du tenant et popularité des données.
- **Algorithme heuristique** : placement des répliques exploitant la diversité des structures tarifaires des fournisseurs cloud.
- **Objectif** : maintenir les performances requises sans dépasser le budget du tenant.
- **Rôle du middleware** : intermédiaire entre les tenants et les fournisseurs cloud pour des décisions intelligentes de placement.

### Résultats principaux

| Métrique | Amélioration |
|----------|-------------|
| Consommation de bande passante | Réduction jusqu'à **78%** vs approches sans réplication |
| Temps de réponse (requêtes complexes) | Diminution de **51%** en moyenne |
| Respect du budget tenant | ✅ Maintenu dans tous les scénarios testés |

**Mots-clés :** Cloud multi-cloud, Réplication de données, Contrat SLA, Budget du tenant.

---

## 1. Introduction

### Contexte

Les environnements multi-cloud sont devenus le paradigme privilégié pour déployer des applications intensives en données car ils permettent de combiner les ressources de plusieurs fournisseurs cloud. Comparés aux solutions single-cloud, ils offrent :

- **Flexibilité accrue**
- **Tolérance aux pannes**
- **Optimisation des coûts** grâce à la sélection de fournisseurs selon les tarifs, les performances et la disponibilité géographique

### Problème identifié

Le principal défi dans la gestion des données multi-cloud est d'activer une réplication efficace qui prend en compte :
1. Les performances techniques (latence, disponibilité)
2. Les **contraintes budgétaires individuelles des tenants**

La plupart des stratégies de réplication existantes se concentrent sur la maximisation du profit du fournisseur ou l'efficacité globale du système, avec peu d'attention aux coûts spécifiques des tenants. Cela crée des inefficacités dans les scénarios multi-tenants où chaque utilisateur peut avoir des attentes différentes en termes de budget et de SLA.

### Lacune dans la littérature

> *"Comment concevoir une stratégie de réplication de données qui s'adapte aux budgets des tenants dans un environnement multi-cloud ?"*

### Contributions principales de l'article

1. **Formalisation** du défi de la réplication de données centrée sur le tenant dans les environnements multi-cloud.
2. **Proposition de TCDRM** : stratégie de réplication sensible au budget intégrant les seuils de budget, popularité et temps de réponse.
3. **Développement d'une heuristique** de placement des répliques réduisant l'espace de recherche.
4. **Extension de CloudSim** pour simuler un environnement multi-cloud avec réplication, modélisation des coûts et routage des requêtes.
5. **Validation** par simulation démontrant le respect des performances tout en respectant les budgets.

---

## 2. Travaux Connexes

### Classification des stratégies de réplication (Mokadem et al., 2022)

Les stratégies de réplication en cloud peuvent être classifiées en deux grandes catégories selon leur orientation économique :

#### 2.1 Stratégies orientées fournisseur (_Provider-oriented_)

Priorité à la réduction des coûts pour les fournisseurs tout en respectant les SLO définis.

**Travaux représentatifs :**
- Wei et al. (2010)
- Bonvin et al. (2010)
- Sousa & Machado (2012)
- Tos et al. (2016, 2018)
- Liu et al. (2019)
- Khelifa et al. (2022)

#### 2.2 Stratégies orientées tenant (_Tenant-oriented_)

Priorité à la réduction des coûts monétaires pour les tenants plutôt que pour les fournisseurs.

**Travaux représentatifs :**
- Sakr et al. (2011)
- Sharma et al. (2011)
- Sakr & Liu (2012)
- Zhao et al. (2015)
- Limam et al. (2019)
- John & Mirnalinee (2020)

### Détail des approches tenant-oriented notables

| Auteur(s) | Approche | Spécificité |
|-----------|----------|-------------|
| **Sakr & Liu (2012)** | Framework middleware entre applications et bases de données cloud | Provisionnement dynamique basé sur les politiques SLA |
| **Limam et al. (2019)** | DRAPP (_Dynamic Replication for Availability, Performance, and Profit_) | Déclenche la réplication en cas de violation de SLA (temps de réponse) |
| **Zhao et al. (2015)** | Framework de gestion des coûts via provisionnement intelligent | Répliques sur VMs dans différentes zones géographiques |

### Approches multi-cloud avec contraintes budgétaires

Un nombre limité d'études a examiné la prise en compte des budgets des tenants dans un contexte multi-cloud :

- Chang et al. (2012) — algorithme de programmation dynamique pour optimiser la réplication
- Abu-Libdeh et al. (2010) — diversité du stockage cloud
- Bessani et al. (2011, 2013) — stockage dépendable dans un cloud-of-clouds
- Liu & Shen (2017) — DAR : allocation de stockage et de requêtes
- Mansouri & Buyya (2019) — réplication dynamique avec hot-spot/cold-spot
- Aldailamy et al. (2024) — algorithmes en ligne DTS et RTS pour réseaux sociaux

### Lacune identifiée

> À la connaissance des auteurs, **aucun travail n'a encore proposé de stratégies de réplication de données orientées tenant pour les bases de données dans un environnement multi-cloud**.

TCDRM comble cette lacune en étant **la première stratégie orientée tenant spécifiquement conçue pour la gestion de bases de données dans un contexte multi-cloud**.

---

## 3. La Stratégie TCDRM Proposée

TCDRM répond à quatre questions fondamentales liées à la réplication de données :

| Question | Module associé |
|----------|---------------|
| **Quand et quoi** répliquer ? | Module de décision (popularité + coût) |
| **Où** répliquer ? | Algorithme de placement heuristique |
| **Supprimer** les répliques ? | Algorithme de suppression (monitoring popularité) |
| **Combien** ça coûte ? | Modèle économique (CPU + I/O + bande passante) |

### 3.1 Architecture

L'architecture repose sur un **middleware** servant d'interface entre l'utilisateur et plusieurs fournisseurs cloud.

```
[Tenant] ──────── Internet ──────── [MIDDLEWARE]
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
              [CP1 (AWS)]         [CP2 (GCP)]         [CP3 (Azure)]
              ┌─────────┐         ┌─────────┐         ┌─────────┐
              │ RG1│RG2 │         │ RG1│RG2 │         │ RG1│RG2 │
              │ DC │ DC │         │ DC │ DC │         │ DC │ DC │
              └─────────┘         └─────────┘         └─────────┘
```

**Hiérarchie de communication et coûts :**
- **Intra-datacenter** : bande passante abondante, coût minimal
- **Inter-datacenters (même région)** : coût modéré
- **Inter-régions (même fournisseur)** : coût intermédiaire
- **Inter-fournisseurs** : coût le plus élevé ⚠️

**Notation formelle :**
- `CP = {CP1, ..., CPj, ..., CPr}` — ensemble de r fournisseurs
- `RGp = {RGp1, ..., RGpi, ..., RGpq}` — ensemble de q régions du fournisseur p
- `DC = {DCpi1, ..., DCpij, ..., DCpin}` — ensemble de n DCs dans la région RGpi du fournisseur p

### 3.2 Quand et quoi répliquer ?

La création d'un nouveau réplique dépend de **trois critères** :

1. **Temps de réponse** : `tQ > TSLA`
2. **Coût monétaire** : `cQ > CSLA`
3. **Popularité des données** : `pdi > PSLA`

#### Métrique de popularité (Chettaoui & Ben Charrada, 2012)

$$p_{di} = \frac{\#Requests}{T_{current} - T_{first\_request} + 1}$$

Où :
- `#Requests` = nombre total de requêtes reçues pour le dataset
- `T_current` = horodatage actuel
- `T_first_request` = horodatage du premier accès au dataset

> **Avantage** : évite le biais introduit par les périodes d'inactivité avant le premier usage et fournit une représentation réaliste de la popularité pendant la durée d'utilisation active.

#### Algorithme 1 : Création de répliques

```
ENTRÉES :
  Q         : Requête à exécuter
  TSLA      : Seuil de temps de réponse
  CSLA      : Seuil de coût monétaire
  D         : Ensemble de données
  PSLA      : Seuil de popularité

SORTIE :
  RD        : Liste des données à répliquer (initialement vide)

DÉBUT
  1. Exécuter Q
  2. tQ ← tempsRéponse(Q)
  3. cQ ← coûtMonétaire(Q)
  4. SI (tQ > TSLA OU cQ > CSLA) ALORS
  5.   POUR chaque donnée di dans D FAIRE
  6.     pdi ← popularitéDonnée(di)
  7.     SI (pdi > PSLA) ALORS
  8.       Ajouter di à RD
  9.     FIN SI
  10.  FIN POUR
  11. FIN SI
FIN
```

### 3.3 Où répliquer ?

L'algorithme de placement heuristique réduit l'espace de recherche en filtrant les fournisseurs selon deux critères en cascade :

1. **Filtre économique** : éliminer les fournisseurs dont le coût estimé dépasse CSLA
2. **Filtre performance** : sélectionner le premier fournisseur dont le temps de réponse estimé respecte TSLA

#### Algorithme 2 : Heuristique de placement

```
ENTRÉES :
  RD        : Données identifiées pour réplication
  P         : Fournisseurs cloud disponibles
  Q         : Requête ayant déclenché la réplication
  TSLA      : Seuil de temps de réponse
  CSLA      : Seuil de coût monétaire maximum

SORTIE :
  Décision finale de placement (fournisseur sélectionné pour chaque donnée)

DÉBUT
  1. POUR chaque donnée rk dans RD FAIRE
  2.   pi ← premierFournisseur(P)
  3.   TANT QUE pi ET (eMci > CSLA OU eRespTi > TSLA) FAIRE
  4.     eMci ← coûtMonétaireEstimé(Q, rk, pi)
  5.     SI (eMci < CSLA) ALORS
  6.       eRespTi ← tempsRéponseEstimé(Q, rk, pi)
  7.       SI (eRespTi < TSLA) ALORS
  8.         PLACER(rk, pi)
  9.       FIN SI
  10.    FIN SI
  11.    pi ← fournisseurSuivant(P)
  12.  FIN TANT QUE
  13. FIN POUR
FIN
```

**Avantage de l'approche bi-filtre :**
- Réduit significativement l'espace de recherche
- Garantit le respect des contraintes économiques ET de performance
- Améliore l'efficacité globale de la stratégie

### 3.4 Suppression des répliques

Pour éviter des coûts de stockage inutiles, TCDRM intègre un module de suppression basé sur la popularité.

**Règle de suppression :**
- Une réplique est supprimée si sa popularité reste **en dessous de PSLA** pendant toute la durée de `deltaT`
- `deltaT` = fenêtre d'observation configurable (ex. : heures ou intervalles de planification)

> **Avantage** : évite les créations/suppressions fréquentes de répliques (oscillations), limitant les coûts inutiles.

#### Algorithme 3 : Suppression des répliques peu populaires

```
ENTRÉES :
  PR        : Période d'observation prédéfinie
  DR        : Ensemble des données actuellement répliquées
  PSLA      : Seuil de popularité

SORTIE :
  Suppression des répliques peu populaires

DÉBUT
  1. POUR chaque Période p dans PR FAIRE
  2.   POUR CHAQUE DonnéeRépliquée rd dans DR FAIRE
  3.     popularité ← calculerPopularité(rd)
  4.     SI popularité < PSLA pendant deltaT ALORS
  5.       supprimerRéplique(rd)
  6.     FIN SI
  7.   FIN POUR
  8. FIN POUR
FIN
```

### 3.5 Modèle Économique

#### Formule du coût total par requête

$$C_Q = C_{CPU} + C_{IO} + C_{bandwidth}$$

| Composante | Description | Tarification typique |
|------------|-------------|---------------------|
| **C_CPU** | Coût du temps CPU utilisé pendant l'exécution | $0.000011 – $0.000060 / vCPU-seconde |
| **C_IO** | Coût des opérations I/O (lecture/écriture disque + création répliques) | Par 1 000 opérations ou par GB transféré |
| **C_bandwidth** | Coût du transfert de données inter-régions ou inter-clouds | $0.01 – $0.12 / GB (données sortantes) |

> Ces paramètres de coût sont basés sur les tarifs publics des principaux fournisseurs cloud (AWS, Azure, Google Cloud) et sont configurables.

#### Mécanisme de contrôle des coûts

- **Seuil CSLA** : coût maximum acceptable pour l'exécution d'une requête
- Si plusieurs fournisseurs qualifiés → sélection du moins cher
- Le fournisseur est traité comme une "boîte noire" → focus sur l'optimisation côté tenant

---

## 4. Analyse Expérimentale

### 4.1 Environnement de Simulation

**Outil utilisé :** CloudSim (Calheiros et al., 2011) — simulateur de référence en recherche cloud

**Extensions développées pour TCDRM :**

| Extension | Description |
|-----------|-------------|
| **Écosystème multi-cloud** | Simulation de AWS, Google Cloud et Microsoft Azure avec leurs propres DCs, types de VMs, tarifs et paramètres de performance |
| **Distribution géographique** | DCs répartis sur Europe, États-Unis et Asie |
| **Modèle de tâches interdépendantes** | Support des requêtes DB complexes composées d'opérations multiples distribuées |
| **Modèle de latence réseau hiérarchique** | Niveaux : intra-VM, intra-DC, inter-DC, inter-cloud |
| **Modèle économique intégré** | Coûts CPU, I/O, bande passante + transferts intra/inter-fournisseurs |

**Volume de simulation :** 1 000 requêtes, chacune impliquant 3 à 6 relations distribuées sur plusieurs fournisseurs.

### 4.2 Définition des Requêtes

| Type | Description |
|------|-------------|
| **Requêtes simples** | Jointures associant une relation à chacune des 3 régions |
| **Requêtes complexes** | Jointures entre relations sur les 3 régions, avec au moins 2 relations par région |

La région d'origine des requêtes est sélectionnée **aléatoirement** à la soumission.

### 4.3 Paramètres de Configuration (Tableau 1)

| Paramètre | Valeur |
|-----------|--------|
| Nombre de fournisseurs | 3 |
| Nombre de régions par fournisseur | 3 |
| Nombre de VMs par région | 20 |
| Taille moyenne d'une relation | 450 MB |
| **Requêtes simples** | |
| → TSLA | 200 ms |
| → CSLA | 0.015 $ par requête |
| **Requêtes complexes** | |
| → TSLA | 400 ms |
| → CSLA | 0.040 $ par requête |
| PSLA (popularité) | 200 |

**Tarifs de bande passante par fournisseur :**

| Type | Google (US/UE/AS) | AWS (US/UE/AS) | Azure (US/UE/AS) |
|------|------------------|----------------|-----------------|
| BW IntraDC ($/GB) | 0.0015 / 0.002 / 0.004 | 0.0015 / 0.002 / 0.004 | 0.0015 / 0.002 / 0.004 |
| I/O ($/GB) | 0.006 / 0.006 / 0.0066 | 0.0096 / 0.008 / 0.0096 | 0.0120 / 0.0096 / 0.0090 |
| BW InterRégion ($/GB) | 0.008 | 0.008 | 0.008 |
| BW Inter-fournisseur ($/GB) | 0.01 | 0.01 | 0.01 |
| CPU ($/10⁶ MI) | 0.020 / 0.025 / 0.027 | 0.020 / 0.018 / 0.020 | 0.0095 / 0.0090 / 0.0080 |

---

## 5. Résultats de Simulation

### 5.1 Facteur de Réplication (Figure 2)

**Ce que montre le graphique :**
Le nombre de répliques créées en fonction du nombre de requêtes exécutées, pour les requêtes simples et complexes.

**Analyse :**
- Le seuil de popularité PSLA est fixé à **200 accès**
- Avant le seuil (< 200 requêtes) : **aucune réplique créée** — les conditions ne sont pas encore remplies
- Après le seuil (~200 requêtes) : **augmentation progressive** du nombre de répliques
- **Requêtes complexes** : génèrent plus de répliques (maximum ~15) que les requêtes simples (~5-6)
- La différence s'explique par le nombre de jointures plus élevé dans les requêtes complexes et la distribution spatiale des relations

**Comportement clé :**
> TCDRM génère des répliques de manière ciblée en analysant chaque requête individuellement, plutôt que de répliquer massivement de manière préventive.

---

### 5.2 Impact sur le Temps de Réponse (Figure 3)

**Ce que montrent les graphiques (2 panels : simples et complexes) :**
Temps de réponse moyen en fonction du nombre de requêtes, comparant TCDRM vs NoRepLc.

**Analyse — Requêtes simples :**
- NoRepLc : courbe quasi-linéaire stable (~200 ms) car chaque requête nécessite des transferts inter-fournisseurs
- TCDRM : forte diminution du temps de réponse après le seuil PSLA, puis stabilisation (~100 ms)
- **Gain** : environ 50% de réduction du temps de réponse

**Analyse — Requêtes complexes :**
- NoRepLc : courbe avec fluctuations (~350–450 ms) dues à l'origine aléatoire des requêtes et aux échanges inter-régions
- TCDRM : déclin significatif après la création des répliques, stabilisation à ~200 ms
- **Gain** : réduction de **51%** en moyenne

**Explication du mécanisme :**
Après la création des répliques, TCDRM réduit les échanges inter-fournisseurs (coûteux et lents) en favorisant les transferts intra-fournisseur. La consommation de bande passante diminue, ce qui divise par deux le temps de réponse.

> Note : l'optimisation du temps de réponse **n'est pas l'objectif principal** de TCDRM (c'est le budget), mais elle en est un bénéfice indirect significatif.

---

### 5.3 Effet sur la Consommation de Bande Passante

#### 5.3.1 Comparaison Inter-fournisseur vs Inter-région (Figure 4)

**Ce que montrent les graphiques (barres, simples et complexes) :**
Consommation de BW en GB selon le type d'échange (interProvider en bleu, interRegion en rouge).

**Analyse :**
- **NoRepLc** : la consommation inter-fournisseurs est **nettement dominante** (~1400 GB simples, ~2700 GB complexes) car les relations sont distribuées sur différents fournisseurs
- **TCDRM** : inversion du rapport — les échanges inter-régions deviennent prépondérants, les échanges inter-fournisseurs quasi nuls
- **Explication** : les répliques créées sont placées chez le même fournisseur que la requête, éliminant les transferts inter-fournisseurs coûteux

**Impact sur les coûts :**
> Le coût inter-fournisseur (~$0.01/GB) est significativement plus élevé que le coût inter-région (~$0.008/GB) ou intra-DC (~$0.0015/GB). Éliminer les transferts inter-fournisseurs est donc un levier majeur d'économie.

#### 5.3.2 Coût de BW par Requête (Figure 5)

**Ce que montrent les graphiques (courbes) :**
Évolution du coût moyen de bande passante par requête au fil des exécutions.

**Analyse :**
- **NoRepLc** : coût stable et élevé (~$0.020/requête simples, ~$0.040/requête complexes)
- **TCDRM** : 
  - Phase initiale (~0-200 requêtes) : coût similaire à NoRepLc (pas encore de répliques)
  - Phase de transition (~200-400 requêtes) : **oscillations** dues à la création progressive des répliques et à leur montée en utilisation
  - Phase stable (>400 requêtes) : coût significativement réduit (~$0.010/requête simples, ~$0.020/requête complexes)
  - Le plancher n'est pas parfaitement plat en raison des échanges inter-régions persistants

#### 5.3.3 Coût Cumulatif de BW (Figure 6)

**Ce que montrent les graphiques (courbes cumulatives) :**
Coût cumulatif de BW en fonction du nombre de requêtes exécutées.

**Analyse :**
- **NoRepLc** : trajectoire linéaire stable (pente constante) = coût proportionnel au volume de requêtes
- **TCDRM** : 
  - Début identique à NoRepLc (avant le seuil PSLA)
  - Après le seuil : **divergence progressive** — la pente de TCDRM devient nettement plus faible
  - Sur 1000 requêtes complexes : ~$30 (NoRepLc) vs ~$15 (TCDRM)
  - **Réduction cumulée de ~78%** pour les requêtes complexes

> **Interprétation économique** : l'investissement initial dans la création de répliques (coût de stockage + premier transfert) est rapidement amorti grâce aux économies répétées sur chaque requête suivante.

---

### 5.4 Effet sur le Coût Total (Figure 7)

**Ce que montre le graphique (barres empilées) :**
Comparaison des coûts totaux entre NoRepLc et TCDRM, décomposés en CPU (bleu), BW (rouge) et Répliques/Stockage (jaune).

**Analyse :**
| Composante | NoRepLc | TCDRM | Commentaire |
|------------|---------|-------|-------------|
| **CPU** | Constant | Constant | Identique pour les deux stratégies |
| **BW** | Dominant (~90% du total) | Réduit (~70% du total) | Principal levier d'économie |
| **Stockage répliques** | Nul | Négligeable | Coût minime (~$0.02/GB/mois) |

**Conclusion clé :**
- Le coût de BW est de loin le **facteur prédominant** dans le coût total
- Stocker une réplique (~$0.02/GB) est ~5× moins cher que transférer des données inter-cloud (~$0.10/GB)
- TCDRM réduit les transferts inter-cloud en échange d'un stockage local, avec un bilan économique très favorable

---

## 6. Analyse des Résultats

### Points forts de TCDRM

1. **Respect systématique du budget** : le tenant ne dépasse jamais le seuil CSLA défini dans son SLA
2. **Réduction BW de 78%** sur les requêtes complexes par rapport à NoRepLc
3. **Réduction du temps de réponse de 51%** sur les requêtes complexes (bénéfice indirect)
4. **Adaptabilité aux workloads** : fonctionne bien sur les accès répétitifs/groupés (amortissement rapide) et sur les accès irréguliers (respect du budget maintenu)
5. **Scalabilité attendue** : les gains devraient s'amplifier avec l'augmentation du nombre de fournisseurs

### Contexte des coûts cloud (AWS, Azure, GCP)

| Type de coût | Tarif moyen | Ratio |
|--------------|-------------|-------|
| Stockage standard | ~$0.02/GB/mois | 1× |
| Transfert inter-cloud (egress) | ~$0.10/GB | **5×** |

> Le transfert inter-cloud coûte 5× plus cher que le stockage → réduire les transferts est la stratégie économique optimale.

### Limitation reconnue

> **Dépendance aux seuils statiques** : dans des environnements dynamiques où les patterns de requêtes, la popularité des données et les prix fluctuent, des seuils fixes peuvent mener à des décisions sous-optimales.

---

## 7. Conclusion

### Récapitulatif des contributions

TCDRM est présenté comme un framework **novateur** car :
- Il est **le premier** à cibler la réplication tenant-orientée pour les **bases de données** dans un contexte **multi-cloud**
- Il équilibre dynamiquement les coûts de stockage, les surcharges de transfert et les contraintes budgétaires
- Il exploite la **diversité tarifaire** des fournisseurs multi-cloud comme opportunité d'optimisation

### Résultats validés

| Objectif | Résultat |
|----------|---------|
| Réduction du coût de BW | ✅ Jusqu'à **78%** pour les requêtes complexes |
| Réduction du temps de réponse | ✅ **51%** en moyenne |
| Respect du budget tenant | ✅ Maintenu dans tous les scénarios |
| Coût de stockage des répliques | ✅ Négligeable par rapport aux économies réalisées |

### Directions de travail futur

1. **Comparaison** avec d'autres stratégies de réplication de l'état de l'art
2. **Déploiement en environnement réel** multi-cloud pour valider les résultats de simulation
3. **Gestion de la cohérence des données** et des défis de sécurité dans les scénarios multi-cloud
4. **Apprentissage automatique** pour ajuster dynamiquement les seuils selon :
   - Les patterns de workload changeants
   - Les structures tarifaires évolutives des fournisseurs
   - Les tendances de popularité des données
5. **Évaluation à plus grande échelle** pour confirmer la scalabilité et la robustesse à long terme

---

## 8. Références Bibliographiques

| Auteur(s) | Année | Titre abrégé | DOI |
|-----------|-------|-------------|-----|
| Abouzamazem & Ezhilchelvan | 2013 | Efficient inter-cloud replication for HA services | 10.1109/IC2E.2013.27 |
| Abu-Libdeh et al. | 2010 | RACS: cloud storage diversity | 10.1145/1807128.1807165 |
| Aldailamy et al. | 2024 | Efficient multi-cloud storage for OSNs | 10.1109/ACCESS.2024.3361748 |
| Ali et al. | 2018 | DROPS: Division and Replication for Security | 10.1109/TCC.2015.2400460 |
| Alghamdi et al. | 2017 | Profit-based file replication in cloud | 10.1109/ICC.2017.7996728 |
| Armbrust et al. | 2010 | A view of cloud computing | 10.1145/1721654.1721672 |
| Bessani et al. | 2013 | DepSky: Cloud-of-Clouds storage | 10.1145/2535929 |
| Bonvin et al. | 2010 | Self-organized replication scheme | 10.1145/1807128.1807162 |
| Boru et al. | 2015 | Energy-efficient data replication | 10.1007/s10586-014-0404-x |
| Calheiros et al. | 2011 | CloudSim toolkit | 10.1002/spe.995 |
| Chang et al. | 2012 | Probability-based multi-cloud selection | 10.1109/ICPP.2012.51 |
| Chettaoui & Ben Charrada | 2012 | Decentralized Periodic Replication (Knapsack) | 10.1109/Grid.2012.23 |
| Chen et al. | 2014 | NCCloud: network-coding storage | 10.1109/TC.2013.167 |
| Dugyani & Govardhan | 2024 | Survey on data replication in cloud | 10.3233/WEB-230087 |
| Edwin et al. | 2019 | Multi-objective optimized replication | 10.1007/s10586-017-1313-6 |
| Foster et al. | 2008 | Cloud computing vs grid computing | 10.1109/GCE.2008.4738445 |
| Grozev & Buyya | 2015 | Performance modelling multi-cloud | 10.1093/comjnl/bxt107 |
| John & Mirnalinee | 2020 | Dynamic data replication for cloud storage | 10.1007/s10257-019-00422-x |
| Karvela et al. | 2021 | Cloud adoption in supply chain (SWOT) | — |
| Khelifa et al. | 2022 | Data correlation + fuzzy inference replication | 10.1016/j.simpat.2021.102428 |
| Li et al. | 2017 | Replication to erasure coding transition | 10.1109/TPDS.2017.2678505 |
| Limam et al. | 2019 | DRAPP: dynamic replication strategy | 10.1007/s10586-018-02899-6 |
| Liu & Shen | 2017 | DAR: minimum-cost cloud storage | 10.1109/TNET.2017.2693222 |
| Liu et al. | 2019 | Popularity-aware multi-failure replication | 10.1109/TPDS.2018.2873384 |
| Mansouri & Javidi | 2018 | Replication based on availability/popularity | 10.22060/miscj.2017.12236.5020 |
| Mansouri & Buyya | 2019 | Dynamic replication with hot/cold-spot | 10.1016/j.jpdc.2018.12.003 |
| Milani & Navimipour | 2016 | Review of data replication techniques | 10.1016/j.jnca.2016.02.005 |
| Miloudi et al. | 2020 | Replication based on data classification | 10.1007/978-3-030-58861-8_1 |
| Mokadem & Hameurlain | 2020 | Replication with tenant + provider guarantees | 10.1016/j.jss.2019.110447 |
| Mokadem et al. | 2022 | Review on data replication in cloud | 10.1504/IJGUC.2022.125135 |
| Sakr & Liu | 2012 | SLA-based dynamic provisioning cloud DB | 10.1109/CLOUD.2012.11 |
| Sakr et al. | 2011 | CloudDB AutoAdmin | 10.1109/ICWS.2011.19 |
| Séguéla et al. | 2021 | Energy and expenditure aware replication | 10.1109/CLOUD53861.2021.00056 |
| Shakarami et al. | 2021 | Survey: data replication in cloud | 10.1007/s10586-021-03283-7 |
| Sharma et al. | 2011 | Kingfisher: cost-aware elasticity | 10.1109/INFCOM.2011.5935016 |
| Sousa & Machado | 2012 | Elastic multi-tenant DB replication | 10.1109/UCC.2012.36 |
| Stantchev & Schröpfer | 2009 | QoS and SLA in grid/cloud | 10.1007/978-3-642-01671-4_3 |
| Tos et al. | 2016 | Performance and profit replication strategy | 10.1109/UIC-ATC-ScalCom-... |
| Tos et al. | 2018 | Ensuring performance/profit via replication | 10.1007/s10586-017-1507-y |
| Wang & Shannigrahi | 2025 | PSMOA: Multi-Objective Optimization Replication | arXiv:2505.14574 |
| Wei et al. | 2010 | CDRM: cost-effective dynamic replication | 10.1109/CLUSTER.2010.24 |
| Wu et al. | 2013 | SPANStore: geo-replicated multi-cloud storage | 10.1145/2517349.2522730 |
| Zhao et al. | 2015 | Consumer-centric SLA management cloud DB | 10.1109/TSC.2013.5 |

---

## Annexe : Glossaire des Termes Clés

| Terme | Définition |
|-------|------------|
| **TCDRM** | Tenant Budget-Aware Data Replication Framework for Multi-Cloud Computing |
| **SLA** | Service Level Agreement — contrat de niveau de service définissant les garanties de performance et de coût |
| **TSLA** | Seuil de temps de réponse défini dans le SLA |
| **CSLA** | Seuil de coût monétaire (budget maximum) défini dans le SLA |
| **PSLA** | Seuil de popularité des données défini dans le SLA |
| **NoRepLc** | No-Replication-Less Cost — stratégie de référence sans réplication avec sélection du fournisseur le moins cher |
| **Tenant** | Locataire/utilisateur d'un service cloud |
| **Middleware** | Couche logicielle intermédiaire entre le tenant et les fournisseurs cloud |
| **CP** | Cloud Provider — fournisseur de services cloud |
| **DC** | Data Center — centre de données |
| **RG** | Region — région géographique d'un fournisseur |
| **VM** | Virtual Machine — machine virtuelle |
| **CloudSim** | Simulateur open-source pour les environnements cloud computing |
| **Inter-Provider BW** | Bande passante entre fournisseurs cloud différents (coût le plus élevé) |
| **Inter-Region BW** | Bande passante entre régions d'un même fournisseur (coût intermédiaire) |
| **Intra-DC BW** | Bande passante au sein d'un même datacenter (coût minimal) |

---

*Document généré à partir de l'article original : Bernardin et al., Journal of Logistics, Informatics and Service Science, Vol. 12 (2025) No. 3, pp. 246–263*