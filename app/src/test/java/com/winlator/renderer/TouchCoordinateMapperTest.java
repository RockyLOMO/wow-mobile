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
        // One 720p scene on phone/tablet displays, with the same touch coordinate policy.
        for (int[] display : new int[][]{{1520, 720}, {1600, 720}, {1920, 1080}, {1920, 1200}, {1024, 768}}) {
            fit.update(display[0], display[1], 1280, 720);
            top = display[1] - fit.viewOffsetY - fit.viewHeight;
            mapper.update(display[1], fit.viewOffsetX, fit.viewOffsetY, fit.viewWidth, fit.viewHeight,
                1280, 720, 1, 0, 0);
            mapper.mapPoint(fit.viewOffsetX + fit.viewWidth * .25f, top + fit.viewHeight * .9f, point);
            check(320, point[0]);
            check(648, point[1]);
            if (Math.abs(fit.viewWidth * 720 - fit.viewHeight * 1280) > 1280)
                throw new AssertionError("Aspect ratio changed beyond pixel rounding");
        }
        for (String previous : new String[]{"1280x1024", "1366x768", "960x432", "1280x720", "bad", null}) {
            if (!WowResolution.DEFAULT.equals(WowResolution.normalize(previous)))
                throw new AssertionError("Fixed 720p migration failed");
        }
        System.out.println("PASS: touch transforms, five display aspect ratios and fixed 720p migration");
    }

    private static void check(float expected, float actual) {
        if (Math.abs(expected - actual) > .01f)
            throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
