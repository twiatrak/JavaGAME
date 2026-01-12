package com.timonipumba;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.files.FileHandle;
import com.timonipumba.assets.SpriteLoader;
import com.timonipumba.camera.CameraModeManager;
import com.timonipumba.level.GeneratedMapMode;
import com.timonipumba.level.GeneratedRunMode;
import com.timonipumba.level.LevelManager;
import com.timonipumba.level.PuzzleRegistry;
import com.timonipumba.systems.*;
import com.timonipumba.world.CorridorBuilder;
import com.timonipumba.world.AlgebraForgeTmxGenerator;
import com.timonipumba.world.GraphLightsOutTmxGenerator;
import com.timonipumba.world.RandomArenaTmxGenerator;
import com.timonipumba.world.RegisterAllocationTmxGenerator;
import com.timonipumba.world.TiledMapLoader;

import java.util.List;

/**
 * Tiled map-based game implementation.
 */
public class TiledMapGame extends ApplicationAdapter implements LevelManager {
    private Engine engine;
    private OrthographicCamera camera;
    private RenderingSystem renderingSystem;
    private HUDSystem hudSystem;
    private PuzzleOverlaySystem puzzleOverlaySystem;
    private DungeonThemeTraversalSystem dungeonThemeTraversalSystem;
    private GateSymbolLabelSystem gateSymbolLabelSystem;
    private ExitSystem exitSystem;
    private GameStateManager gameStateManager;
    private CameraModeManager cameraModeManager;
    private SpriteLoader spriteLoader;

    private TiledMap tiledMap;
    private OrthogonalTiledMapRenderer mapRenderer;

    private boolean levelLoadPending = false;

/** Hand-authored unified world map with fixed arenas (classpath resource). */
    private static final String UNIFIED_MAP = "maps/unified_world.tmx";

    // Default: play the four generated riddles in a campaign sequence.
    private static final GeneratedRunMode GENERATED_RUN_MODE = GeneratedRunMode.CAMPAIGN;
    private static final GeneratedMapMode SINGLE_GENERATED_MODE = GeneratedMapMode.TRAVERSAL_RIDDLE;

    private static final GeneratedMapMode[] CAMPAIGN_STAGES = new GeneratedMapMode[] {
            GeneratedMapMode.REGISTER_ALLOCATION_RIDDLE,
            GeneratedMapMode.LIGHTS_OUT_RIDDLE,
            GeneratedMapMode.ALGEBRA_FORGE_RIDDLE,
            GeneratedMapMode.TRAVERSAL_RIDDLE
    };

    private int campaignStageIndex = 0;

    private static String getGeneratedLocalOutputPath(GeneratedMapMode mode) {
        if (mode == null) {
            mode = GeneratedMapMode.TRAVERSAL_RIDDLE;
        }

        return switch (mode) {
            case TRAVERSAL_RIDDLE -> "desktop/maps/random_world.tmx";
            case REGISTER_ALLOCATION_RIDDLE -> "desktop/maps/register_allocation_world.tmx";
            case LIGHTS_OUT_RIDDLE -> "desktop/maps/lights_out_world.tmx";
            case ALGEBRA_FORGE_RIDDLE -> "desktop/maps/algebra_forge_world.tmx";
        };
    }

    private GeneratedMapMode getActiveGeneratedMode() {
        if (GENERATED_RUN_MODE != GeneratedRunMode.CAMPAIGN) {
            return SINGLE_GENERATED_MODE;
        }
        if (campaignStageIndex < 0) campaignStageIndex = 0;
        if (campaignStageIndex >= CAMPAIGN_STAGES.length) campaignStageIndex = 0;
        return CAMPAIGN_STAGES[campaignStageIndex];
    }

    private void advanceCampaignStage() {
        if (GENERATED_RUN_MODE != GeneratedRunMode.CAMPAIGN) return;
        campaignStageIndex++;
        if (campaignStageIndex >= CAMPAIGN_STAGES.length) {
            // Campaign complete: wrap back to start with fresh seeds.
            campaignStageIndex = 0;
            traversalRiddleSeed = System.currentTimeMillis();
            registerAllocationSeed = System.currentTimeMillis() + 11;
            lightsOutSeed = System.currentTimeMillis() + 22;
            algebraForgeSeed = System.currentTimeMillis() + 33;
        }
    }

    private RandomArenaTmxGenerator arenaGenerator;
    private RegisterAllocationTmxGenerator registerAllocationTmxGenerator;
    private GraphLightsOutTmxGenerator graphLightsOutTmxGenerator;
    private AlgebraForgeTmxGenerator algebraForgeTmxGenerator;
    private String generatedMapPath;

    // Seed for generated-map-only traversal riddles (kept per run/restart).
    private long traversalRiddleSeed = System.currentTimeMillis();

