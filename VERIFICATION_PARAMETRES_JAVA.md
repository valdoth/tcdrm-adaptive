# Vérification des Paramètres Java vs Article TCDRM V1

## 🔍 Analyse Complète des Fichiers Java

J'ai analysé tous les fichiers Java et détecté **les mêmes problèmes** que dans Python.

---

## ❌ Problèmes Détectés dans le Code Java

### **1. TSLA (Seuil de Latence) : 150ms au lieu de 200ms**

| Fichier | Ligne | Valeur Actuelle | Valeur Article |
|---------|-------|-----------------|----------------|
| `StaticTcdrmPolicy.java` | 30 | **150.0 ms** ❌ | 200 ms |
| `TcdrmMetricsPlotter.java` | 80 | **150.0 ms** ❌ | 200 ms |
| `TcdrmCloudSimEnvironment.java` | 52 | **150.0 ms** ❌ | 200 ms |

**Code actuel** :
```java
// StaticTcdrmPolicy.java:30
public StaticTcdrmPolicy() {
    this(150.0, 200, 0.2, 3);  // ❌ 150ms au lieu de 200ms
}

// TcdrmCloudSimEnvironment.java:52
private static final double SLA_LATENCY_THRESHOLD = 150.0;  // ❌
```

**Correction nécessaire** :
```java
// StaticTcdrmPolicy.java:30
public StaticTcdrmPolicy() {
    this(200.0, 200, 0.2, 5);  // ✅ 200ms + MAX_REPLICAS=5
}

// TcdrmCloudSimEnvironment.java:52
private static final double SLA_LATENCY_THRESHOLD = 200.0;  // ✅
```

---

### **2. MAX_REPLICAS : 3 au lieu de 5/13**

| Fichier | Ligne | Valeur Actuelle | Valeur Article |
|---------|-------|-----------------|----------------|
| `StaticTcdrmPolicy.java` | 30 | **3** ❌ | 5 (simple) / 13 (complex) |
| `TcdrmCloudSimEnvironment.java` | 54 | **3** ❌ | 5 (simple) / 13 (complex) |
| `RealRLBenchmark.java` | 25 | **3** ❌ | 5 (simple) / 13 (complex) |

**Code actuel** :
```java
// StaticTcdrmPolicy.java:30
public StaticTcdrmPolicy() {
    this(150.0, 200, 0.2, 3);  // ❌ MAX_REPLICAS=3
}

// TcdrmCloudSimEnvironment.java:54
private static final int MAX_REPLICAS = 3;  // ❌
```

**Correction nécessaire** :
```java
// StaticTcdrmPolicy.java:30
public StaticTcdrmPolicy() {
    this(200.0, 200, 0.2, 5);  // ✅ MAX_REPLICAS=5 pour simple queries
}

// TcdrmCloudSimEnvironment.java:54
private static final int MAX_REPLICAS = 5;  // ✅ Pour simple queries
```

---

### **3. COST_BW_INTER_PROVIDER : 0.10 au lieu de 0.01**

| Fichier | Ligne | Valeur Actuelle | Valeur Article (Tableau 1) |
|---------|-------|-----------------|---------------------------|
| `TcdrmCloudSimEnvironment.java` | 49 | **0.10** ❌ | 0.01 |
| `TcdrmEnvironment.java` | 20 | **0.10** ❌ | 0.01 |
| `TcdrmEnvironmentV2.java` | 22 | **0.10** ❌ | 0.01 |
| `NoRepBenchmark.java` | 14 | **0.10** ❌ | 0.01 |
| `RealRLBenchmark.java` | 29 | **0.10** ❌ | 0.01 |
| `TcdrmBenchmark.java` | 21 | **0.10** ❌ | 0.01 |
| `TcdrmBenchmarkPerQuery.java` | 25 | **0.10** ❌ | 0.01 |

**Code actuel** :
```java
// TcdrmCloudSimEnvironment.java:49
private static final double COST_BW_INTER_PROVIDER = 0.10;  // ❌

// TcdrmBenchmarkPerQuery.java:25
private static final double COST_BW_INTER_PROVIDER = 0.10;  // ❌
```

**Correction nécessaire** :
```java
private static final double COST_BW_INTER_PROVIDER = 0.01;  // ✅ Tableau 1
```

---

### **4. COST_BW_INTER_REGION : Manquant ou Incorrect**

| Fichier | Ligne | Valeur Actuelle | Valeur Article (Tableau 1) |
|---------|-------|-----------------|---------------------------|
| `TcdrmBenchmark.java` | 20 | **0.01** ❌ | 0.008 |
| `TcdrmBenchmarkPerQuery.java` | 24 | **0.008** ✅ | 0.008 |

