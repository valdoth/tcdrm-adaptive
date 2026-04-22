import org.tcdrm.adaptive.api.TcdrmAdapter;

/**
 * Two-model RL comparison (EMA by default): runs simple then complex.
 */
public class RLComparisonEvaluation {
    private static final int TIMEOUT_SEC = 120;

    public static void main(String[] args) {
        System.out.println("Begin simulation (RL comparison: Q-Learning vs DQN — simple + complex, EMA)...");
        try {
            TcdrmAdapter.initSimulation();
            // RANDOM par requête + 1000 requêtes pour alignement
            TcdrmAdapter.setExecRegion("RANDOM");
            TcdrmAdapter.setMaxQueries(1000);

            // Run both simple then complex in a single Python session
            TcdrmAdapter.runQlearningVsDqnBoth(TIMEOUT_SEC);

            System.out.println("\n✅ RL 2-models simple+complex comparison complete → images/ & metrics/\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Unwanted errors happen during RL comparison run");
        }
    }
}
