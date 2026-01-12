package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.components.LightsOutNodeComponent;
import com.timonipumba.util.GlyphNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * System that renders the HUD overlay including:
 * - Player health (top-left corner)
 * - Enemy count (below health)
 * - Game over message (centered, when in GAME_OVER state)
 * - Contextual interaction prompt (when near interactable objects like terminals)
 * 
 * Uses a separate orthographic camera for screen-space UI rendering.
 */
public class HUDSystem extends EntitySystem {
    
    private static final int HUD_PADDING = 10;
    private static final int LINE_HEIGHT = 20;
    
    private final GameStateManager gameStateManager;
    private GateSystem gateSystem;
    private DungeonThemeTraversalSystem dungeonThemeTraversalSystem;
    private RegisterAllocationSystem registerAllocationSystem;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final OrthographicCamera hudCamera;
    private final GlyphLayout glyphLayout;

    private final Map<String, float[]> miniMapNodePositions = new HashMap<>();
    private float miniMapBoxX;
    private float miniMapBoxY;
    private float miniMapBoxW;
    private float miniMapBoxH;

    // Optional: tile-based minimap (used by register-allocation mode which doesn't use GateSystem arena registration).
    private TiledMap tiledMap;
    private int[][] tileMiniMap; // 0 void, 1 floor, 2 wall
    private int tileMiniMapMinX;
    private int tileMiniMapMinY;
    private int tileMiniMapScale;
    private int tileMiniMapW;
    private int tileMiniMapH;
    
    private ComponentMapper<HealthComponent> healthMapper;
    private ComponentMapper<PositionComponent> positionMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<TerminalComponent> terminalMapper;
    private ComponentMapper<AlgebraTerminalComponent> algebraTerminalMapper;
    private ComponentMapper<PuzzleDoorComponent> puzzleDoorMapper;
    private ComponentMapper<RenderableComponent> renderableMapper;
    private ComponentMapper<RegisterNodeComponent> registerNodeMapper;
    private ComponentMapper<LightsOutNodeComponent> lightsOutNodeMapper;
    private ComponentMapper<PlayerInventoryComponent> inventoryMapper;
    private ComponentMapper<SocketComponent> socketMapper;
    
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> terminals;
    private ImmutableArray<Entity> puzzleDoors;
    private ImmutableArray<Entity> registerNodeSockets;
    private ImmutableArray<Entity> lightsOutNodeSockets;
    private ImmutableArray<Entity> sockets;
    
    // Track screen dimensions to avoid redundant camera updates
    private int lastScreenWidth;
    private int lastScreenHeight;
    
