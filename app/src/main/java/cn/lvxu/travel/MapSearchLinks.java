package cn.lvxu.travel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** App-first, browser-second place-search links. This class deliberately has no Android dependency. */
final class MapSearchLinks {
    static final class Links {
        final String packageName;
        final String appUri;
        final String webUri;
        Links(String packageName, String appUri, String webUri) {
            this.packageName = packageName;
            this.appUri = appUri;
            this.webUri = webUri;
        }
    }

    private MapSearchLinks() { }

    static Links forPlace(String service, String placeName) {
        String query = encode(placeName == null ? "" : placeName.trim());
        if ("tencent".equals(service)) return new Links("com.tencent.map",
                "qqmap://map/search?keyword=" + query + "&referer=maisui",
                "https://map.qq.com/search?keyword=" + query);
        if ("baidu".equals(service)) return new Links("com.baidu.BaiduMap",
                "baidumap://map/place/search?query=" + query + "&src=maisui",
                "https://map.baidu.com/search/" + query);
        return new Links("com.autonavi.minimap",
                "androidamap://poi?sourceApplication=maisui&keywords=" + query + "&dev=0",
                "https://uri.amap.com/search?keyword=" + query + "&view=map");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
