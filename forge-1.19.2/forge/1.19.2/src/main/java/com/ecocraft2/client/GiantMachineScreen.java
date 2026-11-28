package com.ecocraft2.client;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.giant.GiantMachineMenu;
import com.ecocraft2.network.Ecc2Network;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Redesigned 256x236 sci-fi GUI screen for all 5 3x3x3 Giant Machines:
 *
 * <ul>
 *   <li>9 independent 16x3 cyan micro-progress bars + batch badges ({@code x1/x2/x3})
 *   <li>Central EU core column, active batch counter ({@code M/27}), EU/t draw LCD, flow arrow
 *   <li>Optional 64,000 mB water tank column for Giant Ore Washing Plant
 *   <li>Interactive 8 Lower-Corner Port Access top-down matrix popup toggled by the side gear button
 * </ul>
 */
public final class GiantMachineScreen extends AbstractContainerScreen<GiantMachineMenu> {

    private static final ResourceLocation TEXTURE =
            Ecc2BridgePlatform.rl("textures/gui/giant_machine.png");
    private static final ResourceLocation GEAR_ICON =
            Ecc2BridgePlatform.rl("textures/gui/icons/port_access_gear.png");
    private static final ResourceLocation SPREAD_ICON =
            Ecc2BridgePlatform.rl("textures/gui/icons/auto_spread.png");

    private static final int[] BAR_X = {13, 33, 53};
    private static final int[] BAR_Y = {43, 67, 91};
    private static final int[] SLOT_X = {13, 33, 53};
    private static final int[] SLOT_Y = {25, 49, 73};

    // Gear button at (224, 114, 20x20)
    private static final int GEAR_X = 224;
    private static final int GEAR_Y = 114;
    private static final int GEAR_SIZE = 20;

    // Auto-spread toggle button at (102, 81, 20x20)
    private static final int SPREAD_X = 102;
    private static final int SPREAD_Y = 81;
    private static final int SPREAD_SIZE = 20;

    // 8 Lower-Corner Port Matrix popup (opened right of main panel or overlaid cleanly inside)
    private boolean portPopupOpen = false;
    private static final int POPUP_X = 52;
    private static final int POPUP_Y = 24;
    private static final int POPUP_W = 152;
    private static final int POPUP_H = 122;

    // 8 port button relative positions inside popup (top-down blueprint of the 3x3x3 base!)
    private static final int[][] PORT_BTN_RECTS = {
        // FRONT_LEFT, FRONT_RIGHT (top edge = Front)
        {34, 24, 38, 16},
        {80, 24, 38, 16},
        // BACK_LEFT, BACK_RIGHT (bottom edge = Back)
        {34, 92, 38, 16},
        {80, 92, 38, 16},
        // LEFT_FRONT, LEFT_BACK (left edge)
        {8, 44, 34, 18},
        {8, 68, 34, 18},
        // RIGHT_FRONT, RIGHT_BACK (right edge)
        {110, 44, 34, 18},
        {110, 68, 34, 18}
    };

    private static final String[] PORT_SHORT = {
        "FL", "FR", "BL", "BR", "LF", "LB", "RF", "RB"
    };

