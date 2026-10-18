package org.tcdrm.adaptive.benchmark;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility to export BenchmarkData metrics to CSV files, inspired by the
 * legacy Simulation examples. Generates per-query logs and optional
 * windowed averages for overtime analysis.
 */
public final class BenchmarkExporter {
    private BenchmarkExporter() {}

    /**
     * Exports per-query metrics for a model to CSV.
     * Columns: query,response_time_ms,cost_bw,cumulative_cost,replicas,
     *          bw_inter_provider_gb,bw_inter_region_gb,cpu_cost,replica_cost,
     *          total_cost,sla_violations_cumul,avg_bw_price
     */
    public static void exportPerQueryCsv(BenchmarkData data, String filePath) throws IOException {
        ensureParentDir(filePath);
        try (FileWriter out = new FileWriter(filePath, false)) {
            out.append("query,response_time_ms,cost_bw,cumulative_cost,replicas,"
                + "bw_inter_provider_gb,bw_inter_region_gb,cpu_cost,replica_cost,total_cost,"
                + "sla_violations_cumul,avg_bw_price\n");
            int n = data.getQueryNumbers().size();
            for (int i = 0; i < n; i++) {
                int q = data.getQueryNumbers().get(i);
                double rt = data.getResponseTimeMs().get(i);
                double cbw = data.getCostPerQuery().get(i);
                double cc = data.getCumulativeCost().get(i);
                int reps = data.getReplicaCount().get(i);
                double bip = data.getBwInterProviderGb().get(i);
                double bir = data.getBwInterRegionGb().get(i);
                double ccpu = safeGet(data.getCpuCostPerQuery(), i);
                double crep = safeGet(data.getReplicaCostPerQuery(), i);
                double ttot = safeGet(data.getTotalCostPerQuery(), i, cbw + ccpu + crep);
                int viol = data.getSlaViolations().get(i);
                double avg = data.getAvgBwPrice().get(i);
                out.append(q + "," + fmt(rt) + "," + fmt(cbw) + "," + fmt(cc) + "," + reps + ","
                    + fmt(bip) + "," + fmt(bir) + "," + fmt(ccpu) + "," + fmt(crep) + ","
                    + fmt(ttot) + "," + viol + "," + fmt(avg) + "\n");
            }
        }
    }

    /**
     * Writes a compact summary CSV for a list of models.
     * Columns: name,queries,final_cumul_bw_cost,total_bw_cost,total_cpu_cost,
     *          total_replica_cost,total_cost,total_bw_inter_provider_gb,
     *          total_bw_inter_region_gb,total_sla_violations,final_replica_count
     */
    public static void exportSummaryCsv(java.util.List<BenchmarkData> models, String filePath) throws IOException {
        ensureParentDir(filePath);
        try (FileWriter out = new FileWriter(filePath, false)) {
            // total_sla_violations : dépassements du seuil EFFECTIF (appris) ; tsla/csla_contract :
            // dépassements des seuils du CONTRAT (Paper Table 1), comparables entre stratégies.
            out.append("name,queries,final_cumul_bw_cost,total_bw_cost,total_cpu_cost,total_replica_cost,total_cost,total_bw_inter_provider_gb,total_bw_inter_region_gb,total_sla_violations,final_replica_count,tsla_contract_violations,csla_contract_violations,first_replica_query,replica_creations,replica_deletions,thrash_events,unused_replica_cost\n");
            for (BenchmarkData d : models) {
                int n = d.getQueryNumbers().size();
                int last = Math.max(0, n - 1);
                double finalCumulBw = n > 0 ? d.getCumulativeCost().get(last) : 0.0;
                int finalReplicas = n > 0 ? d.getReplicaCount().get(last) : 0;
                out.append(d.getName()).append(",")
                    .append(Integer.toString(n)).append(",")
                    .append(fmt(finalCumulBw)).append(",")
                    .append(fmt(d.getTotalBwCost())).append(",")
                    .append(fmt(d.getTotalCpuCost())).append(",")
                    .append(fmt(d.getTotalReplicaCost())).append(",")
                    .append(fmt(d.getTotalCost())).append(",")
                    .append(fmt(d.getTotalBwInterProviderGb())).append(",")
                    .append(fmt(d.getTotalBwInterRegionGb())).append(",")
                    .append(Integer.toString(d.getTotalSlaViolations())).append(",")
                    .append(Integer.toString(finalReplicas)).append(",")
                    .append(Integer.toString(contractTSlaViolations(d))).append(",")
                    .append(Integer.toString(contractCSlaViolations(d))).append(",")
                    .append(Integer.toString(firstReplicaQuery(d))).append(",")
                    .append(Integer.toString(d.getReplicaCreations())).append(",")
                    .append(Integer.toString(d.getReplicaDeletions())).append(",")
                    .append(Integer.toString(d.getThrashEvents())).append(",")
                    .append(fmt(d.getUnusedReplicaCost())).append("\n");
            }
        }
    }

