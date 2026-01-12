package com.timonipumba;

/**
 * Manages the current game state and provides callbacks for state changes.
 * This is a lightweight manager shared by the runtime game modes.
 * 
 * Usage:
 * - Call getState() to check current state before processing systems
 * - Call setState() to transition between states
 * - Systems should gate their processing on isPlaying()
 */
public class GameStateManager {
    
    private GameState currentState = GameState.PLAYING;
    
    public GameStateManager() {
        this.currentState = GameState.PLAYING;
    }
    
    /**
     * Get the current game state.
     */
    public GameState getState() {
        return currentState;
    }
    
    /**
     * Set a new game state.
     */
    public void setState(GameState newState) {
        if (newState != currentState) {
            currentState = newState;
        }
    }
    
    /**
     * Check if the game is currently in PLAYING state.
     * Convenience method for systems to quickly check if they should process.
     */
    public boolean isPlaying() {
        return currentState == GameState.PLAYING;
    }
    
    /**
     * Check if the game is over (player died).
     */
    public boolean isGameOver() {
        return currentState == GameState.GAME_OVER;
    }
    
    /**
     * Check if the level has been cleared.
     */
    public boolean isLevelClear() {
        return currentState == GameState.LEVEL_CLEAR;
    }
    
    /**
     * Check if the game is in an active gameplay state where player movement
     * and interaction should be enabled.
     * 
     * Active gameplay states include:
     * - PLAYING: Normal gameplay with enemies
     * - LEVEL_CLEAR: All enemies defeated, but player can still move and interact
     * 
    * This method should be used by systems that need to allow player control
    * even after enemies are cleared (e.g., PlayerInputSystem, DoorSystem).
     * 
     * @return true if player movement and interaction should be enabled
     */
    public boolean isActiveGameplay() {
        return currentState == GameState.PLAYING || currentState == GameState.LEVEL_CLEAR;
    }
    
    /**
     * Check if in puzzle mode.
     */
    public boolean isPuzzle() {
        return currentState == GameState.PUZZLE;
    }

    /**
     * Check if in traversal riddle mode (generated TMX roguelike overlay).
     */
    public boolean isTraversalRiddle() {
        return currentState == GameState.TRAVERSAL_RIDDLE;
    }

    /**
     * Check if in register-allocation riddle mode (generated TMX roguelike overlay).
     */
    public boolean isRegisterAllocationRiddle() {
        return currentState == GameState.REGISTER_ALLOCATION_RIDDLE;
    }

    /**
     * Check if in Lights-Out riddle mode (generated TMX roguelike overlay).
     */
    public boolean isLightsOutRiddle() {
        return currentState == GameState.LIGHTS_OUT_RIDDLE;
    }

    /**
     * Check if level is complete.
     */
    public boolean isLevelComplete() {
        return currentState == GameState.LEVEL_COMPLETE;
    }
    
    /**
     * Check if in exploration mode (story mode hub/level navigation).
     */
    public boolean isExploration() {
        return currentState == GameState.EXPLORATION;
    }
    
    /**
     * Reset state to PLAYING (called on restart/next level).
     */
    public void reset() {
        currentState = GameState.PLAYING;
    }
    
    /**
     * Reset state to EXPLORATION (for story mode exploration).
     */
    public void resetToExploration() {
        currentState = GameState.EXPLORATION;
    }
}
