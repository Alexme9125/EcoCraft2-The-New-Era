package com.ecocraft2.solar;

import com.ecocraft2.common.solar.SkyCondition;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pixel-aligned GUI screen for all four Advanced Solar Panels 2 tiers, matching
 * {@code assets-gen/gui_coordinates.json} ({@code solar_panel.png}, 194×168).
 */
public final class SolarPanelScreen extends Ecc2ScreenBase<SolarPanelMenu> {

    private static final String TEX = "solar_panel";
    private static final int PANEL_W = 194;
    private static final int PANEL_H = 168;

    private static final int SKY_X = 24;
    private static final int SKY_Y = 32;
    private static final int SKY_SIZE = 14;

    private static final int BAR_X = 46;
    private static final int BAR_Y = 25;
    private static final int BAR_W = 24;
    private static final int BAR_H = 14;

    private static final int TEXT_X = 76;
    private static final int TEXT_MAX_W = 94;

    public SolarPanelScreen(SolarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void drawBackground(
            Ecc2GuiCanvas canvas, int relMouseX, int relMouseY, float partialTick) {
        canvas.blit(TEX, 0, 0, 0, 0, PANEL_W, PANEL_H);

        // Sky condition icon (14x14)
        int iconV =
                switch (menu.skyCondition()) {
                    case DAY -> 16;
                    case NIGHT -> 32;
                    case RAIN -> 48;
                    case NONE -> 64;
                };
        canvas.blit(TEX, SKY_X, SKY_Y, 196, iconV, SKY_SIZE, SKY_SIZE);

        // Energy bar fill (24x14)
        int filled = menu.scaledEnergy(BAR_W);
        if (filled > 0) {
            canvas.blit(TEX, BAR_X, BAR_Y, 196, 0, filled, BAR_H);
        }
    }

    @Override
    protected void drawForeground(Ecc2GuiCanvas canvas, int relMouseX, int relMouseY) {
        canvas.centeredText(title, PANEL_W / 2, 6, 0xFF283240, false);

        String storageText =
                canvas.trimWidth(
                        Ecc2DataSync.formatCompact(menu.storedEu())
                                + "/"
                                + Ecc2DataSync.formatCompact(menu.maxStorage())
                                + " EU",
                        TEXT_MAX_W);
        String outText =
                canvas.trimWidth(
                        "Max: "
                                + Ecc2DataSync.formatCompact(menu.maxOutputEu())
                                + " EU/t (T"
                                + menu.outputTier()
                                + ")",
                        TEXT_MAX_W);
        String genText =
                canvas.trimWidth(
                        "Gen: " + Ecc2DataSync.formatInt(menu.generatingEu()) + " EU/t",
                        TEXT_MAX_W);

        canvas.text(Component.literal(storageText), TEXT_X, 24, 0xFF7BE8F5, false);
        canvas.text(Component.literal(outText), TEXT_X, 34, 0xFFB8D4E4, false);
        canvas.text(
                Component.literal(genText),
                TEXT_X,
                44,
                menu.generatingEu() > 0 ? 0xFF72F2A8 : 0xFFE87878,
                false);
    }

    @Override
    protected List<Component> hoverTooltip(int relMouseX, int relMouseY) {
        if (relMouseX >= SKY_X
                && relMouseX < SKY_X + SKY_SIZE
                && relMouseY >= SKY_Y
                && relMouseY < SKY_Y + SKY_SIZE) {
            String key =
                    switch (menu.skyCondition()) {
                        case DAY -> "gui.ecc2.solar.day";
                        case NIGHT -> "gui.ecc2.solar.night";
                        case RAIN -> "gui.ecc2.solar.rain";
                        case NONE -> "gui.ecc2.solar.blocked";
                    };
            return List.of(
                    Component.translatable(key),
                    Component.translatable(
                            "gui.ecc2.solar.generating",
                            Ecc2DataSync.formatInt(menu.generatingEu())));
        }
        if (relMouseX >= BAR_X
                && relMouseX < BAR_X + BAR_W
                && relMouseY >= BAR_Y
                && relMouseY < BAR_Y + BAR_H) {
            return List.of(
                    Component.translatable(
                            "gui.ecc2.solar.storage",
                            Ecc2DataSync.formatInt(menu.storedEu()),
                            Ecc2DataSync.formatInt(menu.maxStorage())),
                    Component.translatable(
                            "gui.ecc2.solar.max_output",
                            Ecc2DataSync.formatInt(Math.round(menu.maxOutputEu())),
                            menu.outputTier()),
                    Component.translatable(
                            "gui.ecc2.solar.generating",
                            Ecc2DataSync.formatInt(menu.generatingEu())));
        }
        return List.of();
    }
}
