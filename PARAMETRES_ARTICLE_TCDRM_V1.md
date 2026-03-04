# Paramètres Exacts de l'Article TCDRM V1 (Tableau 1)

## 📋 Configuration Complète selon l'Article

### **Paramètres Généraux**

| Paramètre | Valeur | Source |
|-----------|--------|--------|
| **Nombre de fournisseurs** | 3 | Tableau 1 |
| **Nombre de régions par fournisseur** | 3 | Tableau 1 |
| **Nombre de VMs par région** | 20 | Tableau 1 |
| **Taille moyenne d'une relation** | **450 MB (0.45 GB)** | Tableau 1 |
| **Nombre de requêtes** | 1000 | Section 4.1 |

---

### **Seuils SLA**

#### **Requêtes Simples**
| Paramètre | Valeur | Source |
|-----------|--------|--------|
| **T_SLA** | **200 ms** | Tableau 1 |
| **C_SLA** | **0.015 $** par requête | Tableau 1 |

#### **Requêtes Complexes**
| Paramètre | Valeur | Source |
|-----------|--------|--------|
| **T_SLA** | **400 ms** | Tableau 1 |
| **C_SLA** | **0.040 $** par requête | Tableau 1 |

#### **Popularité**
| Paramètre | Valeur | Source |
|-----------|--------|--------|
| **P_SLA** | **200 accès** | Tableau 1 |

---

### **Coûts de Bande Passante ($/GB)**

#### **Intra-Datacenter (BW IntraDC)**
| Fournisseur | US | UE | AS |
|-------------|----|----|-----|
| **Google** | 0.0015 | 0.002 | 0.004 |
| **AWS** | 0.0015 | 0.002 | 0.004 |
| **Azure** | 0.0015 | 0.002 | 0.004 |

**Moyenne** : `0.002 $/GB` (valeur médiane)

---

#### **Inter-Région (BW InterRegion)**
| Fournisseur | Valeur ($/GB) |
|-------------|---------------|
| **Google** | 0.008 |
| **AWS** | 0.008 |
| **Azure** | 0.008 |

**Valeur unique** : `0.008 $/GB`

---

#### **Inter-Fournisseur (BW Inter-Provider)**
| Fournisseur | Valeur ($/GB) |
|-------------|---------------|
| **Google** | 0.01 |
| **AWS** | 0.01 |
| **Azure** | 0.01 |

**Valeur unique** : `0.01 $/GB`

⚠️ **ATTENTION** : L'article mentionne aussi `~$0.10/GB` dans la section 6 (Analyse des Résultats) pour les transferts inter-cloud (egress). Il semble y avoir une **incohérence** entre le Tableau 1 (0.01) et le texte (0.10).

**Recommandation** : Utiliser `0.01 $/GB` (Tableau 1) pour la cohérence avec les simulations, MAIS noter que les coûts réels AWS/Azure/GCP sont effectivement plus proches de `0.10 $/GB` pour l'egress inter-cloud.

---

### **Coûts I/O ($/GB)**

| Fournisseur | US | UE | AS |
|-------------|----|----|-----|
| **Google** | 0.006 | 0.006 | 0.0066 |
| **AWS** | 0.0096 | 0.008 | 0.0096 |
| **Azure** | 0.0120 | 0.0096 | 0.0090 |

**Moyenne approximative** : `0.008 $/GB`

---

### **Coûts CPU ($/10⁶ MI)**

| Fournisseur | US | UE | AS |
|-------------|----|----|-----|
| **Google** | 0.020 | 0.025 | 0.027 |
| **AWS** | 0.020 | 0.018 | 0.020 |
| **Azure** | 0.0095 | 0.0090 | 0.0080 |

**Moyenne approximative** : `0.015 $/10⁶ MI`

⚠️ **Note** : MI = Million Instructions (unité de mesure CPU)

---

### **Coûts de Stockage**

| Type | Valeur | Source |
|------|--------|--------|
| **Stockage standard** | **$0.02/GB/mois** | Section 6 |
| **Stockage par heure** | **$0.02/720 ≈ 0.0000277 $/GB/heure** | Calculé |

---

## 🔧 Corrections à Appliquer

### **1. Coûts de Bande Passante**

