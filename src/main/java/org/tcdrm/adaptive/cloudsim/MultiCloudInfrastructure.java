package org.tcdrm.adaptive.cloudsim;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.*;

/**
 * Infrastructure multi-cloud CloudSimPlus pour TCDRM-Adaptive.
 * 
 * Architecture (conforme au schéma multi-cloud):
 * - 3 providers (Google, AWS, Azure)
 * - 3 régions par provider (US, EU, AS)
 * - 2 sub-regions par région (sub_region1, sub_region2)
 * - 2 datacenters par sub-region
 * - 2 VMs par datacenter
 * 
 * Total: 3 × 3 × 2 × 2 = 36 datacenters, 72 VMs
 * 
 * Modèle réseau:
 * - Intra-DC: 10 Gbps, 1ms latency
 * - Intra-subregion: 5 Gbps, 5ms latency
 * - Inter-region (same provider): 2 Gbps, 30ms latency  
 * - Inter-provider: 1 Gbps, 80ms latency
 */
public class MultiCloudInfrastructure {

    private final CloudSimPlus simulation;
    private final Map<String, Datacenter> datacenters;
    private final Map<String, DatacenterBroker> brokers;
    private final Map<String, List<Vm>> vmsByDatacenter;
    
    // Provider and region names
    public static final String[] PROVIDERS = {"Google", "AWS", "Azure"};
    public static final String[] REGIONS = {"US", "EU", "AS"};
    public static final String[] SUB_REGIONS = {"sub_region1", "sub_region2"};
    public static final int DC_PER_SUBREGION = 2;
    public static final int VMS_PER_DC = 2;
    
    // Host configuration (1 host per DC, small setup)
    private static final int HOSTS_PER_DC = 1;
    private static final int HOST_PES = 8;
    private static final long HOST_MIPS = 10000;
    private static final long HOST_RAM = 16384;  // 16 GB
    private static final long HOST_BW = 10000;  // 10 Gbps
    private static final long HOST_STORAGE = 1000000; // 1 TB
    
    // VM configuration (small VMs)
    private static final int VM_PES = 2;
    private static final long VM_MIPS = 2500;
    private static final long VM_RAM = 4096;  // 4 GB
    private static final long VM_BW = 1000;  // 1 Gbps
    private static final long VM_SIZE = 50000; // 50 GB

    public MultiCloudInfrastructure() {
        this.simulation = new CloudSimPlus();
        this.datacenters = new HashMap<>();
        this.brokers = new HashMap<>();
        this.vmsByDatacenter = new HashMap<>();
        
        createInfrastructure();
    }

    /**
     * Crée l'infrastructure multi-cloud complète avec CloudSimPlus.
     * Architecture: 3 providers × 3 régions × 2 sub-regions × 2 DC = 36 datacenters
     */
    private void createInfrastructure() {
        int totalDCs = 0;
        int totalVMs = 0;
        
        for (String provider : PROVIDERS) {
            // Un broker par provider
            DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
            brokers.put(provider, broker);
            
            for (String region : REGIONS) {
                for (String subRegion : SUB_REGIONS) {
                    for (int dcIdx = 0; dcIdx < DC_PER_SUBREGION; dcIdx++) {
                        String dcName = String.format("%s_%s_%s_DC%d", 
                            provider, region, subRegion, dcIdx + 1);
                        
                        // Créer le datacenter
                        Datacenter dc = createDatacenter(dcName);
                        datacenters.put(dcName, dc);
                        totalDCs++;
                        
                        // Créer les VMs (2 par DC)
                        List<Vm> vms = createVms(VMS_PER_DC, dcName);
                        vmsByDatacenter.put(dcName, vms);
                        broker.submitVmList(vms);
                        totalVMs += VMS_PER_DC;
                    }
                }
            }
        }
        
        System.out.printf("  [CloudSimPlus] %d datacenters créés (%d providers × %d régions × %d sub-regions × %d DC)%n",
            totalDCs, PROVIDERS.length, REGIONS.length, SUB_REGIONS.length, DC_PER_SUBREGION);
        System.out.printf("  [CloudSimPlus] %d VMs total (%d par datacenter)%n",
            totalVMs, VMS_PER_DC);
    }

