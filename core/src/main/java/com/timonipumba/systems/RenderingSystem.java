package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.SortedIteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.timonipumba.components.AnimationComponent;
import com.timonipumba.components.HitFlashComponent;
import com.timonipumba.components.PositionComponent;
import com.timonipumba.components.RangedAttackWindupComponent;
import com.timonipumba.components.RegisterNodeComponent;
import com.timonipumba.components.RenderableComponent;
import com.timonipumba.components.SpriteComponent;

import java.util.Comparator;

/**
 * System that renders all entities with PositionComponent and RenderableComponent.
 * 
 * Rendering behavior:
 * - If entity has AnimationComponent, uses current animation frame for rendering
 * - Else if entity has SpriteComponent with a valid texture, draws the sprite
 * - Otherwise, falls back to drawing a colored rectangle from RenderableComponent
 * - Entities are sorted by renderOrder (from SpriteComponent) for proper layering
 * 
 * Visual feedback effects:
 * - HitFlashComponent: Tints entity to flash color when taking damage
 * - RangedAttackWindupComponent: Tints ranged enemies during attack windup
 * - AnimationComponent: Applies bobbing offset and squash/stretch effects
 * 
 * This allows for gradual migration from colored rectangles to sprites,
 * maintaining backward compatibility for entities without sprites.
 */
public class RenderingSystem extends SortedIteratingSystem {
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<RenderableComponent> rm = ComponentMapper.getFor(RenderableComponent.class);
    private ComponentMapper<SpriteComponent> sm = ComponentMapper.getFor(SpriteComponent.class);
    private ComponentMapper<HitFlashComponent> hfm = ComponentMapper.getFor(HitFlashComponent.class);
    private ComponentMapper<RangedAttackWindupComponent> wm = ComponentMapper.getFor(RangedAttackWindupComponent.class);
    private ComponentMapper<AnimationComponent> animm = ComponentMapper.getFor(AnimationComponent.class);
    private ComponentMapper<RegisterNodeComponent> rnm = ComponentMapper.getFor(RegisterNodeComponent.class);
    
    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private OrthographicCamera camera;
    
    // Track which renderer is currently active
    private boolean spriteMode = false;
    
    // Windup tint color (orange/yellow to indicate charging attack)
    private static final Color WINDUP_TINT = new Color(1f, 0.7f, 0.3f, 1f);

    public RenderingSystem(OrthographicCamera camera) {
        super(Family.all(PositionComponent.class, RenderableComponent.class).get(), new RenderOrderComparator());
        this.camera = camera;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
    }

    @Override
    public void update(float deltaTime) {
        camera.update();
        
        // First pass: draw shapes (entities without sprites)
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        spriteMode = false;
        super.update(deltaTime);
        shapeRenderer.end();
        
        // Second pass: draw sprites
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        // Reset batch color to WHITE to prevent tinting sprites
        spriteBatch.setColor(Color.WHITE);
        spriteMode = true;
        super.update(deltaTime);
        spriteBatch.end();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PositionComponent position = pm.get(entity);
        RenderableComponent renderable = rm.get(entity);
        SpriteComponent sprite = sm.get(entity);
        HitFlashComponent hitFlash = hfm.get(entity);
        RangedAttackWindupComponent windup = wm.get(entity);
        AnimationComponent animation = animm.get(entity);
        
        // Update hit flash timer
        if (hitFlash != null) {
            hitFlash.update(deltaTime);
        }
        
        // Determine tint color based on effects
        Color tintColor = getTintColor(hitFlash, windup, renderable);
        
        // Get the texture region to render (animation frame takes priority)
        TextureRegion regionToRender = getTextureRegion(animation, sprite);
        boolean hasSprite = regionToRender != null;
        
        if (spriteMode) {
            // Sprite pass: only draw entities with sprites
            if (hasSprite) {
                // For register-allocation nodes, use RenderableComponent color as a sprite tint.
                // (By default we avoid tinting sprites, but this puzzle needs color feedback.)
                RegisterNodeComponent node = rnm.get(entity);
                if (tintColor == null
                        && node != null
                        && node.assignedRegisterTokenId != null
                        && renderable != null
                        && renderable.color != null) {
                    tintColor = renderable.color;
                }
                drawSprite(position, renderable, sprite, animation, regionToRender, tintColor);
            }
        } else {
            // Shape pass: only draw entities without sprites
            if (!hasSprite) {
                drawShape(position, renderable, tintColor);
            }
        }
    }
    
