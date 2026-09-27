package cn.lvxu.travel;

import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One cancellable page request; no persistent cache or credentials in diagnostics. */
final class AmapRouteSession {
    interface Fetcher { AmapRoadRoutes.Result fetch(AmapRoadRoutes.Segment segment)throws Exception; }
    interface Listener {void complete(AmapRoadRoutes.Segment segment,AmapRoadRoutes.Result result,String error);}
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"amap-roads");t.setDaemon(true);return t;});
    private static final OkHttpClient CLIENT=ApiHttp.CLIENT.newBuilder().readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build();
    private final Fetcher fetcher; private boolean started;
    private final String key; private volatile boolean cancelled; private volatile Call active; private Future<?> future;
    private final Map<String,String> cities=new HashMap<>();
    AmapRouteSession(String key){this.key=key;this.fetcher=this::fetch;}
    AmapRouteSession(Fetcher fetcher){this.key="";this.fetcher=fetcher;}
    synchronized void start(List<AmapRoadRoutes.Segment> segments,Listener listener) {
        if(cancelled)return;
        if(started)throw new IllegalStateException("Route session already started");
        if(segments.size()>AmapRoadRoutes.MAX_SEGMENTS)throw new IllegalArgumentException("Route batch too large");
        started=true;
        try{future=EXECUTOR.submit(()->{
            for(AmapRoadRoutes.Segment segment:segments){
                if(cancelled||Thread.currentThread().isInterrupted())return;
                if(!segment.unavailable.isEmpty()){listener.complete(segment,null,segment.unavailable);continue;}
                try{AmapRoadRoutes.Result result=fetcher.fetch(segment);if(!cancelled)listener.complete(segment,result,"");}
                catch(Exception e){if(!cancelled)listener.complete(segment,null,e instanceof SafeError?e.getMessage():"高德路线查询失败，请检查网络后重试");}
            }
        });}catch(RejectedExecutionException e){for(AmapRoadRoutes.Segment segment:segments)listener.complete(segment,null,"路线服务正忙，请稍后重试");}
    }
    synchronized void cancel(){cancelled=true;Call call=active;if(call!=null)call.cancel();if(future!=null)future.cancel(true);EXECUTOR.purge();}
    private AmapRoadRoutes.Result fetch(AmapRoadRoutes.Segment segment)throws Exception {
        String path=segment.mode==AmapRoadRoutes.Mode.CYCLE?"v4/direction/bicycling":"v3/direction/"+(segment.mode==AmapRoadRoutes.Mode.WALK?"walking":segment.mode==AmapRoadRoutes.Mode.DRIVE?"driving":"transit/integrated");
        HttpUrl.Builder url=base(path).addQueryParameter("origin",MapRoutes.lonlat(segment.from())).addQueryParameter("destination",MapRoutes.lonlat(segment.to()));
        if(segment.mode==AmapRoadRoutes.Mode.TRANSIT){url.addQueryParameter("city",city(segment.from())).addQueryParameter("cityd",city(segment.to())).addQueryParameter("strategy","公交".equals(segment.label)?"5":"0").addQueryParameter("extensions","all");}
        JSONObject response=json(url.build());
        try{return AmapRoadRoutes.parse(response,segment.mode);}catch(IOException e){throw new SafeError(e.getMessage());}
    }
    private HttpUrl.Builder base(String path){return ApiHttp.url("https://restapi.amap.com/"+path).newBuilder().addQueryParameter("key",key);}
    private String city(double[] coordinate)throws Exception {
        String location=MapRoutes.lonlat(coordinate),cached=cities.get(location);if(cached!=null)return cached;
        String city;
        JSONObject response=json(base("v3/geocode/regeo").addQueryParameter("location",location).addQueryParameter("extensions","base").build());
        try{city=AmapRoadRoutes.city(response);}
        catch(IOException e){throw new SafeError(e.getMessage());}
        cities.put(location,city);return city;
    }
    private JSONObject json(HttpUrl url)throws Exception {
        if(cancelled)throw new InterruptedIOException();
        Call call=CLIENT.newCall(new Request.Builder().url(url).build());active=call;
        if(cancelled){call.cancel();throw new InterruptedIOException();}
        try(Response response=call.execute()){
            if(!response.isSuccessful())throw new SafeError(ApiHttp.httpError(response.code()));
            if(response.body()==null)throw new SafeError("高德返回空内容，请重试");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=response.body().byteStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){if(cancelled)throw new InterruptedIOException();if(bytes.size()+n>2*1024*1024)throw new SafeError("路线数据过大，请缩短路段");bytes.write(buffer,0,n);}
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }finally{active=null;}
    }
    private static final class SafeError extends IOException {SafeError(String message){super(message);}}
}