#### **Python : tcdrm_qlearning_env.py**

```python
# ACTUELLEMENT
self.COST_BW_INTRA_DC = 0.002           # ✅ Correct
self.COST_BW_INTER_PROVIDER = 0.10      # ❌ INCORRECT (devrait être 0.01)

# CORRECTION
self.COST_BW_INTRA_DC = 0.002           # ✅ Correct (moyenne Tableau 1)
self.COST_BW_INTER_REGION = 0.008       # ✅ À ajouter (Tableau 1)
self.COST_BW_INTER_PROVIDER = 0.01      # ✅ Corriger (Tableau 1)
```

#### **Python : tcdrm_env_v2.py**

```python
# ACTUELLEMENT
self.COST_BW_INTRA_DC = 0.002           # ✅ Correct
self.COST_BW_INTER_REGION = 0.05        # ❌ INCORRECT (devrait être 0.008)
self.COST_BW_INTER_CLOUD = 0.10         # ❌ INCORRECT (devrait être 0.01)

# CORRECTION
self.COST_BW_INTRA_DC = 0.002           # ✅ Correct (moyenne Tableau 1)
self.COST_BW_INTER_REGION = 0.008       # ✅ Corriger (Tableau 1)
self.COST_BW_INTER_CLOUD = 0.01         # ✅ Corriger (Tableau 1)
```

---

### **2. Coût de Réplication**

Le coût de réplication devrait utiliser le coût inter-provider (car créer un réplica nécessite un transfert inter-cloud).

```python
# ACTUELLEMENT
self.REPLICATION_COST_PER_GB = self.COST_BW_INTER_PROVIDER  # 0.10 ❌

# CORRECTION
self.REPLICATION_COST_PER_GB = self.COST_BW_INTER_PROVIDER  # 0.01 ✅
# (sera automatiquement correct après correction de COST_BW_INTER_PROVIDER)
```

---

### **3. Paramètres Réseau (Latence et Bande Passante)**

Les valeurs actuelles semblent raisonnables mais ne sont pas explicitement dans l'article.

```python
# ACTUELLEMENT (tcdrm_qlearning_env.py et tcdrm_env_v2.py)
self.BW_LOCAL_GBPS = 10.0      # Bande passante locale (intra-DC)
self.BW_REMOTE_GBPS = 1.0      # Bande passante distante (inter-cloud)
self.LAT_LOCAL_MS = 1.0        # Latence locale
self.LAT_REMOTE_MS = 100.0     # Latence distante

# RECOMMANDATION : Garder ces valeurs (raisonnables et cohérentes)
```

---

## 📊 Impact des Corrections

### **Avant Correction**

```
Scénario : Transfert de 0.45 GB inter-cloud
├─ Coût actuel : 0.45 × 0.10 = 0.045 $
└─ Coût de réplication : 0.045 $
```

### **Après Correction**

```
Scénario : Transfert de 0.45 GB inter-cloud
├─ Coût corrigé : 0.45 × 0.01 = 0.0045 $
└─ Coût de réplication : 0.0045 $
```

**Impact** : Coûts divisés par **10** ! 🎯

---

## ⚠️ Incohérence Détectée dans l'Article

### **Problème : Deux Valeurs pour le Coût Inter-Cloud**

| Source | Valeur | Contexte |
|--------|--------|----------|
| **Tableau 1** | 0.01 $/GB | Paramètres de simulation |
| **Section 6 (Analyse)** | ~0.10 $/GB | "transfer costs can be five times higher than storage costs" |

### **Analyse**

L'article mentionne dans la section 6 :

> "standard storage is approximately $0.02/GB per month, while inter-cloud data transfer (egress) averages $0.10/GB. Thus, transfer costs can be **five times higher** than storage costs"

**Calcul** : 0.10 / 0.02 = 5× ✅

Mais le Tableau 1 indique `0.01 $/GB` pour BW Inter-Provider.

**Calcul avec Tableau 1** : 0.01 / 0.02 = 0.5× ❌ (incohérent avec "5× plus cher")

### **Hypothèses**

