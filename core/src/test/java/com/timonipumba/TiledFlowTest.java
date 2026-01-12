package com.timonipumba;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

/**
 * Tests for Tiled map flow - verifies the game uses the unified world map
 * with all arenas connected by gates and corridors.
 */
class TiledFlowTest {
    
    @Test
    void testUnifiedMapIsConfigured() throws Exception {
        // Access the private UNIFIED_MAP field via reflection
        Field mapField = TiledMapGame.class.getDeclaredField("UNIFIED_MAP");
        mapField.setAccessible(true);
        String unifiedMap = (String) mapField.get(null);
        
        assertNotNull(unifiedMap, "UNIFIED_MAP should not be null");
        assertTrue(unifiedMap.contains("unified_world.tmx"), 
            "Should use unified_world.tmx, but was: " + unifiedMap);
    }
    
    @Test
    void testUnifiedMapPathIsCorrect() throws Exception {
        // Access the private UNIFIED_MAP field via reflection
        Field mapField = TiledMapGame.class.getDeclaredField("UNIFIED_MAP");
        mapField.setAccessible(true);
        String unifiedMap = (String) mapField.get(null);
        
        assertNotNull(unifiedMap, "UNIFIED_MAP should not be null");
        assertEquals("maps/unified_world.tmx", unifiedMap,
            "UNIFIED_MAP path should be maps/unified_world.tmx");
    }
}
