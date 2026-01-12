package com.timonipumba.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.timonipumba.GameConstants;
import com.timonipumba.systems.GraphLightsOutSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Generates TMX for Graph Lights-Out riddle (Lantern Garden)
public class GraphLightsOutTmxGenerator {

    private static final int TILE_FLOOR = GameConstants.FLOOR_TILE_ID;
    private static final int TILE_WALL = 936;
    private static final int TILE_GATE = GameConstants.GATE_TILE_ID;

    // Build on a blank canvas then crop.
    private static final int CANVAS_W = 260;
    private static final int CANVAS_H = 210;
    private static final int CROP_MARGIN = 3;

    // Corridor width (tiles).
    private static final int CORRIDOR_WIDTH = 4;

    // Lantern pads (tiles) - diamond-ish pads rather than big rectangles.
    private static final int PAD_RADIUS = 6;

    private final Random random = new Random();

    public void generateAndWrite(String targetPath, long seed) {
        random.setSeed(seed);

        int[][] floorCanvas = new int[CANVAS_H][CANVAS_W];

        // Jittered grid of lantern pads.
        int nodeCount = 14;
        Node[] nodes = new Node[nodeCount];

        // 4x4 grid with two corners removed -> 14 nodes.
        int gridCols = 4;
        int gridRows = 4;
        int baseX = 55;
        int baseY = 42;
        int spacingX = 38;
        int spacingY = 34;

        // Map node index -> grid cell index (row-major 0..15), excluding two corners.
        int[] cellIndex = new int[] {
                1, 2, 3,
                4, 5, 6, 7,
                8, 9, 10, 11,
                12, 13, 14
        };
        for (int i = 0; i < nodeCount; i++) {
            int cell = cellIndex[i];
            int r = cell / gridCols;
            int c = cell % gridCols;

            int jitterX = randBetween(-3, 3);
            int jitterY = randBetween(-3, 3);
            int cx = baseX + c * spacingX + jitterX;
            int cy = baseY + r * spacingY + jitterY;

            // Width/height are not used for pads, but keep reasonable values for cropping math.
            nodes[i] = new Node("lantern_" + i, cx, cy, 1, 1);
        }

        boolean[][] edge = new boolean[nodeCount][nodeCount];

        // Helper: map a grid cell -> node index (or -1 if excluded).
        int[] nodeAtCell = new int[gridCols * gridRows];
        for (int i = 0; i < nodeAtCell.length; i++) nodeAtCell[i] = -1;
        for (int i = 0; i < nodeCount; i++) {
            nodeAtCell[cellIndex[i]] = i;
        }

        // Add grid adjacency edges (up/down/left/right) between included cells.
        for (int cell = 0; cell < gridCols * gridRows; cell++) {
            int i = nodeAtCell[cell];
            if (i < 0) continue;
            int r = cell / gridCols;
            int c = cell % gridCols;

            int right = (c + 1 < gridCols) ? (cell + 1) : -1;
            int down = (r + 1 < gridRows) ? (cell + gridCols) : -1;
            if (right >= 0) {
                int j = nodeAtCell[right];
                if (j >= 0) addEdge(edge, i, j);
            }
            if (down >= 0) {
                int j = nodeAtCell[down];
                if (j >= 0) addEdge(edge, i, j);
            }
        }

        // Add a few local extra edges to increase coupling (kept local to match visible corridors).
        addEdge(edge, 0, 4);
        addEdge(edge, 2, 7);
        addEdge(edge, 5, 9);
        addEdge(edge, 8, 11);
        addEdge(edge, 10, 13);

        // Paint lantern pads (diamond-ish) rather than large rectangular arenas.
        for (Node n : nodes) {
            paintDiamondPad(floorCanvas, n.cx, n.cy, PAD_RADIUS);
        }

        // Paint corridors.
        for (int i = 0; i < nodeCount; i++) {
            for (int j = i + 1; j < nodeCount; j++) {
                if (!edge[i][j]) continue;
                paintZigZagCorridor(floorCanvas, nodes[i].cx, nodes[i].cy, nodes[j].cx, nodes[j].cy);
            }
        }

        // Start and reward pads
        int startX = 25;
        int startY = 25;
        int rewardX = CANVAS_W - 35;
        int rewardY = CANVAS_H - 45;

        paintDiamondPad(floorCanvas, startX, startY, PAD_RADIUS + 1);
        paintDiamondPad(floorCanvas, rewardX, rewardY, PAD_RADIUS + 1);

        // Connect start -> first lantern (node 0)
        paintZigZagCorridor(floorCanvas, startX, startY, nodes[0].cx, nodes[0].cy);

        // Connect a far lantern -> reward; place a door at the *entrance* to the reward pad
        // (so the brown door block isn't sitting awkwardly mid-corridor).
        int doorTileX;
        int doorTileY;
        {
            int x0 = nodes[nodeCount - 1].cx;
            int y0 = nodes[nodeCount - 1].cy;
            int x1 = rewardX;
            int y1 = rewardY;

            // Deterministic routing for stable door placement.
            int midX = clamp((x0 + x1) / 2 + 8, Math.min(x0, x1), Math.max(x0, x1));
            paintCorridorHorizontal(floorCanvas, y0, x0, midX);
            paintCorridorVertical(floorCanvas, midX, y0, y1);
            paintCorridorHorizontal(floorCanvas, y1, midX, x1);

            // Put the door just before the reward pad.
            doorTileX = clamp(x1 - (PAD_RADIUS + 2), 1, CANVAS_W - 2);
            doorTileY = clamp(y1, 1, CANVAS_H - 2);

            // Corridor is multi-tile wide; ensure the full-width gate band sits on floor.
            int startOffset = -(CORRIDOR_WIDTH / 2);
            int endOffsetExclusive = startOffset + CORRIDOR_WIDTH;
            for (int dy = startOffset; dy < endOffsetExclusive; dy++) {
                setFloor(floorCanvas, doorTileX, doorTileY + dy);
            }
        }

        // Build walls around floors (blank outside remains 0).
        int[][] wallsCanvas = buildWallsFromFloor(floorCanvas);

        // Crop to used region.
        CropResult cropped = cropToUsedRegion(floorCanvas, wallsCanvas, CROP_MARGIN);
        if (cropped == null) {
            throw new IllegalStateException("GraphLightsOutTmxGenerator: nothing generated");
        }

        // Shift objects into cropped space.
        int shiftX = -cropped.minX;
        int shiftY = -cropped.minY;
        int startTileX = startX + shiftX;
        int startTileY = startY + shiftY;
        int doorTileXCropped = doorTileX + shiftX;
        int doorTileYCropped = doorTileY + shiftY;
        int goalTileX = rewardX + shiftX;
        int goalTileY = rewardY + shiftY;

        // Stamp gate tiles into the walls layer at the barrier location (and ensure floor underneath).
        // GateSystem will clear these tiles when the riddle opens the gate.
        {
            int startOffset = -(CORRIDOR_WIDTH / 2);
            int endOffsetExclusive = startOffset + CORRIDOR_WIDTH;
            for (int dy = startOffset; dy < endOffsetExclusive; dy++) {
                int gx = doorTileXCropped;
                int gy = doorTileYCropped + dy;
                if (gy < 0 || gy >= cropped.height || gx < 0 || gx >= cropped.width) continue;
                cropped.walls[gy][gx] = TILE_GATE;
                cropped.floor[gy][gx] = TILE_FLOOR;
            }
        }

        for (Node n : nodes) {
            n.cx += shiftX;
            n.cy += shiftY;
        }

        // Compute a guaranteed-solvable initial state:
        // Start from solved (all on), apply a random set of taps, and record resulting state.
        boolean[] press = new boolean[nodeCount];
        // Heuristic: ~half of nodes pressed.
        for (int i = 0; i < nodeCount; i++) {
            press[i] = random.nextFloat() < 0.55f;
        }
        boolean[] initialOn = computeInitialStateAllOnXorPresses(nodeCount, edge, press);

        String tmx = buildTmxXml(
                cropped.floor,
                cropped.walls,
                cropped.width,
                cropped.height,
                nodes,
                edge,
                initialOn,
                seed,
                startTileX,
                startTileY,
                doorTileXCropped,
            doorTileYCropped,
            goalTileX,
            goalTileY
        );

        FileHandle fh;
        File targetFile = new File(targetPath);
        if (targetFile.isAbsolute()) {
            fh = new FileHandle(targetFile);
        } else {
            fh = Gdx.files.local(targetPath);
        }
        if (fh.file().getParentFile() != null) {
            fh.file().getParentFile().mkdirs();
        }

        RoguelikeTilesetAssets.copyToMapDir(fh.parent());
        fh.writeString(tmx, false, "UTF-8");
    }

