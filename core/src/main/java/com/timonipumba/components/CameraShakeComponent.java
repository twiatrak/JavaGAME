package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for camera shake effect triggered by combat events.
 * 
 * Camera shake adds visual impact to combat by briefly offsetting the camera
 * during intense moments like:
 * - Player taking damage
 * - Enemy deaths
 * 
 * The CameraSystem reads this component from the player entity and applies
 * randomized offsets to the camera position while shake is active.
 * 
 * Shake intensity is reduced in EXPLORATION mode for less disorienting gameplay.
 * 
 * Usage:
 * - Add this component to the player entity
 * - Call triggerShake() when combat events occur
 * - CameraSystem reads shakeTimer and intensity to offset the camera
 */
public class CameraShakeComponent implements Component {
    
    /** Time remaining on the shake effect (seconds) */
    public float shakeTimer = 0f;
    
    /** Current shake intensity (maximum offset in pixels) */
    public float shakeIntensity = 0f;
    
    public CameraShakeComponent() {}
    
    /**
     * Trigger a camera shake effect.
     * If a shake is already active, uses the larger of the two intensities.
     * @param duration Duration of the shake in seconds
     * @param intensity Maximum offset in pixels
     */
    public void triggerShake(float duration, float intensity) {
        // If already shaking, use the larger intensity
        if (this.shakeTimer > 0) {
            this.shakeIntensity = Math.max(this.shakeIntensity, intensity);
            this.shakeTimer = Math.max(this.shakeTimer, duration);
        } else {
            this.shakeTimer = duration;
            this.shakeIntensity = intensity;
        }
    }
    
    /**
     * Update the shake timer.
     * @param deltaTime Time elapsed since last update
     */
    public void update(float deltaTime) {
        if (shakeTimer > 0) {
            shakeTimer -= deltaTime;
            if (shakeTimer <= 0) {
                shakeTimer = 0;
                shakeIntensity = 0;
            }
        }
    }
    
    /**
     * Check if the camera is currently shaking.
     * @return true if the shake effect is active
     */
    public boolean isShaking() {
        return shakeTimer > 0 && shakeIntensity > 0;
    }
    
    /**
     * Get a random offset for this frame based on current intensity.
     * @return Randomized offset value between -intensity and +intensity
     */
    public float getRandomOffset() {
        if (!isShaking()) {
            return 0f;
        }
        return (float) (Math.random() * 2 - 1) * shakeIntensity;
    }
}
