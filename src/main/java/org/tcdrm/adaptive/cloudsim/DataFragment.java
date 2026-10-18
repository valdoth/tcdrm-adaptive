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
    // Requêtes écoulées depuis que ce réplica a servi de SOURCE à une requête : un
    // réplica que plus aucune requête ne lit (donnée refroidie, ou copie placée là où
    // rien ne s'exécute) est inutile même si sa donnée reste accédée ailleurs.
    private final int[]    queriesSinceUsed = new int[MAX_REPLICAS];
    // Vrai dès que le réplica a servi au moins une lecture (mesure du coût inutile).
    private final boolean[] everUsed = new boolean[MAX_REPLICAS];
    // Fenêtre ΔT ENGAGÉE à la création du réplica (TTL fixé à l'insertion) : le
    // méta-contrôleur peut changer ΔT à chaque requête, mais un réplica existant garde
    // la fenêtre sous laquelle il a été créé — une baisse momentanée de ΔT ne rend pas
    // supprimables d'un coup des réplicas qui viennent d'être payés.
    private final int[]    committedWindow = new int[MAX_REPLICAS];
    private int replicaCount = 0;

    // Cooldown before re-creating a replica after deletion
    private int recreateCooldown;

    // Compteur d'accès total (diagnostic / SPEA2 legacy)
    private int accessCount = 0;

    // POPULARITÉ = taux d'accès RÉCENT (EMA), conforme à la littérature (fréquence
    // d'accès sur fenêtre à décroissance exponentielle) — PAS un cumul. Mis à jour à
    // CHAQUE requête pour ce fragment : +λ s'il est accédé, décroissance sinon.
    // Un accès SOUTENU est nécessaire pour être « populaire » (encode la confiance) ;
    // une donnée qui refroidit voit son EMA décroître.
    private double popularityEma = 0.0;

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
        return createReplica(provider, region, 0);
    }

    /**
     * Crée un réplica en lui engageant la fenêtre ΔT de suppression en vigueur.
     * @return le coût de création du réplica, ou 0 si aucun slot libre.
     */
    public double createReplica(String provider, String region, int deletionWindow) {
        if (replicaCount >= MAX_REPLICAS) return 0.0;
        committedWindow[replicaCount]  = Math.max(0, deletionWindow);
        replicaProviders[replicaCount] = provider;
        replicaRegions[replicaCount]   = region;
        queriesSinceRep[replicaCount]  = 0;
        queriesSinceUsed[replicaCount] = 0;
        everUsed[replicaCount] = false;
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
     * Supprime le réplica du slot {@code idx} (les slots suivants sont décalés).
     */
    public void deleteReplicaAt(int idx) {
        if (idx < 0 || idx >= replicaCount) return;
        for (int i = idx; i < replicaCount - 1; i++) {
            replicaProviders[i] = replicaProviders[i + 1];
            replicaRegions[i]   = replicaRegions[i + 1];
            queriesSinceRep[i]  = queriesSinceRep[i + 1];
            queriesSinceUsed[i] = queriesSinceUsed[i + 1];
            everUsed[i]         = everUsed[i + 1];
            committedWindow[i]  = committedWindow[i + 1];
        }
        deleteReplica();
    }

    /**
     * Incrémente le compteur de requêtes sur tous les réplicas actifs (pour warm-up).
     */
    public void incrementQueryCount() {
        for (int i = 0; i < replicaCount; i++) {
            queriesSinceRep[i]++;
            queriesSinceUsed[i]++;
        }
    }

    /**
     * Décompte le cooldown de re-création — à CHAQUE requête pour TOUS les fragments :
     * appelé seulement sur les fragments ayant encore un réplica (ancien comportement),
     * le cooldown d'un fragment dont on vient de supprimer le DERNIER réplica ne
     * décroissait plus, et ce fragment ne pouvait plus jamais être répliqué.
     */
    public void tickRecreateCooldown() {
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
    public record LocationChoice(String provider, String region, boolean usingReplica, double warmupEff,
                                 int replicaIndex, int usableReplicas) {}

    /**
     * Retourne la meilleure source (primaire ou réplica) pour servir la requête depuis
     * (execProvider, execRegion). Un réplica est préféré dès qu'il est AUSSI proche que
     * le primaire (rang ≤ rang primaire) : servir depuis un réplica applique le gain de
     * partage de charge (REPLICA_TRANSFER_GAIN × warmup), donc à distance égale le
     * réplica est toujours plus rapide — maximise l'UTILISATION des réplicas créés.
     *
     * <p>Priorité : intra-DC (0) > inter-région (1) > inter-provider (2).</p>
     */
    public LocationChoice bestSourceLocation(String execProvider, String execRegion) {
        int primaryRank = locationRank(primaryProvider, primaryRegion, execProvider, execRegion);
        int bestRank = primaryRank;
        int bestIdx  = -1;

        for (int i = 0; i < replicaCount; i++) {
            int rank = locationRank(replicaProviders[i], replicaRegions[i], execProvider, execRegion);
            // ≤ : à rang égal, le réplica gagne (gain de transfert + délestage du primaire)
            if (rank < bestRank || (rank == bestRank && bestIdx < 0 && rank <= primaryRank)) {
                bestRank = rank;
                bestIdx  = i;
            }
        }

        if (bestIdx >= 0) {
            double warmup = TcdrmConstants.warmupEfficiency(queriesSinceRep[bestIdx]);
            // Partage de charge : seules les copies AUSSI proches que la source retenue
            // peuvent servir cette lecture. Un réplica d'une autre région/d'un autre
            // provider ne soulage pas le transfert vers ce site d'exécution.
            int usable = 0;
            for (int i = 0; i < replicaCount; i++) {
                if (locationRank(replicaProviders[i], replicaRegions[i], execProvider, execRegion) == bestRank) usable++;
            }
            return new LocationChoice(replicaProviders[bestIdx], replicaRegions[bestIdx], true, warmup,
                bestIdx, usable);
        }
        return new LocationChoice(primaryProvider, primaryRegion, false, 0.0, -1, 0);
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

    /** Âge (en requêtes) du réplica du slot {@code i}. */
    public int getReplicaAge(int i) { return queriesSinceRep[i]; }

    /** Fenêtre ΔT engagée à la création du réplica du slot {@code i}. */
    public int getCommittedWindow(int i) { return committedWindow[i]; }

    /** Requêtes écoulées depuis que le réplica du slot {@code i} a servi de source. */
    public int getQueriesSinceUsed(int i) { return queriesSinceUsed[i]; }

    /** Le réplica du slot {@code i} vient de servir de source à une requête. */
    public void markReplicaUsed(int i) {
        if (i >= 0 && i < replicaCount) {
            queriesSinceUsed[i] = 0;
            everUsed[i] = true;
        }
    }

    /** Vrai si le réplica du slot {@code i} a servi au moins une lecture. */
    public boolean wasReplicaUsed(int i) { return i >= 0 && i < replicaCount && everUsed[i]; }

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

    /** Localisation du i-ème réplica actif (0 ≤ i < replicaCount). */
    public String getReplicaProviderAt(int i) { return replicaProviders[i]; }
    public String getReplicaRegionAt(int i)   { return replicaRegions[i]; }

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

    /**
     * Met à jour la popularité (EMA du taux d'accès) pour la requête courante.
     * À appeler pour CHAQUE fragment à chaque requête : {@code accessed=true} s'il est
     * dans la requête (l'EMA monte), false sinon (l'EMA décroît).
     *   pop ← (1−λ)·pop + λ·[accessed]
     */
    public void updatePopularity(boolean accessed, double lambda) {
        popularityEma = (1.0 - lambda) * popularityEma + (accessed ? lambda : 0.0);
    }

    /** Popularité = taux d'accès récent (EMA) ∈ [0,1]. Définition littérature. */
    public double getPopularityEma() { return popularityEma; }

    /**
     * Reconstitue un historique d'accès (départ exploratoire d'entraînement uniquement) :
     * le fragment a été lu à chacune des {@code accesses} requêtes consécutives qui se
     * terminent {@code gap} requêtes avant {@code currentQuery}. Popularité (EMA),
     * compteur, dernier accès et fenêtres de tendance sont tous dérivés de ce même
     * historique, donc cohérents entre eux (une popularité élevée implique un accès
     * récent et un compteur élevé).
     */
    public void seedAccessHistory(int currentQuery, int accesses, int gap, double lambda) {
        int end = currentQuery - gap;              // première requête sans accès
        int start = Math.max(0, end - accesses);   // première requête avec accès
        resetAccessStats();
        for (int q = start; q < end; q++) recordAccess(q);   // compteur, dernier accès, tendance
        int n = Math.max(0, end - start);
        this.popularityEma = (1.0 - Math.pow(1.0 - lambda, n)) * Math.pow(1.0 - lambda, gap);
    }

    /** Marque tous les réplicas actifs comme déjà chauffés (départ exploratoire). */
    public void markReplicasWarm() {
        for (int i = 0; i < replicaCount; i++) {
            queriesSinceRep[i] = TcdrmConstants.WARMUP_QUERIES;
            queriesSinceUsed[i] = 0;
        }
    }

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
        this.popularityEma = 0.0;
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
