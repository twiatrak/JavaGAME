package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.components.AnimationComponent.State;

/**
 * System that updates animation states and effects for all animated entities.
 * 
 * This system handles:
 * - Automatic state transitions based on entity velocity (IDLE vs WALK)
 * - Code-driven bobbing effects for movement feedback
 * - Squash/stretch effect updates for impact feedback
 * - Animation timer updates
 * 
 * Animation state priority (highest to lowest):
 * 1. DIE - Entity is dying (health <= 0)
 * 2. HURT - Entity is taking damage (hit flash active)
 * 3. ATTACK - Entity is attacking (combat cooldown recently reset)
 * 4. WALK - Entity has non-zero velocity
 * 5. IDLE - Default state
 * 
 * Special handling:
 * - Projectiles always use PROJECTILE state
 * - Non-looping states (ATTACK, HURT, DIE) auto-transition to IDLE when finished
 */
public class AnimationSystem extends IteratingSystem {
    
    private ComponentMapper<AnimationComponent> am = ComponentMapper.getFor(AnimationComponent.class);
    private ComponentMapper<VelocityComponent> vm = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<HealthComponent> hm = ComponentMapper.getFor(HealthComponent.class);
    private ComponentMapper<HitFlashComponent> hfm = ComponentMapper.getFor(HitFlashComponent.class);
    private ComponentMapper<ProjectileComponent> pm = ComponentMapper.getFor(ProjectileComponent.class);
    
    private GameStateManager gameStateManager;
    
    /** Minimum velocity magnitude to be considered "moving" */
    private static final float MOVE_THRESHOLD = 1f;
    
    public AnimationSystem() {
        super(Family.all(AnimationComponent.class).get());
    }
    
    public AnimationSystem(GameStateManager gameStateManager) {
        super(Family.all(AnimationComponent.class).get());
        this.gameStateManager = gameStateManager;
    }
    
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AnimationComponent anim = am.get(entity);
        VelocityComponent velocity = vm.get(entity);
        HealthComponent health = hm.get(entity);
        HitFlashComponent hitFlash = hfm.get(entity);
        ProjectileComponent projectile = pm.get(entity);
        
        // Update animation timer and effects
        anim.update(deltaTime);
        
        // Determine appropriate animation state
        State targetState = determineState(entity, anim, velocity, health, hitFlash, projectile);
        
        // Apply state transition
        anim.setState(targetState);
        
        // Update code-driven effects based on state
        updateBobbing(anim, velocity, deltaTime);
    }
    
    /**
     * Determine the appropriate animation state based on entity conditions.
     */
    private State determineState(Entity entity, AnimationComponent anim, 
                                  VelocityComponent velocity, HealthComponent health,
                                  HitFlashComponent hitFlash, ProjectileComponent projectile) {
        
        // Projectiles always use PROJECTILE state
        if (projectile != null) {
            return State.PROJECTILE;
        }
        
        // Check for death state (highest priority)
        if (health != null && health.isDead()) {
            return State.DIE;
        }
        
        // Check for hurt state (hit flash active)
        if (hitFlash != null && hitFlash.isFlashing()) {
            return State.HURT;
        }
        
        // For non-looping states that are currently playing, let them finish
        State current = anim.currentState;
        if (current == State.ATTACK || current == State.DIE) {
            // Only keep playing if animation data exists and is non-looping
            AnimationComponent.AnimationData data = anim.getAnimation(current);
            if (data != null && !data.looping && !anim.isAnimationFinished()) {
                return current;
            }
        }
        
        // Check for movement (WALK vs IDLE)
        if (velocity != null && isMoving(velocity)) {
            return State.WALK;
        }
        
        return State.IDLE;
    }
    
    /**
     * Check if velocity indicates entity is moving.
     */
    private boolean isMoving(VelocityComponent velocity) {
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
        return speed > MOVE_THRESHOLD;
    }
    
    /**
     * Update bobbing effect based on animation state.
     * Only applies bobbing when actually moving (WALK state with velocity above threshold).
     * IDLE state (standing still) does not bob to keep character stable.
     */
    private void updateBobbing(AnimationComponent anim, VelocityComponent velocity, float deltaTime) {
        State state = anim.currentState;
        
        // Only apply bobbing for WALK state when actually moving
        // IDLE state (zero velocity) should not bob - character stands still
        if (state == State.WALK && velocity != null && isMoving(velocity)) {
            // Calculate bobbing using sine wave
            // Walk state uses faster frequency for movement feedback
            float frequency = anim.bobFrequency * 2f;
            
            // Use stateTime for smooth continuous bobbing
            anim.bobOffsetY = (float) Math.sin(anim.stateTime * frequency * Math.PI * 2) 
                              * anim.bobAmplitude;
        } else {
            // No bobbing for IDLE, ATTACK, HURT, DIE, PROJECTILE states or when not moving
            anim.resetBobbing();
        }
    }
    
    /**
     * Trigger an attack animation on an entity.
     * Called by combat systems when an entity attacks.
     * 
     * @param entity The attacking entity
     */
    public static void triggerAttack(Entity entity) {
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        if (anim != null) {
            anim.setState(State.ATTACK);
            // Apply squash effect for attack feedback
            anim.triggerSquash(1.2f, 0.8f, 0.15f);
        }
    }
    
    /** Trigger a hurt animation on an entity (usually via HitFlashComponent). */
    public static void triggerHurt(Entity entity) {
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        if (anim != null) {
            anim.setState(State.HURT);
            // Apply squash effect for impact feedback
            anim.triggerSquash(0.8f, 1.2f, 0.1f);
        }
    }
    
    /**
     * Trigger a death animation on an entity.
     * Called when entity health reaches zero.
     * 
     * @param entity The dying entity
     */
    public static void triggerDeath(Entity entity) {
        AnimationComponent anim = entity.getComponent(AnimationComponent.class);
        if (anim != null) {
            anim.setState(State.DIE);
        }
    }
}
