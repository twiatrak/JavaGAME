package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

public class CollisionComponent implements Component {
    public float width = 16;
    public float height = 16;
    
    public CollisionComponent() {}
    
    public CollisionComponent(float width, float height) {
        this.width = width;
        this.height = height;
    }
}
