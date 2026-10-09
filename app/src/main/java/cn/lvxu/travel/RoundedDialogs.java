package cn.lvxu.travel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

/** AlertDialog builder that follows the currently selected app palette. */
public final class RoundedDialogs {
    private RoundedDialogs() {}

    public static class Builder extends AlertDialog.Builder {
        private boolean selection;

        public Builder(Context context) {
            super(context);
        }

        public Builder(Context context, int themeResId) {
            super(context, themeResId);
        }

        /** Marks a custom-content dialog as a compact top selection window. */
        public Builder asSelection() {
            selection = true;
            return this;
        }

        @Override public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            selection = true;
            super.setItems(items, listener);
            return this;
        }

        @Override public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem, DialogInterface.OnClickListener listener) {
            selection = true;
            super.setSingleChoiceItems(items, checkedItem, listener);
            return this;
        }

        @Override public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checkedItems, DialogInterface.OnMultiChoiceClickListener listener) {
            selection = true;
            super.setMultiChoiceItems(items, checkedItems, listener);
            return this;
        }

        @Override
        public AlertDialog create() {
            final AlertDialog dialog = super.create();
            if (selection) configureSelectionWindow(dialog);
            final View decor = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
            if (decor != null) {
                // This is deliberately an attach listener instead of setOnShowListener: callers
                // are free to install their own validation or button listeners.
                decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) {
                        view.post(() -> apply(dialog, selection));
                    }

                    @Override public void onViewDetachedFromWindow(View view) {}
                });
                if (decor.isAttachedToWindow()) decor.post(() -> apply(dialog, selection));
            }
            return dialog;
        }
    }

    private static void configureSelectionWindow(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.setWindowAnimations(R.style.SelectionWindowAnimation);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        float density = window.getContext().getResources().getDisplayMetrics().density;
        int screenWidth = window.getContext().getResources().getDisplayMetrics().widthPixels;
        int inset = (int) (12 * density + .5f);
        int maxWidth = (int) (560 * density + .5f);
        // Set this before show so the first animation frame is already correctly sized.
        window.setLayout(Math.max(1, Math.min(screenWidth - inset * 2, maxWidth)), WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.y = (int) (12 * density + .5f);
        window.setAttributes(attributes);
    }

    private static void apply(AlertDialog dialog, boolean selection) {
        Window window = dialog.getWindow();
        // The posted styling callback may run after a rapid dismiss or Activity finish.
        // Window setters notify WindowManager, so never touch a detached dialog.
        if (window == null || !dialog.isShowing() || !window.getDecorView().isAttachedToWindow()) return;

        float radius = window.getContext().getResources().getDisplayMetrics().density * 24f;
        GradientDrawable background = new GradientDrawable();
        background.setColor(MainActivity.SURFACE);
        background.setCornerRadius(radius);
        window.setBackgroundDrawable(background);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (selection) {
            int width = window.getContext().getResources().getDisplayMetrics().widthPixels;
            int height = window.getContext().getResources().getDisplayMetrics().heightPixels;
            float density = window.getContext().getResources().getDisplayMetrics().density;
            int inset = (int) (12 * density + .5f);
            int maxWidth = (int) (560 * density + .5f);
            window.setLayout(Math.max(1, Math.min(width - inset * 2, maxWidth)), WindowManager.LayoutParams.WRAP_CONTENT);
            android.view.WindowInsets insets = window.getDecorView().getRootWindowInsets();
            int topInset = insets == null ? 0 : insets.getSystemWindowInsetTop();
            int bottomInset = insets == null ? 0 : insets.getSystemWindowInsetBottom();
            int maxHeight = Math.max(1, height - topInset - bottomInset - inset * 2);
            if (window.getDecorView().getHeight() > maxHeight) {
                window.setLayout(Math.max(1, Math.min(width - inset * 2, maxWidth)), maxHeight);
            }
        }

        int buttonColor = MainActivity.readable(MainActivity.GREEN, MainActivity.SURFACE);
        ThemeViews.apply(window.getDecorView(), MainActivity.SURFACE);
        // Native ripple masks do not describe the actual transparent button surface.
        setButtonColor(dialog.getButton(AlertDialog.BUTTON_POSITIVE), buttonColor);
        setButtonColor(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), buttonColor);
        setButtonColor(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), buttonColor);
        android.widget.ListView list=dialog.getListView();
        if(list!=null)list.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener(){
            public void onChildViewAdded(View parent,View child){ThemeViews.apply(child,MainActivity.SURFACE);}
            public void onChildViewRemoved(View parent,View child){}
        });
    }

    private static void setButtonColor(Button button, int color) {
        if (button != null) {button.setBackgroundTintList(null);button.setBackgroundColor(android.graphics.Color.TRANSPARENT);button.setTextColor(color);}
    }
}
