package com.timonipumba.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.timonipumba.GameConstants;
import com.timonipumba.GameStateManager;
import com.timonipumba.assets.SpriteLoader;
import com.timonipumba.components.*;
import com.timonipumba.level.LevelManager;
import com.timonipumba.world.CorridorBuilder;

import java.util.*;

/**
 * GateSystem:
 *
 * Behaviour:
 * - If player is inside arena A AND there is at least one enemy with arenaId=A:
 *      -> all gates touching arena A are CLOSED.
 * - Otherwise (no enemies in A, or player not in A):
 *      -> all gates touching arena A are OPEN.
 *
 * Additionally:
 * - Spawn arena can be forced always-open via forceArenaAlwaysOpen(arenaId).
 *
 * When a gate opens:
 * - Collision is disabled on the gate entity.
 * - 212 tiles are cleared from the 'walls' layer in that band.
 * - Wall entities with GateWallComponent in that band are removed.
 *
 * Public API is kept compatible with existing code:
 * - GATE_CLOSED_COLOR, GATE_OPEN_COLOR
 * - openAllGatesImmediately(), closeAllGates(), reset(), getters.
 */
public class GateSystem extends EntitySystem {

    /** Color for closed gates (used by procedural generation / other code) */
    public static final Color GATE_CLOSED_COLOR = new Color(0.6f, 0.4f, 0.2f, 1.0f);

    /** Color for open gates - represents floor tile appearance (for logs / debug) */
    public static final Color GATE_OPEN_COLOR = new Color(0.5f, 0.5f, 0.5f, 1.0f);

    /** Tile ID for closed gate (visual tile in walls layer) */
    public static final int GATE_CLOSED_TILE_ID = GameConstants.GATE_TILE_ID;

    /** Tile ID for floor / open gate (for logs only) */
    public static final int FLOOR_TILE_ID = GameConstants.FLOOR_TILE_ID;

    /** Minimum corridor length in tiles (used only if we actually spawn corridors) */
    public static final int MIN_CORRIDOR_LENGTH = 6;

    /** Track which arenas the player has visited (for logs / future use) */
    private final Set<String> visitedArenas = new HashSet<>();

    /** The current arena the player is in (null if in corridor) */
    private String currentPlayerArena = null;

    // Last traversal info captured when the player exits an arena through a gate into a corridor.
    private String lastTraversalFromArenaId = null;
    private String lastTraversalToArenaId = null;
    private String lastTraversalSymbol = null;

    private final GameStateManager gameStateManager;

