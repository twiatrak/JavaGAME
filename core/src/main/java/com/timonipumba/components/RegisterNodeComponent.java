package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Node assignment state for the Register Allocation (graph coloring) riddle.
 * Attached to socket entities.
 */
public class RegisterNodeComponent implements Component {
    /** Unique node id, e.g. var_0. */
    public String nodeId;

    /** Comma-separated neighbor node ids. */
    public String neighborsCsv;

    /** Assigned register token id (e.g. reg_R1) or null if unassigned. */
    public String assignedRegisterTokenId;

    /** If true, this socket is the win trigger; it is not interacted with directly. */
    public boolean winTrigger;

    /** Door group to unlock when the puzzle is solved (win trigger only). */
    public String unlockDoorGroup;

    public RegisterNodeComponent() {
    }

    public RegisterNodeComponent(String nodeId, String neighborsCsv) {
        this.nodeId = nodeId;
        this.neighborsCsv = neighborsCsv;
    }
}
