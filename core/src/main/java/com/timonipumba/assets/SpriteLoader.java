package com.timonipumba.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.timonipumba.components.EnemyStatsComponent.EnemyType;
import com.timonipumba.components.ItemType;

/**
 * Centralized sprite loading from both the Kenney roguelike tileset and dedicated PNG sprites.
 * 
 * This class loads TextureRegions from:
 * 1. Dedicated PNG sprites under sprites/ directory (preferred for player, enemies, projectiles)
 * 2. Existing tileset image for fallback and items
 * 
 * Sprite access for:
 * - Player (player.png from sprites/, or fallback to tileset/dedicated 20x32 PNG)
 * - Enemies (enemy1.png, enemy2.png, enemy3.png for different types, fallback to tileset)
 * - Items (POTION, BUFF, TREASURE from tileset)
 * - Projectiles (bullet_2.png from sprites/, fallback to tileset)
 * 
 * The tileset is the Kenney roguelike tileset (968x526 pixels, 16x16 tiles).
 * Sprite positions are defined based on that sheet layout.
 * 
 * Usage:
 *   SpriteLoader loader = new SpriteLoader();
 *   TextureRegion playerSprite = loader.getPlayerSprite();
 *   TextureRegion playerFullSprite = loader.getPlayerFullSprite(); // 20x32 dedicated sprite
 *   TextureRegion enemySprite = loader.getEnemySprite(EnemyType.BRUTE);
 *   loader.dispose(); // Call when done (typically on game shutdown)
 */
public class SpriteLoader {
    
    private static final String TILESET_PATH = "tilesets/img/roguelikeSheet_magenta.png";
    private static final String PLAYER_FULL_SPRITE_PATH = "tilesets/img/playersprite.png.png";
    private static final int TILE_SIZE = 16;
    
    // New sprite paths in sprites/ directory
    private static final String SPRITES_PLAYER_PATH = "sprites/player.png";
    private static final String SPRITES_ENEMY1_PATH = "sprites/enemy1.png";
    private static final String SPRITES_ENEMY2_PATH = "sprites/enemy2.png";
    private static final String SPRITES_ENEMY3_PATH = "sprites/enemy3.png";
    private static final String SPRITES_BULLET_PATH = "sprites/bullet_2.png";
    private static final String SPRITES_TERMINAL_PATH = "sprites/terminal.png";
    private static final String SPRITES_TOKEN_KEY_PATH = "sprites/keys_1_1.png";
    private static final String SPRITES_SOCKET_CHEST_PATH = "sprites/mini_chest_1.png";
    
    // Tileset spacing and margin settings.
    // The Kenney roguelike sheet (968x526) has no spacing between tiles.
    // If editing the tileset in Tiled, ensure these values match the TSX metadata.
    // Note: Spacing = pixels between adjacent tiles, Margin = pixels at tileset edges.
    private static final int SPACING = 0;
    private static final int MARGIN = 0;
    
    // Tileset is 968 pixels wide / 16 = 60 tiles per row (with spacing=0)
    // Tileset is 526 pixels tall / 16 = ~32 rows
    private static final int TILES_PER_ROW = 60;
    
    private Texture tileset;
    private Texture playerFullTexture;
    private boolean loaded = false;
    
    // Textures for new sprites from sprites/ directory
    private Texture spritesPlayerTexture;
    private Texture spritesEnemy1Texture;
    private Texture spritesEnemy2Texture;
    private Texture spritesEnemy3Texture;
    private Texture spritesBulletTexture;
    private Texture spritesTerminalTexture;
    private Texture spritesTokenKeyTexture;
    private Texture spritesSocketChestTexture;
    
    // Cached sprite regions from sprites/ directory
    private TextureRegion spritesPlayerSprite;
    private TextureRegion spritesEnemy1Sprite;
    private TextureRegion spritesEnemy2Sprite;
    private TextureRegion spritesEnemy3Sprite;
    private TextureRegion spritesBulletSprite;
    private TextureRegion spritesTerminalSprite;
    private TextureRegion spritesTokenKeySprite;
    private TextureRegion spritesSocketChestSprite;
    
    // Cached sprite regions from tileset (fallback)
    private TextureRegion playerSprite;
    private TextureRegion playerFullSprite;
    private TextureRegion enemyDefaultSprite;
    private TextureRegion enemyBruteSprite;
    private TextureRegion enemyScoutSprite;
    private TextureRegion enemyRangerSprite;
    private TextureRegion potionSprite;
    private TextureRegion buffSprite;
    private TextureRegion treasureSprite;
    private TextureRegion projectileSprite;
    private TextureRegion wallSprite;
    private TextureRegion gateClosedSprite;
    private TextureRegion gateOpenSprite;
    private TextureRegion corridorSprite;
    
