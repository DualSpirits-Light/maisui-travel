package cn.lvxu.travel;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Map;

/** Narrow finance/header geometry and complete, exact AA details. Repeat at font scale 1.3. */
final class FinancePolishUiTest {
 static int run(Instrumentation in) throws Exception {
  Context context=in.getTargetContext();MainActivity host=null;AlertDialog[] details={null},editor={null};
  try(TestStartupGuard guard=new TestStartupGuard(context)) {
   host=(MainActivity)in.startActivitySync(new Intent(context,MainActivity.class)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));in.waitForIdleSync();
   MainActivity a=host;Trip trip=new Trip();trip.title="账本布局验证";
   Trip.Expense bill=new Trip.Expense();bill.name="五十位同行成员共同参加的博物馆主题展览以及周边漫步交通餐饮和预约门票费用明细";
   bill.occurredAt="2026-10-07T12:30";ArrayList<String> people=new ArrayList<>();
   for(int i=0;i<50;i++){Trip.Member member=new Trip.Member();member.name="同行成员"+(i+1);trip.members.add(member);people.add(member.id);bill.shares.put(member.id,199999900L+i);bill.amount+=199999900L+i;}
   bill.payerId=people.get(0);trip.expenses.add(bill);trip.normalize();
   Trip.Settlement transfer=new Trip.Settlement();transfer.fromId=people.get(1);transfer.toId=people.get(0);transfer.amount=17;transfer.occurredAt="2026-10-07T13:30";trip.settlements.add(transfer);
   String before=trip.json().toString();FinanceUi[] finance={null};LinearLayout[] body={null};
   in.runOnMainSync(()->{a.trips.clear();a.trips.add(trip);a.active=trip;a.body=a.col();finance[0]=new FinanceUi(a);finance[0].show();body[0]=a.body;measure(a,body[0],280);});
   int checks=0;View root=body[0],card=root.findViewWithTag("finance-expense:"+bill.id);
   TextView name=find(card,bill.name),amount=find(card,"¥"+FinanceUi.money(bill.amount));
   checks+=check(name!=null&&name.getLineCount()>1&&name.getEllipsize()==null,"long expense name wraps in full");
   checks+=check(amount!=null&&amount!=name&&amount.getLineCount()==1&&amount.getEllipsize()==null,"large exact amount occupies its own intact line");
   checks+=check(find(card,"50 人参与分摊")!=null,"overview retains participant count");
   checks+=check(find(card,AaLedger.label(trip,people.get(49)))==null,"overview does not inline fifty shares");
   TextView action=find(card,"分摊明细");checks+=check(action!=null&&action.isClickable()&&action.isFocusable()&&action.getHeight()>=a.dp(48)&&action.getContentDescription().toString().contains("50 人"),"share details has an accessible touch target");
   View firstRow=(View)find(root,"＋ 记一笔").getParent(),secondRow=(View)find(root,"同行成员").getParent();
   checks+=check(secondRow.getTop()-firstRow.getBottom()>=a.dp(8),"finance action rows are separated by eight dp");
   in.runOnMainSync(()->details[0]=finance[0].shareDetails(trip,bill));in.waitForIdleSync();
   View dialog=details[0].getWindow().getDecorView();
   checks+=check(find(dialog,"合计 ¥"+FinanceUi.money(bill.amount)+" · 50 人参与分摊")!=null,"details retains exact total and count");
   for(Map.Entry<String,Long> share:bill.shares.entrySet()){
    TextView member=find(dialog,AaLedger.label(trip,share.getKey()));
    checks+=check(member!=null&&find((View)member.getParent(),"¥"+FinanceUi.money(share.getValue()))!=null,"every member retains the exact individual share");
   }
   in.runOnMainSync(()->details[0].dismiss());
   // The card remains the editing entry, while its child details action is independently clickable.
   in.runOnMainSync(card::performClick);in.waitForIdleSync();
   java.lang.reflect.Field field=FinanceUi.class.getDeclaredField("draftDialog");field.setAccessible(true);editor[0]=(AlertDialog)field.get(finance[0]);
   checks+=check(editor[0]!=null&&editor[0].isShowing()&&find(editor[0].getWindow().getDecorView(),"编辑支出")!=null,"expense editing remains reachable from the card");
   in.runOnMainSync(()->editor[0].dismiss());
   checks+=check(before.equals(trip.json().toString()),"reading details and cancelling editing preserves expenses and actual transfers");
   final int[] geometry={0};in.runOnMainSync(()->{
    FinanceUi.Chart chart=new FinanceUi.Chart(a,trip,true);chart.layout(0,0,a.dp(280),a.dp(220));
    Bitmap image=Bitmap.createBitmap(a.dp(280),a.dp(220),Bitmap.Config.ARGB_8888);
    try{chart.draw(new Canvas(image));}finally{image.recycle();}
    geometry[0]+=check(Math.abs(chart.p.getTextSize()-12*a.getResources().getDisplayMetrics().scaledDensity)<.01f,"chart text follows font scale");
    float[] bounds=chart.axisBounds(bill.amount,a.dp(280),a.dp(220));Paint.FontMetrics font=chart.p.getFontMetrics();
    geometry[0]+=check(bounds[0]-a.dp(5)-chart.p.measureText("¥"+String.format(java.util.Locale.ROOT,"%.1f万",bill.amount/100d/10000))>=0,"largest axis amount fits its adaptive left margin");
    geometry[0]+=check(bounds[3]+a.dp(8)-font.ascent+font.descent<=a.dp(220),"date axis fits below plot at the current font scale");
    geometry[0]+=check(bounds[1]>bounds[0]&&bounds[3]>bounds[2],"adaptive axis margins retain a usable plot");
    PageUi page=new PageUi(a,"高级设置与旅行账单照片备份和同行成员管理");measure(a,page.surface,280);
    ViewGroup header=(ViewGroup)page.surface.getChildAt(0);TextView title=(TextView)header.getChildAt(0);ImageButton back=(ImageButton)header.getChildAt(1);
    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)title.getLayoutParams();
    geometry[0]+=check(params.leftMargin>=a.dp(56)&&params.rightMargin>=a.dp(56)&&title.getLeft()>=back.getRight(),"page title reserves back space on both sides");
    geometry[0]+=check(title.getLineCount()>1&&title.getEllipsize()==null&&header.getHeight()>=title.getHeight()&&header.getHeight()>=a.dp(56),"page header grows to fit wrapping title");
    geometry[0]+=check(back.getWidth()>=a.dp(48)&&back.getHeight()>=a.dp(48)&&"返回".contentEquals(back.getContentDescription()),"page back action preserves accessible touch size");
   });return checks+geometry[0];
  } finally {
   if(host!=null){MainActivity closing=host;in.runOnMainSync(()->{if(details[0]!=null)details[0].dismiss();if(editor[0]!=null)editor[0].dismiss();closing.finish();});in.waitForIdleSync();}
  }
 }
 private static void measure(MainActivity a,View root,int width){root.measure(View.MeasureSpec.makeMeasureSpec(a.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());}
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static TextView find(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=find(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
}
