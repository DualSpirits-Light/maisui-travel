package cn.lvxu.travel;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import static cn.lvxu.travel.MainActivity.*;

/** A single reorderable row owns its incoming connection and destination card. */
final class ItineraryTimelineUi {
    private final MainActivity a;
    ItineraryTimelineUi(MainActivity activity){a=activity;}
    void show(Trip trip,int day,ArrayList<Trip.Stop> schedule){
        a.body.addView(a.bold(ItineraryFormat.dateLabel(trip,day),17,INK));a.space(a.body,8);
        long cost=0;for(Trip.Stop stop:schedule)cost+=stop.cost;
        a.body.addView(a.text(schedule.size()+" 个地点 · 预计 ¥"+Trip.money(cost),13,MUTED));a.space(a.body,14);
        if(schedule.isEmpty()){
            LinearLayout empty=a.card(a.body);empty.addView(a.bold("今天，想去哪里？",20,INK));a.space(empty,8);
            empty.addView(a.text("这一天还没有安排。添加地点或导入地图链接，慢慢填满期待。",14,MUTED));return;
        }
        if(schedule.size()>1){a.body.addView(a.text("长按地点可拖动排序，抵达时间保持不变。\n安排间隔不代表实际交通耗时，路线详情请查看地图。",12,MUTED));a.space(a.body,10);}
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
            card.addView(a.bold(ItineraryFormat.timeRange(stop),15,INK));a.space(card,6);
            card.addView(a.text("停留 "+stop.duration+" 分钟",12,MUTED));a.space(card,10);
            card.addView(a.bold(stop.name,20,INK));a.space(card,8);TagViews.add(a,card,stop.tagIds,stop.tagNames);
            PlaceMediaUi.preview(a,card,stop);
            card.addView(a.text("预计 ¥"+Trip.money(stop.cost),13,MUTED));
            if(!stop.address.isEmpty()){a.space(card,7);card.addView(a.text(stop.address,13,MUTED));}
            if(!stop.openingHours.isEmpty()){a.space(card,7);card.addView(a.text("营业时间："+stop.openingHours,13,MUTED));}
            if(stop.rating!=null){a.space(card,7);card.addView(a.text("评分："+stop.rating+" / 5 · 来源信息请核实",13,MUTED));}
            if(!stop.note.isEmpty()){a.space(card,9);card.addView(a.text(stop.note,14,MUTED));}PlaceMediaUi.notes(a,card,stop);
            boolean overlap=false;for(Trip.Stop other:schedule)if(other!=stop&&ItineraryFormat.overlaps(stop,other)){overlap=true;break;}
            if(overlap){a.space(card,8);card.addView(a.text("与其他地点的安排时间重叠，请检查。",12,ORANGE));}
            a.space(card,12);a.pair(card,a.action(new MapService(a).name()+"查看 ↗",false,()->a.map(stop)),a.action("编辑地点",false,()->a.stopEditor(stop)));
            card.setContentDescription("查看地点详情："+stop.name);card.setOnClickListener(v->a.showPlaceDetails(trip,stop));
            row.setOnClickListener(v->a.showPlaceDetails(trip,stop));
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));list.bind(row,stop,()->a.showPlaceDetails(trip,stop));
        }
    }
}
