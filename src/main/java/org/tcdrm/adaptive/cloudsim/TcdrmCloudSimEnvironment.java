package org.tcdrm.adaptive.cloudsim;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.tcdrm.adaptive.rl.TcdrmAction;
import org.tcdrm.adaptive.rl.TcdrmState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Environnement TCDRM intégré avec CloudSimPlus
 * Permet d'utiliser les modèles Q-Learning dans des simulations cloud réalistes
 */
public class TcdrmCloudSimEnvironment {
    
    // CloudSim components
    private CloudSimPlus simulation;
    private List<Datacenter> datacenters;
    private DatacenterBroker broker;
    private List<Vm> vms;
    private List<Cloudlet> cloudlets;
    
    // TCDRM parameters
    private final double dataGb;
    private double currentBudget;
    private final double initialBudget;
    private int currentReplicaCount;
    private int accessCount;
    private int currentQuery;
    
    // Costs (from TCDRM paper)
    private static final double COST_BW_INTRA_DC = 0.002;
    private static final double COST_BW_INTER_PROVIDER = 0.10;
    private static final double STORAGE_COST_PER_GB_PER_HOUR = 0.02 / 720.0;
    private static final double REPLICATION_COST_PER_GB = COST_BW_INTER_PROVIDER;
    private static final double SLA_LATENCY_THRESHOLD = 150.0;
    private static final int MAX_QUERIES = 1000;
    private static final int MAX_REPLICAS = 3;
    
    // Network parameters
    private static final double BW_LOCAL_GBPS = 10.0;
    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_LOCAL_MS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;
    
    // CloudSim metrics
    private Map<Integer, Double> queryLatencies;
    private Map<Integer, Double> queryCosts;
    private Map<Integer, Integer> replicaCounts;
    private double totalCost;
    private double totalLatency;
    
    public TcdrmCloudSimEnvironment(double dataGb) {
        this.dataGb = dataGb;
        this.initialBudget = 100.0;
        this.currentBudget = initialBudget;
        this.currentReplicaCount = 0;
        this.accessCount = 0;
        this.currentQuery = 0;
        this.totalCost = 0.0;
        this.totalLatency = 0.0;
        
        this.queryLatencies = new HashMap<>();
        this.queryCosts = new HashMap<>();
        this.replicaCounts = new HashMap<>();
        
        initializeCloudSim();
    }
    
    /**
     * Initialise la simulation CloudSim avec datacenters, hosts, VMs
     */
    private void initializeCloudSim() {
        simulation = new CloudSimPlus();
        datacenters = new ArrayList<>();
        vms = new ArrayList<>();
        cloudlets = new ArrayList<>();
        
        // Créer 3 datacenters (pour simuler multi-cloud)
        for (int i = 0; i < 3; i++) {
            Datacenter dc = createDatacenter("DC_" + i);
            datacenters.add(dc);
        }
        
        // Créer le broker
        broker = new DatacenterBrokerSimple(simulation);
        
        // Créer les VMs (une par datacenter initialement)
        for (int i = 0; i < 1; i++) {
            Vm vm = createVm(i);
            vms.add(vm);
        }
        broker.submitVmList(vms);
    }
    
    /**
     * Crée un datacenter CloudSim
     */
    private Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        
        // Créer 5 hosts par datacenter
        for (int i = 0; i < 5; i++) {
            Host host = createHost();
            hostList.add(host);
        }
        
        Datacenter dc = new DatacenterSimple(simulation, hostList);
        dc.setName(name);
        
        // Configuration des coûts
        dc.getCharacteristics()
            .setCostPerSecond(0.01)
            .setCostPerMem(0.02)
            .setCostPerStorage(0.001)
            .setCostPerBw(COST_BW_INTRA_DC);
        
