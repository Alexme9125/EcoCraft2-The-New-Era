package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.industrialage.ia2.gui.DeviceScreen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** NeoForge 1.21.1 container screen adapter wrapping IC2NE's {@link DeviceScreen}. */
public abstract class Ecc2ScreenBase<M extends AbstractContainerMenu> extends DeviceScreen<M> {

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    protected Ecc2ScreenBase(
            M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    /** Paints the background atlas and dynamic gauges in panel-local coordinates. */
    protected abstract void drawBackground(
            Ecc2GuiCanvas canvas, int relMouseX, int relMouseY, float partialTick);

    /** Paints title/status labels in panel-local coordinates after slots render. */
    protected void drawForeground(Ecc2GuiCanvas canvas, int relMouseX, int relMouseY) {}

    /** Returns custom hover tooltip lines for panel-local coordinates, or empty for default. */
    protected List<Component> hoverTooltip(int relMouseX, int relMouseY) {
        return List.of();
    }

    /** Handles custom mouse clicks in panel-local coordinates; returns {@code true} if consumed. */
    protected boolean handleClick(int relMouseX, int relMouseY, int button) {
        return false;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawBackground(
                canvas(graphics, font, leftPos, topPos),
                mouseX - leftPos,
                mouseY - topPos,
                partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawForeground(canvas(graphics, font, 0, 0), mouseX - leftPos, mouseY - topPos);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hoveringItem = hoveredSlot != null && hoveredSlot.hasItem();
        if (!hoveringItem) {
            var custom = hoverTooltip(mouseX - leftPos, mouseY - topPos);
            if (custom != null && !custom.isEmpty()) {
                graphics.renderComponentTooltip(font, custom, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderPanel(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean panelMouseClicked(double mouseX, double mouseY, int button) {
        if (handleClick((int) Math.floor(mouseX - leftPos), (int) Math.floor(mouseY - topPos), button)) {
            return true;
        }
        return super.panelMouseClicked(mouseX, mouseY, button);
    }

    private static ResourceLocation guiTexture(String guiName) {
        return TEXTURES.computeIfAbsent(
                guiName,
                name ->
                        ResourceLocation.fromNamespaceAndPath(
                                EcoCraft2.MOD_ID, "textures/gui/" + name + ".png"));
    }

    private static Ecc2GuiCanvas canvas(GuiGraphics g, Font font, int originX, int originY) {
        return new Ecc2GuiCanvas() {
            @Override
            public void blit(
                    String guiName,
                    int x,
                    int y,
                    int u,
                    int v,
                    int width,
                    int height,
                    int textureWidth,
                    int textureHeight) {
                if (width <= 0 || height <= 0) {
                    return;
                }
                g.blit(
                        guiTexture(guiName),
                        originX + x,
                        originY + y,
                        (float) u,
                        (float) v,
                        width,
                        height,
                        textureWidth,
                        textureHeight);
            }

            @Override
            public void fill(int x1, int y1, int x2, int y2, int argb) {
                g.fill(originX + x1, originY + y1, originX + x2, originY + y2, argb);
            }

            @Override
            public void text(Component text, int x, int y, int color, boolean shadow) {
                g.drawString(
                        font,
                        text,
                        originX + x,
                        originY + y,
                        Ecc2GuiCanvas.opaqueColor(color),
                        shadow);
            }

            @Override
            public void centeredText(
                    Component text, int centerX, int y, int color, boolean shadow) {
                g.drawString(
                        font,
                        text,
                        originX + centerX - font.width(text) / 2,
                        originY + y,
                        Ecc2GuiCanvas.opaqueColor(color),
                        shadow);
            }

            @Override
            public int textWidth(Component text) {
                return font.width(text);
            }

            @Override
            public String trimWidth(String raw, int maxWidth) {
                if (maxWidth <= 0 || font.width(raw) <= maxWidth) {
                    return raw;
                }
                return font.plainSubstrByWidth(raw, Math.max(1, maxWidth - 6)) + "…";
            }

            @Override
            public void tooltip(List<Component> lines, int screenX, int screenY) {
                g.renderComponentTooltip(font, lines, screenX, screenY);
            }
        };
    }
}
