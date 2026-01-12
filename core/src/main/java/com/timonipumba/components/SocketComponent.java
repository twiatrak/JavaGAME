package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * A placeable/activatable socket that can be triggered by possessing (and optionally consuming)
 * a required token. When activated, it can open legacy doors via a shared group.
 */
public class SocketComponent implements Component {
    /** Optional: token required to activate. If null/empty, socket can always activate. */
    public String requiresTokenId;

    /** Door group to open when this socket is activated. */
    public String doorGroup;

    /** Whether activation consumes one token. Default true. */
    public boolean consumeToken = true;

    /** Whether this socket has been activated already. */
    public boolean activated = false;

    /** If true, activation auto-resets after a duration (timed socket). */
    public boolean momentary = false;

    /** Duration for momentary sockets (seconds). If <= 0, defaults to GameConstants.SWITCH_MOMENTARY_DURATION. */
    public float momentaryDurationSeconds = 0f;

    /** Remaining time while active (seconds). Used only when momentary=true. */
    public float momentaryTimerSeconds = 0f;

    public SocketComponent() {
    }

    public SocketComponent(String requiresTokenId, String doorGroup, boolean consumeToken) {
        this.requiresTokenId = requiresTokenId;
        this.doorGroup = doorGroup;
        this.consumeToken = consumeToken;
    }
}
