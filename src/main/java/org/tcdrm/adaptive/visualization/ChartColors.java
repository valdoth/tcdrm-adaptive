package org.tcdrm.adaptive.visualization;

import java.awt.Color;

/**
 * Centralized color definitions for TCDRM charts.
 * Matches paper figures styling.
 */
public final class ChartColors {
    
    private ChartColors() {}
    
    // Model colors (line charts)
    public static final Color TCDRM = new Color(66, 133, 244);      // blue
    public static final Color NOREP = new Color(234, 67, 53);       // red
    public static final Color QLEARNING = new Color(251, 188, 4);   // yellow
    public static final Color DQN = new Color(52, 168, 83);         // green
    
    // Bar chart colors
    public static final Color INTER_PROVIDER = new Color(66, 133, 244);  // blue
    public static final Color INTER_REGION = new Color(234, 67, 53);     // red
    public static final Color CPU = new Color(66, 133, 244);             // blue
    public static final Color BANDWIDTH = new Color(234, 67, 53);        // red
    public static final Color REPLICA = new Color(251, 188, 4);          // yellow
}
