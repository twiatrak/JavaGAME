package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Optional component for gates that are controlled by socket/switch groups
 * (riddle gates) instead of arena enemy-clear logic.
 */
public class GateDoorComponent implements Component {
    /** Trigger group name (matches socket.doorGroup / door.group style). */
    public String group;

    public GateDoorComponent() {
    }

    public GateDoorComponent(String group) {
        this.group = group;
    }
}
