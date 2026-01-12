package com.timonipumba.components;

import com.timonipumba.GameConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectileComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Parameterized constructors
 * - Lifetime tracking and expiration
 * - Owner tracking (player vs enemy)
 */
class ProjectileComponentTest {
    
    @Test
    void testDefaultConstructor() {
        ProjectileComponent projectile = new ProjectileComponent();
        
        assertEquals(GameConstants.RANGED_ATTACK_DAMAGE, projectile.damage, 
            "Default damage should match GameConstants");
        assertEquals(GameConstants.PROJECTILE_LIFETIME, projectile.lifetime, 0.001f,
            "Default lifetime should match GameConstants");
        assertEquals(0f, projectile.timeAlive, 0.001f, 
            "Initial timeAlive should be 0");
        assertTrue(projectile.ownerIsPlayer, 
            "Default owner should be player");
    }
    
    @Test
    void testTwoArgConstructor() {
        ProjectileComponent projectile = new ProjectileComponent(15, false);
        
        assertEquals(15, projectile.damage, "Damage should match constructor arg");
        assertEquals(GameConstants.PROJECTILE_LIFETIME, projectile.lifetime, 0.001f,
            "Lifetime should use default");
        assertFalse(projectile.ownerIsPlayer, "Owner should be enemy (false)");
    }
    
    @Test
    void testThreeArgConstructor() {
        ProjectileComponent projectile = new ProjectileComponent(20, 3.0f, true);
        
        assertEquals(20, projectile.damage, "Damage should match constructor arg");
        assertEquals(3.0f, projectile.lifetime, 0.001f, "Lifetime should match constructor arg");
        assertTrue(projectile.ownerIsPlayer, "Owner should be player");
    }
    
    @Test
    void testUpdateIncreasesTimeAlive() {
        ProjectileComponent projectile = new ProjectileComponent();
        
        projectile.update(0.5f);
        assertEquals(0.5f, projectile.timeAlive, 0.001f, "timeAlive should increase");
        
        projectile.update(0.3f);
        assertEquals(0.8f, projectile.timeAlive, 0.001f, "timeAlive should accumulate");
    }
    
    @Test
    void testIsExpiredBeforeLifetime() {
        ProjectileComponent projectile = new ProjectileComponent(10, 2.0f, true);
        
        projectile.update(1.0f);
        assertFalse(projectile.isExpired(), "Should not be expired before lifetime");
        
        projectile.update(0.9f);
        assertFalse(projectile.isExpired(), "Should not be expired just before lifetime");
    }
    
    @Test
    void testIsExpiredAtLifetime() {
        ProjectileComponent projectile = new ProjectileComponent(10, 2.0f, true);
        
        projectile.update(2.0f);
        assertTrue(projectile.isExpired(), "Should be expired at lifetime");
    }
    
    @Test
    void testIsExpiredAfterLifetime() {
        ProjectileComponent projectile = new ProjectileComponent(10, 2.0f, true);
        
        projectile.update(3.0f);
        assertTrue(projectile.isExpired(), "Should be expired after lifetime");
    }
    
    @Test
    void testPlayerProjectile() {
        ProjectileComponent playerProjectile = new ProjectileComponent(10, true);
        
        assertTrue(playerProjectile.ownerIsPlayer, "Player projectile should have ownerIsPlayer=true");
    }
    
    @Test
    void testEnemyProjectile() {
        ProjectileComponent enemyProjectile = new ProjectileComponent(5, false);
        
        assertFalse(enemyProjectile.ownerIsPlayer, "Enemy projectile should have ownerIsPlayer=false");
    }
    
    @Test
    void testMultipleUpdates() {
        ProjectileComponent projectile = new ProjectileComponent(10, 1.0f, true);
        
        for (int i = 0; i < 10; i++) {
            projectile.update(0.05f); // 50ms per update
        }
        
        assertEquals(0.5f, projectile.timeAlive, 0.001f, "timeAlive should accumulate correctly");
        assertFalse(projectile.isExpired(), "Should not be expired yet");
        
        for (int i = 0; i < 10; i++) {
            projectile.update(0.05f);
        }
        
        assertTrue(projectile.isExpired(), "Should be expired after full lifetime");
    }
}
