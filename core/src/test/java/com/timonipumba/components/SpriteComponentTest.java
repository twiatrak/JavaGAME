package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpriteComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Parameterized constructors
 * - hasTexture check
 * - Scale setting
 */
class SpriteComponentTest {
    
    @Test
    void testDefaultConstructor() {
        SpriteComponent sprite = new SpriteComponent();
        
        assertNull(sprite.region, "Region should be null by default");
        assertEquals(0, sprite.renderOrder, "Default render order should be 0");
        assertEquals(1.0f, sprite.scaleX, 0.001f, "Default scaleX should be 1.0");
        assertEquals(1.0f, sprite.scaleY, 0.001f, "Default scaleY should be 1.0");
        assertEquals(0f, sprite.rotation, 0.001f, "Default rotation should be 0");
    }
    
    @Test
    void testHasTextureWithNull() {
        SpriteComponent sprite = new SpriteComponent();
        
        assertFalse(sprite.hasTexture(), "hasTexture should return false when region is null");
    }
    
    @Test
    void testRenderOrderConstructor() {
        // Testing with null region since we can't easily create TextureRegion in unit tests
        SpriteComponent sprite = new SpriteComponent(null, 30);
        
        assertNull(sprite.region);
        assertEquals(30, sprite.renderOrder, "Render order should match constructor arg");
    }
    
    @Test
    void testSetScale() {
        SpriteComponent sprite = new SpriteComponent();
        
        SpriteComponent result = sprite.setScale(2.5f);
        
        assertEquals(2.5f, sprite.scaleX, 0.001f, "scaleX should be set");
        assertEquals(2.5f, sprite.scaleY, 0.001f, "scaleY should be set");
        assertSame(sprite, result, "setScale should return this for chaining");
    }
    
    @Test
    void testRotation() {
        SpriteComponent sprite = new SpriteComponent();
        
        sprite.rotation = 45f;
        
        assertEquals(45f, sprite.rotation, 0.001f, "Rotation should be modifiable");
    }
    
    @Test
    void testRenderOrderValues() {
        // Test documented render order layers
        SpriteComponent floor = new SpriteComponent(null, 0);
        SpriteComponent item = new SpriteComponent(null, 10);
        SpriteComponent enemy = new SpriteComponent(null, 20);
        SpriteComponent player = new SpriteComponent(null, 30);
        SpriteComponent projectile = new SpriteComponent(null, 40);
        
        assertTrue(floor.renderOrder < item.renderOrder, "Floor should render before items");
        assertTrue(item.renderOrder < enemy.renderOrder, "Items should render before enemies");
        assertTrue(enemy.renderOrder < player.renderOrder, "Enemies should render before player");
        assertTrue(player.renderOrder < projectile.renderOrder, "Player should render before projectiles");
    }
}
