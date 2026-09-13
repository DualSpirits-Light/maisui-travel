package cn.lvxu.travel;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Reads optional public supporter records. No payment or identity data is collected by the app. */
public final class DonationsService {
    public static final String DEFAULT_URL="https://raw.githubusercontent.com/DualSpirits-Light/maisui-travel/main/data/donations.json";
    private final Context context; private final OkHttpClient client; private final String source;
    public DonationsService(Context context){this(context,new OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build(),DEFAULT_URL);}
    DonationsService(Context c,OkHttpClient client,String source){this.context=c.getApplicationContext();this.client=client;this.source=source;}
    public static final class Record { public final String name,date,message; Record(String n,String d,String m){name=n;date=d;message=m;} }
    public List<Record> cached(){try{return parseRecords(read(cacheFile()));}catch(Exception ignored){}try{return parseRecords(readAsset());}catch(Exception ignored){return Collections.emptyList();}}
    public String refresh() throws IOException {
        if(!source.startsWith("https://")) throw new IOException("捐赠记录地址必须使用 HTTPS");
        Request request=new Request.Builder().url(source).header("Accept","application/json").build();
        try(Response response=client.newCall(request).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new IOException("暂时无法读取捐赠记录");
            String body=response.body().string(); parseRecords(body); writeAtomically(body); return body;
        }
    }
    static List<Record> parseRecords(String raw) throws IOException {
        try{
            JSONObject root=new JSONObject(raw); JSONArray rows=root.optJSONArray("records"); if(rows==null)throw new IOException("捐赠记录格式无效");
            ArrayList<Record> result=new ArrayList<>();
            for(int i=0;i<Math.min(rows.length(),200);i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;String name=trim(x.optString("name"),40),date=trim(x.optString("date"),10),message=trim(x.optString("message"),120);if(!name.isEmpty()&&!date.isEmpty())result.add(new Record(name,date,message));}
            result.sort((a,b)->b.date.compareTo(a.date)); return result;
        }catch(Exception e){if(e instanceof IOException)throw (IOException)e;throw new IOException("捐赠记录格式无效");}
    }
    private static String trim(String v,int max){v=v==null?"":v.trim();return v.length()>max?v.substring(0,max):v;}
    private File cacheFile(){return new File(context.getFilesDir(),"donations-cache.json");}
    private String readAsset() throws IOException {try(InputStream in=context.getAssets().open("data/donations.json")){return read(in);}}
    private static String read(File file) throws IOException {if(!file.isFile())throw new FileNotFoundException();try(InputStream in=new FileInputStream(file)){return read(in);}}
    private static String read(InputStream in) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>128*1024)throw new IOException("捐赠记录过大");out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());}
    private void writeAtomically(String value) throws IOException {AtomicFile file=new AtomicFile(cacheFile());FileOutputStream out=null;try{out=file.startWrite();out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
}
