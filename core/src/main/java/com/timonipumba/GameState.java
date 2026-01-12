package com.timonipumba;

/**
 * Represents the current state of the game.
 * 
 * States:
 * - PLAYING: Normal gameplay, all systems active
 * - GAME_OVER: Player died, systems paused, showing game over message
 * - LEVEL_CLEAR: All enemies defeated, showing level clear message
 * - EXPLORATION: Player is exploring the level/hub before puzzle interaction
 * - PUZZLE: Player is solving a puzzle
 * - LEVEL_COMPLETE: Level completed, showing success message
 */
public enum GameState {
    /** Normal gameplay - all systems active */
    PLAYING,
    
    /** Player died - systems paused, showing "You died - Press R to restart" */
    GAME_OVER,
    
    /** All enemies defeated - showing "Level clear - Press N for next level" */
    LEVEL_CLEAR,
    
    /** Player is exploring the level/hub before interacting with puzzle terminals */
    EXPLORATION,
    
    /** Player is solving a puzzle in story mode */
    PUZZLE,

    /** Player is solving a traversal riddle (generated TMX roguelike mode) */
    TRAVERSAL_RIDDLE,

    /** Player is viewing the register-allocation riddle rules (generated TMX roguelike overlay). */
    REGISTER_ALLOCATION_RIDDLE,

    /** Player is viewing the Lights-Out riddle rules (generated TMX roguelike overlay). */
    LIGHTS_OUT_RIDDLE,

    /** Player is interacting with the Algebra Forge / Oracle terminals (token algebra overlay). */
    ALGEBRA_FORGE_RIDDLE,
    
    /** Level completed - showing success message before next level */
    LEVEL_COMPLETE
}
