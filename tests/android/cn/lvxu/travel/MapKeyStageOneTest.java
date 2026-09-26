package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Offline configuration contract; does not claim live Android SDK authorization. */
final class MapKeyStageOneTest {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();ApiConfig config=new ApiConfig(c);JSONObject before=config.read();
  android.content.SharedPreferences privacy=c.getSharedPreferences("amap-privacy",0),prefs=c.getSharedPreferences("app-prefs-v2",0);
  boolean agreed=privacy.getBoolean("agreed-v1",false),tutorial=prefs.getBoolean("tutorial",false);
  java.lang.reflect.Field initialized=AmapRuntime.class.getDeclaredField("initializedKey");initialized.setAccessible(true);Object old=initialized.get(null);
  MainActivity activity=null;final AlertDialog[] dialog={null};int checks=0;
  try {
   initialized.set(null,null);privacy.edit().putBoolean("agreed-v1",false).commit();prefs.edit().putBoolean("tutorial",true).commit();
   config.put("amapAndroidKey","");config.put("mapKey.amap","offline-web-fixture");
   checks+=check(!AmapRuntime.configured(c)&&!AmapRuntime.prepare(c),"REST key cannot initialize Android SDK");
   checks+=check(AmapIdentity.description(c).matches("(?s).*"+c.getPackageName()+".*(?:[0-9A-F]{2}:){19}[0-9A-F]{2}.*"),"installed package and signing SHA1 shown");
   activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));final MainActivity host=activity;in.waitForIdleSync();
   final int[] resumed={0};in.runOnMainSync(()->dialog[0]=new AdvancedSettingsUi(host).androidKey(()->resumed[0]++));
   in.runOnMainSync(()->{editor(dialog[0].getWindow().getDecorView()).setText("cancelled-fixture");dialog[0].getButton(-2).performClick();});
   checks+=check(config.amapAndroidKey().isEmpty()&&resumed[0]==0,"cancel keeps configuration and pending action unchanged");
   in.runOnMainSync(()->dialog[0]=new AdvancedSettingsUi(host).androidKey(()->resumed[0]++));
   in.runOnMainSync(()->{editor(dialog[0].getWindow().getDecorView()).setText("  offline-android-fixture  ");dialog[0].getButton(-1).performClick();});
   checks+=check(config.amapAndroidKey().equals("offline-android-fixture")&&resumed[0]==1,"save trims key and resumes intent once");
   checks+=check(config.mapKey("amap").equals("offline-web-fixture"),"Android save preserves independent REST key");
   checks+=check(!AmapConsent.granted(c)&&!AmapRuntime.prepare(c),"save does not grant privacy consent or initialize SDK");
   checks+=check(!c.getSharedPreferences("secure-vault",0).getString("api-config-v1","").contains("offline-android-fixture"),"stored key is encrypted");
   initialized.set(null,"previous-process-fixture");
   checks+=check(AmapRuntime.needsRestart(c)&&!AmapRuntime.prepare(c),"changed key blocked before SDK access");
   in.runOnMainSync(()->dialog[0]=new AdvancedSettingsUi(host).androidKey(()->resumed[0]++));
   in.runOnMainSync(()->dialog[0].getButton(-3).performClick());in.waitForIdleSync();
   AccessibilityNodeInfo clear=null;
   for(int i=0;i<20&&clear==null;i++){clear=exact(in.getUiAutomation().getRootInActiveWindow(),"清除");if(clear==null)android.os.SystemClock.sleep(100);}
   if(clear==null)throw new AssertionError("clear confirmation missing");clear.performAction(AccessibilityNodeInfo.ACTION_CLICK);in.waitForIdleSync();
   checks+=check(config.amapAndroidKey().isEmpty()&&config.mapKey("amap").equals("offline-web-fixture"),"clear removes only Android key");
   checks+=check(AmapRuntime.needsRestart(c)&&!AmapRuntime.prepare(c)&&resumed[0]==1,"clear gates SDK and does not resume pending action");
   in.getUiAutomation().performGlobalAction(1);in.waitForIdleSync();
   initialized.set(null,null);config.put("mapProvider","amap");
   in.runOnMainSync(()->{host.active=Trip.demo();host.trips.clear();host.trips.add(host.active);host.page=1;host.day=0;host.mapMode=true;host.render();});in.waitForIdleSync();
   waitExact(in,"高德 Android SDK Key");
   checks+=check(host.mapMode&&field(host,"amapUi")!=null&&field(host,"mapUi")==null,"map entry requests Android configuration without OSM fallback");
   click(in,"保存");waitExact(in,"请输入 Android 平台 Key");
   checks+=check(config.amapAndroidKey().isEmpty()&&exact(in.getUiAutomation().getRootInActiveWindow(),"高德 Android SDK Key")!=null,"blank key stays in entry configuration dialog");
   setEditor(in,"offline-map-entry-fixture");click(in,"保存");
   checks+=check(host.mapMode&&field(host,"amapUi")!=null&&field(host,"mapUi")==null,"map configuration resumes map mode and native entry");
   checks+=check(!AmapConsent.granted(c)&&initialized.get(null)==null,"map configuration does not imply SDK privacy consent");
   in.runOnMainSync(()->{View enable=label(host.root,"启用高德地图");if(enable==null)throw new AssertionError("map consent action missing");enable.performClick();});
   click(in,"暂不启用");
   checks+=check(field(host,"mapUi")==null&&field(field(host,"amapUi"),"mapView")==null&&!AmapConsent.granted(c),"declining consent creates neither OSM nor native map view");
   in.runOnMainSync(()->{host.mapMode=false;host.render();});config.put("amapAndroidKey","");
   in.runOnMainSync(()->new MapSearchUi(host).show());waitExact(in,"高德 Android SDK Key");
   checks+=check(exact(in.getUiAutomation().getRootInActiveWindow(),"搜索高德地点")==null,"missing search key prompts configuration first");
   setEditor(in,"offline-search-entry-fixture");click(in,"保存");waitExact(in,"搜索高德地点");
   checks+=check(config.amapAndroidKey().equals("offline-search-entry-fixture")&&!AmapConsent.granted(c)&&initialized.get(null)==null,"saving search key resumes search form without network or consent");
   click(in,"取消");
   return checks;
  } finally {
   if(activity!=null){MainActivity host=activity;in.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();host.finish();});}
   initialized.set(null,old);config.reset();config.putAll(before);privacy.edit().putBoolean("agreed-v1",agreed).commit();prefs.edit().putBoolean("tutorial",tutorial).commit();
  }
 }
 private static Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static View label(View v,String text){if(v instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View hit=label(((ViewGroup)v).getChildAt(i),text);if(hit!=null)return hit;}return null;}
 private static AccessibilityNodeInfo waitExact(Instrumentation in,String text)throws Exception{for(int i=0;i<30;i++){AccessibilityNodeInfo hit=exact(in.getUiAutomation().getRootInActiveWindow(),text);if(hit!=null)return hit;android.os.SystemClock.sleep(100);}throw new AssertionError("missing UI: "+text);}
 private static void click(Instrumentation in,String text)throws Exception{if(!waitExact(in,text).performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("click: "+text);in.waitForIdleSync();android.os.SystemClock.sleep(100);}
 private static void setEditor(Instrumentation in,String value)throws Exception{AccessibilityNodeInfo node=editable(in.getUiAutomation().getRootInActiveWindow());if(node==null)throw new AssertionError("entry editor missing");android.os.Bundle args=new android.os.Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("entry editor rejected input");in.waitForIdleSync();}
 private static AccessibilityNodeInfo editable(AccessibilityNodeInfo n){if(n==null)return null;if(n.isEditable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo hit=editable(n.getChild(i));if(hit!=null)return hit;}return null;}
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static EditText editor(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=editor(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
 private static AccessibilityNodeInfo exact(AccessibilityNodeInfo n,String text){if(n==null)return null;if(text.contentEquals(n.getText()==null?"":n.getText()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo hit=exact(n.getChild(i),text);if(hit!=null)return hit;}return null;}
}
