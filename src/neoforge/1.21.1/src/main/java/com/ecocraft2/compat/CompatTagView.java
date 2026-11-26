package com.ecocraft2.compat;

import net.minecraft.nbt.CompoundTag;

/** NeoForge 1.21.1 adapter exposing a {@link CompoundTag} through {@link CompatTelemetry.TagView}. */
final class CompatTagView {

    private CompatTagView() {}

    static CompatTelemetry.TagView of(CompoundTag tag) {
        return new View121(tag);
    }

    private record View121(CompoundTag tag) implements CompatTelemetry.TagView {

        @Override
        public boolean contains(String key) {
            return tag.contains(key);
        }

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
    }
}
