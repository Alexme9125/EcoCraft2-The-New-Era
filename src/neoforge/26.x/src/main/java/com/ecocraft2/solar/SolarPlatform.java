package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.kinetic.AdvancedKineticScreen;
import com.ecocraft2.kinetic.AdvancedRotorRenderer;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachinePorts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/** NeoForge 26.x platform bindings for {@link SolarKineticModule}. */
public final class SolarPlatform {

    private static volatile List<SolarKineticModule.MatchedMtRecipe> clientMtRecipes = List.of();

    private SolarPlatform() {}

    public static void registerArmorMaterials(IEventBus modBus) {
        // Equipment layers are data-driven via assets/ecc2/equipment/*.json in 26.x.
    }

    public static boolean isClient(Level level) {
        return level.isClientSide();
    }

    public static boolean isDay(Level level) {
        return level.isBrightOutside();
    }

    public static boolean hasShiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Identifier key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    public static boolean isInItemTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isEmpty()) {
            return false;
        }
        Identifier key = Identifier.tryParse(tagId);
        return key != null && stack.is(TagKey.create(Registries.ITEM, key));
    }

    public static ItemStack firstItemInTag(String tagId, int count) {
        Identifier key = Identifier.tryParse(tagId);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, key);
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            Item item = holder.value();
            if (item != Items.AIR) {
                return new ItemStack(item, Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> allItemsInTag(String tagId, int count) {
        Identifier key = Identifier.tryParse(tagId);
        if (key == null) {
            return List.of();
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, key);
        var result = new ArrayList<ItemStack>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            Item item = holder.value();
            if (item != Items.AIR) {
                result.add(new ItemStack(item, Math.max(1, count)));
            }
        }
        return result;
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(
            String path) {
        return new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null && listener.isAcceptingMessages()) {
            listener.send(payload);
        }
    }

    public static AttributeModifier armorModifier(String path, double amount) {
        return new AttributeModifier(
                Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, path),
                amount,
                AttributeModifier.Operation.ADD_VALUE);
    }

    public static <T extends Block> DeferredBlock<T> registerBlock(
            DeferredRegister.Blocks blocks,
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            Supplier<BlockBehaviour.Properties> props) {
        return blocks.registerBlock(path, factory, props);
    }

    public static DeferredItem<Item> registerSimpleItem(DeferredRegister.Items items, String path) {
        return items.registerSimpleItem(path, Item.Properties::new);
    }

    public static DeferredItem<SolarHelmetItem> registerHelmet(
            DeferredRegister.Items items, String path, SolarHelmetSpec spec) {
        String assetName =
                switch (spec) {
                    case ADVANCED -> "advanced_solar";
                    case HYBRID -> "hybrid_solar";
                    case ULTIMATE_HYBRID -> "ultimate_hybrid_solar";
                };
        var assetKey =
                ResourceKey.create(
                        EquipmentAssets.ROOT_ID,
                        Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, assetName));
        return items.registerItem(
                path,
                props ->
                        new SolarHelmetItem(
                                spec,
                                props.stacksTo(1)
                                        .component(
                                                DataComponents.EQUIPPABLE,
                                                Equippable.builder(EquipmentSlot.HEAD)
                                                        .setEquipSound(
                                                                SoundEvents.ARMOR_EQUIP_DIAMOND)
                                                        .setAsset(assetKey)
                                                        .build())));
    }

    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return new BlockEntityType<>(factory, blocks);
    }

    public static <T extends net.minecraft.world.item.crafting.Recipe<?>>
            RecipeType<T> createRecipeType(String path) {
        return RecipeType.simple(Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static RecipeSerializer<MolecularTransformerRecipe>
            createMolecularTransformerSerializer() {
        return new RecipeSerializer<>(
                MolecularTransformerRecipe.MAP_CODEC, MolecularTransformerRecipe.STREAM_CODEC);
    }

    public static void registerPayloads(IEventBus modBus) {
        modBus.addListener(
                RegisterPayloadHandlersEvent.class,
                event -> {
                    var registrar = event.registrar("1").executesOn(HandlerThread.MAIN);
                    registrar.playToServer(
                            QuantumGeneratorButtonPayload.TYPE,
                            QuantumGeneratorButtonPayload.STREAM_CODEC,
                            QuantumGeneratorButtonPayload::handleServer);
                });
    }

    public static void registerCapabilities(IEventBus modBus) {
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event -> {
                    event.registerBlockEntity(
                            Capabilities.Item.BLOCK,
                            SolarKineticModule.SOLAR_PANEL_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                    event.registerBlockEntity(
                            Capabilities.Item.BLOCK,
                            SolarKineticModule.MOLECULAR_TRANSFORMER_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                    event.registerBlockEntity(
                            Capabilities.Item.BLOCK,
                            SolarKineticModule.KINETIC_GENERATOR_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                });
    }

    private static ResourceHandler<ItemResource> wrapWorldly(BlockEntity be, Direction side) {
        if (!(be instanceof WorldlyContainer container) || !MachineAccess.connects(be, side)) {
            return null;
        }
        return MachinePorts.item(
                be,
                side,
                new WorldlyContainerWrapper(container, side == null ? Direction.UP : side));
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
                    clientMtRecipes = List.copyOf(synced);
                });
    }

    public static void registerRecipeSyncAndFilter(IEventBus gameBus) {
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event ->
                        event.sendRecipes(
                                SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get()));
    }

    public static SolarKineticModule.MatchedMtRecipe findMolecularTransformerRecipe(
            ServerLevel level, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        var type = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
        for (RecipeHolder<MolecularTransformerRecipe> holder :
                level.recipeAccess().recipeMap().byType(type)) {
            MolecularTransformerRecipe recipe = holder.value();
            if (recipe.matchesStack(input)) {
                return new SolarKineticModule.MatchedMtRecipe(
                        holder.id().identifier().toString(), recipe);
            }
        }
        return null;
    }

    public static List<SolarKineticModule.MatchedMtRecipe> allMolecularTransformerRecipes(
            Level level) {
        if (level instanceof ServerLevel server) {
            var type = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
            var out = new ArrayList<SolarKineticModule.MatchedMtRecipe>();
            for (RecipeHolder<MolecularTransformerRecipe> holder :
                    server.recipeAccess().recipeMap().byType(type)) {
                if (holder.value().isAvailable()) {
                    out.add(
                            new SolarKineticModule.MatchedMtRecipe(
                                    holder.id().identifier().toString(), holder.value()));
                }
            }
            return out;
        }
        return clientMtRecipes;
    }
}
