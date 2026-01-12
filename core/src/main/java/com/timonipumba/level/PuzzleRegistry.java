package com.timonipumba.level;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for storing and looking up puzzles by ID.
 * 
 * Puzzles can be registered from:
 * - TMX door object properties (puzzle defined inline in TMX)
 * - External JSON level files
 * - Programmatic registration
 * 
 * This allows puzzle data to be resolved by puzzleId when a door
 * only references a puzzle by ID without inline definition.
 */
public class PuzzleRegistry {
    
    private static final Map<String, Puzzle> puzzles = new HashMap<>();
    
    /**
     * Register a puzzle.
     * 
     * @param puzzle The puzzle to register (must have a non-null puzzleId)
     */
    public static void register(Puzzle puzzle) {
        if (puzzle != null && puzzle.puzzleId != null && !puzzle.puzzleId.isEmpty()) {
            puzzles.put(puzzle.puzzleId, puzzle);
        }
    }
    
    /**
     * Get a puzzle by its ID.
     * 
     * @param puzzleId The puzzle ID
     * @return The puzzle, or null if not found
     */
    public static Puzzle get(String puzzleId) {
        if (puzzleId == null) return null;
        return puzzles.get(puzzleId);
    }
    
    /**
     * Check if a puzzle is registered.
     * 
     * @param puzzleId The puzzle ID
     * @return true if the puzzle exists in the registry
     */
    public static boolean contains(String puzzleId) {
        return puzzleId != null && puzzles.containsKey(puzzleId);
    }
    
    /**
     * Remove a puzzle from the registry.
     * 
     * @param puzzleId The puzzle ID to remove
     */
    public static void remove(String puzzleId) {
        if (puzzleId != null) {
            puzzles.remove(puzzleId);
        }
    }
    
    /**
     * Clear all registered puzzles.
     * Useful when loading a new level or during testing.
     */
    public static void clear() {
        puzzles.clear();
    }
    
    /**
     * Get the number of registered puzzles.
     * 
     * @return The count of puzzles in the registry
     */
    public static int size() {
        return puzzles.size();
    }
}
