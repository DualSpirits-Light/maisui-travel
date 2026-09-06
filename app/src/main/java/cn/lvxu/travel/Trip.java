package cn.lvxu.travel;

import org.json.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Local data model. Money is stored in cents; all dates are ISO local dates. */
public final class Trip {
    public String id = UUID.randomUUID().toString(), title, city, start;
    public int days;
    public long budget;
    public final ArrayList<Stop> stops = new ArrayList<>();
    public final ArrayList<Expense> expenses = new ArrayList<>();
    public final ArrayList<Item> items = new ArrayList<>();
    public static long cents(String input) {
        try {
            long n = new BigDecimal(input.trim()).movePointRight(2).longValueExact();
            if(n < 0 || n > 10000000000L) throw new IllegalArgumentException();
            return n;
        } catch(Exception e) { throw new IllegalArgumentException("金额须为 0–100,000,000，最多两位小数"); }
    }
    public static String money(long n) { return BigDecimal.valueOf(n,2).stripTrailingZeros().toPlainString(); }
    public long spent() { long n=0; for(Expense e:expenses)n+=e.amount; return n; }
    public long planned() { long n=0; for(Stop s:stops)n+=s.cost; return n; }
    public ArrayList<Stop> onDay(int day) { ArrayList<Stop> list=new ArrayList<>(); for(Stop s:stops)if(s.day==day)list.add(s); list.sort(Comparator.comparing(s->s.time)); return list; }
    public JSONObject json() throws JSONException {
        JSONObject o=new JSONObject().put("id",id).put("title",title).put("city",city).put("start",start).put("days",days).put("budget",budget);
        JSONArray ss=new JSONArray(), es=new JSONArray(), is=new JSONArray();
        for(Stop s:stops)ss.put(new JSONObject().put("name",s.name).put("time",s.time).put("day",s.day).put("duration",s.duration).put("mode",s.mode).put("note",s.note).put("cost",s.cost));
        for(Expense e:expenses)es.put(new JSONObject().put("name",e.name).put("category",e.category).put("amount",e.amount));
        for(Item i:items)is.put(new JSONObject().put("name",i.name).put("done",i.done));
        return o.put("stops",ss).put("expenses",es).put("items",is);
    }
    private static String bounded(String s,int max) { if(s.length()>max)throw new IllegalArgumentException("文字过长"); return s; }
    private static long amount(long n) { if(n<0 || n>10000000000L)throw new IllegalArgumentException("金额无效");return n; }
    public static Trip from(JSONObject o) throws JSONException {
        Trip t=new Trip(); t.id=bounded(o.getString("id"),100); t.title=bounded(o.getString("title"),80); t.city=bounded(o.getString("city"),80);
        t.start=LocalDate.parse(o.getString("start")).toString();t.days=o.getInt("days");t.budget=amount(o.getLong("budget"));
        if(t.days<1||t.days>60||t.title.trim().isEmpty()||t.city.trim().isEmpty())throw new IllegalArgumentException("行程无效");
        JSONArray a=o.getJSONArray("stops");if(a.length()>1000)throw new IllegalArgumentException("地点太多");
        for(int k=0;k<a.length();k++) { JSONObject v=a.getJSONObject(k); Stop s=new Stop();s.name=bounded(v.getString("name"),120);s.time=v.getString("time");java.time.LocalTime.parse(s.time);if(!s.time.matches("\\d{2}:\\d{2}"))throw new IllegalArgumentException("时间无效");s.day=v.getInt("day");s.duration=v.getInt("duration");if(s.day<0||s.day>=t.days||s.duration<1||s.duration>1440)throw new IllegalArgumentException("日程无效");s.mode=bounded(v.getString("mode"),30);s.note=bounded(v.getString("note"),2000);s.cost=amount(v.getLong("cost"));t.stops.add(s); }
        a=o.getJSONArray("expenses");if(a.length()>5000)throw new IllegalArgumentException("费用太多");for(int k=0;k<a.length();k++){JSONObject v=a.getJSONObject(k);Expense e=new Expense();e.name=bounded(v.getString("name"),120);e.category=bounded(v.getString("category"),30);e.amount=amount(v.getLong("amount"));t.expenses.add(e);}
        a=o.getJSONArray("items");if(a.length()>1000)throw new IllegalArgumentException("清单太长");for(int k=0;k<a.length();k++){JSONObject v=a.getJSONObject(k);Item i=new Item();i.name=bounded(v.getString("name"),120);i.done=v.getBoolean("done");t.items.add(i);}
        return t;
    }
    public static class Stop { public String name="", time="09:00", mode="步行", note=""; public int day=0,duration=60; public long cost=0; }
    public static class Expense { public String name="",category="餐饮";public long amount; }
    public static class Item { public String name;public boolean done; public Item(){} public Item(String n){name=n;} }
    public static Trip demo() {
        Trip t=new Trip();t.title="杭州 · 把日子交给山水";t.city="杭州";t.start=LocalDate.now().plusDays(7).toString();t.days=3;t.budget=180000;
        String[] names={"西湖 · 断桥残雪","北山街","曲院风荷","灵隐寺","法喜寺","小河直街"};String[] times={"09:00","11:00","14:00","09:30","14:00","10:00"};
        for(int k=0;k<names.length;k++){Stop s=new Stop();s.name=names[k];s.time=times[k];s.day=k<3?0:k<5?1:2;s.duration=90;s.mode=k==0?"地铁":"步行";s.note=k==0?"示例安排：沿湖慢走，给拍照留一点时间。":"示例地点，请自行确认开放时间与交通。";s.cost=k==3?7500:0;t.stops.add(s);}
        t.items.add(new Item("身份证 / 护照"));t.items.add(new Item("充电器与充电宝"));t.items.add(new Item("确认住宿与车票"));return t;
    }
}
