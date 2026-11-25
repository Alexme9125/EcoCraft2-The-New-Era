package com.ecocraft2.giant;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Unified 2D drawing surface abstracting NeoForge 1.21.1 {@code GuiGraphics} and NeoForge 26.x
 * {@code GuiGraphicsExtractor} for all shared EcoCraft 2 container screens.
 */
public interface Ecc2Canvas {

    /** Blits a region from a 256x256 texture under {@code ecc2:textures/...} at GUI-local (x, y). */
    default void blit(String texturePath, int x, int y, int u, int v, int width, int height) {
        blit(texturePath, x, y, u, v, width, height, 256, 256);
    }

    /** Blits a region from an arbitrarily sized texture under {@code ecc2:textures/...}. */
    void blit(
            String texturePath,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height,
            int textureWidth,
            int textureHeight);

    /** Blits a full standalone icon texture (e.g. 16x16) at GUI-local (x, y). */
    default void blitFull(String texturePath, int x, int y, int width, int height) {
        blit(texturePath, x, y, 0, 0, width, height, width, height);
    }

    /** Fills a solid ARGB rectangle in GUI-local coordinates {@code [x1, x2) x [y1, y2)}. */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draws a fluid sprite column in GUI-local coordinates using the built-in fluid registry ID. */
    void drawFluid(int fluidRegistryId, int x, int y, int width, int height);

    void drawText(Component text, int x, int y, int argb, boolean shadow);

    void drawText(String text, int x, int y, int argb, boolean shadow);

    void drawCenteredText(Component text, int centerX, int y, int argb);

    void drawCenteredText(String text, int centerX, int y, int argb);

    void drawScaledText(String text, int x, int y, float scale, int argb);

    int textWidth(Component text);

    int textWidth(String text);

    /** Renders a multi-line tooltip at absolute screen coordinates {@code (screenX, screenY)}. */
    void renderTooltip(List<Component> lines, int screenX, int screenY);

    /** Advances to the next rendering stratum on 26.x so modal popups draw above item stacks. */
    default void nextLayer() {}
}