    private final ComponentMapper<GateComponent> gateMapper   = ComponentMapper.getFor(GateComponent.class);
    private final ComponentMapper<GateDoorComponent> gateDoorMapper = ComponentMapper.getFor(GateDoorComponent.class);
    private final ComponentMapper<CollisionComponent> colMapper = ComponentMapper.getFor(CollisionComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<PlayerComponent> playerMapper = ComponentMapper.getFor(PlayerComponent.class);
    private final ComponentMapper<EnemyComponent> enemyMapper  = ComponentMapper.getFor(EnemyComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);

    private final ComponentMapper<SocketComponent> socketMapper = ComponentMapper.getFor(SocketComponent.class);
    private final ComponentMapper<SwitchComponent> switchMapper = ComponentMapper.getFor(SwitchComponent.class);

    private ImmutableArray<Entity> gates;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> players;

    private ImmutableArray<Entity> sockets;
    private ImmutableArray<Entity> switches;

    /** Sprite loader (not used for gates now, kept for API compatibility) */
    @SuppressWarnings("unused")
    private SpriteLoader spriteLoader;

    /** Corridor builder for spawning corridors when gates open (can be null to disable) */
    private CorridorBuilder corridorBuilder;

    /** Level manager for triggering level transitions */
    private LevelManager levelManager;

    /** Flag to prevent multiple level loads */
    private boolean levelLoadPending = false;

    /** TiledMap reference so we can clear gate tiles (212) when gates open */
    private TiledMap tiledMap;

    /** Arena bounds data for detecting which arena the player is in */
    private final Map<String, float[]> arenaBounds = new HashMap<>();

    /** Track arenas we've already logged as "cleared" once (to avoid log spam) */
    private final Set<String> loggedClearedArenas = new HashSet<>();

    /** Arenas that should be kept always-open (e.g. spawn) */
    private final Set<String> alwaysOpenArenas = new HashSet<>();

    /** Track which gates have already spawned their corridor (avoid duplicates). */
    private final Set<Entity> corridorSpawnedForGate = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Optional: arenas whose exits are temporarily locked by a traversal riddle overlay. */
    private final Set<String> traversalLockedArenas = new HashSet<>();

    /** Optional: when set for an arena, only gates to these target arena IDs may open. */
    private final Map<String, Set<String>> allowedExitTargetsByArena = new HashMap<>();



    public GateSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    // ========= Wiring =========

    public void setSpriteLoader(SpriteLoader spriteLoader) {
        this.spriteLoader = spriteLoader;
    }

    public void setCorridorBuilder(CorridorBuilder corridorBuilder) {
        this.corridorBuilder = corridorBuilder;
    }

    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    public void setTiledMap(TiledMap tiledMap) {
        this.tiledMap = tiledMap;
    }

    /**
     * Register an arena with its bounds for player position tracking.
     */
    public void registerArena(String arenaId, float worldX, float worldY, float width, float height) {
        arenaBounds.put(arenaId, new float[]{worldX, worldY, width, height});
    }

    /**
     * Returns the center of the registered arena bounds, or null if unknown.
     * Used by systems that want to reason about the arena graph (e.g., traversal themes).
     */
    public float[] getArenaCenter(String arenaId) {
        if (arenaId == null) return null;
        float[] b = arenaBounds.get(arenaId);
        if (b == null || b.length < 4) return null;
        return new float[]{b[0] + b[2] / 2f, b[1] + b[3] / 2f};
    }

    /**
     * Returns a defensive copy of the registered arena bounds as {x, y, width, height},
     * or null if the arena is unknown.
     */
    public float[] getArenaBounds(String arenaId) {
        if (arenaId == null) return null;
        float[] b = arenaBounds.get(arenaId);
        if (b == null || b.length < 4) return null;
        return new float[]{b[0], b[1], b[2], b[3]};
    }

    /** Returns the set of arena IDs registered from the current TMX. */
    public java.util.Set<String> getRegisteredArenaIds() {
        return new java.util.HashSet<>(arenaBounds.keySet());
    }

    /**
     * Mark the start arena as visited (player spawns there).
     */
    public void setStartArena(String arenaId) {
        visitedArenas.add(arenaId);
        currentPlayerArena = arenaId;
    }

    // ========= Engine wiring =========

    private final Family gateFamily   = Family.all(GateComponent.class, PositionComponent.class).get();
    private final Family enemyFamily  = Family.all(EnemyComponent.class).get();
    private final Family playerFamily = Family.all(PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get();

    @Override
    public void addedToEngine(Engine engine) {
    }

    private void refreshEntityViews() {
        Engine engine = getEngine();
        if (engine == null) {
            gates = null;
            enemies = null;
            players = null;
            sockets = null;
            switches = null;
            return;
        }

        // Refresh entity views so late-created TMX entities are visible and so
        // public methods (openAllGatesImmediately/closeAllGates/getters) work
        // even if engine.update() hasn't run yet (unit tests rely on this).
        gates = engine.getEntitiesFor(gateFamily);
        enemies = engine.getEntitiesFor(enemyFamily);
        players = engine.getEntitiesFor(playerFamily);

        sockets = engine.getEntitiesFor(Family.all(SocketComponent.class).get());
        switches = engine.getEntitiesFor(Family.all(SwitchComponent.class).get());
    }

    // ========= Main update loop =========

    @Override
    public void update(float deltaTime) {
        if (levelLoadPending) {
            return;
        }

        refreshEntityViews();

        if (gates == null || enemies == null || players == null) {
            return;
        }

        // During LEVEL_CLEAR, many tests and some flows expect gates to open without
        // requiring a player to be inside a specific arena.
        // HOWEVER: riddle-controlled gates (GateDoorComponent) and traversal-locked gates
        // must NOT be force-opened, or puzzle maps trivialize themselves.
        if (gameStateManager != null && gameStateManager.isLevelClear()) {
            // Even during LEVEL_CLEAR, keep arena tracking live so HUD/minimap can
            // highlight the correct current arena while the player moves.
            updateCurrentPlayerArena();

            // Keep puzzle gates responsive to sockets/switches even during LEVEL_CLEAR.
            updateRiddleGateStates();
            openNonRiddleGatesImmediatelyRespectingTraversalLocks();

            // Still update gate animations to keep component state consistent
            for (int i = 0; i < gates.size(); i++) {
                updateGateAnimation(gates.get(i), deltaTime);
            }

            // Corridor auto-advance may still be relevant in some flows.
            checkCorridorTriggerZones();
            return;
        }

        // 1. Track which arena the player is in
        updateCurrentPlayerArena();

        // 2. For each arena that has any gates, enforce:
        //    "player+enemies => closed; otherwise open"
        updateRiddleGateStates();
        enforceArenaGateStates();

        // 3. Run per-gate animation (OPENING -> OPEN)
        for (int i = 0; i < gates.size(); i++) {
            updateGateAnimation(gates.get(i), deltaTime);
        }

        // 4. Level-corridor auto-advance
        checkCorridorTriggerZones();
    }

    /**
     * Opens gates instantly, but skips:
     * - riddle-controlled gates (GateDoorComponent)
     * - traversal-locked / restricted-exit gates
     * This is used for LEVEL_CLEAR, not as a general-purpose "open everything" API.
     */
    private void openNonRiddleGatesImmediatelyRespectingTraversalLocks() {
        if (gates == null) return;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;

            // Skip gates controlled by riddle triggers.
            GateDoorComponent gdc = gateDoorMapper.get(gateEntity);
            if (gdc != null && gdc.group != null && !gdc.group.isEmpty()) {
                continue;
            }

            // Skip traversal-locked / restricted gates (used by traversal riddles).
            if (gate.sourceArenaId != null) {
                if (traversalLockedArenas.contains(gate.sourceArenaId)) {
                    continue;
                }
                if (allowedExitTargetsByArena.containsKey(gate.sourceArenaId)) {
                    continue;
                }
            }

            if (!gate.isOpen()) {
                openGateInstantly(gateEntity, gate);
                spawnCorridorIfConfigured(gateEntity, gate);
            }
        }
    }

    /**
     * Riddle-gates: gates that carry GateDoorComponent are controlled by socket/switch triggers,
     * not by enemy-clear arena logic.
     */
    private void updateRiddleGateStates() {
        if (gates == null || gates.size() == 0) return;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateDoorComponent gdc = gateDoorMapper.get(gateEntity);
            if (gdc == null || gdc.group == null || gdc.group.isEmpty()) {
                continue;
            }

            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;

            boolean shouldBeOpen = isAnyTriggerOn(gdc.group);

            if (shouldBeOpen) {
                if (!gate.isOpen()) {
                    openGateInstantly(gateEntity, gate);
                }
            } else {
                // Safety: don't close on top of the player.
                if (!isPlayerOverlappingGate(gateEntity, gate)) {
                    if (!gate.isClosed()) {
                        closeGate(gateEntity, gate);
                    }
                }
            }
        }
    }

