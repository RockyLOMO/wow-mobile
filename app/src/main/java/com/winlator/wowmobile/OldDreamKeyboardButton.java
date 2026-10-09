package com.winlator.wowmobile;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.winlator.core.AppUtils;

/** Small keyboard shortcut above the scene; doesn't take keyboard focus from Wine. */
final class OldDreamKeyboardButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    OldDreamKeyboardButton(AppCompatActivity activity) {
        super(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(40), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(6);
        setLayoutParams(params);
        setContentDescription("显示或隐藏键盘");
        setTooltipText("键盘");
        setFocusable(false);
        setOnClickListener(view -> AppUtils.showKeyboard(activity));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 40f, getHeight() / 36f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed() ? 0xcc624f2a : 0x99101c24);
        canvas.drawRoundRect(1, 1, 39, 35, 7, 7, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0xbbd9bd83);
        canvas.drawRoundRect(1, 1, 39, 35, 7, 7, paint);
        canvas.drawRoundRect(7, 8, 33, 28, 3, 3, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffeee4cd);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 5; column++) {
                float x = 10 + column * 4.4f, y = 11 + row * 5;
                canvas.drawRoundRect(x, y, x + 2.8f, y + 2.8f, .5f, .5f, paint);
            }
        }
        canvas.drawRoundRect(13, 22, 27, 24, 1, 1, paint);
        canvas.restore();
    }
}
