package cn.lvxu.travel;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.time.LocalDate;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
/** Travel creation and editing keep linked accommodation metadata separate from visible text. */
final class TripEditorUi {
 private static final WeakHashMap<MainActivity,WeakReference<Draft>> drafts=new WeakHashMap<>();
 private static final class Draft {
  final Trip original,tags;final LinearLayout form,optional;final TextView expand;final PlaceImporter.Place[] imported;final Runnable updateSummary;final AlertDialog dialog;
  Draft(Trip original,Trip tags,LinearLayout form,LinearLayout optional,TextView expand,PlaceImporter.Place[] imported,Runnable updateSummary,AlertDialog dialog){this.original=original;this.tags=tags;this.form=form;this.optional=optional;this.expand=expand;this.imported=imported;this.updateSummary=updateSummary;this.dialog=dialog;}
 }
 private static Draft currentDraft(MainActivity a){WeakReference<Draft> ref=drafts.get(a);return ref==null?null:ref.get();}
 static void saveState(MainActivity a,Bundle state){
  Draft draft=currentDraft(a);if(draft==null||!draft.dialog.isShowing())return;
  Bundle saved=new Bundle();saved.putString("original",draft.original==null?"":draft.original.id);saved.putBundle("form",FormDraftState.capture((View)draft.form.getParent()));saved.putBoolean("expanded",draft.optional.getVisibility()==View.VISIBLE);
  saved.putStringArrayList("tags",new java.util.ArrayList<>(draft.tags.tagIds));Bundle names=new Bundle();for(java.util.Map.Entry<String,String> entry:draft.tags.tagNames.entrySet())names.putString(entry.getKey(),entry.getValue());saved.putBundle("tagNames",names);
  if(draft.imported[0]!=null)saved.putBundle("lodging",placeState(draft.imported[0]));captureErrors(draft.form,saved,new int[]{0},false);state.putBundle("travel.draft",saved);
 }
 static void restoreState(MainActivity a,Bundle state){
  Bundle saved=state==null?null:state.getBundle("travel.draft");if(saved==null)return;
  String id=saved.getString("original","");Trip original=null;for(Trip t:a.trips)if(t.id.equals(id)){original=t;break;}if(!id.isEmpty()&&original==null)return;
  Draft existing=currentDraft(a);if(existing!=null&&existing.dialog.isShowing())return;show(a,original);Draft draft=currentDraft(a);if(draft==null)return;
  java.util.ArrayList<String> ids=saved.getStringArrayList("tags");draft.tags.tagIds.clear();if(ids!=null)draft.tags.tagIds.addAll(ids);draft.tags.tagNames.clear();Bundle names=saved.getBundle("tagNames");if(names!=null)for(String key:names.keySet())draft.tags.tagNames.put(key,names.getString(key));draft.updateSummary.run();
  Bundle lodging=saved.getBundle("lodging");if(lodging!=null)draft.imported[0]=restorePlace(lodging);
  boolean expanded=saved.getBoolean("expanded");draft.optional.setVisibility(expanded?View.VISIBLE:View.GONE);draft.expand.setText(expanded?"收起更多信息 ▴":"更多信息（选填） ▾");
  FormDraftState.restore((View)draft.form.getParent(),saved.getBundle("form"));captureErrors(draft.form,saved,new int[]{0},true);
 }
 private static Bundle placeState(PlaceImporter.Place p){Bundle b=new Bundle();b.putString("name",p.name);b.putString("address",p.address);b.putString("sourceUrl",p.sourceUrl);b.putString("openingHours",p.openingHours);b.putString("rating",p.rating);b.putString("poiId",p.poiId);b.putString("imageUrl",p.imageUrl);b.putString("coordinateSystem",p.coordinateSystem);if(p.lat!=null)b.putDouble("lat",p.lat);if(p.lon!=null)b.putDouble("lon",p.lon);return b;}
 private static PlaceImporter.Place restorePlace(Bundle b){PlaceImporter.Place p=new PlaceImporter.Place();p.name=b.getString("name","");p.address=b.getString("address","");p.sourceUrl=b.getString("sourceUrl","");p.openingHours=b.getString("openingHours","");p.rating=b.getString("rating","");p.poiId=b.getString("poiId","");p.imageUrl=b.getString("imageUrl","");p.coordinateSystem=b.getString("coordinateSystem","GCJ02");p.lat=b.containsKey("lat")?b.getDouble("lat"):null;p.lon=b.containsKey("lon")?b.getDouble("lon"):null;return p;}
 private static void captureErrors(View view,Bundle state,int[] index,boolean restore){if(view instanceof EditText){EditText field=(EditText)view;String key="error."+index[0]++;if(restore)field.setError(state.getCharSequence(key));else if(field.getError()!=null)state.putCharSequence(key,field.getError());}if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)captureErrors(group.getChildAt(i),state,index,restore);}}
 static void show(MainActivity a,Trip original){
  if(original==null&&a.trips.size()>=100){a.toast("最多支持 100 个旅行");return;}
  Draft existing=currentDraft(a);if(existing!=null&&existing.dialog.isShowing())existing.dialog.dismiss();
  Trip tags=new Trip();if(original!=null){tags.tagIds.addAll(original.tagIds);tags.tagNames.putAll(original.tagNames);}
  LinearLayout f=a.col();f.addView(a.text("先填写名称、目的地、出发日期和天数，其他信息可以稍后补充。",13,MainActivity.MUTED));a.space(f,12);
  EditText title=a.field(f,"旅行名称",original==null?"":original.title,1),city=CityPickerUi.field(a,f,original==null?"":original.city);
  TextView summary=a.text("",13,MainActivity.MUTED);Runnable updateSummary=()->{java.util.ArrayList<String> names=new java.util.ArrayList<>();TagRepository repo=new TagRepository(a);for(String id:tags.tagIds){String name=tags.tagNames.get(id);if(name==null)for(TagRepository.Tag tag:repo.all())if(id.equals(tag.id)){name=tag.name;break;}if(name!=null)names.add(name);}summary.setText(names.isEmpty()?"尚未选择标签（选填）":String.join("、",names.subList(0,Math.min(3,names.size())))+" · "+tags.tagIds.size()+" 项");};
  f.addView(a.action("旅行城市标签",false,()->TagUi.tripPicker(a,tags,true,updateSummary::run)));f.addView(summary);updateSummary.run();a.space(f,12);
  EditText date=DateTimeFields.date(a,f,"出发日期",original==null?LocalDate.now().plusDays(7).toString():original.start),days=a.field(f,"旅行天数（1–60）",original==null?"3":""+original.days,2);
  LinearLayout optional=a.col();optional.setVisibility(View.GONE);TextView expand=a.action("更多信息（选填） ▾",false,()->{});expand.setOnClickListener(v->{boolean open=optional.getVisibility()!=View.VISIBLE;optional.setVisibility(open?View.VISIBLE:View.GONE);expand.setText(open?"收起更多信息 ▴":"更多信息（选填） ▾");});f.addView(expand);f.addView(optional);
  EditText budget=a.field(optional,"总预算（人民币，选填）",original==null?"":Trip.money(original.budget),8194),departure=a.field(optional,"出发地（选填）",original==null?"":original.departure,1),companions=a.field(optional,"同行人（选填）",original==null?"":original.companions,1),vehicle=a.field(optional,"车牌号 / 车次 / 航班号（选填）",original==null?"":original.vehicleNumber,1);
  companions.setHint("多人用逗号或分号分隔，例如：小麦，小穗");String lodgingText=original==null?"":original.accommodation+(original.accommodationAddress.isEmpty()?"":" · "+original.accommodationAddress);EditText lodging=a.field(optional,"住宿名称 / 地址（选填）",lodgingText,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);lodging.setHint("可点击下方按钮自动识别");final PlaceImporter.Place[] imported={null};
  optional.addView(a.action("导入"+new MapService(a).name()+"链接",false,()->PlaceLinkPicker.show(a,p->{Draft live=currentDraft(a);if(live==null||live.form!=f||!live.dialog.isShowing())return;imported[0]=p;lodging.setText(p.name+(p.address.isEmpty()?"":" · "+p.address));})));
  if(original!=null){a.space(optional,18);optional.addView(a.action("查看已导入的高德链接",false,()->new SourceLinksUi(a).show(original)));}
  Runnable submit=()->{
   String name=a.required(title,80),dest=a.required(city,80);int n=a.number(days,1,60);long money=Trip.cents(budget.getText().toString().trim().isEmpty()?"0":budget.getText().toString());if(original!=null)for(Trip.Stop s:original.stops)if(s.day>=n)throw new IllegalArgumentException("后面天数仍有地点，请先移动或删除后再缩短旅行");String start;try{start=LocalDate.parse(date.getText().toString().trim()).toString();}catch(Exception e){throw new IllegalArgumentException("请选择有效出发日期");}
   String from=Trip.bounded(departure.getText().toString().trim(),120),people=TravelFormRules.companions(companions.getText().toString()),number=Trip.bounded(vehicle.getText().toString().trim(),100),stay=Trip.bounded(lodging.getText().toString().trim(),420);
   Trip t;try{t=original==null?new Trip():Trip.from(original.json());}catch(Exception e){throw new IllegalArgumentException("无法编辑该旅行");}t.title=name;t.city=dest;t.start=start;t.days=n;t.budget=money;t.departure=from;t.companions=people;t.vehicleNumber=number;
   if(imported[0]!=null){PlaceImporter.Place p=imported[0];String expected=p.name+(p.address.isEmpty()?"":" · "+p.address);if(stay.equals(expected)){t.accommodation=Trip.bounded(p.name,120);t.accommodationAddress=Trip.bounded(p.address,300);}else{t.accommodation=Trip.bounded(stay,120);t.accommodationAddress="";}t.accommodationSourceUrl=p.sourceUrl;try{t.accommodationSnapshot=SourceLinksUi.placeFields(p).toString();}catch(Exception ignored){}}
   else if(original==null||!stay.equals(lodgingText)){int separator=stay.indexOf(" · ");t.accommodation=Trip.bounded(separator<0?stay:stay.substring(0,separator),120);t.accommodationAddress=separator<0?"":Trip.bounded(stay.substring(separator+3),300);}
   t.tagIds.clear();t.tagIds.addAll(tags.tagIds);t.tagNames.clear();t.tagNames.putAll(tags.tagNames);
   Trip previousActive=a.active;int previousDay=a.day;int originalIndex=original==null?-1:a.trips.indexOf(original);if(original!=null&&originalIndex<0)throw new IllegalArgumentException("该旅行已删除");
   if(original==null){t.items.add(new Trip.Item("确认车票与住宿"));t.items.add(new Trip.Item("身份证 / 护照"));t.normalize();a.trips.add(t);}else a.trips.set(originalIndex,t);a.active=t;a.day=Math.min(a.day,n-1);
   if(!a.save()){if(original==null)a.trips.remove(t);else a.trips.set(originalIndex,original);a.active=previousActive;a.day=previousDay;throw new IllegalArgumentException("旅行未保存，请重试");}TripStore.sort(a.trips);a.render();if(original==null)a.root.post(()->created(a,t));
  };
  a.pad(f,22);ScrollView scroll=new ScrollView(a);scroll.addView(f);AlertDialog.Builder builder=new RoundedDialogs.Builder(a).setTitle(original==null?"新的旅行":"编辑旅行").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null);if(original!=null)builder.setNeutralButton("删除",null);
  AlertDialog dialog=builder.create();Draft draft=new Draft(original,tags,f,optional,expand,imported,updateSummary,dialog);drafts.put(a,new WeakReference<>(draft));dialog.setOnDismissListener(d->{if(currentDraft(a)==draft)drafts.remove(a);});
  dialog.setOnShowListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(a.loadFailed){a.toast("请先通过备份恢复数据");return;}try{submit.run();dialog.dismiss();}catch(IllegalArgumentException e){a.toast(e.getMessage());}});if(original!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->a.confirm("确定删除？此操作不能撤销。",()->{if(!a.trips.contains(original)){a.toast("该旅行已删除");dialog.dismiss();return;}if(a.deleteTrip(original))dialog.dismiss();}));});dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
 }
 private static void created(MainActivity a,Trip t){new RoundedDialogs.Builder(a).setTitle("旅行创建成功").setMessage("太棒了，你已经成功创建一个旅行！想继续安排行程吗？").setNegativeButton("一会再说",null).setPositiveButton("安排行程",(d,w)->{if(!a.trips.contains(t))return;a.active=t;a.day=0;a.page=1;a.mapMode=false;a.render();a.stopEditor(null);}).show();}
}
