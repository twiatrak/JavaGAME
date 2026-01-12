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

// fight logic. sloppy.
public class CombatSystem extends EntitySystem {
    
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<CollisionComponent> cm = ComponentMapper.getFor(CollisionComponent.class);
    private ComponentMapper<HealthComponent> hm = ComponentMapper.getFor(HealthComponent.class);
    private ComponentMapper<CombatComponent> combatm = ComponentMapper.getFor(CombatComponent.class);
    private ComponentMapper<EnemyComponent> em = ComponentMapper.getFor(EnemyComponent.class);
    private ComponentMapper<EnemyStatsComponent> esm = ComponentMapper.getFor(EnemyStatsComponent.class);
    private ComponentMapper<PlayerComponent> plm = ComponentMapper.getFor(PlayerComponent.class);
    private ComponentMapper<HitFlashComponent> hfm = ComponentMapper.getFor(HitFlashComponent.class);
    private ComponentMapper<CameraShakeComponent> csm = ComponentMapper.getFor(CameraShakeComponent.class);
    
    private GameStateManager gameStateManager;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> allWithHealth;
    
    public CombatSystem() {}
    
    public CombatSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        enemies = engine.getEntitiesFor(
            Family.all(EnemyComponent.class, PositionComponent.class, 
                       CollisionComponent.class, CombatComponent.class).get()
        );
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, 
                       CollisionComponent.class, HealthComponent.class).get()
        );
        allWithHealth = engine.getEntitiesFor(
            Family.all(HealthComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // check status
        if (gameStateManager != null && !gameStateManager.isPlaying()) {
            return;
        }
        
        // enemy hits
        for (Entity enemy : enemies) {
            CombatComponent combat = combatm.get(enemy);
            EnemyStatsComponent stats = esm.get(enemy);
            
            // cd timer
            combat.updateCooldown(deltaTime);
            
            // how much dmg
            int damage = (stats != null) ? stats.damage : combat.attackDamage;
            
            // near player?
            for (Entity player : players) {
                if (isAdjacent(enemy, player)) {
                    // try hit
                    if (combat.canAttack()) {
                        HealthComponent playerHealth = hm.get(player);
                        playerHealth.takeDamage(damage);
                        combat.resetCooldown();
                        
                        // flash red
                        HitFlashComponent hitFlash = hfm.get(player);
                        if (hitFlash != null) {
                            hitFlash.triggerFlash(GameConstants.HIT_FLASH_DURATION_PLAYER);
                        }
                        
                        // Trigger camera shake on player
                        CameraShakeComponent shake = csm.get(player);
                        if (shake != null) {
                            shake.triggerShake(GameConstants.CAMERA_SHAKE_DURATION_HIT, 
                                               GameConstants.CAMERA_SHAKE_INTENSITY_HIT);
                        }
                        
                        if (playerHealth.isDead()) {
                        }
                    }
                }
            }
        }
        
        // Remove dead entities
        for (Entity entity : allWithHealth) {
            HealthComponent health = hm.get(entity);
            if (health != null && health.isDead()) {
                if (plm.get(entity) != null) {
                } else if (em.get(entity) != null) {
                    // Trigger kill shake on player when enemy dies
                    triggerKillShakeOnPlayer();
                }
                getEngine().removeEntity(entity);
            }
        }
    }
    
    /**
     * Trigger a camera shake on the player when an enemy is killed.
     */
    private void triggerKillShakeOnPlayer() {
        for (Entity player : players) {
            CameraShakeComponent shake = csm.get(player);
            if (shake != null) {
                shake.triggerShake(GameConstants.CAMERA_SHAKE_DURATION_KILL, 
                                   GameConstants.CAMERA_SHAKE_INTENSITY_KILL);
            }
        }
    }
    
    /**
     * Check if two entities are adjacent (within one tile distance) or overlapping.
     * Uses AABB collision with a small margin for adjacency.
     */
    private boolean isAdjacent(Entity entity1, Entity entity2) {
        PositionComponent pos1 = pm.get(entity1);
        PositionComponent pos2 = pm.get(entity2);
        CollisionComponent col1 = cm.get(entity1);
        CollisionComponent col2 = cm.get(entity2);
        
        if (pos1 == null || pos2 == null || col1 == null || col2 == null) {
            return false;
        }
        
        // Check with a small margin (1 pixel) for adjacency
        float margin = 1f;
        
        return pos1.x - margin < pos2.x + col2.width && 
               pos1.x + col1.width + margin > pos2.x && 
               pos1.y - margin < pos2.y + col2.height && 
               pos1.y + col1.height + margin > pos2.y;
    }
}
