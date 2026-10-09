package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;

/** Exercises the compact timeline at narrow width; run again with the device's large font setting. */
final class CompactTimelineUiTest {
    static int run(Instrumentation in) throws Exception {
        Context context=in.getTargetContext();
        MainActivity host=null;int count=0;
        try(TestStartupGuard guard=new TestStartupGuard(context)) {
            host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            in.waitForIdleSync();MainActivity a=host;
            Trip trip=new Trip();trip.title="跨年旅行";trip.start="2026-12-31";trip.days=2;
            Trip.Stop first=new Trip.Stop();first.name="甘肃省博物馆丝绸之路文明主题展览与周边漫步";
            first.time="09:00";first.duration=60;first.cost=12800;first.timeLocked=true;
            first.address="兰州市七里河区西津西路3号";first.openingHours="09:00—17:00";
            first.note="请带身份证，提前十五分钟检票";first.rating=4.5f;first.tagNames.put("legacy-humanities","人文");
            Trip.Stop second=new Trip.Stop();second.name="同行集合";second.time="09:30";
            second.duration=60;second.lat=36.06;second.lon=103.83;
            trip.stops.add(first);trip.stops.add(second);trip.normalize();
            final LinearLayout[] rendered=new LinearLayout[1];
            in.runOnMainSync(()->{
                a.trips.clear();a.trips.add(trip);a.active=trip;a.body=a.col();
                new ItineraryTimelineUi(a).show(trip,0,trip.onDay(0));
                rendered[0]=a.body;measure(a,rendered[0]);
            });
            View root=rendered[0];
            count+=check(find(root,"2026年12月31日 周四")!=null,"cross-year trip retains year on its starting day");
            count+=check(find(root,"预约时间已锁定")!=null,"reservation remains visible in compact card");
            count+=check(find(root,"人文")!=null,"names-only embedded tags remain visible");
            count+=check(find(root,"与其他地点的安排时间重叠，请检查。")!=null,"conflict remains visible");
            count+=check(find(root,"09:00 — 10:00")!=null,"exact reservation time remains visible");
            count+=check(find(root,"停留 60 分钟 · 预计 ¥128")!=null,"duration and expense remain available");
            TextView title=find(root,first.name);
            count+=check(title!=null&&title.getLineCount()>1&&title.getEllipsize()==null,"long place name wraps without being hidden");
            count+=check(find(root,first.address)==null&&find(root,"营业时间："+first.openingHours)==null,
                    "secondary metadata does not crowd itinerary overview");
            count+=check(PlaceDetailsContent.rows(trip,first).stream().anyMatch(r->first.address.equals(r.value))
                    &&PlaceDetailsContent.rows(trip,first).stream().anyMatch(r->first.note.equals(r.value)),
                    "details still retain full address and note");
            View firstRow=root.findViewWithTag("timeline-stop:"+first.id);
            View secondRow=root.findViewWithTag("timeline-stop:"+second.id);
            count+=check(find(firstRow,"在行程地图定位")==null&&find(secondRow,"在行程地图定位")!=null,
                    "map focus is available only for a located place");
            TextView map=find(firstRow,"地图查看"),edit=find(firstRow,"编辑");
            count+=check(map!=null&&edit!=null&&map.isClickable()&&edit.isClickable(),"short actions preserve map and edit entry points");
            count+=check(map.getLineCount()==1&&edit.getLineCount()==1&&map.getHeight()==edit.getHeight(),
                    "narrow action row stays aligned without detached arrow line");
            count+=check(firstRow.isClickable()&&firstRow.isLongClickable(),"details click and drag entry points remain available");
            in.runOnMainSync(()->{a.body=a.col();new ItineraryTimelineUi(a).show(trip,1,new ArrayList<>());});
            count+=check(find(a.body,"2027年1月1日 周五")!=null,"new year is explicit after the boundary");
            trip.start=LocalDate.now().withMonth(10).withDayOfMonth(14).toString();trip.days=3;
            in.runOnMainSync(()->{a.body=a.col();new ItineraryTimelineUi(a).show(trip,0,new ArrayList<>());});
            TextView date=(TextView)a.body.getChildAt(0);
            count+=check(date.getText().toString().startsWith("10月14日 周")&&!date.getText().toString().contains("年"),
                    "ordinary current-year trip uses compact month and day");
            return count;
        } finally {
            if(host!=null){MainActivity closing=host;in.runOnMainSync(closing::finish);}
        }
    }
    private static void measure(MainActivity a,View root){
        root.measure(View.MeasureSpec.makeMeasureSpec(a.dp(280),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
    }
    private static int check(boolean condition,String label){if(!condition)throw new AssertionError(label);return 1;}
    private static TextView find(View view,String value){
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){
            TextView found=find(group.getChildAt(i),value);if(found!=null)return found;
        }}return null;
    }
}
