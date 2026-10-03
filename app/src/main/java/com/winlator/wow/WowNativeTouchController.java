package com.winlator.wow;

import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.SparseArray;
import android.view.ViewConfiguration;

/** WoW-specific gestures. Coordinates remain in view space until the mouse sink converts them. */
public final class WowNativeTouchController {
    public interface MouseSink {
        void moveAbsolute(float x, float y);
        void moveRelative(float dx, float dy);
        void pressLeft();
        void releaseLeft();
        void pressRight();
        void releaseRight();
        void scroll(boolean up);
    }

    private enum State { IDLE, PENDING, CAMERA_RIGHT_DRAG, LONG_PRESS_READY, LEFT_DRAG, SCROLL }

    // WoW can render below 30 FPS on a phone. Keep the button down across frames
    // so a tap is not lost between two game input polls.
    private static final int CLICK_HOLD_MS = 120;
    private static final int HAPTIC_MS = 25;
    private static final float SCROLL_STEP = 100f;

    private static final class Finger {
        final float downX, downY;
        float x, y;
        Finger(float x, float y) { downX = this.x = x; downY = this.y = y; }
    }

    private final SparseArray<Finger> fingers = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MouseSink mouse;
    private final Vibrator vibrator;
    private final float slop;
    private State state = State.IDLE;
    private final Runnable longPress = () -> {
        if (state == State.PENDING) state = State.LONG_PRESS_READY;
    };
    private int primaryId = -1;
    private float sensitivity = 1f;
    private float scrollY;
    private boolean leftDown, rightDown;

    public WowNativeTouchController(ViewConfiguration config, Vibrator vibrator, MouseSink mouse) {
        this.slop = config.getScaledTouchSlop() * 1.2f;
        this.vibrator = vibrator;
        this.mouse = mouse;
    }

    public void setSensitivity(float sensitivity) { this.sensitivity = sensitivity; }
    public void setLongPressMs(int longPressMs) { this.longPressMs = longPressMs; }
    private int longPressMs = 380;

    public void down(int id, float x, float y) {
        if (fingers.size() == 0) {
            handler.removeCallbacksAndMessages(null);
            releaseButtons();
        }
        fingers.put(id, new Finger(x, y));
        if (fingers.size() == 1) {
            primaryId = id;
            state = State.PENDING;
            mouse.moveAbsolute(x, y);
            handler.postDelayed(longPress, longPressMs);
        }
        else {
            handler.removeCallbacks(longPress);
            releaseButtons();
            state = State.SCROLL;
            scrollY = 0;
        }
    }

    public void move(int id, float x, float y) {
        Finger finger = fingers.get(id);
        if (finger == null) return;
        float dx = x - finger.x, dy = y - finger.y;
        finger.x = x;
        finger.y = y;
        if (state == State.SCROLL) {
            if (fingers.size() == 2) {
                scrollY += dy * 0.5f;
                if (Math.abs(scrollY) >= SCROLL_STEP) {
                    mouse.scroll(scrollY > 0);
                    scrollY = 0;
                }
            }
            return;
        }
        if (id != primaryId) return;
        float travel = (float)Math.hypot(x - finger.downX, y - finger.downY);
        if (state == State.PENDING && travel > slop) {
            handler.removeCallbacks(longPress);
            state = State.CAMERA_RIGHT_DRAG;
            mouse.pressRight();
            rightDown = true;
        }
        else if (state == State.LONG_PRESS_READY && travel > slop) {
            state = State.LEFT_DRAG;
            mouse.moveAbsolute(finger.downX, finger.downY);
            mouse.pressLeft();
            leftDown = true;
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        if (state == State.CAMERA_RIGHT_DRAG) mouse.moveRelative(dx * sensitivity, dy * sensitivity);
        else if (state == State.LEFT_DRAG) mouse.moveAbsolute(x, y);
    }

    public void up(int id, float x, float y) {
        Finger finger = fingers.get(id);
        if (finger == null) return;
        handler.removeCallbacks(longPress);
        if (id == primaryId && fingers.size() == 1) {
            if (state == State.PENDING || state == State.LONG_PRESS_READY) {
                mouse.moveAbsolute(x, y);
                if (state == State.PENDING) {
                    mouse.pressLeft();
                    leftDown = true;
                    handler.postDelayed(this::releaseButtons, CLICK_HOLD_MS);
                }
                else {
                    mouse.pressRight();
                    rightDown = true;
                    handler.postDelayed(this::releaseButtons, CLICK_HOLD_MS);
                }
            }
            else releaseButtons();
        }
        fingers.remove(id);
        if (fingers.size() == 0) {
            primaryId = -1;
            state = State.IDLE;
        }
        else if (state == State.SCROLL) primaryId = -1;
    }

    public void cancel() {
        handler.removeCallbacks(longPress);
        handler.removeCallbacksAndMessages(null);
        releaseButtons();
        fingers.clear();
        primaryId = -1;
        state = State.IDLE;
    }

    private void releaseButtons() {
        if (leftDown) { mouse.releaseLeft(); leftDown = false; }
        if (rightDown) { mouse.releaseRight(); rightDown = false; }
    }
}
