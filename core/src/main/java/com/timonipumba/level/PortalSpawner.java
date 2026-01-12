package com.timonipumba.level;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameConstants;
import com.timonipumba.components.*;
import com.timonipumba.systems.PortalSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Spawns a wall-segment portal when a puzzle is solved.
 * Activates immediately so it is visible in the same frame.
 */
public class PortalSpawner {
    
    private final Engine engine;
    private PortalSystem portalSystem;
    
    private final ComponentMapper<WallComponent> wallMapper;
    private final ComponentMapper<PositionComponent> positionMapper;
    private final ComponentMapper<CollisionComponent> collisionMapper;
    private final ComponentMapper<RenderableComponent> renderableMapper;
    private final ComponentMapper<PortalComponent> portalMapper;
    
    public PortalSpawner(Engine engine) {
        this.engine = engine;
        this.wallMapper = ComponentMapper.getFor(WallComponent.class);
        this.positionMapper = ComponentMapper.getFor(PositionComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
        this.portalMapper = ComponentMapper.getFor(PortalComponent.class);
    }
    
    /** Set the portal system for activation callbacks. */
    public void setPortalSystem(PortalSystem portalSystem) {
        this.portalSystem = portalSystem;
    }
    
    /** Creates and activates a portal segment for the given puzzle. */
    public boolean onPuzzleSolved(String puzzleId) {
        if (!PortalConfig.WALL_SEGMENT_PORTALS_ENABLED) {
            return false;
        }
        
        if (puzzleId == null || puzzleId.isEmpty()) {
            return false;
        }
        
        // Check if portal already exists for this puzzle
        if (hasPortalForPuzzle(puzzleId)) {
            activateExistingPortal(puzzleId);
            return true;
        }
        
        // Find a valid wall segment for the portal
        List<Entity> segment = findPortalWallSegment(puzzleId);
        
        if (segment.isEmpty()) {
            return false;
        }
        
        // Activate directly; ECS Family queries update next frame.
        String portalGroupId = "portal_" + puzzleId;
        int index = 0;
        for (Entity wallEntity : segment) {
            // Add portal component to wall tile
            PortalComponent portalComp = new PortalComponent(puzzleId, portalGroupId, index, segment.size());
            wallEntity.add(portalComp);
            
            // Activate the portal immediately
            portalComp.activate();
            
            // Update visual to green portal color
            RenderableComponent renderable = renderableMapper.get(wallEntity);
            if (renderable != null) {
                renderable.color = new Color(PortalSystem.PORTAL_COLOR);
            }
            
            index++;
        }
        return true;
    }
    
    /**
     * Check if a portal already exists for the given puzzle.
     */
    private boolean hasPortalForPuzzle(String puzzleId) {
        ImmutableArray<Entity> portals = engine.getEntitiesFor(
            Family.all(PortalComponent.class).get()
        );
        
        for (Entity portal : portals) {
            PortalComponent portalComp = portalMapper.get(portal);
            if (portalComp != null && puzzleId.equals(portalComp.puzzleId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Activate an existing portal for the given puzzle.
     * 
     * Note: We query the engine directly here rather than using PortalSystem's
     * cached Family query, because the cached query may not reflect recently
     * added PortalComponents until the next engine.update() call.
     */
    private void activateExistingPortal(String puzzleId) {
        // Query engine directly for most up-to-date portal list
        ImmutableArray<Entity> portals = engine.getEntitiesFor(
            Family.all(PortalComponent.class).get()
        );
        for (Entity portal : portals) {
            PortalComponent portalComp = portalMapper.get(portal);
            if (portalComp != null && puzzleId.equals(portalComp.puzzleId) && !portalComp.isActive()) {
                portalComp.activate();
                
                // Update visual to green portal color
                RenderableComponent renderable = renderableMapper.get(portal);
                if (renderable != null) {
                    renderable.color = new Color(PortalSystem.PORTAL_COLOR);
                }
            }
        }
    }
    
    /**
     * Find a valid wall segment for portal placement.
     * 
     * @param puzzleId Used for deterministic random seed
     * @return List of wall entities forming the portal segment (empty if none found)
     */
    List<Entity> findPortalWallSegment(String puzzleId) {
        ImmutableArray<Entity> walls = engine.getEntitiesFor(
            Family.all(WallComponent.class, PositionComponent.class).get()
        );
        
        if (walls.size() == 0) {
            return Collections.emptyList();
        }
        
        // Build a grid map of wall positions for efficient lookup
        WallGrid grid = buildWallGrid(walls);
        
        // Find all valid horizontal and vertical runs
        List<WallRun> validRuns = findValidWallRuns(grid, walls);
        
        if (validRuns.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Select a run using deterministic random based on puzzle ID
        WallRun selectedRun = selectRun(validRuns, puzzleId);
        
        return selectedRun.entities;
    }
    
    /**
     * Build a grid representation of wall positions.
     * Excludes walls that already have a PortalComponent to prevent 
     * reusing the same tiles for multiple puzzles.
     */
    private WallGrid buildWallGrid(ImmutableArray<Entity> walls) {
        WallGrid grid = new WallGrid();
        
        for (Entity wall : walls) {
            // Skip walls that already have portal component (already used by another puzzle)
            // This ensures each puzzle gets its own unique portal segment
            if (portalMapper.get(wall) != null) {
                continue;
            }
            
            PositionComponent pos = positionMapper.get(wall);
            if (pos != null) {
                int gridX = (int) (pos.x / GameConstants.TILE_SIZE);
                int gridY = (int) (pos.y / GameConstants.TILE_SIZE);
                grid.add(gridX, gridY, wall);
            }
        }
        
        return grid;
    }
    
    /**
     * Find all valid wall runs (horizontal and vertical) that meet segment length requirements.
     */
    private List<WallRun> findValidWallRuns(WallGrid grid, ImmutableArray<Entity> walls) {
        List<WallRun> validRuns = new ArrayList<>();
        
        // Find horizontal runs
        List<WallRun> horizontalRuns = grid.findHorizontalRuns(
            PortalConfig.MIN_SEGMENT_LENGTH, 
            PortalConfig.MAX_SEGMENT_LENGTH
        );
        validRuns.addAll(horizontalRuns);
        
        // Find vertical runs
        List<WallRun> verticalRuns = grid.findVerticalRuns(
            PortalConfig.MIN_SEGMENT_LENGTH, 
            PortalConfig.MAX_SEGMENT_LENGTH
        );
        validRuns.addAll(verticalRuns);
        
        // Prefer boundary walls (filter/score)
        scoreBoundaryPreference(validRuns, grid);
        
        return validRuns;
    }
    
    /**
     * Score runs based on boundary preference.
     * Runs on the edges of the map or room boundaries get higher scores.
     */
    private void scoreBoundaryPreference(List<WallRun> runs, WallGrid grid) {
        for (WallRun run : runs) {
            int boundaryScore = 0;
            
            // Check if run is on edge of grid
            if (run.isHorizontal) {
                // Check if there are no walls above or below the run
                boolean hasWallsAbove = grid.hasWallsAtY(run.startY + 1, run.startX, run.startX + run.length - 1);
                boolean hasWallsBelow = grid.hasWallsAtY(run.startY - 1, run.startX, run.startX + run.length - 1);
                
                if (!hasWallsAbove) boundaryScore += 10;
                if (!hasWallsBelow) boundaryScore += 10;
            } else {
                // Vertical run
                boolean hasWallsLeft = grid.hasWallsAtX(run.startX - 1, run.startY, run.startY + run.length - 1);
                boolean hasWallsRight = grid.hasWallsAtX(run.startX + 1, run.startY, run.startY + run.length - 1);
                
                if (!hasWallsLeft) boundaryScore += 10;
                if (!hasWallsRight) boundaryScore += 10;
            }
            
            // Prefer shorter runs (closer to preferred length)
            int lengthDiff = Math.abs(run.length - PortalConfig.PREFERRED_SEGMENT_LENGTH);
            boundaryScore -= lengthDiff;
            
            run.score = boundaryScore;
        }
    }
    
    /**
     * Select a run from valid runs using deterministic random.
     */
    private WallRun selectRun(List<WallRun> runs, String puzzleId) {
        if (runs.isEmpty()) {
            return null;
        }
        
        // Sort by score (higher is better)
        runs.sort(Comparator.comparingInt((WallRun r) -> r.score).reversed());
        
        // Take top candidates with same or similar score
        int topScore = runs.get(0).score;
        List<WallRun> topRuns = new ArrayList<>();
        for (WallRun run : runs) {
            if (run.score >= topScore - 2) { // Allow some variance
                topRuns.add(run);
            }
        }
        
        // Use deterministic random to select from top candidates
        long seed = PortalConfig.PLACEMENT_SEED;
        if (puzzleId != null) {
            seed ^= puzzleId.hashCode();
        }
        
        Random random = new Random(seed);
        int selectedIndex = random.nextInt(topRuns.size());
        
        return topRuns.get(selectedIndex);
    }
    
    /**
     * Represents a run of consecutive wall tiles.
     */
    static class WallRun {
        List<Entity> entities = new ArrayList<>();
        int startX;
        int startY;
        int length;
        boolean isHorizontal;
        int score = 0;
        
        WallRun(int startX, int startY, boolean isHorizontal) {
            this.startX = startX;
            this.startY = startY;
            this.isHorizontal = isHorizontal;
        }
    }
    
    /**
     * Grid-based representation of wall positions for efficient lookup.
     */
    static class WallGrid {
        private final java.util.Map<Long, Entity> grid = new java.util.HashMap<>();
        private int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        private int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        
        void add(int x, int y, Entity entity) {
            grid.put(key(x, y), entity);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        
        Entity get(int x, int y) {
            return grid.get(key(x, y));
        }
        
        boolean has(int x, int y) {
            return grid.containsKey(key(x, y));
        }
        
        private long key(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }
        
        /**
         * Find horizontal runs of consecutive walls.
         * If a run is longer than maxLength, extracts valid-length sub-segments.
         */
        List<WallRun> findHorizontalRuns(int minLength, int maxLength) {
            List<WallRun> runs = new ArrayList<>();
            
            for (int y = minY; y <= maxY; y++) {
                int runStartX = -1;
                List<Entity> currentRun = new ArrayList<>();
                
                for (int x = minX; x <= maxX + 1; x++) {
                    Entity wall = get(x, y);
                    
                    if (wall != null) {
                        if (runStartX == -1) {
                            runStartX = x;
                        }
                        currentRun.add(wall);
                    } else {
                        // Run ended or gap
                        extractValidRuns(runs, currentRun, runStartX, y, true, minLength, maxLength);
                        runStartX = -1;
                        currentRun.clear();
                    }
                }
            }
            
            return runs;
        }
        
        /**
         * Find vertical runs of consecutive walls.
         * If a run is longer than maxLength, extracts valid-length sub-segments.
         */
        List<WallRun> findVerticalRuns(int minLength, int maxLength) {
            List<WallRun> runs = new ArrayList<>();
            
            for (int x = minX; x <= maxX; x++) {
                int runStartY = -1;
                List<Entity> currentRun = new ArrayList<>();
                
                for (int y = minY; y <= maxY + 1; y++) {
                    Entity wall = get(x, y);
                    
                    if (wall != null) {
                        if (runStartY == -1) {
                            runStartY = y;
                        }
                        currentRun.add(wall);
                    } else {
                        // Run ended or gap
                        extractValidRuns(runs, currentRun, x, runStartY, false, minLength, maxLength);
                        runStartY = -1;
                        currentRun.clear();
                    }
                }
            }
            
            return runs;
        }
        
        /**
         * Extract valid-length segments from a wall run.
         * If the run is in valid range, adds it directly.
         * If the run is too long, extracts sub-segments of valid length.
         */
        private void extractValidRuns(List<WallRun> runs, List<Entity> walls, 
                                      int startX, int startY, boolean horizontal,
                                      int minLength, int maxLength) {
            if (walls.size() < minLength) {
                return; // Too short
            }
            
            if (walls.size() <= maxLength) {
                // Exactly in valid range - add as-is
                WallRun run = new WallRun(startX, startY, horizontal);
                run.entities.addAll(walls);
                run.length = walls.size();
                runs.add(run);
            } else {
                // Too long - extract non-overlapping sub-segments of preferred/max length
                for (int offset = 0; offset <= walls.size() - minLength; offset += maxLength) {
                    int segLength = Math.min(maxLength, walls.size() - offset);
                    if (segLength >= minLength) {
                        int segStartX = horizontal ? startX + offset : startX;
                        int segStartY = horizontal ? startY : startY + offset;
                        
                        WallRun run = new WallRun(segStartX, segStartY, horizontal);
                        for (int i = offset; i < offset + segLength; i++) {
                            run.entities.add(walls.get(i));
                        }
                        run.length = segLength;
                        runs.add(run);
                    }
                }
            }
        }
        
        /**
         * Check if there are any walls at a given Y level within the X range.
         * Note: For very large maps, this could be optimized by caching boundary info
         * during grid construction. Current implementation is O(range) which is
         * acceptable for typical level sizes (< 100 tiles per dimension).
         */
        boolean hasWallsAtY(int y, int fromX, int toX) {
            for (int x = fromX; x <= toX; x++) {
                if (has(x, y)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Check if there are any walls at a given X level within the Y range.
         * Note: For very large maps, this could be optimized by caching boundary info
         * during grid construction. Current implementation is O(range) which is
         * acceptable for typical level sizes (< 100 tiles per dimension).
         */
        boolean hasWallsAtX(int x, int fromY, int toY) {
            for (int y = fromY; y <= toY; y++) {
                if (has(x, y)) {
                    return true;
                }
            }
            return false;
        }
    }
}
