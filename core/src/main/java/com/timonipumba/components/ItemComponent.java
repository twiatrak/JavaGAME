package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.GameConstants;

/**
 * Component for items that can be picked up by the player.
 * 
 * Items are placed in the world with PositionComponent and RenderableComponent.
 * When the player overlaps an item entity, the PickupSystem processes it.
 * 
 * Properties:
 * - itemType: Determines what happens when the item is picked up
 * - healAmount: For POTION type, amount of health restored
 */
public class ItemComponent implements Component {
    
    /** The type of this item (determines pickup behavior) */
    public ItemType itemType = ItemType.POTION;
    
    /** Amount of health restored (for POTION type) */
    public int healAmount = GameConstants.POTION_HEAL_AMOUNT;
    
    public ItemComponent() {}
    
    public ItemComponent(ItemType itemType) {
        this.itemType = itemType;
    }
    
    public ItemComponent(ItemType itemType, int healAmount) {
        this.itemType = itemType;
        this.healAmount = healAmount;
    }
}
