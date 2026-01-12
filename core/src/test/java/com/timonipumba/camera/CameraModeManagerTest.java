package com.timonipumba.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CameraModeManager.
 * 
 * Validates:
 * - Initial state (EXPLORATION mode)
 * - Mode transitions
 * - Toggle functionality
 * - Reset behavior
 */
class CameraModeManagerTest {
    
    @Test
    void testInitialMode() {
        CameraModeManager manager = new CameraModeManager();
        
        assertEquals(CameraMode.EXPLORATION, manager.getMode(), 
            "Initial mode should be EXPLORATION");
        assertTrue(manager.isExploration());
        assertFalse(manager.isFight());
    }
    
    @Test
    void testSetModeToFight() {
        CameraModeManager manager = new CameraModeManager();
        
        manager.setMode(CameraMode.FIGHT);
        
        assertEquals(CameraMode.FIGHT, manager.getMode());
        assertFalse(manager.isExploration());
        assertTrue(manager.isFight());
    }
    
    @Test
    void testSetModeToExploration() {
        CameraModeManager manager = new CameraModeManager();
        
        // First switch to fight, then back to exploration
        manager.setMode(CameraMode.FIGHT);
        manager.setMode(CameraMode.EXPLORATION);
        
        assertEquals(CameraMode.EXPLORATION, manager.getMode());
        assertTrue(manager.isExploration());
        assertFalse(manager.isFight());
    }
    
    @Test
    void testToggleFromExploration() {
        CameraModeManager manager = new CameraModeManager();
        
        manager.toggle();
        
        assertEquals(CameraMode.FIGHT, manager.getMode());
        assertTrue(manager.isFight());
    }
    
    @Test
    void testToggleFromFight() {
        CameraModeManager manager = new CameraModeManager();
        
        manager.setMode(CameraMode.FIGHT);
        manager.toggle();
        
        assertEquals(CameraMode.EXPLORATION, manager.getMode());
        assertTrue(manager.isExploration());
    }
    
    @Test
    void testMultipleToggles() {
        CameraModeManager manager = new CameraModeManager();
        
        // Start in EXPLORATION
        assertTrue(manager.isExploration());
        
        // Toggle to FIGHT
        manager.toggle();
        assertTrue(manager.isFight());
        
        // Toggle back to EXPLORATION
        manager.toggle();
        assertTrue(manager.isExploration());
        
        // Toggle to FIGHT again
        manager.toggle();
        assertTrue(manager.isFight());
    }
    
    @Test
    void testReset() {
        CameraModeManager manager = new CameraModeManager();
        
        // Set to fight, then reset
        manager.setMode(CameraMode.FIGHT);
        manager.reset();
        
        assertEquals(CameraMode.EXPLORATION, manager.getMode());
        assertTrue(manager.isExploration());
    }
    
    @Test
    void testSetSameModeNoChange() {
        CameraModeManager manager = new CameraModeManager();
        
        // Setting same mode should not cause issues
        manager.setMode(CameraMode.EXPLORATION);
        assertEquals(CameraMode.EXPLORATION, manager.getMode());
        
        manager.setMode(CameraMode.EXPLORATION);
        assertEquals(CameraMode.EXPLORATION, manager.getMode());
    }
    
    @Test
    void testSetModeWithNullIgnored() {
        CameraModeManager manager = new CameraModeManager();
        
        // Setting to fight first
        manager.setMode(CameraMode.FIGHT);
        assertTrue(manager.isFight());
        
        // Setting null should be ignored, mode should remain FIGHT
        manager.setMode(null);
        assertEquals(CameraMode.FIGHT, manager.getMode());
        assertTrue(manager.isFight());
    }
}
