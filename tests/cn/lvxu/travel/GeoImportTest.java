package cn.lvxu.travel;
import java.net.URI;
public final class GeoImportTest {
    static int checks;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(GeoMath.meters(0,0,0,0)==0,"same point");
        check(Math.abs(GeoMath.meters(0,0,0,1)-111195)<2,"equatorial degree");
        check(Double.isFinite(GeoMath.meters(90,0,-90,180)),"antipodes");
        double[] gcj=GeoMath.gcj(30.25,120.14),wgs=GeoMath.wgs(gcj[0],gcj[1],"GCJ02");
        check(GeoMath.meters(30.25,120.14,wgs[0],wgs[1])<1,"coordinate inverse");
        double[] london=GeoMath.wgs(51.5,-.12,"GCJ02");check(london[0]==51.5&&london[1]==-.12,"outside mainland");
        PlaceImporter.Place p=PlaceImporter.resolve("杭州 https://uri.amap.com/marker?position=120.14,30.25&name=%E8%A5%BF%E6%B9%96&opentime=09%3A00-18%3A00&rating=4.7");
        check(p.name.equals("西湖")&&p.lat==30.25&&p.lon==120.14,"offline coordinate link");check(p.openingHours.equals("09:00-18:00")&&p.rating.equals("4.7"),"share metadata");
        String copied="伊兴面片(五泉山店)\n¥35/人·小吃快餐\n火车站西路788号\n[https://surl.amap.com/OrouvESa5g”](https://surl.amap.com/OrouvESa5g” )";
        URI shortUri=PlaceImporter.extractUrl(copied);check(shortUri.toString().equals("https://surl.amap.com/OrouvESa5g"),"curly quote short link extraction");
        PlaceImporter.Place copiedFields=PlaceImporter.parseShareText(copied);check(copiedFields.name.equals("伊兴面片(五泉山店)")&&copiedFields.address.equals("火车站西路788号"),"copied AMap title and address");
        check(!PlaceImporter.allowed(URI.create("https://amap.com.evil.test/a")),"host suffix spoof");
        check(!PlaceImporter.allowed(URI.create("https://user@amap.com/a")),"userinfo rejection");
        check(!PlaceImporter.allowed(URI.create("https://amap.com:8443/a")),"port rejection");
        PlaceImporter.Place bad=PlaceImporter.parseUrl(URI.create("https://uri.amap.com/marker?position=NaN,91"));check(bad.lat==null,"invalid coordinates");
        PlaceImporter.Place html=new PlaceImporter.Place();PlaceImporter.parseHtml(html,"<title>灵隐寺 - 高德地图</title><script>{\"address\":\"灵隐路\",\"rating\":4.8,\"opentime\":\"08:00-17:00\"}</script>");check(html.name.equals("灵隐寺")&&html.rating.equals("4.8"),"structured page metadata");
        PlaceImporter.Place empty=new PlaceImporter.Place();PlaceImporter.parseHtml(empty,"<title>高德地图</title><p>评分 5 营业时间不详</p>");check(empty.name.isEmpty()&&empty.rating.isEmpty()&&empty.openingHours.isEmpty(),"never invent missing metadata");
        PlaceImporter.Place packed=PlaceImporter.parseUrl(URI.create("https://wb.amap.com/?p=B0FFHWXB6E%2C36.042715%2C103.826299%2C%E4%BC%8A%E5%85%B4%E9%9D%A2%E7%89%87%2C%E7%81%AB%E8%BD%A6%E7%AB%99%E8%A5%BF%E8%B7%AF788%E5%8F%B7"));
        check(packed.poiId.equals("B0FFHWXB6E")&&packed.name.equals("伊兴面片")&&packed.address.equals("火车站西路788号"),"packed share identity and address");
        check(Math.abs(packed.lat-36.042715)<.000001&&Math.abs(packed.lon-103.826299)<.000001,"packed latitude longitude order");
        PlaceImporter.Place rich=new PlaceImporter.Place();PlaceImporter.parseHtml(rich,"<script>{\"business\":{\"rating\":\"4.5\",\"opentime_today\":\"10:30-21:30\"},\"photos\":[{\"url\":\"https://store.is.autonavi.com/first.jpg\"},{\"url\":\"https://store.is.autonavi.com/second.jpg\"}]}</script>");
        check(rich.rating.equals("4.5")&&rich.openingHours.equals("10:30-21:30"),"business detail fields");check(rich.imageUrl.endsWith("first.jpg"),"first photo preserved");
        URI folder=URI.create("https://guinness.autonavi.com/activity/2020CommonLanding/index.html?schema="+java.net.URLEncoder.encode("amapuri://ajx_favorites/folder?data="+java.net.URLEncoder.encode("{\"ugcId\":\"1234567890\",\"forceCustom\":true}","UTF-8"),"UTF-8"));
        check(PlaceImporter.favoriteFolderId(folder).equals("1234567890"),"favorite folder identifier decoded");
        boolean folderRejected=false;try{PlaceImporter.resolve(folder.toString());}catch(java.io.IOException expected){folderRejected=expected.getMessage().contains("收藏夹");}check(folderRejected,"favorite folder never becomes a fake place");
        System.out.println("GeoImportTest: "+checks+" checks passed");
    }
}
