package com.timonipumba.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.timonipumba.GameConstants;

import java.io.File;
import java.util.*;

// Random arena TMX generator
public class RandomArenaTmxGenerator {

    public static final int ARENA_WIDTH_TILES = 15;
    public static final int ARENA_HEIGHT_TILES = 15;

        // Corridor is split into two lanes, separated by a wall, so the player can return via
        // a different lane-symbol (avoid forced "VV"-style violations at degree-1 arenas).
        // Lane width must accommodate the player's 32x32 collision box.
        public static final int CORRIDOR_LANE_FLOOR_WIDTH_TILES = 5;
        public static final int CORRIDOR_LANE_SEPARATOR_WALL_TILES = 1;
        public static final int CORRIDOR_SIDE_WALL_WIDTH_TILES = 1;
        public static final int CORRIDOR_TOTAL_WIDTH_TILES =
            CORRIDOR_SIDE_WALL_WIDTH_TILES
                + CORRIDOR_LANE_FLOOR_WIDTH_TILES
                + CORRIDOR_LANE_SEPARATOR_WALL_TILES
                + CORRIDOR_LANE_FLOOR_WIDTH_TILES
                + CORRIDOR_SIDE_WALL_WIDTH_TILES;

    private static final int GRID_COLS = 7;
    private static final int GRID_ROWS = 7;
    private static final int TARGET_ARENA_COUNT = 10;

    private static final int CELL_SPACING_X = ARENA_WIDTH_TILES + 20;
    private static final int CELL_SPACING_Y = ARENA_HEIGHT_TILES + 20;

    private static final int TILE_FLOOR = GameConstants.FLOOR_TILE_ID;
    private static final int TILE_WALL = 936;
    private static final int TILE_GATE = GameConstants.GATE_TILE_ID;

    // Keep random spawns away from corridor entrances so the player cannot be blocked.
    private static final int DOOR_CLEARANCE_DEPTH_TILES = 3;

    private final Random random = new Random();

    private List<ArenaInfo> lastGeneratedArenas = new ArrayList<>();

    public static class ArenaInfo {
        public final String id;
        public final int index;
        public final int gridX;
        public final int gridY;
        public final int originX;
        public final int originY;

        public ArenaInfo(String id, int index, int gridX, int gridY, int originX, int originY) {
            this.id = id;
            this.index = index;
            this.gridX = gridX;
            this.gridY = gridY;
            this.originX = originX;
            this.originY = originY;
        }

        public int centerX() { return originX + ARENA_WIDTH_TILES / 2; }
        public int centerY() { return originY + ARENA_HEIGHT_TILES / 2; }
    }

    /**
     * targetPath is a local path like "desktop/maps/random_world.tmx".
     */
    public void generateAndWrite(String targetPath) {
        generateAndWrite(targetPath, System.currentTimeMillis());
    }

