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
     * Retourne le provider/région du réplica le plus proche de (execProvider, execRegion) :
     * priorité intra-DC > inter-région > inter-provider.
     * Retourne {primaryProvider, primaryRegion} si aucun réplica.
     */
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

    /** Queries since the last (most recent) replica was added — used for deletion gate. */
    public int getQueriesSinceReplication() {
        if (replicaCount == 0) return 0;
        return queriesSinceRep[replicaCount - 1];
    }

    public boolean hasReplica()    { return replicaCount > 0; }
    public boolean canAddReplica() { return replicaCount < MAX_REPLICAS; }
    public int     getReplicaCount() { return replicaCount; }

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
