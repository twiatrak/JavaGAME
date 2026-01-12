package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.CombatComponent;
import com.timonipumba.components.EnemyComponent;
import com.timonipumba.components.EnemyStatsComponent;
import com.timonipumba.components.HealthComponent;
import com.timonipumba.components.HitFlashComponent;
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.components.PositionComponent;
import com.timonipumba.components.CollisionComponent;
import com.timonipumba.components.PuzzleDoorComponent;
import com.timonipumba.components.RenderableComponent;
import com.timonipumba.components.VelocityComponent;
import com.timonipumba.ui.OverlayUi;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * DungeonThemeTraversalSystem (generated TMX roguelike only, wired from TiledMapGame).
 *
 * One map-wide traversal riddle is generated at the start of the dungeon.
 * The player's traversal produces a sequence of bits (0/1) via the lane labels.
 * We combine those bits with the count of unique arenas visited into a checksum-lock.
 *
 * UX:
 * - Instructions open when the player presses M.
 * - No per-arena prompts; the constraint is global and validated on every arena transition.
 */
public class DungeonThemeTraversalSystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 28;

    private static final Color BOX_COLOR = new Color(0.08f, 0.08f, 0.12f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.7f, 0.9f, 1.0f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);
    private static final Color WARNING_COLOR = new Color(1.0f, 0.4f, 0.4f, 1.0f);

    private final GameStateManager gameStateManager;
    private final GateSystem gateSystem;

    private final OverlayUi ui;

    private ImmutableArray<Entity> players;

    private final ComponentMapper<PlayerComponent> playerMapper = ComponentMapper.getFor(PlayerComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);

    private final TraversalLock lock;
    private int checksum;

    private boolean introOpen = false;

    private GameState stateBeforeIntro = GameState.PLAYING;

    private String lastNonNullArenaId;
    private boolean finaleContentRevealed = false;
    private String finaleArenaId = null;

    // Prevent word farming by backtracking: only count the first time an arena is entered.
    private final Set<String> visitedArenas = new HashSet<>();
    private boolean traversalSolved = false;

    public DungeonThemeTraversalSystem(GameStateManager gameStateManager, GateSystem gateSystem, long seed) {
        super(90); // overlay-ish
        this.gameStateManager = gameStateManager;
        this.gateSystem = gateSystem;

        Random r = new Random(seed);
        this.lock = TraversalLock.pick(r);
        this.checksum = lock.initial;

        this.ui = new OverlayUi(1.2f, 2.0f);

    }

    public int getCurrentChecksum() {
        return checksum;
    }

    public int getTargetChecksum() {
        return lock.target;
    }

    public boolean isIntroOverlayOpen() {
        return introOpen;
    }

    private void openIntroOverlay() {
        introOpen = true;
        // Pause player movement while the overlay is visible.
        stateBeforeIntro = gameStateManager.getState();
        gameStateManager.setState(GameState.TRAVERSAL_RIDDLE);
    }

    private void closeIntroOverlay() {
        introOpen = false;
        gameStateManager.setState(stateBeforeIntro);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, HealthComponent.class, PositionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null) return;

        // Allow running while the intro overlay is open (TRAVERSAL_RIDDLE state).
        if (!gameStateManager.isActiveGameplay() && !gameStateManager.isTraversalRiddle()) {
            return;
        }
        if (gateSystem == null) {
            return;
        }

        String currentArena = gateSystem.getCurrentPlayerArenaId();

        if (introOpen) {
            renderIntroOverlay();
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
                closeIntroOverlay();
            }
            return;
        }

        // Instructions are always available via M.
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            openIntroOverlay();
            return;
        }

        // In the end-goal design, we avoid mid-run hard-fails.
        // If you want mid-run punishment, re-enable executioner spawning on dead prefixes.

        // Determine finale arena ID lazily (max arena_N present in the loaded TMX).
        if (finaleArenaId == null) {
            finaleArenaId = computeFinaleArenaId();
        }

        // Track arena transitions. Corridors can yield null arena IDs; ignore those.
        if (currentArena == null) {
            return;
        }

        if (lastNonNullArenaId == null) {
            lastNonNullArenaId = currentArena;
            // Count the starting arena as visited so the lock can refer to a player-observable
            // quantity ("how many distinct arenas have I seen so far?").
            if (currentArena != null) {
                visitedArenas.add(currentArena);
            }
            return;
        }

        if (!currentArena.equals(lastNonNullArenaId)) {
            onArenaTransition(lastNonNullArenaId, currentArena);
            lastNonNullArenaId = currentArena;
        }

        // End-goal: reveal finale content only when the player reaches the final arena
        // AND the traversal lock condition is satisfied.
        if (!finaleContentRevealed && finaleArenaId != null && finaleArenaId.equals(currentArena)) {
            if (isTraversalSolved()) {
                traversalSolved = true;
                revealFinaleContentIfPresent();
                finaleContentRevealed = true;
            }
        }
    }

    private void onArenaTransition(String fromArenaId, String toArenaId) {
        // Ignore transitions into already-visited arenas.
        // This prevents bouncing between two arenas to manufacture any string.
        if (toArenaId != null && visitedArenas.contains(toArenaId)) {
            return;
        }

        if (toArenaId != null) {
            visitedArenas.add(toArenaId);
        }

        // Step counter for the lock: number of UNIQUE arenas seen so far (including the start).
        int step = visitedArenas.size();

        // Prefer the actual gate/lane symbol chosen by the player (when available).
        String gateSymbol = gateSystem.consumeLastTraversalSymbol(fromArenaId, toArenaId);
        int bit;
        if (gateSymbol != null && !gateSymbol.isEmpty()) {
            char c = Character.toUpperCase(gateSymbol.charAt(0));
            bit = (c == 'V') ? 1 : 0;
        } else {
            bit = symbolForTransition(fromArenaId, toArenaId);
        }

        checksum = Math.floorMod((checksum * lock.multiplier) + lock.salt + bit + step, lock.modulus);
    }

    /**
     * Alphabet mapping for the whole dungeon.
     * Symbol 0 = horizontal move (H), 1 = vertical move (V).
     * Uses registered arena centers; falls back to arena index parity if unknown.
     */
    private int symbolForTransition(String fromArenaId, String toArenaId) {
        float[] a = gateSystem.getArenaCenter(fromArenaId);
        float[] b = gateSystem.getArenaCenter(toArenaId);
        if (a != null && b != null) {
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            return (Math.abs(dx) >= Math.abs(dy)) ? 0 : 1;
        }

        int idx = parseArenaIndex(toArenaId);
        return (idx % 2 == 0) ? 0 : 1;
    }

    private static final class TraversalLock {
        final String name;
        final String description;
        final int modulus;
        final int multiplier;
        final int salt;
        final int initial;
        final int target;
        final int minUniqueArenas;

        private TraversalLock(
            String name,
            String description,
            int modulus,
            int multiplier,
            int salt,
            int initial,
            int target,
            int minUniqueArenas
        ) {
            this.name = name;
            this.description = description;
            this.modulus = modulus;
            this.multiplier = multiplier;
            this.salt = salt;
            this.initial = initial;
            this.target = target;
            this.minUniqueArenas = minUniqueArenas;
        }

        static TraversalLock pick(Random r) {
            // Deterministic but nontrivial.
            // Bit comes from corridor labels (0/1). Only first-time arena entry counts.
            // Let t be the count of unique arenas visited so far (including the start).
            // Update: s = (s*A + C + bit + t) mod M
            // Solve: reach the last arena with s == target after visiting enough unique arenas.
            int m = 97;
            int a = 31;
            int c = 7;
            int init = 13;
            int target = Math.floorMod(r.nextInt(), m);
            int minUnique = 7;

            return new TraversalLock(
                "Checksum Lock",
                "Corridors are labeled with bits 0 and 1.\n" +
                    "Only the FIRST time you enter a new arena counts (re-entries do nothing).\n\n" +
                    "Maintain a state s (start s=" + init + ").\n" +
                    "Each time you enter a new arena for the first time:\n" +
                    "  bit = 0 or 1 (the corridor label you used)\n" +
                    "  t   = number of UNIQUE arenas you have seen so far (including the start)\n" +
                    "  s   = (s*" + a + " + " + c + " + bit + t) mod " + m + "\n\n" +
                    "Example first move: if you enter your 2nd unique arena using bit=1,\n" +
                    "  s = (s*" + a + " + " + c + " + 1 + 2) mod " + m + "\n\n" +
                    "Unlock condition (in the LAST arena):\n" +
                    "  s = " + target + "\n" +
                    "and you visited at least " + minUnique + " unique arenas.",
                m, a, c, init, target, minUnique
            );
        }
    }

    private boolean isTraversalSolved() {
        return visitedArenas.size() >= lock.minUniqueArenas && checksum == lock.target;
    }

    private String computeFinaleArenaId() {
        if (gateSystem == null) return null;
        int max = -1;
        for (String id : gateSystem.getRegisteredArenaIds()) {
            int idx = parseArenaIndex(id);
            if (idx > max) max = idx;
        }
        return (max >= 0) ? ("arena_" + max) : null;
    }

    private void revealFinaleContentIfPresent() {
        Engine engine = getEngine();
        if (engine == null) return;

        ImmutableArray<Entity> doors = engine.getEntitiesFor(
            Family.all(PuzzleDoorComponent.class, PositionComponent.class).get()
        );

        ImmutableArray<Entity> terminals = engine.getEntitiesFor(
            Family.all(com.timonipumba.components.TerminalComponent.class, PositionComponent.class).get()
        );


        ComponentMapper<PuzzleDoorComponent> puzzleDoorMapper = ComponentMapper.getFor(PuzzleDoorComponent.class);
        ComponentMapper<CollisionComponent> collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        ComponentMapper<RenderableComponent> renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
        ComponentMapper<com.timonipumba.components.TerminalComponent> terminalMapper = ComponentMapper.getFor(com.timonipumba.components.TerminalComponent.class);
        ComponentMapper<PositionComponent> positionMapper = ComponentMapper.getFor(PositionComponent.class);

        Entity finaleDoorEntity = null;
        PuzzleDoorComponent finaleDoor = null;
        PositionComponent finaleDoorPos = null;

        for (int i = 0; i < doors.size(); i++) {
            Entity e = doors.get(i);
            PuzzleDoorComponent door = puzzleDoorMapper.get(e);
            if (door == null || !door.isFinale()) continue;

            // Reveal the finale door but keep it locked; the terminal triggers the puzzle.
            CollisionComponent col = collisionMapper.get(e);
            if (col != null) {
                if (!door.dimensionsStored) {
                    door.originalWidth = GameConstants.TILE_SIZE;
                    door.originalHeight = GameConstants.TILE_SIZE;
                    door.dimensionsStored = true;
                }
                col.width = GameConstants.TILE_SIZE;
                col.height = GameConstants.TILE_SIZE;
            }

            RenderableComponent renderable = renderableMapper.get(e);
            if (renderable != null && renderable.color != null) {
                renderable.color = new Color(renderable.color.r, renderable.color.g, renderable.color.b, 1f);
            }

            finaleDoorEntity = e;
            finaleDoor = door;
            finaleDoorPos = positionMapper.get(e);
        }

        // Reveal terminal(s) linked to the finale door.
        if (finaleDoor != null) {
            for (int i = 0; i < terminals.size(); i++) {
                Entity t = terminals.get(i);
                com.timonipumba.components.TerminalComponent term = terminalMapper.get(t);
                if (term == null) continue;
                if (!finaleDoor.id.equals(term.doorId)) continue;

                CollisionComponent col = collisionMapper.get(t);
                if (col != null) {
                    col.width = GameConstants.TILE_SIZE;
                    col.height = GameConstants.TILE_SIZE;
                }

                RenderableComponent renderable = renderableMapper.get(t);
                if (renderable != null && renderable.color != null) {
                    renderable.color = new Color(renderable.color.r, renderable.color.g, renderable.color.b, 1f);
                }
            }
        }
    }

    // Note: executioner spawning removed from this mode; traversal failure simply means the finale stays hidden.

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

    private void renderIntroOverlay() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        int boxW = Math.min(760, screenW - PADDING * 2);
        int boxH = Math.min(520, screenH - PADDING * 2);
        int x = (screenW - boxW) / 2;
        int y = (screenH - boxH) / 2;

        ui.drawPanel(x, y, boxW, boxH, BOX_COLOR, BORDER_COLOR);

        ui.batch.begin();

        float textX = x + 25;
        float textY = y + boxH - 25;

        // Reserve space for the bottom hints so wrapped description text cannot overlap.
        float bottomY = y + 30;
        float reservedBottom = (LINE_HEIGHT * 3) + 20;
        float descMinY = bottomY + reservedBottom;

        ui.titleFont.setColor(TITLE_COLOR);
        ui.titleFont.draw(ui.batch, "Traversal Riddle", textX, textY);
        textY -= 45;

        ui.font.setColor(TITLE_COLOR);
        ui.font.draw(ui.batch, lock.name, textX, textY);
        textY -= 30;

        ui.font.setColor(TEXT_COLOR);

        // Fit description into the available vertical space.
        float maxDescHeight = Math.max(60f, textY - descMinY);
        String desc = lock.description;
        float oldScaleX = ui.font.getData().scaleX;
        float oldScaleY = ui.font.getData().scaleY;

        float scale = oldScaleX;
        ui.layout.setText(ui.font, desc, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
        while (ui.layout.height > maxDescHeight && scale > 0.9f) {
            scale -= 0.1f;
            ui.font.getData().setScale(scale);
            ui.layout.setText(ui.font, desc, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
        }

        if (ui.layout.height > maxDescHeight) {
            String working = (desc != null) ? desc : "";
            // Truncate progressively until it fits.
            for (int guard = 0; guard < 80 && working.length() > 60; guard++) {
                working = working.substring(0, Math.max(0, working.length() - 20));
                int cut = Math.max(working.lastIndexOf('\n'), working.lastIndexOf(' '));
                if (cut > 0) {
                    working = working.substring(0, cut);
                }
                working = working.trim() + "\n...";
                ui.layout.setText(ui.font, working, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
                if (ui.layout.height <= maxDescHeight) {
                    desc = working;
                    break;
                }
            }
        }

        wrapAndDraw(ui.font, desc, textX, textY, boxW - 50);

        // Restore font scale (we might have shrunk it to fit).
        ui.font.getData().setScale(oldScaleX, oldScaleY);

        ui.font.setColor(HINT_COLOR);
        ui.font.draw(ui.batch, "Press ENTER/SPACE to begin.", textX, bottomY + (LINE_HEIGHT * 2));
        ui.font.draw(ui.batch, "Press M to view this again.", textX, bottomY + LINE_HEIGHT);
        ui.font.setColor(WARNING_COLOR);
        ui.font.draw(ui.batch, "You only get one shot: reach the last arena with the right checksum.", textX, bottomY);

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