    /** Type de requête déduit du nom du modèle (« …_Complex »). */
    static boolean isComplex(BenchmarkData d) {
        return d.getName().toLowerCase(Locale.ROOT).contains("complex");
    }

    /** Requêtes dont le temps de réponse dépasse le T_SLA du contrat (200 / 400 ms). */
    public static int contractTSlaViolations(BenchmarkData d) {
        double tSla = isComplex(d) ? org.tcdrm.adaptive.core.TcdrmConstants.TSLA_COMPLEX_MS
                                   : org.tcdrm.adaptive.core.TcdrmConstants.TSLA_SIMPLE_MS;
        int v = 0;
        for (double t : d.getResponseTimeMs()) if (t > tSla) v++;
        return v;
    }

    /** Requêtes dont le coût total (transfert + calcul + réplicas) dépasse le C_SLA du contrat. */
    public static int contractCSlaViolations(BenchmarkData d) {
        double cSla = isComplex(d) ? org.tcdrm.adaptive.core.TcdrmConstants.CSLA_COMPLEX
                                   : org.tcdrm.adaptive.core.TcdrmConstants.CSLA_SIMPLE;
        int v = 0;
        for (double c : d.getTotalCostPerQuery()) if (c > cSla) v++;
        return v;
    }

    /** Indice de la première requête avec au moins un réplica (−1 si aucun). */
    public static int firstReplicaQuery(BenchmarkData d) {
        java.util.List<Integer> r = d.getReplicaCount();
        for (int i = 0; i < r.size(); i++) if (r.get(i) > 0) return i;
        return -1;
    }

    /**
     * Writes windowed averages of total cost over time to a CSV file, appending a
     * section per model (label line + sequence line), similar to log_overtime.csv.
     */
    public static void exportOvertimeAverages(BenchmarkData data, String filePath, int window) throws IOException {
        ensureParentDir(filePath);
        try (FileWriter out = new FileWriter(filePath, true)) {
            out.append(data.getName()).append('\n');
            int n = data.getTotalCostPerQuery().size();
            if (n == 0) {
                // Fallback: approximate total = bw cost
                n = data.getCostPerQuery().size();
            }
            double running = 0.0;
            int count = 0;
            for (int i = 0; i < n; i++) {
                double val = i < data.getTotalCostPerQuery().size()
                    ? data.getTotalCostPerQuery().get(i)
                    : data.getCostPerQuery().get(i);
                running += val;
                count++;
                if (count == window) {
                    out.append(fmt(running / window)).append(' ');
                    running = 0.0;
                    count = 0;
                }
            }
            if (count > 0) {
                out.append(fmt(running / count)).append(' ');
            }
            out.append('\n').append('\n');
        }
    }

    private static void ensureParentDir(String filePath) {
        File f = new File(filePath).getParentFile();
        if (f != null) f.mkdirs();
    }

