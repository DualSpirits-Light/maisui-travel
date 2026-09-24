package cn.lvxu.travel;

import org.json.JSONObject;

public final class ThemeColorsTest {
    static int count;

    static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        count++;
    }

    static void rejects(Runnable action, String name) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            count++;
            return;
        }
        throw new AssertionError(name);
    }

    public static void main(String[] args) {
        check(ThemeColors.parseHex("#A1b2C3") == 0xffa1b2c3, "hash hex parses");
        check(ThemeColors.parseHex("00ff80") == 0xff00ff80, "bare hex parses");
        check(ThemeColors.hex(0xff1234ab).equals("#1234AB"), "hex formats");
        rejects(() -> ThemeColors.parseHex("#12345"), "short hex rejected");
        rejects(() -> ThemeColors.parseHex("#GG0000"), "invalid hex rejected");

        check(ThemeColors.IDS.length == 6 && ThemeColors.NAMES.length == 6, "six named presets");
        check(ThemeColors.KEYS.length == 8 && ThemeColors.LABELS.length == 8, "eight palette fields");
        for (String id : ThemeColors.IDS) {
            for (boolean dark : new boolean[]{false, true}) {
                int[] colors = ThemeColors.preset(id, dark);
                check(colors.length == ThemeColors.KEYS.length, id + " palette length");
                check(ThemeColors.contrast(colors[2], colors[0]) >= 4.5, id + " ink on background");
                check(ThemeColors.contrast(colors[2], colors[1]) >= 4.5, id + " ink on surface");
                check(ThemeColors.contrast(colors[2], colors[5]) >= 4.5, id + " ink on pale");
            }
        }
        check(ThemeColors.preset("missing", false)[0] == ThemeColors.preset("forest", false)[0], "unknown preset falls back");

        JSONObject custom = new JSONObject();
        for (int i = 0; i < ThemeColors.KEYS.length; i++) custom.put(ThemeColors.KEYS[i], "#20242A");
        custom.put("bg", "#1B1D22").put("surface", "#252831").put("pale", "#30343E");
        int[] resolved = ThemeColors.resolve("ocean", true, custom);
        check(resolved[0] == ThemeColors.parseHex("#1B1D22"), "custom background retained");
        check(ThemeColors.contrast(resolved[2], resolved[0]) >= 4.5, "custom ink repaired");
        System.out.println("PASS: " + count + " theme color assertions");
    }
}
