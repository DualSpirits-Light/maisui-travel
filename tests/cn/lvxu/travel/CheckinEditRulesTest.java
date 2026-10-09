package cn.lvxu.travel;
import java.util.List;

public final class CheckinEditRulesTest {
    static int count;
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); count++; }
    public static void main(String[] args) {
        check(CheckinEditRules.editableGroupPhotos(List.of(), List.of("photos/scenery.jpg"), "photos/scenery.jpg").isEmpty(), "scenery cover must not become a group photo when editing");
        check(CheckinEditRules.editableGroupPhotos(List.of(), List.of(), "photos/legacy.jpg").equals(List.of("photos/legacy.jpg")), "legacy unclassified photo remains editable");
        check(CheckinEditRules.cover(List.of(), List.of(), "photos/old.jpg").isEmpty(), "clearing every draft photo clears the old cover");
        check(CheckinEditRules.cover(List.of("group"), List.of("scenery"), "old").equals("scenery"), "scenery retains cover priority");
        check(CheckinEditRules.companions(" 小王 \r\n\n 小李 ").equals(List.of("小王", "小李")), "people trim and ignore blank lines");
        check(CheckinEditRules.companions("人".repeat(40)).size() == 1, "forty characters accepted");
        check(CheckinEditRules.companions("小王，小李;阿青；小王\n阿青").equals(List.of("小王","小李","阿青")), "comma semicolon newline normalized and deduplicated");
        boolean rejected=false;try { CheckinEditRules.companions("人".repeat(41)); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected, "forty-one characters rejected before save");
        rejected=false;try { CheckinEditRules.companions(java.util.stream.IntStream.range(0,51).mapToObj(i->"同行"+i).collect(java.util.stream.Collectors.joining("\n"))); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected, "fifty-one companions rejected");
        System.out.println("PASS: " + count + " check-in draft assertions");
    }
}
