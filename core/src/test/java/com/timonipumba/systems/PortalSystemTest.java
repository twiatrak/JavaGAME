package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.LevelManager;
import com.timonipumba.level.PortalConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalSystem.
 */
/**
 * Unit tests for PortalSystem.
 * 
 * Note: These tests explicitly enable wall segment portals since that feature
 * is now disabled by default in favor of the gate-based system.
 */
class PortalSystemTest {
    
    private Engine engine;
    private GameStateManager gameStateManager;
    private PortalSystem portalSystem;
    private TestLevelManager levelManager;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        gameStateManager.setState(GameState.PLAYING);
        
        portalSystem = new PortalSystem(gameStateManager);
        levelManager = new TestLevelManager();
        portalSystem.setLevelManager(levelManager);
        
        engine.addSystem(portalSystem);
        
        PortalConfig.resetToDefaults();
        // Enable wall segment portals for testing portal functionality
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
    }
    
    @AfterEach
    void tearDown() {
        PortalConfig.resetToDefaults();
    }
    
    @Test
    void testInactivePortalDoesNotTrigger() {
        // Create player
        Entity player = createPlayer(100, 100);
        engine.addEntity(player);
        
        // Create inactive portal at same position
        Entity portal = createPortal(100, 100, "test_puzzle", false);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertFalse(levelManager.loadNextLevelCalled, "Inactive portal should not trigger level load");
    }
    
    @Test
    void testActivePortalTriggers() {
        // Create player
        Entity player = createPlayer(100, 100);
        engine.addEntity(player);
        
        // Create active portal at same position
        Entity portal = createPortal(100, 100, "test_puzzle", true);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertTrue(levelManager.loadNextLevelCalled, "Active portal should trigger level load");
    }
    
    @Test
    void testPlayerNotOverlappingDoesNotTrigger() {
        // Create player far from portal
        Entity player = createPlayer(0, 0);
        engine.addEntity(player);
        
        // Create active portal at different position
        Entity portal = createPortal(200, 200, "test_puzzle", true);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertFalse(levelManager.loadNextLevelCalled, "Portal should not trigger when player not overlapping");
    }
    
    @Test
    void testActivatePortalsForPuzzle() {
        // Create two inactive portals for same puzzle
        Entity portal1 = createPortal(100, 100, "test_puzzle", false);
        Entity portal2 = createPortal(116, 100, "test_puzzle", false);
        engine.addEntity(portal1);
        engine.addEntity(portal2);
        
        // Create one portal for different puzzle
        Entity otherPortal = createPortal(200, 200, "other_puzzle", false);
        engine.addEntity(otherPortal);
        
        assertEquals(0, portalSystem.getActivePortalCount());
        assertEquals(3, portalSystem.getInactivePortalCount());
        
        // Activate portals for test_puzzle
        portalSystem.activatePortalsForPuzzle("test_puzzle");
        
        assertEquals(2, portalSystem.getActivePortalCount());
        assertEquals(1, portalSystem.getInactivePortalCount());
        
        // Verify correct portals activated
        assertTrue(portal1.getComponent(PortalComponent.class).isActive());
        assertTrue(portal2.getComponent(PortalComponent.class).isActive());
        assertFalse(otherPortal.getComponent(PortalComponent.class).isActive());
    }
    
    @Test
    void testActivatePortalsUpdatesColor() {
        Entity portal = createPortal(100, 100, "test_puzzle", false);
        portal.add(new RenderableComponent(16, 16, Color.GRAY));
        engine.addEntity(portal);
        
        portalSystem.activatePortalsForPuzzle("test_puzzle");
        
        RenderableComponent renderable = portal.getComponent(RenderableComponent.class);
        // Should be portal green color
        assertEquals(PortalSystem.PORTAL_COLOR.r, renderable.color.r, 0.01f);
        assertEquals(PortalSystem.PORTAL_COLOR.g, renderable.color.g, 0.01f);
        assertEquals(PortalSystem.PORTAL_COLOR.b, renderable.color.b, 0.01f);
    }
    
    @Test
    void testFeatureDisabled() {
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = false;
        
        // Create player and active portal overlapping
        Entity player = createPlayer(100, 100);
        Entity portal = createPortal(100, 100, "test_puzzle", true);
        engine.addEntity(player);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertFalse(levelManager.loadNextLevelCalled, 
            "Portal should not trigger when feature is disabled");
    }
    
    @Test
    void testOnlyTriggersOncePerUpdate() {
        // Create player
        Entity player = createPlayer(100, 100);
        engine.addEntity(player);
        
        // Create two active portals at same position
        Entity portal1 = createPortal(100, 100, "puzzle1", true);
        Entity portal2 = createPortal(100, 100, "puzzle2", true);
        engine.addEntity(portal1);
        engine.addEntity(portal2);
        
        engine.update(0.016f);
        
        assertEquals(1, levelManager.loadNextLevelCallCount, 
            "Should only trigger level load once even with multiple overlapping portals");
    }
    
    @Test
    void testGameStateNotPlayingSkipsProcessing() {
        gameStateManager.setState(GameState.GAME_OVER);
        
        Entity player = createPlayer(100, 100);
        Entity portal = createPortal(100, 100, "test_puzzle", true);
        engine.addEntity(player);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertFalse(levelManager.loadNextLevelCalled,
            "Portal should not trigger when game state is not active gameplay");
    }
    
    @Test
    void testResetLevelLoadPending() {
        Entity player = createPlayer(100, 100);
        Entity portal = createPortal(100, 100, "test_puzzle", true);
        engine.addEntity(player);
        engine.addEntity(portal);
        
        // First trigger
        engine.update(0.016f);
        assertTrue(levelManager.loadNextLevelCalled);
        
        // Reset and clear
        levelManager.reset();
        portalSystem.resetLevelLoadPending();
        
        // Should trigger again
        engine.update(0.016f);
        assertTrue(levelManager.loadNextLevelCalled);
    }
    
    @Test
    void testPortalsDisabledByDefault() {
        // Reset to defaults and verify portals are disabled
        PortalConfig.resetToDefaults();
        
        assertFalse(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED,
            "Wall segment portals should be disabled by default to use gate-based progression");
    }
    
    @Test
    void testNoPortalTriggersWhenDisabledByDefault() {
        // Use default config (portals disabled)
        PortalConfig.resetToDefaults();
        
        // Create fresh system with portals disabled
        portalSystem = new PortalSystem(gameStateManager);
        portalSystem.setLevelManager(levelManager);
        engine = new Engine();
        engine.addSystem(portalSystem);
        
        // Create player and active portal overlapping
        Entity player = createPlayer(100, 100);
        Entity portal = createPortal(100, 100, "test_puzzle", true);
        engine.addEntity(player);
        engine.addEntity(portal);
        
        engine.update(0.016f);
        
        assertFalse(levelManager.loadNextLevelCalled, 
            "Portal should not trigger level load when portals are disabled by default");
    }
    
    // Helper methods
    
    private Entity createPlayer(float x, float y) {
        Entity player = engine.createEntity();
        player.add(new PositionComponent(x, y));
        player.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        player.add(new PlayerComponent());
        return player;
    }
    
    private Entity createPortal(float x, float y, String puzzleId, boolean active) {
        Entity portal = engine.createEntity();
        portal.add(new PositionComponent(x, y));
        portal.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        PortalComponent portalComp = new PortalComponent(puzzleId);
        if (active) {
            portalComp.activate();
        }
        portal.add(portalComp);
        return portal;
    }
    
    // Test double for LevelManager
    private static class TestLevelManager implements LevelManager {
        boolean loadNextLevelCalled = false;
        boolean restartLevelCalled = false;
        int loadNextLevelCallCount = 0;
        
        @Override
        public void loadNextLevel() {
            loadNextLevelCalled = true;
            loadNextLevelCallCount++;
        }
        
        @Override
        public void restartLevel() {
            restartLevelCalled = true;
        }
        
        void reset() {
            loadNextLevelCalled = false;
            restartLevelCalled = false;
            loadNextLevelCallCount = 0;
        }
    }
}
