package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameStateSystem.
 * 
 * Validates:
 * - Game stays in PLAYING state when player exists with positive health
 * - Game transitions to GAME_OVER when no player entity exists
 * - Game transitions to LEVEL_CLEAR when no enemies exist
 * - Restart flow correctly resets game state with new player entity
 */
class GameStateSystemTest {
    
    private Engine engine;
    private GameStateManager gameStateManager;
    private GameStateSystem gameStateSystem;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        gameStateSystem = new GameStateSystem(gameStateManager);
        engine.addSystem(gameStateSystem);
    }
    
    @Test
    void testGameStaysPlayingWithPlayer() {
        // Add player entity
        Entity player = createPlayer();
        engine.addEntity(player);
        
        // Add an enemy to prevent level clear
        Entity enemy = createEnemy();
        engine.addEntity(enemy);
        
        // Update the system
        engine.update(0.016f);
        
        assertTrue(gameStateManager.isPlaying(), 
            "Game should remain in PLAYING state with player and enemies");
    }
    
    @Test
    void testGameOverWhenNoPlayer() {
        // Add only an enemy, no player
        Entity enemy = createEnemy();
        engine.addEntity(enemy);
        
        // Update the system
        engine.update(0.016f);
        
        assertTrue(gameStateManager.isGameOver(), 
            "Game should transition to GAME_OVER when no player exists");
    }
    
    @Test
    void testLevelClearWhenNoEnemies() {
        // Add only a player, no enemies
        Entity player = createPlayer();
        engine.addEntity(player);
        
        // Update the system
        engine.update(0.016f);
        
        assertTrue(gameStateManager.isLevelClear(), 
            "Game should transition to LEVEL_CLEAR when no enemies exist");
    }
    
    @Test
    void testRestartFlowWithNewPlayer() {
        // Simulate initial game with player and enemy
        Entity player = createPlayer();
        Entity enemy = createEnemy();
        engine.addEntity(player);
        engine.addEntity(enemy);
        
        // Verify initial state
        engine.update(0.016f);
        assertTrue(gameStateManager.isPlaying());
        
        // Simulate player death (remove player entity)
        engine.removeEntity(player);
        engine.update(0.016f);
        assertTrue(gameStateManager.isGameOver(), "Game should be over after player removal");
        
        // Simulate restart: reset state, create new engine with new entities
        gameStateManager.reset();
        assertTrue(gameStateManager.isPlaying(), "State should be PLAYING after reset");
        
        // Create a new engine (simulating restart flow)
        Engine newEngine = new Engine();
        GameStateSystem newGameStateSystem = new GameStateSystem(gameStateManager);
        newEngine.addSystem(newGameStateSystem);
        
        // Add new player to new engine
        Entity newPlayer = createPlayer();
        Entity newEnemy = createEnemy();
        newEngine.addEntity(newPlayer);
        newEngine.addEntity(newEnemy);
        
        // Update new engine
        newEngine.update(0.016f);
        
        // Verify state remains PLAYING (not instantly GAME_OVER)
        assertTrue(gameStateManager.isPlaying(), 
            "Game should remain PLAYING after restart with new player entity");
        assertFalse(gameStateManager.isGameOver(), 
            "Game should NOT be GAME_OVER after restart");
    }
    
    @Test
    void testNoStateChangeWhenAlreadyGameOver() {
        // Start in GAME_OVER state
        gameStateManager.setState(GameState.GAME_OVER);
        
        // Update without any entities
        engine.update(0.016f);
        
        // Should remain in GAME_OVER (not process state checks)
        assertTrue(gameStateManager.isGameOver(), 
            "Game should remain in GAME_OVER state when already game over");
    }
    
    @Test
    void testNoStateChangeWhenLevelClear() {
        // Start in LEVEL_CLEAR state
        gameStateManager.setState(GameState.LEVEL_CLEAR);
        
        // Add player but no enemies
        Entity player = createPlayer();
        engine.addEntity(player);
        
        // Update
        engine.update(0.016f);
        
        // Should remain in LEVEL_CLEAR (not process state checks)
        assertTrue(gameStateManager.isLevelClear(), 
            "Game should remain in LEVEL_CLEAR state");
    }
    
    @Test
    void testActiveGameplayTrueDuringPlaying() {
        // Add player and enemy
        Entity player = createPlayer();
        Entity enemy = createEnemy();
        engine.addEntity(player);
        engine.addEntity(enemy);
        
        // Update the system
        engine.update(0.016f);
        
        // Should be in PLAYING state (active gameplay)
        assertTrue(gameStateManager.isPlaying());
        assertTrue(gameStateManager.isActiveGameplay(), 
            "isActiveGameplay() should return true during PLAYING state");
    }
    
    @Test
    void testActiveGameplayTrueDuringLevelClear() {
        // Add only player (no enemies) to trigger LEVEL_CLEAR
        Entity player = createPlayer();
        engine.addEntity(player);
        
        // Update the system
        engine.update(0.016f);
        
        // Should be in LEVEL_CLEAR state (also active gameplay - player can move and interact)
        assertTrue(gameStateManager.isLevelClear());
        assertTrue(gameStateManager.isActiveGameplay(), 
            "isActiveGameplay() should return true during LEVEL_CLEAR state");
    }
    
    @Test
    void testActiveGameplayFalseDuringGameOver() {
        // No player, so GAME_OVER will be triggered
        Entity enemy = createEnemy();
        engine.addEntity(enemy);
        
        // Update the system
        engine.update(0.016f);
        
        // Should be in GAME_OVER state (not active gameplay)
        assertTrue(gameStateManager.isGameOver());
        assertFalse(gameStateManager.isActiveGameplay(), 
            "isActiveGameplay() should return false during GAME_OVER state");
    }
    
    private Entity createPlayer() {
        Entity player = engine.createEntity();
        player.add(new PlayerComponent());
        player.add(new PositionComponent(100f, 100f));
        player.add(new HealthComponent(100));
        return player;
    }
    
    private Entity createEnemy() {
        Entity enemy = engine.createEntity();
        enemy.add(new EnemyComponent());
        enemy.add(new PositionComponent(200f, 200f));
        enemy.add(new HealthComponent(30));
        return enemy;
    }
}
