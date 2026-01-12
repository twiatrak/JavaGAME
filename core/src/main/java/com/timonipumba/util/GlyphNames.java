package com.timonipumba.util;

/**
 * Player-facing display names for Algebra Forge glyph tokens.
 *
 * The underlying IDs remain stable (glyph_0..glyph_7). This mapping is purely cosmetic.
 */
public final class GlyphNames {
    private GlyphNames() {}

    public static boolean isGlyph(String tokenId) {
        return tokenId != null && tokenId.startsWith("glyph_");
    }

    public static String displayName(String tokenId) {
        int idx = glyphIndex(tokenId);
        if (idx < 0) return tokenId != null ? tokenId : "";

        // Fun, memorable names; intentionally non-mathy.
        return switch (idx) {
            case 0 -> "Pebble";
            case 1 -> "Spark";
            case 2 -> "Moustache";
            case 3 -> "Turnip";
            case 4 -> "Waffle";
            case 5 -> "Comet";
            case 6 -> "Knot";
            case 7 -> "Crown";
            default -> "Glyph";
        };
    }

    public static String displayNameWithId(String tokenId) {
        String name = displayName(tokenId);
        if (tokenId == null || tokenId.isEmpty()) return name;
        return name + " (" + tokenId + ")";
    }

    private static int glyphIndex(String glyphId) {
        if (glyphId == null) return -1;
        if (!glyphId.startsWith("glyph_")) return -1;
        try {
            return Integer.parseInt(glyphId.substring("glyph_".length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
