package cn.lvxu.travel;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.Locale;

/** User-started data-sync service. Downloading never silently installs an APK. */
public final class UpdateDownloadService extends Service implements UpdateDownloads.Listener {
    private static final String CHANNEL="app-updates",CANCEL="cn.lvxu.travel.CANCEL_UPDATE";
    private static final int NOTIFICATION=9031;
    public static final String OPEN_UPDATE="open-update-download";
    private UpdateDownloads downloads;
    private boolean observing;
    public static boolean notificationsEnabled(Context context){
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null||!manager.areNotificationsEnabled())return false;
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        return channel==null||channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public static void start(Context context,UpdateService.Release release,UpdateService.Route route,boolean background){
        if(background&&!notificationsEnabled(context))throw new IllegalStateException("请先允许通知，或选择立即更新在窗口中查看进度");
        UpdateDownloads.Snapshot active=UpdateDownloads.get(context).snapshot();
        if(active.isActive()&&(active.release.versionCode!=release.versionCode||active.route!=route))throw new IllegalStateException("请先取消当前下载，再切换线路");
        try{
            Intent intent=new Intent(context,UpdateDownloadService.class).putExtra("release",UpdateDownloads.encode(release)).putExtra("route",route.name()).putExtra("background",background);
            context.startForegroundService(intent);
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("更新信息无效，请重新检查更新");}
    }
    @Override public void onCreate(){super.onCreate();downloads=UpdateDownloads.get(this);NotificationManager manager=getSystemService(NotificationManager.class);NotificationChannel channel=new NotificationChannel(CHANNEL,"应用更新",NotificationManager.IMPORTANCE_LOW);channel.setDescription("更新下载进度、完成及重试提示");manager.createNotificationChannel(channel);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&CANCEL.equals(intent.getAction())){downloads.cancel();if(!downloads.snapshot().isActive())stopSelf();return START_NOT_STICKY;}
        Notification initial=notification(downloads.snapshot(),true);
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,initial);
        try{
            if(intent==null)throw new IllegalArgumentException("下载已中断，请重新开始");
            UpdateService.Release release=UpdateDownloads.decode(intent.getStringExtra("release"));
            boolean background=intent.getBooleanExtra("background",false);
            downloads.start(release,UpdateService.Route.valueOf(intent.getStringExtra("route")),background);
            if(background)downloads.moveToBackground();
            if(!observing){observing=true;downloads.observe(this);}
        }catch(Exception e){if(!downloads.snapshot().isActive()){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}
        return START_NOT_STICKY;
    }
    @Override public void onChanged(UpdateDownloads.Snapshot state){
        if(state.isActive()){getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(state,false));return;}
        stopForeground(STOP_FOREGROUND_REMOVE);
        if(state.phase!=UpdateDownloads.Phase.CANCELLED&&state.phase!=UpdateDownloads.Phase.IDLE&&notificationsEnabled(this))getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(state,false));
        stopSelf();
    }
    private Notification notification(UpdateDownloads.Snapshot state,boolean initial){
        boolean active=initial||state.isActive();
        Intent open=new Intent(this,MainActivity.class).putExtra(OPEN_UPDATE,true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,NOTIFICATION,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(active?android.R.drawable.stat_sys_download:android.R.drawable.stat_sys_download_done)
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active).setCategory(Notification.CATEGORY_PROGRESS);
        String version=state.release==null?"":state.release.versionName;
        String route=state.route==UpdateService.Route.GITHUB?"GitHub（加速）":"Cloudflare";
        if(initial)builder.setContentTitle("正在准备更新").setContentText("下载完成后需确认安装").setProgress(0,0,true);
        else if(state.phase==UpdateDownloads.Phase.READY)builder.setContentTitle("更新已下载 · "+version).setContentText("点击返回应用，确认安装");
        else if(state.phase==UpdateDownloads.Phase.FAILED)builder.setContentTitle("更新下载未完成").setContentText(state.error+" · 点击重试");
        else{
            String title=state.cancellationRequested?"正在取消下载":state.phase==UpdateDownloads.Phase.VERIFYING?"正在验证更新包":"正在下载更新 · "+version;
            int percent=state.total>0?(int)Math.min(100,100*state.downloaded/state.total):0;
            builder.setContentTitle(title).setContentText(route+" · "+(state.total>0?percent+"% · ":"")+String.format(Locale.ROOT,"%.1f MB",state.downloaded/1048576.0))
                .setProgress(100,percent,state.total<=0||state.phase==UpdateDownloads.Phase.VERIFYING);
        }
        if(active){Intent cancel=new Intent(this,UpdateDownloadService.class).setAction(CANCEL);builder.addAction(new Notification.Action.Builder(null,"取消下载",PendingIntent.getService(this,NOTIFICATION+1,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());}
        return builder.build();
    }
    @Override public void onTimeout(int startId,int fgsType){downloads.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){if(observing)downloads.remove(this);if(downloads.snapshot().isActive())downloads.cancel();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
