package com.timonipumba.camera;

/**
 * Manages the current camera mode and provides getters/setters.
 * 
 * This manager tracks whether the camera should be in EXPLORATION mode
 * (wider view for navigation) or FIGHT mode (tighter view for combat).
 * 
 * Usage:
 * - Call getMode() to check the current camera mode
 * - Call setMode() to change the camera mode
 * - The CameraSystem reads this to smoothly interpolate zoom levels
 */
public class CameraModeManager {
    
    private CameraMode currentMode = CameraMode.EXPLORATION;
    
    public CameraModeManager() {
        // Default initialization handled at field declaration
    }
    
    /**
     * Get the current camera mode.
     * @return The current camera mode
     */
    public CameraMode getMode() {
        return currentMode;
    }
    
    /**
     * Set a new camera mode.
     * @param newMode The camera mode to switch to (must not be null)
     */
    public void setMode(CameraMode newMode) {
        if (newMode == null) {
            return;
        }
        if (newMode != currentMode) {
            currentMode = newMode;
        }
    }
    
    /**
     * Toggle between EXPLORATION and FIGHT modes.
     */
    public void toggle() {
        if (currentMode == CameraMode.EXPLORATION) {
            setMode(CameraMode.FIGHT);
        } else {
            setMode(CameraMode.EXPLORATION);
        }
    }
    
    /**
     * Check if the camera is in EXPLORATION mode.
     * @return true if in exploration mode
     */
    public boolean isExploration() {
        return currentMode == CameraMode.EXPLORATION;
    }
    
    /**
     * Check if the camera is in FIGHT mode.
     * @return true if in fight mode
     */
    public boolean isFight() {
        return currentMode == CameraMode.FIGHT;
    }
    
    /**
     * Reset camera mode to the default (EXPLORATION).
     */
    public void reset() {
        currentMode = CameraMode.EXPLORATION;
    }
}
