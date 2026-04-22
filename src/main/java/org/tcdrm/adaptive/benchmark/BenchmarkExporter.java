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
            out.append("name,queries,final_cumul_bw_cost,total_bw_cost,total_cpu_cost,total_replica_cost,total_cost,total_bw_inter_provider_gb,total_bw_inter_region_gb,total_sla_violations,final_replica_count\n");
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
                    .append(Integer.toString(finalReplicas)).append("\n");
            }
        }
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
}
