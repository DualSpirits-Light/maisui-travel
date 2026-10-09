package cn.lvxu.travel;
import android.content.*;import android.view.*;import android.widget.*;import android.os.*;import java.time.*;import java.time.format.DateTimeFormatter;
/** Read-only travel-day overview; updates its own page without rebuilding the underlying map. */
final class TravelDayUi {
 private final MainActivity a;private final Trip trip;private final PageUi page;private final Handler timer=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){if(!page.alive())return;if(page.dialog.getWindow().getDecorView().hasWindowFocus())render(LocalDateTime.now());timer.postDelayed(this,60000);}};
 TravelDayUi(MainActivity a,Trip trip){this.a=a;this.trip=trip;page=new PageUi(a,"出行速览");}
 static void entry(MainActivity a,Trip trip){
  View label=a.body.getChildAt(a.body.getChildCount()-1);a.body.removeView(label);LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
  TextView button=a.action("出行速览",false,()->new TravelDayUi(a,trip).show());button.setTextSize(14);button.setContentDescription("查看今天与下一项安排");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=a.dp(10);row.addView(button,p);a.body.addView(row);
 }
 void show(){render(LocalDateTime.now());page.dialog.setOnDismissListener(d->timer.removeCallbacks(tick));page.surface.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){timer.removeCallbacks(tick);}});page.surface.getViewTreeObserver().addOnWindowFocusChangeListener(focused->{timer.removeCallbacks(tick);if(focused&&page.alive()){render(LocalDateTime.now());timer.postDelayed(tick,60000);}});page.show();}
 void render(LocalDateTime now){
  page.body.removeAllViews();TravelDaySummary s=TravelDaySummary.forTrip(trip,now);
  page.body.addView(a.bold(trip.title,20,MainActivity.INK));a.space(page.body,6);page.body.addView(a.text(now.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))+" · 设备本地时间",12,MainActivity.MUTED));a.space(page.body,12);
  String status;
  switch(s.state){case UPCOMING:status="距旅行开始 "+java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(),s.firstDay.toLocalDate())+" 天";break;case ACTIVE:status="今天 · 按计划此刻";break;case GAP:status="今天 · 下一项安排";break;case EMPTY:status="今天暂无安排";break;case DAY_OVER:status="今天的计划时间已过";break;case PAST:status="旅行日期已过";break;default:status="旅行日期有误，请检查";}
  LinearLayout hero=a.card(page.body);hero.addView(a.bold(status,20,MainActivity.INK));a.space(hero,6);hero.addView(a.text("仅依据计划时间，不代表实际到达、完成或实时交通。",12,MainActivity.MUTED));
  if(s.overlapCount>1){a.space(hero,8);hero.addView(a.text("此刻有 "+s.overlapCount+" 项计划时间重叠，请检查安排。",14,MainActivity.ORANGE));}
  if(s.current!=null)stop(hero,"按计划此刻",s.current);if(s.next!=null)stop(hero,"下一项安排",s.next);
  if(s.current==null&&s.next==null&&s.state!=TravelDaySummary.State.INVALID){a.space(hero,8);hero.addView(a.text(s.state==TravelDaySummary.State.PAST?"可以回看这段旅行的日程和回忆。":"给今天留一点自由，或再添加想去的地方。",14,MainActivity.MUTED));}
  if(s.reservation!=null){LinearLayout fixed=a.card(page.body);fixed.addView(a.bold("固定预约",16,MainActivity.INK));fixed.addView(a.text(s.reservation.name+" · "+when(s.reservation),14,MainActivity.INK));a.space(fixed,4);fixed.addView(a.text("调整日程时保留该固定时间；请自行确认预约凭证。",12,MainActivity.MUTED));fixed.addView(a.action("查看预约地点",false,()->details(s.reservation)));}
  int pending=0;for(Trip.Item item:trip.items)if(!item.done)pending++;if(pending>0){LinearLayout ready=a.card(page.body);ready.addView(a.text("旅行清单还有 "+pending+" 项未勾选",14,MainActivity.INK));ready.addView(a.action("查看清单",false,()->{page.dialog.dismiss();a.active=trip;a.page=3;a.render();}));}
  if(s.state!=TravelDaySummary.State.INVALID){int target=s.current!=null?s.current.day:s.next!=null?s.next.day:s.todayDay>=0?s.todayDay:0;
   String label=s.todayDay>=0?"查看今天行程":"查看安排所在日";int jump=s.todayDay>=0?s.todayDay:target;
   a.pair(page.body,a.action(label,true,()->{page.dialog.dismiss();a.active=trip;a.page=1;a.day=jump;a.render();}),a.action("复制当日日程",false,()->DayItineraryTextUi.show(a,trip,jump)));}
  a.space(page.body,8);page.body.addView(a.action("刷新计划状态",false,()->render(LocalDateTime.now())));
 }
 private String when(Trip.Stop stop){return TravelDaySummary.start(trip,stop).format(DateTimeFormatter.ofPattern("M月d日"))+" · "+ItineraryFormat.timeRange(stop);}
 private void stop(LinearLayout parent,String label,Trip.Stop stop){a.space(parent,12);parent.addView(a.text(label+" · "+when(stop),13,MainActivity.MUTED));parent.addView(a.bold(stop.name,18,MainActivity.INK));a.space(parent,6);a.pair(parent,a.action("地点详情",false,()->details(stop)),a.action("地图查看",false,()->a.map(stop)));}
 private void details(Trip.Stop stop){page.dialog.dismiss();a.showPlaceDetails(trip,stop);}
}
