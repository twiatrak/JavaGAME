package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.EnumMap;
import java.util.Map;

/**
 * Component for managing entity animations with frame strips and code-driven effects.
 * 
 * This animation system supports:
 * - Multiple animation states (IDLE, WALK, ATTACK, HURT, DIE, PROJECTILE)
 * - Frame strip animations with configurable frame durations
 * - Single-frame animations with code-driven bobbing/squash effects
 * - Smooth state transitions
 * 
 * Design inspired by Soul Knight style: minimal frames + code-driven motion feedback.
 * 
 * Usage:
 * 1. Add AnimationComponent to entity along with SpriteComponent
 * 2. Register frame strips for each animation state using setAnimation()
 * 3. AnimationSystem updates current frame based on entity state
 * 4. RenderingSystem reads current frame from AnimationComponent
 * 
 * Frame strips are TextureRegion arrays where each region is one animation frame.
 * For single-frame "animations", use a single-element array.
 */
public class AnimationComponent implements Component {
    
    /**
     * Animation states for entities.
     * Combat systems trigger ATTACK/HURT states.
     * Movement systems trigger WALK/IDLE states.
     */
    public enum State {
        /** Default resting state (can include subtle bobbing) */
        IDLE,
        /** Moving state (walk cycle or bobbing effect) */
        WALK,
        /** Attacking state (melee swing or ranged wind-up) */
        ATTACK,
        /** Taking damage state (brief flash/recoil) */
        HURT,
        /** Death state (death animation before removal) */
        DIE,
        /** Projectile flight state (spinning/trailing effect) */
        PROJECTILE
    }
    
    /**
     * Animation data for a single state.
     * Contains frames, timing, and playback settings.
     */
    public static class AnimationData {
        /** Array of frames for this animation */
        public TextureRegion[] frames;
        
        /** Duration of each frame in seconds */
        public float frameDuration;
        
        /** Whether the animation loops (false = plays once then holds last frame) */
        public boolean looping;
        
        public AnimationData(TextureRegion[] frames, float frameDuration, boolean looping) {
            this.frames = frames;
            this.frameDuration = frameDuration;
            this.looping = looping;
        }
        
        /**
         * Create a single-frame animation (for code-driven effects).
         */
        public AnimationData(TextureRegion frame) {
            this.frames = new TextureRegion[] { frame };
            this.frameDuration = 1.0f;
            this.looping = true;
        }
    }
    
    /** Map of animation states to their frame data */
    private final Map<State, AnimationData> animations = new EnumMap<>(State.class);
    
    /** Current animation state */
    public State currentState = State.IDLE;
    
    /** Previous animation state (for detecting transitions) */
    public State previousState = State.IDLE;
    
    /** Time accumulated in current animation (seconds) */
    public float stateTime = 0f;
    
    /** Whether the current animation has finished playing (for non-looping animations) */
    public boolean animationFinished = false;
    
    // ========== CODE-DRIVEN EFFECTS ==========
    
    /** 
     * Bobbing offset applied to sprite position (for idle/walk wobble).
     * Updated by AnimationSystem using sine wave.
     */
    public float bobOffsetY = 0f;
    
    /** Bobbing amplitude in pixels (0 = no bobbing) */
    public float bobAmplitude = 2f;
    
    /** Bobbing frequency (cycles per second) */
    public float bobFrequency = 3f;
    
    /** 
     * Squash/stretch scale factors for impact feedback.
     * 1.0 = normal, < 1.0 = squashed, > 1.0 = stretched.
     */
    public float squashScaleX = 1.0f;
    public float squashScaleY = 1.0f;
    
    /** Duration of squash effect remaining (seconds) */
    public float squashTimer = 0f;
    
    /** Total duration of current squash effect (for interpolation) */
    public float squashDuration = 0f;
    
    /** Original scale values when squash was triggered (for proper interpolation) */
    private float squashStartScaleX = 1.0f;
    private float squashStartScaleY = 1.0f;
    
    public AnimationComponent() {}
    
    /**
     * Register an animation for a specific state.
     * 
     * @param state The animation state
     * @param frames Array of TextureRegions for animation frames
     * @param frameDuration Duration of each frame in seconds
     * @param looping Whether animation loops or plays once
     * @return this component for chaining
     */
    public AnimationComponent setAnimation(State state, TextureRegion[] frames, 
                                            float frameDuration, boolean looping) {
        animations.put(state, new AnimationData(frames, frameDuration, looping));
        return this;
    }
    
