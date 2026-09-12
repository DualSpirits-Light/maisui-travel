package cn.lvxu.travel;
import com.amap.api.services.core.*;
import com.amap.api.services.poisearch.*;
/** Runs on the worker only after SDK privacy consent. No guessed match for ambiguous names. */
final class AmapDetails {
    static void enrich(android.content.Context a,PlaceImporter.Place p)throws Exception{enrich(a,p,false);}
    static boolean refresh(android.content.Context a,PlaceImporter.Place p)throws Exception{return enrich(a,p,true);}
    private static boolean enrich(android.content.Context a,PlaceImporter.Place p,boolean fresh)throws Exception{
        ServiceSettings settings=ServiceSettings.getInstance();settings.updatePrivacyShow(a,true,true);settings.updatePrivacyAgree(a,true);
        PoiSearchV2.Query query=new PoiSearchV2.Query(p.name,"","");query.setPageNum(0);query.setPageSize(20);query.setShowFields(new PoiSearchV2.ShowFields(PoiSearchV2.ShowFields.BUSINESS|PoiSearchV2.ShowFields.PHOTOS));PoiSearchV2 search=new PoiSearchV2(a,query);PoiItemV2 match=null;
        if(!p.poiId.isEmpty())match=search.searchPOIId(p.poiId);
        else if(!p.name.isEmpty()) {PoiResultV2 result=search.searchPOI();if(result!=null&&result.getPois()!=null)for(PoiItemV2 item:result.getPois()){if(normal(item.getTitle()).equals(normal(p.name))&&!p.address.isEmpty()&&normal(item.getSnippet()).contains(normal(p.address))){if(match!=null)return false;match=item;}}}
        if(match!=null){if(fresh){p.name=PlaceImporter.clip(safe(match.getTitle()),120);p.address=PlaceImporter.clip(safe(match.getSnippet()),300);if(match.getLatLonPoint()!=null){p.lat=match.getLatLonPoint().getLatitude();p.lon=match.getLatLonPoint().getLongitude();p.coordinateSystem="GCJ02";}}fill(p,match);return true;}return false;
    }
    static void fill(PlaceImporter.Place p,PoiItemV2 item){
        Business b=item.getBusiness();if(b!=null){String hours=safe(b.getOpentimeToday());if(hours.isEmpty())hours=safe(b.getOpentimeWeek());if(!hours.isEmpty())p.openingHours=PlaceImporter.clip(hours,300);try{float rating=Float.parseFloat(safe(b.getmRating()));if(Float.isFinite(rating)&&rating>=0&&rating<=5)p.rating=String.valueOf(rating);}catch(Exception ignored){}}
        if(p.imageUrl.isEmpty()&&item.getPhotos()!=null&&!item.getPhotos().isEmpty())p.imageUrl=safe(item.getPhotos().get(0).getUrl()).replaceFirst("^http://","https://");
        if(p.lat==null&&item.getLatLonPoint()!=null){p.lat=item.getLatLonPoint().getLatitude();p.lon=item.getLatLonPoint().getLongitude();p.coordinateSystem="GCJ02";}
    }
    private static String safe(String s){return s==null?"":s.trim();}
    private static String normal(String s){return safe(s).replace("（","(").replace("）",")").replaceAll("\\s+","");}
}
