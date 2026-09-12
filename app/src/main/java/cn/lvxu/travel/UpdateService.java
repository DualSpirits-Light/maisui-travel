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

/** Checks signed release metadata, downloads an APK, and opens Android's installer. */
public final class UpdateService {
    static final String CLOUDFLARE_MANIFEST="https://license.zjm0929.cn/updates/latest.json";
    static final String GITHUB_LATEST_RELEASE="https://api.github.com/repos/DualSpirits-Light/maisui-travel/releases/latest";
    private static final String PREFS="app-prefs-v2",LAST_DAILY_CHECK="lastUpdateDay";
    public enum State { UP_TO_DATE, AVAILABLE, NOT_AVAILABLE }
    public static final class Release {public final State state;public final int versionCode;public final String versionName,notes,apkUrl,sha256;Release(State s,int c,String n,String notes,String url,String hash){state=s;versionCode=c;versionName=n;this.notes=notes;apkUrl=url;sha256=hash;}}
    private final Context context;private final AppPrefs prefs;
    public UpdateService(Context c,AppPrefs p){context=c.getApplicationContext();prefs=p;}
    /**
     * Runs an unrestricted, user-initiated check. The default route uses the
     * Cloudflare manifest first and GitHub only when that endpoint is unavailable.
     * A user-selected HTTPS mirror continues to be queried directly.
     */
    public Release check()throws Exception {String mirror=prefs.updateMirror();if(GITHUB_LATEST_RELEASE.equals(mirror)){try{return checkManifest(CLOUDFLARE_MANIFEST);}catch(Exception cloudflareFailure){try{return checkGitHubRelease(GITHUB_LATEST_RELEASE);}catch(Exception githubFailure){githubFailure.addSuppressed(cloudflareFailure);throw githubFailure;}}}return checkGitHubRelease(mirror);}
    /**
     * Runs at most once for the device's current local calendar day. The day is
     * synchronously saved before any request so failed connections cannot cause a
     * launch-time request loop. A null result means today's automatic check was
     * already performed.
     */
    public Release checkDaily()throws Exception {if(!markDailyCheck(context,java.time.LocalDate.now()))return null;return check();}
    static synchronized boolean markDailyCheck(Context context,java.time.LocalDate day){SharedPreferences p=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);String today=day.toString();if(today.equals(p.getString(LAST_DAILY_CHECK,"")))return false;return p.edit().putString(LAST_DAILY_CHECK,today).commit();}
    private Release checkGitHubRelease(String endpoint)throws Exception {JSONObject release=json(get(https(endpoint),2*1024*1024));JSONArray assets=release.optJSONArray("assets");if(assets==null)throw new FileNotFoundException("发布仓库不可用或没有公开版本");String metadata="";for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if("maisui-update.json".equals(a.optString("name")))metadata=a.getString("browser_download_url");}if(metadata.isEmpty())return new Release(State.NOT_AVAILABLE,0,"","公开版本缺少更新元数据","","");return releaseFromManifest(json(get(https(metadata),1024*1024)));}
    private Release checkManifest(String endpoint)throws Exception{return releaseFromManifest(json(get(https(endpoint),1024*1024)));}
    private Release releaseFromManifest(JSONObject m)throws Exception {int vc=m.getInt("versionCode");String pkg=m.getString("packageName");if(!context.getPackageName().equals(pkg))throw new IOException("更新包标识不匹配");String apk=m.getString("apkUrl"),hash=m.getString("sha256").toLowerCase(Locale.ROOT);if(!hash.matches("[0-9a-f]{64}"))throw new IOException("更新校验值无效");https(apk);int current=currentVersion();State state=vc>current?State.AVAILABLE:State.UP_TO_DATE;return new Release(state,vc,m.optString("versionName",String.valueOf(vc)),m.optString("notes",""),apk,hash);}
    public File downloadAndVerify(Release r)throws Exception {if(r.state!=State.AVAILABLE)throw new IOException("没有可安装的更新");File dir=new File(context.getCacheDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建更新目录");File apk=new File(dir,"maisui-"+r.versionCode+".apk"),part=new File(dir,apk.getName()+".part");HttpURLConnection c=open(https(r.apkUrl));long length=c.getContentLengthLong();if(length<=0||length>256L*1024*1024)throw new IOException("更新包大小无效");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] b=new byte[8192];long total=0;for(int n;(n=in.read(b))!=-1;){total+=n;if(total>256L*1024*1024)throw new IOException("更新包过大");digest.update(b,0,n);out.write(b,0,n);}if(total!=length)throw new IOException("更新包下载不完整");}finally{c.disconnect();}if(!r.sha256.equals(hex(digest.digest()))){part.delete();throw new IOException("更新包校验失败");}verifyPackageAndSigner(part,r.versionCode);if(apk.exists()&&!apk.delete())throw new IOException("无法替换旧更新包");if(!part.renameTo(apk))throw new IOException("无法保存更新包");return apk;}
    /** Opens the system confirmation UI. Installation is never silent. */
    public void requestInstall(File apk){Uri uri=AppFileProvider.uriFor(context,apk);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);}
    private int currentVersion()throws Exception {PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return Build.VERSION.SDK_INT>=28?(int)Math.min(Integer.MAX_VALUE,p.getLongVersionCode()):p.versionCode;}
    private void verifyPackageAndSigner(File apk,int expected)throws Exception {PackageManager pm=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(context.getPackageName(),flags);if(incoming==null||!context.getPackageName().equals(incoming.packageName))throw new IOException("更新包应用标识不匹配");long code=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;if(code!=expected||code<=currentVersion())throw new IOException("更新包版本无效");if(!signers(incoming).equals(signers(current)))throw new IOException("更新包签名与当前应用不一致");}
    private static Set<String> signers(PackageInfo p)throws Exception {Signature[] ss;if(Build.VERSION.SDK_INT>=28){SigningInfo i=p.signingInfo;if(i==null)return Collections.emptySet();ss=i.hasMultipleSigners()?i.getApkContentsSigners():i.getSigningCertificateHistory();}else ss=p.signatures;Set<String> out=new HashSet<>();if(ss!=null)for(Signature s:ss)out.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return out;}
    private static byte[] get(URL u,int max)throws Exception {HttpURLConnection c=open(u);try(InputStream in=c.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] q=new byte[8192];for(int n;(n=in.read(q))!=-1;){if(b.size()+n>max)throw new IOException("服务器响应过大");b.write(q,0,n);}return b.toByteArray();}finally{c.disconnect();}}
    private static HttpURLConnection open(URL first)throws IOException {URL u=first;for(int redirects=0;redirects<=3;redirects++){HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/vnd.github+json, application/json, application/octet-stream");c.setRequestProperty("User-Agent","MaisuiTravel/1");int code=c.getResponseCode();if(code==404){c.disconnect();throw new FileNotFoundException("发布仓库不可用或没有公开版本");}if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("更新服务器重定向无效");try{u=https(new URL(u,location).toString());}catch(Exception e){throw new IOException("更新服务器重定向必须使用 HTTPS",e);}continue;}if(code<200||code>=300){c.disconnect();throw new IOException("更新服务器返回 "+code);}return c;}throw new IOException("更新服务器重定向过多");}
    private static URL https(String s)throws Exception {URL u=new URL(s);if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null)throw new IOException("更新地址必须是 HTTPS");return u;}
    private static JSONObject json(byte[] b)throws Exception{return new JSONObject(new String(b,StandardCharsets.UTF_8));}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
}
