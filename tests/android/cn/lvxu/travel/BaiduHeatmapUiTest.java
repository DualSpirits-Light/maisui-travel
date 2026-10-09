package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Offline entry/configuration contract: deliberately never accepts SDK consent. */
final class BaiduHeatmapUiTest {
    static int run(Instrumentation in)throws Exception {
        Context c=in.getTargetContext();ApiConfig config=new ApiConfig(c);JSONObject before=config.read();
        SharedPreferences consent=c.getSharedPreferences("baidu-city-heatmap",0),prefs=c.getSharedPreferences("app-prefs-v2",0);
        boolean hadConsent=consent.contains("consent-v1"),agreed=consent.getBoolean("consent-v1",false);
        boolean hadTutorial=prefs.contains("tutorial"),tutorial=prefs.getBoolean("tutorial",false);
        java.lang.reflect.Field initialized=BaiduHeatmapRuntime.class.getDeclaredField("initializedKey");initialized.setAccessible(true);Object old=initialized.get(null);
        MainActivity activity=null;PageUi page=null;int n=0;TestStartupGuard startup=new TestStartupGuard(c);
        try {
            initialized.set(null,null);consent.edit().putBoolean("consent-v1",false).commit();prefs.edit().putBoolean("tutorial",true).commit();
            config.put("baiduAndroidKey","");config.put("mapKey.baidu","offline-service-fixture");
            n+=check(!BaiduHeatmapRuntime.configured(c)&&!BaiduHeatmapRuntime.prepare(c),"service AK cannot initialize city heatmap");
            n+=check(Class.forName("com.baidu.lbsapi.auth.LBSAuthManager",false,c.getClassLoader())!=null,"transitive common SDK auth class packaged without initialization");
            activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            final MainActivity host=activity;in.waitForIdleSync();final int[] homeChildren={0};
            in.runOnMainSync(()->{homeChildren[0]=host.body.getChildCount();new ExploreUi(host).show();});
            click(in,"进入 当地人流热力图 →");
            Object module=field(host,"heatmapUi");page=(PageUi)field(module,"page");final PageUi live=page;
            n+=check(page.alive()&&field(module,"view")==null,"missing Android AK opens separate page without SDK view");
            n+=check(host.body.getChildCount()==homeChildren[0],"heatmap leaves home page body untouched");
            click(in,"配置百度 Android AK");waitExact(in,"百度城市热力图配置");setEditor(in,"cancelled-synthetic-ak");click(in,"取消");
            n+=check(config.baiduAndroidKey().isEmpty()&&page.alive(),"cancel preserves configuration and heatmap page");
            click(in,"配置百度 Android AK");setEditor(in,"  synthetic-android-ak  ");click(in,"保存");waitExact(in,"启用百度城市热力");
            n+=check(config.baiduAndroidKey().equals("synthetic-android-ak")&&page.alive(),"save trims AK and resumes same heatmap page");
            n+=check(field(host,"heatmapUi")==module&&host.body.getChildCount()==homeChildren[0],"saving does not replace module or return home");
            n+=check(!BaiduHeatmapRuntime.consent(c)&&initialized.get(null)==null&&field(module,"view")==null,"configuration never grants privacy consent or initializes SDK");
            n+=check(!c.getSharedPreferences("secure-vault",0).getString("api-config-v1","").contains("synthetic-android-ak"),"AK stored encrypted");
            click(in,"启用百度城市热力");click(in,"暂不启用");
            n+=check(page.alive()&&!BaiduHeatmapRuntime.consent(c)&&initialized.get(null)==null,"declined consent stays on page without SDK initialization");
            in.runOnMainSync(()->{BaiduHeatmapUi ui=(BaiduHeatmapUi)module;ui.pause();ui.resume();live.dialog.dismiss();ui.destroy();});
            n+=check(!page.alive()&&Boolean.TRUE.equals(field(module,"disposed"))&&!Boolean.TRUE.equals(field(module,"receiverRegistered"))&&!Boolean.TRUE.equals(field(module,"locating")),"dismiss and repeated cleanup leave no location or receiver work");
            in.runOnMainSync(host::showLocalHeatmap);Object reopened=field(host,"heatmapUi");page=(PageUi)field(reopened,"page");
            waitExact(in,"启用百度城市热力");n+=check(reopened!=module&&page.alive()&&field(reopened,"view")==null,"reopen preserves saved AK and still requires consent");
            n+=check(config.mapProvider().equals(before.optString("mapProvider","amap")),"city heatmap leaves preferred map provider unchanged");
            return n;
        } finally {
            if(activity!=null){MainActivity closing=activity;in.runOnMainSync(()->{try{BaiduHeatmapUi ui=(BaiduHeatmapUi)field(closing,"heatmapUi");if(ui!=null)ui.destroy();}catch(Exception ignored){}closing.finish();});}
            initialized.set(null,old);config.reset();config.putAll(before);
            SharedPreferences.Editor e=consent.edit();if(hadConsent)e.putBoolean("consent-v1",agreed);else e.remove("consent-v1");e.commit();
            e=prefs.edit();if(hadTutorial)e.putBoolean("tutorial",tutorial);else e.remove("tutorial");e.commit();
            startup.close();
        }
    }
    static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
    private static AccessibilityNodeInfo waitExact(Instrumentation in,String text)throws Exception{for(int i=0;i<30;i++){AccessibilityNodeInfo node=exact(in.getUiAutomation().getRootInActiveWindow(),text);if(node!=null)return node;SystemClock.sleep(100);}throw new AssertionError("missing heatmap UI: "+text);}
    private static void click(Instrumentation in,String text)throws Exception{AccessibilityNodeInfo node=waitExact(in,text);while(node!=null&&!node.isClickable())node=node.getParent();if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("cannot click heatmap action");in.waitForIdleSync();SystemClock.sleep(100);}
    private static void setEditor(Instrumentation in,String value)throws Exception{AccessibilityNodeInfo node=editable(in.getUiAutomation().getRootInActiveWindow());if(node==null)throw new AssertionError("AK editor missing");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("AK editor rejected draft");in.waitForIdleSync();}
    private static AccessibilityNodeInfo editable(AccessibilityNodeInfo node){if(node==null)return null;if(node.isEditable())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=editable(node.getChild(i));if(hit!=null)return hit;}return null;}
    private static AccessibilityNodeInfo exact(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.contentEquals(node.getText()==null?"":node.getText()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo hit=exact(node.getChild(i),text);if(hit!=null)return hit;}return null;}
}
