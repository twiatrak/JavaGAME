package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CameraShakeComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Shake triggering
 * - Timer updates and isShaking checks
 * - Random offset generation
 * - Shake intensity merging when already shaking
 */
class CameraShakeComponentTest {
    
    @Test
    void testDefaultConstructor() {
        CameraShakeComponent shake = new CameraShakeComponent();
        
        assertEquals(0f, shake.shakeTimer, 0.001f, "Default shake timer should be 0");
        assertEquals(0f, shake.shakeIntensity, 0.001f, "Default intensity should be 0");
    }
    
    @Test
    void testIsShakingWhenNotActive() {
        CameraShakeComponent shake = new CameraShakeComponent();
        
        assertFalse(shake.isShaking(), "Should not be shaking initially");
    }
    
    @Test
    void testTriggerShake() {
        CameraShakeComponent shake = new CameraShakeComponent();
        
        shake.triggerShake(0.5f, 4f);
        
        assertTrue(shake.isShaking(), "Should be shaking after trigger");
        assertEquals(0.5f, shake.shakeTimer, 0.001f, "Timer should equal duration");
        assertEquals(4f, shake.shakeIntensity, 0.001f, "Intensity should be set");
    }
    
    @Test
    void testUpdateDecreasesTimer() {
        CameraShakeComponent shake = new CameraShakeComponent();
        shake.triggerShake(0.5f, 4f);
        
        shake.update(0.2f);
        
        assertEquals(0.3f, shake.shakeTimer, 0.001f, "Timer should decrease by deltaTime");
        assertTrue(shake.isShaking(), "Should still be shaking");
    }
    
    @Test
    void testUpdateTimerReachesZero() {
        CameraShakeComponent shake = new CameraShakeComponent();
        shake.triggerShake(0.2f, 4f);
        
        shake.update(0.3f); // Overshoots timer
        
        assertEquals(0f, shake.shakeTimer, 0.001f, "Timer should be 0");
        assertEquals(0f, shake.shakeIntensity, 0.001f, "Intensity should be cleared");
        assertFalse(shake.isShaking(), "Should not be shaking after timer expires");
    }
    
    @Test
    void testGetRandomOffsetWhenNotShaking() {
        CameraShakeComponent shake = new CameraShakeComponent();
        
        float offset = shake.getRandomOffset();
        
        assertEquals(0f, offset, 0.001f, "Offset should be 0 when not shaking");
    }
    
    @Test
    void testGetRandomOffsetWhenShaking() {
        CameraShakeComponent shake = new CameraShakeComponent();
        shake.triggerShake(0.5f, 4f);
        
        // Get multiple random offsets and verify they're within bounds
        for (int i = 0; i < 10; i++) {
            float offset = shake.getRandomOffset();
            assertTrue(offset >= -4f && offset <= 4f, 
                "Offset " + offset + " should be within +/- intensity (4)");
        }
    }
    
    @Test
    void testTriggerShakeWhileAlreadyShaking_UsesLargerIntensity() {
        CameraShakeComponent shake = new CameraShakeComponent();
        shake.triggerShake(0.5f, 2f);
        
        // Trigger with larger intensity
        shake.triggerShake(0.3f, 5f);
        
        assertEquals(5f, shake.shakeIntensity, 0.001f, "Should use larger intensity");
        assertEquals(0.5f, shake.shakeTimer, 0.001f, "Should use longer timer");
    }
    
    @Test
    void testTriggerShakeWhileAlreadyShaking_KeepsLargerIntensity() {
        CameraShakeComponent shake = new CameraShakeComponent();
        shake.triggerShake(0.5f, 5f);
        
        // Trigger with smaller intensity
        shake.triggerShake(0.3f, 2f);
        
        assertEquals(5f, shake.shakeIntensity, 0.001f, "Should keep larger intensity");
        assertEquals(0.5f, shake.shakeTimer, 0.001f, "Should keep longer timer");
    }
}
