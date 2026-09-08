package cn.lvxu.travel;
import org.json.*;
public final class ModelTest {
    static int count=0;
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);count++;}
    static void rejects(Runnable r,String name){try{r.run();}catch(IllegalArgumentException e){count++;return;}throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        check(Trip.cents("0.10")+Trip.cents("0.20")==30,"decimal precision");
        check(Trip.money(100).equals("1"),"money display");
        check(Trip.cents("100000000")==10000000000L,"upper bound");
        rejects(()->Trip.cents("-1"),"negative amount");rejects(()->Trip.cents("1.234"),"fraction cent");rejects(()->Trip.cents("NaN"),"not a number");rejects(()->Trip.cents("100000001"),"overflow bound");
        Trip t=Trip.demo();check(t.onDay(0).size()==3,"day filtering");Trip.Stop s=new Trip.Stop();s.name="清晨";s.time="06:00";s.day=0;t.stops.add(s);check(t.onDay(0).get(0)==s,"chronological order");
        Trip.Expense e=new Trip.Expense();e.name="午餐";e.amount=1234;t.expenses.add(e);check(t.spent()==1234,"actual expenses");check(t.planned()==7500,"planned costs separate");
        t.items.get(0).done=true;Trip copy=Trip.from(new JSONObject(t.json().toString()));check(copy.json().toString().equals(t.json().toString()),"backup round trip");check(copy.items.get(0).done,"checklist retained");
        check(copy.categories.size()==6&&copy.lists.size()==1,"defaults retained");
        Trip.Expense rich=new Trip.Expense();rich.name="夜宵";rich.amount=880;rich.categoryId=t.categories.get(0).id;rich.category=t.categories.get(0).name;rich.occurredAt="2026-09-07T21:30";t.expenses.add(rich);
        Trip.Item detail=new Trip.Item("相机");detail.listId=t.lists.get(0).id;detail.importance="重要";detail.note="充满电";detail.attributes.put("数量","1");t.items.add(detail);
        copy=Trip.from(new JSONObject(t.json().toString()));check(copy.expenses.get(copy.expenses.size()-1).occurredAt.equals("2026-09-07T21:30"),"expense datetime retained");check(copy.items.get(copy.items.size()-1).attributes.get("数量").equals("1"),"custom fields retained");
        String deleted=t.categories.get(0).id,replacement=t.categories.get(1).id;t.deleteCategory(deleted,replacement);check(rich.categoryId.equals(replacement)&&t.category(deleted)==null,"category delete reassigns expenses");
        JSONObject legacy=t.json();legacy.remove("categories");legacy.remove("lists");for(int i=0;i<legacy.getJSONArray("expenses").length();i++){legacy.getJSONArray("expenses").getJSONObject(i).remove("id");legacy.getJSONArray("expenses").getJSONObject(i).remove("categoryId");legacy.getJSONArray("expenses").getJSONObject(i).remove("occurredAt");}Trip upgraded=Trip.from(legacy);check(!upgraded.categories.isEmpty()&&!upgraded.lists.isEmpty(),"legacy model migration");
        JSONObject traversal=t.json();traversal.getJSONArray("items").getJSONObject(0).put("photo","../secret.jpg");try{Trip.from(traversal);throw new AssertionError("media traversal");}catch(IllegalArgumentException ok){count++;}
        Trip.Checkin checkin=new Trip.Checkin();checkin.photo="photos/legacy.jpg";checkin.place="山顶";checkin.time="2026-09-07T18:30";checkin.groupPhotos.add("photos/us.jpg");checkin.groupPhotos.add("photos/friends.jpg");checkin.sceneryPhotos.add("photos/view.jpg");checkin.companions.add("小麦");t.checkins.add(checkin);
        copy=Trip.from(new JSONObject(t.json().toString()));Trip.Checkin saved=copy.checkins.get(0);check(saved.groupPhotos.size()==2&&saved.sceneryPhotos.size()==1&&saved.companions.get(0).equals("小麦"),"checkin albums and companions retained");
        JSONObject legacyCheckin=t.json();legacyCheckin.getJSONArray("checkins").getJSONObject(0).remove("groupPhotos");legacyCheckin.getJSONArray("checkins").getJSONObject(0).remove("sceneryPhotos");legacyCheckin.getJSONArray("checkins").getJSONObject(0).remove("companions");saved=Trip.from(legacyCheckin).checkins.get(0);check(saved.groupPhotos.size()==1&&saved.groupPhotos.get(0).equals("photos/legacy.jpg"),"legacy checkin photo migration");
        JSONObject bad=t.json().put("days",0);try{Trip.from(bad);throw new AssertionError("days");}catch(IllegalArgumentException ok){count++;}
        bad=t.json();bad.getJSONArray("stops").getJSONObject(0).put("day",99);try{Trip.from(bad);throw new AssertionError("stop day");}catch(IllegalArgumentException ok){count++;}
        bad=t.json().put("budget",-1);try{Trip.from(bad);throw new AssertionError("budget");}catch(IllegalArgumentException ok){count++;}
        bad=t.json();bad.getJSONArray("stops").getJSONObject(0).put("time","9:00");try{Trip.from(bad);throw new AssertionError("time");}catch(java.time.DateTimeException|IllegalArgumentException ok){count++;}
        System.out.println("PASS: "+count+" model assertions");
    }
}
