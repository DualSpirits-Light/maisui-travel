package cn.lvxu.travel;
import android.app.AlertDialog;
import android.view.Gravity;
import java.time.LocalDateTime;

/** Guided local example. Only explicit tutorial actions change sample data. */
final class TutorialUi {
    private final MainActivity a;private Trip example;private boolean showing;
    TutorialUi(MainActivity host){a=host;}
    void start(){if(showing)return;showing=true;step(0);}
    private void step(int n){
        String[] titles={"欢迎来到麦穗旅序","打开杭州旅行","按天安排景点","记下第一笔开销","整理行前清单","查看每日动线","准备好出发了"};
        String[] messages={"接下来通过杭州示例，认识日程、预算、清单与地图。你可以随时跳过，之后在设置中重新学习。","首页的旅行标题可以直接打开行程，长按卡片可以删除。点击下方按钮，打开杭州示例。","顶部日期可切换每天安排；点击地点可编辑时间、交通和费用。现在我们查看第 2 天的安排。","预算页可以自定义分类、记录支出时间，并切换饼图与折线图。下一步试着记录 28 元的示例午餐。","每个旅行可以有多个清单，每项可设置重要性、备注和图片。下一步勾选示例中的身份证项目。","地图按日期显示地点和直线动线，可以选择两点测量直线距离。底图加载需要联网，距离并非实际道路里程。","你已完成杭州示例！首页的快速规划会引导创建自己的旅行；右上角头像可设置背景、备份与外观。"};
        String[] buttons={"开始体验","打开示例","查看预算","记录示例午餐","勾选身份证","完成学习","开始我的旅行"};
        AlertDialog d=new AlertDialog.Builder(a).setTitle((n+1)+"/7 · "+titles[n]).setMessage(messages[n]).setNegativeButton("跳过教程",(v,w)->finish()).setPositiveButton(buttons[n],(v,w)->{if(n==6){finish();return;}if(n==1){example=null;for(Trip t:a.trips)if(t.title.equals("杭州 · 把日子交给山水")){example=t;break;}if(example==null){example=Trip.demo();a.trips.add(example);}a.active=example;a.page=1;a.day=1;a.mapMode=false;a.changed();}if(n==2){a.page=2;a.render();}if(n==3&&example!=null){boolean found=false;for(Trip.Expense e:example.expenses)if(e.name.equals("教程午餐（示例）"))found=true;if(!found){Trip.Expense e=new Trip.Expense();e.name="教程午餐（示例）";e.amount=2800;e.categoryId=example.categories.get(0).id;e.occurredAt=LocalDateTime.now().withSecond(0).withNano(0).toString();example.expenses.add(e);}a.page=3;a.changed();}if(n==4&&example!=null){for(Trip.Item i:example.items)if(i.name.contains("身份证"))i.done=true;a.page=1;a.day=0;a.mapMode=true;a.changed();}step(n+1);}).create();d.setOnCancelListener(v->finish());d.show();if(d.getWindow()!=null)d.getWindow().setGravity(Gravity.BOTTOM);
    }
    private void finish(){showing=false;a.prefs.setTutorialDone(true);a.page=0;a.render();a.settings.checkDailyUpdate();}
}
