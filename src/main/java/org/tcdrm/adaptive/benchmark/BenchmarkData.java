package org.tcdrm.adaptive.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Données de benchmark pour une série de requêtes.
 * Utilisé pour générer les graphiques du paper (Figs 2-8).
 */
public class BenchmarkData {

    private final String name;
    private final List<Integer> queryNumbers = new ArrayList<>();
    private final List<Double> responseTimeMs = new ArrayList<>();
    private final List<Double> costPerQuery = new ArrayList<>();
    private final List<Double> cumulativeCost = new ArrayList<>();
    private final List<Integer> replicaCount = new ArrayList<>();
    private final List<Double> bwInterProviderGb = new ArrayList<>();
    private final List<Double> bwInterRegionGb = new ArrayList<>();
    private final List<Double> cumulativeBwInterProviderGb = new ArrayList<>();
    private final List<Double> cumulativeBwInterRegionGb = new ArrayList<>();
    private final List<Integer> slaViolations = new ArrayList<>();
    private final List<Double> avgBwPrice = new ArrayList<>();
    private final List<Double> cpuCostPerQuery = new ArrayList<>();
    private final List<Double> replicaCostPerQuery = new ArrayList<>();
    private final List<Double> totalCostPerQuery = new ArrayList<>();
    
    private double totalBwCost = 0;
    private double totalCpuCost = 0;
    private double totalReplicaCost = 0;
    private double cumulBwInterProvider = 0;
    private double cumulBwInterRegion = 0;
    private int totalSlaViolations = 0;
    // Cumulative BW cost (transfer only, not total) for correct BW price charts
    private double cumulBwCostRunning = 0;
    private final List<Double> cumulativeBwCostList = new ArrayList<>();

    public BenchmarkData(String name) {
        this.name = name;
    }

    public void addQueryResult(int queryNum, double timeMs, double cost, double cumCost, 
                               int replicas, double bwInterProvider, double bwInterRegion,
                               double cpuCost, double replicaCost, double tSla) {
        queryNumbers.add(queryNum);
        responseTimeMs.add(timeMs);
        costPerQuery.add(cost);
        cumulativeCost.add(cumCost);
        replicaCount.add(replicas);
        bwInterProviderGb.add(bwInterProvider);
        bwInterRegionGb.add(bwInterRegion);
        
        // Cumulative BW
        cumulBwInterProvider += bwInterProvider;
        cumulBwInterRegion += bwInterRegion;
        cumulativeBwInterProviderGb.add(cumulBwInterProvider);
        cumulativeBwInterRegionGb.add(cumulBwInterRegion);
        
        // SLA violations
        if (timeMs > tSla) {
            totalSlaViolations++;
        }
        slaViolations.add(totalSlaViolations);

        // Cumulative BW cost (transfer cost only) for correct BW price charts
        cumulBwCostRunning += cost;
        cumulativeBwCostList.add(cumulBwCostRunning);
        avgBwPrice.add(cumulBwCostRunning / (queryNum + 1));

        totalBwCost += cost;
        totalCpuCost += cpuCost;
        totalReplicaCost += replicaCost;

        // Per-query breakdowns for export
        cpuCostPerQuery.add(cpuCost);
        replicaCostPerQuery.add(replicaCost);
        totalCostPerQuery.add(cost + cpuCost + replicaCost);
    }

    // === Getters ===
    
    public String getName() { return name; }
    public List<Integer> getQueryNumbers() { return queryNumbers; }
    public List<Double> getResponseTimeMs() { return responseTimeMs; }
    public List<Double> getCostPerQuery() { return costPerQuery; }
    public List<Double> getCumulativeCost() { return cumulativeCost; }
    public List<Integer> getReplicaCount() { return replicaCount; }
    public List<Double> getBwInterProviderGb() { return bwInterProviderGb; }
    public List<Double> getBwInterRegionGb() { return bwInterRegionGb; }
    
    public double getTotalBwCost() { return totalBwCost; }
    public double getTotalCpuCost() { return totalCpuCost; }
    public double getTotalReplicaCost() { return totalReplicaCost; }
    public double getTotalCost() { return totalBwCost + totalCpuCost + totalReplicaCost; }
    
    public double getTotalBwInterProviderGb() {
        return bwInterProviderGb.stream().mapToDouble(Double::doubleValue).sum();
    }
    
    public double getTotalBwInterRegionGb() {
        return bwInterRegionGb.stream().mapToDouble(Double::doubleValue).sum();
    }
    
    public List<Double> getCumulativeBwInterProviderGb() { return cumulativeBwInterProviderGb; }
    public List<Double> getCumulativeBwInterRegionGb() { return cumulativeBwInterRegionGb; }
    public List<Double> getCumulativeBwCostList() { return cumulativeBwCostList; }
    public List<Integer> getSlaViolations() { return slaViolations; }
    public List<Double> getAvgBwPrice() { return avgBwPrice; }
    public int getTotalSlaViolations() { return totalSlaViolations; }
    public List<Double> getCpuCostPerQuery() { return cpuCostPerQuery; }
    public List<Double> getReplicaCostPerQuery() { return replicaCostPerQuery; }
    public List<Double> getTotalCostPerQuery() { return totalCostPerQuery; }

    public void printSummary() {
        int last = queryNumbers.size() - 1;
        System.out.printf("  %s: time[0]=%.1fms, time[500]=%.1fms, cumulCost[%d]=$%.2f, replicas[%d]=%d%n",
            name, 
            responseTimeMs.get(0), 
            responseTimeMs.get(Math.min(500, last)),
            last, cumulativeCost.get(last),
            last, replicaCount.get(last));
    }
}
