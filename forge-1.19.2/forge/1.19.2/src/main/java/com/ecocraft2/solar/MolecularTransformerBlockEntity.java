package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.solar.MolecularTransformerEngine;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.MachinePortCache;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the Molecular Transformer (unlimited Tier 14 input, exact EU acceptance &
 * remainder return, zero item loss on full output, non-overflowing idle counter, 3D plasma BER).
 */
public final class MolecularTransformerBlockEntity extends BlockEntity
        implements WorldPowerPort, EuBuffer, MachineAccessTarget, MenuProvider {

    private final MolecularTransformerEngine engine = new MolecularTransformerEngine();
    private final MachinePortCache portCache = new MachinePortCache(this);
    private long lastTick = Long.MIN_VALUE;
    private boolean clientActive;

    private final ItemStackHandler inventory =
            new ItemStackHandler(2) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return slot == 0;
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }
            };

    private final IItemHandler sidedHandler =
            new IItemHandler() {
                @Override
                public int getSlots() {
                    return 2;
                }

                @Override
                public @NotNull ItemStack getStackInSlot(int slot) {
                    return inventory.getStackInSlot(slot);
                }

                @Override
                public @NotNull ItemStack insertItem(
                        int slot, @NotNull ItemStack stack, boolean simulate) {
                    if (slot != 0) {
                        return stack;
                    }
                    return inventory.insertItem(0, stack, simulate);
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (slot != 1) {
                        return ItemStack.EMPTY;
                    }
                    return inventory.extractItem(1, amount, simulate);
                }

                @Override
                public int getSlotLimit(int slot) {
                    return inventory.getSlotLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return slot == 0;
                }
            };

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    int total = (int) Math.min(Integer.MAX_VALUE, Math.round(engine.totalEu()));
                    int used = (int) Math.min(Integer.MAX_VALUE, Math.round(engine.energyUsed()));
                    int power =
                            (int) Math.min(Integer.MAX_VALUE, Math.round(engine.lastTickPower()));
                    int flags =
                            (engine.isWorking() ? 1 : 0)
                                    | (engine.isOutputBlocked() ? 2 : 0)
                                    | (engine.isActive() ? 4 : 0);
                    return switch (index) {
                        case 0 -> total & 0xFFFF;
                        case 1 -> (total >>> 16) & 0xFFFF;
                        case 2 -> used & 0xFFFF;
                        case 3 -> (used >>> 16) & 0xFFFF;
                        case 4 -> power & 0xFFFF;
                        case 5 -> (power >>> 16) & 0xFFFF;
                        case 6 -> engine.progressPercent();
                        case 7 -> flags;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 8;
                }
            };

    public MolecularTransformerBlockEntity(BlockPos pos, BlockState state) {
        super(SolarKineticRegistry.MOLECULAR_TRANSFORMER_BE.get(), pos, state);
    }

    public MolecularTransformerEngine engine() {
        return engine;
    }

    public ItemStackHandler rawInventory() {
        return inventory;
    }

    public boolean isClientActive() {
        return clientActive || getBlockState().getValue(MolecularTransformerBlock.ACTIVE);
    }

    public Container dropInventory() {
        SimpleContainer drops = new SimpleContainer(2);
        drops.setItem(0, inventory.getStackInSlot(0));
        drops.setItem(1, inventory.getStackInSlot(1));
        return drops;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        lastTick = serverLevel.getGameTime();

        MolecularTransformerRecipe recipe = findMatchingRecipe(serverLevel);
        boolean canAccept = false;
        String recipeKey = null;
        double recipeEu = 0.0;
        ItemStack outputPreview = ItemStack.EMPTY;

        if (recipe != null) {
            outputPreview = recipe.resolveOutput();
            if (!outputPreview.isEmpty()) {
                recipeKey = recipe.getId().toString();
                recipeEu = recipe.energy();
                canAccept = canFitOutput(outputPreview);
            }
        }

        boolean wasActive = engine.isActive();
        var result = engine.tick(recipeKey, recipeEu, canAccept);

        if (result == MolecularTransformerEngine.TickResult.COMPLETED
                && recipe != null
                && !outputPreview.isEmpty()) {
            inventory.extractItem(0, recipe.inputCount(), false);
            insertOutput(outputPreview);
            setChanged();
        } else if (result == MolecularTransformerEngine.TickResult.WORKING
                || result == MolecularTransformerEngine.TickResult.CANCELLED) {
            setChanged();
        }

        boolean nowActive = engine.isActive();
        BlockState state = getBlockState();
        if (state.hasProperty(MolecularTransformerBlock.ACTIVE)
                && state.getValue(MolecularTransformerBlock.ACTIVE) != nowActive) {
            serverLevel.setBlock(
                    worldPosition,
                    state.setValue(MolecularTransformerBlock.ACTIVE, nowActive),
                    Block.UPDATE_ALL);
            serverLevel.sendBlockUpdated(worldPosition, state, getBlockState(), Block.UPDATE_CLIENTS);
        } else if (wasActive != nowActive) {
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private @Nullable MolecularTransformerRecipe findMatchingRecipe(ServerLevel level) {
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) {
            return null;
        }
        SimpleContainer probe = new SimpleContainer(1);
        probe.setItem(0, input);
        for (var recipe :
                level.getRecipeManager()
                        .getAllRecipesFor(SolarKineticRegistry.MT_RECIPE_TYPE.get())) {
            if (recipe.matches(probe, level)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canFitOutput(ItemStack result) {
        ItemStack current = inventory.getStackInSlot(1);
        if (current.isEmpty()) {
            return result.getCount() <= result.getMaxStackSize();
        }
        return ItemStack.isSameItemSameTags(current, result)
                && current.getCount() + result.getCount() <= current.getMaxStackSize();
    }

    private void insertOutput(ItemStack result) {
        ItemStack current = inventory.getStackInSlot(1);
        if (current.isEmpty()) {
            inventory.setStackInSlot(1, result.copy());
        } else {
            current.grow(result.getCount());
            inventory.setStackInSlot(1, current);
        }
    }

    // ---- WorldEnergy / WorldPowerPort / EuBuffer / MachineAccessTarget ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            WorldEnergy.connect(this);
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
        portCache.invalidate();
        super.setRemoved();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        portCache.invalidate();
    }

    @Override
    public void invalidateMachinePorts() {
        portCache.invalidate();
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side != null && !MachineAccess.connects(this, side)) {
                return LazyOptional.empty();
            }
            return portCache.item(side, () -> sidedHandler).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return Ecc2EnergyHelper.ALL_INPUT_PORTS;
    }

    @Override
    public double receive(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return 0.0;
        }
        double remainder = engine.inject(amount);
        double accepted = Math.max(0.0, amount - remainder);
        if (accepted > 0.0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public double packetLimit() {
        // Tier 14 unlimited input — Molecular Transformer never explodes from high voltage.
        return Double.MAX_VALUE;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        double cap = engine.totalEu() > 0.0 ? engine.totalEu() : 1.0;
        double stored = engine.isWorking() ? engine.energyUsed() + engine.energyIn() : 0.0;
        return new EuBuffer.Reading(stored, cap);
    }

    // ---- NBT, Client Sync & MenuProvider ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putBoolean("Working", engine.isWorking());
        if (engine.activeRecipeKey() != null) {
            tag.putString("ActiveRecipe", engine.activeRecipeKey());
        }
        tag.putDouble("TotalEu", engine.totalEu());
        tag.putDouble("EnergyUsed", engine.energyUsed());
        tag.putDouble("EnergyIn", engine.energyIn());
        tag.putInt("IdleTicks", engine.idleTicks());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        String key = tag.contains("ActiveRecipe") ? tag.getString("ActiveRecipe") : null;
        engine.restoreState(
                tag.getBoolean("Working"),
                key,
                tag.getDouble("TotalEu"),
                tag.getDouble("EnergyUsed"),
                tag.getDouble("EnergyIn"),
                tag.contains("IdleTicks")
                        ? tag.getInt("IdleTicks")
                        : MolecularTransformerEngine.MAX_IDLE_TICKS);
        clientActive = engine.isActive();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("ClientActive", engine.isActive());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        clientActive = tag.getBoolean("ClientActive");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.molecular_transformer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new MolecularTransformerMenu(containerId, playerInv, this, syncData);
    }
}