    public GiantMachineScreen(GiantMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 236;
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 48;
        this.inventoryLabelY = 142;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        if (portPopupOpen) {
            renderPortPopup(poseStack, mouseX, mouseY);
        } else {
            renderTooltip(poseStack, mouseX, mouseY);
            if (isHovering(GEAR_X, GEAR_Y, GEAR_SIZE, GEAR_SIZE, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable("gui.ecera.giant.port_access_button"),
                        mouseX,
                        mouseY);
            } else if (isHovering(SPREAD_X, SPREAD_Y, SPREAD_SIZE, SPREAD_SIZE, mouseX, mouseY)) {
                renderComponentTooltip(
                        poseStack,
                        List.of(
                                Component.translatable("gui.ecera.giant.auto_spread"),
                                Component.translatable(
                                        menu.isAutoSpreadEnabled()
                                                ? "gui.ecera.giant.auto_spread.on"
                                                : "gui.ecera.giant.auto_spread.off"),
                                Component.translatable("gui.ecera.giant.auto_spread.hint")),
                        mouseX,
                        mouseY);
            } else if (menu.usesWater() && isHovering(130, 25, 16, 72, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecera.giant.water", menu.waterMb(), menu.waterCapacityMb()),
                        mouseX,
                        mouseY);
            } else if (isHovering(80, 25, 14, 52, mouseX, mouseY)) {
                renderTooltip(
                        poseStack,
                        Component.translatable(
                                "gui.ecera.giant.eu_stored", menu.storedEu(), menu.capacityEu()),
                        mouseX,
                        mouseY);
            }
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, TEXTURE);

        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // 9 independent 16x3 cyan micro-progress bars
        for (int lane = 0; lane < 9; lane++) {
            int bx = leftPos + BAR_X[lane % 3];
            int by = topPos + BAR_Y[lane / 3];
            blit(poseStack, bx, by, 2, 243, 16, 3);
            int pct = menu.laneProgressPercent(lane);
            int w = (pct * 16) / 100;
            if (w > 0) {
                blit(poseStack, bx, by, 2, 238, w, 3);
            }
        }

        // Central EU core column (80, 25, 14x52) filled bottom-up
        int euH = (int) Math.min(52L, ((long) menu.storedEu() * 52L) / menu.capacityEu());
        if (euH > 0) {
            int yBottom = topPos + 25 + 52;
            GuiComponent.fill(
                    poseStack, leftPos + 80, yBottom - euH, leftPos + 94, yBottom, 0xFF2CE6F5);
        }

        // Active flow arrow at (100, 44, 24x15)
        if (menu.activeTotalBatch() > 0) {
            blit(poseStack, leftPos + 100, topPos + 44, 24, 238, 24, 15);
        }

        // Water tank column for Giant Ore Washing Plant (130, 25, 16x72)
        if (menu.usesWater()) {
            int wH =
                    (int)
                            Math.min(
                                    72L,
                                    ((long) menu.waterMb() * 72L) / menu.waterCapacityMb());
            if (wH > 0) {
                int yBottom = topPos + 25 + 72;
                GuiComponent.fill(
                        poseStack,
                        leftPos + 130,
                        yBottom - wH,
                        leftPos + 146,
                        yBottom,
                        0xCC2C88E6);
            }
        }

        // Gear access button at (224, 114)
        boolean hoverGear =
                !portPopupOpen && isHovering(GEAR_X, GEAR_Y, GEAR_SIZE, GEAR_SIZE, mouseX, mouseY);
        blit(
                poseStack,
                leftPos + GEAR_X + 1,
                topPos + GEAR_Y + 1,
                hoverGear || portPopupOpen ? 190 : 170,
                237,
                18,
                18);
        RenderSystem.setShaderTexture(0, GEAR_ICON);
        GuiComponent.blit(
                poseStack, leftPos + GEAR_X + 2, topPos + GEAR_Y + 2, 0, 0, 16, 16, 16, 16);

        // Auto-spread toggle button at (102, 81)
        boolean spreadOn = menu.isAutoSpreadEnabled();
        boolean hoverSpread =
                !portPopupOpen
                        && isHovering(SPREAD_X, SPREAD_Y, SPREAD_SIZE, SPREAD_SIZE, mouseX, mouseY);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(
                poseStack,
                leftPos + SPREAD_X + 1,
                topPos + SPREAD_Y + 1,
                (spreadOn || hoverSpread) ? 190 : 170,
                237,
                18,
                18);
        RenderSystem.setShaderTexture(0, SPREAD_ICON);
        GuiComponent.blit(
                poseStack, leftPos + SPREAD_X + 2, topPos + SPREAD_Y + 2, 0, 0, 16, 16, 16, 16);
        GuiComponent.fill(
                poseStack,
                leftPos + SPREAD_X + 15,
                topPos + SPREAD_Y + 3,
                leftPos + SPREAD_X + 18,
                topPos + SPREAD_Y + 6,
                spreadOn ? 0xFF55FFFF : 0xFF3A4654);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x263240);

