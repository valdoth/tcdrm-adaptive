package org.tcdrm.adaptive.cloudsim;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.tcdrm.adaptive.core.TcdrmConstants;

import java.util.*;

/**
 * Infrastructure multi-cloud CloudSimPlus pour TCDRM-Adaptive.
 *
 * <p><b>Brokers : un {@link DatacenterBroker} par datacenter</b>, chacun ne soumet que ses VMs
 * locales — sinon un broker par provider envoie toutes les VMs vers le premier DC et le placement
 * échoue.</p>
 *
 * <p>Le moteur est lancé en mode synchrone ({@link CloudSimPlus#startSync()} puis
 * {@link CloudSimPlus#runFor(double)}) jusqu'à ce que toutes les VMs soient créées ; les requêtes
 * ({@link QueryCloudlet}) avancent ensuite l'horloge en soumettant des cloudlets sur une VM du site
 * d'exécution (ex. Google / US).</p>
 */
public class MultiCloudInfrastructure {

    public static final double MIN_TIME_BETWEEN_EVENTS = 0.01;

    private final CloudSimPlus simulation;
    private final Map<String, Datacenter> datacenters;
    /** Un broker par identifiant de datacenter (ex. {@code Google_US_sub_region1_DC1}). */
    private final Map<String, DatacenterBroker> brokerByDatacenter;
    private final Map<String, List<Vm>> vmsByDatacenter;

    public static final String[] PROVIDERS = {"Google", "AWS", "Azure"};
    public static final String[] REGIONS = {"US", "EU", "AS"};
    public static final String[] SUB_REGIONS = {"sub_region1", "sub_region2"};
    public static final int DC_PER_SUBREGION = 2;
    public static final int VMS_PER_DC = 2;

    private static final int HOSTS_PER_DC = 1;
    private static final int HOST_PES = 8;
    private static final long HOST_MIPS = 10000;
    private static final long HOST_RAM = 16384;
    private static final long HOST_BW = 10000;
    private static final long HOST_STORAGE = 1000000;

    private static final int VM_PES = 2;
    /** MIPS (Millions of Instructions Per Second) — exposé pour calibrer la longueur des cloudlets requête. */
    public static final long VM_MIPS = 2500;
    private static final long VM_RAM = 4096;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 50000;

    private static final double BOOTSTRAP_RUN_SLICE = 0.05;
    private static final int BOOTSTRAP_MAX_STEPS = 500_000;

    public MultiCloudInfrastructure() {
        this.simulation = new CloudSimPlus(MIN_TIME_BETWEEN_EVENTS);
        this.datacenters = new LinkedHashMap<>();
        this.brokerByDatacenter = new LinkedHashMap<>();
        this.vmsByDatacenter = new LinkedHashMap<>();
        createInfrastructure();
    }

    private void createInfrastructure() {
        int totalDCs = 0;
        int totalVMs = 0;

        for (String provider : PROVIDERS) {
            for (String region : REGIONS) {
                for (String subRegion : SUB_REGIONS) {
                    for (int dcIdx = 0; dcIdx < DC_PER_SUBREGION; dcIdx++) {
                        String dcName = String.format("%s_%s_%s_DC%d",
                            provider, region, subRegion, dcIdx + 1);

                        final Datacenter dc = createDatacenter(dcName);
                        datacenters.put(dcName, dc);

                        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
                        broker.setShutdownWhenIdle(false);
                        // Sinon le broker tente le premier DC global : VMs rejetées (« No suitable host »).
                        broker.setDatacenterMapper((candidate, vm) -> dc);
                        brokerByDatacenter.put(dcName, broker);

                        List<Vm> vms = createVms(VMS_PER_DC, dcName);
                        vmsByDatacenter.put(dcName, vms);
                        broker.submitVmList(vms);
                        totalDCs++;
                        totalVMs += VMS_PER_DC;
                    }
                }
            }
        }

        System.out.printf("  [CloudSimPlus] %d datacenters créés (%d providers × %d régions × %d sub-regions × %d DC)%n",
            totalDCs, PROVIDERS.length, REGIONS.length, SUB_REGIONS.length, DC_PER_SUBREGION);
        System.out.printf("  [CloudSimPlus] %d VMs total (%d par datacenter)%n",
            totalVMs, VMS_PER_DC);

        bootstrapVmPlacement();
    }

    /**
     * Place toutes les VMs localement puis laisse la simulation dans un état où l'on peut soumettre
     * des cloudlets (horloge synchrone).
     */
    private void bootstrapVmPlacement() {
        simulation.startSync();
        int steps = 0;
        while (simulation.isRunning() && steps < BOOTSTRAP_MAX_STEPS) {
            if (allBrokersHaveVmsReady()) {
                break;
            }
            simulation.runFor(BOOTSTRAP_RUN_SLICE);
            steps++;
        }
        if (!allBrokersHaveVmsReady()) {
            throw new IllegalStateException(
                "CloudSim bootstrap : toutes les VMs n'ont pas été créées (timeout).");
        }
        System.out.printf("  [CloudSimPlus] startSync + runFor : VMs prêtes (clock=%.4f s, steps=%d)%n",
            simulation.clock(), steps);
    }

    private boolean allBrokersHaveVmsReady() {
        for (DatacenterBroker b : brokerByDatacenter.values()) {
            int n = b.getVmCreatedList().size();
            if (n < VMS_PER_DC) {
                return false;
            }
        }
        return true;
    }

