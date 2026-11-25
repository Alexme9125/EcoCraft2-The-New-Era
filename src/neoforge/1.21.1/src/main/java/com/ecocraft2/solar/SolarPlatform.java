package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.kinetic.AdvancedKineticScreen;
import com.ecocraft2.kinetic.AdvancedRotorRenderer;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachinePorts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge 1.21.1 platform bindings for {@link SolarKineticModule}. */
public final class SolarPlatform {

    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, EcoCraft2.MOD_ID);

    private static final Map<SolarHelmetSpec, DeferredHolder<ArmorMaterial, ArmorMaterial>>
            HELMET_MATERIALS = new HashMap<>();

    static {
        HELMET_MATERIALS.put(
                SolarHelmetSpec.ADVANCED,
                ARMOR_MATERIALS.register(
                        "advanced_solar", () -> helmetMaterial("advanced_solar", false)));
        HELMET_MATERIALS.put(
                SolarHelmetSpec.HYBRID,
                ARMOR_MATERIALS.register(
                        "hybrid_solar", () -> helmetMaterial("hybrid_solar", true)));
        HELMET_MATERIALS.put(
                SolarHelmetSpec.ULTIMATE_HYBRID,
                ARMOR_MATERIALS.register(
                        "ultimate_hybrid_solar",
                        () -> helmetMaterial("ultimate_hybrid_solar", true)));
    }

    private SolarPlatform() {}

    private static ArmorMaterial helmetMaterial(String name, boolean dyeable) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, name);
        List<ArmorMaterial.Layer> layers =
                dyeable
                        ? List.of(
                                new ArmorMaterial.Layer(id, "", true),
                                new ArmorMaterial.Layer(id, "_overlay", false))
                        : List.of(new ArmorMaterial.Layer(id));
        return new ArmorMaterial(
                Map.of(),
                0,
                SoundEvents.ARMOR_EQUIP_DIAMOND,
                () -> Ingredient.EMPTY,
                layers,
                0.0F,
                0.0F);
    }

    public static void registerArmorMaterials(IEventBus modBus) {
        ARMOR_MATERIALS.register(modBus);
    }

    public static boolean isClient(Level level) {
        return level.isClientSide;
    }

    public static boolean isDay(Level level) {
        return level.isDay();
    }

    public static boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }

    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    public static boolean isInItemTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isEmpty()) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        return rl != null && stack.is(TagKey.create(Registries.ITEM, rl));
    }

    public static ItemStack firstItemInTag(String tagId, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            Item item = holder.value();
            if (item != Items.AIR) {
                return new ItemStack(item, Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }

    public static List<ItemStack> allItemsInTag(String tagId, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) {
            return List.of();
        }
        TagKey<Item> key = TagKey.create(Registries.ITEM, rl);
        var result = new ArrayList<ItemStack>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
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
                ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public static AttributeModifier armorModifier(String path, double amount) {
        return new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, path),
                amount,
                AttributeModifier.Operation.ADD_VALUE);
    }

    public static <T extends Block> DeferredBlock<T> registerBlock(
            DeferredRegister.Blocks blocks,
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            Supplier<BlockBehaviour.Properties> props) {
        return blocks.registerBlock(path, factory, props.get());
    }

    public static DeferredItem<Item> registerSimpleItem(DeferredRegister.Items items, String path) {
        return items.registerSimpleItem(path, new Item.Properties());
    }

    public static DeferredItem<SolarHelmetItem> registerHelmet(
            DeferredRegister.Items items, String path, SolarHelmetSpec spec) {
        return items.registerItem(
                path,
                props -> new SolarHelmetItem(spec, HELMET_MATERIALS.get(spec), props),
                new Item.Properties().stacksTo(1));
    }

    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    public static <T extends net.minecraft.world.item.crafting.Recipe<?>>
            RecipeType<T> createRecipeType(String path) {
        return RecipeType.simple(ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static RecipeSerializer<MolecularTransformerRecipe>
            createMolecularTransformerSerializer() {
        return new MolecularTransformerRecipe.Serializer();
    }

    public static void registerPayloads(IEventBus modBus) {
        modBus.addListener(
                RegisterPayloadHandlersEvent.class,
                event -> {
                    var registrar =
                            event.registrar("1").executesOn(HandlerThread.MAIN);
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
                            Capabilities.ItemHandler.BLOCK,
                            SolarKineticModule.SOLAR_PANEL_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                    event.registerBlockEntity(
                            Capabilities.ItemHandler.BLOCK,
                            SolarKineticModule.MOLECULAR_TRANSFORMER_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                    event.registerBlockEntity(
                            Capabilities.ItemHandler.BLOCK,
                            SolarKineticModule.KINETIC_GENERATOR_BE.get(),
                            (be, side) -> wrapWorldly(be, side));
                });
    }

    private static net.neoforged.neoforge.items.IItemHandler wrapWorldly(
            BlockEntity be, Direction side) {
        if (!(be instanceof WorldlyContainer container) || !MachineAccess.connects(be, side)) {
            return null;
        }
        return MachinePorts.item(
                be, side, new SidedInvWrapper(container, side == null ? Direction.UP : side));
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

    public static void registerRecipeSyncAndFilter(IEventBus gameBus) {
        gameBus.addListener(
                ServerStartedEvent.class, event -> filterDisabledRecipes(event.getServer()));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    if (event.getPlayer() == null) {
                        filterDisabledRecipes(event.getPlayerList().getServer());
                    }
                });
    }

    private static void filterDisabledRecipes(MinecraftServer server) {
        if (server == null) {
            return;
        }
        var manager = server.getRecipeManager();
        var current = manager.getRecipes();
        List<RecipeHolder<?>> filtered = new ArrayList<>(current.size());
        boolean removedAny = false;
        for (RecipeHolder<?> holder : current) {
            if (SolarConfigRecipeCondition.isRecipeDisabledByConfig(holder.id().toString())) {
                removedAny = true;
            } else {
                filtered.add(holder);
            }
        }
        if (removedAny) {
            manager.replaceRecipes(filtered);
        }
    }

    public static SolarKineticModule.MatchedMtRecipe findMolecularTransformerRecipe(
            ServerLevel level, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        var type = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
        for (RecipeHolder<MolecularTransformerRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(type)) {
            MolecularTransformerRecipe recipe = holder.value();
            if (recipe.matchesStack(input)) {
                return new SolarKineticModule.MatchedMtRecipe(holder.id().toString(), recipe);
            }
        }
        return null;
    }

    public static List<SolarKineticModule.MatchedMtRecipe> allMolecularTransformerRecipes(
            Level level) {
        if (level == null) {
            return List.of();
        }
        var type = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
        var out = new ArrayList<SolarKineticModule.MatchedMtRecipe>();
        for (RecipeHolder<MolecularTransformerRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(type)) {
            if (holder.value().isAvailable()) {
                out.add(new SolarKineticModule.MatchedMtRecipe(holder.id().toString(), holder.value()));
            }
        }
        return out;
    }
}
