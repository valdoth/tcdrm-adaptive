package org.tcdrm.adaptive.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NoRepBenchmark {
    private static final int MIN_REPS = 500;
    private static final int MAX_REPS = 15000;
    private static final int STEP_REPS = 500;

    private static final double BW_REMOTE_GBPS = 1.0;
    private static final double LAT_REMOTE_MS = 100.0;  // Aligné avec les autres benchmarks
    private static final double COST_BW_INTER_PROVIDER = 0.01;  // Article Tableau 1

    private static final double CPU_COST_PER_HOUR = 0.02;
    private static final double STORAGE_COST_PER_GB_PER_MONTH = 0.02;
    private static final double PROCESSING_MIN_PER_GB = 0.5;

    private static final double JITTER_RATIO = 0.05;
    private static final double CPU_JITTER_RATIO = 0.05;
    private static final double LOAD_MAX_FACTOR = 1.6;

    private final Random rnd;

    public NoRepBenchmark(long seed) {
        this.rnd = new Random(seed);
    }

    public BenchmarkData computeBenchmark(String queryId, List<Double> fragmentSizesGb) {
        List<Integer> repetitions = new ArrayList<>();
        List<Double> totalTimeMs = new ArrayList<>();
        List<Double> totalBwCost = new ArrayList<>();
        List<Double> totalCpuCost = new ArrayList<>();
        List<Double> totalStorageCost = new ArrayList<>();

        for (int reps = MIN_REPS; reps <= MAX_REPS; reps += STEP_REPS) {
            double loadFactor = 1.0 + (LOAD_MAX_FACTOR - 1.0) * ((reps - MIN_REPS) / (double) (MAX_REPS - MIN_REPS));

            double accTimeMs = 0.0;
            double accBwCost = 0.0;
            double accCpuCost = 0.0;
            double accStorageCost = 0.0;

            for (int i = 0; i < reps; i++) {
                double bwGbps = BW_REMOTE_GBPS / loadFactor;
                double latencyMs = LAT_REMOTE_MS * loadFactor;
                double costPerGb = COST_BW_INTER_PROVIDER;

                double dataGb = fragmentSizesGb.stream().mapToDouble(d -> d).sum();
                double transferMs = (dataGb * 8_000.0 / bwGbps) + latencyMs;
                transferMs *= (1.0 + JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
                double transferCost = dataGb * costPerGb;

                double processingMin = dataGb * PROCESSING_MIN_PER_GB;
                processingMin *= (1.0 + CPU_JITTER_RATIO * (rnd.nextDouble() * 2 - 1));
                double cpuCost = (processingMin / 60.0) * CPU_COST_PER_HOUR;
                double storageCost = 0.0;

                accTimeMs += transferMs + processingMin * 60_000.0;
                accBwCost += transferCost;
                accCpuCost += cpuCost;
                accStorageCost += storageCost;
            }

            repetitions.add(reps);
            totalTimeMs.add(accTimeMs);
            totalBwCost.add(accBwCost);
            totalCpuCost.add(accCpuCost);
            totalStorageCost.add(accStorageCost);
        }

        return new BenchmarkData(repetitions, totalTimeMs, totalBwCost, totalCpuCost, totalStorageCost);
    }
}
