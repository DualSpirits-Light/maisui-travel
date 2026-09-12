package cn.lvxu.travel;
import android.app.*;import android.content.*;import android.view.accessibility.AccessibilityNodeInfo;import android.graphics.Bitmap;import java.io.*;

final class Sharing031UiTests {
 static int run(Instrumentation in)throws Exception {MainActivity a=(MainActivity)in.startActivitySync(new Intent(in.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));int count=0;UiAutomation ui=in.getUiAutomation();try{
  Trip t=Trip.demo();t.stops.clear();Trip.Stop stop=new Trip.Stop();stop.name="原始地点";stop.address="测试地址";stop.sourceUrl="https://amap.com/place/B001";stop.sourceSnapshot=TripLinkCodec.snapshot(stop);stop.name="我的地点名称";t.stops.add(stop);
  in.runOnMainSync(()->{a.trips.clear();a.trips.add(t);a.active=t;a.tripSharing.share(t);});waitText(ui,"纯净分享（攻略）");waitText(ui,"完整分享（回忆）");count++;capture(in,ui,"031-share-menu.png");click(ui,"纯净分享（攻略）");waitText(ui,"本地分享");waitText(ui,"口令分享");count++;click(ui,"口令分享");waitText(ui,"包含图片（总文件最多 32 MB）");waitText(ui,"口令有效 3 天");count++;capture(in,ui,"031-share-code.png");click(ui,"取消");
  in.runOnMainSync(()->a.tripSharing.pickImport());waitText(ui,"本地文件导入");click(ui,"口令导入");waitText(ui,"粘贴口令或分享文字");click(ui,"取消");count++;
  in.runOnMainSync(()->new SourceLinksUi(a).show(t));waitText(ui,"修改记录");waitText(ui,"原始地点 → 我的地点名称");capture(in,ui,"031-source-links.png");count++;click(ui,"删除此高德链接");waitText(ui,"仅以数据分享");click(ui,"取消");if(stop.sourceUrl.isEmpty())throw new AssertionError("cancel removed source link");count++;click(ui,"删除此高德链接");click(ui,"确定");waitText(ui,"还没有保存的高德链接");if(!stop.sourceUrl.isEmpty()||!stop.sourceSnapshot.isEmpty()||!stop.name.equals("我的地点名称"))throw new AssertionError("link deletion changed place data");count++;click(ui,"关闭");return count;
 }finally{in.runOnMainSync(a::finish);}}
 static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text){if(root==null)return null;CharSequence value=root.getText();if(value!=null&&value.toString().contains(text))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),text);if(found!=null)return found;}return null;}
 static AccessibilityNodeInfo waitText(UiAutomation ui,String text)throws Exception {for(int i=0;i<40;i++){AccessibilityNodeInfo n=find(ui.getRootInActiveWindow(),text);if(n!=null)return n;Thread.sleep(100);}throw new AssertionError("Missing UI: "+text);}
 static void click(UiAutomation ui,String text)throws Exception {if(!waitText(ui,text).performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Cannot click "+text);Thread.sleep(180);}
 static void capture(Instrumentation in,UiAutomation ui,String name)throws Exception {Thread.sleep(500);Bitmap b=ui.takeScreenshot();if(b!=null){try(OutputStream out=new FileOutputStream(new File(in.getTargetContext().getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}}
}