**Code actuel** :
```java
// TcdrmBenchmark.java:20
private static final double COST_BW_INTER_REGION = 0.01;  // ❌ Devrait être 0.008
```

**Correction nécessaire** :
```java
private static final double COST_BW_INTER_REGION = 0.008;  // ✅ Tableau 1
```

---

## 📊 Résumé des Corrections Nécessaires

### **Fichiers à Corriger**

| Fichier | Paramètres à Corriger |
|---------|----------------------|
| `StaticTcdrmPolicy.java` | TSLA: 150→200, MAX_REPLICAS: 3→5 |
| `TcdrmMetricsPlotter.java` | SLA_THRESHOLD: 150→200 |
| `TcdrmCloudSimEnvironment.java` | SLA_LATENCY_THRESHOLD: 150→200, MAX_REPLICAS: 3→5, COST_BW_INTER_PROVIDER: 0.10→0.01 |
| `TcdrmEnvironment.java` | COST_BW_INTER_PROVIDER: 0.10→0.01 |
| `TcdrmEnvironmentV2.java` | COST_BW_INTER_PROVIDER: 0.10→0.01 |
| `NoRepBenchmark.java` | COST_BW_INTER_PROVIDER: 0.10→0.01 |
| `RealRLBenchmark.java` | MAX_REPLICAS: 3→5, COST_BW_INTER_PROVIDER: 0.10→0.01 |
| `TcdrmBenchmark.java` | COST_BW_INTER_PROVIDER: 0.10→0.01, COST_BW_INTER_REGION: 0.01→0.008 |
| `TcdrmBenchmarkPerQuery.java` | COST_BW_INTER_PROVIDER: 0.10→0.01 |

---

## 🎯 Impact des Corrections Java

### **Avant Corrections**

```
Scénario Java : 1 requête, data=5.3GB (si non corrigé)
├─ Transfert inter-cloud : 5.3 × 0.10 = 0.53 $
├─ TSLA : 150 ms (trop permissif)
├─ MAX_REPLICAS : 3 (limite artificielle)
└─ Résultats : NON comparables avec article
```

### **Après Corrections**

```
Scénario Java : 1 requête, data=0.45GB
├─ Transfert inter-cloud : 0.45 × 0.01 = 0.0045 $
├─ TSLA : 200 ms (conforme)
├─ MAX_REPLICAS : 5 (conforme simple queries)
└─ Résultats : COMPARABLES avec article ✅
```

**Réduction des coûts** : 0.53 $ → 0.0045 $ = **Divisé par 118** 🎯

---

## ✅ Paramètres Corrects (Déjà Conformes)

| Paramètre | Fichier | Valeur | Article |
|-----------|---------|--------|---------|
| `PSLA` | StaticTcdrmPolicy.java | 200 | ✅ 200 accès |
| `COST_BW_INTRA_DC` | Tous | 0.002 | ✅ 0.002 $/GB |
| `STORAGE_COST` | Tous | 0.02/720 | ✅ $0.02/GB/mois |
| `BW_LOCAL_GBPS` | Tous | 10.0 | ✅ Raisonnable |
| `BW_REMOTE_GBPS` | Tous | 1.0 | ✅ Raisonnable |

---

## 🔧 Corrections à Appliquer

### **1. StaticTcdrmPolicy.java**

```java
// AVANT (ligne 30)
public StaticTcdrmPolicy() {
    this(150.0, 200, 0.2, 3);  // ❌
}

// APRÈS
public StaticTcdrmPolicy() {
    this(200.0, 200, 0.2, 5);  // ✅ TSLA=200ms, MAX_REPLICAS=5
}
```

---

### **2. TcdrmMetricsPlotter.java**

```java
// AVANT (ligne 80)
double SLA_THRESHOLD = 150.0; // ms  // ❌

// APRÈS
double SLA_THRESHOLD = 200.0; // ms  // ✅ Article: 200ms
```

---

### **3. TcdrmCloudSimEnvironment.java**

```java
// AVANT (lignes 49-54)
private static final double COST_BW_INTER_PROVIDER = 0.10;  // ❌
private static final double SLA_LATENCY_THRESHOLD = 150.0;  // ❌
private static final int MAX_REPLICAS = 3;  // ❌

// APRÈS
private static final double COST_BW_INTER_PROVIDER = 0.01;  // ✅ Tableau 1
private static final double SLA_LATENCY_THRESHOLD = 200.0;  // ✅ Tableau 1
private static final int MAX_REPLICAS = 5;  // ✅ Simple queries
```

