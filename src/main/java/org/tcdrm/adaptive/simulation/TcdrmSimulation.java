package org.tcdrm.adaptive.simulation;

import org.tcdrm.adaptive.cloudsim.DataFragment;
import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;
import org.tcdrm.adaptive.cloudsim.QueryCloudlet;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulation TCDRM utilisant CloudSimPlus.
 *
 * Stratégies disponibles :
 * - NoRepLc : pas de réplication
 * - TCDRM   : réplication réactive (seuil P_SLA du papier, sans EMA)
 * - RL      : réplication adaptative via agents Python. L'éligibilité popularité du
 *             papier (Algorithm 1 : pd_i ≥ P_SLA) s'applique à TOUS les modèles, avec
 *             un seuil adaptatif pour le RL (Sujet 1) ; au-dessus du seuil, l'agent
 *             décide librement quand/combien répliquer.
 */
public class TcdrmSimulation {

    private final MultiCloudInfrastructure infrastructure;
    private final List<DataFragment> fragments;
    private final Random rnd;
    private final boolean complex;

    private String execProvider;
    private String execRegion;

    private int currentReplicaCount;
    private double currentBudget;
    private int queryCount;

    // Seuil T_SLA adaptatif — ajusté par la méta-boucle RL entre épisodes (Sujet 1).
    private double dynamicTSla;

    // Seuil de popularité adaptatif (P_SLA normalisé, Sujet 1). Contrat = 1.0
    // (accessCount ≥ P_SLA, Paper Algorithm 1) ; ajusté par le méta-contrôleur
    // Q-learning (ThresholdMetaLearner) — aucune règle d'ajustement codée en dur.
    // Toute réplication RL sous ce seuil est convertie en NOOP : les données
    // non populaires ne sont JAMAIS répliquées.
    private double dynamicMinPopularity = 1.0;

    // Fenêtre d'observation ΔT de suppression (Paper Algorithm 3), en NOMBRE DE
    // REQUÊTES — apprise par méta-contrôleur (fraction de P_SLA). Unifie durée de
    // vie minimale, fenêtre de popularité et cooldown de re-création.
    // Défaut : P_SLA entier (posture la plus conservatrice = ne supprimer qu'après
    // un cycle complet d'inactivité), assouplie uniquement par apprentissage.
    private int dynamicDeletionWindowQueries = TcdrmConstants.P_SLA;

    // Routage sensible au coût (profil d'agent, ex. Rainbow « priorité coût ») :
    // TOLÉRANCE en ms — parmi les sites dont le temps estimé est à moins de
    // `costRoutingToleranceMs` du meilleur temps, on choisit le MOINS CHER en BW.
    // Départage lexicographique (temps d'abord, coût ensuite) : la latence ne peut
    // jamais se dégrader de plus que la tolérance, mais à temps quasi égal les liens
    // inter-région (0.008$/GB) sont préférés aux liens inter-provider (0.01$/GB) —
    // « utiliser l'inter-région le plus possible quand les données sont déjà migrées ».
    // DÉFAUT 1 ms (infrastructure, tous modèles) : départage les égalités analytiques
    // EXACTES entre sites par le coût, au lieu de l'ordre d'itération arbitraire —
    // mesuré : BW −42 % en workload variable, latence moyenne inchangée, strictement
    // neutre en steady. Un profil d'agent peut l'augmenter via setCostAwareRouting.
    private double costRoutingToleranceMs = 1.0;

    // Sujet 2 : optimiseur multi-objectifs du placement de réplicas
    private final ReplicaPlacementOptimizer placementOptimizer;
    // Compteurs de réplicas par provider pour le scoring de saturation (Sujet 2)
    private final int[] replicasPerProvider = new int[MultiCloudInfrastructure.PROVIDERS.length];

    public TcdrmSimulation(long seed, boolean complex) {
        this(seed, complex, null);
    }

    /**
     * Constructeur avec optimiseur de placement partagé (persistant entre épisodes).
     * Utilisé par {@link org.tcdrm.adaptive.training.TrainingEnvironment} pour que le
     * {@link ReplicaPlacementOptimizer} accumule son apprentissage inter-épisodes.
     */
    public TcdrmSimulation(long seed, boolean complex, ReplicaPlacementOptimizer optimizer) {
        this.placementOptimizer = optimizer != null ? optimizer : new ReplicaPlacementOptimizer();
        this.infrastructure = new MultiCloudInfrastructure();
        this.rnd = new Random(seed);
        this.complex = complex;
        this.execProvider = "Google";
        this.execRegion = org.tcdrm.adaptive.core.RuntimeConfig.getExecRegion();
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        this.dynamicTSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;

        this.fragments = createDistributedFragments();
        this.workloadSets = buildLegacyWorkloadSets(seed);
    }

