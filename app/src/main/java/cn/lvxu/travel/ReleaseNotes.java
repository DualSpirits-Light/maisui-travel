package cn.lvxu.travel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;

/** User-facing release history. A version is marked seen only when its dialog closes. */
public final class ReleaseNotes {
    private static final String PREFS="release-notes";
    private static final Object LOCK=new Object();
    private static final Map<Context,AlertDialog> OPEN=new java.util.WeakHashMap<>();
    private ReleaseNotes(){}
    public static String history(){return "0.5.0\n• 主题、选择窗口和弹窗体验优化\n• 地点详情、标签搜索、旅行打卡与照片编辑\n• WebDAV、授权和捐赠管理体验优化\n• 更新下载进度、后台通知与双线路下载\n\n0.4.0\n• 关于页与捐赠支持\n• 两套可编辑标签组、组合搜索和那年今日\n• AI 搜索与旅行规划\n• 多地图服务与自定义 API 配置\n\n0.3.1\n• 图片旅行分享，分享口令有效 3 天\n• 离线授权与授权冻结保护\n• 更新检查与安全安装\n\n0.2.5\n• 在线激活与设备授权";}
    public static String currentChanges(String version){
        if("0.5.0".equals(version))return "主题、选择窗口和弹窗体验优化\n地点详情、标签搜索、旅行打卡与照片编辑\nWebDAV、授权和捐赠管理体验优化\n更新下载进度、后台通知与双线路下载";
        if("0.4.0".equals(version))return "关于页与捐赠支持\n两套可编辑标签组、组合搜索和那年今日\nAI 搜索与旅行规划\n多地图服务与自定义 API 配置";
        if("0.3.1".equals(version))return "图片旅行分享，分享口令有效 3 天\n离线授权与授权冻结保护\n更新检查与安全安装";
        if("0.2.5".equals(version))return "在线激活与设备授权";
        return "本版本包含稳定性改进。";
    }
    public static String markerKey(Context context,String version){
        int code=0;try{PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);code=Build.VERSION.SDK_INT>=28?(int)p.getLongVersionCode():p.versionCode;}catch(Exception ignored){}
        return "seen."+code+"."+version;
    }
    public static void showIfNeeded(MainActivity activity){
        String version="0";try{version=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception ignored){}
        showIfNeeded(activity,version);
    }
    public static void showIfNeeded(MainActivity activity,String version){
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String key=markerKey(activity,version);
        synchronized(LOCK){if(p.getBoolean(key,false)||OPEN.containsKey(activity))return;}
        AlertDialog dialog=new RoundedDialogs.Builder(activity).setTitle("更新日志").setMessage(currentChanges(version)).setPositiveButton("确定",null).create();
        dialog.setOnDismissListener(v->{p.edit().putBoolean(key,true).apply();synchronized(LOCK){OPEN.remove(activity);}});
        synchronized(LOCK){OPEN.put(activity,dialog);}dialog.show();
    }
    public static void showHistory(MainActivity activity){new RoundedDialogs.Builder(activity).setTitle("更新日志").setMessage(history()).setPositiveButton("确定",null).show();}
}
