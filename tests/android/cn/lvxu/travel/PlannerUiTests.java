package cn.lvxu.travel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.app.UiAutomation;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** UI regressions for the quick-planning dialog flow. Run from IntegrationInstrumentation. */
public final class PlannerUiTests {
    private PlannerUiTests() {}

    public static int run(Instrumentation instrumentation) throws Exception {
        Counter checks=new Counter();MainActivity activity=null;
        try{
            Context context=instrumentation.getTargetContext();
            AppPrefs prefs=new AppPrefs(context);prefs.setTutorialDone(true);prefs.setLastUpdateDay(LocalDate.now().toString());
            Activity started=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));
            activity=(MainActivity)started;
            UiAutomation ui=instrumentation.getUiAutomation();
            instrumentation.waitForIdleSync();
            QuickPlanner[] planner=new QuickPlanner[1];MainActivity host=activity;
            instrumentation.runOnMainSync(()->{planner[0]=new QuickPlanner(host);planner[0].start();});
            await(ui,"快速规划 · 1/4",true);
            AlertDialog wizard=dialogOf(planner[0]);

            AccessibilityNodeInfo firstPrevious=await(ui,"上一步",false);
            checks.that(firstPrevious==null||!firstPrevious.isVisibleToUser(),"planner first page hides previous");
            AccessibilityNodeInfo cancel=await(ui,"取消",true),next=await(ui,"下一步",true);
            checks.that(left(cancel)<left(next),"planner first-page cancel precedes next");
            ArrayList<AccessibilityNodeInfo> initialFields=editFields(ui.getRootInActiveWindow());
            checks.that(initialFields.size()>=2,"planner destination page has destination and date fields");
            setText(initialFields.get(0),"苏州");
            checks.that(existsDescription(ui,"选择日期"),"planner date field has calendar button");
            click(awaitDescription(ui,"选择日期",true));
            checks.that(awaitClass(ui,"DatePicker",true)!=null,"calendar button opens date picker");
            click(awaitId(ui,"android:id/button2",true));
            await(ui,"快速规划 · 1/4",true);

            click(await(ui,"下一步",true));
            await(ui,"快速规划 · 2/4",true);
            checks.that(dialogOf(planner[0])==wizard,"planner next reuses its original dialog");
            AccessibilityNodeInfo previous=await(ui,"上一步",true);
            cancel=await(ui,"取消",true);next=await(ui,"下一步",true);
            checks.that(left(previous)<left(cancel)&&left(cancel)<left(next),"planner buttons are previous, cancel, next from left to right");

            click(previous);
            await(ui,"快速规划 · 1/4",true);
            checks.that(dialogOf(planner[0])==wizard,"planner previous reuses its original dialog");
            initialFields=editFields(ui.getRootInActiveWindow());
            checks.that(initialFields.size()>=2&&"苏州".contentEquals(initialFields.get(0).getText()),"planner preserves draft values after previous");
            click(await(ui,"下一步",true));
            await(ui,"快速规划 · 2/4",true);
            click(await(ui,"上一步",true));
            await(ui,"快速规划 · 1/4",true);
            checks.that(dialogOf(planner[0])==wizard&&wizard.isShowing(),"planner remains one dialog after repeated navigation");

