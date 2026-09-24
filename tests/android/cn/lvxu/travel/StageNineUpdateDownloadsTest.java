package cn.lvxu.travel;

import android.content.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class StageNineUpdateDownloadsTest {
    static int run(Context base)throws Exception{
        Context c=new ContextWrapper(base){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("stage9-test-"+name,mode);}
            @Override public File getCacheDir(){File f=new File(super.getCacheDir(),"stage9-coordinator");f.mkdirs();return f;}
        };
        SharedPreferences p=c.getSharedPreferences("update-download-state",0);p.edit().clear().commit();
        CountDownLatch entered=new CountDownLatch(1),finish=new CountDownLatch(1);AtomicInteger count=new AtomicInteger();
        UpdateDownloads.Downloader slow=new UpdateDownloads.Downloader(){
            public File download(UpdateService.Release r,UpdateService.Route route,UpdateService.Progress progress,UpdateService.Cancellation cancel)throws Exception{
                count.incrementAndGet();progress.onProgress(1,100);entered.countDown();finish.await(3,TimeUnit.SECONDS);
                if(cancel.isCancelled())throw new InterruptedIOException("cancelled");throw new IOException("test failure");
            }
            public void verify(File f,UpdateService.Release r){}
        };
        UpdateService.Release release=new UpdateService.Release(UpdateService.State.AVAILABLE,9999,"9.9.9","notes","https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9.9.9/app.apk","https://license.zjm0929.cn/updates/apk/9999.apk",new String(new char[64]).replace('\0','a'));
        UpdateDownloads downloads=new UpdateDownloads(c,slow);int checks=0;
        try{
            downloads.start(release,UpdateService.Route.GITHUB,false);if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("worker did not start");
            downloads.start(release,UpdateService.Route.GITHUB,false);if(count.get()!=1)throw new AssertionError("duplicate start created another download");checks++;
            boolean refused=false;try{downloads.start(release,UpdateService.Route.CLOUDFLARE,false);}catch(IllegalStateException expected){refused=true;}if(!refused)throw new AssertionError("switch must wait for cancellation cleanup");checks++;
            downloads.moveToBackground();if(!downloads.snapshot().background)throw new AssertionError("background transition lost job");checks++;
            downloads.cancel();if(!downloads.snapshot().cancellationRequested||!downloads.snapshot().isActive())throw new AssertionError("cancel must retain ownership until cleanup");checks++;
            finish.countDown();await(downloads,UpdateDownloads.Phase.CANCELLED);checks++;
        }finally{finish.countDown();downloads.closeForTest();p.edit().clear().commit();}
        AtomicInteger verified=new AtomicInteger();
        File ready=new File(c.getCacheDir(),"updates/maisui-9999.apk");ready.getParentFile().mkdirs();
        UpdateDownloads.Downloader complete=new UpdateDownloads.Downloader(){
            public File download(UpdateService.Release r,UpdateService.Route route,UpdateService.Progress progress,UpdateService.Cancellation cancel)throws Exception{try(FileOutputStream out=new FileOutputStream(ready)){out.write(1);}progress.onProgress(1,1);progress.onVerifying();return ready;}
            public void verify(File f,UpdateService.Release r)throws Exception{verified.incrementAndGet();if(!f.isFile()||f.length()!=1)throw new IOException("modified file");}
        };
        UpdateDownloads first=new UpdateDownloads(c,complete),restored=null;
        try{
            first.start(release,UpdateService.Route.CLOUDFLARE,true);await(first,UpdateDownloads.Phase.READY);checks++;
            restored=new UpdateDownloads(c,complete);await(restored,UpdateDownloads.Phase.READY);if(verified.get()!=1)throw new AssertionError("persisted APK must be reverified");checks++;
            restored.closeForTest();restored=null;ready.delete();
            restored=new UpdateDownloads(c,complete);await(restored,UpdateDownloads.Phase.FAILED);checks++;
        }finally{first.closeForTest();if(restored!=null)restored.closeForTest();ready.delete();p.edit().clear().commit();}
        return checks;
    }
    private static void await(UpdateDownloads d,UpdateDownloads.Phase wanted)throws Exception{long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end){if(d.snapshot().phase==wanted)return;Thread.sleep(20);}throw new AssertionError("wanted "+wanted+" but got "+d.snapshot().phase);}
}
