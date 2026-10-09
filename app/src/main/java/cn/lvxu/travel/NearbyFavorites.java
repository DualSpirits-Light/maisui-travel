package cn.lvxu.travel;

import android.content.*;
import org.json.*;
import java.util.*;

/** Local saved POIs survive restarts and participate in normal settings backups. */
final class NearbyFavorites {
    private final SharedPreferences prefs;
    NearbyFavorites(Context c){prefs=c.getSharedPreferences("nearby-favorites-v1",Context.MODE_PRIVATE);}
    ArrayList<NearbyPlace> all(){try{return parse(new JSONArray(prefs.getString("places","[]")));}catch(Exception e){return new ArrayList<>();}}
    boolean contains(String id){for(NearbyPlace p:all())if(p.id.equals(id))return true;return false;}
    boolean toggle(NearbyPlace place)throws Exception{
        ArrayList<NearbyPlace> values=all();boolean removed=values.removeIf(p->p.id.equals(place.id));
        if(!removed){if(values.size()>=500)throw new IllegalArgumentException("收藏已满，请先移除一些地点");values.add(place);}
        if(!prefs.edit().putString("places",array(values).toString()).commit())throw new java.io.IOException("收藏保存失败，请重试");return !removed;
    }
    JSONArray exportJson()throws JSONException{return array(all());}
    void importJson(JSONArray source)throws Exception{ArrayList<NearbyPlace> values=parse(source);if(!prefs.edit().putString("places",array(values).toString()).commit())throw new java.io.IOException("收藏恢复失败");}
    static ArrayList<NearbyPlace> parse(JSONArray source)throws JSONException{
        if(source.length()>500)throw new IllegalArgumentException("收藏地点最多 500 个");
        ArrayList<NearbyPlace> values=new ArrayList<>();HashSet<String> ids=new HashSet<>();
        for(int i=0;i<source.length();i++){NearbyPlace p=NearbyPlace.from(source.getJSONObject(i));if(!ids.add(p.id))throw new IllegalArgumentException("收藏地点重复");values.add(p);}return values;
    }
    private static JSONArray array(List<NearbyPlace> values)throws JSONException{JSONArray out=new JSONArray();for(NearbyPlace p:values)out.put(p.json());return out;}
}
