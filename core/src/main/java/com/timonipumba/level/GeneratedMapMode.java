package com.timonipumba.level;

/**
 * Identifies a procedurally generated TMX "riddle" level.
 *
 * This enum is deliberately small and stable: the campaign is defined as an ordered
 * list of these stages.
 */
public enum GeneratedMapMode {
    TRAVERSAL_RIDDLE,
    REGISTER_ALLOCATION_RIDDLE,
    LIGHTS_OUT_RIDDLE,
    ALGEBRA_FORGE_RIDDLE
}
