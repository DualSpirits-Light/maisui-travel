package cn.lvxu.travel;
import android.content.*;
import java.util.*;
/** Prevents unrelated startup dialogs without retaining changed test preferences. */
final class TestStartupGuard implements AutoCloseable {
 private final SharedPreferences app,notes,warmup;private final Map<String,?> oldApp,oldNotes,oldWarmup;
 TestStartupGuard(Context c)throws Exception{app=c.getSharedPreferences("app-prefs-v2",0);notes=c.getSharedPreferences("release-notes",0);warmup=c.getSharedPreferences("nearby-warmup-v1",0);oldApp=new HashMap<>(app.getAll());oldNotes=new HashMap<>(notes.getAll());oldWarmup=new HashMap<>(warmup.getAll());AppPrefs p=new AppPrefs(c);p.setTutorialDone(true);p.setLastUpdateDay(java.time.LocalDate.now().toString());String version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;notes.edit().putBoolean(ReleaseNotes.markerKey(c,version),true).commit();warmup.edit().putBoolean("startupSuppressed",true).commit();}
 public void close(){restore(app,oldApp);restore(notes,oldNotes);restore(warmup,oldWarmup);}
 private static void restore(SharedPreferences p,Map<String,?> old){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:old.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<String>((Set<String>)v));}e.commit();}
}
