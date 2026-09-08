package cn.lvxu.travel;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.LocalDate;

/** A single-window local wizard: sample places are never presented as live recommendations. */
final class QuickPlanner {
 private final MainActivity a;
 private String city="杭州",start=LocalDate.now().plusDays(7).toString(),title="",mode="步行";
 private String departure="",companions="",vehicleNumber="",accommodation="",accommodationAddress="",accommodationSourceUrl="";
 private int days=3,step=0,generation=0;private long budget=180000;private boolean saved;
 private String daysDraft="3",budgetDraft=Trip.money(180000),dateDraft=start;
 private AlertDialog dialog;private LinearLayout page;private TextView previousButton,nextButton;
 private EditText cityField,dateField,departureField,companionsField,vehicleField,daysField,budgetField,accommodationField,accommodationLinkField,titleField;
 private TransportModesUi transportModes;
 QuickPlanner(MainActivity host){a=host;}
 void start(){
  if(a.trips.size()>=100){a.toast("旅行数量已达上限");return;}
  LinearLayout root=a.col();root.setMinimumHeight(a.dp(480));page=a.col();ScrollView scroll=new ScrollView(a);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout buttons=new LinearLayout(a);buttons.setOrientation(LinearLayout.HORIZONTAL);buttons.setGravity(Gravity.CENTER);previousButton=a.action("上一步",false,this::previous);TextView cancel=a.action("取消",false,()->dialog.dismiss());nextButton=a.action("下一步",true,this::next);
  LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(0,-2,1);buttonParams.setMargins(a.dp(4),a.dp(4),a.dp(4),a.dp(4));buttons.addView(previousButton,buttonParams);buttons.addView(cancel,new LinearLayout.LayoutParams(buttonParams));buttons.addView(nextButton,new LinearLayout.LayoutParams(buttonParams));root.addView(buttons);
  dialog=new AlertDialog.Builder(a).setTitle("快速规划 · 1/4 基本信息").setView(root).create();
  dialog.setOnShowListener(v->renderStep());
  dialog.setOnDismissListener(v->generation++);dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(a.shape(MainActivity.SURFACE,24));
 }
 private void renderStep(){
  if(dialog==null||!dialog.isShowing())return;page.removeAllViews();a.pad(page,22);
  dialog.setTitle(new String[]{"快速规划 · 1/4 基本信息","快速规划 · 2/4 时间与住宿","快速规划 · 3/4 出行偏好","快速规划 · 4/4 确认创建"}[step]);
  if(step==0)basic();else if(step==1)stay();else if(step==2)preferences();else review();
  previousButton.setVisibility(step==0?View.INVISIBLE:View.VISIBLE);nextButton.setText(step==3?"保存":"下一步");nextButton.setContentDescription(step==3?"保存":"下一步");
 }
 private void basic(){
  cityField=a.field(page,"目的地",city,InputType.TYPE_CLASS_TEXT);dateField=DateTimeFields.date(a,page,"出发日期",dateDraft);
  departureField=a.field(page,"出发地（选填）",departure,InputType.TYPE_CLASS_TEXT);companionsField=a.field(page,"同行人（选填）",companions,InputType.TYPE_CLASS_TEXT);
  vehicleField=a.field(page,"车牌号 / 航班号 / 车次（选填）",vehicleNumber,InputType.TYPE_CLASS_TEXT);
 }
 private void stay(){
  daysField=a.field(page,"旅行天数（1–60）",daysDraft,InputType.TYPE_CLASS_NUMBER);budgetField=a.field(page,"总预算（元）",budgetDraft,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
  accommodationField=a.field(page,"住宿处（选填）",accommodation,InputType.TYPE_CLASS_TEXT);accommodationLinkField=a.field(page,"高德住宿链接（选填）",accommodationSourceUrl,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  page.addView(a.action("识别高德住宿链接",false,this::resolveAccommodation));if(!accommodationAddress.isEmpty())page.addView(a.text("识别地址："+accommodationAddress,12,MainActivity.MUTED));
 }
 private void preferences(){titleField=a.field(page,"旅行名称",title.isEmpty()?city+" · 我的旅行":title,InputType.TYPE_CLASS_TEXT);transportModes=new TransportModesUi(a,page,mode,selected->mode=selected,()->{title=titleField.getText().toString();renderStep();});}
 private void review(){
  boolean demo=city.equals("杭州");StringBuilder s=new StringBuilder(city).append(" · ").append(start).append("\n").append(days).append(" 天 · 预算 ¥").append(Trip.money(budget)).append("\n主要交通：").append(mode);
  if(!departure.isEmpty())s.append("\n出发地：").append(departure);if(!companions.isEmpty())s.append("\n同行人：").append(companions);if(!vehicleNumber.isEmpty())s.append("\n车牌 / 班次：").append(vehicleNumber);
  if(!accommodation.isEmpty())s.append("\n住宿：").append(accommodation);if(!accommodationAddress.isEmpty())s.append("\n住宿地址：").append(accommodationAddress);
  s.append("\n\n").append(demo?"将使用杭州示例景点作为起点。开放时间、交通与价格请出发前核实；超过 3 天的日期留给你自由安排。":"将生成按天的规划框架和行前清单。创建后可添加地点，或导入高德分享链接。");page.addView(a.text(s.toString(),16,MainActivity.INK));
 }
 private void previous(){if(step==0)return;captureDraft();step--;renderStep();}
 private void next(){if(a.loadFailed){a.toast("请先通过备份恢复数据");return;}try{capture(true);if(step<3){step++;renderStep();}else create();}catch(IllegalArgumentException e){a.toast(e.getMessage());}}
 private void capture(boolean strict){
  if(step==0){String c=cityField.getText().toString().trim(),d=dateField.getText().toString().trim();if(strict&&c.isEmpty())throw new IllegalArgumentException("请输入目的地");if(c.length()>80)throw new IllegalArgumentException("目的地不能超过 80 个字");try{d=LocalDate.parse(d).toString();}catch(Exception e){throw new IllegalArgumentException("请选择有效的出发日期");}city=c;start=d;dateDraft=d;departure=clip(departureField,120);companions=clip(companionsField,500);vehicleNumber=clip(vehicleField,100);
  }else if(step==1){days=a.number(daysField,1,60);budget=Trip.cents(budgetField.getText().toString());daysDraft=String.valueOf(days);budgetDraft=Trip.money(budget);String enteredAccommodation=clip(accommodationField,120),enteredUrl=clip(accommodationLinkField,1000);if(!accommodationSourceUrl.isEmpty()&&(!enteredAccommodation.equals(accommodation)||!enteredUrl.equals(accommodationSourceUrl))){accommodationAddress="";if(!enteredAccommodation.equals(accommodation)&&enteredUrl.equals(accommodationSourceUrl))enteredUrl="";}accommodation=enteredAccommodation;accommodationSourceUrl=enteredUrl;
  }else if(step==2){String value=titleField.getText().toString().trim();if(strict&&value.isEmpty())throw new IllegalArgumentException("请输入旅行名称");if(value.length()>80)throw new IllegalArgumentException("旅行名称不能超过 80 个字");title=value;mode=transportModes.selection();}
 }
 private void captureDraft(){
  if(step==0){city=cityField.getText().toString().trim();dateDraft=dateField.getText().toString().trim();departure=departureField.getText().toString().trim();companions=companionsField.getText().toString().trim();vehicleNumber=vehicleField.getText().toString().trim();}
  else if(step==1){daysDraft=daysField.getText().toString().trim();budgetDraft=budgetField.getText().toString().trim();String enteredAccommodation=accommodationField.getText().toString().trim(),enteredUrl=accommodationLinkField.getText().toString().trim();if(!accommodationSourceUrl.isEmpty()&&(!enteredAccommodation.equals(accommodation)||!enteredUrl.equals(accommodationSourceUrl))){accommodationAddress="";if(!enteredAccommodation.equals(accommodation)&&enteredUrl.equals(accommodationSourceUrl))enteredUrl="";}accommodation=enteredAccommodation;accommodationSourceUrl=enteredUrl;}
  else if(step==2){title=titleField.getText().toString();mode=transportModes.selection();}
 }
 private String clip(EditText field,int max){String value=field.getText().toString().trim();if(value.length()>max)throw new IllegalArgumentException("输入内容过长");return value;}
 private void resolveAccommodation(){
  try{accommodation=clip(accommodationField,120);}catch(IllegalArgumentException e){accommodationField.setError(e.getMessage());return;}daysDraft=daysField.getText().toString().trim();budgetDraft=budgetField.getText().toString().trim();String shared=accommodationLinkField.getText().toString().trim();if(shared.isEmpty()){accommodationLinkField.setError("请先粘贴高德住宿链接");return;}try{PlaceImporter.extractUrl(shared);}catch(Exception e){accommodationLinkField.setError(e.getMessage());return;}
  accommodationSourceUrl=shared;final int token=++generation;final PlaceImporter.Place[] found=new PlaceImporter.Place[1];a.runJob("正在识别住宿地点…",()->{found[0]=PlaceImporter.resolve(shared);return null;},()->{if(token!=generation||dialog==null||!dialog.isShowing()||step!=1)return;PlaceImporter.Place p=found[0];accommodation=p.name;accommodationAddress=p.address;accommodationSourceUrl=p.sourceUrl;renderStep();a.toast("已识别住宿信息");});
 }
 private void create(){
  if(saved)return;if(a.trips.size()>=100)throw new IllegalArgumentException("旅行数量已达上限");saved=true;boolean demo=city.equals("杭州");Trip t=demo?Trip.demo():new Trip();t.city=city;t.title=title;t.start=start;t.days=days;t.budget=budget;t.departure=departure;t.companions=companions;t.vehicleNumber=vehicleNumber;t.accommodation=accommodation;t.accommodationAddress=accommodationAddress;t.accommodationSourceUrl=accommodationSourceUrl;t.transportMode=mode;t.stops.removeIf(s->s.day>=days);for(Trip.Stop s:t.stops)s.mode=mode;if(!demo){t.items.add(new Trip.Item("确认住宿与车票"));t.items.add(new Trip.Item("检查证件与充电设备"));}t.normalize();a.trips.add(t);a.active=t;a.page=1;a.day=0;a.mapMode=false;a.changed();dialog.dismiss();
 }
}
