package com.winlator.wowmobile;

/** One rendering baseline for the mobile UI, independent of the device display. */
public final class WowResolution {
    public static final String DEFAULT = "1280x720";

    private WowResolution() {}

    public static String normalize(String value) {
        return DEFAULT;
    }
}
