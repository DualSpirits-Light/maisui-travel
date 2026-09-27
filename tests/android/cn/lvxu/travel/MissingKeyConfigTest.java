package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Offline verifier injection exercises real dialogs and encrypted writes, never live services. */
final class MissingKeyConfigTest {
 static int run(Instrumentation in)throws Exception {
  Context c=in.getTargetContext();ApiConfig config=new ApiConfig(c);JSONObject before=config.read();
  android.content.SharedPreferences prefs=c.getSharedPreferences("app-prefs-v2",0);
  java.util.Map<String,?> oldPrefs=new java.util.HashMap<>(prefs.getAll());
  android.content.SharedPreferences notes=c.getSharedPreferences("release-notes",0);
  java.util.Map<String,?> oldNotes=new java.util.HashMap<>(notes.getAll());
  android.content.SharedPreferences about=c.getSharedPreferences("about-support-v1",0);
  java.util.Map<String,?> oldAbout=new java.util.HashMap<>(about.getAll());
  about.edit().putBoolean("auto-update",false).commit();
  notes.edit().putBoolean(ReleaseNotes.markerKey(c,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName),true).commit();
  prefs.edit().putBoolean("tutorial",true).commit();
  MainActivity a=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
  try {int n=0;for(boolean search:new boolean[]{false,true})for(int mode=0;mode<5;mode++)n+=scenario(in,a,config,search,mode);return n+entries(in,a,config);}
  finally {in.runOnMainSync(a::finish);config.reset();config.putAll(before);restore(prefs,oldPrefs);restore(notes,oldNotes);restore(about,oldAbout);}
 }
 private static int scenario(Instrumentation in,MainActivity a,ApiConfig config,boolean search,int mode)throws Exception {
  String field=search?"baiduSearchKey":"mapKey.tencent";config.put(field,"old-offline-value");
  CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
  final AlertDialog[] dialog={null};final int[] resumed={0};
  AiSettingsUi.SearchVerifier verifier=key->{started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new Exception("fixture timeout");try{if(mode==1)throw new Exception("offline verification rejected");}finally{done.countDown();}};
  in.runOnMainSync(()->dialog[0]=search?new AiSettingsUi(a,verifier).showSearchConfig(()->resumed[0]++):new AdvancedSettingsUi(a,(id,key)->verifier.verify(key)).mapKey("tencent",()->resumed[0]++));
  if(mode==4){in.runOnMainSync(()->dialog[0].getButton(-3).performClick());in.waitForIdleSync();return check(config.read().optString(field).isEmpty()&&resumed[0]==0,"clear never resumes");}
  in.runOnMainSync(()->{editor(dialog[0].getWindow().getDecorView()).setText("  candidate-offline-value  ");dialog[0].getButton(-1).performClick();});
  if(!started.await(5,TimeUnit.SECONDS))throw new AssertionError("verification did not start");
  if(mode==2)in.runOnMainSync(()->dialog[0].getButton(-2).performClick());
  if(mode==3)config.put("scenery","https://example.test/changed");
  release.countDown();if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("verification did not finish");
  // Completion is posted after the verifier returns. Wait for its UI state, not network time.
  boolean[] settled={false};for(int i=0;i<50&&!settled[0];i++){in.runOnMainSync(()->settled[0]=!dialog[0].isShowing()||dialog[0].getButton(-1).isEnabled());if(!settled[0])android.os.SystemClock.sleep(20);}
  in.waitForIdleSync();int checks;
  if(mode==0)checks=check(config.read().optString(field).equals("candidate-offline-value")&&resumed[0]==1,"verified save resumes exactly once");
  else checks=check(config.read().optString(field).equals("old-offline-value")&&resumed[0]==0,"failure cancellation or revision race does not save or resume");
  if(mode==1||mode==3){boolean[] kept={false};in.runOnMainSync(()->kept[0]=dialog[0].isShowing()&&editor(dialog[0].getWindow().getDecorView()).getText().toString().equals("  candidate-offline-value  "));checks+=check(kept[0],"failed candidate retains editor input");}
  in.runOnMainSync(()->dialog[0].dismiss());return checks;
 }
 private static int entries(Instrumentation in,MainActivity a,ApiConfig config)throws Exception {
  config.putAll(new JSONObject().put("aiProviders",new org.json.JSONArray()).put("selectedAiProvider",""));
  in.runOnMainSync(()->new AiExploreUi(a).plan());
  waitText(in,"AI 规划");setFirstEditor(in,"离线保留目的地");click(in,"生成建议");waitText(in,"AI 服务商");
  in.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
  waitText(in,"AI 规划");int n=check(waitText(in,"离线保留目的地")!=null,"AI plan keeps input after config cancellation");
  in.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
  in.waitForIdleSync();
  Trip previous=a.active;int previousDay=a.day;
  try {
   in.runOnMainSync(()->{a.active=Trip.demo();a.day=0;});
   for(String provider:new String[]{"tencent","baidu"}){
    config.put("mapProvider",provider);config.put("mapKey."+provider,"");
    in.runOnMainSync(()->new MapSearchUi(a).show());
    waitText(in,MapService.name(provider)+" · 搜索地点");setFirstEditor(in,"离线地点");click(in,"搜索");
    n+=check(waitText(in,MapService.name(provider)+" · Web 服务")!=null,"missing map key opens inline configuration");
    click(in,"取消");n+=check(waitText(in,"离线地点")!=null,"map query survives configuration cancellation");
    in.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);in.waitForIdleSync();
   }
  }finally{in.runOnMainSync(()->{a.active=previous;a.day=previousDay;});}
  return n;
 }
 private static android.view.accessibility.AccessibilityNodeInfo find(android.view.accessibility.AccessibilityNodeInfo node,String text,boolean edit){
  if(node==null)return null;
  if(edit?node.isEditable():text.contentEquals(node.getText()==null?"":node.getText()))return node;
  for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo hit=find(node.getChild(i),text,edit);if(hit!=null)return hit;}return null;
 }
 private static android.view.accessibility.AccessibilityNodeInfo waitText(Instrumentation in,String text)throws Exception{
  for(int i=0;i<50;i++){android.view.accessibility.AccessibilityNodeInfo hit=find(in.getUiAutomation().getRootInActiveWindow(),text,false);if(hit!=null)return hit;android.os.SystemClock.sleep(100);}throw new AssertionError("Missing UI: "+text);
 }
 private static void click(Instrumentation in,String text)throws Exception{
  if(!waitText(in,text).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot click "+text);in.waitForIdleSync();
 }
 private static void setFirstEditor(Instrumentation in,String value){
  android.view.accessibility.AccessibilityNodeInfo edit=find(in.getUiAutomation().getRootInActiveWindow(),"",true);
  android.os.Bundle args=new android.os.Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
  if(edit==null||!edit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Cannot set form input");in.waitForIdleSync();
 }
 @SuppressWarnings("unchecked") private static void restore(android.content.SharedPreferences prefs,java.util.Map<String,?> before){
  android.content.SharedPreferences.Editor edit=prefs.edit().clear();for(java.util.Map.Entry<String,?> item:before.entrySet()){
   String key=item.getKey();Object value=item.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof java.util.Set)edit.putStringSet(key,(java.util.Set<String>)value);
  }edit.commit();
 }
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static EditText editor(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText hit=editor(((ViewGroup)v).getChildAt(i));if(hit!=null)return hit;}return null;}
}
