package com.ecocraft2.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** NeoForge 1.21.1 block-entity persistence and client sync base class. */
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
    protected final void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveEcc2(new CompoundWriter(tag, registries));
    }

    @Override
    protected final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadEcc2(new CompoundReader(tag, registries));
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
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (hasClientSync()) {
            readSyncData(new CompoundReader(tag, registries));
        } else {
            super.handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void onDataPacket(
            Connection connection,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        if (hasClientSync()) {
            var tag = packet.getTag();
            if (tag != null) {
                readSyncData(new CompoundReader(tag, registries));
            }
        } else {
            super.onDataPacket(connection, packet, registries);
        }
    }

    private record CompoundWriter(CompoundTag tag, HolderLookup.Provider registries)
            implements Ecc2TagIO.Writer {
        @Override
        public void putInt(String key, int value) {
            tag.putInt(key, value);
        }

        @Override
        public void putLong(String key, long value) {
            tag.putLong(key, value);
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
        public void putItems(String key, NonNullList<ItemStack> items) {
            var child = new CompoundTag();
            ContainerHelper.saveAllItems(child, items, registries);
            tag.put(key, child);
        }
    }

    private record CompoundReader(CompoundTag tag, HolderLookup.Provider registries)
            implements Ecc2TagIO.Reader {
        @Override
        public int getInt(String key, int fallback) {
            return tag.contains(key) ? tag.getInt(key) : fallback;
        }

        @Override
        public long getLong(String key, long fallback) {
            return tag.contains(key) ? tag.getLong(key) : fallback;
        }

        @Override
        public double getDouble(String key, double fallback) {
            return tag.contains(key) ? tag.getDouble(key) : fallback;
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return tag.contains(key) ? tag.getBoolean(key) : fallback;
        }

        @Override
        public String getString(String key, String fallback) {
            return tag.contains(key) ? tag.getString(key) : fallback;
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
    }
}
