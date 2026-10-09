package cn.lvxu.travel;
import android.app.*;
import android.widget.*;
import java.util.*;
final class TravelAdjustmentUi {
 static void show(MainActivity a){if(a.active==null)return;Trip source=a.active;int day=a.day;
  new RoundedDialogs.Builder(a).setTitle("调整第 "+(day+1)+" 天").setItems(new String[]{"顺延 15 分钟","顺延 30 分钟","顺延 1 小时","提前 30 分钟"},(d,i)->preview(a,source,day,new int[]{15,30,60,-30}[i])).setNegativeButton("取消",null).show();
 }
 private static void preview(MainActivity a,Trip source,int day,int minutes){try{
  Trip before=TravelAdjustment.copy(source),after=TravelAdjustment.shift(source,day,minutes);TravelUndo guard=new TravelUndo("调整时间",before,before);LinearLayout form=a.col();a.pad(form,20);
  form.addView(a.text("仅调整这一天未锁定的地点。已预约的时间请先在地点编辑中锁定；不会根据时间猜测你是否已经游玩。",14,MainActivity.MUTED));
  for(int i=0;i<source.stops.size();i++){Trip.Stop old=source.stops.get(i),now=after.stops.get(i);if(old.day!=day)continue;LinearLayout card=a.card(form);card.addView(a.bold(old.name,16,MainActivity.INK));card.addView(a.text(old.timeLocked?"预约已锁定 · "+old.time:"第 "+(old.day+1)+" 天 "+old.time+" → 第 "+(now.day+1)+" 天 "+now.time,14,MainActivity.MUTED));}
  ArrayList<String> conflicts=TravelAdjustment.conflicts(after);if(!conflicts.isEmpty())form.addView(a.text("请核对：\n"+String.join("\n",conflicts),14,MainActivity.ORANGE));form.addView(a.text("安排间隔不等于交通耗时；请在地图路线中核实交通与开放时间。应用后可撤销本次调整。",12,MainActivity.MUTED));ScrollView scroll=new ScrollView(a);scroll.addView(form);
  AlertDialog dialog=new RoundedDialogs.Builder(a).setTitle("确认调整预览").setView(scroll).setNegativeButton("取消",null).setPositiveButton(conflicts.isEmpty()?"应用调整":"确认冲突后仍应用",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{if(!a.trips.contains(source)||!guard.matches(source)){a.toast("旅行已变化，请重新预览");return;}int index=a.trips.indexOf(source);a.trips.set(index,after);a.active=after;if(!a.save()){a.trips.set(index,source);a.active=source;return;}a.recordUndo("调整时间",before,after);a.render();dialog.dismiss();}));dialog.show();
 }catch(IllegalArgumentException e){a.toast(e.getMessage());}}
}
