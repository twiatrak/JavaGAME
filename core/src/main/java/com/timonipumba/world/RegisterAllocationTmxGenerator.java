package com.timonipumba.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.timonipumba.GameConstants;
import com.timonipumba.systems.RegisterAllocationSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a standalone TMX for a non-terminal riddle:
 * Register allocation as 3-coloring of an interference graph.
 *
 * Map style goal:
 * - Discrete arenas connected by long corridors
 * - Blank/void outside the walkable structure (no giant filled wall field)
 */
public class RegisterAllocationTmxGenerator {

    private static final int TILE_FLOOR = GameConstants.FLOOR_TILE_ID;
    private static final int TILE_WALL = 936;
    private static final int TILE_GATE = GameConstants.GATE_TILE_ID;

    // Build on a large blank canvas then crop.
    private static final int CANVAS_W = 220;
    private static final int CANVAS_H = 220;
    private static final int CROP_MARGIN = 3;

    // Arena sizing (tiles)
    private static final int ARENA_MIN_W = 15;
    private static final int ARENA_MAX_W = 19;
    private static final int ARENA_MIN_H = 13;
    private static final int ARENA_MAX_H = 17;

    // Corridor width (tiles)
    private static final int CORRIDOR_WIDTH = 5;

    private final Random random = new Random();

    public void generateAndWrite(String targetPath, long seed) {
        random.setSeed(seed);

        int[][] floorCanvas = new int[CANVAS_H][CANVAS_W];

        // Place puzzle arenas in a ring; edges are local to reduce crossings.
        int centerX = CANVAS_W / 2;
        int centerY = CANVAS_H / 2;
        int radius = 55;

        Node[] nodes = new Node[10];
        for (int i = 0; i < nodes.length; i++) {
            double theta = (Math.PI * 2.0 * i) / nodes.length;
            int cx = centerX + (int) Math.round(radius * Math.cos(theta));
            int cy = centerY + (int) Math.round(radius * Math.sin(theta));

            int w = makeOdd(randBetween(ARENA_MIN_W, ARENA_MAX_W));
            int h = makeOdd(randBetween(ARENA_MIN_H, ARENA_MAX_H));
            nodes[i] = new Node("var_" + i, cx, cy, w, h);
        }

        // Graph: ring + two short chords to force 3-coloring via triangles.
        boolean[][] edge = new boolean[nodes.length][nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            int j = (i + 1) % nodes.length;
            edge[i][j] = edge[j][i] = true;
        }
        edge[0][2] = edge[2][0] = true; // triangle 0-1-2
        edge[5][7] = edge[7][5] = true; // triangle 5-6-7

        // Paint arenas
        for (Node n : nodes) {
            paintArenaInterior(floorCanvas, n);
        }

        // Paint corridors for edges
        for (int i = 0; i < nodes.length; i++) {
            for (int j = i + 1; j < nodes.length; j++) {
                if (!edge[i][j]) continue;
                paintLCorridor(floorCanvas, nodes[i].cx, nodes[i].cy, nodes[j].cx, nodes[j].cy);
            }
        }

        // Start and reward arenas
        Node startArena = new Node(
                "start",
                centerX - radius - 45,
                centerY + 10,
                makeOdd(randBetween(ARENA_MIN_W, ARENA_MAX_W)),
                makeOdd(randBetween(ARENA_MIN_H, ARENA_MAX_H))
        );
        Node rewardArena = new Node(
                "reward",
                centerX + radius + 45,
                centerY + 10,
                makeOdd(randBetween(ARENA_MIN_W, ARENA_MAX_W)),
                makeOdd(randBetween(ARENA_MIN_H, ARENA_MAX_H))
        );
        paintArenaInterior(floorCanvas, startArena);
        paintArenaInterior(floorCanvas, rewardArena);

        // Connect start -> node 0
        paintLCorridor(floorCanvas, startArena.cx, startArena.cy, nodes[0].cx, nodes[0].cy);

        // Connect node 5 -> reward; place a door on that corridor.
        int doorTileX;
        int doorTileY;
        {
            int x0 = nodes[5].cx;
            int y0 = nodes[5].cy;
            int x1 = rewardArena.cx;
            int y1 = rewardArena.cy;

            // Deterministic-ish routing for stable gate placement.
            int minX = Math.min(x0, x1);
            int maxX = Math.max(x0, x1);
            int midX = clamp((x0 + x1) / 2 + 8, minX, maxX);
            paintCorridorHorizontal(floorCanvas, y0, x0, midX);
            paintCorridorVertical(floorCanvas, midX, y0, y1);
            paintCorridorHorizontal(floorCanvas, y1, midX, x1);

            // Put the gate just before entering the reward arena (on the final horizontal segment).
            int rewardHalfW = Math.max(1, rewardArena.w / 2);
            doorTileX = clamp(x1 - (rewardHalfW + 2), 1, CANVAS_W - 2);
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
            throw new IllegalStateException("RegisterAllocationTmxGenerator: nothing generated");
        }

        // Shift objects into cropped space.
        int shiftX = -cropped.minX;
        int shiftY = -cropped.minY;
        int startTileX = startArena.cx + shiftX;
        int startTileY = startArena.cy + shiftY;
        int rewardTileX = rewardArena.cx + shiftX;
        int rewardTileY = rewardArena.cy + shiftY;
        int doorTileXCropped = doorTileX + shiftX;
        int doorTileYCropped = doorTileY + shiftY;

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

        String tmx = buildTmxXml(
                cropped.floor,
                cropped.walls,
                cropped.width,
                cropped.height,
                nodes,
                edge,
                seed,
                startTileX,
                startTileY,
            rewardTileX,
            rewardTileY,
                doorTileXCropped,
                doorTileYCropped
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

    private int makeOdd(int v) {
        return (v % 2 == 0) ? (v + 1) : v;
    }

    private int randBetween(int lo, int hi) {
        return lo + random.nextInt(Math.max(1, (hi - lo + 1)));
    }

    private void paintArenaInterior(int[][] floor, Node arena) {
        // Leave a 1-tile border empty; walls will be generated later.
        int x0 = arena.originX;
        int y0 = arena.originY;
        int x1 = x0 + arena.w - 1;
        int y1 = y0 + arena.h - 1;

        for (int y = y0 + 1; y <= y1 - 1; y++) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                setFloor(floor, x, y);
            }
        }

        // Ensure corridor endpoints always land on floor.
        setFloor(floor, arena.cx, arena.cy);
    }

