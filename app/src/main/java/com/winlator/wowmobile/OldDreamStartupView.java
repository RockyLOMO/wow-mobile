package com.winlator.wowmobile;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.winlator.R;
import com.winlator.widget.XServerView;
import java.util.Locale;

/** Old Dream artwork stays above the runtime until two samples contain a real scene. */
final class OldDreamStartupView extends FrameLayout {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final XServerView surface;
    private final Bitmap sample=Bitmap.createBitmap(96,54,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[96*54];
    private final long started=SystemClock.elapsedRealtime();
    private final TextView elapsed, hint, loadingText, percentage;
    private final ProgressBar progress;
    private final Button reveal;
    private boolean closed, finished, inFlight;
    private int stableFrames;
    private final Runnable poll=this::checkFrame;

    OldDreamStartupView(Activity activity, XServerView surface) {
        super(activity);
        this.surface=surface;
        setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        setClickable(true);
        setBackgroundColor(0xff081117);
        ImageView art=new ImageView(activity);
        art.setImageResource(R.drawable.olddream_loading);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(art,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xee051019,0xbb051019,0x33051019}));
        addView(shade,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(42),dp(24),dp(28),dp(100));
        FrameLayout.LayoutParams panelParams=new FrameLayout.LayoutParams(-1,-1);
        addView(panel,panelParams);
        TextView title=text(panel,"旧梦WOW",40,0xffd3b47d);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text(panel,"重返艾泽拉斯 · 再续旧梦",18,0xffe0e8eb);

        LinearLayout loading=new LinearLayout(activity);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams loadingParams=new LinearLayout.LayoutParams(-2,-2);
        loadingParams.topMargin=dp(24);
        panel.addView(loading,loadingParams);
        loadingText=text(loading,"正在启动游戏…",20,Color.WHITE);
        elapsed=text(panel,"已等待 0 秒",14,0xffc5d1d7);
        hint=text(panel,"启动可能需要30–60秒，请稍候。",14,0xffa9bcc7);
        hint.setMaxWidth(dp(360));

        LinearLayout actions=new LinearLayout(activity);
        LinearLayout.LayoutParams actionsParams=new LinearLayout.LayoutParams(-2,-2);
        actionsParams.topMargin=dp(18);
        panel.addView(actions,actionsParams);
        reveal=action(actions,"查看游戏画面");
        reveal.setVisibility(View.GONE);
        reveal.setOnClickListener(v -> finishLoading(false));

        LinearLayout bottom=new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(42),0,dp(42),dp(24));
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        addView(bottom,bottomParams);
        LinearLayout labels=new LinearLayout(activity);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(labels,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=text(labels,"预计启动进度",14,0xffc5d1d7);
        caption.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        percentage=text(labels,"0.00%",14,0xffd3b47d);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(10000);
        progress.setProgressTintList(ColorStateList.valueOf(0xffd3b47d));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66c5d1d7));
        bottom.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        Log.i("OldDreamStartup","Loading overlay shown");
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout parent,String value,int size,int colour) {
        TextView text=new TextView(getContext());
        text.setText(value); text.setTextSize(size); text.setTextColor(colour);
        text.setPadding(0,dp(5),0,dp(5));
        parent.addView(text,new LinearLayout.LayoutParams(-2,-2));
        return text;
    }
    private Button action(LinearLayout parent,String label) {
        Button button=new Button(getContext());
        button.setText(label); button.setTextSize(13); button.setAllCaps(false);
        button.setTextColor(0xffd3b47d);
        GradientDrawable background=new GradientDrawable();
        background.setColor(0x33081017); background.setCornerRadius(dp(6));
        background.setStroke(dp(1),0x99d3b47d);
        button.setBackground(background); button.setPadding(dp(16),dp(4),dp(16),dp(4));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(36));
        params.rightMargin=dp(12);
        parent.addView(button,params);
        return button;
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); handler.post(poll); }
    @Override protected void onDetachedFromWindow() {
        closed=true; handler.removeCallbacks(poll); animate().cancel();
        if (!inFlight && !sample.isRecycled()) sample.recycle();
        super.onDetachedFromWindow();
    }
    private void checkFrame() {
        if (closed || finished || inFlight) return;
        long waited=SystemClock.elapsedRealtime()-started;
        long seconds=waited/1000;
        elapsed.setText(String.format(Locale.CHINA,"已等待 %d 秒",seconds));
        int estimated=StartupProgress.estimate(waited);
        progress.setProgress(estimated,true);
        percentage.setText(String.format(Locale.CHINA,"%.2f%%",estimated/100.0));
        if (seconds>=60) {
            hint.setText("加载耗时较久，仍在等待游戏画面。\n可继续等待，或查看游戏画面确认状态。");
            reveal.setVisibility(View.VISIBLE);
        }
        if (!surface.getHolder().getSurface().isValid()) { handler.postDelayed(poll,750); return; }
        inFlight=true;
        try {
            PixelCopy.request(surface,sample,result -> {
                inFlight=false;
                if (closed) { if (!sample.isRecycled()) sample.recycle(); return; }
                if (finished) return;
                boolean ready=false;
                if (result==PixelCopy.SUCCESS) {
                    sample.getPixels(pixels,0,96,0,0,96,54);
                    ready=StartupFrameReadiness.hasScene(pixels);
                }
                stableFrames=ready ? stableFrames+1 : 0;
                if (stableFrames>=2) finishLoading(true);
                else handler.postDelayed(poll,750);
            },handler);
        } catch (IllegalArgumentException unavailable) {
            inFlight=false; stableFrames=0; handler.postDelayed(poll,750);
        }
    }
    private void finishLoading(boolean ready) {
        if (closed || finished) return;
        finished=true; handler.removeCallbacks(poll);
        if (ready) {
            progress.setProgress(10000,true); percentage.setText("100.00%");
            loadingText.setText("加载完成，即将进入游戏…");
        }
        Log.i("OldDreamStartup","Loading overlay dismissed after "+(SystemClock.elapsedRealtime()-started)+"ms; realFrame="+ready);
        animate().alpha(0f).setStartDelay(ready ? 450 : 0).setDuration(200).withEndAction(() -> {
            if (getParent() instanceof ViewGroup) ((ViewGroup)getParent()).removeView(this);
        }).start();
    }
}
