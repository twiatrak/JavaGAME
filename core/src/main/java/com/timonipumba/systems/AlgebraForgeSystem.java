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
import com.timonipumba.GameConstants;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.AlgebraTerminalComponent;
import com.timonipumba.components.CollisionComponent;
import com.timonipumba.components.EnemyComponent;
import com.timonipumba.components.HealthComponent;
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.components.PlayerInventoryComponent;
import com.timonipumba.components.PositionComponent;
import com.timonipumba.ui.OverlayUi;
import com.timonipumba.util.GlyphNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Algebra Forge riddle system.
 *
 * Provides a minimal two-token selection overlay for "forge" and "oracle" terminals.
 * The underlying operation is a hidden, permuted D4 group law (non-commutative).
 * Players discover structure by querying the oracle and crafting with the forge.
 */
public class AlgebraForgeSystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 26;

    private static final Color BOX_COLOR = new Color(0.06f, 0.06f, 0.10f, 0.96f);
    private static final Color BORDER_COLOR = new Color(0.35f, 0.35f, 0.55f, 1.0f);
    private static final Color TITLE_COLOR = new Color(0.9f, 0.85f, 0.65f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color DIM_COLOR = new Color(0.75f, 0.75f, 0.75f, 1.0f);
    private static final Color WARN_COLOR = new Color(1.0f, 0.75f, 0.35f, 1.0f);

    private final GameStateManager gameStateManager;

    private GateSystem gateSystem;

    private ImmutableArray<Entity> enemies;

    private final ComponentMapper<EnemyComponent> enemyMapper = ComponentMapper.getFor(EnemyComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);

    private final OverlayUi ui;

    private ImmutableArray<Entity> players;

    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<CollisionComponent> colMapper = ComponentMapper.getFor(CollisionComponent.class);
    private final ComponentMapper<PlayerInventoryComponent> invMapper = ComponentMapper.getFor(PlayerInventoryComponent.class);
    private final ComponentMapper<AlgebraTerminalComponent> algebraMapper = ComponentMapper.getFor(AlgebraTerminalComponent.class);

    private boolean interactionOpen = false;
    private GameState stateBefore = GameState.PLAYING;

    private Entity activeTerminal;
    private AlgebraTerminalComponent activeTerminalComp;

    private List<String> availableGlyphIds = new ArrayList<>();
    private int selectionStage = 0; // 0 = select left, 1 = select right
    private int selectedLeft = -1;
    private int selectedRight = -1;

    private String statusLine = "";
    private float statusTimer = 0f;

    // Prevent the overlay from immediately closing/committing on the same key press
    // (E/SPACE) that opened it via PlayerInputSystem.
    private float inputDebounceSeconds = 0f;

    public AlgebraForgeSystem(GameStateManager gameStateManager) {
        super(88);
        this.gameStateManager = gameStateManager;

        this.ui = new OverlayUi(1.1f, 1.9f);
    }

    public void setGateSystem(GateSystem gateSystem) {
        this.gateSystem = gateSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, PositionComponent.class).get());
        enemies = engine.getEntitiesFor(Family.all(EnemyComponent.class, PositionComponent.class).get());
    }

    /**
     * Starts an interaction session with a nearby algebra terminal.
     * Called by PlayerInputSystem when the player presses E/SPACE near the terminal.
     */
    public boolean beginInteraction(Entity terminalEntity) {
        if (terminalEntity == null) return false;
        AlgebraTerminalComponent comp = algebraMapper.get(terminalEntity);
        if (comp == null) return false;

        if (players == null || players.size() == 0) return false;
        Entity player = players.first();
        PlayerInventoryComponent inv = invMapper.get(player);

        // Don't allow opening riddle interactions during combat.
        if (isCombatActiveForPlayer(player)) {
            if (inv != null) {
                inv.toast("Clear the arena first.", 1.2f);
            }
            return true;
        }

        if (inv == null) {
            status("No sigils.");
            return true;
        }

        // Build sorted list of available glyph tokens.
        rebuildAvailableGlyphIds(inv);

        if (availableGlyphIds.size() < 1) {
            status("You carry no glyphs.");
            return true;
        }

        activeTerminal = terminalEntity;
        activeTerminalComp = comp;

        boolean wasOpen = interactionOpen;

        interactionOpen = true;
        inputDebounceSeconds = 0.18f;
        selectionStage = 0;
        selectedLeft = -1;
        selectedRight = -1;
        statusLine = "";
        statusTimer = 0f;

        if (gameStateManager != null) {
            // Only capture stateBefore when opening the overlay.
            if (!wasOpen) {
                stateBefore = gameStateManager.getState();
                gameStateManager.setState(GameState.ALGEBRA_FORGE_RIDDLE);
            }
        }

        return true;
    }

    private boolean isCombatActiveForPlayer(Entity player) {
        if (player == null) return false;
        if (enemies == null || enemies.size() == 0) return false;

        PositionComponent pp = posMapper.get(player);
        CollisionComponent pc = colMapper.get(player);
        if (pp == null) return false;

        float px = pp.x + (pc != null ? pc.width * 0.5f : GameConstants.TILE_SIZE * 0.5f);
        float py = pp.y + (pc != null ? pc.height * 0.5f : GameConstants.TILE_SIZE * 0.5f);

        String arenaId = gateSystem != null ? gateSystem.getCurrentPlayerArenaId() : null;

        // Primary rule (matches arena-gate lock): if the player's current arena has
        // any living enemies, treat it as combat.
        if (gateSystem != null && arenaId != null && gateSystem.getLivingEnemyCountInArena(arenaId) > 0) {
            return true;
        }

        // Fallback: if arena tracking isn't available, use a generous proximity check.
        float nearbyThreatRadius = GameConstants.TILE_SIZE * 12f;
        float nearbyThreatRadius2 = nearbyThreatRadius * nearbyThreatRadius;

        for (int i = 0; i < enemies.size(); i++) {
            Entity e = enemies.get(i);
            if (e == null) continue;
            EnemyComponent ec = enemyMapper.get(e);
            if (ec == null) continue;
            HealthComponent hp = healthMapper.get(e);
            if (hp != null && hp.isDead()) continue;

            // If arena tracking isn't available (arenaId == null), then aggro is a strong
            // signal of combat even if the enemy is not yet close.
            if (arenaId == null && ec.isAggroed) {
                return true;
            }

            PositionComponent ep = posMapper.get(e);
            CollisionComponent ecoll = colMapper.get(e);
            if (ep == null) continue;

            float ex = ep.x + (ecoll != null ? ecoll.width * 0.5f : GameConstants.TILE_SIZE * 0.5f);
            float ey = ep.y + (ecoll != null ? ecoll.height * 0.5f : GameConstants.TILE_SIZE * 0.5f);

            float dx = ex - px;
            float dy = ey - py;
            if (dx * dx + dy * dy <= nearbyThreatRadius2) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null) return;

        if (!gameStateManager.isActiveGameplay() && gameStateManager.getState() != GameState.ALGEBRA_FORGE_RIDDLE) {
            return;
        }

        if (!interactionOpen) {
            // Nothing to render.
            return;
        }

        // If combat starts while the overlay is open, force-close it.
        if (players != null && players.size() > 0) {
            Entity player = players.first();
            if (isCombatActiveForPlayer(player)) {
                PlayerInventoryComponent inv = invMapper.get(player);
                if (inv != null) {
                    inv.toast("Clear the arena first.", 1.2f);
                }
                closeOverlay();
                return;
            }
        }

        if (inputDebounceSeconds > 0f) {
            inputDebounceSeconds -= deltaTime;
        }

        if (statusTimer > 0f) {
            statusTimer -= deltaTime;
            if (statusTimer <= 0f) {
                statusLine = "";
            }
        }

        handleOverlayInput();
        renderOverlay();
    }

    private void handleOverlayInput() {
        if (inputDebounceSeconds > 0f) {
            // Ignore the key that opened the overlay (and any immediate repeats).
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
            || Gdx.input.isKeyJustPressed(Input.Keys.E)
            || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            closeOverlay();
            return;
        }

        // Number keys 1..9 select tokens from the list.
        for (int i = 0; i < 9; i++) {
            int key = Input.Keys.NUM_1 + i;
            if (Gdx.input.isKeyJustPressed(key)) {
                int idx = i;
                if (idx >= 0 && idx < availableGlyphIds.size()) {
                    if (selectionStage == 0) {
                        selectedLeft = idx;
                        selectionStage = 1;
                    } else {
                        selectedRight = idx;
                    }
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (selectionStage == 1) {
                selectionStage = 0;
                selectedRight = -1;
            } else {
                selectedLeft = -1;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (selectedLeft < 0 || selectedLeft >= availableGlyphIds.size()) {
                status("Choose the first glyph (1..).", 1.5f);
                return;
            }
            if (selectedRight < 0 || selectedRight >= availableGlyphIds.size()) {
                status("Choose the second glyph (1..).", 1.5f);
                return;
            }

            String a = availableGlyphIds.get(selectedLeft);
            String b = availableGlyphIds.get(selectedRight);
            executeOperation(a, b);
        }
    }

    private void executeOperation(String a, String b) {
        if (players == null || players.size() == 0) return;
        Entity player = players.first();
        PlayerInventoryComponent inv = invMapper.get(player);
        if (inv == null) {
            status("No inventory.");
            return;
        }

        if (!inv.hasToken(a) || !inv.hasToken(b)) {
            status("Missing one of the glyphs.");
            return;
        }

        if (activeTerminalComp == null) {
            status("The altar is silent.");
            return;
        }

        if (activeTerminalComp.kind == AlgebraTerminalComponent.Kind.ORACLE) {
            if (activeTerminalComp.charges <= 0) {
                status("The oracle is spent.");
                return;
            }
            activeTerminalComp.charges -= 1;
                String out = multiplyGlyphs(activeTerminalComp.opId, a, b);
                status(GlyphNames.displayName(a) + " op " + GlyphNames.displayName(b) + " -> " + GlyphNames.displayName(out)
                    + "   (" + activeTerminalComp.charges + " left)", 4.5f);
            return;
        }

        // Forge consumes inputs and produces output.
        boolean consumedA = inv.consumeToken(a, 1);
        boolean consumedB = inv.consumeToken(b, 1);
        if (!consumedA || !consumedB) {
            // Put back if partial.
            if (consumedA) inv.addToken(a, 1);
            if (consumedB) inv.addToken(b, 1);
            status("The forge rejects the offering.");
            return;
        }

        String out = multiplyGlyphs(activeTerminalComp.opId, a, b);
        inv.addToken(out, 1);

        // Notify on first-time discoveries.
        if (GlyphNames.isGlyph(out) && inv.markDiscovered(out)) {
            inv.toast("New glyph discovered: " + GlyphNames.displayNameWithId(out) + "!", 2.8f);
        }

        // Refresh available list since counts changed (without touching game state).
        rebuildAvailableGlyphIds(inv);
        selectionStage = 0;
        selectedLeft = -1;
        selectedRight = -1;
        status(GlyphNames.displayName(a) + " op " + GlyphNames.displayName(b) + " -> " + GlyphNames.displayName(out), 4.5f);
    }

    private void rebuildAvailableGlyphIds(PlayerInventoryComponent inv) {
        availableGlyphIds = new ArrayList<>();
        if (inv == null) return;
        for (Object o : inv.tokenCounts.keys()) {
            if (o instanceof String) {
                String id = (String) o;
                if (isGlyphToken(id) && inv.tokenCounts.get(id, 0) > 0) {
                    availableGlyphIds.add(id);
                }
            }
        }
        Collections.sort(availableGlyphIds);
    }

    private void closeOverlay() {
        interactionOpen = false;
        activeTerminal = null;
        activeTerminalComp = null;
        selectionStage = 0;
        selectedLeft = -1;
        selectedRight = -1;
        availableGlyphIds = new ArrayList<>();
        statusLine = "";
        statusTimer = 0f;

        if (gameStateManager != null) {
            // Safety: never leave the game stuck in the overlay state.
            GameState restore = (stateBefore != null) ? stateBefore : GameState.PLAYING;
            if (restore == GameState.ALGEBRA_FORGE_RIDDLE) {
                restore = GameState.PLAYING;
            }
            gameStateManager.setState(restore);
        }
    }

    private void renderOverlay() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        int boxW = Math.min(820, screenW - PADDING * 2);
        int boxH = Math.min(560, screenH - PADDING * 2);
        int x = (screenW - boxW) / 2;
        int y = (screenH - boxH) / 2;

        ui.drawPanel(x, y, boxW, boxH, BOX_COLOR, BORDER_COLOR);

        ui.batch.begin();

        float textX = x + 25;
        float textY = y + boxH - 25;

        ui.titleFont.setColor(TITLE_COLOR);
        String title = "Algebra Forge";
        if (activeTerminalComp != null) {
            title = "Algebra Forge - " + (activeTerminalComp.kind == AlgebraTerminalComponent.Kind.ORACLE ? "Oracle" : "Forge");
        }
        ui.titleFont.draw(ui.batch, title, textX, textY);
        textY -= 42;

        ui.font.setColor(TEXT_COLOR);
        String body =
            "Choose two glyphs. Order matters.\n" +
            "1..9 selects. ENTER/SPACE commits. BACKSPACE steps back.\n" +
            "ESC/E/M closes (movement resumes)." +
            (activeTerminalComp != null && activeTerminalComp.kind == AlgebraTerminalComponent.Kind.ORACLE
                ? ("  Oracle charges: " + activeTerminalComp.charges)
                : "");

        ui.layout.setText(ui.font, body, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
        ui.font.draw(ui.batch, ui.layout, textX, textY);
        textY -= 75;

        PlayerInventoryComponent inv = null;
        if (players != null && players.size() > 0) {
            inv = invMapper.get(players.first());
        }

        int shown = Math.min(availableGlyphIds.size(), 9);
        for (int i = 0; i < shown; i++) {
            String id = availableGlyphIds.get(i);
            int count = inv != null ? inv.tokenCounts.get(id, 0) : 0;

            boolean isLeft = (i == selectedLeft);
            boolean isRight = (i == selectedRight);

            Color c = DIM_COLOR;
            if (selectionStage == 0) c = TEXT_COLOR;
            if (selectionStage == 1) c = TEXT_COLOR;
            if (isLeft || isRight) c = TITLE_COLOR;

            ui.font.setColor(c);
            String marker = (isLeft ? "[L]" : "   ") + (isRight ? "[R]" : "   ");
            ui.font.draw(ui.batch, (i + 1) + ". " + marker + "  " + GlyphNames.displayName(id) + "  x" + count, textX, textY);
            textY -= LINE_HEIGHT;
        }

        if (availableGlyphIds.size() > 9) {
            ui.font.setColor(DIM_COLOR);
            ui.font.draw(ui.batch, "(more glyphs exist, but only 1..9 are selectable)", textX, textY);
            textY -= LINE_HEIGHT;
        }

        if (statusLine != null && !statusLine.isEmpty()) {
            ui.font.setColor(WARN_COLOR);
            ui.font.draw(ui.batch, statusLine, textX, y + 55);
        }

        ui.font.setColor(DIM_COLOR);
        ui.font.draw(ui.batch, "Hint: press ESC, E, or M to leave.", textX, y + 30);

        ui.batch.end();
    }

    private void status(String msg) {
        status(msg, 2.5f);
    }

    private void status(String msg, float seconds) {
        statusLine = msg != null ? msg : "";
        statusTimer = Math.max(0.5f, seconds);
    }

    private boolean isGlyphToken(String tokenId) {
        return tokenId != null && tokenId.startsWith("glyph_");
    }

    // --- Hidden algebra: permuted D4 group law over 8 elements. ---

    /** Representation: r^k * s^f in normal form (rotation then optional reflection). */
    private static final class D4Elt {
        final int k;     // 0..3
        final int f;     // 0 or 1
        D4Elt(int k, int f) {
            this.k = ((k % 4) + 4) % 4;
            this.f = (f & 1);
        }
    }

    private D4Elt d4Mul(D4Elt a, D4Elt b) {
        // r^a.k s^a.f * r^b.k s^b.f = r^{a.k + (-1)^{a.f} b.k} s^{a.f + b.f}
        int sign = (a.f == 0) ? 1 : -1;
        int k = a.k + sign * b.k;
        int f = a.f ^ b.f;
        return new D4Elt(k, f);
    }

    private String multiplyGlyphs(String opId, String glyphA, String glyphB) {
        int idxA = glyphIndex(glyphA);
        int idxB = glyphIndex(glyphB);
        if (idxA < 0 || idxB < 0) return glyphA;

        int[] perm = permutationFor(opId);
        int[] inv = inversePermutation(perm);

        int eltA = perm[idxA];
        int eltB = perm[idxB];

        D4Elt a = eltFromIndex(eltA);
        D4Elt b = eltFromIndex(eltB);
        D4Elt out = d4Mul(a, b);

        int outIdx = indexFromElt(out);
        int glyphOut = inv[outIdx];
        return "glyph_" + glyphOut;
    }

    private int glyphIndex(String glyphId) {
        if (glyphId == null) return -1;
        if (!glyphId.startsWith("glyph_")) return -1;
        try {
            return Integer.parseInt(glyphId.substring("glyph_".length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private D4Elt eltFromIndex(int idx) {
        // 0..3: rotations r^k, 4..7: reflections r^(idx-4) s
        if (idx < 4) return new D4Elt(idx, 0);
        return new D4Elt(idx - 4, 1);
    }

    private int indexFromElt(D4Elt e) {
        return e.f == 0 ? e.k : (4 + e.k);
    }

    private int[] permutationFor(String opId) {
        // Deterministic pseudo-random permutation of 0..7 based on opId.
        int seed = 0xC0FFEE;
        if (opId != null) {
            seed = opId.hashCode() ^ 0x9E3779B9;
        }
        Random rng = new Random(seed);

        List<Integer> xs = new ArrayList<>();
        for (int i = 0; i < 8; i++) xs.add(i);
        // Fisher-Yates via deterministic RNG.
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

    private int[] inversePermutation(int[] perm) {
        int[] inv = new int[perm.length];
        for (int i = 0; i < perm.length; i++) {
            inv[perm[i]] = i;
        }
        return inv;
    }

    public void dispose() {
        ui.dispose();
    }
}
