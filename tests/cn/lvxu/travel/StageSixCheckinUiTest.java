package cn.lvxu.travel;

/**
 * Contract for the text shown on a grouped check-in record card.
 *
 * A regression in this formatter would reintroduce the raw storage `T` or
 * lose the companions that distinguish a memory from a bare timestamp.
 */
public final class StageSixCheckinUiTest {
    private static int count;

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        count++;
    }

    public static void main(String[] args) {
        Trip.Checkin record = new Trip.Checkin();
        record.mood = "雨后散步";
        record.time = "2026-10-01T09:30";
        record.companions.add("小李");
        record.companions.add("小王");

        check(CheckinGroups.recordSubtitle(record).equals(
                        "雨后散步  ·  2026年10月1日 09:30 · 同行 小李、小王"),
                "grouped record converts storage time and retains companions");

        Trip.Checkin blank = new Trip.Checkin();
        blank.time = "2026-10-02 08:05";
        check(CheckinGroups.recordSubtitle(blank).equals(
                        "未填写心情  ·  2026年10月2日 08:05"),
                "blank memory uses a friendly fallback without a T");

        System.out.println("PASS: " + count + " stage-six check-in UI assertions");
    }
}
