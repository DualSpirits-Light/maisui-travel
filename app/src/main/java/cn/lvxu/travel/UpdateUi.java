package cn.lvxu.travel;

import android.Manifest;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** A compact, stateful update sheet. Download work remains in UpdateDownloads. */
public final class UpdateUi {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 390;

    private final MainActivity activity;
    private final UpdateService updates;
    private final UpdateDownloads downloads;
    private final UpdateDownloads.Listener listener = this::onChanged;
    private Dialog dialog;
    private LinearLayout body;
    private UpdateService.Release shownRelease;
    private UpdateService.Route selectedRoute = UpdateService.Route.GITHUB;
    private boolean observing;
    private boolean closeWhenBackgroundStarts;
    private boolean choosingAfterFailure;
    private UpdateDownloads.Phase renderedPhase;
    private boolean renderedCancelling;
    private ProgressBar renderedProgress;
    private TextView renderedProgressText;

    public UpdateUi(MainActivity activity, UpdateService updates) {
        this.activity = activity;
        this.updates = updates;
        this.downloads = UpdateDownloads.get(activity);
    }

    public void show(UpdateService.Release release) {
        UpdateDownloads.Snapshot snapshot = downloads.snapshot();
        if (release != null) {
            shownRelease = release;
            selectedRoute = defaultRoute(release);
            choosingAfterFailure = false;
        }
        if (snapshot.isActive() && snapshot.release != null) { shownRelease = snapshot.release; choosingAfterFailure = false; }
        if (shownRelease == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (sameRelease(snapshot.release, shownRelease) && snapshot.route != null) selectedRoute = snapshot.route;

        if (dialog != null && dialog.isShowing()) {
            render(snapshot);
            return;
        }
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        body = activity.col();
        body.setPadding(activity.dp(22), activity.dp(20), activity.dp(22), activity.dp(22));
        resetRenderedUi();
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(ignored -> {
            stopObserving();
            if (dialog != null && !dialog.isShowing()) {
                dialog = null;
                body = null;
                resetRenderedUi();
            }
        });
        observe();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.dp(520), activity.getResources().getDisplayMetrics().widthPixels - activity.dp(28));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.max(1, width), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .52f;
            window.setAttributes(attributes);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        render(snapshot);
    }

    /** Allows MainActivity to release this dialog and its observer during destruction. */
    public void dismiss() {
        stopObserving();
        closeWhenBackgroundStarts = false;
        if (dialog != null) dialog.dismiss();
        dialog = null;
        body = null;
        renderedProgress = null;
        renderedProgressText = null;
        renderedPhase = null;
    }

    private void observe() {
        if (observing) return;
        observing = true;
        downloads.observe(listener);
    }

    private void stopObserving() {
        if (!observing) return;
        observing = false;
        downloads.remove(listener);
    }

