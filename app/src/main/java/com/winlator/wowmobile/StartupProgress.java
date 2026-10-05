package com.winlator.wowmobile;

/** Estimated startup progress in hundredths of a percent; only a real frame completes it. */
final class StartupProgress {
    private StartupProgress() {}
    static int estimate(long elapsedMillis) {
        if (elapsedMillis<=0) return 0;
        if (elapsedMillis<=30_000) return (int)(elapsedMillis*8000/30_000);
        return 8000+(int)Math.round(1950*(1-Math.exp(-(elapsedMillis-30_000)/180_000.0)));
    }
}
