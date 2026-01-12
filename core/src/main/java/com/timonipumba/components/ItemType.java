package com.timonipumba.components;

/**
 * Types of items that can be picked up in the game.
 * 
 * Item Types:
 * - POTION: Restores health when picked up
 * - BUFF: Grants temporary stat boost (reserved for future)
 * - TREASURE: Score/loot item (reserved for future)
 */
public enum ItemType {
    /** Healing item - restores health to the player */
    POTION,
    
    /** Buff item - grants temporary stat boost (future feature) */
    BUFF,
    
    /** Treasure item - score/loot (future feature) */
    TREASURE
}
