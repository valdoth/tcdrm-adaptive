package org.tcdrm.adaptive.data;

/**
 * Représente une relation dans le système multi-cloud.
 * Une relation est stockée dans un datacenter spécifique.
 */
public class Relation {
    private final String id;
    private final String homeDatacenter;  // DC d'origine (ex: "Google_US_sub_region1_DC1")
    private final double sizeGb;
    
    public Relation(String id, String homeDatacenter, double sizeGb) {
        this.id = id;
        this.homeDatacenter = homeDatacenter;
        this.sizeGb = sizeGb;
    }
    
    public String getId() {
        return id;
    }
    
    public String getHomeDatacenter() {
        return homeDatacenter;
    }
    
    public double getSizeGb() {
        return sizeGb;
    }
    
    public String getProvider() {
        return homeDatacenter.split("_")[0];
    }
    
    public String getRegion() {
        return homeDatacenter.split("_")[1];
    }
    
    public String getSubRegion() {
        String[] parts = homeDatacenter.split("_");
        return parts.length > 2 ? parts[2] : "";
    }
    
    @Override
    public String toString() {
        return String.format("Relation[%s @ %s, %.2f GB]", id, homeDatacenter, sizeGb);
    }
}
