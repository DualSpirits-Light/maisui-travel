package cn.lvxu.travel;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class TripStore {
    final AtomicFile file;
    TripStore(Context c){file=new AtomicFile(new File(c.getFilesDir(),"trips-v1.json"));}
    boolean exists(){return file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();}
    static String encode(List<Trip> trips)throws JSONException {JSONArray a=new JSONArray();for(Trip t:trips)a.put(t.json());return new JSONObject().put("schema",1).put("trips",a).toString(2);}
    static ArrayList<Trip> decode(String text)throws JSONException {JSONObject o=new JSONObject(text);if(o.getInt("schema")!=1)throw new IllegalArgumentException("不支持此备份版本");JSONArray a=o.getJSONArray("trips");if(a.length()>100)throw new IllegalArgumentException("最多 100 个行程");ArrayList<Trip> ts=new ArrayList<>();HashSet<String> ids=new HashSet<>();for(int k=0;k<a.length();k++){Trip t=Trip.from(a.getJSONObject(k));if(!ids.add(t.id))throw new IllegalArgumentException("行程 ID 重复");ts.add(t);}return ts;}
    ArrayList<Trip> read()throws Exception {return decode(new String(file.readFully(),StandardCharsets.UTF_8));}
    void save(List<Trip> ts)throws Exception {byte[] data=encode(ts).getBytes(StandardCharsets.UTF_8);FileOutputStream out=null;try{out=file.startWrite();out.write(data);file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
}
