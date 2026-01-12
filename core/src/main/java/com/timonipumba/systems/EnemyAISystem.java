package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.timonipumba.GameConstants;
import com.timonipumba.GameStateManager;
import com.timonipumba.assets.SpriteLoader;
import com.timonipumba.components.*;
import com.timonipumba.components.EnemyStatsComponent.EnemyType;



/**
 * System that handles enemy AI behavior with distinct mechanics per enemy type.
 * 
 * <h3>Enemy Types and Behaviors:</h3>
 * 
 * <b>BRUTE</b> - Slow, dangerous tank
 * <ul>
 *   <li>Direct chase: Moves straight toward player</li>
 *   <li>Smaller aggro radius: Can be avoided with care</li>
 *   <li>Once aggroed, pursues until player leaves vision range</li>
 * </ul>
 * 
 * <b>SCOUT</b> - Fast, fragile harasser
 * <ul>
 *   <li>Flanking movement: Approaches at angles, periodically strafes</li>
 *   <li>Large aggro radius: Detects player quickly</li>
 *   <li>Strafes perpendicular to player direction at intervals</li>
 * </ul>
 * 
 * <b>RANGER</b> - Positional threat with ranged attacks
 * <ul>
 *   <li>Distance-keeping: Maintains preferred distance band from player</li>
 *   <li>Backs away if too close, moves closer if too far</li>
 *   <li>Fires projectiles with windup animation</li>
 * </ul>
 * 
 * <b>DEFAULT</b> - Balanced baseline
 * <ul>
 *   <li>Direct chase like BRUTE but with default stats</li>
 * </ul>
 * 
 * <h3>Aggro System:</h3>
 * <ul>
 *   <li>Enemies start in idle state</li>
 *   <li>Player entering aggro radius activates the enemy</li>
 *   <li>Aggroed enemies pursue until player leaves vision range</li>
 *   <li>Leash: Enemies stop chasing when player is far enough away</li>
 * </ul>
 * 
 * Uses EnemyStatsComponent for type-specific values.
 * Only processes when game is in PLAYING state.
 * Compatible with ML controller hooks for training.
 */
public class EnemyAISystem extends IteratingSystem {
    private ComponentMapper<EnemyComponent> em = ComponentMapper.getFor(EnemyComponent.class);
    private ComponentMapper<EnemyStatsComponent> esm = ComponentMapper.getFor(EnemyStatsComponent.class);
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<VelocityComponent> vm = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<CombatComponent> combatMapper = ComponentMapper.getFor(CombatComponent.class);
    private ComponentMapper<CollisionComponent> collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
    private ComponentMapper<RangedAttackWindupComponent> windupMapper = ComponentMapper.getFor(RangedAttackWindupComponent.class);
    
    private Entity player;
    private GameStateManager gameStateManager;
    private SpriteLoader spriteLoader;

    public EnemyAISystem() {
        super(Family.all(EnemyComponent.class, PositionComponent.class, VelocityComponent.class).get());
    }
    
    public EnemyAISystem(GameStateManager gameStateManager) {
        super(Family.all(EnemyComponent.class, PositionComponent.class, VelocityComponent.class).get());
        this.gameStateManager = gameStateManager;
    }
    
    public EnemyAISystem(GameStateManager gameStateManager, SpriteLoader spriteLoader) {
        super(Family.all(EnemyComponent.class, PositionComponent.class, VelocityComponent.class).get());
        this.gameStateManager = gameStateManager;
        this.spriteLoader = spriteLoader;
    }
    
    /**
     * Set the sprite loader for projectile sprites.
     * @param spriteLoader The sprite loader to use
     */
    public void setSpriteLoader(SpriteLoader spriteLoader) {
        this.spriteLoader = spriteLoader;
    }
    
