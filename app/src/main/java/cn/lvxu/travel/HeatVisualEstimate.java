package cn.lvxu.travel;

/** A conservative colour reference, never a measurement of people or occupancy.
 * Caller must provide freshly rendered, registered SDK snapshots with identical
 * viewport, dimensions, style and overlays; only city heat differs. */
public final class HeatVisualEstimate {
    public enum Level {
        UNKNOWN(0), LOW(1), MEDIUM(3), HIGH(5);
        public final int indicators;
        Level(int indicators) { this.indicators = indicators; }
    }
    private HeatVisualEstimate() {}

    public static Level estimate(int[] heat, int[] base, int width, int height,
                                 int centerX, int centerY, int radius) {
        if (heat == null || base == null || width <= 0 || height <= 0
            || (long) width * height > Integer.MAX_VALUE
            || heat.length != width * height || base.length != heat.length
            || radius < 2 || radius > 64 || centerX < radius || centerY < radius
            || centerX >= width - radius || centerY >= height - radius) return Level.UNKNOWN;
        int side = radius * 2 + 1, samples = 0, accepted = 0, unexplained = 0;
        int[] levels = new int[side * side];
        int[] counts = new int[4];
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radius * radius) continue;
                samples++;
                int offset = (centerY + dy) * width + centerX + dx;
                int hc = heat[offset], bc = base[offset];
                if ((hc >>> 24) < 240 || (bc >>> 24) < 240) { unexplained++; continue; }
                int distance = distance(hc, bc);
                if (distance < 42) continue; // Identical parks/roads and weak overlays are unknown.
                float[] h = hsv(hc), b = hsv(bc);
                // Ignore text, map icons and already saturated base features.
                if (b[2] < .45f || b[1] > .30f || h[2] < .45f || h[1] < .25f) {
                    unexplained++; continue;
                }
                int level = colourLevel(h[0]);
                if (level == 0) { unexplained++; continue; }
                levels[(dy + radius) * side + dx + radius] = level;
                counts[level]++; accepted++;
            }
        }
        if (accepted < 12 || accepted * 100 < samples * 35
            || unexplained * 100 > samples * 45) return Level.UNKNOWN;
        // A solid 2x2 block rejects isolated changed glyphs and raster noise.
        boolean cluster = false;
        for (int y = 0; y < side - 1 && !cluster; y++) {
            for (int x = 0; x < side - 1; x++) {
                int i = y * side + x;
                if (levels[i] != 0 && levels[i + 1] != 0
                    && levels[i + side] != 0 && levels[i + side + 1] != 0) { cluster = true; break; }
            }
        }
        if (!cluster) return Level.UNKNOWN;
        int winner = 1;
        for (int i = 2; i <= 3; i++) if (counts[i] > counts[winner]) winner = i;
        // A boundary between different colours should not imply a confident category.
        if (counts[winner] * 100 < accepted * 65) return Level.UNKNOWN;
        return winner == 1 ? Level.LOW : winner == 2 ? Level.MEDIUM : Level.HIGH;
    }

    private static int colourLevel(float hue) {
        if (hue < 18 || hue >= 345) return 3; // Red.
        if (hue < 75) return 2; // Orange and yellow.
        if (hue <= 285) return 1; // Green, cyan, blue and purple.
        return 0; // Pink/magenta is not part of the observed city palette.
    }
    private static int distance(int a, int b) {
        int r = Math.abs((a >> 16 & 255) - (b >> 16 & 255));
        int g = Math.abs((a >> 8 & 255) - (b >> 8 & 255));
        int blue = Math.abs((a & 255) - (b & 255));
        return Math.max(r, Math.max(g, blue));
    }
    private static float[] hsv(int c) {
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float hue = 0;
        if (d > 0) {
            if (max == r) hue = 60 * ((g - b) / d);
            else if (max == g) hue = 60 * ((b - r) / d + 2);
            else hue = 60 * ((r - g) / d + 4);
            if (hue < 0) hue += 360;
        }
        return new float[] { hue, max == 0 ? 0 : d / max, max };
    }
}
