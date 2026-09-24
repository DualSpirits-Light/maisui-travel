package cn.lvxu.travel;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

/** Regression coverage for visible tags and the search filter controls. */
final class StageFiveSearchTagsTest {
 private StageFiveSearchTagsTest() {}
 static int run(Instrumentation in) throws Exception {
  Context context=in.getTargetContext(); MainActivity activity=null; int checks=0;
  try {
   context.getSharedPreferences("app-prefs-v2",0).edit().putBoolean("tutorial",true).apply();
   activity=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
   final MainActivity host=activity; in.waitForIdleSync();
   Trip trip=new Trip();trip.id="stage-five";trip.title="标签旅行";trip.city="测试城";trip.start="2026-09-20";trip.days=1;
   trip.tagIds.add("food");trip.tagNames.put("food","美食");trip.tagIds.add("nature");trip.tagNames.put("nature","自然");trip.tagIds.add("photo");trip.tagNames.put("photo","摄影");trip.tagIds.add("coast");trip.tagNames.put("coast","海岸");
   Trip.Stop stop=new Trip.Stop();stop.id="sunset";stop.name="日落码头";stop.day=0;stop.time="18:00";stop.cost=2500;stop.note="日落拍照";stop.tagIds.add("photo");stop.tagNames.put("photo","摄影");stop.tagIds.add("coast");stop.tagNames.put("coast","海岸");trip.stops.add(stop);
   Trip other=new Trip();other.id="stage-five-other";other.title="另一段旅行";other.city="另一座城";other.start="2026-10-01";other.days=1;Trip.Stop otherStop=new Trip.Stop();otherStop.id="other-stop";otherStop.name="另一处码头";otherStop.day=0;otherStop.time="18:00";other.stops.add(otherStop);
   in.runOnMainSync(()->{host.trips.clear();host.trips.add(trip);host.trips.add(other);host.active=trip;host.page=0;host.render();});in.waitForIdleSync();
   checks+=check(find(host.root,"标签：美食、自然、摄影、+1")!=null,"trip card shows compact readable tag summary; saw "+labels(host.root));
   in.runOnMainSync(()->{host.page=1;host.render();});in.waitForIdleSync();
   View stopTags=find(host.root,"标签：摄影、海岸");checks+=check(stopTags instanceof TextView,"place card shows its readable tag summary");
   if(stopTags instanceof TextView){int bg=ThemeViews.background(stopTags.getBackground(),MainActivity.SURFACE);checks+=check(ThemeColors.contrast(((TextView)stopTags).getCurrentTextColor(),bg)>=4.5,"tag foreground stays readable on its chip");}
   View placeCard=cardFor(host.root,"日落码头");checks+=check(placeCard.getContentDescription()!=null&&placeCard.getContentDescription().toString().equals("查看地点详情：日落码头"),"place card has a clear details action");final boolean[] opened={false};in.runOnMainSync(()->opened[0]=placeCard.performClick());checks+=check(opened[0],"place card opens details");UiAutomation ui=in.getUiAutomation();assertDetails(ui,"2026年9月20日");ui.performGlobalAction(1);SystemClock.sleep(180);
   in.runOnMainSync(()->host.openSearchResult(new SearchIndex.Result("地点",trip.id,stop.id,stop.name,"",stop.cost)));assertDetails(ui);ui.performGlobalAction(1);SystemClock.sleep(180);
   in.runOnMainSync(()->host.openSearchResult(new SearchIndex.Result("地点",other.id,otherStop.id,otherStop.name,"",otherStop.cost)));assertDetails(ui,"2026年10月1日");ui.performGlobalAction(1);SystemClock.sleep(180);checks+=check(host.active==trip,"viewing another trip's place keeps the underlying trip active");
   TextView placeName=title(host.root,"日落码头");final boolean[] titleOpened={false};in.runOnMainSync(()->titleOpened[0]=placeName.performClick());checks+=check(titleOpened[0],"tapping the place name opens its details");assertDetails(ui,"2026年9月20日");AccessibilityNodeInfo checkin=wait(ui,"以此地点新建打卡");in.runOnMainSync(()->checkin.performAction(AccessibilityNodeInfo.ACTION_CLICK));in.waitForIdleSync();checks+=check(host.active==trip,"place check-in stays with the rendered trip");ui.performGlobalAction(1);SystemClock.sleep(180);
   SearchIndex.Query q=new SearchIndex.Query();q.text="日落";q.tagId="photo";q.type="地点";q.from=LocalDate.of(2026,9,20);q.to=q.from;q.minCost=2000L;q.maxCost=3000L;
   checks+=check(SearchIndex.search(Collections.singletonList(trip),q).size()==1,"keyword and every active filter compose");q.type="旅行";checks+=check(SearchIndex.search(Collections.singletonList(trip),q).isEmpty(),"type remains part of combined search");
   final SearchUi[] search={null};in.runOnMainSync(()->{search[0]=new SearchUi(host);search[0].show();});in.waitForIdleSync();
   View searchRoot=surface(search[0]);View tripType=find(searchRoot,"类型：旅行");checks+=check(tripType!=null,"search exposes quick type switches");in.runOnMainSync(tripType::performClick);in.waitForIdleSync();
   checks+=check(find(searchRoot,"当前筛选：类型为旅行")!=null,"search summarizes active type filter");View clearType=find(searchRoot,"清除类型");checks+=check(clearType!=null,"each active filter has its own clear action");in.runOnMainSync(clearType::performClick);in.waitForIdleSync();
   checks+=check(find(searchRoot,"当前筛选：类型为旅行")==null,"clearing one filter updates the summary");
   EditText keyword=keyword(search[0]);in.runOnMainSync(()->keyword.setText("不存在的北极光"));in.waitForIdleSync();
   checks+=check(find(searchRoot,"没有找到与“不存在的北极光”相关的内容。可尝试清除关键词或某个筛选条件。")!=null,"empty search gives an actionable suggestion");
   return checks;
  } finally { if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);} }
 }
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static View cardFor(View root,String title){TextView label=title(root,title);if(label==null||!(label.getParent() instanceof View))throw new AssertionError("place card");return (View)label.getParent();}private static TextView title(View root,String value){if(root instanceof TextView&&value.equals(((TextView)root).getText().toString()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=title(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
 private static void assertDetails(UiAutomation ui)throws Exception{assertDetails(ui,"2026年9月20日");}private static void assertDetails(UiAutomation ui,String date)throws Exception{wait(ui,"地点详情");wait(ui,"旅行日期");wait(ui,date);wait(ui,"开始 - 结束");wait(ui,"18:00 - 19:00");wait(ui,"中搜索");wait(ui,"以此地点新建打卡");}
 private static AccessibilityNodeInfo wait(UiAutomation ui,String text)throws Exception{for(int i=0;i<30;i++){AccessibilityNodeInfo hit=find(ui.getRootInActiveWindow(),text);if(hit!=null)return hit;SystemClock.sleep(100);}throw new AssertionError("Missing details text: "+text);}
 private static View surface(SearchUi search)throws Exception{java.lang.reflect.Field field=SearchUi.class.getDeclaredField("page");field.setAccessible(true);return ((PageUi)field.get(search)).surface;}
 private static EditText keyword(SearchUi search)throws Exception{java.lang.reflect.Field field=SearchUi.class.getDeclaredField("keyword");field.setAccessible(true);return (EditText)field.get(search);}
 private static String labels(View root){StringBuilder out=new StringBuilder();collectLabels(root,out);return out.toString();}private static void collectLabels(View root,StringBuilder out){if(root instanceof TextView)out.append('[').append(((TextView)root).getText()).append('|').append(root.getContentDescription()).append(']');if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)collectLabels(group.getChildAt(i),out);}}
 private static View find(View root,String value){if(root==null)return null;CharSequence description=root.getContentDescription();if(description!=null&&description.toString().contains(value))return root;if(root instanceof TextView){CharSequence text=((TextView)root).getText();if(text!=null&&text.toString().contains(value))return root;}if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),value);if(found!=null)return found;}}return null;}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String value){if(root==null)return null;CharSequence text=root.getText(),description=root.getContentDescription();if(text!=null&&text.toString().contains(value)||description!=null&&description.toString().contains(value))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),value);if(found!=null)return found;}return null;}
}
