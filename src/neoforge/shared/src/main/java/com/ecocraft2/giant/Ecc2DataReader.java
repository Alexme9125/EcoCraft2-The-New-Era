package com.ecocraft2.giant;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Version-independent read facade for restoring EcoCraft 2 BlockEntity state across
 * NeoForge 1.21.1 ({@code CompoundTag}) and 26.x ({@code ValueInput}).
 */
public interface Ecc2DataReader {

    int getInt(String key, int defaultValue);

    double getDouble(String key, double defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    String getString(String key, String defaultValue);

    int[] getIntArray(String key, int expectedLength);

    void loadItems(String key, NonNullList<ItemStack> target);
}