    private boolean isAnyTriggerOn(String group) {
        if (group == null) return false;

        if (switches != null) {
            for (int i = 0; i < switches.size(); i++) {
                SwitchComponent sc = switchMapper.get(switches.get(i));
                if (sc != null && group.equals(sc.group) && sc.on) {
                    return true;
                }
            }
        }

        if (sockets != null) {
            for (int i = 0; i < sockets.size(); i++) {
                SocketComponent s = socketMapper.get(sockets.get(i));
                if (s != null && s.activated && group.equals(s.doorGroup)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void openGateInstantly(Entity gateEntity, GateComponent gate) {
        storeOriginalDimensions(gateEntity, gate);
        gate.openInstantly();
        disableGateCollision(gateEntity);
        updateGateVisualToOpen(gateEntity);
        updateGateRenderableToOpen(gateEntity);
    }

    // ========= Player / arena detection =========

    private void updateCurrentPlayerArena() {
        if (arenaBounds.isEmpty()) {
            currentPlayerArena = null;
            return;
        }

        // Find player
        PositionComponent playerPos = null;
        CollisionComponent playerCol = null;
        for (int i = 0; i < players.size(); i++) {
            Entity player = players.get(i);
            playerPos = posMapper.get(player);
            playerCol = colMapper.get(player);
            if (playerPos != null && playerCol != null) break;
        }
        if (playerPos == null || playerCol == null) {
            currentPlayerArena = null;
            return;
        }

        String newArenaId = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        // arenaBounds iteration order is not stable; if bounds overlap, pick the deepest match.
        for (Map.Entry<String, float[]> entry : arenaBounds.entrySet()) {
            String arenaId = entry.getKey();
            float[] b = entry.getValue();
            if (arenaId == null || b == null || b.length < 4) continue;

            float ax = b[0];
            float ay = b[1];
            float aw = b[2];
            float ah = b[3];

            float halfW = playerCol.width / 2f;
            float halfH = playerCol.height / 2f;
            float centerX = playerPos.x + halfW;
            float centerY = playerPos.y + halfH;

            boolean inside;
            float score;
            if (aw > playerCol.width && ah > playerCol.height) {
                inside =
                        centerX >= ax + halfW && centerX <= ax + aw - halfW &&
                        centerY >= ay + halfH && centerY <= ay + ah - halfH;
                if (!inside) {
                    continue;
                }

                float left = centerX - (ax + halfW);
                float right = (ax + aw - halfW) - centerX;
                float bottom = centerY - (ay + halfH);
                float top = (ay + ah - halfH) - centerY;
                // Larger margin means we're more confidently inside this arena.
                score = Math.min(Math.min(left, right), Math.min(bottom, top));
            } else {
                // Fallback for tiny arenas (or test arenas): use overlap semantics.
                float px0 = playerPos.x;
                float py0 = playerPos.y;
                float px1 = playerPos.x + playerCol.width;
                float py1 = playerPos.y + playerCol.height;
                float ax1 = ax + aw;
                float ay1 = ay + ah;

                float ox = Math.min(px1, ax1) - Math.max(px0, ax);
                float oy = Math.min(py1, ay1) - Math.max(py0, ay);
                inside = ox > 0 && oy > 0;
                if (!inside) {
                    continue;
                }
                // Use overlap area as the score.
                score = ox * oy;
            }

            if (score > bestScore) {
                bestScore = score;
                newArenaId = arenaId;
            }
        }

        // If we are leaving an arena into a corridor (arena -> null), capture which gate was used.
        if (newArenaId == null && currentPlayerArena != null) {
            captureTraversalAtArenaExit(currentPlayerArena);
        }

        if (newArenaId != null && !newArenaId.equals(currentPlayerArena)) {
            visitedArenas.add(newArenaId);
        }

        currentPlayerArena = newArenaId;
    }

    /**
     * Best-effort: when the player exits an arena into a corridor, identify which gate rectangle
     * they are overlapping and record its traversalSymbol + target.
     */
    private void captureTraversalAtArenaExit(String fromArenaId) {
        refreshEntityViews();
        if (players == null || players.size() == 0 || gates == null) return;

        Entity player = players.get(0);
        PositionComponent p = posMapper.get(player);
        CollisionComponent pc = colMapper.get(player);
        if (p == null || pc == null) return;

        float px0 = p.x;
        float py0 = p.y;
        float px1 = p.x + pc.width;
        float py1 = p.y + pc.height;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            PositionComponent gp = posMapper.get(gateEntity);
            CollisionComponent gc = colMapper.get(gateEntity);
            if (gate == null || gp == null || gc == null) continue;
            if (gate.sourceArenaId == null || !gate.sourceArenaId.equals(fromArenaId)) continue;
            if (gate.targetArenaId == null || gate.targetArenaId.isEmpty()) continue;

            // Gates often disable collision when opened (width/height -> 0), but we still need
            // a stable rectangle to detect which gate/lanes the player used.
            float gw = (gc.width > 0f) ? gc.width : (gate.dimensionsStored ? gate.originalWidth : GameConstants.TILE_SIZE);
            float gh = (gc.height > 0f) ? gc.height : (gate.dimensionsStored ? gate.originalHeight : GameConstants.TILE_SIZE);

            float gx0 = gp.x;
            float gy0 = gp.y;
            float gx1 = gp.x + gw;
            float gy1 = gp.y + gh;

            boolean overlap = px0 < gx1 && px1 > gx0 && py0 < gy1 && py1 > gy0;
            if (!overlap) continue;

            lastTraversalFromArenaId = gate.sourceArenaId;
            lastTraversalToArenaId = gate.targetArenaId;
            lastTraversalSymbol = gate.traversalSymbol;
            return;
        }
    }

    /**
     * Consume the last recorded traversal symbol for a specific arena-to-arena move.
     * Returns null if no symbol was captured (e.g., missing bounds, unusual move).
     */
    public String consumeLastTraversalSymbol(String fromArenaId, String toArenaId) {
        if (fromArenaId == null || toArenaId == null) return null;
        if (lastTraversalFromArenaId == null || lastTraversalToArenaId == null) return null;
        if (!fromArenaId.equals(lastTraversalFromArenaId) || !toArenaId.equals(lastTraversalToArenaId)) {
            return null;
        }
        String sym = lastTraversalSymbol;
        lastTraversalFromArenaId = null;
        lastTraversalToArenaId = null;
        lastTraversalSymbol = null;
        return sym;
    }

    // ========= Core behaviour: open/close per arena =========

    private void enforceArenaGateStates() {
        Map<String, Integer> enemiesByArena = buildEnemyCountMap();

        // If no arena bounds are registered, we can't reliably compute player-in-arena.
        // In that case (common in unit tests), fall back to legacy progression:
        // gates are CLOSED while enemies exist in the source arena; OPEN when cleared.
        boolean arenaTrackingEnabled = !arenaBounds.isEmpty();

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;

            // Riddle gates are controlled by socket/switch groups, not arenas.
            if (gateDoorMapper.get(gateEntity) != null) {
                continue;
            }

            String sourceId = gate.sourceArenaId;
            if (sourceId == null) continue;

            boolean forceOpen = alwaysOpenArenas.contains(sourceId);
            int enemyCount = enemiesByArena.getOrDefault(sourceId, 0);

            boolean playerInSource = arenaTrackingEnabled && sourceId.equals(currentPlayerArena);
            boolean shouldClose;
            if (arenaTrackingEnabled) {
                // Gameplay rule: close only if player is inside that arena and enemies remain.
                shouldClose = playerInSource && enemyCount > 0 && !forceOpen;
            } else {
                // Legacy/test rule: gates stay closed until the arena is cleared.
                shouldClose = enemyCount > 0 && !forceOpen;
            }

            if (shouldClose) {
                // Avoid trapping the player inside the doorway band: if the player is still
                // overlapping this gate rectangle (common when just entering an arena), keep it open.
                if (isPlayerOverlappingGate(gateEntity, gate)) {
                    if (gate.isClosed()) {
                        openGateInstantly(gateEntity, gate);
                    }
                    continue;
                }

                if (!gate.isClosed()) {
                    closeGate(gateEntity, gate);
                }
            } else {
                // Avoid logging every empty arena at start; only log arenas the player is actually
                // in or has visited.
                if (enemyCount == 0
                        && (playerInSource || visitedArenas.contains(sourceId))
                        && !loggedClearedArenas.contains(sourceId)) {
                    loggedClearedArenas.add(sourceId);
                }
                // Traversal riddles can temporarily lock an arena's exits and/or restrict
                // which target arena(s) are allowed.
                if (!forceOpen) {
                    if (traversalLockedArenas.contains(sourceId)) {
                        // Same safety rule: don't close on top of the player.
                        if (!isPlayerOverlappingGate(gateEntity, gate)) {
                            if (!gate.isClosed()) {
                                closeGate(gateEntity, gate);
                            }
                        }
                        continue;
                    }

                    Set<String> allowedTargets = allowedExitTargetsByArena.get(sourceId);
                    if (allowedTargets != null && !allowedTargets.isEmpty()) {
                        String target = gate.targetArenaId;
                        // If we can't determine a target arena, don't allow it through a restriction.
                        boolean allowed = target != null && allowedTargets.contains(target);
                        if (!allowed) {
                            // Same safety rule: don't close on top of the player.
                            if (!isPlayerOverlappingGate(gateEntity, gate)) {
                                if (!gate.isClosed()) {
                                    closeGate(gateEntity, gate);
                                }
                            }
                            continue;
                        }
                    }
                }

                if (!gate.isOpen() && !gate.isOpening()) {
                    openGate(gateEntity, gate);
                }
            }
        }
    }

    /**
     * Returns true if the player's AABB overlaps the gate's AABB.
     * Used to avoid closing a gate while the player is still in the doorway band.
     */
    private boolean isPlayerOverlappingGate(Entity gateEntity, GateComponent gate) {
        if (gateEntity == null || gate == null) return false;
        if (players == null || players.size() == 0) return false;

        // Find player
        Entity player = null;
        PositionComponent playerPos = null;
        CollisionComponent playerCol = null;
        for (int i = 0; i < players.size(); i++) {
            Entity p = players.get(i);
            PositionComponent pp = posMapper.get(p);
            CollisionComponent pc = colMapper.get(p);
            if (pp != null && pc != null) {
                player = p;
                playerPos = pp;
                playerCol = pc;
                break;
            }
        }
        if (player == null || playerPos == null || playerCol == null) return false;

        PositionComponent gp = posMapper.get(gateEntity);
        CollisionComponent gc = colMapper.get(gateEntity);
        if (gp == null || gc == null) return false;

        // Gates can disable collision when opened (width/height -> 0). Use stored dimensions.
        float gw = (gc.width > 0f) ? gc.width : (gate.dimensionsStored ? gate.originalWidth : GameConstants.TILE_SIZE);
        float gh = (gc.height > 0f) ? gc.height : (gate.dimensionsStored ? gate.originalHeight : GameConstants.TILE_SIZE);

        float px0 = playerPos.x;
        float py0 = playerPos.y;
        float px1 = playerPos.x + playerCol.width;
        float py1 = playerPos.y + playerCol.height;

        float gx0 = gp.x;
        float gy0 = gp.y;
        float gx1 = gp.x + gw;
        float gy1 = gp.y + gh;

        return px0 < gx1 && px1 > gx0 && py0 < gy1 && py1 > gy0;
    }

    // ========= Traversal riddle helpers =========

    /**
     * @return arena id the player is currently inside, or null if in corridor/unknown.
     */
    public String getCurrentPlayerArenaId() {
        return currentPlayerArena;
    }

    /**
     * @return living enemy count in the given arena id.
     */
    public int getLivingEnemyCountInArena(String arenaId) {
        if (arenaId == null || arenaId.isEmpty()) return 0;
        refreshEntityViews();
        if (enemies == null) return 0;
        return buildEnemyCountMap().getOrDefault(arenaId, 0);
    }

    /**
     * @return true if the player's current arena has any living enemies.
     */
    public boolean isCurrentArenaInCombat() {
        String arenaId = getCurrentPlayerArenaId();
        return arenaId != null && getLivingEnemyCountInArena(arenaId) > 0;
    }

    /**
     * Returns unique outgoing target arena IDs for gates where sourceArenaId == source.
     */
    public List<String> getOutgoingTargets(String sourceArenaId) {
        if (sourceArenaId == null) return java.util.Collections.emptyList();
        refreshEntityViews();
        if (gates == null) return java.util.Collections.emptyList();

        Set<String> targets = new HashSet<>();
        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;
            if (!sourceArenaId.equals(gate.sourceArenaId)) continue;
            if (gate.targetArenaId == null || gate.targetArenaId.isEmpty()) continue;
            targets.add(gate.targetArenaId);
        }
        List<String> out = new ArrayList<>(targets);
        java.util.Collections.sort(out);
        return out;
    }

    /** Lock all exits for an arena (keeps gates CLOSED even if arena is cleared). */
    public void lockArenaExits(String arenaId) {
        if (arenaId == null) return;
        traversalLockedArenas.add(arenaId);
    }

    /** Unlock exits for an arena (normal enemy-based logic applies again). */
    public void unlockArenaExits(String arenaId) {
        if (arenaId == null) return;
        traversalLockedArenas.remove(arenaId);
    }

    /** Restrict which target arena IDs are allowed to open from a given source arena. */
    public void setAllowedExitTargets(String sourceArenaId, Set<String> allowedTargetArenaIds) {
        if (sourceArenaId == null) return;
        if (allowedTargetArenaIds == null || allowedTargetArenaIds.isEmpty()) {
            allowedExitTargetsByArena.remove(sourceArenaId);
            return;
        }
        allowedExitTargetsByArena.put(sourceArenaId, new HashSet<>(allowedTargetArenaIds));
    }

    /** Clear any exit restrictions for a given arena. */
    public void clearAllowedExitTargets(String sourceArenaId) {
        if (sourceArenaId == null) return;
        allowedExitTargetsByArena.remove(sourceArenaId);
    }

    private Map<String, Integer> buildEnemyCountMap() {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < enemies.size(); i++) {
            Entity enemyEntity = enemies.get(i);
            EnemyComponent ec = enemyMapper.get(enemyEntity);
            if (ec == null) continue;

            // Count only living enemies; dead ones may linger for a frame before removal.
            HealthComponent health = healthMapper.get(enemyEntity);
            if (health != null && health.isDead()) {
                continue;
            }

            String arenaId = ec.arenaId;
            if (arenaId == null) continue;

            counts.put(arenaId, counts.getOrDefault(arenaId, 0) + 1);
        }
        return counts;
    }

