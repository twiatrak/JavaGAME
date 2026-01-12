package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.timonipumba.GameConstants;

/**
 * Component for interactive switches in the game world.
 * 
 * <p><strong>DEPRECATED:</strong> This component represents legacy switch-based interaction.
 * New implementations should use {@link TerminalComponent} with {@link PuzzleDoorComponent}
 * for the terminal-based interaction UX with auto-advance puzzle flow.</p>
 * 
 * <h3>Migration Guide:</h3>
 * <ol>
 *   <li>Replace switch objects in Tiled maps with type="terminal"</li>
 *   <li>Replace door objects controlled by switches with type="puzzledoor"</li>
 *   <li>Set the terminal's doorId property to reference the puzzle door's id</li>
 *   <li>Configure the puzzle door with appropriate puzzle data</li>
 * </ol>
 * 
 * <h3>Example Migration:</h3>
 * <p>Before (legacy switch + door):</p>
 * <pre>{@code
 * <object type="switch">
 *   <properties>
 *     <property name="group" value="A"/>
 *   </properties>
 * </object>
 * <object type="door">
 *   <properties>
 *     <property name="group" value="A"/>
 *   </properties>
 * </object>
 * }</pre>
 * 
 * <p>After (terminal + puzzle door):</p>
 * <pre>{@code
 * <object type="terminal">
 *   <properties>
 *     <property name="doorId" value="my_door"/>
 *   </properties>
 * </object>
 * <object type="puzzledoor">
 *   <properties>
 *     <property name="id" value="my_door"/>
 *     <property name="puzzleId" value="my_puzzle"/>
 *   </properties>
 * </object>
 * }</pre>
 * 
 * <p>Legacy switches will continue to work, but they should be migrated.</p>
 * 
 * @deprecated Use {@link TerminalComponent} with {@link PuzzleDoorComponent} instead.
 * @see TerminalComponent
 * @see PuzzleDoorComponent
 */
@Deprecated
public class SwitchComponent implements Component {
    
    /** The group identifier linking this switch to doors */
    public String group = "";
    
    /** Whether the switch is currently on/activated */
    public boolean on = false;
    
    /** If true, the switch auto-resets after timer expires */
    public boolean momentary = false;
    
    /** Time remaining before momentary switch auto-resets (seconds) */
    public float timer = 0f;
    
    public SwitchComponent() {}
    
    public SwitchComponent(String group) {
        this.group = group;
    }
    
    public SwitchComponent(String group, boolean momentary) {
        this.group = group;
        this.momentary = momentary;
    }
    
    /**
     * Toggle the switch state.
     * If momentary, starts the auto-reset timer when turned on,
     * or clears the timer when turned off.
     */
    public void toggle() {
        on = !on;
        if (on && momentary) {
            timer = GameConstants.SWITCH_MOMENTARY_DURATION;
        } else if (!on) {
            timer = 0;
        }
    }
    
    /**
     * Update the timer for momentary switches.
     * @param deltaTime time elapsed since last update
     * @return true if the switch was auto-reset this frame
     */
    public boolean updateTimer(float deltaTime) {
        if (on && momentary && timer > 0) {
            timer -= deltaTime;
            if (timer <= 0) {
                on = false;
                timer = 0;
                return true;
            }
        }
        return false;
    }
}
