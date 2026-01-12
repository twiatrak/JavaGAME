package com.timonipumba.level;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for PuzzleRegistry.
 */
class PuzzleRegistryTest {
    
    @BeforeEach
    void setUp() {
        // Clear registry before each test
        PuzzleRegistry.clear();
    }
    
    @Test
    void testRegisterAndGet() {
        Puzzle puzzle = createTestPuzzle("test_puzzle");
        
        PuzzleRegistry.register(puzzle);
        
        assertEquals(puzzle, PuzzleRegistry.get("test_puzzle"));
    }
    
    @Test
    void testContains() {
        Puzzle puzzle = createTestPuzzle("test_puzzle");
        
        assertFalse(PuzzleRegistry.contains("test_puzzle"));
        
        PuzzleRegistry.register(puzzle);
        
        assertTrue(PuzzleRegistry.contains("test_puzzle"));
    }
    
    @Test
    void testRemove() {
        Puzzle puzzle = createTestPuzzle("test_puzzle");
        PuzzleRegistry.register(puzzle);
        
        assertTrue(PuzzleRegistry.contains("test_puzzle"));
        
        PuzzleRegistry.remove("test_puzzle");
        
        assertFalse(PuzzleRegistry.contains("test_puzzle"));
        assertNull(PuzzleRegistry.get("test_puzzle"));
    }
    
    @Test
    void testClear() {
        PuzzleRegistry.register(createTestPuzzle("puzzle1"));
        PuzzleRegistry.register(createTestPuzzle("puzzle2"));
        
        assertEquals(2, PuzzleRegistry.size());
        
        PuzzleRegistry.clear();
        
        assertEquals(0, PuzzleRegistry.size());
    }
    
    @Test
    void testGetNonExistent() {
        assertNull(PuzzleRegistry.get("nonexistent"));
    }
    
    @Test
    void testGetNull() {
        assertNull(PuzzleRegistry.get(null));
    }
    
    @Test
    void testRegisterNullPuzzle() {
        PuzzleRegistry.register(null);
        assertEquals(0, PuzzleRegistry.size());
    }
    
    @Test
    void testRegisterPuzzleWithNullId() {
        Puzzle puzzle = new Puzzle();
        puzzle.puzzleId = null;
        
        PuzzleRegistry.register(puzzle);
        assertEquals(0, PuzzleRegistry.size());
    }
    
    @Test
    void testRegisterPuzzleWithEmptyId() {
        Puzzle puzzle = new Puzzle();
        puzzle.puzzleId = "";
        
        PuzzleRegistry.register(puzzle);
        assertEquals(0, PuzzleRegistry.size());
    }
    
    @Test
    void testRegisterOverwrites() {
        Puzzle puzzle1 = createTestPuzzle("test_puzzle");
        puzzle1.data.put("answer", "FIRST");
        
        Puzzle puzzle2 = createTestPuzzle("test_puzzle");
        puzzle2.data.put("answer", "SECOND");
        
        PuzzleRegistry.register(puzzle1);
        PuzzleRegistry.register(puzzle2);
        
        Puzzle retrieved = PuzzleRegistry.get("test_puzzle");
        assertEquals("SECOND", retrieved.getData("answer"));
    }
    
    private Puzzle createTestPuzzle(String puzzleId) {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "TESTANSWER");
        data.put("ciphertext", "ENCRYPTED");
        return new Puzzle(puzzleId, "cipher", data);
    }
}
