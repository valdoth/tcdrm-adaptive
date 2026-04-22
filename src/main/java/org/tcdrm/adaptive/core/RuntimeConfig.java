package org.tcdrm.adaptive.core;

/**
 * Runtime configuration overridable by external callers via the public adapter API.
 * If not modified, defaults are used by {@link org.tcdrm.adaptive.simulation.TcdrmSimulation}.
 */
public final class RuntimeConfig {
    private RuntimeConfig() {}

    // Execution region for the join/compute site (must be one of MultiCloudInfrastructure.REGIONS)
    private static String execRegion = "EU";

    // Popularity strategy and TinyLFU parameters
    private static String popularityStrategy = "EMA"; // EMA | TINYLFU | EMA_TINYLFU
    private static Integer tinyWidth = null;
    private static Integer tinyDepth = null;
    private static Integer tinyAging = null;
    private static Double tinyTauHi = null;
    private static Double tinyTauLo = null;

    // Optional override for the number of queries per experiment
    private static Integer maxQueriesOverride = null;

    public static String getExecRegion() { return execRegion; }
    public static void setExecRegion(String region) { if (region != null && !region.isBlank()) execRegion = region; }

    public static String getPopularityStrategy() { return popularityStrategy; }
    public static void setPopularityStrategy(String strategy) { if (strategy != null && !strategy.isBlank()) popularityStrategy = strategy; }

    public static Integer getTinyWidth() { return tinyWidth; }
    public static Integer getTinyDepth() { return tinyDepth; }
    public static Integer getTinyAging() { return tinyAging; }
    public static Double getTinyTauHi() { return tinyTauHi; }
    public static Double getTinyTauLo() { return tinyTauLo; }

    public static void setTinyParams(Integer width, Integer depth, Integer aging, Double tauHi, Double tauLo) {
        tinyWidth = width; tinyDepth = depth; tinyAging = aging; tinyTauHi = tauHi; tinyTauLo = tauLo;
    }

    public static void reset() {
        execRegion = "EU";
        popularityStrategy = "EMA";
        tinyWidth = null; tinyDepth = null; tinyAging = null; tinyTauHi = null; tinyTauLo = null;
        maxQueriesOverride = null;
    }

    public static void setMaxQueries(Integer n) { maxQueriesOverride = n; }
    public static Integer getMaxQueries() { return maxQueriesOverride; }
}
