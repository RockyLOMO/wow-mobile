package com.winlator.renderer;

/** Inverse of the viewport and scene transform used by the last rendered frame. */
public final class TouchCoordinateMapper {
    private float scaleX = 1, scaleY = 1, offsetX, offsetY;
    private int width = 1, height = 1;

    public synchronized void update(int surfaceHeight, int viewportX, int viewportY,
                                    int viewportWidth, int viewportHeight,
                                    int screenWidth, int screenHeight,
                                    float zoom, float sceneOffsetX, float sceneOffsetY) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || zoom <= 0) return;
        width = screenWidth;
        height = screenHeight;
        scaleX = screenWidth / (viewportWidth * zoom);
        scaleY = screenHeight / (viewportHeight * zoom);
        offsetX = sceneOffsetX / zoom - viewportX * scaleX;
        // OpenGL's viewport origin is at the bottom; Android touch starts at the top.
        int viewportTop = surfaceHeight - viewportY - viewportHeight;
        offsetY = sceneOffsetY / zoom - viewportTop * scaleY;
    }

    public synchronized void mapPoint(float x, float y, float[] result) {
        result[0] = Math.max(0, Math.min(width - 1, x * scaleX + offsetX));
        result[1] = Math.max(0, Math.min(height - 1, y * scaleY + offsetY));
    }

    public synchronized void mapDelta(float dx, float dy, float[] result) {
        result[0] = dx * scaleX;
        result[1] = dy * scaleY;
    }
}
