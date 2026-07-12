package org.tcdrm.adaptive.rl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

/**
 * Méta-contrôleur Q-learning : APPREND à ajuster un seuil normalisé borné à partir du
 * stress observé (taux de violations SLA, ratio coût/contrat) sur des fenêtres de requêtes.
 *
 * Remplace les machines à états codées en dur : aucune règle du type
 * « si violations &gt; 20% alors baisser le seuil de 10% » n'est écrite dans le code —
 * c'est le modèle qui découvre la politique d'ajustement par renforcement.
 *
 * Littérature : approche « Q-Threshold » — Horovitz &amp; Arian, "Efficient Cloud
 * Auto-Scaling with SLA Objective Using Q-Learning", IEEE FiCloud 2018 ; reprise dans
 * "SLA-Adaptive Threshold Adjustment for a Kubernetes Horizontal Pod Autoscaler",
 * Electronics 13(7):1242, 2024. Ces travaux substituent un agent Q-learning aux seuils
 * d'auto-scaling fixés manuellement ; on applique ici le même principe aux seuils de
 * réplication TCDRM (T_SLA et éligibilité popularité P_SLA — Sujet 1).
 *
 * <ul>
 *   <li>État : (bucket violations × bucket coût × bucket valeur courante) — discrétisation.</li>
 *   <li>Actions : baisser / garder / monter le seuil d'un pas (granularité d'action).</li>
 *   <li>Récompense : r = −violationRate − max(0, costRatio − 1) — objectif contractuel
 *       (zéro violation, budget respecté), sans aucune règle de seuil codée en dur.</li>
 *   <li>Q-table persistée (.qtable) : entraînée sur les épisodes d'entraînement, rechargée
 *       par le benchmark (exploitation greedy + apprentissage online continu).</li>
 * </ul>
 */
public final class ThresholdMetaLearner {

    private static final int N_ACTIONS = 3; // 0 = baisser, 1 = garder, 2 = monter
    private static final int VIOL_BUCKETS = 5;
    private static final int COST_BUCKETS = 3;

    // Hyperparamètres d'apprentissage (standard Q-learning)
    private static final double ALPHA = 0.30;
    private static final double GAMMA = 0.90;

    private final double minValue;
    private final double maxValue;
    private final double step;
    private final int valueBuckets;
    private final Random rnd;
    private final double[][] q;

    private double epsilon;
    private double value;
    private int lastState = -1;
    private int lastAction = -1;

    public ThresholdMetaLearner(double initialValue, double minValue, double maxValue,
                                double step, double epsilon, long seed) {
        if (maxValue <= minValue || step <= 0) {
            throw new IllegalArgumentException("Invalid threshold bounds/step");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.step = step;
        this.valueBuckets = (int) Math.round((maxValue - minValue) / step) + 1;
        this.epsilon = epsilon;
        this.rnd = new Random(seed);
        this.q = new double[VIOL_BUCKETS * COST_BUCKETS * valueBuckets][N_ACTIONS];
        this.value = clamp(initialValue);
    }

    /**
     * Observe la fenêtre écoulée, met à jour la Q-table (TD-learning) puis choisit
     * (ε-greedy) le prochain ajustement du seuil.
     *
     * @param violationRate taux de violations T_SLA sur la fenêtre [0,1]
     * @param costRatio     coût moyen / contrat C_SLA sur la fenêtre
     * @return la nouvelle valeur du seuil
     */
    public double observeAndAdjust(double violationRate, double costRatio) {
        int s = stateOf(violationRate, costRatio);
        double reward = -violationRate - Math.max(0.0, costRatio - 1.0);

        if (lastState >= 0 && lastAction >= 0) {
            double best = maxQ(s);
            q[lastState][lastAction] += ALPHA * (reward + GAMMA * best - q[lastState][lastAction]);
        }

        int a = (rnd.nextDouble() < epsilon) ? rnd.nextInt(N_ACTIONS) : argmaxQ(s);
        lastState = s;
        lastAction = a;
        value = clamp(value + (a - 1) * step);
        return value;
    }

    /**
     * Réinitialise la trajectoire pour un nouvel épisode/run : le seuil repart de sa
     * valeur contractuelle, la CONNAISSANCE (Q-table) est conservée.
     */
    public void startEpisode(double initialValue) {
        this.value = clamp(initialValue);
        this.lastState = -1;
        this.lastAction = -1;
    }

    public double getValue() { return value; }

    public void setEpsilon(double e) { this.epsilon = Math.max(0.0, Math.min(1.0, e)); }

    // === Discrétisation de l'état (buckets standards, pas des règles de décision) ===

    private int stateOf(double violationRate, double costRatio) {
        int vb;
        if (violationRate <= 0.001)     vb = 0;
        else if (violationRate < 0.05)  vb = 1;
        else if (violationRate < 0.20)  vb = 2;
        else if (violationRate < 0.50)  vb = 3;
        else                            vb = 4;

        int cb;
        if (costRatio < 0.80)      cb = 0;
        else if (costRatio <= 1.20) cb = 1;
        else                        cb = 2;

        int valB = (int) Math.round((value - minValue) / step);
        valB = Math.max(0, Math.min(valueBuckets - 1, valB));
        return (vb * COST_BUCKETS + cb) * valueBuckets + valB;
    }

    private double clamp(double v) { return Math.max(minValue, Math.min(maxValue, v)); }

    private double maxQ(int s) {
        double m = q[s][0];
        for (int i = 1; i < N_ACTIONS; i++) m = Math.max(m, q[s][i]);
        return m;
    }

    private int argmaxQ(int s) {
        int best = 0;
        for (int i = 1; i < N_ACTIONS; i++) if (q[s][i] > q[s][best]) best = i;
        return best;
    }

    // === Persistance (texte simple : 1 ligne d'en-tête + 1 ligne par état) ===

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(String.format(Locale.ROOT, "# qtable states=%d actions=%d min=%s max=%s step=%s%n",
                q.length, N_ACTIONS, minValue, maxValue, step));
            for (double[] row : q) {
                w.write(String.format(Locale.ROOT, "%s %s %s%n", row[0], row[1], row[2]));
            }
        }
    }

    /**
     * Charge une Q-table existante si compatible, sinon crée un learner vierge.
     * Le seuil courant repart toujours de {@code initialValue} (valeur contractuelle).
     */
    public static ThresholdMetaLearner loadOrCreate(File file, double initialValue, double minValue,
                                                    double maxValue, double step, double epsilon, long seed) {
        ThresholdMetaLearner learner = new ThresholdMetaLearner(initialValue, minValue, maxValue, step, epsilon, seed);
        if (file == null || !file.isFile()) return learner;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String header = r.readLine();
            if (header == null || !header.startsWith("# qtable states=" + learner.q.length + " ")) {
                return learner; // structure incompatible → repartir vierge
            }
            for (int s = 0; s < learner.q.length; s++) {
                String line = r.readLine();
                if (line == null) return new ThresholdMetaLearner(initialValue, minValue, maxValue, step, epsilon, seed);
                String[] parts = line.trim().split("\\s+");
                for (int a = 0; a < N_ACTIONS && a < parts.length; a++) {
                    learner.q[s][a] = Double.parseDouble(parts[a]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            return new ThresholdMetaLearner(initialValue, minValue, maxValue, step, epsilon, seed);
        }
        return learner;
    }
}
