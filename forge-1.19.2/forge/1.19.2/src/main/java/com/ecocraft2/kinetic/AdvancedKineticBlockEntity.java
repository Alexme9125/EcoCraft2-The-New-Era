package com.ecocraft2.kinetic;

import com.ecocraft2.bridge.Ecc2KineticSource;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.ecocraft2.common.kinetic.KineticCalculation;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.common.kinetic.RotorSpec;
import com.ecocraft2.common.kinetic.RotorWearAccumulator;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.solar.SolarKineticRegistry;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the 6 Advanced Wind & Water Kinetic Generators.
 *
 * <p>Implements {@link Ecc2KineticSource} so IC2NE's {@code ic2:kinetic_generator} draws KU
 * natively out of the shaft face ({@code facing.getOpposite()}), while the 3D spinning rotor
 * renders on the front face ({@code facing}).
 */
public final class AdvancedKineticBlockEntity extends BlockEntity
        implements Ecc2KineticSource, MenuProvider {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_ROTOR = 1;
    public static final int STATUS_OBSTRUCTED = 2;
    public static final int STATUS_NO_WATER = 3;
    public static final int STATUS_OVERWIND = 4;

    private static final int MAX_WATER_BFS_NODES = 512;
    private static final int MIN_NATURAL_WATER_BLOCKS = 24;

    private final boolean water;
    private final KineticMachineTier tier;
    private final RotorWearAccumulator wearAccumulator = new RotorWearAccumulator();

    private int currentKu;
    private int currentWindMcw;
    private int statusCode = STATUS_NO_ROTOR;
    private boolean overWind;
    private boolean naturalWaterConnected;
    private long lastWaterCheckTick = Long.MIN_VALUE;
    private long drawnTick = Long.MIN_VALUE;

    // Client-synced 3D rotor render fields (0=none, 1=wood, 2=iron, 3=bronze, 4=steel, 5=carbon)
    private int visualRotorType;
    private int visualRotorSpeed;

    private final ItemStackHandler rotorSlot =
            new ItemStackHandler(1) {
                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return water
                            ? Ecc2RotorHooks.isWaterRotor(stack)
                            : Ecc2RotorHooks.isRotor(stack);
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                    syncVisuals();
                }
            };

    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> rotorSlot);

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    ItemStack rotor = rotorSlot.getStackInSlot(0);
                    int max = Ecc2RotorHooks.maxDurability(rotor);
                    int rem = max > 0 ? Math.max(0, max - rotor.getDamageValue()) : 0;
                    int pctTenths = max > 0 ? (int) Math.round((rem * 1000.0) / max) : 0;
                    int remHoursTenths = (int) Math.round((rem / 3600.0) * 10.0);
                    return switch (index) {
                        case 0 -> currentKu;
                        case 1 -> currentWindMcw;
                        case 2 -> statusCode;
                        case 3 -> pctTenths;
                        case 4 -> remHoursTenths;
                        case 5 -> water ? 1 : 0;
                        case 6 -> tier.ordinal();
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 7;
                }
            };

    public AdvancedKineticBlockEntity(BlockPos pos, BlockState state) {
        this(
                pos,
                state,
                state.getBlock() instanceof AdvancedKineticBlock block && block.isWater(),
                state.getBlock() instanceof AdvancedKineticBlock block
                        ? block.tier()
                        : KineticMachineTier.DURABLE);
    }

    public AdvancedKineticBlockEntity(
            BlockPos pos, BlockState state, boolean water, KineticMachineTier tier) {
        super(SolarKineticRegistry.ADVANCED_KINETIC_BE.get(), pos, state);
        this.water = water;
        this.tier = tier;
    }

    public boolean isWater() {
        return water;
    }

    public KineticMachineTier tier() {
        return tier;
    }

    public Direction rotorFace() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AdvancedKineticBlock block) {
            return state.getValue(block.facingProperty());
        }
        return Direction.NORTH;
    }

    public Direction shaftFacing() {
        return rotorFace().getOpposite();
    }

    public int visualRotorType() {
        return visualRotorType;
    }

    public int visualRotorSpeed() {
        return visualRotorSpeed;
    }

    public int currentKu() {
        return currentKu;
    }

    public int currentWindMcw() {
        return currentWindMcw;
    }

    public int statusCode() {
        return statusCode;
    }

    public ItemStackHandler rotorSlot() {
        return rotorSlot;
    }

    public Container dropInventory() {
        SimpleContainer drops = new SimpleContainer(1);
        drops.setItem(0, rotorSlot.getStackInSlot(0));
        return drops;
    }

    private double configuredOutputMultiplier() {
        return switch (tier) {
            case DURABLE -> Ecc2Config.durableKineticOutputMultiplier();
            case OVERCLOCKED -> Ecc2Config.overclockedKineticOutputMultiplier();
            case PRECISION -> Ecc2Config.precisionKineticOutputMultiplier();
            default -> tier.outputMultiplier();
        };
    }

    private double configuredWearMultiplier() {
        return switch (tier) {
            case DURABLE -> Ecc2Config.durableKineticWearMultiplier();
            case OVERCLOCKED -> Ecc2Config.overclockedKineticWearMultiplier();
            case PRECISION -> Ecc2Config.precisionKineticWearMultiplier();
            default -> tier.wearMultiplier();
        };
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack rotor = rotorSlot.getStackInSlot(0);
        RotorSpec spec = Ecc2RotorHooks.lookup(rotor);
        if (spec == null || (water && !spec.waterAllowed())) {
            currentKu = 0;
            currentWindMcw = 0;
            overWind = false;
            statusCode = STATUS_NO_ROTOR;
            updateVisualAndBlockState(serverLevel, 0, 0, false);
            return;
        }

        int baseKu = water ? sampleWaterKu(serverLevel, spec) : sampleWindKu(serverLevel, spec);
        currentKu =
                baseKu > 0
                        ? Math.max(1, (int) Math.round(baseKu * configuredOutputMultiplier()))
                        : 0;

        boolean spinning = currentKu > 0;
        if (spinning) {
            int damage =
                    wearAccumulator.recordTick(true, overWind, configuredWearMultiplier());
            if (damage > 0) {
                SimpleContainer wrapper = new SimpleContainer(1);
                wrapper.setItem(0, rotor);
                boolean broke = Ecc2RotorHooks.applyRotorDamage(wrapper, 0, damage);
                rotorSlot.setStackInSlot(0, broke ? ItemStack.EMPTY : wrapper.getItem(0));
                setChanged();
            }
        }

        int rType = rotorVisualId(rotorSlot.getStackInSlot(0));
        int rSpeed = spinning ? Math.min(200, Math.max(1, currentKu)) : 0;
        updateVisualAndBlockState(serverLevel, rType, rSpeed, spinning);
    }

    private int sampleWindKu(ServerLevel level, RotorSpec spec) {
        Direction rotorDir = rotorFace();
        if (!rotorDir.getAxis().isHorizontal()) {
            currentWindMcw = 0;
            overWind = false;
            statusCode = STATUS_OBSTRUCTED;
            return 0;
        }
        int diameter = spec.windDiameter();
        if (!isRotorSweepClear(level, rotorDir, diameter, false)) {
            currentWindMcw = 0;
            overWind = false;
            statusCode = STATUS_OBSTRUCTED;
            return 0;
        }
        int wind = Ecc2RotorHooks.computeWindStrength(level, worldPosition, shaftFacing());
        if (level.isThundering()) {
            wind = (int) Math.round(wind * 1.5);
        } else if (level.isRaining()) {
            wind = (int) Math.round(wind * 1.2);
        }
        currentWindMcw = wind;
        overWind = KineticCalculation.isOverWind(wind, spec);
        statusCode = overWind ? STATUS_OVERWIND : STATUS_OK;

        double ku = (wind * (double) spec.rating()) / 100.0;
        return Math.max(0, (int) Math.round(ku));
    }

    private int sampleWaterKu(ServerLevel level, RotorSpec spec) {
        Direction rotorDir = rotorFace();
        int diameter = spec.waterDiameter();
        if (!isRotorSweepClear(level, rotorDir, diameter, true)) {
            currentWindMcw = 0;
            overWind = false;
            statusCode = STATUS_OBSTRUCTED;
            return 0;
        }
        long now = level.getGameTime();
        if (now - lastWaterCheckTick >= 40L || lastWaterCheckTick == Long.MIN_VALUE) {
            lastWaterCheckTick = now;
            naturalWaterConnected = checkNaturalWaterConnectivity(level, rotorDir);
        }
        if (!naturalWaterConnected) {
            currentWindMcw = 0;
            overWind = false;
            statusCode = STATUS_NO_WATER;
            return 0;
        }
        BlockPos hub = worldPosition.relative(rotorDir);
        Holder<Biome> biome = level.getBiome(hub);
        int biomeBase =
                biome.is(BiomeTags.IS_DEEP_OCEAN)
                        ? 100
                        : (biome.is(BiomeTags.IS_OCEAN) ? 90 : 80);
        long phase = now / 200L;
        int flow =
                biomeBase + 10 * Math.floorMod(phase + worldPosition.getX() + worldPosition.getZ(), 3);
        currentWindMcw = flow;
        overWind = false;
        statusCode = STATUS_OK;
        return Math.max(1, (int) Math.round((flow * (double) spec.rating()) / 100.0));
    }

    private boolean isRotorSweepClear(
            ServerLevel level, Direction rotorDir, int diameter, boolean requireWater) {
        BlockPos center = worldPosition.relative(rotorDir);
        int radius = Math.max(1, diameter / 2);
        Direction.Axis axis = rotorDir.getAxis();
        int totalCells = 0;
        int waterCells = 0;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a * a + b * b > radius * radius + 1) {
                    continue;
                }
                BlockPos check =
                        switch (axis) {
                            case X -> center.offset(0, a, b);
                            case Y -> center.offset(a, 0, b);
                            case Z -> center.offset(a, b, 0);
                        };
                if (!level.hasChunkAt(check)) {
                    return false;
                }
                BlockState st = level.getBlockState(check);
                if (!st.getCollisionShape(level, check).isEmpty()) {
                    return false;
                }
                totalCells++;
                if (requireWater && st.getFluidState().is(FluidTags.WATER)) {
                    waterCells++;
                }
            }
        }
        if (requireWater) {
            return totalCells > 0
                    && level.hasChunkAt(center)
                    && level.getFluidState(center).is(FluidTags.WATER)
                    && waterCells * 2 >= totalCells;
        }
        return totalCells > 0;
    }

    private boolean checkNaturalWaterConnectivity(ServerLevel level, Direction rotorDir) {
        BlockPos hub = worldPosition.relative(rotorDir);
        if (!level.hasChunkAt(hub) || !level.getFluidState(hub).is(FluidTags.WATER)) {
            return false;
        }
        var queue = new ArrayDeque<BlockPos>();
        var visited = new HashSet<Long>();
        queue.add(hub);
        visited.add(hub.asLong());

        int naturalWaterCount = 0;
        while (!queue.isEmpty() && visited.size() < MAX_WATER_BFS_NODES) {
            BlockPos cur = queue.poll();
            Holder<Biome> biome = level.getBiome(cur);
            if (biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_OCEAN)
                    || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
                naturalWaterCount++;
                if (naturalWaterCount >= MIN_NATURAL_WATER_BLOCKS) {
                    return true;
                }
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (Math.abs(next.getY() - hub.getY()) > 6) {
                    continue;
                }
                if (visited.add(next.asLong())
                        && level.hasChunkAt(next)
                        && level.getFluidState(next).is(FluidTags.WATER)) {
                    queue.add(next);
                }
            }
        }
        return naturalWaterCount >= MIN_NATURAL_WATER_BLOCKS;
    }

    private static int rotorVisualId(ItemStack stack) {
        RotorSpec spec = Ecc2RotorHooks.lookup(stack);
        if (spec == null) {
            return 0;
        }
        return switch (spec) {
            case WOOD -> 1;
            case IRON -> 2;
            case BRONZE -> 3;
            case STEEL -> 4;
            case CARBON -> 5;
        };
    }

    private void updateVisualAndBlockState(
            ServerLevel serverLevel, int rType, int rSpeed, boolean active) {
        boolean visualChanged = rType != visualRotorType || rSpeed != visualRotorSpeed;
        visualRotorType = rType;
        visualRotorSpeed = rSpeed;
        BlockState state = getBlockState();
        if (state.hasProperty(AdvancedKineticBlock.ACTIVE)
                && state.getValue(AdvancedKineticBlock.ACTIVE) != active) {
            serverLevel.setBlock(
                    worldPosition,
                    state.setValue(AdvancedKineticBlock.ACTIVE, active),
                    Block.UPDATE_CLIENTS);
        }
        if (visualChanged) {
            syncVisuals();
        }
    }

    private void syncVisuals() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- Ecc2KineticSource ----

    @Override
    public int ecc2DrawKinetic(ServerLevel level, Direction fromSide, int maxRequestedKu) {
        if (isRemoved() || maxRequestedKu <= 0 || fromSide != shaftFacing()) {
            return 0;
        }
        long now = level.getGameTime();
        if (drawnTick == now) {
            return 0;
        }
        drawnTick = now;
        return Math.min(maxRequestedKu, currentKu);
    }

    // ---- Capabilities, NBT, Client Sync & MenuProvider ----

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> rotorSlot);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Rotor", rotorSlot.serializeNBT());
        tag.putInt("WearTicks", wearAccumulator.activeTickCounter());
        tag.putDouble("FractionalWear", wearAccumulator.fractionalWear());
        tag.putInt("VisualRotorType", visualRotorType);
        tag.putInt("VisualRotorSpeed", visualRotorSpeed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Rotor")) {
            rotorSlot.deserializeNBT(tag.getCompound("Rotor"));
        }
        wearAccumulator.restore(tag.getInt("WearTicks"), tag.getDouble("FractionalWear"));
        visualRotorType = tag.getInt("VisualRotorType");
        visualRotorSpeed = tag.getInt("VisualRotorSpeed");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("VisualRotorType", visualRotorType);
        tag.putInt("VisualRotorSpeed", visualRotorSpeed);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(6.0);
    }

    @Override
    public Component getDisplayName() {
        String prefix =
                switch (tier) {
                    case DURABLE -> "durable";
                    case OVERCLOCKED -> "overclocked";
                    case PRECISION -> "precision";
                    default -> "durable";
                };
        return Component.translatable(
                "container.ecc2." + prefix + "_" + (water ? "water" : "wind") + "_kinetic_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new AdvancedKineticMenu(containerId, playerInv, this, syncData);
    }
}
