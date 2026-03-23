package org.tcdrm.adaptive.data;

import java.util.List;

/**
 * Représente une requête dans le système.
 * Une requête accède à un ensemble de relations.
 */
public class Query {
    private final int id;
    private final List<String> relationIds;  // Relations accédées par cette requête
    private final String sourceDatacenter;   // DC d'où provient la requête
    private final boolean isRead;            // true = lecture, false = écriture
    
    public Query(int id, List<String> relationIds, String sourceDatacenter, boolean isRead) {
        this.id = id;
        this.relationIds = relationIds;
        this.sourceDatacenter = sourceDatacenter;
        this.isRead = isRead;
    }
    
    public int getId() {
        return id;
    }
    
    public List<String> getRelationIds() {
        return relationIds;
    }
    
    public String getSourceDatacenter() {
        return sourceDatacenter;
    }
    
    public boolean isRead() {
        return isRead;
    }
    
    public String getSourceRegion() {
        return sourceDatacenter.split("_")[1];
    }
    
    @Override
    public String toString() {
        return String.format("Query[%d, %s, %d relations from %s]", 
            id, isRead ? "READ" : "WRITE", relationIds.size(), sourceDatacenter);
    }
}
