package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.LevelManager;
import com.timonipumba.level.PortalConfig;

/**
 * System that handles wall-segment portal player collision and teleportation.
 * 
 * Portal lifecycle:
 * 1. Portal creation: When a puzzle is solved, PortalSpawner adds PortalComponent
 *    to wall tiles and immediately activates them with green color.
 * 2. Portal detection: This system monitors for player collision with active portals.
 * 3. Level transition: When player enters an active portal, triggers level transition.
 * 
 * Important notes:
 * - PortalSpawner handles portal creation AND activation (not this system) because
 *   the Ashley ECS Family query cache doesn't update until the next engine.update().
 * - This system's activatePortalsForPuzzle() method includes a fallback to fresh
 *   engine queries in case it's called with stale cached data.
 * - Active portals are displayed as green wall tiles (PORTAL_COLOR).
 * 
 * @see PortalComponent for portal data structure
 * @see com.timonipumba.level.PortalSpawner for portal creation and activation
 * @see PortalConfig for configuration options
 */
public class PortalSystem extends EntitySystem {
    
    /** Color for active portal tiles */
    public static final Color PORTAL_COLOR = new Color(0.2f, 0.9f, 0.3f, 1.0f);
    
    /** Color for inactive portal tiles (same as regular wall) */
    public static final Color INACTIVE_COLOR = Color.GRAY;
    
    private final GameStateManager gameStateManager;
    private LevelManager levelManager;
    
    private ComponentMapper<PositionComponent> positionMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<PortalComponent> portalMapper;
    private ComponentMapper<RenderableComponent> renderableMapper;
    
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> portals;
    
    /** Flag to prevent multiple level loads in the same frame */
    private boolean levelLoadPending = false;
    
    public PortalSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
        this.positionMapper = ComponentMapper.getFor(PositionComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.portalMapper = ComponentMapper.getFor(PortalComponent.class);
        this.renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
    }
    
    /**
     * Set the level manager for level transitions.
     * @param levelManager The level manager callback
     */
    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }
    
    /**
     * Reset the level load pending flag.
     * Should be called after level is loaded to allow future portal usage.
     */
    public void resetLevelLoadPending() {
        this.levelLoadPending = false;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(
            Family.all(PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );
        portals = engine.getEntitiesFor(
            Family.all(PortalComponent.class, PositionComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // Skip if wall-segment portals are disabled
        if (!PortalConfig.WALL_SEGMENT_PORTALS_ENABLED) {
            return;
        }
        
        // Don't process if level load is already pending
        if (levelLoadPending) {
            return;
        }
        
        // Process during active gameplay states
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }
        
        // Check for player entering active portal
        for (Entity player : players) {
            PositionComponent playerPos = positionMapper.get(player);
            CollisionComponent playerCol = collisionMapper.get(player);
            
            if (playerPos == null || playerCol == null) continue;
            
            for (Entity portal : portals) {
                PortalComponent portalComp = portalMapper.get(portal);
                
                // Only process active portals
                if (portalComp == null || !portalComp.isActive()) continue;
                
                PositionComponent portalPos = positionMapper.get(portal);
                CollisionComponent portalCol = collisionMapper.get(portal);
                
                if (portalPos == null) continue;
                
                // Default portal collision size if none specified
                float portalWidth = (portalCol != null) ? portalCol.width : 16f;
                float portalHeight = (portalCol != null) ? portalCol.height : 16f;
                
                if (checkOverlap(playerPos, playerCol, portalPos, portalWidth, portalHeight)) {
                    levelLoadPending = true;
                    
                    if (levelManager != null) {
                        levelManager.loadNextLevel();
                    } else {
                        gameStateManager.setState(GameState.LEVEL_CLEAR);
                    }
                    return;
                }
            }
        }
    }
    
    /** Activates portals for a puzzle; falls back to a fresh engine query for newly-added components. */
    public void activatePortalsForPuzzle(String puzzleId) {
        if (puzzleId == null || puzzleId.isEmpty()) {
            return;
        }
        
        // Collect matching portals from cached query
        java.util.List<Entity> matchingPortals = new java.util.ArrayList<>();
        for (Entity portal : portals) {
            PortalComponent portalComp = portalMapper.get(portal);
            if (portalComp != null && puzzleId.equals(portalComp.puzzleId) && !portalComp.isActive()) {
                matchingPortals.add(portal);
            }
        }
        
        // If cached query returned nothing, try a fresh engine query.
        if (matchingPortals.isEmpty() && getEngine() != null) {
            ImmutableArray<Entity> freshPortals = getEngine().getEntitiesFor(
                Family.all(PortalComponent.class, PositionComponent.class).get()
            );
            for (Entity portal : freshPortals) {
                PortalComponent portalComp = portalMapper.get(portal);
                if (portalComp != null && puzzleId.equals(portalComp.puzzleId) && !portalComp.isActive()) {
                    matchingPortals.add(portal);
                }
            }
        }
        
        int totalForPuzzle = matchingPortals.size();
        
        // Activate each matching portal
        for (Entity portal : matchingPortals) {
            PortalComponent portalComp = portalMapper.get(portal);
            activatePortal(portal, portalComp);
        }
    }
    
    /**
     * Activate a single portal tile.
     * Changes visual appearance and enables collision detection.
     */
    private void activatePortal(Entity portal, PortalComponent portalComp) {
        portalComp.activate();
        
        // Update visual to green portal color
        RenderableComponent renderable = renderableMapper.get(portal);
        if (renderable != null) {
            renderable.color = new Color(PORTAL_COLOR);
        }
        
        // Ensure portal has collision for overlap detection
        // (walls already have collision, so this should already be set)
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
    
    /**
     * Get count of active portals.
     * Useful for testing and debugging.
     */
    public int getActivePortalCount() {
        int count = 0;
        for (Entity portal : portals) {
            PortalComponent portalComp = portalMapper.get(portal);
            if (portalComp != null && portalComp.isActive()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get count of inactive portals.
     * Useful for testing and debugging.
     */
    public int getInactivePortalCount() {
        int count = 0;
        for (Entity portal : portals) {
            PortalComponent portalComp = portalMapper.get(portal);
            if (portalComp != null && !portalComp.isActive()) {
                count++;
            }
        }
        return count;
    }
}
