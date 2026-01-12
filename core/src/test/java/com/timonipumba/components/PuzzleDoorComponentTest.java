package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.timonipumba.level.Puzzle;

/**
 * Unit tests for PuzzleDoorComponent.
 */
class PuzzleDoorComponentTest {
    
    @Test
    void testDefaultConstructor() {
        PuzzleDoorComponent door = new PuzzleDoorComponent();
        assertEquals("", door.id);
        assertNull(door.puzzle);
        assertTrue(door.locked);
        assertFalse(door.hasPuzzle());
        assertNull(door.getPuzzleId());
    }
    
    @Test
    void testConstructorWithIdAndPuzzle() {
        Map<String, String> data = new HashMap<>();
        data.put("answer", "TEST");
        Puzzle puzzle = new Puzzle("puzzle1", "cipher", data);
        
        PuzzleDoorComponent door = new PuzzleDoorComponent("door1", puzzle);
        
        assertEquals("door1", door.id);
        assertEquals(puzzle, door.puzzle);
        assertTrue(door.locked);
        assertTrue(door.hasPuzzle());
        assertEquals("puzzle1", door.getPuzzleId());
    }
    
    @Test
    void testConstructorWithLockedState() {
        Puzzle puzzle = new Puzzle("puzzle1", "cipher", new HashMap<>());
        
        PuzzleDoorComponent lockedDoor = new PuzzleDoorComponent("door1", puzzle, true);
        assertTrue(lockedDoor.locked);
        
        PuzzleDoorComponent unlockedDoor = new PuzzleDoorComponent("door2", puzzle, false);
        assertFalse(unlockedDoor.locked);
    }
    
    @Test
    void testUnlock() {
        Puzzle puzzle = new Puzzle("puzzle1", "cipher", new HashMap<>());
        PuzzleDoorComponent door = new PuzzleDoorComponent("door1", puzzle, true);
        
        assertTrue(door.locked);
        door.unlock();
        assertFalse(door.locked);
    }
    
    @Test
    void testLock() {
        Puzzle puzzle = new Puzzle("puzzle1", "cipher", new HashMap<>());
        PuzzleDoorComponent door = new PuzzleDoorComponent("door1", puzzle, false);
        
        assertFalse(door.locked);
        door.lock();
        assertTrue(door.locked);
    }
    
    @Test
    void testHasPuzzle() {
        PuzzleDoorComponent doorWithoutPuzzle = new PuzzleDoorComponent("door1", null);
        assertFalse(doorWithoutPuzzle.hasPuzzle());
        assertNull(doorWithoutPuzzle.getPuzzleId());
        
        Puzzle puzzle = new Puzzle("puzzle1", "cipher", new HashMap<>());
        PuzzleDoorComponent doorWithPuzzle = new PuzzleDoorComponent("door2", puzzle);
        assertTrue(doorWithPuzzle.hasPuzzle());
        assertEquals("puzzle1", doorWithPuzzle.getPuzzleId());
    }
    
    @Test
    void testDimensionsStored() {
        PuzzleDoorComponent door = new PuzzleDoorComponent();
        
        assertFalse(door.dimensionsStored);
        assertEquals(0f, door.originalWidth);
        assertEquals(0f, door.originalHeight);
        
        door.originalWidth = 64f;
        door.originalHeight = 80f;
        door.dimensionsStored = true;
        
        assertTrue(door.dimensionsStored);
        assertEquals(64f, door.originalWidth);
        assertEquals(80f, door.originalHeight);
    }
}