    /**
     * Generate a TMX using the provided seed. This is used so the dungeon theme and finale puzzle
     * can align deterministically with the same run seed.
     */
    public void generateAndWrite(String targetPath, long seed) {
        random.setSeed(seed);

        List<ArenaInfo> arenas = buildRandomArenaLayout();

        int[] bounds = computeMapBounds(arenas);
        int mapMinX = bounds[0];
        int mapMinY = bounds[1];
        int mapMaxX = bounds[2];
        int mapMaxY = bounds[3];

        int widthTiles = mapMaxX - mapMinX + 1;
        int heightTiles = mapMaxY - mapMinY + 1;

        List<ArenaInfo> shifted = new ArrayList<>();
        for (ArenaInfo a : arenas) {
            int newOriginX = a.originX - mapMinX;
            int newOriginY = a.originY - mapMinY;
            shifted.add(new ArenaInfo(a.id, a.index, a.gridX, a.gridY, newOriginX, newOriginY));
        }
        arenas = shifted;
        lastGeneratedArenas = arenas;

        int[][] floor = new int[heightTiles][widthTiles];
        int[][] walls = new int[heightTiles][widthTiles];
        int[][] symbols = new int[heightTiles][widthTiles];

        for (ArenaInfo arena : arenas) {
            buildArena(arena, walls, floor, widthTiles, heightTiles);
        }

        ArenaInfo[][] grid = new ArenaInfo[GRID_ROWS][GRID_COLS];
        for (ArenaInfo a : arenas) {
            grid[a.gridY][a.gridX] = a;
        }

        List<int[]> corridorPairs = new ArrayList<>();

        for (ArenaInfo a : arenas) {
            int gx = a.gridX;
            int gy = a.gridY;

            if (gx + 1 < GRID_COLS && grid[gy][gx + 1] != null) {
                ArenaInfo b = grid[gy][gx + 1];
                buildCorridorBetween(a, b, walls, floor, symbols, widthTiles, heightTiles);
                corridorPairs.add(new int[]{a.index, b.index});
            }
            if (gy + 1 < GRID_ROWS && grid[gy + 1][gx] != null) {
                ArenaInfo b = grid[gy + 1][gx];
                buildCorridorBetween(a, b, walls, floor, symbols, widthTiles, heightTiles);
                corridorPairs.add(new int[]{a.index, b.index});
            }
        }

        int[] enemiesPerArena = new int[arenas.size()];
        for (ArenaInfo arena : arenas) {
            if (arena.index == 0) {
                enemiesPerArena[arena.index] = 0;
            } else {
                int roll = random.nextInt(100);
                if (roll < 40) enemiesPerArena[arena.index] = 0;
                else if (roll < 75) enemiesPerArena[arena.index] = 1;
                else if (roll < 90) enemiesPerArena[arena.index] = 2;
                else enemiesPerArena[arena.index] = 3 + random.nextInt(2);
            }
        }

        String tmx = buildTmxXml(widthTiles, heightTiles, floor, walls, symbols, arenas, corridorPairs, enemiesPerArena, seed);

        // Respect absolute paths (avoid Gdx.files.local prefixing working dir when given absolute)
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

        // Ensure generated map has local copies of tileset + image so TmxMapLoader can resolve dependencies relative to the TMX
        RoguelikeTilesetAssets.copyToMapDir(fh.parent());

        fh.writeString(tmx, false, "UTF-8");
    }

    public List<ArenaInfo> getLastGeneratedArenas() {
        return new ArrayList<>(lastGeneratedArenas);
    }


    // ----- layout helpers (same as before, omitted comments for brevity) -----

