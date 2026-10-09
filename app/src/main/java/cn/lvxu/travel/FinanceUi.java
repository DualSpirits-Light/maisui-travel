package cn.lvxu.travel;
import android.os.Bundle;import android.app.*;import android.graphics.*;import android.view.*;import android.widget.*;import java.time.*;import java.util.*;

final class FinanceUi {
 private final MainActivity a;private boolean line,charts;private int visibleBills=30;private String visibleTrip="";private AaUi.ExpenseFields draftAa;
 private Bundle restored;private LinearLayout draftForm;private AlertDialog draftDialog;private Trip draftTrip;private Trip.Expense draftOld;private String[] draftPhoto;private ArrayList<String> draftOwned;
 private static final int[] COLORS={0xffE07A5F,0xff3D85C6,0xff8064A2,0xffD6A84B,0xff4F9D69,0xff829399};
 FinanceUi(MainActivity a){this.a=a;}
 static String money(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
 void show(){
  Trip t=a.active;if(!t.id.equals(visibleTrip)){visibleTrip=t.id;visibleBills=30;}t.normalize();a.tripLabel();a.heading("旅行账本","金额统一以人民币记录");
  LinearLayout c=a.card(a.body);long remain=t.budget-t.spent();c.addView(a.bold(t.budget==0?"尚未设置预算":(remain>=0?"剩余预算 ¥":"已超预算 ¥")+money(Math.abs(remain)),24,a.INK));
  long today=0;String date=LocalDate.now().toString();for(Trip.Expense e:t.expenses)if(e.occurredAt.startsWith(date))today+=e.amount;
  c.addView(a.text("实际支出 ¥"+money(t.spent())+" · 今日 ¥"+money(today),13,a.MUTED));
  a.pair(a.body,a.action("＋ 记一笔",true,()->expense(null)),a.action("管理分类",false,this::categories));
  a.space(a.body,8);a.pair(a.body,a.action("同行成员",false,()->new AaUi(a).members(t)),a.action("AA 结算",false,()->new AaUi(a).ledger(t)));ArrayList<Trip.Expense> es=new ArrayList<>(t.expenses);es.sort((x,y)->y.occurredAt.compareTo(x.occurredAt));
  a.section("最近账单");if(es.isEmpty())a.body.addView(a.text("还没有实际支出。",13,a.MUTED));
  for(int i=0;i<Math.min(3,es.size());i++)expenseRow(es.get(i));
  a.body.addView(a.action(charts?"收起支出图表":"查看支出图表",false,()->{charts=!charts;a.render();}));
  if(charts){a.pair(a.body,a.action("分类占比",!line,()->{line=false;a.render();}),a.action("日期趋势",line,()->{line=true;a.render();}));a.body.addView(new Chart(a,t,line),new LinearLayout.LayoutParams(-1,a.dp(220)));
   if(!line&&t.spent()>0)for(Trip.Category cat:t.categories){long sum=0;for(Trip.Expense e:t.expenses)if(cat.id.equals(e.categoryId))sum+=e.amount;if(sum>0)a.body.addView(a.text("● "+cat.name+"    ¥"+money(sum)+"    "+String.format(Locale.ROOT,"%.1f%%",sum*100d/t.spent()),13,cat.color));}}
  if(es.size()>3){a.section("更多账单");for(int i=3;i<Math.min(visibleBills,es.size());i++)expenseRow(es.get(i));if(es.size()>visibleBills)a.body.addView(a.action("加载更多账单（剩余 "+(es.size()-visibleBills)+" 笔）",false,()->{visibleBills+=30;a.render();}));}
 }
 private void expenseRow(Trip.Expense e){
  Trip owner=a.active;LinearLayout x=a.card(a.body);x.setTag("finance-expense:"+e.id);
  x.addView(a.bold(e.name,17,a.INK));a.space(x,4);x.addView(a.bold("¥"+money(e.amount),20,a.INK));
  a.space(x,6);x.addView(a.text(e.category+" · "+e.occurredAt.replace('T',' '),12,a.MUTED));
  if(!e.payerId.isEmpty()){
   x.addView(a.text("付款："+AaLedger.label(owner,e.payerId),12,a.MUTED));
   x.addView(a.text(e.shares.size()+" 人参与分摊",12,a.MUTED));a.space(x,8);
   TextView details=a.action("分摊明细",false,()->shareDetails(owner,e));
   details.setContentDescription("分摊明细，"+e.name+"，"+e.shares.size()+" 人");x.addView(details);
  }
  receiptPreview(x,e.photo);x.setOnClickListener(v->expense(e));
 }
 AlertDialog shareDetails(Trip owner,Trip.Expense e){
  LinearLayout content=a.col();a.pad(content,22);content.addView(a.bold(e.name,17,a.INK));
  content.addView(a.text("合计 ¥"+money(e.amount)+" · "+e.shares.size()+" 人参与分摊",13,a.MUTED));a.space(content,12);
  for(Map.Entry<String,Long> share:e.shares.entrySet()){
   LinearLayout row=a.col();row.setPadding(0,a.dp(8),0,a.dp(8));
   row.addView(a.text(AaLedger.label(owner,share.getKey()),14,a.INK));
   row.addView(a.bold("¥"+money(share.getValue()),16,a.INK));content.addView(row);
  }
  ScrollView scroll=new ScrollView(a);scroll.addView(content);
  AlertDialog dialog=new RoundedDialogs.Builder(a).setTitle("分摊明细").setView(scroll).setPositiveButton("关闭",null).create();dialog.show();return dialog;
 }
 void saveState(Bundle b){b.putInt("finance.visible",visibleBills);b.putString("finance.visibleTrip",visibleTrip);b.putBoolean("finance.line",line);b.putBoolean("finance.charts",charts);if(draftDialog==null||!draftDialog.isShowing())return;b.putBoolean("finance.editing",true);b.putString("finance.trip",draftTrip.id);b.putString("finance.old",draftOld==null?"":draftOld.id);b.putBundle("finance.form",FormDraftState.capture(draftForm));if(draftAa!=null)draftAa.save(b);b.putString("finance.photo",draftPhoto[0]);b.putStringArrayList("finance.owned",new ArrayList<>(draftOwned));}
 void restoreState(Bundle b){visibleBills=Math.max(30,b.getInt("finance.visible",30));visibleTrip=b.getString("finance.visibleTrip","");line=b.getBoolean("finance.line");charts=b.getBoolean("finance.charts");if(b.getBoolean("finance.editing"))restored=new Bundle(b);}
 void reopenDraft(){if(restored==null)return;Bundle state=restored;Trip owner=null;for(Trip t:a.trips)if(t.id.equals(state.getString("finance.trip")))owner=t;if(owner==null){restored=null;a.media.detachSelection(MediaController.SelectionOwner.EXPENSE);return;}Trip.Expense old=null;String id=state.getString("finance.old","");for(Trip.Expense e:owner.expenses)if(e.id.equals(id))old=e;if(!id.isEmpty()&&old==null){restored=null;a.media.detachSelection(MediaController.SelectionOwner.EXPENSE);return;}a.active=owner;expense(old);}
 void expense(Trip.Expense old){
  if(old==null&&a.active.expenses.size()>=5000){a.toast("账单数量已达上限");return;}
  Trip t=a.active;t.normalize();LinearLayout f=a.col();
  EditText name=a.field(f,"账单名称",old==null?"":old.name,1),amount=a.field(f,"实际金额（人民币）",old==null?"":money(old.amount),8194),when=DateTimeFields.dateTime(a,f,"日期时间",old==null?LocalDateTime.now().withSecond(0).withNano(0).toString():old.occurredAt);
  String[] names=new String[t.categories.size()];for(int i=0;i<names.length;i++)names[i]=t.categories.get(i).name;Spinner cat=a.select(f,"支出分类",names,old==null?names[0]:old.category);
  AaUi.ExpenseFields aa=new AaUi.ExpenseFields(a,f,t,old);draftAa=aa;
  final String[] photo={old==null?"":old.photo};final ArrayList<String> owned=new ArrayList<>();final boolean[] closed={false};
  LinearLayout preview=a.col();f.addView(preview);Runnable refresh=()->{preview.removeAllViews();receiptPreview(preview,photo[0]);};refresh.run();
  a.pair(f,a.action("添加 / 更换照片",false,()->a.media.choosePhoto(MediaController.SelectionOwner.EXPENSE,path->{if(closed[0]||a.isDestroyed()||a.isFinishing()){discardDraftPhoto(path);return;}owned.add(path);photo[0]=path;refresh.run();})),a.action("移除照片",false,()->{photo[0]="";refresh.run();}));
  a.pad(f,22);ScrollView scroll=new ScrollView(a);scroll.addView(f);
  AlertDialog.Builder builder=new RoundedDialogs.Builder(a).setTitle(old==null?"记录支出":"编辑支出").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null);
  if(old!=null)builder.setNeutralButton("删除",null);
  AlertDialog dialog=builder.create();draftDialog=dialog;draftForm=f;draftTrip=t;draftOld=old;draftPhoto=photo;draftOwned=owned;
  if(restored!=null){FormDraftState.restore(f,restored.getBundle("finance.form"));aa.restore(restored);photo[0]=restored.getString("finance.photo","");ArrayList<String> paths=restored.getStringArrayList("finance.owned");if(paths!=null)owned.addAll(paths);restored=null;refresh.run();}
  dialog.setOnShowListener(v->{
   if(old!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->a.confirm("删除这笔账单？删除后可撤销。"+(!old.payerId.isEmpty()&&!t.settlements.isEmpty()?"已有实际转账将保留，AA 余额会重新计算。":""),()->{if(deleteExpense(t,old))dialog.dismiss();}));
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
   if(a.loadFailed){a.toast("请先通过备份恢复数据");return;}
   try{String n=a.required(name,120);long value=Trip.cents(amount.getText().toString());String time;
    LocalDateTime parsed=DateTimeValues.parse(when.getText().toString(),null);if(parsed==null)throw new IllegalArgumentException("请选择有效日期时间");time=parsed.withSecond(0).withNano(0).toString();
    if(!a.trips.contains(t))throw new IllegalArgumentException("旅行已变化，请重新打开账单");int index=old==null?-1:t.expenses.indexOf(old);if(old!=null&&index<0)throw new IllegalArgumentException("账单已变化，请重新打开");Trip.Expense e=new Trip.Expense();if(old!=null)e.id=old.id;e.name=n;e.amount=value;e.occurredAt=time;e.photo=photo[0];Trip.Category c=t.categories.get(cat.getSelectedItemPosition());e.categoryId=c.id;e.category=c.name;aa.apply(e);
    Runnable commit=()->{if(!a.trips.contains(t)||(old!=null&&!t.expenses.contains(old))){a.toast("账单已变化，请重新打开");return;}Trip beforeTrip=TravelAdjustment.copy(t);int at=old==null?-1:t.expenses.indexOf(old);if(old==null)t.expenses.add(e);else t.expenses.set(at,e);if(!a.save()){if(old==null)t.expenses.remove(e);else t.expenses.set(at,old);return;}a.recordUndo(old==null?"添加账单":"编辑账单",beforeTrip,t);owned.remove(photo[0]);a.render();dialog.dismiss();};
    if(old!=null&&!t.settlements.isEmpty()&&(old.amount!=e.amount||!old.payerId.equals(e.payerId)||!old.shares.equals(e.shares)))a.confirm("已有实际转账记录。保存后 AA 余额会按新账单重新计算，转账记录保留。继续保存？",commit);else commit.run();
   }catch(IllegalArgumentException ex){a.toast(ex.getMessage());}
  });});
  dialog.setOnDismissListener(d->{closed[0]=true;if(a.isChangingConfigurations()||a.isDestroyed())return;a.media.detachSelection(MediaController.SelectionOwner.EXPENSE);for(String path:owned)discardDraftPhoto(path);owned.clear();if(draftDialog==dialog){draftDialog=null;draftForm=null;}});dialog.show();
  a.media.attachSelection(MediaController.SelectionOwner.EXPENSE,path->{if(closed[0]||a.isDestroyed()||a.isFinishing()){discardDraftPhoto(path);return;}owned.add(path);photo[0]=path;refresh.run();});
 }
 private boolean deleteExpense(Trip t,Trip.Expense old){if(!a.trips.contains(t)||!t.expenses.contains(old)){a.toast("账单已变化，请重新打开");return false;}try{Trip beforeTrip=TravelAdjustment.copy(t);int index=t.expenses.indexOf(old);t.expenses.remove(index);if(!a.save()){t.expenses.add(index,old);return false;}a.recordUndo("删除账单",beforeTrip,t);a.render();return true;}catch(IllegalArgumentException ex){a.toast(ex.getMessage());return false;}}
 private void discardDraftPhoto(String path){try{java.io.File file=a.mediaFile(path);if(file!=null&&!DraftMediaFiles.referenced(a,path))file.delete();}catch(IllegalArgumentException ignored){}}
 private void receiptPreview(LinearLayout parent,String photo){
  if(photo==null||photo.isEmpty())return;
  try{Bitmap bitmap=a.media.files.decode(photo,480);if(bitmap==null)return;ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(bitmap);image.setContentDescription("账单照片");parent.addView(image,new LinearLayout.LayoutParams(-1,a.dp(140)));image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){image.setImageDrawable(null);if(!bitmap.isRecycled())bitmap.recycle();}});}catch(Exception|OutOfMemoryError ignored){parent.addView(a.text("照片暂时无法读取，可更换照片",12,a.MUTED));}
 }
 private void categories(){Trip t=a.active;String[] options=new String[t.categories.size()+1];options[0]="＋ 新建分类";for(int i=0;i<t.categories.size();i++)options[i+1]=t.categories.get(i).name;new RoundedDialogs.Builder(a).setTitle("支出分类").setItems(options,(d,i)->{if(i==0)category(null);else category(t.categories.get(i-1));}).show();}
 private void category(Trip.Category old){Trip t=a.active;if(old==null&&t.categories.size()>=100){a.toast("最多支持 100 个分类");return;}LinearLayout f=a.col();EditText name=a.field(f,"分类名称",old==null?"":old.name,1);String[] names={"珊瑚","蓝色","紫色","金色","绿色","灰色"};int selected=4;if(old!=null)for(int i=0;i<COLORS.length;i++)if(COLORS[i]==old.color)selected=i;Spinner color=a.select(f,"颜色",names,names[selected]);a.dialog(old==null?"新建分类":"编辑分类",f,()->{String n=a.required(name,30);for(Trip.Category c:t.categories)if(c!=old&&c.name.equals(n))throw new IllegalArgumentException("分类名称已存在");Trip.Category c=old==null?new Trip.Category():old;c.name=n;c.color=COLORS[color.getSelectedItemPosition()];if(old==null)t.categories.add(c);else for(Trip.Expense e:t.expenses)if(c.id.equals(e.categoryId))e.category=n;a.changed();},old==null?null:()->deleteCategory(old));}
 private void deleteCategory(Trip.Category old){Trip t=a.active;if(t.categories.size()<2){a.toast("至少保留一个分类");return;}ArrayList<Trip.Category> rest=new ArrayList<>(t.categories);rest.remove(old);String[] n=new String[rest.size()];for(int i=0;i<n.length;i++)n[i]=rest.get(i).name;new RoundedDialogs.Builder(a).setTitle("选择分类接收原账单").setItems(n,(d,i)->{t.deleteCategory(old.id,rest.get(i).id);a.changed();}).setNegativeButton("取消",null).show();}
 static final class Chart extends View {
  final Paint p=new Paint(3);final Trip t;final boolean line;final MainActivity a;
  Chart(MainActivity a,Trip t,boolean line){super(a);this.a=a;this.t=t;this.line=line;setContentDescription(line?"按日期的支出趋势图":"按分类的支出占比图");}
  protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setTextSize(12*a.getResources().getDisplayMetrics().scaledDensity);p.setColor(a.MUTED);if(t.expenses.isEmpty()||(!line&&t.spent()==0)){p.setTextAlign(Paint.Align.CENTER);c.drawText("记录支出后，这里会显示图表",w/2,h/2,p);return;}
   if(!line){long total=t.spent();float at=-90,r=Math.min(w,h)*.4f,cx=w/2,cy=h/2;for(Trip.Category cat:t.categories){long n=0;for(Trip.Expense e:t.expenses)if(cat.id.equals(e.categoryId))n+=e.amount;if(n==0)continue;p.setColor(cat.color);float sweep=(float)(360d*n/total);c.drawArc(cx-r,cy-r,cx+r,cy+r,at,sweep,true,p);at+=sweep;}p.setColor(a.BG);c.drawCircle(cx,cy,r*.57f,p);p.setTextAlign(Paint.Align.CENTER);p.setColor(a.INK);c.drawText("分类占比",cx,cy+a.dp(4),p);return;}
   TreeMap<LocalDate,Long> m=new TreeMap<>();for(Trip.Expense e:t.expenses){LocalDate d=LocalDateTime.parse(e.occurredAt).toLocalDate();m.put(d,m.getOrDefault(d,0L)+e.amount);}long max=1;for(long n:m.values())max=Math.max(max,n);float[] axes=axisBounds(max,w,h);float left=axes[0],right=axes[1],top=axes[2],bottom=axes[3];p.setStrokeWidth(a.dp(1));p.setColor(a.LINE);c.drawLine(left,top,left,bottom,p);c.drawLine(left,bottom,right,bottom,p);p.setColor(a.MUTED);p.setTextAlign(Paint.Align.RIGHT);c.drawText("¥"+compact(max),left-a.dp(5),top-p.getFontMetrics().ascent/2,p);c.drawText("0",left-a.dp(5),bottom,p);p.setTextAlign(Paint.Align.LEFT);c.drawText(m.firstKey().toString().substring(5),left,bottom+a.dp(8)-p.getFontMetrics().ascent,p);if(m.size()>1){p.setTextAlign(Paint.Align.RIGHT);c.drawText(m.lastKey().toString().substring(5),right,bottom+a.dp(8)-p.getFontMetrics().ascent,p);}long first=m.firstKey().toEpochDay(),span=Math.max(1,m.lastKey().toEpochDay()-first);Path path=new Path();int index=0;ArrayList<float[]> dots=new ArrayList<>();for(Map.Entry<LocalDate,Long> e:m.entrySet()){float x=m.size()==1?(left+right)/2:left+(right-left)*(e.getKey().toEpochDay()-first)/span,y=bottom-(bottom-top)*e.getValue()/max;dots.add(new float[]{x,y});if(index++==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setColor(a.GREEN);p.setStrokeWidth(a.dp(2));p.setStyle(Paint.Style.STROKE);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);for(float[] dot:dots)c.drawCircle(dot[0],dot[1],a.dp(4),p);
  }
  float[] axisBounds(long max,float w,float h){
   p.setTextSize(12*a.getResources().getDisplayMetrics().scaledDensity);
   Paint.FontMetrics font=p.getFontMetrics();float textHeight=font.descent-font.ascent;
   float left=Math.max(a.dp(58),p.measureText("¥"+compact(max))+a.dp(17));
   float right=Math.max(left+a.dp(1),w-a.dp(12));
   float top=Math.max(a.dp(24),textHeight+a.dp(8));
   float bottom=Math.max(top+a.dp(1),h-textHeight-a.dp(16));
   return new float[]{left,right,top,bottom};
  }
  private String compact(long cents){double yuan=cents/100d;return yuan>=10000?String.format(Locale.ROOT,"%.1f万",yuan/10000):Trip.money(cents);}
 }
}