            click(await(ui,"取消",true));
            awaitAbsent(ui,"快速规划 ·");
            checks.that(!wizard.isShowing(),"planner cancel closes the whole wizard");
            return checks.value;
        }finally{
            if(activity!=null){MainActivity closing=activity;instrumentation.runOnMainSync(closing::finish);}
        }
    }

    private static AccessibilityNodeInfo await(UiAutomation ui,String text,boolean required) throws Exception {
        for(int attempts=0;attempts<30;attempts++){
            AccessibilityNodeInfo node=findText(ui.getRootInActiveWindow(),text);
            if(node!=null)return node;
            Thread.sleep(50);
        }
        if(required)throw new AssertionError("Missing UI text: "+text);
        return null;
    }
    private static AccessibilityNodeInfo awaitDescription(UiAutomation ui,String description,boolean required) throws Exception {
        for(int attempts=0;attempts<30;attempts++){
            AccessibilityNodeInfo node=findDescription(ui.getRootInActiveWindow(),description);
            if(node!=null)return node;
            Thread.sleep(50);
        }
        if(required)throw new AssertionError("Missing UI description: "+description);
        return null;
    }
    private static AccessibilityNodeInfo awaitClass(UiAutomation ui,String className,boolean required) throws Exception {
        for(int attempts=0;attempts<30;attempts++){
            AccessibilityNodeInfo node=findClass(ui.getRootInActiveWindow(),className);
            if(node!=null)return node;
            Thread.sleep(50);
        }
        if(required)throw new AssertionError("Missing UI class: "+className);
        return null;
    }
    private static AccessibilityNodeInfo awaitId(UiAutomation ui,String id,boolean required) throws Exception {
        for(int attempts=0;attempts<30;attempts++){
            AccessibilityNodeInfo node=findId(ui.getRootInActiveWindow(),id);
            if(node!=null)return node;
            Thread.sleep(50);
        }
        if(required)throw new AssertionError("Missing UI id: "+id);
        return null;
    }
    private static void awaitAbsent(UiAutomation ui,String text) throws Exception {
        for(int attempts=0;attempts<30;attempts++){
            if(findText(ui.getRootInActiveWindow(),text)==null)return;
            Thread.sleep(50);
        }
        throw new AssertionError("Stale UI text after dismissal: "+text);
    }
    private static boolean existsDescription(UiAutomation ui,String description){return findDescription(ui.getRootInActiveWindow(),description)!=null;}
    private static AccessibilityNodeInfo findText(AccessibilityNodeInfo root,String value){
        if(root==null)return null;
        CharSequence text=root.getText();if(text!=null&&text.toString().contains(value))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findText(root.getChild(i),value);if(found!=null)return found;}
        return null;
    }
    private static AccessibilityNodeInfo findDescription(AccessibilityNodeInfo root,String value){
        if(root==null)return null;
        CharSequence description=root.getContentDescription();if(description!=null&&description.toString().contains(value))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findDescription(root.getChild(i),value);if(found!=null)return found;}
        return null;
    }
    private static AccessibilityNodeInfo findClass(AccessibilityNodeInfo root,String value){
        if(root==null)return null;
        CharSequence type=root.getClassName();if(type!=null&&type.toString().contains(value))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findClass(root.getChild(i),value);if(found!=null)return found;}
        return null;
    }
    private static AccessibilityNodeInfo findId(AccessibilityNodeInfo root,String value){
        if(root==null)return null;
        if(value.equals(root.getViewIdResourceName()))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=findId(root.getChild(i),value);if(found!=null)return found;}
        return null;
    }
    private static ArrayList<AccessibilityNodeInfo> editFields(AccessibilityNodeInfo root){ArrayList<AccessibilityNodeInfo> fields=new ArrayList<>();collectEdits(root,fields);return fields;}
    private static void collectEdits(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> fields){if(root==null)return;if("android.widget.EditText".contentEquals(root.getClassName()))fields.add(root);for(int i=0;i<root.getChildCount();i++)collectEdits(root.getChild(i),fields);}
    private static int left(AccessibilityNodeInfo node){Rect bounds=new Rect();node.getBoundsInScreen(bounds);return bounds.left;}
    private static void click(AccessibilityNodeInfo node){if(node==null||!node.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("UI click failed");}
    private static void setText(AccessibilityNodeInfo node,String value){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);if(!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Unable to set field text");}
    private static AlertDialog dialogOf(QuickPlanner planner) throws Exception {Field field=QuickPlanner.class.getDeclaredField("dialog");field.setAccessible(true);return (AlertDialog)field.get(planner);}
    private static final class Counter {int value;void that(boolean condition,String label){if(!condition)throw new AssertionError(label);value++;}}
}
