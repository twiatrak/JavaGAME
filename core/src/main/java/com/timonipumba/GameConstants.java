package com.timonipumba;

/**
 * Central location for all game constants and tuning values.
 * 
 * Modify these values to balance the game:
 * - Health and damage values affect combat difficulty
 * - Vision range affects how aggressively enemies pursue the player
 * - Movement speeds affect overall game pacing
 */
public final class GameConstants {
    
    private GameConstants() {} // Prevent instantiation
    
    // ========== TILE SETTINGS ==========
    /** Size of a single tile in pixels */
    public static final int TILE_SIZE = 16;
    
    // ========== PLAYER SPRITE SETTINGS ==========
    /** Width of the dedicated player sprite in pixels (20x32 PNG) */
    public static final int PLAYER_SPRITE_WIDTH = 20;
    
    /** Height of the dedicated player sprite in pixels (20x32 PNG) */
    public static final int PLAYER_SPRITE_HEIGHT = 32;
    
    // ========== PLAYER COLLISION SETTINGS ==========
    /** Player collision box width in pixels (32x32 for larger player) */
    public static final int PLAYER_COLLISION_WIDTH = 32;
    
    /** Player collision box height in pixels (32x32 for larger player) */
    public static final int PLAYER_COLLISION_HEIGHT = 32;
    
    // ========== PLAYER SETTINGS ==========
    /** Player maximum health points */
    public static final int PLAYER_MAX_HEALTH = 100;
    
    /** Player movement speed (pixels per second) */
    public static final float PLAYER_SPEED = 100f;
    
    /** Damage player deals when bumping into enemy */
    public static final int PLAYER_ATTACK_DAMAGE = 10;
    
    // ========== ENEMY BASE SETTINGS ==========
    /** Default enemy maximum health points */
    public static final int ENEMY_DEFAULT_MAX_HEALTH = 30;
    
    /** Default enemy movement speed (pixels per second) */
    public static final float ENEMY_DEFAULT_SPEED = 50f;
    
    /** Default enemy attack damage */
    public static final int ENEMY_DEFAULT_ATTACK_DAMAGE = 5;
    
    /** Default enemy attack cooldown in seconds */
    public static final float ENEMY_DEFAULT_ATTACK_COOLDOWN = 1.0f;
    
    /** Default vision range for enemy detection (pixels) */
    public static final float ENEMY_DEFAULT_VISION_RANGE = 100f;
    
    // ========== ENEMY ARCHETYPE: BRUTE ==========
    /**
     * BRUTE: Slow, dangerous tank.
     * - High HP: Can take several hits before dying.
     * - High damage: Punishing if player gets caught.
     * - Slow movement: Player can kite and dodge.
     * - Longer attack cooldown: Gives player windows to counterattack.
     * - Smaller aggro radius: Can be avoided if player is careful.
     * Movement behavior: Direct chase toward player (simple pursuit).
     */
    public static final int BRUTE_MAX_HEALTH = 50;
    public static final float BRUTE_SPEED = 30f;
    public static final int BRUTE_ATTACK_DAMAGE = 8;
    public static final float BRUTE_VISION_RANGE = 80f;
    /** Attack cooldown for brute melee attacks in seconds */
    public static final float BRUTE_ATTACK_COOLDOWN = 1.5f;
    /** Aggro radius for brute - smaller means they can be avoided */
    public static final float BRUTE_AGGRO_RADIUS = 100f;
    
