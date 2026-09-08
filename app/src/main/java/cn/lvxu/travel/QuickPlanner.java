package cn.lvxu.travel;
import android.widget.*;
import java.time.LocalDate;

/** A local, explicit wizard: sample places are never presented as live recommendations. */
final class QuickPlanner {
    private final MainActivity a;
    private String city="杭州",start=LocalDate.now().plusDays(7).toString(),title="",mode="步行";
    private int days=3;private long budget=180000;
    QuickPlanner(MainActivity host){a=host;}
    void start(){if(a.trips.size()>=100){a.toast("旅行数量已达上限");return;}destination();}
    private void destination(){LinearLayout f=a.col();EditText c=a.field(f,"目的地",city,1),d=a.field(f,"出发日期 YYYY-MM-DD",start,1);a.dialog("快速规划 · 1/4 目的地",f,()->{city=a.required(c,80);try{start=LocalDate.parse(d.getText().toString().trim()).toString();}catch(Exception e){throw new IllegalArgumentException("请输入有效日期，如 2026-10-01");}duration();},null);}
    private void duration(){LinearLayout f=a.col();EditText n=a.field(f,"旅行天数（1–60）",String.valueOf(days),2),b=a.field(f,"总预算（元）",Trip.money(budget),8194);f.addView(a.action("上一步",false,this::destination));a.dialog("快速规划 · 2/4 时间与预算",f,()->{days=a.number(n,1,60);budget=Trip.cents(b.getText().toString());preferences();},null);}
    private void preferences(){LinearLayout f=a.col();EditText t=a.field(f,"旅行名称",title.isEmpty()?city+" · 我的旅行":title,1);Spinner m=a.select(f,"主要出行方式",new String[]{"步行","地铁","公交","自驾","出租车","其他"},mode);a.dialog("快速规划 · 3/4 出行偏好",f,()->{title=a.required(t,80);mode=m.getSelectedItem().toString();review();},null);}
    private void review(){LinearLayout f=a.col();boolean demo=city.equals("杭州");f.addView(a.text(city+" · "+start+"\n"+days+" 天 · 预算 ¥"+Trip.money(budget)+"\n主要交通："+mode+"\n\n"+(demo?"将使用杭州示例景点作为起点。开放时间、交通与价格请出发前核实；超过 3 天的日期留给你自由安排。":"将生成按天的规划框架和行前清单。创建后可添加地点，或导入高德分享链接。"),16,MainActivity.INK));a.dialog("快速规划 · 4/4 确认创建",f,()->{if(a.trips.size()>=100)throw new IllegalArgumentException("旅行数量已达上限");Trip t=demo?Trip.demo():new Trip();t.city=city;t.title=title;t.start=start;t.days=days;t.budget=budget;t.stops.removeIf(s->s.day>=days);for(Trip.Stop s:t.stops)s.mode=mode;if(!demo){t.items.add(new Trip.Item("确认住宿与车票"));t.items.add(new Trip.Item("检查证件与充电设备"));}t.normalize();a.trips.add(t);a.active=t;a.page=1;a.day=0;a.mapMode=false;a.changed();},null);}
}
