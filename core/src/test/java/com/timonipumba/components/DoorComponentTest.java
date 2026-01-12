package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DoorComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Custom constructors
 * - Property storage
 * - dimensionsStored flag
 */
class DoorComponentTest {
    
    @Test
    void testDefaultConstructor() {
        DoorComponent door = new DoorComponent();
        
        assertEquals("", door.group, "Default group should be empty string");
        assertFalse(door.open, "Default open state should be false");
        assertEquals(0f, door.originalWidth, "Default original width should be 0");
        assertEquals(0f, door.originalHeight, "Default original height should be 0");
        assertFalse(door.dimensionsStored, "Default dimensionsStored should be false");
    }
    
    @Test
    void testGroupConstructor() {
        DoorComponent door = new DoorComponent("A");
        
        assertEquals("A", door.group);
        assertFalse(door.open, "Default open state should be false");
        assertFalse(door.dimensionsStored);
    }
    
    @Test
    void testGroupAndOpenConstructor() {
        DoorComponent door = new DoorComponent("B", true);
        
        assertEquals("B", door.group);
        assertTrue(door.open, "Open state should be true when constructed with true");
    }
    
    @Test
    void testGroupAndClosedConstructor() {
        DoorComponent door = new DoorComponent("C", false);
        
        assertEquals("C", door.group);
        assertFalse(door.open);
    }
    
    @Test
    void testOriginalDimensionsStorage() {
        DoorComponent door = new DoorComponent("A");
        
        // Simulate TiledMapLoader setting original dimensions
        door.originalWidth = 16f;
        door.originalHeight = 16f;
        door.dimensionsStored = true;
        
        assertEquals(16f, door.originalWidth);
        assertEquals(16f, door.originalHeight);
        assertTrue(door.dimensionsStored);
    }
    
    @Test
    void testZeroDimensionsWithFlag() {
        DoorComponent door = new DoorComponent("A");
        
        // Test that zero dimensions can be stored when using the flag
        door.originalWidth = 0f;
        door.originalHeight = 0f;
        door.dimensionsStored = true;
        
        assertTrue(door.dimensionsStored, "Flag should be set even with zero dimensions");
        assertEquals(0f, door.originalWidth);
        assertEquals(0f, door.originalHeight);
    }
    
    @Test
    void testOpenStateModification() {
        DoorComponent door = new DoorComponent("A", false);
        
        assertFalse(door.open);
        
        door.open = true;
        assertTrue(door.open);
        
        door.open = false;
        assertFalse(door.open);
    }
}
