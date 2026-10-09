package cn.lvxu.travel;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.concurrent.*;

/** Short foreground location acquisition; no background permission and no persisted device coordinates. */
final class NearbyWarmup implements LocationListener {
    static final int PERMISSION_CODE=7419,JOB_ID=7420;
    private static NearbyWarmup active;
    private static volatile double[] lastFix;private static volatile long lastFixAt;
    static double[] lastFix(){double[] value=lastFix;return value==null||System.currentTimeMillis()-lastFixAt>120_000?null:value.clone();}
    private final Context context;private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager manager;private boolean stopped,accepted;
    private NearbyWarmup(Context c){context=c.getApplicationContext();}
    static boolean suppressed(Context c){return c.getSharedPreferences("nearby-warmup-v1",0).getBoolean("startupSuppressed",false);}
    static void start(Activity a){if(suppressed(a))return;schedule(a);if(active!=null)return;if(!hasLocation(a))return;active=new NearbyWarmup(a);active.acquire();}
    static void onPermissionResult(Activity a,int code){if(code==PERMISSION_CODE&&hasLocation(a))start(a);}
    static void stop(Activity a){if(active!=null){active.close();active=null;}}
    private static boolean hasLocation(Context c){return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    static void schedule(Context c){if(suppressed(c))return;try{JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler!=null&&scheduler.getPendingJob(JOB_ID)==null)scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(c,NearbyRefreshJob.class)).setPeriodic(NearbyDiscoveryCache.TTL_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());}catch(Exception ignored){}}
    private void acquire(){new NearbyDiscoveryCache(context).prune();ApiConfig cfg=new ApiConfig(context);if(cfg.mapKey("amap").isEmpty()&&(cfg.amapAndroidKey().isEmpty()||!AmapConsent.granted(context))){close();return;}manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);if(manager==null){close();return;}try{Location best=null;for(String provider:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER})if((!LocationManager.GPS_PROVIDER.equals(provider)||context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)&&manager.isProviderEnabled(provider)){Location cached=manager.getLastKnownLocation(provider);if(cached!=null&&System.currentTimeMillis()-cached.getTime()>=0&&System.currentTimeMillis()-cached.getTime()<120_000&&cached.hasAccuracy()&&cached.getAccuracy()<1000&&(best==null||cached.getAccuracy()<best.getAccuracy()))best=cached;manager.requestLocationUpdates(provider,1000,20,this,Looper.getMainLooper());}if(best!=null)onLocationChanged(best);handler.postDelayed(this::close,15_000);}catch(SecurityException ignored){close();}}
    @Override public void onLocationChanged(Location l){if(stopped||accepted||l==null||!l.hasAccuracy()||l.getAccuracy()<=0||l.getAccuracy()>2000||!Double.isFinite(l.getLatitude())||!Double.isFinite(l.getLongitude())||Math.abs(l.getLatitude())>90||Math.abs(l.getLongitude())>180||System.currentTimeMillis()-l.getTime()<0||System.currentTimeMillis()-l.getTime()>120_000)return;accepted=true;double[] point={l.getLatitude(),l.getLongitude()};lastFixAt=l.getTime();lastFix=point.clone();close();long revision=new ApiConfig(context).revision();Thread t=new Thread(()->preload(context,point,"",revision),"nearby-preload");t.setDaemon(true);t.start();}
    static void preload(Context c,double[] origin,String knownCity,long revision){ApiConfig cfg=new ApiConfig(c);boolean sdk=!cfg.amapAndroidKey().isEmpty()&&AmapConsent.granted(c);if(!sdk&&cfg.mapKey("amap").isEmpty())return;NearbyDiscoveryCache cache=new NearbyDiscoveryCache(c);String city=knownCity;for(String category:NearbyPlacesService.CATEGORIES){if("收藏".equals(category))continue;if(cfg.revision()!=revision||Thread.currentThread().isInterrupted())return;if(!city.isEmpty()&&cache.placesFresh(city,category))continue;try{NearbyPlacesService.Result result=NearbyPlacesService.search(c,category,origin,city,sdk);if(cfg.revision()!=revision)return;city=result.city;cache.putPlaces(city,category,result.places);}catch(Exception|LinkageError ignored){return;}}}
    private void close(){stopped=true;handler.removeCallbacksAndMessages(null);if(manager!=null)try{manager.removeUpdates(this);}catch(Exception ignored){}}
    @Override public void onProviderEnabled(String p){}@Override public void onProviderDisabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle b){}
}