---

### **4. TcdrmEnvironment.java & TcdrmEnvironmentV2.java**

```java
// AVANT
private static final double COST_BW_INTER_PROVIDER = 0.10;  // ❌

// APRÈS
private static final double COST_BW_INTER_PROVIDER = 0.01;  // ✅ Tableau 1
```

---

### **5. Tous les Benchmarks (NoRep, TCDRM, RealRL)**

```java
// AVANT
private static final double COST_BW_INTER_PROVIDER = 0.10;  // ❌
private static final int MAX_REPLICAS = 3;  // ❌ (si présent)

// APRÈS
private static final double COST_BW_INTER_PROVIDER = 0.01;  // ✅ Tableau 1
private static final int MAX_REPLICAS = 5;  // ✅ Simple queries
```

---

### **6. TcdrmBenchmark.java (Correction Supplémentaire)**

```java
// AVANT (ligne 20)
private static final double COST_BW_INTER_REGION = 0.01;  // ❌

// APRÈS
private static final double COST_BW_INTER_REGION = 0.008;  // ✅ Tableau 1
```

---

## 📋 Checklist de Vérification Java

### **Paramètres SLA**
- [ ] TSLA : 150ms → **200ms**
- [ ] PSLA : 200 accès ✅ (déjà correct)
- [ ] CSLA : Vérifier si utilisé (budget ratio)

### **Coûts de Bande Passante**
- [ ] COST_BW_INTRA_DC : 0.002 $/GB ✅ (déjà correct)
- [ ] COST_BW_INTER_REGION : **0.008 $/GB** (corriger TcdrmBenchmark.java)
- [ ] COST_BW_INTER_PROVIDER : 0.10 → **0.01 $/GB**

### **Limites de Réplication**
- [ ] MAX_REPLICAS : 3 → **5** (simple queries)
- [ ] MAX_REPLICAS : 3 → **13** (complex queries, si applicable)

### **Autres Paramètres**
- [ ] STORAGE_COST : 0.02/720 $/GB/h ✅ (déjà correct)
- [ ] BW_LOCAL_GBPS : 10.0 ✅ (déjà correct)
- [ ] BW_REMOTE_GBPS : 1.0 ✅ (déjà correct)

---

## 🚀 Prochaines Actions

1. **Appliquer les corrections Java** (9 fichiers à modifier)
2. **Re-compiler le projet** : `mvn clean compile`
3. **Re-exécuter les benchmarks Java** : `./run_complete_workflow.sh`
4. **Comparer les résultats** avec l'article (Figures 2-7)

---

## 📈 Résultats Attendus Après Corrections

### **NOREP (NoRepLc)**
- Coût par requête : ~0.0045 $ (0.45 GB × 0.01 $/GB)
- Temps de réponse : ~360 ms
- Coût total (1000 requêtes) : ~4.5 $

### **TCDRM Static**
- Réplication commence après ~200 requêtes (PSLA)
- Maximum 5 réplicas créés
- Coût total (1000 requêtes) : ~1.0 $ (réduction ~78%)
- Temps de réponse moyen : ~180 ms (réduction ~51%)

### **TCDRM-ADAPTIVE (Q-Learning/DQN)**
- Performance > TCDRM Static
- Coût total < 1.0 $ (réduction > 78%)
- Temps de réponse < 180 ms (réduction > 51%)

---

## ⚠️ Note sur l'Incohérence 0.01 vs 0.10

Comme pour Python, l'article contient une incohérence :
- **Tableau 1** : 0.01 $/GB
- **Section 6** : ~0.10 $/GB (ratio 5× vs storage)

**Décision** : Utiliser **0.01 $/GB** (Tableau 1) pour reproduire les résultats de simulation de l'article.

Si les résultats ne correspondent pas aux Figures 2-7, on pourra tester avec 0.10 $/GB.

---

## ✅ Conclusion

**Tous les fichiers Java ont les MÊMES problèmes que Python** :
1. ❌ TSLA : 150ms au lieu de 200ms
2. ❌ MAX_REPLICAS : 3 au lieu de 5
3. ❌ COST_BW_INTER_PROVIDER : 0.10 au lieu de 0.01
4. ❌ COST_BW_INTER_REGION : 0.01 au lieu de 0.008 (TcdrmBenchmark.java)

**Impact** : Coûts et temps divisés par ~100 après corrections complètes (data_gb + BW).

**Prochaine étape** : Appliquer les corrections Java pour aligner avec l'article TCDRM V1.
