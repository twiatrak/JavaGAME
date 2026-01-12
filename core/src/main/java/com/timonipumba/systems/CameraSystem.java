package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.timonipumba.GameConstants;
import com.timonipumba.camera.CameraMode;
import com.timonipumba.camera.CameraModeManager;
import com.timonipumba.components.CameraShakeComponent;
import com.timonipumba.components.PlayerComponent;
import com.timonipumba.components.PositionComponent;

/**
 * System that makes the camera follow the player and handles camera mode zoom transitions.
 * 
 * Supports two camera modes:
 * - EXPLORATION: Wider view (zoom = EXPLORATION_ZOOM, typically 1.0)
 * - FIGHT: Tighter view (zoom = FIGHT_ZOOM, typically 0.6)
 * 
 * Camera shake:
 * - Reads CameraShakeComponent from player entity
 * - Applies random offset to camera position while shake is active
 * - Shake intensity is reduced in EXPLORATION mode
 * 
 * Zoom transitions are smoothly interpolated using lerp to avoid jarring visual changes.
 */
public class CameraSystem extends IteratingSystem {
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<CameraShakeComponent> csm = ComponentMapper.getFor(CameraShakeComponent.class);
    private OrthographicCamera camera;
    private CameraModeManager cameraModeManager;

    /**
     * Create a camera system without a camera mode manager.
     * Uses default exploration zoom level.
     */
    public CameraSystem(OrthographicCamera camera) {
        super(Family.all(PlayerComponent.class, PositionComponent.class).get());
        this.camera = camera;
        this.cameraModeManager = null;
    }
    
    /**
     * Create a camera system with a camera mode manager for dynamic zoom.
     */
    public CameraSystem(OrthographicCamera camera, CameraModeManager cameraModeManager) {
        super(Family.all(PlayerComponent.class, PositionComponent.class).get());
        this.camera = camera;
        this.cameraModeManager = cameraModeManager;
    }
    
    /**
     * Set the camera mode manager.
     * @param cameraModeManager The camera mode manager to use
     */
    public void setCameraModeManager(CameraModeManager cameraModeManager) {
        this.cameraModeManager = cameraModeManager;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PositionComponent position = pm.get(entity);
        CameraShakeComponent shake = csm.get(entity);
        
        // Center on player (half of player width/height, typically TILE_SIZE/2 = 8)
        float baseX = position.x + GameConstants.TILE_SIZE / 2f;
        float baseY = position.y + GameConstants.TILE_SIZE / 2f;
        
        // Apply camera shake if active
        float shakeOffsetX = 0f;
        float shakeOffsetY = 0f;
        if (shake != null) {
            shake.update(deltaTime);
            if (shake.isShaking()) {
                // Reduce shake in exploration mode
                float shakeMultiplier = getShakeMultiplier();
                shakeOffsetX = shake.getRandomOffset() * shakeMultiplier;
                shakeOffsetY = shake.getRandomOffset() * shakeMultiplier;
            }
        }
        
        camera.position.x = baseX + shakeOffsetX;
        camera.position.y = baseY + shakeOffsetY;
        
        // Handle zoom interpolation based on camera mode
        updateZoom(deltaTime);
    }
    
    /**
     * Get shake intensity multiplier based on camera mode.
     * Shake is reduced in exploration mode to avoid disorientation.
     */
    private float getShakeMultiplier() {
        if (cameraModeManager != null && cameraModeManager.isFight()) {
            return 1.0f; // Full shake in fight mode
        }
        return 0.5f; // Reduced shake in exploration mode
    }
    
    /**
     * Smoothly interpolate camera zoom toward the target for the current mode.
     */
    private void updateZoom(float deltaTime) {
        float targetZoom = getTargetZoom();
        
        // Smoothly interpolate zoom using lerp
        // lerp factor is based on ZOOM_LERP_SPEED and deltaTime
        float lerpFactor = MathUtils.clamp(GameConstants.ZOOM_LERP_SPEED * deltaTime, 0f, 1f);
        camera.zoom = MathUtils.lerp(camera.zoom, targetZoom, lerpFactor);
        
        // Snap to target if very close to avoid endless small adjustments
        if (Math.abs(camera.zoom - targetZoom) < 0.001f) {
            camera.zoom = targetZoom;
        }
    }
    
    /**
     * Get the target zoom level based on the current camera mode.
     */
    private float getTargetZoom() {
        if (cameraModeManager == null) {
            return GameConstants.EXPLORATION_ZOOM;
        }
        
        CameraMode mode = cameraModeManager.getMode();
        if (mode == CameraMode.FIGHT) {
            return GameConstants.FIGHT_ZOOM;
        }
        return GameConstants.EXPLORATION_ZOOM;
    }
}
