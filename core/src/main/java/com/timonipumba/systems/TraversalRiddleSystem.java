package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.timonipumba.GameStateManager;
import com.timonipumba.GameConstants;
import com.timonipumba.ui.OverlayUi;
import com.timonipumba.components.CombatComponent;
import com.timonipumba.components.EnemyComponent;
import com.timonipumba.components.EnemyStatsComponent;
import com.timonipumba.components.GateComponent;
import com.timonipumba.components.HealthComponent;
import com.timonipumba.components.HitFlashComponent;
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.components.PositionComponent;
import com.timonipumba.components.RenderableComponent;
import com.timonipumba.components.VelocityComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * TraversalRiddleSystem (legacy / currently unused).
 *
 * This system implements a per-arena riddle/prompt. It was an earlier iteration of traversal
 * puzzles; the current design uses a map-wide DFA theme (see DungeonThemeTraversalSystem).
 */
public class TraversalRiddleSystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 30;

    private static final Color BOX_COLOR = new Color(0.08f, 0.08f, 0.12f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.7f, 0.9f, 1.0f, 1.0f);

    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);
    private static final Color WARNING_COLOR = new Color(1.0f, 0.4f, 0.4f, 1.0f);

    private final GameStateManager gameStateManager;
    private final GateSystem gateSystem;

    private final OverlayUi ui;

    private final ComponentMapper<GateComponent> gateMapper = ComponentMapper.getFor(GateComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<EnemyComponent> enemyMapper = ComponentMapper.getFor(EnemyComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final ComponentMapper<PlayerComponent> playerMapper = ComponentMapper.getFor(PlayerComponent.class);

    private ImmutableArray<Entity> gates;
    private ImmutableArray<Entity> enemies;
    private ImmutableArray<Entity> players;

    private final Random globalRandom;

    private enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    private static final class GateOption {
        final Direction dir;
        final String targetArenaId;
        final float cx;
        final float cy;

        GateOption(Direction dir, String targetArenaId, float cx, float cy) {
            this.dir = dir;
            this.targetArenaId = targetArenaId;
            this.cx = cx;
            this.cy = cy;
        }
    }

    // Active riddle
    private String activeArenaId;
    private Direction correctDirection;
    private Map<Direction, GateOption> optionsByDirection = new HashMap<>();
    private String title;
    private String prompt;
    private final Map<Direction, String> perDirectionInfo = new HashMap<>();

    private String lastKnownArenaId;
    private final Set<String> arenasWithRiddle = new HashSet<>();

    public TraversalRiddleSystem(GameStateManager gameStateManager, GateSystem gateSystem, long seed) {
        super(90); // overlay-ish
        this.gameStateManager = gameStateManager;
        this.gateSystem = gateSystem;
        this.globalRandom = new Random(seed);

        this.ui = new OverlayUi(1.2f, 2.0f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        gates = engine.getEntitiesFor(Family.all(GateComponent.class).get());
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class).get());
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, HealthComponent.class, PositionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        // Only run during gameplay (PLAYING or LEVEL_CLEAR) so we don't fight with other overlays.
        if (gameStateManager == null || !gameStateManager.isActiveGameplay()) {
            return;
        }
        if (gateSystem == null || gates == null) {
            return;
        }

        String currentArena = gateSystem.getCurrentPlayerArenaId();

        // Detect arena transitions (used to evaluate the player's gate choice).
        if (lastKnownArenaId != null && activeArenaId != null && lastKnownArenaId.equals(activeArenaId)) {
            if (currentArena != null && !currentArena.equals(activeArenaId)) {
                evaluateTraversalChoice(currentArena);
            }
        }
        lastKnownArenaId = currentArena;

        if (currentArena == null) {
            return;
        }

        // If we have an active riddle for this arena, keep rendering it.
        if (activeArenaId != null && activeArenaId.equals(currentArena)) {
            renderOverlay();
            return;
        }

        // If we already generated a riddle for this arena at least once, don't spam on re-entry.
        if (arenasWithRiddle.contains(currentArena)) {
            return;
        }

        // Only trigger when the arena is cleared.
        if (countLivingEnemiesInArena(currentArena) > 0) {
            return;
        }

        Map<Direction, GateOption> options = computeDirectionalOptions(currentArena);
        if (options.size() < 2) {
            return;
        }

        startRiddleForArena(currentArena, options);
    }

    private int countLivingEnemiesInArena(String arenaId) {
        if (arenaId == null || enemies == null) return 0;
        int count = 0;
        for (int i = 0; i < enemies.size(); i++) {
            Entity e = enemies.get(i);
            EnemyComponent ec = enemyMapper.get(e);
            if (ec == null || ec.arenaId == null) continue;
            if (!arenaId.equals(ec.arenaId)) continue;

            HealthComponent h = healthMapper.get(e);
            if (h != null && h.isDead()) continue;
            count++;
        }
        return count;
    }


    private Map<String, Integer> buildOutDegreeMap() {
        Map<String, Set<String>> neighbors = new HashMap<>();
        if (gates == null) return Collections.emptyMap();

        for (int i = 0; i < gates.size(); i++) {
            GateComponent gc = gateMapper.get(gates.get(i));
            if (gc == null) continue;
            if (gc.sourceArenaId == null || gc.targetArenaId == null) continue;
            neighbors.computeIfAbsent(gc.sourceArenaId, k -> new HashSet<>()).add(gc.targetArenaId);
        }

        Map<String, Integer> outDeg = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : neighbors.entrySet()) {
            outDeg.put(e.getKey(), e.getValue().size());
        }
        return outDeg;
    }

    private Map<Direction, GateOption> computeDirectionalOptions(String arenaId) {
        if (arenaId == null) return Collections.emptyMap();
        float[] bounds = gateSystem.getArenaBounds(arenaId);
        if (bounds == null) return Collections.emptyMap();
        float ax = bounds[0];
        float ay = bounds[1];
        float aw = bounds[2];
        float ah = bounds[3];

        Map<Direction, GateOption> options = new HashMap<>();
        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gc = gateMapper.get(gateEntity);
            if (gc == null) continue;
            if (!arenaId.equals(gc.sourceArenaId)) continue;
            if (gc.targetArenaId == null) continue;

            PositionComponent gp = posMapper.get(gateEntity);
            if (gp == null) continue;

            // Use gate entity position as its center-ish reference.
            float cx = gp.x;
            float cy = gp.y;

            Direction dir = inferDirection(cx, cy, ax, ay, aw, ah);
            if (dir == null) continue;

            // Keep the first per-direction (generator should only produce one).
            options.putIfAbsent(dir, new GateOption(dir, gc.targetArenaId, cx, cy));
        }
        return options;
    }

    private Direction inferDirection(float x, float y, float ax, float ay, float aw, float ah) {
        float left = Math.abs(x - ax);
        float right = Math.abs((ax + aw) - x);
        float bottom = Math.abs(y - ay);
        float top = Math.abs((ay + ah) - y);

        float min = Math.min(Math.min(left, right), Math.min(bottom, top));
        if (min == top) return Direction.NORTH;
        if (min == bottom) return Direction.SOUTH;
        if (min == right) return Direction.EAST;
        return Direction.WEST;
    }

    private void startRiddleForArena(String arenaId, Map<Direction, GateOption> options) {
        this.activeArenaId = arenaId;
        this.optionsByDirection = options;
        this.perDirectionInfo.clear();

        // Pick a riddle type that is actually a puzzle (no more random survival probabilities).
        int pick = Math.abs((arenaId.hashCode() ^ globalRandom.nextInt()) % 3);
        if (pick == 0) {
            buildPrimeTargetRiddle();
        } else if (pick == 1) {
            buildGraphOutDegreeRiddle();
        } else {
            buildMod3AutomatonRiddle();
        }

        arenasWithRiddle.add(arenaId);
    }

    private void buildPrimeTargetRiddle() {
        title = "Traversal Riddle: Number Theory";
        prompt = "Choose the gate whose TARGET arena index is PRIME. Wrong gate summons an executioner.";

        List<Direction> dirs = new ArrayList<>(optionsByDirection.keySet());
        Collections.sort(dirs, (a, b) -> a.name().compareTo(b.name()));

        Direction primeDir = null;
        int primeCount = 0;
        for (Direction d : dirs) {
            GateOption opt = optionsByDirection.get(d);
            int idx = parseArenaIndex(opt.targetArenaId);
            perDirectionInfo.put(d, "target=" + idx);
            if (isPrime(idx)) {
                primeCount++;
                primeDir = d;
            }
        }

        // If not uniquely solvable, fall back to mod3 automaton.
        if (primeCount != 1) {
            buildMod3AutomatonRiddle();
            return;
        }
        correctDirection = primeDir;
    }

    private void buildGraphOutDegreeRiddle() {
        title = "Traversal Riddle: Graph";
        prompt = "Choose the gate that leads to the node with MIN out-degree (fewest outgoing edges).";

        Map<String, Integer> outDeg = buildOutDegreeMap();
        Direction bestDir = null;
        int best = Integer.MAX_VALUE;
        int bestCount = 0;

        for (Map.Entry<Direction, GateOption> e : optionsByDirection.entrySet()) {
            int deg = outDeg.getOrDefault(e.getValue().targetArenaId, 0);
            perDirectionInfo.put(e.getKey(), "outDeg=" + deg);
            if (deg < best) {
                best = deg;
                bestDir = e.getKey();
                bestCount = 1;
            } else if (deg == best) {
                bestCount++;
            }
        }

        if (bestDir == null || bestCount != 1) {
            // Ensure unique solvability.
            buildMod3AutomatonRiddle();
            return;
        }
        correctDirection = bestDir;
    }

    private void buildMod3AutomatonRiddle() {
        title = "Traversal Riddle: Automata";

        int curIdx = parseArenaIndex(activeArenaId);
        int curState = mod(curIdx, 3);
        prompt = "Automaton: state = index(current) mod 3 = " + curState + ". Choose gate so (state + index(target)) mod 3 == 0.";

        Direction bestDir = null;
        int bestCount = 0;
        for (Map.Entry<Direction, GateOption> e : optionsByDirection.entrySet()) {
            int tidx = parseArenaIndex(e.getValue().targetArenaId);
            int next = mod(curState + tidx, 3);
            perDirectionInfo.put(e.getKey(), "target=" + tidx + " => next=" + next);
            if (next == 0) {
                bestDir = e.getKey();
                bestCount++;
            }
        }

        // If it's not uniquely solvable, pick a deterministic direction to keep the game moving.
        if (bestDir == null || bestCount != 1) {
            List<Direction> dirs = new ArrayList<>(optionsByDirection.keySet());
            Collections.sort(dirs, (a, b) -> a.name().compareTo(b.name()));
            correctDirection = dirs.get(0);
            prompt = "Automaton (fallback): choose the highlighted direction.";
        } else {
            correctDirection = bestDir;
        }
    }

    private void evaluateTraversalChoice(String enteredArenaId) {
        if (activeArenaId == null || correctDirection == null) return;

        GateOption correctOpt = optionsByDirection.get(correctDirection);
        if (correctOpt == null) {
            clearActiveRiddle();
            return;
        }

        boolean enteredThroughKnownGate = false;
        for (GateOption opt : optionsByDirection.values()) {
            if (enteredArenaId.equals(opt.targetArenaId)) {
                enteredThroughKnownGate = true;
                break;
            }
        }

        // Only punish if the arena we entered was one of the options.
        if (!enteredThroughKnownGate) {
            clearActiveRiddle();
            return;
        }

        if (enteredArenaId.equals(correctOpt.targetArenaId)) {
            // Correct.
            clearActiveRiddle();
            return;
        }

        // Wrong: spawn executioner in the entered arena.
        spawnExecutioner(enteredArenaId);
        clearActiveRiddle();
    }

    private void clearActiveRiddle() {
        activeArenaId = null;
        correctDirection = null;
        optionsByDirection = new HashMap<>();
        perDirectionInfo.clear();
        title = null;
        prompt = null;
    }

    private void spawnExecutioner(String arenaId) {
        Engine engine = getEngine();
        if (engine == null) return;

        PositionComponent playerPos = null;
        for (int i = 0; players != null && i < players.size(); i++) {
            Entity p = players.get(i);
            if (playerMapper.get(p) == null) continue;
            playerPos = posMapper.get(p);
            if (playerPos != null) break;
        }
        if (playerPos == null) return;

        float x = playerPos.x + GameConstants.TILE_SIZE;
        float y = playerPos.y;

        EnemyStatsComponent stats = new EnemyStatsComponent(EnemyStatsComponent.EnemyType.BRUTE);
        // Make it effectively unbeatable and lethal.
        stats.maxHealth = 1000;
        stats.damage = 100; // player max is 100
        stats.speed = 240f;
        stats.visionRange = 9999f;
        stats.aggroRadius = 9999f;
        stats.attackCooldown = 0.15f;
        stats.isRanged = false;

        Entity exec = engine.createEntity();
        exec.add(new PositionComponent(x, y));
        exec.add(new VelocityComponent());
        exec.add(new com.timonipumba.components.CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        exec.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, Color.BLACK));

        EnemyComponent enemyComp = new EnemyComponent();
        enemyComp.arenaId = arenaId;
        enemyComp.isAggroed = true;
        exec.add(enemyComp);
        exec.add(stats);
        exec.add(new HealthComponent(stats.maxHealth));
        exec.add(new CombatComponent(stats.damage, stats.attackCooldown));
        exec.add(new HitFlashComponent(Color.WHITE, GameConstants.HIT_FLASH_DURATION_ENEMY));

        engine.addEntity(exec);
    }

    private int parseArenaIndex(String arenaId) {
        if (arenaId == null) return 0;
        int idx = arenaId.lastIndexOf('_');
        if (idx < 0 || idx == arenaId.length() - 1) return 0;
        try {
            return Integer.parseInt(arenaId.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    private int mod(int v, int m) {
        int r = v % m;
        return r < 0 ? r + m : r;
    }

    private void renderOverlay() {
        if (activeArenaId == null) return;

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        int boxW = Math.min(700, screenW - PADDING * 2);
        int boxH = Math.min(380, screenH - PADDING * 2);
        int x = (screenW - boxW) / 2;
        int y = (screenH - boxH) / 2;

        ui.drawPanel(x, y, boxW, boxH, BOX_COLOR, BORDER_COLOR);

        ui.batch.begin();

        float textX = x + 25;
        float textY = y + boxH - 25;

        // Title
        ui.titleFont.setColor(TITLE_COLOR);
        ui.titleFont.draw(ui.batch, title != null ? title : "Traversal Riddle", textX, textY);
        textY -= 45;

        // Prompt
        ui.font.setColor(TEXT_COLOR);
        wrapAndDraw(ui.font, prompt != null ? prompt : "Choose a gate.", textX, textY, boxW - 50);
        textY -= 60;

        // Options: directions (player chooses by walking through that gate)
        List<Direction> dirs = new ArrayList<>(optionsByDirection.keySet());
        Collections.sort(dirs, (a, b) -> a.name().compareTo(b.name()));
        for (Direction d : dirs) {
            GateOption opt = optionsByDirection.get(d);
            if (opt == null) continue;
            String info = perDirectionInfo.getOrDefault(d, "");
            String line = d.name() + " -> " + (info.isEmpty() ? opt.targetArenaId : info);
            if (d == correctDirection) {
                // Don't reveal answer; just visually emphasize that a correct answer exists.
                ui.font.setColor(TEXT_COLOR);
            } else {
                ui.font.setColor(TEXT_COLOR);
            }
            ui.font.draw(ui.batch, line, textX, textY);
            textY -= LINE_HEIGHT;
        }

        // Hint
        textY -= 10;
        ui.font.setColor(HINT_COLOR);
        ui.font.draw(ui.batch, "Answer by walking through a gate.", textX, textY);

        textY -= LINE_HEIGHT;
        ui.font.setColor(WARNING_COLOR);
        ui.font.draw(ui.batch, "Wrong gate summons an executioner.", textX, textY);

        ui.batch.end();
    }

    private void wrapAndDraw(BitmapFont f, String text, float x, float y, float maxWidth) {
        if (text == null) return;
        ui.layout.setText(f, text, f.getColor(), maxWidth, com.badlogic.gdx.utils.Align.left, true);
        f.draw(ui.batch, ui.layout, x, y);
    }

    public void dispose() {
        ui.dispose();
    }
}
