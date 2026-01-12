package com.timonipumba.level;

import java.util.Map;

/**
 * Represents a puzzle within a level.
 * 
 * Puzzles are type-dispatched (e.g., "cipher") and contain
 * a data block with type-specific configuration.
 */
public class Puzzle {
    /** Unique identifier for this puzzle */
    public String puzzleId;
    
    /** The puzzle type (e.g., "cipher", "binary", "hex") */
    public String type;
    
    /** 
     * Type-specific data for the puzzle.
     * For cipher puzzles, this might include:
     * - prompt: The puzzle description
     * - ciphertext: The encoded text
     * - hint: Optional hint for the player
     * - answer: The expected answer
     */
    public Map<String, String> data;
    
    /** Default constructor for JSON deserialization */
    public Puzzle() {}
    
    /**
     * Create a new puzzle.
     * 
     * @param puzzleId Unique identifier
     * @param type The puzzle type
     * @param data Type-specific configuration data
     */
    public Puzzle(String puzzleId, String type, Map<String, String> data) {
        this.puzzleId = puzzleId;
        this.type = type;
        this.data = data;
    }
    
    /**
     * Get the puzzle's unique identifier.
     * 
     * @return The puzzle ID
     */
    public String getPuzzleId() {
        return puzzleId;
    }
    
    /**
     * Get the puzzle type.
     * 
     * @return The puzzle type (e.g., "cipher", "binary", "hex")
     */
    public String getType() {
        return type;
    }
    
    /**
     * Get a data value by key.
     * 
     * @param key The data key to look up
     * @return The value, or null if not found
     */
    public String getData(String key) {
        if (data == null) return null;
        return data.get(key);
    }
    
    /**
     * Get a data value with a default fallback.
     * 
     * @param key The data key to look up
     * @param defaultValue Value to return if key is not found
     * @return The value, or defaultValue if not found
     */
    public String getData(String key, String defaultValue) {
        if (data == null) return defaultValue;
        String value = data.get(key);
        return value != null ? value : defaultValue;
    }
}
