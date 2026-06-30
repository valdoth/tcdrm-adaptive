package org.tcdrm.adaptive.core;

/**
 * Runtime configuration overridable by external callers via the public adapter API.
 * If not modified, defaults are used by {@link org.tcdrm.adaptive.simulation.TcdrmSimulation}.
 */
public final class RuntimeConfig {
    private RuntimeConfig() {}

    // Execution region for the join/compute site (must be one of MultiCloudInfrastructure.REGIONS)
    private static String execRegion = "EU";

    // Optional override for the number of queries per experiment
    private static Integer maxQueriesOverride = null;

    public static String getExecRegion() { return execRegion; }
    public static void setExecRegion(String region) { if (region != null && !region.isBlank()) execRegion = region; }

    public static void reset() {
        execRegion = "EU";
        maxQueriesOverride = null;
    }

    public static void setMaxQueries(Integer n) { maxQueriesOverride = n; }
    public static Integer getMaxQueries() { return maxQueriesOverride; }
}
