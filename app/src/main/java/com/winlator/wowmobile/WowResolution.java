package com.winlator.wowmobile;

/** Fixed 720px height; even width follows the physical landscape display ratio. */
public final class WowResolution {
    public static final String DEFAULT = "1280x720";

    private WowResolution() {}

    public static String forDisplay(int width, int height) {
        if (width <= 0 || height <= 0) return DEFAULT;
        int landscapeWidth = Math.max(width, height), landscapeHeight = Math.min(width, height);
        int renderWidth = (int)(Math.round(720.0 * landscapeWidth / landscapeHeight / 2) * 2);
        return renderWidth + "x720";
    }

    public static String normalize(String value) {
        if (value == null) return DEFAULT;
        try {
            String[] size = value.split("x");
            int width = Integer.parseInt(size[0]);
            if (size.length == 2 && "720".equals(size[1]) && width >= 720 && width <= 8192 && width % 2 == 0) return value;
        } catch (RuntimeException ignored) {}
        return DEFAULT;
    }
}
