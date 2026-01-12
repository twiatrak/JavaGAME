package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.LightsOutNodeComponent;
import com.timonipumba.components.RenderableComponent;
import com.timonipumba.components.SocketComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Graph Lights-Out riddle.
 *
 * Player-facing: tap lanterns; lanterns and their buddies flip between lit/unlit.
 * Win condition: all non-trigger lanterns are lit.
 */
public class GraphLightsOutSystem extends EntitySystem {

    public static final String PUZZLE_TYPE = "lights_out";

    private final GameStateManager gameStateManager;

    private final ComponentMapper<LightsOutNodeComponent> nodeMapper = ComponentMapper.getFor(LightsOutNodeComponent.class);
    private final ComponentMapper<RenderableComponent> renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
    private final ComponentMapper<SocketComponent> socketMapper = ComponentMapper.getFor(SocketComponent.class);

    private ImmutableArray<Entity> nodes;

    private final Map<String, Entity> byNodeId = new HashMap<>();
    private int cachedNodeCount = -1;

    private boolean solved = false;
    private boolean winTriggered = false;

    public GraphLightsOutSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        nodes = engine.getEntitiesFor(Family.all(LightsOutNodeComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (gameStateManager == null || !gameStateManager.isActiveGameplay()) {
            return;
        }
        if (nodes == null || nodes.size() == 0) {
            return;
        }

        solved = isSolved();
        if (solved && !winTriggered) {
            winTriggered = true;
            activateWinTriggers();
        }
    }

    /**
     * Attempt to interact with a socket. Returns true if this riddle consumed the interaction.
     */
    public boolean tryInteract(Entity player, Entity socketEntity) {
        if (socketEntity == null) return false;

        LightsOutNodeComponent node = nodeMapper.get(socketEntity);
        if (node == null) return false;
        if (node.winTrigger) return true; // ignore direct interaction

        rebuildIndexIfNeeded();

        toggle(socketEntity);
        return true;
    }

    public boolean isSolvedPublic() {
        return solved;
    }

    public int getNodeCount() {
        if (nodes == null) return 0;
        int total = 0;
        for (Entity e : nodes) {
            LightsOutNodeComponent n = nodeMapper.get(e);
            if (n != null && !n.winTrigger) total++;
        }
        return total;
    }

    private void rebuildIndexIfNeeded() {
        int n = nodes != null ? nodes.size() : 0;
        if (n == cachedNodeCount && !byNodeId.isEmpty()) {
            return;
        }
        cachedNodeCount = n;
        byNodeId.clear();

        if (nodes == null) return;
        for (Entity e : nodes) {
            LightsOutNodeComponent node = nodeMapper.get(e);
            if (node == null) continue;
            if (node.nodeId == null || node.nodeId.isEmpty()) continue;
            byNodeId.put(node.nodeId, e);
        }
    }

    private void toggle(Entity nodeEntity) {
        LightsOutNodeComponent node = nodeMapper.get(nodeEntity);
        if (node == null) return;

        // Flip self.
        flip(nodeEntity);

        // Flip neighbors.
        Set<String> neighbors = parseNeighbors(node.neighborsCsv);
        for (String nbId : neighbors) {
            Entity nb = byNodeId.get(nbId);
            if (nb == null) continue;
            LightsOutNodeComponent nbNode = nodeMapper.get(nb);
            if (nbNode == null || nbNode.winTrigger) continue;
            flip(nb);
        }
    }

    private void flip(Entity e) {
        LightsOutNodeComponent n = nodeMapper.get(e);
        if (n == null) return;
        n.on = !n.on;
        applyVisual(e, n.on);
    }

    private void applyVisual(Entity e, boolean on) {
        RenderableComponent r = renderableMapper.get(e);
        if (r == null) return;

        // Bright lantern vs dim lantern.
        if (on) {
            r.color = new Color(1f, 0.95f, 0.35f, 1f);
        } else {
            r.color = new Color(0.25f, 0.25f, 0.30f, 1f);
        }
    }

    private boolean isSolved() {
        if (nodes == null) return false;
        for (Entity e : nodes) {
            LightsOutNodeComponent node = nodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (!node.on) return false;
        }
        return true;
    }

    private void activateWinTriggers() {
        if (nodes == null) return;
        for (Entity e : nodes) {
            LightsOutNodeComponent node = nodeMapper.get(e);
            if (node == null || !node.winTrigger) continue;

            SocketComponent socket = socketMapper.get(e);
            if (socket != null) {
                socket.activated = true;
                if (node.unlockDoorGroup != null && !node.unlockDoorGroup.isEmpty()) {
                    socket.doorGroup = node.unlockDoorGroup;
                }
            }
        }
    }

    private Set<String> parseNeighbors(String csv) {
        HashSet<String> out = new HashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        String[] parts = csv.split(",");
        for (String raw : parts) {
            if (raw == null) continue;
            String s = raw.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
