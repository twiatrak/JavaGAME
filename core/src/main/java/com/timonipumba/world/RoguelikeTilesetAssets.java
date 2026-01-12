package com.timonipumba.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

// Copies tileset and image next to TMX for local resolution
final class RoguelikeTilesetAssets {

    private RoguelikeTilesetAssets() {}

    static void copyToMapDir(FileHandle mapDir) {
        if (mapDir == null) return;

        FileHandle tilesetsDir = mapDir.child("tilesets");
        FileHandle imgDir = tilesetsDir.child("img");
        tilesetsDir.mkdirs();
        imgDir.mkdirs();

        FileHandle srcTsx = Gdx.files.internal("tilesets/roguelikeSheet_magenta.tsx");
        FileHandle dstTsx = tilesetsDir.child("roguelikeSheet_magenta.tsx");
        if (srcTsx.exists()) {
            dstTsx.writeBytes(srcTsx.readBytes(), false);
        }

        FileHandle srcPng = Gdx.files.internal("tilesets/img/roguelikeSheet_magenta.png");
        FileHandle dstPng = imgDir.child("roguelikeSheet_magenta.png");
        if (srcPng.exists()) {
            dstPng.writeBytes(srcPng.readBytes(), false);
        }
    }
}
