package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for gates that open when all enemies in an arena are defeated.
 * 
 * Gates provide a visual and physical barrier between arenas. When all enemies
 * in the current arena are defeated, the gate transitions from CLOSED to OPEN,
 * allowing the player to walk through to the next arena without teleportation.
 * 
 * <h3>Gate States:</h3>
 * <ul>
 *   <li>CLOSED - Gate is impassable, collision enabled, shows closed sprite</li>
 *   <li>OPENING - Transitional state for animation (optional), brief animation plays</li>
 *   <li>OPEN - Gate is passable, collision disabled, shows open sprite</li>
 * </ul>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * Entity gate = engine.createEntity();
 * gate.add(new PositionComponent(x, y));
 * gate.add(new CollisionComponent(width, height));
 * gate.add(new RenderableComponent(width, height, Color.BROWN));
 * gate.add(new GateComponent("arena_1", "arena_2"));
 * engine.addEntity(gate);
 * }</pre>
 * 
 * @see com.timonipumba.systems.GateSystem for gate state management
 */
public class GateComponent implements Component {
    
    /**
     * Gate states representing the lifecycle of a gate.
     */
    public enum GateState {
        /** Gate is closed - blocks player movement */
        CLOSED,
        /** Gate is opening - transitional animation state (optional) */
        OPENING,
        /** Gate is open - allows player passage */
        OPEN
    }
    
    /** Current state of the gate */
    public GateState state = GateState.CLOSED;
    
    /** Arena ID this gate exits from (source arena) */
    public String sourceArenaId;
    
    /** Arena ID this gate leads to (target arena) */
    public String targetArenaId;

    /**
     * Optional traversal symbol associated with this gate.
     * Used by generated TMX mode to label multiple lane-choices per connection.
     * Expected values: "H" or "V" (but any single-letter symbol is supported).
     */
    public String traversalSymbol;
    
    /** Original collision width before gate opened */
    public float originalWidth = 0f;
    
    /** Original collision height before gate opened */
    public float originalHeight = 0f;
    
    /** Flag indicating whether original dimensions have been stored */
    public boolean dimensionsStored = false;
    
    /** Duration of the OPENING animation in seconds (0 for instant) */
    public float openingDuration = 0.5f;
    
    /** Timer for the OPENING state animation */
    public float openingTimer = 0f;
    
    /** Render order / z-index for the gate (above floor, below characters) */
    public int renderOrder = 5;
    
    public GateComponent() {}
    
    /**
     * Create a gate component linking two arenas.
     * 
     * @param sourceArenaId The arena this gate exits from
     * @param targetArenaId The arena this gate leads to
     */
    public GateComponent(String sourceArenaId, String targetArenaId) {
        this.sourceArenaId = sourceArenaId;
        this.targetArenaId = targetArenaId;
    }
    
    /**
     * Create a gate component with a single arena reference.
     * Useful when the gate just needs to open after clearing an arena.
     * 
     * @param arenaId The arena that must be cleared to open this gate
     */
    public GateComponent(String arenaId) {
        this.sourceArenaId = arenaId;
        this.targetArenaId = null;
    }
    
    /**
     * Check if the gate is currently closed (blocking movement).
     * 
     * @return true if state is CLOSED
     */
    public boolean isClosed() {
        return state == GateState.CLOSED;
    }
    
    /**
     * Check if the gate is currently opening (animation in progress).
     * 
     * @return true if state is OPENING
     */
    public boolean isOpening() {
        return state == GateState.OPENING;
    }
    
    /**
     * Check if the gate is fully open (passable).
     * 
     * @return true if state is OPEN
     */
    public boolean isOpen() {
        return state == GateState.OPEN;
    }
    
    /**
     * Begin opening the gate.
     * Transitions from CLOSED to OPENING state.
     */
    public void startOpening() {
        if (state == GateState.CLOSED) {
            state = GateState.OPENING;
            openingTimer = 0f;
        }
    }
    
    /**
     * Complete opening the gate.
     * Transitions to OPEN state.
     */
    public void completeOpening() {
        state = GateState.OPEN;
    }
    
    /**
     * Instantly open the gate (skip OPENING animation).
     */
    public void openInstantly() {
        state = GateState.OPEN;
    }
    
    /**
     * Close the gate.
     * Transitions to CLOSED state.
     */
    public void close() {
        state = GateState.CLOSED;
        openingTimer = 0f;
    }
}