    private List<DataFragment> createDistributedFragments() {
        int perQuery = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        // Modes dynamiques (variable/burst) : pool plus grand que ce qu'une requête
        // touche — la popularité devient une propriété émergente PAR fragment.
        // Taille calibrée pour ÉGALISER LA DIFFICULTÉ entre régimes (probabilité
        // qu'une requête soit couverte par la capacité max de réplication ≈ 10 %
        // mesurée par simulation) : simple 3→9 (×3), complex 6→9 (×1.5). Un pool
        // complex de 18 rendait la tâche d'entraînement quasi insoluble (couverture
        // 1 %) : même 12 réplicas laissaient ~900 violations, enseignant à l'agent
        // que « répliquer ne sert à rien » — politique NOOP catastrophique en éval.
        boolean steady = "steady".equals(org.tcdrm.adaptive.core.RuntimeConfig.getWorkloadMode());
        int nRelations = steady ? perQuery
            : (complex ? (perQuery * 3) / 2 : perQuery * TcdrmConstants.WORKLOAD_POOL_FACTOR);
        List<DataFragment> frags = new ArrayList<>();

        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions = MultiCloudInfrastructure.REGIONS;

        for (int i = 0; i < nRelations; i++) {
            String provider = providers[i % providers.length];
            int regionOffset = complex ? i / providers.length : 0;
            String region = regions[(i + regionOffset) % regions.length];
            frags.add(new DataFragment(i, "R" + i, provider, region));
        }
        return frags;
    }

    private List<int[]> workloadSets;

    /** Nombre effectif de requêtes du run : override runtime sinon MAX_QUERIES (papier: 1000). */
    private static int effectiveMaxQueries() {
        Integer override = org.tcdrm.adaptive.core.RuntimeConfig.getMaxQueries();
        return override != null ? override : TcdrmConstants.MAX_QUERIES;
    }

    private List<int[]> buildLegacyWorkloadSets(long seed) {
        int n = effectiveMaxQueries();
        int perQuery = complex ? TcdrmConstants.RELATIONS_COMPLEX : TcdrmConstants.RELATIONS_SIMPLE;
        String mode = org.tcdrm.adaptive.core.RuntimeConfig.getWorkloadMode();
        switch (mode) {
            case "variable":
                return org.tcdrm.adaptive.data.PopularityWorkloads.generateVariable(
                    fragments.size(), perQuery, n, seed);
            case "burst":
                return org.tcdrm.adaptive.data.PopularityWorkloads.generateBurst(
                    fragments.size(), perQuery, n, seed);
            default: // steady — requête répétée, fidèle au protocole du papier
                if (complex) {
                    return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.generateComplex(fragments, n, seed);
                }
                return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.generateSimple(fragments, n, seed);
        }
    }

    private List<DataFragment> selectFragmentsForQuery(int qIdx) {
        if (workloadSets != null && !workloadSets.isEmpty()) {
            // Boucle sur le pattern généré si le run dépasse sa longueur (warmup,
            // maxEpisodeLength > MAX_QUERIES) — jamais de fallback « tous les
            // fragments », qui fausserait silencieusement la popularité.
            int[] idx = workloadSets.get(qIdx % workloadSets.size());
            return org.tcdrm.adaptive.data.LegacyWorkloadTemplates.select(fragments, idx);
        }
        return fragments;
    }

    /**
     * Enregistre les accès de la requête ET met à jour la popularité (EMA du taux
     * d'accès) de TOUS les fragments : ceux de la requête montent, les autres décroissent.
     * La popularité est ainsi un taux récent (littérature), pas un cumul.
     */
    private void recordAccessAndPopularity(List<DataFragment> active, int qIdx) {
        double lambda = TcdrmConstants.POPULARITY_EMA_LAMBDA;
        for (DataFragment f : fragments) {
            boolean accessed = active.contains(f);
            if (accessed) f.recordAccess(qIdx);
            f.updatePopularity(accessed, lambda);
        }
    }

