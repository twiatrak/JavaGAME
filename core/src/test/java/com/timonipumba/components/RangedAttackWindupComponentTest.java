package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RangedAttackWindupComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Windup start and state
 * - Timer updates and completion detection
 * - Windup cancellation
 */
class RangedAttackWindupComponentTest {
    
    @Test
    void testDefaultConstructor() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        
        assertFalse(windup.isWindingUp, "Should not be winding up initially");
        assertEquals(0f, windup.windupTimer, 0.001f, "Timer should be 0");
        assertEquals(0f, windup.targetDirX, 0.001f, "Target X should be 0");
        assertEquals(0f, windup.targetDirY, 0.001f, "Target Y should be 0");
    }
    
    @Test
    void testIsWindingUpMethod() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        
        assertFalse(windup.isWindingUp(), "isWindingUp() should match field");
        
        windup.isWindingUp = true;
        assertTrue(windup.isWindingUp(), "isWindingUp() should match field");
    }
    
    @Test
    void testStartWindup() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        
        windup.startWindup(0.3f, 0.6f, 0.8f);
        
        assertTrue(windup.isWindingUp(), "Should be winding up");
        assertEquals(0.3f, windup.windupTimer, 0.001f, "Timer should be set");
        assertEquals(0.6f, windup.targetDirX, 0.001f, "Target X should be set");
        assertEquals(0.8f, windup.targetDirY, 0.001f, "Target Y should be set");
    }
    
    @Test
    void testUpdateDecreasesTimer() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        windup.startWindup(0.5f, 1f, 0f);
        
        boolean completed = windup.update(0.2f);
        
        assertFalse(completed, "Should not be completed yet");
        assertEquals(0.3f, windup.windupTimer, 0.001f, "Timer should decrease");
        assertTrue(windup.isWindingUp(), "Should still be winding up");
    }
    
    @Test
    void testUpdateReturnsTrueWhenComplete() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        windup.startWindup(0.2f, 1f, 0f);
        
        boolean completed = windup.update(0.25f);
        
        assertTrue(completed, "Should return true when windup completes");
        assertFalse(windup.isWindingUp(), "Should no longer be winding up");
        assertEquals(0f, windup.windupTimer, 0.001f, "Timer should be 0");
    }
    
    @Test
    void testUpdateWhenNotWindingUp() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        
        boolean completed = windup.update(0.1f);
        
        assertFalse(completed, "Should return false when not winding up");
    }
    
    @Test
    void testCancelWindup() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        windup.startWindup(0.3f, 0.7f, -0.7f);
        
        windup.cancelWindup();
        
        assertFalse(windup.isWindingUp(), "Should not be winding up after cancel");
        assertEquals(0f, windup.windupTimer, 0.001f, "Timer should be 0");
        assertEquals(0f, windup.targetDirX, 0.001f, "Target X should be reset");
        assertEquals(0f, windup.targetDirY, 0.001f, "Target Y should be reset");
    }
    
    @Test
    void testMultipleUpdatesUntilComplete() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        windup.startWindup(0.3f, 1f, 0f);
        
        // First update - not complete
        assertFalse(windup.update(0.1f));
        assertTrue(windup.isWindingUp());
        assertEquals(0.2f, windup.windupTimer, 0.001f);
        
        // Second update - not complete
        assertFalse(windup.update(0.1f));
        assertTrue(windup.isWindingUp());
        assertEquals(0.1f, windup.windupTimer, 0.001f);
        
        // Third update - complete
        assertTrue(windup.update(0.15f));
        assertFalse(windup.isWindingUp());
    }
    
    @Test
    void testStoredDirectionPreservedDuringWindup() {
        RangedAttackWindupComponent windup = new RangedAttackWindupComponent();
        windup.startWindup(0.3f, 0.5f, 0.866f); // Normalized direction
        
        windup.update(0.1f);
        
        // Direction should still be preserved for when projectile fires
        assertEquals(0.5f, windup.targetDirX, 0.001f, "Direction X should be preserved");
        assertEquals(0.866f, windup.targetDirY, 0.001f, "Direction Y should be preserved");
    }
}