    /**
     * Create the sprite loader but don't load textures yet.
     * Call load() to actually load textures.
     */
    public SpriteLoader() {}
    
    /**
     * Load all sprite textures from the tileset.
     * Must be called after LibGDX is initialized (in create() method).
     */
    public void load() {
        if (loaded) return;
        
        try {
            tileset = new Texture(Gdx.files.internal(TILESET_PATH));
            loadSpriteRegions();
            loaded = true;
        } catch (Exception e) {
            System.err.println("Failed to load tileset: " + e.getMessage());
            // Sprites will remain null, RenderingSystem will fall back to colored rectangles
        }
        
        // Load the dedicated 20x32 player sprite (separate try-catch to allow partial loading)
        try {
            playerFullTexture = new Texture(Gdx.files.internal(PLAYER_FULL_SPRITE_PATH));
            playerFullTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            playerFullSprite = new TextureRegion(playerFullTexture);
        } catch (Exception e) {
            System.err.println("SpriteLoader: Failed to load dedicated player sprite: " + e.getMessage());
            playerFullSprite = null;
        }
        
        // Load new PNG sprites from sprites/ directory
        loadSpritesFromDirectory();
    }
    
    /**
     * Attempt to load a texture from the given path.
     * @param path The path to try loading from (via Gdx.files.internal)
     * @return The loaded Texture, or null if loading failed
     */
    private Texture tryLoadTexture(String path) {
        try {
            FileHandle file = Gdx.files.internal(path);
            if (file.exists()) {
                Texture texture = new Texture(file);
                texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                return texture;
            }
        } catch (Exception e) {
            System.err.println("SpriteLoader: Failed to load texture from " + path + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Load sprites from the sprites/ directory.
     * These are the dedicated PNG files for player, enemies, and projectiles.
     */
    private void loadSpritesFromDirectory() {
        // Load player sprite
        spritesPlayerTexture = tryLoadTexture(SPRITES_PLAYER_PATH);
        if (spritesPlayerTexture != null) {
            spritesPlayerSprite = new TextureRegion(spritesPlayerTexture);
        }
        
        // Load enemy sprites
        spritesEnemy1Texture = tryLoadTexture(SPRITES_ENEMY1_PATH);
        if (spritesEnemy1Texture != null) {
            spritesEnemy1Sprite = new TextureRegion(spritesEnemy1Texture);
        }
        
        spritesEnemy2Texture = tryLoadTexture(SPRITES_ENEMY2_PATH);
        if (spritesEnemy2Texture != null) {
            spritesEnemy2Sprite = new TextureRegion(spritesEnemy2Texture);
        }
        
        spritesEnemy3Texture = tryLoadTexture(SPRITES_ENEMY3_PATH);
        if (spritesEnemy3Texture != null) {
            spritesEnemy3Sprite = new TextureRegion(spritesEnemy3Texture);
        }
        
        // Load bullet/projectile sprite
        spritesBulletTexture = tryLoadTexture(SPRITES_BULLET_PATH);
        if (spritesBulletTexture != null) {
            spritesBulletSprite = new TextureRegion(spritesBulletTexture);
        }
        
        // Load terminal sprite
        spritesTerminalTexture = tryLoadTexture(SPRITES_TERMINAL_PATH);
        if (spritesTerminalTexture != null) {
            spritesTerminalSprite = new TextureRegion(spritesTerminalTexture);
        }

        // Load token key sprite
        spritesTokenKeyTexture = tryLoadTexture(SPRITES_TOKEN_KEY_PATH);
        if (spritesTokenKeyTexture != null) {
            spritesTokenKeySprite = new TextureRegion(spritesTokenKeyTexture);
        }

        // Load socket chest sprite
        spritesSocketChestTexture = tryLoadTexture(SPRITES_SOCKET_CHEST_PATH);
        if (spritesSocketChestTexture != null) {
            spritesSocketChestSprite = new TextureRegion(spritesSocketChestTexture);
        }
    }
    
    /**
     * Load individual sprite regions from the tileset.
     * Tile positions are based on examining the Kenney roguelike sheet.
     * 
     * The sheet layout (approximate positions for common roguelike sprites):
     * - Characters: Rows 0-3 (knights, wizards, rogues, etc.)
     * - Monsters: Rows 4-10 (orcs, skeletons, demons, etc.)
     * - Items: Rows 14-18 (potions, scrolls, coins, etc.)
     * - Weapons: Rows 19-22 (swords, bows, arrows, etc.)
     * - Environment: Rows 23+ (walls, floors, doors, etc.)
     */
    private void loadSpriteRegions() {
        // Player sprite: knight character (row 0, column 28 - a typical player character)
        playerSprite = getRegion(28, 0);
        
        // Enemy sprites: various monster types
        // Default enemy: skeleton/basic monster (row 6, column 28)
        enemyDefaultSprite = getRegion(28, 6);
        
        // Brute enemy: orc/larger monster (row 6, column 32)
        enemyBruteSprite = getRegion(32, 6);
        
        // Scout enemy: bat/fast creature (row 6, column 24)
        enemyScoutSprite = getRegion(24, 6);
        
        // Ranger enemy: archer/ranged monster (row 6, column 36)
        enemyRangerSprite = getRegion(36, 6);
        
        // Item sprites
        // Potion: red flask (row 15, column 0)
        potionSprite = getRegion(0, 15);
        
        // Buff: blue flask (row 15, column 1)
        buffSprite = getRegion(1, 15);
        
        // Treasure: gold coin/chest (row 14, column 4)
        treasureSprite = getRegion(4, 14);
        
        // Projectile: arrow (row 20, column 8)
        projectileSprite = getRegion(8, 20);
        
        // Wall: brick/stone (row 25, column 0)
        wallSprite = getRegion(0, 25);
        
        // Gate sprites: doors from environment tiles
        // Closed gate: tile ID 212 from Tiled (1-based ID)
        // Tile ID calculation: For 60-column tileset with 1-based IDs
        // tile ID 212: (212 - 1) / 60 = row 3, (212 - 1) % 60 = col 31 (0-based)
        // The requirements reference col 41 row 3, which would be tile ID 222
        // Using tile 212 as specified in the gate layer of puzzle_example.tmx
        gateClosedSprite = getRegion(31, 3);
        
        // Open gate: floor tile (tile 921) for clear passage (same as corridor/floor)
        // When gate opens, it should visually become floor tiles
        // Tile 921: (921 - 1) / 60 = row 15, (921 - 1) % 60 = col 20
        gateOpenSprite = getRegion(20, 15);
        
        // Corridor sprite: floor tile (tile 921 - same as open gate)
        corridorSprite = getRegion(20, 15);
    }
    
    /**
     * Get a TextureRegion from the tileset at the given tile position.
     * Respects MARGIN and SPACING settings for proper tile alignment.
     * 
     * @param tileX Column index (0-based)
     * @param tileY Row index (0-based, from top)
     * @return TextureRegion for that tile
     */
    private TextureRegion getRegion(int tileX, int tileY) {
        if (tileset == null) return null;
        // Calculate pixel position accounting for margin and spacing
        // x = margin + col * (tileWidth + spacing)
        int x = MARGIN + tileX * (TILE_SIZE + SPACING);
        int y = MARGIN + tileY * (TILE_SIZE + SPACING);
        return new TextureRegion(tileset, x, y, TILE_SIZE, TILE_SIZE);
    }
    
    /**
     * Get the player sprite.
     * Prefers the sprites/player.png sprite, falls back to tileset.
     * @return Player TextureRegion, or null if not loaded
     */
    public TextureRegion getPlayerSprite() {
        // Prefer the new sprites/ directory player sprite
        if (spritesPlayerSprite != null) {
            return spritesPlayerSprite;
        }
        return playerSprite;
    }
    
    /**
     * Get the dedicated 20x32 player sprite for Tiled map mode.
     * This returns the full player PNG asset rather than a 16x16 tile.
     * Prefers sprites/player.png if available.
     * @return Player 20x32 TextureRegion, or null if not loaded
     */
    public TextureRegion getPlayerFullSprite() {
        // Prefer the new sprites/ directory player sprite
        if (spritesPlayerSprite != null) {
            return spritesPlayerSprite;
        }
        return playerFullSprite;
    }
    
    /**
     * Get an enemy sprite based on enemy type.
     * Prefers sprites from sprites/ directory (enemy1.png, enemy2.png, enemy3.png).
     * Mapping:
     * - DEFAULT/BRUTE -> enemy1.png (or tileset fallback)
     * - SCOUT -> enemy2.png (or tileset fallback)
     * - RANGER -> enemy3.png (or tileset fallback)
     * @param enemyType The type of enemy
     * @return Enemy TextureRegion, or null if not loaded
     */
    public TextureRegion getEnemySprite(EnemyType enemyType) {
        if (enemyType == null) {
            // Default: prefer enemy1 sprite
            if (spritesEnemy1Sprite != null) {
                return spritesEnemy1Sprite;
            }
            return enemyDefaultSprite;
        }
        
        switch (enemyType) {
            case BRUTE:
                // Brute uses enemy1.png (big, tanky appearance)
                if (spritesEnemy1Sprite != null) {
                    return spritesEnemy1Sprite;
                }
                return enemyBruteSprite;
            case SCOUT:
                // Scout uses enemy2.png (fast, light appearance)
                if (spritesEnemy2Sprite != null) {
                    return spritesEnemy2Sprite;
                }
                return enemyScoutSprite;
            case RANGER:
                // Ranger uses enemy3.png (ranged attacker appearance)
                if (spritesEnemy3Sprite != null) {
                    return spritesEnemy3Sprite;
                }
                return enemyRangerSprite;
            default:
                // Default uses enemy1.png
                if (spritesEnemy1Sprite != null) {
                    return spritesEnemy1Sprite;
                }
                return enemyDefaultSprite;
        }
    }
    
    /**
     * Get an item sprite based on item type.
     * @param itemType The type of item
     * @return Item TextureRegion, or null if not loaded
     */
    public TextureRegion getItemSprite(ItemType itemType) {
        if (itemType == null) return potionSprite;
        
        switch (itemType) {
            case BUFF:
                return buffSprite;
            case TREASURE:
                return treasureSprite;
            case POTION:
            default:
                return potionSprite;
        }
    }
    
    /**
     * Get the projectile sprite (arrow/bolt).
     * Prefers sprites/bullet_2.png, falls back to tileset.
     * @return Projectile TextureRegion, or null if not loaded
     */
    public TextureRegion getProjectileSprite() {
        // Prefer the new sprites/ directory bullet sprite
        if (spritesBulletSprite != null) {
            return spritesBulletSprite;
        }
        return projectileSprite;
    }
    
    /**
     * Get the wall sprite.
     * @return Wall TextureRegion, or null if not loaded
     */
    public TextureRegion getWallSprite() {
        return wallSprite;
    }
    
    /**
     * Get the closed gate sprite.
     * @return Closed gate TextureRegion, or null if not loaded
     */
    public TextureRegion getGateClosedSprite() {
        return gateClosedSprite;
    }
    
    /**
     * Get the open gate sprite.
     * @return Open gate TextureRegion, or null if not loaded
     */
    public TextureRegion getGateOpenSprite() {
        return gateOpenSprite;
    }
    
    /**
     * Get the corridor/floor sprite.
     * @return Corridor TextureRegion, or null if not loaded
     */
    public TextureRegion getCorridorSprite() {
        return corridorSprite;
    }
    
    /**
     * Get the terminal sprite.
     * @return Terminal TextureRegion, or null if not loaded
     */
    public TextureRegion getTerminalSprite() {
        return spritesTerminalSprite;
    }

    /**
     * Default sprite for collectible puzzle tokens.
     */
    public TextureRegion getTokenKeySprite() {
        return spritesTokenKeySprite;
    }

    /**
     * Default sprite for token sockets/turn-in points.
     */
    public TextureRegion getSocketChestSprite() {
        return spritesSocketChestSprite;
    }
    
    /**
     * Check if sprites have been loaded.
     * @return true if load() has been called successfully
     */
    public boolean isLoaded() {
        return loaded;
    }
    
    // ========== FRAME STRIP HELPERS FOR ANIMATIONS ==========
    
    /**
     * Load a horizontal frame strip from the tileset.
     * Frames are laid out left-to-right starting from the given tile position.
     * 
     * @param startTileX Starting column index (0-based)
     * @param tileY Row index (0-based, from top)
     * @param frameCount Number of frames to extract
     * @return Array of TextureRegions, or empty array if tileset not loaded
     */
    public TextureRegion[] getFrameStripFromTileset(int startTileX, int tileY, int frameCount) {
        if (tileset == null) {
            return new TextureRegion[0];
        }
        
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = getRegion(startTileX + i, tileY);
        }
        return frames;
    }
    
    /**
     * Load a horizontal frame strip from a dedicated sprite sheet image.
     * All frames are assumed to be the same size and laid out horizontally.
     * 
     * <p><b>Note:</b> This method loads textures synchronously, which may cause
     * frame drops during gameplay. For best results, call this method during
     * game initialization or loading screens. For more complex asset loading needs,
     * consider using LibGDX's AssetManager for asynchronous loading.</p>
     * 
     * @param path Path to the sprite sheet image (internal file path)
     * @param frameWidth Width of each frame in pixels
     * @param frameHeight Height of each frame in pixels
     * @param frameCount Number of frames to extract
     * @return Array of TextureRegions, or empty array if loading fails
     */
    public TextureRegion[] loadFrameStrip(String path, int frameWidth, int frameHeight, int frameCount) {
        try {
            FileHandle file = Gdx.files.internal(path);
            if (!file.exists()) {
                System.err.println("SpriteLoader: Frame strip file not found: " + path);
                return new TextureRegion[0];
            }
            
            Texture texture = new Texture(file);
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            
            TextureRegion[] frames = new TextureRegion[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = new TextureRegion(texture, i * frameWidth, 0, frameWidth, frameHeight);
            }
            return frames;
        } catch (Exception e) {
            System.err.println("SpriteLoader: Failed to load frame strip from " + path + ": " + e.getMessage());
            return new TextureRegion[0];
        }
    }
    
    /**
     * Create a single-element frame array from an existing TextureRegion.
     * Useful for creating animation data from static sprites.
     * 
     * @param region The TextureRegion to wrap
     * @return Single-element array containing the region, or empty array if null
     */
    public static TextureRegion[] wrapAsFrameStrip(TextureRegion region) {
        if (region == null) {
            return new TextureRegion[0];
        }
        return new TextureRegion[] { region };
    }
    
    /**
     * Split a TextureRegion into a grid of frames.
     * Useful for loading sprite sheets that contain multiple rows of animations.
     * 
     * @param region The source TextureRegion to split
     * @param tileWidth Width of each frame in pixels
     * @param tileHeight Height of each frame in pixels
     * @return 2D array of frames [row][column], or empty array if region is null
     */
    public static TextureRegion[][] splitIntoGrid(TextureRegion region, int tileWidth, int tileHeight) {
        if (region == null) {
            return new TextureRegion[0][0];
        }
        
        int cols = region.getRegionWidth() / tileWidth;
        int rows = region.getRegionHeight() / tileHeight;
        
        TextureRegion[][] grid = new TextureRegion[rows][cols];
        
        int regionX = region.getRegionX();
        int regionY = region.getRegionY();
        
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                grid[row][col] = new TextureRegion(
                    region.getTexture(),
                    regionX + col * tileWidth,
                    regionY + row * tileHeight,
                    tileWidth,
                    tileHeight
                );
            }
        }
        
        return grid;
    }
    