    private void closeGate(Entity gateEntity, GateComponent gate) {
        gate.close();
        storeOriginalDimensions(gateEntity, gate);

        CollisionComponent col = colMapper.get(gateEntity);
        if (col != null && gate.dimensionsStored) {
            col.width = gate.originalWidth;
            col.height = gate.originalHeight;
        }

        // Restore gate tiles/walls so the gate visibly/physically closes again
        restoreGateTilesAndWalls(gateEntity, gate);

        updateGateRenderableToClosed(gateEntity, gate);
    }

    // ========= Gate animation + visuals =========

    private void openGate(Entity gateEntity, GateComponent gate) {
        storeOriginalDimensions(gateEntity, gate);
        gate.startOpening();

        // no per-frame logging for opening to avoid spam
    }

    private void updateGateAnimation(Entity gateEntity, float deltaTime) {
        GateComponent gate = gateMapper.get(gateEntity);
        if (gate == null) return;

        storeOriginalDimensions(gateEntity, gate);

        if (gate.isOpening()) {
            gate.openingTimer += deltaTime;
            if (gate.openingTimer >= gate.openingDuration) {
                gate.completeOpening();
                disableGateCollision(gateEntity);
                updateGateVisualToOpen(gateEntity);
                updateGateRenderableToOpen(gateEntity);
                spawnCorridorIfConfigured(gateEntity, gate);
            }
        }
    }

