package cn.lvxu.travel;
import android.content.*;
import android.content.ClipboardManager;
import android.net.Uri;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.*;
import java.util.regex.*;
import okhttp3.Call;

/** Explicit, cancellable lookup. External results never mutate a trip. */
final class MeituanTravelUi {
 private final MainActivity a;private final MeituanTravelService service;
 PageUi page;EditText city,query;TextView status,result;View submit,cancel,copy;
 private Call active;private long generation;private String lastResult="";
 MeituanTravelUi(MainActivity a){this(a,new MeituanTravelService(a.prefs.cloud));}
 MeituanTravelUi(MainActivity a,MeituanTravelService service){this.a=a;this.service=service;}
 void show(){
  page=new PageUi(a,"美团旅行");
  LinearLayout intro=a.card(page.body);intro.addView(a.bold("找灵感，也查出行信息",22,MainActivity.INK));
  intro.addView(a.text("查询景点、酒店、门票、车票和行程建议。结果由美团提供，价格和余票以跳转页面为准。",14,MainActivity.MUTED));
  city=a.field(page.body,"目的地城市",a.active==null?"":a.active.city,InputType.TYPE_CLASS_TEXT);city.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
  query=a.field(page.body,"想了解什么？","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);query.setMinLines(3);query.setHint("例如：周末两天，喜欢历史和美食，帮我安排轻松的行程");query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
  a.pair(page.body,a.action("景点推荐",false,()->query.setText("推荐几个值得去的景点，说明特色、开放时间和注意事项")),a.action("住宿参考",false,()->query.setText("推荐交通方便的住宿区域，说明适合人群和预算参考")));
  page.body.addView(a.text("点击查询会将上方城市与需求发送至美团，通常需要 1–2 分钟。需有效云授权，有调用额度限制。",12,MainActivity.MUTED));
  status=a.text("填写需求后开始查询",14,MainActivity.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.body.addView(status);
  submit=a.action("查询美团旅行",true,this::start);cancel=a.action("取消查询",false,()->stop(true));a.pair(page.body,submit,cancel);cancel.setVisibility(View.GONE);
  LinearLayout card=a.card(page.body);result=a.text("查询结果将在这里显示。",15,MainActivity.INK);result.setTextIsSelectable(true);result.setLineSpacing(a.dp(4),1);card.addView(result);
  copy=a.action("复制完整结果",false,()->{if(lastResult.isEmpty())return;((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("美团旅行查询",lastResult));a.toast("已复制");});copy.setVisibility(View.GONE);page.body.addView(copy);
  page.dialog.setOnDismissListener(d->stop(false));page.show();
 }
 private void busy(boolean value){submit.setEnabled(!value);city.setEnabled(!value);query.setEnabled(!value);cancel.setVisibility(value?View.VISIBLE:View.GONE);}
 private void start(){
  if(active!=null)return;
  if(city.getText().toString().trim().isEmpty()){city.setError("请填写目的地城市");city.requestFocus();return;}
  if(query.getText().toString().trim().isEmpty()){query.setError("请填写旅行需求");query.requestFocus();return;}
  final Call call;try{call=service.newQuery(city.getText().toString(),query.getText().toString());}
  catch(IllegalArgumentException|IllegalStateException e){status.setText(e.getMessage());return;}
  catch(Exception e){status.setText("无法读取云授权，请在设置中检查授权状态");return;}
  active=call;long id=++generation;busy(true);status.setText("正在查询美团旅行…通常需要 1–2 分钟，可随时取消");
  new Thread(()->{String value="",failure="";try{value=MeituanTravelService.execute(call);}catch(Exception e){failure=call.isCanceled()?"已取消查询":safeFailure(e);}
   String content=value,error=failure;a.runOnUiThread(()->{if(id!=generation||!page.alive())return;active=null;busy(false);if(!error.isEmpty()){status.setText(error+(lastResult.isEmpty()?"":"；下方保留上次结果"));return;}lastResult=content;render(content);copy.setVisibility(View.VISIBLE);status.setText("查询完成 · 来源：美团旅行");});
  },"meituan-travel").start();
 }
 private static String safeFailure(Exception e){String s=e.getMessage();if(s!=null&&(s.startsWith("美团")||s.startsWith("服务")||s.startsWith("云授权")||s.startsWith("授权")||s.startsWith("查询")||s.startsWith("结果")||s.startsWith("暂未")))return s;return "网络连接失败，请检查网络后重试";}
 private void stop(boolean show){generation++;if(active!=null){active.cancel();active=null;}if(show&&page.alive()){busy(false);status.setText("已取消查询"+(lastResult.isEmpty()?"":"；下方保留上次结果"));}}
 private void render(String content){
  SpannableString text=new SpannableString(content);Matcher m=Pattern.compile("https://[^\\s<>\\[\\]\\)]+",Pattern.CASE_INSENSITIVE).matcher(content);
  while(m.find()){String address=m.group();Uri uri=Uri.parse(address);if(uri.getHost()==null||uri.getUserInfo()!=null)continue;int start=m.start(),end=m.end();text.setSpan(new ClickableSpan(){@Override public void onClick(View view){if(!page.alive())return;new RoundedDialogs.Builder(a).setTitle("打开美团提供的链接").setMessage("即将前往："+uri.getHost()+"\n价格、预订与支付由目标页面提供，请核实后操作。").setNegativeButton("取消",null).setPositiveButton("打开",(d,w)->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){a.toast("没有可用的浏览器");}}).show();}},start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);}
  result.setText(text);result.setMovementMethod(LinkMovementMethod.getInstance());
 }
}
