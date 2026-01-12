package com.timonipumba.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;

/**
 * Small helper for modal overlay UIs: owns common rendering resources and draws the standard
 * boxed panel background.
 */
public final class OverlayUi implements Disposable {

    public final SpriteBatch batch;
    public final ShapeRenderer shapes;
    public final BitmapFont font;
    public final BitmapFont titleFont;
    public final GlyphLayout layout;

    public OverlayUi(float fontScale, float titleFontScale) {
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont();
        this.titleFont = new BitmapFont();
        this.layout = new GlyphLayout();

        font.getData().setScale(fontScale);
        titleFont.getData().setScale(titleFontScale);
    }

    public void drawPanel(int x, int y, int w, int h, Color fill, Color border) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(fill);
        shapes.rect(x, y, w, h);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(border);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    public void drawPanel(float x, float y, float w, float h, Color fill, Color border) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(fill);
        shapes.rect(x, y, w, h);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(border);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        titleFont.dispose();
    }
}
