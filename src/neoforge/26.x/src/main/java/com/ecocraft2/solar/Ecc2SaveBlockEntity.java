package com.ecocraft2.solar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** NeoForge 26.x block-entity persistence, drop side-effects, and client sync base class. */
public abstract class Ecc2SaveBlockEntity extends BlockEntity {

    protected Ecc2SaveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Persists block-entity fields and inventories. */
    protected abstract void saveEcc2(Ecc2TagIO.Writer out);

    /** Restores block-entity fields and inventories. */
    protected abstract void loadEcc2(Ecc2TagIO.Reader in);

    /** Returns {@code true} when this block entity syncs render state to clients. */
    protected boolean hasClientSync() {
        return false;
    }

    /** Writes compact client-sync fields for renderers. */
    protected void writeSyncData(CompoundTag tag) {}

    /** Reads compact client-sync fields on the client thread. */
    protected void readSyncData(Ecc2TagIO.Reader in) {}

    /** Returns the inventory dropped when the block is broken, or {@code null} if none. */
    protected Container dropInventory() {
        return null;
    }

    @Override
    protected final void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveEcc2(new ValueWriter(output));
    }

    @Override
    protected final void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadEcc2(new ValueReader(input));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return hasClientSync() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        if (!hasClientSync()) {
            return super.getUpdateTag(registries);
        }
        var tag = new CompoundTag();
        writeSyncData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        if (hasClientSync()) {
            readSyncData(new ValueReader(input));
        } else {
            super.handleUpdateTag(input);
        }
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        if (hasClientSync()) {
            readSyncData(new ValueReader(input));
        } else {
            super.onDataPacket(connection, input);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (getLevel() instanceof ServerLevel server) {
            var inv = dropInventory();
            if (inv != null) {
                Containers.dropContents(server, pos, inv);
            }
        }
    }

    private record ValueWriter(ValueOutput output) implements Ecc2TagIO.Writer {
        @Override
        public void putInt(String key, int value) {
            output.putInt(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            output.putLong(key, value);
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
        public void putItems(String key, NonNullList<ItemStack> items) {
            var list = new ArrayList<ItemStack>(items.size());
            list.addAll(items);
            output.store(key, ItemStack.OPTIONAL_CODEC.listOf(), list);
        }
    }

    private record ValueReader(ValueInput input) implements Ecc2TagIO.Reader {
        @Override
        public int getInt(String key, int fallback) {
            return input.getIntOr(key, fallback);
        }

        @Override
        public long getLong(String key, long fallback) {
            return input.getLongOr(key, fallback);
        }

        @Override
        public double getDouble(String key, double fallback) {
            return input.getDoubleOr(key, fallback);
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return input.getBooleanOr(key, fallback);
        }

        @Override
        public String getString(String key, String fallback) {
            return input.getStringOr(key, fallback);
        }

        @Override
        public void loadItems(String key, NonNullList<ItemStack> target) {
            var loaded = input.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
            for (int i = 0; i < target.size(); i++) {
                target.set(i, i < loaded.size() ? loaded.get(i) : ItemStack.EMPTY);
            }
        }
    }
}
