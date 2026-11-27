package com.ecocraft2.giant;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.giant.BlockFace;
import com.ecocraft2.common.giant.BlockPos3;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.GiantInputDistributor;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.ecocraft2.common.giant.GiantPortPermissions;
import com.ecocraft2.common.giant.MultiblockBounds;
import com.ecocraft2.common.giant.MultiblockGeometry;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.gas.GasUpgradeAutomation;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.content.ProcessingUpgrades;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.logistics.TransportBlock;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import com.industrialage.ia2.world.MachineAutomation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified BlockEntity for 3x3x3 Giant Machines (bottom-center core + 26 proxy units).
 *
 * <p>Implements 9-lane x 3-operation independent parallel processing via {@link GiantMachineEngine},
 * HV Tier 3 base power with IC2 upgrade scaling, EU power intake on ALL 27 exterior surfaces, and
 * 8 configurable horizontal lower-corner I/O ports governed by {@code GiantPortPermissions}.
 */
public final class GiantMachineBlockEntity extends BlockEntity
        implements WorldPowerPort, EuBuffer, MachineAccessTarget, MenuProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GiantMachineBlockEntity.class);

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

    private static final int[] INPUT_SLOTS = IntStream.range(INPUT_START, INPUT_START + INPUT_COUNT).toArray();
    private static final int[] OUTPUT_SLOTS_STANDARD =
            IntStream.range(OUTPUT_START, OUTPUT_START + OUTPUT_COUNT).toArray();
    private static final int[] OUTPUT_SLOTS_WASHER = {9, 10, 11, 12, 13, 14, 15, 16, 17, WASHER_OUT_SLOT};

    private final GiantMachineKind kind;
    private final GiantMachineEngine engine;

    private boolean formed;
    private boolean isCore;
    private BlockPos corePos = BlockPos.ZERO;
    private Direction structureFacing = Direction.NORTH;
    private boolean dissolving;
    private boolean pendingPortVisualSync;
    private boolean pendingInputSpread;
    private boolean spreading;
    private long lastTick = Long.MIN_VALUE;

    private final Map<Direction, LazyOptional<IItemHandler>> itemPortCaps =
            new EnumMap<>(Direction.class);
    private final Map<Direction, LazyOptional<IFluidHandler>> fluidPortCaps =
            new EnumMap<>(Direction.class);

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    if (slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT) {
                        return GiantRecipeAdapter.acceptsInput(kind, stack);
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
                    if (spreading) {
                        return;
                    }
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
                        case 30 ->
                                (kind.spec().usesWater() ? 1 : 0)
                                        | (engine.isAutoSpreadInputs() ? 2 : 0);
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
        applyConfigToEngine();
    }

    private void applyConfigToEngine() {
        engine.setBaseTier(Ecc2Config.giantBaseTier());
        engine.setMaxBatchPerLane(Ecc2Config.giantMaxBatchPerLane());
        if (kind.spec().usesWater()) {
            engine.setWaterTankCapacityMb(Ecc2Config.giantWasherWaterCapacity());
        }
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
        return kind.spec().usesWater() ? engine.waterTankCapacityMb() : 0;
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
            refreshAllCorners(serverLevel);
        }
    }

    /** Returns {@code true} if automatic 9-slot input spreading is enabled on this machine. */
    public boolean isAutoSpreadInputs() {
        GiantMachineBlockEntity core = resolveCore();
        return core != null ? core.engine.isAutoSpreadInputs() : engine.isAutoSpreadInputs();
    }

    /** Toggles automatic 9-slot input spreading on the core; turning ON schedules immediate spread. */
    public boolean toggleAutoSpread() {
        GiantMachineBlockEntity core = resolveCore();
        if (core == null || !core.isCore() || core.dissolving || core.isRemoved()) {
            return false;
        }
        boolean enabled = core.engine.toggleAutoSpreadInputs();
        core.pendingInputSpread = enabled;
        core.setChanged();
        return enabled;
    }

    /** Marks a deferred input spread to run at the start of the next CORE server tick. */
    public void markPendingInputSpread() {
        GiantMachineBlockEntity core = resolveCore();
        if (core == null
                || !core.isCore()
                || core.dissolving
                || core.isRemoved()
                || core.spreading
                || !core.engine.isAutoSpreadInputs()) {
            return;
        }
        core.pendingInputSpread = true;
    }

    /** Captures a defensive snapshot of the 9 input slots for server-side click-insertion detection. */
    ItemStack[] copyInputSlots() {
        ItemStack[] snapshot = new ItemStack[INPUT_COUNT];
        for (int i = 0; i < INPUT_COUNT; i++) {
            ItemStack s = inventory.getStackInSlot(INPUT_START + i);
            snapshot[i] = s.isEmpty() ? ItemStack.EMPTY : s.copy();
        }
        return snapshot;
    }

    /**
     * Returns {@code true} iff any distinct item key (exact item + NBT tags) has a strictly higher
     * total count in slots {@code 0..8} now than in {@code before}.
     */
    boolean hasInputIncreased(ItemStack[] before) {
        if (before == null || before.length != INPUT_COUNT) {
            return false;
        }
        List<ItemStack> checkedKeys = new ArrayList<>(INPUT_COUNT);
        for (int i = 0; i < INPUT_COUNT; i++) {
            ItemStack cur = inventory.getStackInSlot(INPUT_START + i);
            if (cur.isEmpty() || containsSameKey(checkedKeys, cur)) {
                continue;
            }
            checkedKeys.add(cur);
            long afterSum = 0L;
            long beforeSum = 0L;
            for (int j = 0; j < INPUT_COUNT; j++) {
                ItemStack a = inventory.getStackInSlot(INPUT_START + j);
                if (!a.isEmpty() && ItemStack.isSameItemSameTags(a, cur)) {
                    afterSum += a.getCount();
                }
                ItemStack b = before[j];
                if (b != null && !b.isEmpty() && ItemStack.isSameItemSameTags(b, cur)) {
                    beforeSum += b.getCount();
                }
            }
            if (afterSum > beforeSum) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSameKey(List<ItemStack> keys, ItemStack candidate) {
        for (ItemStack existing : keys) {
            if (ItemStack.isSameItemSameTags(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static int findOrAddGroup(List<ItemStack> templates, ItemStack stack) {
        for (int i = 0; i < templates.size(); i++) {
            if (ItemStack.isSameItemSameTags(templates.get(i), stack)) {
                return i;
            }
        }
        templates.add(stack);
        return templates.size() - 1;
    }

    private int resolveRecipeMinUnit(ItemStack stack) {
        try {
            return GiantRecipeAdapter.recipeInputCount(kind, stack);
        } catch (Throwable failure) {
            LOG.debug(
                    "Falling back to minUnit=1 during giant machine auto-spread lookup: {}",
                    failure.getMessage());
            return 1;
        }
    }

    private boolean spreadInputsNow() {
        if (!(level instanceof ServerLevel)
                || level.isClientSide
                || !isCore()
                || dissolving
                || isRemoved()
                || spreading
                || !engine.isAutoSpreadInputs()) {
            return false;
        }
        spreading = true;
        try {
            List<ItemStack> groupTemplates = new ArrayList<>(INPUT_COUNT);
            GiantInputDistributor.SlotInput[] slotInputs =
                    new GiantInputDistributor.SlotInput[INPUT_COUNT];
            int[] beforeGroups = new int[INPUT_COUNT];
            int[] beforeCounts = new int[INPUT_COUNT];
            int[] slotCaps = new int[INPUT_COUNT];

            for (int i = 0; i < INPUT_COUNT; i++) {
                int slotLimit = Math.max(1, inventory.getSlotLimit(INPUT_START + i));
                ItemStack stack = inventory.getStackInSlot(INPUT_START + i);
                if (stack.isEmpty()) {
                    slotInputs[i] = GiantInputDistributor.SlotInput.empty();
                    beforeGroups[i] = GiantInputDistributor.EMPTY_GROUP;
                    beforeCounts[i] = 0;
                    slotCaps[i] = slotLimit;
                    continue;
                }
                int gk = findOrAddGroup(groupTemplates, stack);
                int cap = Math.max(1, Math.min(slotLimit, stack.getMaxStackSize()));
                int minUnit = resolveRecipeMinUnit(stack);
                slotInputs[i] =
                        new GiantInputDistributor.SlotInput(gk, stack.getCount(), cap, minUnit);
                beforeGroups[i] = gk;
                beforeCounts[i] = stack.getCount();
                slotCaps[i] = cap;
            }

            GiantInputDistributor.PlanResult plan = GiantInputDistributor.plan(slotInputs);
            if (!plan.changed()) {
                return false;
            }

            long[] beforeSums = new long[groupTemplates.size()];
            long[] afterSums = new long[groupTemplates.size()];
            for (int i = 0; i < INPUT_COUNT; i++) {
                if (beforeGroups[i] >= 0) {
                    beforeSums[beforeGroups[i]] += beforeCounts[i];
                }
            }

            ItemStack[] plannedStacks = new ItemStack[INPUT_COUNT];
            for (int i = 0; i < INPUT_COUNT; i++) {
                GiantInputDistributor.SlotOutput out = plan.outputs()[i];
                if (out == null || out.isEmpty()) {
                    plannedStacks[i] = ItemStack.EMPTY;
                    continue;
                }
                int gk = out.groupKey();
                if (gk < 0 || gk >= groupTemplates.size() || out.count() <= 0) {
                    LOG.warn("Aborting giant auto-spread: invalid planned output at slot {}", i);
                    return false;
                }
                boolean frozenOversized =
                        beforeGroups[i] == gk
                                && beforeCounts[i] == out.count()
                                && beforeCounts[i] > slotCaps[i];
                if (frozenOversized) {
                    ItemStack original = inventory.getStackInSlot(INPUT_START + i);
                    plannedStacks[i] = original;
                    afterSums[gk] += original.getCount();
                    continue;
                }
                ItemStack template = groupTemplates.get(gk);
                int slotLimit = Math.max(1, inventory.getSlotLimit(INPUT_START + i));
                int cap = Math.max(1, Math.min(slotLimit, template.getMaxStackSize()));
                if (out.count() > cap) {
                    LOG.warn(
                            "Aborting giant auto-spread: planned count {} exceeds cap {} at slot {}",
                            out.count(),
                            cap,
                            i);
                    return false;
                }
                ItemStack fresh = Ecc2BridgePlatform.copyWithCount(template, out.count());
                plannedStacks[i] = fresh;
                afterSums[gk] += fresh.getCount();
            }

            for (int g = 0; g < groupTemplates.size(); g++) {
                if (beforeSums[g] != afterSums[g]) {
                    LOG.warn(
                            "Aborting giant auto-spread: conservation check failed for group {} ({} != {})",
                            g,
                            beforeSums[g],
                            afterSums[g]);
                    return false;
                }
            }

            for (int i = 0; i < INPUT_COUNT; i++) {
                inventory.setStackInSlot(INPUT_START + i, plannedStacks[i]);
            }
            return true;
        } finally {
            spreading = false;
        }
    }

    private void refreshAllCorners(ServerLevel serverLevel) {
        BlockFace facing = toBlockFace(structureFacing);
        GiantPortPermissions perms = engine.portPermissions();
        Map<BlockPos, List<PortAccessMode>> cornerModes = new LinkedHashMap<>(4);
        for (GiantCornerPort port : GiantCornerPort.values()) {
            BlockPos3 offset = port.cornerOffset(facing);
            BlockPos cornerPos = worldPosition.offset(offset.x(), offset.y(), offset.z());
            cornerModes.computeIfAbsent(cornerPos, ignored -> new ArrayList<>(2)).add(perms.get(port));
        }
        for (var entry : cornerModes.entrySet()) {
            BlockPos cornerPos = entry.getKey();
            if (!serverLevel.hasChunkAt(cornerPos)) {
                continue;
            }
            List<PortAccessMode> modes = entry.getValue();
            PortAccessMode first = modes.isEmpty() ? PortAccessMode.BLOCKED : modes.get(0);
            PortAccessMode second = modes.size() > 1 ? modes.get(1) : first;
            GiantPortVisual visual = GiantPortVisual.combine(first, second);
            BlockState cornerState = serverLevel.getBlockState(cornerPos);
            if (cornerState.getBlock() instanceof GiantMachineBlock
                    && cornerState.getValue(GiantMachineBlock.FORMED)
                    && cornerState.getValue(GiantMachineBlock.ROLE) == GiantUnitRole.CORNER
                    && cornerState.getValue(GiantMachineBlock.PORT_MODE) != visual) {
                serverLevel.setBlock(
                        cornerPos,
                        cornerState.setValue(GiantMachineBlock.PORT_MODE, visual),
                        Block.UPDATE_CLIENTS);
            }
            if (serverLevel.getBlockEntity(cornerPos) instanceof GiantMachineBlockEntity cornerBe) {
                cornerBe.invalidateCornerCaps();
                TransportBlock.refreshConnections(serverLevel, cornerPos);
            }
            serverLevel.updateNeighborsAt(cornerPos, getBlockState().getBlock());
        }
    }

    private void invalidateCornerCaps() {
        List<LazyOptional<IItemHandler>> itemsCopy = List.copyOf(itemPortCaps.values());
        List<LazyOptional<IFluidHandler>> fluidsCopy = List.copyOf(fluidPortCaps.values());
        itemPortCaps.clear();
        fluidPortCaps.clear();
        itemsCopy.forEach(LazyOptional::invalidate);
        fluidsCopy.forEach(LazyOptional::invalidate);
    }

    /**
     * Resets an orphaned formed non-core block when its core's chunk is loaded and the core is
     * missing or no longer formed for this kind.
     */
    public boolean tryRecoverOrphan(ServerLevel serverLevel, BlockState state) {
        if (!formed || isCore) {
            return false;
        }
        if (!serverLevel.hasChunkAt(corePos)) {
            return false;
        }
        if (resolveCore() != null) {
            return false;
        }
        WorldEnergy.disconnect(this);
        formed = false;
        isCore = false;
        corePos = BlockPos.ZERO;
        pendingInputSpread = false;
        invalidateCornerCaps();
        setChanged();
        if (state.getBlock() instanceof GiantMachineBlock) {
            BlockState reset =
                    state.setValue(GiantMachineBlock.FORMED, Boolean.FALSE)
                            .setValue(GiantMachineBlock.ACTIVE, Boolean.FALSE)
                            .setValue(GiantMachineBlock.ROLE, GiantUnitRole.UNFORMED)
                            .setValue(GiantMachineBlock.PORT_MODE, GiantPortVisual.FULL);
            serverLevel.setBlock(worldPosition, reset, Block.UPDATE_ALL);
            serverLevel.sendBlockUpdated(worldPosition, state, reset, Block.UPDATE_CLIENTS);
        }
        return true;
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
        BlockPos3 frontPos3 = bounds.frontCenter(toBlockFace(horizontalFacing));
        BlockPos frontWorldPos = new BlockPos(frontPos3.x(), frontPos3.y(), frontPos3.z());

        for (BlockPos3 p : bounds.allPositions()) {
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            GiantUnitRole role =
                    bp.equals(frontWorldPos)
                            ? GiantUnitRole.FRONT
                            : GiantUnitRole.fromBoundsRole(bounds.roleOf(p));
            BlockState current = level.getBlockState(bp);
            BlockState updated =
                    current.setValue(GiantMachineBlock.FACING, horizontalFacing)
                            .setValue(GiantMachineBlock.FORMED, Boolean.TRUE)
                            .setValue(GiantMachineBlock.ACTIVE, Boolean.FALSE)
                            .setValue(GiantMachineBlock.ROLE, role)
                            .setValue(GiantMachineBlock.PORT_MODE, GiantPortVisual.FULL);
            level.setBlock(bp, updated, Block.UPDATE_ALL);
            if (level.getBlockEntity(bp) instanceof GiantMachineBlockEntity unit) {
                unit.formed = true;
                unit.isCore = bp.equals(coreWorldPos);
                unit.corePos = coreWorldPos;
                unit.structureFacing = horizontalFacing;
                unit.pendingInputSpread = false;
                unit.invalidateCornerCaps();
                unit.setChanged();
                level.sendBlockUpdated(bp, current, updated, Block.UPDATE_CLIENTS);
                WorldEnergy.disconnect(unit);
                WorldEnergy.connect(unit);
            }
        }
        if (level.getBlockEntity(coreWorldPos) instanceof GiantMachineBlockEntity core) {
            core.refreshAllCorners(level);
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
        core.pendingInputSpread = false;
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
                unit.invalidateCornerCaps();
                unit.setChanged();
            }
            BlockState st = level.getBlockState(bp);
            if (st.getBlock() instanceof GiantMachineBlock) {
                BlockState reset =
                        st.setValue(GiantMachineBlock.FORMED, Boolean.FALSE)
                                .setValue(GiantMachineBlock.ACTIVE, Boolean.FALSE)
                                .setValue(GiantMachineBlock.ROLE, GiantUnitRole.UNFORMED)
                                .setValue(GiantMachineBlock.PORT_MODE, GiantPortVisual.FULL);
                level.setBlock(bp, reset, Block.UPDATE_ALL);
                level.sendBlockUpdated(bp, st, reset, Block.UPDATE_CLIENTS);
                TransportBlock.refreshConnections(level, bp);
            }
        }
        WorldEnergy.disconnect(core);
        core.invalidateCornerCaps();
        core.formed = false;
        core.isCore = false;
        core.dissolving = false;
    }

    // ---- Server Tick ----

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (!isCore()) {
            if (formed && (now % 20L) == 0L) {
                tryRecoverOrphan(serverLevel, getBlockState());
            }
            return;
        }
        lastTick = now;
        if (pendingPortVisualSync) {
            pendingPortVisualSync = false;
            refreshAllCorners(serverLevel);
            BlockState curState = getBlockState();
            boolean curActive =
                    curState.hasProperty(GiantMachineBlock.ACTIVE)
                            && curState.getValue(GiantMachineBlock.ACTIVE);
            syncFrontDisplay(serverLevel, curActive);
        }
        if ((now % 20L) == 0L && !verifyStructureIntegrity(serverLevel)) {
            dissolveOnBreak(serverLevel);
            return;
        }
        refreshUpgrades();

        // Flush deferred auto-spread across slots 0..8 BEFORE lane processing so all parallel
        // lanes activate immediately on this tick.
        if (pendingInputSpread) {
            pendingInputSpread = false;
            if (engine.isAutoSpreadInputs() && spreadInputsNow()) {
                setChanged();
            }
        }

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
        boolean runAllowed = GasUpgradeAutomation.isRunAllowed(serverLevel, worldPosition, upgrades);

        boolean nowActive = false;
        if (runAllowed) {
            var summary =
                    engine.tick(
                            new GiantMachineEngine.LaneHandler() {
                                @Override
                                public int availableInputBatch(int lane, int maxBatch) {
                                    return computeLaneInputBatch(lane, maxBatch);
                                }

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

        // Sync active visual state on core block and front-center exterior display block (with legacy heal)
        BlockState state = getBlockState();
        if (state.hasProperty(GiantMachineBlock.ACTIVE)
                && state.getValue(GiantMachineBlock.ACTIVE) != nowActive) {
            serverLevel.setBlock(
                    worldPosition,
                    state.setValue(GiantMachineBlock.ACTIVE, nowActive),
                    Block.UPDATE_CLIENTS);
        }
        syncFrontDisplay(serverLevel, nowActive);
    }

    /**
     * Syncs {@link GiantMachineBlock#ACTIVE} onto the front-center exterior display block at
     * {@code corePos.relative(structureFacing).above()}, and auto-heals legacy formed saves where
     * that block still holds {@link GiantUnitRole#SHELL}.
     */
    private void syncFrontDisplay(ServerLevel serverLevel, boolean nowActive) {
        BlockPos frontPos = worldPosition.relative(structureFacing).above();
        if (!serverLevel.hasChunkAt(frontPos)) {
            return;
        }
        BlockState frontState = serverLevel.getBlockState(frontPos);
        if (!(frontState.getBlock() instanceof GiantMachineBlock gb)
                || gb.kind() != kind
                || !frontState.getValue(GiantMachineBlock.FORMED)) {
            return;
        }
        GiantUnitRole frontRole = frontState.getValue(GiantMachineBlock.ROLE);
        boolean needsRoleUpgrade = frontRole == GiantUnitRole.SHELL;
        if (!needsRoleUpgrade && frontRole != GiantUnitRole.FRONT) {
            return;
        }
        boolean needsFacingSync = frontState.getValue(GiantMachineBlock.FACING) != structureFacing;
        boolean needsActiveSync = frontState.getValue(GiantMachineBlock.ACTIVE) != nowActive;
        if (needsRoleUpgrade || needsFacingSync || needsActiveSync) {
            BlockState updated =
                    frontState
                            .setValue(GiantMachineBlock.ROLE, GiantUnitRole.FRONT)
                            .setValue(GiantMachineBlock.FACING, structureFacing)
                            .setValue(GiantMachineBlock.ACTIVE, nowActive);
            serverLevel.setBlock(frontPos, updated, Block.UPDATE_CLIENTS);
        }
    }

    private boolean verifyStructureIntegrity(ServerLevel serverLevel) {
        MultiblockBounds bounds =
                MultiblockGeometry.boundsFromCore(
                        new BlockPos3(
                                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        for (BlockPos3 p : bounds.allPositions()) {
            BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
            if (!serverLevel.hasChunkAt(bp)) {
                continue;
            }
            BlockState st = serverLevel.getBlockState(bp);
            if (!(st.getBlock() instanceof GiantMachineBlock gb)
                    || gb.kind() != kind
                    || !st.getValue(GiantMachineBlock.FORMED)) {
                return false;
            }
        }
        return true;
    }

    private int computeLaneInputBatch(int lane, int maxBatch) {
        ItemStack input = inventory.getStackInSlot(INPUT_START + lane);
        if (input.isEmpty()) {
            return 0;
        }
        GiantRecipeAdapter.ResolvedRecipe recipe = GiantRecipeAdapter.match(kind, input);
        if (recipe == null) {
            return 0;
        }
        int byInput = input.getCount() / recipe.inputCount();
        return Math.max(0, Math.min(maxBatch, byInput));
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
        int cap = Math.min(maxBatch, byInput);
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
        BlockFace structFace = toBlockFace(structureFacing);
        int[] outSlots = kind.spec().usesWater() ? OUTPUT_SLOTS_WASHER : OUTPUT_SLOTS_STANDARD;
        for (ItemStack u : upgrades) {
            if (u.isEmpty()) {
                continue;
            }
            String id = Ecc2BridgePlatform.itemId(u);
            boolean isItemEject =
                    "ic2:ejector_upgrade".equals(id) || "ic2:advanced_ejector_upgrade".equals(id);
            boolean isItemPull =
                    "ic2:pulling_upgrade".equals(id) || "ic2:advanced_pulling_upgrade".equals(id);
            boolean isFluidPull = "ic2:fluid_pulling_upgrade".equals(id);
            if (!isItemEject && !isItemPull && !isFluidPull) {
                continue;
            }
            List<Direction> allowedFaces = MachineAutomation.faces(u);
            int itemLimit = Math.max(1, ProcessingUpgrades.transferLimit(u.getCount()));
            int fluidBudget = Math.max(1_000, u.getCount() * 1_000);
            for (GiantCornerPort port : GiantCornerPort.values()) {
                PortAccessMode mode = engine.portPermissions().get(port);
                if (!mode.connects()) {
                    continue;
                }
                Direction outDir = toDirection(port.worldFace(structFace));
                if (!allowedFaces.contains(outDir)) {
                    continue;
                }
                BlockPos3 offset = port.cornerOffset(structFace);
                BlockPos neighborPos =
                        worldPosition.offset(offset.x(), offset.y(), offset.z()).relative(outDir);
                if (!level.hasChunkAt(neighborPos)) {
                    continue;
                }
                BlockEntity neighbor = level.getBlockEntity(neighborPos);
                if (neighbor == null) {
                    continue;
                }
                if (isItemEject && mode.allowsOutput()) {
                    var target =
                            neighbor.getCapability(
                                            ForgeCapabilities.ITEM_HANDLER, outDir.getOpposite())
                                    .resolve();
                    if (target.isPresent()
                            && GasUpgradeAutomation.ejectItems(
                                    inventory, outSlots, target.get(), itemLimit)) {
                        setChanged();
                        break;
                    }
                }
                if (isItemPull && mode.allowsInput()) {
                    var source =
                            neighbor.getCapability(
                                            ForgeCapabilities.ITEM_HANDLER, outDir.getOpposite())
                                    .resolve();
                    if (source.isPresent()
                            && GasUpgradeAutomation.pullItems(
                                    inventory,
                                    INPUT_SLOTS,
                                    source.get(),
                                    itemLimit,
                                    s -> GiantRecipeAdapter.acceptsInput(kind, s))) {
                        markPendingInputSpread();
                        setChanged();
                        break;
                    }
                }
                if (isFluidPull && kind.spec().usesWater() && mode.allowsInput()) {
                    var fluidCap =
                            neighbor.getCapability(
                                            ForgeCapabilities.FLUID_HANDLER, outDir.getOpposite())
                                    .resolve();
                    if (fluidCap.isPresent()) {
                        int room = configuredWaterCapacity() - engine.waterMb();
                        if (room > 0) {
                            FluidStack drained =
                                    fluidCap.get()
                                            .drain(
                                                    new FluidStack(
                                                            Fluids.WATER,
                                                            Math.min(fluidBudget, room)),
                                                    IFluidHandler.FluidAction.EXECUTE);
                            if (!drained.isEmpty() && drained.getFluid() == Fluids.WATER) {
                                engine.fillWater(drained.getAmount(), false);
                                setChanged();
                                break;
                            }
                        }
                    }
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
        applyConfigToEngine();
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
        if (!formed || side == null) {
            return super.getCapability(cap, side);
        }
        GiantMachineBlockEntity core = resolveCore();
        if (core == null) {
            return super.getCapability(cap, side);
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            var portOpt = cornerPortForSide(side);
            if (portOpt.isEmpty()) {
                return LazyOptional.empty();
            }
            PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
            if (!mode.connects()) {
                return LazyOptional.empty();
            }
            return itemPortCaps
                    .computeIfAbsent(
                            side,
                            ignored ->
                                    LazyOptional.of(
                                            () ->
                                                    core.createPortItemHandler(
                                                            mode.allowsInput(),
                                                            mode.allowsOutput())))
                    .cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && core.kind.spec().usesWater()) {
            var portOpt = cornerPortForSide(side);
            if (portOpt.isEmpty()) {
                return LazyOptional.empty();
            }
            PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
            if (!mode.connects()) {
                return LazyOptional.empty();
            }
            return fluidPortCaps
                    .computeIfAbsent(
                            side,
                            ignored ->
                                    LazyOptional.of(
                                            () ->
                                                    core.createPortFluidHandler(
                                                            mode.allowsInput(),
                                                            mode.allowsOutput())))
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
                int offered = stack.getCount();
                ItemStack remainder = inventory.insertItem(slot, stack, simulate);
                if (!simulate
                        && slot >= INPUT_START
                        && slot < INPUT_START + INPUT_COUNT
                        && offered > 0
                        && remainder.getCount() < offered) {
                    markPendingInputSpread();
                }
                return remainder;
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
        var portOpt = cornerPortForSide(side);
        if (portOpt.isEmpty()) {
            return false;
        }
        GiantMachineBlockEntity core = resolveCore();
        if (core == null || !core.kind.spec().usesWater()) {
            return false;
        }
        if (!core.engine.portPermissions().allowsInput(portOpt.get())) {
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
        invalidateCornerCaps();
        super.setRemoved();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateCornerCaps();
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        GiantMachineBlockEntity core = resolveCore();
        return core != null && core.lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        if (!formed || resolveCore() == null) {
            return Ecc2EnergyHelper.NO_PORTS;
        }
        MultiblockBounds bounds =
                MultiblockGeometry.boundsFromCore(
                        new BlockPos3(corePos.getX(), corePos.getY(), corePos.getZ()));
        BlockPos3 here =
                new BlockPos3(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        var inputs = EnumSet.noneOf(CapabilityFacade.Face.class);
        for (Direction dir : Direction.values()) {
            if (bounds.isExteriorFace(here, toBlockFace(dir))) {
                inputs.add(Ecc2EnergyHelper.toFace(dir));
            }
        }
        return inputs.isEmpty()
                ? Ecc2EnergyHelper.NO_PORTS
                : new EnergyGraph.Ports(inputs, Set.of(), false);
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
        return core != null ? core.engine.maxInputPacket() : Double.POSITIVE_INFINITY;
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
            tag.putBoolean("AutoSpread", engine.isAutoSpreadInputs());
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
            engine.setAutoSpreadInputs(tag.getBoolean("AutoSpread"));
            pendingInputSpread = false;
            pendingPortVisualSync = true;
            int[] prog = tag.getIntArray("LaneProgress");
            int[] batch = tag.getIntArray("LaneBatch");
            if (prog.length == GiantMachineEngine.LANE_COUNT
                    && batch.length == GiantMachineEngine.LANE_COUNT) {
                engine.restoreLanes(prog, batch);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("Formed", formed);
        tag.putBoolean("IsCore", isCore);
        tag.putInt("CoreX", corePos.getX());
        tag.putInt("CoreY", corePos.getY());
        tag.putInt("CoreZ", corePos.getZ());
        tag.putInt("Facing", structureFacing.get2DDataValue());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        formed = tag.getBoolean("Formed");
        isCore = tag.getBoolean("IsCore");
        corePos = new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ"));
        structureFacing = Direction.from2DDataValue(tag.getInt("Facing"));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