    // Seed for the register-allocation riddle map.
    private long registerAllocationSeed = System.currentTimeMillis();

    // Seed for the lights-out riddle map.
    private long lightsOutSeed = System.currentTimeMillis();

    // Seed for the algebra forge riddle map.
    private long algebraForgeSeed = System.currentTimeMillis();

    private void generateRandomTmx() {
        GeneratedMapMode mode = getActiveGeneratedMode();
        String localOutputPath = getGeneratedLocalOutputPath(mode);

        if (mode == GeneratedMapMode.TRAVERSAL_RIDDLE) {
            if (arenaGenerator == null) {
                arenaGenerator = new RandomArenaTmxGenerator();
            }
            FileHandle target = Gdx.files.local(localOutputPath);
            String absolutePath = target.file().getAbsolutePath();
            arenaGenerator.generateAndWrite(absolutePath, traversalRiddleSeed);
            generatedMapPath = absolutePath;
            return;
        }

        if (mode == GeneratedMapMode.LIGHTS_OUT_RIDDLE) {
            if (graphLightsOutTmxGenerator == null) {
                graphLightsOutTmxGenerator = new GraphLightsOutTmxGenerator();
            }
            FileHandle target = Gdx.files.local(localOutputPath);
            String absolutePath = target.file().getAbsolutePath();
            graphLightsOutTmxGenerator.generateAndWrite(absolutePath, lightsOutSeed);
            generatedMapPath = absolutePath;
            return;
        }

        if (mode == GeneratedMapMode.ALGEBRA_FORGE_RIDDLE) {
            if (algebraForgeTmxGenerator == null) {
                algebraForgeTmxGenerator = new AlgebraForgeTmxGenerator();
            }
            FileHandle target = Gdx.files.local(localOutputPath);
            String absolutePath = target.file().getAbsolutePath();
            algebraForgeTmxGenerator.generateAndWrite(absolutePath, algebraForgeSeed);
            generatedMapPath = absolutePath;
            return;
        }

        if (registerAllocationTmxGenerator == null) {
            registerAllocationTmxGenerator = new RegisterAllocationTmxGenerator();
        }
        FileHandle target = Gdx.files.local(localOutputPath);
        String absolutePath = target.file().getAbsolutePath();
        registerAllocationTmxGenerator.generateAndWrite(absolutePath, registerAllocationSeed);
        generatedMapPath = absolutePath;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 600);
        camera.zoom = GameConstants.EXPLORATION_ZOOM;

        gameStateManager = new GameStateManager();
        cameraModeManager = new CameraModeManager();

        spriteLoader = new SpriteLoader();
        spriteLoader.load();

