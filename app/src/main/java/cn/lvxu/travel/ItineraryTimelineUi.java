package cn.lvxu.travel;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import static cn.lvxu.travel.MainActivity.*;

/** A single reorderable row owns its incoming connection and destination card. */
final class ItineraryTimelineUi {
    private final MainActivity a;
    ItineraryTimelineUi(MainActivity activity){a=activity;}
    void show(Trip trip,int day,ArrayList<Trip.Stop> schedule){
        TextView date=a.bold(dateLabel(trip,day),16,INK);
        date.setContentDescription(ItineraryFormat.dateLabel(trip,day));
        a.body.addView(date);a.space(a.body,6);
        long cost=0;for(Trip.Stop stop:schedule)cost+=stop.cost;
        a.body.addView(a.text(schedule.size()+" 个地点 · 预计 ¥"+Trip.money(cost),13,MUTED));a.space(a.body,12);
        if(schedule.isEmpty()){
            LinearLayout empty=a.card(a.body);empty.addView(a.bold("今天，想去哪里？",20,INK));a.space(empty,8);
            empty.addView(a.text("这一天还没有安排。添加地点或导入地图链接，慢慢填满期待。",14,MUTED));return;
        }
        StopReorderLayout list=new StopReorderLayout(a,trip,day);a.body.addView(list);
        for(int i=0;i<schedule.size();i++){
            Trip.Stop stop=schedule.get(i),previous=i==0?null:schedule.get(i-1);
            LinearLayout row=a.row();row.setGravity(Gravity.TOP);row.setTag("timeline-stop:"+stop.id);
            LinearLayout rail=a.col();rail.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView marker=a.bold(String.valueOf(i+1),12,PRIMARY_TEXT);marker.setGravity(Gravity.CENTER);marker.setBackground(a.shape(GREEN,16));
            rail.addView(marker,new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));
            View line=new View(a);line.setBackgroundColor(LINE);LinearLayout.LayoutParams lineParams=new LinearLayout.LayoutParams(a.dp(2),0,1);lineParams.topMargin=a.dp(5);rail.addView(line,lineParams);
            LinearLayout.LayoutParams railParams=new LinearLayout.LayoutParams(a.dp(28),-1);railParams.rightMargin=a.dp(8);row.addView(rail,railParams);
            LinearLayout content=a.col();row.addView(content,new LinearLayout.LayoutParams(0,-2,1));
            TextView connection=a.text(ItineraryFormat.connection(previous,stop),12,MUTED);connection.setPadding(0,a.dp(4),0,a.dp(10));content.addView(connection);
            LinearLayout card=a.card(content);a.pad(card,14);
            card.addView(a.bold(stop.name,18,INK));a.space(card,6);
            card.addView(a.bold(ItineraryFormat.timeRange(stop),15,INK));a.space(card,6);
            if(stop.timeLocked){card.addView(a.text("预约时间已锁定",12,GREEN));a.space(card,6);}
            card.addView(a.text("停留 "+stop.duration+" 分钟 · 预计 ¥"+Trip.money(stop.cost),12,MUTED));
            if(!stop.tagIds.isEmpty()||!stop.tagNames.isEmpty()){a.space(card,8);TagViews.add(a,card,stop.tagIds,stop.tagNames);}
            if(!stop.note.isEmpty()){
                a.space(card,8);TextView note=a.text(stop.note,13,MUTED);
                note.setMaxLines(2);note.setEllipsize(TextUtils.TruncateAt.END);card.addView(note);
            }
            a.space(card,8);card.addView(a.text("查看地点详情",12,MUTED));
            boolean overlap=false;for(Trip.Stop other:schedule)if(other!=stop&&ItineraryFormat.overlaps(stop,other)){overlap=true;break;}
            if(overlap){a.space(card,8);card.addView(a.text("与其他地点的安排时间重叠，请检查。",12,ORANGE));}
            a.space(card,10);
            TextView map=a.action("地图查看",false,()->a.map(stop));
            map.setContentDescription("使用"+new MapService(a).name()+"查看："+stop.name);
            TextView edit=a.action("编辑",false,()->a.stopEditor(stop));edit.setContentDescription("编辑地点："+stop.name);
            card.addView(new Actions(a,map,edit));
            if(stop.lat!=null&&stop.lon!=null){a.space(card,8);card.addView(a.action("在行程地图定位",false,()->a.focusPlace(stop)));}
            card.setContentDescription("查看地点详情："+stop.name);card.setFocusable(true);card.setOnClickListener(v->a.showPlaceDetails(trip,stop));
            row.setOnClickListener(v->a.showPlaceDetails(trip,stop));
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));list.bind(row,stop,()->a.showPlaceDetails(trip,stop));
        }
        if(schedule.size()>1){a.body.addView(a.text("长按地点拖动排序，时间保持不变；实际交通耗时请查看地图路线。",12,MUTED));}
    }

    private static String dateLabel(Trip trip,int day){
        LocalDate start=LocalDate.parse(trip.start),date=start.plusDays(day);
        boolean showYear=start.getYear()!=start.plusDays(trip.days-1L).getYear()||date.getYear()!=LocalDate.now().getYear();
        String[] weekdays={"周一","周二","周三","周四","周五","周六","周日"};
        return date.format(DateTimeFormatter.ofPattern(showYear?"yyyy年M月d日":"M月d日",Locale.CHINA))
                +" "+weekdays[date.getDayOfWeek().getValue()-1];
    }

    /** Keep short actions equal height; stack them when the user's font needs more width. */
    private static final class Actions extends LinearLayout {
        private final MainActivity activity;private final TextView map,edit;
        private int appliedOrientation=-1;
        Actions(MainActivity a,TextView map,TextView edit){
            super(a);activity=a;this.map=map;this.edit=edit;addView(map);addView(edit);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            float needed=Math.max(actionWidth(map),actionWidth(edit))*2+activity.dp(8);
            boolean stack=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width<needed;
            int orientation=stack?VERTICAL:HORIZONTAL;
            if(appliedOrientation!=orientation){
                appliedOrientation=orientation;setOrientation(orientation);
                LayoutParams first=new LayoutParams(stack?-1:0,stack?-2:-1,stack?0:1);
                LayoutParams second=new LayoutParams(stack?-1:0,stack?-2:-1,stack?0:1);
                if(stack)second.topMargin=activity.dp(8);else second.leftMargin=activity.dp(8);
                map.setLayoutParams(first);edit.setLayoutParams(second);
            }
            super.onMeasure(widthSpec,heightSpec);
        }
        private float actionWidth(TextView action){
            return Math.max(activity.dp(48),action.getPaint().measureText(action.getText().toString())
                    +action.getPaddingLeft()+action.getPaddingRight());
        }
    }
}
