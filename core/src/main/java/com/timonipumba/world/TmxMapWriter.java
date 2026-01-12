package com.timonipumba.world;

import com.timonipumba.GameConstants;

// smol tmx writer for map gens
final class TmxMapWriter {

    private final StringBuilder sb = new StringBuilder();
    private final int width;
    private final int height;
    private final int tileSize;
    private final String tiledVersion;

    private Integer nextLayerId;
    private Integer nextObjectId;

    private TmxMapWriter(int width, int height, int tileSize, String tiledVersion) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.tiledVersion = tiledVersion;
    }

    static TmxMapWriter create(int width, int height) {
        return new TmxMapWriter(width, height, GameConstants.TILE_SIZE, "1.10.2");
    }

    static TmxMapWriter create(int width, int height, String tiledVersion) {
        return new TmxMapWriter(width, height, GameConstants.TILE_SIZE, tiledVersion);
    }

    TmxMapWriter withNextIds(int nextLayerId, int nextObjectId) {
        this.nextLayerId = nextLayerId;
        this.nextObjectId = nextObjectId;
        return this;
    }

    TmxMapWriter startMap() {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<map version=\"1.10\" tiledversion=\"")
                .append(tiledVersion)
                .append("\" orientation=\"orthogonal\" renderorder=\"right-down\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" tilewidth=\"")
                .append(tileSize)
                .append("\" tileheight=\"")
                .append(tileSize)
                .append("\" infinite=\"0\"");

        if (nextLayerId != null && nextObjectId != null) {
            sb.append(" nextlayerid=\"").append(nextLayerId).append("\" nextobjectid=\"").append(nextObjectId).append("\"");
        }

        sb.append(">\n");
        return this;
    }

    TmxMapWriter tileset(String source) {
        sb.append(" <tileset firstgid=\"1\" source=\"").append(source).append("\"/>\n");
        return this;
    }

    TmxMapWriter layerCsv(int id, String name, int[][] tiles) {
        sb.append(" <layer id=\"").append(id).append("\" name=\"").append(name)
                .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n");
        sb.append("  <data encoding=\"csv\">\n");
        TmxXml.writeCsvLayer(sb, width, height, tiles);
        sb.append("\n  </data>\n");
        sb.append(" </layer>\n");
        return this;
    }

    TmxMapWriter objectGroupOpen(int id, String name) {
        sb.append(" <objectgroup id=\"").append(id).append("\" name=\"").append(name).append("\">\n");
        return this;
    }

    TmxMapWriter objectGroupClose() {
        sb.append(" </objectgroup>\n");
        return this;
    }

    TmxMapWriter endMap() {
        sb.append("</map>\n");
        return this;
    }

    StringBuilder sb() {
        return sb;
    }

    String build() {
        return sb.toString();
    }
}