    @Override
    public void update(float deltaTime) {
        // Only process when playing
        if (gameStateManager != null && !gameStateManager.isPlaying()) {
            // Stop all enemies when not playing
            for (Entity entity : getEntities()) {
                VelocityComponent velocity = vm.get(entity);
                if (velocity != null) {
                    velocity.x = 0;
                    velocity.y = 0;
                }
            }
            return;
        }
        
        // Find player entity
        player = null;
        for (Entity entity : getEngine().getEntitiesFor(Family.all(PlayerComponent.class, PositionComponent.class).get())) {
            player = entity;
            break;
        }

        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        EnemyComponent enemy = em.get(entity);
        EnemyStatsComponent stats = esm.get(entity);
        PositionComponent position = pm.get(entity);
        VelocityComponent velocity = vm.get(entity);
        CombatComponent combat = combatMapper.get(entity);

        velocity.x = 0;
        velocity.y = 0;
        
        // Get type-specific values from stats or fall back to defaults
        float speed = (stats != null) ? stats.speed : enemy.speed;
        float visionRange = (stats != null) ? stats.visionRange : enemy.detectionRange;
        float aggroRadius = (stats != null) ? stats.aggroRadius : enemy.detectionRange;
        boolean isRanged = (stats != null) && stats.isRanged;
        float preferredDistance = (stats != null) ? stats.preferredDistance : 0f;
        EnemyType enemyType = (stats != null) ? stats.enemyType : EnemyType.DEFAULT;

        if (player != null) {
            PositionComponent playerPos = pm.get(player);
            
            float dx = playerPos.x - position.x;
            float dy = playerPos.y - position.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            
            // Aggro state management
            updateAggroState(enemy, distance, aggroRadius, visionRange);

            if (enemy.isAggroed && distance > 0) {
                // Normalize direction to player
                float dirX = dx / distance;
                float dirY = dy / distance;
                
                if (isRanged && preferredDistance > 0) {
                    // RANGER behavior: maintain distance and fire projectiles
                    processRangedEnemy(entity, position, velocity, combat, stats, 
                                       playerPos, distance, dirX, dirY, deltaTime);
                } else if (enemyType == EnemyType.SCOUT) {
                    // SCOUT behavior: flanking/strafing movement
                    processScoutEnemy(entity, enemy, velocity, speed, distance, dirX, dirY, deltaTime);
                } else {
                    // BRUTE/DEFAULT behavior: direct chase
                    velocity.x = dirX * speed;
                    velocity.y = dirY * speed;
                }
            } else {
                // Idle behavior - not aggroed
                processIdleBehavior(enemy, deltaTime);
            }
        }
    }
    
    /**
     * Update aggro state based on distance to player.
     * Enemies become aggroed when player enters aggro radius.
     * Once aggroed, they stay active until player leaves vision range (leash).
     */
    private void updateAggroState(EnemyComponent enemy, float distance, float aggroRadius, float visionRange) {
        if (!enemy.isAggroed) {
            // Check if player entered aggro radius
            if (distance < aggroRadius) {
                enemy.isAggroed = true;
            }
        } else {
            // Check if player left vision range (leash behavior)
            if (distance > visionRange) {
                enemy.isAggroed = false;
            }
        }
    }
    
    /**
     * Process idle behavior for non-aggroed enemies.
     */
    private void processIdleBehavior(EnemyComponent enemy, float deltaTime) {
        enemy.idleTimer += deltaTime;
        if (enemy.idleTimer >= enemy.idleDuration) {
            enemy.idleTimer = 0;
        }
    }
    
    /**
     * Process scout enemy AI: flanking/strafing movement.
     * Scouts approach at angles and periodically strafe perpendicular to the player.
     */
    private void processScoutEnemy(Entity entity, EnemyComponent enemy, VelocityComponent velocity,
                                   float speed, float distance, float dirX, float dirY, float deltaTime) {
        // Update strafe timer
        enemy.strafeTimer += deltaTime;
        if (enemy.strafeTimer >= GameConstants.SCOUT_STRAFE_INTERVAL) {
            enemy.strafeTimer = 0;
            // Switch strafe direction
            enemy.strafeDirection *= -1;
        }
        
        // Calculate perpendicular direction for strafing
        // Perpendicular to (dirX, dirY) is (-dirY, dirX) or (dirY, -dirX)
        float perpX = -dirY * enemy.strafeDirection;
        float perpY = dirX * enemy.strafeDirection;
        
        // Blend forward movement with strafe
        // When far away: more forward movement
        // When close: more strafing
        float strafeWeight = Math.min(1.0f, 50f / distance); // More strafing when close
        float forwardWeight = 1.0f - strafeWeight * 0.5f; // Always some forward movement
        
        float moveX = dirX * forwardWeight + perpX * strafeWeight;
        float moveY = dirY * forwardWeight + perpY * strafeWeight;
        
        // Normalize and apply speed
        float moveMag = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (moveMag > 0) {
            velocity.x = (moveX / moveMag) * speed;
            velocity.y = (moveY / moveMag) * speed;
        }
    }
    
