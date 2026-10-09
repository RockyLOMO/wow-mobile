package com.winlator.wowmobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.winlator.container.Shortcut;
import com.winlator.widget.XServerView;

/** Small bridges from the upstream runtime into the independent Old Dream frontend. */
public final class OldDreamIntegration {
    private OldDreamIntegration() {}
    public static boolean isOldDream(Shortcut shortcut) {
        return shortcut != null && "1".equals(shortcut.getExtra("oldDream", "0"));
    }
    public static void showStartup(Activity activity, Shortcut shortcut, FrameLayout root, XServerView surface) {
        if (isOldDream(shortcut)) root.addView(new OldDreamStartupView(activity, surface));
    }
    public static void showKeyboardButton(AppCompatActivity activity, Shortcut shortcut, FrameLayout root) {
        if (isOldDream(shortcut)) root.addView(new OldDreamKeyboardButton(activity));
    }
    public static String resolution(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        return WowResolution.forDisplay(metrics.widthPixels, metrics.heightPixels);
    }
    public static boolean returnToLauncher(Activity activity, Shortcut shortcut) {
        if (shortcut == null || !"1".equals(shortcut.getExtra("oldDream", "0"))) return false;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            Intent intent = new Intent(activity, WowMobileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("skip_auto_launch", true);
            activity.startActivity(intent);
            activity.finish();
        });
        return true;
    }
}
