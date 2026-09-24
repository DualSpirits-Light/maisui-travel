package cn.lvxu.travel;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import okhttp3.*;

/** Checks trusted release metadata, verifies an APK, and opens Android's installer. */
public final class UpdateService {
    static final String CLOUDFLARE_MANIFEST="https://license.zjm0929.cn/updates/latest.json";
    static final String GITHUB_LATEST_RELEASE="https://api.github.com/repos/DualSpirits-Light/maisui-travel/releases/latest";
    private static final String GH_PROXY_PREFIX="https://gh-proxy.org/";
    private static final String GITHUB_API_PREFIX="/repos/DualSpirits-Light/maisui-travel/";
    private static final String GITHUB_DOWNLOAD_PREFIX="/DualSpirits-Light/maisui-travel/releases/download/";
    private static final String CLOUDFLARE_HOST="license.zjm0929.cn";
    private static final long MAX_APK_BYTES=256L*1024L*1024L;
    private static final String PREFS="app-prefs-v2",LAST_DAILY_CHECK="lastUpdateDay",IGNORED_VERSION="ignoredUpdateVersionCode";

    public enum State { UP_TO_DATE, AVAILABLE, NOT_AVAILABLE }
    public enum Route { GITHUB, CLOUDFLARE }
    public interface Progress { void onProgress(long done,long total); default void onVerifying() {} }

    /** Cancellation can be called by the UI thread while a worker is reading an APK. */
    public static final class Cancellation {
        private volatile boolean cancelled; private volatile HttpURLConnection active;
        public void cancel(){cancelled=true;HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
        public boolean isCancelled(){return cancelled;}
        private void attach(HttpURLConnection connection){active=connection;if(cancelled)connection.disconnect();}
        private void detach(HttpURLConnection connection){if(active==connection)active=null;}
    }

    /** Kept for callers that still only know one trusted APK location. */
    public static final class Release {
        public final State state; public final int versionCode;
        public final String versionName,notes,apkUrl,githubApkUrl,cloudflareApkUrl,sha256;
        public Release(State state,int versionCode,String versionName,String notes,String apkUrl,String sha256){this(state,versionCode,versionName,notes,isGitHubDownload(apkUrl)?apkUrl:"",isCloudflareUrl(apkUrl)?apkUrl:"",sha256,apkUrl);}
        /** Route-aware release. apkUrl remains a compatibility default for older callers. */
        public Release(State state,int versionCode,String versionName,String notes,String githubApkUrl,String cloudflareApkUrl,String sha256){this(state,versionCode,versionName,notes,githubApkUrl,cloudflareApkUrl,sha256,!empty(githubApkUrl)?githubApkUrl:cloudflareApkUrl);}
        private Release(State state,int versionCode,String versionName,String notes,String githubApkUrl,String cloudflareApkUrl,String sha256,String apkUrl){this.state=state;this.versionCode=versionCode;this.versionName=nonNull(versionName);this.notes=nonNull(notes);this.githubApkUrl=nonNull(githubApkUrl);this.cloudflareApkUrl=nonNull(cloudflareApkUrl);this.apkUrl=nonNull(apkUrl);this.sha256=nonNull(sha256).toLowerCase(Locale.ROOT);}
        public String url(Route route){String preferred=route==Route.GITHUB?githubApkUrl:cloudflareApkUrl;if(isRouteUrl(route,preferred))return preferred;return isRouteUrl(route,apkUrl)?apkUrl:"";}
        public boolean hasRoute(Route route){return !url(route).isEmpty();}
        private Release withState(State next,String message){return new Release(next,versionCode,versionName,message,githubApkUrl,cloudflareApkUrl,sha256,apkUrl);}
    }

