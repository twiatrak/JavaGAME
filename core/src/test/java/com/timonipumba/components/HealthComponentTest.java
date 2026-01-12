package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Parameterized constructors
 * - Damage application and death detection
 * - Healing mechanics
 */
class HealthComponentTest {
    
    @Test
    void testDefaultConstructor() {
        HealthComponent health = new HealthComponent();
        
        assertEquals(100, health.maxHealth, "Default max health should be 100");
        assertEquals(100, health.currentHealth, "Default current health should be 100");
        assertTrue(health.alive, "Should be alive by default");
    }
    
    @Test
    void testSingleArgConstructor() {
        HealthComponent health = new HealthComponent(50);
        
        assertEquals(50, health.maxHealth, "Max health should match constructor arg");
        assertEquals(50, health.currentHealth, "Current health should equal max");
        assertTrue(health.alive, "Should be alive");
    }
    
    @Test
    void testTwoArgConstructor() {
        HealthComponent health = new HealthComponent(100, 75);
        
        assertEquals(100, health.maxHealth, "Max health should match first arg");
        assertEquals(75, health.currentHealth, "Current health should match second arg");
        assertTrue(health.alive, "Should be alive when health > 0");
    }
    
    @Test
    void testTwoArgConstructorWithZeroHealth() {
        HealthComponent health = new HealthComponent(100, 0);
        
        assertEquals(100, health.maxHealth);
        assertEquals(0, health.currentHealth);
        assertFalse(health.alive, "Should not be alive when health is 0");
    }
    
    @Test
    void testTakeDamage() {
        HealthComponent health = new HealthComponent(100);
        
        health.takeDamage(30);
        
        assertEquals(70, health.currentHealth, "Health should be reduced by damage");
        assertTrue(health.alive, "Should still be alive");
    }
    
    @Test
    void testTakeFatalDamage() {
        HealthComponent health = new HealthComponent(50);
        
        health.takeDamage(50);
        
        assertEquals(0, health.currentHealth, "Health should be 0");
        assertFalse(health.alive, "Should be dead");
        assertTrue(health.isDead(), "isDead should return true");
    }
    
    @Test
    void testTakeOverdamage() {
        HealthComponent health = new HealthComponent(30);
        
        health.takeDamage(100);
        
        assertEquals(0, health.currentHealth, "Health should be capped at 0");
        assertFalse(health.alive, "Should be dead");
    }
    
    @Test
    void testHeal() {
        HealthComponent health = new HealthComponent(100, 50);
        
        health.heal(25);
        
        assertEquals(75, health.currentHealth, "Health should increase by heal amount");
    }
    
    @Test
    void testHealCappedAtMax() {
        HealthComponent health = new HealthComponent(100, 90);
        
        health.heal(50);
        
        assertEquals(100, health.currentHealth, "Health should be capped at max");
    }
    
    @Test
    void testHealDoesNotResurrect() {
        HealthComponent health = new HealthComponent(100);
        health.takeDamage(150); // Kill the entity
        
        health.heal(50);
        
        assertEquals(0, health.currentHealth, "Dead entity should not be healed");
        assertFalse(health.alive, "Entity should remain dead");
    }
    
    @Test
    void testIsDeadMethod() {
        HealthComponent aliveHealth = new HealthComponent(100, 50);
        HealthComponent deadHealth = new HealthComponent(100, 0);
        
        assertFalse(aliveHealth.isDead(), "Entity with health should not be dead");
        assertTrue(deadHealth.isDead(), "Entity with no health should be dead");
    }
    
    @Test
    void testMultipleDamageApplications() {
        HealthComponent health = new HealthComponent(100);
        
        health.takeDamage(10);
        assertEquals(90, health.currentHealth);
        
        health.takeDamage(20);
        assertEquals(70, health.currentHealth);
        
        health.takeDamage(30);
        assertEquals(40, health.currentHealth);
        
        health.takeDamage(40);
        assertEquals(0, health.currentHealth);
        assertTrue(health.isDead());
    }
}
