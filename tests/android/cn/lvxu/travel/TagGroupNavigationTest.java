package cn.lvxu.travel;
import android.app.Instrumentation;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
/** Inspect real tab hierarchy and preserve a selected tag while changing groups. */
final class TagGroupNavigationTest {
 static int run(Instrumentation in)throws Exception{
  Context context=in.getTargetContext();TestStartupGuard guard=new TestStartupGuard(context);MainActivity activity=null;final int[] count={0};final Throwable[] failure={null};
  try{activity=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();final MainActivity host=activity;
   in.runOnMainSync(()->{try{
    Constructor<TagChooser> constructor=TagChooser.class.getDeclaredConstructor(MainActivity.class,ArrayList.class,LinkedHashMap.class,boolean.class,TagUi.Applied.class);constructor.setAccessible(true);
    ArrayList<String> ids=new ArrayList<>();LinkedHashMap<String,String> names=new LinkedHashMap<>();TagChooser chooser=constructor.newInstance(host,ids,names,true,(TagUi.Applied)()->{});
    Method render=TagChooser.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(chooser);
    Field tabsField=TagChooser.class.getDeclaredField("tabs");tabsField.setAccessible(true);LinearLayout tabs=(LinearLayout)tabsField.get(chooser);
    View first=tabs.getChildAt(2);check(first instanceof LinearLayout&&((LinearLayout)first).getChildCount()==2,"group is a tab with label and underline");count[0]++;
    check(first.isSelected()&&((LinearLayout)first).getChildAt(0) instanceof TextView&&((LinearLayout)first).getChildAt(1).getLayoutParams().height>0,"active tab has underline");count[0]++;
    Field availableField=TagChooser.class.getDeclaredField("available");availableField.setAccessible(true);ViewGroup available=(ViewGroup)availableField.get(chooser);View chip=available.getChildAt(0);check(chip instanceof TextView&&chip.getBackground() instanceof android.graphics.drawable.GradientDrawable,"content remains pill chip");count[0]++;
    chip.performClick();Field selectedField=TagChooser.class.getDeclaredField("selected");selectedField.setAccessible(true);Map<?,?> selected=(Map<?,?>)selectedField.get(chooser);check(selected.size()==1,"chip selection stored");count[0]++;
    tabs.getChildAt(1).performClick();check(selected.size()==1&&tabs.getChildAt(1).isSelected()&&!tabs.getChildAt(2).isSelected(),"switching group retains selection and updates active tab");count[0]++;
   }catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError("group navigation",failure[0]);return count[0];
  }finally{if(activity!=null){MainActivity closing=activity;in.runOnMainSync(closing::finish);}guard.close();}
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
