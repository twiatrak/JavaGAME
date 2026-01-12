package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ObjectMap;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Register Allocation riddle: assign a limited set of registers to nodes so adjacent nodes differ.
 *
 * Interaction model:
 * - Nodes are represented by socket entities with RegisterNodeComponent.
 * - Player presses E/SPACE near a node to cycle through available register tokens they own.
 * - Assignment is rejected if it would match any already-assigned neighbor.
 * - When all nodes are assigned and valid, a win-trigger socket is activated to open a door group.
 */
public class RegisterAllocationSystem extends EntitySystem {

    public static final String PUZZLE_TYPE = "register_allocation";

    // Register tokens (owned via PlayerInventoryComponent)
    public static final String REG_R1 = "reg_R1";
    public static final String REG_R2 = "reg_R2";
    public static final String REG_R3 = "reg_R3";
    public static final String REG_R4 = "reg_R4";

    private final GameStateManager gameStateManager;

    private final ComponentMapper<RegisterNodeComponent> nodeMapper = ComponentMapper.getFor(RegisterNodeComponent.class);
    private final ComponentMapper<SocketComponent> socketMapper = ComponentMapper.getFor(SocketComponent.class);
    private final ComponentMapper<RenderableComponent> renderableMapper = ComponentMapper.getFor(RenderableComponent.class);

    private ImmutableArray<Entity> nodeSockets;

    private final ObjectMap<String, Entity> byNodeId = new ObjectMap<>();

    private boolean solved = false;
    private boolean winTriggered = false;

    public RegisterAllocationSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        nodeSockets = engine.getEntitiesFor(Family.all(RegisterNodeComponent.class, SocketComponent.class).get());
        rebuildIndex();
    }

    @Override
    public void update(float deltaTime) {
        if (!gameStateManager.isActiveGameplay()) return;

        // Keep index fresh if entities changed.
        // (Ashley doesn't give an easy change signal here; rebuild is cheap at our scale.)
        rebuildIndex();

        solved = isSolved();
        if (solved && !winTriggered) {
            winTriggered = true;
            activateWinTriggers();
        }
    }

    /**
     * Attempt to interact with a socket. Returns true if it was a register-allocation node (handled).
     */
    public boolean tryInteract(Entity player, Entity socketEntity) {
        RegisterNodeComponent node = nodeMapper.get(socketEntity);
        if (node == null) return false;
        if (node.winTrigger) return true; // ignore direct interaction

        // Simple fixed cycle: Red -> Green -> Blue -> Red ...
        // No inventory and NO auto-skip: the player must satisfy constraints.
        String current = node.assignedRegisterTokenId;
        String chosen;
        if (current == null) {
            chosen = REG_R1;
        } else if (REG_R1.equals(current)) {
            chosen = REG_R2;
        } else if (REG_R2.equals(current)) {
            chosen = REG_R3;
        } else {
            chosen = REG_R1;
        }

        node.assignedRegisterTokenId = chosen;
        SocketComponent socket = socketMapper.get(socketEntity);
        if (socket != null) {
            socket.activated = true;
        }

        applyNodeVisual(socketEntity, chosen);
        return true;
    }

    public int getAssignedCount() {
        int count = 0;
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node != null && !node.winTrigger && node.assignedRegisterTokenId != null) count++;
        }
        return count;
    }

    public int getNodeCount() {
        int total = 0;
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node != null && !node.winTrigger) total++;
        }
        return total;
    }

    public boolean isSolvedPublic() {
        return solved;
    }

    private void rebuildIndex() {
        byNodeId.clear();
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node != null && node.nodeId != null && !node.nodeId.isEmpty()) {
                byNodeId.put(node.nodeId, e);
            }
        }
    }

    private boolean conflicts(RegisterNodeComponent node, String candidate) {
        String csv = node.neighborsCsv;
        if (csv == null || csv.trim().isEmpty()) return false;

        String[] parts = csv.split(",");
        for (String raw : parts) {
            String neighborId = raw.trim();
            if (neighborId.isEmpty()) continue;

            Entity neighborEntity = byNodeId.get(neighborId);
            if (neighborEntity == null) continue;
            RegisterNodeComponent neighbor = nodeMapper.get(neighborEntity);
            if (neighbor == null) continue;

            if (candidate.equals(neighbor.assignedRegisterTokenId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSolved() {
        // All non-win nodes assigned and no conflicts.
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            if (node.assignedRegisterTokenId == null) return false;
        }

        // Check edges both directions (safe even if listed once).
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node == null || node.winTrigger) continue;
            String assigned = node.assignedRegisterTokenId;
            if (assigned == null) return false;
            if (conflicts(node, assigned)) return false;
        }
        return true;
    }

    private void activateWinTriggers() {
        for (Entity e : nodeSockets) {
            RegisterNodeComponent node = nodeMapper.get(e);
            if (node == null || !node.winTrigger) continue;
            SocketComponent socket = socketMapper.get(e);
            if (socket != null) {
                socket.activated = true;
            }
            if (node.unlockDoorGroup != null && !node.unlockDoorGroup.isEmpty()) {
                // Ensure door group matches.
                if (socket != null) {
                    socket.doorGroup = node.unlockDoorGroup;
                }
            }
        }
    }

    private void applyNodeVisual(Entity socketEntity, String registerTokenId) {
        RenderableComponent renderable = renderableMapper.get(socketEntity);
        if (renderable == null) return;

        // Use obvious primary colors for register assignment feedback.
        // This avoids extra UI and makes conflicts visible.
        if (REG_R1.equals(registerTokenId)) {
            renderable.color = new Color(Color.RED);
        } else if (REG_R2.equals(registerTokenId)) {
            renderable.color = new Color(Color.GREEN);
        } else if (REG_R3.equals(registerTokenId)) {
            renderable.color = new Color(Color.BLUE);
        } else {
            renderable.color = new Color(Color.WHITE);
        }
        renderable.color.a = 1f;
    }
}
