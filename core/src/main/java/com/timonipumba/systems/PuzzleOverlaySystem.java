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
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.level.Puzzle;
import com.timonipumba.level.PuzzleHandlerRegistry;
import com.timonipumba.level.LevelManager;
import com.timonipumba.ui.OverlayUi;

/**
 * System that renders puzzle overlay and handles puzzle input when active.
 * 
 * The puzzle overlay appears when the player interacts with a locked puzzle door.
 * While the overlay is visible:
 * - Keyboard input is routed to the overlay for answer entry
 * - The game world continues rendering underneath (no scene switch)
 * - Movement and combat are paused
 * 
 * On correct answer, the door unlocks and normal gameplay resumes.
 */
public class PuzzleOverlaySystem extends EntitySystem {
    
    private static final int PADDING = 40;
    private static final int LINE_HEIGHT = 30;
    private static final Color BOX_COLOR = new Color(0.1f, 0.1f, 0.2f, 0.95f);
    private static final Color BORDER_COLOR = new Color(0.3f, 0.3f, 0.5f, 1.0f);
    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TITLE_COLOR = new Color(0.7f, 0.9f, 1.0f, 1.0f);
    private static final Color INPUT_COLOR = new Color(0.2f, 0.8f, 0.3f, 1.0f);
    private static final Color ERROR_COLOR = new Color(1.0f, 0.4f, 0.4f, 1.0f);
    private static final Color SUCCESS_COLOR = new Color(0.4f, 1.0f, 0.4f, 1.0f);
    private static final Color HINT_COLOR = new Color(0.8f, 0.8f, 0.4f, 1.0f);
    
    private static final int HINT_ATTEMPTS_THRESHOLD = 3;
    private static final float INPUT_COOLDOWN_TIME = 0.15f;
    
    private final GameStateManager gameStateManager;
    private final OverlayUi ui;
    
    private ComponentMapper<PuzzleDoorComponent> puzzleDoorMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<RenderableComponent> renderableMapper;
    
    // Active puzzle state
    private Entity activeDoorEntity;
    private Puzzle activePuzzle;
    private final StringBuilder inputBuffer = new StringBuilder();
    private String feedbackMessage = "";
    private Color feedbackColor = TEXT_COLOR;
    private int incorrectAttempts = 0;
    private boolean showHint = false;
    private float inputCooldown = 0f;
    
    /** Gate system for opening gates when puzzle is solved (gate-based progression) */
    private GateSystem gateSystem;

    /** Level manager used to advance levels when a finale puzzle is solved. */
    private LevelManager levelManager;
    
    /** 
     * Stores the game state before entering PUZZLE state, for restoration on close.
     * Defaults to PLAYING as a safe fallback in case closePuzzle() is called without
     * a corresponding startPuzzle() call.
     */
    private GameState stateBeforePuzzle = GameState.PLAYING;
    
    public PuzzleOverlaySystem(GameStateManager gameStateManager) {
        super(90); // Run after rendering but before HUD
        this.gameStateManager = gameStateManager;

        this.ui = new OverlayUi(1.2f, 2.0f);
        
        puzzleDoorMapper = ComponentMapper.getFor(PuzzleDoorComponent.class);
        collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
    }
    
    /**
     * Set the gate system for opening gates on puzzle solve (gate-based progression).
     * @param gateSystem The gate system
     */
    public void setGateSystem(GateSystem gateSystem) {
        this.gateSystem = gateSystem;
    }

