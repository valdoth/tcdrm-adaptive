package org.tcdrm.adaptive.cloudsim;

import org.tcdrm.adaptive.core.TcdrmConstants;

/**
 * Représente un fragment de données (relation) dans le système TCDRM.
 *
 * Paper Section 3.1:
 * - Chaque relation a une taille moyenne de 450 MB
 * - Les relations sont distribuées sur différents providers/régions
 * - La réplication copie une relation vers un autre datacenter
 *
 * Support jusqu'à MAX_REPLICAS (2) réplicas par fragment :
 * simple (3 fragments) → max 6 réplicas ; complex (6 fragments) → max 12 réplicas.
 */
public class DataFragment {

    /** Max replicas per fragment: 2 (primary × 2 = 6 for simple, 12 for complex). */
    public static final int MAX_REPLICAS = 2;

    private final int id;
    private final String name;
    private final double sizeGb;

    // Localisation primaire (où la donnée originale est stockée)
    private final String primaryProvider;
    private final String primaryRegion;

    // Réplicas (slots 0 et 1)
    private final String[] replicaProviders = new String[MAX_REPLICAS];
    private final String[] replicaRegions   = new String[MAX_REPLICAS];
    private final int[]    queriesSinceRep  = new int[MAX_REPLICAS];
    private int replicaCount = 0;

    // Cooldown before re-creating a replica after deletion
    private int recreateCooldown;

    // Compteur d'accès total — utilisé pour le scoring de popularité SPEA2
    private int accessCount = 0;

    // Index de la dernière requête ayant accédé à ce fragment.
    // Utilisé pour déterminer si les données sont "encore utilisées" avant toute suppression.
    private int lastAccessedQuery = -1;

    // Sliding window for access rate trending (two half-windows: recent vs older)
    private static final int TREND_WINDOW = 100;  // queries per half-window
    private int recentHalfCount = 0;   // accesses in last TREND_WINDOW queries
    private int olderHalfCount  = 0;   // accesses in previous TREND_WINDOW queries
    private int halfWindowStart = 0;   // query index when current half started

    public DataFragment(int id, String name, String primaryProvider, String primaryRegion) {
        this(id, name, TcdrmConstants.AVG_RELATION_SIZE_GB, primaryProvider, primaryRegion);
    }

    public DataFragment(int id, String name, double sizeGb, String primaryProvider, String primaryRegion) {
        this.id = id;
        this.name = name;
        this.sizeGb = sizeGb;
        this.primaryProvider = primaryProvider;
        this.primaryRegion = primaryRegion;
    }

    /**
     * Crée un réplica dans le prochain slot disponible.
     * @return le coût de création du réplica, ou 0 si aucun slot libre.
     */
    public double createReplica(String provider, String region) {
        if (replicaCount >= MAX_REPLICAS) return 0.0;
        replicaProviders[replicaCount] = provider;
        replicaRegions[replicaCount]   = region;
        queriesSinceRep[replicaCount]  = 0;
        replicaCount++;
        return TcdrmConstants.replicationCost(sizeGb);
    }

    /**
     * Supprime le réplica le plus récent (LIFO).
     */
    public void deleteReplica() {
        if (replicaCount > 0) {
            replicaCount--;
            replicaProviders[replicaCount] = null;
            replicaRegions[replicaCount]   = null;
            queriesSinceRep[replicaCount]  = 0;
        }
    }

    /**
     * Incrémente le compteur de requêtes sur tous les réplicas actifs (pour warm-up).
     */
    public void incrementQueryCount() {
        for (int i = 0; i < replicaCount; i++) {
            queriesSinceRep[i]++;
        }
        if (recreateCooldown > 0) recreateCooldown--;
    }

    /**
     * Efficacité de warm-up (moyenne sur tous les réplicas actifs).
     */
    public double getWarmupEfficiency() {
        if (replicaCount == 0) return 0.0;
        double sum = 0;
        for (int i = 0; i < replicaCount; i++) {
            sum += TcdrmConstants.warmupEfficiency(queriesSinceRep[i]);
        }
        return sum / replicaCount;
    }

    /**
     * Résultat du choix de source optimal pour une requête.
     *
     * @param provider    provider source retenu (primaire ou réplica)
     * @param region      région source retenue
     * @param usingReplica true si un réplica est sélectionné (meilleur que le primaire)
     * @param warmupEff   efficacité de warm-up du réplica sélectionné (0 si primaire)
     */
    public record LocationChoice(String provider, String region, boolean usingReplica, double warmupEff) {}

    /**
     * Retourne la meilleure source (primaire ou réplica) pour servir la requête depuis
     * (execProvider, execRegion). Un réplica est préféré UNIQUEMENT s'il est strictement
     * plus proche que le primaire (rang < rang primaire).
     *
     * <p>Priorité : intra-DC (0) > inter-région (1) > inter-provider (2).</p>
     */
    public LocationChoice bestSourceLocation(String execProvider, String execRegion) {
        int primaryRank = locationRank(primaryProvider, primaryRegion, execProvider, execRegion);
        int bestRank = primaryRank;
        int bestIdx  = -1;

        for (int i = 0; i < replicaCount; i++) {
            int rank = locationRank(replicaProviders[i], replicaRegions[i], execProvider, execRegion);
            if (rank < bestRank) {
                bestRank = rank;
                bestIdx  = i;
            }
        }

        if (bestIdx >= 0) {
            double warmup = TcdrmConstants.warmupEfficiency(queriesSinceRep[bestIdx]);
            return new LocationChoice(replicaProviders[bestIdx], replicaRegions[bestIdx], true, warmup);
        }
        return new LocationChoice(primaryProvider, primaryRegion, false, 0.0);
    }

