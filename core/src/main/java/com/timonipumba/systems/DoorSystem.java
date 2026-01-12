package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;

/**
 * System that links doors to switches and manages door states.
 * 
 * Behavior:
 * - For each door, checks all switches with the same group
 * - If ANY switch in the group is ON, the door opens
 * - If ALL switches in the group are OFF, the door closes
 * - Open doors have collision disabled (width/height set to 0)
 * - Closed doors have collision restored to original dimensions
 * - Door color changes to indicate state (more transparent when open)
 * 
 * Note: Original collision dimensions are stored in DoorComponent when the door
 * is first encountered, allowing restoration when door closes.
 */
public class DoorSystem extends EntitySystem {
    
    /** Alpha value for open door transparency */
    private static final float DOOR_OPEN_ALPHA = 0.3f;
    
    /** Alpha value for closed door opacity */
    private static final float DOOR_CLOSED_ALPHA = 1.0f;
    
    private final GameStateManager gameStateManager;
    
    private ComponentMapper<DoorComponent> doorMapper;
    private ComponentMapper<SwitchComponent> switchMapper;
    private ComponentMapper<SocketComponent> socketMapper;
    private ComponentMapper<CollisionComponent> collisionMapper;
    private ComponentMapper<RenderableComponent> renderableMapper;
    
    private ImmutableArray<Entity> doors;
    private ImmutableArray<Entity> switches;
    private ImmutableArray<Entity> sockets;
    
    public DoorSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
        this.doorMapper = ComponentMapper.getFor(DoorComponent.class);
        this.switchMapper = ComponentMapper.getFor(SwitchComponent.class);
        this.socketMapper = ComponentMapper.getFor(SocketComponent.class);
        this.collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
        this.renderableMapper = ComponentMapper.getFor(RenderableComponent.class);
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        doors = engine.getEntitiesFor(
            Family.all(DoorComponent.class, PositionComponent.class).get()
        );
        switches = engine.getEntitiesFor(
            Family.all(SwitchComponent.class).get()
        );
        sockets = engine.getEntitiesFor(
            Family.all(SocketComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // Process during active gameplay states (allow door interaction after enemies defeated)
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }

        // Update timers for momentary sockets and auto-reset when expired.
        // Keeping this here avoids a separate system and ensures doors reflect current socket state.
        if (sockets != null && sockets.size() > 0) {
            for (Entity socketEntity : sockets) {
                SocketComponent socketComp = socketMapper.get(socketEntity);
                if (socketComp == null) continue;

                if (socketComp.momentary && socketComp.activated) {
                    socketComp.momentaryTimerSeconds -= deltaTime;
                    if (socketComp.momentaryTimerSeconds <= 0f) {
                        socketComp.activated = false;
                        socketComp.momentaryTimerSeconds = 0f;
                    }
                }
            }
        }
        
        // Update each door based on switch states
        for (Entity doorEntity : doors) {
            updateDoor(doorEntity);
        }
    }
    
    /**
     * Update a door's state based on associated switches.
     */
    private void updateDoor(Entity doorEntity) {
        DoorComponent door = doorMapper.get(doorEntity);
        CollisionComponent collision = collisionMapper.get(doorEntity);
        RenderableComponent renderable = renderableMapper.get(doorEntity);
        
        if (door == null) return;
        
        // Store original dimensions if not already stored (using flag to handle zero dimensions)
        if (collision != null && !door.dimensionsStored) {
            door.originalWidth = collision.width;
            door.originalHeight = collision.height;
            door.dimensionsStored = true;
        }
        
        // Check if any trigger in the same group is active
        boolean shouldBeOpen = isAnyTriggerOn(door.group);
        
        // Only update if state changed
        if (door.open != shouldBeOpen) {
            door.open = shouldBeOpen;
            
            if (collision != null) {
                if (door.open) {
                    // Open door: disable collision
                    collision.width = 0;
                    collision.height = 0;
                } else {
                    // Close door: restore collision
                    collision.width = door.originalWidth;
                    collision.height = door.originalHeight;
                }
            }
            
            // Update visual representation (modify alpha directly to avoid object allocation)
            if (renderable != null) {
                renderable.color.a = door.open ? DOOR_OPEN_ALPHA : DOOR_CLOSED_ALPHA;
            }
        }
    }
    
    /**
     * Check if any switch with the given group is currently on.
     */
    private boolean isAnyTriggerOn(String group) {
        if (group == null) return false;

        for (Entity switchEntity : switches) {
            SwitchComponent switchComp = switchMapper.get(switchEntity);
            if (switchComp != null && group.equals(switchComp.group) && switchComp.on) {
                return true;
            }
        }

        for (Entity socketEntity : sockets) {
            SocketComponent socketComp = socketMapper.get(socketEntity);
            if (socketComp != null && socketComp.activated && group.equals(socketComp.doorGroup)) {
                return true;
            }
        }

        return false;
    }
}
