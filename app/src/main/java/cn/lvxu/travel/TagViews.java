package cn.lvxu.travel;

import android.text.TextUtils;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;

/** Compact, readable tag chips shared by travel and place cards. */
final class TagViews {
 private static final int MAX_VISIBLE=3;
 private TagViews() {}
 static void add(MainActivity a,LinearLayout parent,List<String> ids,Map<String,String> names){
  ArrayList<String> labels=new ArrayList<>(),keys=new ArrayList<>();LinkedHashSet<String> all=new LinkedHashSet<>();if(ids!=null)all.addAll(ids);if(names!=null)all.addAll(names.keySet());
  for(String id:all){String name=names==null?null:names.get(id);if(name!=null&&!name.trim().isEmpty()){keys.add(id);labels.add(name.trim());}}
  if(labels.isEmpty())return;int hidden=Math.max(0,labels.size()-MAX_VISIBLE),shown=Math.min(MAX_VISIBLE,labels.size());ArrayList<String> summary=new ArrayList<>(labels.subList(0,shown));if(hidden>0)summary.add("+"+hidden);
  HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=a.row();row.setPadding(0,a.dp(2),0,a.dp(2));TagRepository repo=new TagRepository(a);
  for(int i=0;i<summary.size();i++){String label=summary.get(i);int color=MainActivity.PALE;if(i<shown){TagRepository.Tag tag=repo.find(keys.get(i));if(tag!=null)color=tag.color;}if(MainActivity.DARK)color=MainActivity.blend(color,MainActivity.SURFACE,.58f);TextView chip=a.text(label,12,MainActivity.readable(ThemeColors.onColor(color),color));chip.setSingleLine(true);chip.setEllipsize(TextUtils.TruncateAt.END);chip.setMaxWidth(a.dp(112));chip.setPadding(a.dp(10),a.dp(5),a.dp(10),a.dp(5));chip.setBackground(a.shape(color,12));if(i==0)chip.setContentDescription("标签："+String.join("、",summary));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);if(i>0)p.leftMargin=a.dp(6);row.addView(chip,p);}
  scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));parent.addView(scroll,new LinearLayout.LayoutParams(-1,-2));a.space(parent,8);
 }
}
