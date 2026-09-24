package cn.lvxu.travel;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Bounded HTTPS only; redirects disabled so authentication cannot cross origins. */
final class ApiHttp {
 static final class ServiceException extends IOException {ServiceException(String message){super(message);}}
 static final OkHttpClient CLIENT=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
 static HttpUrl url(String value){HttpUrl u=HttpUrl.parse(value);if(u==null||!u.isHttps()||!u.username().isEmpty()||!u.password().isEmpty())throw new IllegalArgumentException("请输入有效的 HTTPS 地址");return u;}
 static JSONObject json(Request request)throws Exception {
  try(Response r=CLIENT.newCall(request).execute()){
   if(!r.isSuccessful())throw new ServiceException(httpError(r.code()));
   if(r.body()==null)throw new ServiceException("服务返回空内容，请稍后重试");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=r.body().byteStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2*1024*1024)throw new ServiceException("返回内容过大，请缩小请求范围");out.write(buf,0,n);}}
   try{return new JSONObject(out.toString("UTF-8"));}catch(JSONException e){throw new ServiceException("服务未返回有效 JSON，请检查接口地址");}
  }catch(ServiceException e){throw e;}
  catch(java.net.SocketTimeoutException e){throw new IOException("请求超时，请检查网络或减少生成内容后重试");}
  catch(java.net.UnknownHostException e){throw new IOException("无法解析服务地址，请检查网络和接口地址");}
  catch(javax.net.ssl.SSLException e){throw new IOException("HTTPS 安全连接失败，请检查系统时间或网络证书");}
  catch(java.net.ConnectException e){throw new IOException("无法连接服务，请检查网络或稍后重试");}
  catch(java.io.InterruptedIOException e){throw new IOException("请求超时或已取消，请重试");}
  catch(IOException e){throw new IOException("网络读取失败，请检查连接后重试");}
 }
 static String httpError(int code){String reason;switch(code){case 400:reason="请求参数无效，请检查模型名称、协议和接口地址";break;case 401:reason="认证失败，请检查密钥是否有效，以及国内或海外接入地址是否匹配";break;case 402:reason="服务额度或余额不足，请检查套餐状态";break;case 403:reason="没有访问权限，请检查服务是否开通及密钥限制";break;case 404:reason="接口或模型不存在，请检查基础地址和模型名称";break;case 408:case 504:reason="服务响应超时，请稍后重试";break;case 413:reason="请求内容过大，请缩短输入";break;case 429:reason="请求频率或套餐额度达到限制，请稍后重试";break;default:reason=code>=300&&code<400?"接口发生跳转，请使用服务商的正式接入地址":code>=500?"服务暂时不可用，请稍后重试":"服务拒绝请求，请检查配置";}return "HTTP "+code+"："+reason;}
 static Request post(String url,JSONObject body,String header,String token){Request.Builder b=new Request.Builder().url(url(url)).post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body.toString()));if(!token.isEmpty())b.header(header,token);return b.build();}
}
