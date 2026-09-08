package cn.lvxu.travel;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Transport selector with locally persisted user-defined modes. */
final class TransportModesUi {
 interface SelectionListener{void selected(String value);}
 static final String PREFS="quick_planner_transport_modes",KEY="custom_modes";
 private static final String[] BUILT_INS={"步行","地铁","公交","自驾","出租车","火车","飞机","其他"};
 private final MainActivity a;private final Spinner spinner;private final List<String> custom;
 TransportModesUi(MainActivity host,LinearLayout form,String current,SelectionListener listener,Runnable refresh){
  a=host;custom=load();List<String> options=new ArrayList<>(Arrays.asList(options(a,current)));spinner=a.select(form,"主要出行方式",options.toArray(new String[0]),current);
  spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){listener.selected(options.get(pos));}});
  form.addView(a.action("＋ 自定义出行方式",false,()->add(listener,refresh)));
  for(String value:custom){LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.addView(a.text(value,14,MainActivity.INK),new LinearLayout.LayoutParams(0,-2,1));ImageButton remove=new ImageButton(a);remove.setImageResource(android.R.drawable.ic_menu_delete);remove.setContentDescription("删除自定义出行方式 "+value);remove.setBackgroundColor(android.graphics.Color.TRANSPARENT);remove.setOnClickListener(v->a.confirm("确定删除“"+value+"”？",()->{boolean selected=value.equals(selection());custom.remove(value);save();if(selected)listener.selected("步行");refresh.run();}));row.addView(remove,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));form.addView(row);}
 }
 String selection(){Object selected=spinner.getSelectedItem();return selected==null?"步行":selected.toString();}
 private void add(SelectionListener listener,Runnable refresh){LinearLayout form=a.col();EditText input=a.field(form,"自定义类别名称","",InputType.TYPE_CLASS_TEXT);a.pad(form,20);AlertDialog d=new AlertDialog.Builder(a).setTitle("新增出行方式").setView(form).setNegativeButton("取消",null).setPositiveButton("添加",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String value=input.getText().toString().trim();if(value.isEmpty()||value.length()>20){input.setError("请输入 1–20 个字");return;}if(custom.size()>=50){input.setError("自定义类别最多 50 个");return;}if(isKnown(value)){input.setError("这个类别已经存在");return;}custom.add(value);save();listener.selected(value);d.dismiss();refresh.run();}));d.show();}
 private boolean isKnown(String value){for(String builtIn:BUILT_INS)if(builtIn.equals(value))return true;return custom.contains(value);}
 private List<String> load(){Set<String> values=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getStringSet(KEY,Collections.emptySet());List<String> result=new ArrayList<>(values);Collections.sort(result);return result;}
 private void save(){SharedPreferences.Editor e=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit();e.putStringSet(KEY,new HashSet<>(custom)).apply();}
 static String[] options(Context context,String current){Set<String> saved=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getStringSet(KEY,Collections.emptySet());List<String> result=new ArrayList<>(Arrays.asList(BUILT_INS));List<String> sorted=new ArrayList<>(saved);Collections.sort(sorted);result.addAll(sorted);if(current!=null&&!current.trim().isEmpty()&&!result.contains(current))result.add(current);return result.toArray(new String[0]);}
}
