package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for wall-segment portals that appear after puzzle completion.
 * 
 * Unlike ExitComponent (which represents always-visible exit zones),
 * PortalComponent represents a contiguous segment of wall tiles that:
 * - Are invisible/dormant until the associated puzzle is solved
 * - Turn green (portal color) when activated
 * - Trigger level transition when player enters
 * 
 * This component is attached to a group of wall entities that form the portal segment.
 * When activated, the wall tiles' collision is disabled and their color changes to green.
 * 
 * @see com.timonipumba.systems.PortalSystem for activation logic
 * @see PortalConfig for configuration options
 */
public class PortalComponent implements Component {
    
    /** ID of the puzzle that must be solved to activate this portal */
    public String puzzleId;
    
    /** Whether the portal is currently active (visible and usable) */
    public boolean active = false;
    
    /** Target map path for level transition (null for next level in sequence) */
    public String targetMap = null;
    
    /** Index of this tile within the portal segment (0 = first tile) */
    public int segmentIndex = 0;
    
    /** Total number of tiles in this portal segment */
    public int segmentLength = 1;
    
    /** Unique group ID linking all tiles in this portal segment */
    public String portalGroupId;
    
    public PortalComponent() {}
    
    /**
     * Create a portal component linked to a puzzle.
     * 
     * @param puzzleId ID of the puzzle that activates this portal
     */
    public PortalComponent(String puzzleId) {
        this.puzzleId = puzzleId;
    }
    
    /**
     * Create a portal component with full configuration.
     * 
     * @param puzzleId ID of the puzzle that activates this portal
     * @param portalGroupId Unique ID linking tiles in this portal segment
     * @param segmentIndex Index within the segment
     * @param segmentLength Total segment length
     */
    public PortalComponent(String puzzleId, String portalGroupId, int segmentIndex, int segmentLength) {
        this.puzzleId = puzzleId;
        this.portalGroupId = portalGroupId;
        this.segmentIndex = segmentIndex;
        this.segmentLength = segmentLength;
    }
    
    /**
     * Activate this portal tile.
     * Called when the associated puzzle is solved.
     */
    public void activate() {
        this.active = true;
    }
    
    /**
     * Check if the portal is active and usable.
     * 
     * @return true if player can use this portal
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Check if this portal has an associated puzzle.
     * 
     * @return true if puzzleId is set
     */
    public boolean hasPuzzle() {
        return puzzleId != null && !puzzleId.isEmpty();
    }
}
