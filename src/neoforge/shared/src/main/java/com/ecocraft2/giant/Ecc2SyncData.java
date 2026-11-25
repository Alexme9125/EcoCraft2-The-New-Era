package com.ecocraft2.giant;

/** Lossless packing helpers for Minecraft's 16-bit signed {@code ContainerData} synchronization. */
public final class Ecc2SyncData {

    private Ecc2SyncData() {}

    public static int low16(int value) {
        return value & 0xFFFF;
    }

    public static int high16(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    public static int unpack32(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    public static int unpackU16(int raw) {
        return raw & 0xFFFF;
    }
}
