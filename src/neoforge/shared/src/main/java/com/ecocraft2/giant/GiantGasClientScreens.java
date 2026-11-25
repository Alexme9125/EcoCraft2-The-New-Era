package com.ecocraft2.giant;

import com.ecocraft2.gas.CultureIncubatorScreen;
import com.ecocraft2.gas.GasFurnaceScreen;
import com.ecocraft2.gas.GasGeneratorScreen;
import com.ecocraft2.gas.OrganicFermenterScreen;
import com.ecocraft2.gas.WaterGasMachineScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Isolated client-only screen registration so dedicated servers never touch client Screen classes.
 */
final class GiantGasClientScreens {

    private GiantGasClientScreens() {}

    static void install(IEventBus modBus) {
        modBus.addListener(
                RegisterMenuScreensEvent.class,
                event -> {
                    event.register(GiantGasModule.GIANT_MACHINE_MENU.get(), GiantMachineScreen::new);
                    event.register(
                            GiantGasModule.WATER_GAS_MACHINE_MENU.get(),
                            WaterGasMachineScreen::new);
                    event.register(GiantGasModule.GAS_FURNACE_MENU.get(), GasFurnaceScreen::new);
                    event.register(
                            GiantGasModule.GAS_GENERATOR_MENU.get(), GasGeneratorScreen::new);
                    event.register(
                            GiantGasModule.CULTURE_INCUBATOR_MENU.get(),
                            CultureIncubatorScreen::new);
                    event.register(
                            GiantGasModule.ORGANIC_FERMENTER_MENU.get(),
                            OrganicFermenterScreen::new);
                });
    }
}
