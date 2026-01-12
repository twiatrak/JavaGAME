package com.timonipumba.level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalConfig.
 */
class PortalConfigTest {
    
    @BeforeEach
    void setUp() {
        PortalConfig.resetToDefaults();
    }
    
    @AfterEach
    void tearDown() {
        PortalConfig.resetToDefaults();
    }
    
    @Test
    void testDefaultValues() {
        PortalConfig.resetToDefaults();
        
        assertEquals(4, PortalConfig.MIN_SEGMENT_LENGTH);
        assertEquals(5, PortalConfig.MAX_SEGMENT_LENGTH);
        assertEquals(4, PortalConfig.PREFERRED_SEGMENT_LENGTH);
        assertFalse(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED, 
            "Wall segment portals disabled by default (use gates instead)");
        assertTrue(PortalConfig.HIDE_LEGACY_EXITS);
        assertTrue(PortalConfig.DETERMINISTIC_PLACEMENT);
        assertEquals(42L, PortalConfig.PLACEMENT_SEED);
    }
    
    @Test
    void testFeatureFlagToggle() {
        assertFalse(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED);
        
        PortalConfig.WALL_SEGMENT_PORTALS_ENABLED = true;
        assertTrue(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED);
        
        PortalConfig.resetToDefaults();
        assertFalse(PortalConfig.WALL_SEGMENT_PORTALS_ENABLED);
    }
    
    @Test
    void testHideLegacyExitsToggle() {
        assertTrue(PortalConfig.HIDE_LEGACY_EXITS);
        
        PortalConfig.HIDE_LEGACY_EXITS = false;
        assertFalse(PortalConfig.HIDE_LEGACY_EXITS);
        
        PortalConfig.resetToDefaults();
        assertTrue(PortalConfig.HIDE_LEGACY_EXITS);
    }
    
    @Test
    void testPreferredWalls() {
        assertNotNull(PortalConfig.PREFERRED_WALLS);
        assertEquals(4, PortalConfig.PREFERRED_WALLS.length);
        assertEquals(PortalConfig.WallDirection.NORTH, PortalConfig.PREFERRED_WALLS[0]);
        assertEquals(PortalConfig.WallDirection.EAST, PortalConfig.PREFERRED_WALLS[1]);
        assertEquals(PortalConfig.WallDirection.SOUTH, PortalConfig.PREFERRED_WALLS[2]);
        assertEquals(PortalConfig.WallDirection.WEST, PortalConfig.PREFERRED_WALLS[3]);
    }
    
    @Test
    void testSegmentLengthConstraints() {
        assertTrue(PortalConfig.MIN_SEGMENT_LENGTH <= PortalConfig.MAX_SEGMENT_LENGTH);
        assertTrue(PortalConfig.PREFERRED_SEGMENT_LENGTH >= PortalConfig.MIN_SEGMENT_LENGTH);
        assertTrue(PortalConfig.PREFERRED_SEGMENT_LENGTH <= PortalConfig.MAX_SEGMENT_LENGTH);
    }
    
    @Test
    void testDeterministicPlacementSeed() {
        assertTrue(PortalConfig.DETERMINISTIC_PLACEMENT);
        
        long originalSeed = PortalConfig.PLACEMENT_SEED;
        PortalConfig.PLACEMENT_SEED = 12345L;
        assertEquals(12345L, PortalConfig.PLACEMENT_SEED);
        
        PortalConfig.resetToDefaults();
        assertEquals(originalSeed, PortalConfig.PLACEMENT_SEED);
    }
}
