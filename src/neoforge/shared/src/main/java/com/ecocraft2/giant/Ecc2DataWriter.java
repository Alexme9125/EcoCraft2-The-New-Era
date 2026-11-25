package com.ecocraft2.giant;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Version-independent write facade for persisting EcoCraft 2 BlockEntity state across
 * NeoForge 1.21.1 ({@code CompoundTag}) and 26.x ({@code ValueOutput}).
 */
public interface Ecc2DataWriter {

    void putInt(String key, int value);

    void putDouble(String key, double value);

    void putBoolean(String key, boolean value);

    void putString(String key, String value);

    void putIntArray(String key, int[] values);

    void putItems(String key, NonNullList<ItemStack> items);
}
