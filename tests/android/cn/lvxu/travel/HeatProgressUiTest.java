package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import java.lang.reflect.*;
import java.util.*;

/** Offline contract for incremental heat rows and refresh completion state. */
final class HeatProgressUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();TestStartupGuard guard=new TestStartupGuard(c);MainActivity activity=null;BaiduHeatmapUi module=null;NearbyDiscoveryCache cache=new NearbyDiscoveryCache(c);SharedPreferences saved=c.getSharedPreferences("nearby-discovery-cache-v1",0);Map<String,?> oldCache=new HashMap<>(saved.getAll());int n=0;
  try{
   activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();module=new BaiduHeatmapUi(activity);
   LinearLayout area=activity.col();set(module,"placesArea",area);set(module,"nearbyStatus",activity.text("ready",12,MainActivity.MUTED));set(module,"city","兰州市");set(module,"category","人文");set(module,"sort","距离");
   NearbyPlace current=new NearbyPlace("heat-current","当前地点","地址","兰州市","人文",36.061,103.834,4.5f);current.heat=3;current.heatAt=System.currentTimeMillis();
   NearbyPlace stale=new NearbyPlace("heat-stale","旧地点","地址","兰州市","人文",36.062,103.835,4.0f);stale.heat=5;stale.heatAt=System.currentTimeMillis()-31*60_000L;
   ArrayList<NearbyPlace> places=new ArrayList<>(Arrays.asList(current,stale));set(module,"places",places);
   invoke(module,"renderPlaces");Map<String,LinearLayout> rows=rows(module);Map<String,View> cards=cards(module);String oldCurrentTag=rows.get(current.id).getTag().toString(),oldStaleTag=rows.get(stale.id).getTag().toString();
   current.heat=1;current.heatAt=System.currentTimeMillis();invoke(module,"patchHeatRows");Map<String,LinearLayout> patched=rows(module);n+=check(cards.get(current.id)==cards(module).get(current.id)&&cards.get(stale.id)==cards(module).get(stale.id),"heat patch keeps card instances");n+=check(rows.get(current.id)==patched.get(current.id)&&rows.get(stale.id)==patched.get(stale.id),"heat patch keeps row instances");n+=check(!oldCurrentTag.equals(patched.get(current.id).getTag())&&oldStaleTag.equals(patched.get(stale.id).getTag()),"only changed heat row tag is rebuilt");
   long previous=System.currentTimeMillis()-10_000L;set(module,"refreshedAt",previous);set(module,"cycleActive",true);set(module,"refreshQueryFailed",true);invoke(module,"refreshCompleted",false);n+=check(((Long)field(module,"refreshedAt")).longValue()==previous,"failed refresh does not mark completion");
   long revision=new ApiConfig(c).revision();set(module,"cycleActive",true);set(module,"refreshQueryFailed",false);set(module,"heatRevision",revision);set(module,"refreshArea","兰州市");set(module,"refreshedAt",previous);invoke(module,"refreshCompleted",true);n+=check(((Long)field(module,"refreshedAt")).longValue()>previous,"complete current-scope refresh marks timestamp");
   cache.putPlaces("兰州市","人文",places);set(module,"cycleActive",true);set(module,"refreshPlaces",new ArrayList<>(places));invoke(module,"cancelCapture");n+=check(cache.getPlaces("兰州市","人文").size()==2,"cancelled refresh preserves cached places");return n;
  }finally{if(module!=null)module.destroy();if(activity!=null){MainActivity close=activity;in.runOnMainSync(close::finish);}restore(saved,oldCache);guard.close();}
 }
 private static Map<String,LinearLayout> rows(Object module)throws Exception{return (Map<String,LinearLayout>)field(module,"heatRows");}
 private static Map<String,View> cards(Object module)throws Exception{Map<String,View> result=new HashMap<>();for(Map.Entry<String,LinearLayout> e:rows(module).entrySet())result.put(e.getKey(),(View)e.getValue().getParent());return result;}
 private static void restore(SharedPreferences p,Map<String,?> old){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> x:old.entrySet()){Object v=x.getValue();if(v instanceof String)e.putString(x.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(x.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(x.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(x.getKey(),(Long)v);}e.commit();}
 private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 private static Object invoke(Object o,String n,Object... args)throws Exception{for(Method m:o.getClass().getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==args.length){m.setAccessible(true);return m.invoke(o,args);}throw new NoSuchMethodException(n);}
 private static int check(boolean value,String message){if(!value)throw new AssertionError(message);return 1;}
}
