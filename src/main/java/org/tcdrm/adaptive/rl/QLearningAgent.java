package org.tcdrm.adaptive.rl;

/**
 * Agent Q-Learning pour TCDRM-ADAPTIVE
 * Implémente l'algorithme Q-Learning pour apprendre la politique optimale de réplication
 */
public class QLearningAgent {
    
    private final QTable qTable;
    private final Environment<TcdrmState, TcdrmAction> environment;
    
    // Hyperparamètres Q-Learning
    private double alpha;           // Taux d'apprentissage (learning rate)
    private double gamma;           // Facteur de discount
    private double epsilon;         // Probabilité d'exploration
    private double epsilonDecay;    // Décroissance d'epsilon
    private double epsilonMin;      // Epsilon minimum
    
    public QLearningAgent(Environment<TcdrmState, TcdrmAction> environment,
                         double alpha, double gamma, double epsilon, 
                         double epsilonDecay, double epsilonMin) {
        this.environment = environment;
        this.qTable = new QTable(environment.getStateSpaceSize(), environment.getActionSpaceSize());
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.epsilonDecay = epsilonDecay;
        this.epsilonMin = epsilonMin;
    }
    
    /**
     * Constructeur avec hyperparamètres par défaut
     */
    public QLearningAgent(Environment<TcdrmState, TcdrmAction> environment) {
        this(environment, 
             0.1,    // alpha: taux d'apprentissage
             0.95,   // gamma: facteur de discount
             1.0,    // epsilon: exploration initiale maximale
             0.995,  // epsilonDecay: décroissance lente
             0.01);  // epsilonMin: toujours un peu d'exploration
    }
    
    /**
     * Entraîne l'agent sur plusieurs épisodes
     * 
     * @param numEpisodes Nombre d'épisodes d'entraînement
     * @param seed Graine aléatoire pour reproductibilité
     * @return Statistiques d'entraînement
     */
    public TrainingStats train(int numEpisodes, Long seed) {
        TrainingStats stats = new TrainingStats(numEpisodes);
        
        System.out.println("=== Début de l'entraînement Q-Learning ===");
        System.out.println("Épisodes: " + numEpisodes);
        System.out.println("Alpha: " + alpha);
        System.out.println("Gamma: " + gamma);
        System.out.println("Epsilon initial: " + epsilon);
        System.out.println();
        
        for (int episode = 0; episode < numEpisodes; episode++) {
            double episodeReward = trainEpisode(seed != null ? seed + episode : null);
            stats.recordEpisode(episode, episodeReward, epsilon);
            
            // Décrémenter epsilon
            epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
            
            // Afficher progression
            if ((episode + 1) % 10 == 0) {
                System.out.println(String.format("Épisode %d/%d - Récompense: %.2f - Epsilon: %.4f",
                    episode + 1, numEpisodes, episodeReward, epsilon));
            }
        }
        
        System.out.println("\n=== Entraînement terminé ===");
        stats.printSummary();
        qTable.printStatistics();
        qTable.printBestActions(10);
        
        return stats;
    }
    
    /**
     * Entraîne l'agent sur un épisode
     * 
     * @param seed Graine aléatoire
     * @return Récompense totale de l'épisode
     */
    private double trainEpisode(Long seed) {
        TcdrmState state = environment.reset(seed);
        double totalReward = 0.0;
        int steps = 0;
        
        while (true) {
            // Choisir action avec epsilon-greedy
            int stateIndex = state.toIndex();
            int actionIndex = qTable.chooseAction(stateIndex, epsilon);
            TcdrmAction action = TcdrmAction.fromValue(actionIndex);
            
            // Exécuter l'action
            Environment.StepResult<TcdrmState> result = environment.step(action);
            
            // Mettre à jour Q-table
            int nextStateIndex = result.getNextState().toIndex();
            qTable.update(stateIndex, actionIndex, result.getReward(), 
                         nextStateIndex, alpha, gamma);
            
            // Accumuler récompense
            totalReward += result.getReward();
            steps++;
            
            // Passer à l'état suivant
            state = result.getNextState();
            
            // Vérifier si l'épisode est terminé
            if (result.isDone()) {
                break;
            }
        }
        
        return totalReward;
    }
    
