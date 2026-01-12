package com.timonipumba.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Simple persistent inventory for collectible puzzle tokens.
 *
 * This is intentionally minimal: it tracks token counts by tokenId.
 */
public class PlayerInventoryComponent implements Component {
    public final ObjectIntMap<String> tokenCounts = new ObjectIntMap<>();

    // Player-facing progress: which glyphs have ever been seen.
    public final ObjectSet<String> discoveredTokens = new ObjectSet<>();

    // Simple HUD toast channel.
    public String toastMessage = "";
    public float toastSecondsRemaining = 0f;

    public void addToken(String tokenId, int amount) {
        if (tokenId == null || tokenId.isEmpty() || amount <= 0) return;
        tokenCounts.getAndIncrement(tokenId, 0, amount);
    }

    /**
     * Marks a token as "discovered" (seen at least once). Returns true if this is the first time.
     */
    public boolean markDiscovered(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) return false;
        if (discoveredTokens.contains(tokenId)) return false;
        discoveredTokens.add(tokenId);
        return true;
    }

    public void toast(String message, float seconds) {
        if (message == null) message = "";
        toastMessage = message;
        toastSecondsRemaining = Math.max(0f, seconds);
    }

    public boolean hasToken(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) return false;
        return tokenCounts.get(tokenId, 0) > 0;
    }

    public boolean consumeToken(String tokenId, int amount) {
        if (tokenId == null || tokenId.isEmpty() || amount <= 0) return false;
        int current = tokenCounts.get(tokenId, 0);
        if (current < amount) return false;
        int next = current - amount;
        if (next <= 0) {
            tokenCounts.remove(tokenId, 0);
        } else {
            tokenCounts.put(tokenId, next);
        }
        return true;
    }
}
