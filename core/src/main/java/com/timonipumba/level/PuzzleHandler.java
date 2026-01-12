package com.timonipumba.level;

/**
 * Interface for puzzle answer validation.
 * 
 * Different puzzle types (cipher, binary, hex, etc.) implement this
 * interface to provide type-specific answer checking.
 */
public interface PuzzleHandler {
    
    /**
     * Get the puzzle type this handler supports.
     * 
     * @return The puzzle type string (e.g., "cipher", "binary")
     */
    String getPuzzleType();
    
    /**
     * Check if the player's answer is correct.
     * 
     * @param puzzle The puzzle to check
     * @param playerAnswer The player's submitted answer
     * @return true if the answer is correct
     */
    boolean checkAnswer(Puzzle puzzle, String playerAnswer);
    
    /**
     * Normalize a player answer for comparison.
     * 
     * @param answer The raw player input
     * @return The normalized answer (e.g., trimmed, uppercased)
     */
    default String normalizeAnswer(String answer) {
        if (answer == null) return "";
        return answer.trim().toUpperCase();
    }
}
