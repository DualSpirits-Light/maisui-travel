package cn.lvxu.travel;

import android.content.Context;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

/** Dedicated Baidu city layer; does not change the app's preferred map provider. */
final class BaiduHeatmapRuntime {
    private static String initializedKey;
    private static String initializationFailure="";
    private BaiduHeatmapRuntime() {}
    static String key(Context context) { return new ApiConfig(context).baiduAndroidKey().trim(); }
    static boolean configured(Context context) { return !key(context).isEmpty(); }
    static boolean consent(Context context) {
        return context.getSharedPreferences("baidu-city-heatmap",Context.MODE_PRIVATE).getBoolean("consent-v1",false);
    }
    static void agree(Context context) {
        context.getSharedPreferences("baidu-city-heatmap",Context.MODE_PRIVATE).edit().putBoolean("consent-v1",true).apply();
    }
    static synchronized boolean needsRestart(Context context) {
        return initializedKey!=null&&!initializedKey.equals(key(context));
    }
    static synchronized boolean prepare(Context context) {
        String candidate=key(context);
        if(candidate.isEmpty()||!consent(context)||needsRestart(context))return false;
        if(initializedKey!=null)return true;
        String stage="privacy";
        try {
            Context app=context.getApplicationContext();
            SDKInitializer.setAgreePrivacy(app,true);
            stage="dynamic-key";
            SDKInitializer.setApiKey(candidate);
            stage="initialize";
            SDKInitializer.initialize(app);
            stage="coordinate-type";
            SDKInitializer.setCoordType(CoordType.BD09LL);
            initializedKey=candidate;
            initializationFailure="";
            return true;
        } catch(RuntimeException|LinkageError error) { initializationFailure=diagnostic(stage,error);return false; }
    }
    static String initializationFailure(){return initializationFailure;}
    /** No messages: SDK exceptions can contain AK, URL or device data. */
    static String diagnostic(String stage,Throwable error){
        StringBuilder safe=new StringBuilder(stage);
        for(int cause=0;error!=null&&cause<3;cause++,error=error.getCause()){
            safe.append(" ").append(error.getClass().getName());
            if(error instanceof ClassNotFoundException||error instanceof NoClassDefFoundError){
                String message=error.getMessage();
                if(message!=null){
                    java.util.regex.Matcher missing=java.util.regex.Pattern.compile("(?:Failed resolution of: L([A-Za-z_$][A-Za-z0-9_$/]*);|Didn't find class \"([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+)\")").matcher(message);
                    if(missing.find()){
                        String name=missing.group(1)!=null?missing.group(1).replace('/','.'):missing.group(2);
                        if(name.length()<=160)safe.append(" missing-class=").append(name);
                    }
                }
            }
            StackTraceElement[] frames=error.getStackTrace();
            for(int i=0;i<Math.min(5,frames.length);i++)safe.append(" @").append(frames[i].getClassName()).append('.').append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber());
        }
        return safe.toString();
    }
}
