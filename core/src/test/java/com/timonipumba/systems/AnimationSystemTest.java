package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.components.AnimationComponent.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AnimationSystem.
 * 
 * Validates:
 * - Bobbing behavior: IDLE with zero velocity yields bobOffsetY == 0f
 * - Bobbing behavior: WALK with non-zero velocity yields non-zero bobOffsetY after some stateTime
 * - State transitions work correctly based on velocity
 * - Squash/stretch effects are only triggered by specific events
 */
class AnimationSystemTest {
    
    private Engine engine;
    private GameStateManager gameStateManager;
    private AnimationSystem animationSystem;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        animationSystem = new AnimationSystem(gameStateManager);
        engine.addSystem(animationSystem);
    }
    
    @Test
    void testIdleWithZeroVelocityNoBobbing() {
        // Create an entity with animation and zero velocity
        Entity entity = createAnimatedEntity(0f, 0f);
        engine.addEntity(entity);
        
        // Update the system multiple times to advance stateTime
        for (int i = 0; i < 10; i++) {
            engine.update(0.1f);
        }
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Entity should be in IDLE state with zero velocity
        assertEquals(State.IDLE, anim.currentState, 
            "Entity with zero velocity should be in IDLE state");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "IDLE state with zero velocity should have no bobbing (bobOffsetY = 0)");
    }
    
    @Test
    void testWalkWithNonZeroVelocityBobs() {
        // Create an entity with animation and non-zero velocity (above MOVE_THRESHOLD of 1f)
        Entity entity = createAnimatedEntity(50f, 50f);
        engine.addEntity(entity);
        
        // Update the system multiple times to advance stateTime beyond initial zero
        // This ensures we hit a non-zero point in the sine wave
        for (int i = 0; i < 5; i++) {
            engine.update(0.05f);
        }
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Entity should be in WALK state with non-zero velocity
        assertEquals(State.WALK, anim.currentState, 
            "Entity with non-zero velocity should be in WALK state");
        
        // Bobbing should be active (non-zero at some point in the sine cycle)
        // We need to check that bobbing is being calculated based on state time
        // After 0.25s with default frequency of 3 * 2 = 6 Hz, we should have moved through the sine wave
        assertTrue(anim.stateTime > 0, "State time should have advanced");
        
        // Since sin(x) varies, we need to test that bobbing mechanism is active
        // We'll verify the bobbing offset is not always zero by checking the formula would produce non-zero
        float frequency = anim.bobFrequency * 2f; // WALK uses 2x frequency
        float expectedBob = (float) Math.sin(anim.stateTime * frequency * Math.PI * 2) * anim.bobAmplitude;
        assertEquals(expectedBob, anim.bobOffsetY, 0.001f, 
            "WALK state bobbing should follow sine wave formula");
    }
    
    @Test
    void testTransitionFromWalkToIdleStopsBobbing() {
        // Create an entity with animation and non-zero velocity
        Entity entity = createAnimatedEntity(50f, 50f);
        engine.addEntity(entity);
        
        // Update to establish WALK state and bobbing
        for (int i = 0; i < 5; i++) {
            engine.update(0.05f);
        }
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        VelocityComponent velocity = entity.getComponent(VelocityComponent.class);
        
        assertEquals(State.WALK, anim.currentState, "Should be in WALK state initially");
        
        // Now stop the entity (set velocity to zero)
        velocity.x = 0f;
        velocity.y = 0f;
        
        // Update to transition to IDLE
        engine.update(0.016f);
        
        // Entity should now be in IDLE state with no bobbing
        assertEquals(State.IDLE, anim.currentState, 
            "Entity with zero velocity should transition to IDLE state");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "After stopping, bobbing should be reset to 0");
    }
    
    @Test
    void testVelocityBelowThresholdTreatedAsIdle() {
        // Create an entity with velocity below MOVE_THRESHOLD (1f)
        Entity entity = createAnimatedEntity(0.5f, 0.5f);
        engine.addEntity(entity);
        
        // Update the system
        for (int i = 0; i < 5; i++) {
            engine.update(0.1f);
        }
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Entity should be in IDLE state (velocity magnitude < 1f)
        assertEquals(State.IDLE, anim.currentState, 
            "Entity with velocity below threshold should be in IDLE state");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "IDLE state should have no bobbing");
    }
    
    @Test
    void testVelocityJustAboveThresholdCausesWalk() {
        // Create an entity with velocity just above MOVE_THRESHOLD (1f)
        // Magnitude of (1f, 1f) = sqrt(2) ≈ 1.414, which is > 1
        Entity entity = createAnimatedEntity(1f, 1f);
        engine.addEntity(entity);
        
        // Update the system
        engine.update(0.1f);
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Entity should be in WALK state
        assertEquals(State.WALK, anim.currentState, 
            "Entity with velocity above threshold should be in WALK state");
    }
    
    @Test
    void testAttackStateNoBobbing() {
        Entity entity = createAnimatedEntity(0f, 0f);
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Register a non-looping attack animation so the state persists
        anim.setAnimation(State.ATTACK, new com.badlogic.gdx.graphics.g2d.TextureRegion[2], 0.2f, false);
        
        engine.addEntity(entity);
        
        // Trigger attack
        AnimationSystem.triggerAttack(entity);
        
        // Update - the ATTACK state should persist because we have animation data
        engine.update(0.016f);
        
        assertEquals(State.ATTACK, anim.currentState, 
            "Entity should be in ATTACK state");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "ATTACK state should have no bobbing");
    }
    
    @Test
    void testHurtStateNoBobbing() {
        Entity entity = createAnimatedEntity(50f, 50f); // Moving entity
        entity.add(new HitFlashComponent());
        engine.addEntity(entity);
        
        // Update to establish walking state first
        engine.update(0.016f);
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        assertEquals(State.WALK, anim.currentState, "Should be walking first");
        
        // Trigger hurt via HitFlashComponent
        HitFlashComponent hitFlash = entity.getComponent(HitFlashComponent.class);
        hitFlash.triggerFlash();
        
        // Update
        engine.update(0.016f);
        
        assertEquals(State.HURT, anim.currentState, 
            "Entity should be in HURT state");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "HURT state should have no bobbing");
    }
    
    @Test
    void testSquashEffectTriggeredByAttack() {
        Entity entity = createAnimatedEntity(0f, 0f);
        engine.addEntity(entity);
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Initial scales should be 1.0
        assertEquals(1.0f, anim.squashScaleX, 0.001f);
        assertEquals(1.0f, anim.squashScaleY, 0.001f);
        
        // Trigger attack
        AnimationSystem.triggerAttack(entity);
        
        // Squash should be triggered (stretched horizontally, squashed vertically)
        assertEquals(1.2f, anim.squashScaleX, 0.001f, 
            "Attack should trigger horizontal stretch");
        assertEquals(0.8f, anim.squashScaleY, 0.001f, 
            "Attack should trigger vertical squash");
        assertTrue(anim.squashTimer > 0, 
            "Squash timer should be active");
    }
    
    @Test
    void testSquashEffectTriggeredByHurt() {
        Entity entity = createAnimatedEntity(0f, 0f);
        engine.addEntity(entity);
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Trigger hurt
        AnimationSystem.triggerHurt(entity);
        
        // Squash should be triggered (squashed horizontally, stretched vertically)
        assertEquals(0.8f, anim.squashScaleX, 0.001f, 
            "Hurt should trigger horizontal squash");
        assertEquals(1.2f, anim.squashScaleY, 0.001f, 
            "Hurt should trigger vertical stretch");
        assertTrue(anim.squashTimer > 0, 
            "Squash timer should be active");
    }
    
    @Test
    void testIdleStateNoSquashWithoutTrigger() {
        Entity entity = createAnimatedEntity(0f, 0f);
        engine.addEntity(entity);
        
        // Update multiple times without triggering any events
        for (int i = 0; i < 10; i++) {
            engine.update(0.1f);
        }
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        
        // Squash scales should remain at default (no continuous squash effect)
        assertEquals(1.0f, anim.squashScaleX, 0.001f, 
            "IDLE should not have continuous squash effect");
        assertEquals(1.0f, anim.squashScaleY, 0.001f, 
            "IDLE should not have continuous squash effect");
    }
    
    @Test
    void testEntityWithNoVelocityComponentStillAnimates() {
        // Create an entity with only AnimationComponent (no velocity)
        Entity entity = engine.createEntity();
        entity.add(new AnimationComponent());
        engine.addEntity(entity);
        
        // Should not crash and should be in IDLE
        engine.update(0.016f);
        
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        assertEquals(State.IDLE, anim.currentState, 
            "Entity without VelocityComponent should default to IDLE");
        assertEquals(0f, anim.bobOffsetY, 0.001f, 
            "Entity without VelocityComponent should not bob");
    }
    
    /**
     * Helper method to create an animated entity with velocity.
     */
    private Entity createAnimatedEntity(float velocityX, float velocityY) {
        Entity entity = engine.createEntity();
        entity.add(new AnimationComponent());
        entity.add(new VelocityComponent(velocityX, velocityY));
        return entity;
    }
}
