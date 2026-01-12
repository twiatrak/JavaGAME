package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.GameConstants;

/**
 * Component marking an entity as a projectile (arrow, bullet, magic bolt, etc.).
 * 
 * Projectiles are spawned by ranged attacks and travel in a direction until:
 * - They hit an enemy (apply damage, then despawn)
 * - They hit a wall (despawn)
 * - They exceed their maximum lifetime or range (despawn)
 * 
 * Projectile entities should also have:
 * - PositionComponent: Current position
 * - VelocityComponent: Direction and speed of travel
 * - CollisionComponent: Collision bounds (typically smaller than entity sprites)
 * - SpriteComponent or RenderableComponent: Visual representation
 * 
 * Properties:
 * - damage: Amount of damage dealt on hit
 * - lifetime: Maximum time before automatic despawn
 * - ownerIsPlayer: Whether this projectile was fired by the player
 */
public class ProjectileComponent implements Component {
    
    /** Damage dealt when this projectile hits a target */
    public int damage = GameConstants.RANGED_ATTACK_DAMAGE;
    
    /** Maximum lifetime in seconds before auto-despawn */
    public float lifetime = GameConstants.PROJECTILE_LIFETIME;
    
    /** Time elapsed since projectile was spawned */
    public float timeAlive = 0f;
    
    /** Whether this projectile was fired by the player (true) or enemy (false) */
    public boolean ownerIsPlayer = true;
    
    public ProjectileComponent() {}
    
    public ProjectileComponent(int damage, boolean ownerIsPlayer) {
        this.damage = damage;
        this.ownerIsPlayer = ownerIsPlayer;
    }
    
    public ProjectileComponent(int damage, float lifetime, boolean ownerIsPlayer) {
        this.damage = damage;
        this.lifetime = lifetime;
        this.ownerIsPlayer = ownerIsPlayer;
    }
    
    /**
     * Update lifetime tracking.
     * @param deltaTime Time elapsed since last update
     */
    public void update(float deltaTime) {
        timeAlive += deltaTime;
    }
    
    /**
     * Check if projectile has exceeded its lifetime.
     * @return true if projectile should be despawned
     */
    public boolean isExpired() {
        return timeAlive >= lifetime;
    }
}
