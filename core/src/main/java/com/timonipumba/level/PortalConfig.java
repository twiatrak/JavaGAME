package com.timonipumba.level;

/**
 * Configuration class for wall-segment portals.
 * 
 * Controls portal behavior including:
 * - Segment length constraints (min/max tiles)
 * - Feature flag for enabling wall-segment portals vs legacy exits
 * - Wall preference for portal placement
 * - Visual styling configuration
 */
public class PortalConfig {
    
    /** Minimum number of consecutive wall tiles for a portal segment */
    public static final int MIN_SEGMENT_LENGTH = 4;
    
    /** Maximum number of consecutive wall tiles for a portal segment */
    public static final int MAX_SEGMENT_LENGTH = 5;
    
    /** Default preferred segment length */
    public static final int PREFERRED_SEGMENT_LENGTH = 4;
    
    /**
     * Feature flag: When true, uses wall-segment portals.
     * When false, uses gate-based progression (recommended).
     * 
     * Default is false (gate-based progression enabled).
     * Wall-segment portals have been deprecated due to issues with
     * black squares rendering and confusing teleportation behavior.
     * 
     * @see com.timonipumba.systems.GateSystem for gate-based progression
     */
    public static boolean WALL_SEGMENT_PORTALS_ENABLED = false;
    
    /**
     * When true, legacy exit entities are hidden until portal is activated.
     * When false, legacy exits remain always visible (backward compatibility).
     */
    public static boolean HIDE_LEGACY_EXITS = true;
    
    /**
     * Preferred wall directions for portal placement, in order of preference.
     * N = North (top), E = East (right), S = South (bottom), W = West (left)
     */
    public enum WallDirection {
        NORTH, EAST, SOUTH, WEST
    }
    
    /** Default wall preference order */
    public static final WallDirection[] PREFERRED_WALLS = {
        WallDirection.NORTH, 
        WallDirection.EAST, 
        WallDirection.SOUTH, 
        WallDirection.WEST
    };
    
    /** 
     * Whether to use deterministic (seeded) portal placement.
     * If true, same puzzle will always generate portal in same location.
     */
    public static boolean DETERMINISTIC_PLACEMENT = true;
    
    /** Seed modifier for deterministic placement (combined with puzzle ID hash) */
    public static long PLACEMENT_SEED = 42L;
    
    /**
     * Reset all configuration to default values.
     * Useful for testing.
     */
    public static void resetToDefaults() {
        WALL_SEGMENT_PORTALS_ENABLED = false;
        HIDE_LEGACY_EXITS = true;
        DETERMINISTIC_PLACEMENT = true;
        PLACEMENT_SEED = 42L;
    }
    
    private PortalConfig() {} // Prevent instantiation
}
