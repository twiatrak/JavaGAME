package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.timonipumba.GameConstants;
import com.timonipumba.GameStateManager;
import com.timonipumba.assets.SpriteLoader;
import com.timonipumba.camera.CameraModeManager;
import com.timonipumba.components.*;
import com.timonipumba.util.GlyphNames;

// handles keys n mouse. wip maybe?
public class PlayerInputSystem extends IteratingSystem {
    private ComponentMapper<PlayerComponent> pm = ComponentMapper.getFor(PlayerComponent.class);
    private ComponentMapper<VelocityComponent> vm = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<PositionComponent> posm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<CollisionComponent> cm = ComponentMapper.getFor(CollisionComponent.class);
    private ComponentMapper<RenderableComponent> rm = ComponentMapper.getFor(RenderableComponent.class);
    private ComponentMapper<PuzzleDoorComponent> puzzleDoorMapper = ComponentMapper.getFor(PuzzleDoorComponent.class);
    private ComponentMapper<TerminalComponent> terminalMapper = ComponentMapper.getFor(TerminalComponent.class);
    private ComponentMapper<AlgebraTerminalComponent> algebraTerminalMapper = ComponentMapper.getFor(AlgebraTerminalComponent.class);
    private ComponentMapper<SocketComponent> socketMapper = ComponentMapper.getFor(SocketComponent.class);
    private ComponentMapper<PlayerInventoryComponent> inventoryMapper = ComponentMapper.getFor(PlayerInventoryComponent.class);
    
    private GameStateManager gameStateManager;
    private SpriteLoader spriteLoader;
    private OrthographicCamera camera;
    private CameraModeManager cameraModeManager;
    private PuzzleOverlaySystem puzzleOverlaySystem;
    
    private ImmutableArray<Entity> puzzleDoors;
    private ImmutableArray<Entity> terminals;
    private ImmutableArray<Entity> sockets;
    
    // face dir
    private float facingX = 1f;
    private float facingY = 0f;
    
    // shoot cd
    private float rangedCooldown = 0f;
    
    // cursor math
    private final Vector3 mouseWorldPos = new Vector3();

    public PlayerInputSystem() {
        super(Family.all(PlayerComponent.class, VelocityComponent.class, PositionComponent.class).get());
    }
    
    public PlayerInputSystem(GameStateManager gameStateManager) {
        super(Family.all(PlayerComponent.class, VelocityComponent.class, PositionComponent.class).get());
        this.gameStateManager = gameStateManager;
    }
    
    public PlayerInputSystem(GameStateManager gameStateManager, SpriteLoader spriteLoader) {
        super(Family.all(PlayerComponent.class, VelocityComponent.class, PositionComponent.class).get());
        this.gameStateManager = gameStateManager;
        this.spriteLoader = spriteLoader;
    }
    
    public PlayerInputSystem(GameStateManager gameStateManager, SpriteLoader spriteLoader, OrthographicCamera camera) {
        super(Family.all(PlayerComponent.class, VelocityComponent.class, PositionComponent.class).get());
        this.gameStateManager = gameStateManager;
        this.spriteLoader = spriteLoader;
        this.camera = camera;
    }
    
    public PlayerInputSystem(GameStateManager gameStateManager, SpriteLoader spriteLoader, OrthographicCamera camera, CameraModeManager cameraModeManager) {
        super(Family.all(PlayerComponent.class, VelocityComponent.class, PositionComponent.class).get());
        this.gameStateManager = gameStateManager;
        this.spriteLoader = spriteLoader;
        this.camera = camera;
        this.cameraModeManager = cameraModeManager;
    }
    
    public void setSpriteLoader(SpriteLoader spriteLoader) {
        this.spriteLoader = spriteLoader;
    }
    
    public void setCamera(OrthographicCamera camera) {
        this.camera = camera;
    }
    
    public void setCameraModeManager(CameraModeManager cameraModeManager) {
        this.cameraModeManager = cameraModeManager;
    }
    
