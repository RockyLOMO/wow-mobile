package com.winlator.renderer;

import com.winlator.wowmobile.WowResolution;

/** Standalone regression check; runs with javac/java without an Android device. */
public final class TouchCoordinateMapperTest {
    private static final float[] point = new float[2];

    public static void main(String[] args) {
        TouchCoordinateMapper mapper = new TouchCoordinateMapper();
        // S10 full-screen stretch: the two axes must have independent scales.
        mapper.update(684, 0, 0, 1520, 684, 1280, 1024, 1, 0, 0);
        mapper.mapPoint(760, 650, point);
        check(640, point[0]);
        check(650f * 1024 / 684, point[1]);
        mapper.mapDelta(152, 68.4f, point);
        check(128, point[0]);
        check(102.4f, point[1]);
        // Matching a wide viewport, and toggling back to fit mode (letterboxes).
        mapper.update(684, 0, 0, 1520, 684, 1280, 720, 1, 0, 0);
        mapper.mapPoint(760, 342, point);
        check(640, point[0]);
        check(360, point[1]);
        ViewTransformation fit = new ViewTransformation();
        fit.update(1520, 685, 1280, 1024);
        int top = 685 - fit.viewOffsetY - fit.viewHeight;
        mapper.update(685, fit.viewOffsetX, fit.viewOffsetY, fit.viewWidth, fit.viewHeight,
            1280, 1024, 1, 0, 0);
        mapper.mapPoint(fit.viewOffsetX + fit.viewWidth * .25f, top + fit.viewHeight * .9f, point);
        check(320, point[0]);
        check(921.6f, point[1]);
        mapper.mapPoint(-100, 9999, point);
        check(0, point[0]);
        check(1023, point[1]);
        // Zoom and keyboard pan use the same scene transform as rendering.
        mapper.update(720, 0, 0, 1520, 720, 1280, 1024, 2, 100, 200);
        mapper.mapPoint(760, 360, point);
        check(370, point[0]);
        check(356, point[1]);
        mapper.update(1520, 0, 0, 720, 1520, 1280, 1024, 1, 0, 0);
        mapper.mapPoint(360, 760, point);
        check(640, point[0]);
        check(512, point[1]);
        if (!"1280x1024".equals(WowResolution.normalize("1280x1024")) ||
            !"1366x768".equals(WowResolution.normalize("1366x768")) ||
            !WowResolution.DEFAULT.equals(WowResolution.normalize("960x432")) ||
            !WowResolution.DEFAULT.equals(WowResolution.normalize("bad")))
            throw new AssertionError("Resolution preservation/minimum failed");
        System.out.println("PASS: stretched/fit/zoom/pan/orientation/clamp/delta mapping and resolution preservation");
    }

    private static void check(float expected, float actual) {
        if (Math.abs(expected - actual) > .01f)
            throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
