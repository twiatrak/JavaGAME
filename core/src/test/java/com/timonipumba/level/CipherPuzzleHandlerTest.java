package com.timonipumba.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for CipherPuzzleHandler.
 */
class CipherPuzzleHandlerTest {
    
    private CipherPuzzleHandler handler = new CipherPuzzleHandler();
    
    @Test
    void testGetPuzzleType() {
        assertEquals("cipher", handler.getPuzzleType());
    }
    
    @Test
    void testCheckAnswerCorrect() {
        Puzzle puzzle = createCipherPuzzle("ACCESSOK");
        
        assertTrue(handler.checkAnswer(puzzle, "ACCESSOK"));
        assertTrue(handler.checkAnswer(puzzle, "accessok")); // case insensitive
        assertTrue(handler.checkAnswer(puzzle, "ACCESS OK")); // spaces removed
        assertTrue(handler.checkAnswer(puzzle, "  accessok  ")); // trimmed
    }
    
    @Test
    void testCheckAnswerIncorrect() {
        Puzzle puzzle = createCipherPuzzle("ACCESSOK");
        
        assertFalse(handler.checkAnswer(puzzle, "WRONGANSWER"));
        assertFalse(handler.checkAnswer(puzzle, "ACCESS"));
        assertFalse(handler.checkAnswer(puzzle, ""));
    }
    
    @Test
    void testCheckAnswerNullPuzzle() {
        assertFalse(handler.checkAnswer(null, "ACCESSOK"));
    }
    
    @Test
    void testCheckAnswerNullAnswer() {
        Puzzle puzzle = createCipherPuzzle("ACCESSOK");
        assertFalse(handler.checkAnswer(puzzle, null));
    }
    
    @Test
    void testCheckAnswerEmptyAnswer() {
        Puzzle puzzle = createCipherPuzzle("ACCESSOK");
        assertFalse(handler.checkAnswer(puzzle, ""));
    }
    
    @Test
    void testNormalizeAnswer() {
        assertEquals("HELLO", handler.normalizeAnswer("hello"));
        assertEquals("HELLO", handler.normalizeAnswer("  hello  "));
        assertEquals("HELLOWORLD", handler.normalizeAnswer("hello world"));
        assertEquals("", handler.normalizeAnswer(null));
        assertEquals("", handler.normalizeAnswer(""));
    }
    
    private Puzzle createCipherPuzzle(String answer) {
        Map<String, String> data = new HashMap<>();
        data.put("answer", answer);
        data.put("ciphertext", "TEST");
        return new Puzzle("test_puzzle", "cipher", data);
    }
}
