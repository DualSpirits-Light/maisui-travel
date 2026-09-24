package cn.lvxu.travel;

import android.content.*;
import org.json.*;
import java.util.*;

/** Durable, non-secret application preferences. */
public final class AppPrefs {
    private static final String[] NAV={"home","itinerary","budget","checklist","checkin"};
    private final SharedPreferences p,modes;private final TagRepository tags;
    final CloudLicenseService cloud;
    public AppPrefs(Context c){p=c.getSharedPreferences("app-prefs-v2",Context.MODE_PRIVATE);modes=c.getSharedPreferences("quick_planner_transport_modes",Context.MODE_PRIVATE);tags=new TagRepository(c);cloud=new CloudLicenseService(c);validateActivation();}
    public void resetSettings(){for(String key:new String[]{"theme","colorPreset","customColors","customColorsEnabled","background","slogan","showPlaceCoordinates","defaultHome","updateMirror","navOrder"})p.edit().remove(key).commit();for(String id:NAV)p.edit().remove("navVisible."+id).commit();modes.edit().clear().commit();}
    public String theme(){return p.getString("theme","light");}
    public void setTheme(String v){p.edit().putString("theme",("dark".equals(v)||"system".equals(v))?v:"light").apply();}
    String colorPreset(){return p.getString("colorPreset","forest");}
    JSONObject customColors(){try{return new JSONObject(p.getString("customColors","{}"));}catch(JSONException e){return new JSONObject();}}
    boolean customColorsEnabled(){return p.getBoolean("customColorsEnabled",false);}
    void setColorPreset(String id){if(!Arrays.asList(ThemeColors.IDS).contains(id))throw new IllegalArgumentException("未知配色");p.edit().putString("colorPreset",id).putBoolean("customColorsEnabled",false).apply();}
    void setCustomColors(JSONObject colors){validateCustomColors(colors);p.edit().putString("customColors",colors.toString()).putBoolean("customColorsEnabled",true).apply();}
    void resetColors(){p.edit().remove("colorPreset").remove("customColors").remove("customColorsEnabled").apply();}
    static void validateCustomColors(JSONObject colors){if(colors.length()>2)throw new IllegalArgumentException("配色内容无效");Iterator<String> modes=colors.keys();while(modes.hasNext()){String mode=modes.next();if(!mode.equals("light")&&!mode.equals("dark"))throw new IllegalArgumentException("配色模式无效");JSONObject values=colors.optJSONObject(mode);if(values==null||values.length()>ThemeColors.KEYS.length)throw new IllegalArgumentException("配色内容无效");Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();if(!Arrays.asList(ThemeColors.KEYS).contains(key)||!(values.opt(key) instanceof String))throw new IllegalArgumentException("配色色位无效");ThemeColors.parseHex(values.optString(key));}}}
    static void validateColorPreferences(JSONObject o){if(o.has("colorPreset")&&!Arrays.asList(ThemeColors.IDS).contains(o.optString("colorPreset")))throw new IllegalArgumentException("配色预设无效");if(o.has("customColors")){JSONObject colors=o.optJSONObject("customColors");if(colors==null)throw new IllegalArgumentException("配色内容无效");validateCustomColors(colors);}}
    public String background(){return p.getString("background","");}
    public void setBackground(String v){p.edit().putString("background",safe(v,500)).apply();}
    public String nickname(){return p.getString("nickname","");}
    public void setNickname(String v){p.edit().putString("nickname",safe(v,40)).apply();}
    public static final String DEFAULT_SLOGAN="把期待，排进日历。";
    public String slogan(){return p.getString("slogan",DEFAULT_SLOGAN);}
    public void setSlogan(String v){String clean=safe(v,80);p.edit().putString("slogan",clean.isEmpty()?DEFAULT_SLOGAN:clean).apply();}
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
    public boolean paid(){if(cloud.hasLicense())return cloud.isValid();validateActivation();return p.getBoolean("paid",false);}
    public String activationSubject(){return p.getString("activationSubject","");}
    public String activationExpires(){return p.getString("activationExpires","");}
    private void validateActivation(){if(!p.getBoolean("paid",false))return;String expiry=p.getString("activationExpires","");if(expiry.isEmpty())return;try{if(java.time.LocalDate.parse(expiry).isBefore(java.time.LocalDate.now()))p.edit().putBoolean("paid",false).commit();}catch(Exception e){p.edit().putBoolean("paid",false).commit();}}
    public List<String> navOrder(){
        ArrayList<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString("navOrder","[]"));for(int i=0;i<a.length();i++){String x=a.getString(i);if(validNav(x)&&!out.contains(x))out.add(x);}}catch(Exception ignored){}
        for(String x:NAV)if(!out.contains(x))out.add(x);return out;
    }
    public void setNavOrder(List<String> values){JSONArray a=new JSONArray();for(String x:values)if(validNav(x)&&!contains(a,x))a.put(x);for(String x:NAV)if(!contains(a,x))a.put(x);p.edit().putString("navOrder",a.toString()).apply();}
    public boolean navVisible(String id){return validNav(id)&&p.getBoolean("navVisible."+id,true);}
    public void setNavVisible(String id,boolean value){if(validNav(id))p.edit().putBoolean("navVisible."+id,value).apply();}
    JSONObject exportJson()throws JSONException {JSONArray custom=new JSONArray();for(String value:new TreeSet<>(modes.getStringSet("custom_modes",Collections.emptySet())))custom.put(value);JSONObject o=new JSONObject().put("theme",theme()).put("colorPreset",colorPreset()).put("customColors",customColors()).put("customColorsEnabled",customColorsEnabled()).put("background",background()).put("nickname",nickname()).put("slogan",slogan()).put("customTransportModes",custom).put("avatar",avatar()).put("showPlaceCoordinates",showPlaceCoordinates()).put("defaultHome",defaultHome()).put("tutorial",tutorialDone()).put("tagRegistry",tags.exportJson());JSONArray n=new JSONArray();for(String x:navOrder())n.put(new JSONObject().put("id",x).put("visible",navVisible(x)));return o.put("nav",n);}
    Map<String,String> importJson(JSONObject o)throws JSONException {validateColorPreferences(o);JSONObject registry=o.optJSONObject("tagRegistry");if(registry!=null)TagRepository.validateSnapshot(registry);JSONArray custom=o.optJSONArray("customTransportModes");if(custom!=null){LinkedHashSet<String> values=new LinkedHashSet<>();for(int i=0;i<Math.min(custom.length(),50);i++){String value=custom.optString(i,"").trim();if(!value.isEmpty()&&value.length()<=20)values.add(value);}modes.edit().putStringSet("custom_modes",values).apply();}setTheme(o.optString("theme","light"));p.edit().putString("colorPreset",o.optString("colorPreset","forest")).putString("customColors",o.optJSONObject("customColors")==null?"{}":o.optJSONObject("customColors").toString()).putBoolean("customColorsEnabled",o.optBoolean("customColorsEnabled",false)).apply();setBackground(o.optString("background",""));setNickname(o.optString("nickname",""));setSlogan(o.optString("slogan",DEFAULT_SLOGAN));setAvatar(o.optString("avatar",""));setShowPlaceCoordinates(o.optBoolean("showPlaceCoordinates",false));setDefaultHome(o.optString("defaultHome","home"));setTutorialDone(o.optBoolean("tutorial",false));JSONArray n=o.optJSONArray("nav");if(n!=null){ArrayList<String> order=new ArrayList<>();for(int i=0;i<n.length();i++){JSONObject x=n.getJSONObject(i);String id=x.getString("id");if(validNav(id)){order.add(id);setNavVisible(id,x.optBoolean("visible",true));}}setNavOrder(order);}return registry==null?Collections.<String,String>emptyMap():tags.importJson(registry);}
    private static boolean validNav(String x){for(String n:NAV)if(n.equals(x))return true;return false;}
    private static boolean validHome(String x){return validNav(x);}
    private static boolean contains(JSONArray a,String x){for(int i=0;i<a.length();i++)if(x.equals(a.optString(i)))return true;return false;}
    private static String safe(String v,int max){v=v==null?"":v.trim();return v.length()>max?v.substring(0,max):v;}
}
