package cn.lvxu.travel;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.*;
import java.util.function.Consumer;
/** Link identification returns data to the current draft without opening another stop editor. */
final class PlaceLinkPicker {
 static void show(MainActivity a,Consumer<PlaceImporter.Place> selected){
  LinearLayout form=a.col();a.pad(form,20);EditText input=a.field(form,"地图分享链接或分享文字","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);ScrollView scroll=new ScrollView(a);scroll.addView(form);
  AlertDialog dialog=new RoundedDialogs.Builder(a).setTitle("导入"+new MapService(a).name()+"链接").setView(scroll).setNegativeButton("取消",null).setPositiveButton("识别",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{String shared=input.getText().toString().trim();try{MapLinks.extract(shared);}catch(Exception e){input.setError(e.getMessage());return;}final PlaceImporter.Place[] place={null};a.runJob("正在识别地点…",()->{place[0]=MapLinks.resolve(a,shared);return null;},()->{if(!dialog.isShowing())return;dialog.dismiss();selected.accept(place[0]);});}));dialog.show();
 }
}
