package cn.lvxu.travel;
import android.app.Instrumentation;
import android.content.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
final class RegionPickerUiTest {
 static int run(Instrumentation in)throws Exception{
  Context c=in.getTargetContext();TestStartupGuard guard=new TestStartupGuard(c);MainActivity activity=null;final int[] count={0},callbacks={0};final Throwable[] failure={null};
  try{activity=(MainActivity)in.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();final MainActivity host=activity;
   in.runOnMainSync(()->{RegionPickerUi picker=new RegionPickerUi(host,r->callbacks[0]++);try{
    check(picker.view.getChildCount()==4,"three selectors and confirm");count[0]++;
    RegionSelection lanzhou=new RegionSelection("甘肃省","兰州市","城关区","620102","district",36,103);picker.setLocation(lanzhou);check(((TextView)picker.view.getChildAt(1)).getText().toString().contains("兰州市"),"location populates city");count[0]++;check(callbacks[0]==0,"location does not confirm");count[0]++;
    Field manual=RegionPickerUi.class.getDeclaredField("manuallySelected");manual.setAccessible(true);manual.setBoolean(picker,true);picker.setLocation(new RegionSelection("北京市","北京市","朝阳区","110105","district",39,116));check(((TextView)picker.view.getChildAt(1)).getText().toString().contains("兰州市"),"manual scope survives later location");count[0]++;
    picker.resetToLocation(lanzhou);check(!manual.getBoolean(picker)&&callbacks[0]==0,"explicit locate restores automatic selection without callback");count[0]++;
    picker.close();picker.setLocation(RegionSelection.nationwide());check(((TextView)picker.view.getChildAt(1)).getText().toString().contains("兰州市"),"closed selector ignores location");count[0]++;
   }catch(Throwable e){failure[0]=e;}finally{picker.close();}});if(failure[0]!=null)throw new AssertionError("region selector",failure[0]);return count[0];
  }finally{if(activity!=null){MainActivity close=activity;in.runOnMainSync(close::finish);}guard.close();}
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
