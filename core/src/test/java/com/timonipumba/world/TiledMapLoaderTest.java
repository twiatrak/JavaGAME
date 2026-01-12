package com.timonipumba.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.timonipumba.GameConstants;
import com.timonipumba.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TiledMapLoader safe spawn functionality.
 * 
 * Tests that players are spawned in safe positions even if the 
 * initial spawn coordinates overlap with wall tiles.
 */
class TiledMapLoaderTest {
    
    private Engine engine;
    private TiledMapLoader mapLoader;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        mapLoader = new TiledMapLoader(engine);
    }
    
    @Test
    void testIsPositionSolid_NoWalls() {
        // No walls in engine
        boolean solid = mapLoader.isPositionSolid(100, 100, 16, 16);
        
        assertFalse(solid, "Position should not be solid when no walls exist");
    }
    
    @Test
    void testIsPositionSolid_WallPresent() {
        // Add a wall at position (100, 100)
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(100, 100));
        wall.add(new CollisionComponent(16, 16));
        wall.add(new WallComponent());
        engine.addEntity(wall);
        
        // Test position overlapping the wall
        boolean solid = mapLoader.isPositionSolid(100, 100, 16, 16);
        
        assertTrue(solid, "Position should be solid when overlapping a wall");
    }
    
    @Test
    void testIsPositionSolid_AdjacentToWall() {
        // Add a wall at position (100, 100)
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(100, 100));
        wall.add(new CollisionComponent(16, 16));
        wall.add(new WallComponent());
        engine.addEntity(wall);
        
        // Test position adjacent to the wall (not overlapping)
        boolean solid = mapLoader.isPositionSolid(116, 100, 16, 16);
        
        assertFalse(solid, "Position should not be solid when adjacent to but not overlapping a wall");
    }
    
    @Test
    void testIsPositionSolid_PartialOverlap() {
        // Add a wall at position (100, 100)
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(100, 100));
        wall.add(new CollisionComponent(16, 16));
        wall.add(new WallComponent());
        engine.addEntity(wall);
        
        // Test position that partially overlaps the wall
        boolean solid = mapLoader.isPositionSolid(108, 108, 16, 16);
        
        assertTrue(solid, "Position should be solid when any corner overlaps a wall");
    }
    
    @Test
    void testFindSafeSpawnPosition_AlreadySafe() {
        // No walls, so starting position should be safe
        float[] safePos = mapLoader.findSafeSpawnPosition(100, 100, 16, 16);
        
        assertEquals(100, safePos[0], "X should be unchanged when already safe");
        assertEquals(100, safePos[1], "Y should be unchanged when already safe");
    }
    
    @Test
    void testFindSafeSpawnPosition_NudgeRequired() {
        // Add a wall at the spawn position
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(100, 100));
        wall.add(new CollisionComponent(16, 16));
        wall.add(new WallComponent());
        engine.addEntity(wall);
        
        // Try to spawn at wall position
        float[] safePos = mapLoader.findSafeSpawnPosition(100, 100, 16, 16);
        
        // Should have moved to a safe position
        assertFalse(mapLoader.isPositionSolid(safePos[0], safePos[1], 16, 16),
            "Resulting position should not be solid");
        
        // Should be within a reasonable distance of the original
        float dx = Math.abs(safePos[0] - 100);
        float dy = Math.abs(safePos[1] - 100);
        assertTrue(dx <= GameConstants.TILE_SIZE * 10 && dy <= GameConstants.TILE_SIZE * 10,
            "Safe position should be within scan radius of original");
    }
    
    @Test
    void testFindSafeSpawnPosition_MultipleWalls() {
        // Add walls in a small cluster
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Entity wall = engine.createEntity();
                wall.add(new PositionComponent(100 + i * 16, 100 + j * 16));
                wall.add(new CollisionComponent(16, 16));
                wall.add(new WallComponent());
                engine.addEntity(wall);
            }
        }
        
        // Try to spawn in the center of the cluster
        float[] safePos = mapLoader.findSafeSpawnPosition(116, 116, 16, 16);
        
        // Should have moved to a safe position outside the cluster
        assertFalse(mapLoader.isPositionSolid(safePos[0], safePos[1], 16, 16),
            "Resulting position should not be solid");
    }
    
    @Test
    void testFindSafeSpawnPosition_ZeroCoordinates() {
        // Spawn at origin (corner case)
        float[] safePos = mapLoader.findSafeSpawnPosition(0, 0, 16, 16);
        
        // Should work without issues
        assertFalse(mapLoader.isPositionSolid(safePos[0], safePos[1], 16, 16),
            "Resulting position should not be solid");
        assertTrue(safePos[0] >= 0 && safePos[1] >= 0,
            "Safe position should have non-negative coordinates");
    }
    
    @Test
    void testFindSafeSpawnPosition_32x32Player() {
        // No walls, should return starting position (normalized)
        float[] safePos = mapLoader.findSafeSpawnPosition(100, 100, 32, 32);
        
        assertEquals(100, safePos[0], "X should be unchanged when already safe with 32x32 collision");
        assertEquals(100, safePos[1], "Y should be unchanged when already safe with 32x32 collision");
    }
    
    @Test
    void testIsPositionSolid_32x32Collision() {
        // Add a wall at position (100, 100)
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(100, 100));
        wall.add(new CollisionComponent(16, 16));
        wall.add(new WallComponent());
        engine.addEntity(wall);
        
        // Test 32x32 entity that should overlap with wall
        // Position (80, 80) with size 32 covers (80 to 111, 80 to 111), overlaps wall at (100 to 115, 100 to 115)
        boolean solid = mapLoader.isPositionSolid(80, 80, 32, 32);
        
        assertTrue(solid, "32x32 entity at (80,80) should overlap wall at (100,100)");
        
        // Position (68, 68) with size 32 covers (68 to 99, 68 to 99), does NOT overlap wall at (100 to 115)
        boolean notSolid = mapLoader.isPositionSolid(68, 68, 32, 32);
        
        assertFalse(notSolid, "32x32 entity at (68,68) should not overlap wall at (100,100)");
    }
    
    @Test
    void testFindSafeSpawnPosition_32x32InWallCluster() {
        // Add walls in a corridor pattern - 32x32 player needs 2-tile-wide gap
        // Wall row at y=0-15
        for (int x = 0; x < 5; x++) {
            Entity wall = engine.createEntity();
            wall.add(new PositionComponent(x * 16, 0));
            wall.add(new CollisionComponent(16, 16));
            wall.add(new WallComponent());
            engine.addEntity(wall);
        }
        
        // Try to spawn 32x32 player with bottom edge at y=-8 (would overlap walls)
        // After normalization, this tests that a 32x32 player finds a safe position
        float[] safePos = mapLoader.findSafeSpawnPosition(32, -8, 32, 32);
        
        // Should find a safe position for 32x32 collision
        assertFalse(mapLoader.isPositionSolid(safePos[0], safePos[1], 32, 32),
            "32x32 player should find safe spawn position away from wall cluster");
    }
}
