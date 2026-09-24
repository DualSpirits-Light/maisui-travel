package cn.lvxu.travel;

import java.util.*;

/**
 * Selects usable device locations independently from Android's asynchronous APIs.
 * A session remains active after its first fix so a later, more accurate provider can win.
 */
final class LocationSession {
    static final long MAX_CACHED_AGE_MS=120_000L;
    static final float MAX_CACHED_ACCURACY_METERS=500f;

    enum Provider { GPS, NETWORK, PASSIVE }
    enum Started { STARTED, NO_PROVIDER }

    static final class Fix {
        final Provider provider;final double latitude,longitude;final float accuracy;final long time;
        Fix(Provider provider,double latitude,double longitude,float accuracy,long time){this.provider=provider;this.latitude=latitude;this.longitude=longitude;this.accuracy=accuracy;this.time=time;}
    }

    private final long now;
    private final ArrayList<Provider> providers=new ArrayList<>();
    private Fix best;
    private boolean active;

    LocationSession(long now){this.now=now;}

    Started start(boolean gps,boolean network,boolean passive){
        providers.clear();best=null;active=false;
        if(gps)providers.add(Provider.GPS);
        if(network)providers.add(Provider.NETWORK);
        if(passive)providers.add(Provider.PASSIVE);
        active=!providers.isEmpty();
        return active?Started.STARTED:Started.NO_PROVIDER;
    }

    List<Provider> providers(){return Collections.unmodifiableList(providers);}
    Fix best(){return best;}
    boolean active(){return active;}
    void cancel(){active=false;}
    void timeout(){active=false;}

    Fix offerCached(List<Fix> cached){
        if(!active||cached==null)return null;
        Fix selected=null;
        for(Fix fix:cached)if(providers.contains(fix==null?null:fix.provider)&&isFreshCache(fix)&&better(fix,selected))selected=fix;
        if(selected==null)return null;
        best=selected;
        return selected;
    }

    Fix offerRealtime(Fix fix){
        if(!active||!providers.contains(fix==null?null:fix.provider)||!valid(fix))return null;
        if(!better(fix,best))return null;
        best=fix;
        return fix;
    }

    static boolean weak(Fix fix){return fix!=null&&fix.accuracy>MAX_CACHED_ACCURACY_METERS;}
    private boolean isFreshCache(Fix fix){return valid(fix)&&now-fix.time>=0&&now-fix.time<=MAX_CACHED_AGE_MS&&fix.accuracy<=MAX_CACHED_ACCURACY_METERS;}
    private static boolean valid(Fix fix){return fix!=null&&fix.provider!=null&&Double.isFinite(fix.latitude)&&Double.isFinite(fix.longitude)&&fix.latitude>=-90&&fix.latitude<=90&&fix.longitude>=-180&&fix.longitude<=180&&Float.isFinite(fix.accuracy)&&fix.accuracy>=0&&fix.time>0;}
    private static boolean better(Fix candidate,Fix current){
        if(current==null)return true;
        if(candidate.accuracy+5f<current.accuracy)return true;
        return candidate.time>current.time&&candidate.accuracy<=current.accuracy*1.2f;
    }
}