    public HUDSystem(GameStateManager gameStateManager) {
        // Run HUD rendering after world rendering (high priority number = run later)
        super(100);
        this.gameStateManager = gameStateManager;
        this.batch = new SpriteBatch();
        this.shapeRenderer = new ShapeRenderer();
        this.font = new BitmapFont(); // Default LibGDX font
        this.font.setColor(Color.WHITE);
        this.hudCamera = new OrthographicCamera();
        this.lastScreenWidth = Gdx.graphics.getWidth();
        this.lastScreenHeight = Gdx.graphics.getHeight();
        this.hudCamera.setToOrtho(false, lastScreenWidth, lastScreenHeight);
        this.glyphLayout = new GlyphLayout();
        this.healthMapper = ComponentMapper.getFor(HealthComponent.class);
        this.positionMapper = ComponentMapper.getFor(PositionComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.terminalMapper = ComponentMapper.getFor(TerminalComponent.class);
        this.algebraTerminalMapper = ComponentMapper.getFor(AlgebraTerminalComponent.class);
        this.puzzleDoorMapper = ComponentMapper.getFor(PuzzleDoorComponent.class);
        this.renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
        this.registerNodeMapper = ComponentMapper.getFor(RegisterNodeComponent.class);
        this.lightsOutNodeMapper = ComponentMapper.getFor(LightsOutNodeComponent.class);
        this.inventoryMapper = ComponentMapper.getFor(PlayerInventoryComponent.class);
        this.socketMapper = ComponentMapper.getFor(SocketComponent.class);
    }

    public void setGateSystem(GateSystem gateSystem) {
        this.gateSystem = gateSystem;
    }

    public void setDungeonThemeTraversalSystem(DungeonThemeTraversalSystem dungeonThemeTraversalSystem) {
        this.dungeonThemeTraversalSystem = dungeonThemeTraversalSystem;
    }

    public void setRegisterAllocationSystem(RegisterAllocationSystem registerAllocationSystem) {
        this.registerAllocationSystem = registerAllocationSystem;
    }

    public void setTiledMap(TiledMap tiledMap) {
        this.tiledMap = tiledMap;
        // Invalidate cache.
        this.tileMiniMap = null;
    }

    private int getTopHudLinesCount() {
        // Top-left text stack:
        // - HP line (always)
        // - optional S line (traversal)
        // - optional REG line (register allocation)
        // - Enemies line (always)
        int lines = 2;
        if (dungeonThemeTraversalSystem != null) {
            lines++;
        }
        if (registerAllocationSystem != null && registerAllocationSystem.getNodeCount() > 0) {
            lines++;
        }
        return lines;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, HealthComponent.class, PositionComponent.class).get());
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class).get());
        terminals = engine.getEntitiesFor(Family.all(TerminalComponent.class, PositionComponent.class).get());
        puzzleDoors = engine.getEntitiesFor(Family.all(PuzzleDoorComponent.class, PositionComponent.class).get());
        registerNodeSockets = engine.getEntitiesFor(Family.all(RegisterNodeComponent.class, SocketComponent.class, PositionComponent.class).get());
        lightsOutNodeSockets = engine.getEntitiesFor(Family.all(LightsOutNodeComponent.class, SocketComponent.class, PositionComponent.class).get());
        sockets = engine.getEntitiesFor(Family.all(SocketComponent.class, PositionComponent.class).get());
    }
    
    @Override
    public void update(float deltaTime) {
        // Only update camera if screen size changed
        int currentWidth = Gdx.graphics.getWidth();
        int currentHeight = Gdx.graphics.getHeight();
        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            lastScreenWidth = currentWidth;
            lastScreenHeight = currentHeight;
            hudCamera.setToOrtho(false, currentWidth, currentHeight);
        }
        hudCamera.update();

        // Keep the Algebra Forge overlay fully readable (intro + terminal interaction both use ALGEBRA_FORGE_RIDDLE).
        if (gameStateManager != null && gameStateManager.getState() == GameState.ALGEBRA_FORGE_RIDDLE) {
            return;
        }

        // Draw minimap first (shapes), then text overlays (sprites).
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        drawMiniMap();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();

        drawMiniMapLabels();
        
        // Draw player health
        drawPlayerHealth();
        
        // Draw enemy count
        drawEnemyCount();

        // Show a small glyph inventory and discovery toasts (Algebra Forge feedback).
        drawGlyphInventoryAndToast(deltaTime);
        
        // Draw game state overlay
        drawGameStateOverlay();
        
        // Draw interaction prompt if near interactable
        drawInteractionPrompt();

        // Small persistent hint.
        drawManualHint();
        
        batch.end();
    }

    private void drawManualHint() {
        String hint = "Press M to see the manual";

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        // Bottom-right, slightly inset.
        float pad = 10f;
        glyphLayout.setText(font, hint);
        float x = screenW - pad - glyphLayout.width;
        float y = pad + glyphLayout.height;

        // Keep it subtle so it doesn't compete with minimap or prompts.
        Color prev = font.getColor();
        font.setColor(1f, 1f, 1f, 0.55f);
        font.draw(batch, hint, x, y);
        font.setColor(prev);
    }

    private void drawGlyphInventoryAndToast(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerInventoryComponent inv = inventoryMapper.get(player);
        if (inv == null) return;

        // Tick toast timer.
        if (inv.toastSecondsRemaining > 0f) {
            inv.toastSecondsRemaining = Math.max(0f, inv.toastSecondsRemaining - deltaTime);
            if (inv.toastSecondsRemaining <= 0f) {
                inv.toastMessage = "";
            }
        }

        // Inventory panel (top-right).
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        float x = screenW - 240f;
        float y = screenH - 18f;

        font.setColor(Color.WHITE);
        font.draw(batch, "GLYPHS", x, y);
        y -= 18f;

        int lines = 0;
        for (String tokenId : inv.discoveredTokens) {
            if (!GlyphNames.isGlyph(tokenId)) continue;
            int count = inv.tokenCounts.get(tokenId, 0);
            if (count <= 0) continue;
            font.setColor(new Color(0.85f, 1.0f, 0.85f, 1f));
            font.draw(batch, GlyphNames.displayName(tokenId) + "  x" + count, x, y);
            y -= 18f;
            if (++lines >= 8) break;
        }

        // Toast (bottom-left-ish so it doesn't fight minimap).
        if (inv.toastSecondsRemaining > 0f && inv.toastMessage != null && !inv.toastMessage.isEmpty()) {
            font.setColor(new Color(1f, 0.95f, 0.55f, 1f));
            font.draw(batch, inv.toastMessage, 30f, 70f);
        }

        font.setColor(Color.WHITE);
    }

    private void drawMiniMap() {
        // Avoid stale minimap rendering: clear cached positions up-front so if arena
        // registration is missing/empty this frame, the minimap disappears rather than
        // showing an old layout.
        miniMapNodePositions.clear();

        // If a puzzle/terminal overlay is open, do not draw the minimap.
        // The overlay must remain fully readable and should be the top-most UI.
        if (gameStateManager != null && gameStateManager.isPuzzle()) {
            return;
        }

        // During traversal riddle intro/help overlay, do not draw minimap.
        // (This is driven by GameState, so it remains robust even if the system
        // reference isn't wired for some reason.)
        if (gameStateManager != null && gameStateManager.getState() == GameState.TRAVERSAL_RIDDLE) {
            return;
        }

        // Traversal riddle intro/help overlay should not be obscured.
        if (dungeonThemeTraversalSystem != null && dungeonThemeTraversalSystem.isIntroOverlayOpen()) {
            return;
        }

        // During intro overlays we only want the riddle text.
        // The minimap overlaps the instruction overlay, so hide it in those states.
        if (gameStateManager != null && (gameStateManager.getState() == GameState.REGISTER_ALLOCATION_RIDDLE
            || gameStateManager.getState() == GameState.LIGHTS_OUT_RIDDLE)) {
            return;
        }
        if (gateSystem == null) {
            return;
        }

        Set<String> arenaIds = gateSystem.getRegisteredArenaIds();
        if (arenaIds == null || arenaIds.isEmpty()) {
            // Register-allocation mode doesn't register arenas; draw node graph instead.
            if (registerNodeSockets != null && registerNodeSockets.size() > 0) {
                drawRegisterAllocationTileMiniMap();
            } else if (lightsOutNodeSockets != null && lightsOutNodeSockets.size() > 0) {
                drawLightsOutTileMiniMap();
            }
            return;
        }

        // Only plot generated arenas (arena_N).
        List<String> arenas = new ArrayList<>();
        for (String id : arenaIds) {
            if (id != null && id.startsWith("arena_")) {
                arenas.add(id);
            }
        }
        if (arenas.isEmpty()) {
            // No arenas to plot; fall back to register-allocation graph if present.
            if (registerNodeSockets != null && registerNodeSockets.size() > 0) {
                drawRegisterAllocationTileMiniMap();
            } else if (lightsOutNodeSockets != null && lightsOutNodeSockets.size() > 0) {
                drawLightsOutTileMiniMap();
            }
            return;
        }

        // Map box placement: top-left, below HP/status lines + Enemies.
        // Reserve some vertical space for the minimap legend (drawn above the box).
        float screenH = Gdx.graphics.getHeight();
        float mapW = 160f;
        float mapH = 150f;
        float mapX = HUD_PADDING;
        float legendH = 34f;
        float mapY = screenH - HUD_PADDING - (LINE_HEIGHT * getTopHudLinesCount()) - legendH - mapH - 10f;
        if (mapY < HUD_PADDING) {
            mapY = HUD_PADDING;
        }

        miniMapBoxX = mapX;
        miniMapBoxY = mapY;
        miniMapBoxW = mapW;
        miniMapBoxH = mapH;

        // Compute arena centers and bounds for normalization.
        Map<String, float[]> centers = new HashMap<>();
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (String arenaId : arenas) {
            float[] c = gateSystem.getArenaCenter(arenaId);
            if (c == null) {
                float[] b = gateSystem.getArenaBounds(arenaId);
                if (b != null && b.length >= 4) {
                    c = new float[]{b[0] + b[2] / 2f, b[1] + b[3] / 2f};
                }
            }
            if (c == null) {
                continue;
            }
            centers.put(arenaId, c);
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]);
            maxY = Math.max(maxY, c[1]);
        }

        if (centers.isEmpty()) {
            return;
        }

        float rangeX = Math.max(1f, maxX - minX);
        float rangeY = Math.max(1f, maxY - minY);

        // Compress everything toward the minimap center so corridors look shorter.
        float centerCompress = 0.55f;
        float boxCx = mapX + mapW / 2f;
        float boxCy = mapY + mapH / 2f;

        for (Map.Entry<String, float[]> e : centers.entrySet()) {
            float[] c = e.getValue();
            float px = mapX + ((c[0] - minX) / rangeX) * mapW;
            float py = mapY + ((c[1] - minY) / rangeY) * mapH;
            px = boxCx + (px - boxCx) * centerCompress;
            py = boxCy + (py - boxCy) * centerCompress;
            miniMapNodePositions.put(e.getKey(), new float[]{px, py});
        }

        // Background box.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.45f);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.6f);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);

        // Draw edges (arena adjacency).
        shapeRenderer.setColor(1f, 1f, 1f, 0.30f);
        for (String src : arenas) {
            float[] a = miniMapNodePositions.get(src);
            if (a == null) continue;

            List<String> targets = gateSystem.getOutgoingTargets(src);
            if (targets == null) continue;
            for (String tgt : targets) {
                float[] b = miniMapNodePositions.get(tgt);
                if (b == null) continue;
                shapeRenderer.line(a[0], a[1], b[0], b[1]);
            }
        }
        shapeRenderer.end();

        // Draw nodes.
        String current = gateSystem.getCurrentPlayerArenaId();
        String terminalArenaId = null;
        if (terminals != null && terminals.size() > 0) {
            for (int i = 0; i < terminals.size(); i++) {
                Entity t = terminals.get(i);
                TerminalComponent tc = terminalMapper.get(t);
                if (tc == null || tc.doorId == null) continue;
                // The traversal finale terminal links to the finale puzzle door.
                if (!"finale_door".equals(tc.doorId)) continue;

                PositionComponent tp = positionMapper.get(t);
                CollisionComponent tcCol = collisionMapper.get(t);
                if (tp == null) continue;

                float tx = tp.x + (tcCol != null ? tcCol.width / 2f : GameConstants.TILE_SIZE / 2f);
                float ty = tp.y + (tcCol != null ? tcCol.height / 2f : GameConstants.TILE_SIZE / 2f);

                for (String arenaId : arenas) {
                    float[] bounds = gateSystem.getArenaBounds(arenaId);
                    if (bounds == null || bounds.length < 4) continue;
                    float ax = bounds[0];
                    float ay = bounds[1];
                    float aw = bounds[2];
                    float ah = bounds[3];
                    if (tx >= ax && tx <= ax + aw && ty >= ay && ty <= ay + ah) {
                        terminalArenaId = arenaId;
                        break;
                    }
                }
                break;
            }
        }
        float nodeSize = 14f;
        float half = nodeSize / 2f;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (String arenaId : arenas) {
            float[] p = miniMapNodePositions.get(arenaId);
            if (p == null) continue;
            float px = p[0];
            float py = p[1];

            boolean isCurrent = current != null && current.equals(arenaId);
            if (isCurrent) {
                shapeRenderer.setColor(1f, 1f, 0.3f, 0.95f);
            } else {
                shapeRenderer.setColor(1f, 1f, 1f, 0.85f);
            }

            shapeRenderer.rect(px - half, py - half, nodeSize, nodeSize);
        }
        shapeRenderer.end();

        // Node outlines to keep squares crisp.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (String arenaId : arenas) {
            float[] p = miniMapNodePositions.get(arenaId);
            if (p == null) continue;
            float px = p[0];
            float py = p[1];

            // Default outline
            shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
            shapeRenderer.rect(px - half, py - half, nodeSize, nodeSize);

            // Mark the terminal arena with a cyan outline.
            if (terminalArenaId != null && terminalArenaId.equals(arenaId)) {
                shapeRenderer.setColor(0f, 1f, 1f, 0.95f);
                shapeRenderer.rect(px - half - 2f, py - half - 2f, nodeSize + 4f, nodeSize + 4f);
            }
        }
        shapeRenderer.end();
    }

    private void drawMiniMapLabels() {
        if (miniMapNodePositions.isEmpty()) {
            return;
        }

        // If a puzzle/terminal overlay is open, do not draw minimap labels.
        if (gameStateManager != null && gameStateManager.isPuzzle()) {
            return;
        }

        // During traversal riddle intro/help overlay, do not draw minimap labels.
        if (gameStateManager != null && gameStateManager.getState() == GameState.TRAVERSAL_RIDDLE) {
            return;
        }

        // Traversal riddle intro/help overlay should not be obscured.
        if (dungeonThemeTraversalSystem != null && dungeonThemeTraversalSystem.isIntroOverlayOpen()) {
            return;
        }

        // During intro overlays we only want the riddle text.
        if (gameStateManager != null && (gameStateManager.getState() == GameState.REGISTER_ALLOCATION_RIDDLE
            || gameStateManager.getState() == GameState.LIGHTS_OUT_RIDDLE)) {
            return;
        }

        boolean hasVarNodes = false;
        boolean hasLanternNodes = false;
        boolean hasRegisterNodes = false;
        for (String id : miniMapNodePositions.keySet()) {
            if (id != null && id.startsWith("var_")) {
                hasVarNodes = true;
            }
            if (id != null && id.startsWith("lantern_")) {
                hasLanternNodes = true;
            }
            if (id != null && id.startsWith("node_")) {
                hasRegisterNodes = true;
            }
            if (hasVarNodes && hasLanternNodes && hasRegisterNodes) break;
        }

        // Put instructions above the minimap box (space is reserved by the minimap placement).
        float headerX = miniMapBoxX + 6f;
        float headerY = miniMapBoxY + miniMapBoxH + 30f;

        font.setColor(1f, 1f, 1f, 0.9f);
        font.draw(batch, "MINIMAP", headerX, headerY);
        if (hasVarNodes) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Red dot=you  Yellow=focus  Magenta=neighbors", headerX, headerY - 16f);
        } else if (hasLanternNodes) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Red dot=you  Yellow=focus  Cyan=neighbors", headerX, headerY - 16f);
        } else if (hasRegisterNodes) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, "Red dot=you  Yellow=focus  Magenta=neighbors", headerX, headerY - 16f);
        }

        // Arena numbers: parse arena_# and draw centered.
        font.setColor(0f, 0f, 0f, 0.95f);
        for (Map.Entry<String, float[]> e : miniMapNodePositions.entrySet()) {
            String arenaId = e.getKey();
            float[] p = e.getValue();
            if (arenaId == null || p == null) continue;

            String label;
            if (arenaId.startsWith("arena_")) {
                label = arenaId.substring("arena_".length());
            } else if (arenaId.startsWith("var_")) {
                label = arenaId.substring("var_".length());
            } else if (arenaId.startsWith("lantern_")) {
                label = arenaId.substring("lantern_".length());
            } else if (arenaId.startsWith("node_")) {
                // Register-allocation nodes: show compact numeric labels.
                label = arenaId.substring("node_".length());
            } else {
                label = arenaId;
            }
            glyphLayout.setText(font, label);
            float tx = p[0] - glyphLayout.width / 2f;
            float ty = p[1] + glyphLayout.height / 2f;
            font.draw(batch, label, tx, ty);
        }
    }

    private void drawLightsOutTileMiniMap() {
        if (lightsOutNodeSockets == null || lightsOutNodeSockets.size() == 0) {
            return;
        }

        ensureTileMiniMap();
        if (tileMiniMap == null) {
            // Fallback: draw the same graph-minimap used for register allocation.
            // (We keep this minimal; tile minimap should be available for generated maps.)
            drawRegisterAllocationGraphMiniMapFallback();
            return;
        }

        // Box placement: top-left, below the top-left text block.
        float screenH = Gdx.graphics.getHeight();
        float mapW = 160f;
        float mapH = 150f;
        float mapX = HUD_PADDING;
        float legendH = 34f;
        float mapY = screenH - HUD_PADDING - (LINE_HEIGHT * getTopHudLinesCount()) - legendH - mapH - 10f;
        if (mapY < HUD_PADDING) {
            mapY = HUD_PADDING;
        }

        miniMapBoxX = mapX;
        miniMapBoxY = mapY;
        miniMapBoxW = mapW;
        miniMapBoxH = mapH;

        // Compute player focus lantern (nearest within interaction radius).
        String focusNodeId = null;
        Set<String> focusNeighbors = null;
        boolean playerNearTerminal = false;
        if (players != null && players.size() > 0) {
            Entity player = players.first();
            PositionComponent pp = positionMapper.get(player);
            CollisionComponent pc = collisionMapper.get(player);
            if (pp != null) {
                float pCx = pp.x + (pc != null ? pc.width / 2f : GameConstants.TILE_SIZE / 2f);
                float pCy = pp.y + (pc != null ? pc.height / 2f : GameConstants.TILE_SIZE / 2f);

                float bestDist = Float.MAX_VALUE;
                Entity bestEntity = null;
                for (Entity e : lightsOutNodeSockets) {
                    LightsOutNodeComponent node = lightsOutNodeMapper.get(e);
                    if (node == null || node.winTrigger) continue;
                    if (node.nodeId == null || node.nodeId.isEmpty()) continue;

                    PositionComponent pos = positionMapper.get(e);
                    CollisionComponent col = collisionMapper.get(e);
                    if (pos == null) continue;
                    float cx = pos.x + (col != null ? col.width / 2f : GameConstants.TILE_SIZE / 2f);
                    float cy = pos.y + (col != null ? col.height / 2f : GameConstants.TILE_SIZE / 2f);
                    float dx = cx - pCx;
                    float dy = cy - pCy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < GameConstants.SWITCH_INTERACTION_RADIUS && dist < bestDist) {
                        bestDist = dist;
                        bestEntity = e;
                    }
                }
                if (bestEntity != null) {
                    LightsOutNodeComponent node = lightsOutNodeMapper.get(bestEntity);
                    if (node != null) {
                        focusNodeId = node.nodeId;
                        focusNeighbors = parseNeighbors(node.neighborsCsv);
                    }
                }

                // If the player is near a terminal, fade the minimap so the world terminal isn't hidden.
                if (terminals != null && terminals.size() > 0) {
                    for (Entity terminal : terminals) {
                        PositionComponent terminalPos = positionMapper.get(terminal);
                        CollisionComponent terminalCol = collisionMapper.get(terminal);
                        RenderableComponent terminalRenderable = renderableMapper.get(terminal);
                        if (terminalPos == null) continue;
                        if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) continue;
                        if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) continue;

                        float tCx = terminalPos.x + (terminalCol != null ? terminalCol.width / 2f : GameConstants.TILE_SIZE / 2f);
                        float tCy = terminalPos.y + (terminalCol != null ? terminalCol.height / 2f : GameConstants.TILE_SIZE / 2f);
                        float dx = tCx - pCx;
                        float dy = tCy - pCy;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist < GameConstants.TERMINAL_INTERACTION_RADIUS) {
                            playerNearTerminal = true;
                            break;
                        }
                    }
                }
            }
        }

        // Draw tile minimap background first.
        float cellW = mapW / (float) tileMiniMapW;
        float cellH = mapH / (float) tileMiniMapH;

        float mapBgAlpha = playerNearTerminal ? 0.10f : 0.25f;
        float wallAlpha = playerNearTerminal ? 0.18f : 0.30f;
        float floorAlpha = playerNearTerminal ? 0.08f : 0.14f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, mapBgAlpha);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);

        // Floors/walls.
        for (int y = 0; y < tileMiniMapH; y++) {
            for (int x = 0; x < tileMiniMapW; x++) {
                int v = tileMiniMap[y][x];
                if (v == 0) continue;
                if (v == 2) {
                    shapeRenderer.setColor(1f, 1f, 1f, wallAlpha);
                } else {
                    shapeRenderer.setColor(1f, 1f, 1f, floorAlpha);
                }
                shapeRenderer.rect(mapX + x * cellW, mapY + y * cellH, cellW, cellH);
            }
        }

        // Player marker (red dot).
        if (players != null && players.size() > 0) {
            Entity player = players.first();
            PositionComponent pp = positionMapper.get(player);
            CollisionComponent pc = collisionMapper.get(player);
            if (pp != null) {
                float pCx = pp.x + (pc != null ? pc.width / 2f : GameConstants.TILE_SIZE / 2f);
                float pCy = pp.y + (pc != null ? pc.height / 2f : GameConstants.TILE_SIZE / 2f);
                int tX = (int) Math.floor(pCx / GameConstants.TILE_SIZE);
                int tY = (int) Math.floor(pCy / GameConstants.TILE_SIZE);
                int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
                int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
                if (rX >= 0 && rX < tileMiniMapW && rY >= 0 && rY < tileMiniMapH) {
                    float px = mapX + (rX + 0.5f) * cellW;
                    float py = mapY + (rY + 0.5f) * cellH;
                    float radius = Math.max(2f, Math.min(cellW, cellH) * 0.35f);
                    shapeRenderer.setColor(1f, 0.15f, 0.15f, 0.95f);
                    shapeRenderer.circle(px, py, radius);
                }
            }
        }

        shapeRenderer.end();

        // Border.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, playerNearTerminal ? 0.40f : 0.60f);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);
        shapeRenderer.end();

        // Overlay lantern squares.
        miniMapNodePositions.clear();
        float nodeSize = 11f;
        float half = nodeSize / 2f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : lightsOutNodeSockets) {
            LightsOutNodeComponent node = lightsOutNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            PositionComponent pos = positionMapper.get(e);
            if (pos == null) continue;

            float cx = pos.x + GameConstants.TILE_SIZE / 2f;
            float cy = pos.y + GameConstants.TILE_SIZE / 2f;
            int tX = (int) Math.floor(cx / GameConstants.TILE_SIZE);
            int tY = (int) Math.floor(cy / GameConstants.TILE_SIZE);
            int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
            int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
            if (rX < 0 || rX >= tileMiniMapW || rY < 0 || rY >= tileMiniMapH) continue;

            float px = mapX + (rX + 0.5f) * cellW;
            float py = mapY + (rY + 0.5f) * cellH;
            miniMapNodePositions.put(node.nodeId, new float[]{px, py});

            if (node.on) {
                shapeRenderer.setColor(1f, 0.95f, 0.35f, 0.95f);
            } else {
                shapeRenderer.setColor(0.35f, 0.35f, 0.45f, 0.85f);
            }
            shapeRenderer.rect(px - half, py - half, nodeSize, nodeSize);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : lightsOutNodeSockets) {
            LightsOutNodeComponent node = lightsOutNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            float[] p = miniMapNodePositions.get(node.nodeId);
            if (p == null) continue;

            if (focusNodeId != null && focusNodeId.equals(node.nodeId)) {
                shapeRenderer.setColor(1f, 1f, 0.1f, 1f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
                shapeRenderer.rect(p[0] - half - 1f, p[1] - half - 1f, nodeSize + 2f, nodeSize + 2f);
            } else if (focusNeighbors != null && focusNeighbors.contains(node.nodeId)) {
                shapeRenderer.setColor(0.2f, 0.9f, 1f, 1f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
                shapeRenderer.rect(p[0] - half - 0.75f, p[1] - half - 0.75f, nodeSize + 1.5f, nodeSize + 1.5f);
            } else {
                shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
            }
        }
        shapeRenderer.end();
    }

    private void drawRegisterAllocationTileMiniMap() {
        if (registerNodeSockets == null || registerNodeSockets.size() == 0) {
            return;
        }

        ensureTileMiniMap();
        if (tileMiniMap == null) {
            // Fallback if we couldn't build from tiles.
            drawRegisterAllocationGraphMiniMapFallback();
            return;
        }

        // Box placement: top-left, below the top-left text block.
        // Reserve some vertical space for the minimap legend (drawn above the box).
        float screenH = Gdx.graphics.getHeight();
        float mapW = 160f;
        float mapH = 150f;
        float mapX = HUD_PADDING;
        float legendH = 34f;
        float mapY = screenH - HUD_PADDING - (LINE_HEIGHT * getTopHudLinesCount()) - legendH - mapH - 10f;
        if (mapY < HUD_PADDING) {
            mapY = HUD_PADDING;
        }

        miniMapBoxX = mapX;
        miniMapBoxY = mapY;
        miniMapBoxW = mapW;
        miniMapBoxH = mapH;

        // Compute player focus node (nearest within interaction radius).
        String focusNodeId = null;
        Set<String> focusNeighbors = null;
        boolean playerNearTerminal = false;
        if (players != null && players.size() > 0) {
            Entity player = players.first();
            PositionComponent pp = positionMapper.get(player);
            CollisionComponent pc = collisionMapper.get(player);
            if (pp != null) {
                float pCx = pp.x + (pc != null ? pc.width / 2f : GameConstants.TILE_SIZE / 2f);
                float pCy = pp.y + (pc != null ? pc.height / 2f : GameConstants.TILE_SIZE / 2f);

                float bestDist = Float.MAX_VALUE;
                Entity bestEntity = null;
                for (Entity e : registerNodeSockets) {
                    RegisterNodeComponent node = registerNodeMapper.get(e);
                    if (node == null || node.winTrigger) continue;
                    if (node.nodeId == null || node.nodeId.isEmpty()) continue;

                    PositionComponent pos = positionMapper.get(e);
                    CollisionComponent col = collisionMapper.get(e);
                    if (pos == null) continue;
                    float cx = pos.x + (col != null ? col.width / 2f : GameConstants.TILE_SIZE / 2f);
                    float cy = pos.y + (col != null ? col.height / 2f : GameConstants.TILE_SIZE / 2f);
                    float dx = cx - pCx;
                    float dy = cy - pCy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist < GameConstants.SWITCH_INTERACTION_RADIUS && dist < bestDist) {
                        bestDist = dist;
                        bestEntity = e;
                    }
                }
                if (bestEntity != null) {
                    RegisterNodeComponent node = registerNodeMapper.get(bestEntity);
                    if (node != null) {
                        focusNodeId = node.nodeId;
                        focusNeighbors = parseNeighbors(node.neighborsCsv);
                    }
                }

                // If the player is near a terminal, fade the minimap so the world terminal isn't hidden.
                if (terminals != null && terminals.size() > 0) {
                    for (Entity terminal : terminals) {
                        PositionComponent terminalPos = positionMapper.get(terminal);
                        CollisionComponent terminalCol = collisionMapper.get(terminal);
                        RenderableComponent terminalRenderable = renderableMapper.get(terminal);
                        if (terminalPos == null) continue;
                        if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) continue;
                        if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) continue;

                        float tCx = terminalPos.x + (terminalCol != null ? terminalCol.width / 2f : GameConstants.TILE_SIZE / 2f);
                        float tCy = terminalPos.y + (terminalCol != null ? terminalCol.height / 2f : GameConstants.TILE_SIZE / 2f);
                        float dx = tCx - pCx;
                        float dy = tCy - pCy;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist < GameConstants.TERMINAL_INTERACTION_RADIUS) {
                            playerNearTerminal = true;
                            break;
                        }
                    }
                }
            }
        }

        // Draw tile minimap background first.
        float cellW = mapW / (float) tileMiniMapW;
        float cellH = mapH / (float) tileMiniMapH;

        float mapBgAlpha = playerNearTerminal ? 0.10f : 0.25f;
        float wallAlpha = playerNearTerminal ? 0.18f : 0.30f;
        float floorAlpha = playerNearTerminal ? 0.08f : 0.14f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, mapBgAlpha);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);

        // Floors/walls.
        for (int y = 0; y < tileMiniMapH; y++) {
            for (int x = 0; x < tileMiniMapW; x++) {
                int v = tileMiniMap[y][x];
                if (v == 0) continue;
                if (v == 2) {
                    shapeRenderer.setColor(1f, 1f, 1f, wallAlpha);
                } else {
                    shapeRenderer.setColor(1f, 1f, 1f, floorAlpha);
                }
                shapeRenderer.rect(mapX + x * cellW, mapY + y * cellH, cellW, cellH);
            }
        }

        // Player marker (red dot).
        if (players != null && players.size() > 0) {
            Entity player = players.first();
            PositionComponent pp = positionMapper.get(player);
            CollisionComponent pc = collisionMapper.get(player);
            if (pp != null) {
                float pCx = pp.x + (pc != null ? pc.width / 2f : GameConstants.TILE_SIZE / 2f);
                float pCy = pp.y + (pc != null ? pc.height / 2f : GameConstants.TILE_SIZE / 2f);
                int tX = (int) Math.floor(pCx / GameConstants.TILE_SIZE);
                int tY = (int) Math.floor(pCy / GameConstants.TILE_SIZE);
                int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
                int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
                if (rX >= 0 && rX < tileMiniMapW && rY >= 0 && rY < tileMiniMapH) {
                    float px = mapX + (rX + 0.5f) * cellW;
                    float py = mapY + (rY + 0.5f) * cellH;
                    float radius = Math.max(2f, Math.min(cellW, cellH) * 0.35f);
                    shapeRenderer.setColor(1f, 0.15f, 0.15f, 0.95f);
                    shapeRenderer.circle(px, py, radius);
                }
            }
        }

        shapeRenderer.end();

        // Border.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, playerNearTerminal ? 0.40f : 0.60f);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);
        shapeRenderer.end();

        // Overlay node squares and outlines.
        miniMapNodePositions.clear();
        float nodeSize = 11f;
        float half = nodeSize / 2f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : registerNodeSockets) {
            RegisterNodeComponent node = registerNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            PositionComponent pos = positionMapper.get(e);
            if (pos == null) continue;

            float cx = pos.x + GameConstants.TILE_SIZE / 2f;
            float cy = pos.y + GameConstants.TILE_SIZE / 2f;
            int tX = (int) Math.floor(cx / GameConstants.TILE_SIZE);
            int tY = (int) Math.floor(cy / GameConstants.TILE_SIZE);
            int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
            int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
            if (rX < 0 || rX >= tileMiniMapW || rY < 0 || rY >= tileMiniMapH) continue;

            float px = mapX + (rX + 0.5f) * cellW;
            float py = mapY + (rY + 0.5f) * cellH;
            miniMapNodePositions.put(node.nodeId, new float[]{px, py});

            Color fill = registerFillColor(node.assignedRegisterTokenId);
            shapeRenderer.setColor(fill);
            shapeRenderer.rect(px - half, py - half, nodeSize, nodeSize);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Entity e : registerNodeSockets) {
            RegisterNodeComponent node = registerNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            float[] p = miniMapNodePositions.get(node.nodeId);
            if (p == null) continue;

            if (focusNodeId != null && focusNodeId.equals(node.nodeId)) {
                // Focused node: bright yellow, thicker outline.
                shapeRenderer.setColor(1f, 1f, 0.1f, 1f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
                shapeRenderer.rect(p[0] - half - 1f, p[1] - half - 1f, nodeSize + 2f, nodeSize + 2f);
            } else if (focusNeighbors != null && focusNeighbors.contains(node.nodeId)) {
                // Neighbors: very visible magenta, slightly thicker outline.
                shapeRenderer.setColor(1f, 0.25f, 1f, 1f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
                shapeRenderer.rect(p[0] - half - 0.75f, p[1] - half - 0.75f, nodeSize + 1.5f, nodeSize + 1.5f);
            } else {
                shapeRenderer.setColor(0f, 0f, 0f, 0.65f);
                shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
            }
        }
        shapeRenderer.end();

        // Terminal marker(s): draw on top of minimap so players can always locate it.
        if (terminals != null && terminals.size() > 0) {
            float r = Math.max(2.5f, Math.min(cellW, cellH) * 0.42f);

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            for (Entity terminal : terminals) {
                PositionComponent terminalPos = positionMapper.get(terminal);
                CollisionComponent terminalCol = collisionMapper.get(terminal);
                RenderableComponent terminalRenderable = renderableMapper.get(terminal);
                TerminalComponent terminalComp = terminalMapper.get(terminal);
                if (terminalPos == null) continue;
                if (terminalComp == null || terminalComp.doorId == null || terminalComp.doorId.isEmpty()) continue;
                if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) continue;
                if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) continue;

                float tCx = terminalPos.x + (terminalCol != null ? terminalCol.width / 2f : GameConstants.TILE_SIZE / 2f);
                float tCy = terminalPos.y + (terminalCol != null ? terminalCol.height / 2f : GameConstants.TILE_SIZE / 2f);
                int tX = (int) Math.floor(tCx / GameConstants.TILE_SIZE);
                int tY = (int) Math.floor(tCy / GameConstants.TILE_SIZE);
                int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
                int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
                if (rX < 0 || rX >= tileMiniMapW || rY < 0 || rY >= tileMiniMapH) continue;

                float px = mapX + (rX + 0.5f) * cellW;
                float py = mapY + (rY + 0.5f) * cellH;
                shapeRenderer.setColor(0.2f, 1f, 1f, 0.95f);
                shapeRenderer.circle(px, py, r);
            }
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (Entity terminal : terminals) {
                PositionComponent terminalPos = positionMapper.get(terminal);
                CollisionComponent terminalCol = collisionMapper.get(terminal);
                RenderableComponent terminalRenderable = renderableMapper.get(terminal);
                TerminalComponent terminalComp = terminalMapper.get(terminal);
                if (terminalPos == null) continue;
                if (terminalComp == null || terminalComp.doorId == null || terminalComp.doorId.isEmpty()) continue;
                if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) continue;
                if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) continue;

                float tCx = terminalPos.x + (terminalCol != null ? terminalCol.width / 2f : GameConstants.TILE_SIZE / 2f);
                float tCy = terminalPos.y + (terminalCol != null ? terminalCol.height / 2f : GameConstants.TILE_SIZE / 2f);
                int tX = (int) Math.floor(tCx / GameConstants.TILE_SIZE);
                int tY = (int) Math.floor(tCy / GameConstants.TILE_SIZE);
                int rX = (tX - tileMiniMapMinX) / Math.max(1, tileMiniMapScale);
                int rY = (tY - tileMiniMapMinY) / Math.max(1, tileMiniMapScale);
                if (rX < 0 || rX >= tileMiniMapW || rY < 0 || rY >= tileMiniMapH) continue;

                float px = mapX + (rX + 0.5f) * cellW;
                float py = mapY + (rY + 0.5f) * cellH;
                shapeRenderer.setColor(0f, 0f, 0f, 0.85f);
                shapeRenderer.circle(px, py, r + 1.0f);
            }
            shapeRenderer.end();
        }

    }


    private void ensureTileMiniMap() {
        if (tileMiniMap != null) return;
        if (tiledMap == null) return;

        TiledMapTileLayer floor = findTileLayer("floor", "ground", "floors");
        TiledMapTileLayer walls = findTileLayer("walls", "collision");
        if (floor == null || walls == null) {
            return;
        }
        int w = floor.getWidth();
        int h = floor.getHeight();

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean used = false;
                TiledMapTileLayer.Cell fc = floor.getCell(x, y);
                if (fc != null && fc.getTile() != null) used = true;
                TiledMapTileLayer.Cell wc = walls.getCell(x, y);
                if (wc != null && wc.getTile() != null) used = true;
                if (!used) continue;

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return;
        }

        int usedW = (maxX - minX) + 1;
        int usedH = (maxY - minY) + 1;

        // Downsample so we don't draw thousands of rects.
        int maxCellsX = 80;
        int maxCellsY = 75;
        int scale = 1;
        scale = Math.max(scale, (int) Math.ceil(usedW / (float) maxCellsX));
        scale = Math.max(scale, (int) Math.ceil(usedH / (float) maxCellsY));
        scale = Math.max(1, scale);

        int outW = (int) Math.ceil(usedW / (float) scale);
        int outH = (int) Math.ceil(usedH / (float) scale);

        int[][] out = new int[outH][outW];
        for (int oy = 0; oy < outH; oy++) {
            for (int ox = 0; ox < outW; ox++) {
                int baseX = minX + ox * scale;
                int baseY = minY + oy * scale;

                boolean anyFloor = false;
                boolean anyWall = false;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        int tx = baseX + dx;
                        int ty = baseY + dy;
                        if (tx < 0 || tx >= w || ty < 0 || ty >= h) continue;
                        TiledMapTileLayer.Cell wc = walls.getCell(tx, ty);
                        if (wc != null && wc.getTile() != null) {
                            anyWall = true;
                        }
                        TiledMapTileLayer.Cell fc = floor.getCell(tx, ty);
                        if (fc != null && fc.getTile() != null) {
                            anyFloor = true;
                        }
                    }
                }

                if (anyWall) out[oy][ox] = 2;
                else if (anyFloor) out[oy][ox] = 1;
                else out[oy][ox] = 0;
            }
        }

        tileMiniMap = out;
        tileMiniMapMinX = minX;
        tileMiniMapMinY = minY;
        tileMiniMapScale = scale;
        tileMiniMapW = outW;
        tileMiniMapH = outH;
    }

    private void drawRegisterAllocationGraphMiniMapFallback() {
        // Simple graph-only fallback (used only if we can't build from tile layers).
        float mapX = miniMapBoxX;
        float mapY = miniMapBoxY;
        float mapW = miniMapBoxW;
        float mapH = miniMapBoxH;

        // Gather node centers by id.
        Map<String, float[]> centers = new HashMap<>();
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (Entity e : registerNodeSockets) {
            RegisterNodeComponent node = registerNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;

            PositionComponent pos = positionMapper.get(e);
            CollisionComponent col = collisionMapper.get(e);
            if (pos == null) continue;

            float cx = pos.x + (col != null ? col.width / 2f : GameConstants.TILE_SIZE / 2f);
            float cy = pos.y + (col != null ? col.height / 2f : GameConstants.TILE_SIZE / 2f);
            centers.put(node.nodeId, new float[]{cx, cy});
            minX = Math.min(minX, cx);
            maxX = Math.max(maxX, cx);
            minY = Math.min(minY, cy);
            maxY = Math.max(maxY, cy);
        }

        if (centers.isEmpty()) return;

        float rangeX = Math.max(1f, maxX - minX);
        float rangeY = Math.max(1f, maxY - minY);
        float boxCx = mapX + mapW / 2f;
        float boxCy = mapY + mapH / 2f;
        float compress = 0.78f;

        miniMapNodePositions.clear();
        for (Map.Entry<String, float[]> e : centers.entrySet()) {
            float[] c = e.getValue();
            float px = mapX + ((c[0] - minX) / rangeX) * mapW;
            float py = mapY + ((c[1] - minY) / rangeY) * mapH;
            px = boxCx + (px - boxCx) * compress;
            py = boxCy + (py - boxCy) * compress;
            miniMapNodePositions.put(e.getKey(), new float[]{px, py});
        }

        // Edges.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.25f);
        for (Entity e : registerNodeSockets) {
            RegisterNodeComponent node = registerNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            float[] a = miniMapNodePositions.get(node.nodeId);
            if (a == null) continue;
            Set<String> nbs = parseNeighbors(node.neighborsCsv);
            if (nbs == null) continue;
            for (String nb : nbs) {
                float[] b = miniMapNodePositions.get(nb);
                if (b == null) continue;
                shapeRenderer.line(a[0], a[1], b[0], b[1]);
            }
        }
        shapeRenderer.end();

        // Nodes.
        float nodeSize = 10f;
        float half = nodeSize / 2f;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity e : registerNodeSockets) {
            RegisterNodeComponent node = registerNodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            float[] p = miniMapNodePositions.get(node.nodeId);
            if (p == null) continue;
            Color fill = registerFillColor(node.assignedRegisterTokenId);
            shapeRenderer.setColor(fill);
            shapeRenderer.rect(p[0] - half, p[1] - half, nodeSize, nodeSize);
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.6f);
        shapeRenderer.rect(mapX, mapY, mapW, mapH);
        shapeRenderer.end();
    }

    private TiledMapTileLayer findTileLayer(String... preferredNames) {
        if (tiledMap == null) return null;
        if (preferredNames != null) {
            for (String name : preferredNames) {
                if (name == null) continue;
                Object layer = tiledMap.getLayers().get(name);
                if (layer instanceof TiledMapTileLayer) {
                    return (TiledMapTileLayer) layer;
                }
            }
        }
        // Case-insensitive pass.
        for (com.badlogic.gdx.maps.MapLayer layer : tiledMap.getLayers()) {
            if (!(layer instanceof TiledMapTileLayer)) continue;
            String ln = layer.getName();
            if (ln == null) continue;
            for (String name : preferredNames) {
                if (name == null) continue;
                if (ln.equalsIgnoreCase(name)) {
                    return (TiledMapTileLayer) layer;
                }
            }
        }
        return null;
    }

    private Set<String> parseNeighbors(String csv) {
        java.util.HashSet<String> out = new java.util.HashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        String[] parts = csv.split(",");
        for (String raw : parts) {
            if (raw == null) continue;
            String s = raw.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private Color registerFillColor(String registerTokenId) {
        if (registerTokenId == null) {
            return new Color(1f, 1f, 1f, 0.75f);
        }
        if (RegisterAllocationSystem.REG_R1.equals(registerTokenId)) {
            return new Color(Color.RED);
        }
        if (RegisterAllocationSystem.REG_R2.equals(registerTokenId)) {
            return new Color(Color.GREEN);
        }
        if (RegisterAllocationSystem.REG_R3.equals(registerTokenId)) {
            return new Color(Color.BLUE);
        }
        if (RegisterAllocationSystem.REG_R4.equals(registerTokenId)) {
            return new Color(Color.YELLOW);
        }
        return new Color(0.6f, 0.8f, 1.0f, 0.85f);
    }
    
    private void drawPlayerHealth() {
        if (players.size() > 0) {
            Entity player = players.first();
            HealthComponent health = healthMapper.get(player);
            if (health != null) {
                String healthText = "HP: " + health.currentHealth + " / " + health.maxHealth;
                font.setColor(Color.WHITE);

                float y = Gdx.graphics.getHeight() - HUD_PADDING;
                font.draw(batch, healthText, HUD_PADDING, y);

                // Add current arena readout next to HP (player-facing orientation).
                String arenaId = (gateSystem != null) ? gateSystem.getCurrentPlayerArenaId() : null;
                String arenaText = "Arena: " + ((arenaId != null) ? arenaId : "corridor");
                glyphLayout.setText(font, healthText);
                float x2 = HUD_PADDING + glyphLayout.width + 18;
                font.draw(batch, arenaText, x2, y);

                // Traversal checksum score for the generated-map riddle.
                if (dungeonThemeTraversalSystem != null) {
                    int s = dungeonThemeTraversalSystem.getCurrentChecksum();
                    int target = dungeonThemeTraversalSystem.getTargetChecksum();
                    String sText = "S: " + s + " / " + target;
                    font.draw(batch, sText, HUD_PADDING, y - LINE_HEIGHT);
                }

                // Register allocation objective/progress.
                // Keep it short and explicit (no typing puzzle).
                if (registerAllocationSystem != null && registerAllocationSystem.getNodeCount() > 0) {
                    int lineIndex = 1;
                    if (dungeonThemeTraversalSystem != null) {
                        lineIndex++;
                    }
                    int assigned = registerAllocationSystem.getAssignedCount();
                    int total = registerAllocationSystem.getNodeCount();
                    String status;
                    if (registerAllocationSystem.isSolvedPublic()) {
                        status = "REG: solved";
                    } else {
                        status = "REG: " + assigned + " / " + total + " (E to assign)";
                    }
                    font.draw(batch, status, HUD_PADDING, y - (LINE_HEIGHT * lineIndex));
                }
            }
        } else {
            // Player is dead
            font.setColor(Color.RED);
            font.draw(batch, "HP: 0 / 0", HUD_PADDING, Gdx.graphics.getHeight() - HUD_PADDING);
        }
    }
    
    private void drawEnemyCount() {
        int enemyCount = enemies.size();
        String enemyText = "Enemies: " + enemyCount;
        font.setColor(Color.WHITE);
        int enemiesLineIndexFromTop = 1;
        if (dungeonThemeTraversalSystem != null) {
            enemiesLineIndexFromTop++;
        }
        if (registerAllocationSystem != null && registerAllocationSystem.getNodeCount() > 0) {
            enemiesLineIndexFromTop++;
        }
        font.draw(batch, enemyText, HUD_PADDING, Gdx.graphics.getHeight() - HUD_PADDING - (LINE_HEIGHT * enemiesLineIndexFromTop));
    }
    
    private void drawGameStateOverlay() {
        GameState state = gameStateManager.getState();
        
        if (state == GameState.GAME_OVER) {
            drawCenteredMessage("You died - Press R to restart", Color.RED);
        }
        // Note: LEVEL_CLEAR text removed - exit auto-advance handles level progression
    }
    
    /**
     * Draw contextual interaction prompt when player is near an interactable object.
     * Shows "Press SPACE to interact" near terminals or locked puzzle doors.
     */
    private void drawInteractionPrompt() {
        // Only show during active gameplay
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }
        
        if (players.size() == 0) {
            return;
        }
        
        Entity player = players.first();
        PositionComponent playerPos = positionMapper.get(player);
        CollisionComponent playerCol = collisionMapper.get(player);
        
        if (playerPos == null) {
            return;
        }
        
        // Calculate player center
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);

        String hint = getInteractableHint(playerCenterX, playerCenterY);
        if (hint != null) {
            drawBottomCenteredMessage(hint, Color.YELLOW);
        }
    }
    
    /**
     * Check if the player is near any interactable (terminal or locked puzzle door).
     */
    private String getInteractableHint(float playerCenterX, float playerCenterY) {
        // Register-allocation node sockets (highest priority: this is where players get stuck).
        if (registerNodeSockets != null && registerNodeSockets.size() > 0) {
            for (Entity socket : registerNodeSockets) {
                RegisterNodeComponent node = registerNodeMapper.get(socket);
                if (node == null || node.winTrigger) continue;

                PositionComponent socketPos = positionMapper.get(socket);
                CollisionComponent socketCol = collisionMapper.get(socket);
                if (socketPos == null) continue;

                float socketCenterX = socketPos.x + (socketCol != null ? socketCol.width / 2 : GameConstants.TILE_SIZE / 2);
                float socketCenterY = socketPos.y + (socketCol != null ? socketCol.height / 2 : GameConstants.TILE_SIZE / 2);
                float dx = socketCenterX - playerCenterX;
                float dy = socketCenterY - playerCenterY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < GameConstants.SWITCH_INTERACTION_RADIUS) {
                    return "SPACE/E: Assign Register (neighbors = corridor-connected)";
                }
            }
        }

        // Lights-out lantern sockets.
        if (lightsOutNodeSockets != null && lightsOutNodeSockets.size() > 0) {
            for (Entity socket : lightsOutNodeSockets) {
                LightsOutNodeComponent node = lightsOutNodeMapper.get(socket);
                if (node == null || node.winTrigger) continue;

                PositionComponent socketPos = positionMapper.get(socket);
                CollisionComponent socketCol = collisionMapper.get(socket);
                if (socketPos == null) continue;

                float socketCenterX = socketPos.x + (socketCol != null ? socketCol.width / 2 : GameConstants.TILE_SIZE / 2);
                float socketCenterY = socketPos.y + (socketCol != null ? socketCol.height / 2 : GameConstants.TILE_SIZE / 2);
                float dx = socketCenterX - playerCenterX;
                float dy = socketCenterY - playerCenterY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < GameConstants.SWITCH_INTERACTION_RADIUS) {
                    return "SPACE/E: Tap Lantern";
                }
            }
        }

        // Generic sockets (e.g., Algebra Forge crown socket).
        if (sockets != null && sockets.size() > 0) {
            for (Entity socketEntity : sockets) {
                // Avoid duplicating hints for node-based riddles.
                if (registerNodeMapper.get(socketEntity) != null) continue;
                if (lightsOutNodeMapper.get(socketEntity) != null) continue;

                SocketComponent socketComp = socketMapper.get(socketEntity);
                if (socketComp == null) continue;
                if (socketComp.activated && !socketComp.momentary) continue;

                PositionComponent socketPos = positionMapper.get(socketEntity);
                CollisionComponent socketCol = collisionMapper.get(socketEntity);
                RenderableComponent socketRenderable = renderableMapper.get(socketEntity);
                if (socketPos == null) continue;

                // Hidden sockets should not hint/promote interaction.
                if (socketCol != null && (socketCol.width <= 0f || socketCol.height <= 0f)) {
                    continue;
                }
                if (socketRenderable != null && socketRenderable.color != null && socketRenderable.color.a <= 0.01f) {
                    continue;
                }

                float socketCenterX = socketPos.x + (socketCol != null ? socketCol.width / 2 : GameConstants.TILE_SIZE / 2);
                float socketCenterY = socketPos.y + (socketCol != null ? socketCol.height / 2 : GameConstants.TILE_SIZE / 2);
                float dx = socketCenterX - playerCenterX;
                float dy = socketCenterY - playerCenterY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < GameConstants.SWITCH_INTERACTION_RADIUS) {
                    String required = socketComp.requiresTokenId;
                    if (required != null && !required.isEmpty()) {
                        return "SPACE/E: Insert " + (GlyphNames.isGlyph(required)
                                ? GlyphNames.displayNameWithId(required)
                                : required);
                    }
                    return socketComp.momentary ? "SPACE/E: Refresh Socket" : "SPACE/E: Activate Socket";
                }
            }
        }

        // Check terminals
        for (Entity terminal : terminals) {
            PositionComponent terminalPos = positionMapper.get(terminal);
            CollisionComponent terminalCol = collisionMapper.get(terminal);
            RenderableComponent terminalRenderable = renderableMapper.get(terminal);
            
            if (terminalPos == null) continue;

            // Hidden terminals should not hint/promote interaction.
            if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) {
                continue;
            }
            if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) {
                continue;
            }
            
            float terminalCenterX = terminalPos.x + (terminalCol != null ? terminalCol.width / 2 : GameConstants.TILE_SIZE / 2);
            float terminalCenterY = terminalPos.y + (terminalCol != null ? terminalCol.height / 2 : GameConstants.TILE_SIZE / 2);
            
            float dx = terminalCenterX - playerCenterX;
            float dy = terminalCenterY - playerCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist < GameConstants.TERMINAL_INTERACTION_RADIUS) {
                // Algebra terminals have their own interaction.
                AlgebraTerminalComponent algebraComp = algebraTerminalMapper.get(terminal);
                if (algebraComp != null) {
                    return algebraComp.kind == AlgebraTerminalComponent.Kind.ORACLE
                            ? "SPACE/E: Oracle"
                            : "SPACE/E: Forge";
                }

                // Check if the linked door is locked
                TerminalComponent terminalComp = terminalMapper.get(terminal);
                if (terminalComp != null && terminalComp.doorId != null && !terminalComp.doorId.isEmpty()) {
                    Entity linkedDoor = findPuzzleDoorById(terminalComp.doorId);
                    if (linkedDoor != null) {
                        if (!terminalComp.allowHiddenDoor) {
                            // Don't prompt if the linked door is still hidden (e.g., traversal finale before solved).
                            CollisionComponent doorCol = collisionMapper.get(linkedDoor);
                            RenderableComponent doorRenderable = renderableMapper.get(linkedDoor);
                            if (doorCol != null && (doorCol.width <= 0f || doorCol.height <= 0f)) {
                                continue;
                            }
                            if (doorRenderable != null && doorRenderable.color != null && doorRenderable.color.a <= 0.01f) {
                                continue;
                            }
                        }

                        PuzzleDoorComponent doorComp = puzzleDoorMapper.get(linkedDoor);
                        if (doorComp != null && doorComp.locked && doorComp.hasPuzzle()) {
                            return "SPACE/E: Terminal";
                        }
                    }
                }
            }
        }
        
        // Check locked puzzle doors (direct interaction without terminal)
        for (Entity door : puzzleDoors) {
            PuzzleDoorComponent doorComp = puzzleDoorMapper.get(door);
            if (doorComp == null || !doorComp.locked || !doorComp.hasPuzzle()) {
                continue;
            }
            
            PositionComponent doorPos = positionMapper.get(door);
            CollisionComponent doorCol = collisionMapper.get(door);
            RenderableComponent doorRenderable = renderableMapper.get(door);
            
            if (doorPos == null) continue;

            // Hidden doors should not hint/promote interaction.
            if (doorCol != null && (doorCol.width <= 0f || doorCol.height <= 0f)) {
                continue;
            }
            if (doorRenderable != null && doorRenderable.color != null && doorRenderable.color.a <= 0.01f) {
                continue;
            }
            
            float doorCenterX = doorPos.x + (doorCol != null ? doorCol.width / 2 : GameConstants.TILE_SIZE / 2);
            float doorCenterY = doorPos.y + (doorCol != null ? doorCol.height / 2 : GameConstants.TILE_SIZE / 2);
            
            float dx = doorCenterX - playerCenterX;
            float dy = doorCenterY - playerCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist < GameConstants.PUZZLE_DOOR_INTERACTION_RADIUS) {
                return "SPACE/E: Puzzle Door";
            }
        }

        return null;
    }
    
    /**
     * Find a puzzle door entity by its ID.
     */
    private Entity findPuzzleDoorById(String doorId) {
        if (puzzleDoors == null || doorId == null || doorId.isEmpty()) {
            return null;
        }
        
        for (Entity door : puzzleDoors) {
            PuzzleDoorComponent doorComp = puzzleDoorMapper.get(door);
            if (doorComp != null && doorId.equals(doorComp.id)) {
                return door;
            }
        }
        return null;
    }
    
    private void drawCenteredMessage(String message, Color color) {
        font.setColor(color);
        glyphLayout.setText(font, message);
        float x = (Gdx.graphics.getWidth() - glyphLayout.width) / 2;
        float y = (Gdx.graphics.getHeight() + glyphLayout.height) / 2;
        font.draw(batch, message, x, y);
    }
    
    /**
     * Draw a message centered horizontally near the bottom of the screen.
     */
    private void drawBottomCenteredMessage(String message, Color color) {
        font.setColor(color);
        glyphLayout.setText(font, message);
        float x = (Gdx.graphics.getWidth() - glyphLayout.width) / 2;
        float y = HUD_PADDING + glyphLayout.height + 10; // 10px above bottom padding
        font.draw(batch, message, x, y);
    }
    
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
    }
}
