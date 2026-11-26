package com.ecocraft2.solar;

import com.ecocraft2.kinetic.AdvancedKineticScreen;
import com.ecocraft2.kinetic.AdvancedRotorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Isolated client-only NeoForge 1.21.1 bindings for {@link SolarKineticModule}. */
public final class SolarClientPlatform {

    private SolarClientPlatform() {}

    public static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public static void registerClient(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(
                RegisterMenuScreensEvent.class,
                event -> {
                    event.register(
                            SolarKineticModule.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
                    event.register(
                            SolarKineticModule.QUANTUM_GENERATOR_MENU.get(),
                            QuantumGeneratorScreen::new);
                    event.register(
                            SolarKineticModule.MOLECULAR_TRANSFORMER_MENU.get(),
                            MolecularTransformerScreen::new);
                    event.register(
                            SolarKineticModule.KINETIC_GENERATOR_MENU.get(),
                            AdvancedKineticScreen::new);
                });
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerBlockEntityRenderer(
                            SolarKineticModule.MOLECULAR_TRANSFORMER_BE.get(),
                            MolecularTransformerRenderer::new);
                    event.registerBlockEntityRenderer(
                            SolarKineticModule.KINETIC_GENERATOR_BE.get(),
                            AdvancedRotorRenderer::new);
                });
        modBus.addListener(
                RegisterColorHandlersEvent.Item.class,
                event ->
                        event.register(
                                (stack, tintIndex) -> {
                                    if (tintIndex != 0) {
                                        return 0xFFFFFFFF;
                                    }
                                    int rgb = DyedItemColor.getOrDefault(stack, -1);
                                    return rgb == -1 ? 0xFFFFFFFF : (0xFF000000 | (rgb & 0xFFFFFF));
                                },
                                SolarKineticModule.HYBRID_SOLAR_HELMET.get(),
                                SolarKineticModule.ULTIMATE_HYBRID_SOLAR_HELMET.get()));
        modBus.addListener(
                RegisterClientExtensionsEvent.class,
                event ->
                        event.registerItem(
                                new IClientItemExtensions() {
                                    @Override
                                    public int getDefaultDyeColor(ItemStack stack) {
                                        return 0xFFFFFFFF;
                                    }
                                },
                                SolarKineticModule.HYBRID_SOLAR_HELMET.get(),
                                SolarKineticModule.ULTIMATE_HYBRID_SOLAR_HELMET.get()));
    }
}
