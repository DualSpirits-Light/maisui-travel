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
        JSONObject bad=t.json().put("days",0);try{Trip.from(bad);throw new AssertionError("days");}catch(IllegalArgumentException ok){count++;}
        bad=t.json();bad.getJSONArray("stops").getJSONObject(0).put("day",99);try{Trip.from(bad);throw new AssertionError("stop day");}catch(IllegalArgumentException ok){count++;}
        bad=t.json().put("budget",-1);try{Trip.from(bad);throw new AssertionError("budget");}catch(IllegalArgumentException ok){count++;}
        bad=t.json();bad.getJSONArray("stops").getJSONObject(0).put("time","9:00");try{Trip.from(bad);throw new AssertionError("time");}catch(java.time.DateTimeException|IllegalArgumentException ok){count++;}
        System.out.println("PASS: "+count+" model assertions");
    }
}
