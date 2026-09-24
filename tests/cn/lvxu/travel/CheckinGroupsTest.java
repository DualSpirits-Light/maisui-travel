package cn.lvxu.travel;

import java.util.ArrayList;
import java.util.Arrays;

/** Pure tests for the check-in home grouping contract. */
public final class CheckinGroupsTest {
    private static int count;

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        count++;
    }

    public static void main(String[] args) {
        Trip first = new Trip();
        first.title = "西湖周末";
        first.city = "杭州";
        first.start = "2026-09-20";
        first.days = 2;
        Trip.Checkin old = new Trip.Checkin();
        old.time = "2026-09-20T18:30";
        Trip.Checkin recent = new Trip.Checkin();
        recent.time = "2026-09-21T09:10";
        first.checkins.add(old);
        first.checkins.add(recent);

        Trip second = new Trip();
        second.title = "空白旅程";
        second.city = "苏州";
        second.start = "2026-10-01";
        second.days = 1;

        ArrayList<CheckinGroups.Group> groups = CheckinGroups.forTrips(Arrays.asList(first, second));
        check(groups.size() == 2, "all trips are represented, including empty trips");
        check(groups.get(0).trip == first && groups.get(0).recordCount == 2, "group keeps owning trip and count");
        check(groups.get(0).dateRange.equals("2026年9月20日 - 2026年9月21日"), "date range has visible date format");
        check(groups.get(1).recordCount == 0, "empty trip has zero records");
        check(CheckinGroups.records(first).get(0) == recent, "records are newest first");
        check(CheckinGroups.ownerOf(Arrays.asList(first, second), recent) == first, "record resolves to owning trip");
        check(CheckinGroups.ownerOf(Arrays.asList(first, second), new Trip.Checkin()) == null, "unowned record is not fabricated into a group");
        check(CheckinGroups.emptyMessage(second).contains("还没有打卡"), "empty trip has a clear explanation");
        check(CheckinGroups.emptyMessage(new ArrayList<Trip>()).contains("还没有旅行"), "empty home has a clear explanation");
        System.out.println("PASS: " + count + " check-in grouping assertions");
    }
}
