package com.timonipumba.world;

import com.badlogic.gdx.files.FileHandle;
import com.timonipumba.GameConstants;
import com.timonipumba.level.VigenereCipher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Generates a standalone TMX for the Algebra Forge riddle. */
public class AlgebraForgeTmxGenerator {

    // Crown (glyph_7) should require at least this many forge steps from start tokens.
    private static final int MIN_CROWN_CRAFT_STEPS = 3;

    private static final int TILE_FLOOR = GameConstants.FLOOR_TILE_ID;
    private static final int TILE_WALL = 936;

    // Build on a blank canvas then crop.
    private static final int CANVAS_W = 180;
    private static final int CANVAS_H = 160;
    private static final int CROP_MARGIN = 1;

    private static final int TILE_GATE = GameConstants.GATE_TILE_ID;

    // Rectangular arenas connected with 2-lane corridors.
    private static final int ARENA_W = 28;
    private static final int ARENA_H = 18;

    private static final int CORRIDOR_SIDE_WALL = 1;
    private static final int CORRIDOR_LANE_W = 3;
    // Separator wall was only useful for traversal lane selection; Algebra Forge doesn't need it.
    private static final int CORRIDOR_SEPARATOR_WALL = 0;
    private static final int CORRIDOR_TOTAL_W = CORRIDOR_SIDE_WALL + CORRIDOR_LANE_W + CORRIDOR_SEPARATOR_WALL + CORRIDOR_LANE_W + CORRIDOR_SIDE_WALL;

    // Keep arena clutter away from doorways/corridor entrances.
    private static final int DOOR_CLEARANCE_TILES = 4;

    public void generateAndWrite(String outputPath, long seed) {
        String tmx = generate(seed);
        write(outputPath, tmx);
    }

    public String generate(long seed) {
        Random rng = new Random(seed ^ 0xA17E_BA5E);

        int[][] floor = new int[CANVAS_H][CANVAS_W];
        int[][] walls = new int[CANVAS_H][CANVAS_W];

        // Grid-ish placement.
        ArenaDef start = new ArenaDef("arena_0", 0, 10, 10);
        ArenaDef a1 = new ArenaDef("arena_1", 1, 70, 10);
        ArenaDef a2 = new ArenaDef("arena_2", 2, 130, 10);
        ArenaDef a3 = new ArenaDef("arena_3", 3, 10, 70);
        ArenaDef lab = new ArenaDef("arena_4", 4, 70, 70);
        ArenaDef a5 = new ArenaDef("arena_5", 5, 130, 70);
        // Offset so the vault approach corridor turns.
        ArenaDef vault = new ArenaDef("arena_6", 6, 110, 120);

        List<ArenaDef> arenas = List.of(start, a1, a2, a3, lab, a5, vault);

        for (ArenaDef a : arenas) {
            stampArena(a, floor, walls);
        }

        // Corridor + normal gates (two lanes per doorway).
        List<GateObj> normalGates = new ArrayList<>();
        carveHorizontalConnection(start, a1, floor, walls, normalGates);
        carveHorizontalConnection(a1, a2, floor, walls, normalGates);
        carveVerticalConnection(start, a3, floor, walls, normalGates);
        carveVerticalConnection(a1, lab, floor, walls, normalGates);
        carveVerticalConnection(a2, a5, floor, walls, normalGates);
        carveHorizontalConnection(a3, lab, floor, walls, normalGates);
        carveHorizontalConnection(lab, a5, floor, walls, normalGates);

        // Vault connection with a locked doorway.
        List<GateObj> vaultRiddleGates = new ArrayList<>();
        carveDoglegConnectionWithVaultLock(a5, vault, floor, walls, normalGates, vaultRiddleGates);

        // Add interior clutter after corridors/gates.
        List<GateObj> allGatesForClearance = new ArrayList<>(normalGates);
        allGatesForClearance.addAll(vaultRiddleGates);
        addPillars(a1, floor, walls, rng, 3, allGatesForClearance);
        addPillars(a2, floor, walls, rng, 3, allGatesForClearance);
        addPillars(a3, floor, walls, rng, 3, allGatesForClearance);
        addPillars(lab, floor, walls, rng, 4, allGatesForClearance);
        addPillars(a5, floor, walls, rng, 3, allGatesForClearance);
        addPillars(vault, floor, walls, rng, 2, allGatesForClearance);

        // Crop to bounding box.
        Crop crop = cropToContent(floor, walls, CROP_MARGIN);
        int shiftX = -crop.offsetX;
        int shiftY = -crop.offsetY;

        String opId = "cathedral_" + (seed & 0xFFFF);
        String groupVault = "algebra_vault_" + seed;

        int startCX = start.centerX();
        int startCY = start.centerY();

        // Socket in the vault-approach corridor.
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int laneHStart = startOffset + CORRIDOR_SIDE_WALL;

        int socketX = vault.centerX() + laneHStart + (CORRIDOR_LANE_W / 2);
        int socketY = (vault.y - 1) - 2; // two tiles before the vault doorway band

        int[] safeSocket = findSafeSpotNear(socketX, socketY, walls, 6);
        socketX = safeSocket[0];
        socketY = safeSocket[1];

        // Make sure socket tile is floor.
        if (socketY >= 0 && socketY < CANVAS_H && socketX >= 0 && socketX < CANVAS_W) {
            floor[socketY][socketX] = TILE_FLOOR;
            walls[socketY][socketX] = 0;
        }

        StringBuilder objects = new StringBuilder();

        // Player
        objects.append(objPlayer("player", startCX + shiftX, startCY + shiftY));

        // Arena bounds for minimap + arena gating
        for (ArenaDef a : arenas) {
            objects.append(objArenaBounds(a, shiftX, shiftY));
        }

        // Enemies (optional)
        List<GateObj> gateClearance = new ArrayList<>(normalGates);
        gateClearance.addAll(vaultRiddleGates);
        for (ArenaDef a : arenas) {
            int count;
            if (a.index == 0) {
                count = 0;
            } else if (a.index == vault.index) {
                count = (rng.nextInt(100) < 60) ? 0 : 1;
            } else {
                int roll = rng.nextInt(100);
                if (roll < 35) count = 0;
                else if (roll < 75) count = 1;
                else count = 2;
            }

            int placed = 0;
            int attempts = 0;
            while (placed < count && attempts++ < 200) {
                int ex = a.x + 2 + rng.nextInt(ARENA_W - 4);
                int ey = a.y + 2 + rng.nextInt(ARENA_H - 4);
                if (walls[ey][ex] != 0) continue;
                if (!hasPlusClearance(ex, ey, walls)) continue;
                if (isNearAnyGate(ex, ey, gateClearance, DOOR_CLEARANCE_TILES)) continue;
                objects.append(objEnemy(ex + shiftX, ey + shiftY, a.id));
                placed++;
            }
        }

        // Starting tokens: ensure Crown (glyph_7) is not one-step craftable.
        List<Integer> startingGlyphs = chooseStartingGlyphsForDifficulty(opId, rng, 6, MIN_CROWN_CRAFT_STEPS);

        int[] p;
        // Spawn pattern: 2 in start, 1 in four arenas.
        p = findSafeSpotInArena(start, startCX + 2, startCY + 1, walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(0), p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(start, startCX + 2, startCY - 1, walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(1), p[0] + shiftX, p[1] + shiftY));

        p = findSafeSpotInArena(a1, a1.centerX() - 3, a1.centerY() + 2, walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(2), p[0] + shiftX, p[1] + shiftY));