    /**
     * @deprecated Use {@link #bestSourceLocation} which also checks whether the replica
     *             is actually closer than the primary before using it.
     */
    @Deprecated
    public String[] getBestReplicaLocation(String execProvider, String execRegion) {
        if (replicaCount == 0) return new String[]{primaryProvider, primaryRegion};
        int bestRank = 3;
        int bestIdx  = 0;
        for (int i = 0; i < replicaCount; i++) {
            int rank = locationRank(replicaProviders[i], replicaRegions[i], execProvider, execRegion);
            if (rank < bestRank) { bestRank = rank; bestIdx = i; }
        }
        return new String[]{replicaProviders[bestIdx], replicaRegions[bestIdx]};
    }

    /** Queries since the last (most recent) replica was added — used for deletion anti-oscillation. */
    public int getQueriesSinceReplication() {
        if (replicaCount == 0) return 0;
        return queriesSinceRep[replicaCount - 1];
    }

    public boolean hasReplica()    { return replicaCount > 0; }
    public boolean canAddReplica() { return replicaCount < MAX_REPLICAS; }

    /** True si un réplica existe déjà à l'emplacement (provider, region) donné. */
    public boolean hasReplicaAt(String provider, String region) {
        for (int i = 0; i < replicaCount; i++) {
            if (provider.equals(replicaProviders[i]) && region.equals(replicaRegions[i])) return true;
        }
        return false;
    }

    public int    getReplicaCount()  { return replicaCount; }

    // Backward-compat: slot 0
    public String getReplicaProvider() { return replicaCount > 0 ? replicaProviders[0] : null; }
    public String getReplicaRegion()   { return replicaCount > 0 ? replicaRegions[0]   : null; }

    public void startRecreateCooldown(int queries) {
        this.recreateCooldown = Math.max(this.recreateCooldown, Math.max(0, queries));
    }
    public boolean hasRecreateCooldown() { return recreateCooldown > 0; }

    // === Getters ===
    public int    getId()              { return id; }
    public String getName()            { return name; }
    public double getSizeGb()          { return sizeGb; }
    public String getPrimaryProvider() { return primaryProvider; }
    public String getPrimaryRegion()   { return primaryRegion; }
    public int    getRecreateCooldown(){ return recreateCooldown; }

    /**
     * Enregistre un accès à ce fragment (requête l'utilisant).
     * Met à jour le compteur total et l'index de dernière utilisation.
     *
     * @param queryIndex index global de la requête courante (pour la fenêtre de popularité)
     */
    public void recordAccess(int queryIndex) {
        accessCount++;
        lastAccessedQuery = queryIndex;
        // Advance the half-window if TREND_WINDOW queries have elapsed
        if (queryIndex - halfWindowStart >= TREND_WINDOW) {
            olderHalfCount = recentHalfCount;
            recentHalfCount = 0;
            halfWindowStart = queryIndex;
        }
        recentHalfCount++;
    }

    /** Rétro-compatibilité — préférer {@link #recordAccess(int)} avec l'index de requête. */
    public void recordAccess() { accessCount++; }

    /** Nombre total d'accès depuis la création de cet épisode. */
    public int getAccessCount() { return accessCount; }

    /** Index de la requête lors du dernier accès (-1 si jamais accédé). */
    public int getLastAccessedQuery() { return lastAccessedQuery; }

    /**
     * Retourne vrai si ce fragment a été accédé dans la fenêtre glissante
     * {@code [currentQuery - window, currentQuery]}.
     *
     * Utilisé avant toute suppression de réplica : on ne supprime pas un réplica
     * dont les données sont encore activement utilisées.
     */
    public boolean isStillPopular(int currentQuery, int window) {
        return lastAccessedQuery >= 0 && (currentQuery - lastAccessedQuery) <= window;
    }

    /** Réinitialise les compteurs d'accès pour un nouvel épisode. */
    public void resetAccessStats() {
        this.accessCount = 0;
        this.lastAccessedQuery = -1;
        this.recentHalfCount = 0;
        this.olderHalfCount = 0;
        this.halfWindowStart = 0;
    }

    /**
     * Returns popularity trend: +1 rising, 0 stable, -1 falling.
     * Inspired by first-order differential equation approach (IRE 2019 paper).
     */
    public int getPopularityTrend() {
        if (olderHalfCount == 0) return 0;  // not enough history
        double ratio = (double) recentHalfCount / olderHalfCount;
        if (ratio > 1.3) return +1;   // 30%+ increase → rising
        if (ratio < 0.7) return -1;   // 30%+ decrease → falling
        return 0;
    }

    public int getRecentAccessCount() { return recentHalfCount; }

    // 0 = intra-DC, 1 = inter-region, 2 = inter-provider
    private static int locationRank(String sp, String sr, String ep, String er) {
        if (!sp.equals(ep)) return 2;
        if (!sr.equals(er)) return 1;
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
            String.format("Fragment[%d:%s, %.2fGB, %s_%s", id, name, sizeGb, primaryProvider, primaryRegion));
        for (int i = 0; i < replicaCount; i++) {
            sb.append(String.format(" -> rep%d@%s_%s(%.0f%%)", i + 1,
                replicaProviders[i], replicaRegions[i],
                TcdrmConstants.warmupEfficiency(queriesSinceRep[i]) * 100));
        }
        sb.append("]");
        return sb.toString();
    }
}
