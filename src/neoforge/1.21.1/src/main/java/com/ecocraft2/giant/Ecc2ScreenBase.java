package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.industrialage.ia2.gui.DeviceScreen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** NeoForge 1.21.1 container screen base bridging {@link GuiGraphics} to {@link Ecc2Canvas}. */
public abstract class Ecc2ScreenBase<M extends AbstractContainerMenu> extends DeviceScreen<M> {

    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();

    protected Ecc2ScreenBase(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    /** Returns {@code true} when an internal modal popup (such as the 8-port matrix) is active. */
    protected boolean isModalOpen() {
        return false;
    }

    /** Attempts to dismiss an internal modal popup on Escape. Return {@code true} to consume close. */
    protected boolean closeModalOnEscape() {
        return false;
    }

    /** Paints the GUI background, gauges, fluid bars, and status icons in GUI-local coordinates. */
    protected abstract void drawBackground(Ecc2Canvas canvas, int relMouseX, int relMouseY);

    /** Paints title/telemetry labels above slots in GUI-local coordinates. */
    protected void drawLabels(Ecc2Canvas canvas, int relMouseX, int relMouseY) {}

    /** Paints any modal overlay above slots and carried items in GUI-local coordinates. */
    protected void drawModalOverlay(Ecc2Canvas canvas, int relMouseX, int relMouseY) {}

    /** Renders hover tooltips (called with both GUI-local and screen-absolute mouse coordinates). */
    protected void drawTooltips(
            Ecc2Canvas canvas, int relMouseX, int relMouseY, int screenX, int screenY) {}

    /** Handles pointer clicks in GUI-local coordinates. Return {@code true} to consume click. */
    protected boolean handleMouseClick(int relX, int relY, int button) {
        return false;
    }

    @Override
    public void onClose() {
        if (closeModalOnEscape()) {
            return;
        }
        super.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawBackground(canvas(graphics, leftPos, topPos), mouseX - leftPos, mouseY - topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawLabels(canvas(graphics, 0, 0), mouseX - leftPos, mouseY - topPos);
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean modal = isModalOpen();
        super.renderPanel(graphics, modal ? -10000 : mouseX, modal ? -10000 : mouseY, partialTick);
        if (modal) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 350.0F);
            try {
                drawModalOverlay(
                        canvas(graphics, leftPos, topPos), mouseX - leftPos, mouseY - topPos);
            } finally {
                pose.popPose();
            }
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
        if (modal || menu.getCarried().isEmpty()) {
            drawTooltips(
                    canvas(graphics, leftPos, topPos),
                    mouseX - leftPos,
                    mouseY - topPos,
                    mouseX,
                    mouseY);
        }
    }

    @Override
    protected boolean panelMouseClicked(double mouseX, double mouseY, int button) {
        int relX = (int) Math.floor(mouseX - leftPos);
        int relY = (int) Math.floor(mouseY - topPos);
        if (handleMouseClick(relX, relY, button)) {
            return true;
        }
        if (isModalOpen()) {
            return true;
        }
        return super.panelMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean panelMouseDragged(
            double mouseX, double mouseY, int button, double dx, double dy) {
        return isModalOpen() || super.panelMouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    protected boolean panelMouseReleased(double mouseX, double mouseY, int button) {
        return isModalOpen() || super.panelMouseReleased(mouseX, mouseY, button);
    }

    private static ResourceLocation texture(String path) {
        return TEXTURE_CACHE.computeIfAbsent(
                path, key -> ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, key));
    }

    private Ecc2Canvas canvas(GuiGraphics graphics, int originX, int originY) {
        return new Ecc2Canvas() {
            @Override
            public void blit(
                    String texturePath,
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
                graphics.blit(
                        texture(texturePath),
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
                graphics.fill(originX + x1, originY + y1, originX + x2, originY + y2, argb);
            }

            @Override
            public void drawFluid(int fluidRegistryId, int x, int y, int width, int height) {
                if (fluidRegistryId <= 0 || width <= 0 || height <= 0) {
                    return;
                }
                var fluid = BuiltInRegistries.FLUID.byId(fluidRegistryId);
                if (fluid == null) {
                    return;
                }
                var ext = IClientFluidTypeExtensions.of(fluid);
                var still = ext.getStillTexture();
                if (still == null) {
                    return;
                }
                var sprite =
                        Minecraft.getInstance()
                                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                                .apply(still);
                int tint = ext.getTintColor();
                float a = ((tint >>> 24) & 0xFF) / 255.0F;
                float r = ((tint >>> 16) & 0xFF) / 255.0F;
                float g = ((tint >>> 8) & 0xFF) / 255.0F;
                float b = (tint & 0xFF) / 255.0F;
                if (a <= 0.0F) {
                    a = 1.0F;
                }
                graphics.blit(originX + x, originY + y, 0, width, height, sprite, r, g, b, a);
            }

            @Override
            public void drawText(Component text, int x, int y, int argb, boolean shadow) {
                graphics.drawString(font, text, originX + x, originY + y, argb, shadow);
            }

            @Override
            public void drawText(String text, int x, int y, int argb, boolean shadow) {
                graphics.drawString(font, text, originX + x, originY + y, argb, shadow);
            }

            @Override
            public void drawCenteredText(Component text, int centerX, int y, int argb) {
                graphics.drawString(
                        font, text, originX + centerX - font.width(text) / 2, originY + y, argb, false);
            }

            @Override
            public void drawCenteredText(String text, int centerX, int y, int argb) {
                graphics.drawString(
                        font, text, originX + centerX - font.width(text) / 2, originY + y, argb, false);
            }

            @Override
            public void drawScaledText(String text, int x, int y, float scale, int argb) {
                var pose = graphics.pose();
                pose.pushPose();
                try {
                    pose.translate(originX + x, originY + y, 0.0F);
                    pose.scale(scale, scale, 1.0F);
                    graphics.drawString(font, text, 0, 0, argb, false);
                } finally {
                    pose.popPose();
                }
            }

            @Override
            public int textWidth(Component text) {
                return font.width(text);
            }

            @Override
            public int textWidth(String text) {
                return font.width(text);
            }

            @Override
            public void renderTooltip(List<Component> lines, int screenX, int screenY) {
                if (lines != null && !lines.isEmpty()) {
                    graphics.renderComponentTooltip(font, lines, screenX, screenY);
                }
            }
        };
    }
}