    // ========== ENEMY ARCHETYPE: SCOUT ==========
    /**
     * SCOUT: Fast, fragile harasser.
     * - Low HP: Dies quickly when caught.
     * - Low damage: Not deadly alone, dangerous in groups.
     * - Very fast movement: Hard to escape, good at flanking.
     * - Short attack cooldown: Constant pressure.
     * - Large aggro radius: Detects player from far away.
     * Movement behavior: Flanking/strafing - approaches at angles, 
     * periodically moves perpendicular to player direction.
     */
    public static final int SCOUT_MAX_HEALTH = 20;
    public static final float SCOUT_SPEED = 80f;
    public static final int SCOUT_ATTACK_DAMAGE = 3;
    public static final float SCOUT_VISION_RANGE = 150f;
    /** Attack cooldown for scout melee attacks in seconds - faster attacks */
    public static final float SCOUT_ATTACK_COOLDOWN = 0.6f;
    /** Aggro radius for scout - larger, they spot player quickly */
    public static final float SCOUT_AGGRO_RADIUS = 180f;
    /** Time between scout direction changes when strafing (seconds) */
    public static final float SCOUT_STRAFE_INTERVAL = 1.0f;
    
    // ========== ENEMY ARCHETYPE: RANGER ==========
    /**
     * RANGER: Positional threat with readable ranged attacks.
     * - Moderate HP: Can take a few hits but not tanky.
     * - Ranged damage: Forces player to close distance or use cover.
     * - Moderate movement: Not too fast, not too slow.
     * - Attack cooldown tuned to be threatening but dodgeable.
     * - Windup animation before firing: Telegraphed attacks.
     * Movement behavior: Maintains preferred distance band (4-10 tiles).
     * If too close: backs away. If too far: moves closer.
     */
    public static final int RANGER_MAX_HEALTH = 25;
    public static final float RANGER_SPEED = 40f;
    public static final int RANGER_ATTACK_DAMAGE = 4;
    public static final float RANGER_VISION_RANGE = 200f;
    /** Preferred distance rangers try to maintain from player (pixels) */
    public static final float RANGER_PREFERRED_DISTANCE = 80f;
    /** Minimum distance rangers try to maintain - back away if closer */
    public static final float RANGER_MIN_DISTANCE = 64f;
    /** Maximum preferred distance - move closer if farther */
    public static final float RANGER_MAX_DISTANCE = 160f;
    /** Cooldown between ranger shots in seconds */
    public static final float RANGER_ATTACK_COOLDOWN = 2.0f;
    /** Aggro radius for ranger - large since they attack from distance */
    public static final float RANGER_AGGRO_RADIUS = 200f;
    
    // ========== RANGED ATTACK SETTINGS ==========
    /** Damage dealt by player ranged attacks */
    public static final int RANGED_ATTACK_DAMAGE = 8;
    
    /** Speed of projectiles in pixels per second */
    public static final float PROJECTILE_SPEED = 200f;
    
    /** Maximum lifetime of projectiles in seconds */
    public static final float PROJECTILE_LIFETIME = 2.0f;
    
    /** Cooldown between player ranged attacks in seconds */
    public static final float RANGED_ATTACK_COOLDOWN = 0.5f;
    
    /** Size of projectile collision box */
    public static final float PROJECTILE_SIZE = 8f;
    
    // ========== ITEMS ==========
    /** Health restored by a healing potion */
    public static final int POTION_HEAL_AMOUNT = 25;
    
    // ========== DUNGEON GENERATION ==========
    /** Default dungeon width in tiles */
    public static final int DUNGEON_WIDTH = 60;
    
    /** Default dungeon height in tiles */
    public static final int DUNGEON_HEIGHT = 40;
    
    /** Probability of spawning an enemy in each room (0.0 to 1.0) */
    public static final float ENEMY_SPAWN_PROBABILITY = 0.5f;
    
    /** Probability of spawning an item in each room (0.0 to 1.0) */
    public static final float ITEM_SPAWN_PROBABILITY = 0.3f;
    
    // ========== CAMERA SETTINGS ==========
    /** Zoom level for EXPLORATION mode (wider view, 1.0 = normal) */
    public static final float EXPLORATION_ZOOM = 1.0f;
    
    /** Zoom level for FIGHT mode (more zoomed in, lower value = closer view) */
    public static final float FIGHT_ZOOM = 0.6f;
    
    /** Speed of zoom interpolation (units per second, higher = faster transition) */
    public static final float ZOOM_LERP_SPEED = 3.0f;
    
