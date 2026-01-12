package com.timonipumba.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class AlgebraForgeTmxGeneratorTest {

    @Test
    void generatesTmxWithForgeOracleAndVaultGate() {
        AlgebraForgeTmxGenerator gen = new AlgebraForgeTmxGenerator();
        long seed = 12345L;
        String tmx = gen.generate(seed);

        assertNotNull(tmx);
        assertTrue(tmx.contains("<map"));
        assertTrue(tmx.contains("name=\"floor\""));
        assertTrue(tmx.contains("name=\"walls\""));

        // Algebra terminals
        assertTrue(tmx.contains("property name=\"terminalType\" value=\"forge\""));
        assertTrue(tmx.contains("property name=\"terminalType\" value=\"oracle\""));
        assertTrue(tmx.contains("property name=\"opId\" value=\"cathedral_"));
        assertTrue(tmx.contains("property name=\"charges\" type=\"int\""));

        // Vault gate wiring via socket group
        assertTrue(tmx.contains("property name=\"type\" value=\"socket\""));
        assertTrue(tmx.contains("name=\"requiresTokenId\" value=\"glyph_7\""));
        assertTrue(tmx.contains("property name=\"type\" value=\"gate\""));
        assertTrue(tmx.contains("name=\"tileId\" value=\"" + com.timonipumba.GameConstants.GATE_TILE_ID + "\""));
        assertTrue(tmx.contains("name=\"open\" type=\"bool\" value=\"false\""));

        // TMX CSV layer data must have commas between all entries.
        // libGDX splits on ',' only (newlines are treated as whitespace), so a row boundary
        // without a comma becomes an invalid token like "0\n0".
        assertNoDigitNewlineDigitInCsvData(tmx);

        // Objects must be shifted into the cropped tile-layer coordinate space.
        // If not, player/items can spawn far outside the rendered tile area (appears as a black map).
        assertObjectsWithinMapBounds(tmx);

        // Sanity: the generated layout should be traversable (no accidental chokepoints).
        // We require that the player can reach the vault socket while the gate is still closed.
        assertPlayerCanReachSocket(tmx);
    }

    private static void assertPlayerCanReachSocket(String tmx) {
        int width = extractIntAttr(tmx, "<map", "width");
        int height = extractIntAttr(tmx, "<map", "height");
        int tileSize = com.timonipumba.GameConstants.TILE_SIZE;

        int[] floor = extractLayerCsvAsFlatInts(tmx, "floor", width, height);
        int[] walls = extractLayerCsvAsFlatInts(tmx, "walls", width, height);

        int[] playerTile = extractFirstObjectTileXY(tmx, "player", tileSize);
        int[] socketTile = extractFirstObjectTileXY(tmx, "socket", tileSize);
        assertNotNull(playerTile, "Missing player object");
        assertNotNull(socketTile, "Missing socket object");

        int start = playerTile[1] * width + playerTile[0];
        int goal = socketTile[1] * width + socketTile[0];

        int floorId = com.timonipumba.GameConstants.FLOOR_TILE_ID;
        int gateId = com.timonipumba.GameConstants.GATE_TILE_ID;
        assertTrue(isPassable(floor, walls, start, floorId, gateId), "Player spawn is not on passable floor");
        assertTrue(isPassable(floor, walls, goal, floorId, gateId), "Socket is not on passable floor");

        boolean[] visited = new boolean[width * height];
        visited[start] = true;
        Deque<Integer> q = new ArrayDeque<>();
        q.add(start);

        while (!q.isEmpty()) {
            int idx = q.removeFirst();
            if (idx == goal) return;

            int x = idx % width;
            int y = idx / width;

            // 4-neighborhood
            if (x > 0) tryEnqueue(width, floor, walls, visited, q, idx - 1, floorId, gateId);
            if (x + 1 < width) tryEnqueue(width, floor, walls, visited, q, idx + 1, floorId, gateId);
            if (y > 0) tryEnqueue(width, floor, walls, visited, q, idx - width, floorId, gateId);
            if (y + 1 < height) tryEnqueue(width, floor, walls, visited, q, idx + width, floorId, gateId);
        }

        fail("Expected player spawn to reach vault socket via walkable tiles");
    }

    private static void tryEnqueue(int width, int[] floor, int[] walls, boolean[] visited, Deque<Integer> q, int idx, int floorId, int gateId) {
        if (idx < 0 || idx >= visited.length) return;
        if (visited[idx]) return;
        if (!isPassable(floor, walls, idx, floorId, gateId)) return;
        visited[idx] = true;
        q.add(idx);
    }

    private static boolean isPassable(int[] floor, int[] walls, int idx, int floorId, int gateId) {
        // Gate tiles are represented in the "walls" layer for visuals,
        // but collision is enforced by spawned gate entities at runtime.
        return floor[idx] == floorId && (walls[idx] == 0 || walls[idx] == gateId);
    }

    private static int[] extractFirstObjectTileXY(String tmx, String objectType, int tileSize) {
        int idx = 0;
        String needle = "<property name=\"type\" value=\"" + objectType + "\"";
        while (true) {
            int start = tmx.indexOf("<object ", idx);
            if (start < 0) return null;
            int end = tmx.indexOf(">", start);
            if (end < 0) return null;

            // Capture the full object block so we can see <properties>.
            int blockEnd;
            String openTag = tmx.substring(start, end + 1);
            if (openTag.endsWith("/>")) {
                blockEnd = end + 1;
            } else {
                int close = tmx.indexOf("</object>", end);
                if (close < 0) return null;
                blockEnd = close + "</object>".length();
            }

            String block = tmx.substring(start, blockEnd);
            if (block.contains(needle)) {
                Integer xPx = extractIntAttrOrNull(openTag, "x");
                Integer yPx = extractIntAttrOrNull(openTag, "y");
                if (xPx == null || yPx == null) return null;
                int x = xPx / tileSize;
                int y = yPx / tileSize;
                return new int[]{x, y};
            }
            idx = blockEnd;
        }
    }

    private static int[] extractLayerCsvAsFlatInts(String tmx, String layerName, int width, int height) {
        String layerNeedle = "<layer";
        String nameNeedle = "name=\"" + layerName + "\"";

        int searchIdx = 0;
        while (true) {
            int layerStart = tmx.indexOf(layerNeedle, searchIdx);
            if (layerStart < 0) fail("Missing layer: " + layerName);
            int layerTagEnd = tmx.indexOf(">", layerStart);
            assertTrue(layerTagEnd > layerStart, "Malformed layer tag");

            String layerTag = tmx.substring(layerStart, layerTagEnd + 1);
            if (!layerTag.contains(nameNeedle)) {
                searchIdx = layerTagEnd + 1;
                continue;
            }

            int dataStart = tmx.indexOf("<data encoding=\"csv\">", layerTagEnd);
            assertTrue(dataStart > 0, "Missing CSV data tag for layer: " + layerName);
            int afterDataTag = tmx.indexOf("\n", dataStart);
            int dataEnd = tmx.indexOf("</data>", dataStart);
            assertTrue(afterDataTag > dataStart && dataEnd > dataStart, "Malformed CSV data for layer: " + layerName);

            String csv = tmx.substring(afterDataTag, dataEnd).trim();
            String[] parts = csv.split(",");
            int expected = width * height;
            assertEquals(expected, parts.length, "CSV cell count mismatch for layer: " + layerName);

            int[] out = new int[expected];
            for (int i = 0; i < expected; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
            return out;
        }
    }

    private static void assertObjectsWithinMapBounds(String tmx) {
        int width = extractIntAttr(tmx, "<map", "width");
        int height = extractIntAttr(tmx, "<map", "height");
        assertTrue(width > 0 && height > 0, "Map width/height should be positive");

        int tileSize = com.timonipumba.GameConstants.TILE_SIZE;
        int maxX = width * tileSize;
        int maxY = height * tileSize;

        int idx = 0;
        int objectCount = 0;
        while (true) {
            int start = tmx.indexOf("<object ", idx);
            if (start < 0) break;
            int end = tmx.indexOf(">", start);
            if (end < 0) break;
            String tag = tmx.substring(start, end + 1);

            Integer x = extractIntAttrOrNull(tag, "x");
            Integer y = extractIntAttrOrNull(tag, "y");
            if (x != null && y != null) {
                objectCount++;
                assertTrue(x >= 0 && x <= maxX, "Object x out of bounds: " + x + " (max " + maxX + ")");
                assertTrue(y >= 0 && y <= maxY, "Object y out of bounds: " + y + " (max " + maxY + ")");
            }

            idx = end + 1;
        }

        assertTrue(objectCount >= 3, "Expected at least a few objects with x/y attributes");
    }

    private static int extractIntAttr(String xml, String tagStart, String attrName) {
        int tagIdx = xml.indexOf(tagStart);
        assertTrue(tagIdx >= 0, "Missing tag start: " + tagStart);
        int tagEnd = xml.indexOf(">", tagIdx);
        assertTrue(tagEnd > tagIdx, "Malformed tag for: " + tagStart);
        String tag = xml.substring(tagIdx, tagEnd + 1);
        Integer val = extractIntAttrOrNull(tag, attrName);
        assertNotNull(val, "Missing attribute '" + attrName + "' in " + tagStart);
        return val;
    }

    private static Integer extractIntAttrOrNull(String tag, String attrName) {
        String needle = attrName + "=\"";
        int idx = tag.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        int end = tag.indexOf('"', start);
        if (end < 0) return null;
        String raw = tag.substring(start, end);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void assertNoDigitNewlineDigitInCsvData(String tmx) {
        int idx = 0;
        while (true) {
            int start = tmx.indexOf("<data encoding=\"csv\">", idx);
            if (start < 0) break;

            int afterTag = tmx.indexOf("\n", start);
            int end = tmx.indexOf("</data>", start);
            assertTrue(afterTag > start && end > start, "CSV section should be well-formed");

            String csv = tmx.substring(afterTag, end);

            for (int i = 0; i < csv.length(); i++) {
                if (csv.charAt(i) != '\n') continue;

                int left = i - 1;
                while (left >= 0 && (csv.charAt(left) == ' ' || csv.charAt(left) == '\t' || csv.charAt(left) == '\r')) {
                    left--;
                }
                int right = i + 1;
                while (right < csv.length() && (csv.charAt(right) == ' ' || csv.charAt(right) == '\t' || csv.charAt(right) == '\r')) {
                    right++;
                }

                if (left < 0 || right >= csv.length()) continue;

                char l = csv.charAt(left);
                char r = csv.charAt(right);
                boolean leftDigit = l >= '0' && l <= '9';
                boolean rightDigit = r >= '0' && r <= '9';
                if (leftDigit && rightDigit) {
                    fail("Invalid TMX CSV: newline between digits without comma separator");
                }
            }

            idx = end + 7;
        }
    }
}