    private void spawnCorridorIfConfigured(Entity gateEntity, GateComponent gate) {
        if (corridorBuilder == null) return;
        if (gateEntity == null) return;
        if (corridorSpawnedForGate.contains(gateEntity)) return;

        PositionComponent pos = posMapper.get(gateEntity);
        if (pos == null) return;

        float startX = pos.x;
        float startY = pos.y;

        // Prefer building towards the target arena center when we can.
        float endX;
        float endY;
        if (gate != null && gate.targetArenaId != null && arenaBounds.containsKey(gate.targetArenaId)) {
            float[] b = arenaBounds.get(gate.targetArenaId);
            endX = b[0] + b[2] / 2f;
            endY = b[1] + b[3] / 2f;
        } else {
            // Fallback for unit tests / unlinked gates: spawn a simple straight corridor.
            endX = startX + (MIN_CORRIDOR_LENGTH * GameConstants.TILE_SIZE);
            endY = startY;
        }

        corridorBuilder.buildCorridor(startX, startY, endX, endY, CorridorBuilder.DEFAULT_CORRIDOR_WIDTH);
        corridorSpawnedForGate.add(gateEntity);
    }

    private void updateGateRenderableToOpen(Entity gateEntity) {
        RenderableComponent renderable = gateEntity.getComponent(RenderableComponent.class);
        if (renderable == null) return;

        renderable.width = GameConstants.TILE_SIZE;
        renderable.height = GameConstants.TILE_SIZE;
        renderable.color = new com.badlogic.gdx.graphics.Color(GATE_OPEN_COLOR);
    }

