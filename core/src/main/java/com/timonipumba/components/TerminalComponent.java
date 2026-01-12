package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for terminal interactables in the game world.
 * 
 * <p><strong>Recommended:</strong> This is the preferred method for door interaction
 * in the new terminal-based UX. Terminals provide a consistent puzzle flow with
 * auto-advance behavior when puzzles are solved.</p>
 * 
 * <p>Terminals are placed near doors and serve as interaction points.
 * When the player is within proximity and presses SPACE or E, the associated
 * puzzle door opens its puzzle overlay. Upon solving the puzzle, the door
 * automatically unlocks and the player can proceed.</p>
 * 
 * <h3>Advantages over legacy switches:</h3>
 * <ul>
 *   <li>Puzzle-based interaction for engaging gameplay</li>
 *   <li>Auto-advance flow after puzzle completion</li>
 *   <li>Clear visual feedback through puzzle overlay</li>
 *   <li>Support for various puzzle types (cipher, binary, etc.)</li>
 * </ul>
 * 
 * <h3>Tiled object properties:</h3>
 * <ul>
 *   <li>type="terminal" (required)</li>
 *   <li>doorId: String, ID of the puzzle door this terminal controls (required)</li>
 * </ul>
 * 
 * <h3>Example Tiled object:</h3>
 * <pre>{@code
 * <object type="terminal">
 *   <properties>
 *     <property name="doorId" value="cipher_door"/>
 *   </properties>
 * </object>
 * }</pre>
 * 
 * <h3>Setup Requirements:</h3>
 * <ol>
 *   <li>Create a puzzle door with type="puzzledoor" and matching id</li>
 *   <li>Configure the puzzle (inline in TMX or via PuzzleRegistry)</li>
 *   <li>Place the terminal near the door for player interaction</li>
 * </ol>
 * 
 * @see PuzzleDoorComponent for the doors controlled by terminals
 * @see com.timonipumba.systems.PuzzleOverlaySystem for puzzle handling
 */
public class TerminalComponent implements Component {
    
    /** The ID of the puzzle door this terminal controls */
    public String doorId = "";
    
    /**
     * If true, the terminal can be interacted with even if its linked PuzzleDoor is currently hidden.
     * This is useful for "virtual" or hidden doors used only to drive a puzzle overlay.
     */
    public boolean allowHiddenDoor = false;
    
    public TerminalComponent() {}
    
    public TerminalComponent(String doorId) {
        this.doorId = (doorId == null) ? "" : doorId;
    }
}
