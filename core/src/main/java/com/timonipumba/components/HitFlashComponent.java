package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

/**
 * Component for visual hit feedback (flash effect) when an entity takes damage.
 * 
 * When an entity with this component takes damage:
 * - Set flashTimer to desired duration (e.g., HIT_FLASH_DURATION_ENEMY or HIT_FLASH_DURATION_PLAYER)
 * - The RenderingSystem will tint the entity's sprite/rectangle to flashColor while timer > 0
 * - Timer decreases each frame; when it reaches 0, the entity returns to normal appearance
 * 
 * Usage:
 * - Add this component to entities that can take damage (player, enemies)
 * - When damage is applied (in CombatSystem/ProjectileSystem), call triggerFlash()
 * - RenderingSystem checks this component and applies the flash tint during rendering
 * 
 * Flash colors:
 * - Enemies typically flash white or bright red
 * - Player typically flashes white/yellow for visibility
 */
public class HitFlashComponent implements Component {
    
    /** Time remaining on the flash effect (seconds) */
    public float flashTimer = 0f;
    
    /** The color to tint the entity during the flash */
    public Color flashColor = Color.WHITE;
    
    /** Default flash duration to use when triggering flash */
    public float defaultFlashDuration = 0.15f;
    
    public HitFlashComponent() {}
    
    /**
     * Create a hit flash component with custom flash color and duration.
     * @param flashColor The color to tint during flash
     * @param defaultDuration The default flash duration in seconds
     */
    public HitFlashComponent(Color flashColor, float defaultDuration) {
        this.flashColor = flashColor.cpy();
        this.defaultFlashDuration = defaultDuration;
    }
    
    /**
     * Trigger a flash effect using the default duration.
     */
    public void triggerFlash() {
        this.flashTimer = defaultFlashDuration;
    }
    
    /**
     * Trigger a flash effect with a custom duration.
     * @param duration Duration of the flash in seconds
     */
    public void triggerFlash(float duration) {
        this.flashTimer = duration;
    }
    
    /**
     * Update the flash timer.
     * @param deltaTime Time elapsed since last update
     */
    public void update(float deltaTime) {
        if (flashTimer > 0) {
            flashTimer -= deltaTime;
            if (flashTimer < 0) {
                flashTimer = 0;
            }
        }
    }
    
    /**
     * Check if the entity is currently flashing.
     * @return true if the flash effect is active
     */
    public boolean isFlashing() {
        return flashTimer > 0;
    }
}
