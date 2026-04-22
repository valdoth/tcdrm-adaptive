package org.tcdrm.adaptive;

/**
 * Options de ligne de commande pour {@link TcdrmMain}.
 */
public final class TcdrmMainArgs {

    /** Exécuter benchmarks + graphiques papier uniquement (pas de Py4J / RL). */
    public final boolean phase1Only;
    /** Forcer AWT headless pour XChart (serveurs CI, sandbox). */
    public final boolean headlessCharts;
    /** Attente max (secondes) du client Python pour la phase 2. */
    public final int pythonConnectTimeoutSec;
    /** Afficher l'aide et quitter. */
    public final boolean help;
    /** Si false, nivaux de log CloudSim Plus plus bavards (INFO). */
    public final boolean quietCloudSim;
    /** Exécuter uniquement la phase RL (pas de benchmarks papier). */
    public final boolean rlOnly;

    private TcdrmMainArgs(boolean phase1Only, boolean headlessCharts, int pythonConnectTimeoutSec,
                         boolean help, boolean quietCloudSim, boolean rlOnly) {
        this.phase1Only = phase1Only;
        this.headlessCharts = headlessCharts;
        this.pythonConnectTimeoutSec = pythonConnectTimeoutSec;
        this.help = help;
        this.quietCloudSim = quietCloudSim;
        this.rlOnly = rlOnly;
    }

    static TcdrmMainArgs parse(String[] args) {
        boolean phase1Only = false;
        boolean headlessCharts = true;
        int pyTimeout = 120;
        boolean help = false;
        boolean verbose = false;
        boolean rlOnly = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--phase1-only":
                    phase1Only = true;
                    break;
                case "--with-awt":
                    headlessCharts = false;
                    break;
                case "--headless":
                    headlessCharts = true;
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                case "--rl-only":
                    rlOnly = true;
                    break;
                case "--py-timeout":
                    if (i + 1 < args.length) {
                        pyTimeout = Math.max(5, Integer.parseInt(args[++i]));
                    }
                    break;
                case "-h":
                case "--help":
                    help = true;
                    break;
                default:
                    break;
            }
        }

        String envPy = System.getenv("TCDRM_PY_TIMEOUT_SEC");
        if (envPy != null && !envPy.isBlank()) {
            try {
                pyTimeout = Math.max(5, Integer.parseInt(envPy.trim()));
            } catch (NumberFormatException ignored) {
                // garde la valeur par défaut
            }
        }

        return new TcdrmMainArgs(phase1Only, headlessCharts, pyTimeout, help, !verbose, rlOnly);
    }

    static void printHelp() {
        System.out.println("""
            Usage: java ... TcdrmMain [options]

            Options:
              --phase1-only     Benchmarks NoRep/TCDRM + figures papier seulement (pas d'attente Python).
              --headless        Forcer java.awt.headless=true pour l'export PNG (défaut: activé).
              --with-awt        Désactiver headless (fenêtre / serveur graphique requis).
              --py-timeout N    Secondes d'attente du client Py4J (défaut: 120, env TCDRM_PY_TIMEOUT_SEC).
                            --rl-only        Exécuter uniquement la phase RL (Q-Learning + DQN), sans benchmarks papier.
              --verbose         Journaux CloudSim Plus détaillés (placement VM, arrêt DC, etc.).
              -h, --help        Affiche cette aide.
            """);
    }
}
