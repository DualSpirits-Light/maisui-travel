package cn.lvxu.travel;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Bounded HTTPS only; redirects disabled so authentication cannot cross origins. */
final class ApiHttp {
 static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
 static HttpUrl url(String value){HttpUrl u=HttpUrl.parse(value);if(u==null||!u.isHttps()||!u.username().isEmpty()||!u.password().isEmpty())throw new IllegalArgumentException("请输入有效的 HTTPS 地址");return u;}
 static JSONObject json(Request request)throws Exception {
  try(Response r=CLIENT.newCall(request).execute()){
   if(!r.isSuccessful())throw new IOException(r.code()==401||r.code()==403?"认证失败，请检查密钥类型、权限或服务是否开通":r.code()==429?"请求过于频繁或额度不足，请稍后重试":"服务暂不可用（HTTP "+r.code()+"）");
   if(r.body()==null)throw new IOException("服务返回空内容");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=r.body().byteStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("返回内容过大，请缩小请求范围");out.write(buf,0,n);}}
   try{return new JSONObject(out.toString("UTF-8"));}catch(JSONException e){throw new IOException("服务未返回有效的数据，请检查接口地址");}
  }catch(java.net.SocketTimeoutException e){throw new IOException("连接超时，请稍后重试");}catch(java.net.UnknownHostException e){throw new IOException("网络不可用，请检查连接");}
 }
 static Request post(String url,JSONObject body,String header,String token){Request.Builder b=new Request.Builder().url(url(url)).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString()));if(!token.isEmpty())b.header(header,token);return b.build();}
}
