package cn.lvxu.travel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One application-owned update job; activities only observe its immutable state. */
public final class UpdateDownloads {
    public enum Phase { IDLE, DOWNLOADING, VERIFYING, READY, FAILED, CANCELLED }
    public interface Listener { void onChanged(Snapshot snapshot); }
    public static final class Snapshot {
        public final Phase phase;
        public final UpdateService.Release release;
        public final UpdateService.Route route;
        public final long downloaded,total;
        public final String error;
        public final File apk;
        public final boolean background,cancellationRequested;
        Snapshot(Phase phase,UpdateService.Release release,UpdateService.Route route,long downloaded,long total,String error,File apk,boolean background,boolean cancelling){
            this.phase=phase;this.release=release;this.route=route;this.downloaded=downloaded;this.total=total;this.error=error;this.apk=apk;this.background=background;this.cancellationRequested=cancelling;
        }
        public boolean isActive(){return phase==Phase.DOWNLOADING||phase==Phase.VERIFYING;}
    }
    interface Downloader {
        File download(UpdateService.Release release,UpdateService.Route route,UpdateService.Progress progress,UpdateService.Cancellation cancellation)throws Exception;
        void verify(File file,UpdateService.Release release)throws Exception;
    }
    private static UpdateDownloads instance;
    public static synchronized UpdateDownloads get(Context context){if(instance==null)instance=new UpdateDownloads(context,null);return instance;}
    private final Context context;
    private final SharedPreferences saved;
    private final Downloader downloader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private Snapshot state=new Snapshot(Phase.IDLE,null,UpdateService.Route.GITHUB,0,-1,"",null,false,false);
    private UpdateService.Cancellation cancellation;

