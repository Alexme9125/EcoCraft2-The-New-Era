package com.ecocraft2.giant;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.PortAccessMode;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Flagship 256x236 sci-fi industrial GUI for 3x3x3 Giant Machines, pixel-aligned verbatim to
 * {@code assets-gen/gui_coordinates.json}, including the interactive 8 lower-corner port
 * permission top-down matrix popup.
 */
public final class GiantMachineScreen extends Ecc2ScreenBase<GiantMachineMenu> {

    private static final String TEX_GUI = "textures/gui/giant_machine.png";
    private static final String TEX_GEAR = "textures/gui/icons/port_access_gear.png";

    private static final int GUI_W = 256;
    private static final int GUI_H = 236;

    private static final int[][] MICRO_BAR_POS = {
        {13, 43}, {33, 43}, {53, 43},
        {13, 67}, {33, 67}, {53, 67},
        {13, 91}, {33, 91}, {53, 91}
    };

    private static final int[][] INPUT_ITEM_POS = {
        {13, 25}, {33, 25}, {53, 25},
        {13, 49}, {33, 49}, {53, 49},
        {13, 73}, {33, 73}, {53, 73}
    };

    // Gear button rect [224, 114, 20, 20]
    private static final int GEAR_X = 224;
    private static final int GEAR_Y = 114;
    private static final int GEAR_W = 20;
    private static final int GEAR_H = 20;

    // Modal 8-corner port permission top-down matrix popup rect (GUI-local)
    private static final int MODAL_X = 32;
    private static final int MODAL_Y = 22;
    private static final int MODAL_W = 192;
    private static final int MODAL_H = 166;
    private static final int CLOSE_X = MODAL_X + MODAL_W - 16;
    private static final int CLOSE_Y = MODAL_Y + 4;

    private record PortTile(GiantCornerPort port, String shortCode, String langKey, int x, int y) {}

    // Top-down layout around central 3x3 machine diagram inside modal (FRONT at top, BACK at bottom)
    private static final List<PortTile> PORT_TILES =
            List.of(
                    new PortTile(
                            GiantCornerPort.FRONT_LEFT,
                            "FL",
                            "gui.ecc2.port.front_left",
                            MODAL_X + 54,
                            MODAL_Y + 34),
                    new PortTile(
                            GiantCornerPort.FRONT_RIGHT,
                            "FR",
                            "gui.ecc2.port.front_right",
                            MODAL_X + 108,
                            MODAL_Y + 34),
                    new PortTile(
                            GiantCornerPort.LEFT_FRONT,
                            "LF",
                            "gui.ecc2.port.left_front",
                            MODAL_X + 18,
                            MODAL_Y + 62),
                    new PortTile(
                            GiantCornerPort.LEFT_BACK,
                            "LB",
                            "gui.ecc2.port.left_back",
                            MODAL_X + 18,
                            MODAL_Y + 90),
                    new PortTile(
                            GiantCornerPort.RIGHT_FRONT,
                            "RF",
                            "gui.ecc2.port.right_front",
                            MODAL_X + 144,
                            MODAL_Y + 62),
                    new PortTile(
                            GiantCornerPort.RIGHT_BACK,
                            "RB",
                            "gui.ecc2.port.right_back",
                            MODAL_X + 144,
                            MODAL_Y + 90),
                    new PortTile(
                            GiantCornerPort.BACK_LEFT,
                            "BL",
                            "gui.ecc2.port.back_left",
                            MODAL_X + 54,
                            MODAL_Y + 118),
                    new PortTile(
                            GiantCornerPort.BACK_RIGHT,
                            "BR",
                            "gui.ecc2.port.back_right",
                            MODAL_X + 108,
                            MODAL_Y + 118));

    private static final int TILE_W = 30;
    private static final int TILE_H = 22;

    private boolean portModalOpen;

