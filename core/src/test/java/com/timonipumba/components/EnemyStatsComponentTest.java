package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.timonipumba.GameConstants;
import com.timonipumba.components.EnemyStatsComponent.EnemyType;

/**
 * Unit tests for EnemyStatsComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Type-based constructor and stat application
 * - Type parsing from strings
 * - Ranged enemy properties (RANGER type)
 * - Attack cooldown per enemy type
 * - Aggro radius per enemy type
 */
class EnemyStatsComponentTest {
    
    @Test
    void testDefaultConstructor() {
        EnemyStatsComponent stats = new EnemyStatsComponent();
        
        assertEquals(GameConstants.ENEMY_DEFAULT_SPEED, stats.speed);
        assertEquals(GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE, stats.damage);
        assertEquals(GameConstants.ENEMY_DEFAULT_VISION_RANGE, stats.visionRange);
        assertEquals(EnemyType.DEFAULT, stats.enemyType);
        assertFalse(stats.isRanged, "Default enemy should not be ranged");
        assertEquals(GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.ENEMY_DEFAULT_VISION_RANGE, stats.aggroRadius);
    }
    
    @Test
    void testBruteTypeConstructor() {
        EnemyStatsComponent stats = new EnemyStatsComponent(EnemyType.BRUTE);
        
        assertEquals(GameConstants.BRUTE_SPEED, stats.speed);
        assertEquals(GameConstants.BRUTE_ATTACK_DAMAGE, stats.damage);
        assertEquals(GameConstants.BRUTE_VISION_RANGE, stats.visionRange);
        assertEquals(GameConstants.BRUTE_MAX_HEALTH, stats.maxHealth);
        assertEquals(EnemyType.BRUTE, stats.enemyType);
        assertFalse(stats.isRanged, "Brute should not be ranged");
        assertEquals(GameConstants.BRUTE_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.BRUTE_AGGRO_RADIUS, stats.aggroRadius);
    }
    
    @Test
    void testScoutTypeConstructor() {
        EnemyStatsComponent stats = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertEquals(GameConstants.SCOUT_SPEED, stats.speed);
        assertEquals(GameConstants.SCOUT_ATTACK_DAMAGE, stats.damage);
        assertEquals(GameConstants.SCOUT_VISION_RANGE, stats.visionRange);
        assertEquals(GameConstants.SCOUT_MAX_HEALTH, stats.maxHealth);
        assertEquals(EnemyType.SCOUT, stats.enemyType);
        assertFalse(stats.isRanged, "Scout should not be ranged");
        assertEquals(GameConstants.SCOUT_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.SCOUT_AGGRO_RADIUS, stats.aggroRadius);
    }
    
    @Test
    void testRangerTypeConstructor() {
        EnemyStatsComponent stats = new EnemyStatsComponent(EnemyType.RANGER);
        
        assertEquals(GameConstants.RANGER_SPEED, stats.speed);
        assertEquals(GameConstants.RANGER_ATTACK_DAMAGE, stats.damage);
        assertEquals(GameConstants.RANGER_VISION_RANGE, stats.visionRange);
        assertEquals(GameConstants.RANGER_MAX_HEALTH, stats.maxHealth);
        assertEquals(GameConstants.RANGER_PREFERRED_DISTANCE, stats.preferredDistance);
        assertEquals(EnemyType.RANGER, stats.enemyType);
        assertTrue(stats.isRanged, "Ranger should be ranged");
        assertEquals(GameConstants.RANGER_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.RANGER_AGGRO_RADIUS, stats.aggroRadius);
    }
    
    @Test
    void testDefaultTypeConstructor() {
        EnemyStatsComponent stats = new EnemyStatsComponent(EnemyType.DEFAULT);
        
        assertEquals(GameConstants.ENEMY_DEFAULT_SPEED, stats.speed);
        assertEquals(GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE, stats.damage);
        assertEquals(GameConstants.ENEMY_DEFAULT_VISION_RANGE, stats.visionRange);
        assertEquals(GameConstants.ENEMY_DEFAULT_MAX_HEALTH, stats.maxHealth);
        assertEquals(EnemyType.DEFAULT, stats.enemyType);
        assertFalse(stats.isRanged, "Default should not be ranged");
        assertEquals(GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN, stats.attackCooldown);
    }
    
    @Test
    void testApplyTypeStats() {
        EnemyStatsComponent stats = new EnemyStatsComponent();
        
        // Start with default, then apply brute
        stats.applyTypeStats(EnemyType.BRUTE);
        assertEquals(GameConstants.BRUTE_SPEED, stats.speed);
        assertEquals(EnemyType.BRUTE, stats.enemyType);
        assertEquals(GameConstants.BRUTE_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.BRUTE_AGGRO_RADIUS, stats.aggroRadius);
        
        // Apply scout
        stats.applyTypeStats(EnemyType.SCOUT);
        assertEquals(GameConstants.SCOUT_SPEED, stats.speed);
        assertEquals(EnemyType.SCOUT, stats.enemyType);
        assertEquals(GameConstants.SCOUT_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.SCOUT_AGGRO_RADIUS, stats.aggroRadius);
        
        // Apply ranger
        stats.applyTypeStats(EnemyType.RANGER);
        assertEquals(GameConstants.RANGER_SPEED, stats.speed);
        assertEquals(EnemyType.RANGER, stats.enemyType);
        assertTrue(stats.isRanged, "Should be ranged after applying RANGER");
        assertEquals(GameConstants.RANGER_ATTACK_COOLDOWN, stats.attackCooldown);
        assertEquals(GameConstants.RANGER_AGGRO_RADIUS, stats.aggroRadius);
    }
    