    interface ConnectionFactory { HttpURLConnection open(URL url)throws IOException; }
    private final Context context; private final ConnectionFactory connections;
    public UpdateService(Context context,AppPrefs ignoredPrefs){this(context,ignoredPrefs,new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build());}
    /** Package-visible test seam. It retains the production verifier and HTTPS policy. */
    UpdateService(Context context,AppPrefs ignoredPrefs,OkHttpClient client){this(context,ignoredPrefs,new OkHttpConnectionFactory(client));}
    /** Package-visible deterministic stream seam. It retains the production APK verifier. */
    UpdateService(Context context,AppPrefs ignoredPrefs,ConnectionFactory connections){this.context=context.getApplicationContext();this.connections=connections;}

    /** Cloudflare is primary; GitHub's release metadata is a resilient fallback. */
    public Release check()throws Exception {try{return checkManifest(CLOUDFLARE_MANIFEST);}catch(Exception cloudflareFailure){try{return checkGitHubRelease();}catch(Exception githubFailure){githubFailure.addSuppressed(cloudflareFailure);throw githubFailure;}}}
    /** Automatic checks suppress only the version the person explicitly ignored. */
    public Release checkDaily()throws Exception {if(!markDailyCheck(context,java.time.LocalDate.now()))return null;Release release=check();return release.state==State.AVAILABLE&&isIgnored(release)?release.withState(State.NOT_AVAILABLE,"已忽略此版本"):release;}
    static synchronized boolean markDailyCheck(Context context,java.time.LocalDate day){SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);String today=day.toString();if(today.equals(prefs.getString(LAST_DAILY_CHECK,"")))return false;return prefs.edit().putString(LAST_DAILY_CHECK,today).commit();}
    public boolean isIgnored(Release release){return release!=null&&release.versionCode>0&&context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(IGNORED_VERSION,-1)==release.versionCode;}
    public void ignore(Release release){if(release==null||release.versionCode<=0)throw new IllegalArgumentException("更新版本无效");context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(IGNORED_VERSION,release.versionCode).apply();}

    private Release checkManifest(String endpoint)throws Exception{return releaseFromManifest(json(getBytes(trusted(endpoint,Trust.CLOUDFLARE),1024*1024,null)));}
    private Release checkGitHubRelease()throws Exception {
        JSONObject release;try{release=json(getGitHubBytes(trusted(GITHUB_LATEST_RELEASE,Trust.GITHUB_API),2*1024*1024));}catch(NotFoundException missing){return notAvailable("未找到公开版本");}
        JSONArray assets=release.optJSONArray("assets");if(assets==null)return notAvailable("发布仓库不可用或没有公开版本");String metadata="";
        for(int i=0;i<assets.length();i++){JSONObject asset=assets.optJSONObject(i);if(asset!=null&&"maisui-update.json".equals(asset.optString("name")))metadata=asset.optString("browser_download_url","");}
        if(empty(metadata))return notAvailable("公开版本缺少更新元数据");
        try{return releaseFromManifest(json(getGitHubBytes(trusted(metadata,Trust.GITHUB_DOWNLOAD),1024*1024)));}catch(NotFoundException missing){return notAvailable("公开版本缺少更新元数据");}
    }
    Release releaseFromManifest(JSONObject manifest)throws Exception {
        int versionCode=manifest.getInt("versionCode");if(versionCode<=0)throw new IOException("更新版本无效");if(!context.getPackageName().equals(manifest.getString("packageName")))throw new IOException("更新包标识不匹配");String hash=manifest.getString("sha256").toLowerCase(Locale.ROOT);if(!hash.matches("[0-9a-f]{64}"))throw new IOException("更新校验值无效");
        String legacy=manifest.optString("apkUrl",""),github=manifest.optString("githubApkUrl",""),cloudflare=manifest.optString("cloudflareApkUrl","");
        if(!empty(github)&&!isGitHubDownload(github))throw new IOException("GitHub 更新地址无效");if(!empty(cloudflare)&&!isCloudflareUrl(cloudflare))throw new IOException("Cloudflare 更新地址无效");
        if(!empty(legacy)){if(isGitHubDownload(legacy)&&empty(github))github=legacy;else if(isCloudflareUrl(legacy)&&empty(cloudflare))cloudflare=legacy;else if(!isGitHubDownload(legacy)&&!isCloudflareUrl(legacy))throw new IOException("更新地址不受信任");}
        State state=versionCode>currentVersion()?State.AVAILABLE:State.UP_TO_DATE;if(state==State.AVAILABLE&&empty(github)&&empty(cloudflare))return notAvailable("公开版本没有可用下载地址");return new Release(state,versionCode,manifest.optString("versionName",String.valueOf(versionCode)),manifest.optString("notes",""),github,cloudflare,hash);
    }

