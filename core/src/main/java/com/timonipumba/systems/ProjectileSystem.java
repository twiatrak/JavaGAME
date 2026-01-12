package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameConstants;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;

/**
 * System that handles projectile movement, collision, and damage.
 * 
 * Projectile behavior:
 * - Moves projectiles based on their velocity each frame
 * - Checks for collisions with walls (destroys projectile)
 * - Checks for collisions with enemies (for player projectiles) or player (for enemy projectiles)
 * - On hit: applies damage to target, triggers hit flash, and destroys projectile
 * - Destroys projectiles that exceed their lifetime
 * 
 * Combat feedback:
 * - Triggers HitFlashComponent on damaged entities for visual feedback
 * - Triggers CameraShakeComponent on player when taking damage or killing enemies
 * 
 * Only processes when game is in PLAYING state.
 */
public class ProjectileSystem extends EntitySystem {
    
    private ComponentMapper<ProjectileComponent> projectileMapper = ComponentMapper.getFor(ProjectileComponent.class);
    private ComponentMapper<PositionComponent> positionMapper = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<VelocityComponent> velocityMapper = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<CollisionComponent> collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
    private ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private ComponentMapper<EnemyComponent> enemyMapper = ComponentMapper.getFor(EnemyComponent.class);
    private ComponentMapper<PlayerComponent> playerMapper = ComponentMapper.getFor(PlayerComponent.class);
    private ComponentMapper<WallComponent> wallMapper = ComponentMapper.getFor(WallComponent.class);
    private ComponentMapper<HitFlashComponent> hitFlashMapper = ComponentMapper.getFor(HitFlashComponent.class);
    private ComponentMapper<CameraShakeComponent> cameraShakeMapper = ComponentMapper.getFor(CameraShakeComponent.class);
    
    private GameStateManager gameStateManager;
    private ImmutableArray<Entity> projectiles;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> walls;
    
    public ProjectileSystem() {}
    
    public ProjectileSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        projectiles = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, PositionComponent.class, 
                       VelocityComponent.class, CollisionComponent.class).get()
        );
        enemies = engine.getEntitiesFor(
            Family.all(EnemyComponent.class, PositionComponent.class, 
                       CollisionComponent.class, HealthComponent.class).get()
        );
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, 
                       CollisionComponent.class, HealthComponent.class).get()
        );
        walls = engine.getEntitiesFor(
            Family.all(WallComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // Only process when playing
        if (gameStateManager != null && !gameStateManager.isPlaying()) {
            return;
        }
        
        // Process each projectile
        // Use a copy to avoid concurrent modification when removing entities
        Entity[] projectileArray = projectiles.toArray(Entity.class);
        
        for (Entity projectile : projectileArray) {
            processProjectile(projectile, deltaTime);
        }
    }
    
    private void processProjectile(Entity projectile, float deltaTime) {
        ProjectileComponent proj = projectileMapper.get(projectile);
        PositionComponent pos = positionMapper.get(projectile);
        VelocityComponent vel = velocityMapper.get(projectile);
        CollisionComponent col = collisionMapper.get(projectile);
        
        if (proj == null || pos == null || vel == null || col == null) {
            return;
        }
        
        // Update lifetime
        proj.update(deltaTime);
        if (proj.isExpired()) {
            destroyProjectile(projectile);
            return;
        }
        
        // Move projectile
        float newX = pos.x + vel.x * deltaTime;
        float newY = pos.y + vel.y * deltaTime;
        
        // Check wall collision
        if (checkWallCollision(newX, newY, col)) {
            destroyProjectile(projectile);
            return;
        }
        
        // Check target collision based on projectile owner
        if (proj.ownerIsPlayer) {
            // Player projectile: check collision with enemies
            Entity hitEnemy = findCollidingEnemy(newX, newY, col);
            if (hitEnemy != null) {
                applyDamage(hitEnemy, proj.damage);
                destroyProjectile(projectile);
                return;
            }
        } else {
            // Enemy projectile: check collision with player
            Entity hitPlayer = findCollidingPlayer(newX, newY, col);
            if (hitPlayer != null) {
                applyDamage(hitPlayer, proj.damage);
                destroyProjectile(projectile);
                return;
            }
        }
        
        // No collision, update position
        pos.x = newX;
        pos.y = newY;
    }
    
    private boolean checkWallCollision(float x, float y, CollisionComponent col) {
        for (Entity wall : walls) {
            PositionComponent wallPos = positionMapper.get(wall);
            CollisionComponent wallCol = collisionMapper.get(wall);
            
            if (checkCollision(x, y, col.width, col.height,
                    wallPos.x, wallPos.y, wallCol.width, wallCol.height)) {
                return true;
            }
        }
        return false;
    }
    
    private Entity findCollidingEnemy(float x, float y, CollisionComponent col) {
        for (Entity enemy : enemies) {
            PositionComponent enemyPos = positionMapper.get(enemy);
            CollisionComponent enemyCol = collisionMapper.get(enemy);
            
            if (checkCollision(x, y, col.width, col.height,
                    enemyPos.x, enemyPos.y, enemyCol.width, enemyCol.height)) {
                return enemy;
            }
        }
        return null;
    }
    
    private Entity findCollidingPlayer(float x, float y, CollisionComponent col) {
        for (Entity player : players) {
            PositionComponent playerPos = positionMapper.get(player);
            CollisionComponent playerCol = collisionMapper.get(player);
            
            if (checkCollision(x, y, col.width, col.height,
                    playerPos.x, playerPos.y, playerCol.width, playerCol.height)) {
                return player;
            }
        }
        return null;
    }
    
    private void applyDamage(Entity target, int damage) {
        HealthComponent health = healthMapper.get(target);
        if (health != null) {
            health.takeDamage(damage);
            
            boolean isPlayer = playerMapper.get(target) != null;
            String targetName = isPlayer ? "Player" : "Enemy";
            
            // Trigger hit flash on the damaged entity
            HitFlashComponent hitFlash = hitFlashMapper.get(target);
            if (hitFlash != null) {
                float flashDuration = isPlayer ? GameConstants.HIT_FLASH_DURATION_PLAYER 
                                               : GameConstants.HIT_FLASH_DURATION_ENEMY;
                hitFlash.triggerFlash(flashDuration);
            }
            
            // Trigger camera shake based on target type
            if (isPlayer) {
                // Player took damage - shake camera
                CameraShakeComponent shake = cameraShakeMapper.get(target);
                if (shake != null) {
                    shake.triggerShake(GameConstants.CAMERA_SHAKE_DURATION_HIT, 
                                       GameConstants.CAMERA_SHAKE_INTENSITY_HIT);
                }
            }
            
            if (health.isDead()) {
                // Enemy killed - trigger kill shake on player
                if (!isPlayer) {
                    triggerKillShakeOnPlayer();
                }
            }
        }
    }
    
    /**
     * Trigger a camera shake on the player when an enemy is killed.
     */
    private void triggerKillShakeOnPlayer() {
        for (Entity player : players) {
            CameraShakeComponent shake = cameraShakeMapper.get(player);
            if (shake != null) {
                shake.triggerShake(GameConstants.CAMERA_SHAKE_DURATION_KILL, 
                                   GameConstants.CAMERA_SHAKE_INTENSITY_KILL);
            }
        }
    }
    
    private void destroyProjectile(Entity projectile) {
        getEngine().removeEntity(projectile);
    }
    
    private boolean checkCollision(float x1, float y1, float w1, float h1,
                                   float x2, float y2, float w2, float h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
