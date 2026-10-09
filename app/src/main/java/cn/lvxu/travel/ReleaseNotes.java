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
    public static String history(){return "0.6.0\n• 美团旅行查询、结果预览与复制\n• 服务端凭据保护、授权校验与调用限额\n• 正式包混淆与关闭调试\n• 整合出行速览、AA 账本、地图热力与界面优化\n\n0.5.3-preview（出行体验测试版）\n• 今天出行速览、下一项安排与固定预约提示\n• 当日日程预览、可选备注及复制分享\n\n0.5.2-preview（界面体验测试版）\n• 简洁日程卡片、中文日期与地点详情入口\n• 统一导航图标、选中状态和自适应按钮\n• 账本分摊明细与大字体界面优化\n• 表单错误定位和明确填写提示\n\n0.5.1-preview（体验测试版）\n• 本地同行成员、AA 均摊与实际转账记录\n• 预约锁定、当天时间调整预览及一步撤销\n• 地图地点联动、旅行草稿恢复及账本分页\n• 删除确认与未设置预算提示修复\n\n0.5.0\n• 主题、选择窗口和弹窗体验优化\n• 地点详情、标签搜索、旅行打卡与照片编辑\n• WebDAV、授权和捐赠管理体验优化\n• 更新下载进度、后台通知与双线路下载\n\n0.4.0\n• 关于页与捐赠支持\n• 两套可编辑标签组、组合搜索和那年今日\n• AI 搜索与旅行规划\n• 多地图服务与自定义 API 配置\n\n0.3.1\n• 图片旅行分享，分享口令有效 3 天\n• 离线授权与授权冻结保护\n• 更新检查与安全安装\n\n0.2.5\n• 在线激活与设备授权";}
    public static String currentChanges(String version){
        if("0.6.0".equals(version))return "美团旅行查询、结果预览与复制\n服务端凭据保护与调用限额\n正式包安全加固\n出行速览、AA 账本、地图热力与界面体验优化";
        if("0.5.3-preview".equals(version))return "今天出行速览、下一项安排与固定预约提示\n当日日程预览、可选备注及复制分享";
        if("0.5.2-preview".equals(version))return "简洁日程卡片、中文日期与地点详情入口\n统一导航图标、选中状态和自适应按钮\n账本分摊明细与大字体界面优化\n表单错误定位和明确填写提示";
        if("0.5.1-preview".equals(version))return "本地同行成员、AA 均摊与实际转账记录\n预约锁定、当天时间调整预览及一步撤销\n地图地点联动、旅行草稿恢复及账本分页\n删除确认与未设置预算提示修复";
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
    private static String version(MainActivity activity){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "0";}}
    public static void markCurrentSeen(MainActivity activity){activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(markerKey(activity,version(activity)),true).apply();}
    public static void showIfNeeded(MainActivity activity){showIfNeeded(activity,version(activity),null);}
    public static void showIfNeeded(MainActivity activity,Runnable after){showIfNeeded(activity,version(activity),after);}
    public static void showIfNeeded(MainActivity activity,String version){showIfNeeded(activity,version,null);}
    private static void showIfNeeded(MainActivity activity,String version,Runnable after){
        SharedPreferences p=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String key=markerKey(activity,version);
        synchronized(LOCK){if(p.getBoolean(key,false)){if(after!=null)after.run();return;}if(OPEN.containsKey(activity))return;}
        AlertDialog dialog=new RoundedDialogs.Builder(activity).setTitle("更新日志").setMessage(currentChanges(version)).setPositiveButton("确定",null).create();
        dialog.setOnDismissListener(v->{p.edit().putBoolean(key,true).apply();synchronized(LOCK){OPEN.remove(activity);}if(after!=null&&!activity.isDestroyed()&&!activity.isFinishing())after.run();});
        synchronized(LOCK){OPEN.put(activity,dialog);}dialog.show();
    }
    public static void showHistory(MainActivity activity){new RoundedDialogs.Builder(activity).setTitle("更新日志").setMessage(history()).setPositiveButton("确定",null).show();}
}
