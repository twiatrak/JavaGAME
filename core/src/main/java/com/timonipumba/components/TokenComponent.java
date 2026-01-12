package com.timonipumba.components;

import com.badlogic.ashley.core.Component;

/** Collectible token for non-terminal riddles (picked up by walking over it). */
public class TokenComponent implements Component {
    public String tokenId;

    public TokenComponent() {
    }

    public TokenComponent(String tokenId) {
        this.tokenId = tokenId;
    }
}
