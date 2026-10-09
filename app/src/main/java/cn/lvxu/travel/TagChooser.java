package cn.lvxu.travel;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A single dialog owns a draft so switching filters never changes saved labels. */
final class TagChooser {
 private final MainActivity a;
 private final TagRepository repo;
 private final boolean multi;
 private final ArrayList<String> targetIds;
 private final LinkedHashMap<String,String> targetNames;
 private final LinkedHashMap<String,String> selected=new LinkedHashMap<>();
 private final ArrayList<TagRepository.Tag> tags;
 private final ArrayList<TagRepository.Group> groups;
 private final TagUi.Applied done;
 private final LinearLayout tabs;
 private final Flow available,chosen;
 private final TextView count;
 private String groupId="sys-preset-2";
 private boolean custom;
 private TagChooser(MainActivity a,ArrayList<String> ids,LinkedHashMap<String,String> names,boolean multi,TagUi.Applied done){
  this.a=a;this.repo=new TagRepository(a);this.multi=multi;targetIds=ids;targetNames=names;this.done=done;
  tags=new ArrayList<>(repo.all());groups=new ArrayList<>(repo.groups());
  for(String id:ids){String name=names.get(id);if(name==null)for(TagRepository.Tag t:tags)if(t.id.equals(id)){name=t.name;break;}selected.put(id,name==null?id:name);}
  tabs=new LinearLayout(a);tabs.setOrientation(LinearLayout.HORIZONTAL);
  available=new Flow(a);chosen=new Flow(a);count=label("");
 }
 static void show(MainActivity a,ArrayList<String> ids,LinkedHashMap<String,String> names,boolean multi,TagUi.Applied done){new TagChooser(a,ids,names,multi,done).show();}
 private void show(){
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(8));
  body.addView(label("标签分组")); HorizontalScrollView groupScroll=new HorizontalScrollView(a);groupScroll.setHorizontalScrollBarEnabled(false);groupScroll.addView(tabs);body.addView(groupScroll);
  ScrollView choices=new ScrollView(a);choices.setFillViewport(true);choices.addView(available);int height=Math.min(dp(250),a.getResources().getDisplayMetrics().heightPixels/3);body.addView(choices,new LinearLayout.LayoutParams(-1,height));
  body.addView(count);ScrollView selection=new ScrollView(a);selection.addView(chosen);body.addView(selection,new LinearLayout.LayoutParams(-1,dp(104)));
  render();
  new RoundedDialogs.Builder(a).asSelection().setTitle(multi?"选择标签":"选择标签（单选）").setView(body).setNegativeButton("取消",null).setNeutralButton("管理标签和标签组",(d,w)->TagUi.manage(a,repo,done)).setPositiveButton("确定",(d,w)->{targetIds.clear();targetIds.addAll(selected.keySet());targetNames.clear();targetNames.putAll(selected);done.onApplied();}).show();
 }
 private void render(){
  tabs.removeAllViews();tab("全部",null,false);for(TagRepository.Group g:groups)tab("sys-preset-1".equals(g.id)?"详细预设":"sys-preset-2".equals(g.id)?"简洁预设":g.name,g.id,false);tab("自定义",null,true);
  available.removeAllViews();for(TagRepository.Tag t:tags)if(custom?!t.system:groupId==null||t.groupIds.contains(groupId)){
   TextView chip=chip(t.name,selected.containsKey(t.id));chip.setContentDescription(t.name+(selected.containsKey(t.id)?"，已选择":"，未选择"));chip.setSelected(selected.containsKey(t.id));chip.setOnClickListener(v->{if(selected.containsKey(t.id))selected.remove(t.id);else{if(!multi)selected.clear();selected.put(t.id,t.name);}render();});available.addView(chip);
  }
  if(available.getChildCount()==0)available.addView(label(custom?"还没有自定义标签，可通过管理添加":"这个标签组暂无标签"));
  count.setText("已选标签 · "+selected.size());chosen.removeAllViews();for(Map.Entry<String,String> e:selected.entrySet()){
   String id=e.getKey();FrameLayout item=new FrameLayout(a);item.setPadding(dp(8),dp(8),0,0);TextView label=chip(e.getValue(),true);item.addView(label,new FrameLayout.LayoutParams(-2,-2));TextView remove=new TextView(a);remove.setText("×");remove.setTextSize(13);remove.setGravity(Gravity.CENTER);GradientDrawable badge=new GradientDrawable();badge.setColor(MainActivity.SURFACE);badge.setCornerRadius(dp(10));badge.setStroke(dp(1),MainActivity.GREEN);remove.setBackground(badge);remove.setTextColor(MainActivity.readable(MainActivity.GREEN,MainActivity.SURFACE));FrameLayout.LayoutParams badgeLayout=new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.TOP|Gravity.LEFT);badgeLayout.leftMargin=-dp(6);badgeLayout.topMargin=-dp(6);item.addView(remove,badgeLayout);item.setContentDescription("移除标签 "+e.getValue());item.setOnClickListener(v->{selected.remove(id);render();});item.setFocusable(true);remove.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);chosen.addView(item);
  }if(selected.isEmpty())chosen.addView(label("点击上方标签添加"));
 }
 private void tab(String name,String id,boolean own){boolean on=custom==own&&Objects.equals(groupId,id);LinearLayout tab=new LinearLayout(a);tab.setOrientation(LinearLayout.VERTICAL);tab.setSelected(on);tab.setContentDescription("标签分组："+name+(on?"，当前分组":""));TextView title=new TextView(a);title.setText(name);title.setTextSize(14);title.setGravity(Gravity.CENTER);title.setMinHeight(dp(44));title.setPadding(dp(14),dp(8),dp(14),dp(8));title.setTextColor(MainActivity.readable(on?MainActivity.GREEN:MainActivity.MUTED,MainActivity.SURFACE));title.setTypeface(null,on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);tab.addView(title,new LinearLayout.LayoutParams(-1,-2));View underline=new View(a);underline.setBackgroundColor(on?MainActivity.GREEN:android.graphics.Color.TRANSPARENT);tab.addView(underline,new LinearLayout.LayoutParams(-1,dp(3)));tab.setOnClickListener(v->{groupId=id;custom=own;render();});tab.setFocusable(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(4),dp(8));tabs.addView(tab,p);}
 private TextView label(String text){TextView v=new TextView(a);v.setText(text);v.setTextSize(13);v.setTextColor(MainActivity.readable(MainActivity.MUTED,MainActivity.SURFACE));v.setPadding(0,dp(10),0,dp(8));return v;}
 private TextView chip(String text,boolean on){TextView v=new TextView(a);v.setText(text);v.setTextSize(14);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(44));v.setPadding(dp(14),dp(8),dp(14),dp(8));int bg=on?MainActivity.GREEN:MainActivity.blend(MainActivity.GREEN,MainActivity.SURFACE,.09f);GradientDrawable shape=new GradientDrawable();shape.setColor(bg);shape.setCornerRadius(dp(22));shape.setStroke(dp(1),MainActivity.blend(MainActivity.GREEN,MainActivity.SURFACE,.24f));v.setBackground(shape);v.setTextColor(MainActivity.readable(on?0xffffffff:MainActivity.INK,bg));return v;}
 private int dp(int n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
 /** Wrap chips using their measured width, including large-font and narrow screens. */
 private static final class Flow extends ViewGroup {
  private final int gap;
  Flow(MainActivity a){super(a);gap=(int)(8*a.getResources().getDisplayMetrics().density+.5f);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),limit=Math.max(1,width-getPaddingLeft()-getPaddingRight()),x=0,y=0,row=0;for(int i=0;i<getChildCount();i++){View c=getChildAt(i);c.measure(MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int w=c.getMeasuredWidth(),h=c.getMeasuredHeight();if(x>0&&x+w>limit){y+=row+gap;x=0;row=0;}x+=w+gap;row=Math.max(row,h);}setMeasuredDimension(width,resolveSize(y+row+getPaddingTop()+getPaddingBottom(),heightSpec));}
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int limit=r-l-getPaddingRight(),x=getPaddingLeft(),y=getPaddingTop(),row=0;for(int i=0;i<getChildCount();i++){View c=getChildAt(i);int w=c.getMeasuredWidth(),h=c.getMeasuredHeight();if(x>getPaddingLeft()&&x+w>limit){y+=row+gap;x=getPaddingLeft();row=0;}c.layout(x,y,x+w,y+h);x+=w+gap;row=Math.max(row,h);}}
  @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
 }
}