    public File downloadAndVerify(Release release)throws Exception{return downloadAndVerify(release,release!=null&&release.hasRoute(Route.CLOUDFLARE)?Route.CLOUDFLARE:Route.GITHUB,null,new Cancellation());}
    public File downloadAndVerify(Release release,Route route,Progress progress,Cancellation cancellation)throws Exception {
        if(release==null||release.state!=State.AVAILABLE)throw new IOException("没有可安装的更新");if(route==null||!release.hasRoute(route))throw new IOException("更新下载地址不可用");if(cancellation==null)cancellation=new Cancellation();checkCancelled(cancellation);
        File directory=new File(context.getCacheDir(),"updates");if(!directory.exists()&&!directory.mkdirs())throw new IOException("无法创建更新目录");File apk=new File(directory,"maisui-"+release.versionCode+".apk"),part=new File(directory,apk.getName()+".part");String direct=release.url(route);
        if(route==Route.GITHUB){try{return downloadOne(release,trusted(githubProxyUrl(direct),Trust.GH_PROXY),apk,part,progress,cancellation);}catch(NetworkFailure|NotFoundException unavailable){checkCancelled(cancellation);return downloadOne(release,trusted(direct,Trust.GITHUB_DOWNLOAD),apk,part,progress,cancellation);}}
        return downloadOne(release,trusted(direct,Trust.CLOUDFLARE),apk,part,progress,cancellation);
    }
    /** Revalidates a READY file after a process restart before it reaches Android's installer. */
    public File verifyDownloaded(File apk,Release release)throws Exception {if(apk==null||!apk.isFile()||apk.length()<=0||apk.length()>MAX_APK_BYTES)throw new IOException("更新包大小无效");if(release==null||!release.sha256.matches("[0-9a-f]{64}"))throw new IOException("更新校验值无效");if(!release.sha256.equals(hashFile(apk)))throw new IOException("更新包校验失败");verifyPackageAndSigner(apk,release);return apk;}
    private File downloadOne(Release release,URL url,File apk,File part,Progress progress,Cancellation cancellation)throws Exception {
        boolean complete=false;HttpURLConnection connection=null;try{
            if(part.exists()&&!part.delete())throw new IOException("无法清理旧更新包");connection=open(url,cancellation);long declared=connection.getContentLengthLong();if(declared>MAX_APK_BYTES)throw new IOException("更新包过大");MessageDigest digest=MessageDigest.getInstance("SHA-256");long done=0;
            try(InputStream input=connection.getInputStream();OutputStream output=new FileOutputStream(part)){byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;){checkCancelled(cancellation);done+=count;if(done>MAX_APK_BYTES)throw new IOException("更新包过大");digest.update(buffer,0,count);output.write(buffer,0,count);if(progress!=null)progress.onProgress(done,declared>=0?declared:-1);}}
            catch(IOException failure){if(cancellation.isCancelled())throw cancelled();throw new NetworkFailure("更新包下载失败",failure);}
            checkCancelled(cancellation);if(declared>=0&&done!=declared)throw new IOException("更新包下载不完整");if(progress!=null)progress.onVerifying();if(!release.sha256.equals(hex(digest.digest())))throw new IOException("更新包校验失败");verifyPackageAndSigner(part,release);if(apk.exists()&&!apk.delete())throw new IOException("无法替换旧更新包");if(!part.renameTo(apk))throw new IOException("无法保存更新包");complete=true;return apk;
        }finally{if(connection!=null){cancellation.detach(connection);connection.disconnect();}if(!complete&&part.exists())part.delete();}
    }
    /** Opens Android's confirmation UI. Installation is never silent. */
    public void requestInstall(File apk){Uri uri=AppFileProvider.uriFor(context,apk);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent);}

    private byte[] getGitHubBytes(URL direct,int maximum)throws Exception {try{return getBytes(trusted(githubProxyUrl(direct.toString()),Trust.GH_PROXY),maximum,null);}catch(NetworkFailure|NotFoundException unavailable){return getBytes(direct,maximum,null);}}
    private byte[] getBytes(URL url,int maximum,Cancellation cancellation)throws Exception {HttpURLConnection connection=null;try{connection=open(url,cancellation);try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;){if(cancellation!=null)checkCancelled(cancellation);if(output.size()+count>maximum)throw new IOException("服务器响应过大");output.write(buffer,0,count);}return output.toByteArray();}catch(IOException failure){if(cancellation!=null&&cancellation.isCancelled())throw cancelled();throw new NetworkFailure("更新服务器连接失败",failure);}}finally{if(connection!=null){if(cancellation!=null)cancellation.detach(connection);connection.disconnect();}}}
    private HttpURLConnection open(URL first,Cancellation cancellation)throws Exception {
        URL url=first;for(int redirects=0;redirects<=3;redirects++){
            checkCancelled(cancellation);HttpURLConnection connection;try{connection=connections.open(url);connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/vnd.github+json, application/json, application/octet-stream");connection.setRequestProperty("User-Agent","MaisuiTravel/1");if(cancellation!=null)cancellation.attach(connection);}catch(IOException failure){if(cancellation!=null&&cancellation.isCancelled())throw cancelled();throw new NetworkFailure("更新服务器连接失败",failure);}
            int code;try{code=connection.getResponseCode();}catch(IOException failure){connection.disconnect();if(cancellation!=null)cancellation.detach(connection);if(cancellation!=null&&cancellation.isCancelled())throw cancelled();throw new NetworkFailure("更新服务器连接失败",failure);}
            if(code==404){connection.disconnect();if(cancellation!=null)cancellation.detach(connection);throw new NotFoundException();}
            if(code==301||code==302||code==303||code==307||code==308){String location=connection.getHeaderField("Location");connection.disconnect();if(cancellation!=null)cancellation.detach(connection);if(location==null)throw new IOException("更新服务器重定向无效");url=redirect(first,new URL(url,location));continue;}
            if(code>=500||code==403||code==408||code==429){connection.disconnect();if(cancellation!=null)cancellation.detach(connection);throw new NetworkFailure("更新服务器暂时不可用（"+code+"）",null);}if(code<200||code>=300){connection.disconnect();if(cancellation!=null)cancellation.detach(connection);throw new IOException("更新服务器返回 "+code);}return connection;
        }throw new IOException("更新服务器重定向过多");
    }
    private void verifyPackageAndSigner(File apk,Release release)throws Exception {PackageManager manager=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo incoming=manager.getPackageArchiveInfo(apk.getAbsolutePath(),flags),installed=manager.getPackageInfo(context.getPackageName(),flags);if(incoming==null||!context.getPackageName().equals(incoming.packageName))throw new IOException("更新包应用标识不匹配");long version=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;if(version!=release.versionCode||version<=currentVersion())throw new IOException("更新包版本无效");Set<String> incomingSigners=signers(incoming),installedSigners=signers(installed);if(incomingSigners.isEmpty()||installedSigners.isEmpty()||!incomingSigners.equals(installedSigners))throw new IOException("更新包签名与当前应用不一致");}
    private int currentVersion()throws Exception {PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return Build.VERSION.SDK_INT>=28?(int)Math.min(Integer.MAX_VALUE,info.getLongVersionCode()):info.versionCode;}
    private static Set<String> signers(PackageInfo info)throws Exception {Signature[] signatures;if(Build.VERSION.SDK_INT>=28){SigningInfo signing=info.signingInfo;if(signing==null)return Collections.emptySet();signatures=signing.hasMultipleSigners()?signing.getApkContentsSigners():signing.getSigningCertificateHistory();}else signatures=info.signatures;Set<String> values=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)if(signature!=null)values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return values;}
    private static String hashFile(File file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[8192];for(int count;(count=input.read(buffer))!=-1;){total+=count;if(total>MAX_APK_BYTES)throw new IOException("更新包过大");digest.update(buffer,0,count);}}if(total==0)throw new IOException("更新包大小无效");return hex(digest.digest());}
    private static JSONObject json(byte[] bytes)throws Exception{return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
    private static Release notAvailable(String notes){return new Release(State.NOT_AVAILABLE,0,"",notes,"","");}
    static String githubProxyUrl(String target){return GH_PROXY_PREFIX+target;}
    private enum Trust { CLOUDFLARE,GITHUB_API,GITHUB_DOWNLOAD,GH_PROXY }
    private static URL trusted(String value,Trust trust)throws Exception {URL url=new URL(value);if(!isTrusted(url,trust))throw new IOException("更新地址不受信任");return url;}
    private static URL redirect(URL initial,URL target)throws Exception {
        Trust trust=trustOf(initial);
        if(isTrusted(target,trust))return target;
        if(trust==Trust.GH_PROXY){
            String original=initial.toString().substring(GH_PROXY_PREFIX.length());
            if(target.toString().equals(original)&&(isGitHubApi(original)||isGitHubDownload(original)))return target;
        }
        if((trust==Trust.GITHUB_DOWNLOAD||trust==Trust.GH_PROXY)&&isGitHubAssetRedirect(target))return target;
        throw new IOException("更新服务器重定向不受信任");
    }
    private static Trust trustOf(URL url)throws IOException {if(isCloudflareUrl(url.toString()))return Trust.CLOUDFLARE;if(isGitHubDownload(url.toString()))return Trust.GITHUB_DOWNLOAD;if(isGitHubApi(url.toString()))return Trust.GITHUB_API;if(isGitHubProxy(url.toString()))return Trust.GH_PROXY;throw new IOException("更新地址不受信任");}
    private static boolean isTrusted(URL url,Trust trust){return url!=null&&"https".equalsIgnoreCase(url.getProtocol())&&url.getUserInfo()==null&&url.getPort()==-1&&((trust==Trust.CLOUDFLARE&&isCloudflareUrl(url.toString()))||(trust==Trust.GITHUB_API&&isGitHubApi(url.toString()))||(trust==Trust.GITHUB_DOWNLOAD&&isGitHubDownload(url.toString()))||(trust==Trust.GH_PROXY&&isGitHubProxy(url.toString())));}
    private static boolean isRouteUrl(Route route,String value){return route==Route.GITHUB?isGitHubDownload(value):isCloudflareUrl(value);}
    private static boolean isCloudflareUrl(String value){try{URL url=new URL(value);return clean(url)&&CLOUDFLARE_HOST.equalsIgnoreCase(url.getHost())&&url.getPath().startsWith("/updates/");}catch(Exception ignored){return false;}}
    private static boolean isGitHubApi(String value){try{URL url=new URL(value);return clean(url)&&"api.github.com".equalsIgnoreCase(url.getHost())&&url.getPath().startsWith(GITHUB_API_PREFIX);}catch(Exception ignored){return false;}}
    private static boolean isGitHubDownload(String value){try{URL url=new URL(value);return clean(url)&&"github.com".equalsIgnoreCase(url.getHost())&&url.getPath().startsWith(GITHUB_DOWNLOAD_PREFIX);}catch(Exception ignored){return false;}}
    private static boolean isGitHubProxy(String value){if(value==null||!value.startsWith(GH_PROXY_PREFIX))return false;String target=value.substring(GH_PROXY_PREFIX.length());return isGitHubApi(target)||isGitHubDownload(target);}
    /** GitHub uses short-lived signed URLs here only after a canonical release URL was accepted. */
    private static boolean isGitHubAssetRedirect(URL url){return "https".equalsIgnoreCase(url.getProtocol())&&url.getUserInfo()==null&&url.getPort()==-1&&url.getRef()==null&&"release-assets.githubusercontent.com".equalsIgnoreCase(url.getHost())&&url.getPath()!=null&&!url.getPath().isEmpty();}
    private static boolean clean(URL url){return "https".equalsIgnoreCase(url.getProtocol())&&url.getUserInfo()==null&&url.getPort()==-1&&url.getQuery()==null&&url.getRef()==null;}
    private static boolean empty(String value){return value==null||value.isEmpty();}private static String nonNull(String value){return value==null?"":value;}
    private static String hex(byte[] bytes){StringBuilder output=new StringBuilder();for(byte value:bytes)output.append(String.format(Locale.ROOT,"%02x",value));return output.toString();}
    private static void checkCancelled(Cancellation cancellation)throws InterruptedIOException {if(cancellation!=null&&cancellation.isCancelled())throw cancelled();}private static InterruptedIOException cancelled(){return new InterruptedIOException("更新下载已取消");}
    private static class NetworkFailure extends IOException {NetworkFailure(String message,Throwable cause){super(message,cause);}}private static final class NotFoundException extends IOException {NotFoundException(){super("发布仓库不可用或没有公开版本");}}

    private static final class OkHttpConnectionFactory implements ConnectionFactory {
        private final OkHttpClient client;
        OkHttpConnectionFactory(OkHttpClient client){if(client==null)throw new NullPointerException("client");this.client=client;}
        @Override public HttpURLConnection open(URL url){return new OkHttpConnection(client,url);}
    }
    /** Minimal HttpURLConnection adapter so cancellation calls OkHttp Call.cancel immediately. */
    private static final class OkHttpConnection extends HttpURLConnection {
        private final OkHttpClient client; private final Map<String,String> headers=new LinkedHashMap<>();
        private volatile Call call; private volatile Response response; private volatile boolean disconnected;
        OkHttpConnection(OkHttpClient client,URL url){super(url);this.client=client;}
        @Override public void setRequestProperty(String name,String value){headers.put(name,value);}
        @Override public String getHeaderField(String name)throws IllegalStateException {try{execute();return response.header(name);}catch(IOException failure){return null;}}
        @Override public int getResponseCode()throws IOException {execute();return response.code();}
        @Override public InputStream getInputStream()throws IOException {execute();ResponseBody body=response.body();if(body==null)throw new IOException("更新服务器没有响应内容");return body.byteStream();}
        @Override public long getContentLengthLong(){try{execute();ResponseBody body=response.body();return body==null?-1:body.contentLength();}catch(IOException failure){return -1;}}
        private void execute()throws IOException {
            if(response!=null)return;
            if(disconnected)throw new InterruptedIOException("更新下载已取消");
            Request.Builder request=new Request.Builder().url(url.toString());for(Map.Entry<String,String> entry:headers.entrySet())request.header(entry.getKey(),entry.getValue());
            OkHttpClient scoped=client.newBuilder().connectTimeout(getConnectTimeout(),java.util.concurrent.TimeUnit.MILLISECONDS).readTimeout(getReadTimeout(),java.util.concurrent.TimeUnit.MILLISECONDS).build();
            Call next=scoped.newCall(request.build());call=next;
            if(disconnected){next.cancel();throw new InterruptedIOException("更新下载已取消");}
            Response received=next.execute();
            if(disconnected){received.close();throw new InterruptedIOException("更新下载已取消");}
            response=received;
        }
        @Override public void disconnect(){disconnected=true;Call active=call;if(active!=null)active.cancel();Response received=response;if(received!=null)received.close();}
        @Override public boolean usingProxy(){return false;}
        @Override public void connect()throws IOException {execute();}
    }
}