    @Test
    void testParseTypeBrute() {
        assertEquals(EnemyType.BRUTE, EnemyStatsComponent.parseType("brute"));
        assertEquals(EnemyType.BRUTE, EnemyStatsComponent.parseType("BRUTE"));
        assertEquals(EnemyType.BRUTE, EnemyStatsComponent.parseType("Brute"));
        assertEquals(EnemyType.BRUTE, EnemyStatsComponent.parseType("  brute  "));
    }
    
    @Test
    void testParseTypeScout() {
        assertEquals(EnemyType.SCOUT, EnemyStatsComponent.parseType("scout"));
        assertEquals(EnemyType.SCOUT, EnemyStatsComponent.parseType("SCOUT"));
        assertEquals(EnemyType.SCOUT, EnemyStatsComponent.parseType("Scout"));
    }
    
    @Test
    void testParseTypeRanger() {
        assertEquals(EnemyType.RANGER, EnemyStatsComponent.parseType("ranger"));
        assertEquals(EnemyType.RANGER, EnemyStatsComponent.parseType("RANGER"));
        assertEquals(EnemyType.RANGER, EnemyStatsComponent.parseType("Ranger"));
        assertEquals(EnemyType.RANGER, EnemyStatsComponent.parseType("  ranger  "));
    }
    
    @Test
    void testParseTypeDefault() {
        assertEquals(EnemyType.DEFAULT, EnemyStatsComponent.parseType(null));
        assertEquals(EnemyType.DEFAULT, EnemyStatsComponent.parseType(""));
        assertEquals(EnemyType.DEFAULT, EnemyStatsComponent.parseType("unknown"));
        assertEquals(EnemyType.DEFAULT, EnemyStatsComponent.parseType("standard"));
    }
    
    @Test
    void testBruteIsSlowerThanScout() {
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertTrue(brute.speed < scout.speed, 
            "Brute should be slower than scout");
    }
    
    @Test
    void testBruteHasMoreHealthThanScout() {
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertTrue(brute.maxHealth > scout.maxHealth, 
            "Brute should have more health than scout");
    }
    
    @Test
    void testRangerHasLargestVisionRange() {
        EnemyStatsComponent defaultEnemy = new EnemyStatsComponent(EnemyType.DEFAULT);
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        EnemyStatsComponent ranger = new EnemyStatsComponent(EnemyType.RANGER);
        
        assertTrue(ranger.visionRange > defaultEnemy.visionRange, 
            "Ranger should have larger vision than default");
        assertTrue(ranger.visionRange > brute.visionRange, 
            "Ranger should have larger vision than brute");
        assertTrue(ranger.visionRange > scout.visionRange, 
            "Ranger should have larger vision than scout");
    }
    
    @Test
    void testRangerPreferredDistance() {
        EnemyStatsComponent ranger = new EnemyStatsComponent(EnemyType.RANGER);
        
        assertTrue(ranger.preferredDistance > 0, 
            "Ranger should have positive preferred distance");
        assertTrue(ranger.preferredDistance < ranger.visionRange, 
            "Preferred distance should be less than vision range");
    }
    
    @Test
    void testMeleeEnemiesHaveNoPreferredDistance() {
        EnemyStatsComponent defaultEnemy = new EnemyStatsComponent(EnemyType.DEFAULT);
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertEquals(0f, defaultEnemy.preferredDistance, 0.001f);
        assertEquals(0f, brute.preferredDistance, 0.001f);
        assertEquals(0f, scout.preferredDistance, 0.001f);
    }
    
    @Test
    void testScoutHasFasterAttackCooldownThanBrute() {
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertTrue(scout.attackCooldown < brute.attackCooldown, 
            "Scout should have faster (shorter) attack cooldown than brute");
    }
    
    @Test
    void testScoutHasLargerAggroRadiusThanBrute() {
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertTrue(scout.aggroRadius > brute.aggroRadius, 
            "Scout should have larger aggro radius than brute");
    }
    
    @Test
    void testBruteHasHigherDamageThanScout() {
        EnemyStatsComponent brute = new EnemyStatsComponent(EnemyType.BRUTE);
        EnemyStatsComponent scout = new EnemyStatsComponent(EnemyType.SCOUT);
        
        assertTrue(brute.damage > scout.damage, 
            "Brute should deal more damage than scout");
    }
}
