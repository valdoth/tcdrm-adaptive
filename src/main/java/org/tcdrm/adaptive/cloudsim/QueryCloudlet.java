package org.tcdrm.adaptive.cloudsim;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.List;
import java.util.Random;

/**
 * Représente une requête TCDRM sous forme de Cloudlet CloudSimPlus.
 * 
 * Paper Section 4.2:
 * - Simple query: 3 relations, 2 joins
 * - Complex query: 6 relations, 5 joins
 * 
 * Le cloudlet modélise:
 * - La charge CPU (MI) pour les opérations de jointure
 * - Le transfert de données (I/O) pour récupérer les relations
 */
public class QueryCloudlet {

    private final int queryId;
    private final boolean complex;
    private final List<DataFragment> fragments;
    private final Cloudlet cloudlet;
    
    // Métriques calculées après exécution
    private double transferTimeMs;
    private double joinTimeMs;
    private double bwCost;
    private double cpuCost;
    private double ioCost;
    private double bwInterProviderGb;
    private double bwInterRegionGb;

    /**
     * Crée un cloudlet pour une requête.
     */
    public QueryCloudlet(int queryId, boolean complex, List<DataFragment> fragments) {
        this.queryId = queryId;
        this.complex = complex;
        this.fragments = fragments;
        
        int nRelations = fragments.size();
        int nJoins = nRelations - 1;
        
        // Calculer la charge de travail en MI (millions d'instructions)
        double miTotal = nRelations * nJoins * TcdrmConstants.MI_PER_JOIN_PER_RELATION;
        long cloudletLength = (long) (miTotal * 1_000_000);
        
        // PEs requis (parallélisme)
        int pesRequired = Math.min(nRelations, 4);
        
        // Taille des données
        long inputSize = (long) (nRelations * TcdrmConstants.AVG_RELATION_SIZE_GB * 1024); // MB
        long outputSize = (long) (TcdrmConstants.AVG_RELATION_SIZE_GB * TcdrmConstants.QUERY_SELECTIVITY * 1024);
        
        // Créer le cloudlet CloudSimPlus
        this.cloudlet = new CloudletSimple(cloudletLength, pesRequired);
        this.cloudlet.setFileSize(inputSize);
        this.cloudlet.setOutputSize(outputSize);
        this.cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
        this.cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.5));
        this.cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.8));
    }

    /**
     * Exécute la requête et calcule les métriques.
     * 
     * @param infra infrastructure CloudSimPlus
     * @param execProvider provider où la requête est exécutée
     * @param execRegion région où la requête est exécutée
     * @param rnd générateur aléatoire pour le jitter
     */
    public void execute(MultiCloudInfrastructure infra, String execProvider, String execRegion, Random rnd) {
        int nRelations = fragments.size();
        int nJoins = nRelations - 1;
        
        double maxTransferMs = 0;
        double totalBwCost = 0;
        bwInterProviderGb = 0;
        bwInterRegionGb = 0;
        
        // Calculer le temps de transfert pour chaque fragment
        for (DataFragment fragment : fragments) {
            String srcProvider, srcRegion;
            
            // Utiliser le réplica s'il est disponible et dans le même provider
            if (fragment.hasReplica() && fragment.getReplicaProvider().equals(execProvider)) {
                srcProvider = fragment.getReplicaProvider();
                srcRegion = fragment.getReplicaRegion();
            } else {
                srcProvider = fragment.getPrimaryProvider();
                srcRegion = fragment.getPrimaryRegion();
            }
            
            // Données effectives transférées (sélectivité)
            double effectiveDataGb = fragment.getSizeGb() * TcdrmConstants.QUERY_SELECTIVITY;
            
            // Temps de transfert
            double transferMs = infra.computeTransferTimeMs(
                effectiveDataGb, srcProvider, srcRegion, execProvider, execRegion, rnd);
            
            // Appliquer le warm-up efficiency pour les réplicas
            if (fragment.hasReplica() && srcProvider.equals(fragment.getReplicaProvider())) {
                double warmup = fragment.getWarmupEfficiency();
                transferMs = transferMs * (1.0 - 0.3 * warmup);
            }
            
            // Parallel fetch: prendre le max
            if (TcdrmConstants.PARALLEL_FETCH) {
                maxTransferMs = Math.max(maxTransferMs, transferMs);
            } else {
                maxTransferMs += transferMs;
            }
            
            // Coût BW sur la taille complète (facturation provider)
            double costPerGb = infra.getBandwidthCostPerGb(srcProvider, srcRegion, execProvider, execRegion);
            totalBwCost += fragment.getSizeGb() * costPerGb;
            
            // Comptabiliser le type de transfert
            if (srcProvider.equals(execProvider)) {
                bwInterRegionGb += fragment.getSizeGb();
            } else {
                bwInterProviderGb += fragment.getSizeGb();
            }
        }
        
        this.transferTimeMs = maxTransferMs;
        this.bwCost = totalBwCost;
        
        // Temps de jointure (modèle quadratique du paper)
        double joinFactor = nJoins * (nJoins + 1) / 2.0;
        
        // Speedup si les données sont locales (répliquées)
        long localFragments = fragments.stream()
            .filter(f -> f.hasReplica() && f.getReplicaProvider().equals(execProvider))
            .count();
        double localFraction = (double) localFragments / nRelations;
        double avgWarmup = fragments.stream()
            .filter(DataFragment::hasReplica)
            .mapToDouble(DataFragment::getWarmupEfficiency)
            .average().orElse(0.0);
        double joinSpeedup = 1.0 - 0.6 * localFraction * avgWarmup;
        
        double cpuJitter = 1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1);
        this.joinTimeMs = joinFactor * TcdrmConstants.JOIN_BASE_MS * joinSpeedup * cpuJitter;
        
        // Coût CPU
        double miTotal = nRelations * nJoins * TcdrmConstants.MI_PER_JOIN_PER_RELATION;
        this.cpuCost = (miTotal / 10.0) * TcdrmConstants.CPU_COST_PER_10M_MI * cpuJitter;
        
        // Coût I/O
        double totalDataGb = nRelations * TcdrmConstants.AVG_RELATION_SIZE_GB;
        this.ioCost = totalDataGb * TcdrmConstants.IO_COST_PER_GB;
    }

    // === Getters ===
    
    public int getQueryId() { return queryId; }
    public boolean isComplex() { return complex; }
    public List<DataFragment> getFragments() { return fragments; }
    public Cloudlet getCloudlet() { return cloudlet; }
    
    public double getTotalTimeMs() { return transferTimeMs + joinTimeMs; }
    public double getTransferTimeMs() { return transferTimeMs; }
    public double getJoinTimeMs() { return joinTimeMs; }
    public double getBwCost() { return bwCost; }
    public double getCpuCost() { return cpuCost; }
    public double getIoCost() { return ioCost; }
    public double getTotalCost() { return bwCost + cpuCost + ioCost; }
    public double getBwInterProviderGb() { return bwInterProviderGb; }
    public double getBwInterRegionGb() { return bwInterRegionGb; }

    @Override
    public String toString() {
        return String.format("Query[%d, %s, %.1fms, $%.4f]",
            queryId, complex ? "complex" : "simple", getTotalTimeMs(), getTotalCost());
    }
}