        p = findSafeSpotInArena(a2, a2.centerX() + 3, a2.centerY() + 2, walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(3), p[0] + shiftX, p[1] + shiftY));

        p = findSafeSpotInArena(lab, lab.centerX() - 4, lab.centerY(), walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(4), p[0] + shiftX, p[1] + shiftY));

        p = findSafeSpotInArena(a3, a3.centerX() + 4, a3.centerY(), walls);
        objects.append(objToken("glyph_" + startingGlyphs.get(5), p[0] + shiftX, p[1] + shiftY));

        // Vault reward: exclude Knot (glyph_6).
        int[] vaultRewardCandidates = new int[] {0, 1, 2, 3, 4, 5};
        int vaultRewardGlyph = vaultRewardCandidates[rng.nextInt(vaultRewardCandidates.length)];
        p = findSafeSpotInArena(vault, vault.centerX(), vault.centerY() - 2, walls);
        objects.append(objToken("glyph_" + vaultRewardGlyph, p[0] + shiftX, p[1] + shiftY));

        // Terminals (also place on safe tiles).
        p = findSafeSpotInArena(start, start.centerX() - 4, start.centerY() + 3, walls);
        objects.append(objAlgebraTerminal("oracle", opId, 5, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(a1, a1.centerX(), a1.centerY() - 4, walls);
        objects.append(objAlgebraTerminal("oracle", opId, 6, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(lab, lab.centerX() - 4, lab.centerY() + 3, walls);
        objects.append(objAlgebraTerminal("forge", opId, 0, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(lab, lab.centerX() + 4, lab.centerY() + 3, walls);
        objects.append(objAlgebraTerminal("forge", opId, 0, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(lab, lab.centerX(), lab.centerY() - 4, walls);
        objects.append(objAlgebraTerminal("oracle", opId, 12, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(a3, a3.centerX(), a3.centerY() + 4, walls);
        objects.append(objAlgebraTerminal("forge", opId, 0, p[0] + shiftX, p[1] + shiftY));
        p = findSafeSpotInArena(a3, a3.centerX(), a3.centerY() - 4, walls);
        objects.append(objAlgebraTerminal("oracle", opId, 8, p[0] + shiftX, p[1] + shiftY));

        // Socket + vault riddle gate group
        objects.append(objSocket(groupVault, socketX + shiftX, socketY + shiftY, "glyph_7", true));

        // Normal progression gates
        for (GateObj g : normalGates) {
            objects.append(objArenaGate(g, shiftX, shiftY));
        }

        // Vault gates locked behind algebra socket
        for (GateObj g : vaultRiddleGates) {
            objects.append(objGate(groupVault,
                g.x + shiftX, g.y + shiftY,
                g.w, g.h,
                false));
        }

        // Finale terminal inside the vault: solving it advances to the next campaign stage.
        // Implemented as a visible terminal linked to a hidden finale PuzzleDoor (reuses PuzzleOverlaySystem).
        p = findSafeSpotInArena(vault, vault.centerX(), vault.centerY(), walls);
        int finaleTileX = p[0] + shiftX;
        int finaleTileY = p[1] + shiftY;

        String finaleDoorId = "algebra_finale_terminal_" + seed;
        String puzzleId = "algebra_finale_puzzle_" + seed;

        final String finaleKey = "FORGE";
        final String finalePlaintext = "FINALTRIAL";
        final String finaleCiphertext = VigenereCipher.encrypt(finalePlaintext, finaleKey);

        objects.append(objPuzzleDoorFinale(finaleDoorId, puzzleId, finaleCiphertext, finaleKey, finalePlaintext, finaleTileX, finaleTileY));
        objects.append(objPuzzleTerminal(finaleDoorId, finaleTileX, finaleTileY, true));

        return emitTmx(crop, objects.toString());
    }

    // ============================================================
    // Difficulty: choose initial glyph multiset (per opId)
    // ============================================================

    private static List<Integer> chooseStartingGlyphsForDifficulty(String opId, Random rng, int tokenCount, int minStepsToCrown) {
        // Candidate pool deliberately excludes glyph_7.
        // We keep glyph_6 out of the starting set so the vault still feels like a reward.
        int[] candidates = new int[] {0, 1, 2, 3, 4, 5};

        int[] perm = permutationFor(opId);
        int[] inv = inversePermutation(perm);

        // Try a bunch of random multisets and pick the hardest that meets constraints.
        List<Integer> best = null;
        int bestSteps = -1;

        int attempts = 6000;
        for (int t = 0; t < attempts; t++) {
            int[] counts = new int[8];
            int distinct = 0;

            for (int i = 0; i < tokenCount; i++) {
                int g = candidates[rng.nextInt(candidates.length)];
                if (counts[g] == 0) distinct++;
                counts[g]++;
            }

            // Require at least 3 distinct glyphs so it doesn't become a boring grind.
            if (distinct < 3) continue;

            // Hard constraint: Crown must NOT be directly craftable from any two starting tokens.
            if (hasOneStepCrown(counts, perm, inv)) continue;

            int steps = shortestStepsToCrown(counts, perm, inv, 10);
            if (steps < 0) continue; // unsolvable from this multiset
            if (steps < minStepsToCrown) continue;

            if (steps > bestSteps) {
                bestSteps = steps;
                best = countsToList(counts, tokenCount);
            }

            // Early exit if we get something decently hard.
            if (bestSteps >= Math.max(minStepsToCrown, 4)) {
                // keep searching a little for an even harder roll, but don't burn forever.
                if (t > attempts / 2) break;
            }
        }

        if (best != null) {
            return best;
        }

        // Fallback: guarantee at least "not one-step". If we can't find minStepsToCrown,
        // relax to 2 while still blocking one-step Crown.
        for (int t = 0; t < attempts; t++) {
            int[] counts = new int[8];
            int distinct = 0;
            for (int i = 0; i < tokenCount; i++) {
                int g = candidates[rng.nextInt(candidates.length)];
                if (counts[g] == 0) distinct++;
                counts[g]++;
            }
            if (distinct < 3) continue;
            if (hasOneStepCrown(counts, perm, inv)) continue;
            int steps = shortestStepsToCrown(counts, perm, inv, 10);
            if (steps >= 2) {
                return countsToList(counts, tokenCount);
            }
        }

        // Absolute fallback: old easy set (should basically never happen).
        List<Integer> legacy = new ArrayList<>();
        legacy.add(0);
        legacy.add(1);
        legacy.add(0);
        legacy.add(1);
        legacy.add(2);
        legacy.add(3);
        return legacy;
    }

    private static List<Integer> countsToList(int[] counts, int expectedTotal) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            for (int k = 0; k < counts[i]; k++) out.add(i);
        }
        // Deterministic but slightly shuffled order for nicer distribution across arenas.
        // (The generator's RNG has already mixed counts; this just avoids grouped spawns.)
        while (out.size() > expectedTotal) out.remove(out.size() - 1);
        while (out.size() < expectedTotal) out.add(0);
        // Simple swap shuffle using a tiny LCG derived from contents.
        int s = 0;
        for (int v : out) s = (s * 31) ^ v;
        s ^= 0xBADC0DE;
        for (int i = out.size() - 1; i > 0; i--) {
            s = s * 1103515245 + 12345;
            int j = (s >>> 16) % (i + 1);
            int tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    private static boolean hasOneStepCrown(int[] counts, int[] perm, int[] inv) {
        for (int a = 0; a < 8; a++) {
            if (counts[a] <= 0) continue;
            for (int b = 0; b < 8; b++) {
                int need = (a == b) ? 2 : 1;
                if (counts[b] < need) continue;
                int out = multiplyGlyphIndex(a, b, perm, inv);
                if (out == 7) return true;
            }
        }
        return false;
    }

    private static int shortestStepsToCrown(int[] startCounts, int[] perm, int[] inv, int maxDepth) {
        long start = packCounts(startCounts);
        Deque<long[]> q = new ArrayDeque<>();
        q.add(new long[] {start, 0});

        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        seen.add(start);

        while (!q.isEmpty()) {
            long[] cur = q.removeFirst();
            long packed = cur[0];
            int depth = (int) cur[1];
            int[] counts = unpackCounts(packed);
            if (counts[7] > 0) return depth;
            if (depth >= maxDepth) continue;

            int total = 0;
            for (int v : counts) total += v;
            if (total < 2) continue;

            for (int a = 0; a < 8; a++) {
                if (counts[a] <= 0) continue;
                for (int b = 0; b < 8; b++) {
                    int need = (a == b) ? 2 : 1;
                    if (counts[b] < need) continue;
                    int out = multiplyGlyphIndex(a, b, perm, inv);
                    int[] next = counts.clone();
                    next[a]--;
                    next[b]--;
                    next[out]++;
                    long np = packCounts(next);
                    if (seen.add(np)) {
                        q.addLast(new long[] {np, depth + 1});
                    }
                }
            }
        }
        return -1;
    }

    // Pack counts[0..7] into a long, 4 bits each (enough for our small token counts).
    private static long packCounts(int[] counts) {
        long v = 0L;
        for (int i = 0; i < 8; i++) {
            long c = Math.max(0, Math.min(15, counts[i]));
            v |= (c << (i * 4));
        }
        return v;
    }

    private static int[] unpackCounts(long packed) {
        int[] c = new int[8];
        for (int i = 0; i < 8; i++) {
            c[i] = (int) ((packed >>> (i * 4)) & 0xF);
        }
        return c;
    }

    // --- Hidden algebra (same as AlgebraForgeSystem), but generator-only and table-based. ---

    private static int multiplyGlyphIndex(int glyphA, int glyphB, int[] perm, int[] inv) {
        int eltA = perm[glyphA];
        int eltB = perm[glyphB];

        int aK = (eltA < 4) ? eltA : (eltA - 4);
        int aF = (eltA < 4) ? 0 : 1;
        int bK = (eltB < 4) ? eltB : (eltB - 4);
        int bF = (eltB < 4) ? 0 : 1;

        int sign = (aF == 0) ? 1 : -1;
        int k = aK + sign * bK;
        k = ((k % 4) + 4) % 4;
        int f = aF ^ bF;
        int outIdx = (f == 0) ? k : (4 + k);
        return inv[outIdx];
    }

    private static int[] permutationFor(String opId) {
        int seed = 0xC0FFEE;
        if (opId != null) {
            seed = opId.hashCode() ^ 0x9E3779B9;
        }
        Random rng = new Random(seed);
        List<Integer> xs = new ArrayList<>();
        for (int i = 0; i < 8; i++) xs.add(i);
        for (int i = xs.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = xs.get(i);
            xs.set(i, xs.get(j));
            xs.set(j, tmp);
        }
        int[] perm = new int[8];
        for (int i = 0; i < 8; i++) perm[i] = xs.get(i);
        return perm;
    }

    private static int[] inversePermutation(int[] perm) {
        int[] inv = new int[perm.length];
        for (int i = 0; i < perm.length; i++) {
            inv[perm[i]] = i;
        }
        return inv;
    }

    private static final class ArenaDef {
        final String id;
        final int index;
        final int x;
        final int y;

        ArenaDef(String id, int index, int x, int y) {
            this.id = id;
            this.index = index;
            this.x = x;
            this.y = y;
        }

        int centerX() { return x + ARENA_W / 2; }
        int centerY() { return y + ARENA_H / 2; }
    }

    private static final class GateObj {
        final String sourceArenaId;
        final String targetArenaId;
        final int x;
        final int y;
        final int w;
        final int h;

        GateObj(String sourceArenaId, String targetArenaId, int x, int y, int w, int h) {
            this.sourceArenaId = sourceArenaId;
            this.targetArenaId = targetArenaId;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static boolean hasPlusClearance(int x, int y, int[][] walls) {
        if (walls == null) return true;
        int h = walls.length;
        int w = walls[0].length;
        if (x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1) return false;
        if (walls[y][x] != 0) return false;
        // Require that the player can at least stand on/around the tile.
        return walls[y - 1][x] == 0 && walls[y + 1][x] == 0 && walls[y][x - 1] == 0 && walls[y][x + 1] == 0;
    }

    private static int[] findSafeSpotInArena(ArenaDef arena, int preferredX, int preferredY, int[][] walls) {
        // Clamp to interior (avoid the 1-tile wall ring + doorway bands).
        int xMin = arena.x + 2;
        int xMax = arena.x + ARENA_W - 3;
        int yMin = arena.y + 2;
        int yMax = arena.y + ARENA_H - 3;

        int x0 = clampInt(preferredX, xMin, xMax);
        int y0 = clampInt(preferredY, yMin, yMax);

        if (hasPlusClearance(x0, y0, walls)) {
            return new int[]{x0, y0};
        }

        // Spiral-ish search out to a small radius.
        for (int r = 1; r <= 8; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int x = x0 + dx;
                    int y = y0 + dy;
                    if (x < xMin || x > xMax || y < yMin || y > yMax) continue;
                    if (hasPlusClearance(x, y, walls)) {
                        return new int[]{x, y};
                    }
                }
            }
        }

        // As a last resort, return the clamped position.
        return new int[]{x0, y0};
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int[] findSafeSpotNear(int preferredX, int preferredY, int[][] walls, int radius) {
        if (walls == null) return new int[]{preferredX, preferredY};
        int h = walls.length;
        int w = walls[0].length;

        int x0 = clampInt(preferredX, 1, w - 2);
        int y0 = clampInt(preferredY, 1, h - 2);

        if (hasPlusClearance(x0, y0, walls)) {
            return new int[]{x0, y0};
        }

        int rMax = Math.max(1, radius);
        for (int r = 1; r <= rMax; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int x = x0 + dx;
                    int y = y0 + dy;
                    if (x <= 0 || y <= 0 || x >= w - 1 || y >= h - 1) continue;
                    if (hasPlusClearance(x, y, walls)) {
                        return new int[]{x, y};
                    }
                }
            }
        }

        return new int[]{x0, y0};
    }

    private void stampArena(ArenaDef a, int[][] floor, int[][] walls) {
        // stampRoom expects x/y to be the interior floor origin; it adds a 1-tile wall border.
        stampRoom(floor, walls, a.x, a.y, ARENA_W, ARENA_H);
    }

    private void addPillars(ArenaDef a, int[][] floor, int[][] walls, Random rng, int count, List<GateObj> gatesForClearance) {
        // Only place inside the walkable floor area.
        int xMin = a.x + 3;
        int xMax = a.x + ARENA_W - 4;
        int yMin = a.y + 3;
        int yMax = a.y + ARENA_H - 4;

        int placed = 0;
        int attempts = 0;
        while (placed < count && attempts++ < 300) {
            int px = xMin + rng.nextInt(Math.max(1, xMax - xMin + 1));
            int py = yMin + rng.nextInt(Math.max(1, yMax - yMin + 1));
            int pw = (rng.nextInt(100) < 70) ? 2 : 3;
            int ph = (rng.nextInt(100) < 70) ? 2 : 3;

            boolean rejected = false;
            for (int yy = py; yy < py + ph && !rejected; yy++) {
                for (int xx = px; xx < px + pw; xx++) {
                    if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) { rejected = true; break; }
                    if (floor[yy][xx] != TILE_FLOOR) { rejected = true; break; }
                    if (gatesForClearance != null && isNearAnyGate(xx, yy, gatesForClearance, DOOR_CLEARANCE_TILES)) {
                        rejected = true;
                        break;
                    }
                }
            }
            if (rejected) continue;

            for (int yy = py; yy < py + ph; yy++) {
                for (int xx = px; xx < px + pw; xx++) {
                    if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) continue;
                    if (floor[yy][xx] == TILE_FLOOR) {
                        walls[yy][xx] = TILE_WALL;
                    }
                }
            }
            placed++;
        }
    }

    private void carveHorizontalConnection(ArenaDef left, ArenaDef right, int[][] floor, int[][] walls, List<GateObj> outGates) {
        // Left/right arenas on same row.
        int corridorYCenter = left.centerY();

        int leftDoorX = left.x + ARENA_W;
        int rightDoorX = right.x - 1;

        int corridorStartX = leftDoorX + 1;
        int corridorEndX = rightDoorX - 1;

        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;

        int laneHStart = startOffset + side;
        int laneVStart = laneHStart + lane + sep;

        // Corridor body
        for (int x = corridorStartX; x <= corridorEndX; x++) {
            for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
                int y = corridorYCenter + off;
                if (y < 0 || y >= CANVAS_H || x < 0 || x >= CANVAS_W) continue;
                int idx = off - startOffset;
                boolean inLaneH = idx >= side && idx < (side + lane);
                boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    walls[y][x] = TILE_WALL;
                }
            }
        }

        // Doorways and gate tiles
        int innerLeftX = leftDoorX - 1;
        int innerRightX = rightDoorX + 1;

        for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
            int y = corridorYCenter + off;
            if (y < 0 || y >= CANVAS_H) continue;
            int idx = off - startOffset;
            boolean inLaneH = idx >= side && idx < (side + lane);
            boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
            if (!(inLaneH || inLaneV)) continue;

            // Carve openings into arena borders.
            if (innerLeftX >= 0 && innerLeftX < CANVAS_W) {
                walls[y][innerLeftX] = 0;
                floor[y][innerLeftX] = TILE_FLOOR;
            }
            if (innerRightX >= 0 && innerRightX < CANVAS_W) {
                walls[y][innerRightX] = 0;
                floor[y][innerRightX] = TILE_FLOOR;
            }

            // Gate tiles on door bands.
            walls[y][leftDoorX] = TILE_GATE;
            floor[y][leftDoorX] = TILE_FLOOR;
            walls[y][rightDoorX] = TILE_GATE;
            floor[y][rightDoorX] = TILE_FLOOR;
        }

        // Gate objects: two lane rectangles per doorway, one per arena direction.
        int laneHTopY = corridorYCenter + laneHStart;
        int laneVTopY = corridorYCenter + laneVStart;

        outGates.add(new GateObj(left.id, right.id, leftDoorX, laneHTopY, 1, lane));
        outGates.add(new GateObj(left.id, right.id, leftDoorX, laneVTopY, 1, lane));

        outGates.add(new GateObj(right.id, left.id, rightDoorX, laneHTopY, 1, lane));
        outGates.add(new GateObj(right.id, left.id, rightDoorX, laneVTopY, 1, lane));
    }

    private void carveVerticalConnection(ArenaDef top, ArenaDef bottom, int[][] floor, int[][] walls, List<GateObj> outGates) {
        int corridorXCenter = top.centerX();

        int topDoorY = top.y + ARENA_H;
        int bottomDoorY = bottom.y - 1;

        int corridorStartY = topDoorY + 1;
        int corridorEndY = bottomDoorY - 1;

        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;

        int laneHStart = startOffset + side;
        int laneVStart = laneHStart + lane + sep;

        for (int y = corridorStartY; y <= corridorEndY; y++) {
            for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
                int x = corridorXCenter + off;
                if (y < 0 || y >= CANVAS_H || x < 0 || x >= CANVAS_W) continue;
                int idx = off - startOffset;
                boolean inLaneH = idx >= side && idx < (side + lane);
                boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    walls[y][x] = TILE_WALL;
                }
            }
        }

        int innerTopY = topDoorY - 1;
        int innerBottomY = bottomDoorY + 1;

        for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
            int x = corridorXCenter + off;
            if (x < 0 || x >= CANVAS_W) continue;
            int idx = off - startOffset;
            boolean inLaneH = idx >= side && idx < (side + lane);
            boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
            if (!(inLaneH || inLaneV)) continue;

            if (innerTopY >= 0 && innerTopY < CANVAS_H) {
                walls[innerTopY][x] = 0;
                floor[innerTopY][x] = TILE_FLOOR;
            }
            if (innerBottomY >= 0 && innerBottomY < CANVAS_H) {
                walls[innerBottomY][x] = 0;
                floor[innerBottomY][x] = TILE_FLOOR;
            }

            walls[topDoorY][x] = TILE_GATE;
            floor[topDoorY][x] = TILE_FLOOR;
            walls[bottomDoorY][x] = TILE_GATE;
            floor[bottomDoorY][x] = TILE_FLOOR;
        }

        int laneHLeftX = corridorXCenter + laneHStart;
        int laneVLeftX = corridorXCenter + laneVStart;

        outGates.add(new GateObj(top.id, bottom.id, laneHLeftX, topDoorY, lane, 1));
        outGates.add(new GateObj(top.id, bottom.id, laneVLeftX, topDoorY, lane, 1));

        outGates.add(new GateObj(bottom.id, top.id, laneHLeftX, bottomDoorY, lane, 1));
        outGates.add(new GateObj(bottom.id, top.id, laneVLeftX, bottomDoorY, lane, 1));
    }

    private void carveVerticalConnectionWithVaultLock(ArenaDef nonVault, ArenaDef vault,
                                                     int[][] floor, int[][] walls,
                                                     List<GateObj> normalGates,
                                                     List<GateObj> vaultRiddleGates) {
        // Ensure nonVault is above vault in our layout.
        ArenaDef top = (nonVault.y <= vault.y) ? nonVault : vault;
        ArenaDef bottom = (top == nonVault) ? vault : nonVault;

        // Carve corridor.
        carveVerticalConnection(top, bottom, floor, walls, normalGates);

        // Remove the normal gates that would belong to the vault doorway and replace with riddle gates.
        // Vault doorway is at bottom.y - 1 (since vault is bottom in our layout).
        int vaultDoorY = vault.y - 1;
        normalGates.removeIf(g -> g.sourceArenaId.equals(vault.id) && g.y == vaultDoorY);

        // Add riddle gates covering both lanes at the vault doorway.
        int corridorXCenter = top.centerX();
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int laneHLeftX = corridorXCenter + (startOffset + CORRIDOR_SIDE_WALL);
        int laneVLeftX = corridorXCenter + (startOffset + CORRIDOR_SIDE_WALL + CORRIDOR_LANE_W + CORRIDOR_SEPARATOR_WALL);

        vaultRiddleGates.add(new GateObj(vault.id, top.id, laneHLeftX, vaultDoorY, CORRIDOR_LANE_W, 1));
        vaultRiddleGates.add(new GateObj(vault.id, top.id, laneVLeftX, vaultDoorY, CORRIDOR_LANE_W, 1));
    }

    private void carveHorizontalConnectionWithVaultLock(ArenaDef nonVault, ArenaDef vault,
                                                       int[][] floor, int[][] walls,
                                                       List<GateObj> normalGates,
                                                       List<GateObj> vaultRiddleGates) {
        // Ensure nonVault is left of vault.
        ArenaDef left = (nonVault.x <= vault.x) ? nonVault : vault;
        ArenaDef right = (left == nonVault) ? vault : nonVault;

        carveHorizontalConnection(left, right, floor, walls, normalGates);

        // Vault doorway is at right.x - 1 (since vault is right in our layout).
        int vaultDoorX = vault.x - 1;
        normalGates.removeIf(g -> g.sourceArenaId.equals(vault.id) && g.x == vaultDoorX);

        int corridorYCenter = left.centerY();
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int laneHTopY = corridorYCenter + (startOffset + CORRIDOR_SIDE_WALL);
        int laneVTopY = corridorYCenter + (startOffset + CORRIDOR_SIDE_WALL + CORRIDOR_LANE_W + CORRIDOR_SEPARATOR_WALL);

        vaultRiddleGates.add(new GateObj(vault.id, left.id, vaultDoorX, laneHTopY, 1, CORRIDOR_LANE_W));
        vaultRiddleGates.add(new GateObj(vault.id, left.id, vaultDoorX, laneVTopY, 1, CORRIDOR_LANE_W));
    }

    private void carveDoglegConnectionWithVaultLock(ArenaDef nonVault, ArenaDef vault,
                                                    int[][] floor, int[][] walls,
                                                    List<GateObj> normalGates,
                                                    List<GateObj> vaultRiddleGates) {
        // A 2-turn (dogleg) corridor that approaches the vault from above.
        // This stays visually distinct and avoids "phantom" minimap edges from corridors
        // that merge into other connections.
        if (nonVault == null || vault == null) return;

        // Ensure nonVault is above vault.
        if (nonVault.y > vault.y) {
            // If layout ever changes, fall back to straight lock methods.
            carveVerticalConnectionWithVaultLock(nonVault, vault, floor, walls, normalGates, vaultRiddleGates);
            return;
        }

        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;
        int laneHStart = startOffset + side;
        int laneVStart = laneHStart + lane + sep;

        int fromDoorY = nonVault.y + ARENA_H;
        int toDoorY = vault.y - 1;

        int fromXCenter = nonVault.centerX();
        int toXCenter = vault.centerX();

        int fromOutsideY = fromDoorY + 1;
        int toOutsideY = toDoorY - 1;

        // Pick an elbow roughly in the middle of the vertical run.
        int elbowY = (fromOutsideY + toOutsideY) / 2;
        // Keep elbow away from the door bands.
        elbowY = Math.max(fromOutsideY + 2, Math.min(toOutsideY - 2, elbowY));

        // Corridor body: down, across, down.
        carveVerticalSegment(Math.min(fromOutsideY, elbowY), Math.max(fromOutsideY, elbowY), fromXCenter, floor, walls);
        carveHorizontalSegment(Math.min(fromXCenter, toXCenter), Math.max(fromXCenter, toXCenter), elbowY, floor, walls);
        carveVerticalSegment(Math.min(elbowY, toOutsideY), Math.max(elbowY, toOutsideY), toXCenter, floor, walls);

        // Smooth out corner turns so the player collider can't snag.
        carveCornerPocket(fromXCenter, elbowY, floor, walls);
        carveCornerPocket(toXCenter, elbowY, floor, walls);

        // Doorway at nonVault (bottom edge): open the arena wall and stamp gate tiles.
        carveOpening(floor, walls, fromXCenter + laneHStart, fromDoorY - 1, lane, 2);
        carveOpening(floor, walls, fromXCenter + laneVStart, fromDoorY - 1, lane, 2);
        stampGateTiles(walls, fromXCenter + laneHStart, fromDoorY, lane, 1);
        stampGateTiles(walls, fromXCenter + laneVStart, fromDoorY, lane, 1);
        for (int i = 0; i < lane; i++) {
            floor[fromDoorY][fromXCenter + laneHStart + i] = TILE_FLOOR;
            floor[fromDoorY][fromXCenter + laneVStart + i] = TILE_FLOOR;
        }

        // Doorway at vault (top edge): open the arena wall and stamp gate tiles.
        carveOpening(floor, walls, toXCenter + laneHStart, toDoorY, lane, 2);
        carveOpening(floor, walls, toXCenter + laneVStart, toDoorY, lane, 2);
        stampGateTiles(walls, toXCenter + laneHStart, toDoorY, lane, 1);
        stampGateTiles(walls, toXCenter + laneVStart, toDoorY, lane, 1);
        for (int i = 0; i < lane; i++) {
            floor[toDoorY][toXCenter + laneHStart + i] = TILE_FLOOR;
            floor[toDoorY][toXCenter + laneVStart + i] = TILE_FLOOR;
        }

        // Normal progression gates on the non-vault side (so the minimap shows the connection).
        normalGates.add(new GateObj(nonVault.id, vault.id, fromXCenter + laneHStart, fromDoorY, lane, 1));
        normalGates.add(new GateObj(nonVault.id, vault.id, fromXCenter + laneVStart, fromDoorY, lane, 1));

        // Riddle gates on the vault doorway (locked until socket activates).
        vaultRiddleGates.add(new GateObj(vault.id, nonVault.id, toXCenter + laneHStart, toDoorY, lane, 1));
        vaultRiddleGates.add(new GateObj(vault.id, nonVault.id, toXCenter + laneVStart, toDoorY, lane, 1));
    }

    private void carveLCorridorConnection(ArenaDef a, ArenaDef b,
                                         int[][] floor, int[][] walls,
                                         List<GateObj> outGates,
                                         Random rng) {
        // A single L-shaped connection between arenas that are not aligned.
        // We still place directional gate objects on the two arena doorways.
        if (a.x == b.x || a.y == b.y) {
            // Aligned; caller should use straight corridor methods.
            return;
        }

        boolean horizontalFirst = rng.nextBoolean();

        // Two lane starts (within the CORRIDOR_TOTAL_W band)
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;
        int laneHStart = startOffset + side;
        int laneVStart = laneHStart + lane + sep;

        if (horizontalFirst) {
            // Leave A from east/west wall, enter B from north/south wall.
            boolean goEast = b.centerX() > a.centerX();
            int aDoorX = goEast ? (a.x + ARENA_W) : (a.x - 1);
            int aInnerX = goEast ? (aDoorX - 1) : (aDoorX + 1);
            int aOutsideX = goEast ? (aDoorX + 1) : (aDoorX - 1);
            int aYCenter = a.centerY();

            boolean bBelow = b.centerY() > aYCenter;
            int bDoorY = bBelow ? (b.y - 1) : (b.y + ARENA_H);
            int bInnerY = bBelow ? (bDoorY + 1) : (bDoorY - 1);
            int bOutsideY = bBelow ? (bDoorY - 1) : (bDoorY + 1);
            int bXCenter = b.centerX();

            int elbowX = bXCenter;
            int elbowY = aYCenter;

            carveHorizontalSegment(Math.min(aOutsideX, elbowX), Math.max(aOutsideX, elbowX), elbowY, floor, walls);
            carveVerticalSegment(Math.min(elbowY, bOutsideY), Math.max(elbowY, bOutsideY), elbowX, floor, walls);

            // Door openings + gate tiles
            carveOpening(floor, walls, Math.min(aInnerX, aDoorX), aYCenter + laneHStart, Math.abs(aInnerX - aDoorX) + 1, lane);
            carveOpening(floor, walls, Math.min(aInnerX, aDoorX), aYCenter + laneVStart, Math.abs(aInnerX - aDoorX) + 1, lane);
            stampGateTiles(walls, aDoorX, aYCenter + laneHStart, 1, lane);
            stampGateTiles(walls, aDoorX, aYCenter + laneVStart, 1, lane);
            for (int i = 0; i < lane; i++) {
                floor[aYCenter + laneHStart + i][aDoorX] = TILE_FLOOR;
                floor[aYCenter + laneVStart + i][aDoorX] = TILE_FLOOR;
            }

            carveOpening(floor, walls, bXCenter + laneHStart, Math.min(bInnerY, bDoorY), lane, Math.abs(bInnerY - bDoorY) + 1);
            carveOpening(floor, walls, bXCenter + laneVStart, Math.min(bInnerY, bDoorY), lane, Math.abs(bInnerY - bDoorY) + 1);
            stampGateTiles(walls, bXCenter + laneHStart, bDoorY, lane, 1);
            stampGateTiles(walls, bXCenter + laneVStart, bDoorY, lane, 1);
            for (int i = 0; i < lane; i++) {
                floor[bDoorY][bXCenter + laneHStart + i] = TILE_FLOOR;
                floor[bDoorY][bXCenter + laneVStart + i] = TILE_FLOOR;
            }

            // Gate objects on A doorway
            outGates.add(new GateObj(a.id, b.id, aDoorX, aYCenter + laneHStart, 1, lane));
            outGates.add(new GateObj(a.id, b.id, aDoorX, aYCenter + laneVStart, 1, lane));
            outGates.add(new GateObj(b.id, a.id, aDoorX, aYCenter + laneHStart, 1, lane));
            outGates.add(new GateObj(b.id, a.id, aDoorX, aYCenter + laneVStart, 1, lane));

            // Gate objects on B doorway
            outGates.add(new GateObj(a.id, b.id, bXCenter + laneHStart, bDoorY, lane, 1));
            outGates.add(new GateObj(a.id, b.id, bXCenter + laneVStart, bDoorY, lane, 1));
            outGates.add(new GateObj(b.id, a.id, bXCenter + laneHStart, bDoorY, lane, 1));
            outGates.add(new GateObj(b.id, a.id, bXCenter + laneVStart, bDoorY, lane, 1));

        } else {
            // Leave A from north/south wall, enter B from east/west wall.
            boolean goDown = b.centerY() > a.centerY();
            int aDoorY = goDown ? (a.y + ARENA_H) : (a.y - 1);
            int aInnerY = goDown ? (aDoorY - 1) : (aDoorY + 1);
            int aOutsideY = goDown ? (aDoorY + 1) : (aDoorY - 1);
            int aXCenter = a.centerX();

            boolean bRight = b.centerX() > aXCenter;
            int bDoorX = bRight ? (b.x - 1) : (b.x + ARENA_W);
            int bInnerX = bRight ? (bDoorX + 1) : (bDoorX - 1);
            int bOutsideX = bRight ? (bDoorX - 1) : (bDoorX + 1);
            int bYCenter = b.centerY();

            int elbowX = aXCenter;
            int elbowY = bYCenter;

            carveVerticalSegment(Math.min(aOutsideY, elbowY), Math.max(aOutsideY, elbowY), elbowX, floor, walls);
            carveHorizontalSegment(Math.min(elbowX, bOutsideX), Math.max(elbowX, bOutsideX), elbowY, floor, walls);

            // A doorway (horizontal band)
            carveOpening(floor, walls, aXCenter + laneHStart, Math.min(aInnerY, aDoorY), lane, Math.abs(aInnerY - aDoorY) + 1);
            carveOpening(floor, walls, aXCenter + laneVStart, Math.min(aInnerY, aDoorY), lane, Math.abs(aInnerY - aDoorY) + 1);
            stampGateTiles(walls, aXCenter + laneHStart, aDoorY, lane, 1);
            stampGateTiles(walls, aXCenter + laneVStart, aDoorY, lane, 1);
            for (int i = 0; i < lane; i++) {
                floor[aDoorY][aXCenter + laneHStart + i] = TILE_FLOOR;
                floor[aDoorY][aXCenter + laneVStart + i] = TILE_FLOOR;
            }

            // B doorway (vertical band)
            carveOpening(floor, walls, Math.min(bInnerX, bDoorX), bYCenter + laneHStart, Math.abs(bInnerX - bDoorX) + 1, lane);
            carveOpening(floor, walls, Math.min(bInnerX, bDoorX), bYCenter + laneVStart, Math.abs(bInnerX - bDoorX) + 1, lane);
            stampGateTiles(walls, bDoorX, bYCenter + laneHStart, 1, lane);
            stampGateTiles(walls, bDoorX, bYCenter + laneVStart, 1, lane);
            for (int i = 0; i < lane; i++) {
                floor[bYCenter + laneHStart + i][bDoorX] = TILE_FLOOR;
                floor[bYCenter + laneVStart + i][bDoorX] = TILE_FLOOR;
            }

            // Gate objects
            outGates.add(new GateObj(a.id, b.id, aXCenter + laneHStart, aDoorY, lane, 1));
            outGates.add(new GateObj(a.id, b.id, aXCenter + laneVStart, aDoorY, lane, 1));
            outGates.add(new GateObj(b.id, a.id, aXCenter + laneHStart, aDoorY, lane, 1));
            outGates.add(new GateObj(b.id, a.id, aXCenter + laneVStart, aDoorY, lane, 1));

            outGates.add(new GateObj(a.id, b.id, bDoorX, bYCenter + laneHStart, 1, lane));
            outGates.add(new GateObj(a.id, b.id, bDoorX, bYCenter + laneVStart, 1, lane));
            outGates.add(new GateObj(b.id, a.id, bDoorX, bYCenter + laneHStart, 1, lane));
            outGates.add(new GateObj(b.id, a.id, bDoorX, bYCenter + laneVStart, 1, lane));
        }
    }

    private void carveHorizontalSegment(int x0, int x1, int yCenter, int[][] floor, int[][] walls) {
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;

        for (int x = x0; x <= x1; x++) {
            for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
                int y = yCenter + off;
                if (y < 0 || y >= CANVAS_H || x < 0 || x >= CANVAS_W) continue;
                int idx = off - startOffset;
                boolean inLaneH = idx >= side && idx < (side + lane);
                boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    if (floor[y][x] == 0) walls[y][x] = TILE_WALL;
                }
            }
        }
    }

    private void carveVerticalSegment(int y0, int y1, int xCenter, int[][] floor, int[][] walls) {
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int side = CORRIDOR_SIDE_WALL;
        int lane = CORRIDOR_LANE_W;
        int sep = CORRIDOR_SEPARATOR_WALL;

        for (int y = y0; y <= y1; y++) {
            for (int off = startOffset; off < startOffset + CORRIDOR_TOTAL_W; off++) {
                int x = xCenter + off;
                if (y < 0 || y >= CANVAS_H || x < 0 || x >= CANVAS_W) continue;
                int idx = off - startOffset;
                boolean inLaneH = idx >= side && idx < (side + lane);
                boolean inLaneV = idx >= (side + lane + sep) && idx < (side + lane + sep + lane);
                if (inLaneH || inLaneV) {
                    floor[y][x] = TILE_FLOOR;
                } else {
                    if (floor[y][x] == 0) walls[y][x] = TILE_WALL;
                }
            }
        }
    }

    private BSPDungeonGenerator.Room pickRoom(Random rng, List<BSPDungeonGenerator.Room> rooms) {
        // Prefer decently sized rooms so content doesn't overlap walls.
        List<BSPDungeonGenerator.Room> candidates = new ArrayList<>();
        for (BSPDungeonGenerator.Room r : rooms) {
            if (r.width >= 8 && r.height >= 8) candidates.add(r);
        }
        if (candidates.isEmpty()) candidates = rooms;
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private BSPDungeonGenerator.Room farthestRoomFrom(BSPDungeonGenerator.Room from, List<BSPDungeonGenerator.Room> rooms, BSPDungeonGenerator.Room... exclude) {
        BSPDungeonGenerator.Room best = null;
        int bestD = -1;
        for (BSPDungeonGenerator.Room r : rooms) {
            if (r == from) continue;
            boolean skip = false;
            for (BSPDungeonGenerator.Room ex : exclude) {
                if (r == ex) {
                    skip = true;
                    break;
                }
            }
            if (skip) continue;
            int d = Math.abs(r.getCenterX() - from.getCenterX()) + Math.abs(r.getCenterY() - from.getCenterY());
            if (d > bestD) {
                bestD = d;
                best = r;
            }
        }
        return best != null ? best : rooms.get(0);
    }

    private static boolean isInside(BSPDungeonGenerator.Room room, int x, int y) {
        return x >= room.x && x < room.x + room.width && y >= room.y && y < room.y + room.height;
    }

    private static int findVaultTransitionIndex(List<int[]> path, BSPDungeonGenerator.Room vaultRoom) {
        for (int i = 0; i < path.size() - 1; i++) {
            int[] a = path.get(i);
            int[] b = path.get(i + 1);
            boolean aIn = isInside(vaultRoom, a[0], a[1]);
            boolean bIn = isInside(vaultRoom, b[0], b[1]);
            if (!aIn && bIn) {
                return i;
            }
        }
        return -1;
    }

    private static final class GateBand {
        final int x, y, w, h;

        GateBand(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static GateBand computeGateBand(int[][] floor, int[][] walls, int cx, int cy, int dx, int dy) {
        // If moving horizontally, gate is a vertical band; if moving vertically, gate is horizontal.
        boolean horizontalMove = dx != 0;
        int floorId = TILE_FLOOR;

        if (horizontalMove) {
            // Scan up/down to find corridor vertical span.
            int top = cy;
            while (top - 1 >= 0 && floor[top - 1][cx] == floorId && walls[top - 1][cx] == 0) top--;
            int bottom = cy;
            while (bottom + 1 < floor.length && floor[bottom + 1][cx] == floorId && walls[bottom + 1][cx] == 0) bottom++;
            int h = bottom - top + 1;
            int x = cx;
            int w = 2;
            return new GateBand(x, top, w, h);
        } else {
            // Scan left/right to find corridor horizontal span.
            int left = cx;
            while (left - 1 >= 0 && floor[cy][left - 1] == floorId && walls[cy][left - 1] == 0) left--;
            int right = cx;
            while (right + 1 < floor[0].length && floor[cy][right + 1] == floorId && walls[cy][right + 1] == 0) right++;
            int w = right - left + 1;
            int y = cy;
            int h = 2;
            return new GateBand(left, y, w, h);
        }
    }

    private static List<int[]> bfsPath(int[][] floor, int[][] walls, int sx, int sy, int gx, int gy) {
        int h = floor.length;
        int w = floor[0].length;
        int start = sy * w + sx;
        int goal = gy * w + gx;

        int[] parent = new int[w * h];
        for (int i = 0; i < parent.length; i++) parent[i] = -1;
        boolean[] visited = new boolean[w * h];

        if (!isWalkable(floor, walls, sx, sy) || !isWalkable(floor, walls, gx, gy)) {
            return List.of();
        }

        Deque<Integer> q = new ArrayDeque<>();
        q.add(start);
        visited[start] = true;

        while (!q.isEmpty()) {
            int cur = q.removeFirst();
            if (cur == goal) break;
            int x = cur % w;
            int y = cur / w;

            // 4-neighborhood
            if (x > 0) tryVisit(floor, walls, w, visited, parent, q, cur, cur - 1);
            if (x + 1 < w) tryVisit(floor, walls, w, visited, parent, q, cur, cur + 1);
            if (y > 0) tryVisit(floor, walls, w, visited, parent, q, cur, cur - w);
            if (y + 1 < h) tryVisit(floor, walls, w, visited, parent, q, cur, cur + w);
        }

        if (!visited[goal]) return List.of();

        ArrayList<int[]> out = new ArrayList<>();
        int cur = goal;
        while (cur != -1 && cur != start) {
            int x = cur % w;
            int y = cur / w;
            out.add(new int[]{x, y});
            cur = parent[cur];
        }
        out.add(new int[]{sx, sy});
        // reverse
        for (int i = 0, j = out.size() - 1; i < j; i++, j--) {
            int[] tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    private static void tryVisit(int[][] floor, int[][] walls, int w, boolean[] visited, int[] parent, Deque<Integer> q, int from, int to) {
        if (to < 0 || to >= visited.length) return;
        if (visited[to]) return;
        int x = to % w;
        int y = to / w;
        if (!isWalkable(floor, walls, x, y)) return;
        visited[to] = true;
        parent[to] = from;
        q.add(to);
    }

    private static boolean isWalkable(int[][] floor, int[][] walls, int x, int y) {
        return y >= 0 && y < floor.length && x >= 0 && x < floor[0].length && floor[y][x] == TILE_FLOOR && walls[y][x] == 0;
    }

    private static int[] pickNearPath(Random rng, int[][] floor, int[][] walls, boolean[][] occupied, List<int[]> path, int nearIndex, int radius) {
        int idx = Math.max(0, Math.min(path.size() - 1, nearIndex));
        int[] base = path.get(idx);
        int cx = base[0];
        int cy = base[1];

        // Random darts around the base, then fallback to linear scan along path.
        for (int i = 0; i < 120; i++) {
            int x = cx + rng.nextInt(radius * 2 + 1) - radius;
            int y = cy + rng.nextInt(radius * 2 + 1) - radius;
            if (y < 0 || y >= floor.length || x < 0 || x >= floor[0].length) continue;
            if (occupied[y][x]) continue;
            if (floor[y][x] == TILE_FLOOR && walls[y][x] == 0) {
                return new int[]{x, y};
            }
        }

        for (int i = idx; i >= 0; i--) {
            int[] p = path.get(i);
            int x = p[0];
            int y = p[1];
            if (occupied[y][x]) continue;
            if (floor[y][x] == TILE_FLOOR && walls[y][x] == 0) {
                return new int[]{x, y};
            }
        }

        return new int[]{cx, cy};
    }

    private void stampVerticalCorridor(int[][] floor, int[][] walls, int x, int y, int len, int thickness) {
        // Vertical corridor of given thickness.
        for (int dy = 0; dy < len; dy++) {
            for (int dx = 0; dx < thickness; dx++) {
                int xx = x + dx;
                int yy = y + dy;
                if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) continue;
                floor[yy][xx] = TILE_FLOOR;
            }
        }
        // Walls left/right of corridor.
        for (int dy = 0; dy < len; dy++) {
            if (y + dy < 0 || y + dy >= CANVAS_H) continue;

            // Don't stamp walls through existing floor (e.g., when connecting to rooms).
            if (x - 1 >= 0 && floor[y + dy][x - 1] == 0) walls[y + dy][x - 1] = TILE_WALL;
            if (x + thickness < CANVAS_W && floor[y + dy][x + thickness] == 0) walls[y + dy][x + thickness] = TILE_WALL;
        }
    }

    private int[] pickFloorSpot(Random rng, int[][] floor, int[][] walls, boolean[][] occupied,
                               int x, int y, int w, int h) {
        int attempts = 80;
        for (int i = 0; i < attempts; i++) {
            int xx = x + rng.nextInt(Math.max(1, w));
            int yy = y + rng.nextInt(Math.max(1, h));
            if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) continue;
            if (occupied[yy][xx]) continue;
            if (floor[yy][xx] == TILE_FLOOR && walls[yy][xx] == 0) {
                occupied[yy][xx] = true;
                return new int[]{xx, yy};
            }
        }
        // Fallback: scan for the first valid tile.
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) continue;
                if (occupied[yy][xx]) continue;
                if (floor[yy][xx] == TILE_FLOOR && walls[yy][xx] == 0) {
                    occupied[yy][xx] = true;
                    return new int[]{xx, yy};
                }
            }
        }
        return new int[]{x, y};
    }

    private void outlineWallsFromFloor(int[][] floor, int[][] walls) {
        for (int y = 1; y < CANVAS_H - 1; y++) {
            for (int x = 1; x < CANVAS_W - 1; x++) {
                if (floor[y][x] == 0) continue;
                // Put wall tiles around exposed floor edges.
                if (floor[y - 1][x] == 0 && walls[y - 1][x] == 0) walls[y - 1][x] = TILE_WALL;
                if (floor[y + 1][x] == 0 && walls[y + 1][x] == 0) walls[y + 1][x] = TILE_WALL;
                if (floor[y][x - 1] == 0 && walls[y][x - 1] == 0) walls[y][x - 1] = TILE_WALL;
                if (floor[y][x + 1] == 0 && walls[y][x + 1] == 0) walls[y][x + 1] = TILE_WALL;
            }
        }
    }

    private void ensureBorderWalls(int[][] walls) {
        int h = walls.length;
        int w = walls[0].length;
        for (int x = 0; x < w; x++) {
            if (walls[0][x] == 0) walls[0][x] = TILE_WALL;
            if (walls[h - 1][x] == 0) walls[h - 1][x] = TILE_WALL;
        }
        for (int y = 0; y < h; y++) {
            if (walls[y][0] == 0) walls[y][0] = TILE_WALL;
            if (walls[y][w - 1] == 0) walls[y][w - 1] = TILE_WALL;
        }
    }

    private static final class Crop {
        final int[][] floor;
        final int[][] walls;
        final int width;
        final int height;
        final int offsetX;
        final int offsetY;

        Crop(int[][] floor, int[][] walls, int width, int height, int offsetX, int offsetY) {
            this.floor = floor;
            this.walls = walls;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private void stampRoom(int[][] floor, int[][] walls, int x, int y, int w, int h) {
        // room interior
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                floor[yy][xx] = TILE_FLOOR;
            }
        }
        // 1-tile walls around
        for (int xx = x - 1; xx <= x + w; xx++) {
            walls[y - 1][xx] = TILE_WALL;
            walls[y + h][xx] = TILE_WALL;
        }
        for (int yy = y - 1; yy <= y + h; yy++) {
            walls[yy][x - 1] = TILE_WALL;
            walls[yy][x + w] = TILE_WALL;
        }
    }

    private void stampCorridor(int[][] floor, int[][] walls, int x, int y, int len, int thickness) {
        // Horizontal corridor of given thickness.
        for (int dx = 0; dx < len; dx++) {
            for (int dy = 0; dy < thickness; dy++) {
                int xx = x + dx;
                int yy = y + dy;
                floor[yy][xx] = TILE_FLOOR;
            }
        }
        // Walls above/below corridor.
        for (int dx = 0; dx < len; dx++) {
            int xx = x + dx;
            if (y - 1 >= 0 && floor[y - 1][xx] == 0) walls[y - 1][xx] = TILE_WALL;
            if (y + thickness < CANVAS_H && floor[y + thickness][xx] == 0) walls[y + thickness][xx] = TILE_WALL;
        }
    }

    private void stampGateTiles(int[][] walls, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                walls[yy][xx] = TILE_GATE;
            }
        }
    }

    private void carveOpening(int[][] floor, int[][] walls, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (yy < 0 || yy >= CANVAS_H || xx < 0 || xx >= CANVAS_W) continue;
                walls[yy][xx] = 0;
                if (floor[yy][xx] == 0) floor[yy][xx] = TILE_FLOOR;
            }
        }
    }

    private Crop cropToContent(int[][] floor, int[][] walls, int margin) {
        int minX = CANVAS_W, minY = CANVAS_H, maxX = 0, maxY = 0;
        boolean any = false;
        for (int y = 0; y < CANVAS_H; y++) {
            for (int x = 0; x < CANVAS_W; x++) {
                if (floor[y][x] != 0 || walls[y][x] != 0) {
                    any = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (!any) throw new IllegalStateException("AlgebraForgeTmxGenerator: nothing generated");

        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(CANVAS_W - 1, maxX + margin);
        maxY = Math.min(CANVAS_H - 1, maxY + margin);

        int w = (maxX - minX + 1);
        int h = (maxY - minY + 1);

        int[][] outFloor = new int[h][w];
        int[][] outWalls = new int[h][w];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                outFloor[yy][xx] = floor[minY + yy][minX + xx];
                outWalls[yy][xx] = walls[minY + yy][minX + xx];
            }
        }

        return new Crop(outFloor, outWalls, w, h, minX, minY);
    }

    private String emitTmx(Crop crop, String objectsXml) {
        // This codebase stores TMX gids directly in the tile arrays (see GameConstants.FLOOR_TILE_ID).
        // So we write values as-is (0 means empty), matching GraphLightsOutTmxGenerator.
        String floorCsv = TmxXml.toCsv(crop.floor);
        String wallsCsv = TmxXml.toCsv(crop.walls);

        int w = crop.width;
        int h = crop.height;

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<map version=\"1.10\" tiledversion=\"1.10.2\" orientation=\"orthogonal\" renderorder=\"right-down\" width=\"" + w + "\" height=\"" + h + "\" tilewidth=\"" + GameConstants.TILE_SIZE + "\" tileheight=\"" + GameConstants.TILE_SIZE + "\" infinite=\"0\">\n" +
                "  <tileset firstgid=\"1\" source=\"tilesets/roguelikeSheet_magenta.tsx\"/>\n" +
                "  <layer id=\"1\" name=\"floor\" width=\"" + w + "\" height=\"" + h + "\">\n" +
                "    <data encoding=\"csv\">\n" + floorCsv + "\n    </data>\n" +
                "  </layer>\n" +
                "  <layer id=\"2\" name=\"walls\" width=\"" + w + "\" height=\"" + h + "\">\n" +
                "    <data encoding=\"csv\">\n" + wallsCsv + "\n    </data>\n" +
                "  </layer>\n" +
                "  <objectgroup id=\"3\" name=\"objects\">\n" +
                objectsXml +
                "  </objectgroup>\n" +
                "</map>\n";
    }

    private String objPlayer(String name, int tileX, int tileY) {
        return "    <object id=\"" + nextObjectId() + "\" name=\"" + name + "\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"20\" height=\"32\">\n" +
                "      <properties>\n" +
                "        <property name=\"type\" value=\"player\"/>\n" +
                "      </properties>\n" +
                "    </object>\n";
    }

    private String objToken(String tokenId, int tileX, int tileY) {
        return "    <object id=\"" + nextObjectId() + "\" name=\"token\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                "      <properties>\n" +
            "        <property name=\"type\" value=\"token\"/>\n" +
                "        <property name=\"tokenId\" value=\"" + tokenId + "\"/>\n" +
                "      </properties>\n" +
                "    </object>\n";
    }

    private String objAlgebraTerminal(String terminalType, String opId, int charges, int tileX, int tileY) {
        return "    <object id=\"" + nextObjectId() + "\" name=\"terminal\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                "      <properties>\n" +
            "        <property name=\"type\" value=\"terminal\"/>\n" +
                "        <property name=\"terminalType\" value=\"" + terminalType + "\"/>\n" +
                "        <property name=\"opId\" value=\"" + opId + "\"/>\n" +
                (terminalType.equals("oracle") ? ("        <property name=\"charges\" type=\"int\" value=\"" + charges + "\"/>\n") : "") +
                "      </properties>\n" +
                "    </object>\n";
    }

    private String objSocket(String group, int tileX, int tileY, String requiresTokenId, boolean consume) {
        return "    <object id=\"" + nextObjectId() + "\" name=\"socket\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                "      <properties>\n" +
            "        <property name=\"type\" value=\"socket\"/>\n" +
                "        <property name=\"group\" value=\"" + group + "\"/>\n" +
                "        <property name=\"requiresTokenId\" value=\"" + requiresTokenId + "\"/>\n" +
                "        <property name=\"consumeToken\" type=\"bool\" value=\"" + (consume ? "true" : "false") + "\"/>\n" +
                "      </properties>\n" +
                "    </object>\n";
    }

    private String objGate(String group, int tileX, int tileY, int tileW, int tileH, boolean open) {
        int wPx = tileW * GameConstants.TILE_SIZE;
        int hPx = tileH * GameConstants.TILE_SIZE;
        return "    <object id=\"" + nextObjectId() + "\" name=\"gate\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + wPx + "\" height=\"" + hPx + "\">\n" +
                "      <properties>\n" +
            "        <property name=\"type\" value=\"gate\"/>\n" +
                "        <property name=\"group\" value=\"" + group + "\"/>\n" +
                "        <property name=\"tileId\" value=\"" + GameConstants.GATE_TILE_ID + "\"/>\n" +
                "        <property name=\"open\" type=\"bool\" value=\"" + (open ? "true" : "false") + "\"/>\n" +
                "      </properties>\n" +
                "    </object>\n";
    }

        private String objArenaGate(GateObj g, int shiftX, int shiftY) {
        int wPx = g.w * GameConstants.TILE_SIZE;
        int hPx = g.h * GameConstants.TILE_SIZE;
        return "    <object id=\"" + nextObjectId() + "\" name=\"gate\" x=\"" + px(g.x + shiftX) + "\" y=\"" + px(g.y + shiftY) + "\" width=\"" + wPx + "\" height=\"" + hPx + "\">\n" +
            "      <properties>\n" +
            "        <property name=\"type\" value=\"gate\"/>\n" +
            "        <property name=\"sourceArenaId\" value=\"" + g.sourceArenaId + "\"/>\n" +
            "        <property name=\"targetArenaId\" value=\"" + g.targetArenaId + "\"/>\n" +
            "        <property name=\"tileId\" value=\"" + GameConstants.GATE_TILE_ID + "\"/>\n" +
            "      </properties>\n" +
            "    </object>\n";
        }

        private String objArenaBounds(ArenaDef a, int shiftX, int shiftY) {
        // Include the full arena footprint including the 1-tile border (matches traversal generator semantics).
        int originX = a.x - 1;
        int originY = a.y - 1;
        int wTiles = ARENA_W + 2;
        int hTiles = ARENA_H + 2;

        int xPx = px(originX + shiftX);
        int yPx = px(originY + shiftY);
        int wPx = wTiles * GameConstants.TILE_SIZE;
        int hPx = hTiles * GameConstants.TILE_SIZE;

        return "    <object id=\"" + nextObjectId() + "\" name=\"arena_bounds\" x=\"" + xPx + "\" y=\"" + yPx + "\" width=\"" + wPx + "\" height=\"" + hPx + "\">\n" +
            "      <properties>\n" +
            "        <property name=\"type\" value=\"arena_bounds\"/>\n" +
            "        <property name=\"arenaId\" value=\"" + a.id + "\"/>\n" +
            (a.index == 0 ? "        <property name=\"isStart\" type=\"bool\" value=\"true\"/>\n" : "") +
            "      </properties>\n" +
            "    </object>\n";
        }

        private String objEnemy(int tileX, int tileY, String arenaId) {
        return "    <object id=\"" + nextObjectId() + "\" name=\"enemy\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
            "      <properties>\n" +
            "        <property name=\"type\" value=\"enemy\"/>\n" +
            "        <property name=\"enemy_type\" value=\"DEFAULT\"/>\n" +
            "        <property name=\"arenaId\" value=\"" + arenaId + "\"/>\n" +
            "      </properties>\n" +
            "    </object>\n";
        }

        private String objExit(int tileX, int tileY) {
            return "    <object id=\"" + nextObjectId() + "\" name=\"exit\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                    "      <properties>\n" +
                    "        <property name=\"type\" value=\"exit\"/>\n" +
                    "      </properties>\n" +
                    "    </object>\n";
        }

        private String objPuzzleDoorFinale(String doorId, String puzzleId, String ciphertext, String key, String answer, int tileX, int tileY) {
            return "    <object id=\"" + nextObjectId() + "\" name=\"puzzledoor\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                    "      <properties>\n" +
                    "        <property name=\"type\" value=\"puzzledoor\"/>\n" +
                    "        <property name=\"id\" value=\"" + doorId + "\"/>\n" +
                    "        <property name=\"locked\" type=\"bool\" value=\"true\"/>\n" +
                    "        <property name=\"puzzleId\" value=\"" + puzzleId + "\"/>\n" +
                    "        <property name=\"isFinale\" type=\"bool\" value=\"true\"/>\n" +
                    "        <property name=\"hidden\" type=\"bool\" value=\"true\"/>\n" +
                    "        <property name=\"puzzleType\" value=\"cipher\"/>\n" +
                    "        <property name=\"ciphertext\" value=\"" + ciphertext + "\"/>\n" +
                    "        <property name=\"key\" value=\"VIGENERE:" + key + "\"/>\n" +
                    "        <property name=\"answer\" value=\"" + answer + "\"/>\n" +
                    "        <property name=\"prompt\" value=\"FINAL TERMINAL: Decrypt the message. Enter the plaintext (A-Z only).\"/>\n" +
                    "        <property name=\"hint\" value=\"Vigenere. Use the key shown (A=0..Z).\"/>\n" +
                    "      </properties>\n" +
                    "    </object>\n";
        }

        private String objPuzzleTerminal(String doorId, int tileX, int tileY, boolean allowHiddenDoor) {
            return "    <object id=\"" + nextObjectId() + "\" name=\"terminal\" x=\"" + px(tileX) + "\" y=\"" + px(tileY) + "\" width=\"" + GameConstants.TILE_SIZE + "\" height=\"" + GameConstants.TILE_SIZE + "\">\n" +
                    "      <properties>\n" +
                    "        <property name=\"type\" value=\"terminal\"/>\n" +
                    "        <property name=\"doorId\" value=\"" + doorId + "\"/>\n" +
                    (allowHiddenDoor ? "        <property name=\"allowHiddenDoor\" type=\"bool\" value=\"true\"/>\n" : "") +
                    "      </properties>\n" +
                    "    </object>\n";
        }
    // Exits are rendered as a bright green square and auto-advance to the next level;
    // Algebra Forge completion is intended to be puzzle-driven (gate unlock), not teleport-driven.

    private int objectId = 100;

    private void carveCornerPocket(int xCenter, int yCenter, int[][] floor, int[][] walls) {
        int startOffset = -(CORRIDOR_TOTAL_W / 2);
        int x0 = xCenter + startOffset;
        int y0 = yCenter + startOffset;
        int x1 = x0 + CORRIDOR_TOTAL_W - 1;
        int y1 = y0 + CORRIDOR_TOTAL_W - 1;

        // Make a wider interior area at the elbow so the player collider doesn't snag.
        for (int y = y0; y <= y1; y++) {
            if (y < 0 || y >= CANVAS_H) continue;
            for (int x = x0; x <= x1; x++) {
                if (x < 0 || x >= CANVAS_W) continue;
                walls[y][x] = 0;
                if (floor[y][x] == 0) floor[y][x] = TILE_FLOOR;
            }
        }

        // Restore corridor border walls around the pocket so corners don't open into void.
        rebuildWallsAroundFloorRegion(x0 - 1, y0 - 1, x1 + 1, y1 + 1, floor, walls);
    }

    private void rebuildWallsAroundFloorRegion(int x0, int y0, int x1, int y1, int[][] floor, int[][] walls) {
        for (int y = y0; y <= y1; y++) {
            if (y < 0 || y >= CANVAS_H) continue;
            for (int x = x0; x <= x1; x++) {
                if (x < 0 || x >= CANVAS_W) continue;
                if (floor[y][x] != TILE_FLOOR) continue;

                stampWallIfEmpty(x - 1, y, floor, walls);
                stampWallIfEmpty(x + 1, y, floor, walls);
                stampWallIfEmpty(x, y - 1, floor, walls);
                stampWallIfEmpty(x, y + 1, floor, walls);
            }
        }
    }

    private void stampWallIfEmpty(int x, int y, int[][] floor, int[][] walls) {
        if (x < 0 || x >= CANVAS_W || y < 0 || y >= CANVAS_H) return;
        if (floor[y][x] != 0) return;
        if (walls[y][x] != 0) return;
        walls[y][x] = TILE_WALL;
    }

    private boolean isNearAnyGate(int x, int y, List<GateObj> gates, int marginTiles) {
        if (gates == null || gates.isEmpty()) return false;
        for (GateObj g : gates) {
            if (g == null) continue;
            int gx0 = g.x - marginTiles;
            int gy0 = g.y - marginTiles;
            int gx1 = (g.x + g.w - 1) + marginTiles;
            int gy1 = (g.y + g.h - 1) + marginTiles;
            if (x >= gx0 && x <= gx1 && y >= gy0 && y <= gy1) {
                return true;
            }
        }
        return false;
    }

    private int nextObjectId() {
        return objectId++;
    }

    private int px(int tile) {
        return tile * GameConstants.TILE_SIZE;
    }

    private void write(String outputPath, String content) {
        try {
            File f = new File(outputPath);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileWriter fw = new FileWriter(f)) {
                fw.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException("AlgebraForgeTmxGenerator: failed to write TMX", e);
        }
    }
}
