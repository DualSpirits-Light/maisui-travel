package cn.lvxu.travel;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Explicit opt-in real SDK check. AK arrives only via an ephemeral private file. */
final class BaiduHeatmapLiveTest {
    static int run(Instrumentation in)throws Exception {
        Context c=in.getTargetContext();File keyFile=new File(c.getFilesDir(),"heatmap-live-ak.txt");File nearbyKeyFile=new File(c.getFilesDir(),"heatmap-live-amap.txt");
        if(!keyFile.isFile())throw new AssertionError("transient live AK input missing");
        ApiConfig config=new ApiConfig(c);JSONObject before=config.read();
        SharedPreferences privacy=c.getSharedPreferences("baidu-city-heatmap",0),prefs=c.getSharedPreferences("app-prefs-v2",0);
        boolean hadConsent=privacy.contains("consent-v1"),consent=privacy.getBoolean("consent-v1",false);
        boolean hadTutorial=prefs.contains("tutorial"),tutorial=prefs.getBoolean("tutorial",false);
        boolean coarse=c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean fine=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        CountDownLatch auth=new CountDownLatch(1);final boolean[] authorized={false};final int[] failureCode={-1},immediateCode={-1};
        MainActivity activity=null;int n=0;TestStartupGuard startup=new TestStartupGuard(c);
        try {
            String key=new String(Files.readAllBytes(keyFile.toPath()),StandardCharsets.UTF_8).trim();
            if(key.isEmpty()||key.length()>256||key.matches("(?s).*\\s.*"))throw new AssertionError("transient live AK input invalid");
            Files.deleteIfExists(keyFile.toPath());config.put("baiduAndroidKey",key);key=null;
            if(nearbyKeyFile.isFile()){String nearbyKey=new String(Files.readAllBytes(nearbyKeyFile.toPath()),StandardCharsets.UTF_8).trim();Files.deleteIfExists(nearbyKeyFile.toPath());config.put("mapKey.amap",nearbyKey);config.put("amapAndroidKey","");}
            privacy.edit().putBoolean("consent-v1",true).commit();prefs.edit().putBoolean("tutorial",true).commit();
            in.getUiAutomation().grantRuntimePermission(c.getPackageName(),Manifest.permission.ACCESS_COARSE_LOCATION);
            in.getUiAutomation().grantRuntimePermission(c.getPackageName(),Manifest.permission.ACCESS_FINE_LOCATION);
            activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            MainActivity host=activity;in.waitForIdleSync();in.runOnMainSync(host::showLocalHeatmap);in.waitForIdleSync();
            Object module=BaiduHeatmapUiTest.field(host,"heatmapUi");Object map=BaiduHeatmapUiTest.field(module,"map");
            n+=BaiduHeatmapUiTest.check(map!=null&&BaiduHeatmapUiTest.field(module,"view")!=null,"live SDK map constructs without crashing: "+BaiduHeatmapUiTest.field(module,"diagnosticFailure"));
            n+=BaiduHeatmapUiTest.check(Boolean.TRUE.equals(map.getClass().getMethod("isBaiduHeatMapEnabled").invoke(map)),"Baidu own city layer enabled");
            final Throwable[] authSetupFailure={null};
            in.runOnMainSync(()->{
                try {
                    ClassLoader loader=host.getClassLoader();
                    Class<?> managerClass=Class.forName("com.baidu.lbsapi.auth.LBSAuthManager",true,loader);
                    Class<?> callbackClass=Class.forName("com.baidu.lbsapi.auth.LBSAuthManagerListener",true,loader);
                    Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,host.getApplicationContext());
                    Object callback=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{callbackClass},(proxy,method,args)->{
                        if("onAuthResult".equals(method.getName())){
                            int code=((Integer)args[0]).intValue(); // Never read the JSON/string payload.
                            failureCode[0]=code;authorized[0]=code==0;auth.countDown();return null;
                        }
                        if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                        if("equals".equals(method.getName()))return proxy==args[0];
                        if("toString".equals(method.getName()))return "LiveAuthCallback";
                        return null;
                    });
                    managerClass.getMethod("setKey",String.class).invoke(manager,config.baiduAndroidKey());
                    immediateCode[0]=((Integer)managerClass.getMethod("authenticate",boolean.class,String.class,java.util.Hashtable.class,callbackClass)
                        .invoke(manager,true,"lbs_androidsdk",new java.util.Hashtable<String,String>(),callback)).intValue();
                } catch(Throwable failure){authSetupFailure[0]=failure;}
            });
            if(authSetupFailure[0]!=null)throw new AssertionError(BaiduHeatmapRuntime.diagnostic("explicit-auth",authSetupFailure[0]));
            boolean gotAuth=auth.await(45,TimeUnit.SECONDS);
            if(gotAuth&&authorized[0])SystemClock.sleep(15_000); // Allow actual remote tiles to paint.
            File evidence=new File(c.getExternalFilesDir(null),"stage16-evidence");if(!evidence.isDirectory()&&!evidence.mkdirs())throw new AssertionError("cannot create live evidence directory");
            Bitmap screenshot=in.getUiAutomation().takeScreenshot();if(screenshot==null)throw new AssertionError("live screenshot unavailable");
            try(FileOutputStream out=new FileOutputStream(new File(evidence,"baidu-city-live.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
            n+=BaiduHeatmapUiTest.check(gotAuth&&authorized[0],"live SDK authorization not confirmed (callback code "+failureCode[0]+", initial code "+immediateCode[0]+")");
            Object mapStatus=map.getClass().getMethod("getMapStatus").invoke(map);
            Object target=mapStatus.getClass().getField("target").get(mapStatus);
            double latitude=target.getClass().getField("latitude").getDouble(target),longitude=target.getClass().getField("longitude").getDouble(target);
            n+=BaiduHeatmapUiTest.check(latitude>38&&latitude<41&&longitude>115&&longitude<118,"live screenshot uses externally simulated Beijing location");
            PageUi page=(PageUi)BaiduHeatmapUiTest.field(module,"page");n+=BaiduHeatmapUiTest.check(page.alive()&&!host.isFinishing(),"live heatmap page remains responsive after loading");
            n+=NearbyHeatLiveChecks.run(in,host,(BaiduHeatmapUi)module,evidence);
            HeatRenderProbe.run(in,(BaiduHeatmapUi)module,evidence);
            in.runOnMainSync(page.dialog::dismiss);in.waitForIdleSync();
            n+=BaiduHeatmapUiTest.check(Boolean.TRUE.equals(BaiduHeatmapUiTest.field(module,"disposed"))&&!Boolean.TRUE.equals(BaiduHeatmapUiTest.field(module,"receiverRegistered"))&&!Boolean.TRUE.equals(BaiduHeatmapUiTest.field(module,"locating")),"live map dismiss releases SDK/location lifecycle");
            return n;
        } finally {
            Files.deleteIfExists(keyFile.toPath());Files.deleteIfExists(nearbyKeyFile.toPath());
            if(activity!=null){MainActivity host=activity;in.runOnMainSync(()->{try{BaiduHeatmapUi ui=(BaiduHeatmapUi)BaiduHeatmapUiTest.field(host,"heatmapUi");if(ui!=null)ui.destroy();}catch(Exception ignored){}host.finish();});}
            config.reset();config.putAll(before);
            SharedPreferences.Editor e=privacy.edit();if(hadConsent)e.putBoolean("consent-v1",consent);else e.remove("consent-v1");e.commit();
            e=prefs.edit();if(hadTutorial)e.putBoolean("tutorial",tutorial);else e.remove("tutorial");e.commit();
            startup.close();
            // Permissions are restored externally after the instrumentation exits: revoking
            // an app's permission here can kill this instrumentation before config cleanup.
            android.util.Log.i("LvxuHeatmapLive","Permission restore flags coarse="+coarse+", fine="+fine);
        }
    }
}
