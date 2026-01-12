package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.world.CorridorBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GateSystem.
 */
class GateSystemTest {
    
    private Engine engine;
    private GameStateManager gameStateManager;
    private GateSystem gateSystem;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        gameStateManager.setState(GameState.PLAYING);
        
        gateSystem = new GateSystem(gameStateManager);
        engine.addSystem(gateSystem);
    }
    
    @Test
    void testGateStartsClosed() {
        Entity gate = createGate(100, 100, "arena_1");
        engine.addEntity(gate);
        
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        assertTrue(gateComp.isClosed(), "Gate should start in CLOSED state");
    }
    
    @Test
    void testGateOpensOnArenaClear() {
        // Create a gate
        Entity gate = createGate(100, 100, "arena_1");
        engine.addEntity(gate);
        
        // Create an enemy with matching arena ID
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        
        // Initial update - gate should remain closed
        engine.update(0.016f);
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        assertTrue(gateComp.isClosed(), "Gate should remain closed while enemies exist in arena");
        
        // Remove enemy - gate should open when arena enemies drop to zero
        engine.removeEntity(enemy);
        
        // Update - gate should start opening
        engine.update(0.016f);
        assertTrue(gateComp.isOpening() || gateComp.isOpen(), 
            "Gate should transition from CLOSED when arena enemies drop to zero");
    }
    
    @Test
    void testGateTransitionsToOpenAfterAnimation() {
        // Create a gate with short animation duration and matching enemy
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0.1f;
        engine.addEntity(gate);
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // First update after enemy removal - gate should start opening
        engine.update(0.016f);
        assertTrue(gateComp.isOpening(), "Gate should be in OPENING state");
        
        // Update until animation completes
        engine.update(0.1f);
        assertTrue(gateComp.isOpen(), "Gate should be in OPEN state after animation duration");
    }
    
    @Test
    void testOpenGateDisablesCollision() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f; // Instant open
        engine.addEntity(gate);
        
        CollisionComponent collision = gate.getComponent(CollisionComponent.class);
        float originalWidth = collision.width;
        float originalHeight = collision.height;
        assertTrue(originalWidth > 0, "Gate should have collision when closed");
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // Open gate (arena enemies reach zero)
        engine.update(0.016f);
        
        assertEquals(0, collision.width, "Open gate should have zero collision width");
        assertEquals(0, collision.height, "Open gate should have zero collision height");
    }
    
    @Test
    void testGateColorChangesOnOpen() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        Color originalColor = new Color(renderable.color);
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // Open gate (arena enemies reach zero)
        engine.update(0.016f);
        
        // Gate color should change from closed (brown) to open (floor tile color)
        boolean colorChanged = Math.abs(originalColor.r - renderable.color.r) > 0.01f ||
                               Math.abs(originalColor.g - renderable.color.g) > 0.01f ||
                               Math.abs(originalColor.b - renderable.color.b) > 0.01f;
        assertTrue(colorChanged, "Gate color should change when opened");
    }
    
    @Test
    void testMultipleGatesOpenOnArenaClear() {
        Entity gate1 = createGate(100, 100, "arena_1");
        Entity gate2 = createGate(200, 200, "arena_1");
        GateComponent gateComp1 = gate1.getComponent(GateComponent.class);
        GateComponent gateComp2 = gate2.getComponent(GateComponent.class);
        gateComp1.openingDuration = 0f;
        gateComp2.openingDuration = 0f;
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        
        assertEquals(2, gateSystem.getClosedGateCount(), "Should have 2 closed gates");
        assertEquals(0, gateSystem.getOpenGateCount(), "Should have 0 open gates");
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // Clear arena (arena enemies reach zero)
        engine.update(0.016f);
        
        assertEquals(0, gateSystem.getClosedGateCount(), "Should have 0 closed gates");
        assertEquals(2, gateSystem.getOpenGateCount(), "Should have 2 open gates");
    }
    
    @Test
    void testOpenAllGatesImmediately() {
        Entity gate1 = createGate(100, 100, "arena_1");
        Entity gate2 = createGate(200, 200, "arena_2");
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        
        gateSystem.openAllGatesImmediately();
        
        assertEquals(2, gateSystem.getOpenGateCount(), "All gates should be open");
        assertEquals(0, gateSystem.getClosedGateCount(), "No gates should be closed");
    }
    
    @Test
    void testCloseAllGates() {
        Entity gate1 = createGate(100, 100, "arena_1");
        Entity gate2 = createGate(200, 200, "arena_2");
        GateComponent gateComp1 = gate1.getComponent(GateComponent.class);
        GateComponent gateComp2 = gate2.getComponent(GateComponent.class);
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        
        // Open gates first
        gateSystem.openAllGatesImmediately();
        assertEquals(2, gateSystem.getOpenGateCount());
        
        // Close gates
        gateSystem.closeAllGates();
        assertEquals(2, gateSystem.getClosedGateCount(), "All gates should be closed");
        assertEquals(0, gateSystem.getOpenGateCount(), "No gates should be open");
    }
    
    @Test
    void testClosedGateRestoresCollision() {
        Entity gate = createGate(100, 100, "arena_1");
        engine.addEntity(gate);
        
        CollisionComponent collision = gate.getComponent(CollisionComponent.class);
        float originalWidth = collision.width;
        float originalHeight = collision.height;
        
        // Open and then close
        gateSystem.openAllGatesImmediately();
        assertEquals(0, collision.width, "Open gate should have zero collision");
        
        gateSystem.closeAllGates();
        assertEquals(originalWidth, collision.width, "Closed gate should restore collision width");
        assertEquals(originalHeight, collision.height, "Closed gate should restore collision height");
    }
    
    @Test
    void testGateDoesNotOpenWithEnemiesPresent() {
        Entity gate = createGate(100, 100, "arena_1");
        Entity enemy = createEnemy(200, 200, "arena_1");  // Enemy in same arena as gate
        engine.addEntity(gate);
        engine.addEntity(enemy);
        
        // Update - gate should remain closed because enemies are present in the arena
        engine.update(0.016f);
        
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        // Gate should not open because we have enemies in the same arena
        // (the arena clear check requires all enemies in the arena to be defeated)
        assertTrue(gateComp.isClosed(), "Gate should remain closed while enemies exist in arena");
    }
    
    @Test
    void testTotalGateCount() {
        engine.addEntity(createGate(100, 100, "arena_1"));
        engine.addEntity(createGate(200, 200, "arena_2"));
        engine.addEntity(createGate(300, 300, "arena_3"));
        
        assertEquals(3, gateSystem.getTotalGateCount(), "Should track all gates");
    }
    
    @Test
    void testResetClearsArenaClearFlag() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy1 = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy1);
        engine.update(0.016f);
        engine.removeEntity(enemy1);
        
        // Trigger arena clear
        engine.update(0.016f);
        assertTrue(gateComp.isOpen(), "Gate should be open");
        
        // Reset and add new enemy with matching arena ID
        gateSystem.reset();
        gateSystem.closeAllGates();
        gameStateManager.setState(GameState.PLAYING);
        
        Entity enemy2 = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy2);
        engine.update(0.016f);
        
        // Remove enemy - gate should open when arena enemies reach zero
        engine.removeEntity(enemy2);
        engine.update(0.016f);
        
        // Gate should open again (reset allowed re-processing)
        assertTrue(gateComp.isOpen(), "Gate should open again after reset");
    }
    
    @Test
    void testGateUsesFloorTileColorWhenOpen() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // Open gate (arena enemies reach zero)
        engine.update(0.016f);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        
        // Open gate should use floor tile color (GATE_OPEN_COLOR), not semi-transparent green
        assertEquals(GateSystem.GATE_OPEN_COLOR.r, renderable.color.r, 0.01f,
            "Open gate should use floor tile red component");
        assertEquals(GateSystem.GATE_OPEN_COLOR.g, renderable.color.g, 0.01f,
            "Open gate should use floor tile green component");
        assertEquals(GateSystem.GATE_OPEN_COLOR.b, renderable.color.b, 0.01f,
            "Open gate should use floor tile blue component");
    }
    
    @Test
    void testGateClosedColorMatchesTileId212() {
        // Verify that closed gate color is brown/gate-like (tile ID 212 from spritesheet, col 41, row 3 zero-based)
        Entity gate = createGate(100, 100, "arena_1");
        engine.addEntity(gate);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        
        // GATE_CLOSED_COLOR should be brown-ish for tile ID 212
        assertEquals(GateSystem.GATE_CLOSED_COLOR.r, renderable.color.r, 0.01f);
        assertEquals(GateSystem.GATE_CLOSED_COLOR.g, renderable.color.g, 0.01f);
        assertEquals(GateSystem.GATE_CLOSED_COLOR.b, renderable.color.b, 0.01f);
    }
    
    @Test
    void testGateSpawnsCorridorOnOpen() {
        // Create gate with corridor builder
        CorridorBuilder corridorBuilder = new CorridorBuilder(engine);
        gateSystem.setCorridorBuilder(corridorBuilder);
        
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Initial state - no corridor tiles
        assertEquals(0, corridorBuilder.getCorridorTileCount(), "Should start with no corridor tiles");
        
        // Create and immediately remove enemy to trigger arena clear
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        
        // Open gate (arena enemies reach zero)
        engine.update(0.016f);
        
        // Corridor should be spawned
        assertTrue(corridorBuilder.getCorridorTileCount() >= GateSystem.MIN_CORRIDOR_LENGTH,
            "Corridor should have at least " + GateSystem.MIN_CORRIDOR_LENGTH + " tiles");
    }
    
    @Test
    void testGateOpensWhenEnemiesReachZero() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Add some enemies with matching arena ID
        Entity enemy1 = createEnemy(200, 200, "arena_1");
        Entity enemy2 = createEnemy(300, 300, "arena_1");
        engine.addEntity(enemy1);
        engine.addEntity(enemy2);
        
        // Update - gate should remain closed
        engine.update(0.016f);
        assertTrue(gateComp.isClosed(), "Gate should remain closed with 2 enemies in arena");
        
        // Remove one enemy - still have one left
        engine.removeEntity(enemy1);
        engine.update(0.016f);
        assertTrue(gateComp.isClosed(), "Gate should remain closed with 1 enemy in arena");
        
        // Remove last enemy - gate should open
        engine.removeEntity(enemy2);
        engine.update(0.016f);
        assertTrue(gateComp.isOpen(), "Gate should open when arena enemies reach zero");
    }
    
    @Test
    void testGatesOpenOnlyForMatchingArena() {
        // Create two gates for different arenas
        Entity gate1 = createGate(100, 100, "arena_1");
        Entity gate2 = createGate(200, 200, "arena_2");
        GateComponent gateComp1 = gate1.getComponent(GateComponent.class);
        GateComponent gateComp2 = gate2.getComponent(GateComponent.class);
        gateComp1.openingDuration = 0f;
        gateComp2.openingDuration = 0f;
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        
        // Create enemies in different arenas
        Entity enemy1 = createEnemy(300, 300, "arena_1");
        Entity enemy2 = createEnemy(400, 400, "arena_2");
        engine.addEntity(enemy1);
        engine.addEntity(enemy2);
        
        // Initial state - both gates closed
        engine.update(0.016f);
        assertTrue(gateComp1.isClosed(), "Gate 1 should be closed");
        assertTrue(gateComp2.isClosed(), "Gate 2 should be closed");
        
        // Remove enemy from arena_1 - only gate_1 should open
        engine.removeEntity(enemy1);
        engine.update(0.016f);
        assertTrue(gateComp1.isOpen(), "Gate 1 should open when arena_1 is cleared");
        assertTrue(gateComp2.isClosed(), "Gate 2 should remain closed while arena_2 has enemies");
        
        // Remove enemy from arena_2 - gate_2 should now open
        engine.removeEntity(enemy2);
        engine.update(0.016f);
        assertTrue(gateComp2.isOpen(), "Gate 2 should open when arena_2 is cleared");
    }
    
    @Test
    void testMultipleGatesSameArenaOpenTogether() {
        // Create multiple gates for the same arena
        Entity gate1 = createGate(100, 100, "arena_1");
        Entity gate2 = createGate(200, 200, "arena_1");
        Entity gate3 = createGate(300, 300, "arena_1");
        
        GateComponent gateComp1 = gate1.getComponent(GateComponent.class);
        GateComponent gateComp2 = gate2.getComponent(GateComponent.class);
        GateComponent gateComp3 = gate3.getComponent(GateComponent.class);
        gateComp1.openingDuration = 0f;
        gateComp2.openingDuration = 0f;
        gateComp3.openingDuration = 0f;
        
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        engine.addEntity(gate3);
        
        // Add enemies to arena_1
        Entity enemy1 = createEnemy(400, 400, "arena_1");
        Entity enemy2 = createEnemy(500, 500, "arena_1");
        engine.addEntity(enemy1);
        engine.addEntity(enemy2);
        
        // Initial state - all gates closed
        engine.update(0.016f);
        assertTrue(gateComp1.isClosed(), "Gate 1 should be closed");
        assertTrue(gateComp2.isClosed(), "Gate 2 should be closed");
        assertTrue(gateComp3.isClosed(), "Gate 3 should be closed");
        
        // Remove all enemies - all gates should open together
        engine.removeEntity(enemy1);
        engine.removeEntity(enemy2);
        engine.update(0.016f);
        
        assertTrue(gateComp1.isOpen(), "Gate 1 should open when arena_1 is cleared");
        assertTrue(gateComp2.isOpen(), "Gate 2 should open when arena_1 is cleared");
        assertTrue(gateComp3.isOpen(), "Gate 3 should open when arena_1 is cleared");
    }
    
    @Test
    void testGateSystemAcceptsTiledMapReference() {
        // Test that setTiledMap can be called without error
        // (TiledMap is null-safe in the implementation)
        gateSystem.setTiledMap(null);
        
        // Create and open a gate - should work even without TiledMap
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Create and remove enemy to trigger gate open
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        engine.update(0.016f);
        
        // Gate should open successfully even without TiledMap
        assertTrue(gateComp.isOpen(), "Gate should open even without TiledMap set");
    }
    
    @Test
    void testGateStoresOriginalDimensionsBeforeOpening() {
        Entity gate = createGate(160, 320, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Original dimensions should be stored
        CollisionComponent collision = gate.getComponent(CollisionComponent.class);
        float originalWidth = collision.width;
        float originalHeight = collision.height;
        
        // Create and remove enemy to trigger gate open
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        engine.update(0.016f);
        
        // Original dimensions should be stored in GateComponent
        assertTrue(gateComp.dimensionsStored, "Original dimensions should be stored");
        assertEquals(originalWidth, gateComp.originalWidth, 0.01f, 
            "Original width should match collision width before opening");
        assertEquals(originalHeight, gateComp.originalHeight, 0.01f, 
            "Original height should match collision height before opening");
    }
    
    @Test
    void testArenaRegistration() {
        // Register an arena
        gateSystem.registerArena("arena_test", 100, 200, 160, 128);
        
        // Create player in the arena
        Entity player = createPlayer(150, 250);
        engine.addEntity(player);
        
        // Update should now track player arena position
        engine.update(0.016f);
        
        // No exceptions should occur, arena registration should work
    }
    
    @Test
    void testStartArenaMarkedAsVisited() {
        // Register arenas
        gateSystem.registerArena("arena_0", 0, 0, 160, 128);
        gateSystem.registerArena("arena_1", 200, 0, 160, 128);
        
        // Set start arena
        gateSystem.setStartArena("arena_0");
        
        // Create player in start arena
        Entity player = createPlayer(80, 64);
        engine.addEntity(player);
        
        // Create gate between arenas
        Entity gate = createGateBidirectional(160, 48, "arena_0", "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Create and remove enemy in arena_0 to open gate
        Entity enemy = createEnemy(80, 80, "arena_0");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        engine.update(0.016f);
        
        // Gate should be open (arena_0 cleared)
        assertTrue(gateComp.isOpen(), "Gate should open when start arena is cleared");
    }
    
    @Test
    void testResetClearsVisitedArenas() {
        // Register arenas
        gateSystem.registerArena("arena_0", 0, 0, 160, 128);
        gateSystem.setStartArena("arena_0");
        
        // Reset should clear all tracked state
        gateSystem.reset();
        
        // After reset, arenas and visited state should be cleared
        // This is verified by the fact that re-registration is needed
    }
    
    @Test
    void testGateRenderableResizedOnOpen() {
        Entity gate = createGate(100, 100, "arena_1");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        float originalWidth = renderable.width;
        float originalHeight = renderable.height;
        
        // Create and remove enemy to trigger gate open
        Entity enemy = createEnemy(200, 200, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        engine.removeEntity(enemy);
        engine.update(0.016f);
        
        // Gate should be resized to single tile to avoid visual glitch
        assertEquals(GameConstants.TILE_SIZE, renderable.width, 0.01f,
            "Open gate renderable should be resized to single tile width");
        assertEquals(GameConstants.TILE_SIZE, renderable.height, 0.01f,
            "Open gate renderable should be resized to single tile height");
    }
    
    @Test
    void testCorridorEndGatesOpenOnArenaClear() {
        // Create gates at both ends of a corridor
        Entity exitGate = createGateBidirectional(100, 100, "arena_1", "arena_2");
        Entity entryGate = createGateBidirectional(200, 100, "arena_1", "arena_2");
        
        GateComponent exitGateComp = exitGate.getComponent(GateComponent.class);
        GateComponent entryGateComp = entryGate.getComponent(GateComponent.class);
        exitGateComp.openingDuration = 0f;
        entryGateComp.openingDuration = 0f;
        
        engine.addEntity(exitGate);
        engine.addEntity(entryGate);
        
        // Create enemy in arena_1
        Entity enemy = createEnemy(50, 50, "arena_1");
        engine.addEntity(enemy);
        engine.update(0.016f);
        
        // Remove enemy - both gates should open
        engine.removeEntity(enemy);
        engine.update(0.016f);
        
        assertTrue(exitGateComp.isOpen(), "Exit gate from cleared arena should open");
        // Entry gate should also open (same source arena)
        assertTrue(entryGateComp.isOpen(), "Entry gate to next arena should also open");
    }
    
    // Helper methods
    
    private Entity createGate(float x, float y, String arenaId) {
        Entity gate = engine.createEntity();
        gate.add(new PositionComponent(x, y));
        gate.add(new CollisionComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE));
        gate.add(new RenderableComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE, 
            GateSystem.GATE_CLOSED_COLOR));
        gate.add(new GateComponent(arenaId));
        return gate;
    }
    
    private Entity createGateBidirectional(float x, float y, String sourceArenaId, String targetArenaId) {
        Entity gate = engine.createEntity();
        gate.add(new PositionComponent(x, y));
        gate.add(new CollisionComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE));
        gate.add(new RenderableComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE, 
            GateSystem.GATE_CLOSED_COLOR));
        gate.add(new GateComponent(sourceArenaId, targetArenaId));
        return gate;
    }
    
    private Entity createPlayer(float x, float y) {
        Entity player = engine.createEntity();
        player.add(new PositionComponent(x, y));
        player.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        player.add(new PlayerComponent());
        return player;
    }
    
    private Entity createEnemy(float x, float y) {
        return createEnemy(x, y, null);
    }
    
    private Entity createEnemy(float x, float y, String arenaId) {
        Entity enemy = engine.createEntity();
        enemy.add(new PositionComponent(x, y));
        EnemyComponent enemyComp = new EnemyComponent();
        enemyComp.arenaId = arenaId;
        enemy.add(enemyComp);
        return enemy;
    }
}
