package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.giant.Ecc2Canvas;
import com.ecocraft2.giant.Ecc2ScreenBase;
import com.ecocraft2.giant.GiantGasPlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for {@code ecc2:culture_incubator} ({@code culture_incubator.png}, 176x180). */
public final class CultureIncubatorScreen extends Ecc2ScreenBase<CultureIncubatorMenu> {

    private static final String TEX = "textures/gui/culture_incubator.png";

    public CultureIncubatorScreen(
            CultureIncubatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 180);
    }

    @Override
    protected void drawBackground(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.blit(TEX, 0, 0, 0, 0, 176, 180);

        // Water tank [14, 22, 16, 50]
        int waterH = (menu.waterMb() * 50) / CultureIncubatorEngine.WATER_TANK_CAPACITY_MB;
        if (waterH > 0) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(Ecc2Ids.MINECRAFT_WATER_ID),
                    14,
                    22 + (50 - waterH),
                    16,
                    waterH);
        }
        c.blit(TEX, 14, 22, 180, 36, 16, 50);

        // Culture medium tank [108, 22, 16, 50]
        int cultureH = (menu.cultureMb() * 50) / CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB;
        if (cultureH > 0) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(Ecc2Ids.CULTURE_MEDIUM_ID),
                    108,
                    22 + (50 - cultureH),
                    16,
                    cultureH);
        }
        c.blit(TEX, 108, 22, 180, 36, 16, 50);

        // Progress arrow [64, 46, 24, 15]
        int arrowW = (menu.progressPermille() * 24) / 1000;
        if (arrowW > 0) {
            c.blit(TEX, 64, 46, 180, 0, arrowW, 15);
        }

        // Energy bolt [62, 65, 14, 14]
        int euH =
                Math.min(
                        14,
                        (int) (((long) menu.storedEu() * 14L) / Math.max(1, menu.energyCapacity())));
        if (euH > 0) {
            c.blit(TEX, 62, 65 + (14 - euH), 180, 18 + (14 - euH), 14, euH);
        }
    }

    @Override
    protected void drawLabels(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.drawCenteredText(title, 88, 6, 0xFF39424F);
    }

    @Override
    protected void drawTooltips(
            Ecc2Canvas c, int relMouseX, int relMouseY, int screenX, int screenY) {
        if (relMouseX >= 12 && relMouseX < 32 && relMouseY >= 20 && relMouseY < 74) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecera.gas.tank",
                                    Component.translatable("gui.ecera.gas.water"),
                                    menu.waterMb(),
                                    CultureIncubatorEngine.WATER_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        } else if (relMouseX >= 106 && relMouseX < 126 && relMouseY >= 20 && relMouseY < 74) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecera.gas.tank",
                                    Component.translatable("gui.ecera.gas.culture_medium"),
                                    menu.cultureMb(),
                                    CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        } else if (relMouseX >= 62 && relMouseX < 76 && relMouseY >= 65 && relMouseY < 79) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecera.giant.eu_stored",
                                    menu.storedEu(),
                                    menu.energyCapacity())),
                    screenX,
                    screenY);
        }
    }
}
