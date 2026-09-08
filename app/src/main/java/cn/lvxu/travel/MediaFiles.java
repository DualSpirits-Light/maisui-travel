package cn.lvxu.travel;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

/** Confined storage and bounded image decoding for app-owned media. */
public final class MediaFiles {
    private static final int MAX_SOURCE = 24 * 1024 * 1024;
    private final Context context;
    private final File root;

    public MediaFiles(Context context) {
        this.context = context.getApplicationContext();
        root = new File(context.getFilesDir(), "media");
        if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("Cannot create media folder");
    }

    public File root() { return root; }
    public static File file(Context context,String relative){return new MediaFiles(context).file(relative);}

    public File file(String relative) {
        if (relative == null || relative.isEmpty()) return null;
        try {
            File f = new File(context.getFilesDir(), relative).getCanonicalFile();
            String base = root.getCanonicalPath() + File.separator;
            if (!f.getPath().startsWith(base)) throw new IllegalArgumentException("Invalid media path");
            return f;
        } catch (IOException e) { throw new IllegalArgumentException("Invalid media path", e); }
    }

    public String newPath(String group, String suffix) {
        String safe = group.matches("[a-z]+") ? group : "photos";
        File dir = new File(root, safe);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create media folder");
        return "media/" + safe + "/" + UUID.randomUUID() + suffix;
    }

    public Bitmap decode(Uri uri, int maxSide) throws IOException {
        if(uri==null||maxSide<64||maxSide>4096)throw new IOException("Invalid image request");
        byte[] bytes = readBounded(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported image");
        BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        int sample = 1; while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2;
        opts.inSampleSize = sample;
        Bitmap raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (raw == null) throw new IOException("Cannot decode image");
        int orientation = jpegOrientation(bytes);
        Matrix matrix = new Matrix();
        if (orientation == 2) matrix.setScale(-1, 1); else if (orientation == 3) matrix.setRotate(180);
        else if (orientation == 4) { matrix.setRotate(180); matrix.postScale(-1, 1); }
        else if (orientation == 5) { matrix.setRotate(90); matrix.postScale(-1, 1); }
        else if (orientation == 6) matrix.setRotate(90); else if (orientation == 7) { matrix.setRotate(270); matrix.postScale(-1, 1); }
        else if (orientation == 8) matrix.setRotate(270);
        if (!matrix.isIdentity()) { Bitmap fixed=Bitmap.createBitmap(raw,0,0,raw.getWidth(),raw.getHeight(),matrix,true); if(fixed!=raw)raw.recycle(); raw=fixed; }
        int longest=Math.max(raw.getWidth(),raw.getHeight());
        if(longest>maxSide){float scale=maxSide/(float)longest;Bitmap small=Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*scale)),Math.max(1,Math.round(raw.getHeight()*scale)),true);if(small!=raw)raw.recycle();raw=small;}
        return raw;
    }

    public Bitmap decode(String relative, int maxSide) throws IOException { File f=file(relative); if(f==null)return null; return decode(Uri.fromFile(f),maxSide); }

    public String save(Bitmap bitmap, String group, int quality) throws IOException {
        if(bitmap==null||bitmap.isRecycled())throw new IOException("Cannot save empty image");quality=Math.max(50,Math.min(100,quality));
        String relative=newPath(group,".jpg");File target=file(relative), temp=new File(target.getParentFile(),target.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out))throw new IOException("Cannot encode image");out.getFD().sync();}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Cannot finish image");}return relative;
    }

    private byte[] readBounded(Uri uri) throws IOException {
        try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Cannot open image");byte[] block=new byte[16384];int n,total=0;
            while((n=in.read(block))!=-1){total+=n;if(total>MAX_SOURCE)throw new IOException("Image is larger than 24 MB");out.write(block,0,n);}return out.toByteArray();
        }
    }

    /** Minimal JPEG EXIF orientation reader; returns 1 for non-JPEG/malformed metadata. */
    private static int jpegOrientation(byte[] b){
        try{int p=2;while(p+4<b.length&&p<256*1024){if((b[p]&255)!=255)break;int marker=b[p+1]&255;p+=2;if(marker==217||marker==218)break;int len=((b[p]&255)<<8)|(b[p+1]&255);if(len<2||p+len>b.length)break;
            if(marker==225&&len>=10&&b[p+2]=='E'&&b[p+3]=='x'&&b[p+4]=='i'&&b[p+5]=='f'){int t=p+8;boolean le=b[t]=='I';int ifd=t+read32(b,t+4,le);int count=read16(b,ifd,le);for(int i=0;i<count;i++){int e=ifd+2+i*12;if(read16(b,e,le)==0x112){int v=read16(b,e+8,le);return v>=1&&v<=8?v:1;}}}p+=len;}
        }catch(Exception ignored){}return 1;
    }
    private static int read16(byte[] b,int p,boolean le){return le?(b[p]&255)|((b[p+1]&255)<<8):((b[p]&255)<<8)|(b[p+1]&255);}
    private static int read32(byte[] b,int p,boolean le){return le?read16(b,p,true)|(read16(b,p+2,true)<<16):(read16(b,p,false)<<16)|read16(b,p+2,false);}
}
