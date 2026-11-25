package com.ecocraft2.solar;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Version-neutral 2D GUI canvas used by EcoCraft 2 screens across NeoForge 1.21.1 and 26.x.
 *
 * <p>All coordinates are relative to the canvas origin (panel top-left for backgrounds and
 * foregrounds). Colors without an explicit alpha byte are automatically promoted to opaque ARGB so
 * Minecraft 26.x text rendering never produces invisible labels.
 */
public interface Ecc2GuiCanvas {

    /** Draws a textured rectangle from {@code ecc2:textures/gui/<guiName>.png}. */
    void blit(
            String guiName,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height,
            int textureWidth,
            int textureHeight);

    /** Draws a textured rectangle from a 256x256 sheet in {@code ecc2:textures/gui/<guiName>.png}. */
    default void blit(String guiName, int x, int y, int u, int v, int width, int height) {
        blit(guiName, x, y, u, v, width, height, 256, 256);
    }

    /** Fills a solid ARGB rectangle in canvas-local coordinates. */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draws left-aligned text at {@code (x, y)}. */
    void text(Component text, int x, int y, int color, boolean shadow);

    /** Draws horizontally centered text around {@code centerX}. */
    void centeredText(Component text, int centerX, int y, int color, boolean shadow);

    /** Returns the rendered pixel width of {@code text}. */
    int textWidth(Component text);

    /** Truncates {@code raw} to fit inside {@code maxWidth} pixels. */
    String trimWidth(String raw, int maxWidth);

    /** Draws a multi-line tooltip at absolute screen coordinates {@code (screenX, screenY)}. */
    void tooltip(List<Component> lines, int screenX, int screenY);

    /** Promotes 24-bit RGB values to opaque 32-bit ARGB while leaving explicit alpha untouched. */
    static int opaqueColor(int color) {
        return (color & 0xFF000000) == 0 ? (0xFF000000 | color) : color;
    }
}