    // Always use '.' as decimal separator to generate portable CSVs
    private static final DecimalFormat DF;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setDecimalSeparator('.') ;
        DF = new DecimalFormat("###.######");
        DF.setDecimalFormatSymbols(sym);
    }
    private static String fmt(double v) { return DF.format(v); }
    private static double safeGet(java.util.List<Double> list, int i) {
        return safeGet(list, i, 0.0);
    }
    private static double safeGet(java.util.List<Double> list, int i, double def) {
        return (list != null && i < list.size()) ? list.get(i) : def;
    }

    /** Un run du benchmark de robustesse (graine, stratégie, régime). */
    public record SeedRun(long seed, String strategy, String regime, BenchmarkData data) {}

    /**
     * Exporte chaque run (une ligne par graine) puis, par stratégie et régime, la moyenne
     * et l'écart-type (échantillon, n−1) du coût, des violations du contrat et des réplicas.
     */
    public static void exportSeedSummaryCsv(java.util.List<SeedRun> runs, String perRunPath,
                                            String aggregatePath) throws IOException {
        ensureParentDir(perRunPath);
        try (FileWriter out = new FileWriter(perRunPath, false)) {
            out.append("seed,strategy,regime,total_cost,tsla_contract_violations,csla_contract_violations,final_replica_count,first_replica_query,replica_creations,replica_deletions,thrash_events,unused_replica_cost\n");
            for (SeedRun r : runs) {
                double[] m = seedMetrics(r.data());
                out.append(Long.toString(r.seed())).append(',').append(r.strategy()).append(',')
                    .append(r.regime()).append(',').append(fmt(m[0])).append(',')
                    .append(Integer.toString((int) m[1])).append(',').append(Integer.toString((int) m[2])).append(',')
                    .append(Integer.toString((int) m[3])).append(',').append(Integer.toString((int) m[4])).append(',')
                    .append(Integer.toString((int) m[5])).append(',').append(Integer.toString((int) m[6])).append(',')
                    .append(Integer.toString((int) m[7])).append(',').append(fmt(m[8])).append('\n');
            }
        }
        java.util.Map<String, java.util.List<double[]>> groups = new java.util.LinkedHashMap<>();
        for (SeedRun r : runs) {
            groups.computeIfAbsent(r.strategy() + "," + r.regime(), k -> new java.util.ArrayList<>())
                .add(seedMetrics(r.data()));
        }
        ensureParentDir(aggregatePath);
        try (FileWriter out = new FileWriter(aggregatePath, false)) {
            out.append("strategy,regime,seeds,cost_mean,cost_std,tsla_viol_mean,tsla_viol_std,csla_viol_mean,csla_viol_std,replicas_mean,replicas_std,first_replica_mean,creations_mean,creations_std,deletions_mean,deletions_std,thrash_mean,thrash_std,unused_cost_mean,unused_cost_std\n");
            for (java.util.Map.Entry<String, java.util.List<double[]>> e : groups.entrySet()) {
                java.util.List<double[]> g = e.getValue();
                out.append(e.getKey()).append(',').append(Integer.toString(g.size()));
                for (int k = 0; k < 4; k++) {
                    out.append(',').append(fmt(mean(g, k))).append(',').append(fmt(std(g, k)));
                }
                out.append(',').append(fmt(mean(g, 4)));
                for (int k = 5; k < 9; k++) {
                    out.append(',').append(fmt(mean(g, k))).append(',').append(fmt(std(g, k)));
                }
                out.append('\n');
            }
        }
    }

    /** [coût total, violations T_SLA du contrat, violations C_SLA du contrat, réplicas finaux,
     *  premier réplica, créations, suppressions, oscillations, coût des réplicas inutilisés]. */
    private static double[] seedMetrics(BenchmarkData d) {
        int n = d.getReplicaCount().size();
        return new double[] {d.getTotalCost(), contractTSlaViolations(d), contractCSlaViolations(d),
            n > 0 ? d.getReplicaCount().get(n - 1) : 0, firstReplicaQuery(d),
            d.getReplicaCreations(), d.getReplicaDeletions(), d.getThrashEvents(), d.getUnusedReplicaCost()};
    }

    private static double mean(java.util.List<double[]> g, int k) {
        double s = 0.0;
        for (double[] v : g) s += v[k];
        return g.isEmpty() ? 0.0 : s / g.size();
    }

    private static double std(java.util.List<double[]> g, int k) {
        if (g.size() < 2) return 0.0;
        double m = mean(g, k), s = 0.0;
        for (double[] v : g) s += (v[k] - m) * (v[k] - m);
        return Math.sqrt(s / (g.size() - 1));
    }
}