        return dc;
    }
    
    /**
     * Crée un host CloudSim
     */
    private Host createHost() {
        List<Pe> peList = new ArrayList<>();
        
        // 4 CPU cores par host
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(1000)); // 1000 MIPS
        }
        
        long ram = 8192; // 8 GB
        long storage = 1000000; // 1 TB
        long bw = 10000; // 10 Gbps
        
        return new HostSimple(ram, bw, storage, peList);
    }
    
    /**
     * Crée une VM CloudSim
     */
    private Vm createVm(int id) {
        long mips = 1000;
        long size = 10000; // 10 GB
        int ram = 2048; // 2 GB
        long bw = 1000; // 1 Gbps
        int pesNumber = 2; // 2 CPU cores
        
        return new VmSimple(id, mips, pesNumber)
            .setRam(ram)
            .setBw(bw)
            .setSize(size);
    }
    
    /**
     * Crée un cloudlet (tâche de requête)
     */
    private Cloudlet createCloudlet(int id, boolean useLocalReplica) {
        long length = (long) (dataGb * 1000); // Proportionnel à la taille des données
        long fileSize = (long) (dataGb * 1024); // MB
        long outputSize = 300;
        int pesNumber = 1;
        
        UtilizationModelDynamic utilizationModel = new UtilizationModelDynamic(0.5);
        
        Cloudlet cloudlet = new CloudletSimple(id, length, pesNumber)
            .setFileSize(fileSize)
            .setOutputSize(outputSize)
            .setUtilizationModelCpu(utilizationModel)
            .setUtilizationModelRam(utilizationModel)
            .setUtilizationModelBw(utilizationModel);
        
        return cloudlet;
    }
    
    /**
     * Reset l'environnement
     */
    public TcdrmState reset(Long seed) {
        currentBudget = initialBudget;
        currentReplicaCount = 0;
        accessCount = 0;
        currentQuery = 0;
        totalCost = 0.0;
        totalLatency = 0.0;
        
        queryLatencies.clear();
        queryCosts.clear();
        replicaCounts.clear();
        
        // Réinitialiser CloudSim
        simulation = new CloudSimPlus();
        initializeCloudSim();
        
        return getCurrentState();
    }
    
    /**
     * Exécute une action (CREATE, DELETE, DO_NOTHING)
     */
    public StepResult step(TcdrmAction action) {
        int previousReplicaCount = currentReplicaCount;
        
        // Exécuter l'action
        boolean actionExecuted = executeAction(action);
        
        // Simuler une requête avec CloudSim
        double queryLatency = simulateQueryWithCloudSim();
        double queryCost = calculateQueryCost();
        
        // Mettre à jour l'état
        currentBudget -= queryCost;
        accessCount++;
        currentQuery++;
        
        totalCost += queryCost;
        totalLatency += queryLatency;
        
        // Enregistrer les métriques
        queryLatencies.put(currentQuery, queryLatency);
        queryCosts.put(currentQuery, queryCost);
        replicaCounts.put(currentQuery, currentReplicaCount);
        
        // Calculer la récompense
        double reward = calculateReward(action, actionExecuted, previousReplicaCount, 
                                       queryCost, queryLatency);
        
        // Vérifier si terminé
        boolean terminated = currentQuery >= MAX_QUERIES;
        boolean truncated = currentBudget <= 0;
        
        TcdrmState nextState = getCurrentState();
        
        String info = String.format(
            "Query %d: Action=%s, Cost=%.4f, Budget=%.2f, Latency=%.2f ms, Replicas=%d",
            currentQuery, action, queryCost, currentBudget, queryLatency, currentReplicaCount
        );
        
        return new StepResult(nextState, reward, terminated, truncated, info);
    }
    
    /**
     * Exécute l'action de réplication
     */
    private boolean executeAction(TcdrmAction action) {
        switch (action) {
            case CREATE_REPLICA:
                if (currentReplicaCount < MAX_REPLICAS) {
                    double creationCost = dataGb * REPLICATION_COST_PER_GB;
                    if (currentBudget >= creationCost) {
                        currentReplicaCount++;
                        currentBudget -= creationCost;
                        
                        // Créer une nouvelle VM dans CloudSim
                        Vm newVm = createVm(vms.size());
                        vms.add(newVm);
                        broker.submitVm(newVm);
                        
                        return true;
                    }
                }
                return false;
                
            case DELETE_REPLICA:
                if (currentReplicaCount > 0) {
                    currentReplicaCount--;
                    
                    // Supprimer une VM dans CloudSim
                    if (!vms.isEmpty()) {
                        Vm vmToRemove = vms.remove(vms.size() - 1);
                        vmToRemove.setFailed(true);
                    }
                    
                    return true;
                }
                return false;
                
            case DO_NOTHING:
            default:
                return true;
        }
    }
    
    /**
     * Simule une requête avec CloudSim et retourne la latence
     */
    private double simulateQueryWithCloudSim() {
        double latency;
        
        if (currentReplicaCount > 0) {
            // Probabilité d'accès local basée sur le nombre de réplicas
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = Math.random() < localProbability;
            
            if (useLocal) {
                // Accès local: latence faible
                latency = LAT_LOCAL_MS + (Math.random() * 2 - 1) * 0.5;
                
                // Créer et soumettre un cloudlet local
                Cloudlet cloudlet = createCloudlet(cloudlets.size(), true);
                cloudlets.add(cloudlet);
                broker.submitCloudlet(cloudlet);
            } else {
                // Accès distant: latence élevée
                latency = LAT_REMOTE_MS * (1.0 + (Math.random() * 2 - 1) * 0.15);
                
                Cloudlet cloudlet = createCloudlet(cloudlets.size(), false);
                cloudlets.add(cloudlet);
                broker.submitCloudlet(cloudlet);
            }
        } else {
            // Pas de réplica: toujours distant
            latency = LAT_REMOTE_MS * (1.0 + (Math.random() * 2 - 1) * 0.15);
            
            Cloudlet cloudlet = createCloudlet(cloudlets.size(), false);
            cloudlets.add(cloudlet);
            broker.submitCloudlet(cloudlet);
        }
        
        return latency;
    }
    
    /**
     * Calcule le coût de la requête
     */
    private double calculateQueryCost() {
        double transferCost;
        
        if (currentReplicaCount > 0) {
            double localProbability = (double) currentReplicaCount / (currentReplicaCount + 2);
            boolean useLocal = Math.random() < localProbability;
            transferCost = dataGb * (useLocal ? COST_BW_INTRA_DC : COST_BW_INTER_PROVIDER);
        } else {
            transferCost = dataGb * COST_BW_INTER_PROVIDER;
        }
        
        // Coût de stockage
        double storageCost = currentReplicaCount * dataGb * STORAGE_COST_PER_GB_PER_HOUR;
        
        return transferCost + storageCost;
    }
    
    /**
     * Calcule la récompense (identique à TcdrmEnvironment)
     */
    private double calculateReward(TcdrmAction action, boolean actionExecuted,
                                   int previousReplicaCount, double queryCost, double queryLatency) {
        double reward = 0.0;
        
        // Bonus SLA
        if (queryLatency < SLA_LATENCY_THRESHOLD) {
            reward += 5.0;
        }
        
        // Économies de bande passante
        if (currentReplicaCount > 0 && queryLatency < LAT_REMOTE_MS) {
            double savings = dataGb * (COST_BW_INTER_PROVIDER - COST_BW_INTRA_DC);
            reward += savings * 10.0;
        }
        
        // Pénalités budget
        if (currentBudget < initialBudget * 0.2) {
            reward -= 20.0;
        }
        if (currentBudget <= 0) {
            reward -= 100.0;
        }
        
        // Pénalité latence
        if (queryLatency > SLA_LATENCY_THRESHOLD) {
            double violation = (queryLatency - SLA_LATENCY_THRESHOLD) / SLA_LATENCY_THRESHOLD;
            reward -= 10.0 * violation;
        }
        
        // Pénalité action non exécutée
        if (!actionExecuted && action != TcdrmAction.DO_NOTHING) {
            reward -= 5.0;
        }
        
        // Pénalité trop de réplicas
        if (currentReplicaCount > 2) {
            reward -= 2.0 * (currentReplicaCount - 2);
        }
        
        return reward;
    }
    
    /**
     * Retourne l'état actuel
     */
    private TcdrmState getCurrentState() {
        double budgetRatio = currentBudget / initialBudget;
        return TcdrmState.fromContinuous(budgetRatio, totalLatency / Math.max(currentQuery, 1), 
                                        accessCount, currentReplicaCount);
    }
    
    /**
     * Exécute la simulation CloudSim complète
     */
    public void runSimulation() {
        simulation.start();
    }
    
    /**
     * Affiche les résultats CloudSim
     */
    public void printResults() {
        System.out.println("\n=== Résultats CloudSim ===");
        System.out.println("Requêtes totales: " + currentQuery);
        System.out.println("Coût total: $" + String.format("%.2f", totalCost));
        System.out.println("Latence moyenne: " + String.format("%.2f", totalLatency / currentQuery) + " ms");
        System.out.println("Budget restant: $" + String.format("%.2f", currentBudget));
        System.out.println("Réplicas finaux: " + currentReplicaCount);
        
        // Afficher les cloudlets
        if (!cloudlets.isEmpty()) {
            new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        }
    }
    
    // Getters
    public Map<Integer, Double> getQueryLatencies() { return queryLatencies; }
    public Map<Integer, Double> getQueryCosts() { return queryCosts; }
    public Map<Integer, Integer> getReplicaCounts() { return replicaCounts; }
    public double getTotalCost() { return totalCost; }
    public double getAverageLatency() { return totalLatency / Math.max(currentQuery, 1); }
    public CloudSimPlus getSimulation() { return simulation; }
    public List<Vm> getVms() { return vms; }
    public List<Cloudlet> getCloudlets() { return cloudlets; }
    
    /**
     * Classe pour les résultats d'un step
     */
    public static class StepResult {
        private final TcdrmState nextState;
        private final double reward;
        private final boolean terminated;
        private final boolean truncated;
        private final String info;
        
        public StepResult(TcdrmState nextState, double reward, boolean terminated, 
                         boolean truncated, String info) {
            this.nextState = nextState;
            this.reward = reward;
            this.terminated = terminated;
            this.truncated = truncated;
            this.info = info;
        }
        
        public TcdrmState getNextState() { return nextState; }
        public double getReward() { return reward; }
        public boolean isTerminated() { return terminated; }
        public boolean isTruncated() { return truncated; }
        public boolean isDone() { return terminated || truncated; }
        public String getInfo() { return info; }
    }
}
