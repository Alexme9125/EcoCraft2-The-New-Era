package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.giant.BlockFace;
import com.ecocraft2.common.giant.BlockPos3;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.ecocraft2.common.giant.MultiblockBounds;
import com.ecocraft2.common.giant.MultiblockGeometry;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unified BlockEntity for 3x3x3 Giant Machines (bottom-center core + 26 proxy units).
 *
 * <p>Implements 9-lane x 3-operation independent parallel processing via {@link GiantMachineEngine},
 * HV Tier 3 base power with IC2 upgrade scaling, EU power intake on ALL 27 exterior surfaces, and
 * 8 configurable horizontal lower-corner I/O ports governed by {@code GiantPortPermissions}.
 */
public final class GiantMachineBlockEntity extends BlockEntity
        implements WorldPowerPort, EuBuffer, MachineAccessTarget, MenuProvider {

    public static final int INPUT_START = 0;
    public static final int INPUT_COUNT = 9;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_COUNT = 9;
    public static final int BATTERY_SLOT = 18;
    public static final int UPGRADE_START = 19;
    public static final int UPGRADE_COUNT = 4;
    public static final int WASHER_IN_SLOT = 23;
    public static final int WASHER_OUT_SLOT = 24;
    public static final int TOTAL_SLOTS = 25;

    public static final int SYNC_DATA_COUNT = 31;

    private final GiantMachineKind kind;
    private final GiantMachineEngine engine;

    private boolean formed;
    private boolean isCore;
    private BlockPos corePos = BlockPos.ZERO;
    private Direction structureFacing = Direction.NORTH;
    private boolean dissolving;
    private long lastTick = Long.MIN_VALUE;

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    if (slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT) {
                        return true;
                    }
                    if (slot == BATTERY_SLOT) {
                        return Ecc2EnergyHelper.canDischarge(stack, engine.inputTier());
                    }
                    if (slot >= UPGRADE_START && slot < UPGRADE_START + UPGRADE_COUNT) {
                        return Ecc2EnergyHelper.isUpgradeItem(stack);
                    }
                    if (slot == WASHER_IN_SLOT) {
                        return kind.spec().usesWater() && hasWaterFluid(stack);
                    }
                    return false;
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                    if (slot >= UPGRADE_START && slot < UPGRADE_START + UPGRADE_COUNT) {
                        refreshUpgrades();
                    }
                }
            };

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    int eu = (int) Math.min(Integer.MAX_VALUE, Math.floor(engine.storedEu()));
                    int cap = (int) Math.min(Integer.MAX_VALUE, Math.floor(engine.energyCapacity()));
                    int water = engine.waterMb();
                    int waterCap = configuredWaterCapacity();
                    if (index >= 12 && index < 21) {
                        return engine.scaledLaneProgress(index - 12, 100);
                    }
                    if (index >= 21 && index < 30) {
                        return engine.laneBatchSize(index - 21);
                    }
                    return switch (index) {
                        case 0 -> eu & 0xFFFF;
                        case 1 -> (eu >>> 16) & 0xFFFF;
                        case 2 -> cap & 0xFFFF;
                        case 3 -> (cap >>> 16) & 0xFFFF;
                        case 4 -> engine.lastActiveTotalBatch();
                        case 5 -> (int) Math.round(engine.lastTickEuConsumed());
                        case 6 -> engine.inputTier();
                        case 7 -> water & 0xFFFF;
                        case 8 -> (water >>> 16) & 0xFFFF;
                        case 9 -> waterCap & 0xFFFF;
                        case 10 -> (waterCap >>> 16) & 0xFFFF;
                        case 11 -> engine.portPermissions().packed() & 0xFFFF;
                        case 30 -> kind.spec().usesWater() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return SYNC_DATA_COUNT;
                }
            };

    public GiantMachineBlockEntity(BlockPos pos, BlockState state) {
        this(
                pos,
                state,
                state.getBlock() instanceof GiantMachineBlock block
                        ? block.kind()
                        : GiantMachineKind.MACERATOR);
    }

    public GiantMachineBlockEntity(BlockPos pos, BlockState state, GiantMachineKind kind) {
        super(GiantRegistry.GIANT_MACHINE_BE.get(), pos, state);
        this.kind = kind;
        this.engine = new GiantMachineEngine(kind);
    }

    public GiantMachineKind kind() {
        return kind;
    }

    public GiantMachineEngine engine() {
        return engine;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public boolean isFormed() {
        return formed;
    }

    public boolean isCore() {
        return formed && isCore;
    }

    public Direction structureFacing() {
        return structureFacing;
    }

    public int configuredWaterCapacity() {
        return kind.spec().usesWater()
                ? Math.min(engine.waterTankCapacityMb(), Ecc2Config.giantWasherWaterCapacity())
                : 0;
    }

    /** Resolves the core BlockEntity for either the core itself or any of the 26 proxy blocks. */
    public @Nullable GiantMachineBlockEntity resolveCore() {
        if (!formed) {
            return null;
        }
        if (isCore) {
            return this;
        }
        if (level != null
                && level.hasChunkAt(corePos)
                && level.getBlockEntity(corePos) instanceof GiantMachineBlockEntity be
                && be.isCore
                && be.formed
                && be.kind == this.kind) {
            return be;
        }
        return null;
    }

    /** Cycles one of the 8 lower-corner port permissions (Full -> Input -> Output -> Blocked). */
    public void cycleCornerPort(GiantCornerPort port) {
        if (!isCore()) {
            return;
        }
        engine.portPermissions().cycle(port);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos3 offset = port.cornerOffset(toBlockFace(structureFacing));
            BlockPos cornerPos = worldPosition.offset(offset.x(), offset.y(), offset.z());
            serverLevel.updateNeighborsAt(cornerPos, getBlockState().getBlock());
        }
    }

    /** Attempts to detect and form a valid 3x3x3 cube containing {@code origin}. */
    public static boolean tryFormMultiblock(
            ServerLevel level, BlockPos origin, GiantMachineKind kind, Direction facing) {
        Optional<MultiblockBounds> found =
                MultiblockGeometry.find3x3x3(
                        new BlockPos3(origin.getX(), origin.getY(), origin.getZ()),
                        p -> {
                            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
                            if (!level.hasChunkAt(bp)) {
                                return false;
                            }
                            BlockState st = level.getBlockState(bp);
                            if (!(st.getBlock() instanceof GiantMachineBlock gb)
                                    || gb.kind() != kind) {
                                return false;
                            }
                            return !st.getValue(GiantMachineBlock.FORMED);
                        });
        if (found.isEmpty()) {
            return false;
        }
        MultiblockBounds bounds = found.get();
        BlockPos coreWorldPos =
                new BlockPos(bounds.core().x(), bounds.core().y(), bounds.core().z());
        Direction horizontalFacing =
                facing.getAxis().isHorizontal() ? facing : Direction.NORTH;

        for (BlockPos3 p : bounds.allPositions()) {
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            GiantUnitRole role = GiantUnitRole.fromBoundsRole(bounds.roleOf(p));
            BlockState current = level.getBlockState(bp);
            BlockState updated =
                    current.setValue(GiantMachineBlock.FACING, horizontalFacing)
                            .setValue(GiantMachineBlock.FORMED, Boolean.TRUE)
                            .setValue(GiantMachineBlock.ACTIVE, Boolean.FALSE)
                            .setValue(GiantMachineBlock.ROLE, role);
            level.setBlock(bp, updated, Block.UPDATE_ALL);
            if (level.getBlockEntity(bp) instanceof GiantMachineBlockEntity unit) {
                unit.formed = true;
                unit.isCore = bp.equals(coreWorldPos);
                unit.corePos = coreWorldPos;
                unit.structureFacing = horizontalFacing;
                unit.setChanged();
                WorldEnergy.disconnect(unit);
                WorldEnergy.connect(unit);
            }
        }
        return true;
    }

    /** Safely dissolves the 3x3x3 multiblock when any block is broken, dropping all stored items. */
    public void dissolveOnBreak(ServerLevel level) {
        if (!formed || dissolving) {
            return;
        }
        GiantMachineBlockEntity core = resolveCore();
        if (core == null || core.dissolving) {
            return;
        }
        core.dissolving = true;
        BlockPos coreWorld = core.worldPosition;
        MultiblockBounds bounds =
                MultiblockGeometry.boundsFromCore(
                        new BlockPos3(coreWorld.getX(), coreWorld.getY(), coreWorld.getZ()));

        // Drop all inventory & upgrades safely at core location.
        SimpleContainer drops = new SimpleContainer(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            drops.setItem(i, core.inventory.getStackInSlot(i));
            core.inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        Containers.dropContents(level, coreWorld, drops);

        for (BlockPos3 p : bounds.allPositions()) {
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            if (bp.equals(worldPosition) || !level.hasChunkAt(bp)) {
                continue;
            }
            if (level.getBlockEntity(bp) instanceof GiantMachineBlockEntity unit) {
                WorldEnergy.disconnect(unit);
                unit.formed = false;
                unit.isCore = false;
                unit.corePos = BlockPos.ZERO;
                unit.setChanged();
            }
            BlockState st = level.getBlockState(bp);
            if (st.getBlock() instanceof GiantMachineBlock) {
                level.setBlock(
                        bp,
                        st.setValue(GiantMachineBlock.FORMED, Boolean.FALSE)
                                .setValue(GiantMachineBlock.ACTIVE, Boolean.FALSE)
                                .setValue(GiantMachineBlock.ROLE, GiantUnitRole.UNFORMED),
                        Block.UPDATE_ALL);
            }
        }
        WorldEnergy.disconnect(core);
        core.formed = false;
        core.isCore = false;
        core.dissolving = false;
    }

    // ---- Server Tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel) || !isCore()) {
            return;
        }
        lastTick = serverLevel.getGameTime();
        refreshUpgrades();

        // Discharge battery slot into EU buffer
        ItemStack battery = inventory.getStackInSlot(BATTERY_SLOT);
        if (!battery.isEmpty() && engine.demandedEu() > 0.0) {
            double drawn =
                    Ecc2EnergyHelper.dischargeStack(
                            battery,
                            Math.min(engine.maxInputPacket(), engine.demandedEu()),
                            engine.energyCapacity(),
                            engine.inputTier());
            if (drawn > 0.0) {
                engine.injectEu(drawn);
                setChanged();
            }
        }

        // Drain water container slot into 64,000 mB tank for Giant Ore Washing Plant
        if (kind.spec().usesWater()) {
            serviceWasherWaterContainer();
        }

        // Optional redstone inverter upgrade pause check
        List<ItemStack> upgrades = upgradeList();
        boolean redstoneInverter = Ecc2EnergyHelper.hasRedstoneInverter(upgrades);
        boolean powered = serverLevel.hasNeighborSignal(worldPosition);
        boolean runAllowed = !redstoneInverter || powered;

        boolean nowActive = false;
        if (runAllowed) {
            var summary =
                    engine.tick(
                            new GiantMachineEngine.LaneHandler() {
                                @Override
                                public int availableBatch(int lane, int maxBatch) {
                                    return computeLaneAvailableBatch(lane, maxBatch);
                                }

                                @Override
                                public void completeOperations(int lane, int completedOps) {
                                    executeLaneCompletion(serverLevel, lane, completedOps);
                                }
                            });
            nowActive = summary.isActive();
            if (summary.euConsumed() > 0.0
                    || summary.completedOps() > 0
                    || summary.waterConsumedMb() > 0) {
                setChanged();
            }
        }

        // Run IC2 ejector/puller upgrades through enabled lower-corner ports every 4 ticks
        if ((lastTick & 3L) == 0L && !upgrades.isEmpty()) {
            serviceCornerUpgradeAutomation(serverLevel, upgrades);
        }

        // Sync active visual state on core block
        BlockState state = getBlockState();
        if (state.hasProperty(GiantMachineBlock.ACTIVE)
                && state.getValue(GiantMachineBlock.ACTIVE) != nowActive) {
            serverLevel.setBlock(
                    worldPosition,
                    state.setValue(GiantMachineBlock.ACTIVE, nowActive),
                    Block.UPDATE_CLIENTS);
        }
    }

    private int computeLaneAvailableBatch(int lane, int maxBatch) {
        ItemStack input = inventory.getStackInSlot(INPUT_START + lane);
        if (input.isEmpty()) {
            return 0;
        }
        GiantRecipeAdapter.ResolvedRecipe recipe = GiantRecipeAdapter.match(kind, input);
        if (recipe == null) {
            return 0;
        }
        int byInput = input.getCount() / recipe.inputCount();
        int cap = Math.min(maxBatch, Math.min(Ecc2Config.giantMaxBatchPerLane(), byInput));
        if (cap <= 0) {
            return 0;
        }
        // Verify output grid can accept all outputs for the batch
        for (int tryBatch = cap; tryBatch >= 1; tryBatch--) {
            if (canFitOutputsForBatch(recipe.outputs(), tryBatch)) {
                return tryBatch;
            }
        }
        return 0;
    }

    private void executeLaneCompletion(ServerLevel level, int lane, int completedOps) {
        ItemStack input = inventory.getStackInSlot(INPUT_START + lane);
        GiantRecipeAdapter.ResolvedRecipe recipe = GiantRecipeAdapter.match(kind, input);
        if (recipe == null) {
            return;
        }
        int consume = recipe.inputCount() * completedOps;
        if (input.getCount() < consume) {
            return;
        }
        inventory.extractItem(INPUT_START + lane, consume, false);

        if (recipe.recycler()) {
            int scrapCount = 0;
            for (int op = 0; op < completedOps; op++) {
                if (engine.rollRecyclerScrap(level.random::nextInt)) {
                    scrapCount++;
                }
            }
            if (scrapCount > 0 && !recipe.outputs().isEmpty()) {
                ItemStack scrap = recipe.outputs().get(0).copy();
                scrap.setCount(scrapCount);
                insertIntoOutputGrid(scrap, false);
            }
        } else {
            for (ItemStack template : recipe.outputs()) {
                int remaining = template.getCount() * completedOps;
                while (remaining > 0) {
                    int chunk = Math.min(remaining, template.getMaxStackSize());
                    ItemStack drop = template.copy();
                    drop.setCount(chunk);
                    insertIntoOutputGrid(drop, false);
                    remaining -= chunk;
                }
            }
        }
    }

    private boolean canFitOutputsForBatch(List<ItemStack> templates, int batch) {
        // Simulate insertion across a snapshot of the 9 output slots
        ItemStack[] sim = new ItemStack[OUTPUT_COUNT];
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            sim[i] = inventory.getStackInSlot(OUTPUT_START + i).copy();
        }
        for (ItemStack tpl : templates) {
            int remaining = tpl.getCount() * batch;
            for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
                if (!sim[i].isEmpty() && ItemStack.isSameItemSameTags(sim[i], tpl)) {
                    int space = sim[i].getMaxStackSize() - sim[i].getCount();
                    int add = Math.min(space, remaining);
                    sim[i].grow(add);
                    remaining -= add;
                }
            }
            for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
                if (sim[i].isEmpty()) {
                    int add = Math.min(tpl.getMaxStackSize(), remaining);
                    sim[i] = tpl.copy();
                    sim[i].setCount(add);
                    remaining -= add;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack insertIntoOutputGrid(ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        // First merge into matching stacks, then empty slots
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int i = 0; i < OUTPUT_COUNT && !remaining.isEmpty(); i++) {
                ItemStack cur = inventory.getStackInSlot(OUTPUT_START + i);
                if (pass == 0 && (cur.isEmpty() || !ItemStack.isSameItemSameTags(cur, remaining))) {
                    continue;
                }
                if (pass == 1 && !cur.isEmpty()) {
                    continue;
                }
                remaining = inventory.insertItem(OUTPUT_START + i, remaining, simulate);
            }
        }
        return remaining;
    }

    private void serviceWasherWaterContainer() {
        ItemStack inStack = inventory.getStackInSlot(WASHER_IN_SLOT);
        if (inStack.isEmpty()) {
            return;
        }
        int room = configuredWaterCapacity() - engine.waterMb();
        if (room <= 0) {
            return;
        }
        ItemStack single = Ecc2BridgePlatform.copyWithCount(inStack, 1);
        FluidUtil.getFluidHandler(single)
                .ifPresent(
                        handler -> {
                            FluidStack sim =
                                    handler.drain(
                                            new FluidStack(Fluids.WATER, room),
                                            IFluidHandler.FluidAction.SIMULATE);
                            if (sim.isEmpty() || sim.getFluid() != Fluids.WATER) {
                                return;
                            }
                            handler.drain(sim, IFluidHandler.FluidAction.EXECUTE);
                            ItemStack emptyContainer = handler.getContainer();
                            if (!canFitReturnSlot(emptyContainer)) {
                                return;
                            }
                            engine.fillWater(sim.getAmount(), false);
                            inventory.extractItem(WASHER_IN_SLOT, 1, false);
                            placeInReturnSlot(emptyContainer);
                            setChanged();
                        });
    }

    private boolean canFitReturnSlot(ItemStack remainder) {
        if (remainder.isEmpty()) {
            return true;
        }
        ItemStack cur = inventory.getStackInSlot(WASHER_OUT_SLOT);
        if (cur.isEmpty()) {
            return remainder.getCount() <= remainder.getMaxStackSize();
        }
        return ItemStack.isSameItemSameTags(cur, remainder)
                && cur.getCount() + remainder.getCount() <= cur.getMaxStackSize();
    }

    private void placeInReturnSlot(ItemStack remainder) {
        if (remainder.isEmpty()) {
            return;
        }
        ItemStack cur = inventory.getStackInSlot(WASHER_OUT_SLOT);
        if (cur.isEmpty()) {
            inventory.setStackInSlot(WASHER_OUT_SLOT, remainder.copy());
        } else {
            cur.grow(remainder.getCount());
            inventory.setStackInSlot(WASHER_OUT_SLOT, cur);
        }
    }

    private void serviceCornerUpgradeAutomation(ServerLevel level, List<ItemStack> upgrades) {
        boolean hasItemEject = false;
        boolean hasItemPull = false;
        boolean hasFluidPull = false;
        for (ItemStack u : upgrades) {
            String id = Ecc2BridgePlatform.itemId(u);
            if ("ic2:ejector_upgrade".equals(id)) {
                hasItemEject = true;
            } else if ("ic2:pulling_upgrade".equals(id)) {
                hasItemPull = true;
            } else if ("ic2:fluid_pulling_upgrade".equals(id)) {
                hasFluidPull = true;
            }
        }
        if (!hasItemEject && !hasItemPull && !hasFluidPull) {
            return;
        }
        BlockFace structFace = toBlockFace(structureFacing);
        for (GiantCornerPort port : GiantCornerPort.values()) {
            PortAccessMode mode = engine.portPermissions().get(port);
            if (!mode.connects()) {
                continue;
            }
            BlockPos3 offset = port.cornerOffset(structFace);
            Direction outDir = toDirection(port.worldFace(structFace));
            BlockPos neighborPos =
                    worldPosition.offset(offset.x(), offset.y(), offset.z()).relative(outDir);
            if (!level.hasChunkAt(neighborPos)) {
                continue;
            }
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) {
                continue;
            }
            if (hasItemEject && mode.allowsOutput()) {
                neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, outDir.getOpposite())
                        .ifPresent(this::ejectOutputsTo);
            }
            if (hasItemPull && mode.allowsInput()) {
                neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, outDir.getOpposite())
                        .ifPresent(this::pullInputsFrom);
            }
            if (hasFluidPull && kind.spec().usesWater() && mode.allowsInput()) {
                neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, outDir.getOpposite())
                        .ifPresent(
                                handler -> {
                                    int room = configuredWaterCapacity() - engine.waterMb();
                                    if (room > 0) {
                                        FluidStack drained =
                                                handler.drain(
                                                        new FluidStack(
                                                                Fluids.WATER, Math.min(1000, room)),
                                                        IFluidHandler.FluidAction.EXECUTE);
                                        if (!drained.isEmpty()
                                                && drained.getFluid() == Fluids.WATER) {
                                            engine.fillWater(drained.getAmount(), false);
                                            setChanged();
                                        }
                                    }
                                });
            }
        }
    }

    private void ejectOutputsTo(IItemHandler target) {
        for (int slot = OUTPUT_START; slot < OUTPUT_START + OUTPUT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
                setChanged();
                return;
            }
        }
    }

    private void pullInputsFrom(IItemHandler source) {
        for (int s = 0; s < source.getSlots(); s++) {
            ItemStack avail = source.extractItem(s, 64, true);
            if (avail.isEmpty()) {
                continue;
            }
            for (int lane = 0; lane < INPUT_COUNT; lane++) {
                ItemStack rem = inventory.insertItem(INPUT_START + lane, avail, true);
                int accepted = avail.getCount() - rem.getCount();
                if (accepted > 0) {
                    ItemStack pulled = source.extractItem(s, accepted, false);
                    inventory.insertItem(INPUT_START + lane, pulled, false);
                    setChanged();
                    return;
                }
            }
        }
    }

    private List<ItemStack> upgradeList() {
        List<ItemStack> list = new ArrayList<>(UPGRADE_COUNT);
        for (int i = 0; i < UPGRADE_COUNT; i++) {
            ItemStack u = inventory.getStackInSlot(UPGRADE_START + i);
            if (!u.isEmpty()) {
                list.add(u);
            }
        }
        return list;
    }

    private void refreshUpgrades() {
        engine.setUpgrades(Ecc2EnergyHelper.readUpgradeProfile(upgradeList()));
    }

    private static boolean hasWaterFluid(ItemStack stack) {
        return FluidUtil.getFluidContained(stack)
                .map(fs -> !fs.isEmpty() && fs.getFluid() == Fluids.WATER)
                .orElse(false);
    }

    // ---- Corner Port Lookup & Sided Capabilities ----

    /** Resolves which of the 8 lower-corner ports this block + face represents, if any. */
    public Optional<GiantCornerPort> cornerPortForSide(@Nullable Direction side) {
        if (!formed || side == null || !side.getAxis().isHorizontal()) {
            return Optional.empty();
        }
        GiantMachineBlockEntity core = resolveCore();
        if (core == null) {
            return Optional.empty();
        }
        int dx = worldPosition.getX() - core.worldPosition.getX();
        int dy = worldPosition.getY() - core.worldPosition.getY();
        int dz = worldPosition.getZ() - core.worldPosition.getZ();
        return GiantCornerPort.fromOffsetAndFace(
                toBlockFace(core.structureFacing), dx, dy, dz, toBlockFace(side));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (!formed) {
            return super.getCapability(cap, side);
        }
        GiantMachineBlockEntity core = resolveCore();
        if (core == null) {
            return super.getCapability(cap, side);
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return LazyOptional.of(() -> core.createPortItemHandler(true, true)).cast();
            }
            var portOpt = cornerPortForSide(side);
            if (portOpt.isEmpty()) {
                return LazyOptional.empty();
            }
            PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
            if (!mode.connects()) {
                return LazyOptional.empty();
            }
            return LazyOptional.of(
                            () -> core.createPortItemHandler(mode.allowsInput(), mode.allowsOutput()))
                    .cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && core.kind.spec().usesWater()) {
            if (side == null) {
                return LazyOptional.of(() -> core.createPortFluidHandler(true, true)).cast();
            }
            var portOpt = cornerPortForSide(side);
            if (portOpt.isEmpty()) {
                return LazyOptional.empty();
            }
            PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
            if (!mode.connects()) {
                return LazyOptional.empty();
            }
            return LazyOptional.of(
                            () ->
                                    core.createPortFluidHandler(
                                            mode.allowsInput(), mode.allowsOutput()))
                    .cast();
        }
        return super.getCapability(cap, side);
    }

    private IItemHandler createPortItemHandler(boolean allowIn, boolean allowOut) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return TOTAL_SLOTS;
            }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                return inventory.getStackInSlot(slot);
            }

            @Override
            public @NotNull ItemStack insertItem(
                    int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!allowIn) {
                    return stack;
                }
                boolean legalInput =
                        (slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT)
                                || slot == BATTERY_SLOT
                                || (kind.spec().usesWater() && slot == WASHER_IN_SLOT);
                if (!legalInput) {
                    return stack;
                }
                return inventory.insertItem(slot, stack, simulate);
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!allowOut) {
                    return ItemStack.EMPTY;
                }
                boolean legalOutput =
                        (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT)
                                || (kind.spec().usesWater() && slot == WASHER_OUT_SLOT);
                if (!legalOutput) {
                    return ItemStack.EMPTY;
                }
                return inventory.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return inventory.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return allowIn && inventory.isItemValid(slot, stack);
            }
        };
    }

    private IFluidHandler createPortFluidHandler(boolean allowIn, boolean allowOut) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                int mb = engine.waterMb();
                return mb > 0 ? new FluidStack(Fluids.WATER, mb) : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return configuredWaterCapacity();
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return stack.getFluid() == Fluids.WATER;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (!allowIn || resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                    return 0;
                }
                int room = Math.max(0, configuredWaterCapacity() - engine.waterMb());
                int amount = Math.min(room, resource.getAmount());
                if (amount > 0 && action.execute()) {
                    engine.fillWater(amount, false);
                    setChanged();
                }
                return amount;
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                if (!allowOut || resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                    return FluidStack.EMPTY;
                }
                return drain(resource.getAmount(), action);
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                if (!allowOut || maxDrain <= 0 || engine.waterMb() <= 0) {
                    return FluidStack.EMPTY;
                }
                int drained = engine.drainWater(maxDrain, action.simulate());
                if (drained > 0 && action.execute()) {
                    setChanged();
                }
                return drained > 0 ? new FluidStack(Fluids.WATER, drained) : FluidStack.EMPTY;
            }
        };
    }

    @Override
    public boolean exchangeHeldFluid(Player player, InteractionHand hand, Direction side) {
        GiantMachineBlockEntity core = resolveCore();
        if (core == null || !core.kind.spec().usesWater()) {
            return false;
        }
        IFluidHandler handler = core.createPortFluidHandler(true, false);
        return FluidUtil.interactWithFluidHandler(player, hand, handler);
    }

    @Override
    public BlockEntity accessController() {
        GiantMachineBlockEntity core = resolveCore();
        return core != null ? core : this;
    }

    // ---- WorldEnergy / WorldPowerPort / EuBuffer (delegated from all 27 exterior blocks!) ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            if (isCore) {
                refreshUpgrades();
            }
            if (formed) {
                WorldEnergy.connect(this);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        WorldEnergy.disconnect(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        WorldEnergy.disconnect(this);
        super.setRemoved();
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        GiantMachineBlockEntity core = resolveCore();
        return core != null && core.lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return formed ? Ecc2EnergyHelper.ALL_INPUT_PORTS : Ecc2EnergyHelper.NO_PORTS;
    }

    @Override
    public double receive(double amount) {
        GiantMachineBlockEntity core = resolveCore();
        if (core == null || !Double.isFinite(amount) || amount <= 0.0) {
            return 0.0;
        }
        double rem = core.engine.injectEu(amount);
        double accepted = Math.max(0.0, amount - rem);
        if (accepted > 0.0) {
            core.setChanged();
        }
        return accepted;
    }

    @Override
    public double packetLimit() {
        GiantMachineBlockEntity core = resolveCore();
        return core != null ? core.engine.maxInputPacket() : 512.0;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        GiantMachineBlockEntity core = resolveCore();
        if (core == null) {
            return new EuBuffer.Reading(0.0, 1.0);
        }
        return new EuBuffer.Reading(core.engine.storedEu(), core.engine.energyCapacity());
    }

    // ---- NBT & MenuProvider ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("IsCore", isCore);
        tag.putInt("CoreX", corePos.getX());
        tag.putInt("CoreY", corePos.getY());
        tag.putInt("CoreZ", corePos.getZ());
        tag.putInt("Facing", structureFacing.get2DDataValue());
        if (isCore) {
            tag.put("Inventory", inventory.serializeNBT());
            tag.putDouble("StoredEu", engine.storedEu());
            tag.putInt("WaterMb", engine.waterMb());
            tag.putInt("PortPerms", engine.portPermissions().packed());
            tag.putIntArray("LaneProgress", engine.copyLaneProgress());
            tag.putIntArray("LaneBatch", engine.copyLaneBatchSizes());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        formed = tag.getBoolean("Formed");
        isCore = tag.getBoolean("IsCore");
        corePos = new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"));
        structureFacing = Direction.from2DDataValue(tag.getInt("Facing"));
        if (isCore) {
            if (tag.contains("Inventory")) {
                inventory.deserializeNBT(tag.getCompound("Inventory"));
            }
            refreshUpgrades();
            engine.setStoredEu(tag.getDouble("StoredEu"));
            engine.setWaterMb(tag.getInt("WaterMb"));
            if (tag.contains("PortPerms")) {
                engine.portPermissions().setPacked(tag.getInt("PortPerms"));
            }
            int[] prog = tag.getIntArray("LaneProgress");
            int[] batch = tag.getIntArray("LaneBatch");
            if (prog.length == GiantMachineEngine.LANE_COUNT
                    && batch.length == GiantMachineEngine.LANE_COUNT) {
                engine.restoreLanes(prog, batch);
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.giant_" + kind.id());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GiantMachineMenu(containerId, playerInv, this, syncData);
    }

    // ---- Direction / BlockFace conversions ----

    public static BlockFace toBlockFace(Direction dir) {
        return switch (dir) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    public static Direction toDirection(BlockFace face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }
}
