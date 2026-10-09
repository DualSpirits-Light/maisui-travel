package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okio.Buffer;
import org.json.JSONObject;

/** Deterministic fake transport: never contacts a public or licensed endpoint. */
final class MeituanTravelUiTest {
 private static final String CONTENT="杭州住宿参考：价格 ￥4XX，请以页面为准\nhttps://www.meituan.com/travel/test";
 static int run(Instrumentation in)throws Exception {
  int n=0;MainActivity host=null;MeituanTravelUi[] holder={null};
  LinkedBlockingQueue<Reply> replies=new LinkedBlockingQueue<>();
  java.util.ArrayList<Reply> all=new java.util.ArrayList<>();
  OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
   Reply reply=replies.poll();if(reply==null)throw new IOException("Unexpected test request");
   reply.request=chain.request();reply.call=chain.call();reply.worker=Thread.currentThread();reply.entered.countDown();
   try{if(!reply.release.await(8,TimeUnit.SECONDS))throw new IOException("Fixture timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
   return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(reply.code).message("fixture")
    .body(ResponseBody.create(MediaType.get("application/json"),reply.body)).build();
  }).build();
  try(TestStartupGuard guard=new TestStartupGuard(in.getTargetContext())) {
   host=(MainActivity)in.startActivitySync(new Intent(in.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
   MainActivity a=host;in.waitForIdleSync();
   MeituanTravelService service=new MeituanTravelService(client,()->new JSONObject().put("licenseId","test-license").put("deviceId","test-device").put("deviceSecret","test-proof").put("developerAuthorization","must-not-forward"));
   in.runOnMainSync(()->{holder[0]=new MeituanTravelUi(a,service);holder[0].show();});in.waitForIdleSync();MeituanTravelUi ui=holder[0];
   in.runOnMainSync(()->{ui.city.setText("");ui.query.setText("");ui.submit.performClick();});
   n+=check(ui.city.getError()!=null,"empty city rejected");
   in.runOnMainSync(()->{ui.city.setText("杭州");ui.submit.performClick();});
   n+=check(ui.query.getError()!=null,"empty query rejected");
   n+=check(ui.submit.isEnabled()&&ui.copy.getVisibility()==View.GONE,"validation leaves idle with no copy action");
   Reply success=reply(replies,all,200,new JSONObject().put("content",CONTENT).toString());
   in.runOnMainSync(()->{ui.city.setText(" 杭州 ");ui.query.setText(" 住宿参考 ");ui.submit.performClick();});entered(success);
   n+=check(!ui.submit.isEnabled()&&!ui.city.isEnabled()&&!ui.query.isEnabled(),"pending request disables editable controls");
   n+=check(ui.cancel.getVisibility()==View.VISIBLE,"pending request exposes cancellation");
   n+=check(success.request.url().toString().equals("https://license.zjm0929.cn/v1/travel/query"),"request uses fixed service URL");
   Buffer buffer=new Buffer();success.request.body().writeTo(buffer);JSONObject body=new JSONObject(buffer.readUtf8());
   n+=check(body.length()==5&&body.getString("city").equals("杭州")&&body.getString("query").equals("住宿参考"),"only trimmed query and device proof fields sent");
   n+=check(body.getString("licenseId").equals("test-license")&&body.getString("deviceId").equals("test-device")&&body.getString("deviceSecret").equals("test-proof"),"device credentials retained");
   n+=check(!body.has("developerAuthorization")&&success.request.header("Authorization")==null,"developer credential never forwarded");
   finish(in,success);
   n+=check(ui.result.getText().toString().equals(CONTENT),"complete content and price placeholder preserved verbatim");
   n+=check(ui.result.getText() instanceof Spanned&&((Spanned)ui.result.getText()).getSpans(0,CONTENT.length(),ClickableSpan.class).length==1,"HTTPS result link is actionable");
   n+=check(ui.copy.getVisibility()==View.VISIBLE&&ui.submit.isEnabled()&&ui.cancel.getVisibility()==View.GONE,"successful query restores controls and enables copy");
   final String[] copied={null};in.runOnMainSync(()->{ui.copy.performClick();ClipboardManager cb=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);copied[0]=cb.getPrimaryClip()==null?null:cb.getPrimaryClip().getItemAt(0).coerceToText(a).toString();});
   n+=check(CONTENT.equals(copied[0]),"copy includes full original text and URL");
   Reply old=reply(replies,all,200,new JSONObject().put("content","过期结果").toString());
   in.runOnMainSync(()->ui.submit.performClick());entered(old);
   in.runOnMainSync(()->ui.cancel.performClick());
   n+=check(old.call.isCanceled(),"cancel aborts transport call");
   n+=check(ui.submit.isEnabled()&&ui.city.isEnabled()&&ui.query.isEnabled(),"cancel restores all controls");
   n+=check(CONTENT.equals(ui.result.getText().toString())&&ui.status.getText().toString().contains("保留上次结果"),"cancel preserves prior result");
   Reply second=reply(replies,all,200,new JSONObject().put("content","第二次查询结果").toString());
   in.runOnMainSync(()->{ui.query.setText("新需求");ui.submit.performClick();});entered(second);
   finish(in,old);
   n+=check(!ui.submit.isEnabled()&&ui.status.getText().toString().contains("正在查询"),"late canceled callback cannot reset newer request state");
   n+=check(CONTENT.equals(ui.result.getText().toString()),"late callback cannot overwrite content");
   finish(in,second);
   n+=check("第二次查询结果".equals(ui.result.getText().toString()),"new request completes after stale callback");
   Reply failure=reply(replies,all,429,"{\"error\":{\"code\":\"TRAVEL_LIMIT_REACHED\"}}");
   in.runOnMainSync(()->ui.submit.performClick());entered(failure);finish(in,failure);
   n+=check(ui.status.getText().toString().contains("额度")&&ui.status.getText().toString().contains("保留上次结果"),"rate limit gives actionable failure with retained-result notice");
   n+=check("第二次查询结果".equals(ui.result.getText().toString())&&ui.copy.getVisibility()==View.VISIBLE,"failure preserves prior content and copy action");
   Reply dismissed=reply(replies,all,200,new JSONObject().put("content","已关闭页面结果").toString());
   in.runOnMainSync(()->ui.submit.performClick());entered(dismissed);
   in.runOnMainSync(()->ui.page.dialog.dismiss());in.waitForIdleSync();
   n+=check(dismissed.call.isCanceled()&&!ui.page.alive(),"dismissal cancels pending request");
   finish(in,dismissed);
   n+=check("第二次查询结果".equals(ui.result.getText().toString()),"dismissed page ignores late response");
   return n;
  } finally {
   for(Reply reply:all)reply.release.countDown();
   MainActivity a=host;if(a!=null)in.runOnMainSync(()->{if(holder[0]!=null)holder[0].page.dialog.dismiss();a.finish();});
   client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();
  }
 }
 private static Reply reply(LinkedBlockingQueue<Reply> queue,java.util.List<Reply> all,int code,String body){Reply r=new Reply(code,body);all.add(r);queue.add(r);return r;}
 private static void entered(Reply reply)throws Exception{if(!reply.entered.await(5,TimeUnit.SECONDS))throw new AssertionError("request did not reach fake transport");}
 private static void finish(Instrumentation in,Reply reply)throws Exception{reply.release.countDown();reply.worker.join(5000);if(reply.worker.isAlive())throw new AssertionError("request worker failed to finish");in.waitForIdleSync();}
 private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
 private static final class Reply {
  final int code;final String body;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  volatile Request request;volatile Call call;volatile Thread worker;
  Reply(int code,String body){this.code=code;this.body=body;}
 }
}
