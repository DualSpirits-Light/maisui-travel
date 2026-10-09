# Conservative first hardened release: rename application internals, while
# preserving runtime behavior and all vendor JNI/reflection contracts.
# Obfuscation is not encryption and cannot protect embedded credentials.
-dontshrink
-dontoptimize
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions

# Framework-instantiated components and callbacks. No blanket app keep rule.
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.app.Application { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# These binary SDKs already contain obfuscation and use native/reflected names.
-keep class com.amap.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-keep class com.baidu.** { *; }
-keep class vi.com.** { *; }
# Preserve HTTP/Kotlin library metadata and resource-relative lookup names.
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class kotlin.** { *; }
-keep class org.jetbrains.annotations.** { *; }

# Optional AMap integrations absent from the pinned distribution. The app uses
# native MapView, not SupportMapFragment/AMap heat tiles. Do not hide unrelated
# missing-class errors with a global -ignorewarnings or -dontwarn **.
-dontwarn android.support.v4.app.Fragment
-dontwarn android.support.v4.app.FragmentActivity
-dontwarn android.support.v4.util.LongSparseArray
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-dontwarn net.jafama.FastMath
