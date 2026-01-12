package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Marks a terminal as part of the Algebra Forge riddle.
 *
 * terminalType (TMX property): "forge" or "oracle"
 * opId (TMX property): identifies which hidden algebra/operation mapping to use
 * charges (TMX property, oracle only): number of free queries
 */
public class AlgebraTerminalComponent implements Component {

    public enum Kind {
        FORGE,
        ORACLE
    }

    public Kind kind = Kind.FORGE;

    /** Identifies which algebra instance to use (allows multiple independent labs). */
    public String opId = "";

    /** For oracle terminals: remaining queries. Ignored for forge terminals. */
    public int charges = 0;

    public AlgebraTerminalComponent() {}

    public AlgebraTerminalComponent(Kind kind, String opId, int charges) {
        this.kind = kind;
        this.opId = opId != null ? opId : "";
        this.charges = charges;
    }
}