        poseStack.pushPose();
        poseStack.scale(0.7f, 0.7f, 1.0f);
        // Header status LCD (152, 4)
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.giant.formed"),
                (int) (155 / 0.7f),
                (int) (6 / 0.7f),
                0x7FFFB0);

        // Active batch LCD (99, 23) & EU draw LCD (99, 64)
        int maxTotalOps = 9 * Math.max(1, Ecc2Config.giantMaxBatchPerLane());
        font.draw(
                poseStack,
                menu.activeTotalBatch() + "/" + maxTotalOps,
                (int) (102 / 0.7f),
                (int) (28 / 0.7f),
                0x8DF0FF);
        font.draw(
                poseStack,
                menu.euDrawPerTick() + "EU",
                (int) (101 / 0.7f),
                (int) (68 / 0.7f),
                0xFFE070);

        // Telemetry LCD (10, 105)
        int tx = (int) (13 / 0.7f);
        font.draw(
                poseStack,
                Component.translatable(
                        "gui.ecera.giant.eu_stored", menu.storedEu(), menu.capacityEu()),
                tx,
                (int) (109 / 0.7f),
                0x8DF0FF);
        font.draw(
                poseStack,
                Component.translatable(
                        "gui.ecera.giant.tier", menu.tier(), menu.tierMaxPacket()),
                tx,
                (int) (120 / 0.7f),
                0xD0E4F0);
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.giant.eu_draw", menu.euDrawPerTick()),
                tx,
                (int) (131 / 0.7f),
                0x7FFFB0);

        // Port summary LCD (153, 105)
        int px = (int) (156 / 0.7f);
        font.draw(
                poseStack,
                Component.translatable("gui.ecera.giant.port_access_button"),
                px,
                (int) (109 / 0.7f),
                0x8DF0FF);
        int enabled = 0;
        for (GiantCornerPort p : GiantCornerPort.values()) {
            if (menu.portMode(p).connects()) {
                enabled++;
            }
        }
        font.draw(poseStack, enabled + " / 8 ON", px, (int) (122 / 0.7f), 0x7FFFB0);

        // Batch corner badges x1/x2/x3 on active input lanes
        for (int lane = 0; lane < 9; lane++) {
            int b = menu.laneBatchSize(lane);
            if (b > 0) {
                int lx = (int) ((SLOT_X[lane % 3] + 1) / 0.7f);
                int ly = (int) ((SLOT_Y[lane / 3] + 1) / 0.7f);
                font.draw(poseStack, "x" + b, lx, ly, 0x3CF0FF);
            }
        }
        poseStack.popPose();
    }

    private void renderPortPopup(PoseStack poseStack, int mouseX, int mouseY) {
        int px = leftPos + POPUP_X;
        int py = topPos + POPUP_Y;
        poseStack.pushPose();
        poseStack.translate(0, 0, 300);

        // Dark sci-fi overlay panel
        GuiComponent.fill(poseStack, px - 2, py - 2, px + POPUP_W + 2, py + POPUP_H + 2, 0xFF8FB4CC);
        GuiComponent.fill(poseStack, px, py, px + POPUP_W, py + POPUP_H, 0xF0121D28);

        font.draw(
                poseStack,
                Component.translatable("gui.ecera.giant.port_access_title"),
                px + 6,
                py + 6,
                0x8DF0FF);

        // Center 3x3 blueprint diagram
        GuiComponent.fill(poseStack, px + 50, py + 44, px + 102, py + 86, 0xFF283747);
        font.draw(poseStack, "3x3x3", px + 61, py + 61, 0xA8C8DC);

        GiantCornerPort[] ports = GiantCornerPort.values();
        GiantCornerPort hoveredPort = null;
        for (int i = 0; i < ports.length; i++) {
            int[] r = PORT_BTN_RECTS[i];
            int bx = px + r[0];
            int by = py + r[1];
            PortAccessMode mode = menu.portMode(ports[i]);
            int fillColor =
                    switch (mode) {
                        case FULL -> 0xFF1A8E99;
                        case INPUT -> 0xFF2B67C6;
                        case OUTPUT -> 0xFFD07422;
                        case BLOCKED -> 0xFF454E59;
                    };
            boolean hover =
                    mouseX >= bx && mouseX < bx + r[2] && mouseY >= by && mouseY < by + r[3];
            if (hover) {
                hoveredPort = ports[i];
            }
            GuiComponent.fill(
                    poseStack,
                    bx - 1,
                    by - 1,
                    bx + r[2] + 1,
                    by + r[3] + 1,
                    hover ? 0xFFFFFFFF : 0xFF1B2430);
            GuiComponent.fill(poseStack, bx, by, bx + r[2], by + r[3], fillColor);
            String label = PORT_SHORT[i] + ":" + modeShort(mode);
            font.draw(
                    poseStack,
                    label,
                    bx + (r[2] - font.width(label)) / 2,
                    by + (r[3] - 8) / 2,
                    0xFFFFFF);
        }

        // Close button [X] top-right
        int cx = px + POPUP_W - 14;
        int cy = py + 4;
        GuiComponent.fill(poseStack, cx, cy, cx + 10, cy + 10, 0xFFAA3344);
        font.draw(poseStack, "X", cx + 2, cy + 1, 0xFFFFFF);

        poseStack.popPose();

        if (hoveredPort != null) {
            PortAccessMode mode = menu.portMode(hoveredPort);
            renderTooltip(
                    poseStack,
                    Component.translatable(
                            "jade.ecera.giant_port",
                            Ecc2BridgePlatform.portName(hoveredPort),
                            Ecc2BridgePlatform.modeName(mode)),
                    mouseX,
                    mouseY);
        }
    }

    private static String modeShort(PortAccessMode mode) {
        return switch (mode) {
            case FULL -> "IO";
            case INPUT -> "IN";
            case OUTPUT -> "OUT";
            case BLOCKED -> "X";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (portPopupOpen) {
                int px = leftPos + POPUP_X;
                int py = topPos + POPUP_Y;
                int cx = px + POPUP_W - 14;
                int cy = py + 4;
                if (mouseX >= cx && mouseX < cx + 10 && mouseY >= cy && mouseY < cy + 10) {
                    portPopupOpen = false;
                    return true;
                }
                GiantCornerPort[] ports = GiantCornerPort.values();
                for (int i = 0; i < ports.length; i++) {
                    int[] r = PORT_BTN_RECTS[i];
                    int bx = px + r[0];
                    int by = py + r[1];
                    if (mouseX >= bx
                            && mouseX < bx + r[2]
                            && mouseY >= by
                            && mouseY < by + r[3]) {
                        Ecc2Network.sendGiantPortToggle(menu.pos(), ports[i]);
                        return true;
                    }
                }
                if (mouseX < px
                        || mouseX >= px + POPUP_W
                        || mouseY < py
                        || mouseY >= py + POPUP_H) {
                    portPopupOpen = false;
                }
                return true;
            }
            if (isHovering(GEAR_X, GEAR_Y, GEAR_SIZE, GEAR_SIZE, mouseX, mouseY)) {
                portPopupOpen = true;
                return true;
            }
            if (isHovering(SPREAD_X, SPREAD_Y, SPREAD_SIZE, SPREAD_SIZE, mouseX, mouseY)) {
                Ecc2Network.sendGiantAutoSpreadToggle(menu.pos());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
