package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.EnemyComponent;
import com.timonipumba.components.PlayerComponent;

/**
 * System that monitors game conditions and updates the game state.
 * 
 * Behavior:
 * - If no player entity exists (or player health <= 0): transition to GAME_OVER
 * - If no enemy entities remain: transition to LEVEL_CLEAR
 * - Only checks conditions when in PLAYING state
 */
public class GameStateSystem extends EntitySystem {
    
    private final GameStateManager gameStateManager;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> enemies;
    
    public GameStateSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class).get());
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class).get());
    }
    
    @Override
    public void update(float deltaTime) {
        // Only check conditions when playing
        if (!gameStateManager.isPlaying()) {
            return;
        }
        
        // Check for player death (no player entity means player was removed after death)
        if (players.size() == 0) {
            gameStateManager.setState(GameState.GAME_OVER);
            return;
        }
        
        // Check for level clear (no enemies remain)
        if (enemies.size() == 0) {
            gameStateManager.setState(GameState.LEVEL_CLEAR);
        }
    }
}
