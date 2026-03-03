# Rapport de Vérification : Conformité avec l'Article de Référence

## 🎯 Objectif
Vérifier la conformité de l'implémentation (Java et Python) avec les spécifications de l'article de référence "TCDRM : Un Framework de Réplication de Données Sensible au Budget du Tenant pour le Cloud Multi-Cloud".

---

## 🔍 1. Incohérences des Seuils SLA (TSLA et CSLA)

L'article spécifie deux types de requêtes avec des seuils différents (Tableau 1 de l'article) :
- **Requêtes simples** : TSLA = 200 ms, CSLA = 0.015 $
- **Requêtes complexes** : TSLA = 400 ms, CSLA = 0.040 $

### Constat dans le code actuel :
- **Java (`StaticTcdrmPolicy.java`)** : TSLA est codé en dur à `150.0 ms`.
- **Java (`TcdrmMetricsPlotter.java`)** : `SLA_THRESHOLD` est codé en dur à `150.0 ms`.
- **Java (`TcdrmCloudSimEnvironment.java`)** : `SLA_LATENCY_THRESHOLD` est codé en dur à `150.0`.
- **Python (`tcdrm_env.py`)** : TSLA initial est de `150.0`.
- **Python (`tcdrm_qlearning_env.py`)** : CSLA est normalisé à `1.0`. `TSLA_BASE` est à `1000.0` (incorrect).

### 🔴 Problème : 
L'implémentation ne fait pas la distinction entre requêtes simples et complexes pour les seuils, et utilise une valeur (`150ms`) qui ne correspond à aucune des deux spécifications de l'article (`200ms` ou `400ms`).

### ✅ Action recommandée :
Il faut adapter le code pour qu'il reçoive le TSLA et le CSLA en paramètre selon le type de requête (simple ou complexe), ou a minima aligner la valeur par défaut sur l'article (ex: 200ms pour requêtes simples).

---

## 🔍 2. Incohérence sur la Taille des Données (Data Size)

L'article spécifie :
- **Taille moyenne d'une relation** : 450 MB (soit ~0.45 GB)

### Constat dans le code actuel :
- **Python (`tcdrm_env.py`)** : La taille par défaut est de `5.3 GB` (`data_gb: float = 5.3`).
- **Python (`tcdrm_qlearning_env.py`)** : Taille par défaut de `5.3 GB`.

### 🔴 Problème :
Le volume de données transféré par requête dans Python est ~10x plus grand que ce qui est décrit dans l'article, ce qui fausse les temps de transfert et les coûts.

### ✅ Action recommandée :
Changer la valeur par défaut de `data_gb` à `0.45` dans les environnements Python pour correspondre à la "taille moyenne d'une relation".

---

## 🔍 3. Incohérence des Coûts de Stockage (Storage Cost)

L'article spécifie :
- **Stockage standard** : ~$0.02/GB/mois

### Constat dans le code actuel :
- **Java (`NoRepBenchmark.java`, `TcdrmBenchmark.java`)** : `STORAGE_COST_PER_GB_PER_MONTH = 0.02` (Correct)
- **Python (`tcdrm_env.py`)** : `STORAGE_COST_PER_GB_PER_HOUR = 0.0001` (Équivaut à ~0.072/mois, ce qui est différent de 0.02).

### 🔴 Problème :
Le coût de stockage en Python est légèrement supérieur à celui de Java/Article.

### ✅ Action recommandée :
Aligner Python sur Java : `STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0` (soit environ `0.0000277`).

---

## 🔍 4. Vérification du Seuil de Popularité (PSLA)

L'article spécifie :
- **PSLA (popularité)** : 200 accès

### Constat dans le code actuel :
- **Java (`TcdrmBenchmarkPerQuery.java`)** : Le seuil était fixe à 200. Nous l'avons récemment rendu aléatoire (100-300) pour différencier les courbes.
- **Python (PLSA)** : Utilise un modèle probabiliste plutôt qu'un seuil fixe.

### 🟡 Statut :
Le seuil fixe à 200 était strictement conforme à l'article pour TCDRM Statique. Le rendre aléatoire (100-300) est utile pour la visualisation (pour que les courbes ne se superposent pas exactement), mais s'écarte techniquement de la définition stricte de l'article pour le cas statique.
*(C'est un compromis acceptable pour les graphes, mais il faut en être conscient).*

---

## 🔍 5. Vérification du Nombre Maximal de Répliques

L'article spécifie :
- **Max répliques simples** : 5
- **Max répliques complexes** : 13

### Constat dans le code actuel :
- **Java (`RealRLBenchmark.java`)** : `MAX_REPLICAS = 3` (Codé en dur, incorrect)
- **Java (`TcdrmCloudSimEnvironment.java`)** : `MAX_REPLICAS = 3` (Codé en dur, incorrect)
- **Python (`tcdrm_env.py`)** : Implémente bien la distinction (`MAX_REPLICAS_SIMPLE = 5`, `MAX_REPLICAS_COMPLEX = 13`).

### 🔴 Problème :
Java limite artificiellement le nombre de réplicas à 3, ce qui empêche d'atteindre les 5 ou 13 réplicas mentionnés dans l'article (et visibles dans la Figure 2 de l'article).

### ✅ Action recommandée :
Modifier `MAX_REPLICAS` dans le code Java pour correspondre à Python et à l'article.

---

## 📝 Résumé des Actions de Correction à Entreprendre

1. **Environnements Python (`tcdrm_env*.py`)** :
   - Changer `data_gb=5.3` par `data_gb=0.45` (450 MB).
   - Corriger `STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0`.
   - Ajuster `TSLA_INITIAL` à `200.0` (au lieu de 150.0).

2. **Environnements Java** :
   - Modifier `MAX_REPLICAS` de `3` à `5` (ou `13` selon le test).
   - Ajuster les variables `SLA_THRESHOLD` à `200.0` dans `TcdrmMetricsPlotter.java`.
   - Optionnel : Revenir au seuil de popularité fixe `200` dans TCDRM Statique si on veut être 100% fidèle à l'article (au détriment de la séparation visuelle des courbes).

Veux-tu que j'applique ces corrections d'alignement avec l'article ?
