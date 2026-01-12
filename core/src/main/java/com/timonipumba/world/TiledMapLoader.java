package com.timonipumba.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.timonipumba.GameConstants;
import com.timonipumba.assets.SpriteLoader;
import com.timonipumba.components.*;
import com.timonipumba.systems.GateSystem;
import com.timonipumba.systems.GraphLightsOutSystem;
import com.timonipumba.systems.RegisterAllocationSystem;
import com.timonipumba.level.PortalConfig;
import com.timonipumba.level.Puzzle;
import com.timonipumba.level.PuzzleRegistry;

import java.util.HashMap;

/**
 * Loads Tiled maps (.tmx) and creates entities from them.
 *
 * Uses a FileHandleResolver so it can load:
 * - internal/classpath maps like "maps/unified_world.tmx" or "maps/puzzle_example.tmx"
 * - local filesystem maps (if you ever add some), but tilesets/images always from classpath.
 */
public class TiledMapLoader {
    private final Engine engine;
    private SpriteLoader spriteLoader;

    /** Maximum number of nudge attempts when finding safe spawn position */
    private static final int MAX_NUDGE_ATTEMPTS = 16;

    /** Nudge step size in pixels for safe spawn resolution */
    private static final float NUDGE_STEP = GameConstants.TILE_SIZE;

    public TiledMapLoader(Engine engine) {
        this.engine = engine;
    }

    public TiledMapLoader(Engine engine, SpriteLoader spriteLoader) {
        this.engine = engine;
        this.spriteLoader = spriteLoader;
    }

    public void setSpriteLoader(SpriteLoader spriteLoader) {
        this.spriteLoader = spriteLoader;
    }

    // ============================================================
    // Map loading (resolver so we can use internal + local files)
    // ============================================================

    public TiledMap loadMap(String mapPath) {
        FileHandleResolver resolver = fileName -> {
            String normalized = fileName.replace('\\', '/');

            // Tilesets and images should come from classpath even when TMX is local
            if (normalized.endsWith(".tsx") || normalized.endsWith(".png") || normalized.contains("/img/")) {
                String baseName = normalized.contains("/")
                        ? normalized.substring(normalized.lastIndexOf('/') + 1)
                        : normalized;

                String[] candidates = new String[] {
                        normalized,
                        stripToSubpath(normalized, "tilesets/"),
                        stripToSubpath(normalized, "img/"),
                        "tilesets/" + baseName,
                        "tilesets/img/" + baseName,
                        baseName
                };

                for (String candidate : candidates) {
                    if (candidate == null || candidate.isEmpty()) {
                        continue;
                    }
                    FileHandle internal = Gdx.files.internal(candidate);
                    if (internal.exists()) {
                        return internal;
                    }
                }

                // If none were found (unexpected), still force classpath using the base name to avoid wrong local resolution
                FileHandle fallbackInternal = Gdx.files.internal(baseName);
                return fallbackInternal;
            }

            // TMX maps: if absolute, use it directly; otherwise prefer local file (generated) if it exists
            java.io.File absFile = new java.io.File(normalized);
            if (absFile.isAbsolute() && absFile.exists()) {
                FileHandle absoluteHandle = new FileHandle(absFile);
                return absoluteHandle;
            }

            FileHandle local = Gdx.files.local(normalized);
            if (local.exists()) {
                return local;
            }

            // Fallback: internal/classpath
            return Gdx.files.internal(normalized);
        };

        TmxMapLoader loader = new TmxMapLoader(resolver);
        TiledMap map = loader.load(mapPath);

        // Load tile layers for walls
        for (MapLayer layer : map.getLayers()) {
            if (layer instanceof TiledMapTileLayer) {
                TiledMapTileLayer tileLayer = (TiledMapTileLayer) layer;
                if ("walls".equalsIgnoreCase(tileLayer.getName()) ||
                    "collision".equalsIgnoreCase(tileLayer.getName())) {
                    loadWallLayer(tileLayer);
                }
            }
        }

        // Load object layer for spawns, enemies, gates, etc.
        MapLayer objectLayer = map.getLayers().get("objects");
        if (objectLayer != null) {
            loadObjectLayer(objectLayer);
        }

        // Load doors from "Doors" layer (if present)
        MapLayer doorsLayer = map.getLayers().get("Doors");
        if (doorsLayer != null) {
            loadDoorsLayer(doorsLayer);
        }

        return map;
    }

    private String stripToSubpath(String path, String marker) {
        int idx = path.indexOf(marker);
        if (idx >= 0) {
            return path.substring(idx);
        }
        return null;
    }

    // ============================================================
    // Collision helpers
    // ============================================================

