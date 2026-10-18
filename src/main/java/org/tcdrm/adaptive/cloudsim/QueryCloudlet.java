package org.tcdrm.adaptive.cloudsim;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.List;
import java.util.Random;

/**
 * Requête TCDRM : transfert multi-cloud (modèle analytique) + phase jointure exécutée dans CloudSim
 * Plus ({@link MultiCloudInfrastructure#runCloudletToCompletion}) après {@code startSync}.
 */
public class QueryCloudlet {

    private final int queryId;
    private final boolean complex;
    private final List<DataFragment> fragments; // active fragments for THIS query

    private Cloudlet cloudlet;

    private double transferTimeMs;
    private double joinTimeMs;
    private double bwCost;
    private double cpuCost;
    private double ioCost;
    private double bwInterProviderGb;
    private double bwInterRegionGb;
    private double servedFraction;

    public QueryCloudlet(int queryId, boolean complex, List<DataFragment> fragments) {
        this.queryId = queryId;
        this.complex = complex;
        this.fragments = fragments;
    }

    public void execute(MultiCloudInfrastructure infra, String execProvider, String execRegion, Random rnd) {
        int nRelations = fragments.size();
        int nJoins = nRelations - 1;

        double maxTransferMs = 0;
        double servedByReplica = 0;   // Σ warmup des relations lues depuis un réplica
        int servedCount = 0;          // relations lues depuis un réplica
        double totalBwCost = 0;
        bwInterProviderGb = 0;
        bwInterRegionGb = 0;

        for (DataFragment fragment : fragments) {
            // Prefer replica only when it is strictly closer than the primary
            DataFragment.LocationChoice loc = fragment.bestSourceLocation(execProvider, execRegion);
            String srcProvider  = loc.provider();
            String srcRegion    = loc.region();
            boolean usingReplica = loc.usingReplica();
            double warmupEff    = loc.warmupEff();

            double effectiveDataGb = fragment.getSizeGb() * TcdrmConstants.QUERY_SELECTIVITY;

            double transferMs = infra.computeTransferTimeMs(
                effectiveDataGb, srcProvider, srcRegion, execProvider, execRegion, rnd);

            if (usingReplica) {
                // Gain PROGRESSIF avec le nombre de copies du fragment qui peuvent
                // servir CE site (partage de charge) — Paper Fig 3. Seules les copies
                // aussi proches que la source retenue comptent : un réplica placé dans
                // une région où rien ne s'exécute n'accélère pas cette lecture.
                double gain = TcdrmConstants.REPLICA_TRANSFER_GAIN_FIRST
                    + TcdrmConstants.REPLICA_TRANSFER_GAIN_EXTRA
                        * Math.max(0, loc.usableReplicas() - 1);
                transferMs = transferMs * (1.0 - gain * warmupEff);
                fragment.markReplicaUsed(loc.replicaIndex());
                servedByReplica += warmupEff;
                servedCount++;
            }

            if (TcdrmConstants.PARALLEL_FETCH) {
                maxTransferMs = Math.max(maxTransferMs, transferMs);
            } else {
                maxTransferMs += transferMs;
            }

            double costPerGb = infra.getBandwidthCostPerGb(srcProvider, srcRegion, execProvider, execRegion);
            totalBwCost += fragment.getSizeGb() * costPerGb;

            if (srcProvider.equals(execProvider)) {
                bwInterRegionGb += fragment.getSizeGb();
            } else {
                bwInterProviderGb += fragment.getSizeGb();
            }
        }

        this.transferTimeMs = maxTransferMs;
        this.bwCost = totalBwCost;

        double joinFactor = nJoins * (nJoins + 1) / 2.0;

        // Couverture réplicas : fraction des relations de CETTE requête effectivement
        // servies par une copie chaude. La jointure accélère PROGRESSIVEMENT avec la
        // couverture — Paper Fig 3 : le temps de réponse décroît à mesure que les
        // relations sont répliquées. Un réplica qui ne sert pas la requête (autre
        // région, donnée non lue) ne compte pas : on mesure le service, pas le stock.
        double coverage = servedByReplica / Math.max(1, nRelations);
        this.servedFraction = (double) servedCount / Math.max(1, nRelations);
        double joinSpeedup = 1.0 - TcdrmConstants.JOIN_COVERAGE_SPEEDUP * coverage;

        double cpuJitter = 1.0 + TcdrmConstants.CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1);

        double targetJoinMs = joinFactor * TcdrmConstants.JOIN_BASE_MS * joinSpeedup * cpuJitter;
        long miJoin = Math.max(1L, Math.round((targetJoinMs / 1000.0) * MultiCloudInfrastructure.VM_MIPS));

        // 1 PE per cloudlet avoids VM contention and gives deterministic join times
        int pesRequired = 1;
        this.cloudlet = new CloudletSimple(miJoin, pesRequired);
        long inputSize = (long) (nRelations * TcdrmConstants.AVG_RELATION_SIZE_GB * 1024);
        long outputSize = (long) (TcdrmConstants.AVG_RELATION_SIZE_GB * TcdrmConstants.QUERY_SELECTIVITY * 1024);
        this.cloudlet.setFileSize(inputSize);
        this.cloudlet.setOutputSize(outputSize);
        this.cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
        this.cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.5));
        this.cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.8));

        double wallSec = infra.runCloudletToCompletion(cloudlet, execProvider, execRegion, rnd);
        if (wallSec > 1e-9) {
            this.joinTimeMs = wallSec * 1000.0;
        } else {
            this.joinTimeMs = targetJoinMs;
        }

        double miTotal = nRelations * nJoins * TcdrmConstants.MI_PER_JOIN_PER_RELATION;
        this.cpuCost = (miTotal / 10.0) * TcdrmConstants.CPU_COST_PER_10M_MI * cpuJitter;

        // IO cost on effective data accessed (with selectivity), not full relation size
        double totalDataGb = nRelations * TcdrmConstants.AVG_RELATION_SIZE_GB * TcdrmConstants.QUERY_SELECTIVITY;
        this.ioCost = totalDataGb * TcdrmConstants.IO_COST_PER_GB;
    }

    /** Fraction des relations de la requête lues depuis un réplica. */
    public double getServedFraction() { return servedFraction; }

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
