package com.timonipumba.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.timonipumba.GameConstants;
import com.timonipumba.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating corridor tiles between arena entrances.
 * 
 * When a gate opens, the CorridorBuilder can spawn corridor tiles along a path
 * from the gate location to the next arena entrance. This provides a visible
 * walkway for the player without teleportation.
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * CorridorBuilder builder = new CorridorBuilder(engine);
 * builder.buildCorridor(gateX, gateY, targetX, targetY, 3); // 3 tiles wide
 * }</pre>
 * 
 * @see com.timonipumba.systems.GateSystem for triggering corridor creation
 */
public class CorridorBuilder {
    
    /** Default corridor tile color (stone/floor color) */
    public static final Color CORRIDOR_COLOR = new Color(0.4f, 0.4f, 0.45f, 1.0f);
    
    /** Default corridor width in tiles */
    public static final int DEFAULT_CORRIDOR_WIDTH = 2;
    
    /** Minimum corridor length in tiles (spec requirement: >= 6 tiles) */
    public static final int MIN_CORRIDOR_LENGTH = 6;
    
    /** Render order for corridor tiles (below characters, above floor) */
    public static final int CORRIDOR_RENDER_ORDER = 2;
    
    private final Engine engine;
    private TextureRegion corridorSprite;
    
    /** List of corridor entities created by this builder */
    private final List<Entity> corridorEntities = new ArrayList<>();
    
    public CorridorBuilder(Engine engine) {
        this.engine = engine;
    }
    
    /**
     * Set the sprite to use for corridor tiles.
     * 
     * @param sprite TextureRegion for corridor floor tiles
     */
    public void setCorridorSprite(TextureRegion sprite) {
        this.corridorSprite = sprite;
    }
    
    /**
     * Build a corridor from source position to target position.
     * 
     * @param startX Starting X position in world coordinates
     * @param startY Starting Y position in world coordinates
     * @param endX Ending X position in world coordinates
     * @param endY Ending Y position in world coordinates
     * @param width Width of the corridor in tiles
     * @return List of created corridor entities
     */
    public List<Entity> buildCorridor(float startX, float startY, float endX, float endY, int width) {
        List<Entity> created = new ArrayList<>();
        
        // Convert to grid coordinates
        int gridStartX = (int) (startX / GameConstants.TILE_SIZE);
        int gridStartY = (int) (startY / GameConstants.TILE_SIZE);
        int gridEndX = (int) (endX / GameConstants.TILE_SIZE);
        int gridEndY = (int) (endY / GameConstants.TILE_SIZE);
        
        // Calculate direction
        int dx = Integer.compare(gridEndX, gridStartX);
        int dy = Integer.compare(gridEndY, gridStartY);
        
        // Build corridor tiles along the path
        int currentX = gridStartX;
        int currentY = gridStartY;
        
        while (currentX != gridEndX || currentY != gridEndY) {
            // Create tiles for the width of the corridor
            for (int w = 0; w < width; w++) {
                int tileX, tileY;
                
                // Offset perpendicular to direction
                if (dx != 0) {
                    // Horizontal movement - offset vertically
                    tileX = currentX;
                    tileY = currentY + w - width / 2;
                } else {
                    // Vertical movement - offset horizontally
                    tileX = currentX + w - width / 2;
                    tileY = currentY;
                }
                
                Entity tile = createCorridorTile(tileX, tileY);
                created.add(tile);
            }
            
            // Move towards target (prefer horizontal then vertical, or L-shaped path)
            if (currentX != gridEndX) {
                currentX += dx;
            } else if (currentY != gridEndY) {
                currentY += dy;
            }
        }
        
        // Add final tiles at target
        for (int w = 0; w < width; w++) {
            int tileX, tileY;
            if (dx != 0) {
                tileX = gridEndX;
                tileY = gridEndY + w - width / 2;
            } else {
                tileX = gridEndX + w - width / 2;
                tileY = gridEndY;
            }
            Entity tile = createCorridorTile(tileX, tileY);
            created.add(tile);
        }
        
        corridorEntities.addAll(created);
        
        return created;
    }
    
    /**
     * Build a simple horizontal corridor.
     * 
     * @param startX Starting X in grid coordinates
     * @param y Y position in grid coordinates
     * @param length Length of corridor in tiles
     * @return List of created corridor entities
     */
    public List<Entity> buildHorizontalCorridor(int startX, int y, int length) {
        List<Entity> created = new ArrayList<>();
        
        for (int x = startX; x < startX + length; x++) {
            Entity tile = createCorridorTile(x, y);
            created.add(tile);
        }
        
        corridorEntities.addAll(created);
        
        return created;
    }
    
    /**
     * Build a simple vertical corridor.
     * 
     * @param x X position in grid coordinates
     * @param startY Starting Y in grid coordinates
     * @param length Length of corridor in tiles
     * @return List of created corridor entities
     */
    public List<Entity> buildVerticalCorridor(int x, int startY, int length) {
        List<Entity> created = new ArrayList<>();
        
        for (int y = startY; y < startY + length; y++) {
            Entity tile = createCorridorTile(x, y);
            created.add(tile);
        }
        
        corridorEntities.addAll(created);
        
        return created;
    }
    
    /**
     * Create a single corridor tile entity at the given grid position.
     * 
     * @param gridX X position in grid coordinates
     * @param gridY Y position in grid coordinates
     * @return The created corridor tile entity
     */
    public Entity createCorridorTile(int gridX, int gridY) {
        float worldX = gridX * GameConstants.TILE_SIZE;
        float worldY = gridY * GameConstants.TILE_SIZE;
        
        Entity tile = engine.createEntity();
        tile.add(new PositionComponent(worldX, worldY));
        // Corridor tiles don't have collision - they are floor tiles
        tile.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, CORRIDOR_COLOR));
        
        // Add sprite if available
        if (corridorSprite != null) {
            tile.add(new SpriteComponent(corridorSprite, CORRIDOR_RENDER_ORDER));
        }
        
        engine.addEntity(tile);
        
        return tile;
    }
    
    /**
     * Remove all corridor tiles created by this builder.
     */
    public void clearCorridors() {
        for (Entity entity : corridorEntities) {
            engine.removeEntity(entity);
        }
        corridorEntities.clear();
    }
    
    /**
     * Get the number of corridor tiles created.
     * 
     * @return Count of corridor entities
     */
    public int getCorridorTileCount() {
        return corridorEntities.size();
    }
    
    /**
     * Get all corridor entities created by this builder.
     * 
     * @return List of corridor entities (unmodifiable view)
     */
    public List<Entity> getCorridorEntities() {
        return new ArrayList<>(corridorEntities);
    }
}
