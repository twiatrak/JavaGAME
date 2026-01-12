package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.timonipumba.components.AnimationComponent.State;
import com.timonipumba.components.AnimationComponent.AnimationData;

/**
 * Unit tests for AnimationComponent.
 * 
 * Validates:
 * - Default constructor values
 * - State transitions and tracking
 * - Animation frame calculation
 * - Looping vs non-looping animations
 * - Code-driven effects (bobbing, squash)
 * - Animation data registration
 */
class AnimationComponentTest {
    
    @Test
    void testDefaultConstructor() {
        AnimationComponent anim = new AnimationComponent();
        
        assertEquals(State.IDLE, anim.currentState, "Default state should be IDLE");
        assertEquals(State.IDLE, anim.previousState, "Default previous state should be IDLE");
        assertEquals(0f, anim.stateTime, 0.001f, "Default state time should be 0");
        assertFalse(anim.animationFinished, "Animation should not be finished initially");
        
        // Bobbing defaults
        assertEquals(0f, anim.bobOffsetY, 0.001f, "Default bob offset should be 0");
        assertEquals(2f, anim.bobAmplitude, 0.001f, "Default bob amplitude should be 2");
        assertEquals(3f, anim.bobFrequency, 0.001f, "Default bob frequency should be 3");
        
        // Squash defaults
        assertEquals(1.0f, anim.squashScaleX, 0.001f, "Default squash scale X should be 1");
        assertEquals(1.0f, anim.squashScaleY, 0.001f, "Default squash scale Y should be 1");
        assertEquals(0f, anim.squashTimer, 0.001f, "Default squash timer should be 0");
    }
    
    @Test
    void testStateTransition() {
        AnimationComponent anim = new AnimationComponent();
        
        anim.setState(State.WALK);
        
        assertEquals(State.WALK, anim.currentState, "Current state should be WALK");
        assertEquals(State.IDLE, anim.previousState, "Previous state should be IDLE");
        assertEquals(0f, anim.stateTime, 0.001f, "State time should reset on transition");
    }
    
    @Test
    void testStateTransitionResetsTime() {
        AnimationComponent anim = new AnimationComponent();
        anim.update(1.0f); // Advance state time
        
        assertTrue(anim.stateTime > 0, "State time should have advanced");
        
        anim.setState(State.ATTACK);
        
        assertEquals(0f, anim.stateTime, 0.001f, "State time should reset on state change");
    }
    
    @Test
    void testSameStateNoReset() {
        AnimationComponent anim = new AnimationComponent();
        anim.setState(State.WALK);
        anim.update(0.5f);
        float timeBeforeRedundantSet = anim.stateTime;
        
        anim.setState(State.WALK); // Set to same state
        
        assertEquals(timeBeforeRedundantSet, anim.stateTime, 0.001f, 
            "State time should not reset when setting same state");
    }
    
    @Test
    void testHasAnimationWhenNotSet() {
        AnimationComponent anim = new AnimationComponent();
        
        assertFalse(anim.hasAnimation(State.IDLE), "Should not have animation when not set");
    }
    
    @Test
    void testSetAnimationSingleFrame() {
        AnimationComponent anim = new AnimationComponent();
        
        // Using null since we can't create TextureRegion in unit tests
        AnimationComponent result = anim.setAnimation(State.IDLE, null);
        
        assertTrue(anim.hasAnimation(State.IDLE), "Should have animation after setting");
        assertSame(anim, result, "setAnimation should return this for chaining");
        
        AnimationData data = anim.getAnimation(State.IDLE);
        assertNotNull(data, "Animation data should not be null");
        assertEquals(1, data.frames.length, "Single frame animation should have 1 frame");
        assertTrue(data.looping, "Single frame animation should loop by default");
    }
    
    @Test
    void testSetAnimationMultiFrame() {
        AnimationComponent anim = new AnimationComponent();
        
        // Create array with null elements - tests AnimationData storage, not rendering
        // (actual TextureRegions can't be created without LibGDX context)
        anim.setAnimation(State.WALK, new com.badlogic.gdx.graphics.g2d.TextureRegion[4], 0.1f, true);
        
        AnimationData data = anim.getAnimation(State.WALK);
        assertNotNull(data, "Animation data should not be null");
        assertEquals(4, data.frames.length, "Should have 4 frames");
        assertEquals(0.1f, data.frameDuration, 0.001f, "Frame duration should be 0.1");
        assertTrue(data.looping, "Animation should be set to loop");
    }
    