    private Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS_PER_DC; i++) {
            hostList.add(createHost());
        }
        
        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
        
        // Configurer les coûts selon Paper Table 1
        dc.getCharacteristics()
            .setCostPerSecond(TcdrmConstants.CPU_COST_PER_10M_MI / 3600.0)
            .setCostPerMem(0.001)
            .setCostPerStorage(TcdrmConstants.STORAGE_COST_PER_GB_PER_MONTH / (30 * 24 * 3600))
            .setCostPerBw(TcdrmConstants.COST_BW_INTER_PROVIDER);
        
        return dc;
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    private List<Vm> createVms(int count, String dcName) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_SIZE);
            vm.setDescription(dcName + "_VM" + i);
            vmList.add(vm);
        }
        return vmList;
    }

    // === Getters ===
    
    public CloudSimPlus getSimulation() {
        return simulation;
    }
    
    public Datacenter getDatacenter(String provider, String region) {
        return datacenters.get(provider + "_" + region);
    }
    
    public Datacenter getDatacenter(String provider, String region, String subRegion, int dcIdx) {
        String dcName = String.format("%s_%s_%s_DC%d", provider, region, subRegion, dcIdx + 1);
        return datacenters.get(dcName);
    }
    
    public DatacenterBroker getBroker(String provider) {
        return brokers.get(provider);
    }
    
    public List<Vm> getVms(String provider, String region) {
        return vmsByDatacenter.get(provider + "_" + region);
    }
    
    public List<Vm> getVms(String provider, String region, String subRegion, int dcIdx) {
        String dcName = String.format("%s_%s_%s_DC%d", provider, region, subRegion, dcIdx + 1);
        return vmsByDatacenter.get(dcName);
    }
    
    public Vm getRandomVm(String provider, String region, Random rnd) {
        List<Vm> vms = getVms(provider, region);
        return vms.get(rnd.nextInt(vms.size()));
    }
    
    public Map<String, Datacenter> getAllDatacenters() {
        return datacenters;
    }

    // === Network metrics based on location ===
    
    public double getLatencyMs(String srcProvider, String srcRegion, String dstProvider, String dstRegion) {
        if (srcProvider.equals(dstProvider)) {
            if (srcRegion.equals(dstRegion)) {
                return TcdrmConstants.LAT_INTRA_DC_MS;
            }
            return TcdrmConstants.LAT_INTER_REGION_MS;
        }
        return TcdrmConstants.LAT_INTER_PROVIDER_MS;
    }
    
    public double getLatencyMs(String srcDC, String dstDC) {
        String[] srcParts = srcDC.split("_");
        String[] dstParts = dstDC.split("_");
        
        String srcProvider = srcParts[0];
        String srcRegion = srcParts[1];
        String srcSubRegion = srcParts.length > 2 ? srcParts[2] : "";
        
        String dstProvider = dstParts[0];
        String dstRegion = dstParts[1];
        String dstSubRegion = dstParts.length > 2 ? dstParts[2] : "";
        
        // Même DC
        if (srcDC.equals(dstDC)) {
            return TcdrmConstants.LAT_INTRA_DC_MS;
        }
        
        // Même sub-region (différents DC)
        if (srcProvider.equals(dstProvider) && srcRegion.equals(dstRegion) && srcSubRegion.equals(dstSubRegion)) {
            return 5.0; // Intra-subregion latency
        }
        
        // Même région, différentes sub-regions
        if (srcProvider.equals(dstProvider) && srcRegion.equals(dstRegion)) {
            return 15.0; // Inter-subregion latency
        }
        
        // Même provider, différentes régions
        if (srcProvider.equals(dstProvider)) {
            return TcdrmConstants.LAT_INTER_REGION_MS;
        }
        
        // Différents providers
        return TcdrmConstants.LAT_INTER_PROVIDER_MS;
    }
    
    public double getBandwidthGbps(String srcProvider, String srcRegion, String dstProvider, String dstRegion) {
        if (srcProvider.equals(dstProvider)) {
            if (srcRegion.equals(dstRegion)) {
                return TcdrmConstants.BW_INTRA_DC_GBPS;
            }
            return TcdrmConstants.BW_INTER_REGION_GBPS;
        }
        return TcdrmConstants.BW_INTER_PROVIDER_GBPS;
    }
    
    public double getBandwidthCostPerGb(String srcProvider, String srcRegion, String dstProvider, String dstRegion) {
        if (srcProvider.equals(dstProvider)) {
            if (srcRegion.equals(dstRegion)) {
                return TcdrmConstants.COST_BW_INTRA_DC;
            }
            return TcdrmConstants.COST_BW_INTER_REGION;
        }
        return TcdrmConstants.COST_BW_INTER_PROVIDER;
    }

    /**
     * Calcule le temps de transfert pour une quantité de données.
     */
    public double computeTransferTimeMs(double dataGb, String srcProvider, String srcRegion, 
                                         String dstProvider, String dstRegion, Random rnd) {
        double latencyMs = getLatencyMs(srcProvider, srcRegion, dstProvider, dstRegion);
        double bwGbps = getBandwidthGbps(srcProvider, srcRegion, dstProvider, dstRegion);
        
        // Ajouter du jitter réaliste
        double jitter = 1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1);
        double latencyJitter = 1.0 + TcdrmConstants.LATENCY_VARIATION_RATIO * (rnd.nextDouble() * 2 - 1);
        
        // Temps = latence + (data / bandwidth)
        double transferMs = (latencyMs * latencyJitter) + (dataGb * 8000.0 / bwGbps) * jitter;
        return transferMs;
    }
}
