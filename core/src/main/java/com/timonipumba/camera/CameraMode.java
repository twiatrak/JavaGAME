package com.timonipumba.camera;

/**
 * Represents the different camera modes available in the game.
 * 
 * Modes:
 * - EXPLORATION: Wider view for navigation and puzzles (default zoom level)
 * - FIGHT: Tighter, more zoomed-in view for combat encounters
 */
public enum CameraMode {
    /** Wider view for exploration and navigation - shows more of the level */
    EXPLORATION,
    
    /** Tighter view for combat - more zoomed in for action clarity */
    FIGHT
}
