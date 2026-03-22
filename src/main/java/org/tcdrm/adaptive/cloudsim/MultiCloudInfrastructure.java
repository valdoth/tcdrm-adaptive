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
 * Architecture (Paper Section 3.1):
 * - 3 providers (Google, AWS, Azure)
 * - 3 régions par provider (US, EU, AS)
 * - 20 VMs par région
 * 
 * Modèle réseau:
 * - Intra-DC: 10 Gbps, 1ms latency
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
    
    // Host configuration (powerful servers)
    private static final int HOSTS_PER_DC = 5;
    private static final int HOST_PES = 32;
    private static final long HOST_MIPS = 20000;
    private static final long HOST_RAM = 65536;  // 64 GB
    private static final long HOST_BW = 100000;  // 100 Gbps internal
    private static final long HOST_STORAGE = 10000000; // 10 TB
    
    // VM configuration
    private static final int VM_PES = 4;
    private static final long VM_MIPS = 5000;
    private static final long VM_RAM = 8192;  // 8 GB
    private static final long VM_BW = 10000;  // 10 Gbps
    private static final long VM_SIZE = 100000; // 100 GB

    public MultiCloudInfrastructure() {
        this.simulation = new CloudSimPlus();
        this.datacenters = new HashMap<>();
        this.brokers = new HashMap<>();
        this.vmsByDatacenter = new HashMap<>();
        
        createInfrastructure();
    }

    /**
     * Crée l'infrastructure multi-cloud complète avec CloudSimPlus.
     */
    private void createInfrastructure() {
        for (String provider : PROVIDERS) {
            // Un broker par provider
            DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
            brokers.put(provider, broker);
            
            for (String region : REGIONS) {
                String dcName = provider + "_" + region;
                
                // Créer le datacenter
                Datacenter dc = createDatacenter(dcName);
                datacenters.put(dcName, dc);
                
                // Créer les VMs
                List<Vm> vms = createVms(TcdrmConstants.NUM_VMS_PER_REGION, dcName);
                vmsByDatacenter.put(dcName, vms);
                broker.submitVmList(vms);
            }
        }
        
        System.out.printf("  [CloudSimPlus] %d datacenters créés (%d providers x %d régions)%n",
            datacenters.size(), PROVIDERS.length, REGIONS.length);
        System.out.printf("  [CloudSimPlus] %d VMs total (%d par datacenter)%n",
            datacenters.size() * TcdrmConstants.NUM_VMS_PER_REGION, TcdrmConstants.NUM_VMS_PER_REGION);
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
    
    public DatacenterBroker getBroker(String provider) {
        return brokers.get(provider);
    }
    
    public List<Vm> getVms(String provider, String region) {
        return vmsByDatacenter.get(provider + "_" + region);
    }
    
    public Vm getRandomVm(String provider, String region, Random rnd) {
        List<Vm> vms = getVms(provider, region);
        return vms.get(rnd.nextInt(vms.size()));
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
