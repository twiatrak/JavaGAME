package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.level.Puzzle;

/**
 * Component for doors that require solving a puzzle to unlock.
 * 
 * Unlike DoorComponent (which is switch-controlled), PuzzleDoorComponent
 * represents doors that are locked until the player solves an associated
 * cipher puzzle by interacting with the door.
 * 
 * When the player presses the interact key (E or Space) near a locked puzzle door,
 * a puzzle overlay appears. Solving the puzzle unlocks the door.
 * 
 * For finale puzzles (isFinale=true), solving the puzzle triggers level completion
 * (LEVEL_CLEAR) instead of opening gates. This is used for the final puzzle arena
 * where there are no outgoing gates.
 */
public class PuzzleDoorComponent implements Component {
    
    /** Unique identifier for this door */
    public String id = "";
    
    /** The puzzle associated with this door */
    public Puzzle puzzle;
    
    /** Whether the door is currently locked */
    public boolean locked = true;
    
    /** Original collision width (stored for unlock behavior) */
    public float originalWidth = 0f;
    
    /** Original collision height (stored for unlock behavior) */
    public float originalHeight = 0f;
    
    /** Flag indicating whether original dimensions have been stored */
    public boolean dimensionsStored = false;
    
    /** 
     * Whether this puzzle is the finale that ends the level when solved.
     * When true, solving this puzzle triggers LEVEL_CLEAR instead of opening gates.
     */
    public boolean isFinale = false;
    
    public PuzzleDoorComponent() {}
    
    public PuzzleDoorComponent(String id, Puzzle puzzle) {
        this.id = id;
        this.puzzle = puzzle;
        this.locked = true;
    }
    
    public PuzzleDoorComponent(String id, Puzzle puzzle, boolean locked) {
        this.id = id;
        this.puzzle = puzzle;
        this.locked = locked;
    }
    
    public PuzzleDoorComponent(String id, Puzzle puzzle, boolean locked, boolean isFinale) {
        this.id = id;
        this.puzzle = puzzle;
        this.locked = locked;
        this.isFinale = isFinale;
    }
    
    /**
     * Unlock the door.
     */
    public void unlock() {
        this.locked = false;
    }
    
    /**
     * Lock the door.
     */
    public void lock() {
        this.locked = true;
    }
    
    /**
     * Check if the door has an associated puzzle.
     * @return true if puzzle is not null
     */
    public boolean hasPuzzle() {
        return puzzle != null;
    }
    
    /**
     * Get the puzzle ID if available.
     * @return the puzzle ID, or null if no puzzle
     */
    public String getPuzzleId() {
        return puzzle != null ? puzzle.getPuzzleId() : null;
    }
    
    /**
     * Check if this is a finale puzzle that ends the level when solved.
     * @return true if solving this puzzle should trigger level completion
     */
    public boolean isFinale() {
        return isFinale;
    }
}
