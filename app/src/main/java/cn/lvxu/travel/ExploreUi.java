package cn.lvxu.travel;
import java.util.*;
final class ExploreUi {
 private final MainActivity a;ExploreUi(MainActivity a){this.a=a;}
 private static final class Feature {final String title,description;final Runnable open;Feature(String t,String d,Runnable r){title=t;description=d;open=r;}}
 void show(){PageUi p=new PageUi(a,"探索");List<Feature> features=Arrays.asList(new Feature("美团旅行","查景点、住宿和出行信息，预览美团提供的旅行建议",()->new MeituanTravelUi(a).show()),new Feature("当地人流热力图","查看当前位置附近的百度城市人流热力，帮助安排出行",a::showLocalHeatmap),new Feature("那年今日","回看往年此时的旅行、照片与心情",()->new OnThisDayUi(a).show()),new Feature("AI 搜索","搜索旅行信息，查看来源并核实细节",()->new AiExploreUi(a).search()),new Feature("AI 规划","说说你的旅行想法，生成可调整的计划",()->new AiExploreUi(a).plan()),new Feature("AI 优化","逐项查看修改前后和原因，自选应用",()->new AiExploreUi(a).optimize()));for(Feature f:features){android.widget.LinearLayout c=a.card(p.body);c.addView(a.bold(f.title,22,MainActivity.INK));c.addView(a.text(f.description,14,MainActivity.MUTED));c.addView(a.action("进入 "+f.title+" →",false,f.open));}p.show(true);}
}
