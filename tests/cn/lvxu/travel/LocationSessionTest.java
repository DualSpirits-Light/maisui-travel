package cn.lvxu.travel;

import java.util.*;

/** Exercises location selection without Android services or a network. */
public final class LocationSessionTest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static LocationSession.Fix fix(LocationSession.Provider provider,long ageMs,float accuracy){
        return new LocationSession.Fix(provider,30.25,120.14,accuracy,1_000_000L-ageMs);
    }

    public static void main(String[] args){
        onlyNetworkProviderStartsAUsableSession();
        gpsAndNetworkAreRequestedTogether();
        freshAccurateCacheIsUsedImmediately();
        staleOrWeakCacheIsIgnored();
        aBetterRealtimeFixReplacesTheEarlierFix();
        timedOutOrCancelledSessionsRejectLateCallbacks();
        System.out.println("PASS: "+checks+" location session assertions");
    }

    static void onlyNetworkProviderStartsAUsableSession(){
        LocationSession session=new LocationSession(1_000_000L);
        check(session.start(false,true,false)==LocationSession.Started.STARTED,"network-only session starts");
        check(session.providers().equals(Arrays.asList(LocationSession.Provider.NETWORK)),"network-only session requests network");
    }

    static void gpsAndNetworkAreRequestedTogether(){
        LocationSession session=new LocationSession(1_000_000L);
        session.start(true,true,false);
        check(session.providers().equals(Arrays.asList(LocationSession.Provider.GPS,LocationSession.Provider.NETWORK)),"GPS does not suppress network");
    }

    static void freshAccurateCacheIsUsedImmediately(){
        LocationSession session=new LocationSession(1_000_000L);
        session.start(false,true,true);
        LocationSession.Fix cached=fix(LocationSession.Provider.PASSIVE,30_000L,35);
        check(session.offerCached(Arrays.asList(cached))==cached,"fresh accurate cache selected");
        check(session.best()==cached,"selected cache becomes current fix");
    }

    static void staleOrWeakCacheIsIgnored(){
        LocationSession session=new LocationSession(1_000_000L);
        session.start(false,true,false);
        check(session.offerCached(Arrays.asList(fix(LocationSession.Provider.NETWORK,120_001L,20)))==null,"old cache rejected");
        check(session.offerCached(Arrays.asList(fix(LocationSession.Provider.NETWORK,1_000L,501)))==null,"weak cache rejected");
        check(session.offerCached(Arrays.asList(fix(LocationSession.Provider.GPS,1_000L,20)))==null,"cache from unavailable provider rejected");
    }

    static void aBetterRealtimeFixReplacesTheEarlierFix(){
        LocationSession session=new LocationSession(1_000_000L);
        session.start(true,true,false);
        LocationSession.Fix rough=fix(LocationSession.Provider.NETWORK,0L,90);
        LocationSession.Fix precise=fix(LocationSession.Provider.GPS,0L,18);
        check(session.offerRealtime(rough)==rough,"first live fix accepted");
        check(session.offerRealtime(precise)==precise,"more accurate live fix replaces current fix");
        check(session.best()==precise,"best live fix retained");
    }

    static void timedOutOrCancelledSessionsRejectLateCallbacks(){
        LocationSession session=new LocationSession(1_000_000L);
        session.start(false,true,false);session.timeout();
        check(session.offerRealtime(fix(LocationSession.Provider.NETWORK,0L,10))==null,"late callback after timeout ignored");
        session.start(false,true,false);session.cancel();
        check(session.offerRealtime(fix(LocationSession.Provider.NETWORK,0L,10))==null,"late callback after cancel ignored");
    }
}
