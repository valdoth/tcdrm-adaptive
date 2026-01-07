package org.tcdrm.adaptive.rl;

/**
 * Représente les actions possibles dans l'environnement TCDRM
 */
public enum TcdrmAction {
    CREATE_REPLICA(0, "Créer un réplica"),
    DELETE_REPLICA(1, "Supprimer un réplica"),
    DO_NOTHING(2, "Ne rien faire");
    
    private final int value;
    private final String description;
    
    TcdrmAction(int value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public int getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static TcdrmAction fromValue(int value) {
        for (TcdrmAction action : values()) {
            if (action.value == value) {
                return action;
            }
        }
        throw new IllegalArgumentException("Invalid action value: " + value);
    }
    
    public static int getActionSpaceSize() {
        return values().length;
    }
}
