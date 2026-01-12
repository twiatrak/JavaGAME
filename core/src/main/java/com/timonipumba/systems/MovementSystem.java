package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.components.*;

// move em. dont walk thru walls.
public class MovementSystem extends IteratingSystem {
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<VelocityComponent> vm = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<CollisionComponent> cm = ComponentMapper.getFor(CollisionComponent.class);
    private ComponentMapper<PlayerComponent> playerMapper = ComponentMapper.getFor(PlayerComponent.class);
    private ComponentMapper<EnemyComponent> enemyMapper = ComponentMapper.getFor(EnemyComponent.class);
    private ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private ComponentMapper<CombatComponent> combatMapper = ComponentMapper.getFor(CombatComponent.class);
    private ComponentMapper<DoorComponent> doorMapper = ComponentMapper.getFor(DoorComponent.class);
    private ComponentMapper<PortalComponent> portalMapper = ComponentMapper.getFor(PortalComponent.class);
    
    // speedup
    private ImmutableArray<Entity> walls;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> doors;

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        walls = engine.getEntitiesFor(
            Family.all(WallComponent.class, PositionComponent.class, CollisionComponent.class).get());
        enemies = engine.getEntitiesFor(
            Family.all(EnemyComponent.class, PositionComponent.class, CollisionComponent.class).get());
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get());
        doors = engine.getEntitiesFor(
            Family.all(DoorComponent.class, PositionComponent.class, CollisionComponent.class).get());
    }

    @Override
protected void processEntity(Entity entity, float deltaTime) {
    PositionComponent position = pm.get(entity);
    VelocityComponent velocity = vm.get(entity);
    CollisionComponent collision = cm.get(entity);

    if (collision != null) {
        // next pos?
        float targetX = position.x + velocity.x * deltaTime;
        float targetY = position.y + velocity.y * deltaTime;

        // results
        float finalX = position.x;
        float finalY = position.y;

        // move X
        if (!checkWallCollisions(targetX, position.y, collision)) {
            finalX = targetX;
        } else {
            // bonk
            velocity.x = 0;
        }

        // move Y
        if (!checkWallCollisions(finalX, targetY, collision)) {
            finalY = targetY;
        } else {
            // bonk
            velocity.y = 0;
        }

        // still there?
        boolean moved = (finalX != position.x) || (finalY != position.y);
        if (!moved) {
            return;
        }

        // hit player/enemy stuff
        boolean isPlayer = playerMapper.get(entity) != null;
        boolean isEnemy = enemyMapper.get(entity) != null;

        if (isPlayer) {
            // Player: check collision with enemies (bump-to-attack)
            Entity collidedEnemy = findCollidingEnemy(entity, finalX, finalY, collision);
            if (collidedEnemy != null) {
                // Bump-to-attack: damage the enemy, do not move the player this frame
                applyBumpAttack(entity, collidedEnemy);
                return;
            }
        } else if (isEnemy) {
            // Enemy: check collision with player (block enemy movement, no attack here)
            if (checkPlayerCollision(entity, finalX, finalY, collision)) {
                // Enemy cannot move through the player
                return;
            }
            // Enemies are allowed to overlap with other enemies
        }

        // 3) Apply final movement
        position.x = finalX;
        position.y = finalY;

    } else {
        // No collision component: simple movement
        position.x += velocity.x * deltaTime;
        position.y += velocity.y * deltaTime;
    }
}
    
    /**
     * Check if movement would cause collision with any wall.
     * Walls with active portals are passable and do not block movement.
     */
    private boolean checkWallCollisions(float newX, float newY, CollisionComponent collision) {
        for (Entity wallEntity : walls) {
            // Skip walls that have an active portal (they are passable)
            PortalComponent portal = portalMapper.get(wallEntity);
            if (portal != null && portal.isActive()) {
                continue;
            }
            
            PositionComponent wallPos = pm.get(wallEntity);
            CollisionComponent wallCol = cm.get(wallEntity);

            if (checkCollision(newX, newY, collision.width, collision.height,
                    wallPos.x, wallPos.y, wallCol.width, wallCol.height)) {
                return true;
            }
        }
        
        // Also check doors (only closed doors block movement)
        for (Entity doorEntity : doors) {
            DoorComponent door = doorMapper.get(doorEntity);
            if (door != null && !door.open) {
                PositionComponent doorPos = pm.get(doorEntity);
                CollisionComponent doorCol = cm.get(doorEntity);
                
                // Only check collision if door has non-zero dimensions
                if (doorCol != null && doorCol.width > 0 && doorCol.height > 0) {
                    if (checkCollision(newX, newY, collision.width, collision.height,
                            doorPos.x, doorPos.y, doorCol.width, doorCol.height)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find an enemy that would collide with the moving entity at the new position.
     * @return The colliding enemy entity, or null if no collision
     */
    private Entity findCollidingEnemy(Entity movingEntity, float newX, float newY, CollisionComponent collision) {
        for (Entity enemyEntity : enemies) {
            if (enemyEntity == movingEntity) continue;
            
            PositionComponent enemyPos = pm.get(enemyEntity);
            CollisionComponent enemyCol = cm.get(enemyEntity);
            
            if (checkCollision(newX, newY, collision.width, collision.height,
                    enemyPos.x, enemyPos.y, enemyCol.width, enemyCol.height)) {
                return enemyEntity;
            }
        }
        return null;
    }
    
    /**
     * Check if movement would cause collision with the player.
     */
    private boolean checkPlayerCollision(Entity movingEntity, float newX, float newY, CollisionComponent collision) {
        for (Entity playerEntity : players) {
            if (playerEntity == movingEntity) continue;
            
            PositionComponent playerPos = pm.get(playerEntity);
            CollisionComponent playerCol = cm.get(playerEntity);
            
            if (checkCollision(newX, newY, collision.width, collision.height,
                    playerPos.x, playerPos.y, playerCol.width, playerCol.height)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Apply bump-to-attack: player deals damage to enemy by attempting to move into them.
     */
    private void applyBumpAttack(Entity player, Entity enemy) {
        CombatComponent playerCombat = combatMapper.get(player);
        HealthComponent enemyHealth = healthMapper.get(enemy);
        
        if (enemyHealth == null) {
            // Enemy has no health component, can't be damaged
            return;
        }
        
        int damage = CombatComponent.PLAYER_ATTACK_DAMAGE;
        if (playerCombat != null) {
            damage = playerCombat.attackDamage;
        }
        
        enemyHealth.takeDamage(damage);
    }

    private boolean checkCollision(float x1, float y1, float w1, float h1,
                                     float x2, float y2, float w2, float h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
