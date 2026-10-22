package org.tcdrm.adaptive.simulation;

import org.tcdrm.adaptive.cloudsim.DataFragment;
import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * TCDRM v2 — Sujet 2 (PREDICTIVE): Optimisation multi-objectifs du placement de réplicas.
 *
 * <p>Score pondéré sur 4 objectifs (inspiré SPEA2 + NSGA-II, Yousra-ben 2024) :</p>
 *
 * <pre>
 *   score = w_lat  × latSaving          — réduction de latence vs primaire
 *         + w_cost × costSaving         — économie bande passante
 *         + w_pop  × popularityFactor   — priorité fragments chauds (SPEA2)
 *         − w_sat  × loadPenalty        — équilibrage de charge (NSGA-II)
 * </pre>
 *
 * <ul>
 *   <li><b>w_pop / popularityFactor</b> : fragment le plus accédé en priorité.
 *       Inspiré de la logique SPEA2 du repo Yousra-ben : les 33% fragments les plus
 *       populaires reçoivent un réplica en priorité.</li>
 *   <li><b>loadPenalty</b> : écart de charge par rapport à la moyenne inter-provider.
 *       Inspiré de la logique NSGA-II : évite la sur-représentation d'un provider.</li>
 * </ul>
 *
 * <p>Les poids sont adaptés automatiquement entre épisodes selon les métriques observées.</p>
 */
public class ReplicaPlacementOptimizer {

    /** Résultat du meilleur placement sélectionné. */
    public record Candidate(DataFragment fragment, String provider, String region, double score) {}

    // Poids multi-objectifs — adaptés dynamiquement entre épisodes
    private double wLat  = 0.30;   // réduction de latence
    private double wCost = 0.30;   // économie de coût BW
    private double wPop  = 0.15;   // popularité (SPEA2)
    private double wSat  = 0.05;   // équilibrage de charge (NSGA-II)
    // COÛT DE SYNCHRONISATION (possession) : pénalise chaque candidat par le prix RÉEL
    // du chemin primaire→réplica pondéré par le taux d'écriture — l'économie exacte,
    // remplaçant l'ancien bonus d'affinité même-provider (proxy). Les placements sans
    // économie de service ne sont plus jamais préférés aux placements rentables.
    private double wSync = 0.20;

    // Fréquences d'exécution par (provider, région) — pour scoring pondéré fréquence
    private final int[][] regionFreq;
    private int totalExecCount = 0;
    // Fréquence RÉCENTE des sites d'exécution (EMA, même constante λ que la popularité) :
    // estime où s'exécuteront les prochaines requêtes, pour le filtre d'utilité attendue.
    // La fréquence cumulée ci-dessus garde des sites abandonnés depuis longtemps.
    private final double[][] recentExecShare;

    // Requêtes récentes (identifiants des fragments lus), mémoire 1/λ : même constante
    // de temps que la popularité. Sert au placement CONJOINT (selectBestJoint).
    private static final int RECENT_QUERY_MEMORY =
        (int) Math.round(1.0 / org.tcdrm.adaptive.core.TcdrmConstants.POPULARITY_EMA_LAMBDA);
    private final java.util.ArrayDeque<int[]> recentQueries = new java.util.ArrayDeque<>();
    private int currentReplicas;   // réplicas existants pendant une évaluation conjointe

    public ReplicaPlacementOptimizer() {
        this.regionFreq = new int[MultiCloudInfrastructure.PROVIDERS.length][MultiCloudInfrastructure.REGIONS.length];
        this.recentExecShare = new double[MultiCloudInfrastructure.PROVIDERS.length][MultiCloudInfrastructure.REGIONS.length];
    }

    /** Enregistre un site d'exécution observé pour pondérer le scoring futur. */
    public void recordExecution(String provider, String region) {
        int pi = indexOf(MultiCloudInfrastructure.PROVIDERS, provider);
        int ri = indexOf(MultiCloudInfrastructure.REGIONS, region);
        if (pi >= 0 && ri >= 0) {
            regionFreq[pi][ri]++;
            totalExecCount++;
            double lambda = org.tcdrm.adaptive.core.TcdrmConstants.POPULARITY_EMA_LAMBDA;
            for (double[] row : recentExecShare) {
                for (int i = 0; i < row.length; i++) row[i] *= 1.0 - lambda;
            }
            recentExecShare[pi][ri] += lambda;
        }
    }