    /**
     * Process ranged enemy AI: maintain distance and fire projectiles.
     * Rangers try to stay within a preferred distance band from the player.
     * If too close: back away. If too far: move closer.
     */
    private void processRangedEnemy(Entity enemy, PositionComponent position, VelocityComponent velocity,
                                    CombatComponent combat, EnemyStatsComponent stats,
                                    PositionComponent playerPos, float distance, 
                                    float dirX, float dirY, float deltaTime) {
        float speed = stats.speed;
        float minDistance = GameConstants.RANGER_MIN_DISTANCE;
        float maxDistance = GameConstants.RANGER_MAX_DISTANCE;
        
        // Update attack cooldown
        if (combat != null) {
            combat.updateCooldown(deltaTime);
        }
        
        // Get or create windup component for this ranger
        RangedAttackWindupComponent windup = windupMapper.get(enemy);
        
        // Movement: maintain distance band
        // Use min/max distance for more nuanced positioning
        if (distance < minDistance) {
            // Too close, back away
            velocity.x = -dirX * speed;
            velocity.y = -dirY * speed;
        } else if (distance > maxDistance) {
            // Too far, move closer
            velocity.x = dirX * speed;
            velocity.y = dirY * speed;
        }
        // If within [minDistance, maxDistance], stay still and focus on shooting
        
        // Handle windup-based ranged attack
        if (windup != null) {
            // If already winding up, check for completion or cancellation
            if (windup.isWindingUp()) {
                // Check if target moved out of range - cancel windup
                if (distance > stats.visionRange) {
                    windup.cancelWindup();
                } else {
                    // Update windup timer
                    boolean windupComplete = windup.update(deltaTime);
                    if (windupComplete) {
                        // Fire the projectile at the stored direction
                        fireEnemyProjectile(position, windup.targetDirX, windup.targetDirY, stats.damage);
                        combat.resetCooldown();
                    }
                }
            } else {
                // Not winding up - check if we should start one
                if (combat != null && combat.canAttack() && distance < stats.visionRange) {
                    // Start windup animation
                    windup.startWindup(GameConstants.RANGER_WINDUP_TIME, dirX, dirY);
                }
            }
        } else {
            // No windup component - use immediate fire (legacy behavior)
            if (combat != null && combat.canAttack() && distance < stats.visionRange) {
                fireEnemyProjectile(position, dirX, dirY, stats.damage);
                combat.resetCooldown();
            }
        }
    }
    
    /**
     * Fire a projectile from an enemy in the specified direction.
     */
    private void fireEnemyProjectile(PositionComponent enemyPos, float dirX, float dirY, int damage) {
        com.badlogic.ashley.core.Engine engine = getEngine();
        if (engine == null) return;
        
        // Calculate projectile spawn position (center of enemy, offset in direction)
        float projectileSize = GameConstants.PROJECTILE_SIZE;
        float spawnX = enemyPos.x + GameConstants.TILE_SIZE / 2 + dirX * GameConstants.TILE_SIZE - projectileSize / 2;
        float spawnY = enemyPos.y + GameConstants.TILE_SIZE / 2 + dirY * GameConstants.TILE_SIZE - projectileSize / 2;
        
        // Create projectile entity
        Entity projectile = engine.createEntity();
        projectile.add(new PositionComponent(spawnX, spawnY));
        projectile.add(new VelocityComponent(
            dirX * GameConstants.PROJECTILE_SPEED,
            dirY * GameConstants.PROJECTILE_SPEED
        ));
        projectile.add(new CollisionComponent(projectileSize, projectileSize));
        projectile.add(new RenderableComponent(projectileSize, projectileSize, Color.ORANGE));
        projectile.add(new ProjectileComponent(damage, false)); // false = enemy projectile
        
        // Add sprite if available
        if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion projectileSprite = spriteLoader.getProjectileSprite();
            if (projectileSprite != null) {
                SpriteComponent sprite = new SpriteComponent(projectileSprite, 40);
                sprite.rotation = (float) Math.toDegrees(Math.atan2(dirY, dirX));
                projectile.add(sprite);
            }
        }
        
        engine.addEntity(projectile);
    }
}
