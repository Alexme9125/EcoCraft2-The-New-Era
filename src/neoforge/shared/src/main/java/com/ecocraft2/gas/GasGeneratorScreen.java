package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.giant.Ecc2Canvas;
import com.ecocraft2.giant.Ecc2ScreenBase;
import com.ecocraft2.giant.GiantGasPlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI screen for {@code ecc2:gas_generator} ({@code gas_generator.png}, 176x166). */
public final class GasGeneratorScreen extends Ecc2ScreenBase<GasGeneratorMenu> {

    private static final String TEX = "textures/gui/gas_generator.png";

    public GasGeneratorScreen(GasGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void drawBackground(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.blit(TEX, 0, 0, 0, 0, 176, 166);

        // Gas tank [20, 22, 16, 50]
        GasFuelSpec fuel = menu.currentFuel();
        int gasH = (menu.gasMb() * 50) / GasGeneratorEngine.GAS_TANK_CAPACITY_MB;
        if (gasH > 0 && fuel != null) {
            c.drawFluid(
                    GiantGasPlatform.fluidRegistryId(fuel.fluidId()),
                    20,
                    22 + (50 - gasH),
                    16,
                    gasH);
        }
        c.blit(TEX, 20, 22, 180, 36, 16, 50);

        // Flame [76, 36, 14, 14]
        if (menu.isMachineActive()) {
            c.blit(TEX, 76, 36, 180, 0, 14, 14);
        }

        // EU bar [98, 24, 24, 14]
        int euW = (menu.storedEu() * 24) / (int) GasGeneratorEngine.EU_CAPACITY;
        if (euW > 0) {
            c.blit(TEX, 98, 24, 180, 18, euW, 14);
        }
    }

    @Override
    protected void drawLabels(Ecc2Canvas c, int relMouseX, int relMouseY) {
        c.drawCenteredText(title, 88, 6, 0xFF39424F);

        // LCD readout [72, 54, 86, 22]
        GasFuelSpec fuel = menu.currentFuel();
        String fuelLabel =
                fuel == null
                        ? Component.translatable("gui.ecc2.gas.empty").getString()
                        : GasFurnaceScreen.fuelName(fuel).getString();
        c.drawScaledText(fuelLabel, 75, 57, 0.72F, 0xFF7CEEFF);
        c.drawScaledText(menu.generatingEuPerTick() + " EU/t  (MV)", 75, 66, 0.72F, 0xFFA4F9C8);
    }

    @Override
    protected void drawTooltips(
            Ecc2Canvas c, int relMouseX, int relMouseY, int screenX, int screenY) {
        if (relMouseX >= 18 && relMouseX < 38 && relMouseY >= 20 && relMouseY < 74) {
            GasFuelSpec fuel = menu.currentFuel();
            Component name =
                    fuel == null
                            ? Component.translatable("gui.ecc2.gas.empty")
                            : GasFurnaceScreen.fuelName(fuel);
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.gas.tank",
                                    name,
                                    menu.gasMb(),
                                    GasGeneratorEngine.GAS_TANK_CAPACITY_MB)),
                    screenX,
                    screenY);
        } else if (relMouseX >= 98 && relMouseX < 122 && relMouseY >= 24 && relMouseY < 38) {
            c.renderTooltip(
                    List.of(
                            Component.translatable(
                                    "gui.ecc2.giant.eu_stored",
                                    menu.storedEu(),
                                    (int) GasGeneratorEngine.EU_CAPACITY),
                            Component.translatable(
                                    "gui.ecc2.gas.output", menu.generatingEuPerTick())),
                    screenX,
                    screenY);
        }
    }
}
