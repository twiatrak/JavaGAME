package com.timonipumba.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GateComponent.
 */
class GateComponentTest {
    
    private GateComponent gate;
    
    @BeforeEach
    void setUp() {
        gate = new GateComponent("arena_1", "arena_2");
    }
    
    @Test
    void testInitialState() {
        assertTrue(gate.isClosed(), "Gate should start CLOSED");
        assertFalse(gate.isOpening(), "Gate should not be OPENING initially");
        assertFalse(gate.isOpen(), "Gate should not be OPEN initially");
        assertEquals(GateComponent.GateState.CLOSED, gate.state);
    }
    
    @Test
    void testArenaIds() {
        assertEquals("arena_1", gate.sourceArenaId);
        assertEquals("arena_2", gate.targetArenaId);
    }
    
    @Test
    void testSingleArenaConstructor() {
        GateComponent singleGate = new GateComponent("only_arena");
        assertEquals("only_arena", singleGate.sourceArenaId);
        assertNull(singleGate.targetArenaId);
    }
    
    @Test
    void testDefaultConstructor() {
        GateComponent defaultGate = new GateComponent();
        assertNull(defaultGate.sourceArenaId);
        assertNull(defaultGate.targetArenaId);
        assertTrue(defaultGate.isClosed());
    }
    
    @Test
    void testStartOpening() {
        gate.startOpening();
        
        assertTrue(gate.isOpening(), "Gate should be OPENING after startOpening()");
        assertFalse(gate.isClosed(), "Gate should not be CLOSED after startOpening()");
        assertFalse(gate.isOpen(), "Gate should not be OPEN after startOpening()");
        assertEquals(0f, gate.openingTimer, "Timer should be reset");
    }
    
    @Test
    void testCompleteOpening() {
        gate.startOpening();
        gate.completeOpening();
        
        assertTrue(gate.isOpen(), "Gate should be OPEN after completeOpening()");
        assertFalse(gate.isClosed(), "Gate should not be CLOSED after completeOpening()");
        assertFalse(gate.isOpening(), "Gate should not be OPENING after completeOpening()");
    }
    
    @Test
    void testOpenInstantly() {
        gate.openInstantly();
        
        assertTrue(gate.isOpen(), "Gate should be OPEN after openInstantly()");
        assertFalse(gate.isClosed(), "Gate should not be CLOSED after openInstantly()");
    }
    
    @Test
    void testClose() {
        gate.openInstantly();
        assertTrue(gate.isOpen());
        
        gate.close();
        
        assertTrue(gate.isClosed(), "Gate should be CLOSED after close()");
        assertFalse(gate.isOpen(), "Gate should not be OPEN after close()");
        assertEquals(0f, gate.openingTimer, "Timer should be reset on close");
    }
    
    @Test
    void testStartOpeningOnlyFromClosed() {
        gate.openInstantly();
        gate.startOpening();
        
        // startOpening should only work from CLOSED state
        assertTrue(gate.isOpen(), "Gate should remain OPEN when calling startOpening() from OPEN state");
    }
    
    @Test
    void testDimensionsNotStoredByDefault() {
        assertFalse(gate.dimensionsStored);
        assertEquals(0f, gate.originalWidth);
        assertEquals(0f, gate.originalHeight);
    }
    
    @Test
    void testDefaultOpeningDuration() {
        assertEquals(0.5f, gate.openingDuration, "Default opening duration should be 0.5 seconds");
    }
    
    @Test
    void testDefaultRenderOrder() {
        assertEquals(5, gate.renderOrder, "Default render order should be 5");
    }
    
    @Test
    void testStateTransitionSequence() {
        // CLOSED -> OPENING -> OPEN -> CLOSED cycle
        assertTrue(gate.isClosed());
        
        gate.startOpening();
        assertTrue(gate.isOpening());
        
        gate.completeOpening();
        assertTrue(gate.isOpen());
        
        gate.close();
        assertTrue(gate.isClosed());
    }
}
