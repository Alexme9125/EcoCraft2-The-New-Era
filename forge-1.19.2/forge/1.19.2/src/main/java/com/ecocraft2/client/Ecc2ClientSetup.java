package com.ecocraft2.client;

import com.ecocraft2.gas.GasRegistry;
import com.ecocraft2.giant.GiantRegistry;
import com.ecocraft2.solar.DyeableSolarHelmetItem;
import com.ecocraft2.solar.SolarKineticRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only bootstrap for EcoCraft 2 Forge 1.19.2 (10 screens, 2 3D BERs, helmet dye colors, fluid layers). */
public final class Ecc2ClientSetup {

    private Ecc2ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(Ecc2ClientSetup::onClientSetup);
        modBus.addListener(Ecc2ClientSetup::onRegisterRenderers);
        modBus.addListener(Ecc2ClientSetup::onRegisterItemColors);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> {
                    MenuScreens.register(
                            SolarKineticRegistry.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
                    MenuScreens.register(
                            SolarKineticRegistry.QUANTUM_GENERATOR_MENU.get(),
                            QuantumGeneratorScreen::new);
                    MenuScreens.register(
                            SolarKineticRegistry.MOLECULAR_TRANSFORMER_MENU.get(),
                            MolecularTransformerScreen::new);
                    MenuScreens.register(
                            SolarKineticRegistry.ADVANCED_KINETIC_MENU.get(),
                            AdvancedKineticScreen::new);
                    MenuScreens.register(
                            GiantRegistry.GIANT_MACHINE_MENU.get(), GiantMachineScreen::new);
                    MenuScreens.register(
                            GasRegistry.WATER_GAS_MACHINE_MENU.get(),
                            GasScreens.WaterGasScreen::new);
                    MenuScreens.register(
                            GasRegistry.GAS_FURNACE_MENU.get(), GasScreens.GasFurnaceScreen::new);
                    MenuScreens.register(
                            GasRegistry.GAS_GENERATOR_MENU.get(),
                            GasScreens.GasGeneratorScreen::new);
                    MenuScreens.register(
                            GasRegistry.CULTURE_INCUBATOR_MENU.get(),
                            GasScreens.CultureIncubatorScreen::new);
                    MenuScreens.register(
                            GasRegistry.ORGANIC_FERMENTER_MENU.get(),
                            GasScreens.OrganicFermenterScreen::new);

                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.WATER_GAS.get(), RenderType.translucent());
                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.FLOWING_WATER_GAS.get(), RenderType.translucent());
                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.NATURAL_GAS.get(), RenderType.translucent());
                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.FLOWING_NATURAL_GAS.get(), RenderType.translucent());
                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.CULTURE_MEDIUM.get(), RenderType.translucent());
                    ItemBlockRenderTypes.setRenderLayer(
                            GasRegistry.FLOWING_CULTURE_MEDIUM.get(), RenderType.translucent());
                });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                SolarKineticRegistry.MOLECULAR_TRANSFORMER_BE.get(),
                MolecularTransformerRenderer::new);
        event.registerBlockEntityRenderer(
                SolarKineticRegistry.ADVANCED_KINETIC_BE.get(), AdvancedRotorRenderer::new);
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) ->
                        tintIndex == 0 && stack.getItem() instanceof DyeableSolarHelmetItem dyeable
                                ? dyeable.getColor(stack)
                                : 0xFFFFFF,
                SolarKineticRegistry.HYBRID_SOLAR_HELMET.get(),
                SolarKineticRegistry.ULTIMATE_HYBRID_SOLAR_HELMET.get());
    }
}
