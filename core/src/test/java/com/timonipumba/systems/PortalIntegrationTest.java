package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for the full puzzle-solve → portal-appear → teleport flow.
 * 
 * These tests verify:
 * 1. No portal visible before puzzle is solved
 * 2. Portal appears (wall tiles turn green) after puzzle is solved
 * 3. Portal segment length is within [4,5] tiles
 * 4. Stepping onto portal activates level transition
 * 
 * Note: These tests explicitly enable wall segment portals since that feature
 * is now disabled by default in favor of the gate-based system.
 */
class PortalIntegrationTest {
    
    private Engine engine;
    private GameStateManager gameStateManager;
    private PortalSpawner portalSpawner;
    private PortalSystem portalSystem;
    private TestLevelManager levelManager;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        gameStateManager.setState(GameState.PLAYING);
        
        portalSpawner = new PortalSpawner(engine);
        portalSystem = new PortalSystem(gameStateManager);
        levelManager = new TestLevelManager();
        
        portalSystem.setLevelManager(levelManager);
        portalSpawner.setPortalSystem(portalSystem);
        
        engine.addSystem(portalSystem);
        
        PortalConfig.resetToDefaults();
        // Enable wall segment portals for testing portal functionality
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
        PuzzleRegistry.clear();
    }
    
    @AfterEach
    void tearDown() {
        PortalConfig.resetToDefaults();
        PuzzleRegistry.clear();
    }
    
    /**
     * Test: No portal visible before puzzle is solved.
     * Verifies that wall tiles don't have PortalComponent until puzzle is solved.
     */
    @Test
    void testNoPortalBeforePuzzleSolved() {
        // Setup: Create walls and register puzzle
        createWallGrid(10, 10);
        registerTestPuzzle("test_puzzle");
        
        // Verify no portal components exist
        int portalCount = countPortalComponents();
        assertEquals(0, portalCount, "No portals should exist before puzzle is solved");
        
        // Verify all walls are gray (not green)
        for (Entity entity : engine.getEntities()) {
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            WallComponent wall = entity.getComponent(WallComponent.class);
            if (wall != null && renderable != null) {
                assertFalse(isPortalGreen(renderable.color), 
                    "Wall should not be green before puzzle is solved");
            }
        }
    }
    
    /**
     * Test: Portal appears after puzzle is solved with correct segment length.
     */
    @Test
    void testPortalAppearsAfterPuzzleSolved() {
        // Setup
        createWallGrid(10, 10);
        registerTestPuzzle("test_puzzle");
        
        // Solve the puzzle
        boolean created = portalSpawner.onPuzzleSolved("test_puzzle");
        
        // Verify portal was created
        assertTrue(created, "Portal should be created when puzzle is solved");
        
        // Verify portal segment length
        int portalCount = countActivePortalComponents();
        assertTrue(portalCount >= PortalConfig.MIN_SEGMENT_LENGTH && 
                   portalCount <= PortalConfig.MAX_SEGMENT_LENGTH,
            "Portal segment length should be between " + PortalConfig.MIN_SEGMENT_LENGTH + 
            " and " + PortalConfig.MAX_SEGMENT_LENGTH + ", got " + portalCount);
        
        // Verify portal tiles are green
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            if (portal != null && portal.isActive() && renderable != null) {
                assertTrue(isPortalGreen(renderable.color),
                    "Active portal tiles should be green");
            }
        }
    }
    
    /**
     * Test: Stepping onto portal triggers level transition.
     */
    @Test
    void testPortalTriggersLevelTransition() {
        // Setup walls and create portal
        createWallGrid(10, 10);
        registerTestPuzzle("test_puzzle");
        portalSpawner.onPuzzleSolved("test_puzzle");
        
        // Find an active portal position
        float portalX = -1, portalY = -1;
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            if (portal != null && portal.isActive() && pos != null) {
                portalX = pos.x;
                portalY = pos.y;
                break;
            }
        }
        
        assertTrue(portalX >= 0 && portalY >= 0, "Should find an active portal position");
        
        // Create player at portal position
        Entity player = createPlayer(portalX, portalY);
        engine.addEntity(player);
        
        // Process one frame
        engine.update(0.016f);
        
        // Verify level transition was triggered
        assertTrue(levelManager.loadNextLevelCalled, 
            "Stepping onto active portal should trigger level transition");
    }
    
    /**
     * Test: Portal only activates after puzzle is specifically solved.
     * Multiple puzzles should have independent portals.
     */
    @Test
    void testMultiplePuzzlesIndependentPortals() {
        // Create two separate wall segments
        createWallLine(0, 0, 5, true);  // First segment
        createWallLine(0, 5, 5, true);  // Second segment (separate row)
        
        registerTestPuzzle("puzzle_a");
        registerTestPuzzle("puzzle_b");
        
        // Solve only puzzle_a
        portalSpawner.onPuzzleSolved("puzzle_a");
        
        // Verify only one portal group exists
        int activeCount = countActivePortalComponents();
        assertTrue(activeCount >= 4 && activeCount <= 5,
            "Only puzzle_a portal should be active");
        
        // All active portals should be for puzzle_a
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            if (portal != null && portal.isActive()) {
                assertEquals("puzzle_a", portal.puzzleId);
            }
        }
    }
    
    /**
     * Test: Inactive portal does not trigger level transition.
     */
    @Test
    void testInactivePortalDoesNotTrigger() {
        // Create walls but don't solve puzzle
        createWallGrid(10, 10);
        registerTestPuzzle("test_puzzle");
        
        // Create portal manually but don't activate it
        Entity wall = engine.getEntities().first();
        PortalComponent portalComp = new PortalComponent("test_puzzle");
        // Don't call activate()
        wall.add(portalComp);
        
        PositionComponent wallPos = wall.getComponent(PositionComponent.class);
        
        // Create player at wall position
        Entity player = createPlayer(wallPos.x, wallPos.y);
        engine.addEntity(player);
        
        // Process
        engine.update(0.016f);
        
        // Verify no transition
        assertFalse(levelManager.loadNextLevelCalled,
            "Inactive portal should not trigger level transition");
    }
    
    /**
     * Test: Legacy exit is hidden when wall-segment portals are enabled.
     */
    @Test
    void testLegacyExitHiddenWhenFeatureEnabled() {
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
        PortalConfig.HIDE_LEGACY_EXITS = true;
        
        // Create a legacy exit
        Entity exit = engine.createEntity();
        exit.add(new PositionComponent(100, 100));
        exit.add(new CollisionComponent(16, 16));
        exit.add(new RenderableComponent(16, 16, Color.LIME));
        exit.add(new ExitComponent());
        engine.addEntity(exit);
        
        // Create and add ExitSystem
        ExitSystem exitSystem = new ExitSystem(gameStateManager);
        engine.addSystem(exitSystem);
        
        // Process one frame
        engine.update(0.016f);
        
        // Verify exit is hidden
        RenderableComponent renderable = exit.getComponent(RenderableComponent.class);
        assertEquals(0f, renderable.color.a, 0.01f,
            "Legacy exit should be hidden (alpha=0)");
        
        CollisionComponent collision = exit.getComponent(CollisionComponent.class);
        assertEquals(0f, collision.width,
            "Legacy exit collision should be disabled");
    }
    
    /**
     * Test: Full end-to-end flow from puzzle creation to teleport.
     */
    @Test
    void testFullPuzzleToTeleportFlow() {
        // Step 1: Setup scene with walls and puzzle door
        createWallGrid(10, 10);
        
        Puzzle puzzle = registerTestPuzzle("cipher_door_puzzle");
        
        // Step 2: Verify initial state - no portals
        assertEquals(0, countActivePortalComponents());
        
        // Step 3: Simulate puzzle being solved (would normally happen via PuzzleOverlaySystem)
        portalSpawner.onPuzzleSolved("cipher_door_puzzle");
        
        // Step 4: Verify portal appeared
        int activePortals = countActivePortalComponents();
        assertTrue(activePortals >= 4 && activePortals <= 5,
            "Portal should appear with 4-5 green tiles");
        
        // Step 5: Verify portal tiles are green
        int greenTileCount = 0;
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            if (portal != null && portal.isActive() && renderable != null) {
                if (isPortalGreen(renderable.color)) {
                    greenTileCount++;
                }
            }
        }
        assertEquals(activePortals, greenTileCount, 
            "All active portal tiles should be green");
        
        // Step 6: Create player at portal and verify teleport
        float portalX = -1, portalY = -1;
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            PositionComponent pos = entity.getComponent(PositionComponent.class);
            if (portal != null && portal.isActive() && pos != null) {
                portalX = pos.x;
                portalY = pos.y;
                break;
            }
        }
        
        Entity player = createPlayer(portalX, portalY);
        engine.addEntity(player);
        
        engine.update(0.016f);
        
        assertTrue(levelManager.loadNextLevelCalled,
            "Player should teleport when entering portal");
    }
    
    /**
     * Test: No visible green exit entities when WALL_SEGMENT_PORTALS_ENABLED is true.
     * Verifies that legacy green square portals are not visible in the scene.
     */
    @Test
    void testNoVisibleLegacyExitsWhenFeatureEnabled() {
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
        PortalConfig.HIDE_LEGACY_EXITS = true;
        
        // Create a legacy exit entity (simulating what TiledMapLoader would create)
        Entity exit = engine.createEntity();
        exit.add(new PositionComponent(100, 100));
        exit.add(new CollisionComponent(16, 16));
        exit.add(new RenderableComponent(16, 16, Color.LIME));
        exit.add(new ExitComponent());
        engine.addEntity(exit);
        
        // Create walls for the portal system
        createWallGrid(10, 10);
        
        // Create ExitSystem which should hide the legacy exit
        ExitSystem exitSystem = new ExitSystem(gameStateManager);
        engine.addSystem(exitSystem);
        
        // Process one frame
        engine.update(0.016f);
        
        // Verify: Check all exit entities are hidden (alpha = 0 or collision = 0)
        int visibleExitCount = 0;
        for (Entity entity : engine.getEntities()) {
            ExitComponent exitComp = entity.getComponent(ExitComponent.class);
            if (exitComp != null) {
                RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
                CollisionComponent collision = entity.getComponent(CollisionComponent.class);
                
                boolean isVisible = (renderable != null && renderable.color.a > 0);
                boolean hasCollision = (collision != null && collision.width > 0 && collision.height > 0);
                
                if (isVisible || hasCollision) {
                    visibleExitCount++;
                }
            }
        }
        
        assertEquals(0, visibleExitCount, 
            "No legacy exit entities should be visible when WALL_SEGMENT_PORTALS_ENABLED is true");
    }
    
    /**
     * Test: Portal spawner is triggered and creates a portal with at least 4 tiles.
     * This verifies that the puzzle-solve-to-portal flow produces a valid portal segment.
     */
    @Test
    void testPuzzleSolveSpawnsValidPortal() {
        // Create walls in a grid pattern to ensure valid segments exist
        createWallGrid(12, 12);
        
        // Register puzzle
        registerTestPuzzle("test_level_puzzle");
        
        // Solve the puzzle
        boolean portalCreated = portalSpawner.onPuzzleSolved("test_level_puzzle");
        
        // Verify a portal was created
        assertTrue(portalCreated, "Portal should be created when puzzle is solved");
        
        // Verify at least MIN_SEGMENT_LENGTH portal tiles exist
        int portalTileCount = countActivePortalComponents();
        assertTrue(portalTileCount >= PortalConfig.MIN_SEGMENT_LENGTH,
            "Portal should have at least " + PortalConfig.MIN_SEGMENT_LENGTH + 
            " active tiles, got " + portalTileCount);
        
        // Verify all portal tiles are green
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            if (portal != null && portal.isActive() && renderable != null) {
                assertTrue(isPortalGreen(renderable.color),
                    "All active portal tiles should be visually distinct (green)");
            }
        }
    }
    
    /**
     * Test: Vigenere puzzle portal is immediately visible after solve.
     * 
     * This tests the exact scenario from the bug report where the vigenere_puzzle
     * portal was created but appeared as an invisible/black square because the
     * portal activation wasn't immediate.
     */
    @Test
    void testVigenerePuzzlePortalImmediatelyVisible() {
        // Setup: Create walls (simulating the level layout)
        createWallGrid(30, 40);  // Similar to the log positions (grid 25-29, y=39)
        
        // Register vigenere puzzle
        registerVigenerePuzzle("vigenere_puzzle");
        
        // Solve the puzzle - portal should be immediately visible (no engine.update() needed)
        boolean portalCreated = portalSpawner.onPuzzleSolved("vigenere_puzzle");
        
        // Verify portal was created
        assertTrue(portalCreated, "Portal should be created for vigenere_puzzle");
        
        // Verify portals are immediately active (before engine.update)
        int activeCount = countActivePortalComponents();
        assertTrue(activeCount >= PortalConfig.MIN_SEGMENT_LENGTH,
            "Portal should have " + PortalConfig.MIN_SEGMENT_LENGTH + "+ active tiles immediately, got " + activeCount);
        
        // Verify portal tiles have correct green color (not gray/black)
        int greenTileCount = 0;
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            if (portal != null && portal.isActive()) {
                assertNotNull(renderable, "Active portal tile should have RenderableComponent");
                assertTrue(isPortalGreen(renderable.color),
                    "Portal tile color should be green, not gray or black. " +
                    "Got color R=" + renderable.color.r + " G=" + renderable.color.g + " B=" + renderable.color.b);
                greenTileCount++;
            }
        }
        
        assertEquals(activeCount, greenTileCount,
            "All active portal tiles should be green");
        
        // Verify PortalSystem can also see the portals (after engine.update)
        engine.update(0.016f);
        int systemActiveCount = portalSystem.getActivePortalCount();
        assertTrue(systemActiveCount >= activeCount, 
            "PortalSystem should see at least " + activeCount + " active portals after update, got " + systemActiveCount);
    }
    
    /**
     * Test: Portal is created and activated in the same frame without engine.update().
     * 
     * This validates the fix for the timing issue where adding PortalComponent
     * to entities wasn't reflected in PortalSystem's cached Family query.
     */
    @Test
    void testPortalCreatedAndActivatedWithoutEngineUpdate() {
        // Create walls
        createWallGrid(10, 10);
        registerTestPuzzle("timing_test_puzzle");
        
        // Before engine.update() - PortalSystem's cached query is empty
        assertEquals(0, portalSystem.getInactivePortalCount());
        assertEquals(0, portalSystem.getActivePortalCount());
        
        // Create and activate portal (should work without engine.update)
        portalSpawner.onPuzzleSolved("timing_test_puzzle");
        
        // Verify portals are active even before engine.update()
        int activeCount = countActivePortalComponents();
        assertTrue(activeCount >= 4, "Portals should be active immediately, got " + activeCount);
        
        // Verify colors are green immediately
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            RenderableComponent renderable = entity.getComponent(RenderableComponent.class);
            if (portal != null && portal.isActive()) {
                assertTrue(isPortalGreen(renderable.color),
                    "Portal should be green immediately, not after engine.update()");
            }
        }
    }
    
    private void registerVigenerePuzzle(String puzzleId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "cipher");
        data.put("ciphertext", "VQBWZ");
        data.put("key", "KEY");
        data.put("answer", "HELLO");
        data.put("hint", "The key is KEY");
        Puzzle puzzle = new Puzzle(puzzleId, "cipher", data);
        PuzzleRegistry.register(puzzle);
    }
    
    // Helper methods
    
    private void createWallGrid(int width, int height) {
        // Create a grid of walls around the perimeter
        for (int x = 0; x < width; x++) {
            createWall(x, 0);           // Bottom row
            createWall(x, height - 1);  // Top row
        }
        for (int y = 1; y < height - 1; y++) {
            createWall(0, y);           // Left column
            createWall(width - 1, y);   // Right column
        }
    }
    
    private void createWallLine(int startX, int startY, int length, boolean horizontal) {
        for (int i = 0; i < length; i++) {
            int x = horizontal ? startX + i : startX;
            int y = horizontal ? startY : startY + i;
            createWall(x, y);
        }
    }
    
    private void createWall(int gridX, int gridY) {
        Entity wall = engine.createEntity();
        wall.add(new PositionComponent(gridX * GameConstants.TILE_SIZE, gridY * GameConstants.TILE_SIZE));
        wall.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        wall.add(new WallComponent());
        wall.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, Color.GRAY));
        engine.addEntity(wall);
    }
    
    private Entity createPlayer(float x, float y) {
        Entity player = engine.createEntity();
        player.add(new PositionComponent(x, y));
        player.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        player.add(new PlayerComponent());
        return player;
    }
    
    private Puzzle registerTestPuzzle(String puzzleId) {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "TEST");
        data.put("ciphertext", "ENCRYPTED");
        Puzzle puzzle = new Puzzle(puzzleId, "cipher", data);
        PuzzleRegistry.register(puzzle);
        return puzzle;
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
    
    private int countActivePortalComponents() {
        int count = 0;
        for (Entity entity : engine.getEntities()) {
            PortalComponent portal = entity.getComponent(PortalComponent.class);
            if (portal != null && portal.isActive()) {
                count++;
            }
        }
        return count;
    }
    
    private boolean isPortalGreen(Color color) {
        // Check if color is close to portal green
        return color.g > 0.7f && color.r < 0.5f && color.b < 0.5f;
    }
    
    // Test double for LevelManager
    private static class TestLevelManager implements LevelManager {
        boolean loadNextLevelCalled = false;
        boolean restartLevelCalled = false;
        
        @Override
        public void loadNextLevel() {
            loadNextLevelCalled = true;
        }
        
        @Override
        public void restartLevel() {
            restartLevelCalled = true;
        }
    }
}
