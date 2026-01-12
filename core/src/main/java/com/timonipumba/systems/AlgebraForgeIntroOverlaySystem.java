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
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.components.PositionComponent;
import com.timonipumba.ui.OverlayUi;

/**
 * Intro/rules overlay for the Algebra Forge riddle.
 *
 * Requirements:
 * - Opens when the player presses M.
 * - Can be opened any time with M.
 * - Uses simple colored tiles (no icons) to explain key objects.
 * - Does not "give away" the underlying math; it just explains the setting.
 */
public class AlgebraForgeIntroOverlaySystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 28;

    private static final Color BOX_COLOR = new Color(0.08f, 0.08f, 0.12f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.95f, 0.8f, 0.95f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);

    private static final float TILE = 18f;
    private static final float TILE_GAP = 10f;

    private final GameStateManager gameStateManager;

    private final OverlayUi ui;

    private ImmutableArray<Entity> players;

    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);

    private boolean introOpen = false;

    private GameState stateBeforeIntro = GameState.PLAYING;

    public AlgebraForgeIntroOverlaySystem(GameStateManager gameStateManager) {
        super(90);
        this.gameStateManager = gameStateManager;

        this.ui = new OverlayUi(1.2f, 2.0f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class, PositionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null) return;

        // Allow this system to run while the overlay is open.
        if (!gameStateManager.isActiveGameplay() && gameStateManager.getState() != GameState.ALGEBRA_FORGE_RIDDLE) {
            return;
        }

        if (players == null || players.size() == 0) return;

        Entity player = players.first();
        if (posMapper.get(player) == null) return;

        if (introOpen) {
            renderIntroOverlay();
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
                closeIntro();
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            openIntro();
        }
    }

    private void openIntro() {
        introOpen = true;
        stateBeforeIntro = gameStateManager.getState();
        gameStateManager.setState(GameState.ALGEBRA_FORGE_RIDDLE);
    }

    private void closeIntro() {
        introOpen = false;
        gameStateManager.setState(stateBeforeIntro);
    }

    private void renderIntroOverlay() {
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
        ui.titleFont.draw(ui.batch, "Algebra Forge", textX, textY);
        textY -= 45;

        ui.font.setColor(TEXT_COLOR);

        String body =
                "This is not a normal dungeon. The machines here speak in symbols.\n\n" +
            "- Press E / SPACE near an Oracle or Forge to interact.\n" +
            "- Oracle: ask a question (A op B). It shows the result. Costs 1 charge.\n" +
            "- Forge: consumes two glyphs (A and B) and creates the result (A op B).\n" +
            "- Order matters: A op B can differ from B op A.\n" +
            "- Sockets accept a specific glyph. Gates react to sockets.\n\n" +
            "You are not told the rule. You have to discover what is being computed.";

        ui.layout.setText(ui.font, body, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
        ui.font.draw(ui.batch, ui.layout, textX, textY);

        ui.batch.end();

        // Legend: colored "tiles" + labels (no icons).
        float legendLeft = x + 25;
        float legendBaseY = y + 145;

        ui.shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        drawLegendRow(legendLeft, legendBaseY + (TILE + TILE_GAP) * 3, new Color(0.2f, 0.9f, 0.2f, 1f), "Glyph token (collect / craft)");
        drawLegendRow(legendLeft, legendBaseY + (TILE + TILE_GAP) * 2, new Color(0.9f, 0.2f, 0.9f, 1f), "Oracle (asks / answers)");
        drawLegendRow(legendLeft, legendBaseY + (TILE + TILE_GAP) * 1, new Color(0.2f, 0.7f, 1.0f, 1f), "Forge (combine two glyphs)");
        drawLegendRow(legendLeft, legendBaseY + (TILE + TILE_GAP) * 0, new Color(0.95f, 0.3f, 0.3f, 1f), "Socket (wants one glyph)");
        ui.shapes.end();

        ui.batch.begin();
        ui.font.setColor(TEXT_COLOR);
        float labelX = legendLeft + TILE + 12f;
        float labelY = legendBaseY + (TILE + TILE_GAP) * 3 + TILE - 3f;
        ui.font.draw(ui.batch, "Glyph token (collect / craft)", labelX, labelY);
        labelY -= (TILE + TILE_GAP);
        ui.font.draw(ui.batch, "Oracle (asks / answers)", labelX, labelY);
        labelY -= (TILE + TILE_GAP);
        ui.font.draw(ui.batch, "Forge (combine two glyphs)", labelX, labelY);
        labelY -= (TILE + TILE_GAP);
        ui.font.draw(ui.batch, "Socket (wants one glyph)", labelX, labelY);

        ui.font.setColor(HINT_COLOR);
        ui.font.draw(ui.batch, "Press ENTER/SPACE to continue.", textX, y + 30 + LINE_HEIGHT);
        ui.font.draw(ui.batch, "Press M to view this again.", textX, y + 30);

        ui.batch.end();
    }

    private void drawLegendRow(float x, float y, Color color, String label) {
        ui.shapes.setColor(color);
        ui.shapes.rect(x, y, TILE, TILE);
        ui.shapes.setColor(new Color(0f, 0f, 0f, 0.35f));
        ui.shapes.rect(x, y, TILE, 2f);
        ui.shapes.rect(x, y, 2f, TILE);
        ui.shapes.rect(x + TILE - 2f, y, 2f, TILE);
        ui.shapes.rect(x, y + TILE - 2f, TILE, 2f);
    }

    public boolean isIntroOverlayOpen() {
        return introOpen;
    }

    public void dispose() {
        ui.dispose();
    }
}
