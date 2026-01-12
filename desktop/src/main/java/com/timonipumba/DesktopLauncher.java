package com.timonipumba;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Timonipumba - Tiled (TMX) Mode");
        config.setWindowedMode(800, 600);
        config.setForegroundFPS(60);
        config.useVsync(true);

        // Default to TMX-driven 4-stage campaign (Register Allocation -> Lights Out -> Algebra Forge -> Traversal).
        new Lwjgl3Application(new TiledMapGame(), config);
    }
}
