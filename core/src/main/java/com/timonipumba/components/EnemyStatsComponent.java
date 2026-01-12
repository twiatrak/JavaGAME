package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.GameConstants;

/**
 * Component that defines enemy stats for different enemy archetypes.
 * 
 * This component allows for enemy variation with distinct combat identities:
 * 
 * <h3>BRUTE - Slow, dangerous tank</h3>
 * <ul>
 *   <li>High HP: Can absorb multiple hits before dying</li>
 *   <li>High damage: Punishing if player gets caught</li>
 *   <li>Slow movement: Player can kite and dodge</li>
 *   <li>Longer attack cooldown: Windows for counterattack</li>
 *   <li>Small aggro radius: Can be avoided with care</li>
 *   <li>Movement: Direct pursuit toward player</li>
 * </ul>
 * 
 * <h3>SCOUT - Fast, fragile harasser</h3>
 * <ul>
 *   <li>Low HP: Dies quickly when caught</li>
 *   <li>Low damage: Not deadly alone, dangerous in groups</li>
 *   <li>Very fast movement: Hard to escape</li>
 *   <li>Short attack cooldown: Constant pressure</li>
 *   <li>Large aggro radius: Detects player from far away</li>
 *   <li>Movement: Flanking/strafing, approaches at angles</li>
 * </ul>
 * 
 * <h3>RANGER - Positional threat with ranged attacks</h3>
 * <ul>
 *   <li>Moderate HP: Can take a few hits</li>
 *   <li>Ranged damage: Forces player to close distance or use cover</li>
 *   <li>Moderate movement speed</li>
 *   <li>Attack cooldown tuned to be threatening but dodgeable</li>
 *   <li>Windup before firing: Telegraphed attacks</li>
 *   <li>Movement: Maintains preferred distance band</li>
 * </ul>
 * 
 * <h3>DEFAULT - Balanced baseline enemy</h3>
 * <ul>
 *   <li>Medium stats across the board</li>
 *   <li>Direct chase behavior</li>
 * </ul>
 */
public class EnemyStatsComponent implements Component {
    
    /** Enemy archetype enum for easy configuration */
    public enum EnemyType {
        DEFAULT, BRUTE, SCOUT, RANGER
    }
    
    /** Movement speed in pixels per second */
    public float speed = GameConstants.ENEMY_DEFAULT_SPEED;
    
    /** Attack damage dealt to player */
    public int damage = GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE;
    
    /** Vision range for player detection (pixels) */
    public float visionRange = GameConstants.ENEMY_DEFAULT_VISION_RANGE;
    
    /** Max health override (0 = use default from HealthComponent) */
    public int maxHealth = 0;
    
    /** The enemy type/archetype */
    public EnemyType enemyType = EnemyType.DEFAULT;
    
    /** Whether this enemy uses ranged attacks */
    public boolean isRanged = false;
    
    /** For ranged enemies, the preferred distance to maintain from player */
    public float preferredDistance = 0f;
    
    /** Attack cooldown in seconds - varies by enemy type */
    public float attackCooldown = GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN;
    
    /** Aggro radius - distance at which enemy becomes active and pursues player */
    public float aggroRadius = GameConstants.ENEMY_DEFAULT_VISION_RANGE;
    
    public EnemyStatsComponent() {}
    
    /**
     * Create stats component from archetype.
     */
    public EnemyStatsComponent(EnemyType type) {
        this.enemyType = type;
        applyTypeStats(type);
    }
    
    /**
     * Apply stats based on enemy type.
     * Sets all type-specific values including speed, damage, health,
     * attack cooldown, aggro radius, and movement behavior parameters.
     */
    public void applyTypeStats(EnemyType type) {
        this.enemyType = type;
        switch (type) {
            case BRUTE:
                this.speed = GameConstants.BRUTE_SPEED;
                this.damage = GameConstants.BRUTE_ATTACK_DAMAGE;
                this.visionRange = GameConstants.BRUTE_VISION_RANGE;
                this.maxHealth = GameConstants.BRUTE_MAX_HEALTH;
                this.isRanged = false;
                this.attackCooldown = GameConstants.BRUTE_ATTACK_COOLDOWN;
                this.aggroRadius = GameConstants.BRUTE_AGGRO_RADIUS;
                break;
                
            case SCOUT:
                this.speed = GameConstants.SCOUT_SPEED;
                this.damage = GameConstants.SCOUT_ATTACK_DAMAGE;
                this.visionRange = GameConstants.SCOUT_VISION_RANGE;
                this.maxHealth = GameConstants.SCOUT_MAX_HEALTH;
                this.isRanged = false;
                this.attackCooldown = GameConstants.SCOUT_ATTACK_COOLDOWN;
                this.aggroRadius = GameConstants.SCOUT_AGGRO_RADIUS;
                break;
                
            case RANGER:
                this.speed = GameConstants.RANGER_SPEED;
                this.damage = GameConstants.RANGER_ATTACK_DAMAGE;
                this.visionRange = GameConstants.RANGER_VISION_RANGE;
                this.maxHealth = GameConstants.RANGER_MAX_HEALTH;
                this.isRanged = true;
                this.preferredDistance = GameConstants.RANGER_PREFERRED_DISTANCE;
                this.attackCooldown = GameConstants.RANGER_ATTACK_COOLDOWN;
                this.aggroRadius = GameConstants.RANGER_AGGRO_RADIUS;
                break;
                
            case DEFAULT:
            default:
                this.speed = GameConstants.ENEMY_DEFAULT_SPEED;
                this.damage = GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE;
                this.visionRange = GameConstants.ENEMY_DEFAULT_VISION_RANGE;
                this.maxHealth = GameConstants.ENEMY_DEFAULT_MAX_HEALTH;
                this.isRanged = false;
                this.attackCooldown = GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN;
                this.aggroRadius = GameConstants.ENEMY_DEFAULT_VISION_RANGE;
                break;
        }
    }
    
    /**
     * Parse enemy type from string (for Tiled map properties).
     * @param typeString String like "brute", "scout", "ranger", or null/empty for default
     * @return The corresponding EnemyType
     */
    public static EnemyType parseType(String typeString) {
        if (typeString == null || typeString.isEmpty()) {
            return EnemyType.DEFAULT;
        }
        
        switch (typeString.toLowerCase().trim()) {
            case "brute":
                return EnemyType.BRUTE;
            case "scout":
                return EnemyType.SCOUT;
            case "ranger":
                return EnemyType.RANGER;
            default:
                return EnemyType.DEFAULT;
        }
    }
}