    /**
     * Dispose of loaded texture resources.
     * Call this when the game shuts down.
     */
    public void dispose() {
        if (tileset != null) {
            tileset.dispose();
            tileset = null;
        }
        if (playerFullTexture != null) {
            playerFullTexture.dispose();
            playerFullTexture = null;
        }
        playerFullSprite = null;
        
        // Dispose new sprites from sprites/ directory
        if (spritesPlayerTexture != null) {
            spritesPlayerTexture.dispose();
            spritesPlayerTexture = null;
        }
        spritesPlayerSprite = null;
        
        if (spritesEnemy1Texture != null) {
            spritesEnemy1Texture.dispose();
            spritesEnemy1Texture = null;
        }
        spritesEnemy1Sprite = null;
        
        if (spritesEnemy2Texture != null) {
            spritesEnemy2Texture.dispose();
            spritesEnemy2Texture = null;
        }
        spritesEnemy2Sprite = null;
        
        if (spritesEnemy3Texture != null) {
            spritesEnemy3Texture.dispose();
            spritesEnemy3Texture = null;
        }
        spritesEnemy3Sprite = null;
        
        if (spritesBulletTexture != null) {
            spritesBulletTexture.dispose();
            spritesBulletTexture = null;
        }
        spritesBulletSprite = null;
        
        if (spritesTerminalTexture != null) {
            spritesTerminalTexture.dispose();
            spritesTerminalTexture = null;
        }
        spritesTerminalSprite = null;

        if (spritesTokenKeyTexture != null) {
            spritesTokenKeyTexture.dispose();
            spritesTokenKeyTexture = null;
        }
        spritesTokenKeySprite = null;

        if (spritesSocketChestTexture != null) {
            spritesSocketChestTexture.dispose();
            spritesSocketChestTexture = null;
        }
        spritesSocketChestSprite = null;
        
        loaded = false;
    }
}
