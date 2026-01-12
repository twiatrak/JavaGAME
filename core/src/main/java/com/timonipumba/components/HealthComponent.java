package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for entities that can take damage and be killed.
 * 
 * Default values:
 * - Player: maxHealth = 100, currentHealth = 100
 * - Enemy: maxHealth = 30, currentHealth = 30
 */
public class HealthComponent implements Component {
    /** Maximum health for this entity */
    public int maxHealth = 100;
    
    /** Current health for this entity */
    public int currentHealth = 100;
    
    /** Whether the entity is still alive (currentHealth > 0) */
    public boolean alive = true;
    
    public HealthComponent() {}
    
    public HealthComponent(int maxHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
        this.alive = true;
    }
    
    public HealthComponent(int maxHealth, int currentHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = currentHealth;
        this.alive = currentHealth > 0;
    }
    
    /**
     * Apply damage to this entity.
     * @param damage Amount of damage to apply (positive value)
     */
    public void takeDamage(int damage) {
        currentHealth -= damage;
        if (currentHealth <= 0) {
            currentHealth = 0;
            alive = false;
        }
    }
    
    /**
     * Heal this entity.
     * @param amount Amount to heal (positive value)
     */
    public void heal(int amount) {
        if (!alive) return;
        currentHealth += amount;
        if (currentHealth > maxHealth) {
            currentHealth = maxHealth;
        }
    }
    
    /**
     * Check if entity is dead.
     * @return true if currentHealth <= 0
     */
    public boolean isDead() {
        return !alive || currentHealth <= 0;
    }
}
