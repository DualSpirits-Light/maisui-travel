package cn.lvxu.travel;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.json.JSONObject;
import okhttp3.*;

final class ShareCodeService {
 static final long MAX_BYTES=32L*1024*1024;
 static final String ENDPOINT="https://license.zjm0929.cn/v1/shares";
 private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followRedirects(false).build();
 static String extract(String value){Matcher m=Pattern.compile("(?i)MS31-[a-f0-9]{32}(?![a-f0-9])").matcher(value==null?"":value);if(!m.find())throw new IllegalArgumentException("请输入完整的麦穗旅行口令（MS31- 开头）");return "MS31-"+m.group().substring(5).toLowerCase(java.util.Locale.ROOT);}
 JSONObject upload(File file)throws Exception {if(file.length()>MAX_BYTES)throw new IOException("口令分享最多 32 MB，请减少照片或选择本地分享");Request req=new Request.Builder().url(ENDPOINT).post(RequestBody.create(file,MediaType.get("application/zip"))).build();try(Response r=client.newCall(req).execute()){if(!r.isSuccessful())throw failure(r);JSONObject result=new JSONObject(r.body().string());extract(result.getString("code"));return result;}}
 void download(String code,File file)throws Exception {Request req=new Request.Builder().url(ENDPOINT+"/"+extract(code)).get().build();try(Response r=client.newCall(req).execute()){if(!r.isSuccessful())throw failure(r);if(r.body()==null||r.body().contentLength()>MAX_BYTES)throw new IOException("分享文件过大");try(InputStream in=r.body().byteStream();OutputStream out=new FileOutputStream(file)){byte[] b=new byte[8192];long size=0;for(int n;(n=in.read(b))!=-1;){size+=n;if(size>MAX_BYTES)throw new IOException("分享文件过大");out.write(b,0,n);}}}catch(Exception e){file.delete();throw e;}}
 private IOException failure(Response r){String message="分享服务暂不可用，请稍后重试";try{if(r.body()!=null){String text=r.body().source().readUtf8( Math.min(4096,Math.max(0,r.body().contentLength())) );JSONObject body=new JSONObject(text);Object error=body.opt("error");if(error instanceof String)message=(String)error;}}catch(Exception ignored){}if(r.code()==410)message="口令不存在或已过期，有效期为 3 天";return new IOException(message);}
}