    /** Distance threshold for automatic fight mode detection (pixels) */
    public static final float FIGHT_MODE_ENEMY_DISTANCE = 150f;
    
    // ========== HIT FEEDBACK SETTINGS ==========
    /** Duration of hit flash effect for enemies (seconds) */
    public static final float HIT_FLASH_DURATION_ENEMY = 0.15f;
    
    /** Duration of hit flash effect for player (seconds) */
    public static final float HIT_FLASH_DURATION_PLAYER = 0.2f;
    
    // ========== CAMERA SHAKE SETTINGS ==========
    /** Intensity of camera shake when player takes damage (pixels) */
    public static final float CAMERA_SHAKE_INTENSITY_HIT = 4f;
    
    /** Duration of camera shake when player takes damage (seconds) */
    public static final float CAMERA_SHAKE_DURATION_HIT = 0.2f;
    
    /** Intensity of camera shake when enemy is killed (pixels) */
    public static final float CAMERA_SHAKE_INTENSITY_KILL = 2f;
    
    /** Duration of camera shake when enemy is killed (seconds) */
    public static final float CAMERA_SHAKE_DURATION_KILL = 0.1f;
    
    // ========== RANGER WINDUP SETTINGS ==========
    /** Duration of ranged attack windup before firing (seconds) */
    public static final float RANGER_WINDUP_TIME = 0.3f;
    
    // ========== PUZZLE ELEMENT SETTINGS ==========
    /** Distance the player must be within to interact with a switch (pixels) */
    public static final float SWITCH_INTERACTION_RADIUS = 24f;
    
    /** Distance the player must be within to interact with a puzzle door (pixels) */
    public static final float PUZZLE_DOOR_INTERACTION_RADIUS = 24f;
    
    /** Distance the player must be within to interact with a terminal (pixels) */
    public static final float TERMINAL_INTERACTION_RADIUS = 24f;
    
    /** Default duration before a momentary switch auto-resets (seconds) */
    public static final float SWITCH_MOMENTARY_DURATION = 2.0f;
    
    /** Default group name for switches/doors when not specified in Tiled */
    public static final String DEFAULT_PUZZLE_GROUP = "default";
    
    /** Default puzzle type when not specified in Tiled (cipher, binary, hex, etc.) */
    public static final String DEFAULT_PUZZLE_TYPE = "cipher";
    
    // ========== GATE AND CORRIDOR SETTINGS ==========
    
    /** Tile ID for closed gates in TMX walls layer (ID 212) */
    public static final int GATE_TILE_ID = 212;
    
    // ===== Generated-map traversal symbol markers =====
    // These are TMX global tile IDs (gid) in the roguelike tileset.
    // The player requested tile id 1489 for corridor letter marking.
    // If you have distinct tiles for H vs V, set these to different gids.
    public static final int CORRIDOR_SYMBOL_H_TILE_GID = 1489;
    public static final int CORRIDOR_SYMBOL_V_TILE_GID = 1490;
    
    // Dedicated non-colliding layer name used by RandomArenaTmxGenerator.
    public static final String SYMBOLS_LAYER_NAME = "symbols";
    
    /** Tile ID for floor tiles (ID 921) */
    public static final int FLOOR_TILE_ID = 921;
    
    /** Render order for corridor/floor tiles (above background) */
    public static final int RENDER_ORDER_CORRIDOR = 2;
    
    /** Render order for closed gates (above floor, below characters) */
    public static final int RENDER_ORDER_GATE_CLOSED = 5;
    
    /** Render order for open gates (above floor, below characters) */
    public static final int RENDER_ORDER_GATE_OPEN = 4;
    
    /** Duration of gate opening animation in seconds */
    public static final float GATE_OPENING_DURATION = 0.5f;
    
    /** Default corridor width in tiles */
    public static final int DEFAULT_CORRIDOR_WIDTH = 2;
    
}
