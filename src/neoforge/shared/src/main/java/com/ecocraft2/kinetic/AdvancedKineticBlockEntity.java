package com.ecocraft2.kinetic;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2KineticSource;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.common.kinetic.RotorSpec;
import com.ecocraft2.common.kinetic.RotorWearAccumulator;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.solar.Ecc2SaveBlockEntity;
import com.ecocraft2.solar.Ecc2TagIO;
import com.ecocraft2.solar.SolarKineticModule;
import com.ecocraft2.solar.SolarPlatform;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity backing all six Advanced Wind &amp; Water Kinetic Generators (Durable, Overclocked,
 * Precision).
 *
 * <p>Implements {@link Ecc2KineticSource} so IC2NE's {@code ic2:kinetic_generator} draws scaled KU
 * natively, applies fractional rotor wear via {@link RotorWearAccumulator} &amp;
 * {@link Ecc2RotorHooks}, and syncs rotor type &amp; speed to the 3D spinning rotor BER.
 */
public final class AdvancedKineticBlockEntity extends Ecc2SaveBlockEntity
        implements Ecc2KineticSource, MachineAccessTarget, WorldlyContainer, MenuProvider {

    public static final int SLOT_ROTOR = 0;
    private static final int[] SLOTS_ROTOR = {SLOT_ROTOR};
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int WATER_RECHECK_INTERVAL = 40;

    private final KineticMachineTier kineticTier;
    private final boolean waterType;
    private final RotorWearAccumulator wearAccumulator = new RotorWearAccumulator();
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private AdvancedKineticPhysics.Status status = AdvancedKineticPhysics.Status.NO_ROTOR;
    private int currentKu;
    private int environmentReading;
    private boolean overWind;
    private boolean naturalWaterConnected;
    private long lastWaterCheckTick = Long.MIN_VALUE;
    private long lastDrawnTick = Long.MIN_VALUE;

    // Client-synced visual fields for the 3D rotor BER
    private int visualRotorOrdinal;
    private int visualSpeedKu;

    public AdvancedKineticBlockEntity(BlockPos pos, BlockState state) {
        this(
                pos,
                state,
                state.getBlock() instanceof AdvancedKineticBlock block
                        ? block.getKineticTier()
                        : KineticMachineTier.DURABLE,
                state.getBlock() instanceof AdvancedKineticBlock block && block.isWaterType());
    }

    public AdvancedKineticBlockEntity(
            BlockPos pos, BlockState state, KineticMachineTier kineticTier, boolean waterType) {
        super(SolarKineticModule.KINETIC_GENERATOR_BE.get(), pos, state);
        this.kineticTier = kineticTier;
        this.waterType = waterType;
    }

    /** Executes one server tick: evaluates wind/water KU, applies rotor wear, syncs visual state. */
    public void serverTick(ServerLevel level) {
        ItemStack rotorStack = items.get(SLOT_ROTOR);
        RotorSpec rotor = resolveInstalledRotor(rotorStack);
        Direction rotorFace = getRotorFace();

        if (waterType) {
            if (rotor == null || !rotor.waterAllowed()) {
                naturalWaterConnected = false;
                lastWaterCheckTick = Long.MIN_VALUE;
            } else if (lastWaterCheckTick == Long.MIN_VALUE
                    || level.getGameTime() - lastWaterCheckTick >= WATER_RECHECK_INTERVAL) {
                lastWaterCheckTick = level.getGameTime();
                naturalWaterConnected =
                        AdvancedKineticPhysics.checkNaturalWaterConnectivity(
                                level, worldPosition, rotorFace);
            }
        }

        AdvancedKineticPhysics.Sample sample =
                waterType
                        ? AdvancedKineticPhysics.sampleWater(
                                level, worldPosition, rotorFace, rotor, naturalWaterConnected)
                        : AdvancedKineticPhysics.sampleWind(level, worldPosition, rotorFace, rotor);

        status = sample.status();
        environmentReading = sample.environmentReading();
        overWind = sample.overWind();

        double scaledExact = sample.baseKu() * configuredOutputMultiplier();
        currentKu = scaledExact > 0.0 ? Math.max(1, (int) Math.round(scaledExact)) : 0;

        boolean spinning = rotor != null && currentKu > 0;
        if (spinning) {
            int wearDamage =
                    wearAccumulator.recordTick(true, overWind, configuredWearMultiplier());
            if (wearDamage > 0) {
                boolean broke = Ecc2RotorHooks.applyRotorDamage(this, SLOT_ROTOR, wearDamage);
                if (broke) {
                    currentKu = 0;
                    status = AdvancedKineticPhysics.Status.NO_ROTOR;
                    overWind = false;
                    rotor = null;
                    spinning = false;
                }
            }
        }

        // Update blockstate ACTIVE flag
        BlockState state = getBlockState();
        if (state.hasProperty(AdvancedKineticBlock.ACTIVE)
                && state.getValue(AdvancedKineticBlock.ACTIVE) != spinning) {
            level.setBlock(
                    worldPosition,
                    state.setValue(AdvancedKineticBlock.ACTIVE, spinning),
                    Block.UPDATE_ALL);
        }

        // Sync rotor material & speed to clients when changed
        int nextVisualOrd = rotor == null ? 0 : (rotor.ordinal() + 1);
        int nextVisualSpeed = spinning ? currentKu : 0;
        if (nextVisualOrd != visualRotorOrdinal
                || Math.abs(nextVisualSpeed - visualSpeedKu) >= 4
                || (nextVisualSpeed == 0 && visualSpeedKu != 0)
                || (nextVisualSpeed > 0 && visualSpeedKu == 0)) {
            visualRotorOrdinal = nextVisualOrd;
            visualSpeedKu = nextVisualSpeed;
            BlockState current = getBlockState();
            level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
        }
    }

    /** Delivers KU to an adjacent {@code ic2:kinetic_generator} via {@link Ecc2KineticSource}. */
    @Override
    public int ecc2DrawKinetic(ServerLevel level, Direction fromSide, int maxRequestedKu) {
        if (isRemoved() || maxRequestedKu <= 0 || currentKu <= 0) {
            return 0;
        }
        if (fromSide != getOutputFace()) {
            return 0;
        }
        long now = level.getGameTime();
        if (lastDrawnTick == now) {
            return 0;
        }
        lastDrawnTick = now;
        return Math.min(currentKu, maxRequestedKu);
    }

    public double configuredOutputMultiplier() {
        return switch (kineticTier) {
            case DURABLE -> Ecc2Config.durableKineticOutputMultiplier();
            case OVERCLOCKED -> Ecc2Config.overclockedKineticOutputMultiplier();
            case PRECISION -> Ecc2Config.precisionKineticOutputMultiplier();
            case STANDARD -> kineticTier.outputMultiplier();
        };
    }

    public double configuredWearMultiplier() {
        return switch (kineticTier) {
            case DURABLE -> Ecc2Config.durableKineticWearMultiplier();
            case OVERCLOCKED -> Ecc2Config.overclockedKineticWearMultiplier();
            case PRECISION -> Ecc2Config.precisionKineticWearMultiplier();
            case STANDARD -> kineticTier.wearMultiplier();
        };
    }

    public boolean acceptsRotor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return waterType ? Ecc2RotorHooks.isWaterRotor(stack) : Ecc2RotorHooks.isRotor(stack);
    }

    public RotorSpec resolveInstalledRotor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return RotorSpec.byItemId(Ecc2BridgePlatform.itemId(stack)).orElse(null);
    }

    public Direction getRotorFace() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AdvancedKineticBlock block) {
            return block.getRotorFace(state);
        }
        return Direction.NORTH;
    }

    public Direction getOutputFace() {
        return getRotorFace().getOpposite();
    }

    // ---- Public getters for Menu, Renderer, Jade, and TOP ----

    public KineticMachineTier getKineticTier() {
        return kineticTier;
    }

    public boolean isWaterType() {
        return waterType;
    }

    public int getCurrentKu() {
        return currentKu;
    }

    public int getEnvironmentReading() {
        return environmentReading;
    }

    public AdvancedKineticPhysics.Status getStatus() {
        return status;
    }

    public boolean isOverWind() {
        return overWind;
    }

    public ItemStack getRotorStack() {
        return items.get(SLOT_ROTOR);
    }

    public int getRotorRemainingDurability() {
        ItemStack rotor = items.get(SLOT_ROTOR);
        int max = Ecc2RotorHooks.maxDurability(rotor);
        return max <= 0 ? 0 : Math.max(0, max - rotor.getDamageValue());
    }

    public int getRotorMaxDurability() {
        return Ecc2RotorHooks.maxDurability(items.get(SLOT_ROTOR));
    }

    public double getRotorRemainingPercent() {
        int max = getRotorMaxDurability();
        return max <= 0 ? 0.0 : (getRotorRemainingDurability() * 100.0) / max;
    }

    public double getRotorRemainingHours() {
        double wearMult = configuredWearMultiplier();
        if (wearMult <= 0.0) {
            return 0.0;
        }
        return (getRotorRemainingDurability() / wearMult) / 3600.0;
    }

    /** Returns client-synced rotor spec for the 3D rotor BER, or {@code null} when empty. */
    public RotorSpec getVisualRotorSpec() {
        if (visualRotorOrdinal <= 0) {
            return null;
        }
        var values = RotorSpec.values();
        int idx = visualRotorOrdinal - 1;
        return idx >= 0 && idx < values.length ? values[idx] : null;
    }

    /** Returns client-synced KU output driving rotor spin speed. */
    public int getVisualSpeedKu() {
        return visualSpeedKu;
    }

    // ---- Persistence & Client Sync ----

    @Override
    protected boolean hasClientSync() {
        return true;
    }

    @Override
    protected void writeSyncData(CompoundTag tag) {
        tag.putInt("rotor_ord", visualRotorOrdinal);
        tag.putInt("rotor_speed", visualSpeedKu);
    }

    @Override
    protected void readSyncData(Ecc2TagIO.Reader in) {
        visualRotorOrdinal = in.getInt("rotor_ord", 0);
        visualSpeedKu = in.getInt("rotor_speed", 0);
    }

    @Override
    protected void saveEcc2(Ecc2TagIO.Writer out) {
        out.putItems("items", items);
        out.putInt("wear_ticks", wearAccumulator.activeTickCounter());
        out.putDouble("wear_frac", wearAccumulator.fractionalWear());
    }

    @Override
    protected void loadEcc2(Ecc2TagIO.Reader in) {
        in.loadItems("items", items);
        wearAccumulator.restore(in.getInt("wear_ticks", 0), in.getDouble("wear_frac", 0.0));
        RotorSpec rotor = resolveInstalledRotor(items.get(SLOT_ROTOR));
        visualRotorOrdinal = rotor == null ? 0 : rotor.ordinal() + 1;
    }

    @Override
    protected Container dropInventory() {
        return this;
    }

    // ---- WorldlyContainer ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return MachineAccess.connects(this, side) ? SLOTS_ROTOR : EMPTY_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        return MachineAccess.allowsInput(this, direction)
                && index == SLOT_ROTOR
                && acceptsRotor(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return MachineAccess.allowsOutput(this, direction) && index == SLOT_ROTOR;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_ROTOR).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == SLOT_ROTOR ? items.get(SLOT_ROTOR) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != SLOT_ROTOR) {
            return;
        }
        items.set(SLOT_ROTOR, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
        if (level != null && !SolarPlatform.isClient(level)) {
            RotorSpec rotor = resolveInstalledRotor(stack);
            visualRotorOrdinal = rotor == null ? 0 : rotor.ordinal() + 1;
            if (rotor == null) {
                visualSpeedKu = 0;
            }
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_ROTOR && acceptsRotor(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.set(SLOT_ROTOR, ItemStack.EMPTY);
        setChanged();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        String tierPrefix =
                switch (kineticTier) {
                    case DURABLE -> "durable";
                    case OVERCLOCKED -> "overclocked";
                    case PRECISION -> "precision";
                    default -> "durable";
                };
        String kind = waterType ? "water" : "wind";
        return Component.translatable(
                "container.ecera." + tierPrefix + "_" + kind + "_kinetic_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new AdvancedKineticMenu(containerId, playerInv, this);
    }
}