    private List<ArenaInfo> buildRandomArenaLayout() {
        List<int[]> cells = new ArrayList<>();
        boolean[][] used = new boolean[GRID_ROWS][GRID_COLS];

        int startGX = GRID_COLS / 2;
        int startGY = GRID_ROWS / 2;

        cells.add(new int[]{startGX, startGY});
        used[startGY][startGX] = true;

        List<int[]> frontier = new ArrayList<>();
        frontier.add(new int[]{startGX, startGY});

        while (cells.size() < TARGET_ARENA_COUNT && !frontier.isEmpty()) {
            int[] base = frontier.remove(random.nextInt(frontier.size()));
            int bx = base[0];
            int by = base[1];

            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            shuffleArray(dirs);

            for (int[] d : dirs) {
                if (cells.size() >= TARGET_ARENA_COUNT) break;
                int nx = bx + d[0];
                int ny = by + d[1];
                if (nx < 0 || nx >= GRID_COLS || ny < 0 || ny >= GRID_ROWS) continue;
                if (used[ny][nx]) continue;
                used[ny][nx] = true;
                cells.add(new int[]{nx, ny});
                frontier.add(new int[]{nx, ny});
            }
        }

        List<ArenaInfo> arenas = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            int gx = c[0];
            int gy = c[1];
            int originX = gx * CELL_SPACING_X;
            int originY = gy * CELL_SPACING_Y;
            arenas.add(new ArenaInfo("arena_" + i, i, gx, gy, originX, originY));
        }
        return arenas;
    }

    private void shuffleArray(int[][] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private int[] computeMapBounds(List<ArenaInfo> arenas) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (ArenaInfo a : arenas) {
            int x0 = a.originX;
            int y0 = a.originY;
            int x1 = x0 + ARENA_WIDTH_TILES - 1;
            int y1 = y0 + ARENA_HEIGHT_TILES - 1;

            minX = Math.min(minX, x0 - 10);
            minY = Math.min(minY, y0 - 10);
            maxX = Math.max(maxX, x1 + 10);
            maxY = Math.max(maxY, y1 + 10);
        }

        return new int[]{minX, minY, maxX, maxY};
    }

    private void buildArena(ArenaInfo arena, int[][] walls, int[][] floor, int mapW, int mapH) {
        int x0 = arena.originX;
        int y0 = arena.originY;
        int x1 = x0 + ARENA_WIDTH_TILES - 1;
        int y1 = y0 + ARENA_HEIGHT_TILES - 1;

        for (int x = x0; x <= x1; x++) {
            if (inBounds(x, y0, mapW, mapH)) walls[y0][x] = TILE_WALL;
            if (inBounds(x, y1, mapW, mapH)) walls[y1][x] = TILE_WALL;
        }
        for (int y = y0; y <= y1; y++) {
            if (inBounds(x0, y, mapW, mapH)) walls[y][x0] = TILE_WALL;
            if (inBounds(x1, y, mapW, mapH)) walls[y][x1] = TILE_WALL;
        }

        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int y = y0 + 1; y <= y1 - 1; y++) {
                if (inBounds(x, y, mapW, mapH)) floor[y][x] = TILE_FLOOR;
            }
        }
    }

    private boolean inBounds(int x, int y, int mapW, int mapH) {
        return x >= 0 && x < mapW && y >= 0 && y < mapH;
    }

    private void buildCorridorBetween(ArenaInfo a, ArenaInfo b,
                                      int[][] walls, int[][] floor,
                                      int[][] symbols,
                                      int mapW, int mapH) {
        if (a.gridX == b.gridX) {
            buildVerticalCorridor(a, b, walls, floor, symbols, mapW, mapH);
        } else if (a.gridY == b.gridY) {
            buildHorizontalCorridor(a, b, walls, floor, symbols, mapW, mapH);
        }
    }

    private void buildHorizontalCorridor(ArenaInfo left, ArenaInfo right,
                                         int[][] walls, int[][] floor,
                                         int[][] symbols,
                                         int mapW, int mapH) {
        if (left.gridX > right.gridX) {
            ArenaInfo tmp = left;
            left = right;
            right = tmp;
        }

        int corridorYCenter = left.centerY();
        int startOffset = -(CORRIDOR_TOTAL_WIDTH_TILES / 2);
        int endOffsetExclusive = startOffset + CORRIDOR_TOTAL_WIDTH_TILES;

        int leftDoorX = left.originX + ARENA_WIDTH_TILES - 1;
        int corridorStartX = leftDoorX + 1;

        int rightDoorX = right.originX;
        int corridorEndX = rightDoorX - 1;

        corridorStartX = Math.max(0, corridorStartX);
        corridorEndX = Math.min(mapW - 1, corridorEndX);

        // Lane layout across offsets (top to bottom):
        // side-wall | lane-H floor | separator wall | lane-V floor | side-wall
        int laneHalfSpan = CORRIDOR_LANE_FLOOR_WIDTH_TILES; // lane spans are contiguous offsets
        int side = CORRIDOR_SIDE_WALL_WIDTH_TILES;
        int sep = CORRIDOR_LANE_SEPARATOR_WALL_TILES;

        for (int x = corridorStartX; x <= corridorEndX; x++) {
            for (int offset = startOffset; offset < endOffsetExclusive; offset++) {
                int y = corridorYCenter + offset;
                if (!inBounds(x, y, mapW, mapH)) continue;

                int idx = offset - startOffset; // 0..CORRIDOR_TOTAL_WIDTH_TILES-1

                boolean inSideWall = idx < side || idx >= (CORRIDOR_TOTAL_WIDTH_TILES - side);
                boolean inLaneH = idx >= side && idx < (side + laneHalfSpan);
                boolean inSeparator = idx >= (side + laneHalfSpan) && idx < (side + laneHalfSpan + sep);
                boolean inLaneV = idx >= (side + laneHalfSpan + sep) && idx < (side + laneHalfSpan + sep + laneHalfSpan);

                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    walls[y][x] = TILE_WALL;
                }
            }
        }

        // Create two separate door openings/gates aligned to the two lanes.
        int innerLeftX = leftDoorX - 1;
        int innerRightX = rightDoorX + 1;

        for (int offset = startOffset; offset < endOffsetExclusive; offset++) {
            int y = corridorYCenter + offset;
            if (!inBounds(0, y, mapW, mapH)) continue;

            int idx = offset - startOffset;
            boolean inLaneH = idx >= side && idx < (side + laneHalfSpan);
            boolean inLaneV = idx >= (side + laneHalfSpan + sep) && idx < (side + laneHalfSpan + sep + laneHalfSpan);

            if (!(inLaneH || inLaneV)) {
                continue;
            }

            if (inBounds(innerLeftX, y, mapW, mapH)) {
                walls[y][innerLeftX] = 0;
                floor[y][innerLeftX] = TILE_FLOOR;
            }
            if (inBounds(innerRightX, y, mapW, mapH)) {
                walls[y][innerRightX] = 0;
                floor[y][innerRightX] = TILE_FLOOR;
            }

            // Gate tiles only in lane openings.
            if (inBounds(leftDoorX, y, mapW, mapH)) {
                walls[y][leftDoorX] = TILE_GATE;
                floor[y][leftDoorX] = TILE_FLOOR;
            }
            if (inBounds(rightDoorX, y, mapW, mapH)) {
                walls[y][rightDoorX] = TILE_GATE;
                floor[y][rightDoorX] = TILE_FLOOR;
            }

            // No tile markers: gate labels already show 0/1 and marker gids can look like stray tiles.
        }
    }

    private void buildVerticalCorridor(ArenaInfo top, ArenaInfo bottom,
                                       int[][] walls, int[][] floor,
                                       int[][] symbols,
                                       int mapW, int mapH) {
        if (top.gridY > bottom.gridY) {
            ArenaInfo tmp = top;
            top = bottom;
            bottom = tmp;
        }

        int corridorXCenter = top.centerX();
        int startOffset = -(CORRIDOR_TOTAL_WIDTH_TILES / 2);
        int endOffsetExclusive = startOffset + CORRIDOR_TOTAL_WIDTH_TILES;

        int topDoorY = top.originY + ARENA_HEIGHT_TILES - 1;
        int corridorStartY = topDoorY + 1;

        int bottomDoorY = bottom.originY;
        int corridorEndY = bottomDoorY - 1;

        corridorStartY = Math.max(0, corridorStartY);
        corridorEndY = Math.min(mapH - 1, corridorEndY);

        int side = CORRIDOR_SIDE_WALL_WIDTH_TILES;
        int laneHalfSpan = CORRIDOR_LANE_FLOOR_WIDTH_TILES;
        int sep = CORRIDOR_LANE_SEPARATOR_WALL_TILES;

        for (int y = corridorStartY; y <= corridorEndY; y++) {
            for (int offset = startOffset; offset < endOffsetExclusive; offset++) {
                int x = corridorXCenter + offset;
                if (!inBounds(x, y, mapW, mapH)) continue;

                int idx = offset - startOffset;

                boolean inSideWall = idx < side || idx >= (CORRIDOR_TOTAL_WIDTH_TILES - side);
                boolean inLaneH = idx >= side && idx < (side + laneHalfSpan);
                boolean inSeparator = idx >= (side + laneHalfSpan) && idx < (side + laneHalfSpan + sep);
                boolean inLaneV = idx >= (side + laneHalfSpan + sep) && idx < (side + laneHalfSpan + sep + laneHalfSpan);

                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    walls[y][x] = TILE_WALL;
                }
            }
        }

        int innerTopY = topDoorY - 1;
        int innerBottomY = bottomDoorY + 1;

        for (int offset = startOffset; offset < endOffsetExclusive; offset++) {
            int x = corridorXCenter + offset;
            if (!inBounds(x, 0, mapW, mapH)) continue;

            int idx = offset - startOffset;
            boolean inLaneH = idx >= side && idx < (side + laneHalfSpan);
            boolean inLaneV = idx >= (side + laneHalfSpan + sep) && idx < (side + laneHalfSpan + sep + laneHalfSpan);
            if (!(inLaneH || inLaneV)) continue;

            if (inBounds(x, innerTopY, mapW, mapH)) {
                walls[innerTopY][x] = 0;
                floor[innerTopY][x] = TILE_FLOOR;
            }
            if (inBounds(x, innerBottomY, mapW, mapH)) {
                walls[innerBottomY][x] = 0;
                floor[innerBottomY][x] = TILE_FLOOR;
            }

            if (inBounds(x, topDoorY, mapW, mapH)) {
                walls[topDoorY][x] = TILE_GATE;
                floor[topDoorY][x] = TILE_FLOOR;
            }
            if (inBounds(x, bottomDoorY, mapW, mapH)) {
                walls[bottomDoorY][x] = TILE_GATE;
                floor[bottomDoorY][x] = TILE_FLOOR;
            }

            // No tile markers: gate labels already show 0/1 and marker gids can look like stray tiles.
        }
    }

    private String buildTmxXml(int widthTiles, int heightTiles,
                               int[][] floor, int[][] walls, int[][] symbols,
                               List<ArenaInfo> arenas,
                               List<int[]> corridorPairs,
                               int[] enemiesPerArena,
                               long seed) {

        TmxMapWriter w = TmxMapWriter.create(widthTiles, heightTiles, "1.11.2")
            .withNextIds(5, 200)
            .startMap()
            .tileset("tilesets/roguelikeSheet_magenta.tsx")
            .layerCsv(1, "floor", floor)
            .layerCsv(2, "walls", walls)
            .layerCsv(3, GameConstants.SYMBOLS_LAYER_NAME, symbols)
            .objectGroupOpen(4, "objects");

        StringBuilder sb = w.sb();
        int nextObjectId = 1;

        // Player
        if (!arenas.isEmpty()) {
            ArenaInfo a0 = arenas.get(0);
            float worldX = a0.centerX() * GameConstants.TILE_SIZE;
            float worldY = a0.centerY() * GameConstants.TILE_SIZE;
            sb.append("  <object id=\"").append(nextObjectId).append("\" name=\"player\" x=\"").append(worldX)
              .append("\" y=\"").append(worldY).append("\" width=\"20\" height=\"32\">\n");
            sb.append("   <properties>\n");
            sb.append("    <property name=\"type\" value=\"player\"/>\n");
            sb.append("   </properties>\n");
            sb.append("  </object>\n");
            nextObjectId++;
        }

                // Arena bounds: include the full footprint (incl. 1-tile border) so doorway transitions count.
            for (ArenaInfo arena : arenas) {
                    float x = arena.originX * GameConstants.TILE_SIZE;
                    float y = arena.originY * GameConstants.TILE_SIZE;
                    float arenaWidthPx = ARENA_WIDTH_TILES * GameConstants.TILE_SIZE;
                    float h = ARENA_HEIGHT_TILES * GameConstants.TILE_SIZE;

                        sb.append("  <object id=\"").append(nextObjectId++).append("\" name=\"arena_bounds\" x=\"")
                            .append(x).append("\" y=\"").append(y)
                            .append("\" width=\"").append(arenaWidthPx)
                            .append("\" height=\"").append(h).append("\">\n");
                        sb.append("   <properties>\n");
                        sb.append("    <property name=\"type\" value=\"arena_bounds\"/>\n");
                        sb.append("    <property name=\"arenaId\" value=\"").append(arena.id).append("\"/>\n");
                        if (arena.index == 0) {
                            sb.append("    <property name=\"isStart\" type=\"bool\" value=\"true\"/>\n");
                        }
                        sb.append("   </properties>\n");
                        sb.append("  </object>\n");
                }

        // Enemies
        Map<String, List<int[]>> doorwayClearanceByArena = computeDoorwayClearanceRects(arenas, corridorPairs);
        for (ArenaInfo arena : arenas) {
            int count = enemiesPerArena[arena.index];
            if (count <= 0) continue;

            int placed = 0;
            int attempts = 0;
            while (placed < count && attempts++ < 200) {
                int x = arena.originX + 2 + random.nextInt(ARENA_WIDTH_TILES - 4);
                int y = arena.originY + 2 + random.nextInt(ARENA_HEIGHT_TILES - 4);
                if (isInAnyRect(x, y, doorwayClearanceByArena.get(arena.id))) {
                    continue;
                }
                float worldX = x * GameConstants.TILE_SIZE;
                float worldY = y * GameConstants.TILE_SIZE;

                sb.append("  <object id=\"").append(nextObjectId).append("\" name=\"enemy\" x=\"")
                  .append(worldX).append("\" y=\"").append(worldY)
                  .append("\" width=\"").append(GameConstants.TILE_SIZE)
                  .append("\" height=\"").append(GameConstants.TILE_SIZE).append("\">\n");
                sb.append("   <properties>\n");
                sb.append("    <property name=\"type\" value=\"enemy\"/>\n");
                sb.append("    <property name=\"enemy_type\" value=\"DEFAULT\"/>\n");
                sb.append("    <property name=\"arenaId\" value=\"").append(arena.id).append("\"/>\n");
                sb.append("   </properties>\n");
                sb.append("  </object>\n");
                nextObjectId++;
                placed++;
            }
        }

        // Gates: align objects with gate tile bands at arena doorways.
        // Two per doorway (one for each lane): traversalSymbol = H or V.
        for (int[] pair : corridorPairs) {
            ArenaInfo from = arenas.get(pair[0]);
            ArenaInfo to = arenas.get(pair[1]);

            boolean isVertical = (from.gridX == to.gridX);
            boolean isHorizontal = (from.gridY == to.gridY);

            if (isVertical) {
                ArenaInfo top = (from.gridY <= to.gridY) ? from : to;
                ArenaInfo bottom = (top == from) ? to : from;

                int corridorXCenter = top.centerX();
                int gateWidthTiles = CORRIDOR_LANE_FLOOR_WIDTH_TILES;
                int gateHeightTiles = 1;

                int topDoorY = top.originY + ARENA_HEIGHT_TILES - 1;
                int bottomDoorY = bottom.originY;

                // Two lane X offsets: left lane (H) and right lane (V)
                int side = CORRIDOR_SIDE_WALL_WIDTH_TILES;
                int lane = CORRIDOR_LANE_FLOOR_WIDTH_TILES;
                int sep = CORRIDOR_LANE_SEPARATOR_WALL_TILES;

                int laneHStartX = corridorXCenter + (-(CORRIDOR_TOTAL_WIDTH_TILES / 2)) + side;
                int laneVStartX = laneHStartX + lane + sep;

                float gateXH = laneHStartX * GameConstants.TILE_SIZE;
                float gateXV = laneVStartX * GameConstants.TILE_SIZE;

                // Gate on the top arena doorway belongs to the top arena (source=top, target=bottom)
                appendGateObject(sb, nextObjectId++, "gate_" + top.id + "_to_" + bottom.id + "_top_H",
                    gateXH, topDoorY * GameConstants.TILE_SIZE,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    top.id, bottom.id,
                    "H");

                appendGateObject(sb, nextObjectId++, "gate_" + top.id + "_to_" + bottom.id + "_top_V",
                    gateXV, topDoorY * GameConstants.TILE_SIZE,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    top.id, bottom.id,
                    "V");

                // Gate on the bottom arena doorway belongs to the bottom arena (source=bottom, target=top)
                appendGateObject(sb, nextObjectId++, "gate_" + bottom.id + "_to_" + top.id + "_bottom_H",
                    gateXH, bottomDoorY * GameConstants.TILE_SIZE,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    bottom.id, top.id,
                    "H");

                appendGateObject(sb, nextObjectId++, "gate_" + bottom.id + "_to_" + top.id + "_bottom_V",
                    gateXV, bottomDoorY * GameConstants.TILE_SIZE,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    bottom.id, top.id,
                    "V");

            } else if (isHorizontal) {
                ArenaInfo left = (from.gridX <= to.gridX) ? from : to;
                ArenaInfo right = (left == from) ? to : from;

                int corridorYCenter = left.centerY();
                int gateWidthTiles = 1;
                int gateHeightTiles = CORRIDOR_LANE_FLOOR_WIDTH_TILES;

                int leftDoorX = left.originX + ARENA_WIDTH_TILES - 1;
                int rightDoorX = right.originX;

                int side = CORRIDOR_SIDE_WALL_WIDTH_TILES;
                int lane = CORRIDOR_LANE_FLOOR_WIDTH_TILES;
                int sep = CORRIDOR_LANE_SEPARATOR_WALL_TILES;

                int laneHStartY = corridorYCenter + (-(CORRIDOR_TOTAL_WIDTH_TILES / 2)) + side;
                int laneVStartY = laneHStartY + lane + sep;

                float gateYH = laneHStartY * GameConstants.TILE_SIZE;
                float gateYV = laneVStartY * GameConstants.TILE_SIZE;

                // Gate on the left arena doorway belongs to the left arena (source=left, target=right)
                appendGateObject(sb, nextObjectId++, "gate_" + left.id + "_to_" + right.id + "_left_H",
                    leftDoorX * GameConstants.TILE_SIZE, gateYH,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    left.id, right.id,
                    "H");

                appendGateObject(sb, nextObjectId++, "gate_" + left.id + "_to_" + right.id + "_left_V",
                    leftDoorX * GameConstants.TILE_SIZE, gateYV,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    left.id, right.id,
                    "V");

                // Gate on the right arena doorway belongs to the right arena (source=right, target=left)
                appendGateObject(sb, nextObjectId++, "gate_" + right.id + "_to_" + left.id + "_right_H",
                    rightDoorX * GameConstants.TILE_SIZE, gateYH,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    right.id, left.id,
                    "H");

                appendGateObject(sb, nextObjectId++, "gate_" + right.id + "_to_" + left.id + "_right_V",
                    rightDoorX * GameConstants.TILE_SIZE, gateYV,
                    gateWidthTiles * GameConstants.TILE_SIZE,
                    gateHeightTiles * GameConstants.TILE_SIZE,
                    right.id, left.id,
                    "V");

            } else {
                int midX = (from.centerX() + to.centerX()) / 2;
                int midY = (from.centerY() + to.centerY()) / 2;
                appendGateObject(sb, nextObjectId++, "gate_" + from.id + "_to_" + to.id,
                        (midX - 0.5f) * GameConstants.TILE_SIZE,
                        (midY - 0.5f) * GameConstants.TILE_SIZE,
                        GameConstants.TILE_SIZE,
                        GameConstants.TILE_SIZE,
                        from.id, to.id);
            }
        }

                // Finale: place a locked puzzle door + terminal in the last arena.
                // These are emitted as hidden and are revealed only if the traversal goal is met.
                if (!arenas.isEmpty()) {
                        ArenaInfo end = arenas.get(arenas.size() - 1);
                        int doorTileX = end.centerX();
                        int doorTileY = end.centerY();
                        float doorX = doorTileX * GameConstants.TILE_SIZE;
                        float doorY = doorTileY * GameConstants.TILE_SIZE;

                    String puzzleId = "finale_cipher_" + seed;

                    // Finale puzzle: classic cipher-style riddle (non-DFA).
                        sb.append("  <object id=\"").append(nextObjectId++).append("\" name=\"puzzledoor\" x=\"")
                            .append(doorX).append("\" y=\"").append(doorY).append("\" width=\"")
                            .append(GameConstants.TILE_SIZE).append("\" height=\"").append(GameConstants.TILE_SIZE).append("\">\n");
                        sb.append("   <properties>\n");
                        sb.append("    <property name=\"type\" value=\"puzzledoor\"/>\n");
                        sb.append("    <property name=\"id\" value=\"finale_door\"/>\n");
                        sb.append("    <property name=\"locked\" type=\"bool\" value=\"true\"/>\n");
                        sb.append("    <property name=\"isFinale\" type=\"bool\" value=\"true\"/>\n");
                        sb.append("    <property name=\"hidden\" type=\"bool\" value=\"true\"/>\n");
                        sb.append("    <property name=\"puzzleId\" value=\"").append(puzzleId).append("\"/>\n");
                    sb.append("    <property name=\"puzzleType\" value=\"cipher\"/>\n");
                    sb.append("    <property name=\"prompt\" value=\"Finale: decrypt the message. Enter the plaintext (letters/numbers only).\"/>\n");
                    sb.append("    <property name=\"ciphertext\" value=\"UWTKRX\"/>\n");
                    sb.append("    <property name=\"key\" value=\"CAESAR+5\"/>\n");
                    sb.append("    <property name=\"answer\" value=\"PROFMS\"/>\n");
                    sb.append("    <property name=\"hint\" value=\"Shift each letter 5 back (Caesar).\"/>\n");
                        sb.append("   </properties>\n");
                        sb.append("  </object>\n");

                        // Terminal placed one tile above the door, linked via doorId.
                        sb.append("  <object id=\"").append(nextObjectId++).append("\" name=\"terminal\" x=\"")
                            .append(doorTileX * GameConstants.TILE_SIZE).append("\" y=\"")
                            .append((doorTileY + 1) * GameConstants.TILE_SIZE)
                            .append("\" width=\"").append(GameConstants.TILE_SIZE)
                            .append("\" height=\"").append(GameConstants.TILE_SIZE).append("\">\n");
                        sb.append("   <properties>\n");
                        sb.append("    <property name=\"type\" value=\"terminal\"/>\n");
                        sb.append("    <property name=\"doorId\" value=\"finale_door\"/>\n");
                        sb.append("   </properties>\n");
                        sb.append("  </object>\n");
                }

        w.objectGroupClose().endMap();
        return w.build();
    }

    private Map<String, List<int[]>> computeDoorwayClearanceRects(List<ArenaInfo> arenas, List<int[]> corridorPairs) {
        Map<Integer, ArenaInfo> byIndex = new HashMap<>();
        for (ArenaInfo a : arenas) {
            byIndex.put(a.index, a);
        }

        Map<String, List<int[]>> out = new HashMap<>();
        for (ArenaInfo a : arenas) {
            out.put(a.id, new ArrayList<>());
        }

        if (corridorPairs == null) return out;

        int totalW = CORRIDOR_TOTAL_WIDTH_TILES;
        int startOffset = -(totalW / 2);
        int side = CORRIDOR_SIDE_WALL_WIDTH_TILES;
        int lane = CORRIDOR_LANE_FLOOR_WIDTH_TILES;
        int sep = CORRIDOR_LANE_SEPARATOR_WALL_TILES;

        // Span across both lanes + separator (exclude the outer side walls).
        int bandStart = startOffset + side;
        int bandLen = (lane * 2) + sep;

        for (int[] pair : corridorPairs) {
            if (pair == null || pair.length < 2) continue;
            ArenaInfo from = byIndex.get(pair[0]);
            ArenaInfo to = byIndex.get(pair[1]);
            if (from == null || to == null) continue;

            if (from.gridY == to.gridY) {
                // Horizontal corridor.
                ArenaInfo left = (from.gridX <= to.gridX) ? from : to;
                ArenaInfo right = (left == from) ? to : from;

                int cy = left.centerY();
                int y0 = cy + bandStart;
                int y1 = y0 + bandLen - 1;

                // Right wall of left arena.
                int doorXLeft = left.originX + ARENA_WIDTH_TILES - 1;
                addRect(out.get(left.id), doorXLeft - DOOR_CLEARANCE_DEPTH_TILES, y0, doorXLeft - 1, y1);

                // Left wall of right arena.
                int doorXRight = right.originX;
                addRect(out.get(right.id), doorXRight + 1, y0, doorXRight + DOOR_CLEARANCE_DEPTH_TILES, y1);
            } else if (from.gridX == to.gridX) {
                // Vertical corridor.
                ArenaInfo top = (from.gridY <= to.gridY) ? from : to;
                ArenaInfo bottom = (top == from) ? to : from;

                int cx = top.centerX();
                int x0 = cx + bandStart;
                int x1 = x0 + bandLen - 1;

                // Bottom wall of top arena.
                int doorYTop = top.originY + ARENA_HEIGHT_TILES - 1;
                addRect(out.get(top.id), x0, doorYTop - DOOR_CLEARANCE_DEPTH_TILES, x1, doorYTop - 1);

                // Top wall of bottom arena.
                int doorYBottom = bottom.originY;
                addRect(out.get(bottom.id), x0, doorYBottom + 1, x1, doorYBottom + DOOR_CLEARANCE_DEPTH_TILES);
            }
        }

        return out;
    }

    private static void addRect(List<int[]> rects, int x0, int y0, int x1, int y1) {
        if (rects == null) return;
        rects.add(new int[]{Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)});
    }

    private static boolean isInAnyRect(int x, int y, List<int[]> rects) {
        if (rects == null || rects.isEmpty()) return false;
        for (int[] r : rects) {
            if (r == null || r.length < 4) continue;
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) {
                return true;
            }
        }
        return false;
    }

    private void appendGateObject(StringBuilder sb, int id, String name,
                                  float x, float y, float width, float height,
                                  String sourceArenaId, String targetArenaId,
                                  String traversalSymbol) {
        sb.append("  <object id=\"").append(id).append("\" name=\"").append(name)
          .append("\" x=\"").append(x).append("\" y=\"").append(y)
          .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n");
        sb.append("   <properties>\n");
        sb.append("    <property name=\"type\" value=\"gate\"/>\n");
        sb.append("    <property name=\"sourceArenaId\" value=\"").append(sourceArenaId).append("\"/>\n");
        sb.append("    <property name=\"targetArenaId\" value=\"").append(targetArenaId).append("\"/>\n");
        if (traversalSymbol != null && !traversalSymbol.isEmpty()) {
            sb.append("    <property name=\"traversalSymbol\" value=\"").append(traversalSymbol).append("\"/>\n");
        }
        sb.append("   </properties>\n");
        sb.append("  </object>\n");
    }

    private void appendGateObject(StringBuilder sb, int objectId, String name,
                                   float x, float y,
                                   float width, float height,
                                   String sourceArenaId, String targetArenaId) {
        sb.append("  <object id=\"").append(objectId).append("\" name=\"")
          .append(name).append("\" x=\"").append(x)
          .append("\" y=\"").append(y).append("\" ")
          .append("width=\"").append(width).append("\" ")
          .append("height=\"").append(height).append("\">\n");
        sb.append("   <properties>\n");
        sb.append("    <property name=\"type\" value=\"gate\"/>\n");
        sb.append("    <property name=\"sourceArenaId\" value=\"").append(sourceArenaId).append("\"/>\n");
        sb.append("    <property name=\"targetArenaId\" value=\"").append(targetArenaId).append("\"/>\n");
        sb.append("   </properties>\n");
        sb.append("  </object>\n");
    }

}
