import org.tcdrm.adaptive.api.TcdrmAdapter;

/**
 * DQN validation runner (EMA by default).
 * - Runs simple then complex in a single Python session
 * - Exports CSVs and images into validation/{metrics,images}
 */
public class DNNEvaluation {
    private static final int TIMEOUT_SEC = 120;

    public static void main(String[] args) {
        System.out.println("Begin simulation (DQN: EMA)...");
        try {
            // Reset config (defaults to EMA); no createArchitecture() needed
            TcdrmAdapter.initSimulation();
            // Même config que validation principale: RANDOM + 1000 requêtes
            TcdrmAdapter.setExecRegion("RANDOM");
            TcdrmAdapter.setMaxQueries(1000);
            TcdrmAdapter.runDqnBoth(TIMEOUT_SEC);
            System.out.println("\n✅ DQN simple+complex complete → images/ & metrics/\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unwanted errors happen during DQN run");
        }
    }
}
