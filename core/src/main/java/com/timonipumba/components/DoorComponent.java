package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Component for doors that can be opened/closed by switches.
 * 
 * <p><strong>Note:</strong> This component is for legacy switch-controlled doors.
 * For new implementations, prefer {@link PuzzleDoorComponent} with {@link TerminalComponent}
 * which provides terminal-based interaction with auto-advance puzzle flow.</p>
 * 
 * <p>Doors are linked to switches via a group identifier. When any switch with
 * the same group is activated, the door opens (collision disabled).
 * When no switches in the group are active, the door closes (collision enabled).</p>
 * 
 * <h3>Tiled object properties:</h3>
 * <ul>
 *   <li>type="door" (required)</li>
 *   <li>group: String identifying which switches control this door (required)</li>
 *   <li>open: boolean, initial state of the door (optional, default false)</li>
 * </ul>
 * 
 * <h3>Example Tiled object:</h3>
 * <pre>{@code
 * <object type="door">
 *   <properties>
 *     <property name="group" value="A"/>
 *     <property name="open" value="false"/>
 *   </properties>
 * </object>
 * }</pre>
 * 
 * <h3>Visual representation:</h3>
 * <ul>
 *   <li>Closed doors are rendered with their configured color (e.g., BROWN)</li>
 *   <li>Open doors are rendered with transparency or a different color</li>
 * </ul>
 * 
 * <h3>Collision behavior:</h3>
 * <ul>
 *   <li>When open=false: door blocks movement (CollisionComponent active)</li>
 *   <li>When open=true: door allows passage (CollisionComponent dimensions set to 0)</li>
 * </ul>
 * 
 * @see PuzzleDoorComponent for puzzle-based door control (recommended)
 * @see SwitchComponent for the switch that controls these doors (deprecated)
 */
public class DoorComponent implements Component {
    
    /** The group identifier linking this door to switches */
    public String group = "";
    
    /** Whether the door is currently open */
    public boolean open = false;
    
    /** Original collision width (stored when door is first encountered) */
    public float originalWidth = 0f;
    
    /** Original collision height (stored when door is first encountered) */
    public float originalHeight = 0f;
    
    /** Flag indicating whether original dimensions have been stored */
    public boolean dimensionsStored = false;
    
    public DoorComponent() {}
    
    public DoorComponent(String group) {
        this.group = group;
    }
    
    public DoorComponent(String group, boolean open) {
        this.group = group;
        this.open = open;
    }
}
