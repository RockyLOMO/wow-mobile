package com.winlator.wowmobile;

import java.util.Locale;

/** Estimated startup progress in hundredths of a percent; only a real frame completes it. */
final class StartupProgress {
    static final long DEFAULT_DURATION_MS=60_000;
    private StartupProgress() {}
    static int estimate(long elapsedMillis,long previousDurationMillis) {
        if (elapsedMillis<=0) return 0;
        long duration=previousDurationMillis>0 ? previousDurationMillis : DEFAULT_DURATION_MS;
        double ratio=elapsedMillis/(double)duration;
        if (ratio<=1) return (int)Math.round(ratio*9000);
        return 9000+(int)Math.round(950*(1-Math.exp(-(ratio-1)*2)));
    }
    static String elapsedText(long elapsedMillis) {
        long seconds=Math.max(0,elapsedMillis)/1000;
        return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    static boolean showManualReveal(long elapsedMillis) {
        return elapsedMillis>120_000;
    }
}
