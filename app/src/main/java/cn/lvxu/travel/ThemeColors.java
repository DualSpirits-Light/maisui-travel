package cn.lvxu.travel;

import org.json.JSONObject;

/** Pure-Java color palettes shared by the settings UI and persisted preferences. */
public final class ThemeColors {
    public static final String[] IDS = {"forest", "blush", "peach", "ocean", "lavender", "graphite"};
    public static final String[] NAMES = {"原绿", "樱雾粉", "奶杏蜜桃", "海盐蓝", "薰衣草紫", "石墨青"};
    public static final String[] KEYS = {"bg", "surface", "ink", "muted", "primary", "pale", "line", "warning"};
    public static final String[] LABELS = {"背景", "卡片", "正文", "次要文字", "主色", "浅色", "分割线", "提醒"};

    private static final int[][] LIGHT = {
            colors("#F6FAF4", "#FFFFFF", "#172219", "#506254", "#267A46", "#E0F1E3", "#D4E4D6", "#C55A22"),
            colors("#FFF7F8", "#FFFFFF", "#2A1C22", "#71525D", "#B9446B", "#F6EBD9", "#F1D3DD", "#B5503F"),
            colors("#FFF8F2", "#FFFFFF", "#2D211A", "#705B4B", "#AD552A", "#E2EDF4", "#F0D9C6", "#B75626"),
            colors("#F4FAFC", "#FFFFFF", "#17242A", "#4C626C", "#247C9E", "#E9E5F7", "#CFE3EA", "#BA6026"),
            colors("#F8F6FE", "#FFFFFF", "#231D31", "#5E526F", "#7656B8", "#EAE3FA", "#DDD3F1", "#B65D34"),
            colors("#F5F8F8", "#FFFFFF", "#1C292B", "#506164", "#35737B", "#DDEBED", "#D0E0E2", "#B86532")
    };
    private static final int[][] DARK = {
            colors("#101A12", "#18241B", "#F0F7EF", "#C3D2C4", "#70D68A", "#263B2A", "#36523B", "#FFAD70"),
            colors("#21151A", "#2C1C23", "#FFF1F4", "#E5C2CD", "#FF8DB2", "#3B3327", "#613C4A", "#FFAD96"),
            colors("#241A13", "#302219", "#FFF2E8", "#E5C9B6", "#FFAA74", "#253B48", "#654635", "#FFC16D"),
            colors("#102027", "#18303A", "#EDF8FC", "#BED8E1", "#66CBEF", "#342D48", "#35606F", "#FFB477"),
            colors("#1D182A", "#29213A", "#F6F0FF", "#D8C8ED", "#B99AFF", "#382D51", "#50416F", "#FFB28D"),
            colors("#152225", "#1E3034", "#EEF8F8", "#C5DADD", "#72D0D1", "#2A4348", "#3C6066", "#FFB675")
    };

    private ThemeColors() { }

    public static int[] preset(String id, boolean dark) {
        int index = 0;
        if (id != null) for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) { index = i; break; }
        return (dark ? DARK[index] : LIGHT[index]).clone();
    }

    public static int parseHex(String value) {
        if (value == null) throw new IllegalArgumentException("请输入颜色编码");
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("请输入六位十六进制颜色，如 #23644F");
        return 0xff000000 | Integer.parseInt(hex, 16);
    }

    public static String hex(int color) {
        return String.format("#%06X", color & 0x00ffffff);
    }

    public static int onColor(int color) {
        return contrast(0xff000000, color) >= contrast(0xffffffff, color) ? 0xff000000 : 0xffffffff;
    }

    public static boolean isDark(int color) {
        return luminance(color) < 0.179;
    }

    public static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    public static int[] resolve(String presetId, boolean dark, JSONObject custom) {
        int[] result = preset(presetId, dark);
        if (custom != null) {
            for (int i = 0; i < KEYS.length; i++) {
                if (custom.has(KEYS[i]) && !custom.isNull(KEYS[i])) {
                    Object value = custom.opt(KEYS[i]);
                    if (!(value instanceof String)) throw new IllegalArgumentException("颜色必须为十六进制文字");
                    result[i] = parseHex((String) value);
                }
            }
        }
        result[2] = readableText(result[2], result[0], result[1], result[5]);
        result[3] = readableText(result[3], result[0], result[1], result[5]);
        return result;
    }

    private static int[] colors(String... values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) result[i] = parseHex(values[i]);
        return result;
    }

    private static int readableText(int requested, int... backgrounds) {
        if (readableOnAll(requested, backgrounds)) return requested;
        int black = 0xff000000, white = 0xffffffff;
        return minimumContrast(black, backgrounds) >= minimumContrast(white, backgrounds) ? black : white;
    }

    private static boolean readableOnAll(int foreground, int... backgrounds) {
        for (int background : backgrounds) if (contrast(foreground, background) < 4.5) return false;
        return true;
    }

    private static double minimumContrast(int foreground, int... backgrounds) {
        double result = Double.MAX_VALUE;
        for (int background : backgrounds) result = Math.min(result, contrast(foreground, background));
        return result;
    }

    private static double luminance(int color) {
        double red = channel((color >> 16) & 0xff);
        double green = channel((color >> 8) & 0xff);
        double blue = channel(color & 0xff);
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
