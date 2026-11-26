package com.ecocraft2.giant;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.giant.BlockFace;
import com.ecocraft2.common.giant.BlockPos3;
import com.ecocraft2.common.giant.GiantCornerPort;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantPortPermissions;
import com.ecocraft2.common.giant.MultiblockBounds;
import com.ecocraft2.common.giant.MultiblockGeometry;
import com.ecocraft2.common.giant.PortAccessMode;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.ProcessingSlots;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.logistics.FluidContents;
import com.industrialage.ia2.logistics.TransportBlock;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity backing every block of a 3x3x3 Giant Machine.
 *
 * <p>When formed, the bottom-center {@link GiantBlockRole#CORE} block owns the 25-slot shared
 * inventory, {@link GiantMachineEngine} (9 lanes x 3 operations), 64,000 mB Ore Washer water tank,
 * and {@link GiantPortPermissions}. All 26 proxy blocks forward EU reception to the core, while the
 * 4 bottom {@link GiantBlockRole#CORNER} blocks expose item and fluid automation exclusively on
 * their 2 outward horizontal faces (8 {@link GiantCornerPort}s).
 */
public final class GiantUnitBlockEntity extends Ecc2BlockEntityBase
        implements WorldPowerPort,
                EuBuffer,
                MachineAccessTarget,
                Ecc2CapabilityProvider,
                MenuProvider {

    public static final int INPUT_START = 0;
    public static final int INPUT_COUNT = 9;
    public static final int OUTPUT_START = 9;
    public static final int OUTPUT_COUNT = 9;
    public static final int SLOT_BATTERY = 18;
    public static final int UPGRADE_START = 19;
    public static final int UPGRADE_COUNT = 4;
    public static final int SLOT_WASHER_IN = 23;
    public static final int SLOT_WASHER_OUT = 24;
    public static final int INVENTORY_SIZE = 25;

    private static final int[] INPUT_SLOTS = IntStream.range(0, 9).toArray();
    private static final int[] OUTPUT_SLOTS_STANDARD = IntStream.range(9, 18).toArray();
    private static final int[] OUTPUT_SLOTS_WASHER = {9, 10, 11, 12, 13, 14, 15, 16, 17, 24};
    private static final int[] AUTOMATED_SLOTS_STANDARD = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
    };
    private static final int[] AUTOMATED_SLOTS_WASHER = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 23, 24
    };
    private static final EnergyGraph.Ports EMPTY_PORTS =
            new EnergyGraph.Ports(Set.of(), Set.of(), false);

    private final GiantMachineType machineType;
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final ProcessingSlots coreAutomationSlots = new CoreAutomationSlots();
    private GiantMachineEngine engine;

    private boolean formed;
    private GiantBlockRole role = GiantBlockRole.UNFORMED;
    private BlockPos corePos;
    private Direction structureFacing = Direction.NORTH;
    private boolean dissolved;
    private boolean pendingPortVisualSync;
    private int lastTick = Integer.MIN_VALUE;

    public GiantUnitBlockEntity(BlockPos pos, BlockState state) {
        super(GiantGasModule.GIANT_UNIT_BE.get(), pos, state);
        this.machineType =
                state.getBlock() instanceof GiantUnitBlock unit
                        ? unit.machineType()
                        : GiantMachineType.MACERATOR;
        this.engine = new GiantMachineEngine(this.machineType.effectiveSpec());
        this.formed = state.hasProperty(GiantUnitBlock.FORMED) && state.getValue(GiantUnitBlock.FORMED);
        this.role =
                state.hasProperty(GiantUnitBlock.ROLE)
                        ? state.getValue(GiantUnitBlock.ROLE)
                        : GiantBlockRole.UNFORMED;
        this.structureFacing =
                state.hasProperty(GiantUnitBlock.FACING)
                        ? state.getValue(GiantUnitBlock.FACING)
                        : Direction.NORTH;
    }

    // ---- Multiblock Lifecycle ----

    void bindFormed(BlockPos corePos, Direction structureFacing, GiantBlockRole role) {
        this.formed = true;
        this.corePos = corePos.immutable();
        this.structureFacing = structureFacing;
        this.role = role;
        this.dissolved = false;
        if (role == GiantBlockRole.CORE) {
            this.engine = new GiantMachineEngine(this.machineType.effectiveSpec());
        }
        setChanged();
    }

    void unbindFormed() {
        this.formed = false;
        this.corePos = null;
        this.role = GiantBlockRole.UNFORMED;
        this.dissolved = false;
        setChanged();
    }

    boolean markDissolvedOnce() {
        if (dissolved) {
            return false;
        }
        dissolved = true;
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (formed && getLevel() instanceof ServerLevel) {
            WorldEnergy.connect(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        WorldEnergy.disconnect(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        WorldEnergy.disconnect(this);
    }

    @Override
    protected void onRemovedFromWorld(ServerLevel level, BlockPos pos, BlockState oldState) {
        if (formed && corePos != null) {
            GiantMultiblockController.dissolve(level, corePos, pos, this);
        }
    }

    // ---- Server Tick (CORE processing + cross-chunk integrity & orphan recovery) ----

    void serverTick(ServerLevel level, BlockState state) {
        if (!formed) {
            return;
        }
        int tickCount = level.getServer().getTickCount();
        if (role != GiantBlockRole.CORE) {
            if (Math.floorMod(tickCount + worldPosition.hashCode(), 20) == 0) {
                tryRecoverOrphan(level, state);
            }
            return;
        }
        lastTick = tickCount;
        if (pendingPortVisualSync) {
            pendingPortVisualSync = false;
            refreshAllCorners(level);
        }
        if (Math.floorMod(tickCount + worldPosition.hashCode(), 20) == 0) {
            BlockPos brokenPos = findBrokenMember(level);
            if (brokenPos != null) {
                GiantMultiblockController.dissolve(level, worldPosition, brokenPos, null);
                return;
            }
        }
        boolean changed = false;

        // 1. Refresh IC2 upgrades (Overclocker, Transformer, Energy Storage).
        var upgradeList = upgradeStacks();
        var profile = Ecc2UpgradeHelper.readProfile(upgradeList);
        if (!profile.equals(engine.upgrades())) {
            engine.setUpgrades(profile);
            changed = true;
        }

        // 2. Discharge battery slot (18) & redstone dust into core EU buffer.
        changed |= dischargeBattery();

        // 3. Drain Ore Washer water containers from slot 23 -> 24.
        if (usesWater()) {
            changed |= drainWasherContainer();
        }

        // 4. Optional redstone inverter gating: when installed, require redstone signal on core/corners.
        boolean canRun =
                !Ecc2UpgradeHelper.hasRedstoneInverter(upgradeList)
                        || hasMultiblockRedstoneSignal(level);

        // 5. Tick 9-lane parallel GiantMachineEngine using configured per-lane batch cap.
        int maxBatchCfg = Math.max(1, Math.min(64, Ecc2Config.giantMaxBatchPerLane()));
        if (engine.maxBatchPerLane() != maxBatchCfg) {
            engine.setMaxBatchPerLane(maxBatchCfg);
        }
        if (canRun) {
            var summary =
                    engine.tick(
                            new GiantMachineEngine.LaneHandler() {
                                @Override
                                public int availableBatch(int lane, int maxBatch) {
                                    int capped = Math.min(maxBatch, maxBatchCfg);
                                    return computeAvailableBatch(level, lane, capped);
                                }

                                @Override
                                public int availableInputBatch(int lane, int maxBatch) {
                                    int capped = Math.min(maxBatch, maxBatchCfg);
                                    return computeAvailableInputBatch(level, lane, capped);
                                }

                                @Override
                                public void completeOperations(int lane, int completedOps) {
                                    executeLaneCompletion(level, lane, completedOps);
                                }
                            });
            if (summary.isActive() || summary.euConsumed() > 0 || summary.waterConsumedMb() > 0) {
                changed = true;
            }
        }

        // 6. Execute IC2 item & fluid ejector/pulling upgrades via enabled lower-corner ports.
        changed |= executeCornerUpgrades(level, upgradeList);

        // 7. Sync active blockstate on core.
        boolean nowActive = canRun && engine.isActive();
        if (!isRemoved() && state.getValue(GiantUnitBlock.ACTIVE) != nowActive) {
            level.setBlock(
                    worldPosition,
                    state.setValue(GiantUnitBlock.ACTIVE, nowActive),
                    Block.UPDATE_CLIENTS);
        }
        if (changed) {
            setChanged();
        }
    }

    /** Recovers a non-core block orphaned when its core was broken across a chunk boundary. */
    boolean tryRecoverOrphan(ServerLevel level, BlockState state) {
        if (!formed || role == GiantBlockRole.CORE) {
            return false;
        }
        if (corePos != null && !level.hasChunkAt(corePos)) {
            return false;
        }
        if (coreEntity() != null) {
            return false;
        }
        WorldEnergy.disconnect(this);
        unbindFormed();
        if (state.getBlock() instanceof GiantUnitBlock) {
            BlockState unformed =
                    state.setValue(GiantUnitBlock.FORMED, false)
                            .setValue(GiantUnitBlock.ACTIVE, false)
                            .setValue(GiantUnitBlock.ROLE, GiantBlockRole.UNFORMED)
                            .setValue(GiantUnitBlock.PORT_MODE, GiantPortVisual.FULL);
            level.setBlock(worldPosition, unformed, Block.UPDATE_ALL);
        }
        invalidateCapabilities();
        TransportBlock.refreshConnections(level, worldPosition);
        return true;
    }

    private BlockPos findBrokenMember(ServerLevel level) {
        MultiblockBounds bounds =
                MultiblockGeometry.boundsFromCore(GiantMultiblockController.toPos3(worldPosition));
        for (BlockPos3 p3 : bounds.allPositions()) {
            BlockPos pos = GiantMultiblockController.toBlockPos(p3);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState st = level.getBlockState(pos);
            if (!(st.getBlock() instanceof GiantUnitBlock unit)
                    || unit.machineType() != machineType
                    || !st.getValue(GiantUnitBlock.FORMED)) {
                return pos;
            }
            if (!(level.getBlockEntity(pos) instanceof GiantUnitBlockEntity member)
                    || !member.isFormed()
                    || !worldPosition.equals(member.corePos())) {
                return pos;
            }
        }
        return null;
    }

    private boolean hasMultiblockRedstoneSignal(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition)) {
            return true;
        }
        BlockFace facing = GiantMultiblockController.toBlockFace(structureFacing);
        for (GiantCornerPort port : GiantCornerPort.values()) {
            BlockPos3 off = port.cornerOffset(facing);
            BlockPos cornerPos = worldPosition.offset(off.x(), off.y(), off.z());
            if (level.hasChunkAt(cornerPos) && level.hasNeighborSignal(cornerPos)) {
                return true;
            }
        }
        return false;
    }

    private boolean dischargeBattery() {
        ItemStack stack = items.get(SLOT_BATTERY);
        if (stack.isEmpty() || engine.demandedEu() <= 0.0) {
            return false;
        }
        double extracted =
                PortableGear.discharge(
                        stack, engine.demandedEu(), engine.energyCapacity(), engine.inputTier());
        if (extracted > 0.0) {
            engine.injectEu(extracted);
            return true;
        }
        if (stack.is(Items.REDSTONE) && engine.demandedEu() >= 800.0) {
            engine.injectEu(800.0);
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private boolean drainWasherContainer() {
        ItemStack in = items.get(SLOT_WASHER_IN);
        int room = engine.waterTankCapacityMb() - engine.waterMb();
        if (in.isEmpty() || room <= 0) {
            return false;
        }
        var drained =
                GiantGasPlatform.drainFluidContainer(
                        in, Ecc2Ids.MINECRAFT_WATER_ID::equals, room);
        if (drained == null || drained.amountMb() <= 0) {
            return false;
        }
        ItemStack remainder = drained.resultingItem();
        if (!remainder.isEmpty() && !canMergeIntoSlot(SLOT_WASHER_OUT, remainder)) {
            return false;
        }
        engine.fillWater(drained.amountMb(), false);
        in.shrink(1);
        if (!remainder.isEmpty()) {
            mergeIntoSlot(SLOT_WASHER_OUT, remainder);
        }
        return true;
    }

    private int computeAvailableInputBatch(ServerLevel level, int lane, int maxBatch) {
        ItemStack input = items.get(INPUT_START + lane);
        if (input.isEmpty() || maxBatch <= 0) {
            return 0;
        }
        GiantRecipeAdapter.Result recipe = machineType.recipeAdapter().resolve(level, input);
        if (recipe == null || recipe.inputCount() <= 0) {
            return 0;
        }
        return Math.min(maxBatch, input.getCount() / recipe.inputCount());
    }

    private int computeAvailableBatch(ServerLevel level, int lane, int maxBatch) {
        ItemStack input = items.get(INPUT_START + lane);
        if (input.isEmpty() || maxBatch <= 0) {
            return 0;
        }
        GiantRecipeAdapter.Result recipe = machineType.recipeAdapter().resolve(level, input);
        if (recipe == null || recipe.inputCount() <= 0) {
            return 0;
        }
        int byInput = input.getCount() / recipe.inputCount();
        int candidate = Math.min(maxBatch, byInput);
        if (candidate <= 0) {
            return 0;
        }
        for (int k = candidate; k >= 1; k--) {
            if (canFitOutputs(recipe.outputs(), k)) {
                return k;
            }
        }
        return 0;
    }

    private void executeLaneCompletion(ServerLevel level, int lane, int completedOps) {
        ItemStack input = items.get(INPUT_START + lane);
        GiantRecipeAdapter.Result recipe = machineType.recipeAdapter().resolve(level, input);
        if (recipe == null) {
            return;
        }
        input.shrink(recipe.inputCount() * completedOps);
        if (recipe.recycler()) {
            int scrapCount = 0;
            for (int op = 0; op < completedOps; op++) {
                if (engine.rollRecyclerScrap(level.getRandom()::nextInt)) {
                    scrapCount++;
                }
            }
            if (scrapCount > 0 && !recipe.outputs().isEmpty()) {
                insertIntoOutputGrid(recipe.outputs().get(0).copyWithCount(scrapCount));
            }
        } else {
            for (ItemStack template : recipe.outputs()) {
                int total = template.getCount() * completedOps;
                while (total > 0) {
                    int chunk = Math.min(total, template.getMaxStackSize());
                    insertIntoOutputGrid(template.copyWithCount(chunk));
                    total -= chunk;
                }
            }
        }
    }

    /** Simulates inserting {@code batchCount} copies of all output stacks into slots 9..17. */
    private boolean canFitOutputs(List<ItemStack> perOpOutputs, int batchCount) {
        if (perOpOutputs.isEmpty() || batchCount <= 0) {
            return true;
        }
        ItemStack[] sim = new ItemStack[OUTPUT_COUNT];
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            sim[i] = items.get(OUTPUT_START + i).copy();
        }
        for (ItemStack template : perOpOutputs) {
            int remaining = template.getCount() * batchCount;
            int maxStack = template.getMaxStackSize();
            for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
                if (!sim[i].isEmpty()
                        && ItemStack.isSameItemSameComponents(sim[i], template)
                        && sim[i].getCount() < maxStack) {
                    int add = Math.min(remaining, maxStack - sim[i].getCount());
                    sim[i].grow(add);
                    remaining -= add;
                }
            }
            for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
                if (sim[i].isEmpty()) {
                    int add = Math.min(remaining, maxStack);
                    sim[i] = template.copyWithCount(add);
                    remaining -= add;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void insertIntoOutputGrid(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int remaining = stack.getCount();
        int maxStack = stack.getMaxStackSize();
        for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
            ItemStack existing = items.get(OUTPUT_START + i);
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < maxStack) {
                int add = Math.min(remaining, maxStack - existing.getCount());
                existing.grow(add);
                remaining -= add;
            }
        }
        for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
            if (items.get(OUTPUT_START + i).isEmpty()) {
                int add = Math.min(remaining, maxStack);
                items.set(OUTPUT_START + i, stack.copyWithCount(add));
                remaining -= add;
            }
        }
    }

    private boolean canMergeIntoSlot(int slot, ItemStack incoming) {
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) {
            return incoming.getCount() <= incoming.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(existing, incoming)
                && existing.getCount() + incoming.getCount() <= existing.getMaxStackSize();
    }

    private void mergeIntoSlot(int slot, ItemStack incoming) {
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) {
            items.set(slot, incoming.copy());
        } else {
            existing.grow(incoming.getCount());
        }
    }

    private boolean executeCornerUpgrades(ServerLevel level, List<ItemStack> upgradeList) {
        var endpoints = buildCornerEndpoints(level);
        if (endpoints.isEmpty()) {
            return false;
        }
        int[] outSlots = usesWater() ? OUTPUT_SLOTS_WASHER : OUTPUT_SLOTS_STANDARD;
        boolean changed =
                Ecc2UpgradeHelper.executeItemUpgrades(
                        level,
                        upgradeList,
                        endpoints,
                        coreAutomationSlots,
                        INPUT_SLOTS,
                        outSlots,
                        stack -> machineType.recipeAdapter().acceptsInput(level, stack));
        if (usesWater()) {
            changed |=
                    Ecc2UpgradeHelper.executeFluidUpgrades(
                            level,
                            upgradeList,
                            endpoints,
                            Ecc2Ids.MINECRAFT_WATER_ID::equals,
                            (fid, amt) -> engine.fillWater(amt, false),
                            () -> engine.waterTankCapacityMb() - engine.waterMb(),
                            null,
                            null,
                            null);
        }
        return changed;
    }

    private List<Ecc2UpgradeHelper.PortEndpoint> buildCornerEndpoints(ServerLevel level) {
        var list = new ArrayList<Ecc2UpgradeHelper.PortEndpoint>(8);
        BlockFace facing = GiantMultiblockController.toBlockFace(structureFacing);
        GiantPortPermissions perms = engine.portPermissions();
        for (GiantCornerPort port : GiantCornerPort.values()) {
            PortAccessMode mode = perms.get(port);
            if (!mode.connects()) {
                continue;
            }
            BlockPos3 off = port.cornerOffset(facing);
            BlockPos cornerPos = worldPosition.offset(off.x(), off.y(), off.z());
            if (!level.hasChunkAt(cornerPos)
                    || !(level.getBlockEntity(cornerPos) instanceof GiantUnitBlockEntity cornerBe)) {
                continue;
            }
            Direction worldDir = GiantMultiblockController.toDirection(port.worldFace(facing));
            list.add(
                    new Ecc2UpgradeHelper.PortEndpoint(
                            cornerBe, cornerPos, worldDir, mode.allowsInput(), mode.allowsOutput()));
        }
        return list;
    }

    // ---- 8 Lower-Corner Port Permission API ----

    public Optional<GiantCornerPort> cornerPortFor(Direction worldFace) {
        if (!formed || role != GiantBlockRole.CORNER || worldFace == null || corePos == null) {
            return Optional.empty();
        }
        int dx = worldPosition.getX() - corePos.getX();
        int dy = worldPosition.getY() - corePos.getY();
        int dz = worldPosition.getZ() - corePos.getZ();
        return GiantCornerPort.fromOffsetAndFace(
                GiantMultiblockController.toBlockFace(structureFacing),
                dx,
                dy,
                dz,
                GiantMultiblockController.toBlockFace(worldFace));
    }

    public PortAccessMode cyclePortPermission(ServerLevel level, GiantCornerPort port) {
        GiantUnitBlockEntity core = coreEntity();
        if (core == null) {
            return PortAccessMode.BLOCKED;
        }
        PortAccessMode next = core.engine.portPermissions().cycle(port);
        core.setChanged();
        core.refreshAllCorners(level);
        return next;
    }

    public void setPortPermission(ServerLevel level, GiantCornerPort port, PortAccessMode mode) {
        GiantUnitBlockEntity core = coreEntity();
        if (core == null) {
            return;
        }
        core.engine.portPermissions().set(port, mode);
        core.setChanged();
        core.refreshAllCorners(level);
    }

    private void refreshAllCorners(ServerLevel level) {
        BlockFace facing = GiantMultiblockController.toBlockFace(structureFacing);
        GiantPortPermissions perms = engine.portPermissions();
        Map<BlockPos, List<PortAccessMode>> cornerModes = new LinkedHashMap<>(4);
        for (GiantCornerPort port : GiantCornerPort.values()) {
            BlockPos3 off = port.cornerOffset(facing);
            BlockPos cornerPos = worldPosition.offset(off.x(), off.y(), off.z());
            cornerModes.computeIfAbsent(cornerPos, ignored -> new ArrayList<>(2)).add(perms.get(port));
        }
        for (var entry : cornerModes.entrySet()) {
            BlockPos cornerPos = entry.getKey();
            if (!level.hasChunkAt(cornerPos)) {
                continue;
            }
            List<PortAccessMode> modes = entry.getValue();
            PortAccessMode first = modes.isEmpty() ? PortAccessMode.BLOCKED : modes.get(0);
            PortAccessMode second = modes.size() > 1 ? modes.get(1) : first;
            GiantPortVisual visual = GiantPortVisual.combine(first, second);
            BlockState cornerState = level.getBlockState(cornerPos);
            if (cornerState.getBlock() instanceof GiantUnitBlock
                    && cornerState.getValue(GiantUnitBlock.FORMED)
                    && cornerState.getValue(GiantUnitBlock.ROLE) == GiantBlockRole.CORNER
                    && cornerState.getValue(GiantUnitBlock.PORT_MODE) != visual) {
                level.setBlock(
                        cornerPos,
                        cornerState.setValue(GiantUnitBlock.PORT_MODE, visual),
                        Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(cornerPos) instanceof GiantUnitBlockEntity cornerBe) {
                cornerBe.invalidateCapabilities();
                TransportBlock.refreshConnections(level, cornerPos);
            }
        }
    }

    // ---- WorldPowerPort & EuBuffer (ALL formed blocks connect & delegate to CORE) ----

    @Override
    public EnergyGraph.Ports energyPorts() {
        if (!formed || corePos == null || coreEntity() == null) {
            return EMPTY_PORTS;
        }
        MultiblockBounds bounds =
                MultiblockGeometry.boundsFromCore(GiantMultiblockController.toPos3(corePos));
        BlockPos3 here = GiantMultiblockController.toPos3(worldPosition);
        var inputs = EnumSet.noneOf(CapabilityFacade.Face.class);
        for (Direction dir : Direction.values()) {
            if (bounds.isExteriorFace(here, GiantMultiblockController.toBlockFace(dir))) {
                inputs.add(CapabilityFacade.Face.valueOf(dir.name()));
            }
        }
        return inputs.isEmpty()
                ? EMPTY_PORTS
                : new EnergyGraph.Ports(inputs, Set.of(), false);
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        GiantUnitBlockEntity core = coreEntity();
        return core != null
                && !core.isRemoved()
                && core.lastTick == level.getServer().getTickCount();
    }

    @Override
    public boolean source() {
        return false;
    }

    @Override
    public double packetLimit() {
        GiantUnitBlockEntity core = coreEntity();
        // Never return 0.0 for an orphaned or cross-chunk unloaded core (which would trigger
        // spurious overvoltage explosions on adjacent cables before orphan recovery runs).
        return core == null ? Double.POSITIVE_INFINITY : core.engine.maxInputPacket();
    }

    @Override
    public double receive(double amount) {
        GiantUnitBlockEntity core = coreEntity();
        if (core == null || amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double remainder = core.engine.injectEu(amount);
        double accepted = Math.max(0.0, amount - remainder);
        if (accepted > 0.0) {
            core.setChanged();
        }
        return accepted;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        GiantUnitBlockEntity core = coreEntity();
        if (core == null) {
            return new EuBuffer.Reading(0.0, 0.0);
        }
        return new EuBuffer.Reading(core.engine.storedEu(), core.engine.energyCapacity());
    }

    // ---- MachineAccessTarget & Ecc2CapabilityProvider (8 Lower-Corner Ports) ----

    @Override
    public boolean exposesMachineAccess() {
        // Giant machines use dedicated 8-lower-corner permissions instead of IC2's 6-side panel.
        return false;
    }

    @Override
    public boolean usesMachineAccess() {
        return false;
    }

    @Override
    public boolean exchangeHeldFluid(Player player, InteractionHand hand, Direction side) {
        var portOpt = cornerPortFor(side);
        if (portOpt.isEmpty()) {
            return false;
        }
        GiantUnitBlockEntity core = coreEntity();
        if (core == null || !core.usesWater()) {
            return false;
        }
        if (!core.engine.portPermissions().allowsInput(portOpt.get())) {
            return false;
        }
        return GiantGasPlatform.interactPlayerFluid(
                core, player, hand, side, core.waterTankView(true, false));
    }

    @Override
    public ProcessingSlots itemCapabilitySlots(Direction side) {
        var portOpt = cornerPortFor(side);
        if (portOpt.isEmpty()) {
            return null;
        }
        GiantUnitBlockEntity core = coreEntity();
        if (core == null) {
            return null;
        }
        PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
        return mode.connects() ? core.coreAutomationSlots : null;
    }

    /** Returns the core's internal inventory view for menus, upgrades, and break drops only. */
    public Container coreInventory() {
        return coreAutomationSlots;
    }

    @Override
    public boolean allowsItemInput(Direction side) {
        var portOpt = cornerPortFor(side);
        if (portOpt.isEmpty()) {
            return false;
        }
        GiantUnitBlockEntity core = coreEntity();
        return core != null && core.engine.portPermissions().allowsInput(portOpt.get());
    }

    @Override
    public boolean allowsItemOutput(Direction side) {
        var portOpt = cornerPortFor(side);
        if (portOpt.isEmpty()) {
            return false;
        }
        GiantUnitBlockEntity core = coreEntity();
        return core != null && core.engine.portPermissions().allowsOutput(portOpt.get());
    }

    @Override
    public Ecc2FluidTankView fluidCapabilityTank(Direction side) {
        var portOpt = cornerPortFor(side);
        if (portOpt.isEmpty()) {
            return null;
        }
        GiantUnitBlockEntity core = coreEntity();
        if (core == null || !core.usesWater()) {
            return null;
        }
        PortAccessMode mode = core.engine.portPermissions().get(portOpt.get());
        if (!mode.connects()) {
            return null;
        }
        return core.waterTankView(mode.allowsInput(), mode.allowsOutput());
    }

    private Ecc2FluidTankView waterTankView(boolean allowIn, boolean allowOut) {
        return new Ecc2FluidTankView() {
            @Override
            public int tankCount() {
                return 1;
            }

            @Override
            public String fluidId(int tank) {
                return tank == 0 && engine.waterMb() > 0 ? Ecc2Ids.MINECRAFT_WATER_ID : "";
            }

            @Override
            public int fluidAmount(int tank) {
                return tank == 0 ? engine.waterMb() : 0;
            }

            @Override
            public int tankCapacity(int tank) {
                return tank == 0 ? engine.waterTankCapacityMb() : 0;
            }

            @Override
            public boolean acceptsFluid(int tank, String fluidId) {
                return tank == 0 && Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId);
            }

            @Override
            public boolean allowsInput() {
                return allowIn;
            }

            @Override
            public boolean allowsOutput() {
                return allowOut;
            }

            @Override
            public int fillFluid(int tank, String fluidId, int amount, boolean simulate) {
                if (!allowIn || !Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.fillWater(amount, simulate);
            }

            @Override
            public int drainFluid(int tank, String fluidId, int maxDrain, boolean simulate) {
                if (!allowOut) {
                    return 0;
                }
                if (!fluidId.isEmpty() && !Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.drainWater(maxDrain, simulate);
            }

            @Override
            public void restoreSnapshot(Snapshot snapshot) {
                if (snapshot.amounts().length > 0) {
                    engine.setWaterMb(snapshot.amounts()[0]);
                }
            }

            @Override
            public void markChanged() {
                setChanged();
            }
        };
    }

    // ---- Internal Core ProcessingSlots (NOT directly implemented on GiantUnitBlockEntity so
    // vanilla hoppers/droppers cannot bypass the 8 lower-corner capability gates or void items
    // into unformed/shell blocks) ----

    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot >= INPUT_START && slot < INPUT_START + INPUT_COUNT) {
            return !(getLevel() instanceof ServerLevel server)
                    || machineType.recipeAdapter().acceptsInput(server, stack);
        }
        if (slot == SLOT_BATTERY) {
            return PortableGear.canDischarge(stack, engine.inputTier())
                    || stack.is(Items.REDSTONE);
        }
        if (slot >= UPGRADE_START && slot < UPGRADE_START + UPGRADE_COUNT) {
            return Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, usesWater());
        }
        if (slot == SLOT_WASHER_IN) {
            return usesWater()
                    && GiantGasPlatform.isDrainableContainer(
                            stack, Ecc2Ids.MINECRAFT_WATER_ID::equals);
        }
        return false;
    }

    public boolean stillValid(Player player) {
        if (!formed || isRemoved() || getLevel() == null || getLevel().getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5)
                <= 100.0;
    }

    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    private final class CoreAutomationSlots implements ProcessingSlots {

        @Override
        public int[] getSlotsForFace(Direction side) {
            return usesWater() ? AUTOMATED_SLOTS_WASHER : AUTOMATED_SLOTS_STANDARD;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return GiantUnitBlockEntity.this.canPlaceItem(slot, stack);
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            if (stack.isEmpty() || slot >= UPGRADE_START && slot < UPGRADE_START + UPGRADE_COUNT) {
                return false;
            }
            if (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT) {
                return false;
            }
            if (slot == SLOT_WASHER_OUT) {
                return false;
            }
            if (slot == SLOT_BATTERY) {
                return PortableGear.canSupplyCharge(stack, engine.inputTier())
                        || stack.is(Items.REDSTONE);
            }
            return GiantUnitBlockEntity.this.canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            if (stack.isEmpty()) {
                return false;
            }
            if (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT) {
                return true;
            }
            return usesWater() && slot == SLOT_WASHER_OUT;
        }

        @Override
        public int automationLimit(int slot, ItemStack stack) {
            return slot == SLOT_BATTERY
                    ? PortableGear.powerSlotLimit(stack)
                    : stack.getMaxStackSize();
        }

        @Override
        public int getContainerSize() {
            return INVENTORY_SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : items) {
                if (!s.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
            if (!removed.isEmpty()) {
                GiantUnitBlockEntity.this.setChanged();
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            stack.limitSize(getMaxStackSize(stack));
            GiantUnitBlockEntity.this.setChanged();
        }

        @Override
        public void setChanged() {
            GiantUnitBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return GiantUnitBlockEntity.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            GiantUnitBlockEntity.this.clearContent();
        }
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable(machineType.containerTitleKey());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        GiantUnitBlockEntity core = coreEntity();
        return new GiantMachineMenu(containerId, playerInventory, core != null ? core : this);
    }

    // ---- Persistence ----

    @Override
    protected void saveState(Ecc2DataWriter out) {
        out.putBoolean("ecc2_formed", formed);
        out.putString("ecc2_role", role.getSerializedName());
        out.putString("ecc2_facing", structureFacing.getSerializedName());
        out.putBoolean("ecc2_has_core", corePos != null);
        if (corePos != null) {
            out.putInt("ecc2_core_x", corePos.getX());
            out.putInt("ecc2_core_y", corePos.getY());
            out.putInt("ecc2_core_z", corePos.getZ());
        }
        if (role == GiantBlockRole.CORE) {
            out.putItems("ecc2_items", items);
            out.putDouble("ecc2_eu", engine.storedEu());
            out.putInt("ecc2_water", engine.waterMb());
            out.putInt("ecc2_ports", engine.portPermissions().packed());
            out.putIntArray("ecc2_lane_prog", engine.copyLaneProgress());
            out.putIntArray("ecc2_lane_batch", engine.copyLaneBatchSizes());
        }
    }

    @Override
    protected void loadState(Ecc2DataReader in) {
        formed = in.getBoolean("ecc2_formed", false);
        String roleStr = in.getString("ecc2_role", "unformed");
        role = GiantBlockRole.UNFORMED;
        for (GiantBlockRole candidate : GiantBlockRole.values()) {
            if (candidate.getSerializedName().equals(roleStr)) {
                role = candidate;
                break;
            }
        }
        String faceStr = in.getString("ecc2_facing", "north");
        Direction parsed = Direction.byName(faceStr);
        structureFacing =
                parsed != null && parsed.getAxis().isHorizontal() ? parsed : Direction.NORTH;
        if (in.getBoolean("ecc2_has_core", false)) {
            corePos =
                    new BlockPos(
                            in.getInt("ecc2_core_x", worldPosition.getX()),
                            in.getInt("ecc2_core_y", worldPosition.getY()),
                            in.getInt("ecc2_core_z", worldPosition.getZ()));
        } else {
            corePos = null;
        }
        if (role == GiantBlockRole.CORE) {
            in.loadItems("ecc2_items", items);
            engine = new GiantMachineEngine(machineType.effectiveSpec());
            engine.setMaxBatchPerLane(Ecc2Config.giantMaxBatchPerLane());
            engine.setUpgrades(Ecc2UpgradeHelper.readProfile(upgradeStacks()));
            engine.setStoredEu(in.getDouble("ecc2_eu", 0.0));
            engine.setWaterMb(in.getInt("ecc2_water", 0));
            engine.portPermissions().setPacked(in.getInt("ecc2_ports", 0));
            int[] savedProg = in.getIntArray("ecc2_lane_prog", GiantMachineEngine.LANE_COUNT);
            int[] savedBatch = in.getIntArray("ecc2_lane_batch", GiantMachineEngine.LANE_COUNT);
            engine.restoreLanes(savedProg, savedBatch);
            pendingPortVisualSync = true;
        }
    }

    // ---- Public Getters for Menus, Screens, and Compat (JEI/EMI/Jade/TOP) ----

    public GiantMachineType machineType() {
        return machineType;
    }

    public boolean isFormed() {
        return formed;
    }

    public GiantBlockRole role() {
        return role;
    }

    public BlockPos corePos() {
        return corePos;
    }

    public Direction structureFacing() {
        return structureFacing;
    }

    public GiantUnitBlockEntity coreEntity() {
        if (!formed || corePos == null) {
            return null;
        }
        if (role == GiantBlockRole.CORE && corePos.equals(worldPosition)) {
            return this;
        }
        if (getLevel() == null || !getLevel().hasChunkAt(corePos)) {
            return null;
        }
        return getLevel().getBlockEntity(corePos) instanceof GiantUnitBlockEntity be
                        && be.isFormed()
                        && be.role() == GiantBlockRole.CORE
                ? be
                : null;
    }

    public GiantMachineEngine engine() {
        GiantUnitBlockEntity core = coreEntity();
        return core != null ? core.engine : this.engine;
    }

    public GiantPortPermissions portPermissions() {
        return engine().portPermissions();
    }

    public boolean usesWater() {
        return machineType.spec().usesWater();
    }

    public double storedEu() {
        return engine().storedEu();
    }

    public double energyCapacity() {
        return engine().energyCapacity();
    }

    public int inputTier() {
        return engine().inputTier();
    }

    public int activeTotalBatch() {
        return engine().lastActiveTotalBatch();
    }

    public double lastTickEuConsumed() {
        return engine().lastTickEuConsumed();
    }

    public int waterMb() {
        return engine().waterMb();
    }

    public int waterCapacityMb() {
        return engine().waterTankCapacityMb();
    }

    public int laneProgress(int lane) {
        return engine().laneProgress(lane);
    }

    public int laneBatchSize(int lane) {
        return engine().laneBatchSize(lane);
    }

    public int operationTicks() {
        return engine().operationTicks();
    }

    public boolean isMachineActive() {
        return engine().isActive();
    }

    public List<FluidContents> fluidContents() {
        GiantUnitBlockEntity core = coreEntity();
        if (core == null || !core.usesWater()) {
            return List.of();
        }
        int amt = core.engine.waterMb();
        int cap = core.engine.waterTankCapacityMb();
        return List.of(
                amt > 0
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(Ecc2Ids.MINECRAFT_WATER_ID),
                                amt,
                                cap)
                        : FluidContents.empty(cap));
    }

    private List<ItemStack> upgradeStacks() {
        return items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT);
    }
}
