package org.tcdrm.adaptive.rl;

/**
 * Interface Environment inspirée de Gymnasium pour l'apprentissage par renforcement
 * Définit le contrat pour les environnements RL compatibles avec TCDRM-ADAPTIVE
 * 
 * @param <S> Type de l'état (State)
 * @param <A> Type de l'action (Action)
 */
public interface Environment<S, A> {
    
    /**
     * Réinitialise l'environnement à un état initial
     * Équivalent à gymnasium.Env.reset()
     * 
     * @param seed Graine aléatoire pour la reproductibilité (optionnel)
     * @return État initial de l'environnement
     */
    S reset(Long seed);
    
    /**
     * Exécute une action dans l'environnement et retourne le résultat
     * Équivalent à gymnasium.Env.step(action)
     * 
     * @param action Action à exécuter
     * @return Résultat de l'étape (état suivant, récompense, terminé, info)
     */
    StepResult<S> step(A action);
    
    /**
     * Retourne l'espace des actions possibles
     * 
     * @return Nombre d'actions possibles
     */
    int getActionSpaceSize();
    
    /**
     * Retourne l'espace des états possibles
     * 
     * @return Nombre d'états possibles
     */
    int getStateSpaceSize();
    
    /**
     * Ferme l'environnement et libère les ressources
     */
    void close();
    
    /**
     * Classe interne représentant le résultat d'une étape
     * Équivalent au tuple (observation, reward, terminated, truncated, info) de Gymnasium
     */
    class StepResult<S> {
        private final S nextState;
        private final double reward;
        private final boolean terminated;
        private final boolean truncated;
        private final String info;
        
        public StepResult(S nextState, double reward, boolean terminated, boolean truncated, String info) {
            this.nextState = nextState;
            this.reward = reward;
            this.terminated = terminated;
            this.truncated = truncated;
            this.info = info;
        }
        
        public S getNextState() {
            return nextState;
        }
        
        public double getReward() {
            return reward;
        }
        
        public boolean isTerminated() {
            return terminated;
        }
        
        public boolean isTruncated() {
            return truncated;
        }
        
        public String getInfo() {
            return info;
        }
        
        public boolean isDone() {
            return terminated || truncated;
        }
    }
}