    /** Efface l'historique des sites d'exécution (début d'épisode). */
    public void resetExecutionHistory() {
        for (int[] row : regionFreq) java.util.Arrays.fill(row, 0);
        for (double[] row : recentExecShare) java.util.Arrays.fill(row, 0.0);
        totalExecCount = 0;
        recentQueries.clear();
    }

    /** Enregistre les fragments lus par une requête (mémoire glissante de 1/λ requêtes). */
    public void recordQuery(List<DataFragment> active) {
        int[] ids = new int[active.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = active.get(i).getId();
        recentQueries.addLast(ids);
        while (recentQueries.size() > RECENT_QUERY_MEMORY) recentQueries.removeFirst();
    }

    private static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return -1;
    }

    /**
     * Sélectionne le meilleur (fragment, provider, région) par score multi-objectifs.
     *
     * @param fragments           tous les fragments de la simulation
     * @param execProvider        provider d'exécution des requêtes
     * @param execRegion          région d'exécution des requêtes
     * @param infra               infrastructure pour calculs latence/coût
     * @param replicasPerProvider nombre de réplicas existants par provider
     * @param minPopularityNorm   éligibilité PAR FRAGMENT (Paper Algorithm 1, seuil
     *                            adaptatif Sujet 1) : un fragment n'est candidat que si
     *                            SA popularité normalisée (accessCount/P_SLA) atteint ce
     *                            seuil — on ne réplique pas une donnée qui n'est pas
     *                            utilisée.
     * @param useHorizon          fenêtre ΔT (requêtes) que le réplica recevra : un
     *                            emplacement dont le nombre de lectures attendu sur
     *                            cette fenêtre est inférieur à 1 n'est pas créé
     *                            (0 = pas de filtre)
     * @return meilleur candidat, ou {@code null} si aucun fragment éligible
     */
    public Candidate selectBest(
        List<DataFragment> fragments,
        String execProvider, String execRegion,
        MultiCloudInfrastructure infra,
        int[] replicasPerProvider,
        double minPopularityNorm,
        int useHorizon
    ) {
        return selectBest(fragments, execProvider, execRegion, infra, replicasPerProvider,
            f -> org.tcdrm.adaptive.core.TcdrmConstants.normalizedPopularity(f.getPopularityEma())
                    >= minPopularityNorm,
            DataFragment::getPopularityEma, useHorizon);
    }

    /**
     * Variante à métrique de popularité explicite : {@code eligible} décide si un fragment
     * peut recevoir un réplica, {@code popularity} donne le poids relatif (SPEA2) des
     * fragments éligibles. Permet à la référence TCDRM d'utiliser le compteur d'accès
     * du papier au lieu de l'EMA des agents RL.
     */
    public Candidate selectBest(
        List<DataFragment> fragments,
        String execProvider, String execRegion,
        MultiCloudInfrastructure infra,
        int[] replicasPerProvider,
        Predicate<DataFragment> eligible,
        ToDoubleFunction<DataFragment> popularity
    ) {
        return selectBest(fragments, execProvider, execRegion, infra, replicasPerProvider,
            eligible, popularity, 0);
    }

    /**
     * Variante complète. Avec {@code useHorizon} &gt; 0, filtre d'UTILITÉ ATTENDUE (agents
     * RL) : un réplica est supprimé s'il ne sert aucune lecture pendant sa fenêtre ΔT
     * (Algorithm 3) ; symétriquement, on ne le crée pas si le nombre de lectures qu'il
     * servirait pendant cette fenêtre est attendu inférieur à 1 :
     * ΔT × taux d'accès de la donnée (EMA) × part récente des sites d'exécution pour
     * lesquels il deviendrait la copie la plus proche. Sans ce filtre, des seconds
     * réplicas étaient placés pour des sites rarement utilisés, puis supprimés sans
     * avoir servi (créations et coûts inutiles en charge variable).
     */
    public Candidate selectBest(
        List<DataFragment> fragments,
        String execProvider, String execRegion,
        MultiCloudInfrastructure infra,
        int[] replicasPerProvider,
        Predicate<DataFragment> eligible,
        ToDoubleFunction<DataFragment> popularity,
        int useHorizon
    ) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;

