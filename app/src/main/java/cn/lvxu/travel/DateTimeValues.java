package cn.lvxu.travel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Platform-independent date-time conversion shared by fields and pure tests. */
final class DateTimeValues {
    private static final DateTimeFormatter EDIT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter LEGACY = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    private DateTimeValues() {}

    static LocalDateTime parse(String text, LocalDateTime fallback) {
        String value = text == null ? "" : text.trim();
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{EDIT, LEGACY}) {
            try { return LocalDateTime.parse(value, formatter); } catch (Exception ignored) {}
        }
        try { return LocalDateTime.parse(value); } catch (Exception ignored) {}
        return fallback;
    }

    static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        LocalDateTime value = parse(text, null);
        return value == null ? text.trim() : value.format(EDIT);
    }

    static String storage(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        LocalDateTime value = parse(text, null);
        return value == null ? text.trim() : value.format(LEGACY);
    }

    static String display(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        LocalDateTime value = parse(text, null);
        return value == null ? text.trim() : value.format(DISPLAY);
    }
}
