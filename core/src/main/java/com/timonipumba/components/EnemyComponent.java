package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component for enemy entities with basic AI state.
 * 
 * This component tracks:
 * - Basic movement properties (speed, detection range)
 * - Idle behavior timers
 * - Aggro state (whether enemy is actively pursuing player)
 * - Strafe timer for scout-type flanking behavior
 * - Arena association for gate progression logic
 * 
 * For type-specific stats (HP, damage, vision), use EnemyStatsComponent.
 */
public class EnemyComponent implements Component {
    /**
     * ID of the arena this enemy belongs to.
     * Used by GateSystem to track when all enemies in an arena are defeated.
     * Gates open only when all enemies with matching sourceArenaId are killed.
     * Null means this enemy is not associated with any specific arena.
     */
    public String arenaId = null;
    
    /** Base movement speed (may be overridden by EnemyStatsComponent) */
    public float speed = 50f;
    
    /** Detection range for player (may be overridden by EnemyStatsComponent) */
    public float detectionRange = 100f;
    
    /** Timer for idle behavior */
    public float idleTimer = 0f;
    
    /** Duration of idle phase before changing behavior */
    public float idleDuration = 2f;
    
    /** 
     * Whether this enemy is currently aggroed (actively pursuing player).
     * Enemies start idle and become aggroed when player enters aggro radius.
     * Once aggroed, they pursue until player leaves vision range.
     */
    public boolean isAggroed = false;
    
    /**
     * Timer for scout strafe behavior.
     * Scouts periodically change strafe direction to flank the player.
     */
    public float strafeTimer = 0f;
    
    /**
     * Current strafe direction for scouts: 1 = strafe right, -1 = strafe left.
     * Perpendicular to the direction toward the player.
     */
    public int strafeDirection = 1;
}