    /**
     * Set the level manager callback.
     * Used for finale puzzles where solving the terminal should advance the level.
     */
    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }
    
    @Override
    public void update(float deltaTime) {
        // Update cooldown
        if (inputCooldown > 0) {
            inputCooldown -= deltaTime;
        }
        
        // Only process when in PUZZLE state
        if (!gameStateManager.isPuzzle()) {
            return;
        }
        
        // Handle puzzle input
        handlePuzzleInput();
        
        // Render overlay
        renderOverlay();
    }
    
    /**
     * Start puzzle interaction for a door entity.
     * Called by PlayerInputSystem when player interacts with a locked puzzle door.
     * Stores the current game state to restore it when the puzzle is closed.
     */
    public void startPuzzle(Entity doorEntity) {
        PuzzleDoorComponent door = puzzleDoorMapper.get(doorEntity);
        if (door == null || !door.hasPuzzle()) {
            return;
        }
        
        this.activeDoorEntity = doorEntity;
        this.activePuzzle = door.puzzle;
        this.inputBuffer.setLength(0);
        this.feedbackMessage = "";
        this.feedbackColor = TEXT_COLOR;
        this.incorrectAttempts = 0;
        this.showHint = false;
        this.inputCooldown = INPUT_COOLDOWN_TIME;
        
        // Store current state before transitioning to PUZZLE state
        // This allows restoration to LEVEL_CLEAR if that was the state before puzzle
        this.stateBeforePuzzle = gameStateManager.getState();
        
        gameStateManager.setState(GameState.PUZZLE);
    }
    
    /**
     * Close the puzzle overlay and return to the previous gameplay state.
     * Restores the state that was active before the puzzle was opened (e.g., PLAYING or LEVEL_CLEAR).
     */
    public void closePuzzle() {
        this.activeDoorEntity = null;
        this.activePuzzle = null;
        this.inputBuffer.setLength(0);
        this.feedbackMessage = "";
        this.incorrectAttempts = 0;
        this.showHint = false;
        
        // Restore the state that was active before opening the puzzle
        // This allows interactions to continue working during LEVEL_CLEAR state
        gameStateManager.setState(stateBeforePuzzle);
    }
    
    /**
     * Check if a puzzle is currently active.
     */
    public boolean isPuzzleActive() {
        return activePuzzle != null && gameStateManager.isPuzzle();
    }
    
    private void handlePuzzleInput() {
        if (activePuzzle == null || inputCooldown > 0) {
            return;
        }
        
        // ESC to cancel and return to gameplay
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            closePuzzle();
            inputCooldown = INPUT_COOLDOWN_TIME;
            return;
        }
        
        // ENTER to submit answer
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            submitAnswer();
            inputCooldown = INPUT_COOLDOWN_TIME;
            return;
        }
        
        // BACKSPACE to delete
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            }
            inputCooldown = INPUT_COOLDOWN_TIME / 2;
            return;
        }
        
        // Letters A-Z
        for (int i = Input.Keys.A; i <= Input.Keys.Z; i++) {
            if (Gdx.input.isKeyJustPressed(i)) {
                char c = (char) ('A' + (i - Input.Keys.A));
                inputBuffer.append(c);
                inputCooldown = INPUT_COOLDOWN_TIME / 3;
                return;
            }
        }
        
        // Numbers 0-9
        for (int i = Input.Keys.NUM_0; i <= Input.Keys.NUM_9; i++) {
            if (Gdx.input.isKeyJustPressed(i)) {
                char c = (char) ('0' + (i - Input.Keys.NUM_0));
                inputBuffer.append(c);
                inputCooldown = INPUT_COOLDOWN_TIME / 3;
                return;
            }
        }
    }
    
    private void submitAnswer() {
        if (activePuzzle == null || activeDoorEntity == null) {
            return;
        }
        
        String answer = inputBuffer.toString();
        
        if (PuzzleHandlerRegistry.checkAnswer(activePuzzle, answer)) {
            // Correct answer - unlock door
            feedbackMessage = "ACCESS GRANTED!";
            feedbackColor = SUCCESS_COLOR;
            
            unlockDoor();
            
            // Close puzzle after brief delay to show success message
            // For now, close immediately
            closePuzzle();
        } else {
            // Incorrect answer
            incorrectAttempts++;
            feedbackMessage = "ACCESS DENIED - Try again";
            feedbackColor = ERROR_COLOR;
            
            if (incorrectAttempts >= HINT_ATTEMPTS_THRESHOLD) {
                showHint = true;
            }
        }
    }
    
    private void unlockDoor() {
        if (activeDoorEntity == null) {
            return;
        }
        
        PuzzleDoorComponent door = puzzleDoorMapper.get(activeDoorEntity);
        CollisionComponent collision = collisionMapper.get(activeDoorEntity);
        RenderableComponent renderable = renderableMapper.get(activeDoorEntity);
        
        String puzzleId = null;
        boolean isFinale = false;
        if (door != null) {
            door.unlock();
            puzzleId = door.getPuzzleId();
            isFinale = door.isFinale();
        }
        
        // Disable collision so player can pass through
        if (collision != null) {
            if (!door.dimensionsStored) {
                door.originalWidth = collision.width;
                door.originalHeight = collision.height;
                door.dimensionsStored = true;
            }
            collision.width = 0;
            collision.height = 0;
        }
        
        // Change door appearance to indicate unlocked
        if (renderable != null) {
            renderable.color = new Color(0.2f, 0.8f, 0.2f, 0.5f); // Green, semi-transparent
        }
        
        // Handle finale puzzle - trigger LEVEL_CLEAR instead of opening gates
        // In the finale puzzle arena, solving the riddle ends the level (no outbound gate)
        if (isFinale) {
            if (levelManager != null) {
                levelManager.loadNextLevel();
            } else {
                gameStateManager.setState(GameState.LEVEL_CLEAR);
            }
            return;
        }
        
        // For non-finale puzzles, use gate-based progression (portals are disabled in Tiled mode)
        // Gate opening is handled by GateSystem when enemies reach zero
        // If gates need to be opened by puzzle solve (not enemy clear), use gateSystem
        if (gateSystem != null) {
            gateSystem.openAllGatesImmediately();
        }
    }
    
    private void renderOverlay() {
        if (activePuzzle == null) {
            return;
        }
        
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        
        // Calculate overlay dimensions
        float overlayWidth = Math.min(600, screenWidth - PADDING * 2);
        float overlayHeight = 350;
        float overlayX = (screenWidth - overlayWidth) / 2;
        float overlayY = (screenHeight - overlayHeight) / 2;
        
        // Draw semi-transparent background overlay
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        ui.shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        ui.shapes.setColor(0, 0, 0, 0.7f);
        ui.shapes.rect(0, 0, screenWidth, screenHeight);
        ui.shapes.end();
        
        // Draw puzzle box
        ui.drawPanel(overlayX, overlayY, overlayWidth, overlayHeight, BOX_COLOR, BORDER_COLOR);
        
        // Draw input box
        float inputBoxHeight = 40;
        float inputBoxY = overlayY + 60;
        ui.drawPanel(
                overlayX + 20,
                inputBoxY,
                overlayWidth - 40,
                inputBoxHeight,
                new Color(0.15f, 0.15f, 0.25f, 0.9f),
                INPUT_COLOR);
        
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        
        // Draw text
        ui.batch.begin();
        
        // Title
        ui.titleFont.setColor(TITLE_COLOR);
        ui.layout.setText(ui.titleFont, "SECURITY TERMINAL");
        float titleX = overlayX + (overlayWidth - ui.layout.width) / 2;
        ui.titleFont.draw(ui.batch, "SECURITY TERMINAL", titleX, overlayY + overlayHeight - 20);
        
        float y = overlayY + overlayHeight - 70;
        
        // Prompt
        ui.font.setColor(TEXT_COLOR);
        String prompt = activePuzzle.getData("prompt", "Solve the puzzle to unlock the door:");
        ui.font.draw(ui.batch, prompt, overlayX + 20, y, overlayWidth - 40, -1, true);
        y -= LINE_HEIGHT * 2;
        
        // Ciphertext
        String ciphertext = activePuzzle.getData("ciphertext", "");
        if (!ciphertext.isEmpty()) {
            ui.font.setColor(HINT_COLOR);
            ui.font.draw(ui.batch, "Ciphertext: " + ciphertext, overlayX + 20, y);
            y -= LINE_HEIGHT;
        }
        
        // Key
        String key = activePuzzle.getData("key", "");
        if (!key.isEmpty()) {
            ui.font.draw(ui.batch, "Key: " + key, overlayX + 20, y);
            y -= LINE_HEIGHT;
        }
        
        // Hint (if showing)
        if (showHint) {
            String hint = activePuzzle.getData("hint", "No hint available.");
            ui.font.setColor(HINT_COLOR);
            ui.font.draw(ui.batch, "Hint: " + hint, overlayX + 20, y, overlayWidth - 40, -1, true);
        }
        
        // Input text
        ui.font.setColor(INPUT_COLOR);
        String inputText = "> " + inputBuffer.toString() + "_";
        ui.font.draw(ui.batch, inputText, overlayX + 30, inputBoxY + 28);
        
        // Feedback message
        if (!feedbackMessage.isEmpty()) {
            ui.font.setColor(feedbackColor);
            ui.layout.setText(ui.font, feedbackMessage);
            float feedbackX = overlayX + (overlayWidth - ui.layout.width) / 2;
            ui.font.draw(ui.batch, feedbackMessage, feedbackX, inputBoxY - 15);
        }
        
        // Controls hint
        ui.font.setColor(new Color(0.5f, 0.5f, 0.5f, 1.0f));
        ui.font.draw(ui.batch, "[Type answer, ENTER to submit, ESC to cancel]", 
            overlayX + 20, overlayY + 25);
        
        ui.batch.end();
    }
    
    public void dispose() {
        ui.dispose();
    }
}