    @Test
    void testGetCurrentFrameWithNoAnimations() {
        AnimationComponent anim = new AnimationComponent();
        
        assertNull(anim.getCurrentFrame(), "Should return null when no animations set");
    }
    
    @Test
    void testUpdateAdvancesStateTime() {
        AnimationComponent anim = new AnimationComponent();
        
        anim.update(0.5f);
        
        assertEquals(0.5f, anim.stateTime, 0.001f, "State time should advance");
    }
    
    @Test
    void testSquashEffectTrigger() {
        AnimationComponent anim = new AnimationComponent();
        
        anim.triggerSquash(0.8f, 1.2f, 0.2f);
        
        assertEquals(0.8f, anim.squashScaleX, 0.001f, "Squash scale X should be set");
        assertEquals(1.2f, anim.squashScaleY, 0.001f, "Squash scale Y should be set");
        assertEquals(0.2f, anim.squashTimer, 0.001f, "Squash timer should be set");
        assertEquals(0.2f, anim.squashDuration, 0.001f, "Squash duration should be set");
    }
    
    @Test
    void testSquashEffectDecays() {
        AnimationComponent anim = new AnimationComponent();
        anim.triggerSquash(0.5f, 1.5f, 0.1f);
        
        // Run enough updates to exceed timer
        anim.update(0.15f);
        
        assertEquals(0f, anim.squashTimer, 0.001f, "Squash timer should reach 0");
        assertEquals(1.0f, anim.squashScaleX, 0.001f, "Squash scale X should reset to 1");
        assertEquals(1.0f, anim.squashScaleY, 0.001f, "Squash scale Y should reset to 1");
    }
    
    @Test
    void testResetBobbing() {
        AnimationComponent anim = new AnimationComponent();
        anim.bobOffsetY = 5f;
        
        anim.resetBobbing();
        
        assertEquals(0f, anim.bobOffsetY, 0.001f, "Bob offset should reset to 0");
    }
    
    @Test
    void testAnimationDataSingleFrameConstructor() {
        AnimationData data = new AnimationData(null);
        
        assertEquals(1, data.frames.length, "Should have 1 frame");
        assertEquals(1.0f, data.frameDuration, 0.001f, "Default duration should be 1.0");
        assertTrue(data.looping, "Should loop by default");
    }
    
    @Test
    void testAnimationDataMultiFrameConstructor() {
        AnimationData data = new AnimationData(
            new com.badlogic.gdx.graphics.g2d.TextureRegion[3], 
            0.15f, 
            false
        );
        
        assertEquals(3, data.frames.length, "Should have 3 frames");
        assertEquals(0.15f, data.frameDuration, 0.001f, "Duration should match");
        assertFalse(data.looping, "Should not loop");
    }
    
    @Test
    void testIsAnimationFinishedInitially() {
        AnimationComponent anim = new AnimationComponent();
        
        assertFalse(anim.isAnimationFinished(), "Animation should not be finished initially");
    }
    
    @Test
    void testAllAnimationStates() {
        // Verify all enum states exist
        State[] states = State.values();
        assertEquals(6, states.length, "Should have 6 animation states");
        
        assertNotNull(State.IDLE);
        assertNotNull(State.WALK);
        assertNotNull(State.ATTACK);
        assertNotNull(State.HURT);
        assertNotNull(State.DIE);
        assertNotNull(State.PROJECTILE);
    }
    
    @Test
    void testStateTransitionChain() {
        AnimationComponent anim = new AnimationComponent();
        
        anim.setState(State.WALK);
        assertEquals(State.IDLE, anim.previousState);
        
        anim.setState(State.ATTACK);
        assertEquals(State.WALK, anim.previousState);
        
        anim.setState(State.HURT);
        assertEquals(State.ATTACK, anim.previousState);
    }
    
    @Test
    void testBobAmplitudeAndFrequencyModifiable() {
        AnimationComponent anim = new AnimationComponent();
        
        anim.bobAmplitude = 5f;
        anim.bobFrequency = 6f;
        
        assertEquals(5f, anim.bobAmplitude, 0.001f);
        assertEquals(6f, anim.bobFrequency, 0.001f);
    }
}
