package com.timonipumba.world;

/**
 * TMX/XML helpers for TMX generators
 *
 * Intentionally keeps indentation/newline conventions consistent with existing generators.
 */
final class TmxXml {

    private TmxXml() {}

    static String object(int id, String name, float x, float y, float w, float h, String propertiesXml) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <object id=\"").append(id).append("\" name=\"").append(name)
                .append("\" x=\"").append(x).append("\" y=\"").append(y)
                .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\">\n");
        sb.append(propertiesXml);
        sb.append("  </object>\n");
        return sb.toString();
    }

    static String props(String... props) {
        StringBuilder sb = new StringBuilder();
        sb.append("   <properties>\n");
        for (String p : props) sb.append(p);
        sb.append("   </properties>\n");
        return sb.toString();
    }

    static String prop(String name, String value) {
        return "    <property name=\"" + escape(name) + "\" value=\"" + escape(value) + "\"/>\n";
    }

    static String prop(String name, String value, String type) {
        return "    <property name=\"" + escape(name) + "\" type=\"" + escape(type) + "\" value=\"" + escape(value) + "\"/>\n";
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Appends TMX CSV for a 2D tile layer.
     *
     * TMX CSV expects exactly width*height integers separated by commas.
     * Avoid trailing commas at end-of-row to keep parsing consistent across tools.
     */
    static void writeCsvLayer(StringBuilder sb, int width, int height, int[][] tiles) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int id = tiles[y][x];
                if (id < 0) id = 0;
                sb.append(id);
                if (x < width - 1 || y < height - 1) {
                    sb.append(",");
                }
            }
            if (y < height - 1) sb.append("\n");
        }
    }

    static String toCsv(int[][] layer) {
        StringBuilder sb = new StringBuilder();
        int h = layer.length;
        int w = layer[0].length;
        writeCsvLayer(sb, w, h, layer);
        return sb.toString();
    }
}
