package cn.lvxu.travel;
import java.util.*;
final class ExploreUi {
 private final MainActivity a;ExploreUi(MainActivity a){this.a=a;}
 private static final class Feature {final String title,description;final Runnable open;Feature(String t,String d,Runnable r){title=t;description=d;open=r;}}
 void show(){PageUi p=new PageUi(a,"探索");List<Feature> features=Arrays.asList(new Feature("那年今日","回看往年此时的旅行、照片与心情",()->new OnThisDayUi(a).show()),new Feature("AI 搜索","搜索旅行信息，查看来源并核实细节",()->new AiExploreUi(a).search()),new Feature("AI 规划","说说你的旅行想法，生成可调整的计划",()->new AiExploreUi(a).plan()));for(Feature f:features){android.widget.LinearLayout c=a.card(p.body);c.addView(a.bold(f.title,22,MainActivity.INK));c.addView(a.text(f.description,14,MainActivity.MUTED));c.addView(a.action("进入 "+f.title+" →",false,f.open));}p.show(true);}
}
