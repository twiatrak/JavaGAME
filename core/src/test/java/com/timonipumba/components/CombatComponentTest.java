package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CombatComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Parameterized constructors
 * - Attack cooldown mechanics
 * - Combat constants
 */
class CombatComponentTest {
    
    private static final float EPSILON = 0.001f;
    
    @Test
    void testDefaultConstructor() {
        CombatComponent combat = new CombatComponent();
        
        assertEquals(10, combat.attackDamage, "Default attack damage should be 10");
        assertEquals(CombatComponent.ENEMY_ATTACK_COOLDOWN, combat.attackCooldown, EPSILON,
            "Default cooldown should match ENEMY_ATTACK_COOLDOWN");
        assertEquals(0f, combat.cooldownTimer, EPSILON, "Timer should start at 0");
    }
    
    @Test
    void testSingleArgConstructor() {
        CombatComponent combat = new CombatComponent(25);
        
        assertEquals(25, combat.attackDamage, "Attack damage should match constructor arg");
    }
    
    @Test
    void testTwoArgConstructor() {
        CombatComponent combat = new CombatComponent(15, 2.0f);
        
        assertEquals(15, combat.attackDamage, "Attack damage should match first arg");
        assertEquals(2.0f, combat.attackCooldown, EPSILON, "Cooldown should match second arg");
    }
    
    @Test
    void testCanAttackWhenReady() {
        CombatComponent combat = new CombatComponent();
        
        assertTrue(combat.canAttack(), "Should be able to attack when cooldownTimer is 0");
    }
    
    @Test
    void testCannotAttackOnCooldown() {
        CombatComponent combat = new CombatComponent();
        combat.cooldownTimer = 0.5f;
        
        assertFalse(combat.canAttack(), "Should not be able to attack when on cooldown");
    }
    
    @Test
    void testResetCooldown() {
        CombatComponent combat = new CombatComponent(10, 1.5f);
        
        combat.resetCooldown();
        
        assertEquals(1.5f, combat.cooldownTimer, EPSILON, 
            "Cooldown timer should be set to attackCooldown value");
        assertFalse(combat.canAttack(), "Should not be able to attack after reset");
    }
    
    @Test
    void testUpdateCooldownReducesTimer() {
        CombatComponent combat = new CombatComponent();
        combat.cooldownTimer = 1.0f;
        
        combat.updateCooldown(0.3f);
        
        assertEquals(0.7f, combat.cooldownTimer, EPSILON, "Timer should decrease by deltaTime");
    }
    
    @Test
    void testUpdateCooldownToZero() {
        CombatComponent combat = new CombatComponent();
        combat.cooldownTimer = 0.5f;
        
        combat.updateCooldown(1.0f);
        
        assertTrue(combat.cooldownTimer < 0, "Timer can go negative");
        assertTrue(combat.canAttack(), "Should be able to attack when timer is negative or zero");
    }
    
    @Test
    void testCooldownDoesNotDecreaseWhenZero() {
        CombatComponent combat = new CombatComponent();
        combat.cooldownTimer = 0f;
        
        combat.updateCooldown(0.5f);
        
        // Timer should remain at 0 since the conditional check prevents going lower
        assertEquals(0f, combat.cooldownTimer, EPSILON, 
            "Timer should remain at 0 when updateCooldown is called with timer already at 0");
        assertTrue(combat.canAttack(), "Should still be able to attack when timer is 0");
    }
    
    @Test
    void testFullCooldownCycle() {
        CombatComponent combat = new CombatComponent(10, 1.0f);
        
        // Initially can attack
        assertTrue(combat.canAttack());
        
        // Attack and reset cooldown
        combat.resetCooldown();
        assertFalse(combat.canAttack());
        
        // Partially through cooldown
        combat.updateCooldown(0.5f);
        assertFalse(combat.canAttack());
        
        // Complete cooldown
        combat.updateCooldown(0.5f);
        assertTrue(combat.canAttack());
    }
    
    @Test
    void testPlayerAttackDamageConstant() {
        assertEquals(10, CombatComponent.PLAYER_ATTACK_DAMAGE, 
            "Player attack damage should be 10");
    }
    
    @Test
    void testEnemyAttackDamageConstant() {
        assertEquals(5, CombatComponent.ENEMY_ATTACK_DAMAGE, 
            "Enemy attack damage should be 5");
    }
    
    @Test
    void testEnemyAttackCooldownConstant() {
        assertEquals(1.0f, CombatComponent.ENEMY_ATTACK_COOLDOWN, EPSILON,
            "Enemy attack cooldown should be 1.0 second");
    }
}