    public boolean isPositionSolid(float x, float y, float width, float height) {
        ImmutableArray<Entity> walls = engine.getEntitiesFor(
                Family.all(WallComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );

        float[][] corners = {
                {x, y},
                {x + width - 1, y},
                {x, y + height - 1},
                {x + width - 1, y + height - 1}
        };

        for (Entity wall : walls) {
            PositionComponent wallPos = wall.getComponent(PositionComponent.class);
            CollisionComponent wallCol = wall.getComponent(CollisionComponent.class);

            if (wallPos == null || wallCol == null) continue;

            for (float[] corner : corners) {
                if (corner[0] >= wallPos.x && corner[0] < wallPos.x + wallCol.width &&
                    corner[1] >= wallPos.y && corner[1] < wallPos.y + wallCol.height) {
                    return true;
                }
            }
        }

        return false;
    }

    public float[] findSafeSpawnPosition(float startX, float startY, float width, float height) {
        if (!isPositionSolid(startX, startY, width, height)) {
            return new float[]{startX, startY};
        }

        for (int ring = 1; ring <= MAX_NUDGE_ATTEMPTS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (Math.abs(dx) != ring && Math.abs(dy) != ring) {
                        continue;
                    }

                    float testX = startX + dx * NUDGE_STEP;
                    float testY = startY + dy * NUDGE_STEP;

                    if (testX < 0 || testY < 0) {
                        continue;
                    }

                    if (!isPositionSolid(testX, testY, width, height)) {
                        return new float[]{testX, testY};
                    }
                }
            }
        }
        return findFirstNonSolidPosition(startX, startY, width, height);
    }

    private float[] findFirstNonSolidPosition(float startX, float startY, float width, float height) {
        int scanRadius = 10;

        for (int dy = 0; dy <= scanRadius; dy++) {
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                float testX = startX + dx * NUDGE_STEP;
                float testY = startY + dy * NUDGE_STEP;

                if (testX >= 0 && testY >= 0 && !isPositionSolid(testX, testY, width, height)) {
                    return new float[]{testX, testY};
                }
            }

            if (dy > 0) {
                for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                    float testX = startX + dx * NUDGE_STEP;
                    float testY = startY - dy * NUDGE_STEP;

                    if (testX >= 0 && testY >= 0 && !isPositionSolid(testX, testY, width, height)) {
                        return new float[]{testX, testY};
                    }
                }
            }
        }
        float centerX = 10 * NUDGE_STEP;
        float centerY = 10 * NUDGE_STEP;
        return new float[]{centerX, centerY};
    }

    // ============================================================
    // TMX layers / objects → entities
    // ============================================================

    private void loadObjectLayer(MapLayer layer) {
        for (MapObject object : layer.getObjects()) {
            float x;
            float y;
            float width;
            float height;

            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                x = rect.x;
                y = rect.y;
                width = rect.width;
                height = rect.height;
            } else if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                x = tileObject.getX();
                y = tileObject.getY() - GameConstants.TILE_SIZE;
                width = GameConstants.TILE_SIZE;
                height = GameConstants.TILE_SIZE;
            } else {
                continue;
            }

            String type = getPropertyString(object, "type", null);
            if (type == null) continue;

            switch (type.toLowerCase()) {
                case "player":
                    createPlayer(object, x, y);
                    break;
                case "enemy":
                    String enemyType = getPropertyString(object, "enemy_type", null);
                    String arenaId = getPropertyString(object, "arenaId", null);
                    createEnemy(x, y, enemyType, arenaId);
                    break;
                case "potion":
                    createItem(x, y, ItemType.POTION);
                    break;
                case "buff":
                    createItem(x, y, ItemType.BUFF);
                    break;
                case "treasure":
                    createItem(x, y, ItemType.TREASURE);
                    break;
                case "door":
                    createDoor(object, x, y);
                    break;
                case "puzzledoor":
                    createPuzzleDoor(object, x, y);
                    break;
                case "exit":
                    createExit(object, x, y);
                    break;
                case "gate":
                    createGate(object, x, y);
                    break;
                case "token":
                    createToken(object, x, y);
                    break;
                case "socket":
                    createSocket(object, x, y);
                    break;
                case "terminal":
                    createTerminal(object, x, y);
                    break;
                case "arena":
                    registerArenaBounds(object, x, y, width, height);
                    break;
                default:
                    System.out.println("Unknown object type in Tiled map: " + type);
                    break;
            }
        }
    }

    private void loadDoorsLayer(MapLayer layer) {
        for (MapObject object : layer.getObjects()) {
            float x, y, width, height;

            if (object instanceof RectangleMapObject) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                x = rect.x;
                y = rect.y;
                width = rect.width;
                height = rect.height;
            } else if (object instanceof TiledMapTileMapObject) {
                TiledMapTileMapObject tileObject = (TiledMapTileMapObject) object;
                x = tileObject.getX();
                y = tileObject.getY() - GameConstants.TILE_SIZE;
                width = GameConstants.TILE_SIZE;
                height = GameConstants.TILE_SIZE;
            } else {
                continue;
            }

            createDoorFromObject(object, x, y, width, height);
        }
    }

    private void createDoorFromObject(MapObject object, float x, float y, float width, float height) {
        String id = getPropertyString(object, "id", null);
        Boolean locked = getPropertyBoolean(object, "locked", false);
        String puzzleId = getPropertyString(object, "puzzleId", null);

        if (id == null || id.isEmpty()) {
            id = "door_" + (int) x + "_" + (int) y;
            System.out.println("Warning: Door at (" + x + "," + y + ") missing 'id', using generated ID: " + id);
        }

        boolean isLocked = locked != null && locked;

        if (puzzleId != null && !puzzleId.isEmpty()) {
            Puzzle puzzle = PuzzleRegistry.get(puzzleId);

            if (puzzle == null) {
                puzzle = buildPuzzleFromMapObject(object, puzzleId);
                if (puzzle != null) {
                    PuzzleRegistry.register(puzzle);
                }
            }

            if (isLocked && puzzle == null) {
                System.out.println("Warning: Locked door at (" + x + "," + y + ") has puzzleId=" +
                        puzzleId + " but no puzzle data found - will not be unlockable!");
            }

            Color doorColor = isLocked ? new Color(0.6f, 0.3f, 0.1f, 1.0f)
                                       : new Color(0.2f, 0.8f, 0.2f, 0.5f);

            Entity doorEntity = engine.createEntity();
            doorEntity.add(new PositionComponent(x, y));
            doorEntity.add(new CollisionComponent(width, height));

            RenderableComponent renderable = new RenderableComponent(width, height, doorColor);
            validateDoorRenderable(renderable, x, y, "puzzledoor (Doors layer)");
            doorEntity.add(renderable);

            PuzzleDoorComponent puzzleDoorComp = new PuzzleDoorComponent(id, puzzle, isLocked);
            puzzleDoorComp.originalWidth = width;
            puzzleDoorComp.originalHeight = height;
            puzzleDoorComp.dimensionsStored = true;
            doorEntity.add(puzzleDoorComp);

            engine.addEntity(doorEntity);
        } else {
            String group = getPropertyString(object, "group", GameConstants.DEFAULT_PUZZLE_GROUP);
            Boolean open = getPropertyBoolean(object, "open", false);

            Entity doorEntity = engine.createEntity();
            doorEntity.add(new PositionComponent(x, y));
            doorEntity.add(new CollisionComponent(width, height));

            RenderableComponent renderable = new RenderableComponent(width, height, Color.BROWN);
            validateDoorRenderable(renderable, x, y, "door (Doors layer)");
            doorEntity.add(renderable);

            DoorComponent doorComp = new DoorComponent(group, open != null && open);
            doorComp.originalWidth = width;
            doorComp.originalHeight = height;
            doorComp.dimensionsStored = true;
            doorEntity.add(doorComp);

            engine.addEntity(doorEntity);
        }
    }

    private String getPropertyString(MapObject object, String key, String defaultValue) {
        String value = object.getProperties().get(key, String.class);
        if (value != null) return value;
        value = object.getProperties().get(key.toLowerCase(), String.class);
        if (value != null) return value;
        return defaultValue;
    }

    private Boolean getPropertyBoolean(MapObject object, String key, Boolean defaultValue) {
        Boolean value = object.getProperties().get(key, Boolean.class);
        if (value != null) return value;
        value = object.getProperties().get(key.toLowerCase(), Boolean.class);
        if (value != null) return value;
        return defaultValue;
    }

    /**
     * Create wall entities from the given tile layer.
     *
     * Gate tiles (ID 212) are ALSO treated as walls here so they block where you
     * see purple tiles. GateSystem is responsible for removing the corresponding
     * walls and tiles when gates open.
     */
    private void loadWallLayer(TiledMapTileLayer layer) {
        int width = layer.getWidth();
        int height = layer.getHeight();
        float tileWidth = layer.getTileWidth();
        float tileHeight = layer.getTileHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell == null || cell.getTile() == null) continue;

                int tileId = cell.getTile().getId();

                Entity wall = engine.createEntity();
                wall.add(new PositionComponent(x * tileWidth, y * tileHeight));
                wall.add(new CollisionComponent(tileWidth, tileHeight));
                wall.add(new WallComponent());

                // Gate tiles are also walls until GateSystem clears them.
                if (tileId == GameConstants.GATE_TILE_ID) {
                    wall.add(new GateWallComponent());
                }

                engine.addEntity(wall);
            }
        }
    }

    /**
     * Registers an arena rectangle into GateSystem for player arena tracking.
     * This is used by procedurally generated traversal maps to avoid coordinate-system
     * mismatches between TMX CSV (top-down) and world space (bottom-up).
     */
    private void registerArenaBounds(MapObject object, float x, float y, float width, float height) {
        if (engine == null) {
            return;
        }

        GateSystem gateSystem = engine.getSystem(GateSystem.class);
        if (gateSystem == null) {
            return;
        }

        String arenaId = getPropertyString(object, "arenaId", null);
        if (arenaId == null || arenaId.isEmpty()) {
            return;
        }

        gateSystem.registerArena(arenaId, x, y, width, height);

        Boolean isStart = getPropertyBoolean(object, "isStart", false);
        if (isStart != null && isStart) {
            gateSystem.setStartArena(arenaId);
        }
    }

    private void createToken(MapObject object, float x, float y) {
        String tokenId = getPropertyString(object, "tokenId", null);
        if (tokenId == null || tokenId.isEmpty()) {
            tokenId = getPropertyString(object, "id", null);
        }
        if (tokenId == null || tokenId.isEmpty()) {
            tokenId = "token_" + x + "_" + y;
            System.out.println("Warning: Token at (" + x + "," + y + ") missing tokenId/id; using " + tokenId);
        }

        // Algebra Forge uses in-world colored markers instead of icon sprites.
        boolean isAlgebraGlyph = tokenId != null && tokenId.startsWith("glyph_");

        Entity token = engine.createEntity();
        token.add(new PositionComponent(x, y));
        token.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        token.add(new RenderableComponent(
                GameConstants.TILE_SIZE,
                GameConstants.TILE_SIZE,
                isAlgebraGlyph ? new Color(0.2f, 0.9f, 0.2f, 1f) : Color.LIGHT_GRAY
        ));
        token.add(new TokenComponent(tokenId));

        if (isAlgebraGlyph) {
            engine.addEntity(token);
            return;
        }

        if (object instanceof TiledMapTileMapObject) {
            TiledMapTileMapObject tileObj = (TiledMapTileMapObject) object;
            TextureRegion tileRegion = tileObj.getTile() != null ? tileObj.getTile().getTextureRegion() : null;
            if (tileRegion != null) {
                token.add(new SpriteComponent(tileRegion, 10));
                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, tileRegion);
                token.add(animComp);
            }
        } else if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion keySprite = spriteLoader.getTokenKeySprite();
            if (keySprite != null) {
                token.add(new SpriteComponent(keySprite, 10));
                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, keySprite);
                token.add(animComp);
            }
        }

        engine.addEntity(token);
    }

    private void createSocket(MapObject object, float x, float y) {
        String requiresTokenId = getPropertyString(object, "requiresTokenId", null);
        String group = getPropertyString(object, "group", null);
        Boolean consume = getPropertyBoolean(object, "consume", true);
        Boolean activated = getPropertyBoolean(object, "activated", false);

        // Optional: timed (momentary) sockets. When active, they auto-reset after duration.
        Boolean momentary = getPropertyBoolean(object, "momentary", false);
        Float durationSeconds = getPropertyFloat(object, "durationSeconds", 0f);

        // Optional register-allocation puzzle metadata
        String puzzleType = getPropertyString(object, "puzzleType", null);
        String nodeId = getPropertyString(object, "nodeId", null);
        String neighbors = getPropertyString(object, "neighbors", null);
        Boolean winTrigger = getPropertyBoolean(object, "winTrigger", false);
        String unlockDoorGroup = getPropertyString(object, "unlockDoorGroup", null);
        Boolean on = getPropertyBoolean(object, "on", false);

        if (group == null || group.isEmpty()) {
            group = GameConstants.DEFAULT_PUZZLE_GROUP;
        }

        Entity socket = engine.createEntity();
        socket.add(new PositionComponent(x, y));
        socket.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        // For Algebra Forge, show sockets as simple colored markers (no chest icon).
        boolean isAlgebraSocket = requiresTokenId != null && requiresTokenId.startsWith("glyph_");
        socket.add(new RenderableComponent(
            GameConstants.TILE_SIZE,
            GameConstants.TILE_SIZE,
            isAlgebraSocket ? new Color(0.95f, 0.3f, 0.3f, 1f) : Color.WHITE
        ));

        SocketComponent socketComp = new SocketComponent(requiresTokenId, group, consume != null && consume);
        socketComp.activated = activated != null && activated;
        socketComp.momentary = momentary != null && momentary;
        socketComp.momentaryDurationSeconds = durationSeconds != null ? durationSeconds : 0f;
        socket.add(socketComp);

        if (RegisterAllocationSystem.PUZZLE_TYPE.equals(puzzleType)) {
            RegisterNodeComponent nodeComp = new RegisterNodeComponent(nodeId, neighbors);
            nodeComp.winTrigger = winTrigger != null && winTrigger;
            nodeComp.unlockDoorGroup = unlockDoorGroup;
            socket.add(nodeComp);

            // Win-trigger sockets are implementation details (used to open the reward door group).
            // Hide them so players don't try to assign registers to them.
            if (nodeComp.winTrigger) {
                CollisionComponent col = socket.getComponent(CollisionComponent.class);
                if (col != null) {
                    col.width = 0;
                    col.height = 0;
                }
                RenderableComponent r = socket.getComponent(RenderableComponent.class);
                if (r != null && r.color != null) {
                    r.color.a = 0f;
                }
            }
        }

        if (GraphLightsOutSystem.PUZZLE_TYPE.equals(puzzleType)) {
            LightsOutNodeComponent nodeComp = new LightsOutNodeComponent(nodeId, neighbors, on != null && on);
            nodeComp.winTrigger = winTrigger != null && winTrigger;
            nodeComp.unlockDoorGroup = unlockDoorGroup;
            socket.add(nodeComp);

            // Apply initial lantern visual immediately.
            RenderableComponent r = socket.getComponent(RenderableComponent.class);
            if (r != null) {
                if (nodeComp.on) {
                    r.color = new Color(1f, 0.95f, 0.35f, 1f);
                } else {
                    r.color = new Color(0.25f, 0.25f, 0.30f, 1f);
                }
            }

            // Win-trigger sockets are implementation details.
            if (nodeComp.winTrigger) {
                CollisionComponent col = socket.getComponent(CollisionComponent.class);
                if (col != null) {
                    col.width = 0;
                    col.height = 0;
                }
                if (r != null && r.color != null) {
                    r.color.a = 0f;
                }
            }
        }

        // Skip visuals for hidden win-trigger sockets.
        if (RegisterAllocationSystem.PUZZLE_TYPE.equals(puzzleType) && (winTrigger != null && winTrigger)) {
            engine.addEntity(socket);
            return;
        }

        // Skip visuals for hidden win-trigger sockets.
        if (GraphLightsOutSystem.PUZZLE_TYPE.equals(puzzleType) && (winTrigger != null && winTrigger)) {
            engine.addEntity(socket);
            return;
        }

        // Algebra Forge sockets intentionally use only the colored marker.
        if (requiresTokenId != null && requiresTokenId.startsWith("glyph_")) {
            engine.addEntity(socket);
            return;
        }

        if (object instanceof TiledMapTileMapObject) {
            TiledMapTileMapObject tileObj = (TiledMapTileMapObject) object;
            TextureRegion tileRegion = tileObj.getTile() != null ? tileObj.getTile().getTextureRegion() : null;
            if (tileRegion != null) {
                socket.add(new SpriteComponent(tileRegion, 10));
                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, tileRegion);
                socket.add(animComp);
            }
        } else if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion chestSprite = spriteLoader.getSocketChestSprite();
            if (chestSprite != null) {
                socket.add(new SpriteComponent(chestSprite, 10));
                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, chestSprite);
                socket.add(animComp);
            }
        }

        engine.addEntity(socket);
    }

    private Float getPropertyFloat(MapObject object, String key, float defaultValue) {
        if (object == null || object.getProperties() == null) return defaultValue;
        Object raw = object.getProperties().get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Float) return (Float) raw;
        if (raw instanceof Double) return ((Double) raw).floatValue();
        if (raw instanceof Integer) return ((Integer) raw).floatValue();
        if (raw instanceof String) {
            try {
                return Float.parseFloat((String) raw);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int getPropertyInt(MapObject object, String key, int defaultValue) {
        if (object == null || object.getProperties() == null) return defaultValue;
        Object raw = object.getProperties().get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Integer) return (Integer) raw;
        if (raw instanceof Long) return ((Long) raw).intValue();
        if (raw instanceof Float) return ((Float) raw).intValue();
        if (raw instanceof Double) return ((Double) raw).intValue();
        if (raw instanceof String) {
            try {
                return Integer.parseInt((String) raw);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private void createPlayer(MapObject sourceObject, float x, float y) {
        Entity player = engine.createEntity();

        float collisionWidth = GameConstants.PLAYER_COLLISION_WIDTH;
        float collisionHeight = GameConstants.PLAYER_COLLISION_HEIGHT;

        float normalizedX = x + (GameConstants.TILE_SIZE / 2f) - (collisionWidth / 2f);
        float normalizedY = y + (GameConstants.TILE_SIZE / 2f) - (collisionHeight / 2f);

        float[] safePosition = findSafeSpawnPosition(normalizedX, normalizedY, collisionWidth, collisionHeight);
        float safeX = safePosition[0];
        float safeY = safePosition[1];

        player.add(new PositionComponent(safeX, safeY));
        player.add(new VelocityComponent());
        player.add(new CollisionComponent(collisionWidth, collisionHeight));
        player.add(new PlayerComponent());
        player.add(new PlayerInventoryComponent());
        player.add(new HealthComponent(GameConstants.PLAYER_MAX_HEALTH));
        player.add(new CombatComponent(GameConstants.PLAYER_ATTACK_DAMAGE));

        player.add(new HitFlashComponent(Color.WHITE, GameConstants.HIT_FLASH_DURATION_PLAYER));
        player.add(new CameraShakeComponent());

        TextureRegion spriteRegion = null;
        float renderWidth = GameConstants.TILE_SIZE;
        float renderHeight = GameConstants.TILE_SIZE;

        if (spriteLoader != null && spriteLoader.isLoaded()) {
            spriteRegion = spriteLoader.getPlayerFullSprite();
            if (spriteRegion != null) {
                renderWidth = GameConstants.PLAYER_SPRITE_WIDTH;
                renderHeight = GameConstants.PLAYER_SPRITE_HEIGHT;
            }
        }

        if (spriteRegion == null && spriteLoader != null && spriteLoader.isLoaded()) {
            spriteRegion = spriteLoader.getPlayerSprite();
        }

        if (spriteRegion == null && sourceObject instanceof TiledMapTileMapObject) {
            TiledMapTileMapObject tileObj = (TiledMapTileMapObject) sourceObject;
            TextureRegion tileRegion = tileObj.getTile() != null
                    ? tileObj.getTile().getTextureRegion()
                    : null;
            if (tileRegion != null) {
                spriteRegion = tileRegion;
            }
        }

        player.add(new RenderableComponent(renderWidth, renderHeight, Color.GREEN));

        if (spriteRegion != null) {
            player.add(new SpriteComponent(spriteRegion, 30));

            AnimationComponent animComp = new AnimationComponent();
            animComp.setAnimation(AnimationComponent.State.IDLE, spriteRegion);
            animComp.setAnimation(AnimationComponent.State.WALK, spriteRegion);
            player.add(animComp);
        }

        engine.addEntity(player);
    }

    private void createEnemy(float x, float y, String enemyTypeString, String arenaId) {
        EnemyStatsComponent.EnemyType enemyType = EnemyStatsComponent.parseType(enemyTypeString);
        EnemyStatsComponent stats = new EnemyStatsComponent(enemyType);

        int maxHealth = stats.maxHealth > 0 ? stats.maxHealth : GameConstants.ENEMY_DEFAULT_MAX_HEALTH;

        Color color = Color.RED;
        if (enemyType == EnemyStatsComponent.EnemyType.BRUTE) {
            color = Color.MAROON;
        } else if (enemyType == EnemyStatsComponent.EnemyType.SCOUT) {
            color = Color.ORANGE;
        } else if (enemyType == EnemyStatsComponent.EnemyType.RANGER) {
            color = Color.PURPLE;
        }

        float attackCooldown = stats.attackCooldown;

        Entity enemy = engine.createEntity();
        enemy.add(new PositionComponent(x, y));
        enemy.add(new VelocityComponent());
        enemy.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        enemy.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, color));

        EnemyComponent enemyComp = new EnemyComponent();
        enemyComp.arenaId = arenaId;
        enemy.add(enemyComp);

        enemy.add(stats);
        enemy.add(new HealthComponent(maxHealth));
        enemy.add(new CombatComponent(stats.damage, attackCooldown));

        enemy.add(new HitFlashComponent(Color.WHITE, GameConstants.HIT_FLASH_DURATION_ENEMY));

        if (enemyType == EnemyStatsComponent.EnemyType.RANGER) {
            enemy.add(new RangedAttackWindupComponent());
        }

        if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion enemySprite = spriteLoader.getEnemySprite(enemyType);
            if (enemySprite != null) {
                enemy.add(new SpriteComponent(enemySprite, 20));

                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, enemySprite);
                animComp.setAnimation(AnimationComponent.State.WALK, enemySprite);
                enemy.add(animComp);
            }
        }

        engine.addEntity(enemy);
    }

    private void createItem(float x, float y, ItemType itemType) {
        Color color;
        switch (itemType) {
            case POTION:
                color = Color.MAGENTA;
                break;
            case BUFF:
                color = Color.CYAN;
                break;
            case TREASURE:
                color = Color.GOLD;
                break;
            default:
                color = Color.WHITE;
        }

        Entity item = engine.createEntity();
        item.add(new PositionComponent(x, y));
        item.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        item.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, color));
        item.add(new ItemComponent(itemType));

        if (spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion itemSprite = spriteLoader.getItemSprite(itemType);
            if (itemSprite != null) {
                item.add(new SpriteComponent(itemSprite, 10));

                AnimationComponent animComp = new AnimationComponent();
                animComp.setAnimation(AnimationComponent.State.IDLE, itemSprite);
                item.add(animComp);
            }
        }

        engine.addEntity(item);
    }

    private void createDoor(MapObject object, float x, float y) {
        String group = object.getProperties().get("group", String.class);
        Boolean open = object.getProperties().get("open", Boolean.class);

        if (group == null) {
            System.out.println("Warning: Door at (" + x + "," + y + ") missing 'group' property, using default '" +
                    GameConstants.DEFAULT_PUZZLE_GROUP + "'");
            group = GameConstants.DEFAULT_PUZZLE_GROUP;
        }

        Entity doorEntity = engine.createEntity();
        doorEntity.add(new PositionComponent(x, y));
        doorEntity.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));

        RenderableComponent renderable = new RenderableComponent(
                GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, Color.BROWN);
        validateDoorRenderable(renderable, x, y, "door");
        doorEntity.add(renderable);

        DoorComponent doorComp = new DoorComponent(group, open != null && open);
        doorComp.originalWidth = GameConstants.TILE_SIZE;
        doorComp.originalHeight = GameConstants.TILE_SIZE;
        doorComp.dimensionsStored = true;
        doorEntity.add(doorComp);

        engine.addEntity(doorEntity);
    }

    private void createPuzzleDoor(MapObject object, float x, float y) {
        String id = object.getProperties().get("id", String.class);
        Boolean locked = object.getProperties().get("locked", Boolean.class);
        String puzzleId = object.getProperties().get("puzzleId", String.class);
        Boolean isFinale = getPropertyBoolean(object, "isFinale", false);
        Boolean hidden = getPropertyBoolean(object, "hidden", false);

        if (id == null || id.isEmpty()) {
            id = "puzzledoor_" + x + "_" + y;
            System.out.println("Warning: PuzzleDoor at (" + x + "," + y + ") missing 'id', using generated ID: " + id);
        }

        boolean isLocked = locked == null || locked;
        boolean finaleFlag = isFinale != null && isFinale;

        Puzzle puzzle = null;
        if (puzzleId != null && !puzzleId.isEmpty()) {
            puzzle = PuzzleRegistry.get(puzzleId);

            if (puzzle == null) {
                puzzle = buildPuzzleFromMapObject(object, puzzleId);
                if (puzzle != null) {
                    PuzzleRegistry.register(puzzle);
                }
            }
        }

        if (isLocked && puzzle == null) {
            System.out.println("Warning: Locked PuzzleDoor at (" + x + "," + y + ") has no puzzle - will not be unlockable!");
        }

        Entity doorEntity = engine.createEntity();
        doorEntity.add(new PositionComponent(x, y));
        doorEntity.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));

        Color doorColor = isLocked
                ? new Color(0.6f, 0.3f, 0.1f, 1.0f)
                : new Color(0.2f, 0.8f, 0.2f, 0.5f);
        RenderableComponent renderable = new RenderableComponent(
                GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, doorColor);
        validateDoorRenderable(renderable, x, y, "puzzledoor");
        doorEntity.add(renderable);

        PuzzleDoorComponent puzzleDoorComp = new PuzzleDoorComponent(id, puzzle, isLocked, finaleFlag);
        puzzleDoorComp.originalWidth = GameConstants.TILE_SIZE;
        puzzleDoorComp.originalHeight = GameConstants.TILE_SIZE;
        puzzleDoorComp.dimensionsStored = true;
        doorEntity.add(puzzleDoorComp);

        // Optional: allow TMX to spawn a puzzle door hidden (for end-goal reveals).
        if (hidden != null && hidden) {
            CollisionComponent col = doorEntity.getComponent(CollisionComponent.class);
            if (col != null) {
                col.width = 0;
                col.height = 0;
            }
            // RenderingSystem also draws sprites; keep this transparent so both shapes and sprites can be hidden.
            renderable.color = new Color(renderable.color.r, renderable.color.g, renderable.color.b, 0f);
        }

        engine.addEntity(doorEntity);
    }

    private Puzzle buildPuzzleFromMapObject(MapObject object, String puzzleId) {
        String puzzleType = getPropertyString(object, "puzzleType", null);
        if (puzzleType == null || puzzleType.isEmpty()) {
            puzzleType = getPropertyString(object, "type", GameConstants.DEFAULT_PUZZLE_TYPE);
        }

        String answer = getPropertyString(object, "answer", null);
        if (answer == null || answer.isEmpty()) {
            return null;
        }

        HashMap<String, String> data = new HashMap<>();
        data.put("answer", answer);

        String ciphertext = getPropertyString(object, "ciphertext", null);
        if (ciphertext != null && !ciphertext.isEmpty()) {
            data.put("ciphertext", ciphertext);
        }

        String key = getPropertyString(object, "key", null);
        if (key != null && !key.isEmpty()) {
            data.put("key", key);
        }

        String hint = getPropertyString(object, "hint", null);
        if (hint != null && !hint.isEmpty()) {
            data.put("hint", hint);
        }

        String prompt = getPropertyString(object, "prompt", null);
        if (prompt != null && !prompt.isEmpty()) {
            data.put("prompt", prompt);
        }

        return new Puzzle(puzzleId, puzzleType, data);
    }

    private void createExit(MapObject object, float x, float y) {
        String targetMap = object.getProperties().get("targetMap", String.class);

        Boolean hidden = getPropertyBoolean(object, "hidden", false);

        boolean hideLegacyExit = PortalConfig.WALL_SEGMENT_PORTALS_ENABLED && PortalConfig.HIDE_LEGACY_EXITS;

        Entity exitEntity = engine.createEntity();
        exitEntity.add(new PositionComponent(x, y));

        if (hideLegacyExit || (hidden != null && hidden)) {
            exitEntity.add(new CollisionComponent(0, 0));
            exitEntity.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, new Color(0, 0, 0, 0)));
        } else {
            exitEntity.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
            exitEntity.add(new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, Color.LIME));
        }

        exitEntity.add(new ExitComponent(targetMap));
        engine.addEntity(exitEntity);
    }

    private void createTerminal(MapObject object, float x, float y) {
        String doorId = getPropertyString(object, "doorId", null);
        String terminalType = getPropertyString(object, "terminalType", "puzzle");
        String opId = getPropertyString(object, "opId", "");
        int charges = getPropertyInt(object, "charges", 0);
        Boolean hidden = getPropertyBoolean(object, "hidden", false);
        Boolean allowHiddenDoor = getPropertyBoolean(object, "allowHiddenDoor", false);

        // For algebra terminals, doorId is optional (they don't control puzzle doors).
        boolean isAlgebraTerminal = terminalType != null &&
                (terminalType.equalsIgnoreCase("forge") || terminalType.equalsIgnoreCase("oracle"));
        if (!isAlgebraTerminal && (doorId == null || doorId.isEmpty())) {
            System.out.println("Warning: Terminal at (" + x + "," + y + ") missing 'doorId' property");
            doorId = "";
        }

        // Keep TerminalComponent stable even for algebra terminals.
        if (doorId == null) {
            doorId = "";
        }

        Entity terminalEntity = engine.createEntity();
        terminalEntity.add(new PositionComponent(x, y));
        terminalEntity.add(new CollisionComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE));
        Color marker = Color.CYAN;
        if (isAlgebraTerminal) {
            if (terminalType != null && terminalType.equalsIgnoreCase("oracle")) {
                marker = new Color(0.9f, 0.2f, 0.9f, 1f);
            } else {
                marker = new Color(0.2f, 0.7f, 1.0f, 1f);
            }
        }
        RenderableComponent renderable = new RenderableComponent(GameConstants.TILE_SIZE, GameConstants.TILE_SIZE, marker);
        terminalEntity.add(renderable);
        TerminalComponent terminalComponent = new TerminalComponent(doorId);
        terminalComponent.allowHiddenDoor = allowHiddenDoor != null && allowHiddenDoor;
        terminalEntity.add(terminalComponent);

        if (isAlgebraTerminal) {
            AlgebraTerminalComponent.Kind kind = terminalType.equalsIgnoreCase("oracle")
                    ? AlgebraTerminalComponent.Kind.ORACLE
                    : AlgebraTerminalComponent.Kind.FORGE;
            terminalEntity.add(new AlgebraTerminalComponent(kind, opId, charges));
        }

        // Algebra Forge terminals should not use the generic terminal icon.
        if (!isAlgebraTerminal && spriteLoader != null && spriteLoader.isLoaded()) {
            TextureRegion terminalSprite = spriteLoader.getTerminalSprite();
            if (terminalSprite != null) {
                terminalEntity.add(new SpriteComponent(terminalSprite, 15));
            }
        }

        // Optional: allow TMX to spawn a terminal hidden (for end-goal reveals).
        if (hidden != null && hidden) {
            CollisionComponent col = terminalEntity.getComponent(CollisionComponent.class);
            if (col != null) {
                col.width = 0;
                col.height = 0;
            }
            renderable.color = new Color(renderable.color.r, renderable.color.g, renderable.color.b, 0f);
        }

        engine.addEntity(terminalEntity);
    }

    /**
     * Creates a gate entity from Tiled map object.
     *
     * Gate tiles (ID 212) in the 'walls' layer are the visual band.
     * This entity sits over that band and controls when they block or open.
     */
    private void createGate(MapObject object, float x, float y) {
        String sourceArenaId = getPropertyString(object, "sourceArenaId", null);
        String targetArenaId = getPropertyString(object, "targetArenaId", null);
        String traversalSymbol = getPropertyString(object, "traversalSymbol", null);
        String group = getPropertyString(object, "group", null);
        Boolean open = getPropertyBoolean(object, "open", false);
        int gateTileId = getPropertyInt(object, "tileId", GameConstants.GATE_TILE_ID);

        float width = GameConstants.TILE_SIZE;
        float height = GameConstants.TILE_SIZE;
        if (object instanceof RectangleMapObject) {
            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            width = rect.width;
            height = rect.height;
        }

        Entity gateEntity = engine.createEntity();
        gateEntity.add(new PositionComponent(x, y));
        gateEntity.add(new CollisionComponent(width, height));
        gateEntity.add(new WallComponent()); // collision blocker

        GateComponent gateComp;
        if (sourceArenaId != null && targetArenaId != null) {
            gateComp = new GateComponent(sourceArenaId, targetArenaId);
        } else if (sourceArenaId != null) {
            gateComp = new GateComponent(sourceArenaId);
        } else {
            gateComp = new GateComponent();
        }

        gateComp.originalWidth = width;
        gateComp.originalHeight = height;
        gateComp.dimensionsStored = true;
        gateComp.traversalSymbol = traversalSymbol;
        gateEntity.add(gateComp);

        // Optional: gates can also be used as riddle-controlled barriers by providing a trigger group.
        if (group != null && !group.isEmpty()) {
            gateEntity.add(new GateDoorComponent(group));
            // Riddle gates should feel snappy.
            gateComp.openingDuration = 0f;
            // Respect optional initial state.
            if (open != null && open) {
                gateComp.openInstantly();
            } else {
                gateComp.close();
            }
        }

        engine.addEntity(gateEntity);
    }

    private void validateDoorRenderable(RenderableComponent renderable, float x, float y, String doorType) {
        if (renderable.color == null) {
            renderable.color = new Color(0.6f, 0.3f, 0.1f, 1.0f);
            System.out.println("Warning: " + doorType + " at (" + x + "," + y +
                    ") had null color - set to default brown");
        }

        if (renderable.width <= 0) {
            renderable.width = GameConstants.TILE_SIZE;
            System.out.println("Warning: " + doorType + " at (" + x + "," + y +
                    ") had zero width - set to TILE_SIZE");
        }
        if (renderable.height <= 0) {
            renderable.height = GameConstants.TILE_SIZE;
            System.out.println("Warning: " + doorType + " at (" + x + "," + y +
                    ") had zero height - set to TILE_SIZE");
        }

        if (renderable.color.a <= 0) {
            renderable.color.a = 1.0f;
            System.out.println("Warning: " + doorType + " at (" + x + "," + y +
                    ") had alpha=0 - set to 1.0 for visibility");
        }
    }
}
