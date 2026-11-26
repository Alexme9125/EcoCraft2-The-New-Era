package com.ecocraft2.solar;

import com.ecocraft2.kinetic.AdvancedKineticScreen;
import com.ecocraft2.kinetic.AdvancedRotorRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Isolated client-only NeoForge 26.x bindings for {@link SolarKineticModule}. */
public final class SolarClientPlatform {

    private SolarClientPlatform() {}

    public static boolean hasShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    public static void sendToServer(CustomPacketPayload payload) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null && listener.isAcceptingMessages()) {
            listener.send(payload);
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
        // Cache synced Molecular Transformer recipes on the client for JEI / EMI compat.
        gameBus.addListener(
                RecipesReceivedEvent.class,
                event -> {
                    var type = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
                    var synced = new ArrayList<SolarKineticModule.MatchedMtRecipe>();
                    for (RecipeHolder<MolecularTransformerRecipe> holder :
                            event.getRecipeMap().byType(type)) {
                        if (holder.value().isAvailable()) {
                            synced.add(
                                    new SolarKineticModule.MatchedMtRecipe(
                                            holder.id().identifier().toString(), holder.value()));
                        }
                    }
                    SolarPlatform.setClientMtRecipes(List.copyOf(synced));
                });
    }
}
