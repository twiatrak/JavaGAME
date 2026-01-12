package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.ui.OverlayUi;

/** Intro overlay for the Graph Lights-Out riddle. */
public class GraphLightsOutIntroOverlaySystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 28;

    private static final Color BOX_COLOR = new Color(0.08f, 0.08f, 0.12f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.9f, 0.9f, 0.6f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);

    private final GameStateManager gameStateManager;

    private final OverlayUi ui;

    private ImmutableArray<Entity> players;

    private boolean introOpen = false;

    private GameState stateBeforeIntro = GameState.PLAYING;

    public GraphLightsOutIntroOverlaySystem(GameStateManager gameStateManager) {
        super(90);
        this.gameStateManager = gameStateManager;

        this.ui = new OverlayUi(1.2f, 2.0f);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null) return;

        if (!gameStateManager.isActiveGameplay() && gameStateManager.getState() != GameState.LIGHTS_OUT_RIDDLE) {
            return;
        }

        if (players == null || players.size() == 0) return;

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
        gameStateManager.setState(GameState.LIGHTS_OUT_RIDDLE);
    }

    private void closeIntro() {
        introOpen = false;
        gameStateManager.setState(stateBeforeIntro);
    }

    private void renderIntroOverlay() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();

        int boxW = Math.min(780, screenW - PADDING * 2);
        int boxH = Math.min(520, screenH - PADDING * 2);
        int x = (screenW - boxW) / 2;
        int y = (screenH - boxH) / 2;

        ui.drawPanel(x, y, boxW, boxH, BOX_COLOR, BORDER_COLOR);

        ui.batch.begin();

        float textX = x + 25;
        float textY = y + boxH - 25;

        ui.titleFont.setColor(TITLE_COLOR);
        ui.titleFont.draw(ui.batch, "Lantern Garden", textX, textY);
        textY -= 45;

        ui.font.setColor(TEXT_COLOR);

        String body =
                "The lanterns are grumpy tonight. Make them all glow!\n\n" +
                "- Stand next to a lantern and press E / SPACE to tap it.\n" +
                "- When you tap one, some nearby lanterns may flip too.\n\n" +
                "There is a trick to doing this without endless tapping.\n" +
                "Try repeating the same tap twice and watch carefully.\n\n" +
                "When every lantern is glowing, the reward door opens.";

        ui.layout.setText(ui.font, body, ui.font.getColor(), boxW - 50, com.badlogic.gdx.utils.Align.left, true);
        ui.font.draw(ui.batch, ui.layout, textX, textY);

        ui.font.setColor(HINT_COLOR);
        ui.font.draw(ui.batch, "Press ENTER/SPACE to continue.", textX, y + 30 + LINE_HEIGHT);
        ui.font.draw(ui.batch, "Press M to view this again.", textX, y + 30);

        ui.batch.end();
    }

    public void dispose() {
        ui.dispose();
    }
}
