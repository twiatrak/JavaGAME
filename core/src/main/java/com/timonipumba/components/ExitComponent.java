package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component for exit/goal zones that trigger level completion.
 * 
 * When the player overlaps an entity with ExitComponent, the game
 * transitions to LEVEL_CLEAR state. The player can then press N to
 * advance to the next map.
 * 
 * Tiled object properties:
 * - type="exit" (required)
 * - targetMap: String, optional path to specific map to load next (reserved for future use)
 * 
 * Example Tiled object:
 * {@code
 * <object type="exit">
 *   <properties>
 *     <property name="targetMap" value="maps/level2.tmx"/>
 *   </properties>
 * </object>
 * }
 * 
 * Visual representation:
 * - Exits are typically rendered as a glowing or highlighted area
 * - Default color is Color.LIME for visibility
 */
public class ExitComponent implements Component {
    
    /** Optional target map path for future use (e.g., specific level transitions) */
    public String targetMap = null;
    
    public ExitComponent() {}
    
    public ExitComponent(String targetMap) {
        this.targetMap = targetMap;
    }
}
