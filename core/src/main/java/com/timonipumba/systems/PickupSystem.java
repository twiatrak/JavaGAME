package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * System that handles item pickups when the player overlaps with item entities.
 * 
 * Behavior:
 * - Checks for overlap between player and items each frame
 * - On overlap, applies item effect (e.g., healing for potions)
 * - Removes the item entity from the engine after pickup
 * - Only processes when game is in PLAYING state
 */
public class PickupSystem extends EntitySystem {
    
    private final GameStateManager gameStateManager;
    
    private ComponentMapper<PositionComponent> positionMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<ItemComponent> itemMapper;
    private ComponentMapper<HealthComponent> healthMapper;
    
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> items;
    
    public PickupSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
        this.positionMapper = ComponentMapper.getFor(PositionComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.itemMapper = ComponentMapper.getFor(ItemComponent.class);
        this.healthMapper = ComponentMapper.getFor(HealthComponent.class);
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, 
                       CollisionComponent.class, HealthComponent.class).get()
        );
        items = engine.getEntitiesFor(
            Family.all(ItemComponent.class, PositionComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // Process pickups during active gameplay states (allow pickups after enemies defeated)
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }
        
        // Collect items to remove after iteration to avoid concurrent modification
        List<Entity> itemsToRemove = new ArrayList<>();
        
        // Check each player for item overlaps
        for (Entity player : players) {
            PositionComponent playerPos = positionMapper.get(player);
            CollisionComponent playerCol = collisionMapper.get(player);
            HealthComponent playerHealth = healthMapper.get(player);
            
            if (playerPos == null || playerCol == null || playerHealth == null) {
                continue;
            }
            
            // Check each item for overlap with player
            for (Entity item : items) {
                // Skip items already marked for removal
                if (itemsToRemove.contains(item)) {
                    continue;
                }
                
                PositionComponent itemPos = positionMapper.get(item);
                CollisionComponent itemCol = collisionMapper.get(item);
                
                // Default item collision size if none specified
                float itemWidth = (itemCol != null) ? itemCol.width : 16f;
                float itemHeight = (itemCol != null) ? itemCol.height : 16f;
                
                if (checkOverlap(playerPos, playerCol, itemPos, itemWidth, itemHeight)) {
                    // Process pickup
                    processPickup(player, item, playerHealth);
                    // Mark item for removal
                    itemsToRemove.add(item);
                }
            }
        }
        
        // Remove all collected items after iteration
        for (Entity item : itemsToRemove) {
            getEngine().removeEntity(item);
        }
    }
    
    private boolean checkOverlap(PositionComponent pos1, CollisionComponent col1, 
                                  PositionComponent pos2, float width2, float height2) {
        return pos1.x < pos2.x + width2 && 
               pos1.x + col1.width > pos2.x && 
               pos1.y < pos2.y + height2 && 
               pos1.y + col1.height > pos2.y;
    }
    
    private void processPickup(Entity player, Entity item, HealthComponent playerHealth) {
        ItemComponent itemComp = itemMapper.get(item);
        if (itemComp == null) {
            return;
        }
        
        switch (itemComp.itemType) {
            case POTION:
                // Heal player (capped at max health)
                int oldHealth = playerHealth.currentHealth;
                playerHealth.heal(itemComp.healAmount);
                break;
                
            case BUFF:
                // Reserved for future implementation
                break;
                
            case TREASURE:
                // Reserved for future implementation
                break;
        }
    }
}
