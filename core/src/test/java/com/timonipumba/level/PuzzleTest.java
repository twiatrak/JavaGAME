package com.timonipumba.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for Puzzle data model and PuzzleHandlerRegistry.
 */
class PuzzleTest {
    
    @Test
    void testDefaultConstructor() {
        Puzzle puzzle = new Puzzle();
        assertNull(puzzle.puzzleId);
        assertNull(puzzle.type);
        assertNull(puzzle.data);
    }
    
    @Test
    void testConstructor() {
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        
        Puzzle puzzle = new Puzzle("test_id", "cipher", data);
        
        assertEquals("test_id", puzzle.puzzleId);
        assertEquals("cipher", puzzle.type);
        assertNotNull(puzzle.data);
        assertEquals("value1", puzzle.data.get("key1"));
    }
    
    @Test
    void testGetPuzzleId() {
        Puzzle puzzle = new Puzzle("my_puzzle", "cipher", new HashMap<>());
        assertEquals("my_puzzle", puzzle.getPuzzleId());
    }
    
    @Test
    void testGetType() {
        Puzzle puzzle = new Puzzle("my_puzzle", "cipher", new HashMap<>());
        assertEquals("cipher", puzzle.getType());
    }
    
    @Test
    void testGetData() {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "HELLO");
        data.put("hint", "A greeting");
        
        Puzzle puzzle = new Puzzle("p1", "cipher", data);
        
        assertEquals("HELLO", puzzle.getData("answer"));
        assertEquals("A greeting", puzzle.getData("hint"));
        assertNull(puzzle.getData("missing"));
    }
    
    @Test
    void testGetDataWithDefault() {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "HELLO");
        
        Puzzle puzzle = new Puzzle("p1", "cipher", data);
        
        assertEquals("HELLO", puzzle.getData("answer", "DEFAULT"));
        assertEquals("DEFAULT", puzzle.getData("missing", "DEFAULT"));
    }
    
    @Test
    void testGetDataNullData() {
        Puzzle puzzle = new Puzzle();
        puzzle.data = null;
        
        assertNull(puzzle.getData("anything"));
        assertEquals("default", puzzle.getData("anything", "default"));
    }
    
    @Test
    void testPuzzleHandlerRegistryCheckAnswer() {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "CORRECT");
        
        Puzzle puzzle = new Puzzle("p1", "cipher", data);
        
        assertTrue(PuzzleHandlerRegistry.checkAnswer(puzzle, "CORRECT"));
        assertTrue(PuzzleHandlerRegistry.checkAnswer(puzzle, "correct"));
        assertFalse(PuzzleHandlerRegistry.checkAnswer(puzzle, "wrong"));
    }
    
    @Test
    void testPuzzleHandlerRegistryUnknownType() {
        Puzzle puzzle = new Puzzle("p1", "unknown_type", new HashMap<>());
        assertFalse(PuzzleHandlerRegistry.checkAnswer(puzzle, "answer"));
    }
    
    @Test
    void testPuzzleHandlerRegistryNullPuzzle() {
        assertFalse(PuzzleHandlerRegistry.checkAnswer(null, "answer"));
    }
}
