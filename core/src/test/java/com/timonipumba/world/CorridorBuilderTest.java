package com.timonipumba.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.timonipumba.GameConstants;
import com.timonipumba.components.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Unit tests for CorridorBuilder.
 */
class CorridorBuilderTest {
    
    private Engine engine;
    private CorridorBuilder builder;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        builder = new CorridorBuilder(engine);
    }
    
    @Test
    void testCreateSingleCorridorTile() {
        Entity tile = builder.createCorridorTile(5, 3);
        
        assertNotNull(tile, "Should create corridor tile entity");
        
        PositionComponent pos = tile.getComponent(PositionComponent.class);
        assertNotNull(pos, "Corridor tile should have position component");
        assertEquals(5 * GameConstants.TILE_SIZE, pos.x, "X position should be grid * tile size");
        assertEquals(3 * GameConstants.TILE_SIZE, pos.y, "Y position should be grid * tile size");
        
        RenderableComponent renderable = tile.getComponent(RenderableComponent.class);
        assertNotNull(renderable, "Corridor tile should have renderable component");
        assertEquals(GameConstants.TILE_SIZE, renderable.width, "Width should be tile size");
        assertEquals(GameConstants.TILE_SIZE, renderable.height, "Height should be tile size");
        
        // Corridor tiles should not have collision
        CollisionComponent collision = tile.getComponent(CollisionComponent.class);
        assertNull(collision, "Corridor tiles should not have collision");
    }
    
    @Test
    void testBuildHorizontalCorridor() {
        List<Entity> tiles = builder.buildHorizontalCorridor(0, 5, 10);
        
        assertEquals(10, tiles.size(), "Should create 10 corridor tiles");
        assertEquals(10, builder.getCorridorTileCount(), "Builder should track created tiles");
        
        // Verify positions
        for (int i = 0; i < tiles.size(); i++) {
            PositionComponent pos = tiles.get(i).getComponent(PositionComponent.class);
            assertEquals((i) * GameConstants.TILE_SIZE, pos.x, "X position should increase");
            assertEquals(5 * GameConstants.TILE_SIZE, pos.y, "Y position should be constant");
        }
    }
    
    @Test
    void testBuildVerticalCorridor() {
        List<Entity> tiles = builder.buildVerticalCorridor(3, 0, 8);
        
        assertEquals(8, tiles.size(), "Should create 8 corridor tiles");
        
        // Verify positions
        for (int i = 0; i < tiles.size(); i++) {
            PositionComponent pos = tiles.get(i).getComponent(PositionComponent.class);
            assertEquals(3 * GameConstants.TILE_SIZE, pos.x, "X position should be constant");
            assertEquals((i) * GameConstants.TILE_SIZE, pos.y, "Y position should increase");
        }
    }
    
    @Test
    void testBuildCorridorBetweenPoints() {
        float startX = 0;
        float startY = 0;
        float endX = 5 * GameConstants.TILE_SIZE;
        float endY = 0;
        
        List<Entity> tiles = builder.buildCorridor(startX, startY, endX, endY, 1);
        
        assertTrue(tiles.size() > 0, "Should create corridor tiles");
        assertTrue(tiles.size() >= 5, "Should create at least 5 tiles for 5-tile path");
    }
    
    @Test
    void testBuildCorridorWithWidth() {
        // Build horizontal corridor with width 3
        float startX = 0;
        float startY = 5 * GameConstants.TILE_SIZE;
        float endX = 10 * GameConstants.TILE_SIZE;
        float endY = 5 * GameConstants.TILE_SIZE;
        
        List<Entity> tiles = builder.buildCorridor(startX, startY, endX, endY, 3);
        
        // Width of 3 means each step creates 3 tiles
        assertTrue(tiles.size() > 10, "Should create multiple tiles per step with width > 1");
    }
    
    @Test
    void testClearCorridors() {
        builder.buildHorizontalCorridor(0, 0, 5);
        assertEquals(5, builder.getCorridorTileCount());
        
        builder.clearCorridors();
        
        assertEquals(0, builder.getCorridorTileCount(), "Should clear all corridor tiles");
    }
    
    @Test
    void testGetCorridorEntities() {
        builder.buildHorizontalCorridor(0, 0, 3);
        List<Entity> entities = builder.getCorridorEntities();
        
        assertEquals(3, entities.size(), "Should return all corridor entities");
        
        // Verify it returns a copy
        entities.clear();
        assertEquals(3, builder.getCorridorTileCount(), "Clearing copy should not affect builder");
    }
    
    @Test
    void testMultipleCorridorsAccumulate() {
        builder.buildHorizontalCorridor(0, 0, 3);
        assertEquals(3, builder.getCorridorTileCount());
        
        builder.buildVerticalCorridor(5, 0, 4);
        assertEquals(7, builder.getCorridorTileCount(), "Should accumulate corridor tiles");
    }
    
    @Test
    void testCorridorTileColor() {
        Entity tile = builder.createCorridorTile(0, 0);
        RenderableComponent renderable = tile.getComponent(RenderableComponent.class);
        
        assertEquals(CorridorBuilder.CORRIDOR_COLOR.r, renderable.color.r, 0.01f);
        assertEquals(CorridorBuilder.CORRIDOR_COLOR.g, renderable.color.g, 0.01f);
        assertEquals(CorridorBuilder.CORRIDOR_COLOR.b, renderable.color.b, 0.01f);
    }
    
    @Test
    void testCorridorWithDiagonalPath() {
        // Build corridor with diagonal component (L-shaped path)
        float startX = 0;
        float startY = 0;
        float endX = 3 * GameConstants.TILE_SIZE;
        float endY = 3 * GameConstants.TILE_SIZE;
        
        List<Entity> tiles = builder.buildCorridor(startX, startY, endX, endY, 1);
        
        assertTrue(tiles.size() >= 6, "Should create enough tiles for L-shaped path (3 horizontal + 3 vertical)");
    }
    
    @Test
    void testZeroLengthCorridor() {
        List<Entity> tiles = builder.buildHorizontalCorridor(5, 5, 0);
        
        assertEquals(0, tiles.size(), "Zero length corridor should create no tiles");
    }
    
    @Test
    void testNegativePositionCorridor() {
        // Should handle negative grid positions (though unusual for game use)
        Entity tile = builder.createCorridorTile(-1, -1);
        
        PositionComponent pos = tile.getComponent(PositionComponent.class);
        assertEquals(-GameConstants.TILE_SIZE, pos.x, "Should handle negative grid positions");
        assertEquals(-GameConstants.TILE_SIZE, pos.y, "Should handle negative grid positions");
    }
    
    @Test
    void testCorridorRenderOrder() {
        builder.setCorridorSprite(null); // No sprite, so no SpriteComponent
        Entity tile = builder.createCorridorTile(0, 0);
        
        // Without sprite, should just have RenderableComponent
        RenderableComponent renderable = tile.getComponent(RenderableComponent.class);
        assertNotNull(renderable, "Should have renderable component");
        
        SpriteComponent sprite = tile.getComponent(SpriteComponent.class);
        assertNull(sprite, "Should not have sprite component when no sprite set");
    }
    
    @Test
    void testCorridorTilesAreWalkable() {
        // Verify corridor tiles do not have collision (are walkable)
        Entity tile = builder.createCorridorTile(5, 5);
        
        CollisionComponent collision = tile.getComponent(CollisionComponent.class);
        assertNull(collision, "Corridor tiles should not have collision - they must be walkable");
    }
    
    @Test
    void testCorridorTilesAreVisible() {
        // Verify corridor tiles have renderable component (are visible)
        Entity tile = builder.createCorridorTile(5, 5);
        
        RenderableComponent renderable = tile.getComponent(RenderableComponent.class);
        assertNotNull(renderable, "Corridor tiles must be visible");
        assertEquals(GameConstants.TILE_SIZE, renderable.width, "Corridor should be tile sized");
        assertEquals(GameConstants.TILE_SIZE, renderable.height, "Corridor should be tile sized");
    }
    
    @Test
    void testMinCorridorLengthConstant() {
        // Verify minimum corridor length is 6 tiles as per specification
        assertEquals(6, CorridorBuilder.MIN_CORRIDOR_LENGTH,
            "Minimum corridor length should be 6 tiles per specification");
    }
    
    @Test
    void testCorridorMeetsMinimumLength() {
        // Build a corridor of exactly minimum length (6 tiles horizontal)
        List<Entity> tiles = builder.buildHorizontalCorridor(0, 5, 6);
        
        // Verify corridor meets minimum length requirement
        assertEquals(CorridorBuilder.MIN_CORRIDOR_LENGTH, tiles.size(), 
            "Should create exactly minimum corridor length of " + CorridorBuilder.MIN_CORRIDOR_LENGTH + " tiles");
    }
}
