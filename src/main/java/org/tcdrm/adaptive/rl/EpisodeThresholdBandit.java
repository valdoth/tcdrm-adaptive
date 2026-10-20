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
 * Apprentissage du seuil relatif de popularité ρ PAR ÉPISODE : bandit manchot
 * ε-greedy (Sutton &amp; Barto, Reinforcement Learning: An Introduction, 2e éd. 2018,
 * chap. 2) dont chaque bras est un niveau de ρ et dont la récompense est le coût
 * moyen par requête de l'épisode joué avec ce niveau.
 *
 * Pourquoi pas le méta-contrôleur par requête ({@link ThresholdMetaLearner}) : l'effet
 * de ρ sur le coût passe par des réplicas créés puis lus pendant des centaines de
 * requêtes. Une Q-table qui le change à chaque requête ne voyait qu'un signal noyé
 * dans le bruit : ρ errait entre 0,1 et 0,9 au cours d'un même run, et selon
 * l'entraînement le coût en charge variable (complexe) allait de 17 à 22,6 $, contre
 * 16,9 ± 0,2 $ avec ce bandit (sonde : agent glouton, 5 entraînements de 750 épisodes,
 * graines 777–781). Le coût d'épisode mesure directement l'effet de ρ.
 *
 * Bras jamais joués d'abord (ordre de la grille), puis ε-greedy sur le coût moyen.
 * La valeur retenue pour l'évaluation est le bras de plus faible coût moyen ; tant
 * qu'aucun bras n'a été joué, c'est la valeur initiale (ρ = 1, seules les données
 * les plus populaires).
 */
public final class EpisodeThresholdBandit {

    /** Exploration ε-greedy (Sutton &amp; Barto 2018, §2.3 : ε = 0,1). */
    public static final double EPSILON = 0.1;

    private final double minValue;
    private final double resolution;
    private final double initialValue;
    private final double[] costSum;
    private final int[] plays;
    private final Random rnd;

    public EpisodeThresholdBandit(double initialValue, double minValue, double maxValue,
                                  double resolution, long seed) {
        if (maxValue <= minValue || resolution <= 0) {
            throw new IllegalArgumentException("Invalid threshold bounds/resolution");
        }
        this.minValue = minValue;
        this.resolution = resolution;
        this.initialValue = initialValue;
        int n = (int) Math.round((maxValue - minValue) / resolution) + 1;
        this.costSum = new double[n];
        this.plays = new int[n];
        this.rnd = new Random(seed);
    }

    /** Niveau à jouer pour un épisode d'ENTRAÎNEMENT (exploration comprise). */
    public double select() {
        for (int i = 0; i < plays.length; i++) if (plays[i] == 0) return valueOf(i);
        if (rnd.nextDouble() < EPSILON) return valueOf(rnd.nextInt(plays.length));
        return valueOf(bestIndex());
    }

    /** Niveau de plus faible coût moyen (évaluation) ; valeur initiale si rien n'a été joué. */
    public double best() {
        int b = bestIndex();
        return b < 0 ? initialValue : valueOf(b);
    }

    /** Enregistre le coût moyen par requête d'un épisode joué avec le niveau {@code value}. */
    public void update(double value, double avgCostPerQuery) {
        int i = indexOf(value);
        costSum[i] += avgCostPerQuery;
        plays[i]++;
    }

    private int bestIndex() {
        int b = -1;
        for (int i = 0; i < plays.length; i++) {
            if (plays[i] == 0) continue;
            if (b < 0 || costSum[i] / plays[i] < costSum[b] / plays[b]) b = i;
        }
        return b;
    }

    private double valueOf(int i) { return minValue + i * resolution; }

    private int indexOf(double v) {
        int i = (int) Math.round((v - minValue) / resolution);
        return Math.max(0, Math.min(plays.length - 1, i));
    }

    // === Persistance (texte simple : 1 ligne d'en-tête + 1 ligne « somme compte » par bras) ===

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write(String.format(Locale.ROOT, "# bandit arms=%d min=%s resolution=%s%n",
                plays.length, minValue, resolution));
            for (int i = 0; i < plays.length; i++) {
                w.write(costSum[i] + " " + plays[i] + System.lineSeparator());
            }
        }
    }

    /** Charge un bandit existant si compatible (même grille), sinon en crée un vierge. */
    public static EpisodeThresholdBandit loadOrCreate(File file, double initialValue, double minValue,
                                                      double maxValue, double resolution, long seed) {
        EpisodeThresholdBandit b = new EpisodeThresholdBandit(initialValue, minValue, maxValue, resolution, seed);
        if (file == null || !file.isFile()) return b;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String header = r.readLine();
            String expected = String.format(Locale.ROOT, "# bandit arms=%d min=%s resolution=%s",
                b.plays.length, minValue, resolution);
            if (header == null || !header.equals(expected)) return b;
            for (int i = 0; i < b.plays.length; i++) {
                String line = r.readLine();
                if (line == null) return new EpisodeThresholdBandit(initialValue, minValue, maxValue, resolution, seed);
                String[] p = line.trim().split("\\s+");
                b.costSum[i] = Double.parseDouble(p[0]);
                b.plays[i] = Integer.parseInt(p[1]);
            }
        } catch (IOException | RuntimeException e) {
            return new EpisodeThresholdBandit(initialValue, minValue, maxValue, resolution, seed);
        }
        return b;
    }
}