    private void paintLCorridor(int[][] floor, int x0, int y0, int x1, int y1) {
        boolean horizFirst = random.nextBoolean();
        if (horizFirst) {
            paintCorridorHorizontal(floor, y0, x0, x1);
            paintCorridorVertical(floor, x1, y0, y1);
        } else {
            paintCorridorVertical(floor, x0, y0, y1);
            paintCorridorHorizontal(floor, y1, x0, x1);
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

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

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

        if (minX == Integer.MAX_VALUE) return null;

        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(w - 1, maxX + margin);
        maxY = Math.min(h - 1, maxY + margin);

        int outW = (maxX - minX) + 1;
        int outH = (maxY - minY) + 1;

        int[][] outFloor = new int[outH][outW];
        int[][] outWalls = new int[outH][outW];
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                outFloor[y][x] = floor[minY + y][minX + x];
                outWalls[y][x] = walls[minY + y][minX + x];
            }
        }

        return new CropResult(outFloor, outWalls, outW, outH, minX, minY);
    }

    private String buildTmxXml(int[][] floor, int[][] walls, int width, int height, Node[] nodes, boolean[][] edge,
                               long seed, int startX, int startY, int rewardX, int rewardY, int doorTileX, int doorTileY) {
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

        // No register token pickups needed for this riddle: the player simply colors sockets.

        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            String neighborsCsv = neighborsCsv(nodes, edge, i);
            sb.append(TmxXml.object(nextId++, "socket", n.cx * GameConstants.TILE_SIZE, n.cy * GameConstants.TILE_SIZE,
                GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
                TmxXml.props(
                    TmxXml.prop("type", "socket"),
                    TmxXml.prop("puzzleType", RegisterAllocationSystem.PUZZLE_TYPE),
                    TmxXml.prop("nodeId", n.id),
                    TmxXml.prop("neighbors", neighborsCsv),
                    TmxXml.prop("consume", "false", "bool")
                )));
        }

        final String rewardGroup = "register_reward_" + seed;
        // Gate spans the corridor width so the reward arena can't be reached without solving.
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

            sb.append(TmxXml.object(nextId++, "socket", (doorTileX - 2) * GameConstants.TILE_SIZE, (doorTileY + 2) * GameConstants.TILE_SIZE,
                GameConstants.TILE_SIZE, GameConstants.TILE_SIZE,
                TmxXml.props(
                    TmxXml.prop("type", "socket"),
                    TmxXml.prop("group", rewardGroup),
                    TmxXml.prop("puzzleType", RegisterAllocationSystem.PUZZLE_TYPE),
                    TmxXml.prop("winTrigger", "true", "bool"),
                    TmxXml.prop("unlockDoorGroup", rewardGroup),
                    TmxXml.prop("activated", "false", "bool")
                )));

        // Finale terminal in the reward arena: solving it completes the level.
        // Implemented as a terminal linked to a hidden finale PuzzleDoor so we can reuse PuzzleOverlaySystem.
        float goalX = rewardX * GameConstants.TILE_SIZE;
        float goalY = rewardY * GameConstants.TILE_SIZE;
        String finaleDoorId = "register_finale_terminal_" + seed;
        String puzzleId = "register_finale_puzzle_" + seed;

        // Finale: a Vigenere cipher (keeps the ending feeling like a terminal hack, not a checkbox).
        // Answer normalization strips whitespace and uppercases.
        final String finaleKey = "REGISTER";
        final String finalePlaintext = "THREECOLORS";
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
                TmxXml.prop("hint", "Vigenere. Use the key shown (A=0..Z)." )
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

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
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
