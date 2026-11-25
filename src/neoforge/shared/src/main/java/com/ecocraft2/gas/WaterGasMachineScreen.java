package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.giant.Ecc2Canvas;
import com.ecocraft2.giant.Ecc2ScreenBase;
import com.ecocraft2.giant.GiantGasPlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for {@code ecc2:water_gas_machine} ({@code water_gas_machine.png}, 176x180). */
public final class WaterGasMachineScreen extends Ecc2ScreenBase<WaterGasMachineMenu> {

    private static final String TEX = "textures/gui/water_gas_machine.png";

    public WaterGasMachineScreen(
            WaterGasMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 180);
    }

    @Override
    protected void drawBackground(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.blit(TEX, 0, 0, 0, 0, 176, 180);

        // Water tank [14, 22, 16, 50]
        int waterH = (menu.waterMb() * 50) / WaterGasEngine.WATER_TANK_CAPACITY_MB;
        if (waterH > 0) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(Ecc2Ids.MINECRAFT_WATER_ID),
                    14,
                    22 + (50 - waterH),
                    16,
                    waterH);
        }
        c.blit(TEX, 14, 22, 180, 68, 16, 50);

        // Water gas tank [108, 22, 16, 50]
        int gasH = (menu.waterGasMb() * 50) / WaterGasEngine.GAS_TANK_CAPACITY_MB;
        if (gasH > 0) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(Ecc2Ids.WATER_GAS_ID),
                    108,
                    22 + (50 - gasH),
                    16,
                    gasH);
        }
        c.blit(TEX, 108, 22, 180, 68, 16, 50);

        // Solar status icon [84, 22, 14, 14]
        c.blit(TEX, 84, 22, 180, menu.isSolarActive() ? 34 : 50, 14, 14);

        // Progress arrow [60, 46, 24, 15]
        int arrowW = (menu.progressPermille() * 24) / 1000;
        if (arrowW > 0) {
            c.blit(TEX, 60, 46, 180, 0, arrowW, 15);
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
        if (inside(relMouseX, relMouseY, 12, 20, 20, 54)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.gas.tank",
                                    Component.translatable("gui.ecc2.gas.water"),
                                    menu.waterMb(),
                                    WaterGasEngine.WATER_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        } else if (inside(relMouseX, relMouseY, 106, 20, 20, 54)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.gas.tank",
                                    Component.translatable("gui.ecc2.gas.water_gas"),
                                    menu.waterGasMb(),
                                    WaterGasEngine.GAS_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        } else if (inside(relMouseX, relMouseY, 84, 22, 14, 14)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    menu.isSolarActive()
                                            ? "gui.ecc2.gas.solar_active"
                                            : "gui.ecc2.gas.solar_inactive")),
                    screenX,
                    screenY);
        } else if (inside(relMouseX, relMouseY, 62, 65, 14, 14)) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.giant.eu_stored",
                                    menu.storedEu(),
                                    menu.energyCapacity())),
                    screenX,
                    screenY);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
