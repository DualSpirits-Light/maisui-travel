package cn.lvxu.travel;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/** Native screen host with explicit data, media, finance, map and settings controllers. */
public class MainActivity extends Activity {
    static int BG=0xffF6F5F0,INK=0xff233C34,GREEN=0xff23644F,MUTED=0xff717B73,
        LINE=0xffE2E6DC,PALE=0xffE7EDDF,ORANGE=0xffAC592F,SURFACE=0xffFFFFFF;
    ArrayList<Trip> trips=new ArrayList<>();
    TripStore store;Trip active;AppPrefs prefs;int page=0,day=0;
    LinearLayout root,body;boolean loadFailed=false,mapMode=false;
    MediaController media;SettingsUi settings;
    private FinanceUi finance;private ChecklistUi lists;private CheckinUi checkins;
    private AmapUi mapUi;private TutorialUi tutorial;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final ArrayList<Dialog> progressDialogs=new ArrayList<>();
    private boolean destroyed;
    interface ImageCallback{void selected(String relativeMediaPath);}

    @Override public void onCreate(Bundle state){
        prefs=new AppPrefs(this);applyPalette();super.onCreate(state);store=new TripStore(this);
        try{if(store.exists())trips=store.read();else{trips.add(Trip.demo());store.save(trips);}}
        catch(Exception e){loadFailed=true;new AlertDialog.Builder(this).setTitle("无法读取本地行程").setMessage("原文件已保留。请重启重试，或通过设置中的备份功能恢复。").setPositiveButton("知道了",null).show();}
        if(!trips.isEmpty())active=trips.get(0);
        if(state!=null){page=state.getInt("page");day=state.getInt("day");mapMode=state.getBoolean("mapMode");for(Trip t:trips)if(t.id.equals(state.getString("trip")))active=t;}
        media=new MediaController(this);if(state!=null)media.restoreState(state);
        settings=new SettingsUi(this);finance=new FinanceUi(this);lists=new ChecklistUi(this);checkins=new CheckinUi(this,media);tutorial=new TutorialUi(this);
        render();root.post(()->{if(!prefs.tutorialDone()&&!loadFailed)showTutorial();else settings.checkDailyUpdate();});handleSharedPlace(getIntent());
    }
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleSharedPlace(intent);}
    private void handleSharedPlace(Intent intent){if(intent!=null&&Intent.ACTION_SEND.equals(intent.getAction())){String value=intent.getStringExtra(Intent.EXTRA_TEXT);if(value!=null&&(value.contains("amap.com")||value.contains("gaode.com")))root.post(()->importPlace(value));}}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("page",page);state.putInt("day",day);state.putBoolean("mapMode",mapMode);if(active!=null)state.putString("trip",active.id);if(media!=null)media.saveState(state);}
    @Override public void onDestroy(){destroyed=true;if(mapUi!=null)mapUi.destroy();for(Dialog d:progressDialogs)if(d.isShowing())d.dismiss();jobs.shutdown();super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(mapUi!=null)mapUi.resume();}
    @Override protected void onPause(){if(mapUi!=null)mapUi.pause();super.onPause();}
    @Override public void onBackPressed(){if(page!=0){page=0;render();}else super.onBackPressed();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);media.onPermissions(request,permissions,results);}
    void applyPalette(){boolean dark="dark".equals(prefs.theme())||("system".equals(prefs.theme())&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);BG=dark?0xff14231E:0xffF6F5F0;SURFACE=dark?0xff20372C:0xffFFFFFF;INK=dark?0xffEBF1E9:0xff233C34;MUTED=dark?0xffBDCCC0:0xff717B73;GREEN=dark?0xff459474:0xff23644F;PALE=dark?0xff304B3C:0xffE7EDDF;LINE=dark?0xff3D5848:0xffE2E6DC;ORANGE=dark?0xffE5AE81:0xffAC592F;setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);}
    int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;}
    TextView bold(String s,int size,int color){TextView t=text(s,size,color);t.setTypeface(null,Typeface.BOLD);return t;}
    void space(LinearLayout l,int h){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    void pad(View v,int p){v.setPadding(dp(p),dp(p),dp(p),dp(p));}
    TextView action(String label,boolean primary,Runnable fn){TextView v=bold(label,14,primary?Color.WHITE:INK);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(shape(primary?GREEN:PALE,14));v.setContentDescription(label);v.setFocusable(true);v.setOnClickListener(w->fn.run());return v;}
    void pair(LinearLayout l,View left,View right){LinearLayout r=row();r.addView(left,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(10);r.addView(right,p);l.addView(r);}
    LinearLayout card(LinearLayout parent){LinearLayout c=col();pad(c,18);c.setBackground(shape(SURFACE,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;}
    void heading(String title,String subtitle){body.addView(bold(title,27,INK));space(body,8);body.addView(text(subtitle,13,MUTED));space(body,20);}
    void section(String title){space(body,12);body.addView(bold(title,18,INK));space(body,12);}
    void toast(String s){if(!destroyed)Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void runJob(String label,Callable<String> work,Runnable success){
        if(destroyed)return;ProgressDialog dialog=new ProgressDialog(this);dialog.setMessage(label);dialog.setCancelable(false);dialog.show();progressDialogs.add(dialog);
        jobs.execute(()->{String message=null;Exception error=null;try{message=work.call();}catch(Exception e){error=e;}final String result=message;final Exception failure=error;runOnUiThread(()->{progressDialogs.remove(dialog);if(dialog.isShowing())dialog.dismiss();if(destroyed)return;if(failure!=null){String m=failure.getMessage();toast(m==null?"操作失败，请稍后重试":m);}else{if(success!=null)success.run();if(result!=null&&!result.isEmpty())toast(result);}});});
    }
    void pickImage(ImageCallback callback){media.pick(callback);}
    File mediaFile(String relative){return MediaFiles.file(this,relative);}
    boolean save(){if(loadFailed){toast("请先恢复本地数据，再进行修改");return false;}try{store.save(trips);return true;}catch(Exception e){toast("保存失败，此次修改未保存，请检查存储空间");return false;}}
    void changed(){if(!save()&&!loadFailed){try{String id=active==null?"":active.id;trips=store.read();active=trips.isEmpty()?null:trips.get(0);for(Trip t:trips)if(t.id.equals(id))active=t;}catch(Exception e){loadFailed=true;}}render();}

    void render(){
        if(destroyed)return;applyPalette();if(mapUi!=null){mapUi.destroy();mapUi=null;}
        root=col();root.setBackgroundColor(BG);setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,in)->{Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return in;});
        boolean dark=BG==0xff14231E;getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout top=row();top.setPadding(dp(22),dp(10),dp(22),dp(10));top.addView(bold(getString(R.string.app_name),22,INK),new LinearLayout.LayoutParams(0,-2,1));View avatar;
        File avatarFile=null;try{avatarFile=mediaFile(prefs.avatar());}catch(Exception ignored){}
        if(avatarFile!=null&&avatarFile.isFile()){ImageView image=new ImageView(this);image.setImageURI(Uri.fromFile(avatarFile));image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(shape(PALE,24));image.setClipToOutline(true);avatar=image;}
        else{String nick=prefs.nickname();TextView v=bold(nick.isEmpty()?"我":nick.substring(0,1),16,INK);v.setGravity(Gravity.CENTER);v.setBackground(shape(PALE,24));avatar=v;}
        avatar.setContentDescription("设置");avatar.setFocusable(true);avatar.setOnClickListener(v->settings.open());top.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=col();body.setPadding(dp(22),dp(12),dp(22),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(page==0)home();else if(active==null){heading("下一程，从这里开始","新建一个旅行，慢慢填满期待。");body.addView(action("＋ 创建旅行",true,()->tripEditor(null)));}
        else{day=Math.max(0,Math.min(day,active.days-1));if(page==1)itinerary();else if(page==2)finance.show();else if(page==3)lists.show();else if(page==4)checkins.show();}
        LinearLayout nav=row();nav.setPadding(dp(10),dp(8),dp(10),dp(8));nav.setBackgroundColor(SURFACE);ArrayList<String> visible=new ArrayList<>();for(String key:prefs.navOrder())if(prefs.navVisible(key))visible.add(key);if(visible.isEmpty())visible.add("home");
        for(String key:visible){int index=pageIndex(key);LinearLayout item=col();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(58));item.setBackground(shape(page==index?PALE:SURFACE,14));item.addView(bold(new String[]{"◈","☷","¥","✓","◎"}[index],22,page==index?GREEN:MUTED),new LinearLayout.LayoutParams(-2,-2));space(item,3);item.addView(text(new String[]{"旅行","行程","预算","清单","打卡"}[index],11,INK),new LinearLayout.LayoutParams(-2,-2));item.setContentDescription(new String[]{"旅行","行程","预算","清单","打卡"}[index]);item.setFocusable(true);item.setOnClickListener(v->{page=index;render();});nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));}root.addView(nav);
    }
    static int pageIndex(String key){if("itinerary".equals(key))return 1;if("budget".equals(key))return 2;if("checklist".equals(key))return 3;if("checkin".equals(key))return 4;return 0;}
    void openTrip(Trip trip){active=trip;day=0;page=1;render();}
    void home(){
        heading("把期待，排进日历。",prefs.nickname().isEmpty()?"每一程，都从容。":prefs.nickname()+"，准备好出发了吗？");LinearLayout hero=col();hero.setBackground(shape(PALE,24));hero.setClipToOutline(true);View cover;File coverFile=null;try{coverFile=mediaFile(prefs.background());}catch(Exception ignored){}
        if(coverFile!=null&&coverFile.isFile()){ImageView image=new ImageView(this);image.setImageURI(Uri.fromFile(coverFile));image.setScaleType(ImageView.ScaleType.CENTER_CROP);cover=image;}else cover=new Landscape(this);
        cover.setContentDescription("设置首页背景");cover.setOnClickListener(v->media.showBackgroundPicker());hero.addView(cover,new LinearLayout.LayoutParams(-1,dp(160)));LinearLayout copy=col();pad(copy,18);copy.addView(bold("去山野，也去日常之外",21,INK));space(copy,8);copy.addView(text("把灵感变成下一段旅程。",13,MUTED));space(copy,15);pair(copy,action("快速规划 →",true,()->new QuickPlanner(this).start()),action("＋ 新建旅行",false,()->tripEditor(null)));hero.addView(copy);body.addView(hero);section("我的旅行  ·  "+trips.size());
        if(trips.isEmpty())body.addView(text("还没有行程。创建第一段旅行吧。",14,MUTED));
        for(Trip t:trips){LinearLayout c=card(body);c.addView(text(t.city+" / "+t.days+" DAYS",12,MUTED));space(c,10);TextView title=bold(t.title,20,INK);title.setContentDescription("打开："+t.title);c.addView(title);space(c,8);c.addView(text(t.start+" 出发 · "+t.stops.size()+" 个地点",13,MUTED));View.OnLongClickListener delete=v->{confirm("删除“"+t.title+"”？账单、清单和打卡记录也会移除。",()->deleteTrip(t));return true;};c.setOnClickListener(v->openTrip(t));c.setOnLongClickListener(delete);title.setOnClickListener(v->openTrip(t));title.setOnLongClickListener(delete);space(c,14);pair(c,action("打开行程 →",true,()->openTrip(t)),action("编辑旅行",false,()->tripEditor(t)));}
        space(body,4);body.addView(text("长按旅行卡片可删除 · 点击标题进入行程\n数据保存在本机，WebDAV 备份由你主动连接。",12,MUTED));
    }
    void deleteTrip(Trip t){trips.remove(t);if(active==t){active=trips.isEmpty()?null:trips.get(0);day=0;}changed();}
    void tripLabel(){TextView v=text(active.title+" ▾",13,INK);v.setPadding(0,dp(8),0,dp(16));v.setContentDescription("切换旅行");v.setOnClickListener(w->{String[] names=new String[trips.size()];for(int i=0;i<names.length;i++)names[i]=trips.get(i).title;new AlertDialog.Builder(this).setTitle("切换旅行").setItems(names,(d,i)->{active=trips.get(i);day=0;render();}).show();});body.addView(v);}
    void itinerary(){
        tripLabel();heading("每日行程","按时间串起风景，也留一点空白。");pair(body,action("列表日程",!mapMode,()->{mapMode=false;render();}),action("地图导览",mapMode,()->{mapMode=true;render();}));space(body,16);HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout ds=row();
        for(int i=0;i<active.days;i++){final int d=i;TextView v=action("D"+(i+1)+"\n"+LocalDate.parse(active.start).plusDays(i).format(DateTimeFormatter.ofPattern("M/d")),day==i,()->{day=d;render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(76),-2);p.rightMargin=dp(8);ds.addView(v,p);}hs.addView(ds);body.addView(hs);hs.post(()->hs.scrollTo(dp(Math.max(0,day-1)*84),0));space(body,18);ArrayList<Trip.Stop> schedule=active.onDay(day);
        if(mapMode){mapUi=new AmapUi(this);mapUi.show(schedule);mapUi.resume();}else{long cost=0;for(Trip.Stop s:schedule)cost+=s.cost;body.addView(text(schedule.size()+" 个地点 · 预计 ¥"+Trip.money(cost),13,MUTED));space(body,14);if(schedule.isEmpty()){LinearLayout c=card(body);c.addView(bold("今天，想去哪里？",20,INK));space(c,8);c.addView(text("添加地点或粘贴高德分享链接。",14,MUTED));}int lastEnd=-1;
            for(int i=0;i<schedule.size();i++){Trip.Stop s=schedule.get(i);LinearLayout c=card(body);c.addView(bold(String.format(Locale.ROOT,"%02d   %s · %d 分钟",i+1,s.time,s.duration),15,INK));space(c,12);c.addView(bold(s.name,20,INK));space(c,8);c.addView(text("抵达："+s.mode+" / 预计 ¥"+Trip.money(s.cost),13,MUTED));if(!s.address.isEmpty()){space(c,7);c.addView(text(s.address,13,MUTED));}if(!s.openingHours.isEmpty()){space(c,7);c.addView(text("营业时间："+s.openingHours,13,MUTED));}if(s.rating!=null){space(c,7);c.addView(text("评分："+s.rating+" / 5 · 来源信息请核实",13,MUTED));}if(!s.note.isEmpty()){space(c,9);c.addView(text(s.note,14,MUTED));}int start=LocalTime.parse(s.time).toSecondOfDay()/60;if(start<lastEnd){space(c,8);c.addView(text("与前面的安排时间重叠，请检查。",12,ORANGE));}lastEnd=Math.max(lastEnd,start+s.duration);space(c,14);pair(c,action("高德查看 ↗",false,()->map(s)),action("编辑地点",false,()->stopEditor(s)));c.setOnLongClickListener(v->{confirm("删除“"+s.name+"”？",()->{active.stops.remove(s);changed();});return true;});}
        }
        space(body,14);pair(body,action("＋ 添加地点",true,()->stopEditor(null)),action("导入高德链接",false,()->importPlace("")));space(body,12);body.addView(action("高德搜索地点",false,()->new AmapPlaceSearch(this).search("")));space(body,12);body.addView(action("分享文字行程",false,this::share));
    }
    EditText field(LinearLayout form,String label,String value,int type){form.addView(text(label,13,MUTED));EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setInputType(type);e.setSingleLine((type&InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0);form.addView(e,new LinearLayout.LayoutParams(-1,-2));space(form,12);return e;}
    Spinner select(LinearLayout form,String label,String[] options,String current){form.addView(text(label,13,MUTED));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));for(int i=0;i<options.length;i++)if(options[i].equals(current))s.setSelection(i);form.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));space(form,10);return s;}
    String required(EditText e,int max){String s=e.getText().toString().trim();if(s.isEmpty()||s.length()>max){e.setError("请填写 1–"+max+" 个字");throw new IllegalArgumentException("请检查填写内容");}return s;}
    int number(EditText e,int min,int max){try{int n=Integer.parseInt(e.getText().toString());if(n<min||n>max)throw new NumberFormatException();return n;}catch(Exception ex){e.setError("请输入 "+min+"–"+max);throw new IllegalArgumentException("请检查数字范围");}}
    void dialog(String title,LinearLayout form,Runnable submit,Runnable remove){pad(form,22);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null);if(remove!=null)b.setNeutralButton("删除",(d,w)->confirm("确定删除？此操作不能撤销。",remove));AlertDialog d=b.create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{if(loadFailed){toast("请先通过备份恢复数据");return;}try{submit.run();d.dismiss();}catch(IllegalArgumentException e){toast(e.getMessage());}}));d.show();}
    void confirm(String label,Runnable fn){new AlertDialog.Builder(this).setTitle(label).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->fn.run()).show();}
    void datePicker(EditText field){LocalDate date=LocalDate.parse(field.getText().toString());new DatePickerDialog(this,(v,y,m,d)->field.setText(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show();}
    void tripEditor(Trip original){
        if(original==null&&trips.size()>=100){toast("最多支持 100 个旅行");return;}LinearLayout f=col();EditText title=field(f,"旅行名称",original==null?"":original.title,1),city=field(f,"目的地城市",original==null?"":original.city,1),date=field(f,"出发日期",original==null?LocalDate.now().plusDays(7).toString():original.start,1);date.setFocusable(false);date.setOnClickListener(v->datePicker(date));EditText days=field(f,"旅行天数（1–60）",original==null?"3":""+original.days,2),budget=field(f,"总预算（人民币）",original==null?"2000":Trip.money(original.budget),8194);
        dialog(original==null?"新的旅行":"编辑旅行",f,()->{String name=required(title,80),dest=required(city,80);int n=number(days,1,60);long money=Trip.cents(budget.getText().toString());if(original!=null)for(Trip.Stop s:original.stops)if(s.day>=n)throw new IllegalArgumentException("后面天数仍有地点，请先移动或删除后再缩短旅行");Trip t=original==null?new Trip():original;t.title=name;t.city=dest;t.start=date.getText().toString();t.days=n;t.budget=money;if(original==null){t.items.add(new Trip.Item("确认车票与住宿"));t.items.add(new Trip.Item("身份证 / 护照"));t.normalize();trips.add(t);}active=t;day=Math.min(day,n-1);changed();},original==null?null:()->deleteTrip(original));
    }
    void stopEditor(Trip.Stop original){editStop(original,original==null?new Trip.Stop():original);}
    void stopEditorDraft(Trip.Stop draft){editStop(null,draft);}
    private void editStop(Trip.Stop original,Trip.Stop s){
        if(active==null){toast("请先创建旅行");return;}if(original==null&&active.stops.size()>=1000){toast("地点数量已达上限");return;}final Trip target=active;LinearLayout f=col();EditText name=field(f,"地点名称",s.name,1);String[] days=new String[target.days];for(int i=0;i<days.length;i++)days[i]="第 "+(i+1)+" 天";Spinner chosen=select(f,"安排日期",days,days[original==null?day:s.day]);EditText time=field(f,"开始时间",s.time,1);time.setFocusable(false);time.setOnClickListener(v->{LocalTime t=LocalTime.parse(time.getText().toString());new TimePickerDialog(this,(p,h,m)->time.setText(String.format(Locale.ROOT,"%02d:%02d",h,m)),t.getHour(),t.getMinute(),true).show();});
        EditText duration=field(f,"停留时长（分钟）",""+s.duration,2);Spinner mode=select(f,"抵达方式",new String[]{"步行","地铁","公交","出租车","自驾","火车","飞机","其他"},s.mode);EditText cost=field(f,"预计费用（人民币）",Trip.money(s.cost),8194),address=field(f,"地址",s.address,1),hours=field(f,"营业时间（未知可留空）",s.openingHours,1),rating=field(f,"评分 0–5（未知可留空）",s.rating==null?"":String.valueOf(s.rating),8194);EditText lat=field(f,"纬度（地图位置，可留空）",s.lat==null?"":String.valueOf(s.lat),12290),lon=field(f,"经度",s.lon==null?"":String.valueOf(s.lon),12290);Spinner coord=select(f,"坐标来源",new String[]{"WGS84","GCJ02"},s.coordinateSystem);EditText note=field(f,"备注",s.note,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);if(!s.sourceUrl.isEmpty())f.addView(text("来源："+s.sourceUrl,12,MUTED));
        dialog(original==null?"添加地点":"编辑地点",f,()->{String n=required(name,120);int dur=number(duration,1,1440);long c=Trip.cents(cost.getText().toString());String notes=note.getText().toString().trim(),addr=address.getText().toString().trim(),open=hours.getText().toString().trim();if(notes.length()>2000||addr.length()>300||open.length()>300)throw new IllegalArgumentException("文字内容过长");String la=lat.getText().toString().trim(),lo=lon.getText().toString().trim();Double latitude=null,longitude=null;Float score=null;
            try{if(!la.isEmpty()||!lo.isEmpty()){latitude=Double.parseDouble(la);longitude=Double.parseDouble(lo);if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new Exception();}}catch(Exception e){throw new IllegalArgumentException("请同时填写有效的经纬度");}String rs=rating.getText().toString().trim();try{if(!rs.isEmpty()){score=Float.parseFloat(rs);if(!Float.isFinite(score)||score<0||score>5)throw new Exception();}}catch(Exception e){throw new IllegalArgumentException("评分请输入 0–5");}
            s.name=n;s.time=time.getText().toString();s.day=chosen.getSelectedItemPosition();s.duration=dur;s.mode=mode.getSelectedItem().toString();s.cost=c;s.address=addr;s.openingHours=open;s.rating=score;s.lat=latitude;s.lon=longitude;s.coordinateSystem=coord.getSelectedItem().toString();s.note=notes;if(original==null)target.stops.add(s);active=target;day=s.day;changed();
        },original==null?null:()->{target.stops.remove(original);changed();});
    }
    void importPlace(String initial){
        if(active==null){toast("请先创建一个旅行，再导入地点");return;}LinearLayout form=col();pad(form,20);EditText input=field(form,"粘贴高德分享链接或分享文字",initial,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);form.addView(text("识别名称、位置及分享页提供的信息。缺失的营业时间或评分可手动补充。",12,MUTED));AlertDialog d=new AlertDialog.Builder(this).setTitle("导入高德地点").setView(form).setNegativeButton("取消",null).setPositiveButton("识别",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{final String shared=input.getText().toString().trim();try{PlaceImporter.extractUrl(shared);}catch(Exception e){input.setError(e.getMessage());return;}d.dismiss();final PlaceImporter.Place[] result=new PlaceImporter.Place[1];runJob("正在识别分享地点…",()->{result[0]=PlaceImporter.resolve(shared);return null;},()->{PlaceImporter.Place p=result[0];Trip.Stop s=new Trip.Stop();s.name=p.name;s.address=p.address;s.openingHours=p.openingHours;s.sourceUrl=p.sourceUrl;s.lat=p.lat;s.lon=p.lon;s.coordinateSystem=p.coordinateSystem;s.day=day;try{if(!p.rating.isEmpty())s.rating=Float.valueOf(p.rating);}catch(NumberFormatException ignored){}stopEditorDraft(s);});}));d.show();
    }
    void map(Trip.Stop s){Intent intent;if(s.lat!=null&&s.lon!=null){Uri uri=new Uri.Builder().scheme("androidamap").authority("viewMap").appendQueryParameter("sourceApplication","麦穗旅序").appendQueryParameter("poiname",s.name).appendQueryParameter("lat",String.valueOf(s.lat)).appendQueryParameter("lon",String.valueOf(s.lon)).appendQueryParameter("dev","GCJ02".equals(s.coordinateSystem)?"0":"1").build();intent=new Intent(Intent.ACTION_VIEW,uri).setPackage("com.autonavi.minimap");}else{Uri uri=Uri.parse("androidamap://poi").buildUpon().appendQueryParameter("sourceApplication","麦穗旅序").appendQueryParameter("keywords",s.name).appendQueryParameter("city",active.city).appendQueryParameter("dev","0").build();intent=new Intent(Intent.ACTION_VIEW,uri).setPackage("com.autonavi.minimap");}try{startActivity(intent);}catch(ActivityNotFoundException e){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://uri.amap.com/search?keyword="+Uri.encode(s.name)+"&city="+Uri.encode(active.city)+"&view=map")));}catch(ActivityNotFoundException ex){toast("请安装高德地图或浏览器");}}}
    void share(){StringBuilder b=new StringBuilder(active.title+"\n"+active.city+" · "+active.start+" · "+active.days+"天\n");for(int i=0;i<active.days;i++){b.append("\n第 ").append(i+1).append(" 天\n");for(Trip.Stop s:active.onDay(i))b.append(s.time).append(" ").append(s.name).append(" · ").append(s.duration).append("分钟 · ").append(s.mode).append("\n").append(s.note.isEmpty()?"":s.note+"\n");}b.append("\n总预算 ¥").append(Trip.money(active.budget)).append("\n来自麦穗旅序");startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,b.toString()),"分享行程"));}
    void showTutorial(){tutorial.start();}
    @Override public void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(media.onResult(req,result,data))return;settings.onActivityResult(req,result,data);}

    static final class Landscape extends View{
        final Paint p=new Paint(3);Landscape(Context c){super(c);}
        void fill(Canvas c,int color,float... points){p.setColor(color);Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,p);}
        @Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/400f,getHeight()/160f);c.drawColor(0xffE7EDDF);p.setColor(0xffDCA76C);c.drawCircle(306,44,23,p);fill(c,0xffBDCCB4,0,138,72,42,126,101,202,33,328,151,400,117,400,160,0,160);fill(c,0xff729681,0,133,73,93,135,141,246,65,350,156,400,115,400,160,0,160);fill(c,0xff376D59,0,155,96,120,184,158,294,111,400,141,400,160,0,160);p.setColor(0xffF6F5F0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);Path trail=new Path();trail.moveTo(213,160);trail.cubicTo(192,143,286,145,267,131);c.drawPath(trail,p);p.setStyle(Paint.Style.FILL);c.restore();}
    }
}
