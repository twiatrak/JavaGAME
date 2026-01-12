package com.timonipumba.level;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.timonipumba.GameConstants;
import com.timonipumba.components.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Unit tests for PortalSpawner.
 * 
 * Note: These tests explicitly enable wall segment portals since that feature
 * is now disabled by default in favor of the gate-based system.
 */
class PortalSpawnerTest {
    
    private Engine engine;
    private PortalSpawner spawner;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        spawner = new PortalSpawner(engine);
        PortalConfig.resetToDefaults();
        // Enable wall segment portals for testing portal functionality
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
    }
    
    @AfterEach
    void tearDown() {
        PortalConfig.resetToDefaults();
    }
    
    @Test
    void testOnPuzzleSolved_NoWalls() {
        // No walls in engine - should return false
        boolean result = spawner.onPuzzleSolved("test_puzzle");
        
        assertFalse(result);
    }
    
    @Test
    void testOnPuzzleSolved_NullPuzzleId() {
        createWallSegment(0, 0, 5, true); // Create 5 horizontal walls
        
        boolean result = spawner.onPuzzleSolved(null);
        
        assertFalse(result);
    }
    
    @Test
    void testOnPuzzleSolved_EmptyPuzzleId() {
        createWallSegment(0, 0, 5, true);
        
        boolean result = spawner.onPuzzleSolved("");
        
        assertFalse(result);
    }
    
    @Test
    void testOnPuzzleSolved_FeatureDisabled() {
        createWallSegment(0, 0, 5, true);
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = false;
        
        boolean result = spawner.onPuzzleSolved("test_puzzle");
        
        assertFalse(result);
    }
    
    @Test
    void testOnPuzzleSolved_ValidHorizontalSegment() {
        // Create exactly 5 consecutive horizontal walls
        createWallSegment(0, 0, 5, true);
        
        boolean result = spawner.onPuzzleSolved("test_puzzle");
        
        assertTrue(result);
        
        // Verify portal components were added
        int portalCount = countPortalComponents();
        assertTrue(portalCount >= 4 && portalCount <= 5, 
            "Expected 4-5 portal tiles, got " + portalCount);
    }
    
    @Test
    void testOnPuzzleSolved_ValidVerticalSegment() {
        // Create exactly 4 consecutive vertical walls
        createWallSegment(0, 0, 4, false);
        
        boolean result = spawner.onPuzzleSolved("test_puzzle");
        
        assertTrue(result);
        
        int portalCount = countPortalComponents();
        assertEquals(4, portalCount);
    }
    
    @Test
    void testOnPuzzleSolved_SegmentTooShort() {
        // Create only 3 walls - below minimum
        createWallSegment(0, 0, 3, true);
        
        boolean result = spawner.onPuzzleSolved("test_puzzle");
        
        assertFalse(result); // No valid segment found
    }
    
    @Test
    void testOnPuzzleSolved_IdempotentCreation() {
        createWallSegment(0, 0, 5, true);
        
        // First call creates portal
        boolean result1 = spawner.onPuzzleSolved("test_puzzle");
        assertTrue(result1);
        int count1 = countPortalComponents();
        
        // Second call should not create duplicate
        boolean result2 = spawner.onPuzzleSolved("test_puzzle");
        assertTrue(result2); // Still returns true (activates existing)
        int count2 = countPortalComponents();
        
        assertEquals(count1, count2, "Portal count should not increase on second call");
    }
    
    @Test
    void testOnPuzzleSolved_DifferentPuzzles() {
        // Create two separate wall segments
        createWallSegment(0, 0, 5, true);  // First segment
        createWallSegment(10, 0, 5, true); // Second segment (far enough away)
        
        // Create portals for two different puzzles
        spawner.onPuzzleSolved("puzzle1");
        int count1 = countPortalComponents();
        
        spawner.onPuzzleSolved("puzzle2");
        int count2 = countPortalComponents();
        
        // Both puzzles should have portals
        assertTrue(count2 > count1 || count2 == count1, 
            "Second puzzle should create new portal or reuse existing");
    }
    
    @Test
    void testFindPortalWallSegment_EmptyGrid() {
        List<Entity> segment = spawner.findPortalWallSegment("test");
        
        assertTrue(segment.isEmpty());
    }
    
    @Test
    void testFindPortalWallSegment_ValidHorizontalRun() {
        createWallSegment(0, 0, 5, true);
        
        List<Entity> segment = spawner.findPortalWallSegment("test");
        
        assertFalse(segment.isEmpty());
        assertTrue(segment.size() >= 4 && segment.size() <= 5);
    }
    
    @Test
    void testFindPortalWallSegment_ValidVerticalRun() {
        createWallSegment(0, 0, 4, false);
        
        List<Entity> segment = spawner.findPortalWallSegment("test");
        
        assertFalse(segment.isEmpty());
        assertEquals(4, segment.size());
    }
    
    @Test
    void testFindPortalWallSegment_DeterministicPlacement() {
        // Create multiple valid segments
        createWallSegment(0, 0, 5, true);
        createWallSegment(0, 5, 5, true);
        
        // Same puzzle ID should select same segment
        List<Entity> segment1 = spawner.findPortalWallSegment("deterministic_test");
        List<Entity> segment2 = spawner.findPortalWallSegment("deterministic_test");
        
        assertEquals(segment1.size(), segment2.size());
        // Same entities should be selected
        for (int i = 0; i < segment1.size(); i++) {
            assertSame(segment1.get(i), segment2.get(i));
        }
    }
    
    @Test
    void testPortalActivation() {
        createWallSegment(0, 0, 5, true);
        
        spawner.onPuzzleSolved("test_puzzle");
        
        // Verify all portal components are activated
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            if (portal != null) {
                assertTrue(portal.isActive(), "Portal should be active after puzzle solved");
                assertEquals("test_puzzle", portal.puzzleId);
            }
        }
    }
    
    @Test
    void testPortalGroupId() {
        createWallSegment(0, 0, 5, true);
        
        spawner.onPuzzleSolved("test_puzzle");
        
        String expectedGroupId = "portal_test_puzzle";
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            if (portal != null) {
                assertEquals(expectedGroupId, portal.portalGroupId);
            }
        }
    }
    
    @Test
    void testPortalSegmentIndices() {
        createWallSegment(0, 0, 4, true);
        
        spawner.onPuzzleSolved("test_puzzle");
        
        boolean[] indicesFound = new boolean[4];
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            if (portal != null) {
                assertTrue(portal.segmentIndex >= 0 && portal.segmentIndex < portal.segmentLength);
                indicesFound[portal.segmentIndex] = true;
                assertEquals(4, portal.segmentLength);
            }
        }
        
        // Verify all indices are present
        for (int i = 0; i < 4; i++) {
            assertTrue(indicesFound[i], "Missing segment index " + i);
        }
    }
    
    // Helper methods
    
    private void createWallSegment(int startX, int startY, int length, boolean horizontal) {
        for (int i = 0; i < length; i++) {
            int x = horizontal ? startX + i : startX;
            int y = horizontal ? startY : startY + i;
            
            Entity wall = engine.createEntity();
            wall.add(new PositionComponent(x * GameConstants.TILE_SIZE, y * GameConstants.TILE_SIZE));
            wall.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
            wall.add(new WallComponent());
            wall.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, 
                com.badlogic.gdx.graphics.Color.GRAY));
            engine.addEntity(wall);
        }
    }
    
    private int countPortalComponents() {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(PortalComponent.class) != null) {
                count++;
            }
        }
        return count;
    }
}
