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
 * Méta-contrôleur Q-learning : APPREND à choisir la valeur d'un seuil normalisé borné,
 * à partir du stress observé (taux de violations SLA, ratio coût/contrat), fourni en
 * continu sous forme de signal lissé (EMA) — observation et décision À CHAQUE REQUÊTE,
 * sans aucune cadence fixe : le MOMENT d'un changement de seuil est entièrement
 * déterminé par la politique apprise.
 *
 * L'action est la SÉLECTION DIRECTE du niveau de seuil parmi tous les niveaux de
 * l'intervalle — il n'existe AUCUNE limite de vitesse d'ajustement ni règle
 * « si stress alors ±X% » : la trajectoire du seuil est
 * entièrement déterminée par la politique apprise. La granularité de l'intervalle
 * ({@code resolution}) est une discrétisation de l'espace d'action, au même titre que
 * les buckets d'état — pas une règle de comportement.
 *
 * Littérature : approche « Q-Threshold » — Horovitz &amp; Arian, "Efficient Cloud
 * Auto-Scaling with SLA Objective Using Q-Learning", IEEE FiCloud 2018 ; reprise dans
 * "SLA-Adaptive Threshold Adjustment for a Kubernetes Horizontal Pod Autoscaler",
 * Electronics 13(7):1242, 2024. Ces travaux substituent un agent Q-learning aux seuils
 * fixés manuellement ; on applique le même principe aux seuils de réplication TCDRM
 * (T_SLA et éligibilité popularité P_SLA — Sujet 1).
 *
 * <ul>
 *   <li>État : (bucket violations × bucket coût × bucket valeur courante).</li>
 *   <li>Actions : choisir le niveau du seuil (un niveau par cran de résolution).</li>
 *   <li>Récompense : r = −violationRate − max(0, costRatio − 1)
 *       − w_fid·(écart au contrat normalisé). Le terme de fidélité enseigne que
 *       s'écarter du contrat (P_SLA) n'est justifié que s'il supprime des violations —
 *       l'équilibre seuil/SLA est APPRIS, pas codé.</li>
 *   <li>Q-table persistée (.qtable) PAR AGENT : entraînée pendant l'entraînement de
 *       l'agent, rechargée par le benchmark (greedy + apprentissage online).</li>
 * </ul>
 */
public final class ThresholdMetaLearner {

    private static final int VIOL_BUCKETS = 5;
    private static final int COST_BUCKETS = 3;

    // Hyperparamètres d'apprentissage (standard Q-learning), calibrés pour la cadence
    // de décision PAR REQUÊTE (1000 pas/épisode).
    //
    // GAMMA : le bénéfice d'ouvrir le seuil met ~40+ requêtes à se matérialiser
    // (réplication par l'agent → warmup ~10 requêtes → décroissance de l'EMA de
    // violations sur ~1/α ≈ 25 requêtes), alors que les coûts d'ouverture (fidélité,
    // événement) sont immédiats. γ=0.998 donne un horizon effectif ≈ 500 requêtes —
    // équivalent au γ=0.9 de l'ancienne cadence fenêtrée (0.9 ≈ 0.998^50). Un γ
    // myope (0.9 par requête ≈ 10 requêtes d'horizon) fait apprendre au méta-contrôleur
    // à ouvrir PLUS TARD que le contrat statique : il paie l'ouverture sans jamais
    // en voir le retour.
    private static final double ALPHA = 0.05;
    private static final double GAMMA = 0.998;
    /**
     * Poids du terme de fidélité au contrat dans la récompense méta — même famille que
     * les poids r1..r9 de la reward des agents : un réglage de fonction de récompense,
     * pas un seuil de comportement.
     *
     * Réduit 0.5 → 0.3 : la fidélité PÉNALISE l'ouverture du seuil (relaxation), qui doit
     * être « payée » par des violations évitées. Un agent LENT à exploiter la porte
     * ouverte (Rainbow en début d'entraînement) ne réduit pas assez les violations pour
     * couvrir une fidélité forte → le méta apprend à garder la porte FERMÉE, ce qui
     * empêche l'agent de répliquer (cercle vicieux observé : Rainbow ouvre à q≈160 vs
     * q≈43 pour Q-Learning). Une fidélité plus faible laisse le méta ouvrir sous
     * violations soutenues même quand l'agent n'est pas encore optimal — le coût de
     * détention par fragment protège toujours contre la réplication de données froides.
     */
    private static final double FIDELITY_WEIGHT = 0.3;
    /**
     * Coût d'ÉVÉNEMENT : chaque BAISSE du seuil coûte proportionnellement à son
     * ampleur, en plus du coût de fidélité par requête. Sans lui, la cadence par
     * requête permettrait une « ouverture-éclair » gratuite : baisser le seuil une
     * seule requête (le temps que l'agent réplique) puis le remonter — le coût de
     * fidélité, facturé par requête passée sous le contrat, serait négligeable.
     */
    private static final double CHANGE_WEIGHT = 1.0;
    /**
     * Coût LINÉAIRE continu dans la récompense méta : sans lui, le terme
     * max(0, costRatio−1) ne se déclenche JAMAIS tant que le coût reste sous le
     * contrat C_SLA (cas permanent en workload simple) — le méta-contrôleur serait
     * aveugle au coût et n'apprendrait le MOMENT d'ouverture du seuil que par les
     * violations. Avec ce terme, ouvrir l'éligibilité quand la réplication réduit
     * réellement le coût moyen (liens inter-région vs inter-provider) est appris.
     */
    private static final double COST_LINEAR_WEIGHT = 0.3;