    private void updateGateRenderableToClosed(Entity gateEntity, GateComponent gate) {
        RenderableComponent renderable = gateEntity.getComponent(RenderableComponent.class);
        if (renderable == null) return;

        // Restore visual size to original collision size if we have it.
        if (gate != null && gate.dimensionsStored) {
            renderable.width = gate.originalWidth;
            renderable.height = gate.originalHeight;
        }
        renderable.color = new com.badlogic.gdx.graphics.Color(GATE_CLOSED_COLOR);
    }

    private void storeOriginalDimensions(Entity gateEntity, GateComponent gate) {
        if (!gate.dimensionsStored) {
            CollisionComponent col = colMapper.get(gateEntity);
            if (col != null) {
                gate.originalWidth = col.width;
                gate.originalHeight = col.height;
                gate.dimensionsStored = true;
            }
        }
    }

    private void disableGateCollision(Entity gateEntity) {
        CollisionComponent col = colMapper.get(gateEntity);
        if (col != null) {
            col.width = 0;
            col.height = 0;
        }
    }

    private void updateGateVisualToOpen(Entity gateEntity) {
        clearGateTilesFromWallsLayer(gateEntity);
        clearGateWallsFromEntities(gateEntity);
    }

    /** Re-create gate tiles and gate wall collision when a gate transitions back to CLOSED. */
    private void restoreGateTilesAndWalls(Entity gateEntity, GateComponent gate) {
        if (tiledMap == null) {
            return;
        }

        MapLayer layer = tiledMap.getLayers().get("walls");
        if (!(layer instanceof TiledMapTileLayer)) {
            return;
        }

        TiledMapTileLayer walls = (TiledMapTileLayer) layer;
        PositionComponent pos = posMapper.get(gateEntity);
        if (pos == null) return;

        float tileSize = GameConstants.TILE_SIZE;
        float width = gate.dimensionsStored ? gate.originalWidth : tileSize;
        float height = gate.dimensionsStored ? gate.originalHeight : tileSize;

        int startX = (int) (pos.x / tileSize);
        int startY = (int) (pos.y / tileSize);
        int endX = (int) ((pos.x + width - 1) / tileSize);
        int endY = (int) ((pos.y + height - 1) / tileSize);

        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(walls.getWidth() - 1, endX);
        endY = Math.min(walls.getHeight() - 1, endY);

        com.badlogic.gdx.maps.tiled.TiledMapTile gateTile = tiledMap.getTileSets().getTile(GATE_CLOSED_TILE_ID);
        // If the gate tile is missing, still restore collision walls; visuals will be skipped.

        // Remove any existing gate-wall entities overlapping before we recreate
        Engine engine = getEngine();
        if (engine != null) {
            ImmutableArray<Entity> wallsEntities = engine.getEntitiesFor(
                    Family.all(WallComponent.class, PositionComponent.class, CollisionComponent.class).get()
            );
            for (int i = 0; i < wallsEntities.size(); i++) {
                Entity wall = wallsEntities.get(i);
                GateWallComponent gw = wall.getComponent(GateWallComponent.class);
                if (gw == null) continue;

                PositionComponent wPos = wall.getComponent(PositionComponent.class);
                CollisionComponent wCol = wall.getComponent(CollisionComponent.class);
                if (wPos == null || wCol == null) continue;

                float wx1 = wPos.x;
                float wy1 = wPos.y;
                float wx2 = wPos.x + wCol.width;
                float wy2 = wPos.y + wCol.height;

                boolean overlaps =
                        wx1 < (pos.x + width) && wx2 > pos.x &&
                        wy1 < (pos.y + height) && wy2 > pos.y;

                if (overlaps) {
                    engine.removeEntity(wall);
                }
            }
        }

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                if (gateTile != null) {
                    TiledMapTileLayer.Cell cell = new TiledMapTileLayer.Cell();
                    cell.setTile(gateTile);
                    walls.setCell(x, y, cell);
                }

                if (engine != null) {
                    Entity wallEntity = engine.createEntity();
                    wallEntity.add(new PositionComponent(x * tileSize, y * tileSize));
                    wallEntity.add(new CollisionComponent(tileSize, tileSize));
                    wallEntity.add(new WallComponent());
                    wallEntity.add(new GateWallComponent());
                    engine.addEntity(wallEntity);
                }
            }
        }
    }

    private void clearGateTilesFromWallsLayer(Entity gateEntity) {
        if (tiledMap == null) {
            return;
        }

        MapLayer layer = tiledMap.getLayers().get("walls");
        if (!(layer instanceof TiledMapTileLayer)) {
            return;
        }

        TiledMapTileLayer walls = (TiledMapTileLayer) layer;
        PositionComponent pos = posMapper.get(gateEntity);
        GateComponent gate = gateMapper.get(gateEntity);
        if (pos == null || gate == null) return;

        float tileSize = GameConstants.TILE_SIZE;
        float width = gate.dimensionsStored ? gate.originalWidth : tileSize;
        float height = gate.dimensionsStored ? gate.originalHeight : tileSize;

        int startX = (int) (pos.x / tileSize);
        int startY = (int) (pos.y / tileSize);
        int endX = (int) ((pos.x + width - 1) / tileSize);
        int endY = (int) ((pos.y + height - 1) / tileSize);

        startX = Math.max(0, startX);
        startY = Math.max(0, startY);
        endX = Math.min(walls.getWidth() - 1, endX);
        endY = Math.min(walls.getHeight() - 1, endY);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                TiledMapTileLayer.Cell cell = walls.getCell(x, y);
                if (cell != null && cell.getTile() != null &&
                        cell.getTile().getId() == GATE_CLOSED_TILE_ID) {
                    walls.setCell(x, y, null);
                }
            }
        }
    }

    private void clearGateWallsFromEntities(Entity gateEntity) {
        Engine engine = getEngine();
        if (engine == null) return;

        PositionComponent pos = posMapper.get(gateEntity);
        GateComponent gate = gateMapper.get(gateEntity);
        if (pos == null || gate == null) return;

        float tileSize = GameConstants.TILE_SIZE;
        float width = gate.dimensionsStored ? gate.originalWidth : tileSize;
        float height = gate.dimensionsStored ? gate.originalHeight : tileSize;

        float minX = pos.x;
        float minY = pos.y;
        float maxX = pos.x + width;
        float maxY = pos.y + height;

        ImmutableArray<Entity> walls = engine.getEntitiesFor(
                Family.all(WallComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );

        int removed = 0;
        for (int i = 0; i < walls.size(); i++) {
            Entity wall = walls.get(i);
            GateWallComponent gw = wall.getComponent(GateWallComponent.class);
            if (gw == null) continue;

            PositionComponent wPos = wall.getComponent(PositionComponent.class);
            CollisionComponent wCol = wall.getComponent(CollisionComponent.class);
            if (wPos == null || wCol == null) continue;

            float wx1 = wPos.x;
            float wy1 = wPos.y;
            float wx2 = wPos.x + wCol.width;
            float wy2 = wPos.y + wCol.height;

            boolean overlaps =
                    wx1 < maxX && wx2 > minX &&
                    wy1 < maxY && wy2 > minY;

            if (overlaps) {
                engine.removeEntity(wall);
                removed++;
            }
        }

    }

    // ========= Corridor / trigger zones =========

    private void checkCorridorTriggerZones() {
        if (corridorBuilder == null || levelManager == null) {
            return;
        }

        java.util.List<Entity> corridorEntities = corridorBuilder.getCorridorEntities();
        for (Entity corridor : corridorEntities) {
            PositionComponent corridorPos = posMapper.get(corridor);
            if (corridorPos == null) continue;

            for (int p = 0; p < players.size(); p++) {
                Entity player = players.get(p);
                PositionComponent playerPos = posMapper.get(player);
                CollisionComponent playerCol = colMapper.get(player);
                if (playerPos == null || playerCol == null) continue;

                float corridorSize = GameConstants.TILE_SIZE;
                if (checkOverlap(playerPos, playerCol, corridorPos, corridorSize, corridorSize)) {
                    java.util.List<Entity> allCorridorTiles = corridorBuilder.getCorridorEntities();
                    if (!allCorridorTiles.isEmpty()) {
                        Entity lastTile = allCorridorTiles.get(allCorridorTiles.size() - 1);
                        PositionComponent lastTilePos = posMapper.get(lastTile);
                        if (lastTilePos != null &&
                                Math.abs(corridorPos.x - lastTilePos.x) < 1 &&
                                Math.abs(corridorPos.y - lastTilePos.y) < 1) {
                            levelLoadPending = true;
                            levelManager.loadNextLevel();
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean checkOverlap(PositionComponent pos1, CollisionComponent col1,
                                 PositionComponent pos2, float width2, float height2) {
        return pos1.x < pos2.x + width2 &&
                pos1.x + col1.width > pos2.x &&
                pos1.y < pos2.y + height2 &&
                pos1.y + col1.height > pos2.y;
    }

    // ========= Utility / public API =========

    /**
     * Used by other systems (e.g. puzzles) to open all gates instantly.
     */
    public void openAllGatesImmediately() {
        refreshEntityViews();
        if (gates == null) return;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;

            storeOriginalDimensions(gateEntity, gate);
            gate.openInstantly();
            disableGateCollision(gateEntity);
            updateGateVisualToOpen(gateEntity);
            updateGateRenderableToOpen(gateEntity);
            spawnCorridorIfConfigured(gateEntity, gate);
        }
    }

    public void closeAllGates() {
        refreshEntityViews();
        if (gates == null) return;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            CollisionComponent col = colMapper.get(gateEntity);

            if (gate != null) {
                gate.close();
                if (col != null && gate.dimensionsStored) {
                    col.width = gate.originalWidth;
                    col.height = gate.originalHeight;
                }
                updateGateRenderableToClosed(gateEntity, gate);
            }
        }
    }

    public void reset() {
        visitedArenas.clear();
        currentPlayerArena = null;
        loggedClearedArenas.clear();
        alwaysOpenArenas.clear();
        corridorSpawnedForGate.clear();
        traversalLockedArenas.clear();
        allowedExitTargetsByArena.clear();
        levelLoadPending = false;
    }

    public int getClosedGateCount() {
        int count = 0;
        refreshEntityViews();
        if (gates == null) return 0;
        for (int i = 0; i < gates.size(); i++) {
            GateComponent gate = gateMapper.get(gates.get(i));
            if (gate != null && gate.isClosed()) {
                count++;
            }
        }
        return count;
    }

    public int getOpenGateCount() {
        int count = 0;
        refreshEntityViews();
        if (gates == null) return 0;
        for (int i = 0; i < gates.size(); i++) {
            GateComponent gate = gateMapper.get(gates.get(i));
            if (gate != null && gate.isOpen()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalGateCount() {
        refreshEntityViews();
        return (gates != null) ? gates.size() : 0;
    }

    // ========= Spawn / always-open helpers =========

    /**
     * Force an arena to be always open:
     * - Immediately opens all gates touching this arena (disables collision, clears tiles + walls).
     * - Marks the arena as "always open" so we don't try to close it later.
     */
    public void forceArenaAlwaysOpen(String arenaId) {
        if (arenaId == null) return;

        Engine engine = getEngine();
        if (engine == null) return;

        // Refresh gates list in case we're called early
        gates = engine.getEntitiesFor(gateFamily);
        if (gates == null) return;

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null) continue;

            boolean touchesArena =
                    arenaId.equals(gate.sourceArenaId) ||
                    arenaId.equals(gate.targetArenaId);

            if (!touchesArena) continue;

            // Make sure we have original dimensions
            storeOriginalDimensions(gateEntity, gate);

            // Mark as open in component
            gate.openInstantly();

            // Disable collision and clear visuals
            disableGateCollision(gateEntity);
            updateGateVisualToOpen(gateEntity);
        }

        alwaysOpenArenas.add(arenaId);
        loggedClearedArenas.add(arenaId);
    }
}
