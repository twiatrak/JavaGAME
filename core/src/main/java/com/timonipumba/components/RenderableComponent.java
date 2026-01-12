package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class RenderableComponent implements Component {
    public float width = 16;
    public float height = 16;
    // Color is mutable. We keep a caller-provided instance to allow systems/validators
    // to mutate it in place, but we defensively copy libGDX static Color constants.
    public Color color = new Color(Color.WHITE);
    
    public RenderableComponent() {}
    
    public RenderableComponent(float width, float height, Color color) {
        this.width = width;
        this.height = height;
        this.color = normalizeColorReference(color);
    }

    private static Color normalizeColorReference(Color input) {
        if (input == null) {
            return new Color(Color.WHITE);
        }

        // If the caller passes one of libGDX's static Color constants (e.g. Color.BROWN),
        // we must copy it; otherwise any in-place alpha changes would mutate the constant.
        if (isLibgdxStaticColorConstant(input)) {
            return new Color(input);
        }

        // For non-constant colors, keep the reference so external code can observe mutations.
        return input;
    }

    private static boolean isLibgdxStaticColorConstant(Color input) {
        try {
            for (Field field : Color.class.getFields()) {
                int mods = field.getModifiers();
                if (!Modifier.isStatic(mods)) continue;
                if (!Modifier.isPublic(mods)) continue;
                if (!Modifier.isFinal(mods)) continue;
                if (field.getType() != Color.class) continue;

                Object value = field.get(null);
                if (value == input) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Best-effort defensive copy; if reflection fails, treat as non-constant.
        }
        return false;
    }
}
