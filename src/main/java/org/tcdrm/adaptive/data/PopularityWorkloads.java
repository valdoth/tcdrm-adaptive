package org.tcdrm.adaptive.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Workloads à popularité dynamique (inspirés des traces réelles Google/Alibaba utilisées
 * par les frameworks d'évaluation CloudSimPlus, cf. Intelligent-VM-Optimizer) :
 * un pool de fragments PLUS GRAND que ce qu'une requête touche, et une sélection
 * biaisée dont la distribution évolue au fil des requêtes.
 *
 * Deux régimes, entièrement pilotés par la seed (reproductibles, et IDENTIQUES pour
 * tous les modèles d'un même benchmark — protocole d'équité conservé) :
 *
 * <ul>
 *   <li><b>VARIABLE</b> — popularité Zipf sur les fragments, avec dérive du hot-set :
 *       à intervalles aléatoires (seedés), une partie des rangs de popularité permute.
 *       Des données chaudes refroidissent, des froides deviennent chaudes — le moment
 *       où répliquer/supprimer varie d'une seed à l'autre et doit être APPRIS.</li>
 *   <li><b>BURST</b> — fond faiblement biaisé + pics soudains : un fragment aléatoire
 *       voit sa probabilité d'accès multipliée pendant une durée aléatoire, puis
 *       retombe. Teste la réactivité (réplication rapide) et la parcimonie
 *       (suppression après le pic).</li>
 * </ul>
 *
 * Chaque requête tire {@code perQuery} fragments distincts (3 simple / 6 complex),
 * ce qui préserve le modèle de coût et la calibration T_SLA du papier.
 */
public final class PopularityWorkloads {
    private PopularityWorkloads() {}

    /** Exposant Zipf du régime VARIABLE (skew classique des workloads OLAP). */
    private static final double ZIPF_S_VARIABLE = 1.0;
    /** Exposant Zipf du fond du régime BURST (biais léger). */
    private static final double ZIPF_S_BURST_BASE = 0.4;
    /** Multiplicateur de popularité d'un fragment en burst. */
    private static final double BURST_BOOST = 15.0;

    /** Régime VARIABLE : Zipf + dérive du hot-set à intervalles seedés. */
    public static List<int[]> generateVariable(int poolSize, int perQuery, int count, long seed) {
        Random rnd = new Random(seed);
        double[] rankWeight = zipfWeights(poolSize, ZIPF_S_VARIABLE);
        int[] rankOfFragment = shuffledIdentity(poolSize, rnd);

        List<int[]> sets = new ArrayList<>(count);
        int nextDrift = drawDriftInterval(rnd);
        for (int q = 0; q < count; q++) {
            if (q == nextDrift) {
                driftRanks(rankOfFragment, rnd);
                nextDrift = q + drawDriftInterval(rnd);
            }
            double[] w = new double[poolSize];
            for (int f = 0; f < poolSize; f++) w[f] = rankWeight[rankOfFragment[f]];
            sets.add(sampleDistinct(w, perQuery, rnd));
        }
        return sets;
    }

    /** Régime BURST : fond légèrement biaisé + pics soudains sur un fragment aléatoire. */
    public static List<int[]> generateBurst(int poolSize, int perQuery, int count, long seed) {
        Random rnd = new Random(seed);
        double[] base = zipfWeights(poolSize, ZIPF_S_BURST_BASE);
        int[] rankOfFragment = shuffledIdentity(poolSize, rnd);

        int burstFragment = -1;
        int burstEnd = -1;
        int nextBurst = 100 + rnd.nextInt(200);

        List<int[]> sets = new ArrayList<>(count);
        for (int q = 0; q < count; q++) {
            if (q == nextBurst) {
                burstFragment = rnd.nextInt(poolSize);
                burstEnd = q + 80 + rnd.nextInt(120);
                nextBurst = burstEnd + 60 + rnd.nextInt(180);
            }
            boolean bursting = burstFragment >= 0 && q < burstEnd;
            double[] w = new double[poolSize];
            for (int f = 0; f < poolSize; f++) {
                w[f] = base[rankOfFragment[f]];
                if (bursting && f == burstFragment) w[f] *= BURST_BOOST;
            }
            sets.add(sampleDistinct(w, perQuery, rnd));
        }
        return sets;
    }

    // === Internes ===

    private static double[] zipfWeights(int n, double s) {
        double[] w = new double[n];
        for (int r = 0; r < n; r++) w[r] = 1.0 / Math.pow(r + 1, s);
        return w;
    }

    private static int[] shuffledIdentity(int n, Random rnd) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        return a;
    }

    /** Intervalle entre deux dérives du hot-set : [150, 300] requêtes. */
    private static int drawDriftInterval(Random rnd) {
        return 150 + rnd.nextInt(151);
    }

    /** Permute ~1/4 des rangs de popularité (le hot-set se déplace partiellement). */
    private static void driftRanks(int[] rankOfFragment, Random rnd) {
        int n = rankOfFragment.length;
        int swaps = Math.max(1, n / 4);
        for (int s = 0; s < swaps; s++) {
            int a = rnd.nextInt(n);
            int b = rnd.nextInt(n);
            int t = rankOfFragment[a]; rankOfFragment[a] = rankOfFragment[b]; rankOfFragment[b] = t;
        }
    }

    /** Tirage pondéré de k fragments distincts (roulette sans remise). */
    private static int[] sampleDistinct(double[] weights, int k, Random rnd) {
        int n = weights.length;
        k = Math.min(k, n);
        double[] w = weights.clone();
        int[] out = new int[k];
        for (int pick = 0; pick < k; pick++) {
            double total = 0;
            for (double v : w) total += v;
            double r = rnd.nextDouble() * total;
            int chosen = n - 1;
            double acc = 0;
            for (int f = 0; f < n; f++) {
                acc += w[f];
                if (r <= acc) { chosen = f; break; }
            }
            out[pick] = chosen;
            w[chosen] = 0.0;
        }
        return out;
    }
}
