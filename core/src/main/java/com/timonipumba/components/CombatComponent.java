package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.GameConstants;

/**
 * Component for entities that can attack.
 * Contains attack damage values and cooldown timers.
 * 
 * Combat Constants (defined in GameConstants):
 * - PLAYER_ATTACK_DAMAGE = 10: Damage dealt when player bumps into enemy
 * - ENEMY_DEFAULT_ATTACK_DAMAGE = 5: Damage dealt when enemy contacts player
 * - ENEMY_DEFAULT_ATTACK_COOLDOWN = 1.0f: Seconds between enemy attacks
 */
public class CombatComponent implements Component {
    
    // Legacy constants - use GameConstants instead
    /** @deprecated Use GameConstants.PLAYER_ATTACK_DAMAGE instead */
    @Deprecated
    public static final int PLAYER_ATTACK_DAMAGE = GameConstants.PLAYER_ATTACK_DAMAGE;
    /** @deprecated Use GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE instead */
    @Deprecated
    public static final int ENEMY_ATTACK_DAMAGE = GameConstants.ENEMY_DEFAULT_ATTACK_DAMAGE;
    /** @deprecated Use GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN instead */
    @Deprecated
    public static final float ENEMY_ATTACK_COOLDOWN = GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN;
    
    /** Damage this entity deals per attack */
    public int attackDamage = GameConstants.PLAYER_ATTACK_DAMAGE;
    
    /** Time in seconds between attacks (only relevant for enemies) */
    public float attackCooldown = GameConstants.ENEMY_DEFAULT_ATTACK_COOLDOWN;
    
    /** Timer tracking time since last attack */
    public float cooldownTimer = 0f;
    
    public CombatComponent() {}
    
    public CombatComponent(int attackDamage) {
        this.attackDamage = attackDamage;
    }
    
    public CombatComponent(int attackDamage, float attackCooldown) {
        this.attackDamage = attackDamage;
        this.attackCooldown = attackCooldown;
    }
    
    /**
     * Check if this entity can attack (cooldown has passed).
     * @return true if ready to attack
     */
    public boolean canAttack() {
        return cooldownTimer <= 0f;
    }
    
    /**
     * Reset the attack cooldown after an attack.
     */
    public void resetCooldown() {
        cooldownTimer = attackCooldown;
    }
    
    /**
     * Update cooldown timer.
     * @param deltaTime Time elapsed since last update
     */
    public void updateCooldown(float deltaTime) {
        if (cooldownTimer > 0f) {
            cooldownTimer -= deltaTime;
        }
    }
}
