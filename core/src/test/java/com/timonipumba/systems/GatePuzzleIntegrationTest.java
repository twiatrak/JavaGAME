package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.PortalConfig;
import com.timonipumba.level.Puzzle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for gate-based progression without portals.
 * 
 * These tests verify:
 * 1. Portals are disabled by default (no green/black squares)
 * 2. Gates use correct tile ID 211 colors
 * 3. Entering arena closes gate
 * 4. Clearing arena opens gate
 * 5. Puzzle completion opens gates (not portals)
 */
class GatePuzzleIntegrationTest {
    
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
        
        // Ensure portals are disabled (default behavior)
        PortalConfig.resetToDefaults();
    }
    
    @AfterEach
    void tearDown() {
        PortalConfig.resetToDefaults();
    }
    
    @Test
    void testPortalsDisabledByDefault() {
        // Verify portals are disabled
        assertFalse(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED,
            "Wall segment portals should be disabled by default");
    }
    
    @Test
    void testNoPortalEntitiesInGateBasedProgression() {
        // Create gates (the new progression system)
        Entity gate1 = createGate(100, 100, "arena_1", "arena_2");
        Entity gate2 = createGate(200, 200, "arena_2", "arena_3");
        engine.addEntity(gate1);
        engine.addEntity(gate2);
        
        // Count portal components - should be zero
        int portalCount = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(PortalComponent.class) != null) {
                portalCount++;
            }
        }
        
        assertEquals(0, portalCount, 
            "No portal components should exist in gate-based progression");
    }
    
    @Test
    void testGateClosedUsesCorrectColor() {
        Entity gate = createGate(100, 100, "arena_1", "arena_2");
        engine.addEntity(gate);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        
        // Closed gate should use GATE_CLOSED_COLOR (brown for tile ID 211)
        assertEquals(GateSystem.GATE_CLOSED_COLOR.r, renderable.color.r, 0.01f);
        assertEquals(GateSystem.GATE_CLOSED_COLOR.g, renderable.color.g, 0.01f);
        assertEquals(GateSystem.GATE_CLOSED_COLOR.b, renderable.color.b, 0.01f);
        
        // Should not be green (portal color)
        assertFalse(isPortalGreen(renderable.color), 
            "Closed gate should not be green (portal color)");
    }
    
    @Test
    void testGateOpenUsesFloorTileColor() {
        Entity gate = createGate(100, 100, "arena_1", "arena_2");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Open the gate
        gameStateManager.setState(GameState.LEVEL_CLEAR);
        engine.update(0.016f);
        
        RenderableComponent renderable = gate.getComponent(RenderableComponent.class);
        
        // Open gate should use floor tile color (GATE_OPEN_COLOR)
        assertEquals(GateSystem.GATE_OPEN_COLOR.r, renderable.color.r, 0.01f);
        assertEquals(GateSystem.GATE_OPEN_COLOR.g, renderable.color.g, 0.01f);
        assertEquals(GateSystem.GATE_OPEN_COLOR.b, renderable.color.b, 0.01f);
        
        // Open gate color should NOT be the semi-transparent green portal color
        assertFalse(isPortalGreen(renderable.color),
            "Open gate should use floor tile color, not portal green");
    }
    
    @Test
    void testClearingArenaOpensGate() {
        Entity gate = createGate(100, 100, "arena_1", "arena_2");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Add enemy
        Entity enemy = createEnemy(200, 200);
        engine.addEntity(enemy);
        
        // Gate should be closed
        assertTrue(gateComp.isClosed());
        
        // Remove enemy and clear level
        engine.removeEntity(enemy);
        gameStateManager.setState(GameState.LEVEL_CLEAR);
        engine.update(0.016f);
        
        // Gate should be open
        assertTrue(gateComp.isOpen(), "Gate should open when arena is cleared");
    }
    
    @Test
    void testGateCollidableWhenClosed() {
        Entity gate = createGate(100, 100, "arena_1", "arena_2");
        engine.addEntity(gate);
        
        CollisionComponent collision = gate.getComponent(CollisionComponent.class);
        
        assertTrue(collision.width > 0, "Closed gate should have collision width");
        assertTrue(collision.height > 0, "Closed gate should have collision height");
    }
    
    @Test
    void testGateNotCollidableWhenOpen() {
        Entity gate = createGate(100, 100, "arena_1", "arena_2");
        GateComponent gateComp = gate.getComponent(GateComponent.class);
        gateComp.openingDuration = 0f;
        engine.addEntity(gate);
        
        // Open gate
        gameStateManager.setState(GameState.LEVEL_CLEAR);
        engine.update(0.016f);
        
        CollisionComponent collision = gate.getComponent(CollisionComponent.class);
        
        assertEquals(0f, collision.width, "Open gate should have no collision width");
        assertEquals(0f, collision.height, "Open gate should have no collision height");
    }
    
    @Test
    void testGateLogsTileId() {
        // This test verifies the logging includes tile ID 212
        // The GATE_CLOSED_TILE_ID constant should be 212 (col=41, row=3 zero-based)
        assertEquals(212, GateSystem.GATE_CLOSED_TILE_ID,
            "Gate closed tile ID should be 212 (col=41, row=3 zero-based)");
    }
    
    @Test
    void testPuzzleDoorsDoNotSpawnPortalsWhenDisabled() {
        // Verify that when portals are disabled (default), puzzle completion
        // should use gates instead of creating portal entities
        
        // Create a puzzle door
        Entity door = createPuzzleDoor(100, 100);
        engine.addEntity(door);
        
        // Simulate puzzle being solved (would normally trigger portal spawn if enabled)
        PuzzleDoorComponent doorComp = door.getComponent(PuzzleDoorComponent.class);
        doorComp.unlock();
        
        // Count portal components - should still be zero
        int portalCount = 0;
        for (Entity entity : engine.getEntities()) {
            if (entity.getComponent(PortalComponent.class) != null) {
                portalCount++;
            }
        }
        
        assertEquals(0, portalCount, 
            "Solving puzzle should not create portal entities when portals are disabled");
    }
    
    // Helper methods
    
    private Entity createGate(float x, float y, String sourceArenaId, String targetArenaId) {
        Entity gate = engine.createEntity();
        gate.add(new PositionComponent(x, y));
        gate.add(new CollisionComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE));
        gate.add(new RenderableComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE,
            GateSystem.GATE_CLOSED_COLOR));
        gate.add(new GateComponent(sourceArenaId, targetArenaId));
        return gate;
    }
    
    private Entity createEnemy(float x, float y) {
        Entity enemy = engine.createEntity();
        enemy.add(new PositionComponent(x, y));
        enemy.add(new EnemyComponent());
        return enemy;
    }
    
    private Entity createPuzzleDoor(float x, float y) {
        Map<String, String> puzzleData = new HashMap<>();
        puzzleData.put("answer", "TEST");
        Puzzle puzzle = new Puzzle("test_puzzle", "cipher", puzzleData);
        
        Entity door = engine.createEntity();
        door.add(new PositionComponent(x, y));
        door.add(new CollisionComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE));
        door.add(new RenderableComponent(GameConstants.TILE_SIZE * 2, GameConstants.TILE_SIZE,
            Color.RED));
        door.add(new PuzzleDoorComponent("test_door", puzzle, true));
        return door;
    }
    
    private boolean isPortalGreen(Color color) {
        // Check if color is close to portal green (semi-transparent green)
        return color.g > 0.7f && color.r < 0.5f && color.b < 0.5f;
    }
}