    private void onChanged(UpdateDownloads.Snapshot snapshot) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            dismiss();
            return;
        }
        if (snapshot.isActive() && snapshot.release != null) { shownRelease = snapshot.release; choosingAfterFailure = false; }
        if (closeWhenBackgroundStarts && snapshot.isActive() && snapshot.background) {
            closeWhenBackgroundStarts = false;
            dismiss();
            return;
        }
        if (dialog != null && dialog.isShowing()) render(snapshot);
    }

    private void render(UpdateDownloads.Snapshot snapshot) {
        if (body == null || shownRelease == null) return;
        boolean snapshotMatches = sameRelease(snapshot.release, shownRelease);
        if (snapshotMatches && snapshot.isActive() && renderedPhase == snapshot.phase && renderedCancelling == snapshot.cancellationRequested && renderedProgress != null && renderedProgressText != null) {
            updateProgress(snapshot);
            return;
        }
        body.removeAllViews();
        renderedProgress = null;
        renderedProgressText = null;
        renderedPhase = snapshotMatches ? snapshot.phase : UpdateDownloads.Phase.IDLE;
        renderedCancelling = snapshotMatches && snapshot.cancellationRequested;
        body.setBackground(activity.shape(MainActivity.SURFACE, 28));
        header(body);
        TextView version = activity.bold("版本 " + shownRelease.versionName, 15, MainActivity.GREEN);
        version.setGravity(Gravity.CENTER);
        version.setPadding(activity.dp(13), activity.dp(7), activity.dp(13), activity.dp(7));
        version.setBackground(activity.shape(MainActivity.PALE, 16));
        body.addView(version, new LinearLayout.LayoutParams(-1, -2));
        activity.space(body, 16);
        notes(body, shownRelease.notes);

        if (snapshotMatches && snapshot.isActive()) {
            downloading(body, snapshot);
        } else if (snapshotMatches && snapshot.phase == UpdateDownloads.Phase.READY) {
            ready(body, snapshot);
        } else if (snapshotMatches && snapshot.phase == UpdateDownloads.Phase.FAILED && !choosingAfterFailure) {
            failed(body, snapshot);
        } else {
            choices(body);
        }
        LinearLayout renderedBody = body;
        renderedBody.post(() -> {
            if (body == renderedBody) ThemeViews.apply(renderedBody, MainActivity.SURFACE);
        });
    }

    private void header(LinearLayout parent) {
        FrameLayout header = new FrameLayout(activity);
        TextView title = activity.bold("有新的更新", 23, MainActivity.INK);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new FrameLayout.LayoutParams(-1, activity.dp(42)));
        TextView close = activity.text("×", 28, MainActivity.MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭更新窗口");
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new FrameLayout.LayoutParams(activity.dp(42), activity.dp(42), Gravity.END));
        parent.addView(header);
        activity.space(parent, 4);
    }

    private void notes(LinearLayout parent, String notes) {
        if (notes == null || notes.trim().isEmpty()) return;
        TextView label = activity.bold("本次更新", 14, MainActivity.INK);
        parent.addView(label);
        activity.space(parent, 7);
        final int maximum = Math.min(activity.dp(164), activity.getResources().getDisplayMetrics().heightPixels / 4);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, android.view.View.MeasureSpec.makeMeasureSpec(maximum, android.view.View.MeasureSpec.AT_MOST));
            }
        };
        TextView text = activity.text(notes.trim(), 14, MainActivity.MUTED);
        text.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        text.setBackground(activity.shape(MainActivity.PALE, 16));
        scroll.addView(text);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        activity.space(parent, 16);
    }

    private void choices(LinearLayout parent) {
        TextView hint = activity.text("选择下载线路", 14, MainActivity.INK);
        parent.addView(hint);
        activity.space(parent, 8);
        LinearLayout routes = activity.row();
        routes.addView(route(UpdateService.Route.GITHUB, "GitHub（加速）"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView cloudflare = route(UpdateService.Route.CLOUDFLARE, "Cloudflare");
        LinearLayout.LayoutParams cloudflareParams = new LinearLayout.LayoutParams(0, -2, 1);
        cloudflareParams.leftMargin = activity.dp(10);
        routes.addView(cloudflare, cloudflareParams);
        parent.addView(routes);
        activity.space(parent, 18);
        TextView download = action("立即更新", true, () -> start(false));
        parent.addView(download, new LinearLayout.LayoutParams(-1, -2));
        activity.space(parent, 10);
        TextView background = action("后台下载", false, () -> start(true));
        parent.addView(background, new LinearLayout.LayoutParams(-1, -2));
        activity.space(parent, 8);
        TextView skip = action("跳过此版本", false, () -> {
            updates.ignore(shownRelease);
            dismiss();
        });
        parent.addView(skip, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView route(UpdateService.Route route, String label) {
        boolean available = shownRelease.hasRoute(route);
        boolean selected = route == selectedRoute && available;
        TextView choice = activity.bold(label, 14, selected ? MainActivity.PRIMARY_TEXT : MainActivity.readable(MainActivity.INK, MainActivity.PALE));
        choice.setGravity(Gravity.CENTER);
        choice.setMinHeight(activity.dp(48));
        choice.setPadding(activity.dp(8), activity.dp(10), activity.dp(8), activity.dp(10));
        choice.setBackground(activity.shape(selected ? MainActivity.GREEN : MainActivity.PALE, 16));
        choice.setContentDescription(label + (selected ? "，已选中" : ""));
        choice.setEnabled(available);
        choice.setAlpha(available ? 1f : .45f);
        if (available) choice.setOnClickListener(v -> {
            selectedRoute = route;
            render(downloads.snapshot());
        });
        return choice;
    }

    private void downloading(LinearLayout parent, UpdateDownloads.Snapshot snapshot) {
        boolean cancelling = snapshot.cancellationRequested;
        String title = cancelling ? "正在取消下载…" : snapshot.phase == UpdateDownloads.Phase.VERIFYING ? "正在验证更新包…" : "正在下载更新…";
        parent.addView(activity.bold(title, 17, MainActivity.INK));
        activity.space(parent, 7);
        TextView detail = activity.text(snapshot.phase == UpdateDownloads.Phase.VERIFYING ? "正在检查文件完整性和签名" : routeName(snapshot.route), 13, MainActivity.MUTED);
        parent.addView(detail);
        activity.space(parent, 12);
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(MainActivity.GREEN));
        progress.setIndeterminateTintList(ColorStateList.valueOf(MainActivity.GREEN));
        progress.setIndeterminate(snapshot.phase == UpdateDownloads.Phase.VERIFYING || snapshot.total <= 0);
        if (!progress.isIndeterminate()) {
            progress.setMax(100);
            progress.setProgress(percent(snapshot.downloaded, snapshot.total));
        }
        parent.addView(progress, new LinearLayout.LayoutParams(-1, activity.dp(8)));
        activity.space(parent, 10);
        TextView bytes = activity.bold(progressText(snapshot.downloaded, snapshot.total), 14, MainActivity.INK);
        bytes.setGravity(Gravity.CENTER);
        parent.addView(bytes, new LinearLayout.LayoutParams(-1, -2));
        renderedProgress = progress;
        renderedProgressText = bytes;
        activity.space(parent, 16);
        if (!cancelling) {
            parent.addView(action("转到后台", false, this::moveToBackground), new LinearLayout.LayoutParams(-1, -2));
            activity.space(parent, 10);
        }
        TextView cancel = action(cancelling ? "正在取消…" : "取消下载", false, downloads::cancel);
        cancel.setEnabled(!cancelling);
        parent.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateProgress(UpdateDownloads.Snapshot snapshot) {
        if (renderedProgress == null || renderedProgressText == null) return;
        renderedProgress.setIndeterminate(snapshot.phase == UpdateDownloads.Phase.VERIFYING || snapshot.total <= 0);
        if (!renderedProgress.isIndeterminate()) {
            renderedProgress.setMax(100);
            renderedProgress.setProgress(percent(snapshot.downloaded, snapshot.total));
        }
        renderedProgressText.setText(progressText(snapshot.downloaded, snapshot.total));
    }

    private void resetRenderedUi() {
        renderedProgress = null;
        renderedProgressText = null;
        renderedPhase = null;
        renderedCancelling = false;
    }

    private void ready(LinearLayout parent, UpdateDownloads.Snapshot snapshot) {
        parent.addView(activity.bold("更新已准备好", 17, MainActivity.INK));
        activity.space(parent, 7);
        parent.addView(activity.text("已完成下载和验证。安装仍需由你在系统窗口中确认。", 14, MainActivity.MUTED));
        activity.space(parent, 18);
        parent.addView(action("立即安装", true, () -> {
            if (snapshot.apk == null) {
                activity.toast("更新文件不可用，请重新下载");
                return;
            }
            updates.requestInstall(snapshot.apk);
        }), new LinearLayout.LayoutParams(-1, -2));
        activity.space(parent, 10);
        parent.addView(action("稍后安装", false, this::dismiss), new LinearLayout.LayoutParams(-1, -2));
    }

    private void failed(LinearLayout parent, UpdateDownloads.Snapshot snapshot) {
        parent.addView(activity.bold("下载未完成", 17, MainActivity.INK));
        activity.space(parent, 7);
        String error = snapshot.error == null || snapshot.error.trim().isEmpty() ? "下载没有完成，请重试或切换线路。" : snapshot.error;
        TextView message = activity.text(error, 14, MainActivity.ORANGE);
        message.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        message.setBackground(activity.shape(MainActivity.PALE, 16));
        parent.addView(message);
        activity.space(parent, 18);
        parent.addView(action("重新下载", true, () -> start(snapshot.background)), new LinearLayout.LayoutParams(-1, -2));
        activity.space(parent, 10);
        parent.addView(action("更换线路", false, () -> {
            UpdateService.Route alternative = otherRoute(selectedRoute);
            if (!shownRelease.hasRoute(alternative)) {
                activity.toast("另一条下载线路暂不可用");
                return;
            }
            selectedRoute = alternative;
            choicesAfterFailure();
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    private void choicesAfterFailure() {
        choosingAfterFailure = true;
        render(downloads.snapshot());
    }

    private void start(boolean background) {
        if (!shownRelease.hasRoute(selectedRoute)) {
            activity.toast("这条下载线路暂不可用，请选择另一条线路");
            return;
        }
        if (background && !UpdateDownloadService.notificationsEnabled(activity)) {
            if (Build.VERSION.SDK_INT >= 33) activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            activity.toast("后台下载需要开启通知；开启后请再次点击，或选择立即更新");
            return;
        }
        try {
            UpdateDownloadService.start(activity, shownRelease, selectedRoute, background);
            closeWhenBackgroundStarts = background;
            choosingAfterFailure = false;
        } catch (IllegalArgumentException | IllegalStateException problem) {
            String message = problem.getMessage();
            activity.toast(message == null || message.isEmpty() ? "暂时无法开始下载" : message);
        }
    }

    private static UpdateService.Route defaultRoute(UpdateService.Release release) {
        return release.hasRoute(UpdateService.Route.GITHUB) ? UpdateService.Route.GITHUB : UpdateService.Route.CLOUDFLARE;
    }

    private void moveToBackground() {
        if (!UpdateDownloadService.notificationsEnabled(activity)) {
            if (Build.VERSION.SDK_INT >= 33) activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
            activity.toast("后台下载需要开启通知；开启后请再次点击，或留在此处查看进度");
            return;
        }
        downloads.moveToBackground();
        dismiss();
    }

    private static boolean sameRelease(UpdateService.Release first, UpdateService.Release second) {
        return first != null && second != null && first.versionCode == second.versionCode && first.sha256.equals(second.sha256);
    }

    private static UpdateService.Route otherRoute(UpdateService.Route route) {
        return route == UpdateService.Route.GITHUB ? UpdateService.Route.CLOUDFLARE : UpdateService.Route.GITHUB;
    }

    private static String routeName(UpdateService.Route route) {
        return route == UpdateService.Route.CLOUDFLARE ? "Cloudflare" : "GitHub（加速）";
    }

    static String progressText(long downloaded, long total) {
        if (total <= 0) return "已下载 " + bytes(downloaded);
        return percent(downloaded, total) + "% · " + bytes(downloaded) + " / " + bytes(total);
    }

    private static int percent(long downloaded, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(downloaded * 100d / total)));
    }

    private static String bytes(long value) {
        double amount = Math.max(0, value);
        if (amount < 1024d) return String.format(Locale.ROOT, "%.0f B", amount);
        if (amount < 1024d * 1024d) return String.format(Locale.ROOT, "%.1f KB", amount / 1024d);
        return String.format(Locale.ROOT, "%.1f MB", amount / (1024d * 1024d));
    }

    private TextView action(String label, boolean primary, Runnable callback) {
        TextView view = activity.bold(label, 15, primary ? MainActivity.PRIMARY_TEXT : MainActivity.readable(MainActivity.INK, MainActivity.PALE));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(activity.dp(50));
        view.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
        view.setBackground(activity.shape(primary ? MainActivity.GREEN : MainActivity.PALE, 17));
        view.setContentDescription(label);
        view.setFocusable(true);
        view.setOnClickListener(ignored -> callback.run());
        return view;
    }
}
