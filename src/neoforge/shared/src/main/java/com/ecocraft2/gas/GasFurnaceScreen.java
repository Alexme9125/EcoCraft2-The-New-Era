package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.giant.Ecc2Canvas;
import com.ecocraft2.giant.Ecc2ScreenBase;
import com.ecocraft2.giant.GiantGasPlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for {@code ecc2:gas_furnace} ({@code gas_furnace.png}, 176x166). */
public final class GasFurnaceScreen extends Ecc2ScreenBase<GasFurnaceMenu> {

    private static final String TEX = "textures/gui/gas_furnace.png";

    public GasFurnaceScreen(GasFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void drawBackground(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.blit(TEX, 0, 0, 0, 0, 176, 166);

        // Gas tank [16, 22, 16, 50]
        GasFuelSpec fuel = menu.currentFuel();
        int gasH = (menu.gasMb() * 50) / GasFurnaceEngine.GAS_TANK_CAPACITY_MB;
        if (gasH > 0 && fuel != null) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(fuel.fluidId()),
                    16,
                    22 + (50 - gasH),
                    16,
                    gasH);
        }
        c.blit(TEX, 16, 22, 180, 36, 16, 50);

        // Blue gas flame [68, 41, 14, 14]
        if (menu.isMachineActive()) {
            c.blit(TEX, 68, 41, 180, 0, 14, 14);
        }

        // Progress arrow [90, 36, 24, 15]
        int arrowW = (menu.progressPermille() * 24) / 1000;
        if (arrowW > 0) {
            c.blit(TEX, 90, 36, 180, 18, arrowW, 15);
        }
    }

    @Override
    protected void drawLabels(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.drawCenteredText(title, 88, 6, 0xFF39424F);
    }

    @Override
    protected void drawTooltips(
            Ecc2Canvas c, int relMouseX, int relMouseY, int screenX, int screenY) {
        if (relMouseX >= 14 && relMouseX < 34 && relMouseY >= 20 && relMouseY < 74) {
            GasFuelSpec fuel = menu.currentFuel();
            Component name =
                    fuel == null
                            ? Component.translatable("gui.ecc2.gas.empty")
                            : fuelName(fuel);
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.gas.tank",
                                    name,
                                    menu.gasMb(),
                                    GasFurnaceEngine.GAS_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        }
    }

    static Component fuelName(GasFuelSpec fuel) {
        return switch (fuel) {
            case WATER_GAS -> Component.translatable("gui.ecc2.gas.water_gas");
            case NATURAL_GAS -> Component.translatable("gui.ecc2.gas.natural_gas");
            case BIOGAS -> Component.translatable("gui.ecc2.gas.biogas");
        };
    }
}
