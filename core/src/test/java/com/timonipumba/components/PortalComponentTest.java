package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalComponent.
 */
class PortalComponentTest {
    
    @Test
    void testDefaultConstructor() {
        PortalComponent portal = new PortalComponent();
        
        assertNull(portal.puzzleId);
        assertFalse(portal.active);
        assertNull(portal.targetMap);
        assertEquals(0, portal.segmentIndex);
        assertEquals(1, portal.segmentLength);
        assertNull(portal.portalGroupId);
    }
    
    @Test
    void testPuzzleIdConstructor() {
        PortalComponent portal = new PortalComponent("test_puzzle");
        
        assertEquals("test_puzzle", portal.puzzleId);
        assertFalse(portal.active);
        assertTrue(portal.hasPuzzle());
    }
    
    @Test
    void testFullConstructor() {
        PortalComponent portal = new PortalComponent("puzzle1", "group1", 2, 5);
        
        assertEquals("puzzle1", portal.puzzleId);
        assertEquals("group1", portal.portalGroupId);
        assertEquals(2, portal.segmentIndex);
        assertEquals(5, portal.segmentLength);
        assertFalse(portal.active);
    }
    
    @Test
    void testActivate() {
        PortalComponent portal = new PortalComponent("puzzle1");
        
        assertFalse(portal.isActive());
        
        portal.activate();
        
        assertTrue(portal.isActive());
    }
    
    @Test
    void testHasPuzzle_WithPuzzle() {
        PortalComponent portal = new PortalComponent("my_puzzle");
        assertTrue(portal.hasPuzzle());
    }
    
    @Test
    void testHasPuzzle_NullPuzzle() {
        PortalComponent portal = new PortalComponent();
        assertFalse(portal.hasPuzzle());
    }
    
    @Test
    void testHasPuzzle_EmptyPuzzle() {
        PortalComponent portal = new PortalComponent("");
        assertFalse(portal.hasPuzzle());
    }
    
    @Test
    void testTargetMapModification() {
        PortalComponent portal = new PortalComponent();
        
        assertNull(portal.targetMap);
        
        portal.targetMap = "maps/next_level.tmx";
        assertEquals("maps/next_level.tmx", portal.targetMap);
    }
}
