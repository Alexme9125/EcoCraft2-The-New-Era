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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** NeoForge 26.x platform bindings for {@code com.ecocraft2.giant} and {@code com.ecocraft2.gas}. */
public final class GiantGasPlatform {

    private static final String NETWORK_VERSION = "1";

    private GiantGasPlatform() {}

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(
            String path) {
        return new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, path));
    }

    public static <T extends Block> DeferredBlock<T> registerBlock(
            DeferredRegister.Blocks blocks,
            String path,
            Function<BlockBehaviour.Properties, T> factory,
            Supplier<BlockBehaviour.Properties> properties) {
        return blocks.registerBlock(path, factory, properties);
    }

    public static <T extends Item> DeferredHolder<Item, T> registerItem(
            DeferredRegister.Items items,
            String path,
            Function<Item.Properties, T> factory,
            Supplier<Item.Properties> properties) {
        return items.registerItem(path, factory, properties);
    }

    public static DeferredHolder<Item, Item> registerSimpleItem(
            DeferredRegister.Items items, String path) {
        return items.registerSimpleItem(path);
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return new BlockEntityType<>(factory, blocks);
    }

    public static BlockBehaviour.Properties fluidNonSolid(BlockBehaviour.Properties properties) {
        return properties.noCollision();
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
                                Capabilities.Item.BLOCK,
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
                                Capabilities.Fluid.BLOCK,
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
        var key = Identifier.tryParse(id);
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
        var key = Identifier.tryParse(id);
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
        var key = Identifier.tryParse(raw);
        return key != null && stack.is(TagKey.create(Registries.ITEM, key));
    }

    public static boolean hasFluidTag(Fluid fluid, String tagId) {
        if (fluid == null || fluid == Fluids.EMPTY || tagId == null || tagId.isEmpty()) {
            return false;
        }
        String raw = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        var key = Identifier.tryParse(raw);
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
        return level.recipeAccess()
                .getRecipeFor(RecipeType.SMELTING, single, level)
                .map(holder -> holder.value().assemble(single))
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
        var useRemainder = stack.get(DataComponents.USE_REMAINDER);
        if (useRemainder != null) {
            var converted = useRemainder.convertInto().create();
            if (!converted.isEmpty()) {
                return converted;
            }
        }
        var craftingRemainder = stack.getCraftingRemainder();
        return craftingRemainder == null ? ItemStack.EMPTY : craftingRemainder.create();
    }

    public static boolean isDaytime(ServerLevel level) {
        return level.isBrightOutside();
    }

    public static void sendActionBar(Player player, Component message) {
        player.sendOverlayMessage(message);
    }

    public static boolean isDrainableContainer(ItemStack stack, Predicate<String> fluidFilter) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var scratch = new SimpleContainer(stack.copyWithCount(1));
        var handler =
                ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(scratch), 0)
                        .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.size(); i++) {
            var res = handler.getResource(i);
            if (!res.isEmpty()
                    && res.getComponentsPatch().isEmpty()
                    && handler.getAmountAsLong(i) > 0
                    && fluidFilter.test(fluidId(res.toStack(1)))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFillableContainer(ItemStack stack, String fluidId) {
        if (stack == null || stack.isEmpty() || fluidId == null || fluidId.isEmpty()) {
            return false;
        }
        var probe = fluidStack(fluidId, CellFamily.CAPACITY);
        if (probe.isEmpty()) {
            return false;
        }
        var scratch = new SimpleContainer(stack.copyWithCount(1));
        var handler =
                ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(scratch), 0)
                        .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return false;
        }
        try (var tx = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            return handler.insert(FluidResource.of(probe), CellFamily.CAPACITY, tx) > 0;
        }
    }

    public static Ecc2FluidContainerResult drainFluidContainer(
            ItemStack containerStack, Predicate<String> fluidFilter, int maxSpaceMb) {
        if (containerStack == null || containerStack.isEmpty() || maxSpaceMb <= 0) {
            return null;
        }
        var scratch = new SimpleContainer(containerStack.copyWithCount(1));
        var handler =
                ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(scratch), 0)
                        .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return null;
        }
        String matchedId = "";
        int totalAmount = 0;
        for (int i = 0; i < handler.size(); i++) {
            var res = handler.getResource(i);
            int amt = handler.getAmountAsInt(i);
            if (res.isEmpty() || amt <= 0) {
                continue;
            }
            if (!res.getComponentsPatch().isEmpty()) {
                return null;
            }
            String fid = fluidId(res.toStack(1));
            if (!fluidFilter.test(fid)) {
                return null;
            }
            if (matchedId.isEmpty()) {
                matchedId = fid;
            } else if (!matchedId.equals(fid)) {
                return null;
            }
            totalAmount += amt;
        }
        if (matchedId.isEmpty() || totalAmount <= 0 || totalAmount > maxSpaceMb) {
            return null;
        }
        var expected = FluidResource.of(fluidStack(matchedId, totalAmount));
        try (var tx = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            int extracted = handler.extract(expected, totalAmount, tx);
            if (extracted != totalAmount) {
                return null;
            }
            tx.commit();
            return new Ecc2FluidContainerResult(scratch.getItem(0).copy(), matchedId, totalAmount);
        }
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
        int offer = requireFullUnit ? CellFamily.CAPACITY : availableMb;
        var fluid = fluidStack(fluidId, offer);
        if (fluid.isEmpty()) {
            return null;
        }
        var scratch = new SimpleContainer(containerStack.copyWithCount(1));
        var handler =
                ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(scratch), 0)
                        .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return null;
        }
        try (var tx = Transaction.open(Transaction.getCurrentOpenedTransaction())) {
            int filled = handler.insert(FluidResource.of(fluid), offer, tx);
            if (filled <= 0 || (requireFullUnit && filled != CellFamily.CAPACITY)) {
                return null;
            }
            tx.commit();
            return new Ecc2FluidContainerResult(scratch.getItem(0).copy(), fluidId, filled);
        }
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
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM)
                == null) {
            return false;
        }
        if (!(be.getLevel() instanceof ServerLevel)) {
            return true;
        }
        return FluidUtil.interactWithFluidHandler(
                player, hand, be.getBlockPos(), new TankHandler(view));
    }

    public static int pushItemToNeighbor(
            ServerLevel level, BlockPos neighborPos, Direction targetFace, ItemStack stack) {
        if (stack.isEmpty() || !level.hasChunkAt(neighborPos)) {
            return 0;
        }
        var handler = level.getCapability(Capabilities.Item.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return 0;
        }
        try (var tx = Transaction.openRoot()) {
            int accepted =
                    ResourceHandlerUtil.insertStacking(
                            handler, ItemResource.of(stack), stack.getCount(), tx);
            if (accepted > 0) {
                tx.commit();
            }
            return accepted;
        }
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
        var handler = level.getCapability(Capabilities.Item.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty() || handler.getAmountAsLong(slot) <= 0L) {
                continue;
            }
            ItemStack probe = resource.toStack(1);
            if (!filter.test(probe)) {
                continue;
            }
            // Never extract more than the item's own max stack size (prevents silent truncation
            // of unstackable or 16-stackable items when stored into machine inventory slots).
            int capped = Math.min(maxAmount, probe.getMaxStackSize());
            if (capped <= 0) {
                continue;
            }
            try (var tx = Transaction.openRoot()) {
                int extracted = handler.extract(slot, resource, capped, tx);
                if (extracted > 0) {
                    tx.commit();
                    return resource.toStack(extracted);
                }
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
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return 0;
        }
        var resource = FluidResource.of(fluidStack(fluidId, amount));
        if (resource.isEmpty()) {
            return 0;
        }
        try (var tx = Transaction.openRoot()) {
            int accepted = handler.insert(resource, amount, tx);
            if (accepted > 0) {
                tx.commit();
            }
            return accepted;
        }
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
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, neighborPos, targetFace);
        if (handler == null) {
            return null;
        }
        try (var tx = Transaction.openRoot()) {
            var batch =
                    ResourceHandlerUtil.extractFirst(
                            handler,
                            resource ->
                                    resource.getComponentsPatch().isEmpty()
                                            && fluidFilter.test(fluidId(resource.toStack(1))),
                            maxAmount,
                            tx);
            if (batch == null) {
                return null;
            }
            tx.commit();
            return new Ecc2FluidContainerResult(
                    ItemStack.EMPTY, fluidId(batch.resource().toStack(1)), batch.amount());
        }
    }

    public static boolean isInTransaction() {
        return Transaction.getLifecycle() != Transaction.Lifecycle.NONE;
    }

    private static final class PortGatedItemHandler extends SnapshotJournal<Integer>
            implements ResourceHandler<ItemResource> {

        private final Ecc2CapabilityProvider provider;
        private final Direction side;
        private final ProcessingSlots slots;
        private final ResourceHandler<ItemResource> delegate;
        private int insertedSlotMask;

        PortGatedItemHandler(
                Ecc2CapabilityProvider provider,
                Direction side,
                ProcessingSlots slots,
                ResourceHandler<ItemResource> delegate) {
            this.provider = provider;
            this.side = side;
            this.slots = slots;
            this.delegate = delegate;
        }

        @Override
        protected Integer createSnapshot() {
            return insertedSlotMask;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            this.insertedSlotMask = snapshot == null ? 0 : snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            int committedMask = this.insertedSlotMask;
            this.insertedSlotMask = 0;
            if (committedMask != 0) {
                for (int bit = 0; bit < 31; bit++) {
                    if ((committedMask & (1 << bit)) != 0) {
                        provider.onAutomationInputInserted(bit);
                    }
                }
            }
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public ItemResource getResource(int slot) {
            return delegate.getResource(slot);
        }

        @Override
        public long getAmountAsLong(int slot) {
            return delegate.getAmountAsLong(slot);
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return delegate.getCapacityAsLong(slot, resource);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return provider.allowsItemInput(side) && delegate.isValid(slot, resource);
        }

        @Override
        public int insert(
                int slot, ItemResource resource, int amount, TransactionContext transaction) {
            if (!provider.allowsItemInput(side)) {
                return 0;
            }
            int accepted = delegate.insert(slot, resource, amount, transaction);
            if (accepted > 0) {
                int[] faceSlots = slots.getSlotsForFace(side);
                if (slot >= 0 && slot < faceSlots.length) {
                    int rawSlot = faceSlots[slot];
                    if (rawSlot >= 0 && rawSlot < 31) {
                        updateSnapshots(transaction);
                        insertedSlotMask |= (1 << rawSlot);
                    }
                }
            }
            return accepted;
        }

        @Override
        public int extract(
                int slot, ItemResource resource, int amount, TransactionContext transaction) {
            return provider.allowsItemOutput(side)
                    ? delegate.extract(slot, resource, amount, transaction)
                    : 0;
        }
    }

    private static final class TankHandler extends SnapshotJournal<Ecc2FluidTankView.Snapshot>
            implements ResourceHandler<FluidResource> {

        private final Ecc2FluidTankView view;

        TankHandler(Ecc2FluidTankView view) {
            this.view = view;
        }

        @Override
        protected Ecc2FluidTankView.Snapshot createSnapshot() {
            return view.captureSnapshot();
        }

        @Override
        protected void revertToSnapshot(Ecc2FluidTankView.Snapshot snapshot) {
            view.restoreSnapshot(snapshot);
        }

        @Override
        protected void onRootCommit(Ecc2FluidTankView.Snapshot snapshot) {
            view.markChanged();
        }

        @Override
        public int size() {
            return view.tankCount();
        }

        @Override
        public FluidResource getResource(int slot) {
            String fid = view.fluidId(slot);
            int amt = view.fluidAmount(slot);
            return fid.isEmpty() || amt <= 0
                    ? FluidResource.EMPTY
                    : FluidResource.of(fluidStack(fid, amt));
        }

        @Override
        public long getAmountAsLong(int slot) {
            return view.fluidAmount(slot);
        }

        @Override
        public long getCapacityAsLong(int slot, FluidResource resource) {
            return view.tankCapacity(slot);
        }

        @Override
        public boolean isValid(int slot, FluidResource resource) {
            return view.allowsInput()
                    && !resource.isEmpty()
                    && resource.getComponentsPatch().isEmpty()
                    && view.acceptsFluid(slot, fluidId(resource.toStack(1)));
        }

        @Override
        public int insert(
                int slot, FluidResource resource, int amount, TransactionContext transaction) {
            if (!isValid(slot, resource) || amount <= 0) {
                return 0;
            }
            String fid = fluidId(resource.toStack(1));
            int accepted = view.fillFluid(slot, fid, amount, true);
            if (accepted > 0) {
                updateSnapshots(transaction);
                return view.fillFluid(slot, fid, accepted, false);
            }
            return 0;
        }

        @Override
        public int extract(
                int slot, FluidResource resource, int amount, TransactionContext transaction) {
            if (!view.allowsOutput() || resource.isEmpty() || amount <= 0) {
                return 0;
            }
            if (!resource.equals(getResource(slot))) {
                return 0;
            }
            String fid = fluidId(resource.toStack(1));
            int drained = view.drainFluid(slot, fid, amount, true);
            if (drained > 0) {
                updateSnapshots(transaction);
                return view.drainFluid(slot, fid, drained, false);
            }
            return 0;
        }
    }
}
