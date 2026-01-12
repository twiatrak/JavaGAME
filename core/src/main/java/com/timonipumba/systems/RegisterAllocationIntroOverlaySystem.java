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

/**
 * Shows an intro/rules popup for the Register Allocation riddle in generated TMX mode.
 *
 * Requirements:
 * - Opens when the player presses M.
 * - Can be opened any time with M.
 */
public class RegisterAllocationIntroOverlaySystem extends EntitySystem {

    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 28;

    private static final Color BOX_COLOR = new Color(0.08f, 0.08f, 0.12f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.7f, 0.9f, 1.0f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);

    private final GameStateManager gameStateManager;

    private final OverlayUi ui;

    private ImmutableArray<Entity> players;

    private boolean introOpen = false;

    private GameState stateBeforeIntro = GameState.PLAYING;

    public RegisterAllocationIntroOverlaySystem(GameStateManager gameStateManager) {
        super(90); // overlay-ish; same slot as other overlays
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

        // Allow this system to run while the overlay is open.
        if (!gameStateManager.isActiveGameplay() && !gameStateManager.isRegisterAllocationRiddle()) {
            return;
        }

        if (players == null || players.size() == 0) {
            return;
        }

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
        gameStateManager.setState(GameState.REGISTER_ALLOCATION_RIDDLE);
    }

    private void closeIntro() {
        introOpen = false;
        gameStateManager.setState(stateBeforeIntro);
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

        ui.titleFont.setColor(TITLE_COLOR);
        ui.titleFont.draw(ui.batch, "Register Allocation", textX, textY);
        textY -= 45;

        ui.font.setColor(TEXT_COLOR);

        String body =
            "Goal: assign registers so *neighbors* are different.\n\n" +
            "Neighbor = a node directly connected by a corridor to this node.\n" +
            "(If you can walk straight from one node to another via one hallway, they are neighbors.)\n\n" +
            "1) Walk over the registers in the spawn room (R1, R2, R3).\n" +
            "   You can pick up ALL of them — they stay in your inventory and are NOT consumed.\n" +
            "2) Find the node sockets in the ring.\n" +
            "3) Stand next to a node and press E/SPACE to assign.\n" +
            "   Press again to cycle through the registers you own.\n" +
            "4) A choice is invalid if ANY neighbor already has that same register.\n\n" +
            "Feedback: the node changes color based on the assigned register.\n" +
            "When all nodes are assigned, the reward door opens.";

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
