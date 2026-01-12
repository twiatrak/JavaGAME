package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for ranged attack windup visual feedback.
 * 
 * When a ranged enemy (RANGER type) is preparing to fire:
 * - isWindingUp is set to true and windupTimer is set to the windup duration
 * - RenderingSystem can tint the enemy differently to signal the impending attack
 * - When windupTimer reaches 0, the enemy fires the projectile
 * 
 * This gives the player a visual cue to dodge incoming ranged attacks,
 * making combat more readable and fair.
 * 
 * Usage:
 * - EnemyAISystem starts windup when ranger is ready to attack
 * - RenderingSystem checks this component to apply windup visual (tint/glow)
 * - When windup completes, EnemyAISystem fires the projectile
 */
public class RangedAttackWindupComponent implements Component {
    
    /** Whether the entity is currently winding up a ranged attack */
    public boolean isWindingUp = false;
    
    /** Time remaining until the attack fires (seconds) */
    public float windupTimer = 0f;
    
    /** Direction to fire when windup completes (normalized X) */
    public float targetDirX = 0f;
    
    /** Direction to fire when windup completes (normalized Y) */
    public float targetDirY = 0f;
    
    public RangedAttackWindupComponent() {}
    
    /**
     * Start a windup animation for a ranged attack.
     * @param duration Windup duration in seconds
     * @param dirX Normalized X direction to fire
     * @param dirY Normalized Y direction to fire
     */
    public void startWindup(float duration, float dirX, float dirY) {
        this.isWindingUp = true;
        this.windupTimer = duration;
        this.targetDirX = dirX;
        this.targetDirY = dirY;
    }
    
    /**
     * Update the windup timer.
     * @param deltaTime Time elapsed since last update
     * @return true if windup just completed (timer hit 0 this frame)
     */
    public boolean update(float deltaTime) {
        if (!isWindingUp) {
            return false;
        }
        
        float previousTimer = windupTimer;
        windupTimer -= deltaTime;
        
        if (windupTimer <= 0 && previousTimer > 0) {
            // Windup just completed this frame
            windupTimer = 0;
            isWindingUp = false;
            return true;
        }
        
        return false;
    }
    
    /**
     * Cancel the current windup (e.g., if target moves out of range).
     */
    public void cancelWindup() {
        isWindingUp = false;
        windupTimer = 0;
        targetDirX = 0;
        targetDirY = 0;
    }
    
    /**
     * Check if currently winding up.
     * @return true if windup is in progress
     */
    public boolean isWindingUp() {
        return isWindingUp;
    }
}