    /**
     * Évalue la politique apprise (sans exploration)
     * 
     * @param numEpisodes Nombre d'épisodes d'évaluation
     * @param seed Graine aléatoire
     * @return Récompense moyenne
     */
    public double evaluate(int numEpisodes, Long seed) {
        double totalReward = 0.0;
        
        for (int episode = 0; episode < numEpisodes; episode++) {
            TcdrmState state = environment.reset(seed != null ? seed + episode : null);
            double episodeReward = 0.0;
            
            while (true) {
                // Toujours choisir la meilleure action (epsilon = 0)
                int stateIndex = state.toIndex();
                int actionIndex = qTable.getBestAction(stateIndex);
                TcdrmAction action = TcdrmAction.fromValue(actionIndex);
                
                // Exécuter l'action
                Environment.StepResult<TcdrmState> result = environment.step(action);
                
                episodeReward += result.getReward();
                state = result.getNextState();
                
                if (result.isDone()) {
                    break;
                }
            }
            
            totalReward += episodeReward;
        }
        
        return totalReward / numEpisodes;
    }
    
    /**
     * Obtient la meilleure action pour un état donné
     */
    public TcdrmAction getBestAction(TcdrmState state) {
        int actionIndex = qTable.getBestAction(state.toIndex());
        return TcdrmAction.fromValue(actionIndex);
    }
    
    public QTable getQTable() {
        return qTable;
    }
    
    public double getEpsilon() {
        return epsilon;
    }
    
    /**
     * Classe pour stocker les statistiques d'entraînement
     */
    public static class TrainingStats {
        private final double[] episodeRewards;
        private final double[] epsilonValues;
        private int currentEpisode;
        
        public TrainingStats(int numEpisodes) {
            this.episodeRewards = new double[numEpisodes];
            this.epsilonValues = new double[numEpisodes];
            this.currentEpisode = 0;
        }
        
        public void recordEpisode(int episode, double reward, double epsilon) {
            if (episode < episodeRewards.length) {
                episodeRewards[episode] = reward;
                epsilonValues[episode] = epsilon;
                currentEpisode = episode + 1;
            }
        }
        
        public void printSummary() {
            if (currentEpisode == 0) return;
            
            double sum = 0.0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            
            for (int i = 0; i < currentEpisode; i++) {
                sum += episodeRewards[i];
                min = Math.min(min, episodeRewards[i]);
                max = Math.max(max, episodeRewards[i]);
            }
            
            double avg = sum / currentEpisode;
            
            // Calculer moyenne des 10 derniers épisodes
            int lastN = Math.min(10, currentEpisode);
            double lastSum = 0.0;
            for (int i = currentEpisode - lastN; i < currentEpisode; i++) {
                lastSum += episodeRewards[i];
            }
            double lastAvg = lastSum / lastN;
            
            System.out.println("\n=== Statistiques d'entraînement ===");
            System.out.println("Épisodes complétés: " + currentEpisode);
            System.out.println("Récompense min: " + String.format("%.2f", min));
            System.out.println("Récompense max: " + String.format("%.2f", max));
            System.out.println("Récompense moyenne: " + String.format("%.2f", avg));
            System.out.println("Récompense moyenne (10 derniers): " + String.format("%.2f", lastAvg));
            System.out.println("Epsilon final: " + String.format("%.4f", epsilonValues[currentEpisode - 1]));
        }
        
        public double[] getEpisodeRewards() {
            return episodeRewards;
        }
        
        public double[] getEpsilonValues() {
            return epsilonValues;
        }
    }
}
