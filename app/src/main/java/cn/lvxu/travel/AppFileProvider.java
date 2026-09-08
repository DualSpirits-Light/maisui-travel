package cn.lvxu.travel;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Small dependency-free FileProvider limited to cache/camera. */
public final class AppFileProvider extends ContentProvider {
    private File cameraRoot;
    @Override public void attachInfo(Context c,ProviderInfo i){super.attachInfo(c,i);if(i.exported)throw new SecurityException("Provider must not be exported");if(!i.grantUriPermissions)throw new SecurityException("URI grants required");}
    @Override public boolean onCreate(){cameraRoot=new File(getContext().getCacheDir(),"camera");return cameraRoot.exists()||cameraRoot.mkdirs();}
    public static Uri uri(Context c,File f){return uriFor(c,f);}
    /** Grants only app-owned media, camera captures and downloaded update packages. */
    public static Uri uriFor(Context c,File source){try{File f=source.getCanonicalFile(),media=new File(c.getFilesDir(),"media").getCanonicalFile(),camera=new File(c.getCacheDir(),"camera").getCanonicalFile(),updates=new File(c.getCacheDir(),"updates").getCanonicalFile(),shares=new File(c.getCacheDir(),"shares").getCanonicalFile();String kind,relative;if(within(f,media)){kind="media";relative=rel(media,f);}else if(within(f,camera)){kind="camera";relative=rel(camera,f);}else if(within(f,updates)){kind="updates";relative=rel(updates,f);}else if(within(f,shares)){kind="shares";relative=rel(shares,f);}else throw new IllegalArgumentException("File is outside shared app folders");Uri.Builder b=new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").appendPath(kind);for(String part:relative.split("/"))b.appendPath(part);return b.build();}catch(IOException e){throw new IllegalArgumentException("Invalid file",e);}}
    private static boolean within(File f,File root)throws IOException{return f.equals(root)||f.getPath().startsWith(root.getPath()+File.separator);}
    private static String rel(File root,File f){return root.toURI().relativize(f.toURI()).getPath();}
    private File resolve(Uri u)throws FileNotFoundException{if(u==null||!getContext().getPackageName().concat(".files").equals(u.getAuthority())||u.getPathSegments().size()<2)throw new FileNotFoundException();try{String kind=u.getPathSegments().get(0);File root;if("camera".equals(kind))root=cameraRoot;else if("updates".equals(kind))root=new File(getContext().getCacheDir(),"updates");else if("shares".equals(kind))root=new File(getContext().getCacheDir(),"shares");else if("media".equals(kind))root=new File(getContext().getFilesDir(),"media");else throw new FileNotFoundException();File f=root;for(int n=1;n<u.getPathSegments().size();n++)f=new File(f,u.getPathSegments().get(n));f=f.getCanonicalFile();if(!within(f,root.getCanonicalFile()))throw new FileNotFoundException();return f;}catch(IOException e){throw new FileNotFoundException();}}
    @Override public String getType(Uri u){try{String name=resolve(u).getName().toLowerCase(java.util.Locale.ROOT);return name.endsWith(".apk")?"application/vnd.android.package-archive":name.endsWith(".zip")?"application/zip":"image/jpeg";}catch(Exception e){return "application/octet-stream";}}
    @Override public Cursor query(Uri u,String[] projection,String selection,String[] args,String sort){File f;try{f=resolve(u);}catch(Exception e){return null;}String[] p=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(p,1);Object[] row=new Object[p.length];for(int i=0;i<p.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(p[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(p[i]))row[i]=f.length();}c.addRow(row);return c;}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{File f=resolve(u);if(f.isDirectory())throw new FileNotFoundException();int flags;if("r".equals(mode))flags=ParcelFileDescriptor.MODE_READ_ONLY;else if("w".equals(mode)||"wt".equals(mode))flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;else if("wa".equals(mode))flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_APPEND;else if("rw".equals(mode))flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE;else if("rwt".equals(mode))flags=ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE;else throw new FileNotFoundException("Unsupported mode");return ParcelFileDescriptor.open(f,flags);}
    @Override public int delete(Uri u,String s,String[] a){try{return resolve(u).delete()?1:0;}catch(Exception e){return 0;}}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