    public void setPuzzleOverlaySystem(PuzzleOverlaySystem puzzleOverlaySystem) {
        this.puzzleOverlaySystem = puzzleOverlaySystem;
    }
    
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        // puzzles
        puzzleDoors = engine.getEntitiesFor(
            Family.all(PuzzleDoorComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );
        // screens
        terminals = engine.getEntitiesFor(
            Family.all(TerminalComponent.class, PositionComponent.class).get()
        );

        sockets = engine.getEntitiesFor(
            Family.all(SocketComponent.class, PositionComponent.class).get()
        );
    }
    
    @Override
    public void update(float deltaTime) {
        // shoot cd
        if (rangedCooldown > 0) {
            rangedCooldown -= deltaTime;
        }
        
        // cam toggle
        if (Gdx.input.isKeyJustPressed(Input.Keys.C) && cameraModeManager != null) {
            cameraModeManager.toggle();
        }
        
        // check if playing
        if (gameStateManager != null && !gameStateManager.isActiveGameplay()) {
            // stop move
            for (Entity entity : getEntities()) {
                VelocityComponent velocity = vm.get(entity);
                if (velocity != null) {
                    velocity.x = 0;
                    velocity.y = 0;
                }
            }
            return;
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerComponent player = pm.get(entity);
        VelocityComponent velocity = vm.get(entity);
        PositionComponent position = posm.get(entity);
        CollisionComponent collision = cm.get(entity);

        velocity.x = 0;
        velocity.y = 0;

        // move keys
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            velocity.y = player.speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            velocity.y = -player.speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            velocity.x = -player.speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            velocity.x = player.speed;
        }
        
        // facing
        if (velocity.x != 0 || velocity.y != 0) {
            float length = (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
            facingX = velocity.x / length;
            facingY = velocity.y / length;
        }
        
        // E to use things
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            if (!tryInteractWithTerminal(position, collision)) {
                if (!tryInteractWithSocket(entity, position, collision)) {
                    tryInteractWithPuzzleDoor(position, collision);
                }
            }
        }
        
        // space interact or shoot
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            boolean interacted = tryInteractWithTerminal(position, collision)
                    || tryInteractWithSocket(entity, position, collision)
                    || tryInteractWithPuzzleDoor(position, collision);
            if (!interacted && rangedCooldown <= 0) {
                // bang
                fireProjectile(entity, position, collision, facingX, facingY);
                rangedCooldown = GameConstants.RANGED_ATTACK_COOLDOWN;
            }
        }
        
