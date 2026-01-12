package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

public class PositionComponent implements Component {
    public float x = 0;
    public float y = 0;
    
    public PositionComponent() {}
    
    public PositionComponent(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
