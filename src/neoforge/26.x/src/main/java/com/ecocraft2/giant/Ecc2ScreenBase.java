package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.industrialage.ia2.gui.DeviceScreen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** NeoForge 26.x container screen base bridging {@link GuiGraphicsExtractor} to {@link Ecc2Canvas}. */
public abstract class Ecc2ScreenBase<M extends AbstractContainerMenu> extends DeviceScreen<M> {

    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();

    protected Ecc2ScreenBase(M menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
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
    public void extractContents(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(canvas(graphics, leftPos, topPos), mouseX - leftPos, mouseY - topPos);
        super.extractContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawLabels(canvas(graphics, 0, 0), mouseX - leftPos, mouseY - topPos);
    }

    @Override
    protected void renderPanel(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean modal = isModalOpen();
        super.renderPanel(graphics, modal ? -10000 : mouseX, modal ? -10000 : mouseY, partialTick);
        if (modal) {
            graphics.nextStratum();
            var c = canvas(graphics, leftPos, topPos);
            drawModalOverlay(c, mouseX - leftPos, mouseY - topPos);
            drawTooltips(c, mouseX - leftPos, mouseY - topPos, mouseX, mouseY);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (isModalOpen()) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty()) {
            drawTooltips(
                    canvas(graphics, leftPos, topPos),
                    mouseX - leftPos,
                    mouseY - topPos,
                    mouseX,
                    mouseY);
        }
    }

    @Override
    protected boolean panelMouseClicked(MouseButtonEvent event, boolean twice) {
        int relX = (int) Math.floor(event.x() - leftPos);
        int relY = (int) Math.floor(event.y() - topPos);
        if (handleMouseClick(relX, relY, event.button())) {
            return true;
        }
        if (isModalOpen()) {
            return true;
        }
        return super.panelMouseClicked(event, twice);
    }

    @Override
    protected boolean panelMouseDragged(MouseButtonEvent event, double dx, double dy) {
        return isModalOpen() || super.panelMouseDragged(event, dx, dy);
    }

    @Override
    protected boolean panelMouseReleased(MouseButtonEvent event) {
        return isModalOpen() || super.panelMouseReleased(event);
    }

    private static Identifier texture(String path) {
        return TEXTURE_CACHE.computeIfAbsent(
                path, key -> Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, key));
    }

    private Ecc2Canvas canvas(GuiGraphicsExtractor graphics, int originX, int originY) {
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
                        RenderPipelines.GUI_TEXTURED,
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
                var state = fluid.defaultFluidState();
                var model =
                        Minecraft.getInstance()
                                .getModelManager()
                                .getFluidStateModelSet()
                                .get(state);
                var tint = model.fluidTintSource();
                graphics.blitSprite(
                        RenderPipelines.GUI_TEXTURED,
                        model.stillMaterial().sprite(),
                        originX + x,
                        originY + y,
                        width,
                        height,
                        tint == null ? -1 : tint.color(state));
            }

            @Override
            public void drawText(Component text, int x, int y, int argb, boolean shadow) {
                graphics.text(font, text, originX + x, originY + y, argb, shadow);
            }

            @Override
            public void drawText(String text, int x, int y, int argb, boolean shadow) {
                graphics.text(font, text, originX + x, originY + y, argb, shadow);
            }

            @Override
            public void drawCenteredText(Component text, int centerX, int y, int argb) {
                graphics.text(
                        font, text, originX + centerX - font.width(text) / 2, originY + y, argb, false);
            }

            @Override
            public void drawCenteredText(String text, int centerX, int y, int argb) {
                graphics.text(
                        font, text, originX + centerX - font.width(text) / 2, originY + y, argb, false);
            }

            @Override
            public void drawScaledText(String text, int x, int y, float scale, int argb) {
                var pose = graphics.pose();
                pose.pushMatrix();
                try {
                    pose.translate(originX + x, originY + y);
                    pose.scale(scale);
                    graphics.text(font, text, 0, 0, argb, false);
                } finally {
                    pose.popMatrix();
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
                    graphics.setComponentTooltipForNextFrame(font, lines, screenX, screenY);
                }
            }

            @Override
            public void nextLayer() {
                graphics.nextStratum();
            }
        };
    }
}