    private final double minValue;
    private final double maxValue;
    private final double resolution;
    private final int nLevels;
    private final Random rnd;
    private final double[][] q;

    private double epsilon;
    private double value;
    private int lastState = -1;
    private int lastAction = -1;
    /** Ampleur normalisée de la baisse introduite par la dernière action (coût d'événement). */
    private double lastDrop = 0.0;

    public ThresholdMetaLearner(double initialValue, double minValue, double maxValue,
                                double resolution, double epsilon, long seed) {
        if (maxValue <= minValue || resolution <= 0) {
            throw new IllegalArgumentException("Invalid threshold bounds/resolution");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.resolution = resolution;
        this.nLevels = (int) Math.round((maxValue - minValue) / resolution) + 1;
        this.epsilon = epsilon;
        this.rnd = new Random(seed);
        this.q = new double[VIOL_BUCKETS * COST_BUCKETS * nLevels][nLevels];
        // Prior informé : la récompense méta contient un terme de fidélité au contrat
        // connu a priori (−FIDELITY_WEIGHT × assouplissement). L'encoder dans la
        // Q-table initiale (au lieu de 0 partout) évite qu'une table VIERGE en greedy
        // pur dérive vers les niveaux inexplorés par simple optimisme d'initialisation :
        // sans apprentissage, la politique reste au contrat ; dès que des violations
        // sont observées, le TD-learning surpasse ce prior et apprend à assouplir.
        for (double[] row : q) {
            for (int a = 0; a < nLevels; a++) {
                double relax = (maxValue - (minValue + a * resolution)) / (maxValue - minValue);
                row[a] = -FIDELITY_WEIGHT * relax;
            }
        }
        this.value = clamp(initialValue);
    }

    /**
     * Observe le stress courant (signal lissé EMA), met à jour la Q-table (TD-learning)
     * puis choisit (ε-greedy) le niveau courant du seuil — n'importe quel niveau de
     * l'intervalle, sans restriction de déplacement. Appelé à CHAQUE requête : le
     * moment d'un changement de seuil est une décision apprise, pas une cadence.
     *
     * @param violationRate taux de violations T_SLA lissé (EMA) [0,1]
     * @param costRatio     ratio lissé coût / contrat C_SLA (EMA)
     * @return la nouvelle valeur du seuil
     */
    public double observeAndAdjust(double violationRate, double costRatio) {
        int s = stateOf(violationRate, costRatio);
        // Fidélité au contrat : s'éloigner du seuil contractuel (maxValue) coûte —
        // l'assouplissement doit se payer en violations évitées. Le coût d'événement
        // (CHANGE_WEIGHT × baisse introduite par la dernière action) rend les
        // ouvertures-éclair non gratuites.
        double relaxation = (maxValue - value) / (maxValue - minValue);
        double reward = -violationRate - Math.max(0.0, costRatio - 1.0)
            - COST_LINEAR_WEIGHT * costRatio
            - FIDELITY_WEIGHT * relaxation
            - CHANGE_WEIGHT * lastDrop;

        if (lastState >= 0 && lastAction >= 0) {
            double best = maxQ(s);
            q[lastState][lastAction] += ALPHA * (reward + GAMMA * best - q[lastState][lastAction]);
        }

        int a = (rnd.nextDouble() < epsilon) ? rnd.nextInt(nLevels) : argmaxQ(s, levelOf(value));
        double newValue = clamp(minValue + a * resolution);
        lastDrop = Math.max(0.0, (value - newValue) / (maxValue - minValue));
        lastState = s;
        lastAction = a;
        value = newValue;
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
        this.lastDrop = 0.0;
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

        int valB = (int) Math.round((value - minValue) / resolution);
        valB = Math.max(0, Math.min(nLevels - 1, valB));
        return (vb * COST_BUCKETS + cb) * nLevels + valB;
    }

    private double clamp(double v) { return Math.max(minValue, Math.min(maxValue, v)); }

    private double maxQ(int s) {
        double m = q[s][0];
        for (int i = 1; i < nLevels; i++) m = Math.max(m, q[s][i]);
        return m;
    }

    /** Index de niveau correspondant à une valeur de seuil. */
    private int levelOf(double v) {
        int lvl = (int) Math.round((v - minValue) / resolution);
        return Math.max(0, Math.min(nLevels - 1, lvl));
    }

    /**
     * Argmax avec tie-break au niveau COURANT : sur un état jamais visité (ligne
     * uniforme, ex. Q-table vierge), la politique greedy conserve le seuil en place
     * (le contrat en début de run) au lieu de sauter arbitrairement à l'index 0 —
     * qui, pour le seuil de popularité, autoriserait la réplication dès la requête 1.
     */
    private int argmaxQ(int s, int preferredLevel) {
        int best = preferredLevel;
        for (int i = 0; i < nLevels; i++) if (q[s][i] > q[s][best]) best = i;
        return best;
    }

    // === Persistance (texte simple : 1 ligne d'en-tête + 1 ligne par état) ===

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(String.format(Locale.ROOT, "# qtable states=%d actions=%d min=%s max=%s resolution=%s%n",
                q.length, nLevels, minValue, maxValue, resolution));
            for (double[] row : q) {
                StringBuilder sb = new StringBuilder();
                for (int a = 0; a < nLevels; a++) {
                    if (a > 0) sb.append(' ');
                    sb.append(row[a]);
                }
                sb.append(System.lineSeparator());
                w.write(sb.toString());
            }
        }
    }

    /**
     * Charge une Q-table existante si compatible (mêmes dimensions), sinon crée un
     * learner vierge. Le seuil courant repart toujours de {@code initialValue}
     * (valeur contractuelle).
     */
    public static ThresholdMetaLearner loadOrCreate(File file, double initialValue, double minValue,
                                                    double maxValue, double resolution, double epsilon, long seed) {
        ThresholdMetaLearner learner = new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed);
        if (file == null || !file.isFile()) return learner;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String header = r.readLine();
            String expected = "# qtable states=" + learner.q.length + " actions=" + learner.nLevels + " ";
            if (header == null || !header.startsWith(expected)) {
                return learner; // structure incompatible → repartir vierge
            }
            for (int s = 0; s < learner.q.length; s++) {
                String line = r.readLine();
                if (line == null) return new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed);
                String[] parts = line.trim().split("\\s+");
                for (int a = 0; a < learner.nLevels && a < parts.length; a++) {
                    learner.q[s][a] = Double.parseDouble(parts[a]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            return new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed);
        }
        return learner;
    }
}