    /**
     * Exécute un cloudlet sur une VM du DC d'exécution choisi (premier DC lexicographic du couple provider/région).
     * Avance l'horloge CloudSim jusqu'à fin SUCCESS (ou abandon).
     *
     * @return durée wall-clock simulée entre début et fin d'exécution du cloudlet (secondes), ou 0 si échec
     */
    public double runCloudletToCompletion(Cloudlet cloudlet, String execProvider, String execRegion, Random rnd) {
        DatacenterBroker broker = getBrokerForExecution(execProvider, execRegion);
        List<Vm> candidates = new ArrayList<>(broker.getVmExecList());
        if (candidates.isEmpty()) {
            candidates.addAll(broker.getVmCreatedList());
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        // Sélection BEST-FIT (politique native CloudSimPlus, cf. DatacenterBrokerBestFit) :
        // la VM avec le plus de PEs attendus libres reçoit le cloudlet — au lieu d'un
        // tirage aléatoire. Réduit l'attente quand plusieurs requêtes se chevauchent.
        Vm vm = candidates.get(0);
        for (Vm v : candidates) {
            if (v.getExpectedFreePesNumber() > vm.getExpectedFreePesNumber()) vm = v;
        }
        cloudlet.setBroker(broker);
        cloudlet.setVm(vm);
        broker.submitCloudletList(Collections.singletonList(cloudlet), vm);

        final int maxSteps = 200_000;
        int step = 0;
        while (step < maxSteps) {
            Cloudlet.Status st = cloudlet.getStatus();
            if (st == Cloudlet.Status.SUCCESS || st == Cloudlet.Status.FAILED) {
                break;
            }
            if (!simulation.isRunning()) {
                break;
            }
            simulation.runFor(0.1);
            step++;
        }

        if (cloudlet.getStatus() == Cloudlet.Status.SUCCESS && cloudlet.getFinishTime() >= cloudlet.getStartTime()) {
            return cloudlet.getFinishTime() - cloudlet.getStartTime();
        }
        return 0;
    }

    /**
     * Premier DC du couple (ex. Google, US) → broker dédié (même préfixe que {@link #getVms}).
     */
    public DatacenterBroker getBrokerForExecution(String provider, String region) {
        String prefix = provider + "_" + region + "_";
        return datacenters.keySet().stream()
            .filter(k -> k.startsWith(prefix))
            .sorted()
            .findFirst()
            .map(brokerByDatacenter::get)
            .orElseThrow(() -> new IllegalStateException("Aucun DC pour " + provider + "/" + region));
    }

    private Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < HOSTS_PER_DC; i++) {
            hostList.add(createHost());
        }
        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
        dc.setName(name);

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
        Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
        host.setVmScheduler(new VmSchedulerSpaceShared());
        return host;
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

    public CloudSimPlus getSimulation() {
        return simulation;
    }

    public Datacenter getDatacenter(String provider, String region) {
        String prefix = provider + "_" + region + "_";
        for (Map.Entry<String, Datacenter> e : datacenters.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                return e.getValue();
            }
        }
        return null;
    }

    public Datacenter getDatacenter(String provider, String region, String subRegion, int dcIdx) {
        String dcName = String.format("%s_%s_%s_DC%d", provider, region, subRegion, dcIdx + 1);
        return datacenters.get(dcName);
    }

    /** @deprecated Préférer {@link #getBrokerForExecution(String, String)} */
    @Deprecated
    public DatacenterBroker getBroker(String provider) {
        return getBrokerForExecution(provider, "US");
    }

    public List<Vm> getVms(String provider, String region) {
        String prefix = provider + "_" + region + "_";
        List<Vm> all = new ArrayList<>();
        for (Map.Entry<String, List<Vm>> e : vmsByDatacenter.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                all.addAll(e.getValue());
            }
        }
        return all;
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

    public Map<String, DatacenterBroker> getBrokerByDatacenter() {
        return Collections.unmodifiableMap(brokerByDatacenter);
    }

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

        if (srcDC.equals(dstDC)) {
            return TcdrmConstants.LAT_INTRA_DC_MS;
        }

        if (srcProvider.equals(dstProvider) && srcRegion.equals(dstRegion) && srcSubRegion.equals(dstSubRegion)) {
            return 5.0;
        }

        if (srcProvider.equals(dstProvider) && srcRegion.equals(dstRegion)) {
            return 15.0;
        }

        if (srcProvider.equals(dstProvider)) {
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

    public double computeTransferTimeMs(double dataGb, String srcProvider, String srcRegion,
                                         String dstProvider, String dstRegion, Random rnd) {
        double latencyMs = getLatencyMs(srcProvider, srcRegion, dstProvider, dstRegion);
        double bwGbps = getBandwidthGbps(srcProvider, srcRegion, dstProvider, dstRegion);

        double jitter = 1.0 + TcdrmConstants.JITTER_RATIO * (rnd.nextDouble() * 2 - 1);
        double latencyJitter = 1.0 + TcdrmConstants.LATENCY_VARIATION_RATIO * (rnd.nextDouble() * 2 - 1);

        return (latencyMs * latencyJitter) + (dataGb * 8000.0 / bwGbps) * jitter;
    }
}