    UpdateDownloads(Context context,Downloader testDownloader){
        this.context=context.getApplicationContext();saved=this.context.getSharedPreferences("update-download-state",Context.MODE_PRIVATE);
        if(testDownloader!=null)downloader=testDownloader;
        else {UpdateService service=new UpdateService(this.context,new AppPrefs(this.context));downloader=new Downloader(){
            public File download(UpdateService.Release r,UpdateService.Route route,UpdateService.Progress p,UpdateService.Cancellation c)throws Exception{return service.downloadAndVerify(r,route,p,c);}
            public void verify(File f,UpdateService.Release r)throws Exception{service.verifyDownloaded(f,r);}
        };}
        restore();
    }
    public synchronized Snapshot snapshot(){return state;}
    public void observe(Listener listener){synchronized(this){listeners.add(listener);}main.post(()->{synchronized(this){if(!listeners.contains(listener))return;}listener.onChanged(snapshot());});}
    public synchronized void remove(Listener listener){listeners.remove(listener);}
    private void emit(){main.post(()->{Set<Listener> copy;synchronized(this){copy=new HashSet<>(listeners);}for(Listener l:copy){synchronized(this){if(!listeners.contains(l))continue;}l.onChanged(snapshot());}});}
    public synchronized void start(UpdateService.Release release,UpdateService.Route route,boolean background){
        if(release==null||release.state!=UpdateService.State.AVAILABLE||!release.hasRoute(route))throw new IllegalArgumentException("这条下载线路暂不可用，请切换另一条线路");
        if(state.isActive()){
            if(state.release.versionCode==release.versionCode&&state.release.sha256.equals(release.sha256)&&state.route==route)return;
            throw new IllegalStateException("已有下载正在进行，请先取消后再切换线路");
        }
        cancellation=new UpdateService.Cancellation();final UpdateService.Cancellation token=cancellation;
        state=new Snapshot(Phase.DOWNLOADING,release,route,0,-1,"",null,background,false);persist();emit();
        worker.execute(()->{
            try {
                File file=downloader.download(release,route,new UpdateService.Progress(){
                    private long last;
                    public void onProgress(long done,long total){long now=android.os.SystemClock.elapsedRealtime();if(done!=total&&now-last<250)return;last=now;progress(Phase.DOWNLOADING,done,total);}
                    public void onVerifying(){Snapshot s=snapshot();progress(Phase.VERIFYING,s.downloaded,s.total);}
                },token);
                synchronized(UpdateDownloads.this){if(token.isCancelled()){file.delete();finish(Phase.CANCELLED,null,"");}else finish(Phase.READY,file,"");}
            }catch(Exception failure){synchronized(UpdateDownloads.this){finish(token.isCancelled()?Phase.CANCELLED:Phase.FAILED,null,token.isCancelled()?"":friendlyError(failure));}}
        });
    }
    private synchronized void progress(Phase phase,long done,long total){state=new Snapshot(phase,state.release,state.route,done,total,"",null,state.background,state.cancellationRequested);emit();}
    private void finish(Phase phase,File file,String error){state=new Snapshot(phase,state.release,state.route,state.downloaded,state.total,error,file,state.background,false);cancellation=null;persist();emit();}
    public synchronized void cancel(){if(!state.isActive()||cancellation==null)return;state=new Snapshot(state.phase,state.release,state.route,state.downloaded,state.total,"",null,state.background,true);cancellation.cancel();emit();}
    public synchronized void moveToBackground(){if(state.isActive()){state=new Snapshot(state.phase,state.release,state.route,state.downloaded,state.total,state.error,state.apk,true,state.cancellationRequested);persist();emit();}}
    private void persist(){try{saved.edit().putString("release",encode(state.release)).putString("phase",state.phase.name()).putString("route",state.route.name()).putBoolean("background",state.background).apply();}catch(Exception ignored){}}
    private void restore(){
        try{
            String encoded=saved.getString("release","");if(encoded.isEmpty())return;
            UpdateService.Release release=decode(encoded);UpdateService.Route route=UpdateService.Route.valueOf(saved.getString("route","GITHUB"));
            boolean background=saved.getBoolean("background",false);String phase=saved.getString("phase","");
            if("READY".equals(phase)){
                File file=new File(context.getCacheDir(),"updates/maisui-"+release.versionCode+".apk");
                state=new Snapshot(Phase.VERIFYING,release,route,file.length(),file.length(),"",null,background,false);
                cancellation=new UpdateService.Cancellation();final UpdateService.Cancellation token=cancellation;
                worker.execute(()->{try{downloader.verify(file,release);synchronized(this){if(token.isCancelled())file.delete();finish(token.isCancelled()?Phase.CANCELLED:Phase.READY,token.isCancelled()?null:file,"");}}catch(Exception e){file.delete();synchronized(this){finish(Phase.FAILED,null,"已下载文件不可用，请重新下载");}}});
            }else state=new Snapshot(Phase.FAILED,release,route,0,-1,"上次下载已中断，请重新下载或切换线路",null,background,false);
        }catch(Exception invalid){saved.edit().clear().apply();}
    }
    static String encode(UpdateService.Release r)throws Exception{return r==null?"":new JSONObject().put("versionCode",r.versionCode).put("versionName",r.versionName).put("notes",r.notes).put("githubApkUrl",r.githubApkUrl).put("cloudflareApkUrl",r.cloudflareApkUrl).put("sha256",r.sha256).toString();}
    static UpdateService.Release decode(String value)throws Exception{if(value==null||value.length()>1048576)throw new IllegalArgumentException("更新信息无效");JSONObject j=new JSONObject(value);return new UpdateService.Release(UpdateService.State.AVAILABLE,j.getInt("versionCode"),j.getString("versionName"),j.optString("notes"),j.optString("githubApkUrl"),j.optString("cloudflareApkUrl"),j.getString("sha256"));}
    private static String friendlyError(Exception e){if(e instanceof java.net.SocketTimeoutException)return "下载超时，请检查网络或切换线路后重试";String m=e.getMessage();if(m!=null&&m.length()<200&&m.matches("(?s).*[\\u4e00-\\u9fff].*"))return m;return "下载失败，请检查网络或切换线路后重试";}
    void closeForTest(){cancel();worker.shutdownNow();listeners.clear();}
}
