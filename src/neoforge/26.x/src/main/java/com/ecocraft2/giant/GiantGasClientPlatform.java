package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.gas.Ecc2FluidSpec;
import java.util.List;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Isolated client-only NeoForge 26.x bindings for {@code com.ecocraft2.giant} and {@code gas}. */
public final class GiantGasClientPlatform {

    private GiantGasClientPlatform() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    public static void registerFluidClientExtensions(IEventBus modBus, List<Ecc2FluidSpec> specs) {
        modBus.addListener(
                RegisterFluidModelsEvent.class,
                event -> {
                    for (var spec : specs) {
                        var still =
                                new Material(
                                        Identifier.fromNamespaceAndPath(
                                                EcoCraft2.MOD_ID, spec.stillTexturePath()));
                        var flow =
                                new Material(
                                        Identifier.fromNamespaceAndPath(
                                                EcoCraft2.MOD_ID, spec.flowTexturePath()));
                        var model =
                                new FluidModel.Unbaked(
                                        still,
                                        flow,
                                        null,
                                        (FluidTintSource) state -> spec.tintArgb());
                        event.register(model, spec.sourceFluid(), spec.flowingFluid());
                    }
                });
    }
}
