package com.winlator.wowmobile;

/** Ignore a black surface, tiny cursor and uniform clear colour during client startup. */
final class StartupFrameReadiness {
    private StartupFrameReadiness() {}
    static boolean hasScene(int[] pixels) {
        if (pixels.length == 0) return false;
        int lit=0, bright=0, minimum=255, maximum=0;
        for (int pixel : pixels) {
            int value=Math.max((pixel >> 16) & 255, Math.max((pixel >> 8) & 255,pixel & 255));
            if (value>24) lit++;
            if (value>80) bright++;
            minimum=Math.min(minimum,value);
            maximum=Math.max(maximum,value);
        }
        return lit*10>=pixels.length && bright*100>=pixels.length && maximum-minimum>=40;
    }
}
