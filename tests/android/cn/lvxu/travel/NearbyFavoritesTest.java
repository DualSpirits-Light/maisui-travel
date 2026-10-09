package cn.lvxu.travel;
import android.content.Context;
import org.json.*;
final class NearbyFavoritesTest {
 static int run(Context c)throws Exception{NearbyFavorites repo=new NearbyFavorites(c);JSONArray previous=repo.exportJson();int n=0;try{repo.importJson(new JSONArray());NearbyPlace p=new NearbyPlace("fixture","测试博物馆","测试地址","兰州","博物馆",36.061,103.805,4.8f);p.heat=5;p.heatAt=System.currentTimeMillis();
  n+=check(repo.toggle(p)&&new NearbyFavorites(c).contains("fixture"),"favorite persists after new repository");n+=check(repo.all().get(0).heat==0,"saved favorite heat unknown after reload");
  JSONObject backup=new AppPrefs(c).exportJson();n+=check(backup.getJSONArray("nearbyFavorites").length()==1,"ordinary backup includes nearby favorites");repo.importJson(new JSONArray());new AppPrefs(c).importJson(backup);n+=check(repo.contains("fixture"),"ordinary preference restore includes favorites");
  n+=check(!repo.toggle(p)&&!repo.contains("fixture"),"remove favorite persists");boolean rejected=false;try{repo.importJson(new JSONArray().put(p.json()).put(p.json()));}catch(IllegalArgumentException e){rejected=true;}n+=check(rejected&&repo.all().isEmpty(),"bad favorite archive leaves storage unchanged");return n;
 }finally{repo.importJson(previous);}}
 private static int check(boolean yes,String label){if(!yes)throw new AssertionError(label);return 1;}
}