    public GiantMachineScreen(GiantMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_W, GUI_H);
    }

    @Override
    protected boolean isModalOpen() {
        return portModalOpen;
    }

    @Override
    protected boolean closeModalOnEscape() {
        if (portModalOpen) {
            portModalOpen = false;
            return true;
        }
        return false;
    }

    @Override
    protected void drawBackground(Ecc2Canvas c, int relMouseX, int relMouseY) {
        // 1. Main 256x236 panel
        c.blit(TEX_GUI, 0, 0, 0, 0, GUI_W, GUI_H);

        // 2. Header status LED inside [152, 4, 96, 11]
        boolean active = menu.isMachineActive();
        c.blit(TEX_GUI, 155, 6, active ? 128 : 138, 238, 8, 8);

        // 3. 9 micro-progress bars below the 3x3 blue input slots
        int maxProgress = 0;
        for (int lane = 0; lane < 9; lane++) {
            int bx = MICRO_BAR_POS[lane][0];
            int by = MICRO_BAR_POS[lane][1];
            c.blit(TEX_GUI, bx, by, 2, 243, 16, 3);
            int permille = menu.laneProgressPermille(lane);
            if (permille > maxProgress) {
                maxProgress = permille;
            }
            int fillW = (permille * 16) / 1000;
            if (fillW > 0) {
                c.blit(TEX_GUI, bx, by, 2, 238, fillW, 3);
            }
        }

        // 4. Central EU core vertical bar [80, 25, 14, 52] using tiled eu_segment_fill [52, 238, 14, 16]
        int euCap = menu.energyCapacity();
        int euStored = menu.storedEu();
        int euFillPx = euCap > 0 ? Math.min(52, (int) (((long) euStored * 52L) / euCap)) : 0;
        if (euFillPx > 0) {
            int remaining = euFillPx;
            int drawBottom = 25 + 52;
            while (remaining > 0) {
                int segH = Math.min(16, remaining);
                c.blit(TEX_GUI, 80, drawBottom - segH, 52, 238 + (16 - segH), 14, segH);
                drawBottom -= segH;
                remaining -= segH;
            }
        }

        // 5. Main flow arrow [100, 44, 24, 15], sprite [24, 238, 24, 15]
        int arrowW = active ? Math.max(2, (maxProgress * 24) / 1000) : 0;
        if (arrowW > 0) {
            c.blit(TEX_GUI, 100, 44, 24, 238, arrowW, 15);
        }

        // 6. Ore Washer 64,000 mB water column [130, 25, 16, 72] or dimmed mask for non-washers
        if (menu.usesWater()) {
            int waterCap = Math.max(1, menu.waterCapacityMb());
            int waterPx = Math.min(72, (int) (((long) menu.waterMb() * 72L) / waterCap));
            if (waterPx > 0) {
                int waterId = GiantGasPlatform.fluidRegistryId(Ecc2Ids.MINECRAFT_WATER_ID);
                c.drawFluid(waterId, 130, 25 + (72 - waterPx), 16, waterPx);
            }
            for (int yOff = 0; yOff < 72; yOff += 16) {
                int h = Math.min(16, 72 - yOff);
                c.blit(TEX_GUI, 130, 25 + yOff, 150, 238, 16, h);
            }
        } else {
            // Subtle dimmed cover over washer-only water tank & container slots
            c.fill(128, 23, 148, 99, 0xCC2C3440);
            c.fill(129, 105, 147, 123, 0xCC2C3440);
            c.fill(129, 125, 147, 143, 0xCC2C3440);
        }

        // 7. Compact 8-corner port summary preview inside port_summary_lcd [153, 105, 64, 40]
        drawPortSummaryPreview(c);

        // 8. Gear button [224, 114, 20, 20]
        boolean gearHover =
                !portModalOpen && inside(relMouseX, relMouseY, GEAR_X, GEAR_Y, GEAR_W, GEAR_H);
        c.blit(TEX_GUI, GEAR_X + 1, GEAR_Y + 1, gearHover ? 190 : 170, 237, 18, 18);
        c.blitFull(TEX_GEAR, GEAR_X + 2, GEAR_Y + 2, 16, 16);
    }

    private void drawPortSummaryPreview(Ecc2Canvas c) {
        // Mini 3x3 core box inside [153, 105, 64, 40]
        int cx = 177;
        int cy = 117;
        c.fill(cx, cy, cx + 16, cy + 16, 0xFF283747);
        c.fill(cx + 1, cy + 1, cx + 15, cy + 15, 0xFF1A2530);

        drawMiniPortDot(c, cx + 1, cy - 4, menu.portMode(GiantCornerPort.FRONT_LEFT));
        drawMiniPortDot(c, cx + 11, cy - 4, menu.portMode(GiantCornerPort.FRONT_RIGHT));
        drawMiniPortDot(c, cx + 1, cy + 17, menu.portMode(GiantCornerPort.BACK_LEFT));
        drawMiniPortDot(c, cx + 11, cy + 17, menu.portMode(GiantCornerPort.BACK_RIGHT));
        drawMiniPortDot(c, cx - 5, cy + 1, menu.portMode(GiantCornerPort.LEFT_FRONT));
        drawMiniPortDot(c, cx - 5, cy + 11, menu.portMode(GiantCornerPort.LEFT_BACK));
        drawMiniPortDot(c, cx + 17, cy + 1, menu.portMode(GiantCornerPort.RIGHT_FRONT));
        drawMiniPortDot(c, cx + 17, cy + 11, menu.portMode(GiantCornerPort.RIGHT_BACK));
    }

    private static void drawMiniPortDot(Ecc2Canvas c, int x, int y, PortAccessMode mode) {
        c.fill(x, y, x + 4, y + 3, modeColor(mode));
    }

    @Override
    protected void drawLabels(Ecc2Canvas c, int relMouseX, int relMouseY) {
        // Title (top-left)
        c.drawText(title, 8, 6, 0xFFDCE7F2, false);

        // Header LCD text [152, 4, 96, 11]
        String tierStr = "T" + menu.inputTier() + " " + menu.maxInputVoltage() + " EU/t";
        c.drawScaledText(tierStr, 166, 7, 0.72F, 0xFF7BE8FF);

        // Per-lane batch badges (x1 / x2 / x3) drawn over active input slots
        for (int lane = 0; lane < 9; lane++) {
            int batch = menu.laneBatch(lane);
            if (batch > 0) {
                int ix = INPUT_ITEM_POS[lane][0];
                int iy = INPUT_ITEM_POS[lane][1];
                String badge = "x" + batch;
                c.fill(ix, iy, ix + 10, iy + 6, 0xCC0D2330);
                c.drawScaledText(badge, ix + 1, iy + 1, 0.65F, 0xFF66F2FF);
            }
        }

        // Active batch LCD [99, 23, 26, 16]
        c.drawScaledText(menu.activeTotalBatch() + "/27", 102, 28, 0.72F, 0xFF7DF9FF);

        // Live EU draw LCD [99, 64, 26, 15]
        String drawTxt =
                menu.euDrawPerTick() >= 100
                        ? String.valueOf(Math.round(menu.euDrawPerTick()))
                        : String.format(java.util.Locale.ROOT, "%.0f", menu.euDrawPerTick());
        c.drawScaledText(drawTxt, 102, 69, 0.72F, 0xFFFFD166);

        // Telemetry LCD [10, 105, 114, 40]
        c.drawScaledText(
                "EU: " + formatCompact(menu.storedEu()) + " / " + formatCompact(menu.energyCapacity()),
                13,
                109,
                0.72F,
                0xFF9BE8FF);
        c.drawScaledText(
                "OPS: "
                        + menu.activeTotalBatch()
                        + "/27  DRAW: "
                        + String.format(java.util.Locale.ROOT, "%.1f", menu.euDrawPerTick())
                        + " EU/t",
                13,
                120,
                0.68F,
                0xFFC9DCEB);
        if (menu.usesWater()) {
            c.drawScaledText(
                    "H2O: " + menu.waterMb() + " / " + menu.waterCapacityMb() + " mB",
                    13,
                    131,
                    0.70F,
                    0xFF62C8FF);
        } else {
            c.drawScaledText(
                    "TIER " + menu.inputTier() + " (" + menu.maxInputVoltage() + " EU/t)",
                    13,
                    131,
                    0.70F,
                    0xFF8AA8C0);
        }
    }

    @Override
    protected void drawModalOverlay(Ecc2Canvas c, int relMouseX, int relMouseY) {
        if (!portModalOpen) {
            return;
        }
        // Dim backdrop over entire GUI panel
        c.fill(0, 0, GUI_W, GUI_H, 0xB0091018);

        // Modal beveled industrial frame
        c.fill(MODAL_X - 2, MODAL_Y - 2, MODAL_X + MODAL_W + 2, MODAL_Y + MODAL_H + 2, 0xFF4F5D6E);
        c.fill(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + MODAL_H, 0xFF17222D);
        // Header strip
        c.fill(MODAL_X, MODAL_Y, MODAL_X + MODAL_W, MODAL_Y + 18, 0xFF233446);
        c.fill(MODAL_X, MODAL_Y + 18, MODAL_X + MODAL_W, MODAL_Y + 19, 0xFF39C5E8);
        c.drawText(
                Component.translatable("gui.ecc2.giant.port_access_title"),
                MODAL_X + 6,
                MODAL_Y + 5,
                0xFFEAF6FF,
                false);

        // Close [X] button
        boolean closeHover = inside(relMouseX, relMouseY, CLOSE_X, CLOSE_Y, 12, 12);
        c.fill(
                CLOSE_X,
                CLOSE_Y,
                CLOSE_X + 12,
                CLOSE_Y + 12,
                closeHover ? 0xFFB83A3A : 0xFF3B4958);
        c.drawCenteredText("X", CLOSE_X + 6, CLOSE_Y + 2, 0xFFFFFFFF);

        // Central 3x3 top-down diagram body
        int boxX = MODAL_X + 54;
        int boxY = MODAL_Y + 62;
        int boxW = 84;
        int boxH = 50;
        c.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF56697D);
        c.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF263340);
        c.drawCenteredText("3x3x3", boxX + boxW / 2, boxY + 14, 0xFF9AC5DC);
        c.drawScaledText("FRONT", boxX + 28, boxY + 3, 0.72F, 0xFF7BE8FF);
        c.drawScaledText("BACK", boxX + 31, boxY + 41, 0.72F, 0xFF8AA3B8);

        // 8 clickable lower-corner port tiles
        for (PortTile tile : PORT_TILES) {
            PortAccessMode mode = menu.portMode(tile.port());
            boolean hovered = inside(relMouseX, relMouseY, tile.x(), tile.y(), TILE_W, TILE_H);
            int borderColor = hovered ? 0xFFFFFFFF : modeColor(mode);
            c.fill(tile.x(), tile.y(), tile.x() + TILE_W, tile.y() + TILE_H, borderColor);
            c.fill(
                    tile.x() + 1,
                    tile.y() + 1,
                    tile.x() + TILE_W - 1,
                    tile.y() + TILE_H - 1,
                    hovered ? 0xFF253749 : 0xFF1A2734);

            // Port icon sprite (12x12 from giant_machine.png y=238)
            int spriteU =
                    switch (mode) {
                        case FULL -> 70;
                        case INPUT -> 84;
                        case OUTPUT -> 98;
                        case BLOCKED -> 112;
                    };
            c.blit(TEX_GUI, tile.x() + 2, tile.y() + 5, spriteU, 238, 12, 12);
            c.drawScaledText(tile.shortCode(), tile.x() + 16, tile.y() + 8, 0.78F, 0xFFEAF6FF);
        }

        // Legend bar at bottom of modal
        int ly = MODAL_Y + MODAL_H - 18;
        c.fill(MODAL_X + 6, ly - 3, MODAL_X + MODAL_W - 6, ly - 2, 0xFF33475B);
        drawLegendSwatch(c, MODAL_X + 8, ly, PortAccessMode.FULL, "FULL");
        drawLegendSwatch(c, MODAL_X + 54, ly, PortAccessMode.INPUT, "IN");
        drawLegendSwatch(c, MODAL_X + 94, ly, PortAccessMode.OUTPUT, "OUT");
        drawLegendSwatch(c, MODAL_X + 138, ly, PortAccessMode.BLOCKED, "OFF");
    }

    private static void drawLegendSwatch(
            Ecc2Canvas c, int x, int y, PortAccessMode mode, String label) {
        c.fill(x, y + 2, x + 7, y + 9, modeColor(mode));
        c.drawScaledText(label, x + 10, y + 3, 0.72F, 0xFFD0E2F0);
    }

    @Override
    protected void drawTooltips(
            Ecc2Canvas c, int relMouseX, int relMouseY, int screenX, int screenY) {
        if (portModalOpen) {
            for (PortTile tile : PORT_TILES) {
                if (inside(relMouseX, relMouseY, tile.x(), tile.y(), TILE_W, TILE_H)) {
                    PortAccessMode mode = menu.portMode(tile.port());
                    c.renderTooltip(
                            List.of(
                                    Component.translatable(tile.langKey()),
                                    Component.translatable(modeLangKey(mode)),
                                    Component.translatable("gui.ecc2.giant.port_access_hint")),
                            screenX,
                            screenY);
                    return;
                }
            }
            return;
        }

        // Gear button tooltip
        if (inside(relMouseX, relMouseY, GEAR_X, GEAR_Y, GEAR_W, GEAR_H)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable("gui.ecc2.giant.port_access_button"),
                            Component.translatable("gui.ecc2.giant.port_access_hint")),
                    screenX,
                    screenY);
            return;
        }

        // EU core bar tooltip [80, 25, 14, 52]
        if (inside(relMouseX, relMouseY, 80, 25, 14, 52)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.giant.eu_stored",
                                    formatNumber(menu.storedEu()),
                                    formatNumber(menu.energyCapacity())),
                            Component.translatable(
                                    "gui.ecc2.giant.tier",
                                    menu.inputTier(),
                                    formatNumber(menu.maxInputVoltage())),
                            Component.translatable(
                                    "gui.ecc2.giant.eu_draw",
                                    String.format(
                                            java.util.Locale.ROOT, "%.1f", menu.euDrawPerTick()))),
                    screenX,
                    screenY);
            return;
        }

        // Water tank tooltip [128, 23, 20, 76]
        if (menu.usesWater() && inside(relMouseX, relMouseY, 128, 23, 20, 76)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.giant.water",
                                    formatNumber(menu.waterMb()),
                                    formatNumber(menu.waterCapacityMb()))),
                    screenX,
                    screenY);
            return;
        }

        // Active batch LCD tooltip [99, 23, 26, 16]
        if (inside(relMouseX, relMouseY, 99, 23, 26, 16)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.giant.active_ops", menu.activeTotalBatch())),
                    screenX,
                    screenY);
        }
    }

    @Override
    protected boolean handleMouseClick(int relX, int relY, int button) {
        if (button != 0 && button != 1) {
            return portModalOpen;
        }
        if (portModalOpen) {
            // Close button or click outside modal
            if (inside(relX, relY, CLOSE_X, CLOSE_Y, 12, 12)
                    || !inside(relX, relY, MODAL_X, MODAL_Y, MODAL_W, MODAL_H)) {
                portModalOpen = false;
                return true;
            }
            // Click one of the 8 lower-corner port tiles
            for (PortTile tile : PORT_TILES) {
                if (inside(relX, relY, tile.x(), tile.y(), TILE_W, TILE_H)) {
                    int modeArg = -1;
                    if (button == 1) {
                        // Right-click cycles backwards
                        PortAccessMode cur = menu.portMode(tile.port());
                        modeArg = (cur.code() + 3) & 3;
                    }
                    GiantGasClientPlatform.sendToServer(
                            new GiantPortTogglePayload(
                                    menu.corePos(), tile.port().ordinal(), modeArg));
                    return true;
                }
            }
            return true;
        }

        if (inside(relX, relY, GEAR_X, GEAR_Y, GEAR_W, GEAR_H)) {
            portModalOpen = true;
            return true;
        }
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int modeColor(PortAccessMode mode) {
        return switch (mode) {
            case FULL -> 0xFF34D8EB;
            case INPUT -> 0xFF3E8BFF;
            case OUTPUT -> 0xFFFF9838;
            case BLOCKED -> 0xFF6B7785;
        };
    }

    private static String modeLangKey(PortAccessMode mode) {
        return switch (mode) {
            case FULL -> "gui.ecc2.port.mode.full";
            case INPUT -> "gui.ecc2.port.mode.input";
            case OUTPUT -> "gui.ecc2.port.mode.output";
            case BLOCKED -> "gui.ecc2.port.mode.blocked";
        };
    }

    private static String formatNumber(int n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    private static String formatCompact(int n) {
        if (n >= 1_000_000) {
            return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fk", n / 1_000.0);
        }
        return String.valueOf(n);
    }
}
