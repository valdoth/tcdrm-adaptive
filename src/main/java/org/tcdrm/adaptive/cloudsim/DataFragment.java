package org.tcdrm.adaptive.cloudsim;

import org.tcdrm.adaptive.core.TcdrmConstants;

/**
 * Représente un fragment de données (relation) dans le système TCDRM.
 * 
 * Paper Section 3.1:
 * - Chaque relation a une taille moyenne de 450 MB
 * - Les relations sont distribuées sur différents providers/régions
 * - La réplication copie une relation vers un autre datacenter
 */
public class DataFragment {

    private final int id;
    private final String name;
    private final double sizeGb;
    
    // Localisation primaire (où la donnée originale est stockée)
    private final String primaryProvider;
    private final String primaryRegion;
    
    // Réplica (null si pas répliqué)
    private String replicaProvider;
    private String replicaRegion;
    private int queriesSinceReplication;
    
    public DataFragment(int id, String name, String primaryProvider, String primaryRegion) {
        this(id, name, TcdrmConstants.AVG_RELATION_SIZE_GB, primaryProvider, primaryRegion);
    }

    public DataFragment(int id, String name, double sizeGb, String primaryProvider, String primaryRegion) {
        this.id = id;
        this.name = name;
        this.sizeGb = sizeGb;
        this.primaryProvider = primaryProvider;
        this.primaryRegion = primaryRegion;
        this.replicaProvider = null;
        this.replicaRegion = null;
        this.queriesSinceReplication = 0;
    }

    /**
     * Crée un réplica de ce fragment dans un autre datacenter.
     * @return le coût de création du réplica
     */
    public double createReplica(String provider, String region) {
        this.replicaProvider = provider;
        this.replicaRegion = region;
        this.queriesSinceReplication = 0;
        return TcdrmConstants.replicationCost(sizeGb);
    }

    /**
     * Supprime le réplica.
     */
    public void deleteReplica() {
        this.replicaProvider = null;
        this.replicaRegion = null;
        this.queriesSinceReplication = 0;
    }

    /**
     * Incrémente le compteur de requêtes depuis la réplication (pour warm-up).
     */
    public void incrementQueryCount() {
        if (hasReplica()) {
            queriesSinceReplication++;
        }
    }

    /**
     * Calcule l'efficacité du warm-up (Paper Fig 3).
     * Les réplicas deviennent progressivement efficaces.
     */
    public double getWarmupEfficiency() {
        if (!hasReplica()) {
            return 0.0;
        }
        return TcdrmConstants.warmupEfficiency(queriesSinceReplication);
    }

    public boolean hasReplica() {
        return replicaProvider != null && replicaRegion != null;
    }

    // === Getters ===
    
    public int getId() { return id; }
    public String getName() { return name; }
    public double getSizeGb() { return sizeGb; }
    public String getPrimaryProvider() { return primaryProvider; }
    public String getPrimaryRegion() { return primaryRegion; }
    public String getReplicaProvider() { return replicaProvider; }
    public String getReplicaRegion() { return replicaRegion; }
    public int getQueriesSinceReplication() { return queriesSinceReplication; }

    @Override
    public String toString() {
        String replica = hasReplica() ? 
            String.format(" -> replica@%s_%s (warmup=%.0f%%)", 
                replicaProvider, replicaRegion, getWarmupEfficiency() * 100) : "";
        return String.format("Fragment[%d:%s, %.2fGB, %s_%s%s]", 
            id, name, sizeGb, primaryProvider, primaryRegion, replica);
    }
}
