package com.ecocraft2.giant;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** NeoForge 1.21.1 BlockEntity base bridging {@link CompoundTag} persistence to {@link Ecc2DataWriter}/{@link Ecc2DataReader}. */
public abstract class Ecc2BlockEntityBase extends BlockEntity {

    protected Ecc2BlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Serializes machine state into the version-independent writer. */
    protected abstract void saveState(Ecc2DataWriter out);

    /** Restores machine state from the version-independent reader. */
    protected abstract void loadState(Ecc2DataReader in);

    /** Invoked once on the server when the block is broken or replaced by a different block. */
    protected void onRemovedFromWorld(ServerLevel level, BlockPos pos, BlockState oldState) {}

    @Override
    protected final void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveState(
                new Ecc2DataWriter() {
                    @Override
                    public void putInt(String key, int value) {
                        tag.putInt(key, value);
                    }

                    @Override
                    public void putDouble(String key, double value) {
                        tag.putDouble(key, value);
                    }

                    @Override
                    public void putBoolean(String key, boolean value) {
                        tag.putBoolean(key, value);
                    }

                    @Override
                    public void putString(String key, String value) {
                        tag.putString(key, value == null ? "" : value);
                    }

                    @Override
                    public void putIntArray(String key, int[] values) {
                        tag.putIntArray(key, values);
                    }

                    @Override
                    public void putItems(String key, NonNullList<ItemStack> items) {
                        var sub = new CompoundTag();
                        ContainerHelper.saveAllItems(sub, items, registries);
                        tag.put(key, sub);
                    }
                });
    }

    @Override
    protected final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadState(
                new Ecc2DataReader() {
                    @Override
                    public int getInt(String key, int defaultValue) {
                        return tag.contains(key) ? tag.getInt(key) : defaultValue;
                    }

                    @Override
                    public double getDouble(String key, double defaultValue) {
                        return tag.contains(key) ? tag.getDouble(key) : defaultValue;
                    }

                    @Override
                    public boolean getBoolean(String key, boolean defaultValue) {
                        return tag.contains(key) ? tag.getBoolean(key) : defaultValue;
                    }

                    @Override
                    public String getString(String key, String defaultValue) {
                        return tag.contains(key) ? tag.getString(key) : defaultValue;
                    }

                    @Override
                    public int[] getIntArray(String key, int expectedLength) {
                        int[] raw = tag.getIntArray(key);
                        if (raw.length == expectedLength) {
                            return raw;
                        }
                        return Arrays.copyOf(raw, expectedLength);
                    }

                    @Override
                    public void loadItems(String key, NonNullList<ItemStack> target) {
                        for (int i = 0; i < target.size(); i++) {
                            target.set(i, ItemStack.EMPTY);
                        }
                        if (tag.contains(key)) {
                            ContainerHelper.loadAllItems(tag.getCompound(key), target, registries);
                        }
                    }
                });
    }
}
