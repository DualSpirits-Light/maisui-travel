package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Expense form cancellation and original camera import on the dedicated emulator. */
final class ExpensePhotosUiTest {
 static int run(Instrumentation in)throws Exception {
  Context context=in.getTargetContext();TripStore persisted=new TripStore(context);ArrayList<Trip> saved=persisted.read();AppPrefs prefs=new AppPrefs(context);JSONObject savedPrefs=prefs.exportJson();TestStartupGuard startup=new TestStartupGuard(context);prefs.setDefaultHome("home");MainActivity host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));in.waitForIdleSync();
  Trip trip=Trip.demo();Trip.Expense expense=new Trip.Expense();expense.name="票据测试";expense.amount=1200;expense.occurredAt="2026-10-05T12:30";trip.expenses.add(expense);ArrayList<String> created=new ArrayList<>();int checks=0;
  try {
   String original=fixture(host);created.add(original);expense.photo=original;
   in.runOnMainSync(()->{host.trips.clear();host.trips.add(trip);host.active=trip;new FinanceUi(host).expense(expense);});in.waitForIdleSync();
   click(in,"移除照片");click(in,"取消");if(!expense.photo.equals(original)||!host.mediaFile(original).isFile())throw new AssertionError("cancel removal changed saved receipt");checks++;
   in.runOnMainSync(()->new FinanceUi(host).expense(expense));in.waitForIdleSync();click(in,"添加 / 更换照片");
   MainActivity.ImageCallback selection=(MainActivity.ImageCallback)get(host.media,"callback");click(in,"添加照片",false); // chooser title assertion
   String draft=fixture(host);created.add(draft);in.runOnMainSync(()->selection.selected(draft));click(in,"取消"); // closes chooser
   click(in,"取消");if(!expense.photo.equals(original)||host.mediaFile(draft).exists())throw new AssertionError("cancelled draft receipt persisted or leaked");checks++;
   String late=fixture(host);created.add(late);in.runOnMainSync(()->selection.selected(late));if(host.mediaFile(late).exists())throw new AssertionError("late callback leaked receipt");checks++;
   final String[] selected={null};File camera=File.createTempFile("expense-camera-",".png",host.getCacheDir());byte[] bytes=Files.readAllBytes(host.mediaFile(original).toPath());Files.write(camera.toPath(),bytes);
   set(host.media,"cameraFile",camera);set(host.media,"cropSelection",false);set(host.media,"checkinCapture",false);set(host.media,"callback",(MainActivity.ImageCallback)path->selected[0]=path);
   in.runOnMainSync(()->host.media.onResult(MediaController.CAMERA,Activity.RESULT_OK,null));for(int i=0;i<60&&selected[0]==null;i++)android.os.SystemClock.sleep(100);in.waitForIdleSync();
   if(selected[0]==null||camera.exists()||!Arrays.equals(bytes,Files.readAllBytes(host.mediaFile(selected[0]).toPath())))throw new AssertionError("camera receipt original copy/cleanup failed");created.add(selected[0]);checks++;
   in.runOnMainSync(()->new FinanceUi(host).expense(null));in.waitForIdleSync();
   setField(in,"账单名称","原图账单保存测试");setField(in,"实际金额（人民币）","18.50");String displayed=field(in,"日期时间").getText().toString();java.time.LocalDateTime defaultTime=DateTimeValues.parse(displayed,null);if(defaultTime==null||displayed.contains("T"))throw new AssertionError("default expense timestamp display invalid");checks++;
   click(in,"添加 / 更换照片");MainActivity.ImageCallback saving=(MainActivity.ImageCallback)get(host.media,"callback");String receipt=fixture(host);created.add(receipt);in.runOnMainSync(()->saving.selected(receipt));click(in,"取消");click(in,"保存");
   Trip savedTrip=persisted.read().stream().filter(t->t.id.equals(trip.id)).findFirst().orElseThrow(()->new AssertionError("expense trip not persisted"));Trip.Expense savedExpense=savedTrip.expenses.stream().filter(e->e.name.equals("原图账单保存测试")).findFirst().orElseThrow(()->new AssertionError("expense form save rejected visible datetime"));
   if(savedExpense.amount!=1850||!savedExpense.occurredAt.equals(defaultTime.withSecond(0).withNano(0).toString())||!savedExpense.photo.equals(receipt)||!Arrays.equals(Files.readAllBytes(host.mediaFile(receipt).toPath()),Files.readAllBytes(host.mediaFile(savedExpense.photo).toPath())))throw new AssertionError("saved expense amount/date/receipt differs");checks++;
   return checks;
  }finally{in.runOnMainSync(host::finish);in.waitForIdleSync();for(String path:created){File file=host.mediaFile(path);if(file!=null)file.delete();}persisted.save(saved);persisted.close();prefs.importJson(savedPrefs);startup.close();}
 }
 private static void setField(Instrumentation in,String label,String text)throws Exception{AccessibilityNodeInfo node=field(in,label);Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Cannot set "+label);android.os.SystemClock.sleep(100);}
 private static AccessibilityNodeInfo field(Instrumentation in,String label){for(int attempt=0;attempt<20;attempt++){ArrayList<AccessibilityNodeInfo> nodes=new ArrayList<>();flatten(in.getUiAutomation().getRootInActiveWindow(),nodes);boolean after=false;for(AccessibilityNodeInfo node:nodes){if(label.contentEquals(node.getText()==null?"":node.getText()))after=true;else if(after&&node.isEditable())return node;}scroll(in);android.os.SystemClock.sleep(100);}throw new AssertionError("Missing expense field: "+label);}
 private static void flatten(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> out){if(root==null)return;out.add(root);for(int i=0;i<root.getChildCount();i++)flatten(root.getChild(i),out);}
 private static void scroll(Instrumentation in){ArrayList<AccessibilityNodeInfo> nodes=new ArrayList<>();flatten(in.getUiAutomation().getRootInActiveWindow(),nodes);for(AccessibilityNodeInfo node:nodes)if(node.isScrollable()){node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);return;}}
 private static String fixture(MainActivity host)throws Exception{Bitmap image=Bitmap.createBitmap(18,12,Bitmap.Config.ARGB_8888);try{return host.media.files.save(image,"test",90);}finally{image.recycle();}}
 private static Object get(Object object,String field)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
 private static void set(Object object,String field,Object value)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);}
 private static void click(Instrumentation in,String label){click(in,label,true);}
 private static void click(Instrumentation in,String label,boolean action){for(int i=0;i<30;i++){AccessibilityNodeInfo node=find(in.getUiAutomation().getRootInActiveWindow(),label);if(node!=null){if(action){node.performAction(AccessibilityNodeInfo.ACTION_CLICK);android.os.SystemClock.sleep(150);in.waitForIdleSync();}return;}if(i>2)scroll(in);android.os.SystemClock.sleep(100);}throw new AssertionError("Missing expense action: "+label);}
 private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label){if(root==null)return null;if(label.contentEquals(root.getText()==null?"":root.getText()))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo node=find(root.getChild(i),label);if(node!=null)return node;}return null;}
}
