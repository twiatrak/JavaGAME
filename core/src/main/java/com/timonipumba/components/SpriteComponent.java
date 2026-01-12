package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Component that holds a texture region (sprite) for entity rendering.
 * 
 * A "sprite" is a textured image (TextureRegion) representing an entity,
 * as opposed to a simple colored rectangle (RenderableComponent).
 * 
 * When an entity has both SpriteComponent and RenderableComponent:
 * - RenderingSystem draws the sprite texture instead of the colored rectangle
 * - RenderableComponent can still be used for debug rendering or as fallback
 * 
 * Usage:
 * - Player, enemies, items, and projectiles can use sprites for visual representation
 * - renderOrder can be used to control draw order (higher = drawn later, on top)
 */
public class SpriteComponent implements Component {
    
    /** The texture region to render for this entity */
    public TextureRegion region;
    
    /** 
     * Render order for layering. Higher values are drawn on top.
     * Default layers:
     * - 0: Floor/background items
     * - 10: Items (potions, treasure)
     * - 20: Enemies
     * - 30: Player
     * - 40: Projectiles/effects
     */
    public int renderOrder = 0;
    
    /** Scale factor for rendering (1.0 = original size) */
    public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    
    /** Rotation in degrees */
    public float rotation = 0f;
    
    public SpriteComponent() {}
    
    public SpriteComponent(TextureRegion region) {
        this.region = region;
    }
    
    public SpriteComponent(TextureRegion region, int renderOrder) {
        this.region = region;
        this.renderOrder = renderOrder;
    }
    
    /**
     * Set uniform scale for both X and Y.
     * @param scale The scale factor
     * @return this component for chaining
     */
    public SpriteComponent setScale(float scale) {
        this.scaleX = scale;
        this.scaleY = scale;
        return this;
    }
    
    /**
     * Check if this component has a valid texture region to render.
     * @return true if region is not null
     */
    public boolean hasTexture() {
        return region != null;
    }
}
