package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.timonipumba.GameConstants;

/**
 * Unit tests for SwitchComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Custom constructors
 * - Toggle behavior
 * - Momentary switch timer behavior
 */
class SwitchComponentTest {
    
    @Test
    void testDefaultConstructor() {
        SwitchComponent sw = new SwitchComponent();
        
        assertEquals("", sw.group, "Default group should be empty string");
        assertFalse(sw.on, "Default on state should be false");
        assertFalse(sw.momentary, "Default momentary should be false");
        assertEquals(0f, sw.timer, "Default timer should be 0");
    }
    
    @Test
    void testGroupConstructor() {
        SwitchComponent sw = new SwitchComponent("A");
        
        assertEquals("A", sw.group);
        assertFalse(sw.on);
        assertFalse(sw.momentary);
    }
    
    @Test
    void testMomentaryConstructor() {
        SwitchComponent sw = new SwitchComponent("B", true);
        
        assertEquals("B", sw.group);
        assertTrue(sw.momentary);
        assertFalse(sw.on);
    }
    
    @Test
    void testToggleNonMomentary() {
        SwitchComponent sw = new SwitchComponent("A", false);
        
        assertFalse(sw.on);
        
        sw.toggle();
        assertTrue(sw.on, "Switch should be on after first toggle");
        assertEquals(0f, sw.timer, "Non-momentary switch should not set timer");
        
        sw.toggle();
        assertFalse(sw.on, "Switch should be off after second toggle");
    }
    
    @Test
    void testToggleMomentary() {
        SwitchComponent sw = new SwitchComponent("A", true);
        
        assertFalse(sw.on);
        assertEquals(0f, sw.timer);
        
        sw.toggle();
        assertTrue(sw.on, "Switch should be on after toggle");
        assertEquals(GameConstants.SWITCH_MOMENTARY_DURATION, sw.timer, 
            "Momentary switch should set timer when turned on");
        
        // Toggle off should not set timer
        sw.toggle();
        assertFalse(sw.on);
        assertEquals(0f, sw.timer, "Timer should not be set when toggling off");
    }
    
    @Test
    void testUpdateTimerMomentary() {
        SwitchComponent sw = new SwitchComponent("A", true);
        sw.toggle(); // Turn on
        
        assertTrue(sw.on);
        float initialTimer = sw.timer;
        
        // Update with small delta - should not reset
        boolean reset = sw.updateTimer(0.5f);
        assertFalse(reset, "Should not reset yet");
        assertTrue(sw.on, "Switch should still be on");
        assertEquals(initialTimer - 0.5f, sw.timer, 0.001f);
        
        // Update with remaining time - should reset
        reset = sw.updateTimer(sw.timer + 0.1f);
        assertTrue(reset, "Should have reset");
        assertFalse(sw.on, "Switch should be off after auto-reset");
        assertEquals(0f, sw.timer, "Timer should be 0 after reset");
    }
    
    @Test
    void testUpdateTimerNonMomentary() {
        SwitchComponent sw = new SwitchComponent("A", false);
        sw.toggle(); // Turn on
        
        // Non-momentary switch should not auto-reset
        boolean reset = sw.updateTimer(100f);
        assertFalse(reset, "Non-momentary switch should never auto-reset");
        assertTrue(sw.on, "Switch should still be on");
    }
    
    @Test
    void testUpdateTimerWhenOff() {
        SwitchComponent sw = new SwitchComponent("A", true);
        
        // Switch is off - timer update should do nothing
        boolean reset = sw.updateTimer(1f);
        assertFalse(reset);
        assertFalse(sw.on);
    }
}
