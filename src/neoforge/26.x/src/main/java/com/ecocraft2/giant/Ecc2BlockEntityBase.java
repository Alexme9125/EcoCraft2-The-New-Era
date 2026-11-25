package com.ecocraft2.giant;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** NeoForge 26.x BlockEntity base bridging {@link ValueOutput}/{@link ValueInput} and {@code preRemoveSideEffects}. */
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
    protected final void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveState(
                new Ecc2DataWriter() {
                    @Override
                    public void putInt(String key, int value) {
                        output.putInt(key, value);
                    }

                    @Override
                    public void putDouble(String key, double value) {
                        output.putDouble(key, value);
                    }

                    @Override
                    public void putBoolean(String key, boolean value) {
                        output.putBoolean(key, value);
                    }

                    @Override
                    public void putString(String key, String value) {
                        output.putString(key, value == null ? "" : value);
                    }

                    @Override
                    public void putIntArray(String key, int[] values) {
                        var list = new ArrayList<Integer>(values.length);
                        for (int v : values) {
                            list.add(v);
                        }
                        output.store(key, Codec.INT.listOf(), list);
                    }

                    @Override
                    public void putItems(String key, NonNullList<ItemStack> items) {
                        var list = new ArrayList<ItemStack>(items.size());
                        list.addAll(items);
                        output.store(key, ItemStack.OPTIONAL_CODEC.listOf(), list);
                    }
                });
    }

    @Override
    protected final void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadState(
                new Ecc2DataReader() {
                    @Override
                    public int getInt(String key, int defaultValue) {
                        return input.getIntOr(key, defaultValue);
                    }

                    @Override
                    public double getDouble(String key, double defaultValue) {
                        return input.getDoubleOr(key, defaultValue);
                    }

                    @Override
                    public boolean getBoolean(String key, boolean defaultValue) {
                        return input.getBooleanOr(key, defaultValue);
                    }

                    @Override
                    public String getString(String key, String defaultValue) {
                        return input.getStringOr(key, defaultValue);
                    }

                    @Override
                    public int[] getIntArray(String key, int expectedLength) {
                        var list = input.read(key, Codec.INT.listOf()).orElse(List.of());
                        int[] result = new int[expectedLength];
                        int n = Math.min(expectedLength, list.size());
                        for (int i = 0; i < n; i++) {
                            result[i] = list.get(i);
                        }
                        return result;
                    }

                    @Override
                    public void loadItems(String key, NonNullList<ItemStack> target) {
                        var list =
                                input.read(key, ItemStack.OPTIONAL_CODEC.listOf())
                                        .orElse(List.of());
                        for (int i = 0; i < target.size(); i++) {
                            target.set(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
                        }
                    }
                });
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (getLevel() instanceof ServerLevel level) {
            onRemovedFromWorld(level, pos, state);
        }
    }
}
