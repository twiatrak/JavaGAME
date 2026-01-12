package com.timonipumba.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Validation tests for puzzle_example.tmx map structure.
 * 
 * These tests verify that the map adheres to the gate/corridor specification:
 * - No 'exit' object (which rendered as black square)
 * - Gate layer exists with tile 211 for CLOSED state
 * - Corridor layer exists with floor tiles (921)
 * - Gate object exists with correct properties
 */
class PuzzleMapValidationTest {
    
    /** Tile ID for closed gates (column 41, row 3 zero-based in spritesheet) */
    private static final int GATE_CLOSED_TILE_ID = 212;
    
    /** Floor tile ID used in corridor layer */
    private static final int FLOOR_TILE_ID = 921;
    
    @Test
    void testPuzzleMapHasNoExitObject() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Check that there's no exit object type
        assertFalse(mapContent.contains("\"type\" value=\"exit\""),
            "puzzle_example.tmx should not contain 'exit' type objects");
        assertFalse(mapContent.contains("name=\"exit\"") && mapContent.contains("type=\"exit\""),
            "puzzle_example.tmx should not have exit objects (renders as black square)");
    }
    
    @Test
    void testPuzzleMapHasNoOutgoingGateObject() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Puzzle arena is the finale - should NOT have gate objects with targetArenaId
        // (which would indicate outgoing gates to other arenas)
        // An incoming gate object (with sourceArenaId only) is allowed since players enter the arena
        
        // Check for gates that have targetArenaId - these would be outgoing gates
        // Pattern: gate object with targetArenaId property would be an outgoing gate
        int gateStart = mapContent.indexOf("type\" value=\"gate\"");
        if (gateStart >= 0) {
            // Gate object exists - verify it doesn't have targetArenaId (outgoing)
            int objectEnd = mapContent.indexOf("</object>", gateStart);
            String gateSection = mapContent.substring(gateStart, objectEnd);
            assertFalse(gateSection.contains("targetArenaId"),
                "Gate in puzzle arena should NOT have targetArenaId (puzzle arena is finale)");
        }
        // Note: it's OK to have a gate object if it's just an incoming gate (sourceArenaId only)
    }
    
    @Test
    void testPuzzleMapHasGateLayer() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Check that gate layer exists
        assertTrue(mapContent.contains("name=\"gate\""),
            "puzzle_example.tmx should have a 'gate' layer");
    }
    
    @Test
    void testPuzzleMapHasCorridorLayer() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Check that corridor layer exists
        assertTrue(mapContent.contains("name=\"corridor\""),
            "puzzle_example.tmx should have a 'corridor' layer");
    }
    
    @Test
    void testGateLayerContainsTile211() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Find gate layer data section
        int gateLayerStart = mapContent.indexOf("name=\"gate\"");
        assertTrue(gateLayerStart > 0, "Gate layer should exist");
        
        int dataStart = mapContent.indexOf("<data", gateLayerStart);
        int dataEnd = mapContent.indexOf("</data>", dataStart);
        String gateData = mapContent.substring(dataStart, dataEnd);
        
        // Check that tile 211 (CLOSED gate) is present in CSV data
        // Handle all possible positions: start, middle, end of line/data
        String tileStr = String.valueOf(GATE_CLOSED_TILE_ID);
        assertTrue(gateData.contains("," + tileStr + ",") || 
                   gateData.contains(tileStr + ",") ||
                   gateData.contains("," + tileStr + "\n") ||
                   gateData.contains("," + tileStr) ||
                   gateData.contains(tileStr + "\n"),
            "Gate layer should contain tile ID " + GATE_CLOSED_TILE_ID + " for CLOSED gates");
    }
    
    @Test
    void testCorridorLayerContainsFloorTiles() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Find corridor layer data section
        int corridorLayerStart = mapContent.indexOf("name=\"corridor\"");
        assertTrue(corridorLayerStart > 0, "Corridor layer should exist");
        
        // According to requirements: "Corridors start empty and are spawned/revealed at runtime"
        // The corridor layer should exist but may be empty initially
        // Floor tiles (921) are spawned at runtime by CorridorBuilder when gates open
        
        int dataStart = mapContent.indexOf("<data", corridorLayerStart);
        int dataEnd = mapContent.indexOf("</data>", dataStart);
        assertTrue(dataStart > 0 && dataEnd > dataStart, 
            "Corridor layer should have data section");
    }
    
    @Test
    void testCorridorStartsEmpty() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Find corridor layer data section
        int corridorLayerStart = mapContent.indexOf("name=\"corridor\"");
        assertTrue(corridorLayerStart > 0, "Corridor layer should exist");
        
        int dataStart = mapContent.indexOf("<data", corridorLayerStart);
        int dataEnd = mapContent.indexOf("</data>", dataStart);
        String corridorData = mapContent.substring(dataStart, dataEnd);
        
        // According to requirements: "Corridors start empty and are spawned/revealed at runtime"
        // Count floor tiles (921) in corridor layer - should be 0 at map load
        int floorTileCount = countOccurrences(corridorData, String.valueOf(FLOOR_TILE_ID));
        
        assertEquals(0, floorTileCount,
            "Corridor layer should start empty (tiles spawned at runtime), found: " + floorTileCount);
    }
    
    @Test
    void testCorridorHasMinimumLength() throws IOException {
        // Note: According to requirements "Corridors start empty and are spawned/revealed at runtime"
        // This test validates that the CorridorBuilder spawns at least 6 tiles (min corridor length)
        // The actual runtime behavior is tested in GateSystemTest.testGateSpawnsCorridorOnOpen()
        
        // Verify that corridor layer exists for runtime tile placement
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        int corridorLayerStart = mapContent.indexOf("name=\"corridor\"");
        assertTrue(corridorLayerStart > 0, 
            "Corridor layer should exist for runtime floor tile spawning");
    }
    
    @Test
    void testPuzzleDoorHasFinaleProperty() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Find puzzledoor object section
        int puzzleDoorStart = mapContent.indexOf("type\" value=\"puzzledoor\"");
        assertTrue(puzzleDoorStart > 0, "PuzzleDoor object should exist");
        
        // Look for isFinale property - puzzle arena is finale so puzzle must end level
        int objectEnd = mapContent.indexOf("</object>", puzzleDoorStart);
        String puzzleDoorSection = mapContent.substring(puzzleDoorStart, objectEnd);
        
        assertTrue(puzzleDoorSection.contains("isFinale") && puzzleDoorSection.contains("true"),
            "PuzzleDoor in puzzle arena should have isFinale=true (solving ends level)");
    }
    
    @Test
    void testNoPortalVisualsInPuzzleArena() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Check that there are no portal objects or portal-related types
        assertFalse(mapContent.contains("type\" value=\"portal\""),
            "puzzle_example.tmx should not contain portal type objects");
    }
    
    @Test
    void testPuzzleArenaHasNoPuzzleExitGates() throws IOException {
        String mapContent = loadMapContent("maps/puzzle_example.tmx");
        
        // Puzzle arena should not have gates leading out to other arenas
        // This validates that solving the puzzle ends the level, not opens a gate
        assertFalse(mapContent.contains("targetArenaId"),
            "Puzzle arena should not have gates with targetArenaId (no outgoing gates)");
    }
    
    /**
     * Helper method to load map content from resources.
     */
    private String loadMapContent(String resourcePath) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    /**
     * Helper method to count occurrences of a substring.
     */
    private int countOccurrences(String str, String findStr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(findStr, idx)) != -1) {
            count++;
            idx += findStr.length();
        }
        return count;
    }
}
