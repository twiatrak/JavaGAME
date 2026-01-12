package com.timonipumba.level;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of puzzle handlers by type.
 * 
 * Allows type-dispatch for different puzzle types without
 * hardcoding type logic throughout the codebase.
 */
public class PuzzleHandlerRegistry {
    
    private static final Map<String, PuzzleHandler> handlers = new HashMap<>();
    
    static {
        // Register built-in handlers
        registerHandler(new CipherPuzzleHandler());
    }
    
    /**
     * Register a puzzle handler.
     * 
     * @param handler The handler to register
     */
    public static void registerHandler(PuzzleHandler handler) {
        if (handler != null && handler.getPuzzleType() != null) {
            handlers.put(handler.getPuzzleType().toLowerCase(), handler);
        }
    }
    
    /**
     * Get a handler for the given puzzle type.
     * 
     * @param puzzleType The puzzle type
     * @return The handler, or null if not found
     */
    public static PuzzleHandler getHandler(String puzzleType) {
        if (puzzleType == null) return null;
        return handlers.get(puzzleType.toLowerCase());
    }
    
    /**
     * Check an answer using the appropriate handler.
     * 
     * @param puzzle The puzzle to check
     * @param playerAnswer The player's answer
     * @return true if correct, false if incorrect or no handler found
     */
    public static boolean checkAnswer(Puzzle puzzle, String playerAnswer) {
        if (puzzle == null || puzzle.type == null) {
            return false;
        }
        
        PuzzleHandler handler = getHandler(puzzle.type);
        if (handler == null) {
            System.err.println("PuzzleHandlerRegistry: No handler for puzzle type: " + puzzle.type);
            return false;
        }
        
        return handler.checkAnswer(puzzle, playerAnswer);
    }
}
