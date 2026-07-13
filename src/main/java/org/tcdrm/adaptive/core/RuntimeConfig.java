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

    /**
     * Régime du workload :
     * <ul>
     *   <li>{@code steady}   — requête répétée, fidèle au protocole du papier ;</li>
     *   <li>{@code variable} — popularité Zipf avec dérive du hot-set (seedée) ;</li>
     *   <li>{@code burst}    — fond faiblement biaisé + pics soudains de popularité.</li>
     * </ul>
     * Défauts par contexte : l'ENTRAÎNEMENT alterne variable/burst par épisode
     * (apprendre sur les régimes dynamiques), le BENCHMARK utilise steady
     * (protocole du papier, identique pour tous les modèles — équité).
     * L'env {@code TCDRM_WORKLOAD}, si présente, force le MÊME mode partout.
     */
    private static String workloadMode = initWorkloadMode();
    /** Vrai si {@code TCDRM_WORKLOAD} a explicitement fixé le mode (prioritaire partout). */
    private static boolean explicitWorkloadMode = isValidWorkloadMode(System.getenv("TCDRM_WORKLOAD"));

    private static String initWorkloadMode() {
        String env = System.getenv("TCDRM_WORKLOAD");
        return normalizeWorkloadMode(env, "steady");
    }

    private static boolean isValidWorkloadMode(String mode) {
        if (mode == null || mode.isBlank()) return false;
        return switch (mode.trim().toLowerCase()) {
            case "steady", "variable", "burst" -> true;
            default -> false;
        };
    }

    private static String normalizeWorkloadMode(String mode, String fallback) {
        if (mode == null || mode.isBlank()) return fallback;
        String m = mode.trim().toLowerCase();
        return switch (m) {
            case "steady", "variable", "burst" -> m;
            default -> fallback;
        };
    }

    public static String getExecRegion() { return execRegion; }
    public static void setExecRegion(String region) { if (region != null && !region.isBlank()) execRegion = region; }

    public static void reset() {
        execRegion = "EU";
        maxQueriesOverride = null;
        workloadMode = initWorkloadMode();
        explicitWorkloadMode = isValidWorkloadMode(System.getenv("TCDRM_WORKLOAD"));
    }

    public static void setMaxQueries(Integer n) { maxQueriesOverride = n; }
    public static Integer getMaxQueries() { return maxQueriesOverride; }

    public static String getWorkloadMode() { return workloadMode; }
    public static void setWorkloadMode(String mode) { workloadMode = normalizeWorkloadMode(mode, workloadMode); }
    /** Vrai si le mode a été forcé par {@code TCDRM_WORKLOAD} — il s'impose alors partout. */
    public static boolean hasExplicitWorkloadMode() { return explicitWorkloadMode; }
}