1. **Hypothèse 1** : Le Tableau 1 contient une erreur typographique (0.01 au lieu de 0.10)
2. **Hypothèse 2** : Le Tableau 1 utilise des tarifs simplifiés pour la simulation
3. **Hypothèse 3** : Les tarifs réels AWS/Azure/GCP (~0.10 $/GB) sont mentionnés dans l'analyse mais la simulation utilise des valeurs réduites

### **Recommandation**

**Option A (Fidélité au Tableau 1)** :
- Utiliser `0.01 $/GB` pour correspondre exactement aux paramètres de simulation
- Accepter l'incohérence avec la section 6

**Option B (Fidélité à la Section 6)** :
- Utiliser `0.10 $/GB` pour correspondre aux tarifs réels et au ratio 5×
- S'écarter du Tableau 1

**CHOIX RECOMMANDÉ** : **Option A** (Tableau 1)
- Les simulations de l'article ont été faites avec ces paramètres
- Les résultats (Fig. 2-7) correspondent à ces valeurs
- Pour reproduire les résultats, il faut utiliser les mêmes paramètres

---

## ✅ Paramètres Finaux Recommandés

### **Pour Python (Q-Learning et DQN)**

```python
# Paramètres généraux
data_gb = 0.45                          # 450 MB (Tableau 1)
MAX_QUERIES = 1000                      # Section 4.1
INITIAL_BUDGET = 1000.0                 # Budget initial (non spécifié, valeur raisonnable)

# SLA (Simple Queries)
TSLA_BASE = 200.0                       # ms (Tableau 1)
CSLA = 0.015                            # $ par requête (Tableau 1)
PSLA = 200                              # accès (Tableau 1)
RT_MAX = 0.200                          # secondes (converti de 200ms)

# Coûts de bande passante ($/GB)
COST_BW_INTRA_DC = 0.002               # Tableau 1 (moyenne)
COST_BW_INTER_REGION = 0.008           # Tableau 1
COST_BW_INTER_PROVIDER = 0.01          # Tableau 1 ⚠️ (ou 0.10 selon Section 6)
COST_BW_INTER_CLOUD = 0.01             # Alias pour INTER_PROVIDER

# Coûts de stockage
STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0  # ~0.0000277 $/GB/heure

# Coût de réplication
REPLICATION_COST_PER_GB = COST_BW_INTER_PROVIDER  # 0.01 $/GB

# Paramètres réseau
BW_LOCAL_GBPS = 10.0                   # Bande passante locale (raisonnable)
BW_REMOTE_GBPS = 1.0                   # Bande passante distante (raisonnable)
LAT_LOCAL_MS = 1.0                     # Latence locale (raisonnable)
LAT_REMOTE_MS = 100.0                  # Latence distante (raisonnable)

# MAX_REPLICAS
MAX_REPLICAS_SIMPLE = 5                # Figure 2 (simple queries)
MAX_REPLICAS_COMPLEX = 13              # Figure 2 (complex queries)
```

---

## 🎯 Résultats Attendus Après Corrections

### **Métriques Clés (selon Article)**

| Métrique | NOREP | TCDRM | Amélioration |
|----------|-------|-------|--------------|
| **Bandwidth Reduction** | Baseline | -78% | 78% de réduction |
| **Response Time (Complex)** | ~400 ms | ~200 ms | -51% |
| **Budget Compliance** | Variable | 100% | Respect total |
| **Total Cost (1000 queries)** | Baseline | ~50% | 50% d'économie |

### **Graphes Attendus**

- **Fig. 2** : Réplicas atteignent 5 (simple) ou 13 (complex) après ~200 requêtes
- **Fig. 3** : Temps de réponse réduit de 51% après stabilisation
- **Fig. 6** : Coût cumulatif BW réduit de 78%
- **Fig. 7** : Coût BW dominant, stockage négligeable

---

## 📝 Actions Immédiates

1. ✅ Corriger `COST_BW_INTER_PROVIDER` : `0.10 → 0.01`
2. ✅ Corriger `COST_BW_INTER_REGION` : `0.05 → 0.008`
3. ✅ Corriger `COST_BW_INTER_CLOUD` : `0.10 → 0.01`
4. ✅ Vérifier que tous les autres paramètres correspondent au Tableau 1
5. ✅ Re-exécuter les benchmarks
6. ✅ Comparer les résultats avec les Figures 2-7 de l'article
