package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.IOException;
import java.util.ArrayList;

/** UI contract for WebDAV editing: validating a draft never mutates saved credentials. */
final class WebDavSettingsUiTest {
    private WebDavSettingsUiTest() {}

    static int run(Instrumentation instrumentation) throws Exception {
        Context context=instrumentation.getTargetContext();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        try {
            return failedCandidateKeepsEditor(instrumentation,activity)
                    + failureBubbleShowsSafeStatusOnly(instrumentation,activity)
                    + successfulCandidateKeepsDrawer(instrumentation,activity)
                    + cancelledCandidateNeverSaves(instrumentation,activity);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static int failureBubbleShowsSafeStatusOnly(Instrumentation in,MainActivity host)throws Exception {
        FakeDav unavailable=new FakeDav();unavailable.failure=new IOException("WebDAV 返回 503：temporary upstream detail");
        SettingsUi settings=new SettingsUi(host,unavailable);openEditor(in,settings);fill(fields(in),"https://dav.example.test/folder/","alice","pass");click(in,"验证并保存");waitFor(in,"503 · 服务暂不可用，请稍后重试");click(in,"取消");
        FakeDav conflict=new FakeDav();conflict.failure=new IOException("WebDAV 返回 409：collection is locked");
        SettingsUi conflictSettings=new SettingsUi(host,conflict);openEditor(in,conflictSettings);fill(fields(in),"https://dav.example.test/folder/","alice","pass");click(in,"验证并保存");waitFor(in,"409 · 请求未成功，请检查服务设置");click(in,"取消");
        FakeDav unknown=new FakeDav();unknown.failure=new IOException("upstream failure at https://private.example.test/token");
        SettingsUi sanitized=new SettingsUi(host,unknown);openEditor(in,sanitized);fill(fields(in),"https://dav.example.test/folder/","alice","pass");click(in,"验证并保存");waitFor(in,"连接失败，请检查网络和 WebDAV 设置");
        int checks=0;checks+=check(find(in,"upstream failure") == null,"unknown exception is not shown to the user");click(in,"取消");return checks;
    }

    private static int failedCandidateKeepsEditor(Instrumentation in,MainActivity host) throws Exception {
        FakeDav fake=new FakeDav(); fake.failure=new IOException("WebDAV 返回 401");
        SettingsUi settings=new SettingsUi(host,fake);
        openEditor(in,settings); AccessibilityNodeInfo[] fields=fields(in); fill(fields,"https://dav.example.test/folder/","alice","wrong-pass"); click(in,"验证并保存");
        waitFor(in,"401 · 未授权，请检查用户名或密码");
        int checks=0;
        checks+=check(editorShowing(in),"failure keeps WebDAV editor open");
        AccessibilityNodeInfo[] after=fields(in);
        checks+=check(after.length>=3&&"https://dav.example.test/folder/".contentEquals(after[0].getText())&&"alice".contentEquals(after[1].getText())&&"wrong-pass".contentEquals(after[2].getText()),"failure retains all entered values");
        checks+=check(fake.configureCalls==0&&!fake.configured,"failed candidate does not save configuration");
        click(in,"取消"); return checks;
    }

    private static int successfulCandidateKeepsDrawer(Instrumentation in,MainActivity host) throws Exception {
        FakeDav fake=new FakeDav(); SettingsUi settings=new SettingsUi(host,fake);
        openEditor(in,settings); fill(fields(in),"https://dav.example.test/folder/","alice","pass"); click(in,"验证并保存");
        waitUntil(in,()->fake.configureCalls==1&&find(in,"已配置：https://dav.example.test/folder/")!=null,"successful connection redraws WebDAV card");
        int checks=0;
        checks+=check(!editorShowing(in),"success closes only WebDAV editor");
        checks+=check(find(in,"配置")!=null&&find(in,"立即上传")!=null&&find(in,"选择云端备份恢复")!=null,"success keeps settings drawer open and shows configured actions");
        click(in,"关闭设置"); return checks;
    }

    private static int cancelledCandidateNeverSaves(Instrumentation in,MainActivity host) throws Exception {
        FakeDav fake=new FakeDav(); fake.waitForRelease=true;
        SettingsUi settings=new SettingsUi(host,fake);
        openEditor(in,settings); fill(fields(in),"https://dav.example.test/folder/","alice","pass"); click(in,"验证并保存");
        waitUntil(in,()->fake.testCalls==1,"candidate starts"); click(in,"取消"); fake.release(); SystemClock.sleep(250);
        int checks=0;
        checks+=check(fake.configureCalls==0&&!fake.configured,"closing an in-flight candidate cannot save it");
        click(in,"关闭设置"); return checks;
    }

    private static void openEditor(Instrumentation in,SettingsUi settings)throws Exception {in.runOnMainSync(settings::open);waitFor(in,"配置 WebDAV");click(in,"配置 WebDAV");waitFor(in,"HTTPS WebDAV 文件夹地址");}
    private static void fill(AccessibilityNodeInfo[] fields,String endpoint,String user,String password)throws Exception {if(fields.length<3)throw new AssertionError("WebDAV editor fields missing");set(fields[0],endpoint);set(fields[1],user);set(fields[2],password);}
    private static AccessibilityNodeInfo[] fields(Instrumentation in){ArrayList<AccessibilityNodeInfo> found=new ArrayList<>();collectEdits(in.getUiAutomation().getRootInActiveWindow(),found);return found.toArray(new AccessibilityNodeInfo[0]);}
    private static boolean editorShowing(Instrumentation in){return find(in,"HTTPS WebDAV 文件夹地址")!=null;}
    private static void set(AccessibilityNodeInfo node,String value){Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments))throw new AssertionError("Cannot set WebDAV editor text");}
    private static void click(Instrumentation in,String label)throws Exception {waitUntil(in,()->{AccessibilityNodeInfo node=find(in,label);return node!=null&&node.isEnabled()&&node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK);},"Cannot click action: "+label);in.waitForIdleSync();}
    private static AccessibilityNodeInfo find(Instrumentation in,String text){return find(in.getUiAutomation().getRootInActiveWindow(),text);}
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text){if(root==null)return null;CharSequence value=root.getText();CharSequence description=root.getContentDescription();if((value!=null&&value.toString().contains(text))||(description!=null&&description.toString().contains(text)))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo hit=find(root.getChild(i),text);if(hit!=null)return hit;}return null;}
    private static void collectEdits(AccessibilityNodeInfo root,ArrayList<AccessibilityNodeInfo> out){if(root==null)return;if(root.isEditable())out.add(root);for(int i=0;i<root.getChildCount();i++)collectEdits(root.getChild(i),out);}
    private static void waitFor(Instrumentation in,String text)throws Exception {waitUntil(in,()->find(in,text)!=null,"missing "+text);}
    private static void waitUntil(Instrumentation in,Condition condition,String failure)throws Exception {for(int i=0;i<50;i++){if(condition.ok())return;SystemClock.sleep(100);}throw new AssertionError(failure);}
    private static int check(boolean value,String label){if(!value)throw new AssertionError(label);return 1;}
    private interface Condition {boolean ok();}

    private static final class FakeDav implements SettingsUi.DavGateway {
        boolean configured,waitForRelease; int testCalls,configureCalls; Exception failure;
        private final Object lock=new Object(); private boolean released;
        public boolean configured(){return configured;} public String endpoint(){return configured?"https://dav.example.test/folder/":"";}
        public void testCandidate(String endpoint,String user,String password)throws Exception {testCalls++; synchronized(lock){while(waitForRelease&&!released)lock.wait();}if(failure!=null)throw failure;}
        public void configure(String endpoint,String user,String password){configureCalls++;configured=true;}
        public void clear(){configured=false;}
        void release(){synchronized(lock){released=true;lock.notifyAll();}}
    }
}
