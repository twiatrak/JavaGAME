package com.timonipumba.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation tests for unified_world.tmx map structure.
 * This map combines all arenas with gate-based progression.
 */
class UnifiedMapValidationTest {
    
    private String loadMapContent(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Could not find resource: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
    }
    
    @Test
    void testUnifiedMapExists() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertNotNull(mapContent, "unified_world.tmx should exist");
        assertFalse(mapContent.isEmpty(), "unified_world.tmx should not be empty");
    }
    
    @Test
    void testUnifiedMapHasPlayerSpawn() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("type\" value=\"player\""),
            "unified_world.tmx should have a player spawn");
    }
    
    @Test
    void testUnifiedMapHasGates() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("type\" value=\"gate\""),
            "unified_world.tmx should have gate objects for arena transitions");
        
        // Gate objects are used for arena transitions. The map may include
        // multiple gate objects per transition (e.g., one per side).
        int gateCount = 0;
        int index = 0;
        while ((index = mapContent.indexOf("type\" value=\"gate\"", index)) != -1) {
            gateCount++;
            index++;
        }
        assertTrue(gateCount >= 4,
            "unified_world.tmx should have at least 4 gate objects; found=" + gateCount);
    }
    
    @Test
    void testUnifiedMapHasCorrectArenaIds() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        
        // Verify arena IDs are present
        assertTrue(mapContent.contains("arena_tutorial"),
            "Should have arena_tutorial identifier");
        assertTrue(mapContent.contains("arena_easy"),
            "Should have arena_easy identifier");
        assertTrue(mapContent.contains("arena_medium"),
            "Should have arena_medium identifier");
        assertTrue(mapContent.contains("arena_hard"),
            "Should have arena_hard identifier");
        assertTrue(mapContent.contains("arena_puzzle"),
            "Should have arena_puzzle identifier");
    }
    
    @Test
    void testUnifiedMapHasPuzzleDoorFinale() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("type\" value=\"puzzledoor\""),
            "unified_world.tmx should have a puzzle door");
        assertTrue(mapContent.contains("isFinale\" type=\"bool\" value=\"true\""),
            "unified_world.tmx puzzle door should be marked as finale");
    }
    
    @Test
    void testUnifiedMapHasTerminal() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("type\" value=\"terminal\""),
            "unified_world.tmx should have a terminal");
    }
    
    @Test
    void testUnifiedMapHasMultipleEnemyTypes() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        
        assertTrue(mapContent.contains("enemy_type\" value=\"brute\""),
            "unified_world.tmx should have brute enemies");
        assertTrue(mapContent.contains("enemy_type\" value=\"scout\""),
            "unified_world.tmx should have scout enemies");
        assertTrue(mapContent.contains("enemy_type\" value=\"ranger\""),
            "unified_world.tmx should have ranger enemies");
    }
    
    @Test
    void testUnifiedMapHasPotions() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("type\" value=\"potion\""),
            "unified_world.tmx should have potions");
    }
    
    @Test
    void testUnifiedMapHasFloorAndWallsLayers() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("name=\"floor\""),
            "unified_world.tmx should have a floor layer");
        assertTrue(mapContent.contains("name=\"walls\""),
            "unified_world.tmx should have a walls layer");
    }
    
    @Test
    void testUnifiedMapHasObjectsLayer() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        assertTrue(mapContent.contains("name=\"objects\""),
            "unified_world.tmx should have an objects layer");
    }
    
    @Test
    void testUnifiedMapCorrectDimensions() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        // 80 tiles wide, 90 tiles tall (compact non-linear layout)
        // New layout: smaller arenas (~20 rows), longer corridors (16 rows)
        // Non-linear arrangement with branching paths
        assertTrue(mapContent.contains("width=\"80\""),
            "unified_world.tmx should be 80 tiles wide");
        assertTrue(mapContent.contains("height=\"90\""),
            "unified_world.tmx should be 90 tiles tall");
    }
    
    @Test
    void testUnifiedMapHasGateTiles() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        // Gate tiles use tile ID 212 in walls layer
        assertTrue(mapContent.contains(",212,") || mapContent.contains(",212\n") || mapContent.contains("212,"),
            "unified_world.tmx should have gate tiles with ID 212 in walls layer");
    }
    
    @Test
    void testAllEnemiesHaveArenaIds() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        
        // Count enemies (type="enemy")
        int enemyCount = 0;
        int index = 0;
        while ((index = mapContent.indexOf("type\" value=\"enemy\"", index)) != -1) {
            enemyCount++;
            index++;
        }
        
        // Count arenaId properties
        int arenaIdCount = 0;
        index = 0;
        while ((index = mapContent.indexOf("arenaId\" value=\"arena_", index)) != -1) {
            arenaIdCount++;
            index++;
        }
        
        assertEquals(enemyCount, arenaIdCount, 
            "All " + enemyCount + " enemies should have an arenaId property for gate association");
    }
    
    @Test
    void testEachArenaHasMatchingGateAndEnemies() throws Exception {
        String mapContent = loadMapContent("maps/unified_world.tmx");
        
        // Check that each non-puzzle arena has enemies with matching arenaId
        String[] arenas = {"arena_tutorial", "arena_easy", "arena_medium", "arena_hard"};
        
        for (String arenaId : arenas) {
            // Check if there's a gate with this sourceArenaId
            assertTrue(mapContent.contains("sourceArenaId\" value=\"" + arenaId + "\""),
                "Arena " + arenaId + " should have a gate with sourceArenaId");
            
            // Check if there are enemies with this arenaId
            assertTrue(mapContent.contains("arenaId\" value=\"" + arenaId + "\""),
                "Arena " + arenaId + " should have enemies with matching arenaId");
        }
    }
}