        // Popularité max observée — pour normalisation SPEA2
        double maxPop = 1e-9;
        for (DataFragment f : fragments) {
            maxPop = Math.max(maxPop, popularity.applyAsDouble(f));
        }

        // Charge moyenne par provider — pour équilibrage NSGA-II
        int totalReplicas = 0;
        for (int r : replicasPerProvider) totalReplicas += r;
        double avgLoad = (double) totalReplicas / Math.max(1, providers.length);

        Candidate best      = null;
        double    bestScore = Double.NEGATIVE_INFINITY;
        // COUVRIR AVANT DE DOUBLER : un premier réplica d'un fragment non couvert passe
        // devant tout second réplica (une requête lit ses relations en parallèle — la
        // relation NON répliquée fixe la latence, une seconde copie ailleurs n'y change rien).
        boolean bestCovers = false;

        for (DataFragment f : fragments) {
            if (!f.canAddReplica() || f.hasRecreateCooldown()) continue;
            // Éligibilité par donnée (Algorithm 1) : la popularité de CE fragment.
            if (!eligible.test(f)) continue;

            double popularityFactor = popularity.applyAsDouble(f) / maxPop;  // relatif (SPEA2), pas de normalisation absolue nécessaire

            for (int pi = 0; pi < providers.length; pi++) {
                String tp = providers[pi];
                for (String tr : regions) {
                    if (tp.equals(f.getPrimaryProvider()) && tr.equals(f.getPrimaryRegion())) continue;
                    if (f.hasReplicaAt(tp, tr)) continue;

                    double[] sc = computeScore(f, tp, tr, infra,
                        replicasPerProvider[pi], avgLoad, popularityFactor);
                    // Un emplacement qui ne rapproche la donnée d'AUCUN site d'exécution
                    // observé (copie existante déjà aussi proche) n'est jamais créé.
                    if (sc[1] <= 1e-9) continue;
                    if (useHorizon > 0
                            && useHorizon * f.getPopularityEma() * recentBenefitShare(f, tp, tr, infra) < 1.0) {
                        continue;
                    }
                    double s = sc[0];
                    boolean covers = !f.hasReplica();
                    if ((covers && !bestCovers) || (covers == bestCovers && s > bestScore)) {
                        bestScore = s;
                        bestCovers = covers;
                        best = new Candidate(f, tp, tr, s);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Placement CONJOINT (agents RL et référence gloutonne). Une requête lit ses relations
     * en parallèle : son temps est fixé par la relation la plus lointaine et son coût est
     * la somme des transferts. Un réplica évalué seul (selectBest) ne voit pas cette
     * interaction : les réplicas se dispersaient sur deux ou trois fournisseurs et les
     * requêtes payaient encore la bande passante inter-provider (≈ 1300 GB contre
     * ≈ 300 GB quand les réplicas sont regroupés, validation en charge variable).
     *
     * <p>Chaque site candidat est évalué par un PLAN : y placer toutes les données
     * éligibles (au plus {@code capacity}, non couvertes d'abord puis par popularité),
     * puis rejouer les requêtes récentes, chacune sur le site d'exécution que choisirait
     * le routage (temps, puis coût). Le coût rejoué est celui que facture la simulation :
     * lectures, synchronisation des écritures du primaire vers chaque réplica,
     * maintenance des réplicas et création des réplicas du plan. Les plans sont classés comme le contrat : d'abord le
     * nombre de requêtes au-delà de T_SLA, puis le coût, puis le temps — répliquer pour
     * aller plus vite qu'exigé n'est pas un gain. Le plan doit améliorer la situation
     * actuelle ; on crée alors son premier réplica qui passe le filtre d'utilité
     * attendue, la part des lectures étant celle que le plan lui confierait. Les plans
     * sont essayés du meilleur au moins bon.</p>
     *
     * @return meilleur candidat, ou {@code null} si aucun plan n'améliore les requêtes récentes
     */
    public Candidate selectBestJoint(
        List<DataFragment> fragments,
        MultiCloudInfrastructure infra,
        double minPopularityNorm,
        int useHorizon,
        int capacity,
        double tSla
    ) {
        if (capacity <= 0 || recentQueries.isEmpty()) return null;
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;
        int nSites = providers.length * regions.length;

        java.util.Map<Integer, DataFragment> byId = new java.util.HashMap<>();
        for (DataFragment f : fragments) byId.put(f.getId(), f);
        List<DataFragment[]> queries = new java.util.ArrayList<>();
        for (int[] ids : recentQueries) {
            DataFragment[] q = new DataFragment[ids.length];
            boolean ok = true;
            for (int i = 0; i < ids.length && ok; i++) { q[i] = byId.get(ids[i]); ok = q[i] != null; }
            if (ok) queries.add(q);
        }
        if (queries.isEmpty()) return null;
        currentReplicas = 0;
        for (DataFragment f : fragments) currentReplicas += f.getReplicaCount();

        double[] current = replay(queries, infra, null, -1, null, tSla);

        record Plan(int site, List<DataFragment> order, double[] outcome) {}
        List<Plan> plans = new java.util.ArrayList<>();
        for (int s = 0; s < nSites; s++) {
            String tp = providers[s / regions.length], tr = regions[s % regions.length];
            List<DataFragment> order = new java.util.ArrayList<>();
            for (DataFragment f : fragments) {
                if (!f.canAddReplica() || f.hasRecreateCooldown()) continue;
                if (org.tcdrm.adaptive.core.TcdrmConstants.normalizedPopularity(f.getPopularityEma()) < minPopularityNorm) continue;
                if (tp.equals(f.getPrimaryProvider()) && tr.equals(f.getPrimaryRegion())) continue;
                if (f.hasReplicaAt(tp, tr)) continue;
                order.add(f);
            }
            if (order.isEmpty()) continue;
            // Couvrir avant de doubler, puis la donnée la plus populaire d'abord.
            order.sort(java.util.Comparator.<DataFragment>comparingInt(f -> f.hasReplica() ? 1 : 0)
                .thenComparing(java.util.Comparator.comparingDouble(DataFragment::getPopularityEma).reversed()));
            if (order.size() > capacity) order = new java.util.ArrayList<>(order.subList(0, capacity));
            java.util.Set<DataFragment> planned = new java.util.HashSet<>(order);
            double[] after = replay(queries, infra, planned, s, null, tSla);
            if (better(after, current)) plans.add(new Plan(s, order, after));
        }
        plans.sort((a, b) -> better(a.outcome(), b.outcome()) ? -1 : better(b.outcome(), a.outcome()) ? 1 : 0);

        for (Plan plan : plans) {
            String tp = providers[plan.site() / regions.length], tr = regions[plan.site() % regions.length];
            java.util.Set<DataFragment> planned = new java.util.HashSet<>(plan.order());
            java.util.Map<DataFragment, double[]> share = new java.util.HashMap<>();
            for (DataFragment f : plan.order()) share.put(f, new double[2]);   // {lectures servies par le site, lectures}
            replay(queries, infra, planned, plan.site(), share, tSla);
            for (DataFragment f : plan.order()) {
                double[] sh = share.get(f);
                double servedShare = sh[1] > 0 ? sh[0] / sh[1] : 0.0;
                if (useHorizon > 0 && useHorizon * f.getPopularityEma() * servedShare < 1.0) continue;
                return new Candidate(f, tp, tr, -plan.outcome()[1]);
            }
        }
        return null;
    }

    /** (violations T_SLA, coût, temps) lexicographique : a strictement meilleur que b. */
    private static boolean better(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            double eps = 1e-9 * Math.max(1.0, Math.abs(b[i]));
            if (a[i] < b[i] - eps) return true;
            if (a[i] > b[i] + eps) return false;
        }
        return false;
    }

    /**
     * Rejoue les requêtes récentes, chacune sur son meilleur site d'exécution (temps puis
     * coût), avec les copies existantes plus, si {@code planned} n'est pas nul, une copie
     * de chaque fragment planifié au site {@code plannedSite}. Source de chaque relation :
     * la copie de meilleur rang (intra-DC, inter-région, inter-provider), comme
     * {@link DataFragment#bestSourceLocation}. Si {@code share} est fourni, compte pour
     * chaque fragment planifié ses lectures et celles servies par le site planifié.
     *
     * @return {requêtes au-delà de tSla, somme des coûts, somme des temps}
     */
    private double[] replay(List<DataFragment[]> queries, MultiCloudInfrastructure infra,
                            java.util.Set<DataFragment> planned, int plannedSite,
                            java.util.Map<DataFragment, double[]> share, double tSla) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;
        String pp = plannedSite >= 0 ? providers[plannedSite / regions.length] : null;
        String pr = plannedSite >= 0 ? regions[plannedSite % regions.length] : null;
        double totalTime = 0.0, totalCost = 0.0, violations = 0.0;
        for (DataFragment[] q : queries) {
            double bestTime = Double.MAX_VALUE, bestCost = Double.MAX_VALUE;
            String bestP = null, bestR = null;
            for (String ep : providers) {
                for (String er : regions) {
                    double time = 0.0, cost = 0.0;
                    for (DataFragment f : q) {
                        double[] src = bestSource(f, ep, er, infra,
                            planned != null && planned.contains(f) ? pp : null, pr);
                        time = org.tcdrm.adaptive.core.TcdrmConstants.PARALLEL_FETCH
                            ? Math.max(time, src[0]) : time + src[0];
                        cost += src[1];
                    }
                    if (time < bestTime - 1e-9 || (Math.abs(time - bestTime) <= 1e-9 && cost < bestCost)) {
                        bestTime = time; bestCost = cost; bestP = ep; bestR = er;
                    }
                }
            }
            totalTime += bestTime;
            totalCost += bestCost + possessionCost(q, infra, planned, pp, pr);
            if (bestTime > tSla) violations += 1.0;
            if (share != null) {
                for (DataFragment f : q) {
                    double[] sh = share.get(f);
                    if (sh == null) continue;
                    sh[1] += 1.0;
                    if (bestSource(f, bestP, bestR, infra, pp, pr)[2] > 0.5) sh[0] += 1.0;
                }
            }
        }
        if (planned != null) {
            for (DataFragment f : planned) totalCost += org.tcdrm.adaptive.core.TcdrmConstants.replicationCost(f.getSizeGb());
        }
        return new double[]{violations, totalCost, totalTime};
    }

    /**
     * Coût de possession attendu des réplicas pour une requête (mêmes règles que
     * TcdrmSimulation) : une écriture (probabilité 1 − READ_WRITE_RATIO) propage chaque
     * relation lue du primaire vers tous ses réplicas, et chaque réplica coûte sa
     * maintenance par requête.
     */
    private double possessionCost(DataFragment[] q, MultiCloudInfrastructure infra,
                                  java.util.Set<DataFragment> planned, String pp, String pr) {
        double writeRatio = 1.0 - org.tcdrm.adaptive.core.TcdrmConstants.READ_WRITE_RATIO;
        double sync = 0.0;
        for (DataFragment f : q) {
            for (int i = 0; i < f.getReplicaCount(); i++) {
                sync += f.getSizeGb() * infra.getBandwidthCostPerGb(f.getPrimaryProvider(), f.getPrimaryRegion(),
                    f.getReplicaProviderAt(i), f.getReplicaRegionAt(i));
            }
            if (planned != null && planned.contains(f)) {
                sync += f.getSizeGb() * infra.getBandwidthCostPerGb(f.getPrimaryProvider(), f.getPrimaryRegion(), pp, pr);
            }
        }
        int replicas = (planned != null ? planned.size() : 0) + currentReplicas;
        return writeRatio * sync + replicas * org.tcdrm.adaptive.core.TcdrmConstants.REPLICA_MAINTENANCE_COST_PER_QUERY;
    }

    /**
     * Meilleure source de {@code f} pour le site (ep, er) : copie de meilleur rang (à rang
     * égal, la plus rapide), parmi le primaire, les réplicas et la copie planifiée
     * éventuelle (pp, pr). Temps analytique moyen (latence + transfert de la part lue,
     * gain de transfert d'un réplica chaud), coût sur la taille pleine (mêmes règles que
     * QueryCloudlet.execute).
     *
     * @return {temps ms, coût $, 1 si la copie planifiée est retenue sinon 0}
     */
    private static double[] bestSource(DataFragment f, String ep, String er, MultiCloudInfrastructure infra,
                                       String pp, String pr) {
        double dataGb = f.getSizeGb() * org.tcdrm.adaptive.core.TcdrmConstants.QUERY_SELECTIVITY;
        int bestRank = DataFragment.locationRank(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
        double bestTime = transferMs(infra, dataGb, f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er, false);
        double bestCost = f.getSizeGb() * infra.getBandwidthCostPerGb(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
        double planned = 0.0;
        int n = f.getReplicaCount() + (pp != null ? 1 : 0);
        for (int i = 0; i < n; i++) {
            boolean isPlanned = i == f.getReplicaCount();
            String sp = isPlanned ? pp : f.getReplicaProviderAt(i);
            String sr = isPlanned ? pr : f.getReplicaRegionAt(i);
            int rank = DataFragment.locationRank(sp, sr, ep, er);
            double t = transferMs(infra, dataGb, sp, sr, ep, er, true);
            if (rank < bestRank || (rank == bestRank && t < bestTime)) {
                bestRank = rank;
                bestTime = t;
                bestCost = f.getSizeGb() * infra.getBandwidthCostPerGb(sp, sr, ep, er);
                planned = isPlanned ? 1.0 : 0.0;
            }
        }
        return new double[]{bestTime, bestCost, planned};
    }

    private static double transferMs(MultiCloudInfrastructure infra, double dataGb,
                                     String sp, String sr, String ep, String er, boolean replica) {
        double t = infra.getLatencyMs(sp, sr, ep, er) + dataGb * 8000.0 / infra.getBandwidthGbps(sp, sr, ep, er);
        return replica ? t * (1.0 - org.tcdrm.adaptive.core.TcdrmConstants.REPLICA_TRANSFER_GAIN_FIRST) : t;
    }

    /**
     * Part récente (EMA) des sites d'exécution pour lesquels un réplica en
     * (targetProvider, targetRegion) serait strictement plus proche que toute copie
     * existante — c'est-à-dire la part des requêtes qu'il servirait.
     */
    private double recentBenefitShare(DataFragment f, String targetProvider, String targetRegion,
                                      MultiCloudInfrastructure infra) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;
        double share = 0.0;
        for (int pi = 0; pi < providers.length; pi++) {
            for (int ri = 0; ri < regions.length; ri++) {
                double w = recentExecShare[pi][ri];
                if (w <= 0.0) continue;
                String ep = providers[pi], er = regions[ri];
                double latCurrent = infra.getLatencyMs(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
                for (int i = 0; i < f.getReplicaCount(); i++) {
                    latCurrent = Math.min(latCurrent, infra.getLatencyMs(
                        f.getReplicaProviderAt(i), f.getReplicaRegionAt(i), ep, er));
                }
                if (infra.getLatencyMs(targetProvider, targetRegion, ep, er) < latCurrent) share += w;
            }
        }
        return share;
    }

    /** @return {score, gain de latence pondéré} — le gain seul sert de filtre d'utilité. */
    private double[] computeScore(
        DataFragment f,
        String targetProvider, String targetRegion,
        MultiCloudInfrastructure infra,
        int replicasOnTarget, double avgLoad,
        double popularityFactor
    ) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;
        int N = providers.length * regions.length;

        double weightedLatSaving  = 0.0;
        double weightedCostSaving = 0.0;

        for (int pi = 0; pi < providers.length; pi++) {
            for (int ri = 0; ri < regions.length; ri++) {
                String ep = providers[pi], er = regions[ri];
                // weight = observed frequency, or uniform (1/N) when no history yet
                double weight = totalExecCount > 0
                    ? (double) regionFreq[pi][ri] / totalExecCount
                    : 1.0 / N;

                if (weight <= 0.0) continue;
                // Gain MARGINAL : par rapport à la meilleure copie DÉJÀ disponible
                // (primaire ou réplica existant) pour ce site, pas au seul primaire.
                // Un second réplica proche d'une copie existante ne rapporte rien :
                // couvrir un fragment non répliqué passe avant doubler un fragment couvert.
                double latPrimary = infra.getLatencyMs(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
                double costPrimary = infra.getBandwidthCostPerGb(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
                double latCurrent = latPrimary, costCurrent = costPrimary;
                for (int i = 0; i < f.getReplicaCount(); i++) {
                    latCurrent = Math.min(latCurrent, infra.getLatencyMs(
                        f.getReplicaProviderAt(i), f.getReplicaRegionAt(i), ep, er));
                    costCurrent = Math.min(costCurrent, infra.getBandwidthCostPerGb(
                        f.getReplicaProviderAt(i), f.getReplicaRegionAt(i), ep, er));
                }
                double latReplica = infra.getLatencyMs(targetProvider, targetRegion, ep, er);
                weightedLatSaving += weight * Math.max(0.0, latCurrent - latReplica) / Math.max(1.0, latPrimary);

                double costReplica = infra.getBandwidthCostPerGb(targetProvider, targetRegion, ep, er);
                weightedCostSaving += weight * Math.max(0.0, costCurrent - costReplica) / Math.max(1e-9, costPrimary);
            }
        }

        // Pénalité d'équilibrage de charge (NSGA-II) — écart par rapport à la moyenne
        double loadRatio   = avgLoad > 0 ? replicasOnTarget / avgLoad : 0.0;
        double loadPenalty = Math.max(0.0, loadRatio - 1.0);

        // COÛT DE POSSESSION (synchronisation) : chaque écriture (READ_WRITE_RATIO)
        // propage la relation du primaire vers ce réplica au prix RÉEL du chemin.
        // Pénalité normalisée par le prix inter-provider — un candidat qui n'apporte
        // AUCUNE économie de service (ex. réplica d'un fragment dont le primaire est
        // déjà au site d'exécution) obtient un score net ≈ nul au lieu d'un bonus,
        // et n'est plus placé avant les candidats réellement rentables. Remplace
        // l'ancien bonus d'affinité même-provider (proxy) par l'économie exacte.
        double syncCostNorm = infra.getBandwidthCostPerGb(
                f.getPrimaryProvider(), f.getPrimaryRegion(), targetProvider, targetRegion)
            / org.tcdrm.adaptive.core.TcdrmConstants.COST_BW_INTER_PROVIDER;
        double writeRatio = 1.0 - org.tcdrm.adaptive.core.TcdrmConstants.READ_WRITE_RATIO;
        double syncPenalty = syncCostNorm * writeRatio * 10.0; // ×10 : ramène le taux d'écriture (0.1) à l'échelle [0,1] des autres termes

        double score = wLat * weightedLatSaving + wCost * weightedCostSaving
             + wPop * popularityFactor
             - wSync * syncPenalty
             - wSat * loadPenalty;
        return new double[]{score, weightedLatSaving};
    }

    /**
     * Adapte les poids dynamiquement selon les métriques de l'épisode écoulé.
     *
     * <ul>
     *   <li>Violations T_SLA élevées → augmenter w_lat + w_pop (répliquer les fragments chauds)</li>
     *   <li>Coût élevé → augmenter w_cost, réduire w_pop (parcimonie)</li>
     *   <li>Performance stable → dérive douce vers les poids de référence</li>
     * </ul>
     */
    public void adaptWeights(double violationRate, double avgCostRatio) {
        double step = 0.03;
        if (violationRate > 0.20) {
            // Violations élevées : prioriser latence et popularité
            wLat  = Math.min(0.55, wLat  + step);
            wPop  = Math.min(0.30, wPop  + step * 0.5);
            wCost = Math.max(0.15, wCost - step * 0.5);
        } else if (avgCostRatio > 1.20) {
            // Coût élevé : prioriser les économies BW, réduire popularité
            wCost = Math.min(0.55, wCost + step);
            wPop  = Math.max(0.10, wPop  - step * 0.5);
            wLat  = Math.max(0.15, wLat  - step * 0.5);
        } else {
            // Stable : dérive douce vers les poids de référence
            wLat  += (0.35 - wLat)  * 0.05;
            wCost += (0.35 - wCost) * 0.05;
            wPop  += (0.20 - wPop)  * 0.05;
        }
        // Renormaliser : somme des poids = 1 (wSync préservé — le coût de possession
        // est structurel, pas un arbitrage conjoncturel).
        double total = wLat + wCost + wPop + wSat + wSync;
        wLat /= total; wCost /= total; wPop /= total; wSat /= total; wSync /= total;
    }

    // Getters pour monitoring
    public double getWLat()  { return wLat; }
    public double getWCost() { return wCost; }
    public double getWSat()  { return wSat; }
    public double getWPop()  { return wPop; }
}
