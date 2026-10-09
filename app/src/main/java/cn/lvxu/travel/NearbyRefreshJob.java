package cn.lvxu.travel;

import android.app.job.*;

/** System-scheduled best-effort refresh of known public city POIs, without device tracking. */
public final class NearbyRefreshJob extends JobService {
    private Thread worker;
    @Override public boolean onStartJob(JobParameters params){if(NearbyWarmup.suppressed(this))return false;worker=new Thread(()->{try{NearbyDiscoveryCache cache=new NearbyDiscoveryCache(this);java.util.List<String> cities=cache.regions();cache.prune();long revision=new ApiConfig(this).revision();for(String city:cities){if(Thread.currentThread().isInterrupted())break;NearbyWarmup.preload(this,new double[]{0,0},city,revision);}}finally{jobFinished(params,false);}},"nearby-refresh");worker.start();return true;}
    @Override public boolean onStopJob(JobParameters params){if(worker!=null)worker.interrupt();return true;}
}
