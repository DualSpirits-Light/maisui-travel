package cn.lvxu.travel;

import android.view.Gravity;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Three scope selectors plus an explicit confirm; location updates never invoke the callback. */
final class RegionPickerUi implements AutoCloseable {
 interface Listener {void selected(RegionSelection region);}
 final LinearLayout view;private final MainActivity a;private final Listener listener;private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final TextView provinceButton,cityButton,districtButton,confirm;private String province="",city="",district="";private boolean closed,busy,manuallySelected;private int generation;
 RegionPickerUi(MainActivity activity,Listener listener){a=activity;this.listener=listener;view=a.row();view.setGravity(Gravity.CENTER_VERTICAL);provinceButton=button("全国",0);cityButton=button("全市",1);districtButton=button("全区",2);confirm=a.text("确定",14,MainActivity.GREEN);confirm.setGravity(Gravity.CENTER);confirm.setPadding(a.dp(10),a.dp(12),a.dp(10),a.dp(12));confirm.setOnClickListener(v->confirm());view.addView(confirm,new LinearLayout.LayoutParams(-2,-2));labels();}
 private TextView button(String text,int column){TextView b=a.text(text,13,MainActivity.INK);b.setGravity(Gravity.CENTER);b.setMinHeight(a.dp(48));b.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(8));b.setBackground(a.shape(MainActivity.SURFACE,10));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.rightMargin=a.dp(4);view.addView(b,params);b.setOnClickListener(v->choose(column));return b;}
 void setLocation(RegionSelection region){if(closed||manuallySelected||region==null)return;set(region);}
 void set(RegionSelection region){generation++;busy=false;province=region.province;city=region.city;district=region.district;labels();}
 void resetToLocation(RegionSelection region){manuallySelected=false;setLocation(region);}
 private void labels(){provinceButton.setText((province.isEmpty()?"全国":province)+" ▾");cityButton.setText((city.isEmpty()?"全市":city)+" ▾");districtButton.setText((district.isEmpty()?"全区":district)+" ▾");cityButton.setContentDescription(city.isEmpty()?"全市：省内所有城市":city);districtButton.setContentDescription(district.isEmpty()?"全区：市内所有区县":district);cityButton.setEnabled(!province.isEmpty()&&!busy);districtButton.setEnabled(!city.isEmpty()&&!busy);provinceButton.setEnabled(!busy);confirm.setEnabled(!busy);}
 private void choose(int column){if(closed||busy)return;if(column==0){try{org.json.JSONArray data=new org.json.JSONArray(CityPickerUi.read(a));ArrayList<String> names=new ArrayList<>();names.add("全国");for(int i=0;i<data.length();i++)names.add(data.getJSONObject(i).getString("province"));show(names,column);}catch(Exception e){a.toast("省份列表暂不可用");}return;}
  final int request=++generation;final String parent=column==1?province:city;busy=true;labels();worker.execute(()->{try{ArrayList<RegionService.Node> nodes=RegionService.children(a,parent);ArrayList<String> names=new ArrayList<>();names.add(column==1?"全市":"全区");for(RegionService.Node n:nodes)if(column==1?"city".equals(n.level):"district".equals(n.level))names.add(n.name);
   // Municipalities skip a city level in the official administrative hierarchy.
   if(column==1&&names.size()==1)for(RegionService.Node n:nodes)if("district".equals(n.level)){names.add(parent);break;}
   a.runOnUiThread(()->{if(closed||request!=generation)return;busy=false;labels();show(names,column);});
  }catch(Exception e){a.runOnUiThread(()->{if(closed||request!=generation)return;busy=false;labels();a.toast("行政区域加载失败："+safeMessage(e));});}});
 }
 private void show(ArrayList<String> names,int column){String title=column==0?"选择省份":column==1?"选择城市（全市表示省内全部城市）":"选择区县";new RoundedDialogs.Builder(a).asSelection().setTitle(title).setItems(names.toArray(new String[0]),(dialog,index)->{manuallySelected=true;generation++;String name=names.get(index);if(column==0){province=index==0?"":name;city="";district="";}else if(column==1){city=index==0?"":name;district="";}else district=index==0?"":name;labels();}).setNegativeButton("取消",null).show();}
 private void confirm(){if(closed||busy)return;final String p=province,c=city,d=district;final int request=++generation;busy=true;labels();worker.execute(()->{try{RegionSelection result=RegionService.resolve(a,p,c,d);if(!result.hasCenter())throw new java.io.IOException("行政区域暂时没有有效中心坐标");a.runOnUiThread(()->{if(closed||request!=generation)return;busy=false;manuallySelected=true;labels();listener.selected(result);});}catch(Exception e){a.runOnUiThread(()->{if(closed||request!=generation)return;busy=false;labels();a.toast("位置选择失败："+safeMessage(e));});}});}
 private static String safeMessage(Exception e){return "请检查高德服务配置及网络后重试";}
 public void close(){closed=true;generation++;worker.shutdownNow();}
}
