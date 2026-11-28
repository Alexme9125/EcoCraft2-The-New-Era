package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachinePorts;
import com.industrialage.ia2.access.ProcessingItemPort;
import com.industrialage.ia2.access.ProcessingSlots;
import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge 1.21.1 platform bindings for {@code com.ecocraft2.giant} and {@code com.ecocraft2.gas}. */
public final class GiantGasPlatform {

    private static final String NETWORK_VERSION = "1";

    private GiantGasPlatform() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(
            String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static <T extends Block> DeferredBlock<T> registerBlock(
            DeferredRegister.Blocks blocks,
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            Supplier<BlockBehaviour.Properties> properties) {
        return blocks.registerBlock(path, factory, properties.get());
    }

    public static <T extends Item> DeferredHolder<Item, T> registerItem(
            DeferredRegister.Items items,
            String path,
            Function<Item.Properties, T> factory,
            Supplier<Item.Properties> properties) {
        return items.registerItem(path, factory, properties.get());
    }

    public static DeferredHolder<Item, Item> registerSimpleItem(
            DeferredRegister.Items items, String path) {
        return items.registerSimpleItem(path);
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    public static BlockBehaviour.Properties fluidNonSolid(BlockBehaviour.Properties properties) {
        return properties.noCollission();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerCapabilities(
            IEventBus modBus, List<Supplier<? extends BlockEntityType<?>>> beTypes) {
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event -> {
                    for (var supplier : beTypes) {
                        BlockEntityType type = supplier.get();
                        event.registerBlockEntity(
                                Capabilities.ItemHandler.BLOCK,
                                type,
                                (be, side) -> {
                                    if (!(be instanceof Ecc2CapabilityProvider provider)) {
                                        return null;
                                    }
                                    var slots = provider.itemCapabilitySlots(side);
                                    if (slots == null) {
                                        return null;
                                    }
                                    if (provider.usesMachineAccess()) {
                                        if (!MachineAccess.connects(be, side)) {
                                            return null;
                                        }
                                        return MachinePorts.item(
                                                be, side, new ProcessingItemPort(slots, side));
                                    }
                                    return new PortGatedItemHandler(
                                            provider,
                                            side,
                                            slots,
                                            new ProcessingItemPort(slots, side));
                                });
                        event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                type,
                                (be, side) -> {
                                    if (!(be instanceof Ecc2CapabilityProvider provider)) {
                                        return null;
                                    }
                                    var view = provider.fluidCapabilityTank(side);
                                    if (view == null || view.tankCount() <= 0) {
                                        return null;
                                    }
                                    if (provider.usesMachineAccess()) {
                                        if (!MachineAccess.connects(be, side)) {
                                            return null;
                                        }
                                        return MachinePorts.fluid(be, side, new TankHandler(view));
                                    }
                                    return new TankHandler(view);
                                });
                    }
                });
    }

    public static <T extends CustomPacketPayload> void registerServerboundPayload(
            IEventBus modBus,
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        modBus.addListener(
                RegisterPayloadHandlersEvent.class,
                event -> {
                    var registrar =
                            event.registrar(NETWORK_VERSION).executesOn(HandlerThread.MAIN);
                    registrar.playToServer(type, codec, handler);
                });
    }

    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        var key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    public static ItemStack itemStack(String id, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = findItem(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public static Fluid findFluid(String id) {
        if (id == null || id.isEmpty()) {
            return Fluids.EMPTY;
        }
        var key = ResourceLocation.tryParse(id);
        return key == null
                ? Fluids.EMPTY
                : BuiltInRegistries.FLUID.getOptional(key).orElse(Fluids.EMPTY);
    }

    public static FluidStack fluidStack(String id, int amount) {
        if (amount <= 0) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = findFluid(id);
        return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    public static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? ""
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String fluidId(Fluid fluid) {
        return fluid == null || fluid == Fluids.EMPTY
                ? ""
                : BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    public static String fluidId(FluidStack stack) {
        return stack == null || stack.isEmpty() ? "" : fluidId(stack.getFluid());
    }

    public static boolean hasItemTag(ItemStack stack, String tagId) {
        if (stack == null || stack.isEmpty() || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = ResourceLocation.tryParse(raw);
        return key != null && stack.is(TagKey.create(Registries.ITEM, key));
    }

    public static boolean hasFluidTag(Fluid fluid, String tagId) {
        if (fluid == null || fluid == Fluids.EMPTY || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = ResourceLocation.tryParse(raw);
        return key != null && fluid.defaultFluidState().is(TagKey.create(Registries.FLUID, key));
    }

    public static boolean hasFluidTag(String fluidId, String tagId) {
        return hasFluidTag(findFluid(fluidId), tagId);
    }

    public static boolean isWaterFluid(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        if (Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId)) {
            return true;
        }
        Fluid fluid = findFluid(fluidId);
        return fluid != Fluids.EMPTY
                && (fluid.defaultFluidState().is(FluidTags.WATER)
                        || hasFluidTag(fluid, Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_WATER)));
    }

    public static boolean isCultureMediumFluid(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        return Ecc2Ids.CULTURE_MEDIUM_ID.equals(fluidId)
                || hasFluidTag(fluidId, Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_CULTURE_MEDIUM));
    }

    public static boolean isBiogasFluid(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        return Ecc2Ids.IC2_BIOGAS_ID.equals(fluidId)
                || hasFluidTag(fluidId, Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_BIOGAS));
    }

    public static Optional<GasFuelSpec> resolveGasFuel(String fluidId) {
        return GasFuelSpec.resolve(fluidId, "c", tag -> hasFluidTag(fluidId, tag));
    }

    public static boolean isGasFuel(String fluidId) {
        return resolveGasFuel(fluidId).isPresent();
    }

    public static boolean isRedstoneDust(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(Items.REDSTONE)
                        || hasItemTag(stack, Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_DUSTS_REDSTONE)));
    }

    public static int fluidRegistryId(String fluidId) {
        Fluid fluid = findFluid(fluidId);
        return fluid == Fluids.EMPTY ? 0 : BuiltInRegistries.FLUID.getId(fluid);
    }

    public static Component fluidDisplayName(String fluidId) {
        Fluid fluid = findFluid(fluidId);
        if (fluid == Fluids.EMPTY) {
            return Component.translatable("gui.ecera.gas.empty");
        }
        return fluid.getFluidType().getDescription();
    }

    public static ItemStack smeltingResult(ServerLevel level, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var single = new SingleRecipeInput(input);
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, single, level)
                .map(holder -> holder.value().assemble(single, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    public static int foodNutrition(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        var food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : Math.max(0, food.nutrition());
    }

    public static ItemStack foodContainerRemainder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var food = stack.get(DataComponents.FOOD);
        if (food != null && food.usingConvertsTo().isPresent()) {
            var converted = food.usingConvertsTo().get();
            if (!converted.isEmpty()) {
                return converted.copy();
            }
        }
        var craftingRemainder = stack.getCraftingRemainingItem();
        return craftingRemainder.isEmpty() ? ItemStack.EMPTY : craftingRemainder.copy();
    }

    public static boolean isDaytime(ServerLevel level) {
        return level.isDay();
    }

    public static void sendActionBar(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    public static boolean isDrainableContainer(ItemStack stack, Predicate<String> fluidFilter) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getTanks(); i++) {
            var stored = handler.getFluidInTank(i);
            if (!stored.isEmpty()
                    && stored.getComponentsPatch().isEmpty()
                    && fluidFilter.test(fluidId(stored))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFillableContainer(ItemStack stack, String fluidId) {
        if (stack == null || stack.isEmpty() || fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        var handler = stack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return false;
        }
        var probe = fluidStack(fluidId, CellFamily.CAPACITY);
        if (probe.isEmpty()) {
            return false;
        }
        return handler.fill(probe, IFluidHandler.FluidAction.SIMULATE) > 0;
    }

    public static Ecc2FluidContainerResult drainFluidContainer(
            ItemStack containerStack, Predicate<String> fluidFilter, int maxSpaceMb) {
        if (containerStack == null || containerStack.isEmpty() || maxSpaceMb <= 0) {
            return null;
        }
        var handler = containerStack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return null;
        }
        String matchedId = "";
        int totalAmount = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            var stored = handler.getFluidInTank(i);
            if (stored.isEmpty()) {
                continue;
            }
            if (!stored.getComponentsPatch().isEmpty()) {
                return null;
            }
            String fid = fluidId(stored);
            if (!fluidFilter.test(fid)) {
                return null;
            }
            if (matchedId.isEmpty()) {
                matchedId = fid;
            } else if (!matchedId.equals(fid)) {
                return null;
            }
            totalAmount += stored.getAmount();
        }
        if (matchedId.isEmpty() || totalAmount <= 0 || totalAmount > maxSpaceMb) {
            return null;
        }
        var drained =
                handler.drain(
                        fluidStack(matchedId, totalAmount), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()
                || !drained.getComponentsPatch().isEmpty()
                || drained.getAmount() != totalAmount
                || !matchedId.equals(fluidId(drained))) {
            return null;
        }
        return new Ecc2FluidContainerResult(handler.getContainer().copy(), matchedId, totalAmount);
    }

    public static Ecc2FluidContainerResult fillFluidContainer(
            ItemStack containerStack, String fluidId, int availableMb) {
        if (containerStack == null
                || containerStack.isEmpty()
                || fluidId == null
                || fluidId.isEmpty()
                || availableMb <= 0) {
            return null;
        }
        boolean requireFullUnit =
                containerStack.getItem() instanceof CellItem || containerStack.is(Items.BUCKET);
        if (requireFullUnit && availableMb < CellFamily.CAPACITY) {
            return null;
        }
        var handler = containerStack.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return null;
        }
        int offer = requireFullUnit ? CellFamily.CAPACITY : availableMb;
        var fluid = fluidStack(fluidId, offer);
        if (fluid.isEmpty()) {
            return null;
        }
        int sim = handler.fill(fluid, IFluidHandler.FluidAction.SIMULATE);
        if (sim <= 0 || (requireFullUnit && sim != CellFamily.CAPACITY)) {
            return null;
        }
        int filled = handler.fill(fluidStack(fluidId, sim), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return null;
        }
        return new Ecc2FluidContainerResult(handler.getContainer().copy(), fluidId, filled);
    }

    public static boolean interactPlayerFluid(
            BlockEntity be,
            Player player,
            InteractionHand hand,
            Direction side,
            Ecc2FluidTankView view) {
        if (view == null || view.tankCount() <= 0) {
            return false;
        }
        if (player.getItemInHand(hand).getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return false;
        }
        if (!(be.getLevel() instanceof ServerLevel)) {
            return true;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, new TankHandler(view));
    }

    public static int pushItemToNeighbor(
            ServerLevel level, BlockPos neighborPos, Direction targetFace, ItemStack stack) {
        if (stack.isEmpty() || !level.hasChunkAt(neighborPos)) {
            return 0;
        }
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return 0;
        }
        var remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
        return stack.getCount() - remainder.getCount();
    }

