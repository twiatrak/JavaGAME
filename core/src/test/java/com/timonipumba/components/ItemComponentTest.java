package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.timonipumba.GameConstants;

/**
 * Unit tests for ItemComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Type-specific constructor
 * - Heal amount constructor
 */
class ItemComponentTest {
    
    @Test
    void testDefaultConstructor() {
        ItemComponent item = new ItemComponent();
        
        assertEquals(ItemType.POTION, item.itemType, "Default item type should be POTION");
        assertEquals(GameConstants.POTION_HEAL_AMOUNT, item.healAmount, 
            "Default heal amount should match GameConstants");
    }
    
    @Test
    void testTypeConstructor() {
        ItemComponent potion = new ItemComponent(ItemType.POTION);
        assertEquals(ItemType.POTION, potion.itemType);
        
        ItemComponent buff = new ItemComponent(ItemType.BUFF);
        assertEquals(ItemType.BUFF, buff.itemType);
        
        ItemComponent treasure = new ItemComponent(ItemType.TREASURE);
        assertEquals(ItemType.TREASURE, treasure.itemType);
    }
    
    @Test
    void testCustomHealAmount() {
        ItemComponent item = new ItemComponent(ItemType.POTION, 50);
        
        assertEquals(ItemType.POTION, item.itemType);
        assertEquals(50, item.healAmount);
    }
    
    @Test
    void testItemTypes() {
        // Ensure all item types are defined
        assertEquals(3, ItemType.values().length, 
            "Should have exactly 3 item types: POTION, BUFF, TREASURE");
        
        assertNotNull(ItemType.POTION);
        assertNotNull(ItemType.BUFF);
        assertNotNull(ItemType.TREASURE);
    }
}
