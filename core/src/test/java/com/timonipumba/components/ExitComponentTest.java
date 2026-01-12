package com.timonipumba.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExitComponent.
 * 
 * Validates:
 * - Default constructor values
 * - Custom constructor with target map
 */
class ExitComponentTest {
    
    @Test
    void testDefaultConstructor() {
        ExitComponent exit = new ExitComponent();
        
        assertNull(exit.targetMap, "Default targetMap should be null");
    }
    
    @Test
    void testTargetMapConstructor() {
        ExitComponent exit = new ExitComponent("maps/level2.tmx");
        
        assertEquals("maps/level2.tmx", exit.targetMap);
    }
    
    @Test
    void testNullTargetMap() {
        ExitComponent exit = new ExitComponent(null);
        
        assertNull(exit.targetMap);
    }
    
    @Test
    void testTargetMapModification() {
        ExitComponent exit = new ExitComponent();
        
        assertNull(exit.targetMap);
        
        exit.targetMap = "maps/boss.tmx";
        assertEquals("maps/boss.tmx", exit.targetMap);
        
        exit.targetMap = null;
        assertNull(exit.targetMap);
    }
}
