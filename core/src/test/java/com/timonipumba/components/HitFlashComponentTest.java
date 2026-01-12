package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.graphics.Color;

/**
 * Unit tests for HitFlashComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Custom constructor with flash color and duration
 * - Flash triggering with default and custom durations
 * - Timer updates and isFlashing checks
 */
class HitFlashComponentTest {
    
    @Test
    void testDefaultConstructor() {
        HitFlashComponent flash = new HitFlashComponent();
        
        assertEquals(0f, flash.flashTimer, 0.001f, "Default flash timer should be 0");
        assertNotNull(flash.flashColor, "Flash color should not be null");
        assertEquals(Color.WHITE, flash.flashColor, "Default flash color should be WHITE");
        assertEquals(0.15f, flash.defaultFlashDuration, 0.001f, "Default duration should be 0.15");
    }
    
    @Test
    void testCustomConstructor() {
        HitFlashComponent flash = new HitFlashComponent(Color.RED, 0.3f);
        
        assertEquals(0f, flash.flashTimer, 0.001f, "Timer should start at 0");
        assertEquals(Color.RED, flash.flashColor, "Flash color should be RED");
        assertEquals(0.3f, flash.defaultFlashDuration, 0.001f, "Custom duration should be 0.3");
    }
    
    @Test
    void testIsFlashingWhenNotActive() {
        HitFlashComponent flash = new HitFlashComponent();
        
        assertFalse(flash.isFlashing(), "Should not be flashing initially");
    }
    
    @Test
    void testTriggerFlashWithDefaultDuration() {
        HitFlashComponent flash = new HitFlashComponent();
        
        flash.triggerFlash();
        
        assertTrue(flash.isFlashing(), "Should be flashing after trigger");
        assertEquals(0.15f, flash.flashTimer, 0.001f, "Timer should equal default duration");
    }
    
    @Test
    void testTriggerFlashWithCustomDuration() {
        HitFlashComponent flash = new HitFlashComponent();
        
        flash.triggerFlash(0.5f);
        
        assertTrue(flash.isFlashing(), "Should be flashing after trigger");
        assertEquals(0.5f, flash.flashTimer, 0.001f, "Timer should equal custom duration");
    }
    
    @Test
    void testUpdateDecreasesTimer() {
        HitFlashComponent flash = new HitFlashComponent();
        flash.triggerFlash(0.3f);
        
        flash.update(0.1f);
        
        assertEquals(0.2f, flash.flashTimer, 0.001f, "Timer should decrease by deltaTime");
        assertTrue(flash.isFlashing(), "Should still be flashing");
    }
    
    @Test
    void testUpdateTimerReachesZero() {
        HitFlashComponent flash = new HitFlashComponent();
        flash.triggerFlash(0.1f);
        
        flash.update(0.2f); // Overshoots timer
        
        assertEquals(0f, flash.flashTimer, 0.001f, "Timer should clamp to 0");
        assertFalse(flash.isFlashing(), "Should not be flashing after timer expires");
    }
    
    @Test
    void testUpdateWhenNotFlashing() {
        HitFlashComponent flash = new HitFlashComponent();
        
        flash.update(0.1f);
        
        assertEquals(0f, flash.flashTimer, 0.001f, "Timer should remain 0");
        assertFalse(flash.isFlashing(), "Should still not be flashing");
    }
}
