package org.tcdrm.adaptive.simulation;

import org.tcdrm.adaptive.cloudsim.DataFragment;
import org.tcdrm.adaptive.cloudsim.MultiCloudInfrastructure;

import java.util.List;

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

    public ReplicaPlacementOptimizer() {
        this.regionFreq = new int[MultiCloudInfrastructure.PROVIDERS.length][MultiCloudInfrastructure.REGIONS.length];
    }

    /** Enregistre un site d'exécution observé pour pondérer le scoring futur. */
    public void recordExecution(String provider, String region) {
        int pi = indexOf(MultiCloudInfrastructure.PROVIDERS, provider);
        int ri = indexOf(MultiCloudInfrastructure.REGIONS, region);
        if (pi >= 0 && ri >= 0) { regionFreq[pi][ri]++; totalExecCount++; }
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
     * @return meilleur candidat, ou {@code null} si aucun fragment éligible
     */
    public Candidate selectBest(
        List<DataFragment> fragments,
        String execProvider, String execRegion,
        MultiCloudInfrastructure infra,
        int[] replicasPerProvider,
        double minPopularityNorm
    ) {
        String[] providers = MultiCloudInfrastructure.PROVIDERS;
        String[] regions   = MultiCloudInfrastructure.REGIONS;

        // Popularité max observée — pour normalisation SPEA2
        double maxEma = 1e-9;
        for (DataFragment f : fragments) {
            maxEma = Math.max(maxEma, f.getPopularityEma());
        }

        // Charge moyenne par provider — pour équilibrage NSGA-II
        int totalReplicas = 0;
        for (int r : replicasPerProvider) totalReplicas += r;
        double avgLoad = (double) totalReplicas / Math.max(1, providers.length);

        Candidate best      = null;
        double    bestScore = Double.NEGATIVE_INFINITY;

        for (DataFragment f : fragments) {
            if (!f.canAddReplica() || f.hasRecreateCooldown()) continue;
            // Éligibilité par donnée (Algorithm 1) : la popularité de CE fragment,
            // normalisée par le contrat P_SLA, doit atteindre le seuil adaptatif.
            double popNorm = org.tcdrm.adaptive.core.TcdrmConstants.normalizedPopularity(f.getPopularityEma());
            if (popNorm < minPopularityNorm) continue;

            double popularityFactor = f.getPopularityEma() / maxEma;  // relatif (SPEA2), pas de normalisation absolue nécessaire

            for (int pi = 0; pi < providers.length; pi++) {
                String tp = providers[pi];
                for (String tr : regions) {
                    if (tp.equals(f.getPrimaryProvider()) && tr.equals(f.getPrimaryRegion())) continue;
                    if (f.hasReplicaAt(tp, tr)) continue;

                    double s = computeScore(f, tp, tr, infra,
                        replicasPerProvider[pi], avgLoad, popularityFactor);
                    if (s > bestScore) {
                        bestScore = s;
                        best = new Candidate(f, tp, tr, s);
                    }
                }
            }
        }
        return best;
    }

    private double computeScore(
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

                double latPrimary = infra.getLatencyMs(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
                double latReplica = infra.getLatencyMs(targetProvider, targetRegion, ep, er);
                weightedLatSaving += weight * Math.max(0.0, latPrimary - latReplica) / Math.max(1.0, latPrimary);

                double costPrimary = infra.getBandwidthCostPerGb(f.getPrimaryProvider(), f.getPrimaryRegion(), ep, er);
                double costReplica = infra.getBandwidthCostPerGb(targetProvider, targetRegion, ep, er);
                weightedCostSaving += weight * Math.max(0.0, costPrimary - costReplica) / Math.max(1e-9, costPrimary);
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

        return wLat * weightedLatSaving + wCost * weightedCostSaving
             + wPop * popularityFactor
             - wSync * syncPenalty
             - wSat * loadPenalty;
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
