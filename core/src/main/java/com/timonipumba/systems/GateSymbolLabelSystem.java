package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.timonipumba.GameConstants;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.CollisionComponent;
import com.timonipumba.components.GateComponent;
import com.timonipumba.components.PositionComponent;

/**
 * Draws an in-world label above each gate indicating which traversal symbol it will append.
 *
 * Current alphabet: {0, 1}
 * - 0: lane symbol (previously shown as H)
 * - 1: lane symbol (previously shown as V)
 */
public class GateSymbolLabelSystem extends EntitySystem {

    private final GameStateManager gameStateManager;
    private final GateSystem gateSystem;
    private final OrthographicCamera camera;

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout;

    private ImmutableArray<Entity> gates;

    private final ComponentMapper<GateComponent> gateMapper = ComponentMapper.getFor(GateComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<CollisionComponent> collisionMapper = ComponentMapper.getFor(CollisionComponent.class);

    public GateSymbolLabelSystem(GameStateManager gameStateManager, GateSystem gateSystem, OrthographicCamera camera) {
        super(200); // draw after most gameplay systems and rendering
        this.gameStateManager = gameStateManager;
        this.gateSystem = gateSystem;
        this.camera = camera;

        this.batch = new SpriteBatch();
        this.font = new BitmapFont();
        this.layout = new GlyphLayout();

        font.getData().setScale(1.1f);
        font.setColor(Color.WHITE);
    }

    @Override
    public void addedToEngine(Engine engine) {
        // TMX-loaded gates do NOT have RenderableComponent; they are represented by tiles in the map layers.
        gates = engine.getEntitiesFor(Family.all(GateComponent.class, PositionComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null || gateSystem == null || camera == null || gates == null) {
            return;
        }
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (int i = 0; i < gates.size(); i++) {
            Entity gateEntity = gates.get(i);
            GateComponent gate = gateMapper.get(gateEntity);
            if (gate == null || gate.sourceArenaId == null || gate.targetArenaId == null) {
                continue;
            }

            char symbol;
            if (gate.traversalSymbol != null && !gate.traversalSymbol.isEmpty()) {
                symbol = Character.toUpperCase(gate.traversalSymbol.charAt(0));
                if (symbol != 'H' && symbol != 'V') {
                    symbol = symbolForTransition(gate.sourceArenaId, gate.targetArenaId);
                }
            } else {
                symbol = symbolForTransition(gate.sourceArenaId, gate.targetArenaId);
            }
            String text = (symbol == 'V') ? "1" : "0";

            PositionComponent pos = posMapper.get(gateEntity);
            CollisionComponent collision = collisionMapper.get(gateEntity);
            float gateWidth = (collision != null) ? collision.width : GameConstants.TILE_SIZE;
            float gateHeight = (collision != null) ? collision.height : GameConstants.TILE_SIZE;

            layout.setText(font, text);

            float textX = pos.x + (gateWidth * 0.5f) - (layout.width * 0.5f);
            float textY = pos.y + gateHeight + (GameConstants.TILE_SIZE * 0.10f) + layout.height;

            font.draw(batch, text, textX, textY);
        }

        batch.end();
    }

    private char symbolForTransition(String fromArenaId, String toArenaId) {
        float[] a = gateSystem.getArenaCenter(fromArenaId);
        float[] b = gateSystem.getArenaCenter(toArenaId);
        if (a != null && b != null) {
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            return (Math.abs(dx) >= Math.abs(dy)) ? 'H' : 'V';
        }

        // Fallback: parity of the target arena index (keeps the label deterministic even if centers are missing)
        int idx = parseArenaIndex(toArenaId);
        return (idx % 2 == 0) ? 'H' : 'V';
    }

    private int parseArenaIndex(String arenaId) {
        // Expected: arena_<number>
        try {
            int underscore = arenaId.lastIndexOf('_');
            if (underscore >= 0 && underscore + 1 < arenaId.length()) {
                return Integer.parseInt(arenaId.substring(underscore + 1));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
