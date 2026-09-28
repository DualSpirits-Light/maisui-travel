package cn.lvxu.travel;

public final class CheckinMemoriesTest {
    private static int assertions;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); assertions++; }
    public static void main(String[] args) {
        Trip t=new Trip(); t.start="2026-09-20"; t.days=2;
        Trip.Stop s=new Trip.Stop(); s.name="湖边"; t.stops.add(s);
        Trip.Checkin c=new Trip.Checkin(); c.place="湖边"; c.time="2026-09-20T10:00"; t.checkins.add(c);
        check(CheckinMemories.linkedStop(t,c)==null,"legacy names never silently link");
        check(CheckinMemories.linkLabel(t,c).contains("待确认"),"unique legacy candidate explained");
        Trip.Stop duplicate=new Trip.Stop(); duplicate.name=s.name; t.stops.add(duplicate);
        check(CheckinMemories.linkLabel(t,c).contains("同名"),"duplicate names remain unlinked");
        c.stopId=s.id;
        check(CheckinMemories.linkedStop(t,c)==s,"explicit id identifies exact stop");
        check(CheckinMemories.forStop(t,duplicate).isEmpty(),"no cross-link to duplicate");
        s.name="改名后";
        check(CheckinMemories.linkedStop(t,c)==s,"rename preserves association");
        duplicate.id=s.id;
        check(CheckinMemories.linkedStop(t,c)==null,"duplicate ids are never arbitrarily resolved");
        duplicate.id=Trip.uid();
        c.stopId="";
        check(CheckinMemories.forStop(t,s).isEmpty(),"cleared association removes place membership");
        c.stopId=s.id;
        t.stops.remove(s);
        check(CheckinMemories.linkLabel(t,c).contains("已删除"),"deleted association explained");
        c.photo="photos/a.jpg"; c.groupPhotos.add(c.photo); c.sceneryPhotos.add("photos/b.jpg");
        check(CheckinMemories.photos(c).size()==2,"legacy and current photos deduplicated");
        Trip.Checkin outside=new Trip.Checkin(); outside.time="2026-09-25 12:00"; t.checkins.add(outside);
        Trip.Checkin invalid=new Trip.Checkin(); invalid.time="old invalid time"; t.checkins.add(invalid);
        check(CheckinMemories.days(t).size()==4,"planned days plus outside and invalid preserved");
        check(CheckinMemories.records(t,"2026-09-20").size()==1,"day filter accepts ISO datetime");
        check(CheckinMemories.records(t,"unknown").size()==1,"invalid dates remain accessible");
        check(CheckinMemories.records(t,"").size()==3,"all records accessible");
        System.out.println("PASS: "+assertions+" memory assertions");
    }
}
