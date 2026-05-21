import org.tcdrm.adaptive.api.TcdrmAdapter;

/**
 * Full 4-model comparison: NoRepLc + TCDRM + Q-Learning + DQN, simple + complex.
 * Generates all paper figures (Fig 1–6) and summary_phase2_rl.csv.
 */
public class RLComparisonEvaluation {
    private static final int TIMEOUT_SEC = 300;

    public static void main(String[] args) {
        System.out.println("Begin full 4-model comparison (NoRepLc + TCDRM + Q-Learning + DQN)...");
        try {
            TcdrmAdapter.initSimulation();
            TcdrmAdapter.setExecRegion("RANDOM");
            TcdrmAdapter.setMaxQueries(3000);

            // Runs NoRepLc + TCDRM baselines, then waits for Python and runs QL + DQN.
            // Generates all paper figures and summary CSV.
            TcdrmAdapter.runAllFourModels(TIMEOUT_SEC);

            System.out.println("\n✅ 4-model comparison complete → images/ & metrics/\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during 4-model comparison run");
        }
    }
}
