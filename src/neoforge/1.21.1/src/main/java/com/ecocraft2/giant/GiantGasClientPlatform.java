package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.gas.Ecc2FluidSpec;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Isolated client-only NeoForge 1.21.1 bindings for {@code com.ecocraft2.giant} and {@code gas}. */
public final class GiantGasClientPlatform {

    private GiantGasClientPlatform() {}

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void registerFluidClientExtensions(IEventBus modBus, List<Ecc2FluidSpec> specs) {
        modBus.addListener(
                RegisterClientExtensionsEvent.class,
                event -> {
                    for (var spec : specs) {
                        event.registerFluidType(
                                new IClientFluidTypeExtensions() {
                                    @Override
                                    public ResourceLocation getStillTexture() {
                                        return ResourceLocation.fromNamespaceAndPath(
                                                EcoCraft2.MOD_ID, spec.stillTexturePath());
                                    }

                                    @Override
                                    public ResourceLocation getFlowingTexture() {
                                        return ResourceLocation.fromNamespaceAndPath(
                                                EcoCraft2.MOD_ID, spec.flowTexturePath());
                                    }

                                    @Override
                                    public int getTintColor() {
                                        return spec.tintArgb();
                                    }
                                },
                                spec.fluidType().get());
                    }
                });
    }
}
