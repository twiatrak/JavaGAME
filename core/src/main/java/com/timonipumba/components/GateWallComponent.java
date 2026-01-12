package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/**
 * Marker component for wall entities that correspond to gate tiles (ID 212)
 * in the Tiled 'walls' layer.
 *
 * This lets GateSystem or other systems distinguish gate walls from regular
 * solid walls and remove them when gates open.
 */
public class GateWallComponent implements Component {
    // No fields needed – presence of this component is enough.
}
