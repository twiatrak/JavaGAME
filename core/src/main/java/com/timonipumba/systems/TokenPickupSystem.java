package com.timonipumba.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.timonipumba.GameStateManager;
import com.timonipumba.components.*;
import com.timonipumba.util.GlyphNames;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles pickup of TokenComponent entities and stores them in PlayerInventoryComponent.
 */
public class TokenPickupSystem extends EntitySystem {

    private final GameStateManager gameStateManager;

    private final ComponentMapper<PositionComponent> positionMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<CollisionComponent> collisionMapper = ComponentMapper.getFor(CollisionComponent.class);
    private final ComponentMapper<TokenComponent> tokenMapper = ComponentMapper.getFor(TokenComponent.class);
    private final ComponentMapper<PlayerInventoryComponent> inventoryMapper = ComponentMapper.getFor(PlayerInventoryComponent.class);

    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> tokens;

    public TokenPickupSystem(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(
                Family.all(PlayerComponent.class, PositionComponent.class, CollisionComponent.class).get()
        );
        tokens = engine.getEntitiesFor(
                Family.all(TokenComponent.class, PositionComponent.class).get()
        );
    }

    @Override
    public void update(float deltaTime) {
        if (!gameStateManager.isActiveGameplay()) {
            return;
        }

        List<Entity> tokensToRemove = new ArrayList<>();

        for (Entity player : players) {
            PositionComponent playerPos = positionMapper.get(player);
            CollisionComponent playerCol = collisionMapper.get(player);
            if (playerPos == null || playerCol == null) continue;

            PlayerInventoryComponent inventory = inventoryMapper.get(player);
            if (inventory == null) {
                inventory = new PlayerInventoryComponent();
                player.add(inventory);
            }

            for (Entity tokenEntity : tokens) {
                if (tokensToRemove.contains(tokenEntity)) continue;

                PositionComponent tokenPos = positionMapper.get(tokenEntity);
                CollisionComponent tokenCol = collisionMapper.get(tokenEntity);
                float tokenWidth = tokenCol != null ? tokenCol.width : playerCol.width;
                float tokenHeight = tokenCol != null ? tokenCol.height : playerCol.height;

                if (tokenPos != null && checkOverlap(playerPos, playerCol, tokenPos, tokenWidth, tokenHeight)) {
                    TokenComponent token = tokenMapper.get(tokenEntity);
                    if (token != null && token.tokenId != null && !token.tokenId.isEmpty()) {
                        inventory.addToken(token.tokenId, 1);
                        if (GlyphNames.isGlyph(token.tokenId)) {
                            boolean first = inventory.markDiscovered(token.tokenId);
                            if (first) {
                                inventory.toast("New glyph discovered: " + GlyphNames.displayNameWithId(token.tokenId) + "!", 2.8f);
                            }
                        }
                    }
                    tokensToRemove.add(tokenEntity);
                }
            }
        }

        for (Entity token : tokensToRemove) {
            getEngine().removeEntity(token);
        }
    }

    private boolean checkOverlap(PositionComponent pos1, CollisionComponent col1,
                                 PositionComponent pos2, float width2, float height2) {
        return pos1.x < pos2.x + width2 &&
                pos1.x + col1.width > pos2.x &&
                pos1.y < pos2.y + height2 &&
                pos1.y + col1.height > pos2.y;
    }
}
