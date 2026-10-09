package cn.lvxu.travel;
import org.json.*;
public final class NearbyRegionTest {
 public static void main(String[] args)throws Exception{int n=0;RegionSelection all=RegionSelection.nationwide(),province=new RegionSelection("甘肃省","","","620000","province",36,103),city=new RegionSelection("甘肃省","兰州市","","620100","city",36,103),district=new RegionSelection("甘肃省","兰州市","城关区","620102","district",36,103);
  check(NearbyPlacesService.regionUrl("fixture","美食",all).queryParameter("city")==null);n++;check(NearbyPlacesService.regionUrl("fixture","美食",all).queryParameter("citylimit")==null);n++;
  check("620102".equals(NearbyPlacesService.regionUrl("fixture","博物馆",district).queryParameter("city")));n++;check("true".equals(NearbyPlacesService.regionUrl("fixture","博物馆",district).queryParameter("citylimit")));n++;
  check(NearbyPlacesService.contains(province,"620502"));n++;check(!NearbyPlacesService.contains(province,"610100"));n++;check(NearbyPlacesService.contains(city,"620104"));n++;check(!NearbyPlacesService.contains(city,"620502"));n++;check(!NearbyPlacesService.contains(district,"620104"));n++;check(!NearbyPlacesService.contains(district,""));n++;
  JSONArray pois=new JSONArray().put(poi("兰州博物馆","620102","兰州市")).put(poi("天水博物馆","620502","天水市")).put(poi("外省地点","610102","西安市"));JSONObject response=new JSONObject().put("status","1").put("pois",pois);
  NearbyPlacesService.Result p=NearbyPlacesService.parseRegion(response,"博物馆",province);check(p.places.size()==2);n++;check(p.places.get(1).city.equals("天水市"));n++;check(NearbyPlacesService.parseRegion(response,"博物馆",district).places.size()==1);n++;check(NearbyPlacesService.parseRegion(response,"博物馆",all).places.size()==3);n++;System.out.println("NearbyRegionTest: "+n+" assertions passed");
 }
 private static JSONObject poi(String name,String code,String city)throws Exception{return new JSONObject().put("id",code).put("name",name).put("adcode",code).put("cityname",city).put("pname","甘肃省").put("location","103.83,36.06");}
 private static void check(boolean value){if(!value)throw new AssertionError();}
}
