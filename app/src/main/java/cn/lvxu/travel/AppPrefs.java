package cn.lvxu.travel;

import android.content.*;
import org.json.*;
import java.util.*;

/** Durable, non-secret application preferences. */
public final class AppPrefs {
    private static final String[] NAV={"home","itinerary","budget","checklist","checkin"};
    private final SharedPreferences p;
    public AppPrefs(Context c){p=c.getSharedPreferences("app-prefs-v2",Context.MODE_PRIVATE);validateActivation();}
    public String theme(){return p.getString("theme","light");}
    public void setTheme(String v){p.edit().putString("theme",("dark".equals(v)||"system".equals(v))?v:"light").apply();}
    public String background(){return p.getString("background","");}
    public void setBackground(String v){p.edit().putString("background",safe(v,500)).apply();}
    public String nickname(){return p.getString("nickname","");}
    public void setNickname(String v){p.edit().putString("nickname",safe(v,40)).apply();}
    public String avatar(){return p.getString("avatar","");}
    public void setAvatar(String v){p.edit().putString("avatar",safe(v,500)).apply();}
    /** Page shown when the app starts. MainActivity maps these stable ids to pages. */
    public String defaultHome(){return p.getString("defaultHome","home");}
    public void setDefaultHome(String v){p.edit().putString("defaultHome",validHome(v)?v:"home").apply();}
    public boolean showPlaceCoordinates(){return p.getBoolean("showPlaceCoordinates",false);}
    public void setShowPlaceCoordinates(boolean value){p.edit().putBoolean("showPlaceCoordinates",value).apply();}
    public boolean tutorialDone(){return p.getBoolean("tutorial",false);}
    public void setTutorialDone(boolean v){p.edit().putBoolean("tutorial",v).apply();}
    public String updateMirror(){return p.getString("updateMirror","https://api.github.com/repos/DualSpirits-Light/maisui-travel/releases/latest");}
    public void setUpdateMirror(String v){p.edit().putString("updateMirror",safe(v,1000)).apply();}
    public long lastUpdateCheck(){return p.getLong("lastUpdateCheck",0);}
    public void setLastUpdateCheck(long v){p.edit().putLong("lastUpdateCheck",v).apply();}
    public String lastUpdateDay(){return p.getString("lastUpdateDay","");}
    public void setLastUpdateDay(String v){p.edit().putString("lastUpdateDay",safe(v,10)).apply();}
    public boolean paid(){validateActivation();return p.getBoolean("paid",false);}
    public String activationSubject(){return p.getString("activationSubject","");}
    public String activationExpires(){return p.getString("activationExpires","");}
    void setActivation(String subject,String expires){p.edit().putString("activationSubject",safe(subject,120)).putString("activationExpires",safe(expires,10)).putBoolean("paid",true).commit();}
    private void validateActivation(){if(!p.getBoolean("paid",false))return;String expiry=p.getString("activationExpires","");if(expiry.isEmpty())return;try{if(java.time.LocalDate.parse(expiry).isBefore(java.time.LocalDate.now()))p.edit().putBoolean("paid",false).commit();}catch(Exception e){p.edit().putBoolean("paid",false).commit();}}
    public List<String> navOrder(){
        ArrayList<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString("navOrder","[]"));for(int i=0;i<a.length();i++){String x=a.getString(i);if(validNav(x)&&!out.contains(x))out.add(x);}}catch(Exception ignored){}
        for(String x:NAV)if(!out.contains(x))out.add(x);return out;
    }
    public void setNavOrder(List<String> values){JSONArray a=new JSONArray();for(String x:values)if(validNav(x)&&!contains(a,x))a.put(x);for(String x:NAV)if(!contains(a,x))a.put(x);p.edit().putString("navOrder",a.toString()).apply();}
    public boolean navVisible(String id){return validNav(id)&&p.getBoolean("navVisible."+id,true);}
    public void setNavVisible(String id,boolean value){if(validNav(id))p.edit().putBoolean("navVisible."+id,value).apply();}
    JSONObject exportJson()throws JSONException {JSONObject o=new JSONObject().put("theme",theme()).put("background",background()).put("nickname",nickname()).put("avatar",avatar()).put("showPlaceCoordinates",showPlaceCoordinates()).put("defaultHome",defaultHome()).put("tutorial",tutorialDone());JSONArray n=new JSONArray();for(String x:navOrder())n.put(new JSONObject().put("id",x).put("visible",navVisible(x)));return o.put("nav",n);}
    void importJson(JSONObject o)throws JSONException {setTheme(o.optString("theme","light"));setBackground(o.optString("background",""));setNickname(o.optString("nickname",""));setAvatar(o.optString("avatar",""));setShowPlaceCoordinates(o.optBoolean("showPlaceCoordinates",false));setDefaultHome(o.optString("defaultHome","home"));setTutorialDone(o.optBoolean("tutorial",false));JSONArray n=o.optJSONArray("nav");if(n!=null){ArrayList<String> order=new ArrayList<>();for(int i=0;i<n.length();i++){JSONObject x=n.getJSONObject(i);String id=x.getString("id");if(validNav(id)){order.add(id);setNavVisible(id,x.optBoolean("visible",true));}}setNavOrder(order);}}
    private static boolean validNav(String x){for(String n:NAV)if(n.equals(x))return true;return false;}
    private static boolean validHome(String x){return validNav(x);}
    private static boolean contains(JSONArray a,String x){for(int i=0;i<a.length();i++)if(x.equals(a.optString(i)))return true;return false;}
    private static String safe(String v,int max){v=v==null?"":v.trim();return v.length()>max?v.substring(0,max):v;}
}
