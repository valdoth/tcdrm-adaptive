import org.tcdrm.adaptive.api.TcdrmAdapter;

/**
 * Unified Q-Learning validation runner (EMA by default).
 * - Runs simple then complex in a single Python session
 * - Exports CSVs and images into validation/{metrics,images}
 */
public class QLearningEvaluation {
    private static final int TIMEOUT_SEC = 300;

    public static void main(String[] args) {
        System.out.println("Begin simulation (Q-Learning: simple + complex, EMA)...");
        try {
            // Reset config (defaults to EMA); no createArchitecture() needed
            TcdrmAdapter.initSimulation();
            // Aligner le benchmark sur la validation: région RANDOM par requête + 1000 requêtes
            TcdrmAdapter.setExecRegion("RANDOM");
            // 1000 requêtes — conforme au papier et à MAX_QUERIES (workloads + normalisation état RL calibrés pour 1000)
            TcdrmAdapter.setMaxQueries(1000);
            TcdrmAdapter.runQlearningBoth(TIMEOUT_SEC);
            System.out.println("\n✅ Q-Learning simple+complex complete → images/ & metrics/\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unwanted errors happen during Q-Learning run");
        }
    }
}
