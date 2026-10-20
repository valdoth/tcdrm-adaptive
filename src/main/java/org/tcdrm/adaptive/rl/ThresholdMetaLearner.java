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
 * L'action est INCRÉMENTALE : baisser le seuil d'un cran, le garder, ou le monter
 * d'un cran ({@code resolution}), comme les actions d'ajustement de seuil de
 * l'approche Q-Threshold. Aucune règle « si stress alors ±X% » : QUAND et DANS QUEL
 * SENS bouger restent appris. La sélection directe de n'importe quel niveau, utilisée
 * auparavant, laissait une Q-table bruitée faire sauter le seuil d'un extrême à
 * l'autre d'une requête à la suivante (T_SLA 240 → 400 ms, C_SLA 0,024 → 0,040 $,
 * P_SLA 0,70 → 1,0) : la récompense et l'état des agents changeaient alors au hasard,
 * et Q-Learning apprenait que répliquer déclenche des dépassements de coût.
 *
 * Littérature : approche « Q-Threshold » — Horovitz &amp; Arian, "Efficient Cloud
 * Auto-Scaling with SLA Objective Using Q-Learning", IEEE FiCloud 2018 ; reprise dans
 * "SLA-Adaptive Threshold Adjustment for a Kubernetes Horizontal Pod Autoscaler",
 * Electronics 13(7):1242, 2024. Ces travaux substituent un agent Q-learning aux seuils
 * fixés manuellement ; on applique le même principe aux seuils de réplication TCDRM
 * (T_SLA et éligibilité popularité P_SLA — Sujet 1).
 *
 * <ul>
 *   <li>État : (bucket violations × bucket coût × bucket popularité × bucket valeur
 *       courante). Le bucket popularité n'existe que pour le seuil de popularité
 *       ({@code popBuckets} &gt; 1) : sans lui, le méta-contrôleur ne voit pas si les
 *       données sont populaires et apprend à ouvrir l'éligibilité dès les premières
 *       violations, c'est-à-dire sur des données lues une seule fois.</li>
 *   <li>Actions : baisser d'un cran, garder, monter d'un cran.</li>
 *   <li>Récompense : r = −violationRate − max(0, costRatio − 1)
 *       − w_fid·(écart au contrat normalisé). Le terme de fidélité enseigne que
 *       s'écarter du contrat n'est justifié que s'il supprime des violations —
 *       l'équilibre seuil/SLA est APPRIS, pas codé. Le seuil de popularité n'a pas ce
 *       terme ({@link #withoutFidelity()}) : il part du plancher P_SLA.</li>
 *   <li>Q-table persistée (.qtable) PAR AGENT : entraînée pendant l'entraînement de
 *       l'agent, rechargée par le benchmark (greedy + apprentissage online).</li>
 * </ul>
 */
public final class ThresholdMetaLearner {

    private static final int VIOL_BUCKETS = 5;
    private static final int COST_BUCKETS = 3;
    /** Actions : 0 = baisser d'un cran, 1 = garder, 2 = monter d'un cran. */
    private static final int N_ACTIONS = 3;
    private static final int KEEP = 1;

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
     * Coût de CHANGEMENT (switching cost) : chaque changement du seuil, à la baisse
     * comme à la hausse, coûte proportionnellement à son ampleur, en plus du coût de
     * fidélité par requête. Sans lui, la cadence par requête permettrait une
     * « ouverture-éclair » gratuite : baisser le seuil une seule requête (le temps que
     * l'agent réplique) puis le remonter. Ne facturer que les baisses laissait la
     * remontée gratuite : en validation, les seuils sautaient d'un extrême à l'autre
     * d'une requête à la suivante (ΔT 190 → 10 → 160, P_SLA 0,70 → 1,0 → 0,90).
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
    /**
     * Poids de la charge de réplicas FROIDS (Σ (1 − pop_f)^k × réplicas_f / max, dans
     * [0,1]) dans la récompense du seuil de popularité — même échelle que le taux de
     * violations. Ouvrir l'éligibilité sur des données qui ne sont pas encore populaires
     * crée des réplicas froids : ce terme rend ce coût visible au méta-contrôleur, qui
     * apprend lui-même à partir de quelle popularité l'ouverture est rentable.
     * Le même poids s'applique à la charge d'oscillations du méta-contrôleur ΔT.
     * Réglable par TCDRM_META_COLD_WEIGHT (analyse de sensibilité) : la même valeur doit
     * être utilisée à l'entraînement et au benchmark (variable héritée par les deux JVM).
     */
    private static final double COLD_REPLICA_WEIGHT = coldReplicaWeight();

    private static double coldReplicaWeight() {
        try {
            return Math.max(0.0, Double.parseDouble(
                System.getenv().getOrDefault("TCDRM_META_COLD_WEIGHT", "1.0")));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    private final double minValue;
    private final double maxValue;
    private final double resolution;
    private final int nLevels;
    private final int popBuckets;
    private final Random rnd;
    private final double[][] q;
    /**
     * Nombre de fois où chaque (état, action) a été joué. Le choix greedy et la cible
     * TD ne portent que sur les actions déjà essayées : une action jamais jouée garde
     * sa valeur initiale (≈ 0), bien au-dessus des valeurs apprises (très négatives),
     * et la prendre en compte faisait osciller le seuil d'un cran à chaque requête
     * dans les états peu visités (0,904 ↔ 1,0 en validation, graine 777).
     */
    private final int[][] visits;
    /** Poids du terme de fidélité au contrat (0 pour le seuil de popularité, voir {@link #withoutFidelity()}). */
    private double fidelityWeight = FIDELITY_WEIGHT;

    private double epsilon;
    private double value;
    private int lastState = -1;
    private int lastAction = -1;
    /** Ampleur normalisée du changement introduit par la dernière action (coût de changement). */
    private double lastChange = 0.0;
    /** Faux = politique FIGÉE : le seuil suit la Q-table apprise sans la mettre à jour. */
    private boolean learning = true;

    public ThresholdMetaLearner(double initialValue, double minValue, double maxValue,
                                double resolution, double epsilon, long seed) {
        this(initialValue, minValue, maxValue, resolution, epsilon, seed, 1);
    }

    /** @param popBuckets nombre de buckets de popularité dans l'état (1 = popularité non observée). */
    public ThresholdMetaLearner(double initialValue, double minValue, double maxValue,
                                double resolution, double epsilon, long seed, int popBuckets) {
        if (maxValue <= minValue || resolution <= 0) {
            throw new IllegalArgumentException("Invalid threshold bounds/resolution");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.resolution = resolution;
        this.nLevels = (int) Math.round((maxValue - minValue) / resolution) + 1;
        this.popBuckets = Math.max(1, popBuckets);
        this.epsilon = epsilon;
        this.rnd = new Random(seed);
        this.q = new double[VIOL_BUCKETS * COST_BUCKETS * this.popBuckets * nLevels][N_ACTIONS];
        this.visits = new int[q.length][N_ACTIONS];
        // Prior informé : bouger coûte le coût de changement d'un cran, connu a priori.
        // Une table VIERGE en greedy pur garde donc le seuil en place ; dès que des
        // violations ou des coûts sont observés, le TD-learning surpasse ce prior.
        double stepCost = CHANGE_WEIGHT * resolution / (maxValue - minValue);
        for (double[] row : q) {
            row[0] = -stepCost;
            row[KEEP] = 0.0;
            row[2] = -stepCost;
        }
        this.value = clamp(initialValue);
    }

    /**
     * Observe le stress courant (signal lissé EMA), met à jour la Q-table (TD-learning)
     * puis choisit (ε-greedy) de baisser, garder ou monter le seuil d'un cran. Appelé à CHAQUE requête : le
     * moment d'un changement de seuil est une décision apprise, pas une cadence.
     *
     * @param violationRate taux de violations T_SLA lissé (EMA) [0,1]
     * @param costRatio     ratio lissé coût / contrat C_SLA (EMA)
     * @return la nouvelle valeur du seuil
     */
    public double observeAndAdjust(double violationRate, double costRatio) {
        return observeAndAdjust(violationRate, costRatio, 0.0, 0.0);
    }

    /**
     * Variante avec signal propre au seuil : la popularité des données (normalisée
     * [0,1], état — seuil de popularité seulement) et une charge pénalisée dans la
     * récompense ([0,1], même échelle que le taux de violations) : réplicas froids pour
     * le seuil de popularité, oscillations création/suppression (EMA) pour ΔT.
     */
    public double observeAndAdjust(double violationRate, double costRatio,
                                   double popularity, double coldReplicaLoad) {
        int s = stateOf(violationRate, costRatio, popularity);
        // Fidélité au contrat : pour TOUS les méta-contrôleurs, le contrat est la borne
        // haute (maxValue = 1.0) et le terme pénalise l'ÉCART à ce contrat, pas un
        // « relâchement » au sens opérationnel — le sens d'une baisse diffère selon le
        // seuil : popularité → réplication plus précoce ; T_SLA et C_SLA internes →
        // exigence plus stricte que le contrat (réplication plus précoce, agent plus
        // économe) ; ΔT → suppression plus rapide. Dans les quatre cas la baisse engage
        // des actions (et des coûts) que le contrat seul ne déclencherait pas : elle
        // doit se payer en violations ou en coût évités. Le coût d'événement
        // (CHANGE_WEIGHT × changement introduit par la dernière action) rend les
        // changements-éclair non gratuits.
        double deviation = (maxValue - value) / (maxValue - minValue);
        double reward = -violationRate - Math.max(0.0, costRatio - 1.0)
            - COST_LINEAR_WEIGHT * costRatio
            - fidelityWeight * deviation
            - CHANGE_WEIGHT * lastChange
            - COLD_REPLICA_WEIGHT * coldReplicaLoad;

        int level = levelOf(value);
        if (learning && lastState >= 0 && lastAction >= 0) {
            double best = maxQ(s, level);
            q[lastState][lastAction] += ALPHA * (reward + GAMMA * best - q[lastState][lastAction]);
            visits[lastState][lastAction]++;
        }

        int a = (rnd.nextDouble() < epsilon) ? randomValidAction(level) : argmaxQ(s, level);
        double newValue = clamp(minValue + (level + a - KEEP) * resolution);
        lastChange = Math.abs(value - newValue) / (maxValue - minValue);
        lastState = s;
        lastAction = a;
        value = newValue;
        return value;
    }

    /**
     * Retire le terme de fidélité au contrat (seuil de popularité). Ce seuil part du
     * plancher P_SLA (popularité atteinte après τ = 1/λ accès consécutifs) et non du
     * contrat : en dessous, aucune réplication n'est permise ; au-dessus, c'est à
     * l'agent de décider. Le méta-contrôleur ne le remonte que si les réplicas froids
     * (terme coldReplicaLoad) ou les violations le justifient. Avec un terme de
     * fidélité, il restait au contrat (200 accès), soit le déclenchement de TCDRM.
     */
    public ThresholdMetaLearner withoutFidelity() {
        this.fidelityWeight = 0.0;
        return this;
    }

    /**
     * Réinitialise la trajectoire pour un nouvel épisode/run : le seuil repart de sa
     * valeur initiale, la CONNAISSANCE (Q-table) est conservée.
     */
    public void startEpisode(double initialValue) {
        this.value = clamp(initialValue);
        this.lastState = -1;
        this.lastAction = -1;
        this.lastChange = 0.0;
    }

    public double getValue() { return value; }

    /** Active ou fige l'apprentissage TD (évaluation en ligne ou politique figée). */
    public void setLearning(boolean on) { this.learning = on; }

    public void setEpsilon(double e) { this.epsilon = Math.max(0.0, Math.min(1.0, e)); }

    // === Discrétisation de l'état (buckets standards, pas des règles de décision) ===

    private int stateOf(double violationRate, double costRatio, double popularity) {
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
        int pb = Math.max(0, Math.min(popBuckets - 1, (int) (popularity * popBuckets)));
        return ((vb * COST_BUCKETS + cb) * popBuckets + pb) * nLevels + valB;
    }

    private double clamp(double v) { return Math.max(minValue, Math.min(maxValue, v)); }

    /** Vrai si l'action {@code a} reste dans l'intervalle depuis le niveau {@code level}. */
    private boolean isValid(int a, int level) {
        int target = level + a - KEEP;
        return target >= 0 && target < nLevels;
    }

    /** Vrai si l'action {@code a} est valide et a déjà été jouée dans l'état {@code s}. */
    private boolean isKnown(int s, int a, int level) {
        return isValid(a, level) && visits[s][a] > 0;
    }

    /**
     * Valeur de l'état : maximum sur les actions VALIDES déjà JOUÉES (voir
     * {@link #visits}) ; « garder » sert de valeur par défaut d'un état jamais visité.
     */
    private double maxQ(int s, int level) {
        double m = Double.NEGATIVE_INFINITY;
        for (int a = 0; a < N_ACTIONS; a++) if (isKnown(s, a, level)) m = Math.max(m, q[s][a]);
        return m == Double.NEGATIVE_INFINITY ? q[s][KEEP] : m;
    }

    private int randomValidAction(int level) {
        int a;
        do { a = rnd.nextInt(N_ACTIONS); } while (!isValid(a, level));
        return a;
    }

    /** Index de niveau correspondant à une valeur de seuil. */
    private int levelOf(double v) {
        int lvl = (int) Math.round((v - minValue) / resolution);
        return Math.max(0, Math.min(nLevels - 1, lvl));
    }

    /**
     * Argmax sur les actions valides déjà jouées, avec tie-break sur « garder » ;
     * « garder » si aucune ne l'a été (les autres ne sont essayées que par ε).
     */
    private int argmaxQ(int s, int level) {
        int best = -1;
        for (int a = 0; a < N_ACTIONS; a++) {
            if (!isKnown(s, a, level)) continue;
            if (best < 0 || q[s][a] > q[s][best] || (q[s][a] == q[s][best] && a == KEEP)) best = a;
        }
        return best < 0 ? KEEP : best;
    }

    // === Persistance (texte simple : 1 ligne d'en-tête + 1 ligne par état) ===

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(String.format(Locale.ROOT, "# qtable-visits states=%d actions=%d min=%s max=%s resolution=%s%n",
                q.length, N_ACTIONS, minValue, maxValue, resolution));
            for (int s = 0; s < q.length; s++) {
                StringBuilder sb = new StringBuilder();
                for (int a = 0; a < N_ACTIONS; a++) {
                    if (a > 0) sb.append(' ');
                    sb.append(q[s][a]);
                }
                for (int a = 0; a < N_ACTIONS; a++) sb.append(' ').append(visits[s][a]);
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
        return loadOrCreate(file, initialValue, minValue, maxValue, resolution, epsilon, seed, 1);
    }

    public static ThresholdMetaLearner loadOrCreate(File file, double initialValue, double minValue,
                                                    double maxValue, double resolution, double epsilon, long seed,
                                                    int popBuckets) {
        // Ancien format (# qtable-inc, sans compteurs de visites) : incompatible, table vierge.
        ThresholdMetaLearner learner = new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed, popBuckets);
        if (file == null || !file.isFile()) return learner;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String header = r.readLine();
            String expected = "# qtable-visits states=" + learner.q.length + " actions=" + N_ACTIONS + " ";
            if (header == null || !header.startsWith(expected)) {
                return learner; // structure incompatible → repartir vierge
            }
            for (int s = 0; s < learner.q.length; s++) {
                String line = r.readLine();
                if (line == null) return new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed, popBuckets);
                String[] parts = line.trim().split("\\s+");
                for (int a = 0; a < N_ACTIONS && a < parts.length; a++) {
                    learner.q[s][a] = Double.parseDouble(parts[a]);
                }
                for (int a = 0; a < N_ACTIONS && N_ACTIONS + a < parts.length; a++) {
                    learner.visits[s][a] = Integer.parseInt(parts[N_ACTIONS + a]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            return new ThresholdMetaLearner(initialValue, minValue, maxValue, resolution, epsilon, seed, popBuckets);
        }
        return learner;
    }
}