        // click shoot
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && rangedCooldown <= 0) {
            fireProjectileTowardMouse(entity, position, collision);
        }
    }

    // try activate socket
    private boolean tryInteractWithSocket(Entity playerEntity, PositionComponent playerPos, CollisionComponent playerCol) {
        if (sockets == null || sockets.size() == 0 || playerPos == null) return false;

        // coords
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);

        Entity nearestSocket = null;
        float nearestDistance = Float.MAX_VALUE;

        for (Entity socketEntity : sockets) {
            PositionComponent socketPos = posm.get(socketEntity);
            CollisionComponent socketCol = cm.get(socketEntity);
            if (socketPos == null) continue;

            // skip hidden
            if (socketCol != null && (socketCol.width <= 0f || socketCol.height <= 0f)) {
                continue;
            }
            RenderableComponent socketRenderable = rm.get(socketEntity);
            if (socketRenderable != null && socketRenderable.color != null && socketRenderable.color.a <= 0.01f) {
                continue;
            }

            float socketCenterX = socketPos.x + (socketCol != null ? socketCol.width / 2 : GameConstants.TILE_SIZE / 2);
            float socketCenterY = socketPos.y + (socketCol != null ? socketCol.height / 2 : GameConstants.TILE_SIZE / 2);

            float dx = playerCenterX - socketCenterX;
            float dy = playerCenterY - socketCenterY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= GameConstants.SWITCH_INTERACTION_RADIUS && distance < nearestDistance) {
                nearestDistance = distance;
                nearestSocket = socketEntity;
            }
        }

        if (nearestSocket == null) return false;

        // riddle logic
        GraphLightsOutSystem graphLightsOutSystem = getEngine() != null
            ? getEngine().getSystem(GraphLightsOutSystem.class)
            : null;
        if (graphLightsOutSystem != null && graphLightsOutSystem.tryInteract(playerEntity, nearestSocket)) {
            return true;
        }

        RegisterAllocationSystem registerAllocationSystem = getEngine() != null
            ? getEngine().getSystem(RegisterAllocationSystem.class)
            : null;
        if (registerAllocationSystem != null && registerAllocationSystem.tryInteract(playerEntity, nearestSocket)) {
            return true;
        }

        SocketComponent socket = socketMapper.get(nearestSocket);
        if (socket == null) return false;
        if (socket.activated) {
            // For momentary sockets, allow re-interaction to refresh the timer.
            if (socket.momentary) {
                float duration = socket.momentaryDurationSeconds > 0f
                        ? socket.momentaryDurationSeconds
                        : GameConstants.SWITCH_MOMENTARY_DURATION;
                socket.momentaryTimerSeconds = duration;

                PlayerInventoryComponent inv = inventoryMapper.get(playerEntity);
                if (inv != null) {
                    inv.toast("Socket refreshed.", 1.0f);
                }
            } else {
                PlayerInventoryComponent inv = inventoryMapper.get(playerEntity);
                if (inv != null) {
                    inv.toast("Already activated.", 1.0f);
                }
            }
            return true;
        }

        PlayerInventoryComponent inventory = inventoryMapper.get(playerEntity);
        if (inventory == null) {
            inventory = new PlayerInventoryComponent();
            playerEntity.add(inventory);
        }

        String required = socket.requiresTokenId;
        boolean requires = required != null && !required.isEmpty();
        if (requires) {
            if (!inventory.hasToken(required)) {
                String msg = "Requires " + (GlyphNames.isGlyph(required)
                        ? GlyphNames.displayNameWithId(required)
                        : required);
                inventory.toast(msg, 1.2f);
                return true; // interacted, but didn't activate
            }

            if (socket.consumeToken) {
                boolean consumed = inventory.consumeToken(required, 1);
                if (!consumed) {
                    inventory.toast("Requires " + required + ".", 1.2f);
                    return true;
                }
            }
        }

        socket.activated = true;
        if (socket.momentary) {
            float duration = socket.momentaryDurationSeconds > 0f
                    ? socket.momentaryDurationSeconds
                    : GameConstants.SWITCH_MOMENTARY_DURATION;
            socket.momentaryTimerSeconds = duration;
        }

        if (requires && GlyphNames.isGlyph(required) && required.equalsIgnoreCase("glyph_7")) {
            inventory.toast("Inserted Crown. Vault unlocked.", 1.4f);
        } else if (requires) {
            inventory.toast("Inserted " + (GlyphNames.isGlyph(required) ? GlyphNames.displayName(required) : required) + ".", 1.2f);
        } else {
            inventory.toast("Socket activated.", 1.2f);
        }
        return true;
    }
    
    /**
     * Fire a projectile toward the mouse position in world coordinates.
     */
    private void fireProjectileTowardMouse(Entity player, PositionComponent playerPos, CollisionComponent playerCol) {
        if (camera == null) {
            // Fallback to facing direction if no camera is set
            fireProjectile(player, playerPos, playerCol, facingX, facingY);
            rangedCooldown = GameConstants.RANGED_ATTACK_COOLDOWN;
            return;
        }
        
        // Get mouse position in screen coordinates
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        
        // Unproject to world coordinates
        mouseWorldPos.set(mouseX, mouseY, 0);
        camera.unproject(mouseWorldPos);
        
        // Calculate player center
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);
        
        // Calculate direction from player to mouse
        float dx = mouseWorldPos.x - playerCenterX;
        float dy = mouseWorldPos.y - playerCenterY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        
        float dirX;
        float dirY;
        
        if (length > 0) {
            dirX = dx / length;
            dirY = dy / length;
            
            // Update facing direction to match mouse aim
            facingX = dirX;
            facingY = dirY;
        } else {
            // Mouse at player center - use current facing direction
            dirX = facingX;
            dirY = facingY;
        }
        
        fireProjectile(player, playerPos, playerCol, dirX, dirY);
        rangedCooldown = GameConstants.RANGED_ATTACK_COOLDOWN;
    }
    
    /**
     * Fire a projectile in the specified direction.
     */
    private void fireProjectile(Entity player, PositionComponent playerPos, CollisionComponent playerCol,
                                 float dirX, float dirY) {
        Engine engine = getEngine();
        if (engine == null) return;
        
        // Calculate projectile spawn position (center of player)
        float projectileSize = GameConstants.PROJECTILE_SIZE;
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);
        
        // Offset spawn position slightly in firing direction
        float spawnX = playerCenterX + dirX * GameConstants.TILE_SIZE - projectileSize / 2;
        float spawnY = playerCenterY + dirY * GameConstants.TILE_SIZE - projectileSize / 2;
        
        // Create projectile entity
        Entity projectile = engine.createEntity();
        projectile.add(new PositionComponent(spawnX, spawnY));
        projectile.add(new VelocityComponent(
            dirX * GameConstants.PROJECTILE_SPEED,
            dirY * GameConstants.PROJECTILE_SPEED
        ));
        projectile.add(new CollisionComponent(projectileSize, projectileSize));
        projectile.add(new RenderableComponent(projectileSize, projectileSize, Color.YELLOW));
        projectile.add(new ProjectileComponent(GameConstants.RANGED_ATTACK_DAMAGE, true));
        
        // Add sprite if available
        if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion projectileSprite = spriteLoader.getProjectileSprite();
            if (projectileSprite != null) {
                SpriteComponent sprite = new SpriteComponent(projectileSprite, 40);
                // Rotate projectile sprite to match direction
                sprite.rotation = (float) Math.toDegrees(Math.atan2(dirY, dirX));
                projectile.add(sprite);
            }
        }
        
        engine.addEntity(projectile);
    }
    
    /**
     * Try to interact with a nearby puzzle door.
     * If a locked puzzle door is within interaction distance, starts the puzzle overlay.
     * 
     * @return true if a puzzle door was found and interaction started, false otherwise
     */
    private boolean tryInteractWithPuzzleDoor(PositionComponent playerPos, CollisionComponent playerCol) {
        if (puzzleOverlaySystem == null || puzzleDoors == null) {
            return false;
        }
        
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);
        
        Entity nearestDoor = findNearbyLockedPuzzleDoor(playerCenterX, playerCenterY);
        
        if (nearestDoor != null) {
            puzzleOverlaySystem.startPuzzle(nearestDoor);
            return true;
        }
        return false;
    }
    
    /**
     * Find a nearby locked puzzle door within interaction distance.
     * 
     * @param playerCenterX Player center X position
     * @param playerCenterY Player center Y position
     * @return The nearest locked puzzle door entity, or null if none nearby
     */
    private Entity findNearbyLockedPuzzleDoor(float playerCenterX, float playerCenterY) {
        if (puzzleDoors == null) {
            return null;
        }
        
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (Entity doorEntity : puzzleDoors) {
            PuzzleDoorComponent door = puzzleDoorMapper.get(doorEntity);
            PositionComponent doorPos = posm.get(doorEntity);
            CollisionComponent doorCol = cm.get(doorEntity);
            RenderableComponent doorRenderable = doorEntity.getComponent(RenderableComponent.class);
            
            if (door == null || !door.locked || !door.hasPuzzle()) {
                continue; // Skip unlocked doors or doors without puzzles
            }
            
            if (doorPos == null || doorCol == null) {
                continue;
            }

            // Hidden doors should not be interactable.
            if (doorCol.width <= 0f || doorCol.height <= 0f) {
                continue;
            }
            if (doorRenderable != null && doorRenderable.color != null && doorRenderable.color.a <= 0.01f) {
                continue;
            }
            
            // Calculate door center
            float doorCenterX = doorPos.x + doorCol.width / 2;
            float doorCenterY = doorPos.y + doorCol.height / 2;
            
            // Calculate distance
            float dx = doorCenterX - playerCenterX;
            float dy = doorCenterY - playerCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist < GameConstants.PUZZLE_DOOR_INTERACTION_RADIUS && dist < nearestDist) {
                nearest = doorEntity;
                nearestDist = dist;
            }
        }
        
        return nearest;
    }
    
    /**
     * Check if the player is near any locked puzzle door.
     * Useful for displaying interaction prompts.
     * 
     * @param playerCenterX Player center X position
     * @param playerCenterY Player center Y position
     * @return true if a locked puzzle door is within interaction distance
     */
    public boolean isNearLockedPuzzleDoor(float playerCenterX, float playerCenterY) {
        return findNearbyLockedPuzzleDoor(playerCenterX, playerCenterY) != null;
    }
    
    /**
     * Try to interact with a nearby terminal.
     * If a terminal is within interaction distance, starts the puzzle overlay for its linked door.
     * 
     * @return true if a terminal was found and interaction started, false otherwise
     */
    private boolean tryInteractWithTerminal(PositionComponent playerPos, CollisionComponent playerCol) {
        if (puzzleOverlaySystem == null || terminals == null || puzzleDoors == null) {
            return false;
        }
        
        float playerCenterX = playerPos.x + (playerCol != null ? playerCol.width / 2 : GameConstants.TILE_SIZE / 2);
        float playerCenterY = playerPos.y + (playerCol != null ? playerCol.height / 2 : GameConstants.TILE_SIZE / 2);
        
        Entity nearestTerminal = findNearbyTerminal(playerCenterX, playerCenterY);
        
        if (nearestTerminal != null) {
            // Algebra terminals take precedence over puzzle terminals.
            AlgebraTerminalComponent algebraComp = algebraTerminalMapper.get(nearestTerminal);
            if (algebraComp != null) {
                AlgebraForgeSystem algebraForgeSystem = getEngine() != null
                        ? getEngine().getSystem(AlgebraForgeSystem.class)
                        : null;
                if (algebraForgeSystem != null) {
                    return algebraForgeSystem.beginInteraction(nearestTerminal);
                }
                return true;
            }

            TerminalComponent terminal = terminalMapper.get(nearestTerminal);
            if (terminal != null && !terminal.doorId.isEmpty()) {
                // Find the linked puzzle door
                Entity linkedDoor = findPuzzleDoorById(terminal.doorId);
                if (linkedDoor != null) {
                    if (!terminal.allowHiddenDoor) {
                        // If the linked door is still hidden (e.g., traversal finale content), block interaction.
                        CollisionComponent doorCol = cm.get(linkedDoor);
                        RenderableComponent doorRenderable = linkedDoor.getComponent(RenderableComponent.class);
                        if (doorCol != null && (doorCol.width <= 0f || doorCol.height <= 0f)) {
                            return false;
                        }
                        if (doorRenderable != null && doorRenderable.color != null && doorRenderable.color.a <= 0.01f) {
                            return false;
                        }
                    }

                    PuzzleDoorComponent doorComp = puzzleDoorMapper.get(linkedDoor);
                    if (doorComp != null && doorComp.locked && doorComp.hasPuzzle()) {
                        puzzleOverlaySystem.startPuzzle(linkedDoor);
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Find a nearby terminal within interaction distance.
     * 
     * @param playerCenterX Player center X position
     * @param playerCenterY Player center Y position
     * @return The nearest terminal entity, or null if none nearby
     */
    private Entity findNearbyTerminal(float playerCenterX, float playerCenterY) {
        if (terminals == null) {
            return null;
        }
        
        Entity nearest = null;
        float nearestDist = Float.MAX_VALUE;
        
        for (Entity terminalEntity : terminals) {
            PositionComponent terminalPos = posm.get(terminalEntity);
            CollisionComponent terminalCol = cm.get(terminalEntity);
            RenderableComponent terminalRenderable = terminalEntity.getComponent(RenderableComponent.class);
            TerminalComponent terminalComp = terminalMapper.get(terminalEntity);
            AlgebraTerminalComponent algebraComp = algebraTerminalMapper.get(terminalEntity);
            
            if (terminalPos == null) {
                continue;
            }

            // Interactable terminals are either puzzle terminals (doorId set) or algebra terminals.
            boolean isPuzzleTerminal = terminalComp != null && terminalComp.doorId != null && !terminalComp.doorId.trim().isEmpty();
            boolean isAlgebraTerminal = algebraComp != null;
            if (!isPuzzleTerminal && !isAlgebraTerminal) {
                continue;
            }

            // Hidden terminals (finale content before traversal success) should not be interactable.
            if (terminalCol != null && (terminalCol.width <= 0f || terminalCol.height <= 0f)) {
                continue;
            }
            if (terminalRenderable != null && terminalRenderable.color != null && terminalRenderable.color.a <= 0.01f) {
                continue;
            }

            // For puzzle terminals, also require the linked door to be revealed/visible.
            if (isPuzzleTerminal) {
                Entity linkedDoor = findPuzzleDoorById(terminalComp.doorId);
                if (linkedDoor == null) {
                    continue;
                }
                if (terminalComp == null || !terminalComp.allowHiddenDoor) {
                    CollisionComponent doorCol = cm.get(linkedDoor);
                    RenderableComponent doorRenderable = linkedDoor.getComponent(RenderableComponent.class);
                    if (doorCol != null && (doorCol.width <= 0f || doorCol.height <= 0f)) {
                        continue;
                    }
                    if (doorRenderable != null && doorRenderable.color != null && doorRenderable.color.a <= 0.01f) {
                        continue;
                    }
                }

                PuzzleDoorComponent doorComp = puzzleDoorMapper.get(linkedDoor);
                if (doorComp == null || !doorComp.locked || !doorComp.hasPuzzle()) {
                    continue;
                }
            }
            
            // Calculate terminal center
            float terminalCenterX = terminalPos.x + (terminalCol != null ? terminalCol.width / 2 : GameConstants.TILE_SIZE / 2);
            float terminalCenterY = terminalPos.y + (terminalCol != null ? terminalCol.height / 2 : GameConstants.TILE_SIZE / 2);
            
            // Calculate distance
            float dx = terminalCenterX - playerCenterX;
            float dy = terminalCenterY - playerCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            
            if (dist < GameConstants.TERMINAL_INTERACTION_RADIUS && dist < nearestDist) {
                nearest = terminalEntity;
                nearestDist = dist;
            }
        }
        
        return nearest;
    }
    
    /**
     * Find a puzzle door by its ID.
     * 
     * @param doorId The door ID to search for
     * @return The puzzle door entity, or null if not found
     */
    private Entity findPuzzleDoorById(String doorId) {
        if (puzzleDoors == null || doorId == null || doorId.isEmpty()) {
            return null;
        }
        
        for (Entity doorEntity : puzzleDoors) {
            PuzzleDoorComponent door = puzzleDoorMapper.get(doorEntity);
            if (door != null && doorId.equals(door.id)) {
                return doorEntity;
            }
        }
        return null;
    }
    
    /**
     * Check if the player is near any terminal.
     * Useful for displaying interaction prompts.
     * 
     * @param playerCenterX Player center X position
     * @param playerCenterY Player center Y position
     * @return true if a terminal is within interaction distance
     */
    public boolean isNearTerminal(float playerCenterX, float playerCenterY) {
        return findNearbyTerminal(playerCenterX, playerCenterY) != null;
    }
    
    /**
     * Check if the player is near any interactable (terminal or locked puzzle door).
     * Useful for displaying interaction prompts.
     * 
     * @param playerCenterX Player center X position
     * @param playerCenterY Player center Y position
     * @return true if an interactable is within interaction distance
     */
    public boolean isNearInteractable(float playerCenterX, float playerCenterY) {
        return isNearTerminal(playerCenterX, playerCenterY) || isNearLockedPuzzleDoor(playerCenterX, playerCenterY);
    }
}