    public static ItemStack pullItemFromNeighbor(
            ServerLevel level,
            BlockPos neighborPos,
            Direction targetFace,
            int maxAmount,
            Predicate<ItemStack> filter) {
        if (maxAmount <= 0 || !level.hasChunkAt(neighborPos)) {
            return ItemStack.EMPTY;
        }
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var preview = handler.extractItem(slot, maxAmount, true);
            if (!preview.isEmpty() && filter.test(preview)) {
                return handler.extractItem(
                        slot, Math.min(maxAmount, preview.getMaxStackSize()), false);
            }
        }
        return ItemStack.EMPTY;
    }

    public static int pushFluidToNeighbor(
            ServerLevel level,
            BlockPos neighborPos,
            Direction targetFace,
            String fluidId,
            int amount) {
        if (amount <= 0 || fluidId == null || fluidId.isEmpty() || !level.hasChunkAt(neighborPos)) {
            return 0;
        }
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return 0;
        }
        var stack = fluidStack(fluidId, amount);
        return stack.isEmpty() ? 0 : handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    public static Ecc2FluidContainerResult pullFluidFromNeighbor(
            ServerLevel level,
            BlockPos neighborPos,
            Direction targetFace,
            Predicate<String> fluidFilter,
            int maxAmount) {
        if (maxAmount <= 0 || !level.hasChunkAt(neighborPos)) {
            return null;
        }
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return null;
        }
        for (int i = 0; i < handler.getTanks(); i++) {
            var stored = handler.getFluidInTank(i);
            if (stored.isEmpty() || !stored.getComponentsPatch().isEmpty()) {
                continue;
            }
            String fid = fluidId(stored);
            if (!fluidFilter.test(fid)) {
                continue;
            }
            var drained =
                    handler.drain(
                            fluidStack(fid, Math.min(maxAmount, stored.getAmount())),
                            IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty() && drained.getComponentsPatch().isEmpty()) {
                return new Ecc2FluidContainerResult(
                        ItemStack.EMPTY, fluidId(drained), drained.getAmount());
            }
        }
        return null;
    }

    public static boolean isInTransaction() {
        return false;
    }

    private record PortGatedItemHandler(
            Ecc2CapabilityProvider provider,
            Direction side,
            ProcessingSlots slots,
            IItemHandler delegate)
            implements IItemHandler {

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return provider.allowsItemInput(side) && delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!provider.allowsItemInput(side)) {
                return stack;
            }
            int offered = stack.getCount();
            ItemStack remainder = delegate.insertItem(slot, stack, simulate);
            if (!simulate && offered > 0 && remainder.getCount() < offered) {
                int[] faceSlots = slots.getSlotsForFace(side);
                if (slot >= 0 && slot < faceSlots.length) {
                    provider.onAutomationInputInserted(faceSlots[slot]);
                }
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return provider.allowsItemOutput(side)
                    ? delegate.extractItem(slot, amount, simulate)
                    : ItemStack.EMPTY;
        }
    }

    private record TankHandler(Ecc2FluidTankView view) implements IFluidHandler {

        @Override
        public int getTanks() {
            return view.tankCount();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluidStack(view.fluidId(tank), view.fluidAmount(tank));
        }

        @Override
        public int getTankCapacity(int tank) {
            return view.tankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return view.allowsInput()
                    && !stack.isEmpty()
                    && stack.getComponentsPatch().isEmpty()
                    && view.acceptsFluid(tank, fluidId(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!view.allowsInput()
                    || resource.isEmpty()
                    || !resource.getComponentsPatch().isEmpty()) {
                return 0;
            }
            int filled =
                    view.fillFluid(-1, fluidId(resource), resource.getAmount(), action.simulate());
            if (filled > 0 && action.execute()) {
                view.markChanged();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!view.allowsOutput()
                    || resource.isEmpty()
                    || !resource.getComponentsPatch().isEmpty()) {
                return FluidStack.EMPTY;
            }
            String fid = fluidId(resource);
            int drained = view.drainFluid(-1, fid, resource.getAmount(), action.simulate());
            if (drained > 0 && action.execute()) {
                view.markChanged();
            }
            return drained <= 0 ? FluidStack.EMPTY : fluidStack(fid, drained);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!view.allowsOutput() || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            String fid = view.defaultDrainFluidId();
            if (fid.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int drained = view.drainFluid(-1, fid, maxDrain, action.simulate());
            if (drained > 0 && action.execute()) {
                view.markChanged();
            }
            return drained <= 0 ? FluidStack.EMPTY : fluidStack(fid, drained);
        }
    }
}
