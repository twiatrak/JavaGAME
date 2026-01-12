package com.timonipumba.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.timonipumba.GameState;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DoorSystem.
 * 
 * Validates:
 * - Door opens when switch is activated
 * - Door closes when switch is deactivated
 * - Multiple switches in same group
 * - Door collision changes
 * - Door visibility changes (alpha)
 * - Original dimensions are stored
 */
class DoorSystemTest {
    
    private Engine engine;
    private DoorSystem doorSystem;
    private GameStateManager gameStateManager;
    
    @BeforeEach
    void setUp() {
        engine = new Engine();
        gameStateManager = new GameStateManager();
        gameStateManager.setState(GameState.PLAYING);
        doorSystem = new DoorSystem(gameStateManager);
        engine.addSystem(doorSystem);
    }
    
    @Test
    void testDoorInitiallyClosedWithSwitchOff() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", false);
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        CollisionComponent collision = door.getComponent(CollisionComponent.class);
        
        assertFalse(doorComp.open, "Door should remain closed when switch is off");
        assertEquals(16f, collision.width, "Collision should be full width");
        assertEquals(16f, collision.height, "Collision should be full height");
    }
    
    @Test
    void testDoorOpensWhenSwitchTurnedOn() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", false);
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        engine.update(0.1f);
        
        // Turn switch on
        SwitchComponent switchComp = switchEntity.getComponent(SwitchComponent.class);
        switchComp.on = true;
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        CollisionComponent collision = door.getComponent(CollisionComponent.class);
        
        assertTrue(doorComp.open, "Door should open when switch is on");
        assertEquals(0f, collision.width, "Collision should be removed");
        assertEquals(0f, collision.height, "Collision should be removed");
    }
    
    @Test
    void testDoorClosesWhenSwitchTurnedOff() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", true); // Start on
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        assertTrue(doorComp.open, "Door should be open initially");
        
        // Turn switch off
        SwitchComponent switchComp = switchEntity.getComponent(SwitchComponent.class);
        switchComp.on = false;
        engine.update(0.1f);
        
        CollisionComponent collision = door.getComponent(CollisionComponent.class);
        
        assertFalse(doorComp.open, "Door should close when switch is off");
        assertEquals(16f, collision.width, "Collision should be restored");
        assertEquals(16f, collision.height, "Collision should be restored");
    }
    
    @Test
    void testDoorOpenWithMultipleSwitches() {
        Entity door = createDoor("A", false);
        Entity switch1 = createSwitch("A", false);
        Entity switch2 = createSwitch("A", false);
        
        engine.addEntity(door);
        engine.addEntity(switch1);
        engine.addEntity(switch2);
        
        // Turn first switch on
        switch1.getComponent(SwitchComponent.class).on = true;
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        assertTrue(doorComp.open, "Door should open with one switch on");
        
        // Turn second switch on, first still on
        switch2.getComponent(SwitchComponent.class).on = true;
        engine.update(0.1f);
        assertTrue(doorComp.open, "Door should stay open with both switches on");
        
        // Turn first switch off, second still on
        switch1.getComponent(SwitchComponent.class).on = false;
        engine.update(0.1f);
        assertTrue(doorComp.open, "Door should stay open with one switch still on");
        
        // Turn second switch off
        switch2.getComponent(SwitchComponent.class).on = false;
        engine.update(0.1f);
        assertFalse(doorComp.open, "Door should close when all switches are off");
    }
    
    @Test
    void testDoorDifferentGroups() {
        Entity doorA = createDoor("A", false);
        Entity doorB = createDoor("B", false);
        Entity switchA = createSwitch("A", false);
        Entity switchB = createSwitch("B", false);
        
        engine.addEntity(doorA);
        engine.addEntity(doorB);
        engine.addEntity(switchA);
        engine.addEntity(switchB);
        engine.update(0.1f);
        
        // Turn switch A on
        switchA.getComponent(SwitchComponent.class).on = true;
        engine.update(0.1f);
        
        assertTrue(doorA.getComponent(DoorComponent.class).open, "Door A should open");
        assertFalse(doorB.getComponent(DoorComponent.class).open, "Door B should remain closed");
    }
    
    @Test
    void testDoorVisibilityChanges() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", false);
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        engine.update(0.1f);
        
        RenderableComponent renderable = door.getComponent(RenderableComponent.class);
        assertEquals(1.0f, renderable.color.a, 0.01f, "Closed door should be fully opaque");
        
        // Open door
        switchEntity.getComponent(SwitchComponent.class).on = true;
        engine.update(0.1f);
        
        assertEquals(0.3f, renderable.color.a, 0.01f, "Open door should be transparent");
        
        // Close door
        switchEntity.getComponent(SwitchComponent.class).on = false;
        engine.update(0.1f);
        
        assertEquals(1.0f, renderable.color.a, 0.01f, "Closed door should be opaque again");
    }
    
    @Test
    void testOriginalDimensionsStored() {
        Entity door = engine.createEntity();
        door.add(new PositionComponent(100, 100));
        door.add(new CollisionComponent(32, 48)); // Non-standard size
        door.add(new RenderableComponent(32, 48, Color.BROWN));
        DoorComponent doorComp = new DoorComponent("A", false);
        door.add(doorComp);
        
        Entity switchEntity = createSwitch("A", false);
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        engine.update(0.1f);
        
        assertTrue(doorComp.dimensionsStored, "Dimensions should be stored");
        assertEquals(32f, doorComp.originalWidth, "Original width should be stored");
        assertEquals(48f, doorComp.originalHeight, "Original height should be stored");
        
        // Open and close door
        switchEntity.getComponent(SwitchComponent.class).on = true;
        engine.update(0.1f);
        switchEntity.getComponent(SwitchComponent.class).on = false;
        engine.update(0.1f);
        
        // Dimensions should be restored
        CollisionComponent collision = door.getComponent(CollisionComponent.class);
        assertEquals(32f, collision.width, "Width should be restored");
        assertEquals(48f, collision.height, "Height should be restored");
    }
    
    @Test
    void testSystemPausedWhenNotPlaying() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", true); // Switch is on
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        
        // Set game to non-active state (puzzle overlay)
        gameStateManager.setState(GameState.PUZZLE);
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        assertFalse(doorComp.open, "Door should not open during PUZZLE state");
        
        // Back to playing
        gameStateManager.setState(GameState.PLAYING);
        engine.update(0.1f);
        
        assertTrue(doorComp.open, "Door should open during PLAYING state");
    }
    
    @Test
    void testSystemActiveInLevelClearState() {
        Entity door = createDoor("A", false);
        Entity switchEntity = createSwitch("A", true);
        
        engine.addEntity(door);
        engine.addEntity(switchEntity);
        
        gameStateManager.setState(GameState.LEVEL_CLEAR);
        engine.update(0.1f);
        
        DoorComponent doorComp = door.getComponent(DoorComponent.class);
        assertTrue(doorComp.open, "Door should open during LEVEL_CLEAR state");
    }
    
    private Entity createDoor(String group, boolean open) {
        Entity door = engine.createEntity();
        door.add(new PositionComponent(100, 100));
        door.add(new CollisionComponent(16, 16));
        door.add(new RenderableComponent(16, 16, Color.BROWN));
        DoorComponent doorComp = new DoorComponent(group, open);
        doorComp.originalWidth = 16f;
        doorComp.originalHeight = 16f;
        doorComp.dimensionsStored = true;
        door.add(doorComp);
        return door;
    }
    
    private Entity createSwitch(String group, boolean on) {
        Entity switchEntity = engine.createEntity();
        switchEntity.add(new PositionComponent(50, 50));
        SwitchComponent switchComp = new SwitchComponent(group);
        switchComp.on = on;
        switchEntity.add(switchComp);
        return switchEntity;
    }
}
