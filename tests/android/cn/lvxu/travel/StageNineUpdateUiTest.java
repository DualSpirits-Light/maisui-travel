package cn.lvxu.travel;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

/** Checks the route controls and compact live-download copy on a real Android window. */
final class StageNineUpdateUiTest {
    private StageNineUpdateUiTest() {}

    static int run(Instrumentation instrumentation) throws Exception {
        int checks = 0;
        String progress = UpdateUi.progressText(1_024L, 2_048L);
        if (!"50% · 1.0 KB / 2.0 KB".equals(progress)) {
            throw new AssertionError("progress must include an exact percentage and both byte counts: " + progress);
        }
        checks++;
        String unknownTotal = UpdateUi.progressText(1_536L, 0L);
        if (!"已下载 1.5 KB".equals(unknownTotal)) {
            throw new AssertionError("unknown totals must still show the downloaded byte count: " + unknownTotal);
        }
        checks++;
        MainActivity host = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        UpdateService.Release release = new UpdateService.Release(UpdateService.State.AVAILABLE, 9009, "9.0.0", "修复行程同步，并改善夜间阅读体验。",
            "https://github.com/DualSpirits-Light/maisui-travel/releases/download/v9/maisui.apk", "https://license.zjm0929.cn/updates/maisui.apk", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        try {
            instrumentation.runOnMainSync(() -> host.updateUi.show(release));
            AccessibilityNodeInfo title = waitNode(instrumentation, "有新的更新");
            if (title == null) throw new AssertionError("update panel must identify an available update");
            checks++;
            AccessibilityNodeInfo github = waitNode(instrumentation, "GitHub（加速），已选中");
            if (github == null) throw new AssertionError("GitHub must be the initially selected route when available");
            checks++;
            AccessibilityNodeInfo cloudflare = waitNode(instrumentation, "Cloudflare");
            cloudflare.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (waitNode(instrumentation, "Cloudflare，已选中") == null) throw new AssertionError("tapping a route must visibly select it");
            checks++;
            if (waitNode(instrumentation, "立即更新") == null || waitNode(instrumentation, "后台下载") == null || waitNode(instrumentation, "跳过此版本") == null) {
                throw new AssertionError("initial panel must offer foreground, background, and skip actions");
            }
            checks++;
            android.os.SystemClock.sleep(250);
            Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            if(screenshot!=null){
                File output=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"stage9-update-ui.png");
                try(FileOutputStream file=new FileOutputStream(output)){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}
                screenshot.recycle();
            }
        } finally {
            instrumentation.runOnMainSync(() -> { host.updateUi.dismiss(); host.finish(); });
        }
        return checks;
    }

    private static AccessibilityNodeInfo waitNode(Instrumentation instrumentation, String label) {
        for (int attempt = 0; attempt < 60; attempt++) {
            AccessibilityNodeInfo result = find(instrumentation.getUiAutomation().getRootInActiveWindow(), label);
            if (result != null) return result;
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Missing update UI item: " + label);
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (label.contentEquals(node.getText() == null ? "" : node.getText()) || label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo result = find(node.getChild(index), label);
            if (result != null) return result;
        }
        return null;
    }
}
