package cn.lvxu.travel;
import android.app.AlertDialog;
import java.util.*;
import java.util.function.Consumer;
/** Provider search delivers a result into an existing editor rather than creating another draft. */
final class PlaceSearchPicker {
 static void show(MainActivity a,Trip target,String query,Consumer<Trip.Stop> selected){
  String clean=query==null?"":query.trim();if(clean.isEmpty()){a.toast("请先输入地点名称");return;}
  final String city;try{city=SearchCityScope.require(target==null?null:target.city);}catch(IllegalArgumentException e){a.toast(e.getMessage());return;}
  if("amap".equals(new ApiConfig(a).mapProvider())){new AmapPlaceSearch(a).search(clean,target,selected);return;}
  MapService service=new MapService(a);Runnable search=()->{final ArrayList<PlaceImporter.Place>[] found=new ArrayList[]{null};a.runJob("正在搜索地点…",()->{found[0]=service.search(clean,city);return null;},()->{if(a.active!=target||!a.trips.contains(target))return;if(found[0].isEmpty()){a.toast("没有找到相关地点");return;}String[] labels=new String[found[0].size()];for(int i=0;i<labels.length;i++){PlaceImporter.Place p=found[0].get(i);labels[i]=p.name+"\n"+p.address;}new RoundedDialogs.Builder(a).setTitle("选择地点").setItems(labels,(d,w)->{if(a.active!=target||!a.trips.contains(target)){a.toast("已切换旅行，请在当前行程里重新搜索");return;}PlaceImporter.Place p=found[0].get(w);Trip.Stop s=new Trip.Stop();s.name=p.name;s.address=p.address;s.openingHours=p.openingHours;s.lat=p.lat;s.lon=p.lon;s.coordinateSystem=p.coordinateSystem;s.sourceUrl=p.sourceUrl;try{s.rating=Float.valueOf(p.rating);}catch(Exception ignored){}s.sourceSnapshot=TripLinkCodec.snapshot(s);selected.accept(s);}).setNegativeButton("取消",null).show();});};if(!service.configured())new AdvancedSettingsUi(a).mapKey(new ApiConfig(a).mapProvider(),search);else search.run();
 }
}