        initializeGame();
    }

    private void initializeGame() {
        engine = new Engine();

        GeneratedMapMode activeMode = getActiveGeneratedMode();
        boolean enableTraversal = activeMode == GeneratedMapMode.TRAVERSAL_RIDDLE;
        boolean enableRegisterAllocation = activeMode == GeneratedMapMode.REGISTER_ALLOCATION_RIDDLE;
        boolean enableLightsOut = activeMode == GeneratedMapMode.LIGHTS_OUT_RIDDLE;
        boolean enableAlgebraForge = activeMode == GeneratedMapMode.ALGEBRA_FORGE_RIDDLE;
        boolean enableGateSymbolLabels = enableTraversal;
        boolean enableIntroOverlay = true;

        PlayerInputSystem playerInputSystem =
                new PlayerInputSystem(gameStateManager, spriteLoader, camera, cameraModeManager);
        EnemyAISystem enemyAISystem = new EnemyAISystem(gameStateManager, spriteLoader);

        puzzleOverlaySystem = new PuzzleOverlaySystem(gameStateManager);
        puzzleOverlaySystem.setLevelManager(this);
        playerInputSystem.setPuzzleOverlaySystem(puzzleOverlaySystem);

        exitSystem = new ExitSystem(gameStateManager);
        exitSystem.setLevelManager(this);

        // NOTE: In TMX mode, corridors are already authored/generated into the map layers.
        // Spawning corridor entities on gate-open creates an extra visible overlay that does not
        // exist in Tiled (and can look like "weird tiles").
        CorridorBuilder corridorBuilder = new CorridorBuilder(engine);
        corridorBuilder.setCorridorSprite(spriteLoader.getCorridorSprite());

        GateSystem gateSystem = new GateSystem(gameStateManager);
        gateSystem.setSpriteLoader(spriteLoader);
        // Do not wire CorridorBuilder for TMX maps.
        gateSystem.setLevelManager(this);

        puzzleOverlaySystem.setGateSystem(gateSystem);

        engine.addSystem(playerInputSystem);
        engine.addSystem(enemyAISystem);
        engine.addSystem(new ProjectileSystem(gameStateManager));
        engine.addSystem(new MovementSystem());
        engine.addSystem(new PickupSystem(gameStateManager));
        engine.addSystem(new TokenPickupSystem(gameStateManager));
        engine.addSystem(new CombatSystem(gameStateManager));

        RegisterAllocationSystem registerAllocationSystem = null;
        if (enableRegisterAllocation) {
            registerAllocationSystem = new RegisterAllocationSystem(gameStateManager);
            engine.addSystem(registerAllocationSystem);
        }

        GraphLightsOutSystem graphLightsOutSystem = null;
        if (enableLightsOut) {
            graphLightsOutSystem = new GraphLightsOutSystem(gameStateManager);
            engine.addSystem(graphLightsOutSystem);
        }

        engine.addSystem(new DoorSystem(gameStateManager));
        engine.addSystem(exitSystem);
        engine.addSystem(gateSystem);

        // Algebra forge terminals (forge/oracle) and their selection overlay.
        if (enableAlgebraForge) {
            AlgebraForgeSystem algebraForgeSystem = new AlgebraForgeSystem(gameStateManager);
            algebraForgeSystem.setGateSystem(gateSystem);
            engine.addSystem(algebraForgeSystem);
            engine.addSystem(new AlgebraForgeIntroOverlaySystem(gameStateManager));
        }

        if (enableTraversal) {
            dungeonThemeTraversalSystem = new DungeonThemeTraversalSystem(gameStateManager, gateSystem, traversalRiddleSeed);
            engine.addSystem(dungeonThemeTraversalSystem);

            if (enableGateSymbolLabels) {
                gateSymbolLabelSystem = new GateSymbolLabelSystem(gameStateManager, gateSystem, camera);
                engine.addSystem(gateSymbolLabelSystem);
            } else {
                gateSymbolLabelSystem = null;
            }
        } else {
            dungeonThemeTraversalSystem = null;
            gateSymbolLabelSystem = null;

            if (enableRegisterAllocation && enableIntroOverlay) {
                // Register-allocation riddle rules popup (shows at start; reopens on return to spawn).
                engine.addSystem(new RegisterAllocationIntroOverlaySystem(gameStateManager));
            }
            if (enableLightsOut && enableIntroOverlay) {
                engine.addSystem(new GraphLightsOutIntroOverlaySystem(gameStateManager));
            }
        }

        engine.addSystem(new GameStateSystem(gameStateManager));
        engine.addSystem(new AnimationSystem(gameStateManager));
        engine.addSystem(new CameraSystem(camera, cameraModeManager));

        renderingSystem = new RenderingSystem(camera);
        engine.addSystem(renderingSystem);
        hudSystem = new HUDSystem(gameStateManager);
        hudSystem.setGateSystem(gateSystem);
        hudSystem.setDungeonThemeTraversalSystem(dungeonThemeTraversalSystem);
        hudSystem.setRegisterAllocationSystem(registerAllocationSystem);
        engine.addSystem(hudSystem);

        // Puzzle overlay should render LAST so it sits above HUD/minimap.
        engine.addSystem(puzzleOverlaySystem);

        // Decide which map to use
        String mapPath;
        generateRandomTmx();
        mapPath = generatedMapPath;

        // Load the map using our resolver-based loader
        TiledMapLoader mapLoader = new TiledMapLoader(engine, spriteLoader);
        tiledMap = mapLoader.loadMap(mapPath);

        // Wire TiledMap into HUD so it can draw a true (tile-based) minimap.
        hudSystem.setTiledMap(tiledMap);

        // Wire TiledMap into GateSystem so it can clear gate tiles from 'walls' layer
        gateSystem.setTiledMap(tiledMap);

        // If we're in traversal-riddle generated mode, register arenas with GateSystem for lock-in logic.
        // Prefer registration coming from TMX objects (arena_bounds) to avoid coordinate mismatches.
        if (enableTraversal
            && arenaGenerator != null
            && gateSystem.getRegisteredArenaIds().isEmpty()) {
            List<RandomArenaTmxGenerator.ArenaInfo> arenas = arenaGenerator.getLastGeneratedArenas();

            // libGDX's TMX loading uses a bottom-left origin for world coordinates, while our
            // RandomArenaTmxGenerator uses tile Y indices in the same order as TMX CSV (top row first).
            // Convert arena bounds from generator tile space to world space by flipping Y.
            int mapHeightTiles = 0;
            if (tiledMap != null) {
                Object hObj = tiledMap.getProperties().get("height");
                if (hObj instanceof Integer) {
                    mapHeightTiles = (Integer) hObj;
                } else if (hObj instanceof String) {
                    try {
                        mapHeightTiles = Integer.parseInt((String) hObj);
                    } catch (NumberFormatException ignored) {
                        mapHeightTiles = 0;
                    }
                }
            }
            for (RandomArenaTmxGenerator.ArenaInfo arena : arenas) {
                // Register the inner walkable arena area (exclude 1-tile wall ring) so gates only lock
                // after the player has actually stepped inside.
                float width  = (RandomArenaTmxGenerator.ARENA_WIDTH_TILES  - 2) * GameConstants.TILE_SIZE;
                float height = (RandomArenaTmxGenerator.ARENA_HEIGHT_TILES - 2) * GameConstants.TILE_SIZE;

                float worldX = (arena.originX + 1) * GameConstants.TILE_SIZE;

                // Flip Y if we have map height; otherwise fall back to the old behaviour.
                // We want worldY to be the *bottom* of the inner arena rectangle.
                float worldY;
                if (mapHeightTiles > 0) {
                    int innerTopYTiles = arena.originY + 1;
                    int innerHeightTiles = RandomArenaTmxGenerator.ARENA_HEIGHT_TILES - 2;
                    int innerBottomYUpTiles = mapHeightTiles - (innerTopYTiles + innerHeightTiles);
                    worldY = innerBottomYUpTiles * GameConstants.TILE_SIZE;
                } else {
                    worldY = (arena.originY + 1) * GameConstants.TILE_SIZE;
                }

                gateSystem.registerArena(arena.id, worldX, worldY, width, height);
            }

            // Mark start arena as visited & current
            if (!arenas.isEmpty()) {
                String startId = arenas.get(0).id;
                gateSystem.setStartArena(startId);
            }
        }

        mapRenderer = new OrthogonalTiledMapRenderer(tiledMap, 1f);
        levelLoadPending = false;
    }

    @Override
    public void render() {
        if (gameStateManager.isGameOver() && Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            restartLevel();
        }

        if (levelLoadPending) {
            doLoadNextLevel();
            return;
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        mapRenderer.setView(camera);
        mapRenderer.render();

        engine.update(Gdx.graphics.getDeltaTime());
    }

    @Override
    public void loadNextLevel() {
        levelLoadPending = true;
    }

    @Override
    public void restartLevel() {
        disposeMapResources();
        renderingSystem.dispose();
        hudSystem.dispose();
        if (puzzleOverlaySystem != null) {
            puzzleOverlaySystem.dispose();
        }
        if (dungeonThemeTraversalSystem != null) {
            dungeonThemeTraversalSystem.dispose();
            dungeonThemeTraversalSystem = null;
        }
        if (gateSymbolLabelSystem != null) {
            gateSymbolLabelSystem.dispose();
            gateSymbolLabelSystem = null;
        }

        gameStateManager.reset();
        cameraModeManager.reset();
        PuzzleRegistry.clear();

        if (GENERATED_RUN_MODE == GeneratedRunMode.CAMPAIGN) {
            campaignStageIndex = 0;
            traversalRiddleSeed = System.currentTimeMillis();
            registerAllocationSeed = System.currentTimeMillis() + 11;
            lightsOutSeed = System.currentTimeMillis() + 22;
            algebraForgeSeed = System.currentTimeMillis() + 33;
        }

        initializeGame();
    }

    private void doLoadNextLevel() {
        levelLoadPending = false;

        if (GENERATED_RUN_MODE == GeneratedRunMode.CAMPAIGN) {
            GeneratedMapMode completed = getActiveGeneratedMode();
            advanceCampaignStage();
        }

        disposeMapResources();
        renderingSystem.dispose();
        hudSystem.dispose();
        if (puzzleOverlaySystem != null) {
            puzzleOverlaySystem.dispose();
        }
        if (dungeonThemeTraversalSystem != null) {
            dungeonThemeTraversalSystem.dispose();
            dungeonThemeTraversalSystem = null;
        }
        if (gateSymbolLabelSystem != null) {
            gateSymbolLabelSystem.dispose();
            gateSymbolLabelSystem = null;
        }

        gameStateManager.reset();
        cameraModeManager.reset();
        PuzzleRegistry.clear();

        initializeGame();
    }

    private void disposeMapResources() {
        if (mapRenderer != null) {
            mapRenderer.dispose();
            mapRenderer = null;
        }
        if (tiledMap != null) {
            tiledMap.dispose();
            tiledMap = null;
        }
    }

    @Override
    public void dispose() {
        disposeMapResources();
        renderingSystem.dispose();
        hudSystem.dispose();
        if (puzzleOverlaySystem != null) {
            puzzleOverlaySystem.dispose();
        }
        if (dungeonThemeTraversalSystem != null) {
            dungeonThemeTraversalSystem.dispose();
            dungeonThemeTraversalSystem = null;
        }
        if (gateSymbolLabelSystem != null) {
            gateSymbolLabelSystem.dispose();
            gateSymbolLabelSystem = null;
        }
        if (spriteLoader != null) {
            spriteLoader.dispose();
        }
    }
}
