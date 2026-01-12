package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Node metadata for the Graph Lights-Out riddle.
 *
 * Story-facing interpretation: a lantern you can tap.
 * Gameplay rule: tapping a lantern flips its state and the state of its neighbors.
 */
public class LightsOutNodeComponent implements Component {

    /** Puzzle node ID (must be unique within the map). */
    public String nodeId;

    /** Comma-separated list of neighbor node IDs. */
    public String neighborsCsv;

    /** Current lantern state (true = lit/happy, false = unlit). */
    public boolean on;

    /** Internal trigger node used to open a door group when solved. */
    public boolean winTrigger;

    /** Optional door group to force on win-trigger sockets. */
    public String unlockDoorGroup;

    public LightsOutNodeComponent() {
    }

    public LightsOutNodeComponent(String nodeId, String neighborsCsv, boolean on) {
        this.nodeId = nodeId;
        this.neighborsCsv = neighborsCsv;
        this.on = on;
    }
}
