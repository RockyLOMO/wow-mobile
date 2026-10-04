package com.winlator.wowmobile;

/** Shared resolution policy for game configuration, container and settings. */
public final class WowResolution {
    public static final String DEFAULT = "1280x720";

    private WowResolution() {}

    public static String normalize(String value) {
        if (value != null) {
            String[] parts = value.trim().split("x");
            if (parts.length == 2) {
                try {
                    int width = Integer.parseInt(parts[0]);
                    int height = Integer.parseInt(parts[1]);
                    if (width >= 1280 && width <= 7680 && height >= 540 && height <= 4320)
                        return width + "x" + height;
                }
                catch (NumberFormatException ignored) {}
            }
        }
        return DEFAULT;
    }
}