    /** Résultat d'une requête. */
    public record QueryResult(
        int queryNumber,
        double queryTimeMs,
        double bwCost,
        double cpuCost,
        double ioCost,
        double totalCost,
        double bwInterProviderGb,
        double bwInterRegionGb,
        int replicaCount
    ) {}

    /** Exécute une requête NoRepLc (pas de réplication). */
    public QueryResult executeNoRepQuery() {
        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        final int qIdx = queryCount;
        recordAccessAndPopularity(active, qIdx);

        // Choisir le meilleur site d'exécution compte tenu des localisations primaires
        String[] optSite = findOptimalExecSite(active);
        double[] sync = syncReplicasOnWrite(active); // toujours [0,0,0] : pas de réplicas
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, optSite[0], optSite[1], rnd);

        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost() + sync[0],
            query.getCpuCost(),
            query.getIoCost(),
            query.getTotalCost() + sync[0],
            query.getBwInterProviderGb() + sync[1],
            query.getBwInterRegionGb() + sync[2],
            0
        );
        queryCount++;
        return result;
    }

    /**
     * Exécute une requête TCDRM v1 — stratégie réactive fidèle au papier.
     *
     * Réplique si : SLA violé ET queryCount >= P_SLA (200 requêtes).
     * Aucun EMA ni TinyLFU — cohérent avec la suppression de ces mécanismes.
     */
    public QueryResult executeTcdrmQuery() {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);

        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);

        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        final int qIdxTcdrm = queryCount;
        recordAccessAndPopularity(active, qIdxTcdrm);

        // Choisir le meilleur site d'exécution (primaires + réplicas déjà créés)
        String[] optSite = findOptimalExecSite(active);
        this.execProvider = optSite[0];
        this.execRegion = optSite[1];
        placementOptimizer.recordExecution(optSite[0], optSite[1]);
        double[] sync = syncReplicasOnWrite(active);
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, optSite[0], optSite[1], rnd);

        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
        double tSla = complex ? TcdrmConstants.TSLA_COMPLEX_MS : TcdrmConstants.TSLA_SIMPLE_MS;
        double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        boolean slaViolated = query.getTotalTimeMs() > tSla
                || (query.getTotalCost() + maintenanceCost + sync[0]) > cSla;
        // Répliquer uniquement quand le workload est stabilisé (popularité réelle détectée)
        boolean popularEnough = isWorkloadStabilized();

        double creationCost = 0.0;
        if (slaViolated && currentReplicaCount < maxReplicas && popularEnough) {
            creationCost = createReplica();
            currentBudget -= creationCost;
        }

        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost() + creationCost + sync[0],
            query.getCpuCost(),
            query.getIoCost() + maintenanceCost,
            query.getTotalCost() + creationCost + maintenanceCost + sync[0],
            query.getBwInterProviderGb() + sync[1],
            query.getBwInterRegionGb() + sync[2],
            currentReplicaCount
        );
        queryCount++;
        currentBudget -= query.getTotalCost() + maintenanceCost + sync[0];
        return result;
    }

    /**
     * Exécute une requête avec une action RL.
     *
     * Éligibilité popularité (Paper Algorithm 1) : REPLICATE n'est exécutée que si les
     * données sont populaires (popularityScore ≥ seuil P_SLA adaptatif) — sinon NOOP.
     * Au-dessus du seuil, l'agent décide librement : la reward et l'état [7]
     * (popularityScore) lui enseignent le bon moment et le bon nombre de réplicas.
     *
     * @param action 0=NOOP, 1=REPLICATE, 2=DELETE
     */
    public QueryResult executeRLQuery(int action) {
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);
        double creationCost = 0.0;

        // Garde-fou contractuel (Algorithm 1) : jamais de réplica sur données non populaires.
        // Appliqué ici, dans l'environnement, pour qu'aucun chemin (éval, entraînement,
        // warm-up aléatoire) ne puisse répliquer à popularité insuffisante.
        if (action == 1 && !isReplicationAllowed()) action = 0;

        if (action == 1 && currentReplicaCount < maxReplicas && currentBudget > 0) {
            creationCost = createReplica();
            currentBudget -= creationCost;
        } else if (action == 2 && currentReplicaCount > 0) {
            int idx = findDeletableReplicaIndex();
            if (idx >= 0) deleteReplicaAtIndex(idx);
        }

        fragments.stream().filter(DataFragment::hasReplica).forEach(DataFragment::incrementQueryCount);

        List<DataFragment> active = selectFragmentsForQuery(queryCount);
        final int qIdxRL = queryCount;
        recordAccessAndPopularity(active, qIdxRL);

        // Évaluer tous les sites d'exécution possibles (provider × région) et choisir
        // celui qui minimise le temps de transfert analytique pour CES fragments.
        String[] optSite = findOptimalExecSite(active);
        this.execProvider = optSite[0];
        this.execRegion = optSite[1];
        placementOptimizer.recordExecution(optSite[0], optSite[1]);
        double[] sync = syncReplicasOnWrite(active);
        QueryCloudlet query = new QueryCloudlet(queryCount, complex, active);
        query.execute(infrastructure, optSite[0], optSite[1], rnd);

        double maintenanceCost = currentReplicaCount * TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;

        QueryResult result = new QueryResult(
            queryCount,
            query.getTotalTimeMs(),
            query.getBwCost() + creationCost + sync[0],
            query.getCpuCost(),
            query.getIoCost() + maintenanceCost,
            query.getTotalCost() + creationCost + maintenanceCost + sync[0],
            query.getBwInterProviderGb() + sync[1],
            query.getBwInterRegionGb() + sync[2],
            currentReplicaCount
        );
        queryCount++;
        currentBudget -= query.getTotalCost() + maintenanceCost + sync[0];
        return result;
    }

    /**
     * Trouve le site d'exécution (provider, région) qui minimise le temps de transfert
     * analytique moyen pour les fragments actifs, en tenant compte des réplicas existants.
     *
     * Évalue tous les (PROVIDERS × REGIONS) sans appel CloudSimPlus (pas de jitter) pour
     * garantir un choix déterministe et rapide — le vrai jitter s'applique ensuite lors
     * de l'appel {@link QueryCloudlet#execute}.
     */
    private String[] findOptimalExecSite(List<DataFragment> active) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;
        int nSites = providers.length * regions.length;
        double[] times = new double[nSites];
        double[] costs = new double[nSites];
        double minTime = Double.MAX_VALUE;

        int idx = 0;
        for (String p : providers) {
            for (String r : regions) {
                double siteTime = 0;
                double siteCost = 0;
                for (DataFragment frag : active) {
                    DataFragment.LocationChoice loc = frag.bestSourceLocation(p, r);
                    double dataGb = frag.getSizeGb() * TcdrmConstants.QUERY_SELECTIVITY;
                    double t = estimateMeanTransferMs(dataGb, loc.provider(), loc.region(), p, r);
                    if (loc.usingReplica()) {
                        // Même gain progressif que QueryCloudlet.execute (cohérence du choix de site)
                        double gain = TcdrmConstants.REPLICA_TRANSFER_GAIN_FIRST
                            + TcdrmConstants.REPLICA_TRANSFER_GAIN_EXTRA
                                * Math.max(0, frag.getReplicaCount() - 1);
                        t *= (1.0 - gain * loc.warmupEff());
                    }
                    siteTime = TcdrmConstants.PARALLEL_FETCH
                        ? Math.max(siteTime, t)
                        : siteTime + t;
                    // Coût BW facturé sur la taille PLEINE de la relation (même règle
                    // que QueryCloudlet.execute) depuis la source retenue vers ce site.
                    siteCost += frag.getSizeGb()
                        * infrastructure.getBandwidthCostPerGb(loc.provider(), loc.region(), p, r);
                }
                times[idx] = siteTime;
                costs[idx] = siteCost;
                minTime = Math.min(minTime, siteTime);
                idx++;
            }
        }

        int best = -1;
        if (costRoutingToleranceMs <= 0.0) {
            // Comportement historique : premier site au temps minimal (ordre d'itération).
            for (int i = 0; i < nSites; i++) {
                if (best < 0 || times[i] < times[best]) best = i;
            }
        } else {
            // Départage lexicographique : parmi les sites à moins de
            // `costRoutingToleranceMs` du meilleur temps, choisir le moins cher en BW
            // (à coût égal, le plus rapide).
            for (int i = 0; i < nSites; i++) {
                if (times[i] <= minTime + costRoutingToleranceMs) {
                    if (best < 0 || costs[i] < costs[best]
                            || (costs[i] == costs[best] && times[i] < times[best])) {
                        best = i;
                    }
                }
            }
        }
        return new String[]{providers[best / regions.length], regions[best % regions.length]};
    }

    /**
     * Estime le temps de transfert analytique sans jitter (valeur moyenne).
     * Utilisé uniquement pour la sélection du site optimal — pas pour les métriques.
     */
    private double estimateMeanTransferMs(double dataGb, String srcP, String srcR,
                                           String dstP, String dstR) {
        double latMs  = infrastructure.getLatencyMs(srcP, srcR, dstP, dstR);
        double bwGbps = infrastructure.getBandwidthGbps(srcP, srcR, dstP, dstR);
        return latMs + (dataGb * 8000.0 / bwGbps);
    }

    /**
     * Estime le temps de transfert si on ignorait tous les réplicas (baseline NoRepLc)
     * pour le site donné. Utilisé pour quantifier le gain apporté par la réplication.
     */
    public double estimateNoRepTransferMs(List<DataFragment> active, String execP, String execR) {
        double siteTime = 0;
        for (DataFragment frag : active) {
            double dataGb = frag.getSizeGb() * TcdrmConstants.QUERY_SELECTIVITY;
            double t = estimateMeanTransferMs(dataGb, frag.getPrimaryProvider(), frag.getPrimaryRegion(), execP, execR);
            siteTime = TcdrmConstants.PARALLEL_FETCH
                ? Math.max(siteTime, t)
                : siteTime + t;
        }
        return siteTime;
    }

    /**
     * Coût de MAINTENANCE DE COHÉRENCE (modèle économique standard de la réplication
     * cloud : création + stockage + propagation des mises à jour — la littérature
     * data grid/cloud facture chaque écriture × taille × nombre de réplicas).
     *
     * Avec READ_WRITE_RATIO = 0.9 (Paper Table 2, jusqu'ici inutilisé), 10 % des
     * requêtes sont des écritures : chaque réplica des fragments touchés doit être
     * resynchronisé depuis son primaire (transfert asynchrone — coût BW réel facturé
     * sur la taille pleine de la relation, comme la création, sans impact latence).
     *
     * C'est le coût RÉCURRENT réel de détention d'un réplica : il donne des dents
     * économiques mesurables (dans les figures) au contrat de popularité — détenir
     * beaucoup de réplicas de données activement écrites coûte, exactement comme
     * dans un vrai cloud pay-as-you-go. Appliqué identiquement aux trois stratégies
     * (NoRepLc n'a pas de réplicas → coût nul par construction).
     *
     * @return [coût $, GB inter-provider, GB inter-région]
     */
    private double[] syncReplicasOnWrite(List<DataFragment> active) {
        // Tirage unique par requête, AVANT l'exécution du cloudlet, au même point dans
        // les trois stratégies : les séquences aléatoires restent alignées entre modèles.
        boolean isWrite = rnd.nextDouble() >= TcdrmConstants.READ_WRITE_RATIO;
        if (!isWrite) return new double[]{0.0, 0.0, 0.0};
        double cost = 0.0, ipGb = 0.0, irGb = 0.0;
        for (DataFragment f : active) {
            for (int i = 0; i < f.getReplicaCount(); i++) {
                String rp = f.getReplicaProviderAt(i);
                String rr = f.getReplicaRegionAt(i);
                cost += f.getSizeGb() * infrastructure.getBandwidthCostPerGb(
                    f.getPrimaryProvider(), f.getPrimaryRegion(), rp, rr);
                if (f.getPrimaryProvider().equals(rp)) irGb += f.getSizeGb();
                else                                    ipGb += f.getSizeGb();
            }
        }
        return new double[]{cost, ipGb, irGb};
    }

    /**
     * Crée un réplica via l'optimiseur multi-objectifs (Sujet 2).
     *
     * Éligibilité PAR FRAGMENT (Paper Algorithm 1 : pd_i ≥ P_SLA, condition par
     * donnée) : seuls les fragments dont la popularité PROPRE atteint le seuil
     * adaptatif peuvent recevoir un réplica — on ne réplique jamais une donnée
     * froide au motif qu'une AUTRE donnée est populaire. Le placement parmi les
     * fragments éligibles reste guidé par latence, coût, popularité, saturation.
     */
    private double createReplica() {
        ReplicaPlacementOptimizer.Candidate c = placementOptimizer.selectBest(
            fragments, execProvider, execRegion, infrastructure, replicasPerProvider,
            dynamicMinPopularity);
        if (c == null) return 0.0;
        double cost = c.fragment().createReplica(c.provider(), c.region());
        if (cost > 0.0) {
            currentReplicaCount++;
            replicasPerProvider[providerIndex(c.provider())]++;
        }
        return cost;
    }

    /**
     * Trouve le fragment le MOINS populaire éligible à la suppression de réplica.
     *
     * Règles d'éligibilité (ordre de priorité) :
     * 1. Le fragment a un réplica actif.
     * 2. La durée de vie minimale est dépassée (anti-oscillation).
     * 3. Les données ne sont PAS encore activement utilisées (fenêtre glissante).
     *
     * Parmi les fragments éligibles, on choisit celui dont le dernier accès est le
     * plus ancien (le moins récemment utilisé = le moins populaire).
     * Si AUCUN fragment éligible n'est trouvé (toutes les données sont encore utilisées),
     * retourne -1 : la suppression est bloquée.
     */
    private int findDeletableReplicaIndex() {
        int bestIdx       = -1;
        int oldestAccess  = Integer.MAX_VALUE;

        for (int i = 0; i < fragments.size(); i++) {
            DataFragment f = fragments.get(i);
            if (!f.hasReplica()) continue;
            // ΔT appris (Algorithm 3) : durée de vie minimale ET fenêtre de popularité
            if (f.getQueriesSinceReplication() < dynamicDeletionWindowQueries) continue;
            if (f.isStillPopular(queryCount, dynamicDeletionWindowQueries)) continue;

            // Score deletion candidates: prefer falling trend + oldest access
            // trend: -1=falling (prefer delete), 0=stable, +1=rising (avoid delete)
            int trend = f.getPopularityTrend();
            if (trend > 0) continue;  // never delete rising-popularity replicas

            int lastAccess = f.getLastAccessedQuery();
            // Encode: falling trend = very old access score, stable = normal
            int effectiveAge = trend < 0 ? (lastAccess - 10000) : lastAccess;
            if (effectiveAge < oldestAccess) {
                oldestAccess = effectiveAge;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private void deleteReplicaAtIndex(int idx) {
        DataFragment f = fragments.get(idx);
        String replicaProvider = f.getReplicaProvider();
        f.deleteReplica();
        f.startRecreateCooldown(dynamicDeletionWindowQueries);
        currentReplicaCount--;
        if (replicaProvider != null) {
            int pi = providerIndex(replicaProvider);
            replicasPerProvider[pi] = Math.max(0, replicasPerProvider[pi] - 1);
        }
    }

    /**
     * Vérifie la condition de popularité du papier (Algorithm 1, Table 1) :
     *   pd_i = #Requests ≥ P_SLA (200)
     *
     * Avec une requête répétée 1000 fois accédant tous les fragments à chaque exécution,
     * la condition est atteinte au query 200, ce qui correspond au comportement de la Fig. 2.
     * Cette implémentation est fidèle au seuil P_SLA explicitement défini dans le papier.
     */
    public boolean isWorkloadStabilized() {
        for (DataFragment f : fragments) {
            if (f.getAccessCount() >= TcdrmConstants.P_SLA) {
                return true;
            }
        }
        return false;
    }

    /**
     * Popularité [0,1] = taux d'accès RÉCENT (EMA) du fragment le plus populaire
     * (définition littérature : fréquence d'accès à décroissance exponentielle, PAS un
     * cumul). Basse au début (peu d'accès soutenu observé → confiance faible), monte
     * avec un accès soutenu (~q=200 pour EMA≈0.9), décroît si les données refroidissent.
     * Utilisé comme state[7] et pour l'éligibilité : l'agent apprend à ne pas répliquer
     * de données pas encore établies comme populaires — sans aucun seuil codé en dur.
     */
    public double getPopularityScore() {
        double maxEma = 0.0;
        for (DataFragment f : fragments) {
            if (f.getPopularityEma() > maxEma) maxEma = f.getPopularityEma();
        }
        return TcdrmConstants.normalizedPopularity(maxEma);
    }

    /**
     * Charge de détention des réplicas sur données froides : Σ (1 − pop_f)^k × réplicas_f,
     * fonction LISSE et convexe de la popularité — AUCUN seuil statique (Sujet 1 :
     * pas de valeur codée en dur).
     *
     * L'exposant k > 1 (REPLICA_HOLDING_CONVEXITY) est un réglage de FORME de la
     * récompense (même famille que les poids r1..r9), pas un seuil de comportement :
     * <ul>
     *   <li>pop → 0 (donnée froide, ex. au tout départ) : coldness → 1 → détention
     *       fortement pénalisée → l'agent apprend de lui-même à NE PAS répliquer avant
     *       que les données chauffent (répond à « pas de réplication prématurée »),
     *       sans aucun plancher codé.</li>
     *   <li>pop moyenne (données qui chauffent) : la convexité écrase la pénalité
     *       (0.5^k ≪ 0.5) → la réplication précoce gagnante n'est plus dissuadée
     *       (répond à « ne pas répliquer trop tard »).</li>
     *   <li>pop → 1 (chaud) : coldness → 0 → aucune pénalité.</li>
     * </ul>
     * En variable/burst, une donnée qui refroidit voit sa coldness remonter → l'agent
     * apprend à supprimer ses réplicas (Algorithm 3).
     */
    public double getUnpopularReplicaLoad() {
        double k = TcdrmConstants.REPLICA_HOLDING_CONVEXITY;
        double load = 0.0;
        for (DataFragment f : fragments) {
            if (f.getReplicaCount() > 0) {
                double pop = TcdrmConstants.normalizedPopularity(f.getPopularityEma());
                double coldness = Math.pow(1.0 - pop, k);
                load += coldness * f.getReplicaCount();
            }
        }
        return load;
    }

    /** Index du provider dans {@link MultiCloudInfrastructure#PROVIDERS}. */
    private int providerIndex(String provider) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        for (int i = 0; i < providers.length; i++) {
            if (providers[i].equals(provider)) return i;
        }
        return 0;
    }

    /**
     * Construit l'état RL (9 dimensions).
     *
     * <pre>
     * [0] latency / dynamicTSla             — latence normalisée
     * [1] budget / INITIAL_BUDGET           — budget restant normalisé
     * [2] replicas / maxReplicas            — taux de remplissage des réplicas
     * [3] cost / cSlaEffective              — coût normalisé
     * [4] latency > tSla ? 1 : 0           — violation T_SLA
     * [5] cost > cSla ? 1 : 0             — violation C_SLA
     * [6] queryCount / MAX_QUERIES          — progression globale [0,1]
     * [7] popularityScore (clamp [0,1])     — 0 = données inconnues (query 0),
     *                                         1 = P_SLA atteint (query 200+)
     * [8] complex ? 1 : 0                   — type de requête : permet à un modèle RL
     *                                         unique de conditionner sa politique sur
     *                                         le régime simple/complex (Axe 5)
     * </pre>
     */
    public double[] buildRLState(double lastLatency, double lastCost) {
        double cSla = complex ? TcdrmConstants.CSLA_COMPLEX : TcdrmConstants.CSLA_SIMPLE;
        return buildRLState(lastLatency, lastCost, cSla);
    }

    public double[] buildRLState(double lastLatency, double lastCost, double cSlaEffective) {
        double tSla = dynamicTSla;
        double cSla = cSlaEffective;
        int maxReplicas = TcdrmConstants.maxReplicasForQueryType(complex);

        // Structure de popularité du pool (Sujet 1) : la popularité MAX seule ([7]) ne
        // dit pas QUELS ni COMBIEN d'éléments sont populaires — l'agent ne peut pas
        // distinguer « 1 fragment chaud → 2 réplicas utiles » de « tous chauds →
        // couverture complète urgente ». Ces deux dimensions donnent la vision
        // par élément : fraction éligible au seuil courant, et popularité moyenne.
        int eligible = 0;
        double popSum = 0.0;
        for (DataFragment f : fragments) {
            double pop = TcdrmConstants.normalizedPopularity(f.getPopularityEma());
            popSum += pop;
            if (pop >= dynamicMinPopularity) eligible++;
        }
        double eligibleFraction = (double) eligible / Math.max(1, fragments.size());
        double meanPopularity   = popSum / Math.max(1, fragments.size());

        return new double[] {
            lastLatency / tSla,                                    // 0: latence normalisée
            currentBudget / TcdrmConstants.INITIAL_BUDGET,         // 1: budget
            (double) currentReplicaCount / maxReplicas,            // 2: réplicas
            lastCost / cSla,                                       // 3: coût normalisé
            lastLatency > tSla ? 1.0 : 0.0,                       // 4: violation T_SLA
            lastCost > cSla ? 1.0 : 0.0,                          // 5: violation C_SLA
            (double) queryCount / effectiveMaxQueries(),           // 6: progression [0,1]
            getPopularityScore(),                                  // 7: popularité normalisée [0,1]
                                                                   //    0 = données inconnues (query 0)
                                                                   //    1 = P_SLA atteint (query 200+)
            complex ? 1.0 : 0.0,                                   // 8: type de requête (0=simple, 1=complex)
            eligibleFraction,                                      // 9: fraction de fragments ÉLIGIBLES
                                                                   //    (pop_f ≥ seuil adaptatif courant)
            meanPopularity                                         // 10: popularité MOYENNE du pool
        };
    }

    // === Getters ===

    public MultiCloudInfrastructure getInfrastructure() { return infrastructure; }
    public List<DataFragment> getFragments() { return fragments; }
    public int getCurrentReplicaCount() { return currentReplicaCount; }
    public double getCurrentBudget() { return currentBudget; }
    public int getQueryCount() { return queryCount; }
    public boolean isComplex() { return complex; }

    public void reset() {
        this.currentReplicaCount = 0;
        this.currentBudget = TcdrmConstants.INITIAL_BUDGET;
        this.queryCount = 0;
        java.util.Arrays.fill(replicasPerProvider, 0);
        fragments.forEach(f -> {
            while (f.hasReplica()) f.deleteReplica();
            f.resetAccessStats();
        });
    }

    // === Seuils SLA dynamiques (Sujet 1) ===

    /** Injecte le seuil T_SLA adaptatif pour cet épisode. */
    public void setDynamicThresholds(double tSla) {
        this.dynamicTSla = Math.max(50.0, tSla);
    }

    /**
     * Injecte les seuils adaptatifs T_SLA et popularité (P_SLA normalisé).
     * Le seuil popularité est borné à [0,1] — bornes de définition de la popularité
     * normalisée (fraction du P_SLA contractuel), pas un réglage comportemental.
     */
    public void setDynamicThresholds(double tSla, double minPopularity) {
        setDynamicThresholds(tSla);
        this.dynamicMinPopularity = Math.max(0.0, Math.min(1.0, minPopularity));
    }

    /**
     * Éligibilité popularité (Paper Algorithm 1) : vrai si les données observées sont
     * suffisamment populaires pour autoriser une réplication.
     */
    public boolean isReplicationAllowed() {
        return getPopularityScore() >= dynamicMinPopularity;
    }

    /**
     * Injecte la fenêtre ΔT de suppression apprise (Paper Algorithm 3), exprimée en
     * fraction du P_SLA contractuel. Bornée aux bornes de définition [MIN, 1.0].
     */
    public void setDynamicDeletionWindow(double fractionOfPsla) {
        double f = Math.max(TcdrmConstants.META_DELETION_WINDOW_MIN, Math.min(1.0, fractionOfPsla));
        this.dynamicDeletionWindowQueries = (int) Math.round(f * TcdrmConstants.P_SLA);
    }

    /** Fenêtre ΔT de suppression courante (requêtes). */
    public int getDynamicDeletionWindowQueries() { return dynamicDeletionWindowQueries; }

    public double getDynamicTSla() { return dynamicTSla; }

    /** Seuil de popularité adaptatif courant (P_SLA normalisé, Sujet 1). */
    public double getDynamicMinPopularity() { return dynamicMinPopularity; }

    /** Poids courants de l'optimiseur de placement (Sujet 2) pour monitoring. */
    public double[] getPlacementWeights() {
        return new double[]{ placementOptimizer.getWLat(), placementOptimizer.getWCost(),
                             placementOptimizer.getWSat() };
    }

    /** Notifie l'optimiseur de placement des métriques de l'épisode (Sujet 2). */
    public void adaptPlacementWeights(double violationRate, double avgCostRatio) {
        placementOptimizer.adaptWeights(violationRate, avgCostRatio);
    }

    public ReplicaPlacementOptimizer getPlacementOptimizer() { return placementOptimizer; }

    /**
     * Tolérance (ms) du routage sensible au coût — profil d'agent, persisté avec sa
     * configuration d'entraînement. 0 = choix de site au temps seul.
     */
    public void setCostAwareRouting(double toleranceMs) {
        this.costRoutingToleranceMs = Math.max(0.0, toleranceMs);
    }

    public double getCostRoutingToleranceMs() { return costRoutingToleranceMs; }
}
