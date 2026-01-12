package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.LevelManager;
import com.timonipumba.level.PortalConfig;

/**
 * System that handles exit zone detection for level completion.
 * 
 * Behavior:
 * - Checks when the player overlaps an entity with ExitComponent
 * - On overlap during active gameplay states (PLAYING or LEVEL_CLEAR),
 *   automatically advances to the next level via LevelManager callback.
 * - If no LevelManager is set, falls back to setting LEVEL_CLEAR state.
 * 
 * Note: When PortalConfig.WALL_SEGMENT_PORTALS_ENABLED is true, this system
 * is effectively disabled in favor of PortalSystem. Legacy exits remain in
 * the scene but are not processed unless the feature flag is disabled.
 * 
 * @see PortalSystem for the new wall-segment portal behavior
 * @see PortalConfig for feature flag configuration
 */
public class ExitSystem extends EntitySystem {
    
    private final GameStateManager gameStateManager;
    private LevelManager levelManager;
    
    private ComponentMapper<PositionComponent> positionMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<ExitComponent> exitMapper;
    private ComponentMapper<RenderableComponent> renderableMapper;
    
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> exits;
    
    /** Flag to prevent multiple level loads in the same frame */
    private boolean levelLoadPending = false;
    
    /** Flag to track if exits have been hidden for wall-segment portal mode */
    private boolean exitsHidden = false;
    
    public ExitSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
        this.positionMapper = ComponentMapper.getFor(PositionComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.exitMapper = ComponentMapper.getFor(ExitComponent.class);
        this.renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
    }
    
    /**
     * Set the level manager for auto-advancing to next level.
     * @param levelManager The level manager callback
     */
    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }
    
    /**
     * Reset the level load pending flag.
     * Should be called after level is loaded to allow future exits to trigger.
     */
    public void resetLevelLoadPending() {
        this.levelLoadPending = false;
        this.exitsHidden = false;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );
        exits = engine.getEntitiesFor(
            Family.all(ExitComponent.class, PositionComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // When wall-segment portals are enabled, hide legacy exits and skip processing
        if (PortalConfig.WALL_SEGMENT_PORTALS_ENABLED) {
            if (PortalConfig.HIDE_LEGACY_EXITS && !exitsHidden) {
                hideAllExits();
                exitsHidden = true;
            }
            return; // Skip exit detection - PortalSystem handles this instead
        }
        
        // Don't process if level load is already pending
        if (levelLoadPending) {
            return;
        }
        
        // Process during active gameplay states (PLAYING and LEVEL_CLEAR)
        // This allows exit detection even after enemies are cleared
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }
        
        // Check for player-exit overlap
        for (Entity player : players) {
            PositionComponent playerPos = positionMapper.get(player);
            CollisionComponent playerCol = collisionMapper.get(player);
            
            if (playerPos == null || playerCol == null) continue;
            
            for (Entity exit : exits) {
                PositionComponent exitPos = positionMapper.get(exit);
                CollisionComponent exitCol = collisionMapper.get(exit);
                
                if (exitPos == null) continue;
                
                // Default exit collision size if none specified
                float exitWidth = (exitCol != null) ? exitCol.width : 16f;
                float exitHeight = (exitCol != null) ? exitCol.height : 16f;
                
                if (checkOverlap(playerPos, playerCol, exitPos, exitWidth, exitHeight)) {
                    levelLoadPending = true;
                    
                    if (levelManager != null) {
                        // Auto-advance to next level
                        levelManager.loadNextLevel();
                    } else {
                        // Fallback: just set LEVEL_CLEAR if no manager
                        gameStateManager.setState(GameState.LEVEL_CLEAR);
                    }
                    return;
                }
            }
        }
    }
    
    /**
     * Hide all exit entities by setting their renderable alpha to 0 and collision to 0.
     * Called when wall-segment portals are enabled to hide legacy exits.
     */
    private void hideAllExits() {
        for (Entity exit : exits) {
            // Hide visually
            RenderableComponent renderable = renderableMapper.get(exit);
            if (renderable != null) {
                renderable.color.a = 0f;
            }
            
            // Disable collision
            CollisionComponent collision = collisionMapper.get(exit);
            if (collision != null) {
                collision.width = 0;
                collision.height = 0;
            }
        }
        
    }
    
    /**
     * Check if two bounding boxes overlap.
     */
    private boolean checkOverlap(PositionComponent pos1, CollisionComponent col1, 
                                  PositionComponent pos2, float width2, float height2) {
        return pos1.x < pos2.x + width2 && 
               pos1.x + col1.width > pos2.x && 
               pos1.y < pos2.y + height2 && 
               pos1.y + col1.height > pos2.y;
    }
}
