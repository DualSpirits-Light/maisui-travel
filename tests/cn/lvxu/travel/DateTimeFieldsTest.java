package cn.lvxu.travel;

/** Pure contract tests for the date-time field's display/storage boundary. */
public final class DateTimeFieldsTest {
    private static int count;

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        count++;
    }

    public static void main(String[] args) {
        check(DateTimeValues.normalize("2026-09-07T18:30").equals("2026-09-07 18:30"), "legacy T is normalized for editing");
        check(DateTimeValues.normalize("2026-09-07 18:30").equals("2026-09-07 18:30"), "space format remains stable");
        check(DateTimeValues.normalize("").isEmpty(), "blank date-time remains blank");
        check(DateTimeValues.storage("2026-09-07 18:30").equals("2026-09-07T18:30"), "editing value is stored in legacy format");
        check(DateTimeValues.storage("2026-09-07T18:30").equals("2026-09-07T18:30"), "legacy value stays compatible");
        check(DateTimeValues.display("2026-09-07T18:30").equals("2026年9月7日 18:30"), "visible date-time never contains T");
        check(DateTimeValues.parse("2026-02-30 18:30", null) == null, "invalid calendar date is rejected");
        check(DateTimeValues.parse("2026-09-07 24:00", null) == null, "invalid clock time is rejected");
        System.out.println("PASS: " + count + " date-time assertions");
    }
}
