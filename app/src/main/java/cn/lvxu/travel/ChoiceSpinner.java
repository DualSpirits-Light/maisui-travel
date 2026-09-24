package cn.lvxu.travel;

import android.app.AlertDialog;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

/** A Spinner whose choices use the app's top selection-window treatment. */
public final class ChoiceSpinner extends Spinner {
    private final MainActivity activity;
    private final String title;
    private AlertDialog choiceDialog;
    private float touchDownX;
    private float touchDownY;
    private boolean tapCandidate;

    public ChoiceSpinner(MainActivity activity, String title) {
        super(activity);
        this.activity = activity;
        this.title = title;
    }

    @Override public boolean performClick() {
        if (choiceDialog != null && choiceDialog.isShowing()) return true;
        SpinnerAdapter adapter = getAdapter();
        if (adapter == null || adapter.getCount() == 0) return true;

        CharSequence[] choices = new CharSequence[adapter.getCount()];
        for (int i = 0; i < choices.length; i++) choices[i] = String.valueOf(adapter.getItem(i));
        choiceDialog = new RoundedDialogs.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(choices, getSelectedItemPosition(), (dialog, which) -> {
                    setSelection(which);
                    dialog.dismiss();
                })
                .create();
        choiceDialog.setOnDismissListener(dialog -> choiceDialog = null);
        choiceDialog.show();
        return true;
    }

    /** Avoid Spinner's forwarding popup path while letting a ScrollView intercept a drag. */
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                tapCandidate = true;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tapCandidate) {
                    float distance = Math.max(Math.abs(event.getX() - touchDownX), Math.abs(event.getY() - touchDownY));
                    if (distance > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        tapCandidate = false;
                        setPressed(false);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean click = tapCandidate;
                tapCandidate = false;
                setPressed(false);
                if (click) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                tapCandidate = false;
                setPressed(false);
                return true;
            default:
                return true;
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (choiceDialog != null) choiceDialog.dismiss();
        super.onDetachedFromWindow();
    }
}