    private boolean[] computeInitialStateAllOnXorPresses(int n, boolean[][] edge, boolean[] press) {
        // state starts as all on.
        boolean[] state = new boolean[n];
        for (int i = 0; i < n; i++) state[i] = true;

        // Apply each press: flips i and all neighbors.
        for (int i = 0; i < n; i++) {
            if (!press[i]) continue;
            state[i] = !state[i];
            for (int j = 0; j < n; j++) {
                if (!edge[i][j]) continue;
                state[j] = !state[j];
            }
        }

        return state;
    }

    private void addEdge(boolean[][] edge, int i, int j) {
        if (i < 0 || j < 0 || i >= edge.length || j >= edge.length) return;
        if (i == j) return;
        edge[i][j] = true;
        edge[j][i] = true;
    }

    private int makeOdd(int v) {
        return (v % 2 == 0) ? (v + 1) : v;
    }

    private int randBetween(int lo, int hi) {
        return lo + random.nextInt(Math.max(1, (hi - lo + 1)));
    }

    private void paintDiamondPad(int[][] floor, int cx, int cy, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= radius) {
                    setFloor(floor, cx + dx, cy + dy);
                }
            }
        }
        setFloor(floor, cx, cy);
    }

    private void paintZigZagCorridor(int[][] floor, int x0, int y0, int x1, int y1) {
        boolean horizFirst = random.nextBoolean();
        if (horizFirst) {
            int minX = Math.min(x0, x1);
            int maxX = Math.max(x0, x1);
            int midX = clamp((x0 + x1) / 2 + randBetween(-10, 10), minX, maxX);
            paintCorridorHorizontal(floor, y0, x0, midX);
            paintCorridorVertical(floor, midX, y0, y1);
            paintCorridorHorizontal(floor, y1, midX, x1);
        } else {
            int minY = Math.min(y0, y1);
            int maxY = Math.max(y0, y1);
            int midY = clamp((y0 + y1) / 2 + randBetween(-10, 10), minY, maxY);
            paintCorridorVertical(floor, x0, y0, midY);
            paintCorridorHorizontal(floor, midY, x0, x1);
            paintCorridorVertical(floor, x1, midY, y1);
        }
    }

    private void paintCorridorHorizontal(int[][] floor, int y, int x0, int x1) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int half = CORRIDOR_WIDTH / 2;
        for (int x = minX; x <= maxX; x++) {
            for (int dy = -half; dy <= half; dy++) {
                setFloor(floor, x, y + dy);
            }
        }
    }

    private void paintCorridorVertical(int[][] floor, int x, int y0, int y1) {
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        int half = CORRIDOR_WIDTH / 2;
        for (int y = minY; y <= maxY; y++) {
            for (int dx = -half; dx <= half; dx++) {
                setFloor(floor, x + dx, y);
            }
        }
    }

    private void setFloor(int[][] floor, int x, int y) {
        if (y < 0 || y >= floor.length) return;
        if (x < 0 || x >= floor[0].length) return;
        floor[y][x] = TILE_FLOOR;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int[][] buildWallsFromFloor(int[][] floor) {
        int h = floor.length;
        int w = floor[0].length;
        int[][] walls = new int[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (floor[y][x] == TILE_FLOOR) {
                    walls[y][x] = 0;
                    continue;
                }

                boolean nearFloor = false;
                for (int dy = -1; dy <= 1 && !nearFloor; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        if (floor[ny][nx] == TILE_FLOOR) {
                            nearFloor = true;
                            break;
                        }
                    }
                }

                walls[y][x] = nearFloor ? TILE_WALL : 0;
            }
        }

        return walls;
    }

    private CropResult cropToUsedRegion(int[][] floor, int[][] walls, int margin) {
        int h = floor.length;
        int w = floor[0].length;

        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (floor[y][x] != 0 || walls[y][x] != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) return null;

        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(w - 1, maxX + margin);
        maxY = Math.min(h - 1, maxY + margin);

        int newW = (maxX - minX + 1);
        int newH = (maxY - minY + 1);

        int[][] newFloor = new int[newH][newW];
        int[][] newWalls = new int[newH][newW];

        for (int y = 0; y < newH; y++) {
            for (int x = 0; x < newW; x++) {
                newFloor[y][x] = floor[minY + y][minX + x];
                newWalls[y][x] = walls[minY + y][minX + x];
            }
        }

        return new CropResult(newFloor, newWalls, newW, newH, minX, minY);
    }

    private String buildTmxXml(
            int[][] floor,
            int[][] walls,
            int width,
            int height,
            Node[] nodes,
            boolean[][] edge,
            boolean[] initialOn,
            long seed,
            int startX,
            int startY,
            int doorTileX,
            int doorTileY,
            int goalTileX,
            int goalTileY
    ) {
            TmxMapWriter w = TmxMapWriter.create(width, height)
                .startMap()
                .tileset("tilesets/roguelikeSheet_magenta.tsx")
                .layerCsv(1, "floor", floor)
                .layerCsv(2, "walls", walls)
                .objectGroupOpen(3, "objects");

            StringBuilder sb = w.sb();
        int nextId = 1;

        sb.append(TmxXml.object(nextId++, "player", startX * GameConstants.TILE_SIZE, startY * GameConstants.TILE_SIZE,
            20, 32,
            TmxXml.props(TmxXml.prop("type", "player"))));

        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            String neighborsCsv = neighborsCsv(nodes, edge, i);
                sb.append(TmxXml.object(nextId++, "socket", n.cx * GameConstants.TILE_SIZE, n.cy * GameConstants.TILE_SIZE,
                    GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
                    TmxXml.props(
                        TmxXml.prop("type", "socket"),
                        TmxXml.prop("puzzleType", GraphLightsOutSystem.PUZZLE_TYPE),
                        TmxXml.prop("nodeId", n.id),
                        TmxXml.prop("neighbors", neighborsCsv),
                        TmxXml.prop("on", initialOn[i] ? "true" : "false", "bool"),
                        TmxXml.prop("consume", "false", "bool")
                    )));
        }

        final String rewardGroup = "lantern_reward_" + seed;
        // Gate spans the corridor width so the reward pad cannot be accessed by walking around a single tile.
        {
            int startOffset = -(CORRIDOR_WIDTH / 2);
            float gx = doorTileX * GameConstants.TILE_SIZE;
            float gy = (doorTileY + startOffset) * GameConstants.TILE_SIZE;
            float gw = GameConstants.TILE_SIZE;
            float gh = CORRIDOR_WIDTH * GameConstants.TILE_SIZE;
                sb.append(TmxXml.object(nextId++, "gate",
                    gx, gy, gw, gh,
                    TmxXml.props(
                        TmxXml.prop("type", "gate"),
                        TmxXml.prop("group", rewardGroup),
                        TmxXml.prop("open", "false", "bool"),
                        TmxXml.prop("tileId", String.valueOf(TILE_GATE), "int")
                    )));
        }

        // Hidden win-trigger socket used to open the reward door group when solved.
        sb.append(TmxXml.object(nextId++, "socket", (doorTileX - 2) * GameConstants.TILE_SIZE, (doorTileY + 2) * GameConstants.TILE_SIZE,
            GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
            TmxXml.props(
                TmxXml.prop("type", "socket"),
                TmxXml.prop("group", rewardGroup),
                TmxXml.prop("puzzleType", GraphLightsOutSystem.PUZZLE_TYPE),
                TmxXml.prop("winTrigger", "true", "bool"),
                TmxXml.prop("unlockDoorGroup", rewardGroup),
                TmxXml.prop("activated", "false", "bool")
            )));

        // Reward arena behind the gate.
        float goalX = goalTileX * GameConstants.TILE_SIZE;
        float goalY = goalTileY * GameConstants.TILE_SIZE;

        // Finale terminal in the reward arena: solving it advances to the next campaign stage.
        // Implemented as a terminal linked to a hidden finale PuzzleDoor so we can reuse PuzzleOverlaySystem.
        String finaleDoorId = "lantern_finale_terminal_" + seed;
        String puzzleId = "lantern_finale_puzzle_" + seed;

        final String finaleKey = "LANTERN";
        final String finalePlaintext = "ALLLIGHT";
        final String finaleCiphertext = com.timonipumba.level.VigenereCipher.encrypt(finalePlaintext, finaleKey);

        sb.append(TmxXml.object(nextId++, "puzzledoor", goalX, goalY,
            GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
            TmxXml.props(
                TmxXml.prop("type", "puzzledoor"),
                TmxXml.prop("id", finaleDoorId),
                TmxXml.prop("locked", "true", "bool"),
                TmxXml.prop("puzzleId", puzzleId),
                TmxXml.prop("isFinale", "true", "bool"),
                TmxXml.prop("hidden", "true", "bool"),
                TmxXml.prop("puzzleType", "cipher"),
                TmxXml.prop("ciphertext", finaleCiphertext),
                TmxXml.prop("key", "VIGENERE:" + finaleKey),
                TmxXml.prop("answer", finalePlaintext),
                TmxXml.prop("prompt", "FINAL TERMINAL: Decrypt the message. Enter the plaintext (A-Z only)."),
                TmxXml.prop("hint", "Vigenere. Use the key shown (A=0..Z).")
            )));

        sb.append(TmxXml.object(nextId++, "terminal", goalX, goalY,
            GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
            TmxXml.props(
                TmxXml.prop("type", "terminal"),
                TmxXml.prop("doorId", finaleDoorId),
                TmxXml.prop("allowHiddenDoor", "true", "bool")
            )));

        w.objectGroupClose().endMap();
        return w.build();
    }

    private String neighborsCsv(Node[] nodes, boolean[][] edge, int i) {
        List<String> out = new ArrayList<>();
        for (int j = 0; j < nodes.length; j++) {
            if (i == j) continue;
            if (edge[i][j]) out.add(nodes[j].id);
        }
        return String.join(",", out);
    }


    private static class Node {
        final String id;
        int cx;
        int cy;
        final int w;
        final int h;
        final int originX;
        final int originY;

        Node(String id, int cx, int cy, int w, int h) {
            this.id = id;
            this.cx = cx;
            this.cy = cy;
            this.w = w;
            this.h = h;
            this.originX = cx - (w / 2);
            this.originY = cy - (h / 2);
        }
    }

    private static class CropResult {
        final int[][] floor;
        final int[][] walls;
        final int width;
        final int height;
        final int minX;
        final int minY;

        CropResult(int[][] floor, int[][] walls, int width, int height, int minX, int minY) {
            this.floor = floor;
            this.walls = walls;
            this.width = width;
            this.height = height;
            this.minX = minX;
            this.minY = minY;
        }
    }
}
