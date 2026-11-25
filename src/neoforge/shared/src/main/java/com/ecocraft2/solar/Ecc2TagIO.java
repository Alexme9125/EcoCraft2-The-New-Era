package com.ecocraft2.solar;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Version-neutral block-entity persistence bridge unifying Minecraft 1.21.1 ({@code CompoundTag})
 * and Minecraft 26.x ({@code ValueOutput} / {@code ValueInput}).
 */
public final class Ecc2TagIO {

    private Ecc2TagIO() {}

    /** Serializes primitive fields and item inventories into platform storage. */
    public interface Writer {
        void putInt(String key, int value);

        void putLong(String key, long value);

        void putDouble(String key, double value);

        void putBoolean(String key, boolean value);

        void putString(String key, String value);

        void putItems(String key, NonNullList<ItemStack> items);
    }

    /** Reads primitive fields and item inventories from platform storage with safe defaults. */
    public interface Reader {
        int getInt(String key, int fallback);

        long getLong(String key, long fallback);

        double getDouble(String key, double fallback);

        boolean getBoolean(String key, boolean fallback);

        String getString(String key, String fallback);

        void loadItems(String key, NonNullList<ItemStack> target);
    }
}
