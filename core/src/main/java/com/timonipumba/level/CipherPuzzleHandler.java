package com.timonipumba.level;

/**
 * Handler for cipher-type puzzles.
 * 
 * Validates answers against the expected solution stored in puzzle data.
 * Supports case-insensitive comparison with whitespace normalization.
 */
public class CipherPuzzleHandler implements PuzzleHandler {
    
    @Override
    public String getPuzzleType() {
        return "cipher";
    }
    
    @Override
    public boolean checkAnswer(Puzzle puzzle, String playerAnswer) {
        if (puzzle == null || puzzle.data == null) {
            return false;
        }
        
        String expectedAnswer = puzzle.getData("answer");
        if (expectedAnswer == null) {
            return false;
        }
        
        String normalizedPlayer = normalizeAnswer(playerAnswer);
        String normalizedExpected = normalizeAnswer(expectedAnswer);
        
        return normalizedPlayer.equals(normalizedExpected);
    }
    
    @Override
    public String normalizeAnswer(String answer) {
        if (answer == null) return "";
        // Remove whitespace and convert to uppercase for cipher puzzles
        return answer.replaceAll("\\s+", "").toUpperCase();
    }
}
