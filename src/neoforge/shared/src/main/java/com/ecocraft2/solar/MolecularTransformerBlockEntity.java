package com.ecocraft2.solar;

import com.ecocraft2.common.solar.MolecularTransformerEngine;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.platform.CapabilityFacade.Face;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Block entity for {@code ecc2:molecular_transformer}, backed by {@link MolecularTransformerEngine}
 * (Tier 14 unlimited sink, exact {@link #receive(double)}, zero item loss on full output, capped
 * idle grace ticks, light level 12 while active).
 */
public final class MolecularTransformerBlockEntity extends Ecc2SaveBlockEntity
        implements WorldPowerPort, EuBuffer, MachineAccessTarget, WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_COUNT = 2;

    private static final int[] SLOTS_INPUT = {SLOT_INPUT};
    private static final int[] SLOTS_OUTPUT = {SLOT_OUTPUT};
    private static final int[] SLOTS_ALL = {SLOT_INPUT, SLOT_OUTPUT};
    private static final Set<Face> ALL_FACES = Set.of(Face.values());
    private static final EnergyGraph.Ports SINK_PORTS =
            new EnergyGraph.Ports(ALL_FACES, Set.of(), false);

    private final MolecularTransformerEngine engine = new MolecularTransformerEngine();
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private long lastTickedGameTime = Long.MIN_VALUE;
    private boolean clientActive;

    public MolecularTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(SolarKineticModule.MOLECULAR_TRANSFORMER_BE.get(), pos, state);
    }

    /** Executes one server tick against live {@code ecc2:molecular_transformer} recipes. */
    public void serverTick(ServerLevel level) {
        lastTickedGameTime = level.getGameTime();
        boolean wasActive = engine.isActive();
        double prevUsed = engine.energyUsed();

        ItemStack input = items.get(SLOT_INPUT);
        var match =
                input.isEmpty()
                        ? null
                        : SolarPlatform.findMolecularTransformerRecipe(level, input);

        String recipeKey = null;
        double recipeEnergy = 0.0;
        ItemStack resolvedOut = ItemStack.EMPTY;
        boolean canAcceptOut = false;

        if (match != null) {
            resolvedOut = match.recipe().resolveOutput();
            if (!resolvedOut.isEmpty() && match.recipe().energy() > 0L) {
                recipeKey = match.id();
                recipeEnergy = match.recipe().energy();
                canAcceptOut = canAcceptOutput(resolvedOut);
            }
        }
        pendingOutput = resolvedOut;

        var result = engine.tick(recipeKey, recipeEnergy, canAcceptOut);
        if (result == MolecularTransformerEngine.TickResult.COMPLETED
                && match != null
                && !resolvedOut.isEmpty()) {
            input.shrink(match.recipe().inputCount());
            if (input.isEmpty()) {
                items.set(SLOT_INPUT, ItemStack.EMPTY);
            }
            insertOutput(resolvedOut);
            setChanged();
        } else if (engine.energyUsed() != prevUsed) {
            setChanged();
        }

        boolean nowActive = engine.isActive();
        BlockState state = getBlockState();
        if (state.hasProperty(MolecularTransformerBlock.ACTIVE)
                && state.getValue(MolecularTransformerBlock.ACTIVE) != nowActive) {
            level.setBlock(
                    worldPosition,
                    state.setValue(MolecularTransformerBlock.ACTIVE, nowActive),
                    Block.UPDATE_ALL);
            level.sendBlockUpdated(worldPosition, state, getBlockState(), Block.UPDATE_CLIENTS);
        } else if (wasActive != nowActive) {
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack current = items.get(SLOT_OUTPUT);
        if (current.isEmpty()) {
            return result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
        }
        if (!ItemStack.isSameItemSameComponents(current, result)) {
            return false;
        }
        int maxStack = Math.min(getMaxStackSize(), current.getMaxStackSize());
        return current.getCount() + result.getCount() <= maxStack;
    }

    private void insertOutput(ItemStack result) {
        ItemStack current = items.get(SLOT_OUTPUT);
        if (current.isEmpty()) {
            items.set(SLOT_OUTPUT, result.copy());
        } else {
            current.grow(result.getCount());
        }
    }

    // ---- Public getters for Menu, Renderer, Jade, and TOP ----

    public MolecularTransformerEngine getEngine() {
        return engine;
    }

    public boolean isWorking() {
        return engine.isWorking();
    }

    public boolean isActive() {
        if (level != null && SolarPlatform.isClient(level)) {
            BlockState state = getBlockState();
            return clientActive
                    || (state.hasProperty(MolecularTransformerBlock.ACTIVE)
                            && state.getValue(MolecularTransformerBlock.ACTIVE));
        }
        return engine.isActive();
    }

    public boolean isOutputBlocked() {
        return engine.isOutputBlocked();
    }

    public double getEnergyUsed() {
        return engine.energyUsed();
    }

    public double getTotalEu() {
        return engine.totalEu();
    }

    public double getLastTickPower() {
        return engine.lastTickPower();
    }

    public double getProgress() {
        return engine.progress();
    }

    public int getProgressPercent() {
        return engine.progressPercent();
    }

    public ItemStack getInputStack() {
        return items.get(SLOT_INPUT);
    }

    public ItemStack getOutputStack() {
        return items.get(SLOT_OUTPUT);
    }

    public ItemStack getPendingOutput() {
        return pendingOutput;
    }

    public int getPendingOutputItemId() {
        return pendingOutput.isEmpty() ? -1 : BuiltInRegistries.ITEM.getId(pendingOutput.getItem());
    }

    public int getPendingOutputCount() {
        return pendingOutput.isEmpty() ? 0 : pendingOutput.getCount();
    }

    // ---- WorldPowerPort & EuBuffer ----

    @Override
    public void onLoad() {
        super.onLoad();
        WorldEnergy.connect(this);
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
        return lastTickedGameTime == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return SINK_PORTS;
    }

    @Override
    public boolean source() {
        return false;
    }

    @Override
    public double packetLimit() {
        // Tier 14 unlimited sink — never explodes from over-voltage.
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double receive(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double remainder = engine.inject(amount);
        double accepted = Math.max(0.0, Math.min(amount, amount - remainder));
        if (accepted > 0.0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.energyUsed(), Math.max(1.0, engine.totalEu()));
    }

    // ---- Persistence & Client Sync ----

    @Override
    protected boolean hasClientSync() {
        return true;
    }

    @Override
    protected void writeSyncData(CompoundTag tag) {
        tag.putBoolean("active", engine.isActive());
    }

    @Override
    protected void readSyncData(Ecc2TagIO.Reader in) {
        this.clientActive = in.getBoolean("active", false);
    }

    @Override
    protected void saveEcc2(Ecc2TagIO.Writer out) {
        out.putItems("items", items);
        out.putBoolean("working", engine.isWorking());
        out.putString(
                "recipe_key",
                engine.activeRecipeKey() == null ? "" : engine.activeRecipeKey());
        out.putDouble("total_eu", engine.totalEu());
        out.putDouble("energy_used", engine.energyUsed());
        out.putDouble("energy_in", engine.energyIn());
        out.putInt("idle_ticks", engine.idleTicks());
    }

    @Override
    protected void loadEcc2(Ecc2TagIO.Reader in) {
        in.loadItems("items", items);
        boolean working = in.getBoolean("working", false);
        String key = in.getString("recipe_key", "");
        double total = in.getDouble("total_eu", 0.0);
        double used = in.getDouble("energy_used", 0.0);
        double buffered = in.getDouble("energy_in", 0.0);
        int idle = in.getInt("idle_ticks", MolecularTransformerEngine.MAX_IDLE_TICKS);
        engine.restoreState(working, key.isEmpty() ? null : key, total, used, buffered, idle);
        this.clientActive = engine.isActive();
    }

    @Override
    protected Container dropInventory() {
        return this;
    }

    // ---- WorldlyContainer ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (side == Direction.DOWN) {
            return SLOTS_OUTPUT;
        }
        if (side == Direction.UP) {
            return SLOTS_INPUT;
        }
        return SLOTS_ALL;
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_INPUT && !stack.isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_OUTPUT;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_INPUT).isEmpty() && items.get(SLOT_OUTPUT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.set(SLOT_INPUT, ItemStack.EMPTY);
        items.set(SLOT_OUTPUT, ItemStack.EMPTY);
        setChanged();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.molecular_transformer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new MolecularTransformerMenu(containerId, playerInv, this);
    }
}