    /**
     * Register a single-frame animation (for code-driven effects).
     * 
     * @param state The animation state
     * @param frame Single TextureRegion for this state
     * @return this component for chaining
     */
    public AnimationComponent setAnimation(State state, TextureRegion frame) {
        animations.put(state, new AnimationData(frame));
        return this;
    }
    
    /**
     * Change to a new animation state.
     * Resets stateTime if transitioning to a different state.
     * 
     * @param newState The state to transition to
     */
    public void setState(State newState) {
        if (currentState != newState) {
            previousState = currentState;
            currentState = newState;
            stateTime = 0f;
            animationFinished = false;
        }
    }
    
    /**
     * Get the animation data for a specific state.
     * @param state The animation state
     * @return AnimationData for the state, or null if not set
     */
    public AnimationData getAnimation(State state) {
        return animations.get(state);
    }
    
    /**
     * Check if a state has animation data registered.
     * @param state The animation state to check
     * @return true if animation data exists for this state
     */
    public boolean hasAnimation(State state) {
        return animations.containsKey(state);
    }
    
    /**
     * Get the current frame based on state time and animation data.
     * Falls back to the first frame of IDLE if current state has no animation.
     * 
     * @return Current TextureRegion frame, or null if no animations set
     */
    public TextureRegion getCurrentFrame() {
        AnimationData data = animations.get(currentState);
        
        // Fall back to IDLE if current state has no animation
        if (data == null) {
            data = animations.get(State.IDLE);
        }
        
        if (data == null || data.frames == null || data.frames.length == 0) {
            return null;
        }
        
        // Calculate current frame index
        int frameIndex = getFrameIndex(data);
        return data.frames[frameIndex];
    }
    
    /**
     * Calculate the frame index for the given animation data.
     */
    private int getFrameIndex(AnimationData data) {
        int frameCount = data.frames.length;
        if (frameCount == 1) {
            return 0;
        }
        
        int frameIndex = (int)(stateTime / data.frameDuration);
        
        if (data.looping) {
            return frameIndex % frameCount;
        } else {
            // Non-looping: clamp to last frame
            if (frameIndex >= frameCount) {
                animationFinished = true;
                return frameCount - 1;
            }
            return frameIndex;
        }
    }
    
    /**
     * Update state time and effects.
     * Called each frame by AnimationSystem.
     * 
     * @param deltaTime Time elapsed since last update
     */
    public void update(float deltaTime) {
        stateTime += deltaTime;
        
        // Update squash effect timer
        if (squashTimer > 0) {
            squashTimer -= deltaTime;
            if (squashTimer <= 0) {
                // Reset to normal scale
                squashScaleX = 1.0f;
                squashScaleY = 1.0f;
                squashTimer = 0f;
            } else {
                // Interpolate from triggered scale to normal (1.0) based on progress
                float progress = 1f - (squashTimer / squashDuration);
                squashScaleX = lerp(squashStartScaleX, 1.0f, progress);
                squashScaleY = lerp(squashStartScaleY, 1.0f, progress);
            }
        }
    }
    
    /**
     * Trigger a squash/stretch effect (for impact feedback).
     * 
     * @param scaleX Initial X scale (< 1 = squash horizontally)
     * @param scaleY Initial Y scale (< 1 = squash vertically)
     * @param duration Duration of the squash effect in seconds
     */
    public void triggerSquash(float scaleX, float scaleY, float duration) {
        this.squashScaleX = scaleX;
        this.squashScaleY = scaleY;
        this.squashStartScaleX = scaleX;
        this.squashStartScaleY = scaleY;
        this.squashTimer = duration;
        this.squashDuration = duration;
    }
    
    /**
     * Reset bobbing offset calculation base.
     * Called when transitioning to a state that shouldn't bob.
     */
    public void resetBobbing() {
        bobOffsetY = 0f;
    }
    
    /**
     * Linear interpolation helper.
     */
    private static float lerp(float start, float end, float t) {
        return start + t * (end - start);
    }
    
    /**
     * Check if the current state's animation has finished.
     * Only relevant for non-looping animations.
     * @return true if animation has played through
     */
    public boolean isAnimationFinished() {
        return animationFinished;
    }
}