    /**
     * Get the texture region to render, prioritizing animation frames over static sprites.
     */
    private TextureRegion getTextureRegion(AnimationComponent animation, SpriteComponent sprite) {
        // First, try to get frame from animation component
        if (animation != null) {
            TextureRegion animFrame = animation.getCurrentFrame();
            if (animFrame != null) {
                return animFrame;
            }
        }
        
        // Fall back to static sprite
        if (sprite != null && sprite.hasTexture()) {
            return sprite.region;
        }
        
        return null;
    }
    
    /**
     * Determine the tint color for an entity based on active effects.
     * Priority: Hit flash > Windup > Normal color
     */
    private Color getTintColor(HitFlashComponent hitFlash, RangedAttackWindupComponent windup, RenderableComponent renderable) {
        // Hit flash takes priority (most urgent visual feedback)
        if (hitFlash != null && hitFlash.isFlashing()) {
            return hitFlash.flashColor;
        }
        
        // Windup effect (enemy charging attack)
        if (windup != null && windup.isWindingUp()) {
            return WINDUP_TINT;
        }
        
        // Default: no tint for sprites, normal color for shapes
        return null;
    }
    
    /**
     * Draw a sprite for the entity with optional tint and animation effects.
     */
    private void drawSprite(PositionComponent position, RenderableComponent renderable, 
                            SpriteComponent sprite, AnimationComponent animation,
                            TextureRegion region, Color tintColor) {
        // Respect renderable alpha for sprite visibility (used for hidden terminals/exits, etc.)
        if (renderable != null && renderable.color != null && renderable.color.a <= 0.01f) {
            return;
        }

        // Base scale from sprite component
        float scaleX = (sprite != null) ? sprite.scaleX : 1.0f;
        float scaleY = (sprite != null) ? sprite.scaleY : 1.0f;
        float rotation = (sprite != null) ? sprite.rotation : 0f;
        
        // Apply animation squash/stretch effects
        if (animation != null) {
            scaleX *= animation.squashScaleX;
            scaleY *= animation.squashScaleY;
        }
        
        float width = renderable.width * scaleX;
        float height = renderable.height * scaleY;
        
        // Calculate draw position (apply bobbing offset from animation)
        float drawX = position.x;
        float drawY = position.y;
        if (animation != null) {
            drawY += animation.bobOffsetY;
        }
        
        // Apply tint color if specified, otherwise use white (no tint)
        if (tintColor != null) {
            spriteBatch.setColor(tintColor);
        } else {
            spriteBatch.setColor(Color.WHITE);
        }
        
        if (rotation != 0) {
            // Draw with rotation (origin at center)
            spriteBatch.draw(region, 
                drawX, drawY, 
                width / 2, height / 2,  // origin
                width, height, 
                1f, 1f,  // scale (already applied above)
                rotation);
        } else {
            // Simple draw without rotation
            spriteBatch.draw(region, drawX, drawY, width, height);
        }
        
        // Reset tint for next entity
        spriteBatch.setColor(Color.WHITE);
    }
    
    /**
     * Draw a colored rectangle for the entity (fallback when no sprite) with optional tint override.
     */
    private void drawShape(PositionComponent position, RenderableComponent renderable, Color tintColor) {
        // Use tint color if provided, otherwise use renderable's color
        Color drawColor = (tintColor != null) ? tintColor : renderable.color;
        if (drawColor != null && drawColor.a <= 0.01f) {
            return;
        }
        shapeRenderer.setColor(drawColor);
        shapeRenderer.rect(position.x, position.y, renderable.width, renderable.height);
    }
    
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }
    
    /**
     * Comparator that sorts entities by their sprite render order.
     * Entities without SpriteComponent get order 0.
     * Lower order values are drawn first (bottom), higher values drawn last (top).
     */
    private static class RenderOrderComparator implements Comparator<Entity> {
        private ComponentMapper<SpriteComponent> sm = ComponentMapper.getFor(SpriteComponent.class);
        
        @Override
        public int compare(Entity e1, Entity e2) {
            SpriteComponent s1 = sm.get(e1);
            SpriteComponent s2 = sm.get(e2);
            
            int order1 = (s1 != null) ? s1.renderOrder : 0;
            int order2 = (s2 != null) ? s2.renderOrder : 0;
            
            return Integer.compare(order1, order2);
        }
    }
}
